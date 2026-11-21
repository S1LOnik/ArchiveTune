// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

#[derive(Debug, Clone, PartialEq)]
pub struct TempoEstimate {
    pub bpm: f64,
    pub interval_ms: f64,
    pub confidence: f64,
    pub phase_ms: f64,
}

/// Estimates tempo and beat phase from a spectral flux curve.
///
/// Implements spectral flux autocorrelation with downsampling, lag searching in
/// the 60-180 BPM range, prominence confidence scoring, octave normalization,
/// and a 4-beat comb filter phase search.
pub fn estimate_tempo(flux: &[f32], flux_rate: f64) -> Option<TempoEstimate> {
    if flux.len() < 64 || !flux_rate.is_finite() || flux_rate <= 0.0 {
        return None;
    }

    // Ensure all input samples are valid numbers.
    if flux.iter().any(|v| !v.is_finite()) {
        return None;
    }

    let factor = ((flux_rate / 187.0) as i64).max(1) as usize;
    let compact_size = flux.len() / factor;
    if compact_size < 32 {
        return None;
    }

    let mut compact = Vec::with_capacity(compact_size);
    for i in 0..compact_size {
        let mut m = 0.0f32;
        let base = i * factor;
        for k in 0..factor {
            m = m.max(flux[base + k]);
        }
        compact.push(m);
    }
    let compact_rate = flux_rate / (factor as f64);

    let mut sum = 0.0f64;
    for &v in &compact {
        sum += v as f64;
    }
    let mean = (sum / (compact.len() as f64)) as f32;
    let centered: Vec<f32> = compact.iter().map(|&v| v - mean).collect();

    let mut power = 0.0f64;
    for &v in &centered {
        let d = v as f64;
        power += d * d;
    }
    if power <= 1e-9 || !power.is_finite() {
        return None;
    }

    let min_lag = (((compact_rate * 60.0 / 180.0) as i64).max(2)) as usize;
    let max_lag = (((compact_rate * 60.0 / 60.0) as i64).min((centered.len() / 2) as i64)) as usize;
    if max_lag <= min_lag {
        return None;
    }

    let mut best_lag: isize = -1;
    let mut best_score = 0.0f64;
    let mut score_sum = 0.0f64;

    for lag in min_lag..=max_lag {
        let mut dot = 0.0f64;
        let limit = centered.len() - lag;
        for t in 0..limit {
            dot += (centered[t] as f64) * (centered[t + lag] as f64);
        }
        let score = dot / power;
        score_sum += score;
        if score > best_score {
            best_score = score;
            best_lag = lag as isize;
        }
    }

    if best_lag <= 0 {
        return None;
    }
    let best_lag = best_lag as usize;

    let count = (max_lag - min_lag + 1) as f64;
    let avg = score_sum / count;
    let prominence = (best_score - avg) / (best_score.abs() + avg.abs() + 1e-9);
    let confidence = (prominence * 2.0).clamp(0.0, 1.0);

    let raw_interval_ms = 1000.0 * (best_lag as f64) / compact_rate;
    if raw_interval_ms <= 0.0 || !raw_interval_ms.is_finite() {
        return None;
    }
    let mut bpm = 60_000.0 / raw_interval_ms;
    while bpm < 70.0 {
        bpm *= 2.0;
    }
    while bpm > 180.0 {
        bpm /= 2.0;
    }

    let bar = best_lag * 4;
    let mut best_phase = 0usize;
    let mut best_comb = -1.0f64;

    for phase in 0..bar {
        let mut comb = 0.0f64;
        let mut t = phase;
        while t < centered.len() {
            comb += centered[t] as f64;
            t += bar;
        }
        if comb > best_comb {
            best_comb = comb;
            best_phase = phase;
        }
    }

    let phase_ms = 1000.0 * (best_phase as f64) / compact_rate;

    Some(TempoEstimate {
        bpm,
        interval_ms: 60_000.0 / bpm,
        confidence,
        phase_ms,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    // Numerical tolerance explanation:
    // Floating-point arithmetic between Kotlin (JVM HotSpot x86_64) and Rust (LLVM x86_64)
    // uses IEEE 754 32-bit floats for input and centered values, and 64-bit doubles for sums.
    // Due to possible FMA contraction or compiler instruction scheduling, differences are bounded by 1e-7.
    const FLOAT_TOLERANCE: f64 = 1e-7;

    fn assert_close(actual: f64, expected: f64, label: &str) {
        assert!(
            (actual - expected).abs() <= FLOAT_TOLERANCE,
            "{label}: actual {actual} != expected {expected} (diff {})",
            (actual - expected).abs()
        );
    }

    #[test]
    fn silence_returns_none() {
        let silence = vec![0.0f32; 128];
        assert_eq!(estimate_tempo(&silence, 100.0), None);
    }

    #[test]
    fn fewer_than_64_returns_none() {
        let small = vec![1.0f32; 63];
        assert_eq!(estimate_tempo(&small, 100.0), None);
    }

    #[test]
    fn invalid_rates_or_samples_return_none() {
        assert_eq!(estimate_tempo(&[1.0f32; 100], 0.0), None);
        assert_eq!(estimate_tempo(&[1.0f32; 100], -10.0), None);
        assert_eq!(estimate_tempo(&[1.0f32; 100], f64::NAN), None);

        let mut with_nan = vec![0.0f32; 100];
        with_nan[10] = f32::NAN;
        assert_eq!(estimate_tempo(&with_nan, 100.0), None);
    }

    #[test]
    fn regular_pulses_match_baseline() {
        let flux: Vec<f32> = (0..500)
            .map(|i| if i % 50 == 0 { 1.0f32 } else { 0.0f32 })
            .collect();
        let estimate = estimate_tempo(&flux, 100.0).expect("regular pulses should estimate tempo");
        assert_close(estimate.bpm, 120.0, "bpm");
        assert_close(estimate.interval_ms, 500.0, "interval_ms");
        assert_close(estimate.confidence, 1.0, "confidence");
        assert_close(estimate.phase_ms, 0.0, "phase_ms");
    }

    #[test]
    fn phase_shift_matches_baseline() {
        let flux: Vec<f32> = (0..500)
            .map(|i| {
                if (i + 500 - 15) % 50 == 0 {
                    1.0f32
                } else {
                    0.0f32
                }
            })
            .collect();
        let estimate =
            estimate_tempo(&flux, 100.0).expect("phase shifted pulses should estimate tempo");
        assert_close(estimate.bpm, 120.0, "bpm");
        assert_close(estimate.interval_ms, 500.0, "interval_ms");
        assert_close(estimate.confidence, 1.0, "confidence");
        assert_close(estimate.phase_ms, 150.0, "phase_ms");
    }

    #[test]
    fn half_double_ambiguity_matches_baseline() {
        let flux: Vec<f32> = (0..600)
            .map(|i| {
                if i % 40 == 0 {
                    1.0f32
                } else if i % 20 == 0 {
                    0.5f32
                } else {
                    0.0f32
                }
            })
            .collect();
        let estimate = estimate_tempo(&flux, 100.0).expect("ambiguous pulse should estimate tempo");
        assert_close(estimate.bpm, 150.0, "bpm");
        assert_close(estimate.interval_ms, 400.0, "interval_ms");
        assert_close(estimate.confidence, 1.0, "confidence");
        assert_close(estimate.phase_ms, 0.0, "phase_ms");
    }

    #[test]
    fn long_array_matches_baseline() {
        let flux: Vec<f32> = (0..2500)
            .map(|i| (if i % 45 == 0 { 1.0f32 } else { 0.05f32 }) + (i % 7) as f32 * 0.01f32)
            .collect();
        let estimate = estimate_tempo(&flux, 100.0).expect("long array should estimate tempo");
        assert_close(estimate.bpm, 133.33333333333334, "bpm");
        assert_close(estimate.interval_ms, 449.99999999999994, "interval_ms");
        assert_close(estimate.confidence, 1.0, "confidence");
        assert_close(estimate.phase_ms, 0.0, "phase_ms");
    }

    #[test]
    fn decimation_rate_matches_baseline() {
        let flux: Vec<f32> = (0..800)
            .map(|i| if i % 80 == 0 { 1.0f32 } else { 0.0f32 })
            .collect();
        let estimate = estimate_tempo(&flux, 400.0).expect("decimated array should estimate tempo");
        assert_close(estimate.bpm, 150.0, "bpm");
        assert_close(estimate.interval_ms, 400.0, "interval_ms");
        assert_close(estimate.confidence, 1.0, "confidence");
        assert_close(estimate.phase_ms, 0.0, "phase_ms");
    }
}
