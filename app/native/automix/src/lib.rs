use std::sync::Arc;

use jni::JNIEnv;
use jni::objects::{JFloatArray, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};
use realfft::num_complex::Complex32;
use realfft::{RealFftPlanner, RealToComplex};

struct Spectrum {
    plan: Arc<dyn RealToComplex<f32>>,
    input: Vec<f32>,
    output: Vec<Complex32>,
    scratch: Vec<Complex32>,
    magnitudes: Vec<f32>,
}

impl Spectrum {
    fn new(size: usize) -> Self {
        let plan = RealFftPlanner::new().plan_fft_forward(size);
        Self {
            input: plan.make_input_vec(),
            output: plan.make_output_vec(),
            scratch: plan.make_scratch_vec(),
            magnitudes: vec![0.0; size / 2],
            plan,
        }
    }
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixSpectrum_nativeCreate(
    _env: JNIEnv,
    _receiver: JObject,
    size: jint,
) -> jlong {
    if !matches!(size, 512 | 1024) {
        return 0;
    }
    Box::into_raw(Box::new(Spectrum::new(size as usize))) as jlong
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixSpectrum_nativeCalculate(
    env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
    input: JFloatArray,
    magnitudes: JFloatArray,
) -> jboolean {
    if handle == 0 {
        return JNI_FALSE;
    }
    // The Kotlin instance owns this pointer and uses it on the decoder thread until close.
    let spectrum = unsafe { &mut *(handle as *mut Spectrum) };
    if env.get_array_length(&input).ok() != Some(spectrum.input.len() as jint)
        || env.get_array_length(&magnitudes).ok() != Some(spectrum.magnitudes.len() as jint)
    {
        return JNI_FALSE;
    }
    if env
        .get_float_array_region(&input, 0, &mut spectrum.input)
        .is_err()
        || spectrum
            .plan
            .process_with_scratch(
                &mut spectrum.input,
                &mut spectrum.output,
                &mut spectrum.scratch,
            )
            .is_err()
    {
        return JNI_FALSE;
    }
    for (magnitude, bin) in spectrum.magnitudes.iter_mut().zip(&spectrum.output) {
        *magnitude = (bin.re * bin.re + bin.im * bin.im).sqrt();
    }
    match env.set_float_array_region(&magnitudes, 0, &spectrum.magnitudes) {
        Ok(()) => JNI_TRUE,
        Err(_) => JNI_FALSE,
    }
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixSpectrum_nativeRelease(
    _env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
) {
    if handle != 0 {
        // Kotlin clears the handle before calling this method.
        drop(unsafe { Box::from_raw(handle as *mut Spectrum) });
    }
}
