// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::fmt::Write as _;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use jni::JNIEnv;
use jni::objects::{
    JByteArray, JClass, JFloatArray, JIntArray, JLongArray, JObject, JObjectArray, JShortArray,
    JString,
};
use jni::sys::{
    JNI_FALSE, JNI_TRUE, jboolean, jbyteArray, jdouble, jfloat, jfloatArray, jint, jintArray,
    jlong, jobjectArray, jstring,
};

use crate::audio_codec_pcm::{
    compute_three_band_energy, measure_true_peak_4x, pcm_s24_le_to_f32, quantize_f32_to_i16_tpdf,
};
use crate::audio_dsp_eq::{
    BiquadKind, ParametricEqBank, estimate_integrated_lufs, replay_gain_db_for_target,
};
use crate::audio_tags::parse_audio_tags_to_json;
use crate::cache_crypto::{sha256_hex, sniff_media_mime};
use crate::cue_pls_parser::{infer_metadata_from_filename, parse_cue_to_json, parse_pls_to_json};
use crate::dsp_filters::{
    SvfFilterProcessor, analyze_energy_profile, equal_power_gains, hann_window,
    resample_cubic_mono, robust_mean, s_curve_gains,
};
use crate::http_range_sync::{parse_content_range, select_cache_evictions};
use crate::lyrics_export_lrc::{lrc_to_srt, parse_xspf_to_json, srt_or_vtt_to_lrc};
use crate::playback_dynamics::{DynamicsCompressor, time_stretch_wsola_mono};
use crate::playlist_dedup::{
    deduplicate_track_indices, extract_youtube_playlist_id, extract_youtube_video_id,
    normalize_isrc, parse_m3u_to_json, smart_shuffle_indices, strip_tracking_query_params,
};
use crate::qrc_parser::{
    has_word_timings, is_qrc, parse_qrc_to_json, qrc_to_enhanced_lrc,
};
use crate::romanization::{
    detect_dominant_language, hiragana_to_katakana, katakana_to_romaji, looks_chinese,
    looks_hindi, looks_japanese, looks_korean, needs_other_romanization, romanize_cyrillic,
    romanize_hindi, romanize_korean,
};
use crate::stream_selector::{
    TargetAudioQuality, extract_dash_segment_urls, extract_flac_metadata_blocks,
    extract_mdat_payloads, fuzzy_similarity_score, rank_audio_formats,
};
use crate::ttml_parser::{parse_ttml_to_json, push_json_str};
use crate::waveform_peaks::{compute_waveform_peaks_f32, compute_waveform_peaks_i16};

fn contain_panic<T>(failure: T, call: impl FnOnce() -> T) -> T {
    catch_unwind(AssertUnwindSafe(call)).unwrap_or(failure)
}

fn read_jstring(env: &mut JNIEnv, value: &JString) -> Option<String> {
    if value.is_null() {
        return None;
    }
    env.get_string(value).ok().map(Into::into)
}

fn read_string_array(env: &mut JNIEnv, array: &JObjectArray) -> Vec<String> {
    if array.is_null() {
        return Vec::new();
    }
    let len = env.get_array_length(array).unwrap_or(0).max(0);
    let mut out = Vec::with_capacity(len as usize);
    for idx in 0..len {
        let s = env
            .get_object_array_element(array, idx)
            .ok()
            .and_then(|obj| {
                let js = JString::from(obj);
                read_jstring(env, &js)
            })
            .unwrap_or_default();
        out.push(s);
    }
    out
}

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(ptr::null_mut())
}

fn to_jfloat_array(env: &mut JNIEnv, slice: &[f32]) -> jfloatArray {
    match env.new_float_array(slice.len() as jint) {
        Ok(arr) => {
            if !slice.is_empty() && env.set_float_array_region(&arr, 0, slice).is_err() {
                return ptr::null_mut();
            }
            arr.into_raw()
        }
        Err(_) => ptr::null_mut(),
    }
}

fn to_jint_array(env: &mut JNIEnv, slice: &[usize]) -> jintArray {
    let ints: Vec<jint> = slice.iter().map(|&v| v as jint).collect();
    match env.new_int_array(ints.len() as jint) {
        Ok(arr) => {
            if !ints.is_empty() && env.set_int_array_region(&arr, 0, &ints).is_err() {
                return ptr::null_mut();
            }
            arr.into_raw()
        }
        Err(_) => ptr::null_mut(),
    }
}

fn to_jstring_array(env: &mut JNIEnv, items: &[String]) -> jobjectArray {
    let Ok(arr) = env.new_object_array(items.len() as jint, "java/lang/String", JObject::null())
    else {
        return ptr::null_mut();
    };
    for (idx, item) in items.iter().enumerate() {
        let Ok(jstr) = env.new_string(item) else {
            return ptr::null_mut();
        };
        if env
            .set_object_array_element(&arr, idx as jint, &jstr)
            .is_err()
        {
            return ptr::null_mut();
        }
    }
    arr.into_raw()
}

// ---------------------------------------------------------------------------
// AutoMixFilterProcessor JNI
// ---------------------------------------------------------------------------

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeCreateFilter(
    _env: JNIEnv,
    _receiver: JObject,
    channels: jint,
    sample_rate: jint,
) -> jlong {
    contain_panic(0, || {
        match SvfFilterProcessor::new(channels.max(0) as usize, sample_rate) {
            Ok(proc) => Box::into_raw(Box::new(proc)) as jlong,
            Err(_) => 0,
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeSetFilterCutoffs(
    _env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
    low_pass_hz: jfloat,
    high_pass_hz: jfloat,
) {
    contain_panic((), || {
        if handle == 0 {
            return;
        }
        let proc = unsafe { &mut *(handle as *mut SvfFilterProcessor) };
        proc.set_cutoffs(low_pass_hz, high_pass_hz);
    });
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeFlushFilter(
    _env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
) {
    contain_panic((), || {
        if handle == 0 {
            return;
        }
        let proc = unsafe { &mut *(handle as *mut SvfFilterProcessor) };
        proc.flush();
    });
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeIsFilterParked(
    _env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
) -> jboolean {
    contain_panic(JNI_TRUE, || {
        if handle == 0 {
            return JNI_TRUE;
        }
        let proc = unsafe { &*(handle as *const SvfFilterProcessor) };
        if proc.is_parked() {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeProcessFilterShort(
    env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
    samples: JShortArray,
    count: jint,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        if handle == 0 || samples.is_null() || count <= 0 {
            return JNI_FALSE;
        }
        let proc = unsafe { &mut *(handle as *mut SvfFilterProcessor) };
        let len = env.get_array_length(&samples).unwrap_or(0);
        let n = (count.min(len)).max(0) as usize;
        if n == 0 {
            return JNI_FALSE;
        }
        let mut buf = vec![0i16; n];
        if env.get_short_array_region(&samples, 0, &mut buf).is_err() {
            return JNI_FALSE;
        }
        let modified = proc.process_i16_in_place(&mut buf);
        if modified && env.set_short_array_region(&samples, 0, &buf).is_err() {
            return JNI_FALSE;
        }
        if modified { JNI_TRUE } else { JNI_FALSE }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeProcessFilterFloat(
    env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
    samples: JFloatArray,
    count: jint,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        if handle == 0 || samples.is_null() || count <= 0 {
            return JNI_FALSE;
        }
        let proc = unsafe { &mut *(handle as *mut SvfFilterProcessor) };
        let len = env.get_array_length(&samples).unwrap_or(0);
        let n = (count.min(len)).max(0) as usize;
        if n == 0 {
            return JNI_FALSE;
        }
        let mut buf = vec![0.0f32; n];
        if env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return JNI_FALSE;
        }
        let modified = proc.process_f32_in_place(&mut buf);
        if modified && env.set_float_array_region(&samples, 0, &buf).is_err() {
            return JNI_FALSE;
        }
        if modified { JNI_TRUE } else { JNI_FALSE }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixFilterProcessor_nativeReleaseFilter(
    _env: JNIEnv,
    _receiver: JObject,
    handle: jlong,
) {
    contain_panic((), || {
        if handle != 0 {
            unsafe {
                drop(Box::from_raw(handle as *mut SvfFilterProcessor));
            }
        }
    });
}

// ---------------------------------------------------------------------------
// AutoMixNativeDsp JNI (window, curve analysis, resampler, LUFS, peaks, EQ)
// ---------------------------------------------------------------------------

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeHannWindow(
    mut env: JNIEnv,
    _class: JClass,
    size: jint,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if size <= 0 {
            return to_jfloat_array(&mut env, &[]);
        }
        let win = hann_window(size as usize);
        to_jfloat_array(&mut env, &win)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeRobustMean(
    env: JNIEnv,
    _class: JClass,
    values: JFloatArray,
) -> jfloat {
    contain_panic(0.0, || {
        if values.is_null() {
            return 0.0;
        }
        let len = env.get_array_length(&values).unwrap_or(0).max(0) as usize;
        if len == 0 {
            return 0.0;
        }
        let mut buf = vec![0.0f32; len];
        if env.get_float_array_region(&values, 0, &mut buf).is_err() {
            return 0.0;
        }
        robust_mean(&buf)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeAnalyzeCurvesJson(
    mut env: JNIEnv,
    _class: JClass,
    energy: JFloatArray,
    vocal: JFloatArray,
    duration_ms: jlong,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let e_len = if energy.is_null() {
            0
        } else {
            env.get_array_length(&energy).unwrap_or(0).max(0) as usize
        };
        let v_len = if vocal.is_null() {
            0
        } else {
            env.get_array_length(&vocal).unwrap_or(0).max(0) as usize
        };
        let mut e_buf = vec![0.0f32; e_len];
        let mut v_buf = vec![0.0f32; v_len];
        if e_len > 0 && env.get_float_array_region(&energy, 0, &mut e_buf).is_err() {
            return ptr::null_mut();
        }
        if v_len > 0 && env.get_float_array_region(&vocal, 0, &mut v_buf).is_err() {
            return ptr::null_mut();
        }

        let summary = analyze_energy_profile(&e_buf, &v_buf, duration_ms);
        let mut out = String::with_capacity(512);
        let _ = write!(
            out,
            "{{\"audibleStartMs\":{},\"introEndMs\":{},\"outroStartMs\":{},",
            summary.audible_start_ms,
            summary.intro_end_ms,
            summary.outro_start_ms,
        );
        let _ = write!(
            out,
            "\"contentEndMs\":{},\"finalFadeOnsetMs\":{}",
            summary.content_end_ms,
            summary.final_fade_onset_ms,
        );
        out.push_str(",\"mixInCandidatesMs\":[");
        for (i, v) in summary.mix_in_candidates_ms.iter().enumerate() {
            if i > 0 {
                out.push(',');
            }
            let _ = write!(out, "{v}");
        }
        out.push_str("],\"mixOutCandidatesMs\":[");
        for (i, v) in summary.mix_out_candidates_ms.iter().enumerate() {
            if i > 0 {
                out.push(',');
            }
            let _ = write!(out, "{v}");
        }
        out.push_str("],\"normalizedVocal\":[");
        for (i, v) in summary.normalized_vocal.iter().enumerate() {
            if i > 0 {
                out.push(',');
            }
            let _ = write!(out, "{v:.5}");
        }
        out.push_str("]}");
        to_jstring(&mut env, &out)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeEqualPowerGains(
    mut env: JNIEnv,
    _class: JClass,
    progress: jfloat,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        let (a, b) = equal_power_gains(progress);
        to_jfloat_array(&mut env, &[a, b])
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeSCurveGains(
    mut env: JNIEnv,
    _class: JClass,
    progress: jfloat,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        let (a, b) = s_curve_gains(progress);
        to_jfloat_array(&mut env, &[a, b])
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeResampleCubicMono(
    mut env: JNIEnv,
    _class: JClass,
    input: JFloatArray,
    src_rate: jint,
    dst_rate: jint,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if input.is_null() || src_rate <= 0 || dst_rate <= 0 {
            return ptr::null_mut();
        }
        let len = env.get_array_length(&input).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0.0f32; len];
        if len > 0 && env.get_float_array_region(&input, 0, &mut buf).is_err() {
            return ptr::null_mut();
        }
        let out = resample_cubic_mono(&buf, src_rate as u32, dst_rate as u32);
        to_jfloat_array(&mut env, &out)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeEstimateLufs(
    env: JNIEnv,
    _class: JClass,
    samples: JFloatArray,
    sample_rate: jint,
    channels: jint,
) -> jdouble {
    contain_panic(-70.0, || {
        if samples.is_null() || sample_rate <= 0 || channels <= 0 {
            return -70.0;
        }
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        if len == 0 {
            return -70.0;
        }
        let mut buf = vec![0.0f32; len];
        if env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return -70.0;
        }
        estimate_integrated_lufs(&buf, sample_rate, channels as usize)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeWaveformPeaksShort(
    mut env: JNIEnv,
    _class: JClass,
    samples: JShortArray,
    channels: jint,
    buckets: jint,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if samples.is_null() || channels <= 0 || buckets <= 0 {
            return to_jfloat_array(&mut env, &[]);
        }
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0i16; len];
        if len > 0 && env.get_short_array_region(&samples, 0, &mut buf).is_err() {
            return ptr::null_mut();
        }
        let peaks = compute_waveform_peaks_i16(&buf, channels as usize, buckets as usize);
        to_jfloat_array(&mut env, &peaks)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeWaveformPeaksFloat(
    mut env: JNIEnv,
    _class: JClass,
    samples: JFloatArray,
    channels: jint,
    buckets: jint,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if samples.is_null() || channels <= 0 || buckets <= 0 {
            return to_jfloat_array(&mut env, &[]);
        }
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0.0f32; len];
        if len > 0 && env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return ptr::null_mut();
        }
        let peaks = compute_waveform_peaks_f32(&buf, channels as usize, buckets as usize);
        to_jfloat_array(&mut env, &peaks)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeCreateEq(
    _env: JNIEnv,
    _class: JClass,
    sample_rate: jint,
    channels: jint,
    bands: jint,
) -> jlong {
    contain_panic(0, || {
        match ParametricEqBank::new(sample_rate, channels.max(0) as usize, bands.max(0) as usize) {
            Ok(eq) => Box::into_raw(Box::new(eq)) as jlong,
            Err(_) => 0,
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeSetEqPreamp(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    preamp_db: jfloat,
) {
    contain_panic((), || {
        if handle != 0 {
            let eq = unsafe { &mut *(handle as *mut ParametricEqBank) };
            eq.set_preamp_db(preamp_db);
        }
    });
}

#[allow(clippy::too_many_arguments)]
#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeSetEqBand(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    index: jint,
    kind: jint,
    freq_hz: jdouble,
    q: jdouble,
    gain_db: jdouble,
    enabled: jboolean,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        if handle == 0 || index < 0 {
            return JNI_FALSE;
        }
        let eq = unsafe { &mut *(handle as *mut ParametricEqBank) };
        let ok = eq.set_band(
            index as usize,
            BiquadKind::from_i32(kind),
            freq_hz,
            q,
            gain_db,
            enabled != JNI_FALSE,
        );
        if ok { JNI_TRUE } else { JNI_FALSE }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeProcessEqFloat(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    samples: JFloatArray,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        if handle == 0 || samples.is_null() {
            return JNI_FALSE;
        }
        let eq = unsafe { &mut *(handle as *mut ParametricEqBank) };
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        if len == 0 {
            return JNI_FALSE;
        }
        let mut buf = vec![0.0f32; len];
        if env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return JNI_FALSE;
        }
        let processed = eq.process_f32_in_place(&mut buf);
        if processed && env.set_float_array_region(&samples, 0, &buf).is_err() {
            return JNI_FALSE;
        }
        if processed { JNI_TRUE } else { JNI_FALSE }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeReleaseEq(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    contain_panic((), || {
        if handle != 0 {
            unsafe {
                drop(Box::from_raw(handle as *mut ParametricEqBank));
            }
        }
    });
}

// ---------------------------------------------------------------------------
// LyricsUtils JNI (TTML, QRC, romanization, script detection)
// ---------------------------------------------------------------------------

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeParseTtmlJson(
    mut env: JNIEnv,
    _class: JClass,
    xml: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let Some(text) = read_jstring(&mut env, &xml) else {
            return ptr::null_mut();
        };
        let json = parse_ttml_to_json(&text);
        to_jstring(&mut env, &json)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeIsQrc(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let Some(text) = read_jstring(&mut env, &content) else {
            return JNI_FALSE;
        };
        if is_qrc(&text) { JNI_TRUE } else { JNI_FALSE }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeHasQrcWordTimings(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let Some(text) = read_jstring(&mut env, &content) else {
            return JNI_FALSE;
        };
        if has_word_timings(&text) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeParseQrcJson(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let Some(text) = read_jstring(&mut env, &content) else {
            return ptr::null_mut();
        };
        let json = parse_qrc_to_json(&text);
        to_jstring(&mut env, &json)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeQrcToEnhancedLrc(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let Some(text) = read_jstring(&mut env, &content) else {
            return ptr::null_mut();
        };
        let lrc = qrc_to_enhanced_lrc(&text);
        to_jstring(&mut env, &lrc)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeHiraganaToKatakana(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &text).unwrap_or_default();
        let converted = hiragana_to_katakana(&input);
        to_jstring(&mut env, &converted)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeKatakanaToRomaji(
    mut env: JNIEnv,
    _class: JClass,
    katakana: JString,
    next_katakana: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &katakana).unwrap_or_default();
        let next = read_jstring(&mut env, &next_katakana);
        let romaji = katakana_to_romaji(&input, next.as_deref());
        to_jstring(&mut env, &romaji)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeRomanizeKorean(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &text).unwrap_or_default();
        let out = romanize_korean(&input);
        to_jstring(&mut env, &out)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeRomanizeHindi(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &text).unwrap_or_default();
        let out = romanize_hindi(&input);
        to_jstring(&mut env, &out)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeRomanizeCyrillic(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &text).unwrap_or_default();
        let out = romanize_cyrillic(&input);
        to_jstring(&mut env, &out)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeLooksJapanese(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let Some(input) = read_jstring(&mut env, &text) else {
            return JNI_FALSE;
        };
        if looks_japanese(&input) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeLooksKorean(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let Some(input) = read_jstring(&mut env, &text) else {
            return JNI_FALSE;
        };
        if looks_korean(&input) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeLooksChinese(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let Some(input) = read_jstring(&mut env, &text) else {
            return JNI_FALSE;
        };
        if looks_chinese(&input) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeLooksHindi(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let Some(input) = read_jstring(&mut env, &text) else {
            return JNI_FALSE;
        };
        if looks_hindi(&input) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeDetectDominantLanguage(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &text).unwrap_or_default();
        let code = detect_dominant_language(&input);
        to_jstring(&mut env, code)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeNeedsOtherRomanization(
    mut env: JNIEnv,
    _class: JClass,
    line: JString,
    dominant_lang: JString,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        let text = read_jstring(&mut env, &line).unwrap_or_default();
        let lang = read_jstring(&mut env, &dominant_lang).unwrap_or_default();
        if needs_other_romanization(&text, &lang) {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    })
}

// ---------------------------------------------------------------------------
// TidalNativeBridge JNI (DASH MPD, dfLa/FLAC blocks, MP4 mdat payloads)
// ---------------------------------------------------------------------------

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_tidal_TidalNativeBridge_nativeExtractDashSegmentUrls(
    mut env: JNIEnv,
    _class: JClass,
    manifest_xml: JString,
) -> jobjectArray {
    contain_panic(ptr::null_mut(), || {
        let Some(xml) = read_jstring(&mut env, &manifest_xml) else {
            return ptr::null_mut();
        };
        match extract_dash_segment_urls(&xml) {
            Ok(urls) => to_jstring_array(&mut env, &urls),
            Err(_) => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_tidal_TidalNativeBridge_nativeExtractFlacMetadataBlocks(
    env: JNIEnv,
    _class: JClass,
    init_bytes: JByteArray,
) -> jbyteArray {
    contain_panic(ptr::null_mut(), || {
        if init_bytes.is_null() {
            return ptr::null_mut();
        }
        let Ok(raw) = env.convert_byte_array(&init_bytes) else {
            return ptr::null_mut();
        };
        let Ok(blocks) = extract_flac_metadata_blocks(&raw) else {
            return ptr::null_mut();
        };
        env.byte_array_from_slice(&blocks)
            .map(|a| a.into_raw())
            .unwrap_or(ptr::null_mut())
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_tidal_TidalNativeBridge_nativeExtractMdatPayloads(
    env: JNIEnv,
    _class: JClass,
    segment_bytes: JByteArray,
) -> jbyteArray {
    contain_panic(ptr::null_mut(), || {
        if segment_bytes.is_null() {
            return ptr::null_mut();
        }
        let Ok(raw) = env.convert_byte_array(&segment_bytes) else {
            return ptr::null_mut();
        };
        let Ok(payload) = extract_mdat_payloads(&raw) else {
            return ptr::null_mut();
        };
        env.byte_array_from_slice(&payload)
            .map(|a| a.into_raw())
            .unwrap_or(ptr::null_mut())
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_tidal_TidalNativeBridge_nativeNormalizeIsrc(
    mut env: JNIEnv,
    _class: JClass,
    raw: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &raw);
        match normalize_isrc(input.as_deref()) {
            Some(clean) => to_jstring(&mut env, &clean),
            None => ptr::null_mut(),
        }
    })
}

// ---------------------------------------------------------------------------
// NativeMediaUtils JNI (audio tags, format ranking, fuzzy search, playlists)
// ---------------------------------------------------------------------------

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeParseAudioTagsJson(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        if bytes.is_null() {
            return ptr::null_mut();
        }
        let Ok(raw) = env.convert_byte_array(&bytes) else {
            return ptr::null_mut();
        };
        match parse_audio_tags_to_json(&raw) {
            Some(json) => to_jstring(&mut env, &json),
            None => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeSniffMediaMime(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        if bytes.is_null() {
            return ptr::null_mut();
        }
        let Ok(raw) = env.convert_byte_array(&bytes) else {
            return ptr::null_mut();
        };
        match sniff_media_mime(&raw) {
            Some(mime) => to_jstring(&mut env, mime),
            None => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeInferMetadataFromFilenameJson(
    mut env: JNIEnv,
    _class: JClass,
    filename: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &filename).unwrap_or_default();
        let (track_num, artist, title) = infer_metadata_from_filename(&input);
        let mut out = String::with_capacity(128);
        out.push('{');
        out.push_str("\"title\":");
        push_json_str(&mut out, &title);
        if let Some(a) = &artist {
            out.push_str(",\"artist\":");
            push_json_str(&mut out, a);
        }
        if let Some(t) = track_num {
            let _ = write!(out, ",\"trackNumber\":{t}");
        }
        out.push('}');
        to_jstring(&mut env, &out)
    })
}

#[allow(clippy::too_many_arguments)]
#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeRankAudioFormats(
    mut env: JNIEnv,
    _class: JClass,
    codecs: JObjectArray,
    bitrates: JIntArray,
    sample_rates: JIntArray,
    is_direct_flags: JIntArray,
    quality_ordinal: jint,
    network_metered: jboolean,
) -> jintArray {
    contain_panic(ptr::null_mut(), || {
        let codec_vec = read_string_array(&mut env, &codecs);
        let n = codec_vec.len();
        if n == 0 || bitrates.is_null() || sample_rates.is_null() || is_direct_flags.is_null() {
            return to_jint_array(&mut env, &[]);
        }
        let mut br_buf = vec![0i32; n];
        let mut sr_buf = vec![0i32; n];
        let mut dir_int = vec![0i32; n];
        if env.get_int_array_region(&bitrates, 0, &mut br_buf).is_err()
            || env
                .get_int_array_region(&sample_rates, 0, &mut sr_buf)
                .is_err()
            || env
                .get_int_array_region(&is_direct_flags, 0, &mut dir_int)
                .is_err()
        {
            return to_jint_array(&mut env, &[]);
        }
        let codec_refs: Vec<&str> = codec_vec.iter().map(String::as_str).collect();
        let dir_bools: Vec<bool> = dir_int.iter().map(|&v| v != 0).collect();
        let ranked = rank_audio_formats(
            &codec_refs,
            &br_buf,
            &sr_buf,
            &dir_bools,
            TargetAudioQuality::from_i32(quality_ordinal),
            network_metered != JNI_FALSE,
        );
        to_jint_array(&mut env, &ranked)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeFuzzySimilarity(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
    candidate: JString,
) -> jdouble {
    contain_panic(0.0, || {
        let q = read_jstring(&mut env, &query).unwrap_or_default();
        let c = read_jstring(&mut env, &candidate).unwrap_or_default();
        fuzzy_similarity_score(&q, &c)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeExtractYouTubeVideoId(
    mut env: JNIEnv,
    _class: JClass,
    url_or_id: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &url_or_id).unwrap_or_default();
        match extract_youtube_video_id(&input) {
            Some(id) => to_jstring(&mut env, &id),
            None => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeExtractYouTubePlaylistId(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &url).unwrap_or_default();
        match extract_youtube_playlist_id(&input) {
            Some(id) => to_jstring(&mut env, &id),
            None => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeStripTrackingParams(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let input = read_jstring(&mut env, &url).unwrap_or_default();
        let cleaned = strip_tracking_query_params(&input);
        to_jstring(&mut env, &cleaned)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeDeduplicateTracks(
    mut env: JNIEnv,
    _class: JClass,
    ids: JObjectArray,
    titles: JObjectArray,
    artists: JObjectArray,
    durations_ms: JLongArray,
) -> jintArray {
    contain_panic(ptr::null_mut(), || {
        let id_vec = read_string_array(&mut env, &ids);
        let title_vec = read_string_array(&mut env, &titles);
        let artist_vec = read_string_array(&mut env, &artists);
        let d_len = if durations_ms.is_null() {
            0
        } else {
            env.get_array_length(&durations_ms).unwrap_or(0).max(0) as usize
        };
        let mut dur_buf = vec![0i64; d_len];
        if d_len > 0
            && env
                .get_long_array_region(&durations_ms, 0, &mut dur_buf)
                .is_err()
        {
            return to_jint_array(&mut env, &[]);
        }
        let id_refs: Vec<&str> = id_vec.iter().map(String::as_str).collect();
        let title_refs: Vec<&str> = title_vec.iter().map(String::as_str).collect();
        let artist_refs: Vec<&str> = artist_vec.iter().map(String::as_str).collect();
        let kept = deduplicate_track_indices(&id_refs, &title_refs, &artist_refs, &dur_buf);
        to_jint_array(&mut env, &kept)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeSmartShuffle(
    mut env: JNIEnv,
    _class: JClass,
    artist_keys: JObjectArray,
    seed: jlong,
) -> jintArray {
    contain_panic(ptr::null_mut(), || {
        let keys = read_string_array(&mut env, &artist_keys);
        let key_refs: Vec<&str> = keys.iter().map(String::as_str).collect();
        let order = smart_shuffle_indices(&key_refs, seed as u64);
        to_jint_array(&mut env, &order)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeParseM3uJson(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &content).unwrap_or_default();
        let json = parse_m3u_to_json(&text);
        to_jstring(&mut env, &json)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeParseCueJson(
    mut env: JNIEnv,
    _class: JClass,
    cue: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &cue).unwrap_or_default();
        let json = parse_cue_to_json(&text);
        to_jstring(&mut env, &json)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeSha256Hex(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        if bytes.is_null() {
            return ptr::null_mut();
        }
        let Ok(raw) = env.convert_byte_array(&bytes) else {
            return ptr::null_mut();
        };
        let hex = sha256_hex(&raw);
        to_jstring(&mut env, &hex)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeParsePlsJson(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &content).unwrap_or_default();
        let json = parse_pls_to_json(&text);
        to_jstring(&mut env, &json)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeParseContentRange(
    mut env: JNIEnv,
    _class: JClass,
    header: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &header).unwrap_or_default();
        let Some(meta) = parse_content_range(&text) else {
            return ptr::null_mut();
        };
        let total_str = meta
            .total_len
            .map_or_else(|| "null".to_string(), |v| v.to_string());
        let json = format!(
            "{{\"start\":{},\"end\":{},\"totalLength\":{total_str}}}",
            meta.start, meta.end
        );
        to_jstring(&mut env, &json)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeSelectCacheEvictions(
    mut env: JNIEnv,
    _class: JClass,
    sizes: JLongArray,
    last_access_ms: JLongArray,
    pin_flags: JIntArray,
    max_bytes: jlong,
) -> jintArray {
    contain_panic(ptr::null_mut(), || {
        if sizes.is_null() || last_access_ms.is_null() || pin_flags.is_null() || max_bytes < 0 {
            return to_jint_array(&mut env, &[]);
        }
        let n = env.get_array_length(&sizes).unwrap_or(0).max(0) as usize;
        if n == 0 {
            return to_jint_array(&mut env, &[]);
        }
        let mut s_buf = vec![0i64; n];
        let mut t_buf = vec![0i64; n];
        let mut p_buf = vec![0i32; n];
        if env.get_long_array_region(&sizes, 0, &mut s_buf).is_err()
            || env
                .get_long_array_region(&last_access_ms, 0, &mut t_buf)
                .is_err()
            || env
                .get_int_array_region(&pin_flags, 0, &mut p_buf)
                .is_err()
        {
            return to_jint_array(&mut env, &[]);
        }
        let u_sizes: Vec<u64> = s_buf.iter().map(|&v| v.max(0) as u64).collect();
        let pins: Vec<bool> = p_buf.iter().map(|&v| v != 0).collect();
        let evicted = select_cache_evictions(&u_sizes, &t_buf, &pins, max_bytes as u64);
        to_jint_array(&mut env, &evicted)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeThreeBandEnergy(
    mut env: JNIEnv,
    _class: JClass,
    mono: JFloatArray,
    sample_rate: jint,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if mono.is_null() || sample_rate <= 0 {
            return to_jfloat_array(&mut env, &[0.0, 0.0, 0.0]);
        }
        let len = env.get_array_length(&mono).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0.0f32; len];
        if len > 0 && env.get_float_array_region(&mono, 0, &mut buf).is_err() {
            return to_jfloat_array(&mut env, &[0.0, 0.0, 0.0]);
        }
        let (b, m, h) = compute_three_band_energy(&buf, sample_rate);
        to_jfloat_array(&mut env, &[b, m, h])
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeTruePeak4x(
    mut env: JNIEnv,
    _class: JClass,
    samples: JFloatArray,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if samples.is_null() {
            return to_jfloat_array(&mut env, &[0.0, -120.0]);
        }
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0.0f32; len];
        if len > 0 && env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return to_jfloat_array(&mut env, &[0.0, -120.0]);
        }
        let (lin, db) = measure_true_peak_4x(&buf);
        to_jfloat_array(&mut env, &[lin, db])
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativePcmS24LeToFloat(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if bytes.is_null() {
            return to_jfloat_array(&mut env, &[]);
        }
        let Ok(raw) = env.convert_byte_array(&bytes) else {
            return to_jfloat_array(&mut env, &[]);
        };
        let converted = pcm_s24_le_to_f32(&raw);
        to_jfloat_array(&mut env, &converted)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeQuantizeFloatToShortTpdf(
    env: JNIEnv,
    _class: JClass,
    samples: JFloatArray,
    seed: jlong,
) -> jni::sys::jshortArray {
    contain_panic(ptr::null_mut(), || {
        if samples.is_null() {
            return ptr::null_mut();
        }
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0.0f32; len];
        if len > 0 && env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return ptr::null_mut();
        }
        let quantized = quantize_f32_to_i16_tpdf(&buf, seed as u64);
        match env.new_short_array(quantized.len() as jint) {
            Ok(arr) => {
                if !quantized.is_empty()
                    && env.set_short_array_region(&arr, 0, &quantized).is_err()
                {
                    return ptr::null_mut();
                }
                arr.into_raw()
            }
            Err(_) => ptr::null_mut(),
        }
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeStretchWsola(
    mut env: JNIEnv,
    _class: JClass,
    mono: JFloatArray,
    sample_rate: jint,
    tempo: jfloat,
) -> jfloatArray {
    contain_panic(ptr::null_mut(), || {
        if mono.is_null() || sample_rate <= 0 {
            return to_jfloat_array(&mut env, &[]);
        }
        let len = env.get_array_length(&mono).unwrap_or(0).max(0) as usize;
        let mut buf = vec![0.0f32; len];
        if len > 0 && env.get_float_array_region(&mono, 0, &mut buf).is_err() {
            return to_jfloat_array(&mut env, &[]);
        }
        let stretched = time_stretch_wsola_mono(&buf, sample_rate, tempo);
        to_jfloat_array(&mut env, &stretched)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeReplayGainDb(
    _env: JNIEnv,
    _class: JClass,
    measured_lufs: jdouble,
    target_lufs: jdouble,
) -> jdouble {
    contain_panic(0.0, || replay_gain_db_for_target(measured_lufs, target_lufs))
}

#[allow(clippy::too_many_arguments)]
#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeCreateCompressor(
    _env: JNIEnv,
    _class: JClass,
    sample_rate: jint,
    threshold_db: jfloat,
    ratio: jfloat,
    knee_db: jfloat,
    attack_ms: jfloat,
    release_ms: jfloat,
    makeup_db: jfloat,
) -> jlong {
    contain_panic(0, || {
        let comp = DynamicsCompressor::new(
            sample_rate,
            threshold_db,
            ratio,
            knee_db,
            attack_ms,
            release_ms,
            makeup_db,
        );
        Box::into_raw(Box::new(comp)) as jlong
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeProcessCompressorFloat(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    samples: JFloatArray,
    channels: jint,
) -> jboolean {
    contain_panic(JNI_FALSE, || {
        if handle == 0 || samples.is_null() || channels <= 0 {
            return JNI_FALSE;
        }
        let comp = unsafe { &mut *(handle as *mut DynamicsCompressor) };
        let len = env.get_array_length(&samples).unwrap_or(0).max(0) as usize;
        if len == 0 {
            return JNI_FALSE;
        }
        let mut buf = vec![0.0f32; len];
        if env.get_float_array_region(&samples, 0, &mut buf).is_err() {
            return JNI_FALSE;
        }
        comp.process_in_place(&mut buf, channels as usize);
        if env.set_float_array_region(&samples, 0, &buf).is_err() {
            return JNI_FALSE;
        }
        JNI_TRUE
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_playback_automix_AutoMixNativeDsp_nativeReleaseCompressor(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    contain_panic((), || {
        if handle != 0 {
            unsafe {
                drop(Box::from_raw(handle as *mut DynamicsCompressor));
            }
        }
    });
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeSrtToLrc(
    mut env: JNIEnv,
    _class: JClass,
    content: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &content).unwrap_or_default();
        let lrc = srt_or_vtt_to_lrc(&text);
        to_jstring(&mut env, &lrc)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_lyrics_LyricsUtils_nativeLrcToSrt(
    mut env: JNIEnv,
    _class: JClass,
    lrc: JString,
    default_tail_ms: jlong,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &lrc).unwrap_or_default();
        let srt = lrc_to_srt(&text, default_tail_ms);
        to_jstring(&mut env, &srt)
    })
}

#[unsafe(no_mangle)]
extern "system" fn Java_moe_rukamori_archivetune_utils_NativeMediaUtils_nativeParseXspfJson(
    mut env: JNIEnv,
    _class: JClass,
    xml: JString,
) -> jstring {
    contain_panic(ptr::null_mut(), || {
        let text = read_jstring(&mut env, &xml).unwrap_or_default();
        let json = parse_xspf_to_json(&text);
        to_jstring(&mut env, &json)
    })
}

#[cfg(test)]
mod tests {
    use super::contain_panic;

    #[test]
    fn ext_bridge_contains_panics_safely() {
        assert_eq!(contain_panic(-1, || panic!("simulated jni panic")), -1);
        assert_eq!(contain_panic(-1, || 42), 42);
    }
}
