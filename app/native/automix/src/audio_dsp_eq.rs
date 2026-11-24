// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::f64::consts::PI;

/// Biquad filter topology implemented via RBJ Audio EQ Cookbook bilinear transform.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BiquadKind {
    Peaking = 0,
    LowShelf = 1,
    HighShelf = 2,
    LowPass = 3,
    HighPass = 4,
    BandPass = 5,
    Notch = 6,
}

impl BiquadKind {
    pub fn from_i32(raw: i32) -> Self {
        match raw {
            1 => Self::LowShelf,
            2 => Self::HighShelf,
            3 => Self::LowPass,
            4 => Self::HighPass,
            5 => Self::BandPass,
            6 => Self::Notch,
            _ => Self::Peaking,
        }
    }
}

/// Normalized Transposed Direct Form II biquad coefficients for single-channel processing.
#[derive(Debug, Clone, Copy)]
pub struct BiquadCoeffs {
    pub b0: f32,
    pub b1: f32,
    pub b2: f32,
    pub a1: f32,
    pub a2: f32,
}

impl Default for BiquadCoeffs {
    fn default() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
        }
    }
}

impl BiquadCoeffs {
    pub fn design(
        kind: BiquadKind,
        sample_rate: f64,
        freq_hz: f64,
        q: f64,
        gain_db: f64,
    ) -> Self {
        if sample_rate <= 0.0 || freq_hz <= 0.0 || q <= 0.0 {
            return Self::default();
        }
        let nyquist = sample_rate * 0.49;
        let f0 = freq_hz.clamp(10.0, nyquist);
        let safe_q = q.clamp(0.1, 24.0);
        let clamped_gain = gain_db.clamp(-24.0, 24.0);
        if matches!(
            kind,
            BiquadKind::Peaking | BiquadKind::LowShelf | BiquadKind::HighShelf
        ) && clamped_gain.abs() < 1e-4
        {
            return Self::default();
        }

        let w0 = 2.0 * PI * f0 / sample_rate;
        let cos_w0 = w0.cos();
        let sin_w0 = w0.sin();
        let alpha = sin_w0 / (2.0 * safe_q);
        let a = 10.0_f64.powf(clamped_gain / 40.0);

        let (b0, b1, b2, a0, a1, a2) = match kind {
            BiquadKind::Peaking => (
                1.0 + alpha * a,
                -2.0 * cos_w0,
                1.0 - alpha * a,
                1.0 + alpha / a,
                -2.0 * cos_w0,
                1.0 - alpha / a,
            ),
            BiquadKind::LowShelf => {
                let beta = 2.0 * a.sqrt() * alpha;
                (
                    a * ((a + 1.0) - (a - 1.0) * cos_w0 + beta),
                    2.0 * a * ((a - 1.0) - (a + 1.0) * cos_w0),
                    a * ((a + 1.0) - (a - 1.0) * cos_w0 - beta),
                    (a + 1.0) + (a - 1.0) * cos_w0 + beta,
                    -2.0 * ((a - 1.0) + (a + 1.0) * cos_w0),
                    (a + 1.0) + (a - 1.0) * cos_w0 - beta,
                )
            }
            BiquadKind::HighShelf => {
                let beta = 2.0 * a.sqrt() * alpha;
                (
                    a * ((a + 1.0) + (a - 1.0) * cos_w0 + beta),
                    -2.0 * a * ((a - 1.0) + (a + 1.0) * cos_w0),
                    a * ((a + 1.0) + (a - 1.0) * cos_w0 - beta),
                    (a + 1.0) - (a - 1.0) * cos_w0 + beta,
                    2.0 * ((a - 1.0) - (a + 1.0) * cos_w0),
                    (a + 1.0) - (a - 1.0) * cos_w0 - beta,
                )
            }
            BiquadKind::LowPass => (
                (1.0 - cos_w0) * 0.5,
                1.0 - cos_w0,
                (1.0 - cos_w0) * 0.5,
                1.0 + alpha,
                -2.0 * cos_w0,
                1.0 - alpha,
            ),
            BiquadKind::HighPass => (
                (1.0 + cos_w0) * 0.5,
                -(1.0 + cos_w0),
                (1.0 + cos_w0) * 0.5,
                1.0 + alpha,
                -2.0 * cos_w0,
                1.0 - alpha,
            ),
            BiquadKind::BandPass => (alpha, 0.0, -alpha, 1.0 + alpha, -2.0 * cos_w0, 1.0 - alpha),
            BiquadKind::Notch => (
                1.0,
                -2.0 * cos_w0,
                1.0,
                1.0 + alpha,
                -2.0 * cos_w0,
                1.0 - alpha,
            ),
        };

        if a0.abs() < 1e-12 {
            return Self::default();
        }
        let inv = 1.0 / a0;
        Self {
            b0: (b0 * inv) as f32,
            b1: (b1 * inv) as f32,
            b2: (b2 * inv) as f32,
            a1: (a1 * inv) as f32,
            a2: (a2 * inv) as f32,
        }
    }

    pub fn magnitude_db(&self, sample_rate: f64, freq_hz: f64) -> f64 {
        if sample_rate <= 0.0 || freq_hz <= 0.0 {
            return 0.0;
        }
        let w = 2.0 * PI * freq_hz / sample_rate;
        let cw = w.cos();
        let sw = w.sin();
        let c2w = (2.0 * w).cos();
        let s2w = (2.0 * w).sin();

        let num_re = (self.b0 as f64) + (self.b1 as f64) * cw + (self.b2 as f64) * c2w;
        let num_im = -(self.b1 as f64) * sw - (self.b2 as f64) * s2w;
        let den_re = 1.0 + (self.a1 as f64) * cw + (self.a2 as f64) * c2w;
        let den_im = -(self.a1 as f64) * sw - (self.a2 as f64) * s2w;

        let num_sq = num_re * num_re + num_im * num_im;
        let den_sq = (den_re * den_re + den_im * den_im).max(1e-18);
        10.0 * (num_sq / den_sq).max(1e-18).log10()
    }
}

#[derive(Debug, Clone, Default)]
struct BiquadChannelState {
    z1: f32,
    z2: f32,
}

impl BiquadChannelState {
    #[inline]
    fn step(&mut self, input: f32, c: BiquadCoeffs) -> f32 {
        let out = c.b0 * input + self.z1;
        self.z1 = c.b1 * input - c.a1 * out + self.z2;
        self.z2 = c.b2 * input - c.a2 * out;
        if out.is_finite() { out } else { 0.0 }
    }

    fn reset(&mut self) {
        self.z1 = 0.0;
        self.z2 = 0.0;
    }
}

#[derive(Debug, Clone)]
struct EqBand {
    kind: BiquadKind,
    freq_hz: f64,
    q: f64,
    gain_db: f64,
    enabled: bool,
    coeffs: BiquadCoeffs,
    states: Vec<BiquadChannelState>,
}

/// Multi-band parametric equalizer + preamp + soft limiter for ArchiveTune native audio DSP.
#[derive(Debug, Clone)]
pub struct ParametricEqBank {
    sample_rate: f64,
    channels: usize,
    preamp_linear: f32,
    limiter_enabled: bool,
    limiter_threshold: f32,
    bands: Vec<EqBand>,
}

impl ParametricEqBank {
    pub fn new(sample_rate: i32, channels: usize, band_count: usize) -> Result<Self, &'static str> {
        if !(8_000..=768_000).contains(&sample_rate) {
            return Err("invalid sample rate");
        }
        if channels == 0 || channels > 16 {
            return Err("invalid channel count");
        }
        if band_count == 0 || band_count > 64 {
            return Err("invalid band count");
        }
        let default_freqs = default_eq_frequencies(band_count);
        let mut bands = Vec::with_capacity(band_count);
        for (idx, freq) in default_freqs.into_iter().enumerate() {
            let kind = if idx == 0 && band_count >= 5 {
                BiquadKind::LowShelf
            } else if idx + 1 == band_count && band_count >= 5 {
                BiquadKind::HighShelf
            } else {
                BiquadKind::Peaking
            };
            bands.push(EqBand {
                kind,
                freq_hz: freq,
                q: 1.414,
                gain_db: 0.0,
                enabled: false,
                coeffs: BiquadCoeffs::default(),
                states: vec![BiquadChannelState::default(); channels],
            });
        }
        Ok(Self {
            sample_rate: sample_rate as f64,
            channels,
            preamp_linear: 1.0,
            limiter_enabled: true,
            limiter_threshold: 0.985,
            bands,
        })
    }

    pub fn set_preamp_db(&mut self, preamp_db: f32) {
        let clamped = preamp_db.clamp(-24.0, 24.0);
        self.preamp_linear = 10.0_f32.powf(clamped / 20.0);
    }

    pub fn set_band(
        &mut self,
        index: usize,
        kind: BiquadKind,
        freq_hz: f64,
        q: f64,
        gain_db: f64,
        enabled: bool,
    ) -> bool {
        let Some(band) = self.bands.get_mut(index) else {
            return false;
        };
        band.kind = kind;
        band.freq_hz = freq_hz;
        band.q = q;
        band.gain_db = gain_db;
        band.enabled = enabled && (!matches!(
            kind,
            BiquadKind::Peaking | BiquadKind::LowShelf | BiquadKind::HighShelf
        ) || gain_db.abs() >= 1e-4);
        band.coeffs = if band.enabled {
            BiquadCoeffs::design(kind, self.sample_rate, freq_hz, q, gain_db)
        } else {
            BiquadCoeffs::default()
        };
        true
    }

    pub fn flush(&mut self) {
        for band in &mut self.bands {
            for st in &mut band.states {
                st.reset();
            }
        }
    }

    pub fn frequency_response_db(&self, freq_hz: f64) -> f64 {
        let mut db = 20.0 * (self.preamp_linear as f64).max(1e-6).log10();
        for band in &self.bands {
            if band.enabled {
                db += band.coeffs.magnitude_db(self.sample_rate, freq_hz);
            }
        }
        db
    }

    pub fn has_active_processing(&self) -> bool {
        (self.preamp_linear - 1.0).abs() >= 1e-4 || self.bands.iter().any(|b| b.enabled)
    }

    pub fn process_f32_in_place(&mut self, samples: &mut [f32]) -> bool {
        if !self.has_active_processing() || samples.is_empty() {
            return false;
        }
        let channels = self.channels;
        let preamp = self.preamp_linear;
        let limit = self.limiter_threshold;
        let use_limiter = self.limiter_enabled;

        for frame in samples.chunks_exact_mut(channels) {
            for (ch, sample_ref) in frame.iter_mut().enumerate() {
                let mut x = *sample_ref * preamp;
                for band in &mut self.bands {
                    if band.enabled {
                        x = band.states[ch].step(x, band.coeffs);
                    }
                }
                if use_limiter && x.abs() > limit {
                    let excess = (x.abs() - limit) / (1.0 - limit).max(0.01);
                    let shaped = limit + (1.0 - limit) * (excess / (1.0 + excess));
                    x = shaped.copysign(x);
                }
                *sample_ref = if x.is_finite() { x } else { 0.0 };
            }
        }
        true
    }

    pub fn process_i16_in_place(&mut self, samples: &mut [i16]) -> bool {
        if !self.has_active_processing() || samples.is_empty() {
            return false;
        }
        let channels = self.channels;
        let preamp = self.preamp_linear;
        let limit = self.limiter_threshold;
        let use_limiter = self.limiter_enabled;

        for frame in samples.chunks_exact_mut(channels) {
            for (ch, sample_ref) in frame.iter_mut().enumerate() {
                let mut x = (*sample_ref as f32 / 32768.0) * preamp;
                for band in &mut self.bands {
                    if band.enabled {
                        x = band.states[ch].step(x, band.coeffs);
                    }
                }
                if use_limiter && x.abs() > limit {
                    let excess = (x.abs() - limit) / (1.0 - limit).max(0.01);
                    let shaped = limit + (1.0 - limit) * (excess / (1.0 + excess));
                    x = shaped.copysign(x);
                }
                let q = (x.clamp(-1.0, 1.0) * 32767.0).round() as i32;
                *sample_ref = q.clamp(i16::MIN as i32, i16::MAX as i32) as i16;
            }
        }
        true
    }
}

fn default_eq_frequencies(band_count: usize) -> Vec<f64> {
    if band_count == 10 {
        return vec![
            31.25, 62.5, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0,
        ];
    }
    if band_count == 5 {
        return vec![60.0, 230.0, 910.0, 3_600.0, 14_000.0];
    }
    let min_log = 31.25_f64.ln();
    let max_log = 16_000.0_f64.ln();
    (0..band_count)
        .map(|i| {
            let t = if band_count == 1 {
                0.5
            } else {
                (i as f64) / ((band_count - 1) as f64)
            };
            (min_log + t * (max_log - min_log)).exp()
        })
        .collect()
}

/// Applies Bauer stereophonic-to-binaural headphone crossfeed across interleaved stereo PCM.
pub fn apply_stereo_crossfeed_f32(samples: &mut [f32], level: f32) {
    let amt = level.clamp(0.0, 0.5);
    if amt <= 1e-4 || samples.len() < 2 {
        return;
    }
    let direct = 1.0 - amt * 0.5;
    let cross = amt * 0.5;
    for pair in samples.chunks_exact_mut(2) {
        let l = pair[0];
        let r = pair[1];
        pair[0] = l * direct + r * cross;
        pair[1] = r * direct + l * cross;
    }
}

/// Adjusts stereo width (`0.0` = mono, `1.0` = unchanged, up to `2.0` = widened).
pub fn apply_stereo_width_f32(samples: &mut [f32], width: f32) {
    let w = width.clamp(0.0, 2.0);
    if (w - 1.0).abs() < 1e-4 || samples.len() < 2 {
        return;
    }
    for pair in samples.chunks_exact_mut(2) {
        let mid = 0.5 * (pair[0] + pair[1]);
        let side = 0.5 * (pair[0] - pair[1]) * w;
        pair[0] = (mid + side).clamp(-1.0, 1.0);
        pair[1] = (mid - side).clamp(-1.0, 1.0);
    }
}

/// Estimates integrated EBU R128 / ITU-R BS.1770-4 loudness (LUFS) with K-weighting filters
/// for mono or stereo normalized `f32` PCM buffers.
pub fn estimate_integrated_lufs(interleaved: &[f32], sample_rate: i32, channels: usize) -> f64 {
    if interleaved.is_empty() || sample_rate <= 0 || channels == 0 {
        return -70.0;
    }
    let sr = sample_rate as f64;
    // Stage 1: +4 dB high-shelf boost around 1681 Hz; Stage 2: 38 Hz high-pass
    let shelf = BiquadCoeffs::design(BiquadKind::HighShelf, sr, 1681.97, 0.7071, 4.0);
    let hpf = BiquadCoeffs::design(BiquadKind::HighPass, sr, 38.135, 0.5003, 0.0);

    let active_ch = channels.min(2);
    let mut s1 = vec![BiquadChannelState::default(); active_ch];
    let mut s2 = vec![BiquadChannelState::default(); active_ch];

    let block_frames = ((sr * 0.4).round() as usize).max(1);
    let hop_frames = (block_frames / 4).max(1);

    let mut filtered_squares = Vec::with_capacity(interleaved.len() / channels);
    for frame in interleaved.chunks_exact(channels) {
        let mut energy = 0.0f64;
        for ch in 0..active_ch {
            let y1 = s1[ch].step(frame[ch], shelf);
            let y2 = s2[ch].step(y1, hpf) as f64;
            energy += y2 * y2;
        }
        filtered_squares.push(energy);
    }

    if filtered_squares.len() < block_frames {
        let mean: f64 =
            filtered_squares.iter().sum::<f64>() / (filtered_squares.len().max(1) as f64);
        if mean <= 1e-12 {
            return -70.0;
        }
        return -0.691 + 10.0 * mean.log10();
    }

    let mut block_powers = Vec::new();
    let mut win_sum: f64 = filtered_squares[..block_frames].iter().sum();
    let mut pos = 0usize;
    loop {
        let z = win_sum / (block_frames as f64);
        let l_j = if z > 1e-12 {
            -0.691 + 10.0 * z.log10()
        } else {
            -70.0
        };
        if l_j > -70.0 {
            block_powers.push(z);
        }
        let next_pos = pos + hop_frames;
        if next_pos + block_frames > filtered_squares.len() {
            break;
        }
        for i in pos..next_pos {
            win_sum -= filtered_squares[i];
            win_sum += filtered_squares[i + block_frames];
        }
        pos = next_pos;
    }

    if block_powers.is_empty() {
        return -70.0;
    }
    let abs_mean: f64 = block_powers.iter().sum::<f64>() / (block_powers.len() as f64);
    let abs_lufs = -0.691 + 10.0 * abs_mean.max(1e-12).log10();
    let rel_threshold = 10.0_f64.powf((abs_lufs - 10.0 + 0.691) / 10.0);

    let gated: Vec<f64> = block_powers
        .into_iter()
        .filter(|&p| p >= rel_threshold)
        .collect();
    if gated.is_empty() {
        return -70.0;
    }
    let final_mean = gated.iter().sum::<f64>() / (gated.len() as f64);
    -0.691 + 10.0 * final_mean.max(1e-12).log10()
}

/// Computes ReplayGain adjustment in dB to reach target LUFS (default -14.0 LUFS).
pub fn replay_gain_db_for_target(measured_lufs: f64, target_lufs: f64) -> f64 {
    if !measured_lufs.is_finite() || measured_lufs <= -69.0 {
        return 0.0;
    }
    (target_lufs - measured_lufs).clamp(-18.0, 12.0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::f32::consts::PI as F32_PI;

    #[test]
    fn flat_eq_bypasses_and_boosted_eq_increases_target_band() {
        let mut eq = ParametricEqBank::new(48_000, 1, 10).unwrap();
        assert!(!eq.has_active_processing());
        let mut flat = vec![0.25f32; 64];
        assert!(!eq.process_f32_in_place(&mut flat));

        assert!(eq.set_band(5, BiquadKind::Peaking, 1_000.0, 1.4, 6.0, true));
        assert!(eq.has_active_processing());
        let resp = eq.frequency_response_db(1_000.0);
        assert!((resp - 6.0).abs() < 0.25, "expected ~+6 dB at 1 kHz, got {resp}");

        let mut sine: Vec<f32> = (0..4800)
            .map(|i| 0.2 * (2.0 * F32_PI * 1_000.0 * (i as f32) / 48_000.0).sin())
            .collect();
        assert!(eq.process_f32_in_place(&mut sine));
        let rms = (sine[2400..].iter().map(|s| s * s).sum::<f32>() / 2400.0).sqrt();
        assert!(rms > 0.22);
    }

    #[test]
    fn stereo_width_zero_produces_mono_channels() {
        let mut stereo = vec![0.8f32, -0.2, 0.4, 0.0];
        apply_stereo_width_f32(&mut stereo, 0.0);
        assert!((stereo[0] - stereo[1]).abs() < 1e-6);
        assert!((stereo[0] - 0.3).abs() < 1e-6);
    }

    #[test]
    fn lufs_estimator_scales_with_sine_amplitude() {
        let tone_loud: Vec<f32> = (0..48_000)
            .map(|i| 0.5 * (2.0 * F32_PI * 1_000.0 * (i as f32) / 48_000.0).sin())
            .collect();
        let tone_quiet: Vec<f32> = tone_loud.iter().map(|s| s * 0.25).collect();
        let l_loud = estimate_integrated_lufs(&tone_loud, 48_000, 1);
        let l_quiet = estimate_integrated_lufs(&tone_quiet, 48_000, 1);
        assert!((l_loud - l_quiet - 12.04).abs() < 0.5);
    }
}
