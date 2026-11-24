// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::f32::consts::PI;

pub const OPEN_HZ: f32 = 20_000.0;
pub const OFF_HZ: f32 = 10.0;
pub const MIN_HZ: f32 = 20.0;
pub const MAX_HIGH_PASS_HZ: f32 = 7_000.0;
const SETTLED_HZ: f32 = 400.0;
const GLIDE_FRAMES: usize = 64;
const GLIDE_ALPHA: f32 = 0.25;
const DAMPING: f32 = 1.0;
const MAX_F: f32 = 0.8;
const CURVE_STEP_MS: i64 = 250;
const FINAL_FADE_WINDOW_MS: i64 = 15_000;
const FINAL_FADE_SMOOTHING_SPAN: usize = 4;
const FINAL_FADE_QUIET_RATIO: f32 = 0.60;

/// Native real-time State Variable Filter (SVF) processor with exponential cutoff glide.
///
/// Mirrors Kotlin `AutoMixFilterProcessor` acoustics while executing inner per-sample
/// loops in optimized Rust/NEON without JNI boundary boxing per sample.
#[derive(Debug, Clone)]
pub struct SvfFilterProcessor {
    channel_count: usize,
    sample_rate: f32,
    target_low_hz: f32,
    target_high_hz: f32,
    current_low_hz: f32,
    current_high_hz: f32,
    low_f: f32,
    high_f: f32,
    low_low: Vec<f32>,
    low_band: Vec<f32>,
    high_low: Vec<f32>,
    high_band: Vec<f32>,
    frame_cursor: usize,
}

impl SvfFilterProcessor {
    pub fn new(channel_count: usize, sample_rate: i32) -> Result<Self, &'static str> {
        if channel_count == 0 || channel_count > 32 {
            return Err("channel_count must be within 1..=32");
        }
        if sample_rate < 8_000 || sample_rate > 768_000 {
            return Err("sample_rate must be within 8000..=768000");
        }
        let mut proc = Self {
            channel_count,
            sample_rate: sample_rate as f32,
            target_low_hz: OPEN_HZ,
            target_high_hz: OFF_HZ,
            current_low_hz: OPEN_HZ,
            current_high_hz: OFF_HZ,
            low_f: 0.0,
            high_f: 0.0,
            low_low: vec![0.0; channel_count],
            low_band: vec![0.0; channel_count],
            high_low: vec![0.0; channel_count],
            high_band: vec![0.0; channel_count],
            frame_cursor: 0,
        };
        proc.update_coefficients();
        Ok(proc)
    }

    pub fn channel_count(&self) -> usize {
        self.channel_count
    }

    pub fn sample_rate(&self) -> f32 {
        self.sample_rate
    }

    pub fn set_cutoffs(&mut self, low_pass_hz: f32, high_pass_hz: f32) {
        if low_pass_hz.is_finite() {
            self.target_low_hz = low_pass_hz.clamp(MIN_HZ, OPEN_HZ);
        }
        if high_pass_hz.is_finite() {
            self.target_high_hz = high_pass_hz.clamp(OFF_HZ, MAX_HIGH_PASS_HZ);
        }
    }

    pub fn flush(&mut self) {
        self.low_low.fill(0.0);
        self.low_band.fill(0.0);
        self.high_low.fill(0.0);
        self.high_band.fill(0.0);
        self.current_low_hz = self.target_low_hz;
        self.current_high_hz = self.target_high_hz;
        self.frame_cursor = 0;
        self.update_coefficients();
    }

    pub fn is_parked(&self) -> bool {
        self.target_low_hz >= OPEN_HZ
            && self.target_high_hz <= OFF_HZ
            && self.current_low_hz >= OPEN_HZ - SETTLED_HZ
            && self.current_high_hz <= OFF_HZ + SETTLED_HZ
    }

    fn update_coefficients(&mut self) {
        let nyquist = self.sample_rate * 0.5;
        let max_cutoff = (nyquist * 0.45).max(MIN_HZ);
        let low_hz = self.current_low_hz.clamp(MIN_HZ, max_cutoff);
        let high_hz = self.current_high_hz.clamp(MIN_HZ, max_cutoff);
        self.low_f = (2.0 * (PI * low_hz / self.sample_rate).sin()).min(MAX_F);
        self.high_f = (2.0 * (PI * high_hz / self.sample_rate).sin()).min(MAX_F);
    }

    #[inline]
    fn step_glide(&mut self) {
        self.current_low_hz += (self.target_low_hz - self.current_low_hz) * GLIDE_ALPHA;
        self.current_high_hz += (self.target_high_hz - self.current_high_hz) * GLIDE_ALPHA;
        self.update_coefficients();
    }

    #[inline]
    fn low_pass_sample(&mut self, channel: usize, input: f32) -> f32 {
        let low = self.low_low[channel];
        let band = self.low_band[channel];
        let new_low = low + self.low_f * band;
        let new_band = band + self.low_f * (input - new_low - DAMPING * band);
        self.low_low[channel] = new_low;
        self.low_band[channel] = new_band;
        new_low
    }

    #[inline]
    fn high_pass_sample(&mut self, channel: usize, input: f32) -> f32 {
        let low = self.high_low[channel];
        let band = self.high_band[channel];
        let new_low = low + self.high_f * band;
        let new_band = band + self.high_f * (input - new_low - DAMPING * band);
        self.high_low[channel] = new_low;
        self.high_band[channel] = new_band;
        input - new_low - DAMPING * new_band
    }

    /// Processes interleaved 16-bit PCM samples in-place.
    /// Returns `true` if filtering was applied or `false` if processor was parked (pass-through).
    pub fn process_i16_in_place(&mut self, samples: &mut [i16]) -> bool {
        if samples.is_empty() || self.channel_count == 0 {
            return false;
        }
        if self.is_parked() {
            return false;
        }
        let low_active = self.target_low_hz < OPEN_HZ - SETTLED_HZ
            || self.current_low_hz < OPEN_HZ - SETTLED_HZ;
        let high_active = self.target_high_hz > OFF_HZ + SETTLED_HZ
            || self.current_high_hz > OFF_HZ + SETTLED_HZ;
        if !low_active && !high_active {
            return false;
        }

        let channels = self.channel_count;
        for frame in samples.chunks_exact_mut(channels) {
            if self.frame_cursor.is_multiple_of(GLIDE_FRAMES) {
                self.step_glide();
            }
            self.frame_cursor = self.frame_cursor.wrapping_add(1);
            for (ch, sample_ref) in frame.iter_mut().enumerate() {
                let mut sample = (*sample_ref as f32) / 32768.0;
                if low_active {
                    sample = self.low_pass_sample(ch, sample);
                }
                if high_active {
                    sample = self.high_pass_sample(ch, sample);
                }
                let scaled = (sample.clamp(-1.0, 1.0) * 32767.0) as i32;
                *sample_ref = scaled.clamp(i16::MIN as i32, i16::MAX as i32) as i16;
            }
        }
        true
    }

    /// Processes interleaved 32-bit float PCM samples in-place.
    pub fn process_f32_in_place(&mut self, samples: &mut [f32]) -> bool {
        if samples.is_empty() || self.channel_count == 0 {
            return false;
        }
        if self.is_parked() {
            return false;
        }
        let low_active = self.target_low_hz < OPEN_HZ - SETTLED_HZ
            || self.current_low_hz < OPEN_HZ - SETTLED_HZ;
        let high_active = self.target_high_hz > OFF_HZ + SETTLED_HZ
            || self.current_high_hz > OFF_HZ + SETTLED_HZ;
        if !low_active && !high_active {
            return false;
        }

        let channels = self.channel_count;
        for frame in samples.chunks_exact_mut(channels) {
            if self.frame_cursor.is_multiple_of(GLIDE_FRAMES) {
                self.step_glide();
            }
            self.frame_cursor = self.frame_cursor.wrapping_add(1);
            for (ch, sample_ref) in frame.iter_mut().enumerate() {
                let mut sample = *sample_ref;
                if low_active {
                    sample = self.low_pass_sample(ch, sample);
                }
                if high_active {
                    sample = self.high_pass_sample(ch, sample);
                }
                *sample_ref = if sample.is_finite() { sample } else { 0.0 };
            }
        }
        true
    }
}

/// Computes a Hann window of length `size`.
pub fn hann_window(size: usize) -> Vec<f32> {
    if size == 0 {
        return Vec::new();
    }
    let denom = size as f32;
    (0..size)
        .map(|i| 0.5 * (1.0 - (2.0 * PI * (i as f32) / denom).cos()))
        .collect()
}

/// Computes the interquartile trimmed mean of `values` (matching Kotlin `robustMean`).
pub fn robust_mean(values: &[f32]) -> f32 {
    if values.is_empty() {
        return 0.0;
    }
    let mut sorted: Vec<f32> = values.iter().copied().filter(|v| v.is_finite()).collect();
    if sorted.is_empty() {
        return 0.0;
    }
    sorted.sort_by(f32::total_cmp);
    let quarter = sorted.len() / 4;
    let from = quarter;
    let to = sorted.len() - quarter;
    if to <= from {
        return sorted[sorted.len() / 2];
    }
    let sum: f32 = sorted[from..to].iter().sum();
    sum / ((to - from) as f32)
}

/// Applies symmetric moving-average smoothing with half-width `span`.
pub fn smooth_curve(curve: &[f32], span: usize) -> Vec<f32> {
    if curve.is_empty() || span == 0 {
        return curve.to_vec();
    }
    let len = curve.len();
    let mut out = vec![0.0f32; len];
    let mut acc = 0.0f32;
    let mut count = 0usize;
    let init_end = span.min(len - 1);
    for &v in &curve[..=init_end] {
        acc += v;
        count += 1;
    }
    for idx in 0..len {
        out[idx] = if count > 0 { acc / (count as f32) } else { 0.0 };
        let add = idx + span + 1;
        if add < len {
            acc += curve[add];
            count += 1;
        }
        if let Some(rem) = idx.checked_sub(span) {
            acc -= curve[rem];
            count -= 1;
        }
    }
    out
}

/// Normalizes a raw vocal activity curve using median baseline and 95th-percentile spread.
pub fn normalize_vocal_curve(raw: &[f32]) -> Vec<f32> {
    if raw.is_empty() {
        return Vec::new();
    }
    let mut sorted = raw.to_vec();
    sorted.sort_by(f32::total_cmp);
    let median = sorted[sorted.len() / 2];
    let p95 = sorted[(sorted.len() * 19) / 20];
    let spread = p95 - median;
    if !spread.is_finite() || spread <= 1e-6 {
        return vec![0.5f32; raw.len()];
    }
    raw.iter()
        .map(|&v| ((v - median) / spread).clamp(0.0, 1.0))
        .collect()
}

/// Detects the final sustained fade-out onset timestamp in milliseconds.
pub fn detect_final_fade_onset_ms(
    energy: &[f32],
    intro_end_ms: i64,
    content_end_ms: i64,
    step_ms: i64,
) -> i64 {
    if energy.is_empty() || content_end_ms <= intro_end_ms || step_ms <= 0 {
        return content_end_ms;
    }
    let end_idx = ((content_end_ms / step_ms).max(0) as usize).min(energy.len() - 1);
    let win_start_ms = intro_end_ms.max(content_end_ms - FINAL_FADE_WINDOW_MS);
    let start_idx = ((win_start_ms / step_ms).max(0) as usize).min(end_idx);
    if end_idx <= start_idx {
        return content_end_ms;
    }
    let window = &energy[start_idx..=end_idx];
    if window.len() < 4 {
        return content_end_ms;
    }
    let smooth = smooth_curve(window, FINAL_FADE_SMOOTHING_SPAN);
    let mut sorted = smooth.clone();
    sorted.sort_by(f32::total_cmp);
    let loud = sorted[(sorted.len() * 9) / 10].max(1e-6);
    let quiet = loud * FINAL_FADE_QUIET_RATIO;

    let mut last_loud_idx: Option<usize> = None;
    for (idx, &v) in smooth.iter().enumerate() {
        if v >= quiet {
            last_loud_idx = Some(idx);
        }
    }
    match last_loud_idx {
        Some(idx) if idx + 1 < smooth.len() => {
            (((start_idx + idx + 1) as i64) * step_ms).min(content_end_ms)
        }
        _ => content_end_ms,
    }
}

/// Extracted structural transition cues and normalized vocal curve.
#[derive(Debug, Clone, PartialEq)]
pub struct CurveAnalysisSummary {
    pub audible_start_ms: i64,
    pub intro_end_ms: i64,
    pub outro_start_ms: i64,
    pub content_end_ms: i64,
    pub final_fade_onset_ms: i64,
    pub mix_in_candidates_ms: Vec<i64>,
    pub mix_out_candidates_ms: Vec<i64>,
    pub normalized_vocal: Vec<f32>,
}

/// Computes full AutoMix structure cues and transition candidates from energy and vocal curves.
pub fn analyze_energy_profile(
    energy: &[f32],
    vocal_raw: &[f32],
    duration_ms: i64,
) -> CurveAnalysisSummary {
    let normalized_vocal = normalize_vocal_curve(vocal_raw);
    if energy.is_empty() {
        return CurveAnalysisSummary {
            audible_start_ms: 0,
            intro_end_ms: 0,
            outro_start_ms: 0,
            content_end_ms: duration_ms.max(0),
            final_fade_onset_ms: duration_ms.max(0),
            mix_in_candidates_ms: vec![0],
            mix_out_candidates_ms: vec![0],
            normalized_vocal,
        };
    }

    let level = robust_mean(energy).max(1e-6);
    let floor = level * 0.10;
    let active = level * 0.35;
    let content_floor = level * 0.15;

    let audible_start_ms = energy
        .iter()
        .position(|&e| e > floor)
        .map(|idx| (idx as i64) * CURVE_STEP_MS)
        .unwrap_or(0);
    let intro_end_ms = energy
        .iter()
        .position(|&e| e > active)
        .map(|idx| (idx as i64) * CURVE_STEP_MS)
        .unwrap_or(0);
    let content_end_ms = energy
        .iter()
        .rposition(|&e| e > content_floor)
        .map(|idx| ((idx + 1) as i64) * CURVE_STEP_MS)
        .unwrap_or(duration_ms.max(0));
    let mut outro_start_ms = energy
        .iter()
        .rposition(|&e| e > active)
        .map(|idx| (idx as i64) * CURVE_STEP_MS)
        .unwrap_or(intro_end_ms);
    if outro_start_ms < intro_end_ms {
        outro_start_ms = intro_end_ms;
    }

    let mut mix_out = Vec::with_capacity(7);
    let tail_from = intro_end_ms.max(content_end_ms - 30_000);
    let mut idx = ((tail_from / CURVE_STEP_MS).max(0) as usize).min(energy.len());
    while idx + 2 < energy.len() && mix_out.len() < 5 {
        let drop = energy[idx] - energy[idx + 1];
        if drop > active * 0.8 && energy[idx + 1] < active {
            mix_out.push(((idx + 1) as i64) * CURVE_STEP_MS);
            idx += 8;
        } else {
            idx += 1;
        }
    }
    mix_out.push((content_end_ms - 12_000).max(intro_end_ms));
    mix_out.push(outro_start_ms);
    mix_out.sort_unstable();
    mix_out.dedup();

    let final_fade_onset_ms =
        detect_final_fade_onset_ms(energy, intro_end_ms, content_end_ms, CURVE_STEP_MS);

    let mut mix_in = Vec::with_capacity(6);
    let mut j = (audible_start_ms / CURVE_STEP_MS).max(0) as usize;
    let head_to = (intro_end_ms + 24_000).min(content_end_ms);
    while j + 1 < energy.len() && ((j as i64) * CURVE_STEP_MS) < head_to && mix_in.len() < 4 {
        let rise = energy[j + 1] - energy[j];
        if rise > active * 0.6 && energy[j] < active {
            mix_in.push(((j + 1) as i64) * CURVE_STEP_MS);
            j += 8;
        } else {
            j += 1;
        }
    }
    mix_in.push(intro_end_ms);
    mix_in.sort_unstable();
    mix_in.dedup();

    CurveAnalysisSummary {
        audible_start_ms,
        intro_end_ms,
        outro_start_ms,
        content_end_ms,
        final_fade_onset_ms,
        mix_in_candidates_ms: mix_in,
        mix_out_candidates_ms: mix_out,
        normalized_vocal,
    }
}

/// Downmixes interleaved signed 16-bit PCM frames into normalized mono `f32` samples.
pub fn downmix_i16_to_mono(interleaved: &[i16], channels: usize, out: &mut Vec<f32>) {
    out.clear();
    if channels == 0 || interleaved.is_empty() {
        return;
    }
    let scale = 1.0 / (32768.0 * (channels as f32));
    out.reserve(interleaved.len() / channels);
    for frame in interleaved.chunks_exact(channels) {
        let mut sum: i32 = 0;
        for &s in frame {
            sum += s as i32;
        }
        out.push((sum as f32) * scale);
    }
}

/// Downmixes interleaved `f32` PCM frames into mono `f32` samples.
pub fn downmix_f32_to_mono(interleaved: &[f32], channels: usize, out: &mut Vec<f32>) {
    out.clear();
    if channels == 0 || interleaved.is_empty() {
        return;
    }
    let inv_ch = 1.0 / (channels as f32);
    out.reserve(interleaved.len() / channels);
    for frame in interleaved.chunks_exact(channels) {
        let mut sum = 0.0f32;
        for &s in frame {
            sum += s;
        }
        out.push(sum * inv_ch);
    }
}

/// Equal-power crossfade gain pair `(outgoing, incoming)` for `progress` in `[0.0, 1.0]`.
pub fn equal_power_gains(progress: f32) -> (f32, f32) {
    let t = progress.clamp(0.0, 1.0);
    let angle = t * (PI * 0.5);
    (angle.cos(), angle.sin())
}

/// S-curve cosine crossfade gain pair `(outgoing, incoming)` matching smooth phrasing transitions.
pub fn s_curve_gains(progress: f32) -> (f32, f32) {
    let t = progress.clamp(0.0, 1.0);
    let incoming = 0.5 - 0.5 * (t * PI).cos();
    let outgoing = 1.0 - incoming;
    (outgoing, incoming)
}

/// Applies transparent soft-clipping (`tanh`-like cubic/rational knee above threshold).
pub fn soft_clip_in_place(samples: &mut [f32], threshold: f32) {
    let knee = threshold.clamp(0.25, 0.99);
    let headroom = 1.0 - knee;
    for s in samples.iter_mut() {
        let x = *s;
        let ax = x.abs();
        if ax > knee {
            let excess = (ax - knee) / headroom;
            let shaped = knee + headroom * (excess / (1.0 + excess));
            *s = shaped.copysign(x);
        }
    }
}

/// Catmull-Rom cubic resampler for mono float buffers.
pub fn resample_cubic_mono(input: &[f32], src_rate: u32, dst_rate: u32) -> Vec<f32> {
    if input.is_empty() || src_rate == 0 || dst_rate == 0 {
        return Vec::new();
    }
    if src_rate == dst_rate {
        return input.to_vec();
    }
    let ratio = (src_rate as f64) / (dst_rate as f64);
    let out_len = (((input.len() as f64) * (dst_rate as f64)) / (src_rate as f64)).round() as usize;
    let out_len = out_len.max(1);
    let last_idx = (input.len() - 1) as isize;
    let sample_at = |idx: isize| -> f32 {
        let clamped = idx.clamp(0, last_idx) as usize;
        input[clamped]
    };

    let mut out = Vec::with_capacity(out_len);
    for i in 0..out_len {
        let pos = (i as f64) * ratio;
        let base = pos.floor() as isize;
        let t = (pos - (base as f64)) as f32;
        let y0 = sample_at(base - 1);
        let y1 = sample_at(base);
        let y2 = sample_at(base + 1);
        let y3 = sample_at(base + 2);
        let a0 = -0.5 * y0 + 1.5 * y1 - 1.5 * y2 + 0.5 * y3;
        let a1 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3;
        let a2 = -0.5 * y0 + 0.5 * y2;
        let a3 = y1;
        out.push(((a0 * t + a1) * t + a2) * t + a3);
    }
    out
}

/// Computes `(peak_linear, rms_linear, peak_dbfs, rms_dbfs)` for normalized audio diagnostics.
pub fn measure_levels(samples: &[f32]) -> (f32, f32, f32, f32) {
    if samples.is_empty() {
        return (0.0, 0.0, -120.0, -120.0);
    }
    let mut peak = 0.0f32;
    let mut sum_sq = 0.0f64;
    for &s in samples {
        if s.is_finite() {
            let a = s.abs();
            if a > peak {
                peak = a;
            }
            sum_sq += (s as f64) * (s as f64);
        }
    }
    let rms = (sum_sq / (samples.len() as f64)).sqrt() as f32;
    let to_db = |amp: f32| -> f32 {
        if amp <= 1e-6 {
            -120.0
        } else {
            20.0 * amp.log10()
        }
    };
    (peak, rms, to_db(peak), to_db(rms))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hann_window_matches_reference_endpoints() {
        let w = hann_window(512);
        assert_eq!(w.len(), 512);
        assert!(w[0].abs() < 1e-6);
        assert!((w[256] - 1.0).abs() < 1e-4);
    }

    #[test]
    fn svf_parked_passes_samples_unchanged() {
        let mut proc = SvfFilterProcessor::new(2, 48_000).unwrap();
        let orig = vec![1000i16, -2000, 3000, -4000, 5000, -6000];
        let mut buf = orig.clone();
        assert!(!proc.process_i16_in_place(&mut buf));
        assert_eq!(buf, orig);
    }

    #[test]
    fn svf_lowpass_attenuates_nyquist_tone() {
        let mut proc = SvfFilterProcessor::new(1, 48_000).unwrap();
        proc.set_cutoffs(400.0, OFF_HZ);
        proc.flush();
        let mut tone: Vec<f32> = (0..2048)
            .map(|i| if i % 2 == 0 { 0.8 } else { -0.8 })
            .collect();
        assert!(proc.process_f32_in_place(&mut tone));
        let tail_rms = measure_levels(&tone[1024..]).1;
        assert!(tail_rms < 0.05, "expected lowpass attenuation, got {tail_rms}");
    }

    #[test]
    fn svf_highpass_attenuates_dc_offset() {
        let mut proc = SvfFilterProcessor::new(1, 48_000).unwrap();
        proc.set_cutoffs(OPEN_HZ, 500.0);
        proc.flush();
        let mut dc = vec![0.5f32; 4096];
        assert!(proc.process_f32_in_place(&mut dc));
        let tail_rms = measure_levels(&dc[3072..]).1;
        assert!(tail_rms < 0.02, "expected highpass DC rejection, got {tail_rms}");
    }

    #[test]
    fn curve_analysis_finds_intro_outro_and_fade() {
        let mut energy = vec![0.01f32; 160];
        for e in &mut energy[12..140] {
            *e = 0.8;
        }
        for (k, e) in energy[140..155].iter_mut().enumerate() {
            *e = 0.8 * (1.0 - (k as f32) / 15.0);
        }
        let vocal = vec![0.2f32; 160];
        let summary = analyze_energy_profile(&energy, &vocal, 40_000);
        assert_eq!(summary.intro_end_ms, 3_000);
        assert!(summary.outro_start_ms >= 34_500);
        assert!(summary.final_fade_onset_ms < 39_000);
        assert_eq!(summary.normalized_vocal.len(), 160);
    }

    #[test]
    fn equal_power_midpoint_sums_to_unit_energy() {
        let (a, b) = equal_power_gains(0.5);
        assert!(((a * a + b * b) - 1.0).abs() < 1e-5);
    }

    #[test]
    fn resample_preserves_dc_and_scales_length() {
        let input = vec![0.25f32; 441];
        let out = resample_cubic_mono(&input, 44_100, 48_000);
        assert_eq!(out.len(), 480);
        for v in out {
            assert!((v - 0.25).abs() < 1e-4);
        }
    }
}
