// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::collections::HashSet;

use crate::track_matching::normalize;
use crate::ttml_parser::unescape_xml;

/// Extracts DASH MPD segment URLs (`SegmentList`, `SegmentTemplate` + `SegmentTimeline`,
/// or direct `BaseURL`), mirroring `TidalAudioProvider.extractDashSegmentUrls`.
pub fn extract_dash_segment_urls(manifest_xml: &str) -> Result<Vec<String>, &'static str> {
    let cleaned = sanitize_xml_entities(manifest_xml);

    let list_urls = extract_segment_list_urls(&cleaned);
    if !list_urls.is_empty() {
        return Ok(list_urls);
    }

    let base_url = first_xml_tag_value(&cleaned, "BaseURL")
        .map(|s| unescape_xml(&s))
        .unwrap_or_default();
    let init = first_xml_attr(&cleaned, "initialization")
        .map(|s| unescape_xml(&s))
        .unwrap_or_default();
    let media_template = first_xml_attr(&cleaned, "media")
        .map(|s| unescape_xml(&s))
        .unwrap_or_default();

    if init.trim().is_empty() || media_template.trim().is_empty() {
        let lower_base = base_url.to_ascii_lowercase();
        if lower_base.starts_with("http://") || lower_base.starts_with("https://") {
            return Ok(vec![base_url]);
        }
        return Err("TIDAL DASH manifest had no initialization/media templates");
    }

    let segment_count = count_segment_timeline_entries(&cleaned);
    if segment_count == 0 {
        return Err("TIDAL DASH manifest listed no media segments");
    }

    let mut urls = Vec::with_capacity(segment_count + 1);
    urls.push(resolve_dash_url(&base_url, &init));
    for idx in 1..=segment_count {
        let filled = media_template.replace("$Number$", &idx.to_string());
        urls.push(resolve_dash_url(&base_url, &filled));
    }
    Ok(urls)
}

fn sanitize_xml_entities(input: &str) -> String {
    if !input.contains('&') {
        return input.to_string();
    }
    let mut out = String::with_capacity(input.len());
    let bytes = input.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'&' {
            let rest = &input[i + 1..];
            let known = rest.starts_with("amp;")
                || rest.starts_with("lt;")
                || rest.starts_with("gt;")
                || rest.starts_with("quot;")
                || rest.starts_with("apos;")
                || rest.starts_with('#');
            if known {
                out.push('&');
            } else {
                out.push_str("&amp;");
            }
        } else {
            let ch = input[i..].chars().next().unwrap_or('\0');
            out.push(ch);
            i += ch.len_utf8();
            continue;
        }
        i += 1;
    }
    out
}

fn extract_segment_list_urls(xml: &str) -> Vec<String> {
    let base_url = first_xml_tag_value(xml, "BaseURL")
        .map(|s| unescape_xml(&s))
        .unwrap_or_default();
    let mut urls = Vec::new();
    if let Some(init_url) = extract_tag_attr(xml, "Initialization", "sourceURL") {
        urls.push(resolve_dash_url(&base_url, &unescape_xml(&init_url)));
    }
    for media in extract_all_tag_attrs(xml, "SegmentURL", "media") {
        urls.push(resolve_dash_url(&base_url, &unescape_xml(&media)));
    }
    urls
}

fn first_xml_tag_value(xml: &str, tag: &str) -> Option<String> {
    let open_pat = format!("<{tag}");
    let start = find_ci(xml, &open_pat)?;
    let after_open = &xml[start + open_pat.len()..];
    let gt = after_open.find('>')?;
    if after_open[..gt].trim_end().ends_with('/') {
        return None;
    }
    let body = &after_open[gt + 1..];
    let close_pat = format!("</{tag}>");
    let end = find_ci(body, &close_pat)?;
    Some(body[..end].trim().to_string())
}

fn first_xml_attr(xml: &str, attr_name: &str) -> Option<String> {
    let lower = xml.to_ascii_lowercase();
    let needle = format!("{}=\"", attr_name.to_ascii_lowercase());
    let mut offset = 0usize;
    while let Some(rel) = lower[offset..].find(&needle) {
        let abs = offset + rel;
        let prev = abs.checked_sub(1).and_then(|idx| xml.as_bytes().get(idx).copied());
        if prev.is_none_or(|b| b.is_ascii_whitespace()) {
            let val_start = abs + needle.len();
            if let Some(end_q) = xml[val_start..].find('"') {
                return Some(xml[val_start..val_start + end_q].to_string());
            }
        }
        offset = abs + needle.len();
    }
    None
}

fn extract_tag_attr(xml: &str, tag_name: &str, attr_name: &str) -> Option<String> {
    extract_all_tag_attrs(xml, tag_name, attr_name).into_iter().next()
}

fn extract_all_tag_attrs(xml: &str, tag_name: &str, attr_name: &str) -> Vec<String> {
    let lower = xml.to_ascii_lowercase();
    let open_pat = format!("<{}", tag_name.to_ascii_lowercase());
    let mut results = Vec::new();
    let mut pos = 0usize;
    while let Some(rel) = lower[pos..].find(&open_pat) {
        let abs = pos + rel;
        let after = abs + open_pat.len();
        let next_b = xml.as_bytes().get(after).copied();
        if next_b.is_some_and(|b| b.is_ascii_whitespace() || b == b'>' || b == b'/') {
            if let Some(gt_rel) = xml[after..].find('>') {
                let tag_slice = &xml[after..after + gt_rel];
                if let Some(val) = first_xml_attr(tag_slice, attr_name) {
                    results.push(val);
                }
                pos = after + gt_rel + 1;
                continue;
            }
        }
        pos = after;
    }
    results
}

fn count_segment_timeline_entries(xml: &str) -> usize {
    let lower = xml.to_ascii_lowercase();
    let mut total = 0usize;
    let mut pos = 0usize;
    while let Some(rel) = lower[pos..].find("<s") {
        let abs = pos + rel;
        let next = xml.as_bytes().get(abs + 2).copied();
        if next.is_some_and(|b| b.is_ascii_whitespace()) {
            if let Some(gt_rel) = xml[abs..].find('>') {
                let tag = &xml[abs..abs + gt_rel];
                if first_xml_attr(tag, "d").is_some() {
                    let repeat = first_xml_attr(tag, "r")
                        .and_then(|r| r.parse::<isize>().ok())
                        .filter(|&r| r > 0)
                        .unwrap_or(0) as usize;
                    total = total.saturating_add(repeat + 1);
                }
                pos = abs + gt_rel + 1;
                continue;
            }
        }
        pos = abs + 2;
    }
    total
}

fn find_ci(haystack: &str, needle: &str) -> Option<usize> {
    haystack
        .to_ascii_lowercase()
        .find(&needle.to_ascii_lowercase())
}

/// Resolves relative DASH segment paths against `base_url`.
pub fn resolve_dash_url(base_url: &str, path: &str) -> String {
    let lower_path = path.to_ascii_lowercase();
    if lower_path.starts_with("http://") || lower_path.starts_with("https://") {
        return path.to_string();
    }
    let lower_base = base_url.to_ascii_lowercase();
    if !(lower_base.starts_with("http://") || lower_base.starts_with("https://")) {
        return path.to_string();
    }
    if path.starts_with('/') {
        if let Some(scheme_end) = base_url.find("://") {
            let after_scheme = &base_url[scheme_end + 3..];
            let host_end = after_scheme
                .find('/')
                .map(|p| scheme_end + 3 + p)
                .unwrap_or(base_url.len());
            return format!("{}{}", &base_url[..host_end], path);
        }
    }
    let prefix = if let Some(q) = base_url.find('?') {
        &base_url[..q]
    } else {
        base_url
    };
    if prefix.ends_with('/') {
        format!("{}{}", prefix, path.trim_start_matches('/'))
    } else if let Some(last_slash) = prefix.rfind('/') {
        if last_slash > prefix.find("://").map(|i| i + 2).unwrap_or(0) {
            format!("{}/{}", &prefix[..last_slash], path.trim_start_matches('/'))
        } else {
            format!("{}/{}", prefix, path.trim_start_matches('/'))
        }
    } else {
        format!("{}/{}", prefix, path.trim_start_matches('/'))
    }
}

/// Extracts FLAC metadata blocks (`STREAMINFO` + headers) from an MP4 `dfLa` init segment.
pub fn extract_flac_metadata_blocks(init_bytes: &[u8]) -> Result<Vec<u8>, &'static str> {
    let pos = init_bytes
        .windows(4)
        .position(|w| w == b"dfLa")
        .filter(|&p| p >= 4)
        .ok_or("TIDAL HiRes init segment had no FLAC metadata")?;
    let box_start = pos - 4;
    let size32 = u32::from_be_bytes([
        init_bytes[box_start],
        init_bytes[box_start + 1],
        init_bytes[box_start + 2],
        init_bytes[box_start + 3],
    ]) as usize;
    let payload_start = pos + 8;
    let box_end = box_start.saturating_add(size32);
    if size32 < 16 || payload_start + 4 > box_end || box_end > init_bytes.len() {
        return Err("TIDAL HiRes FLAC metadata box was invalid");
    }
    let metadata_blocks = &init_bytes[payload_start..box_end];
    let first_type = metadata_blocks[0] & 0x7F;
    let first_len = ((metadata_blocks[1] as usize) << 16)
        | ((metadata_blocks[2] as usize) << 8)
        | (metadata_blocks[3] as usize);
    if first_type != 0 || first_len != 34 || metadata_blocks.len() < 4 + first_len {
        return Err("TIDAL HiRes FLAC STREAMINFO was invalid");
    }
    Ok(metadata_blocks.to_vec())
}

/// Concatenates raw media bytes from all `mdat` boxes in an MP4 segment.
pub fn extract_mdat_payloads(segment_bytes: &[u8]) -> Result<Vec<u8>, &'static str> {
    let mut out = Vec::with_capacity(segment_bytes.len());
    let mut found_mdat = false;
    let mut offset = 0usize;
    while offset + 8 <= segment_bytes.len() {
        let size32 = u32::from_be_bytes([
            segment_bytes[offset],
            segment_bytes[offset + 1],
            segment_bytes[offset + 2],
            segment_bytes[offset + 3],
        ]) as u64;
        let box_type = &segment_bytes[offset + 4..offset + 8];
        let (hdr_len, box_size) = match size32 {
            0 => (8usize, segment_bytes.len() - offset),
            1 => {
                if offset + 16 > segment_bytes.len() {
                    return Err("truncated extended MP4 box header");
                }
                let size64 = u64::from_be_bytes([
                    segment_bytes[offset + 8],
                    segment_bytes[offset + 9],
                    segment_bytes[offset + 10],
                    segment_bytes[offset + 11],
                    segment_bytes[offset + 12],
                    segment_bytes[offset + 13],
                    segment_bytes[offset + 14],
                    segment_bytes[offset + 15],
                ]);
                (16usize, size64 as usize)
            }
            s => (8usize, s as usize),
        };
        let box_end = offset.saturating_add(box_size);
        if box_size < hdr_len || box_end > segment_bytes.len() {
            return Err("TIDAL HiRes segment had an invalid MP4 box");
        }
        if box_type == b"mdat" {
            out.extend_from_slice(&segment_bytes[offset + hdr_len..box_end]);
            found_mdat = true;
        }
        offset = box_end;
    }
    if !found_mdat {
        return Err("TIDAL HiRes segment had no audio payload");
    }
    Ok(out)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TargetAudioQuality {
    Low = 0,
    High = 1,
    Highest = 2,
    Auto = 3,
}

impl TargetAudioQuality {
    pub fn from_i32(v: i32) -> Self {
        match v {
            0 => Self::Low,
            1 => Self::High,
            2 => Self::Highest,
            _ => Self::Auto,
        }
    }
}

/// Lightweight audio stream descriptor for deterministic sorting in Rust.
#[derive(Debug, Clone)]
pub struct AudioFormatCandidate<'a> {
    pub index: usize,
    pub is_direct_url: bool,
    pub bitrate: i32,
    pub sample_rate: i32,
    pub codec: &'a str,
}

pub fn codec_preference_rank(mime_or_codec: &str) -> i32 {
    let lower = mime_or_codec.to_ascii_lowercase();
    if lower.contains("flac") {
        return 5;
    }
    if lower.contains("opus") {
        return 4;
    }
    if lower.contains("mp4a") || lower.contains("aac") {
        return 3;
    }
    if lower.contains("vorbis") {
        return 2;
    }
    if lower.contains("mp3") || lower.contains("mpeg") {
        return 1;
    }
    0
}

/// Ranks candidate audio format indices according to ArchiveTune's quality policy.
pub fn rank_audio_formats(
    codecs: &[&str],
    bitrates: &[i32],
    sample_rates: &[i32],
    is_direct: &[bool],
    quality: TargetAudioQuality,
    network_metered: bool,
) -> Vec<usize> {
    let n = codecs
        .len()
        .min(bitrates.len())
        .min(sample_rates.len())
        .min(is_direct.len());
    if n == 0 {
        return Vec::new();
    }

    let effective = match quality {
        TargetAudioQuality::Auto => {
            if network_metered {
                TargetAudioQuality::High
            } else {
                TargetAudioQuality::Highest
            }
        }
        q => q,
    };

    let target_bitrate: Option<i32> = match effective {
        TargetAudioQuality::Low => Some(70_000),
        TargetAudioQuality::High => Some(160_000),
        TargetAudioQuality::Highest => Some(320_000),
        TargetAudioQuality::Auto => None,
    };

    let mut items: Vec<AudioFormatCandidate<'_>> = (0..n)
        .filter(|&i| bitrates[i] > 0)
        .map(|i| AudioFormatCandidate {
            index: i,
            is_direct_url: is_direct[i],
            bitrate: bitrates[i],
            sample_rate: sample_rates[i],
            codec: codecs[i],
        })
        .collect();

    let cmp_higher = |a: &AudioFormatCandidate<'_>, b: &AudioFormatCandidate<'_>| {
        b.is_direct_url
            .cmp(&a.is_direct_url)
            .then_with(|| b.bitrate.cmp(&a.bitrate))
            .then_with(|| {
                codec_preference_rank(b.codec).cmp(&codec_preference_rank(a.codec))
            })
            .then_with(|| b.sample_rate.cmp(&a.sample_rate))
    };

    let cmp_lower_above = |a: &AudioFormatCandidate<'_>, b: &AudioFormatCandidate<'_>| {
        b.is_direct_url
            .cmp(&a.is_direct_url)
            .then_with(|| a.bitrate.cmp(&b.bitrate))
            .then_with(|| {
                codec_preference_rank(b.codec).cmp(&codec_preference_rank(a.codec))
            })
            .then_with(|| b.sample_rate.cmp(&a.sample_rate))
    };

    match target_bitrate {
        None | Some(320_000)
            if effective == TargetAudioQuality::Highest || target_bitrate.is_none() =>
        {
            items.sort_by(cmp_higher);
            items.into_iter().map(|c| c.index).collect()
        }
        Some(limit) => {
            let mut preferred: Vec<_> = items
                .iter()
                .filter(|c| c.bitrate <= limit)
                .cloned()
                .collect();
            let mut fallback: Vec<_> = items
                .into_iter()
                .filter(|c| c.bitrate > limit)
                .collect();
            preferred.sort_by(cmp_higher);
            fallback.sort_by(cmp_lower_above);
            preferred
                .into_iter()
                .chain(fallback)
                .map(|c| c.index)
                .collect()
        }
    }
}

/// Optimal String Alignment (restricted Damerau-Levenshtein) edit distance.
pub fn damerau_levenshtein(a: &str, b: &str) -> usize {
    let ac: Vec<char> = a.chars().collect();
    let bc: Vec<char> = b.chars().collect();
    let n = ac.len();
    let m = bc.len();
    if n == 0 {
        return m;
    }
    if m == 0 {
        return n;
    }
    let width = m + 1;
    let mut dp = vec![0usize; (n + 1) * width];
    for i in 0..=n {
        dp[i * width] = i;
    }
    for (j, cell) in dp.iter_mut().take(width).enumerate() {
        *cell = j;
    }
    for i in 1..=n {
        for j in 1..=m {
            let cost = usize::from(ac[i - 1] != bc[j - 1]);
            let del = dp[(i - 1) * width + j] + 1;
            let ins = dp[i * width + (j - 1)] + 1;
            let sub = dp[(i - 1) * width + (j - 1)] + cost;
            let mut best = del.min(ins).min(sub);
            if i > 1 && j > 1 && ac[i - 1] == bc[j - 2] && ac[i - 2] == bc[j - 1] {
                best = best.min(dp[(i - 2) * width + (j - 2)] + 1);
            }
            dp[i * width + j] = best;
        }
    }
    dp[n * width + m]
}

/// Computes fuzzy similarity in `[0.0, 1.0]` combining prefix, token overlap, and edit ratio.
pub fn fuzzy_similarity_score(query: &str, candidate: &str) -> f64 {
    let q_norm = normalize(Some(query));
    let c_norm = normalize(Some(candidate));
    if q_norm.is_empty() || c_norm.is_empty() {
        return 0.0;
    }
    if q_norm == c_norm {
        return 1.0;
    }

    let mut bonus = 0.0f64;
    if c_norm.starts_with(&q_norm) {
        bonus = 0.92;
    } else if c_norm.contains(&q_norm) {
        bonus = 0.85;
    }

    let q_tokens: HashSet<&str> = q_norm.split_whitespace().collect();
    let c_tokens: HashSet<&str> = c_norm.split_whitespace().collect();
    let overlap = if !q_tokens.is_empty() {
        let common = q_tokens.intersection(&c_tokens).count() as f64;
        let union = q_tokens.union(&c_tokens).count().max(1) as f64;
        common / union
    } else {
        0.0
    };

    let q_clipped: String = q_norm.chars().take(96).collect();
    let c_clipped: String = c_norm.chars().take(96).collect();
    let max_len = q_clipped.chars().count().max(c_clipped.chars().count()).max(1);
    let dist = damerau_levenshtein(&q_clipped, &c_clipped);
    let edit_sim = (1.0 - (dist as f64) / (max_len as f64)).clamp(0.0, 1.0);

    bonus.max(overlap * 0.55 + edit_sim * 0.45)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_segment_template_mpd() {
        let mpd = concat!(
            "<MPD><Period><AdaptationSet><Representation>",
            "<BaseURL>https://cdn.example.com/track/</BaseURL>",
            "<SegmentTemplate initialization=\"init.mp4\" media=\"seg_$Number$.mp4\">",
            "<SegmentTimeline><S d=\"1000\" r=\"2\"/><S d=\"800\"/></SegmentTimeline>",
            "</SegmentTemplate></Representation></AdaptationSet></Period></MPD>"
        );
        let urls = extract_dash_segment_urls(mpd).unwrap();
        assert_eq!(urls.len(), 5);
        assert_eq!(urls[0], "https://cdn.example.com/track/init.mp4");
        assert_eq!(urls[1], "https://cdn.example.com/track/seg_1.mp4");
        assert_eq!(urls[4], "https://cdn.example.com/track/seg_4.mp4");
    }

    #[test]
    fn extracts_mdat_from_synthetic_mp4_segment() {
        let mut seg = Vec::new();
        // ftyp box (12 bytes)
        seg.extend_from_slice(&12u32.to_be_bytes());
        seg.extend_from_slice(b"ftypisom");
        // mdat box (13 bytes = 8 header + 5 payload)
        seg.extend_from_slice(&13u32.to_be_bytes());
        seg.extend_from_slice(b"mdat");
        seg.extend_from_slice(b"FLAC!");
        let payload = extract_mdat_payloads(&seg).unwrap();
        assert_eq!(payload, b"FLAC!");
    }

    #[test]
    fn ranks_audio_formats_by_quality_and_direct_url() {
        let codecs = [
            "audio/mp4; codecs=\"mp4a.40.2\"",
            "audio/webm; codecs=\"opus\"",
            "audio/webm; codecs=\"opus\"",
        ];
        let bitrates = [128_000, 145_000, 256_000];
        let rates = [44_100, 48_000, 48_000];
        let direct = [true, true, false];

        let ranked_high = rank_audio_formats(
            &codecs,
            &bitrates,
            &rates,
            &direct,
            TargetAudioQuality::High,
            false,
        );
        assert_eq!(ranked_high, vec![1, 0, 2]);

        let ranked_highest = rank_audio_formats(
            &codecs,
            &bitrates,
            &rates,
            &direct,
            TargetAudioQuality::Highest,
            false,
        );
        assert_eq!(ranked_highest, vec![1, 0, 2]);
    }

    #[test]
    fn fuzzy_similarity_scores_typos_and_transpositions() {
        assert_eq!(damerau_levenshtein("kitten", "kitetn"), 1);
        let s_exact = fuzzy_similarity_score("Bohemian Rhapsody", "Bohemian Rhapsody");
        let s_typo = fuzzy_similarity_score("Bohemian Rapsody", "Bohemian Rhapsody");
        let s_diff = fuzzy_similarity_score("Bohemian Rhapsody", "Completely Different Song");
        assert!((s_exact - 1.0).abs() < 1e-9);
        assert!(s_typo > s_diff);
        assert!(s_typo > 0.7);
    }
}
