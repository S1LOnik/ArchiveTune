// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::collections::HashMap;
use std::fmt::Write as _;

use crate::ttml_parser::{push_json_str, unescape_xml};

#[derive(Debug, Clone, PartialEq)]
pub struct QrcWord {
    pub text: String,
    pub start_time: f64,
    pub end_time: f64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct QrcLine {
    pub text: String,
    pub start_time: f64,
    pub end_time: f64,
    pub words: Vec<QrcWord>,
    pub agent: Option<String>,
}

#[derive(Debug, Clone)]
struct TimedWord {
    text: String,
    start_ms: i64,
    duration_ms: i64,
}

#[derive(Debug, Clone)]
struct MutableParsedLine {
    text: String,
    start_ms: i64,
    duration_ms: i64,
    words: Vec<TimedWord>,
    agent: Option<String>,
}

#[derive(Default)]
struct TrackMetadata {
    title: Option<String>,
    artist: Option<String>,
}

struct AgentContext {
    aliases: HashMap<String, String>,
    next_voice_id: usize,
    current_singer: Option<String>,
}

impl Default for AgentContext {
    fn default() -> Self {
        Self {
            aliases: HashMap::new(),
            next_voice_id: 1,
            current_singer: None,
        }
    }
}

const METADATA_PREFIXES: &[&str] = &[
    "词",
    "作词",
    "曲",
    "作曲",
    "编曲",
    "和声",
    "混音",
    "吉他",
    "制作人",
    "演唱",
    "原唱",
    "翻唱",
    "后期",
    "和音",
    "录音",
    "策划",
    "伴奏",
    "美工",
    "海报",
    "旁白",
    "writtenby",
    "producedby",
    "composedby",
    "arrangedby",
    "mixing",
    "mastering",
    "vocal",
    "vocals",
    "guitar",
    "bass",
    "drums",
    "producer",
    "lyricist",
    "composer",
    "arranger",
    "lyricsby",
];

/// Extracts raw `LyricContent="..."` XML attribute when present, otherwise returns `content`.
pub fn extract_lyric_content(content: &str) -> String {
    let lower = content.to_ascii_lowercase();
    if let Some(pos) = lower.find("lyriccontent") {
        let rest = &content[pos + "lyriccontent".len()..];
        let trimmed = rest.trim_start();
        if let Some(after_eq) = trimmed.strip_prefix('=') {
            let after_eq = after_eq.trim_start();
            if let Some(after_q) = after_eq.strip_prefix('"') {
                if let Some(end_q) = after_q.find('"') {
                    return unescape_xml(&after_q[..end_q]);
                }
            } else if let Some(after_q) = after_eq.strip_prefix('\'') {
                if let Some(end_q) = after_q.find('\'') {
                    return unescape_xml(&after_q[..end_q]);
                }
            }
        }
    }
    content.to_string()
}

fn parse_line_timing(trimmed: &str) -> Option<(i64, i64, &str)> {
    let inner = trimmed.strip_prefix('[')?;
    let close = inner.find(']')?;
    let header = &inner[..close];
    let body = &inner[close + 1..];
    let comma = header.find(',')?;
    let a = &header[..comma];
    let b = &header[comma + 1..];
    if a.is_empty()
        || a.len() > 8
        || b.is_empty()
        || b.len() > 8
        || !a.bytes().all(|c| c.is_ascii_digit())
        || !b.bytes().all(|c| c.is_ascii_digit())
    {
        return None;
    }
    let start_ms: i64 = a.parse().ok()?;
    let dur_ms: i64 = b.parse().ok()?;
    Some((start_ms, dur_ms, body))
}

fn parse_metadata_tag(trimmed: &str) -> Option<(&str, &str)> {
    let inner = trimmed.strip_prefix('[')?.strip_suffix(']')?;
    let colon = inner.find(':')?;
    let key = &inner[..colon];
    let val = &inner[colon + 1..];
    if key.is_empty() || !key.bytes().all(|c| c.is_ascii_alphabetic()) {
        return None;
    }
    Some((key, val))
}

fn parse_word_timings(body: &str) -> Vec<TimedWord> {
    let mut words = Vec::new();
    let mut rest = body;
    while let Some(open) = rest.find('(') {
        let text_part = &rest[..open];
        let after_open = &rest[open + 1..];
        let Some(close) = after_open.find(')') else {
            break;
        };
        let timing_spec = &after_open[..close];
        if let Some((start_ms, dur_ms)) = parse_word_tuple(timing_spec) {
            if !text_part.is_empty() {
                words.push(TimedWord {
                    text: text_part.to_string(),
                    start_ms,
                    duration_ms: dur_ms,
                });
            }
            rest = &after_open[close + 1..];
        } else {
            // Keep characters up to closing paren inside next word token if parenthesized literal
            break;
        }
    }
    words
}

fn contains_word_timing(body: &str) -> bool {
    let mut rest = body;
    while let Some(open) = rest.find('(') {
        let after_open = &rest[open + 1..];
        let Some(close) = after_open.find(')') else {
            return false;
        };
        if parse_word_tuple(&after_open[..close]).is_some() {
            return true;
        }
        rest = &after_open[close + 1..];
    }
    false
}

fn parse_word_tuple(spec: &str) -> Option<(i64, i64)> {
    let mut parts = spec.split(',');
    let s_str = parts.next()?;
    let d_str = parts.next()?;
    if let Some(flag) = parts.next() {
        if flag.is_empty() || flag.len() > 8 || !flag.bytes().all(|b| b.is_ascii_digit()) {
            return None;
        }
    }
    if parts.next().is_some() {
        return None;
    }
    if s_str.is_empty()
        || s_str.len() > 8
        || d_str.is_empty()
        || d_str.len() > 8
        || !s_str.bytes().all(|b| b.is_ascii_digit())
        || !d_str.bytes().all(|b| b.is_ascii_digit())
    {
        return None;
    }
    Some((s_str.parse().ok()?, d_str.parse().ok()?))
}

/// Checks if raw content uses QQ Music QRC container or word-timed QRC syntax.
pub fn is_qrc(content: &str) -> bool {
    let lower = content.to_ascii_lowercase();
    if lower.contains("<qrcinfos") || lower.contains("lyriccontent=") {
        return true;
    }
    content.lines().any(|line| {
        parse_line_timing(line.trim()).is_some_and(|(_, _, body)| contains_word_timing(body))
    })
}

/// Checks whether the QRC content actually carries word-level timing markers.
pub fn has_word_timings(content: &str) -> bool {
    let extracted = extract_lyric_content(content);
    extracted.lines().any(|line| {
        parse_line_timing(line.trim()).is_some_and(|(_, _, body)| contains_word_timing(body))
    })
}

fn read_metadata(content: &str) -> TrackMetadata {
    let mut meta = TrackMetadata::default();
    for line in content.lines() {
        if let Some((key, val)) = parse_metadata_tag(line.trim()) {
            let trimmed_val = val.trim();
            if trimmed_val.is_empty() {
                continue;
            }
            if key.eq_ignore_ascii_case("ti") {
                meta.title = Some(trimmed_val.to_string());
            } else if key.eq_ignore_ascii_case("ar") {
                meta.artist = Some(trimmed_val.to_string());
            }
        }
    }
    meta
}

fn normalize_for_comparison(value: &str) -> String {
    value
        .chars()
        .filter(|c| c.is_alphanumeric())
        .flat_map(char::to_lowercase)
        .collect()
}

fn is_metadata_prefix(value: &str) -> bool {
    let norm = normalize_for_comparison(value);
    METADATA_PREFIXES.contains(&norm.as_str())
        || norm.ends_with("词")
        || norm.ends_with("曲")
        || norm.ends_with("声")
        || norm.ends_with("音")
}

fn is_metadata_line(line: &MutableParsedLine, meta: &TrackMetadata) -> bool {
    let norm_text = normalize_for_comparison(&line.text);
    let norm_title = meta
        .title
        .as_deref()
        .map(normalize_for_comparison)
        .unwrap_or_default();
    let norm_artist = meta
        .artist
        .as_deref()
        .map(normalize_for_comparison)
        .unwrap_or_default();

    if !norm_title.is_empty() && norm_text.contains(&norm_title)
        && (norm_artist.is_empty() || norm_text.contains(&norm_artist))
    {
        return true;
    }
    if !norm_artist.is_empty() && norm_text == norm_artist {
        return true;
    }
    if line.words.len() > 2 {
        let first_dur = line.words[0].duration_ms;
        return line
            .words
            .iter()
            .all(|w| (w.duration_ms - first_dur).abs() < 10);
    }
    false
}

fn remove_char_prefix(words: &mut Vec<TimedWord>, char_count: usize) {
    let mut remaining = char_count;
    let mut retained = Vec::with_capacity(words.len());
    for word in words.iter() {
        let w_len = word.text.chars().count();
        if remaining >= w_len {
            remaining -= w_len;
        } else if remaining > 0 {
            let trimmed: String = word.text.chars().skip(remaining).collect();
            let trimmed = trimmed.trim_start().to_string();
            remaining = 0;
            if !trimmed.is_empty() {
                retained.push(TimedWord {
                    text: trimmed,
                    start_ms: word.start_ms,
                    duration_ms: word.duration_ms,
                });
            }
        } else {
            retained.push(word.clone());
        }
    }
    while retained
        .first()
        .is_some_and(|w| w.text.trim().is_empty())
    {
        retained.remove(0);
    }
    if let Some(first) = retained.first_mut() {
        first.text = first.text.trim_start().to_string();
    }
    *words = retained;
}

fn extract_singer(
    line: &mut MutableParsedLine,
    ctx: &mut AgentContext,
    is_first_few: bool,
    meta: &TrackMetadata,
) -> bool {
    if is_first_few && is_metadata_line(line, meta) {
        return false;
    }
    let Some((colon_byte, _)) = line.text.char_indices().find(|&(_, c)| c == ':' || c == '：')
    else {
        line.agent = ctx.current_singer.clone();
        return true;
    };
    let char_count_before_colon = line.text[..colon_byte].chars().count();
    let singer_name = line.text[..colon_byte].trim().to_string();
    if is_metadata_prefix(&singer_name) {
        return false;
    }
    if singer_name.is_empty() || singer_name.chars().count() > 30 {
        line.agent = ctx.current_singer.clone();
        return true;
    }

    let agent = ctx
        .aliases
        .entry(singer_name.clone())
        .or_insert_with(|| {
            let upper = singer_name.to_uppercase();
            if upper == "合" || upper == "ALL" || upper == "合唱" {
                "v1000".to_string()
            } else {
                let id = format!("v{}", ctx.next_voice_id);
                ctx.next_voice_id += 1;
                id
            }
        })
        .clone();
    ctx.current_singer = Some(agent.clone());

    remove_char_prefix(&mut line.words, char_count_before_colon + 1);
    let joined: String = line.words.iter().map(|w| w.text.as_str()).collect();
    line.text = joined.trim().to_string();
    if line.text.is_empty() || line.words.is_empty() {
        return false;
    }
    line.agent = Some(agent);
    if let (Some(first), Some(last)) = (line.words.first(), line.words.last()) {
        line.start_ms = first.start_ms;
        line.duration_ms = (last.start_ms + last.duration_ms - first.start_ms).max(0);
    }
    true
}

/// Parses QRC lyrics content into structured lines with word timestamps and singer agents.
pub fn parse_qrc(content: &str) -> Vec<QrcLine> {
    let lyric_content = extract_lyric_content(content);
    let meta = read_metadata(&lyric_content);
    let mut ctx = AgentContext::default();
    let mut parsed_lines: Vec<MutableParsedLine> = Vec::new();

    for raw_line in lyric_content.lines() {
        let trimmed = raw_line.trim();
        if trimmed.is_empty() || parse_metadata_tag(trimmed).is_some() {
            continue;
        }
        let Some((start_ms, dur_ms, body)) = parse_line_timing(trimmed) else {
            continue;
        };
        let words = parse_word_timings(body);
        if words.is_empty() {
            continue;
        }
        let joined: String = words.iter().map(|w| w.text.as_str()).collect();
        let mut candidate = MutableParsedLine {
            text: joined,
            start_ms,
            duration_ms: dur_ms,
            words,
            agent: None,
        };
        if extract_singer(&mut candidate, &mut ctx, parsed_lines.len() < 5, &meta) {
            parsed_lines.push(candidate);
        }
    }

    let mut out = Vec::with_capacity(parsed_lines.len());
    for (idx, line) in parsed_lines.iter().enumerate() {
        let next_start = parsed_lines.get(idx + 1).map(|l| l.start_ms);
        let fallback_end = line
            .words
            .last()
            .map(|w| w.start_ms + w.duration_ms)
            .or(next_start)
            .unwrap_or(line.start_ms);
        let line_end_ms = if line.duration_ms > 0 {
            line.start_ms + line.duration_ms
        } else if let Some(ns) = next_start {
            ns
        } else {
            fallback_end
        }
        .max(line.start_ms);

        let mut out_words = Vec::with_capacity(line.words.len());
        for (wi, w) in line.words.iter().enumerate() {
            let next_word_start = line.words.get(wi + 1).map(|nw| nw.start_ms);
            let word_end_ms = if w.duration_ms > 0 {
                w.start_ms + w.duration_ms
            } else if let Some(nws) = next_word_start {
                nws
            } else {
                line_end_ms
            }
            .max(w.start_ms);
            out_words.push(QrcWord {
                text: w.text.clone(),
                start_time: (w.start_ms as f64) / 1000.0,
                end_time: (word_end_ms as f64) / 1000.0,
            });
        }

        out.push(QrcLine {
            text: line.text.trim().to_string(),
            start_time: (line.start_ms as f64) / 1000.0,
            end_time: (line_end_ms as f64) / 1000.0,
            words: out_words,
            agent: line.agent.clone(),
        });
    }
    out
}

/// Serializes QRC lines to Enhanced LRC (`[mm:ss.cc]<mm:ss.cc>word`) format.
pub fn qrc_to_enhanced_lrc(content: &str) -> String {
    let lines = parse_qrc(content);
    let mut out = String::with_capacity(lines.len() * 64);
    for line in lines {
        let start_ms = (line.start_time * 1000.0).round() as i64;
        let _ = write!(out, "{}", format_lrc_bracket(start_ms));
        if line.words.is_empty() {
            out.push_str(&line.text);
        } else {
            for word in &line.words {
                let w_ms = (word.start_time * 1000.0).round() as i64;
                let _ = write!(out, "{}{}", format_lrc_angle(w_ms), word.text);
            }
        }
        out.push('\n');
    }
    out
}

fn format_lrc_bracket(ms: i64) -> String {
    let clamped = ms.max(0);
    let mins = clamped / 60_000;
    let secs = (clamped % 60_000) / 1000;
    let cs = (clamped % 1000) / 10;
    format!("[{mins:02}:{secs:02}.{cs:02}]")
}

fn format_lrc_angle(ms: i64) -> String {
    let clamped = ms.max(0);
    let mins = clamped / 60_000;
    let secs = (clamped % 60_000) / 1000;
    let cs = (clamped % 1000) / 10;
    format!("<{mins:02}:{secs:02}.{cs:02}>")
}

/// Serializes parsed QRC lines to compact JSON for Kotlin `LyricsUtils` consumption.
pub fn parse_qrc_to_json(content: &str) -> String {
    let lines = parse_qrc(content);
    let mut out = String::with_capacity(lines.len() * 128 + 4);
    out.push('[');
    for (i, line) in lines.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push_str("{\"text\":");
        push_json_str(&mut out, &line.text);
        let _ = write!(
            out,
            ",\"startTime\":{:.6},\"endTime\":{:.6}",
            line.start_time, line.end_time
        );
        out.push_str(",\"words\":[");
        for (wi, w) in line.words.iter().enumerate() {
            if wi > 0 {
                out.push(',');
            }
            out.push_str("{\"text\":");
            push_json_str(&mut out, &w.text);
            let _ = write!(
                out,
                ",\"startTime\":{:.6},\"endTime\":{:.6}",
                w.start_time, w.end_time
            );
            out.push('}');
        }
        out.push(']');
        if let Some(agent) = &line.agent {
            out.push_str(",\"agent\":");
            push_json_str(&mut out, agent);
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
    fn parses_xml_wrapped_qrc_with_duet_agents_and_filters_credits() {
        let raw = concat!(
            "<QrcInfos><LyricInfo><Lyric_1 LyricContent=\"",
            "[ti:Demo]&#10;[ar:Singer]&#10;",
            "[0,500]作(0,250)词：(250,250)Author&#10;",
            "[1000,2000]男：(1000,200)Hello (1200,800)world&#10;",
            "[3500,1500]合：(3500,500)Yeah(4000,500)!",
            "\"/></LyricInfo></QrcInfos>"
        );
        assert!(is_qrc(raw));
        assert!(has_word_timings(raw));
        let lines = parse_qrc(raw);
        assert_eq!(lines.len(), 2);
        assert_eq!(lines[0].agent.as_deref(), Some("v1"));
        assert_eq!(lines[0].text, "Hello world");
        assert_eq!(lines[0].words.len(), 2);
        assert_eq!(lines[1].agent.as_deref(), Some("v1000"));
        assert_eq!(lines[1].text, "Yeah!");
    }

    #[test]
    fn converts_qrc_to_enhanced_lrc() {
        let qrc = "[65000,2000]Test(65000,500)ing(65500,1500)";
        let lrc = qrc_to_enhanced_lrc(qrc);
        assert_eq!(lrc.trim(), "[01:05.00]<01:05.00>Test<01:05.50>ing");
    }
}
