// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::fmt::Write as _;

use crate::ttml_parser::{push_json_str, unescape_xml};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SubtitleCue {
    pub start_ms: i64,
    pub end_ms: i64,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct XspfTrack {
    pub location: String,
    pub title: Option<String>,
    pub creator: Option<String>,
    pub album: Option<String>,
    pub duration_ms: Option<i64>,
}

/// Parses SubRip (`.srt`) or WebVTT (`.vtt`) subtitles into ordered millisecond cues.
pub fn parse_srt_or_vtt(content: &str) -> Vec<SubtitleCue> {
    let cleaned = content.replace('\r', "");
    let mut cues = Vec::new();
    for block in cleaned.split("\n\n") {
        let lines: Vec<&str> = block
            .lines()
            .map(str::trim)
            .filter(|l| !l.is_empty())
            .collect();
        if lines.is_empty() {
            continue;
        }
        if lines[0].eq_ignore_ascii_case("WEBVTT") || lines[0].starts_with("NOTE") {
            continue;
        }
        let timing_idx = lines.iter().position(|l| l.contains("-->"));
        let Some(t_idx) = timing_idx else {
            continue;
        };
        let Some((start_ms, end_ms)) = parse_arrow_timing_line(lines[t_idx]) else {
            continue;
        };
        let text = lines[t_idx + 1..]
            .iter()
            .map(|l| strip_inline_markup(l))
            .collect::<Vec<_>>()
            .join("\n")
            .trim()
            .to_string();
        if !text.is_empty() {
            cues.push(SubtitleCue {
                start_ms,
                end_ms: end_ms.max(start_ms),
                text,
            });
        }
    }
    cues.sort_by_key(|c| c.start_ms);
    cues
}

fn parse_arrow_timing_line(line: &str) -> Option<(i64, i64)> {
    let (left, right) = line.split_once("-->")?;
    let start = parse_srt_timestamp_ms(left.trim())?;
    let end_token = right.split_whitespace().next().unwrap_or("");
    let end = parse_srt_timestamp_ms(end_token)?;
    Some((start, end))
}

/// Parses `HH:MM:SS,mmm` / `HH:MM:SS.mmm` / `MM:SS.mmm` into milliseconds.
pub fn parse_srt_timestamp_ms(raw: &str) -> Option<i64> {
    let norm = raw.trim().replace(',', ".");
    let (hms, frac) = norm
        .split_once('.')
        .map_or((norm.as_str(), "0"), |(a, b)| (a, b));
    let parts: Vec<&str> = hms.split(':').collect();
    let (hours, mins, secs): (i64, i64, i64) = match parts.as_slice() {
        [h, m, s] => (h.parse().ok()?, m.parse().ok()?, s.parse().ok()?),
        [m, s] => (0, m.parse().ok()?, s.parse().ok()?),
        _ => return None,
    };
    let mut frac_digits: String = frac.chars().take(3).collect();
    while frac_digits.len() < 3 {
        frac_digits.push('0');
    }
    let ms: i64 = frac_digits.parse().ok()?;
    Some(((hours * 3600 + mins * 60 + secs) * 1000) + ms)
}

fn strip_inline_markup(line: &str) -> String {
    let mut out = String::with_capacity(line.len());
    let mut in_tag = false;
    for ch in line.chars() {
        if ch == '<' {
            in_tag = true;
            continue;
        }
        if ch == '>' && in_tag {
            in_tag = false;
            continue;
        }
        if !in_tag {
            out.push(ch);
        }
    }
    unescape_xml(&out)
}

/// Converts SRT/WebVTT subtitle cues into standard synced `.lrc` format (`[mm:ss.cc]text`).
pub fn srt_or_vtt_to_lrc(content: &str) -> String {
    let cues = parse_srt_or_vtt(content);
    let mut out = String::with_capacity(cues.len() * 48);
    for cue in cues {
        let clamped = cue.start_ms.max(0);
        let mins = clamped / 60_000;
        let secs = (clamped % 60_000) / 1000;
        let cs = (clamped % 1000) / 10;
        let single_line = cue.text.replace('\n', " ");
        let _ = writeln!(out, "[{mins:02}:{secs:02}.{cs:02}]{}", single_line.trim());
    }
    out
}

/// Converts standard synced `.lrc` lines into SubRip (`.srt`) cues with monotonic end bounds.
pub fn lrc_to_srt(lrc: &str, default_tail_ms: i64) -> String {
    let mut timed: Vec<(i64, String)> = Vec::new();
    for raw in lrc.lines() {
        let trimmed = raw.trim();
        if let Some(after_open) = trimmed.strip_prefix('[')
            && let Some((tag, body)) = after_open.split_once(']')
            && let Some(ms) = parse_lrc_tag_ms(tag)
        {
            let clean = strip_angle_word_timings(body).trim().to_string();
            if !clean.is_empty() {
                timed.push((ms, clean));
            }
        }
    }
    timed.sort_by_key(|(ms, _)| *ms);

    let tail = default_tail_ms.max(500);
    let mut out = String::with_capacity(timed.len() * 64);
    for (idx, (start_ms, text)) in timed.iter().enumerate() {
        let end_ms = timed
            .get(idx + 1)
            .map(|(next_ms, _)| (*next_ms).max(*start_ms + 250))
            .unwrap_or(*start_ms + tail);
        let _ = writeln!(
            out,
            "{}\n{} --> {}\n{}\n",
            idx + 1,
            format_srt_timestamp(*start_ms),
            format_srt_timestamp(end_ms),
            text
        );
    }
    out
}

fn parse_lrc_tag_ms(tag: &str) -> Option<i64> {
    let (mins_s, rest) = tag.split_once(':')?;
    let mins: i64 = mins_s.parse().ok()?;
    let (secs_s, frac_s) = rest
        .split_once('.')
        .or_else(|| rest.split_once(':'))
        .unwrap_or((rest, "0"));
    let secs: i64 = secs_s.parse().ok()?;
    let ms = match frac_s.len() {
        0 => 0,
        1 => frac_s.parse::<i64>().ok()? * 100,
        2 => frac_s.parse::<i64>().ok()? * 10,
        _ => frac_s[..3].parse::<i64>().ok()?,
    };
    Some((mins * 60 + secs) * 1000 + ms)
}

fn strip_angle_word_timings(body: &str) -> String {
    let mut out = String::with_capacity(body.len());
    let mut rest = body;
    while let Some(open) = rest.find('<') {
        out.push_str(&rest[..open]);
        let after = &rest[open + 1..];
        if let Some(close) = after.find('>') {
            let inner = &after[..close];
            let is_timing = inner
                .bytes()
                .all(|b| b.is_ascii_digit() || b == b':' || b == b'.' || b == b',');
            if is_timing {
                rest = &after[close + 1..];
                continue;
            }
        }
        out.push('<');
        rest = after;
    }
    out.push_str(rest);
    out
}

fn format_srt_timestamp(ms: i64) -> String {
    let c = ms.max(0);
    let h = c / 3_600_000;
    let m = (c % 3_600_000) / 60_000;
    let s = (c % 60_000) / 1000;
    let rem = c % 1000;
    format!("{h:02}:{m:02}:{s:02},{rem:03}")
}

/// Normalizes `(start, end)` timestamp pairs so durations are monotonic and non-overlapping
/// within each line, preventing karaoke highlight flicker on malformed metadata feeds.
pub fn stabilize_word_timings(starts: &mut [f64], ends: &mut [f64]) {
    let n = starts.len().min(ends.len());
    if n == 0 {
        return;
    }
    for i in 0..n {
        if !starts[i].is_finite() || starts[i] < 0.0 {
            starts[i] = if i > 0 { ends[i - 1] } else { 0.0 };
        }
        if i > 0 && starts[i] < starts[i - 1] {
            starts[i] = starts[i - 1];
        }
        if !ends[i].is_finite() || ends[i] < starts[i] {
            ends[i] = starts[i] + 0.04;
        }
        if i > 0 && ends[i - 1] > starts[i] {
            ends[i - 1] = starts[i];
        }
    }
}

/// Parses XSPF (`application/xspf+xml`) playlists into ordered track records.
pub fn parse_xspf_playlist(xml: &str) -> Vec<XspfTrack> {
    let mut tracks = Vec::new();
    let lower = xml.to_ascii_lowercase();
    let mut pos = 0usize;
    while let Some(rel) = lower[pos..].find("<track") {
        let abs = pos + rel;
        let next_b = xml.as_bytes().get(abs + 6).copied();
        if !next_b.is_some_and(|b| b == b'>' || b.is_ascii_whitespace()) {
            pos = abs + 6;
            continue;
        }
        let Some(end_rel) = lower[abs..].find("</track>") else {
            break;
        };
        let block = &xml[abs..abs + end_rel + 8];
        if let Some(location) = extract_xml_element_text(block, "location") {
            tracks.push(XspfTrack {
                location,
                title: extract_xml_element_text(block, "title"),
                creator: extract_xml_element_text(block, "creator"),
                album: extract_xml_element_text(block, "album"),
                duration_ms: extract_xml_element_text(block, "duration")
                    .and_then(|s| s.parse::<i64>().ok())
                    .filter(|&d| d > 0),
            });
        }
        pos = abs + end_rel + 8;
    }
    tracks
}

fn extract_xml_element_text(xml: &str, tag: &str) -> Option<String> {
    let lower = xml.to_ascii_lowercase();
    let open = format!("<{}", tag.to_ascii_lowercase());
    let start = lower.find(&open)?;
    let after = &xml[start + open.len()..];
    let gt = after.find('>')?;
    if after[..gt].trim_end().ends_with('/') {
        return None;
    }
    let body = &after[gt + 1..];
    let close = format!("</{}>", tag.to_ascii_lowercase());
    let end = body.to_ascii_lowercase().find(&close)?;
    let cleaned = unescape_xml(body[..end].trim());
    (!cleaned.is_empty()).then_some(cleaned)
}

pub fn parse_xspf_to_json(xml: &str) -> String {
    let tracks = parse_xspf_playlist(xml);
    let mut out = String::with_capacity(tracks.len() * 128 + 4);
    out.push('[');
    for (i, t) in tracks.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str("{\"location\":");
        push_json_str(&mut out, &t.location);
        if let Some(v) = &t.title {
            out.push_str(",\"title\":");
            push_json_str(&mut out, v);
        }
        if let Some(v) = &t.creator {
            out.push_str(",\"creator\":");
            push_json_str(&mut out, v);
        }
        if let Some(v) = &t.album {
            out.push_str(",\"album\":");
            push_json_str(&mut out, v);
        }
        if let Some(d) = t.duration_ms {
            let _ = write!(out, ",\"durationMs\":{d}");
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
    fn converts_srt_and_vtt_to_lrc_and_back() {
        let vtt = concat!(
            "WEBVTT\n\n",
            "00:01:02.500 --> 00:01:05.000 align:center\n",
            "<i>Hello</i> &amp; world\n"
        );
        let lrc = srt_or_vtt_to_lrc(vtt);
        assert_eq!(lrc.trim(), "[01:02.50]Hello & world");
        let srt = lrc_to_srt(&lrc, 3000);
        assert!(srt.contains("00:01:02,500 --> 00:01:05,500"));
        assert!(srt.contains("Hello & world"));
    }

    #[test]
    fn parses_xspf_tracks_and_stabilizes_timings() {
        let xspf = concat!(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<playlist><trackList><track>",
            "<location>file:///music/01.flac</location>",
            "<title>Track &amp; One</title><creator>Artist</creator><duration>240000</duration>",
            "</track></trackList></playlist>"
        );
        let tracks = parse_xspf_playlist(xspf);
        assert_eq!(tracks.len(), 1);
        assert_eq!(tracks[0].title.as_deref(), Some("Track & One"));
        assert_eq!(tracks[0].duration_ms, Some(240_000));

        let mut starts = [1.0, 0.8, 1.5];
        let mut ends = [1.2, 1.1, 1.4];
        stabilize_word_timings(&mut starts, &mut ends);
        assert!(starts[0] <= ends[0] && ends[0] <= starts[1] && starts[1] <= ends[1]);
    }
}
