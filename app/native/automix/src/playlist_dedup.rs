// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::collections::{HashMap, HashSet};
use std::fmt::Write as _;

use crate::track_matching::normalize;
use crate::ttml_parser::push_json_str;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct M3uEntry {
    pub uri: String,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub duration_seconds: Option<i64>,
}

/// Parses standard M3U / M3U8 playlists including `#EXTINF:dur,Artist - Title` directives.
pub fn parse_m3u_playlist(content: &str) -> Vec<M3uEntry> {
    let mut entries = Vec::new();
    let mut pending_dur: Option<i64> = None;
    let mut pending_artist: Option<String> = None;
    let mut pending_title: Option<String> = None;

    for raw_line in content.lines() {
        let line = raw_line.trim().trim_start_matches('\u{FEFF}');
        if line.is_empty() {
            continue;
        }
        if let Some(after) = line.strip_prefix('#') {
            let upper = after.to_ascii_uppercase();
            if upper.starts_with("EXTINF:") {
                let spec = &after[7..];
                let (dur_part, meta_part) = spec.split_once(',').unwrap_or((spec, ""));
                let dur_num = dur_part
                    .split_whitespace()
                    .next()
                    .and_then(|s| s.parse::<i64>().ok())
                    .filter(|&d| d >= 0);
                pending_dur = dur_num;

                let cleaned_meta = meta_part.trim();
                if !cleaned_meta.is_empty() {
                    if let Some((art, tit)) = cleaned_meta.split_once(" - ") {
                        let a = art.trim();
                        let t = tit.trim();
                        if !a.is_empty() {
                            pending_artist = Some(a.to_string());
                        }
                        if !t.is_empty() {
                            pending_title = Some(t.to_string());
                        }
                    } else {
                        pending_title = Some(cleaned_meta.to_string());
                    }
                }
            } else if let Some(val) = after
                .strip_prefix("EXTART:")
                .or_else(|| after.strip_prefix("extart:"))
            {
                let a = val.trim();
                if !a.is_empty() {
                    pending_artist = Some(a.to_string());
                }
            }
            continue;
        }

        entries.push(M3uEntry {
            uri: line.to_string(),
            title: pending_title.take(),
            artist: pending_artist.take(),
            duration_seconds: pending_dur.take(),
        });
    }

    entries
}

/// Exports entries to UTF-8 M3U8 playlist text.
pub fn format_m3u8_playlist(entries: &[M3uEntry]) -> String {
    let mut out = String::with_capacity(entries.len() * 64 + 16);
    out.push_str("#EXTM3U\n");
    for entry in entries {
        let dur = entry.duration_seconds.unwrap_or(-1);
        match (&entry.artist, &entry.title) {
            (Some(a), Some(t)) => {
                let _ = writeln!(out, "#EXTINF:{dur},{a} - {t}");
            }
            (None, Some(t)) => {
                let _ = writeln!(out, "#EXTINF:{dur},{t}");
            }
            (Some(a), None) => {
                let _ = writeln!(out, "#EXTINF:{dur},{a}");
            }
            (None, None) => {
                let _ = writeln!(out, "#EXTINF:{dur},");
            }
        }
        out.push_str(&entry.uri);
        out.push('\n');
    }
    out
}

pub fn parse_m3u_to_json(content: &str) -> String {
    let entries = parse_m3u_playlist(content);
    let mut out = String::with_capacity(entries.len() * 96 + 4);
    out.push('[');
    for (i, e) in entries.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str("{\"uri\":");
        push_json_str(&mut out, &e.uri);
        if let Some(t) = &e.title {
            out.push_str(",\"title\":");
            push_json_str(&mut out, t);
        }
        if let Some(a) = &e.artist {
            out.push_str(",\"artist\":");
            push_json_str(&mut out, a);
        }
        if let Some(d) = e.duration_seconds {
            let _ = write!(out, ",\"durationSeconds\":{d}");
        }
        out.push('}');
    }
    out.push(']');
    out
}

/// Normalizes a 12-character ISRC code (uppercase ASCII letters/digits, rejecting malformed values).
pub fn normalize_isrc(raw: Option<&str>) -> Option<String> {
    let text = raw?;
    let compact: String = text
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .map(|c| c.to_ascii_uppercase())
        .collect();
    if compact.len() != 12 {
        return None;
    }
    let bytes = compact.as_bytes();
    let valid = bytes[0..2].iter().all(u8::is_ascii_alphabetic)
        && bytes[2..5].iter().all(u8::is_ascii_alphanumeric)
        && bytes[5..12].iter().all(u8::is_ascii_digit);
    valid.then_some(compact)
}

fn is_valid_yt_id(id: &str) -> bool {
    id.len() == 11
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

/// Extracts an 11-char YouTube/YouTube Music video ID from arbitrary share or watch URLs.
pub fn extract_youtube_video_id(url_or_id: &str) -> Option<String> {
    let trimmed = url_or_id.trim();
    if trimmed.is_empty() {
        return None;
    }
    if is_valid_yt_id(trimmed) {
        return Some(trimmed.to_string());
    }
    let lower = trimmed.to_ascii_lowercase();
    let is_yt = lower.contains("youtu.be")
        || lower.contains("youtube.com")
        || lower.contains("youtube-nocookie.com");
    if !is_yt {
        return None;
    }

    if let Some(q_idx) = trimmed.find('?') {
        let query = trimmed[q_idx + 1..].split('#').next().unwrap_or("");
        for param in query.split('&') {
            if let Some((k, v)) = param.split_once('=')
                && k == "v"
                && is_valid_yt_id(v)
            {
                return Some(v.to_string());
            }
        }
    }

    let path_only = trimmed.split('?').next().unwrap_or(trimmed);
    let path_clean = path_only.split('#').next().unwrap_or(path_only);
    if let Some(pos) = lower.find("youtu.be/") {
        let seg = path_clean[pos + 9..].split('/').next().unwrap_or("");
        if is_valid_yt_id(seg) {
            return Some(seg.to_string());
        }
    }
    for prefix in ["/shorts/", "/embed/", "/live/", "/v/"] {
        if let Some(pos) = lower.find(prefix) {
            let seg = path_clean[pos + prefix.len()..]
                .split('/')
                .next()
                .unwrap_or("");
            if is_valid_yt_id(seg) {
                return Some(seg.to_string());
            }
        }
    }
    None
}

/// Extracts a YouTube playlist/album `list=` identifier from a URL.
pub fn extract_youtube_playlist_id(url: &str) -> Option<String> {
    let q_idx = url.find('?')?;
    let query = url[q_idx + 1..].split('#').next().unwrap_or("");
    for param in query.split('&') {
        if let Some((k, v)) = param.split_once('=')
            && k == "list"
            && !v.is_empty()
            && v.len() <= 128
        {
            let valid = v
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_');
            if valid {
                return Some(v.to_string());
            }
        }
    }
    None
}

/// Strips tracking query params (`si`, `utm_*`, `feature`, `pp`, `ab_channel`, `fbclid`).
pub fn strip_tracking_query_params(url: &str) -> String {
    let (base_and_q, frag) = url
        .split_once('#')
        .map_or((url, None), |(a, f)| (a, Some(f)));
    let Some((base, query)) = base_and_q.split_once('?') else {
        return url.to_string();
    };
    let kept: Vec<&str> = query
        .split('&')
        .filter(|kv| {
            if kv.is_empty() {
                return false;
            }
            let key = kv.split('=').next().unwrap_or("").to_ascii_lowercase();
            !(key == "si"
                || key == "feature"
                || key == "pp"
                || key == "ab_channel"
                || key == "fbclid"
                || key == "gclid"
                || key.starts_with("utm_"))
        })
        .collect();

    let mut out = String::with_capacity(url.len());
    out.push_str(base);
    if !kept.is_empty() {
        out.push('?');
        out.push_str(&kept.join("&"));
    }
    if let Some(f) = frag {
        out.push('#');
        out.push_str(f);
    }
    out
}

/// Deduplicates candidate indices by `(normalized title, normalized artist)` and optional ID,
/// preserving first occurrence order.
pub fn deduplicate_track_indices(
    ids: &[&str],
    titles: &[&str],
    artists: &[&str],
    durations_ms: &[i64],
) -> Vec<usize> {
    let n = ids
        .len()
        .min(titles.len())
        .min(artists.len())
        .min(durations_ms.len());
    let mut seen_ids: HashSet<&str> = HashSet::with_capacity(n);
    let mut seen_meta: HashSet<(String, String, i64)> = HashSet::with_capacity(n);
    let mut kept = Vec::with_capacity(n);

    for i in 0..n {
        let id = ids[i].trim();
        if !id.is_empty() && !seen_ids.insert(id) {
            continue;
        }
        let norm_title = normalize(Some(titles[i]));
        let norm_artist = normalize(Some(artists[i]));
        let dur_bucket = if durations_ms[i] > 0 {
            (durations_ms[i] + 1_500) / 3_000
        } else {
            -1
        };
        if !norm_title.is_empty()
            && !norm_artist.is_empty()
            && !seen_meta.insert((norm_title, norm_artist, dur_bucket))
        {
            continue;
        }
        kept.push(i);
    }
    kept
}

/// Produces a deterministic smart-shuffle permutation minimizing back-to-back same-artist plays
/// using per-artist even spacing across the timeline with SplitMix64 jitter.
pub fn smart_shuffle_indices(artist_keys: &[&str], seed: u64) -> Vec<usize> {
    let n = artist_keys.len();
    if n <= 1 {
        return (0..n).collect();
    }

    let mut groups: HashMap<String, Vec<usize>> = HashMap::new();
    for (idx, &artist) in artist_keys.iter().enumerate() {
        let key = normalize(Some(artist));
        groups.entry(key).or_default().push(idx);
    }

    let mut state = seed ^ 0x9E37_79B9_7F4A_7C15;
    let mut next_u64 = || -> u64 {
        state = state.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = state;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    };

    let mut scheduled: Vec<(f64, usize)> = Vec::with_capacity(n);
    let mut group_keys: Vec<String> = groups.keys().cloned().collect();
    group_keys.sort();

    for key in group_keys {
        if let Some(mut indices) = groups.remove(&key) {
            // Fisher-Yates shuffle inside artist bucket
            for i in (1..indices.len()).rev() {
                let j = (next_u64() as usize) % (i + 1);
                indices.swap(i, j);
            }
            let count = indices.len() as f64;
            let offset = ((next_u64() % 10_000) as f64) / 10_000.0;
            for (rank, track_idx) in indices.into_iter().enumerate() {
                let jitter = ((((next_u64() % 1_000) as f64) / 1_000.0) - 0.5) * 0.35;
                let slot = ((rank as f64) + offset + jitter) / count;
                scheduled.push((slot, track_idx));
            }
        }
    }

    scheduled.sort_by(|a, b| a.0.total_cmp(&b.0).then_with(|| a.1.cmp(&b.1)));
    scheduled.into_iter().map(|(_, idx)| idx).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_and_roundtrips_m3u8() {
        let raw = concat!(
            "#EXTM3U\n",
            "#EXTINF:215,Artist A - Song 1\n",
            "/music/a.flac\n",
            "https://example.com/b.mp3\n"
        );
        let entries = parse_m3u_playlist(raw);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].artist.as_deref(), Some("Artist A"));
        assert_eq!(entries[0].title.as_deref(), Some("Song 1"));
        assert_eq!(entries[0].duration_seconds, Some(215));
        assert_eq!(entries[1].uri, "https://example.com/b.mp3");
        let serialized = format_m3u8_playlist(&entries);
        assert!(serialized.starts_with("#EXTM3U"));
        assert_eq!(parse_m3u_playlist(&serialized).len(), 2);
    }

    #[test]
    fn extracts_youtube_ids_and_strips_tracking() {
        assert_eq!(
            extract_youtube_video_id("https://youtu.be/dQw4w9WgXcQ?si=abc123"),
            Some("dQw4w9WgXcQ".to_string())
        );
        assert_eq!(
            extract_youtube_video_id("https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"),
            Some("dQw4w9WgXcQ".to_string())
        );
        let tracked = "https://youtube.com/watch?v=dQw4w9WgXcQ&si=xyz&utm_source=x";
        assert_eq!(
            strip_tracking_query_params(tracked),
            "https://youtube.com/watch?v=dQw4w9WgXcQ"
        );
        assert_eq!(
            extract_youtube_playlist_id("https://music.youtube.com/playlist?list=PL1234_abc&si=z"),
            Some("PL1234_abc".to_string())
        );
    }

    #[test]
    fn deduplicates_and_smart_shuffles_without_artist_clusters() {
        let ids = ["1", "2", "1", "3"];
        let titles = ["Echoes", "Mirage", "Echoes", "Mirage"];
        let artists = ["Alpha", "Beta", "Alpha", "Beta"];
        let durs = [180_000, 200_000, 180_500, 200_100];
        let kept = deduplicate_track_indices(&ids, &titles, &artists, &durs);
        assert_eq!(kept, vec![0, 1]);

        let shuf_artists = ["A", "A", "A", "B", "B", "C", "D"];
        let order = smart_shuffle_indices(&shuf_artists, 42);
        assert_eq!(order.len(), 7);
        let back_to_back = order
            .windows(2)
            .filter(|w| shuf_artists[w[0]] == shuf_artists[w[1]])
            .count();
        assert_eq!(back_to_back, 0);
    }
}
