// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use regex::Regex;
use std::collections::HashSet;
use std::sync::LazyLock;
use unicode_normalization::UnicodeNormalization;

pub const MIN_MATCH_SCORE: i32 = 60;
pub const DURATION_TOLERANCE_MS: i64 = 45_000;

static STOP_WORDS: LazyLock<HashSet<&'static str>> = LazyLock::new(|| {
    [
        "the",
        "a",
        "an",
        "of",
        "and",
        "feat",
        "ft",
        "featuring",
        "with",
    ]
    .into_iter()
    .collect()
});

static VERSION_TOKENS: &[&str] = &[
    "remix",
    "acoustic",
    "live",
    "instrumental",
    "demo",
    "edit",
    "mix",
    "version",
];

static FEAT_SUFFIX_RE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\b(feat|ft|featuring)\b.*$").unwrap());

static VERSION_WORD_RE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"\b(explicit|clean|remaster|remastered|version|audio|official)\b").unwrap()
});

static SEARCH_TITLE_FEAT_RE: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)\s*[\[(]\s*(feat\.?|ft\.?|featuring)\b.*?[\])]").unwrap());

static SEARCH_TITLE_VERSION_RE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)\s*-\s*(explicit|clean|remaster(?:ed)?|audio|official)\b.*$").unwrap()
});

static MULTI_SPACE_RE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").unwrap());

fn is_combining_mark(c: char) -> bool {
    matches!(c,
        '\u{0300}'..='\u{036F}'
        | '\u{1AB0}'..='\u{1AFF}'
        | '\u{1DC0}'..='\u{1DFF}'
        | '\u{20D0}'..='\u{20FF}'
        | '\u{FE20}'..='\u{FE2F}'
    )
}

/// Normalizes a string by converting to lowercase, decomposing Unicode diacritics (NFD),
/// stripping non-spacing combining marks, replacing non-ascii-alphanumeric with spaces,
/// and collapsing spaces.
pub fn normalize(value: Option<&str>) -> String {
    let Some(val) = value else {
        return String::new();
    };

    let lower = val.to_lowercase();
    let nfd: String = lower.nfd().filter(|&c| !is_combining_mark(c)).collect();

    let mut result = String::with_capacity(nfd.len());
    let mut prev_space = true;
    for c in nfd.chars() {
        if c.is_ascii_alphanumeric() {
            result.push(c);
            prev_space = false;
        } else if !prev_space {
            result.push(' ');
            prev_space = true;
        }
    }

    if result.ends_with(' ') {
        result.pop();
    }
    result
}

/// Normalizes title by removing trailing featuring patterns and version tokens.
pub fn normalize_title(value: &str) -> String {
    let normalized = normalize(Some(value));
    let no_feat = FEAT_SUFFIX_RE.replace(&normalized, "");
    let no_version = VERSION_WORD_RE.replace_all(&no_feat, " ");
    let collapsed = MULTI_SPACE_RE.replace_all(&no_version, " ");
    collapsed.trim().to_string()
}

pub fn search_title(value: &str) -> String {
    let trimmed = value.trim();
    let step1 = SEARCH_TITLE_FEAT_RE.replace_all(trimmed, "");
    let step2 = SEARCH_TITLE_VERSION_RE.replace_all(&step1, "");
    let step3 = MULTI_SPACE_RE.replace_all(&step2, " ");
    step3.trim().to_string()
}

pub fn search_artist(value: &str) -> String {
    let trimmed = value.trim();
    let before_comma = trimmed.split(',').next().unwrap_or("");
    let collapsed = MULTI_SPACE_RE.replace_all(before_comma, " ");
    collapsed.trim().to_string()
}

pub fn significant_tokens(value: &str) -> HashSet<String> {
    value
        .split(' ')
        .map(|s| s.trim())
        .filter(|s| s.len() >= 2 && !STOP_WORDS.contains(s))
        .map(|s| s.to_string())
        .collect()
}

pub fn token_overlap(wanted: &HashSet<String>, candidate: &HashSet<String>) -> f64 {
    if wanted.is_empty() || candidate.is_empty() {
        return 0.0;
    }
    let shared = wanted.intersection(candidate).count();
    let max_len = wanted.len().max(candidate.len());
    shared as f64 / max_len as f64
}

pub fn duration_matches(wanted_ms: Option<i64>, candidate_ms: Option<i64>) -> bool {
    match (wanted_ms, candidate_ms) {
        (Some(w), Some(c)) => (w - c).abs() <= DURATION_TOLERANCE_MS,
        _ => true,
    }
}

pub fn has_version_mismatch(wanted: &str, candidate: &str) -> bool {
    let padded_wanted = format!(" {wanted} ");
    let padded_cand = format!(" {candidate} ");
    VERSION_TOKENS.iter().any(|&token| {
        let needle = format!(" {token} ");
        padded_wanted.contains(&needle) != padded_cand.contains(&needle)
    })
}

pub fn score(
    wanted_title: &str,
    wanted_artists: &[&str],
    candidate_title: &str,
    candidate_artist: &str,
    wanted_duration_ms: Option<i64>,
    candidate_duration_ms: Option<i64>,
) -> i32 {
    if wanted_title.trim().is_empty() || candidate_title.trim().is_empty() {
        return i32::MIN;
    }
    if has_version_mismatch(wanted_title, candidate_title) {
        return i32::MIN;
    }

    let wanted_tokens = significant_tokens(wanted_title);
    let candidate_tokens = significant_tokens(candidate_title);
    let title_overlap = token_overlap(&wanted_tokens, &candidate_tokens);
    if title_overlap < 0.5 {
        return i32::MIN;
    }

    let mut final_score = (title_overlap * 100.0) as i32;

    if !wanted_artists.is_empty() && !candidate_artist.trim().is_empty() {
        let cand_tokens = significant_tokens(candidate_artist);
        let mut max_artist_overlap = 0.0f64;
        for wanted_art in wanted_artists {
            let art_tokens = significant_tokens(wanted_art);
            let overlap = token_overlap(&art_tokens, &cand_tokens);
            if overlap > max_artist_overlap {
                max_artist_overlap = overlap;
            }
        }
        final_score += (max_artist_overlap * 60.0) as i32;
    }

    if let (Some(w_dur), Some(c_dur)) = (wanted_duration_ms, candidate_duration_ms) {
        if duration_matches(Some(w_dur), Some(c_dur)) {
            final_score += 30;
        } else {
            final_score -= 40;
        }
    }

    final_score
}

/// Evaluates a batch of candidates against a target and returns the 0-based index of the best match,
/// or -1 if no candidate reaches MIN_MATCH_SCORE (60).
/// In case of ties, preserves the earliest candidate index.
pub fn best_match_index(
    target_title: &str,
    target_artists: &[&str],
    target_duration_ms: Option<i64>,
    candidate_titles: &[&str],
    candidate_primary_artists: &[&str],
    candidate_durations_ms: &[Option<i64>],
) -> i32 {
    let norm_target_title = normalize_title(target_title);
    let norm_target_artists: Vec<String> =
        target_artists.iter().map(|&a| normalize(Some(a))).collect();
    let norm_target_artist_refs: Vec<&str> =
        norm_target_artists.iter().map(|s| s.as_str()).collect();

    let mut best_idx = -1;
    let mut best_score = i32::MIN;

    let count = candidate_titles.len();
    for i in 0..count {
        let cand_title = candidate_titles.get(i).copied().unwrap_or("");
        let cand_artist = candidate_primary_artists.get(i).copied().unwrap_or("");
        let cand_duration = candidate_durations_ms.get(i).copied().flatten();

        let norm_cand_title = normalize_title(cand_title);
        let norm_cand_artist = normalize(Some(cand_artist));

        let current_score = score(
            &norm_target_title,
            &norm_target_artist_refs,
            &norm_cand_title,
            &norm_cand_artist,
            target_duration_ms,
            cand_duration,
        );

        if current_score >= MIN_MATCH_SCORE && current_score > best_score {
            best_score = current_score;
            best_idx = i as i32;
        }
    }

    best_idx
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_normalize_diacritics_and_punctuation() {
        assert_eq!(normalize(Some("Beyoncé")), "beyonce");
        assert_eq!(normalize(Some("Mötley Crüe")), "motley crue");
        assert_eq!(normalize(Some("AC/DC")), "ac dc");
        assert_eq!(normalize(Some("Dvořák")), "dvorak");
        assert_eq!(normalize(None), "");
        assert_eq!(normalize(Some("   ")), "");
        assert_eq!(normalize(Some("Кино - Группа крови")), "");
        assert_eq!(normalize(Some("YOASOBI - 夜に駆ける")), "yoasobi");
    }

    #[test]
    fn test_normalize_title() {
        assert_eq!(
            normalize_title("Song Title (feat. Drake & Future)"),
            "song title"
        );
        assert_eq!(
            normalize_title("Song Title - Remastered 2021"),
            "song title 2021"
        );
        assert_eq!(
            normalize_title("Hotel California - Live On MTV / 1994"),
            "hotel california live on mtv 1994"
        );
    }

    #[test]
    fn test_duration_matches() {
        assert!(duration_matches(Some(180_000), Some(200_000)));
        assert!(duration_matches(Some(180_000), Some(225_000)));
        assert!(!duration_matches(Some(180_000), Some(226_000)));
        assert!(duration_matches(None, Some(180_000)));
        assert!(duration_matches(Some(180_000), None));
        assert!(duration_matches(None, None));
    }

    #[test]
    fn test_score_version_mismatch() {
        let sc = score(
            "blinding lights acoustic",
            &["the weeknd"],
            "blinding lights",
            "the weeknd",
            Some(200_000),
            Some(200_000),
        );
        assert_eq!(sc, i32::MIN);
    }

    #[test]
    fn test_score_overlap_and_bonus() {
        let sc = score(
            "hotel california",
            &["eagles"],
            "hotel california",
            "eagles",
            Some(390_000),
            Some(391_000),
        );
        assert_eq!(sc, 190);
    }

    #[test]
    fn test_best_match_index_preserves_ties() {
        let best_idx = best_match_index(
            "Starboy",
            &["The Weeknd", "Daft Punk"],
            Some(230_000),
            &["Starboy", "Starboy", "Starboy (Kygo Remix)"],
            &["The Weeknd", "The Weeknd", "The Weeknd"],
            &[Some(230_500), Some(230_500), Some(240_000)],
        );
        assert_eq!(best_idx, 0);
    }

    #[test]
    fn test_best_match_index_below_threshold() {
        let best_idx = best_match_index(
            "Bohemian Rhapsody",
            &["Queen"],
            Some(354_000),
            &["Another One Bites the Dust"],
            &["Queen"],
            &[Some(215_000)],
        );
        assert_eq!(best_idx, -1);
    }
}
