// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{
    JByteArray, JClass, JDoubleArray, JFloatArray, JLongArray, JObject, JObjectArray, JString,
};
use jni::sys::{
    JNI_FALSE, JNI_TRUE, jboolean, jdouble, jdoubleArray, jint, jlong, jobjectArray, jstring,
};

use crate::{
    AutoMixAnalysisRaw, Spectrum, best_match_index, duration_matches, estimate_tempo,
    extract_embedded_lyrics, normalize, normalize_title, plan_automix, score, search_artist,
    search_title,
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

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_localmedia_EmbeddedLyricsExtractor_nativeExtractLyrics(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
    extension: JString,
    mime_type: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        if bytes.is_null() {
            return ptr::null_mut();
        }
        let len = match env.get_array_length(&bytes) {
            Ok(l) if l > 0 => l as usize,
            _ => return ptr::null_mut(),
        };

        let mut signed_bytes = vec![0i8; len];
        if env
            .get_byte_array_region(&bytes, 0, &mut signed_bytes)
            .is_err()
        {
            return ptr::null_mut();
        }

        let u8_slice: &[u8] = unsafe {
            std::slice::from_raw_parts(signed_bytes.as_ptr() as *const u8, signed_bytes.len())
        };

        let ext_str: Option<String> = if extension.is_null() {
            None
        } else {
            env.get_string(&extension).ok().map(|s| s.into())
        };

        let mime_str: Option<String> = if mime_type.is_null() {
            None
        } else {
            env.get_string(&mime_type).ok().map(|s| s.into())
        };

        match extract_embedded_lyrics(u8_slice, ext_str.as_deref(), mime_str.as_deref()) {
            Some(lyrics) => env
                .new_string(lyrics)
                .map(|s| s.into_raw())
                .unwrap_or(ptr::null_mut()),
            None => ptr::null_mut(),
        }
    })
}

#[allow(clippy::too_many_arguments)]
fn extract_analysis<'a>(
    env: &mut JNIEnv,
    scalars: &JDoubleArray,
    mix_in: &JLongArray,
    mix_out: &JLongArray,
    energy: &JFloatArray,
    vocal: &JFloatArray,
    scalars_buf: &'a mut [f64; 10],
    mix_in_buf: &'a mut Vec<i64>,
    mix_out_buf: &'a mut Vec<i64>,
    energy_buf: &'a mut Vec<f32>,
    vocal_buf: &'a mut Vec<f32>,
) -> Option<AutoMixAnalysisRaw<'a>> {
    if scalars.is_null() {
        return None;
    }
    let s_len = env.get_array_length(scalars).ok()?;
    if s_len < 10 {
        return None;
    }
    env.get_double_array_region(scalars, 0, scalars_buf).ok()?;

    if !mix_in.is_null() {
        let l = env.get_array_length(mix_in).unwrap_or(0);
        mix_in_buf.resize(l as usize, 0);
        let _ = env.get_long_array_region(mix_in, 0, mix_in_buf);
    }

    if !mix_out.is_null() {
        let l = env.get_array_length(mix_out).unwrap_or(0);
        mix_out_buf.resize(l as usize, 0);
        let _ = env.get_long_array_region(mix_out, 0, mix_out_buf);
    }

    if !energy.is_null() {
        let l = env.get_array_length(energy).unwrap_or(0);
        energy_buf.resize(l as usize, 0.0);
        let _ = env.get_float_array_region(energy, 0, energy_buf);
    }

    if !vocal.is_null() {
        let l = env.get_array_length(vocal).unwrap_or(0);
        vocal_buf.resize(l as usize, 0.0);
        let _ = env.get_float_array_region(vocal, 0, vocal_buf);
    }

    Some(AutoMixAnalysisRaw {
        duration_ms: scalars_buf[0] as i64,
        bpm: scalars_buf[1],
        beat_interval_ms: scalars_buf[2],
        beat_confidence: scalars_buf[3],
        downbeat_phase_ms: scalars_buf[4],
        audible_start_ms: scalars_buf[5] as i64,
        intro_end_ms: scalars_buf[6] as i64,
        outro_start_ms: scalars_buf[7] as i64,
        content_end_ms: scalars_buf[8] as i64,
        final_fade_onset_ms: scalars_buf[9] as i64,
        mix_in_candidates_ms: mix_in_buf.as_slice(),
        mix_out_candidates_ms: mix_out_buf.as_slice(),
        energy_curve: energy_buf.as_slice(),
        vocal_activity: vocal_buf.as_slice(),
    })
}

#[allow(clippy::too_many_arguments)]
#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixPlanner_nativePlan(
    mut env: JNIEnv,
    _class: JClass,
    cur_scalars: JDoubleArray,
    cur_mix_in: JLongArray,
    cur_mix_out: JLongArray,
    cur_energy: JFloatArray,
    cur_vocal: JFloatArray,
    nxt_scalars: JDoubleArray,
    nxt_mix_in: JLongArray,
    nxt_mix_out: JLongArray,
    nxt_energy: JFloatArray,
    nxt_vocal: JFloatArray,
    current_duration_ms: jlong,
    next_duration_ms: jlong,
    position_ms: jlong,
    fallback_fade_ms: jlong,
    min_fade_ms: jlong,
    gapless_album: jboolean,
) -> jobjectArray {
    contain_panic(ptr::null_mut(), || {
        let mut cur_scalars_buf = [0.0; 10];
        let mut cur_mix_in_buf = Vec::new();
        let mut cur_mix_out_buf = Vec::new();
        let mut cur_energy_buf = Vec::new();
        let mut cur_vocal_buf = Vec::new();

        let cur = extract_analysis(
            &mut env,
            &cur_scalars,
            &cur_mix_in,
            &cur_mix_out,
            &cur_energy,
            &cur_vocal,
            &mut cur_scalars_buf,
            &mut cur_mix_in_buf,
            &mut cur_mix_out_buf,
            &mut cur_energy_buf,
            &mut cur_vocal_buf,
        );

        let mut nxt_scalars_buf = [0.0; 10];
        let mut nxt_mix_in_buf = Vec::new();
        let mut nxt_mix_out_buf = Vec::new();
        let mut nxt_energy_buf = Vec::new();
        let mut nxt_vocal_buf = Vec::new();

        let nxt = extract_analysis(
            &mut env,
            &nxt_scalars,
            &nxt_mix_in,
            &nxt_mix_out,
            &nxt_energy,
            &nxt_vocal,
            &mut nxt_scalars_buf,
            &mut nxt_mix_in_buf,
            &mut nxt_mix_out_buf,
            &mut nxt_energy_buf,
            &mut nxt_vocal_buf,
        );

        let plan = plan_automix(
            cur.as_ref(),
            nxt.as_ref(),
            current_duration_ms,
            next_duration_ms,
            position_ms,
            fallback_fade_ms,
            min_fade_ms,
            gapless_album == JNI_TRUE,
        );

        let double_arr = match env.new_double_array(12) {
            Ok(arr) => arr,
            Err(_) => return ptr::null_mut(),
        };

        let nums: [f64; 12] = [
            plan.style as i32 as f64,
            plan.transition_start_ms as f64,
            plan.transition_end_ms as f64,
            plan.fade_ms as f64,
            plan.incoming_cue_ms as f64,
            plan.incoming_playback_rate,
            if plan.bass_swap { 1.0 } else { 0.0 },
            plan.bass_swap_fraction,
            plan.filter_sweep,
            plan.vocal_overlap,
            if plan.marker_visible { 1.0 } else { 0.0 },
            if plan.blocked { 1.0 } else { 0.0 },
        ];

        if env.set_double_array_region(&double_arr, 0, &nums).is_err() {
            return ptr::null_mut();
        }

        let reason_jstr = match env.new_string(plan.reason) {
            Ok(s) => s,
            Err(_) => return ptr::null_mut(),
        };

        let obj_class = match env.find_class("java/lang/Object") {
            Ok(c) => c,
            Err(_) => return ptr::null_mut(),
        };

        let result_array = match env.new_object_array(2, obj_class, JObject::null()) {
            Ok(arr) => arr,
            Err(_) => return ptr::null_mut(),
        };

        let _ = env.set_object_array_element(&result_array, 0, JObject::from(double_arr));
        let _ = env.set_object_array_element(&result_array, 1, JObject::from(reason_jstr));

        result_array.into_raw()
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeParseLyrics(
    mut env: JNIEnv,
    _class: JClass,
    lyrics: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let lyrics_str: String = match env.get_string(&lyrics) {
            Ok(s) => s.into(),
            Err(_) => return ptr::null_mut(),
        };

        let parsed = crate::lyrics_parser::parse_lyrics(&lyrics_str);
        let json = crate::lyrics_parser::serialize_entries_to_json(&parsed);
        match env.new_string(json) {
            Ok(js) => js.into_raw(),
            Err(_) => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeCleanInlineWordTiming(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text_str: String = match env.get_string(&text) {
            Ok(s) => s.into(),
            Err(_) => return ptr::null_mut(),
        };

        let cleaned = crate::lyrics_parser::clean_inline_word_timing_text(&text_str);
        match env.new_string(cleaned) {
            Ok(js) => js.into_raw(),
            Err(_) => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeIsLineSyncedLrc(
    mut env: JNIEnv,
    _class: JClass,
    lyrics: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let lyrics_str: String = match env.get_string(&lyrics) {
            Ok(s) => s.into(),
            Err(_) => return JNI_FALSE,
        };

        if crate::lyrics_parser::is_line_synced_lrc(&lyrics_str) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeFindCurrentLineIndex(
    env: JNIEnv,
    _class: JClass,
    times: JLongArray,
    durations: JLongArray,
    position: jlong,
    lead_ms: jlong,
) -> jint {
    contain_panic(-1, || {
        let len = match env.get_array_length(&times) {
            Ok(l) => l as usize,
            Err(_) => return -1,
        };
        let mut times_buf = vec![0i64; len];
        if env
            .get_long_array_region(&times, 0, &mut times_buf)
            .is_err()
        {
            return -1;
        }

        let dur_len = match env.get_array_length(&durations) {
            Ok(l) => l as usize,
            Err(_) => 0,
        };
        let mut dur_buf = vec![0i64; dur_len];
        if dur_len > 0 {
            let _ = env.get_long_array_region(&durations, 0, &mut dur_buf);
        }

        crate::lyrics_parser::find_current_line_index(&times_buf, &dur_buf, position, lead_ms)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeHasTrueWordSync(
    env: JNIEnv,
    _class: JClass,
    start_times: JDoubleArray,
    end_times: JDoubleArray,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let len = match env.get_array_length(&start_times) {
            Ok(l) => l as usize,
            Err(_) => return JNI_FALSE,
        };
        if len == 0 {
            return JNI_FALSE;
        }
        let end_len = match env.get_array_length(&end_times) {
            Ok(l) => l as usize,
            Err(_) => return JNI_FALSE,
        };
        if len != end_len {
            return JNI_FALSE;
        }

        let mut st_buf = vec![0.0f64; len];
        if env
            .get_double_array_region(&start_times, 0, &mut st_buf)
            .is_err()
        {
            return JNI_FALSE;
        }

        let mut et_buf = vec![0.0f64; len];
        if env
            .get_double_array_region(&end_times, 0, &mut et_buf)
            .is_err()
        {
            return JNI_FALSE;
        }

        if crate::lyrics_parser::has_true_word_sync(&st_buf, &et_buf) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
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
