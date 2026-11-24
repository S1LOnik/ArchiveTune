// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

/// Real-time soft-knee feed-forward RMS/Peak compressor and makeup limiter.
#[derive(Debug, Clone)]
pub struct DynamicsCompressor {
    sample_rate: f32,
    threshold_db: f32,
    ratio: f32,
    knee_db: f32,
    attack_coeff: f32,
    release_coeff: f32,
    makeup_linear: f32,
    envelope_db: f32,
}

impl DynamicsCompressor {
    pub fn new(
        sample_rate: i32,
        threshold_db: f32,
        ratio: f32,
        knee_db: f32,
        attack_ms: f32,
        release_ms: f32,
        makeup_db: f32,
    ) -> Self {
        let sr = (sample_rate.max(8_000)) as f32;
        let atk_s = (attack_ms.clamp(0.1, 500.0)) * 0.001;
        let rel_s = (release_ms.clamp(5.0, 2_000.0)) * 0.001;
        Self {
            sample_rate: sr,
            threshold_db: threshold_db.clamp(-60.0, 0.0),
            ratio: ratio.clamp(1.0, 20.0),
            knee_db: knee_db.clamp(0.0, 24.0),
            attack_coeff: (-1.0 / (atk_s * sr)).exp(),
            release_coeff: (-1.0 / (rel_s * sr)).exp(),
            makeup_linear: 10.0_f32.powf(makeup_db.clamp(-12.0, 24.0) / 20.0),
            envelope_db: -96.0,
        }
    }

    pub fn sample_rate(&self) -> f32 {
        self.sample_rate
    }

    pub fn reset(&mut self) {
        self.envelope_db = -96.0;
    }

    #[inline]
    fn gain_reduction_db(&self, input_db: f32) -> f32 {
        let half_knee = self.knee_db * 0.5;
        let over = input_db - self.threshold_db;
        let slope = 1.0 - (1.0 / self.ratio);
        if self.knee_db > 0.01 && over.abs() <= half_knee {
            let x = over + half_knee;
            (slope * x * x) / (2.0 * self.knee_db)
        } else if over > 0.0 {
            slope * over
        } else {
            0.0
        }
    }

    /// Processes interleaved normalized `f32` PCM samples in-place across linked stereo/multi channels.
    pub fn process_in_place(&mut self, samples: &mut [f32], channels: usize) {
        if samples.is_empty() || channels == 0 {
            return;
        }
        for frame in samples.chunks_exact_mut(channels) {
            let mut linked_peak = 0.0f32;
            for &s in frame.iter() {
                if s.is_finite() {
                    let a = s.abs();
                    if a > linked_peak {
                        linked_peak = a;
                    }
                }
            }
            let level_db = if linked_peak <= 1e-5 {
                -100.0
            } else {
                20.0 * linked_peak.log10()
            };
            let coeff = if level_db > self.envelope_db {
                self.attack_coeff
            } else {
                self.release_coeff
            };
            self.envelope_db = coeff * self.envelope_db + (1.0 - coeff) * level_db;

            let gr_db = self.gain_reduction_db(self.envelope_db);
            let gain = 10.0_f32.powf(-gr_db / 20.0) * self.makeup_linear;
            for s in frame.iter_mut() {
                let y = if s.is_finite() { *s * gain } else { 0.0 };
                *s = y.clamp(-1.0, 1.0);
            }
        }
    }
}

/// Streaming ReplayGain / track & album gain scanner accumulating block energies across chunks.
#[derive(Debug, Default, Clone)]
pub struct ReplayGainAccumulator {
    total_samples: u64,
    sum_squares: f64,
    max_peak: f32,
}

impl ReplayGainAccumulator {
    pub fn push_samples(&mut self, samples: &[f32]) {
        for &s in samples {
            if s.is_finite() {
                let a = s.abs();
                if a > self.max_peak {
                    self.max_peak = a;
                }
                self.sum_squares += (s as f64) * (s as f64);
                self.total_samples += 1;
            }
        }
    }

    pub fn reset(&mut self) {
        self.total_samples = 0;
        self.sum_squares = 0.0;
        self.max_peak = 0.0;
    }

    /// Computes `(recommended_gain_db, peak_linear, estimated_lufs)` targeting `target_lufs`.
    pub fn finalize(&self, target_lufs: f64) -> (f64, f32, f64) {
        if self.total_samples == 0 || self.sum_squares <= 1e-12 {
            return (0.0, 0.0, -70.0);
        }
        let mean_sq = self.sum_squares / (self.total_samples as f64);
        let approx_lufs = -0.691 + 10.0 * mean_sq.max(1e-12).log10();
        let mut gain_db = (target_lufs - approx_lufs).clamp(-18.0, 12.0);
        if self.max_peak > 1e-6 {
            let max_headroom_db = -20.0 * (self.max_peak as f64).log10();
            if gain_db > max_headroom_db {
                gain_db = max_headroom_db;
            }
        }
        (gain_db, self.max_peak, approx_lufs)
    }
}

/// Time-domain WSOLA (Waveform Similarity Overlap-Add) pitch-preserving tempo scaler for mono PCM.
pub fn time_stretch_wsola_mono(input: &[f32], sample_rate: i32, tempo: f32) -> Vec<f32> {
    if input.is_empty() || sample_rate <= 0 {
        return Vec::new();
    }
    let ratio = tempo.clamp(0.5, 2.0);
    if (ratio - 1.0).abs() < 1e-3 {
        return input.to_vec();
    }
    let win_len = (((sample_rate as f32) * 0.04).round() as usize).clamp(256, 4096) & !1usize;
    let half_win = win_len / 2;
    let search_tol = (half_win / 2).max(16);
    if input.len() <= win_len + search_tol {
        return input.to_vec();
    }

    let expected_len = ((input.len() as f32) / ratio).round().max(1.0) as usize;
    let mut out = vec![0.0f32; expected_len + win_len];
    let mut norm = vec![0.0f32; expected_len + win_len];
    let window: Vec<f32> = (0..win_len)
        .map(|i| {
            let phase = std::f32::consts::PI * (i as f32) / (win_len as f32);
            phase.sin().powi(2)
        })
        .collect();

    let mut out_pos = 0usize;
    let mut in_pos = 0usize;
    let syn_hop = half_win;
    let ana_hop = ((syn_hop as f32) * ratio).round().max(1.0) as usize;

    // Seed first frame cleanly
    for i in 0..win_len {
        out[i] += input[i] * window[i];
        norm[i] += window[i];
    }
    out_pos += syn_hop;
    in_pos += ana_hop;

    while in_pos + win_len <= input.len() && out_pos + win_len <= out.len() {
        let min_cand = in_pos.saturating_sub(search_tol);
        let max_cand = (in_pos + search_tol).min(input.len() - win_len);
        let mut best_offset = in_pos;
        let mut best_corr = f32::NEG_INFINITY;

        // Cross-correlate overlap tail in output against candidate head
        for cand in (min_cand..=max_cand).step_by(2) {
            let mut dot = 0.0f32;
            for k in (0..half_win).step_by(2) {
                dot += out[out_pos + k] * input[cand + k];
            }
            if dot > best_corr {
                best_corr = dot;
                best_offset = cand;
            }
        }

        for i in 0..win_len {
            let w = window[i];
            out[out_pos + i] += input[best_offset + i] * w;
            norm[out_pos + i] += w;
        }
        out_pos += syn_hop;
        in_pos += ana_hop;
    }

    let final_len = out_pos.min(expected_len);
    for i in 0..final_len {
        if norm[i] > 1e-4 {
            out[i] /= norm[i];
        }
    }
    out.truncate(final_len);
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::f32::consts::TAU;

    #[test]
    fn compressor_attenuates_peaks_above_threshold() {
        let mut comp = DynamicsCompressor::new(48_000, -12.0, 4.0, 3.0, 1.0, 50.0, 0.0);
        let mut loud = vec![0.9f32; 4800];
        comp.process_in_place(&mut loud, 1);
        assert!(loud[4799] < 0.7);
    }

    #[test]
    fn wsola_stretches_length_proportionally() {
        let tone: Vec<f32> = (0..24_000)
            .map(|i| 0.5 * (TAU * 220.0 * (i as f32) / 48_000.0).sin())
            .collect();
        let faster = time_stretch_wsola_mono(&tone, 48_000, 1.5);
        assert!(faster.len() >= 14_000 && faster.len() <= 17_000);
        let slower = time_stretch_wsola_mono(&tone, 48_000, 0.75);
        assert!(slower.len() >= 29_000 && slower.len() <= 33_000);
    }

    #[test]
    fn replay_gain_caps_gain_to_prevent_clipping() {
        let mut acc = ReplayGainAccumulator::default();
        acc.push_samples(&[0.95f32, -0.95f32, 0.01f32, 0.01f32]);
        let (gain_db, peak, _) = acc.finalize(-5.0);
        assert!((peak - 0.95).abs() < 1e-5);
        assert!(gain_db <= 0.5);
    }
}
