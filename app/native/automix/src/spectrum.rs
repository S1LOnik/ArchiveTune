// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::fmt;
use std::sync::Arc;

use realfft::num_complex::Complex32;
use realfft::{RealFftPlanner, RealToComplex};

/// An AutoMix FFT plan and reusable buffers for one stream of real PCM frames.
pub struct Spectrum {
    plan: Arc<dyn RealToComplex<f32>>,
    pub(crate) input: Vec<f32>,
    bins: Vec<Complex32>,
    scratch: Vec<Complex32>,
    pub(crate) magnitudes: Vec<f32>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum SpectrumError {
    UnsupportedSize,
    BufferLength,
    TransformFailed,
}

impl fmt::Display for SpectrumError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(match self {
            Self::UnsupportedSize => "AutoMix FFT size must be 512 or 1024",
            Self::BufferLength => "AutoMix FFT input or output has the wrong length",
            Self::TransformFailed => "AutoMix FFT failed",
        })
    }
}

impl std::error::Error for SpectrumError {}

impl Spectrum {
    pub fn new(size: usize) -> Result<Self, SpectrumError> {
        if !matches!(size, 512 | 1024) {
            return Err(SpectrumError::UnsupportedSize);
        }

        let plan = RealFftPlanner::new().plan_fft_forward(size);
        Ok(Self {
            input: plan.make_input_vec(),
            bins: plan.make_output_vec(),
            scratch: plan.make_scratch_vec(),
            magnitudes: vec![0.0; size / 2],
            plan,
        })
    }

    /// Writes unnormalized magnitudes for bins `0..size / 2`, excluding Nyquist.
    pub fn magnitude_spectrum(
        &mut self,
        input: &[f32],
        magnitudes: &mut [f32],
    ) -> Result<(), SpectrumError> {
        if input.len() != self.input.len() || magnitudes.len() != self.magnitudes.len() {
            return Err(SpectrumError::BufferLength);
        }

        self.input.copy_from_slice(input);
        self.transform()?;
        magnitudes.copy_from_slice(&self.magnitudes);
        Ok(())
    }

    pub(crate) fn transform(&mut self) -> Result<(), SpectrumError> {
        self.plan
            .process_with_scratch(&mut self.input, &mut self.bins, &mut self.scratch)
            .map_err(|_| SpectrumError::TransformFailed)?;
        for (magnitude, bin) in self.magnitudes.iter_mut().zip(&self.bins) {
            *magnitude = (bin.re * bin.re + bin.im * bin.im).sqrt();
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::f64::consts::TAU;

    fn reference_bin(input: &[f32], bin: usize) -> f64 {
        let mut real = 0.0;
        let mut imaginary = 0.0;
        for (sample_index, &sample) in input.iter().enumerate() {
            let angle = TAU * (sample_index * bin) as f64 / input.len() as f64;
            real += sample as f64 * angle.cos();
            imaginary -= sample as f64 * angle.sin();
        }
        real.hypot(imaginary)
    }

    #[test]
    fn consecutive_frames_match_reference_dft() {
        for size in [512, 1024] {
            let mut fft = Spectrum::new(size).unwrap();
            let mut magnitudes = vec![f32::NAN; size / 2];
            let mut noise_state = 12345u32;
            let frames = [
                vec![0.0; size],
                (0..size).map(|i| if i == 7 { 1.0 } else { 0.0 }).collect(),
                vec![1.0; size],
                (0..size)
                    .map(|i| if i % 2 == 0 { 1.0 } else { -1.0 })
                    .collect(),
                (0..size)
                    .map(|i| (TAU * (i * 19) as f64 / size as f64).sin() as f32)
                    .collect(),
                (0..size)
                    .map(|_| {
                        noise_state = noise_state.wrapping_mul(1664525).wrapping_add(1013904223);
                        f32::from((noise_state >> 16) as i16) / 32768.0
                    })
                    .collect(),
            ];

            for frame in frames {
                fft.magnitude_spectrum(&frame, &mut magnitudes).unwrap();
                for (bin, &actual) in magnitudes.iter().enumerate() {
                    let expected = reference_bin(&frame, bin);
                    assert!(
                        (f64::from(actual) - expected).abs() < 0.0002,
                        "size {size}, bin {bin}: expected {expected}, got {actual}"
                    );
                }
            }
        }
    }

    #[test]
    fn wrong_buffer_lengths_leave_the_output_unchanged() {
        let mut fft = Spectrum::new(512).unwrap();
        let mut output = [3.0; 256];
        assert_eq!(
            fft.magnitude_spectrum(&[], &mut output),
            Err(SpectrumError::BufferLength)
        );
        assert_eq!(
            fft.magnitude_spectrum(&[0.0; 512], &mut output[..255]),
            Err(SpectrumError::BufferLength)
        );
        assert_eq!(output, [3.0; 256]);

        fft.magnitude_spectrum(&[0.0; 512], &mut output).unwrap();
        assert_eq!(output, [0.0; 256]);
    }

    #[test]
    fn rejects_unsupported_sizes() {
        for size in [0, 1, 511, 2048, usize::MAX] {
            assert!(matches!(
                Spectrum::new(size),
                Err(SpectrumError::UnsupportedSize)
            ));
        }
    }
}
