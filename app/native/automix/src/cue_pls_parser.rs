// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::collections::HashMap;
use std::fmt::Write as _;

use crate::ttml_parser::push_json_str;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CueTrack {
    pub track_number: i32,
    pub title: Option<String>,
    pub performer: Option<String>,
    pub album: Option<String>,
    pub file: Option<String>,
    pub start_ms: i64,
    pub pregap_ms: Option<i64>,
}

/// Parses CUE sheet metadata and frame-accurate `MM:SS:FF` (75 fps CD sectors) timestamps.
pub fn parse_cue_sheet(cue: &str) -> Vec<CueTrack> {
    let mut album_title: Option<String> = None;
    let mut album_performer: Option<String> = None;
    let mut current_file: Option<String> = None;
    let mut tracks: Vec<CueTrack> = Vec::new();
    let mut active: Option<CueTrack> = None;

    for raw in cue.lines() {
        let line = raw.trim().trim_start_matches('\u{FEFF}');
        if line.is_empty() {
            continue;
        }
        let upper = line.to_ascii_uppercase();
        if upper.starts_with("FILE ") {
            if let Some(f) = extract_quoted_arg(&line[5..]) {
                current_file = Some(f);
            }
        } else if upper.starts_with("TRACK ") {
            if let Some(prev) = active.take() {
                tracks.push(prev);
            }
            let num = line[6..]
                .split_whitespace()
                .next()
                .and_then(|s| s.parse::<i32>().ok())
                .unwrap_or((tracks.len() + 1) as i32);
            active = Some(CueTrack {
                track_number: num,
                title: None,
                performer: album_performer.clone(),
                album: album_title.clone(),
                file: current_file.clone(),
                start_ms: 0,
                pregap_ms: None,
            });
        } else if upper.starts_with("TITLE ") {
            let val = extract_cue_value(&line[6..]);
            if let Some(track) = active.as_mut() {
                track.title = Some(val);
            } else {
                album_title = Some(val);
            }
        } else if upper.starts_with("PERFORMER ") {
            let val = extract_cue_value(&line[10..]);
            if let Some(track) = active.as_mut() {
                track.performer = Some(val);
            } else {
                album_performer = Some(val);
            }
        } else if upper.starts_with("INDEX ") {
            let mut parts = line[6..].split_whitespace();
            let idx_kind = parts.next().unwrap_or("");
            let ts = parts.next().unwrap_or("");
            if let Some(ms) = parse_cue_timestamp_ms(ts) {
                if let Some(track) = active.as_mut() {
                    if idx_kind == "00" {
                        track.pregap_ms = Some(ms);
                    } else if idx_kind == "01" {
                        track.start_ms = ms;
                    }
                }
            }
        }
    }
    if let Some(last) = active.take() {
        tracks.push(last);
    }
    tracks
}

fn extract_quoted_arg(raw: &str) -> Option<String> {
    let trimmed = raw.trim();
    if let Some(after_q) = trimmed.strip_prefix('"') {
        if let Some(end_q) = after_q.find('"') {
            return Some(after_q[..end_q].to_string());
        }
    }
    trimmed.split_whitespace().next().map(str::to_string)
}

fn extract_cue_value(raw: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.len() >= 2 && trimmed.starts_with('"') && trimmed.ends_with('"') {
        trimmed[1..trimmed.len() - 1].to_string()
    } else {
        trimmed.to_string()
    }
}

/// Converts CUE `MM:SS:FF` (75 sectors/sec) timestamp into milliseconds.
pub fn parse_cue_timestamp_ms(ts: &str) -> Option<i64> {
    let mut parts = ts.trim().split(':');
    let mins: i64 = parts.next()?.parse().ok()?;
    let secs: i64 = parts.next()?.parse().ok()?;
    let frames: i64 = parts.next()?.parse().ok()?;
    if mins < 0 || !(0..60).contains(&secs) || !(0..75).contains(&frames) {
        return None;
    }
    Some((mins * 60 + secs) * 1000 + (frames * 1000) / 75)
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PlsEntry {
    pub uri: String,
    pub title: Option<String>,
    pub length_seconds: Option<i64>,
}

/// Parses INI-style `.pls` playlists (`[playlist]`, `FileN=`, `TitleN=`, `LengthN=`).
pub fn parse_pls_playlist(content: &str) -> Vec<PlsEntry> {
    let mut files: HashMap<usize, String> = HashMap::new();
    let mut titles: HashMap<usize, String> = HashMap::new();
    let mut lengths: HashMap<usize, i64> = HashMap::new();

    for raw in content.lines() {
        let line = raw.trim().trim_start_matches('\u{FEFF}');
        let Some((k, v)) = line.split_once('=') else {
            continue;
        };
        let key_lower = k.trim().to_ascii_lowercase();
        let val = v.trim();
        if let Some(num_str) = key_lower.strip_prefix("file") {
            if let Ok(idx) = num_str.parse::<usize>() {
                if !val.is_empty() {
                    files.insert(idx, val.to_string());
                }
            }
        } else if let Some(num_str) = key_lower.strip_prefix("title") {
            if let Ok(idx) = num_str.parse::<usize>() {
                if !val.is_empty() {
                    titles.insert(idx, val.to_string());
                }
            }
        } else if let Some(num_str) = key_lower.strip_prefix("length") {
            if let Ok(idx) = num_str.parse::<usize>() {
                if let Ok(len) = val.parse::<i64>() {
                    if len >= 0 {
                        lengths.insert(idx, len);
                    }
                }
            }
        }
    }

    let mut keys: Vec<usize> = files.keys().copied().collect();
    keys.sort_unstable();
    keys.into_iter()
        .filter_map(|k| {
            files.remove(&k).map(|uri| PlsEntry {
                uri,
                title: titles.remove(&k),
                length_seconds: lengths.remove(&k),
            })
        })
        .collect()
}

/// Infers `(track_number, artist, title)` from common local audio filenames when ID3/Vorbis tags
/// are absent (e.g. `03. Artist - Song Title (FLAC 24-bit).flac`).
pub fn infer_metadata_from_filename(filename: &str) -> (Option<i32>, Option<String>, String) {
    let base_name = filename
        .rsplit(['/', '\\'])
        .next()
        .unwrap_or(filename);
    let stem = match base_name.rfind('.') {
        Some(dot) if dot > 0 => &base_name[..dot],
        _ => base_name,
    };

    let cleaned = strip_quality_tags(stem).replace('_', " ");
    let mut rest = cleaned.trim();
    let mut track_num: Option<i32> = None;

    // Leading track prefix like "03 - ", "03. ", "03 "
    let digit_count = rest.bytes().take_while(u8::is_ascii_digit).count();
    if (1..=3).contains(&digit_count) {
        let after_digits = &rest[digit_count..];
        let trimmed_after = after_digits.trim_start();
        if let Some(stripped) = trimmed_after
            .strip_prefix('-')
            .or_else(|| trimmed_after.strip_prefix('.'))
        {
            if let Ok(n) = rest[..digit_count].parse::<i32>() {
                if n > 0 {
                    track_num = Some(n);
                    rest = stripped.trim_start();
                }
            }
        } else if after_digits.starts_with(' ') {
            if let Ok(n) = rest[..digit_count].parse::<i32>() {
                if (1..=99).contains(&n) {
                    track_num = Some(n);
                    rest = trimmed_after;
                }
            }
        }
    }

    if let Some((art, tit)) = rest.split_once(" - ") {
        let a = art.trim();
        let t = tit.trim();
        if !a.is_empty() && !t.is_empty() {
            return (track_num, Some(a.to_string()), t.to_string());
        }
    }

    (track_num, None, rest.trim().to_string())
}

fn strip_quality_tags(stem: &str) -> String {
    let mut out = stem.to_string();
    let noise = [
        "[FLAC]",
        "[MP3]",
        "[AAC]",
        "[M4A]",
        "[320kbps]",
        "[320]",
        "[24bit]",
        "[24-bit]",
        "[16-bit]",
        "[Hi-Res]",
        "[Lossless]",
        "(Official Video)",
        "(Official Audio)",
        "(Official Music Video)",
        "(Lyric Video)",
        "(Audio)",
    ];
    for tag in noise {
        if let Some(pos) = out.to_ascii_lowercase().find(&tag.to_ascii_lowercase()) {
            out.replace_range(pos..pos + tag.len(), " ");
        }
    }
    out.split_whitespace().collect::<Vec<_>>().join(" ")
}

pub fn parse_cue_to_json(cue: &str) -> String {
    let tracks = parse_cue_sheet(cue);
    let mut out = String::with_capacity(tracks.len() * 96 + 4);
    out.push('[');
    for (i, t) in tracks.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        let _ = write!(
            out,
            "{{\"trackNumber\":{},\"startMs\":{}",
            t.track_number, t.start_ms
        );
        if let Some(title) = &t.title {
            out.push_str(",\"title\":");
            push_json_str(&mut out, title);
        }
        if let Some(perf) = &t.performer {
            out.push_str(",\"performer\":");
            push_json_str(&mut out, perf);
        }
        if let Some(alb) = &t.album {
            out.push_str(",\"album\":");
            push_json_str(&mut out, alb);
        }
        if let Some(file) = &t.file {
            out.push_str(",\"file\":");
            push_json_str(&mut out, file);
        }
        out.push('}');
    }
    out.push(']');
    out
}

pub fn parse_pls_to_json(content: &str) -> String {
    let entries = parse_pls_playlist(content);
    let mut out = String::with_capacity(entries.len() * 96 + 4);
    out.push('[');
    for (i, e) in entries.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str("{\"uri\":");
        push_json_str(&mut out, &e.uri);
        if let Some(title) = &e.title {
            out.push_str(",\"title\":");
            push_json_str(&mut out, title);
        }
        if let Some(len) = e.length_seconds {
            let _ = write!(out, ",\"lengthSeconds\":{len}");
        }
        out.push('}');
    }
    out.push(']');
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_cue_sheet_tracks_and_cd_frames() {
        let cue = concat!(
            "PERFORMER \"Pink Floyd\"\n",
            "TITLE \"Wish You Were Here\"\n",
            "FILE \"album.flac\" WAVE\n",
            "  TRACK 01 AUDIO\n",
            "    TITLE \"Shine On\"\n",
            "    INDEX 01 00:00:00\n",
            "  TRACK 02 AUDIO\n",
            "    TITLE \"Welcome to the Machine\"\n",
            "    INDEX 00 13:29:00\n",
            "    INDEX 01 13:30:15\n"
        );
        let tracks = parse_cue_sheet(cue);
        assert_eq!(tracks.len(), 2);
        assert_eq!(tracks[1].title.as_deref(), Some("Welcome to the Machine"));
        assert_eq!(tracks[1].start_ms, 810_200);
    }

    #[test]
    fn parses_pls_and_infers_filename_metadata() {
        let pls = concat!(
            "[playlist]\n",
            "File1=https://stream.example.com/1\n",
            "Title1=Radio One\n",
            "Length1=-1\n",
            "NumberOfEntries=1\n"
        );
        let entries = parse_pls_playlist(pls);
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].title.as_deref(), Some("Radio One"));

        let (num, artist, title) =
            infer_metadata_from_filename("/Music/04. Daft Punk - Instant Crush [FLAC].flac");
        assert_eq!(num, Some(4));
        assert_eq!(artist.as_deref(), Some("Daft Punk"));
        assert_eq!(title, "Instant Crush");
    }
}
