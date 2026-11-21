// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JClass, JFloatArray, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jdouble, jdoubleArray, jint, jlong};

use crate::{Spectrum, estimate_tempo};

fn contain_panic<T>(failure: T, call: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(call)).unwrap_or(failure)
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixSpectrum_nativeCreate(
    _env: JNIEnv,
    _receiver: JObject,
    size: jint,
) -> jlong {
    contain_panic(0, || match Spectrum::new(size as usize) {
        Ok(spectrum) => Box::into_raw(Box::new(spectrum)) as jlong,
        Err(_) => 0,
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixSpectrum_nativeCalculate(
    env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
    input: JFloatArray,
    magnitudes: JFloatArray,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        if handle == 0 {
            return JNI_FALSE;
        }

        // AutoMixSpectrum exclusively owns this handle and serializes calculate and close.
        let spectrum = unsafe { &mut *(handle as *mut Spectrum) };
        if env.get_array_length(&input).ok() != Some(spectrum.input.len() as jint)
            || env.get_array_length(&magnitudes).ok() != Some(spectrum.magnitudes.len() as jint)
        {
            return JNI_FALSE;
        }

        if env
            .get_float_array_region(&input, 0, &mut spectrum.input)
            .is_err()
            || spectrum.transform().is_err()
        {
            return JNI_FALSE;
        }
        match env.set_float_array_region(&magnitudes, 0, &spectrum.magnitudes) {
            Ok(()) => JNI_TRUE,
            Err(_) => JNI_FALSE,
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixSpectrum_nativeRelease(
    _env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
) {
    contain_panic((), || {
        if handle != 0 {
            // AutoMixSpectrum clears the handle under its lock before releasing it once.
            drop(unsafe { Box::from_raw(handle as *mut Spectrum) });
        }
    });
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixTempo_nativeEstimateTempo(
    env: JNIEnv,
    _class: JClass,
    flux: JFloatArray,
    flux_rate: jdouble,
) -> jdoubleArray {
    contain_panic(ptr::null_mut(), || {
        let len = match env.get_array_length(&flux) {
            Ok(len) if len >= 64 => len as usize,
            _ => return ptr::null_mut(),
        };

        let mut flux_vec = vec![0.0f32; len];
        if env.get_float_array_region(&flux, 0, &mut flux_vec).is_err() {
            return ptr::null_mut();
        }

        let estimate = match estimate_tempo(&flux_vec, flux_rate) {
            Some(est) => est,
            None => return ptr::null_mut(),
        };

        let output_array = match env.new_double_array(4) {
            Ok(arr) => arr,
            Err(_) => return ptr::null_mut(),
        };

        let values = [
            estimate.bpm,
            estimate.interval_ms,
            estimate.confidence,
            estimate.phase_ms,
        ];

        if env
            .set_double_array_region(&output_array, 0, &values)
            .is_err()
        {
            return ptr::null_mut();
        }

        output_array.into_raw()
    })
}

#[cfg(test)]
mod tests {
    use super::contain_panic;

    #[test]
    fn catches_a_panic_and_preserves_normal_results() {
        assert!(!contain_panic(false, || panic!("test panic")));
        assert!(contain_panic(false, || true));
    }
}
