// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JFloatArray, JObject};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};

use crate::Spectrum;

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

#[cfg(test)]
mod tests {
    use super::contain_panic;

    #[test]
    fn catches_a_panic_and_preserves_normal_results() {
        assert!(!contain_panic(false, || panic!("test panic")));
        assert!(contain_panic(false, || true));
    }
}
