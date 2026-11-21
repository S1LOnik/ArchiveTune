// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

pub const MIN_BEATMATCH_CONFIDENCE: f64 = 0.55;
pub const MIN_FILTER_CONFIDENCE: f64 = 0.20;
pub const MAX_RATE_ALIGNMENT: f64 = 0.04;
pub const MIN_OVERLAP_BEATS: f64 = 4.0;
pub const MAX_OVERLAP_BEATS: f64 = 16.0;
pub const MAX_OVERLAP_MS: i64 = 12_000;
pub const END_GUARD_MS: i64 = 1_500;
pub const MAX_TRANSITION_LEAD_MS: i64 = 14_000;
pub const HARD_END_LEAD_MS: i64 = 3_500;
pub const AUTO_MIX_CURVE_STEP_MS: i64 = 250;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(i32)]
pub enum AutoMixStyleRaw {
    Gapless = 0,
    EqualPower = 1,
    DjBlend = 2,
    DjFilter = 3,
}

#[derive(Debug, Clone)]
pub struct AutoMixAnalysisRaw<'a> {
    pub duration_ms: i64,
    pub bpm: f64,
    pub beat_interval_ms: f64,
    pub beat_confidence: f64,
    pub downbeat_phase_ms: f64,
    pub audible_start_ms: i64,
    pub intro_end_ms: i64,
    pub outro_start_ms: i64,
    pub content_end_ms: i64,
    pub final_fade_onset_ms: i64,
    pub mix_in_candidates_ms: &'a [i64],
    pub mix_out_candidates_ms: &'a [i64],
    pub energy_curve: &'a [f32],
    pub vocal_activity: &'a [f32],
}

impl<'a> AutoMixAnalysisRaw<'a> {
    pub fn is_usable(&self) -> bool {
        self.duration_ms > 0 && self.content_end_ms > 0 && !self.energy_curve.is_empty()
    }

    pub fn energy_at(&self, ms: i64) -> f32 {
        curve_value_at(self.energy_curve, ms)
    }

    pub fn vocal_at(&self, ms: i64) -> f32 {
        curve_value_at(self.vocal_activity, ms)
    }
}

fn curve_value_at(curve: &[f32], ms: i64) -> f32 {
    if curve.is_empty() {
        return 0.0;
    }
    let index = (ms / AUTO_MIX_CURVE_STEP_MS) as isize;
    if index < 0 {
        curve[0]
    } else if (index as usize) < curve.len() {
        curve[index as usize]
    } else {
        curve[curve.len() - 1]
    }
}

#[derive(Debug, Clone)]
pub struct AutoMixPlanRaw {
    pub blocked: bool,
    pub reason: String,
    pub style: AutoMixStyleRaw,
    pub transition_start_ms: i64,
    pub transition_end_ms: i64,
    pub fade_ms: i64,
    pub incoming_cue_ms: i64,
    pub incoming_playback_rate: f64,
    pub bass_swap: bool,
    pub bass_swap_fraction: f64,
    pub filter_sweep: f64,
    pub vocal_overlap: f64,
    pub marker_visible: bool,
}

impl AutoMixPlanRaw {
    pub fn fallback(fade_ms: i64, anchor_ms: i64, reason: &str) -> Self {
        Self {
            blocked: false,
            reason: reason.to_string(),
            style: AutoMixStyleRaw::EqualPower,
            transition_start_ms: anchor_ms,
            transition_end_ms: anchor_ms + fade_ms,
            fade_ms,
            incoming_cue_ms: 0,
            incoming_playback_rate: 1.0,
            bass_swap: false,
            bass_swap_fraction: 0.5,
            filter_sweep: 0.0,
            vocal_overlap: 0.0,
            marker_visible: false,
        }
    }
}

#[allow(clippy::too_many_arguments)]
pub fn plan_automix(
    current: Option<&AutoMixAnalysisRaw>,
    next: Option<&AutoMixAnalysisRaw>,
    current_track_duration_ms: i64,
    next_track_duration_ms: i64,
    _position_ms: i64,
    fallback_fade_ms: i64,
    min_fade_ms: i64,
    gapless_album: bool,
) -> AutoMixPlanRaw {
    let current_usable = current.is_some_and(|a| a.is_usable());
    let next_usable = next.is_some_and(|a| a.is_usable());

    if gapless_album {
        return AutoMixPlanRaw {
            blocked: false,
            reason: "same-album sequence".to_string(),
            style: AutoMixStyleRaw::Gapless,
            transition_start_ms: current_track_duration_ms,
            transition_end_ms: current_track_duration_ms,
            fade_ms: 0,
            incoming_cue_ms: 0,
            incoming_playback_rate: 1.0,
            bass_swap: false,
            bass_swap_fraction: 0.5,
            filter_sweep: 0.0,
            vocal_overlap: 0.0,
            marker_visible: current_usable && next_usable,
        };
    }

    if !current_usable || !next_usable {
        let anchor = fallback_anchor(current, current_track_duration_ms, fallback_fade_ms);
        let reason = if !current_usable && !next_usable {
            "no analysis"
        } else {
            "partial analysis"
        };
        return AutoMixPlanRaw::fallback(fallback_fade_ms, anchor, reason);
    }

    let cur = current.unwrap();
    let nxt = next.unwrap();

    let outgoing_anchor = pick_mix_out_anchor(cur, current_track_duration_ms, fallback_fade_ms);
    let outgoing_fade_ms = dynamic_fade_ms(cur, outgoing_anchor, fallback_fade_ms, min_fade_ms);
    let incoming_cue = pick_mix_in_cue(nxt, next_track_duration_ms);

    let tempo_ratio = if cur.bpm > 1.0 && nxt.bpm > 1.0 {
        nxt.bpm / cur.bpm
    } else {
        1.0
    };

    let beat_matchable = cur.beat_confidence >= MIN_BEATMATCH_CONFIDENCE
        && nxt.beat_confidence >= MIN_BEATMATCH_CONFIDENCE
        && (tempo_ratio - 1.0).abs() <= MAX_RATE_ALIGNMENT;

    let filterable = cur.beat_confidence >= MIN_FILTER_CONFIDENCE
        || nxt.beat_confidence >= MIN_FILTER_CONFIDENCE;

    let available_ms = (current_track_duration_ms - outgoing_anchor - END_GUARD_MS).max(0);

    if beat_matchable {
        plan_beat_matched(
            cur,
            nxt,
            outgoing_anchor,
            incoming_cue,
            available_ms,
            outgoing_fade_ms,
            min_fade_ms,
        )
    } else if filterable {
        plan_filter_ride(
            cur,
            outgoing_anchor,
            incoming_cue,
            available_ms,
            outgoing_fade_ms,
            min_fade_ms,
        )
    } else {
        AutoMixPlanRaw::fallback(outgoing_fade_ms, outgoing_anchor, "low beat confidence")
    }
}

#[allow(clippy::too_many_arguments)]
fn plan_beat_matched(
    current: &AutoMixAnalysisRaw,
    next: &AutoMixAnalysisRaw,
    outgoing_anchor: i64,
    incoming_cue: i64,
    available_ms: i64,
    fallback_fade_ms: i64,
    min_fade_ms: i64,
) -> AutoMixPlanRaw {
    let beat_ms = current.beat_interval_ms;
    if beat_ms <= 0.0 {
        return AutoMixPlanRaw::fallback(fallback_fade_ms, outgoing_anchor, "no beat interval");
    }

    let overlap_beats =
        (MAX_OVERLAP_BEATS.min(available_ms as f64 / beat_ms)).max(MIN_OVERLAP_BEATS);
    let mut overlap_ms = ((overlap_beats * beat_ms).round() as i64)
        .min(MAX_OVERLAP_MS)
        .max(min_fade_ms);

    let mut vocal_overlap =
        vocal_clash_across(current, next, outgoing_anchor, incoming_cue, overlap_ms);
    if vocal_overlap > 0.55 && overlap_ms as f64 > min_fade_ms as f64 * 1.5 {
        overlap_ms = ((overlap_ms as f64 * 0.6).round() as i64).max(min_fade_ms);
        vocal_overlap =
            vocal_clash_across(current, next, outgoing_anchor, incoming_cue, overlap_ms);
    }

    if overlap_ms < min_fade_ms || vocal_overlap > 0.8 {
        return filter_ride_plan(
            outgoing_anchor,
            incoming_cue,
            fallback_fade_ms.min(available_ms).max(min_fade_ms),
            vocal_overlap,
            "vocal clash",
        );
    }

    let start_ms = align_to_downbeat(current, outgoing_anchor).max(outgoing_anchor);
    let end_ms = start_ms + overlap_ms;

    let rate_alignment = if next.bpm > 1.0 && current.bpm > 1.0 {
        (current.bpm / next.bpm).clamp(1.0 - MAX_RATE_ALIGNMENT, 1.0 + MAX_RATE_ALIGNMENT)
    } else {
        1.0
    };

    let bass_swap_fraction = quietest_beat_fraction(current, start_ms, overlap_ms);

    AutoMixPlanRaw {
        blocked: false,
        reason: format!("beat-matched {:.1}->{:.1} bpm", current.bpm, next.bpm),
        style: AutoMixStyleRaw::DjBlend,
        transition_start_ms: start_ms,
        transition_end_ms: end_ms,
        fade_ms: overlap_ms,
        incoming_cue_ms: incoming_cue,
        incoming_playback_rate: rate_alignment,
        bass_swap: true,
        bass_swap_fraction,
        filter_sweep: 0.0,
        vocal_overlap,
        marker_visible: true,
    }
}

fn plan_filter_ride(
    current: &AutoMixAnalysisRaw,
    outgoing_anchor: i64,
    incoming_cue: i64,
    available_ms: i64,
    fallback_fade_ms: i64,
    min_fade_ms: i64,
) -> AutoMixPlanRaw {
    let fade_ms = fallback_fade_ms
        .min(available_ms)
        .max(min_fade_ms)
        .min(MAX_OVERLAP_MS);
    let vocal_overlap =
        vocal_clash_across(current, current, outgoing_anchor, incoming_cue, fade_ms);
    filter_ride_plan(
        outgoing_anchor,
        incoming_cue,
        fade_ms,
        vocal_overlap,
        "filter ride",
    )
}

fn filter_ride_plan(
    outgoing_anchor: i64,
    incoming_cue: i64,
    fade_ms: i64,
    vocal_overlap: f64,
    reason: &str,
) -> AutoMixPlanRaw {
    AutoMixPlanRaw {
        blocked: false,
        reason: reason.to_string(),
        style: AutoMixStyleRaw::DjFilter,
        transition_start_ms: outgoing_anchor,
        transition_end_ms: outgoing_anchor + fade_ms,
        fade_ms,
        incoming_cue_ms: incoming_cue,
        incoming_playback_rate: 1.0,
        bass_swap: false,
        bass_swap_fraction: 0.5,
        filter_sweep: 1.0,
        vocal_overlap,
        marker_visible: true,
    }
}

fn pick_mix_out_anchor(
    analysis: &AutoMixAnalysisRaw,
    duration_ms: i64,
    fallback_fade_ms: i64,
) -> i64 {
    let hard_limit = (duration_ms - END_GUARD_MS).max(0);
    let content_end = analysis.content_end_ms.clamp(0, hard_limit);

    let onset = analysis.final_fade_onset_ms;
    if onset > 0 && onset < content_end {
        return onset
            .max(content_end - MAX_OVERLAP_MS)
            .min(content_end - 1)
            .max(earliest_transition_start(duration_ms));
    }

    (content_end - HARD_END_LEAD_MS.min(fallback_fade_ms))
        .max(analysis.intro_end_ms.max(0))
        .max(earliest_transition_start(duration_ms))
        .max(0)
        .min((content_end - 1).max(0))
}

fn earliest_transition_start(duration_ms: i64) -> i64 {
    (duration_ms - MAX_TRANSITION_LEAD_MS).max(0)
}

fn dynamic_fade_ms(
    analysis: &AutoMixAnalysisRaw,
    anchor_ms: i64,
    fallback_fade_ms: i64,
    min_fade_ms: i64,
) -> i64 {
    let content_end = analysis.content_end_ms;
    let onset = analysis.final_fade_onset_ms;
    if onset <= 0 || onset >= content_end {
        return fallback_fade_ms;
    }
    let natural = (content_end - anchor_ms).max(0);
    if natural < min_fade_ms {
        return fallback_fade_ms;
    }
    natural.min(MAX_OVERLAP_MS)
}

fn pick_mix_in_cue(analysis: &AutoMixAnalysisRaw, duration_ms: i64) -> i64 {
    let base = analysis
        .mix_in_candidates_ms
        .iter()
        .copied()
        .find(|&c| c > 0 && c < duration_ms / 2)
        .unwrap_or_else(|| analysis.intro_end_ms.clamp(0, duration_ms / 2));
    align_to_downbeat(analysis, base)
}

fn align_to_downbeat(analysis: &AutoMixAnalysisRaw, ms: i64) -> i64 {
    let bar = analysis.downbeat_phase_ms;
    let interval = analysis.beat_interval_ms * 4.0;
    if interval <= 0.0 || bar.is_nan() {
        return ms;
    }
    let phase = (ms as f64 - bar) / interval;
    let snapped = bar + phase.ceil() * interval;
    let snapped_down = bar + phase.floor() * interval;
    let up = snapped.round() as i64;
    let down = snapped_down.round() as i64;
    if (up - ms).abs() <= (down - ms).abs() {
        up.max(0)
    } else {
        down.max(0)
    }
}

fn vocal_clash_across(
    current: &AutoMixAnalysisRaw,
    next: &AutoMixAnalysisRaw,
    current_start_ms: i64,
    next_cue_ms: i64,
    overlap_ms: i64,
) -> f64 {
    if overlap_ms <= 0 {
        return 0.0;
    }
    let steps = ((overlap_ms / AUTO_MIX_CURVE_STEP_MS) as usize).clamp(1, 48);
    let mut clash = 0.0f64;
    for s in 0..steps {
        let offset = s as i64 * AUTO_MIX_CURVE_STEP_MS;
        let a = current.vocal_at(current_start_ms + offset);
        let b = next.vocal_at(next_cue_ms + offset);
        clash += a.min(b) as f64;
    }
    (clash / steps as f64).clamp(0.0, 1.0)
}

fn quietest_beat_fraction(analysis: &AutoMixAnalysisRaw, start_ms: i64, overlap_ms: i64) -> f64 {
    let beat = analysis.beat_interval_ms;
    if beat <= 0.0 || overlap_ms <= 0 {
        return 0.5;
    }
    let mut best_fraction = 0.5;
    let mut best_energy = f32::MAX;
    let mut t = 0.0f64;
    while t < overlap_ms as f64 {
        let energy = analysis.energy_at(start_ms + t.round() as i64);
        if energy < best_energy {
            best_energy = energy;
            best_fraction = t / overlap_ms as f64;
        }
        t += beat;
    }
    best_fraction.clamp(0.15, 0.85)
}

fn fallback_anchor(
    analysis: Option<&AutoMixAnalysisRaw>,
    duration_ms: i64,
    fallback_fade_ms: i64,
) -> i64 {
    let content_end = analysis
        .map(|a| a.content_end_ms)
        .filter(|&c| c > 0)
        .unwrap_or(duration_ms);

    let silence_onset = analysis
        .map(|a| a.final_fade_onset_ms)
        .filter(|&s| s > 0 && s < content_end);

    let anchor = silence_onset
        .unwrap_or_else(|| (content_end - HARD_END_LEAD_MS.min(fallback_fade_ms)).max(0));

    anchor
        .max(earliest_transition_start(duration_ms))
        .max(0)
        .min((content_end - 1).max(0))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_plan_gapless_sequence() {
        let plan = plan_automix(None, None, 200_000, 210_000, 100_000, 6_000, 2_000, true);
        assert_eq!(plan.style, AutoMixStyleRaw::Gapless);
        assert_eq!(plan.fade_ms, 0);
        assert_eq!(plan.transition_start_ms, 200_000);
        assert_eq!(plan.reason, "same-album sequence");
    }

    #[test]
    fn test_plan_fallback_no_analysis() {
        let plan = plan_automix(None, None, 200_000, 210_000, 100_000, 6_000, 2_000, false);
        assert_eq!(plan.style, AutoMixStyleRaw::EqualPower);
        assert_eq!(plan.fade_ms, 6_000);
        assert_eq!(plan.reason, "no analysis");
    }
}
