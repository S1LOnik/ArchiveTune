// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use regex::Regex;
use std::collections::{HashMap, HashSet};
use std::sync::LazyLock;

pub const NBSP: char = '\u{00A0}';
pub const ENHANCED_LRC_LAST_WORD_DEFAULT_DURATION_MS: i64 = 600;
pub const MIN_WORD_DURATION_MS: i64 = 40;
pub const SINGLE_WORD_LINE_MAX_SWEEP_MS: i64 = 3_000;
pub const ENHANCED_LRC_TRAILING_LINE_DURATION_MS: i64 = 4_000;
pub const MINUTE_IN_MILLIS: i64 = 60_000;
pub const SECOND_IN_MILLIS: i64 = 1_000;

static LINE_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)$").unwrap());
static TIME_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]").unwrap());
static WHITESPACE_REGEX: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").unwrap());
static ENHANCED_LRC_WORD_TIME_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>").unwrap());
static ENHANCED_LRC_WORD_TOKEN_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>)([^<]*)").unwrap());
static INLINE_MILLISECONDS_TIME_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"<\d{1,8}(?:,\d{1,8})?>").unwrap());
static YRC_LINE_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^\[(\d{1,8}),\d{1,8}\](.*)$").unwrap());
static YRC_WORD_TIME_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)").unwrap());
static YRC_WORD_TOKEN_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(.*?)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)").unwrap());
static QRC_TRANSLATION_LINE_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^\[(\d{1,8}),(\d{1,8})\](.*)$").unwrap());
static QRC_WORD_TIMING_DETECT_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)").unwrap());
static QRC_LINE_TIMING_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^\[(\d{1,8}),(\d{1,8})\](.*)$").unwrap());
static QRC_WORD_TIMING_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(.*?)\((\d{1,8}),(\d{1,8})(?:,\d{1,8})?\)").unwrap());
static QRC_METADATA_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"^\[([A-Za-z]+):(.*)\]$").unwrap());
static QRC_LYRIC_CONTENT_ATTR_REGEX: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r#"(?i)LyricContent\s*=\s*""#).unwrap());

static METADATA_PREFIXES: LazyLock<HashSet<&'static str>> = LazyLock::new(|| {
    let mut s = HashSet::new();
    s.insert("词");
    s.insert("作词");
    s.insert("曲");
    s.insert("作曲");
    s.insert("编曲");
    s.insert("和声");
    s.insert("混音");
    s.insert("吉他");
    s.insert("制作人");
    s.insert("演唱");
    s.insert("原唱");
    s.insert("翻唱");
    s.insert("后期");
    s.insert("和音");
    s.insert("录音");
    s.insert("策划");
    s.insert("伴奏");
    s.insert("美工");
    s.insert("海报");
    s.insert("旁白");
    s.insert("writtenby");
    s.insert("producedby");
    s.insert("composedby");
    s.insert("arrangedby");
    s.insert("mixing");
    s.insert("mastering");
    s.insert("vocal");
    s.insert("vocals");
    s.insert("guitar");
    s.insert("bass");
    s.insert("drums");
    s.insert("producer");
    s.insert("lyricist");
    s.insert("composer");
    s.insert("arranger");
    s.insert("lyricsby");
    s
});

#[derive(Debug, Clone, PartialEq)]
pub struct ParsedWordTimestamp {
    pub text: String,
    pub start_time: f64,
    pub end_time: f64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ParsedLyricsEntry {
    pub time: i64,
    pub text: String,
    pub words: Option<Vec<ParsedWordTimestamp>>,
    pub agent: Option<String>,
    pub provider_translation_text: Option<String>,
    pub duration_ms: i64,
}

pub fn normalize_lyrics_text(lyrics: &str) -> String {
    let raw = lyrics.replace('\u{FEFF}', "").replace(
        ['\u{200B}', '\u{200C}', '\u{200D}', '\u{2060}', '\u{00AD}'],
        "",
    );
    let trimmed = trim_whitespace_and_nbsp(&raw);
    let stripped = strip_code_fence(trimmed);
    let unwrapped = unwrap_json_envelope(stripped);
    let normalized = if is_escaped_ttml(unwrapped) {
        unescape_xml(unwrapped)
    } else {
        unwrapped.to_string()
    };
    trim_whitespace_and_nbsp(&normalized).to_string()
}

pub fn trim_whitespace_and_nbsp(s: &str) -> &str {
    s.trim_matches(|c: char| c.is_whitespace() || c == NBSP)
}

fn strip_code_fence(lyrics: &str) -> &str {
    if !lyrics.starts_with("```") {
        return lyrics;
    }
    let lines: Vec<&str> = lyrics.lines().collect();
    if lines.len() <= 1 {
        return lyrics;
    }
    let body = if lines.last().map(|l| l.trim()) == Some("```") {
        &lines[1..lines.len() - 1]
    } else {
        &lines[1..]
    };
    let joined = body.join("\n");
    let trimmed = trim_whitespace_and_nbsp(&joined);
    if trimmed.is_empty() {
        return "";
    }
    let start_offset = lyrics.find(trimmed).unwrap_or(0);
    let end_offset = start_offset + trimmed.len();
    &lyrics[start_offset..end_offset]
}

fn unwrap_json_envelope(value: &str) -> &str {
    let first = value.chars().next();
    if first != Some('{') && first != Some('[') {
        return value;
    }
    if let Some(pos) = value.find(r#""lyrics":""#) {
        let start = pos + 10;
        if let Some(end) = value[start..].find('"') {
            return &value[start..start + end];
        }
    }
    value
}

fn is_escaped_ttml(lyrics: &str) -> bool {
    let trimmed = lyrics.trim_start();
    trimmed.starts_with("&lt;tt")
        || trimmed.contains("&lt;tt")
        || (trimmed.contains("http://www.w3.org/ns/ttml") && trimmed.contains("&lt;"))
}

fn unescape_xml(value: &str) -> String {
    value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

pub fn clean_inline_word_timing_text(text: &str) -> String {
    let s1 = ENHANCED_LRC_WORD_TIME_REGEX.replace_all(text, "");
    let s2 = INLINE_MILLISECONDS_TIME_REGEX.replace_all(&s1, "");
    let s3 = YRC_WORD_TIME_REGEX.replace_all(&s2, "");
    let s4 = WHITESPACE_REGEX.replace_all(&s3, " ");
    trim_whitespace_and_nbsp(&s4).to_string()
}

pub fn is_qrc(content: &str) -> bool {
    let content_lower = content.to_ascii_lowercase();
    if content_lower.contains("<qrcinfos") || content_lower.contains("lyriccontent=") {
        return true;
    }
    content.lines().any(|line| {
        if let Some(caps) = QRC_LINE_TIMING_REGEX.captures(line.trim()) {
            caps.get(3)
                .is_some_and(|trailing| QRC_WORD_TIMING_DETECT_REGEX.is_match(trailing.as_str()))
        } else {
            false
        }
    })
}

pub fn has_word_timings(content: &str) -> bool {
    let extracted = extract_qrc_lyric_content(content);
    extracted.lines().any(|line| {
        if let Some(caps) = QRC_LINE_TIMING_REGEX.captures(line.trim()) {
            caps.get(3)
                .is_some_and(|trailing| QRC_WORD_TIMING_REGEX.is_match(trailing.as_str()))
        } else {
            false
        }
    })
}

pub fn is_line_synced_lrc(lyrics: &str) -> bool {
    let normalized = normalize_lyrics_text(lyrics);
    if is_qrc(&normalized) {
        return true;
    }
    lyrics.lines().any(|line| {
        let trimmed = line.trim();
        LINE_REGEX.is_match(trimmed) || YRC_LINE_REGEX.is_match(trimmed)
    })
}

pub fn parse_enhanced_lrc_stamp_ms(stamp: &str) -> Option<i64> {
    let body = stamp.strip_prefix('<')?.strip_suffix('>')?;
    let parts: Vec<&str> = body.split([':', '.']).collect();
    if parts.len() < 2 {
        return None;
    }
    let min: i64 = parts[0].parse().ok()?;
    let sec: i64 = parts[1].parse().ok()?;
    let mil: i64 = if parts.len() >= 3 {
        let fraction = parts[2];
        match fraction.len() {
            1 => fraction.parse::<i64>().ok()? * 100,
            2 => fraction.parse::<i64>().ok()? * 10,
            _ => {
                let digits = if fraction.len() > 3 {
                    &fraction[..3]
                } else {
                    fraction
                };
                let padded = format!("{:0<3}", digits);
                padded.parse().ok()?
            }
        }
    } else {
        0
    };
    Some(min * MINUTE_IN_MILLIS + sec * SECOND_IN_MILLIS + mil)
}

pub fn extract_enhanced_lrc_word_timestamps(raw_text: &str) -> Option<Vec<ParsedWordTimestamp>> {
    if !ENHANCED_LRC_WORD_TIME_REGEX.is_match(raw_text) {
        return None;
    }
    let tokens: Vec<_> = ENHANCED_LRC_WORD_TOKEN_REGEX
        .captures_iter(raw_text)
        .collect();
    if tokens.is_empty() {
        return None;
    }

    let mut words = Vec::new();
    for (index, token) in tokens.iter().enumerate() {
        let stamp_str = token.get(1)?.as_str();
        let start_ms = parse_enhanced_lrc_stamp_ms(stamp_str)?;
        let raw_token_text = token.get(2).map(|m| m.as_str()).unwrap_or("");
        let normalized_text = WHITESPACE_REGEX.replace_all(raw_token_text, " ");
        let word_text = trim_whitespace_and_nbsp(&normalized_text);
        if word_text.is_empty() {
            continue;
        }

        let text_with_gap = if normalized_text.ends_with(' ') {
            format!("{} ", word_text)
        } else {
            word_text.to_string()
        };

        let next_start_ms = tokens.get(index + 1).and_then(|next_token| {
            next_token
                .get(1)
                .and_then(|m| parse_enhanced_lrc_stamp_ms(m.as_str()))
        });

        let end_ms = next_start_ms.unwrap_or(start_ms + ENHANCED_LRC_LAST_WORD_DEFAULT_DURATION_MS);
        let final_end_ms = end_ms.max(start_ms + MIN_WORD_DURATION_MS);

        words.push(ParsedWordTimestamp {
            text: text_with_gap,
            start_time: start_ms as f64 / 1000.0,
            end_time: final_end_ms as f64 / 1000.0,
        });
    }

    if words.is_empty() { None } else { Some(words) }
}

pub fn extract_yrc_word_timestamps(raw_text: &str) -> Option<Vec<ParsedWordTimestamp>> {
    if !YRC_WORD_TIME_REGEX.is_match(raw_text) {
        return None;
    }
    let tokens: Vec<_> = YRC_WORD_TOKEN_REGEX.captures_iter(raw_text).collect();
    if tokens.is_empty() {
        return None;
    }

    let mut words = Vec::new();
    for token in tokens {
        let raw_word = token.get(1).map(|m| m.as_str()).unwrap_or("");
        let normalized = WHITESPACE_REGEX.replace_all(raw_word, " ");
        let trimmed = trim_whitespace_and_nbsp(&normalized);
        let word_text = if raw_word.ends_with(' ') {
            format!("{} ", trimmed)
        } else {
            trimmed.to_string()
        };
        if trim_whitespace_and_nbsp(&word_text).is_empty() {
            continue;
        }

        let start_ms: i64 = token.get(2).and_then(|m| m.as_str().parse().ok())?;
        let duration_ms: i64 = token.get(3).and_then(|m| m.as_str().parse().ok())?;
        let end_ms = start_ms + duration_ms.max(0);
        let final_end_ms = end_ms.max(start_ms + MIN_WORD_DURATION_MS);

        words.push(ParsedWordTimestamp {
            text: word_text,
            start_time: start_ms as f64 / 1000.0,
            end_time: final_end_ms as f64 / 1000.0,
        });
    }

    if words.is_empty() { None } else { Some(words) }
}

pub fn parse_line_synced_lrc_line(line: &str) -> Option<Vec<ParsedLyricsEntry>> {
    let trimmed = line.trim();
    if trimmed.is_empty() {
        return None;
    }
    let caps = LINE_REGEX.captures(trimmed)?;
    let times_group = caps.get(1)?.as_str();
    let raw_text = caps.get(3).map(|m| m.as_str()).unwrap_or("");
    let text = clean_inline_word_timing_text(raw_text);

    let inline_words = extract_enhanced_lrc_word_timestamps(raw_text)
        .or_else(|| extract_yrc_word_timestamps(raw_text));

    let mut entries = Vec::new();
    for time_cap in TIME_REGEX.captures_iter(times_group) {
        let min: i64 = time_cap.get(1)?.as_str().parse().ok()?;
        let sec: i64 = time_cap.get(2)?.as_str().parse().ok()?;
        let mut mil: i64 = time_cap
            .get(3)
            .and_then(|m| m.as_str().parse().ok())
            .unwrap_or(0);
        if let Some(mil_match) = time_cap.get(3) {
            match mil_match.as_str().len() {
                1 => mil *= 100,
                2 => mil *= 10,
                _ => {}
            }
        }
        let time = min * MINUTE_IN_MILLIS + sec * SECOND_IN_MILLIS + mil;

        if let Some(words) = &inline_words {
            let line_start_sec = time as f64 / 1000.0;
            let adjusted_words = words
                .iter()
                .map(|w| {
                    if w.start_time >= line_start_sec - 0.001 {
                        w.clone()
                    } else {
                        ParsedWordTimestamp {
                            text: w.text.clone(),
                            start_time: line_start_sec,
                            end_time: w.end_time.max(line_start_sec + 0.05),
                        }
                    }
                })
                .collect();
            entries.push(ParsedLyricsEntry {
                time,
                text: text.clone(),
                words: Some(adjusted_words),
                agent: None,
                provider_translation_text: None,
                duration_ms: 0,
            });
        } else {
            entries.push(ParsedLyricsEntry {
                time,
                text: text.clone(),
                words: None,
                agent: None,
                provider_translation_text: None,
                duration_ms: 0,
            });
        }
    }

    if entries.is_empty() {
        None
    } else {
        Some(entries)
    }
}

pub fn parse_milliseconds_synced_line(line: &str) -> Option<Vec<ParsedLyricsEntry>> {
    let trimmed = line.trim();
    if trimmed.is_empty() {
        return None;
    }
    let caps = YRC_LINE_REGEX.captures(trimmed)?;
    let time: i64 = caps.get(1)?.as_str().parse().ok()?;
    let raw_text = caps.get(2).map(|m| m.as_str()).unwrap_or("");
    let text = clean_inline_word_timing_text(raw_text);

    Some(vec![ParsedLyricsEntry {
        time,
        text,
        words: None,
        agent: None,
        provider_translation_text: None,
        duration_ms: 0,
    }])
}

pub fn merge_line_synced_translations(entries: &[ParsedLyricsEntry]) -> Vec<ParsedLyricsEntry> {
    let mut merged_by_time: HashMap<i64, usize> = HashMap::new();
    let mut result: Vec<ParsedLyricsEntry> = Vec::new();

    for entry in entries {
        if let Some(&idx) = merged_by_time.get(&entry.time) {
            let existing = &result[idx];
            let clean_candidate = WHITESPACE_REGEX.replace_all(&entry.text, " ");
            let candidate_trimmed = clean_candidate.trim();
            let existing_trimmed = existing.text.trim();

            if !candidate_trimmed.is_empty()
                && !candidate_trimmed.eq_ignore_ascii_case(existing_trimmed)
                && result[idx].provider_translation_text.is_none()
            {
                result[idx].provider_translation_text = Some(candidate_trimmed.to_string());
            }
        } else {
            merged_by_time.insert(entry.time, result.len());
            result.push(entry.clone());
        }
    }

    result.sort_by_key(|e| e.time);
    result
}

pub fn clamp_enhanced_lrc_last_word_ends(entries: &mut [ParsedLyricsEntry]) {
    let n = entries.len();
    for i in 0..n {
        let entry_time = entries[i].time;
        let next_start_ms = if i + 1 < n && entries[i + 1].time > entry_time {
            entries[i + 1].time
        } else {
            entry_time + ENHANCED_LRC_TRAILING_LINE_DURATION_MS
        };

        if let Some(words) = &mut entries[i].words {
            if words.is_empty() {
                continue;
            }
            let word_count = words.len();
            let last_word = &words[word_count - 1];
            let original_end_ms = (last_word.end_time * 1000.0).round() as i64;
            let mut last_end_ms = original_end_ms;

            if word_count == 1 && last_end_ms < next_start_ms {
                let start_ms = (last_word.start_time * 1000.0).round() as i64;
                last_end_ms = next_start_ms.min(start_ms + SINGLE_WORD_LINE_MAX_SWEEP_MS);
            }

            if last_end_ms > next_start_ms {
                words[word_count - 1].end_time = next_start_ms as f64 / 1000.0;
            } else if last_end_ms != original_end_ms {
                words[word_count - 1].end_time = last_end_ms as f64 / 1000.0;
            }
        }
    }
}

pub fn extract_qrc_translations(lyrics: &str) -> HashMap<i64, String> {
    let mut word_timed_starts: HashSet<i64> = HashSet::new();
    let mut translation_candidates: Vec<(i64, String)> = Vec::new();

    for line in lyrics.lines() {
        let trimmed = line.trim();
        if let Some(caps) = QRC_TRANSLATION_LINE_REGEX.captures(trimmed) {
            let start_ms_opt = caps.get(1).and_then(|m| m.as_str().parse::<i64>().ok());
            if let Some(start_ms) = start_ms_opt {
                let content = caps.get(3).map(|m| m.as_str()).unwrap_or("");
                if QRC_WORD_TIMING_DETECT_REGEX.is_match(content) {
                    word_timed_starts.insert(start_ms);
                } else if !content.trim().is_empty() {
                    translation_candidates.push((start_ms, content.trim().to_string()));
                }
            }
        }
    }

    let mut translations = HashMap::new();
    for (start_ms, text) in translation_candidates {
        if word_timed_starts.contains(&start_ms) && !translations.contains_key(&start_ms) {
            translations.insert(start_ms, text);
        }
    }
    translations
}

fn extract_qrc_lyric_content(content: &str) -> String {
    if let Some(mat) = QRC_LYRIC_CONTENT_ATTR_REGEX.find(content) {
        let start = mat.end();
        if let Some(rel_end) = content[start..].find('"') {
            return unescape_xml(&content[start..start + rel_end]);
        }
    }
    content.to_string()
}

struct AgentContext {
    aliases: HashMap<String, String>,
    next_voice_id: usize,
    current_singer: Option<String>,
}

pub fn parse_qrc(content: &str) -> Vec<ParsedLyricsEntry> {
    let lyric_content = extract_qrc_lyric_content(content);
    let translations_by_start = extract_qrc_translations(&lyric_content);
    let mut context = AgentContext {
        aliases: HashMap::new(),
        next_voice_id: 1,
        current_singer: None,
    };

    struct IntermediateLine {
        start_ms: i64,
        duration_ms: i64,
        text: String,
        words: Vec<ParsedWordTimestamp>,
        agent: Option<String>,
    }

    let mut intermediate: Vec<IntermediateLine> = Vec::new();

    for line in lyric_content.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || QRC_METADATA_REGEX.is_match(trimmed) {
            continue;
        }

        let caps = match QRC_LINE_TIMING_REGEX.captures(trimmed) {
            Some(c) => c,
            None => continue,
        };

        let start_ms: i64 = match caps.get(1).and_then(|m| m.as_str().parse().ok()) {
            Some(s) => s,
            None => continue,
        };
        let duration_ms: i64 = match caps.get(2).and_then(|m| m.as_str().parse().ok()) {
            Some(d) => d,
            None => continue,
        };
        let trailing = caps.get(3).map(|m| m.as_str()).unwrap_or("");

        let mut timed_words = Vec::new();
        for w_cap in QRC_WORD_TIMING_REGEX.captures_iter(trailing) {
            let w_text = w_cap.get(1).map(|m| m.as_str()).unwrap_or("");
            if w_text.is_empty() {
                continue;
            }
            let start = w_cap.get(2).and_then(|m| m.as_str().parse::<i64>().ok());
            let dur = w_cap.get(3).and_then(|m| m.as_str().parse::<i64>().ok());
            if let (Some(s), Some(d)) = (start, dur) {
                timed_words.push((w_text.to_string(), s, d));
            }
        }

        if timed_words.is_empty() {
            continue;
        }

        let line_text: String = timed_words.iter().map(|(t, _, _)| t.as_str()).collect();

        let colon_idx = line_text.find([':', '：']);
        let mut final_agent = context.current_singer.clone();
        let mut final_timed_words = timed_words;

        if let Some(idx) = colon_idx {
            let singer_name = line_text[..idx].trim();
            let is_meta_prefix = METADATA_PREFIXES
                .contains(singer_name.to_ascii_lowercase().as_str())
                || singer_name.ends_with('词')
                || singer_name.ends_with('曲')
                || singer_name.ends_with('声')
                || singer_name.ends_with('音');

            if is_meta_prefix {
                continue;
            }

            if !singer_name.is_empty() && singer_name.len() <= 30 {
                let agent = context
                    .aliases
                    .entry(singer_name.to_string())
                    .or_insert_with(|| {
                        let upper = singer_name.to_ascii_uppercase();
                        if upper == "合" || upper == "ALL" || upper == "合唱" {
                            "v1000".to_string()
                        } else {
                            let voice = format!("v{}", context.next_voice_id);
                            context.next_voice_id += 1;
                            voice
                        }
                    })
                    .clone();
                context.current_singer = Some(agent.clone());
                final_agent = Some(agent);

                let char_count = idx + 1;
                let mut remaining = char_count;
                let mut retained = Vec::new();

                for (w_text, w_start, w_dur) in &final_timed_words {
                    if remaining >= w_text.len() {
                        remaining -= w_text.len();
                    } else if remaining > 0 {
                        let trimmed_word = w_text[remaining..].trim_start().to_string();
                        remaining = 0;
                        if !trimmed_word.is_empty() {
                            retained.push((trimmed_word, *w_start, *w_dur));
                        }
                    } else {
                        retained.push((w_text.clone(), *w_start, *w_dur));
                    }
                }
                final_timed_words = retained;
            }
        }

        if final_timed_words.is_empty() {
            continue;
        }

        let line_final_text: String = final_timed_words
            .iter()
            .map(|(t, _, _)| t.as_str())
            .collect();
        let trimmed_final_text = line_final_text.trim();
        if trimmed_final_text.is_empty() {
            continue;
        }

        let first_w_start = final_timed_words[0].1;
        let last_w = &final_timed_words[final_timed_words.len() - 1];
        let actual_start_ms = if colon_idx.is_some() {
            first_w_start
        } else {
            start_ms
        };
        let actual_duration_ms = if colon_idx.is_some() {
            (last_w.1 + last_w.2 - first_w_start).max(0)
        } else {
            duration_ms
        };

        let parsed_words: Vec<ParsedWordTimestamp> = final_timed_words
            .iter()
            .enumerate()
            .map(|(w_idx, (w_text, w_s, w_d))| {
                let next_w_start = final_timed_words.get(w_idx + 1).map(|(_, s, _)| *s);
                let line_end_ms = actual_start_ms + actual_duration_ms;
                let w_end_ms = if *w_d > 0 {
                    *w_s + *w_d
                } else if let Some(next_s) = next_w_start {
                    next_s
                } else {
                    line_end_ms
                }
                .max(*w_s);

                ParsedWordTimestamp {
                    text: w_text.clone(),
                    start_time: *w_s as f64 / 1000.0,
                    end_time: w_end_ms as f64 / 1000.0,
                }
            })
            .collect();

        intermediate.push(IntermediateLine {
            start_ms: actual_start_ms,
            duration_ms: actual_duration_ms,
            text: trimmed_final_text.to_string(),
            words: parsed_words,
            agent: final_agent,
        });
    }

    let mut result = Vec::new();
    let num_lines = intermediate.len();

    for i in 0..num_lines {
        let line = &intermediate[i];
        let next_start_ms = intermediate.get(i + 1).map(|l| l.start_ms);
        let line_end_ms = if line.duration_ms > 0 {
            line.start_ms + line.duration_ms
        } else if let Some(next_s) = next_start_ms {
            next_s
        } else {
            line.words
                .last()
                .map(|w| (w.end_time * 1000.0).round() as i64)
                .unwrap_or(line.start_ms)
        }
        .max(line.start_ms);

        let dur_ms = (line_end_ms - line.start_ms).max(0);
        let trans = translations_by_start.get(&line.start_ms).cloned();

        result.push(ParsedLyricsEntry {
            time: line.start_ms,
            text: line.text.clone(),
            words: Some(line.words.clone()),
            agent: line.agent.clone(),
            provider_translation_text: trans,
            duration_ms: dur_ms,
        });
    }

    result
}

pub fn parse_lyrics(lyrics: &str) -> Vec<ParsedLyricsEntry> {
    let normalized = normalize_lyrics_text(lyrics);
    if normalized.is_empty() {
        return Vec::new();
    }

    if is_qrc(&normalized) {
        return parse_qrc(&normalized);
    }

    let mut result = Vec::new();
    for line in normalized.lines() {
        if let Some(entries) =
            parse_line_synced_lrc_line(line).or_else(|| parse_milliseconds_synced_line(line))
        {
            result.extend(entries);
        }
    }

    let mut merged = merge_line_synced_translations(&result);
    clamp_enhanced_lrc_last_word_ends(&mut merged);
    merged
}

pub fn find_current_line_index(
    line_times: &[i64],
    line_durations: &[i64],
    position: i64,
    lead_ms: i64,
) -> i32 {
    if line_times.is_empty() {
        return -1;
    }

    let last_index = line_times.len() - 1;
    let mut low = 0;
    let mut high = last_index;

    while low <= high {
        let mid = (low + high) >> 1;
        let mid_time = line_times[mid];

        if mid_time < position {
            low = mid + 1;
        } else if mid == 0 {
            high = 0;
            break;
        } else {
            high = mid - 1;
        }
    }

    let current_idx = high.min(last_index);
    let next_idx = current_idx + 1;
    if next_idx > last_index {
        return current_idx as i32;
    }

    let current_time = line_times[current_idx];
    let current_duration = line_durations.get(current_idx).copied().unwrap_or(0);
    let next_time = line_times[next_idx];

    if current_duration > 0 {
        let current_line_end_ms = current_time + current_duration;
        if position < current_line_end_ms {
            return current_idx as i32;
        }
    }

    let gap_between_line_starts_ms = next_time - current_time;
    let effective_lead_ms = if gap_between_line_starts_ms > 2_000 {
        0
    } else {
        lead_ms
    };

    if position + effective_lead_ms >= next_time {
        next_idx as i32
    } else {
        current_idx as i32
    }
}

pub fn has_true_word_sync(start_times: &[f64], end_times: &[f64]) -> bool {
    if start_times.is_empty() || start_times.len() != end_times.len() {
        return false;
    }
    let n = start_times.len();
    if n == 1 {
        return (end_times[0] - start_times[0]) > 0.0;
    }

    let mut distinct_starts = HashSet::new();
    for &st in start_times {
        distinct_starts.insert((st * 1000.0).round() as i64);
    }
    if distinct_starts.len() <= 1 {
        return false;
    }

    let mut distinct_ends = HashSet::new();
    for &et in end_times {
        distinct_ends.insert((et * 1000.0).round() as i64);
    }
    if distinct_ends.len() <= 1 {
        return false;
    }

    let mut all_non_positive = true;
    for i in 0..n {
        if end_times[i] - start_times[i] > 0.0 {
            all_non_positive = false;
            break;
        }
    }
    if all_non_positive {
        return false;
    }

    let line_start = start_times.iter().copied().fold(f64::INFINITY, f64::min);
    let line_end = end_times.iter().copied().fold(f64::NEG_INFINITY, f64::max);
    let line_duration = line_end - line_start;

    if line_duration > 0.0 && n > 2 {
        let tolerance = 0.05;
        let mut is_evenly_distributed = true;
        for (i, &st) in start_times.iter().enumerate() {
            let expected = line_start + (line_duration * i as f64 / (n - 1) as f64);
            if (st - expected).abs() >= tolerance {
                is_evenly_distributed = false;
                break;
            }
        }

        if is_evenly_distributed {
            let positive_durations: Vec<f64> = (0..n)
                .map(|i| (end_times[i] - start_times[i]).max(0.0))
                .filter(|&d| d > 0.0)
                .collect();
            if positive_durations.len() >= 3 {
                let sum: f64 = positive_durations.iter().sum();
                let avg = sum / positive_durations.len() as f64;
                if avg > 0.0 {
                    let variance: f64 = positive_durations
                        .iter()
                        .map(|d| (d - avg) * (d - avg))
                        .sum::<f64>()
                        / positive_durations.len() as f64;
                    let stddev = variance.sqrt();
                    if stddev / avg < 0.1 {
                        return false;
                    }
                }
            }
        }
    }

    true
}

pub fn serialize_entries_to_json(entries: &[ParsedLyricsEntry]) -> String {
    let mut out = String::with_capacity(entries.len() * 128);
    out.push('[');
    for (i, entry) in entries.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push('{');
        out.push_str("\"time\":");
        out.push_str(&entry.time.to_string());
        out.push_str(",\"text\":");
        escape_json_str(&entry.text, &mut out);
        out.push_str(",\"durationMs\":");
        out.push_str(&entry.duration_ms.to_string());
        if let Some(agent) = &entry.agent {
            out.push_str(",\"agent\":");
            escape_json_str(agent, &mut out);
        }
        if let Some(trans) = &entry.provider_translation_text {
            out.push_str(",\"providerTranslationText\":");
            escape_json_str(trans, &mut out);
        }
        if let Some(words) = &entry.words {
            out.push_str(",\"words\":[");
            for (j, w) in words.iter().enumerate() {
                if j > 0 {
                    out.push(',');
                }
                out.push('{');
                out.push_str("\"text\":");
                escape_json_str(&w.text, &mut out);
                out.push_str(",\"startTime\":");
                out.push_str(&format!("{:.4}", w.start_time));
                out.push_str(",\"endTime\":");
                out.push_str(&format!("{:.4}", w.end_time));
                out.push('}');
            }
            out.push(']');
        }
        out.push('}');
    }
    out.push(']');
    out
}

fn escape_json_str(s: &str, out: &mut String) {
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{08}' => out.push_str("\\b"),
            '\u{0C}' => out.push_str("\\f"),
            c if (c as u32) < 0x20 => {
                out.push_str(&format!("\\u{:04x}", c as u32));
            }
            c => out.push(c),
        }
    }
    out.push('"');
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_standard_lrc() {
        let lrc = "[00:10.50]Hello world\n[00:15.00]Second line";
        let entries = parse_lyrics(lrc);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].time, 10500);
        assert_eq!(entries[0].text, "Hello world");
        assert_eq!(entries[1].time, 15000);
        assert_eq!(entries[1].text, "Second line");
    }

    #[test]
    fn test_parse_multi_timestamp_lrc() {
        let lrc = "[00:10.00][00:20.00]Chorus line";
        let entries = parse_lyrics(lrc);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].time, 10000);
        assert_eq!(entries[0].text, "Chorus line");
        assert_eq!(entries[1].time, 20000);
        assert_eq!(entries[1].text, "Chorus line");
    }

    #[test]
    fn test_parse_enhanced_lrc_and_clamping() {
        let lrc = "[00:01.00]<00:01.00>One <00:01.50>Two <00:02.00>Three\n[00:05.00]Next line";
        let entries = parse_lyrics(lrc);
        assert_eq!(entries.len(), 2);
        let words = entries[0].words.as_ref().unwrap();
        assert_eq!(words.len(), 3);
        assert_eq!(words[0].text, "One ");
        assert!((words[0].start_time - 1.0).abs() < 0.001);
        assert!((words[0].end_time - 1.5).abs() < 0.001);
        assert_eq!(words[1].text, "Two ");
        assert_eq!(words[2].text, "Three");
        assert!((words[2].end_time - 2.6).abs() < 0.001);
    }

    #[test]
    fn test_translation_merging() {
        let lrc =
            "[00:10.00]Original line\n[00:10.00]Переведенная строка\n[00:20.00]Second original";
        let entries = parse_lyrics(lrc);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].time, 10000);
        assert_eq!(entries[0].text, "Original line");
        assert_eq!(
            entries[0].provider_translation_text.as_deref(),
            Some("Переведенная строка")
        );
        assert_eq!(entries[1].time, 20000);
        assert!(entries[1].provider_translation_text.is_none());
    }

    #[test]
    fn test_clean_inline_word_timing() {
        let text = "Hello <00:01.00>beautiful (1000,500)world <500,200>!";
        let cleaned = clean_inline_word_timing_text(text);
        assert_eq!(cleaned, "Hello beautiful world !");
    }

    #[test]
    fn test_find_current_line_index() {
        let times = [1000, 2500, 4000];
        let durations = [1000, 1000, 2000];

        assert_eq!(find_current_line_index(&times, &durations, 500, 300), 0);
        assert_eq!(find_current_line_index(&times, &durations, 1500, 300), 0);
        assert_eq!(find_current_line_index(&times, &durations, 2300, 300), 1);
        assert_eq!(find_current_line_index(&times, &durations, 3000, 300), 1);
        assert_eq!(find_current_line_index(&times, &durations, 5000, 300), 2);
    }

    #[test]
    fn test_has_true_word_sync() {
        let starts = [1.0, 1.5, 2.2];
        let ends = [1.4, 2.1, 3.5];
        assert!(has_true_word_sync(&starts, &ends));

        let same_starts = [1.0, 1.0, 1.0];
        let diff_ends = [1.5, 2.0, 2.5];
        assert!(!has_true_word_sync(&same_starts, &diff_ends));

        let zeros = [1.0, 2.0, 3.0];
        assert!(!has_true_word_sync(&zeros, &zeros));
    }

    #[test]
    fn test_json_serialization() {
        let entry = ParsedLyricsEntry {
            time: 1234,
            text: "Hello \"World\"\nTest".to_string(),
            words: Some(vec![ParsedWordTimestamp {
                text: "Hello ".to_string(),
                start_time: 1.2345,
                end_time: 1.5678,
            }]),
            agent: Some("v1".to_string()),
            provider_translation_text: Some("Привет".to_string()),
            duration_ms: 1000,
        };

        let json = serialize_entries_to_json(&[entry]);
        assert!(json.starts_with("[{"));
        assert!(json.contains(r#""time":1234"#));
        assert!(json.contains(r#""text":"Hello \"World\"\nTest""#));
        assert!(json.contains(r#""agent":"v1""#));
        assert!(json.contains(r#""providerTranslationText":"Привет""#));
        assert!(json.contains(r#""words":[{"text":"Hello ","#));
    }
}
