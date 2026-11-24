// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

/// Extracted silence/trim boundary timestamps in sample frames and RMS envelope stats.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AudioEnvelopeReport {
    pub total_frames: usize,
    pub leading_silence_frames: usize,
    pub trailing_silence_frames: usize,
    pub peak_linear: f32,
    pub rms_linear: f32,
    pub crest_factor_db: f32,
    pub stereo_correlation: f32,
}

/// Computes normalized waveform amplitude bars (`0.0..=1.0`) from interleaved `i16` PCM
/// across `bucket_count` visual bars for player seekbar / scrub preview rendering.
pub fn compute_waveform_peaks_i16(
    samples: &[i16],
    channels: usize,
    bucket_count: usize,
) -> Vec<f32> {
    if samples.is_empty() || channels == 0 || bucket_count == 0 {
        return Vec::new();
    }
    let total_frames = samples.len() / channels;
    if total_frames == 0 {
        return Vec::new();
    }
    let mut peaks = vec![0.0f32; bucket_count];
    for (bucket_idx, out_peak) in peaks.iter_mut().enumerate() {
        let start_frame = (bucket_idx * total_frames) / bucket_count;
        let end_frame = (((bucket_idx + 1) * total_frames) / bucket_count).max(start_frame + 1);
        let clamped_end = end_frame.min(total_frames);
        let mut max_abs = 0i32;
        for f in start_frame..clamped_end {
            let base = f * channels;
            for ch in 0..channels {
                let abs_val = (samples[base + ch] as i32).abs();
                if abs_val > max_abs {
                    max_abs = abs_val;
                }
            }
        }
        *out_peak = ((max_abs as f32) / 32768.0).clamp(0.0, 1.0);
    }
    smooth_and_normalize_peaks(&mut peaks);
    peaks
}

/// Computes normalized waveform amplitude bars (`0.0..=1.0`) from interleaved `f32` PCM.
pub fn compute_waveform_peaks_f32(
    samples: &[f32],
    channels: usize,
    bucket_count: usize,
) -> Vec<f32> {
    if samples.is_empty() || channels == 0 || bucket_count == 0 {
        return Vec::new();
    }
    let total_frames = samples.len() / channels;
    if total_frames == 0 {
        return Vec::new();
    }
    let mut peaks = vec![0.0f32; bucket_count];
    for (bucket_idx, out_peak) in peaks.iter_mut().enumerate() {
        let start_frame = (bucket_idx * total_frames) / bucket_count;
        let end_frame = (((bucket_idx + 1) * total_frames) / bucket_count).max(start_frame + 1);
        let clamped_end = end_frame.min(total_frames);
        let mut max_abs = 0.0f32;
        for f in start_frame..clamped_end {
            let base = f * channels;
            for ch in 0..channels {
                let s = samples[base + ch];
                if s.is_finite() {
                    let a = s.abs();
                    if a > max_abs {
                        max_abs = a;
                    }
                }
            }
        }
        *out_peak = max_abs.clamp(0.0, 1.0);
    }
    smooth_and_normalize_peaks(&mut peaks);
    peaks
}

/// Downsamples an existing waveform peak array into `target_buckets` preserving local maxima.
pub fn downsample_peaks(source: &[f32], target_buckets: usize) -> Vec<f32> {
    if source.is_empty() || target_buckets == 0 {
        return Vec::new();
    }
    if source.len() <= target_buckets {
        return source.iter().map(|v| v.clamp(0.0, 1.0)).collect();
    }
    let mut out = vec![0.0f32; target_buckets];
    for (idx, peak_ref) in out.iter_mut().enumerate() {
        let start = (idx * source.len()) / target_buckets;
        let end = (((idx + 1) * source.len()) / target_buckets)
            .max(start + 1)
            .min(source.len());
        let mut best = 0.0f32;
        for &v in &source[start..end] {
            if v.is_finite() && v > best {
                best = v;
            }
        }
        *peak_ref = best.clamp(0.0, 1.0);
    }
    out
}

/// Applies gentle perceptual log-compression to raw peak heights so quiet dynamics stay visible.
pub fn smooth_and_normalize_peaks(peaks: &mut [f32]) {
    if peaks.is_empty() {
        return;
    }
    let max_val = peaks
        .iter()
        .copied()
        .filter(|v| v.is_finite())
        .fold(0.0f32, f32::max);
    if max_val <= 1e-6 {
        peaks.fill(0.0);
        return;
    }
    let norm = 1.0 / max_val;
    for v in peaks.iter_mut() {
        let linear = (*v * norm).clamp(0.0, 1.0);
        // Gentle cube-root + linear blend for perceptual balance
        *v = (0.65 * linear + 0.35 * linear.powf(0.65)).clamp(0.0, 1.0);
    }
}

/// Measures leading/trailing silence, peak, RMS, crest factor, and L/R phase correlation.
pub fn analyze_envelope_f32(
    samples: &[f32],
    channels: usize,
    silence_threshold_linear: f32,
) -> AudioEnvelopeReport {
    if samples.is_empty() || channels == 0 {
        return AudioEnvelopeReport {
            total_frames: 0,
            leading_silence_frames: 0,
            trailing_silence_frames: 0,
            peak_linear: 0.0,
            rms_linear: 0.0,
            crest_factor_db: 0.0,
            stereo_correlation: 1.0,
        };
    }

    let total_frames = samples.len() / channels;
    let thresh = silence_threshold_linear.max(1e-6);
    let mut peak = 0.0f32;
    let mut sum_sq = 0.0f64;
    let mut first_active: Option<usize> = None;
    let mut last_active: usize = 0;

    let mut lr_dot = 0.0f64;
    let mut l_sq = 0.0f64;
    let mut r_sq = 0.0f64;

    for (frame_idx, frame) in samples.chunks_exact(channels).enumerate() {
        let mut frame_peak = 0.0f32;
        for &s in frame {
            if s.is_finite() {
                let a = s.abs();
                if a > frame_peak {
                    frame_peak = a;
                }
                sum_sq += (s as f64) * (s as f64);
            }
        }
        if frame_peak > peak {
            peak = frame_peak;
        }
        if frame_peak > thresh {
            if first_active.is_none() {
                first_active = Some(frame_idx);
            }
            last_active = frame_idx;
        }
        if channels >= 2 {
            let l = if frame[0].is_finite() {
                frame[0] as f64
            } else {
                0.0
            };
            let r = if frame[1].is_finite() {
                frame[1] as f64
            } else {
                0.0
            };
            lr_dot += l * r;
            l_sq += l * l;
            r_sq += r * r;
        }
    }

    let rms = (sum_sq / ((total_frames * channels).max(1) as f64)).sqrt() as f32;
    let crest_factor_db = if rms > 1e-6 && peak > rms {
        20.0 * (peak / rms).log10()
    } else {
        0.0
    };
    let (leading_silence_frames, trailing_silence_frames) = match first_active {
        Some(first) => (first, total_frames.saturating_sub(last_active + 1)),
        None => (total_frames, 0),
    };
    let stereo_correlation = if channels >= 2 && l_sq > 1e-12 && r_sq > 1e-12 {
        (lr_dot / (l_sq * r_sq).sqrt()).clamp(-1.0, 1.0) as f32
    } else {
        1.0
    };

    AudioEnvelopeReport {
        total_frames,
        leading_silence_frames,
        trailing_silence_frames,
        peak_linear: peak,
        rms_linear: rms,
        crest_factor_db,
        stereo_correlation,
    }
}

/// Removes slow DC bias from mono/interleaved PCM using a 1st-order DC blocker per channel.
pub fn remove_dc_offset_in_place(samples: &mut [f32], channels: usize) {
    if samples.is_empty() || channels == 0 {
        return;
    }
    const R: f32 = 0.995;
    let mut prev_x = vec![0.0f32; channels];
    let mut prev_y = vec![0.0f32; channels];

    for frame in samples.chunks_exact_mut(channels) {
        for (ch, s_ref) in frame.iter_mut().enumerate() {
            let x = if s_ref.is_finite() { *s_ref } else { 0.0 };
            let y = x - prev_x[ch] + R * prev_y[ch];
            prev_x[ch] = x;
            prev_y[ch] = y;
            *s_ref = y;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn computes_normalized_peaks_and_downsamples() {
        let mut pcm = vec![0i16; 100];
        pcm[20] = 16_384;
        pcm[80] = 32_767;
        let peaks = compute_waveform_peaks_i16(&pcm, 1, 10);
        assert_eq!(peaks.len(), 10);
        assert!((peaks[8] - 1.0).abs() < 1e-4);
        assert!(peaks[2] > 0.4 && peaks[2] < 0.8);
        assert_eq!(peaks[0], 0.0);

        let down = downsample_peaks(&peaks, 5);
        assert_eq!(down.len(), 5);
        assert!((down[4] - 1.0).abs() < 1e-4);
    }

    #[test]
    fn detects_leading_trailing_silence_and_correlation() {
        let mut stereo = vec![0.0f32; 40]; // 20 stereo frames
        // Frames 5..=14 active identical L=R
        for f in 5..=14 {
            stereo[f * 2] = 0.5;
            stereo[f * 2 + 1] = 0.5;
        }
        let rep = analyze_envelope_f32(&stereo, 2, 0.01);
        assert_eq!(rep.leading_silence_frames, 5);
        assert_eq!(rep.trailing_silence_frames, 5);
        assert!((rep.stereo_correlation - 1.0).abs() < 1e-4);
    }

    #[test]
    fn dc_blocker_attenuates_constant_bias() {
        let mut buf = vec![0.4f32; 4096];
        remove_dc_offset_in_place(&mut buf, 1);
        assert!(buf[4095].abs() < 1e-3);
    }
}
