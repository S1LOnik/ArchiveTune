// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

pub mod embedded_lyrics;
mod jni_bridge;
pub mod lyrics_parser;
pub mod planner;
mod spectrum;
mod tempo;
pub mod track_matching;

pub use embedded_lyrics::extract_embedded_lyrics;
pub use planner::{AutoMixAnalysisRaw, AutoMixPlanRaw, AutoMixStyleRaw, plan_automix};
pub use spectrum::{Spectrum, SpectrumError};
pub use tempo::{TempoEstimate, estimate_tempo};
pub use track_matching::{
    DURATION_TOLERANCE_MS, MIN_MATCH_SCORE, best_match_index, duration_matches,
    has_version_mismatch, normalize, normalize_title, score, search_artist, search_title,
    significant_tokens, token_overlap,
};
