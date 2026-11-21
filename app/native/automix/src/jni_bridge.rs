// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{JClass, JFloatArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jdouble, jdoubleArray, jint, jlong, jstring};

use crate::{
    Spectrum, best_match_index, duration_matches, estimate_tempo, normalize, normalize_title,
    score, search_artist, search_title,
};

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

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeDurationMatches(
    _env: JNIEnv,
    _class: JClass,
    wanted_ms: jlong,
    candidate_ms: jlong,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let w = if wanted_ms >= 0 {
            Some(wanted_ms)
        } else {
            None
        };
        let c = if candidate_ms >= 0 {
            Some(candidate_ms)
        } else {
            None
        };
        if duration_matches(w, c) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeNormalize(
    mut env: JNIEnv,
    _class: JClass,
    value: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text: Option<String> = if value.is_null() {
            None
        } else {
            env.get_string(&value).ok().map(|s| s.into())
        };
        let norm = normalize(text.as_deref());
        env.new_string(norm)
            .map(|s| s.into_raw())
            .unwrap_or(ptr::null_mut())
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeNormalizeTitle(
    mut env: JNIEnv,
    _class: JClass,
    value: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text: String = if value.is_null() {
            String::new()
        } else {
            env.get_string(&value).map(|s| s.into()).unwrap_or_default()
        };
        let norm = normalize_title(&text);
        env.new_string(norm)
            .map(|s| s.into_raw())
            .unwrap_or(ptr::null_mut())
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeSearchTitle(
    mut env: JNIEnv,
    _class: JClass,
    value: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text: String = if value.is_null() {
            String::new()
        } else {
            env.get_string(&value).map(|s| s.into()).unwrap_or_default()
        };
        let st = search_title(&text);
        env.new_string(st)
            .map(|s| s.into_raw())
            .unwrap_or(ptr::null_mut())
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeSearchArtist(
    mut env: JNIEnv,
    _class: JClass,
    value: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text: String = if value.is_null() {
            String::new()
        } else {
            env.get_string(&value).map(|s| s.into()).unwrap_or_default()
        };
        let sa = search_artist(&text);
        env.new_string(sa)
            .map(|s| s.into_raw())
            .unwrap_or(ptr::null_mut())
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeScore(
    mut env: JNIEnv,
    _class: JClass,
    wanted_title: JString,
    wanted_artists: JObjectArray,
    candidate_title: JString,
    candidate_artist: JString,
    wanted_duration_ms: jlong,
    candidate_duration_ms: jlong,
) -> jint {
    contain_panic(i32::MIN, || {
        let w_title: String = if wanted_title.is_null() {
            String::new()
        } else {
            env.get_string(&wanted_title)
                .map(|s| s.into())
                .unwrap_or_default()
        };
        let c_title: String = if candidate_title.is_null() {
            String::new()
        } else {
            env.get_string(&candidate_title)
                .map(|s| s.into())
                .unwrap_or_default()
        };
        let c_artist: String = if candidate_artist.is_null() {
            String::new()
        } else {
            env.get_string(&candidate_artist)
                .map(|s| s.into())
                .unwrap_or_default()
        };

        let mut w_artists = Vec::new();
        if !wanted_artists.is_null() {
            let len = env.get_array_length(&wanted_artists).unwrap_or(0);
            for i in 0..len {
                if let Ok(elem) = env.get_object_array_element(&wanted_artists, i) {
                    let jstr = JString::from(elem);
                    let s: String = env
                        .get_string(&jstr)
                        .map(|js| js.into())
                        .unwrap_or_default();
                    w_artists.push(s);
                }
            }
        }
        let w_artists_refs: Vec<&str> = w_artists.iter().map(|s| s.as_str()).collect();

        let w_dur = if wanted_duration_ms >= 0 {
            Some(wanted_duration_ms)
        } else {
            None
        };
        let c_dur = if candidate_duration_ms >= 0 {
            Some(candidate_duration_ms)
        } else {
            None
        };

        score(&w_title, &w_artists_refs, &c_title, &c_artist, w_dur, c_dur)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_audiosource_TrackMatching_nativeBestMatchIndex(
    mut env: JNIEnv,
    _class: JClass,
    target_title: JString,
    target_artists: JObjectArray,
    target_duration_ms: jlong,
    candidate_titles: JObjectArray,
    candidate_primary_artists: JObjectArray,
    candidate_durations_ms: JLongArray,
) -> jint {
    contain_panic(-1, || {
        let t_title: String = if target_title.is_null() {
            String::new()
        } else {
            env.get_string(&target_title)
                .map(|s| s.into())
                .unwrap_or_default()
        };

        let mut t_artists = Vec::new();
        if !target_artists.is_null() {
            let len = env.get_array_length(&target_artists).unwrap_or(0);
            for i in 0..len {
                if let Ok(elem) = env.get_object_array_element(&target_artists, i) {
                    let jstr = JString::from(elem);
                    let s: String = env
                        .get_string(&jstr)
                        .map(|js| js.into())
                        .unwrap_or_default();
                    t_artists.push(s);
                }
            }
        }
        let t_artists_refs: Vec<&str> = t_artists.iter().map(|s| s.as_str()).collect();
        let t_dur = if target_duration_ms >= 0 {
            Some(target_duration_ms)
        } else {
            None
        };

        let count = if candidate_titles.is_null() {
            0
        } else {
            env.get_array_length(&candidate_titles).unwrap_or(0) as usize
        };

        let mut c_titles = Vec::with_capacity(count);
        for i in 0..(count as jint) {
            let s: String = if let Ok(elem) = env.get_object_array_element(&candidate_titles, i) {
                let jstr = JString::from(elem);
                env.get_string(&jstr)
                    .map(|js| js.into())
                    .unwrap_or_default()
            } else {
                String::new()
            };
            c_titles.push(s);
        }
        let c_titles_refs: Vec<&str> = c_titles.iter().map(|s| s.as_str()).collect();

        let mut c_artists = Vec::with_capacity(count);
        for i in 0..(count as jint) {
            let s: String = if candidate_primary_artists.is_null() {
                String::new()
            } else if let Ok(elem) = env.get_object_array_element(&candidate_primary_artists, i) {
                let jstr = JString::from(elem);
                env.get_string(&jstr)
                    .map(|js| js.into())
                    .unwrap_or_default()
            } else {
                String::new()
            };
            c_artists.push(s);
        }
        let c_artists_refs: Vec<&str> = c_artists.iter().map(|s| s.as_str()).collect();

        let mut c_durations = vec![0i64; count];
        if !candidate_durations_ms.is_null() {
            let _ = env.get_long_array_region(&candidate_durations_ms, 0, &mut c_durations);
        }
        let c_dur_opts: Vec<Option<i64>> = c_durations
            .iter()
            .map(|&d| if d >= 0 { Some(d) } else { None })
            .collect();

        best_match_index(
            &t_title,
            &t_artists_refs,
            t_dur,
            &c_titles_refs,
            &c_artists_refs,
            &c_dur_opts,
        )
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
