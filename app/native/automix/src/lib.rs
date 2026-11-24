// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

pub mod audio_codec_pcm;
pub mod audio_dsp_eq;
pub mod audio_tags;
pub mod cache_crypto;
pub mod cue_pls_parser;
pub mod dsp_filters;
pub mod embedded_lyrics;
pub mod http_range_sync;
mod jni_bridge;
mod jni_bridge_ext;
pub mod lyrics_export_lrc;
pub mod lyrics_parser;
pub mod planner;
pub mod playback_dynamics;
pub mod playlist_dedup;
pub mod qrc_parser;
pub mod romanization;
mod spectrum;
pub mod stream_selector;
mod tempo;
pub mod track_matching;
pub mod ttml_parser;
pub mod waveform_peaks;

pub use embedded_lyrics::extract_embedded_lyrics;
pub use planner::{AutoMixAnalysisRaw, AutoMixPlanRaw, AutoMixStyleRaw, plan_automix};
pub use spectrum::{Spectrum, SpectrumError};
pub use tempo::{TempoEstimate, estimate_tempo};
pub use track_matching::{
    DURATION_TOLERANCE_MS, MIN_MATCH_SCORE, best_match_index, duration_matches,
    has_version_mismatch, normalize, normalize_title, score, search_artist, search_title,
    significant_tokens, token_overlap,
};
