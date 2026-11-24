// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::fmt::Write as _;

use crate::ttml_parser::push_json_str;

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct AudioTagMetadata {
    pub container: &'static str,
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album_artist: Option<String>,
    pub album: Option<String>,
    pub track_number: Option<i32>,
    pub disc_number: Option<i32>,
    pub year: Option<i32>,
    pub duration_ms: Option<i64>,
    pub sample_rate: Option<i32>,
    pub channels: Option<i32>,
    pub bits_per_sample: Option<i32>,
    pub bitrate_kbps: Option<i32>,
    pub lyrics: Option<String>,
    pub artwork_offset: Option<usize>,
    pub artwork_len: Option<usize>,
    pub artwork_mime: Option<String>,
}

/// Parses embedded container metadata, artwork bounds, and audio stream properties
/// directly from container bytes (ID3v2/MP3, FLAC, Ogg Vorbis/Opus, MP4/M4A, WAV).
pub fn parse_audio_tags(data: &[u8]) -> Option<AudioTagMetadata> {
    if data.len() < 12 {
        return None;
    }
    if data.starts_with(b"fLaC") {
        return parse_flac(data);
    }
    if data.starts_with(b"OggS") {
        return parse_ogg(data);
    }
    if data.starts_with(b"RIFF") && data.get(8..12) == Some(b"WAVE") {
        return parse_wav(data);
    }
    if data.get(4..8) == Some(b"ftyp") {
        return parse_mp4(data);
    }
    if data.starts_with(b"ID3") {
        return parse_mp3_with_id3(data);
    }
    if looks_like_mpeg_sync(data) {
        return parse_mpeg_audio_only(data, 0);
    }
    None
}

fn read_be_u16(b: &[u8]) -> Option<u16> {
    b.get(0..2).map(|s| u16::from_be_bytes([s[0], s[1]]))
}

fn read_le_u16(b: &[u8]) -> Option<u16> {
    b.get(0..2).map(|s| u16::from_le_bytes([s[0], s[1]]))
}

fn read_be_u32(b: &[u8]) -> Option<u32> {
    b.get(0..4)
        .map(|s| u32::from_be_bytes([s[0], s[1], s[2], s[3]]))
}

fn read_le_u32(b: &[u8]) -> Option<u32> {
    b.get(0..4)
        .map(|s| u32::from_le_bytes([s[0], s[1], s[2], s[3]]))
}

fn read_be_u64(b: &[u8]) -> Option<u64> {
    b.get(0..8)
        .map(|s| u64::from_be_bytes([s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]]))
}

fn read_le_u64(b: &[u8]) -> Option<u64> {
    b.get(0..8)
        .map(|s| u64::from_le_bytes([s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]]))
}

fn decode_syncsafe_u32(b: &[u8]) -> Option<u32> {
    if b.len() < 4 || b[..4].iter().any(|&x| x >= 0x80) {
        return None;
    }
    Some(((b[0] as u32) << 21) | ((b[1] as u32) << 14) | ((b[2] as u32) << 7) | (b[3] as u32))
}

fn parse_slash_int(raw: &str) -> Option<i32> {
    let head = raw.split('/').next()?.trim();
    head.parse::<i32>().ok().filter(|&v| v > 0)
}

fn parse_year_int(raw: &str) -> Option<i32> {
    let digits: String = raw
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(|c| c.is_ascii_digit())
        .collect();
    if digits.len() >= 4 {
        digits[..4]
            .parse::<i32>()
            .ok()
            .filter(|&y| (1..=9999).contains(&y))
    } else {
        None
    }
}

fn sanitize_tag_text(raw: &str) -> Option<String> {
    let trimmed = raw.trim_matches(|c: char| c.is_whitespace() || c == '\0');
    if trimmed.is_empty() {
        return None;
    }
    let lower = trimmed.to_ascii_lowercase();
    if matches!(
        lower.as_str(),
        "<unknown>" | "unknown" | "unknown artist" | "unknown album" | "unknown title"
    ) {
        return None;
    }
    Some(trimmed.to_string())
}

fn undo_unsync(bytes: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0usize;
    while i < bytes.len() {
        out.push(bytes[i]);
        if bytes[i] == 0xFF && bytes.get(i + 1) == Some(&0x00) {
            i += 2;
        } else {
            i += 1;
        }
    }
    out
}

fn decode_id3_string(encoding: u8, bytes: &[u8]) -> String {
    match encoding {
        0 => bytes
            .iter()
            .take_while(|&&b| b != 0)
            .map(|&b| b as char)
            .collect(),
        1 => {
            let sliced = trim_utf16_nul(bytes);
            if sliced.starts_with(&[0xFE, 0xFF]) {
                decode_utf16_be(&sliced[2..])
            } else if sliced.starts_with(&[0xFF, 0xFE]) {
                decode_utf16_le(&sliced[2..])
            } else {
                decode_utf16_le(sliced)
            }
        }
        2 => decode_utf16_be(trim_utf16_nul(bytes)),
        3 => {
            let end = bytes.iter().position(|&b| b == 0).unwrap_or(bytes.len());
            String::from_utf8_lossy(&bytes[..end]).into_owned()
        }
        _ => String::from_utf8_lossy(bytes)
            .trim_end_matches('\0')
            .to_string(),
    }
}

fn trim_utf16_nul(bytes: &[u8]) -> &[u8] {
    let mut i = 0usize;
    while i + 1 < bytes.len() {
        if bytes[i] == 0 && bytes[i + 1] == 0 {
            return &bytes[..i];
        }
        i += 2;
    }
    bytes
}

fn decode_utf16_le(bytes: &[u8]) -> String {
    let units: Vec<u16> = bytes
        .as_chunks::<2>()
        .0
        .iter()
        .map(|c| u16::from_le_bytes([c[0], c[1]]))
        .collect();
    String::from_utf16_lossy(&units)
}

fn decode_utf16_be(bytes: &[u8]) -> String {
    let units: Vec<u16> = bytes
        .as_chunks::<2>()
        .0
        .iter()
        .map(|c| u16::from_be_bytes([c[0], c[1]]))
        .collect();
    String::from_utf16_lossy(&units)
}

fn skip_encoded_nul(encoding: u8, bytes: &[u8]) -> usize {
    if encoding == 1 || encoding == 2 {
        let mut i = 0usize;
        while i + 1 < bytes.len() {
            if bytes[i] == 0 && bytes[i + 1] == 0 {
                return i + 2;
            }
            i += 2;
        }
        bytes.len()
    } else {
        bytes
            .iter()
            .position(|&b| b == 0)
            .map(|p| p + 1)
            .unwrap_or(bytes.len())
    }
}

fn parse_mp3_with_id3(data: &[u8]) -> Option<AudioTagMetadata> {
    if data.len() < 10 || &data[0..3] != b"ID3" {
        return None;
    }
    let version = data[3];
    let flags = data[5];
    let tag_size = decode_syncsafe_u32(&data[6..10])? as usize;
    let payload_end = (10usize.saturating_add(tag_size)).min(data.len());
    let mut meta = AudioTagMetadata {
        container: "mp3",
        ..AudioTagMetadata::default()
    };

    let tag_slice = &data[10..payload_end];
    let owned_unsync;
    let body: &[u8] = if (flags & 0x80) != 0 && version < 4 {
        owned_unsync = undo_unsync(tag_slice);
        &owned_unsync
    } else {
        tag_slice
    };
    let unsync_zero_offset = (flags & 0x80) == 0 || version >= 4;

    let mut pos = 0usize;
    if (flags & 0x40) != 0 && version >= 3 && body.len() >= 4 {
        let ext_len = if version == 4 {
            decode_syncsafe_u32(&body[0..4]).unwrap_or(0) as usize
        } else {
            4usize.saturating_add(read_be_u32(&body[0..4]).unwrap_or(0) as usize)
        };
        pos = ext_len.min(body.len());
    }

    while pos < body.len() {
        if body[pos] == 0 {
            break;
        }
        if version == 2 {
            if pos + 6 > body.len() {
                break;
            }
            let frame_id = std::str::from_utf8(&body[pos..pos + 3]).unwrap_or("");
            let frame_len = ((body[pos + 3] as usize) << 16)
                | ((body[pos + 4] as usize) << 8)
                | (body[pos + 5] as usize);
            pos += 6;
            if frame_len == 0 || pos + frame_len > body.len() {
                break;
            }
            let content = &body[pos..pos + frame_len];
            apply_id3_frame(
                frame_id,
                content,
                unsync_zero_offset.then_some(10 + pos),
                &mut meta,
            );
            pos += frame_len;
        } else {
            if pos + 10 > body.len() {
                break;
            }
            let frame_id = std::str::from_utf8(&body[pos..pos + 4]).unwrap_or("");
            let raw_len = if version == 4 {
                decode_syncsafe_u32(&body[pos + 4..pos + 8]).unwrap_or(0) as usize
            } else {
                read_be_u32(&body[pos + 4..pos + 8]).unwrap_or(0) as usize
            };
            let frame_flags = read_be_u16(&body[pos + 8..pos + 10]).unwrap_or(0);
            pos += 10;
            if raw_len == 0 || pos + raw_len > body.len() {
                break;
            }
            let frame_slice = &body[pos..pos + raw_len];
            let frame_unsync = version == 4 && (frame_flags & 0x0002) != 0;
            if frame_unsync {
                let decoded = undo_unsync(frame_slice);
                apply_id3_frame(frame_id, &decoded, None, &mut meta);
            } else {
                apply_id3_frame(
                    frame_id,
                    frame_slice,
                    unsync_zero_offset.then_some(10 + pos),
                    &mut meta,
                );
            }
            pos += raw_len;
        }
    }

    if let Some(mpeg) = parse_mpeg_audio_only(data, payload_end) {
        if meta.duration_ms.is_none() {
            meta.duration_ms = mpeg.duration_ms;
        }
        meta.sample_rate = mpeg.sample_rate;
        meta.channels = mpeg.channels;
        meta.bitrate_kbps = mpeg.bitrate_kbps;
    }

    Some(meta)
}

fn apply_id3_frame(
    frame_id: &str,
    content: &[u8],
    abs_frame_offset: Option<usize>,
    meta: &mut AudioTagMetadata,
) {
    if content.is_empty() {
        return;
    }
    let encoding = content[0];
    match frame_id {
        "TIT2" | "TT2" => {
            if meta.title.is_none() {
                meta.title = sanitize_tag_text(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TPE1" | "TP1" => {
            if meta.artist.is_none() {
                meta.artist = sanitize_tag_text(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TPE2" | "TP2" => {
            if meta.album_artist.is_none() {
                meta.album_artist = sanitize_tag_text(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TALB" | "TAL" => {
            if meta.album.is_none() {
                meta.album = sanitize_tag_text(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TRCK" | "TRK" => {
            if meta.track_number.is_none() {
                meta.track_number = parse_slash_int(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TPOS" | "TPA" => {
            if meta.disc_number.is_none() {
                meta.disc_number = parse_slash_int(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TYER" | "TDRC" | "TYE" => {
            if meta.year.is_none() {
                meta.year = parse_year_int(&decode_id3_string(encoding, &content[1..]));
            }
        }
        "TLEN" | "TLE" => {
            if meta.duration_ms.is_none()
                && let Ok(ms) = decode_id3_string(encoding, &content[1..])
                    .trim()
                    .parse::<i64>()
                && ms > 0
            {
                meta.duration_ms = Some(ms);
            }
        }
        "USLT" | "ULT" => {
            if meta.lyrics.is_none() && content.len() > 4 {
                let desc_start = 4;
                let desc_len = skip_encoded_nul(encoding, &content[desc_start..]);
                let text_start = (desc_start + desc_len).min(content.len());
                let text = decode_id3_string(encoding, &content[text_start..]);
                meta.lyrics = sanitize_tag_text(&text);
            }
        }
        "SYLT" | "SLT" => {
            if content.len() > 6 && content[4] == 2 {
                let desc_len = skip_encoded_nul(encoding, &content[6..]);
                let body = &content[(6 + desc_len).min(content.len())..];
                if let Some(lrc) = decode_sylt_to_lrc(encoding, body) {
                    meta.lyrics = Some(lrc);
                }
            }
        }
        "APIC" | "PIC" => {
            if meta.artwork_offset.is_none()
                && let Some((rel_off, img_len, mime)) = locate_id3_picture(frame_id, content)
                && let Some(base) = abs_frame_offset
            {
                meta.artwork_offset = Some(base + rel_off);
                meta.artwork_len = Some(img_len);
                meta.artwork_mime = Some(mime);
            }
        }
        _ => {}
    }
}

fn decode_sylt_to_lrc(encoding: u8, mut body: &[u8]) -> Option<String> {
    let mut lines = Vec::new();
    while !body.is_empty() {
        let nul = skip_encoded_nul(encoding, body);
        let text = decode_id3_string(encoding, &body[..nul]);
        if body.len() < nul + 4 {
            break;
        }
        let ts_ms = read_be_u32(&body[nul..nul + 4])? as u64;
        body = &body[nul + 4..];
        let mins = ts_ms / 60_000;
        let secs = (ts_ms % 60_000) / 1000;
        let cs = (ts_ms % 1000) / 10;
        let cleaned = text.trim();
        if !cleaned.is_empty() {
            lines.push(format!("[{mins:02}:{secs:02}.{cs:02}]{cleaned}"));
        }
    }
    if lines.is_empty() {
        None
    } else {
        Some(lines.join("\n"))
    }
}

fn locate_id3_picture(frame_id: &str, content: &[u8]) -> Option<(usize, usize, String)> {
    if content.len() < 4 {
        return None;
    }
    let encoding = content[0];
    if frame_id == "PIC" {
        if content.len() < 6 {
            return None;
        }
        let mime = match &content[1..4] {
            b"PNG" => "image/png".to_string(),
            _ => "image/jpeg".to_string(),
        };
        let desc_len = skip_encoded_nul(encoding, &content[5..]);
        let off = 5 + desc_len;
        if off < content.len() {
            return Some((off, content.len() - off, mime));
        }
        return None;
    }

    let mime_end = content[1..].iter().position(|&b| b == 0)?;
    let mime = String::from_utf8_lossy(&content[1..1 + mime_end]).into_owned();
    let after_mime = 1 + mime_end + 1;
    if after_mime >= content.len() {
        return None;
    }
    // skip picture type byte (1 byte) + description string
    let desc_start = after_mime + 1;
    if desc_start > content.len() {
        return None;
    }
    let desc_len = skip_encoded_nul(encoding, &content[desc_start..]);
    let off = desc_start + desc_len;
    if off < content.len() {
        Some((off, content.len() - off, mime))
    } else {
        None
    }
}

fn looks_like_mpeg_sync(data: &[u8]) -> bool {
    data.len() >= 4 && data[0] == 0xFF && (data[1] & 0xE0) == 0xE0
}

fn mpeg_sample_rate(version_bits: u8, sr_bits: u8) -> Option<i32> {
    let base = match sr_bits {
        0 => 44_100,
        1 => 48_000,
        2 => 32_000,
        _ => return None,
    };
    match version_bits {
        3 => Some(base),     // MPEG 1
        2 => Some(base / 2), // MPEG 2
        0 => Some(base / 4), // MPEG 2.5
        _ => None,
    }
}

fn mpeg_bitrate_kbps(version_bits: u8, br_bits: u8) -> Option<i32> {
    if br_bits == 0 || br_bits == 15 {
        return None;
    }
    let mpeg1_l3 = [
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0,
    ];
    let mpeg2_l3 = [
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0,
    ];
    if version_bits == 3 {
        Some(mpeg1_l3[br_bits as usize])
    } else {
        Some(mpeg2_l3[br_bits as usize])
    }
}

fn parse_mpeg_audio_only(data: &[u8], start_offset: usize) -> Option<AudioTagMetadata> {
    let search_limit = (start_offset + 8192).min(data.len().saturating_sub(4));
    let mut header_off = None;
    let mut i = start_offset;
    while i <= search_limit {
        if data[i] == 0xFF && (data[i + 1] & 0xE0) == 0xE0 {
            let vb = (data[i + 1] >> 3) & 0x03;
            let layer = (data[i + 1] >> 1) & 0x03;
            let br_idx = (data[i + 2] >> 4) & 0x0F;
            let sr_idx = (data[i + 2] >> 2) & 0x03;
            if vb != 1 && layer != 0 && br_idx != 0 && br_idx != 15 && sr_idx != 3 {
                header_off = Some(i);
                break;
            }
        }
        i += 1;
    }
    let off = header_off?;
    let h = &data[off..off + 4];
    let version_bits = (h[1] >> 3) & 0x03;
    let layer = (h[1] >> 1) & 0x03;
    let br_bits = (h[2] >> 4) & 0x0F;
    let sr_bits = (h[2] >> 2) & 0x03;
    let ch_mode = (h[3] >> 6) & 0x03;

    let sample_rate = mpeg_sample_rate(version_bits, sr_bits)?;
    let bitrate_kbps = mpeg_bitrate_kbps(version_bits, br_bits)?;
    let channels = if ch_mode == 3 { 1 } else { 2 };
    let samples_per_frame = if version_bits == 3 && layer == 1 {
        1152
    } else {
        576
    };

    // Check Xing / Info header
    let side_info = if version_bits == 3 {
        if channels == 1 { 17 } else { 32 }
    } else if channels == 1 {
        9
    } else {
        17
    };
    let xing_off = off + 4 + side_info;
    let mut duration_ms = None;
    if let Some(tag) = data.get(xing_off..xing_off + 4)
        && (tag == b"Xing" || tag == b"Info")
        && let Some(flags) = data.get(xing_off + 4..xing_off + 8).and_then(read_be_u32)
        && (flags & 0x01) != 0
        && let Some(frames) = data.get(xing_off + 8..xing_off + 12).and_then(read_be_u32)
    {
        duration_ms =
            Some(((frames as i64) * (samples_per_frame as i64) * 1000) / (sample_rate as i64));
    }

    if duration_ms.is_none() && bitrate_kbps > 0 {
        let payload_bytes = data.len().saturating_sub(off) as i64;
        duration_ms = Some((payload_bytes * 8) / (bitrate_kbps as i64));
    }

    Some(AudioTagMetadata {
        container: "mp3",
        sample_rate: Some(sample_rate),
        channels: Some(channels),
        bitrate_kbps: Some(bitrate_kbps),
        duration_ms,
        ..AudioTagMetadata::default()
    })
}

fn parse_flac(data: &[u8]) -> Option<AudioTagMetadata> {
    if data.len() < 8 || !data.starts_with(b"fLaC") {
        return None;
    }
    let mut meta = AudioTagMetadata {
        container: "flac",
        ..AudioTagMetadata::default()
    };
    let mut pos = 4usize;

    while pos + 4 <= data.len() {
        let header = data[pos];
        let is_last = (header & 0x80) != 0;
        let block_type = header & 0x7F;
        let block_len = ((data[pos + 1] as usize) << 16)
            | ((data[pos + 2] as usize) << 8)
            | (data[pos + 3] as usize);
        pos += 4;
        if pos + block_len > data.len() {
            break;
        }
        let block = &data[pos..pos + block_len];
        match block_type {
            0 if block.len() >= 18 => {
                // STREAMINFO: 80 bits already min/max block+frame sizes -> bytes 10..18
                let packed = read_be_u64(&block[10..18]).unwrap_or(0);
                let sr = ((packed >> 44) & 0xFFFFF) as i32;
                let ch = (((packed >> 41) & 0x07) as i32) + 1;
                let bps = (((packed >> 36) & 0x1F) as i32) + 1;
                let total_samples = (packed & 0xF_FFFF_FFFF) as i64;
                if sr > 0 {
                    meta.sample_rate = Some(sr);
                    meta.channels = Some(ch);
                    meta.bits_per_sample = Some(bps);
                    if total_samples > 0 {
                        meta.duration_ms = Some((total_samples * 1000) / (sr as i64));
                    }
                }
            }
            4 => apply_vorbis_comment(block, &mut meta),
            6 if meta.artwork_offset.is_none() => {
                if let Some((rel_off, img_len, mime)) = locate_flac_picture(block) {
                    meta.artwork_offset = Some(pos + rel_off);
                    meta.artwork_len = Some(img_len);
                    meta.artwork_mime = Some(mime);
                }
            }
            _ => {}
        }
        pos += block_len;
        if is_last {
            break;
        }
    }

    Some(meta)
}

fn locate_flac_picture(block: &[u8]) -> Option<(usize, usize, String)> {
    if block.len() < 32 {
        return None;
    }
    let mime_len = read_be_u32(&block[4..8])? as usize;
    let off_after_mime = 8usize.checked_add(mime_len)?;
    let mime = String::from_utf8_lossy(block.get(8..off_after_mime)?).into_owned();
    let desc_len = read_be_u32(block.get(off_after_mime..off_after_mime + 4)?)? as usize;
    let after_desc = off_after_mime + 4 + desc_len;
    let img_len_off = after_desc + 16;
    let img_len = read_be_u32(block.get(img_len_off..img_len_off + 4)?)? as usize;
    let data_off = img_len_off + 4;
    if data_off + img_len <= block.len() && img_len > 0 {
        Some((data_off, img_len, mime))
    } else {
        None
    }
}

fn apply_vorbis_comment(block: &[u8], meta: &mut AudioTagMetadata) {
    let Some(vendor_len) = read_le_u32(block).map(|v| v as usize) else {
        return;
    };
    let mut pos = 4usize.saturating_add(vendor_len);
    let Some(count) = block.get(pos..pos + 4).and_then(read_le_u32) else {
        return;
    };
    pos += 4;
    let safe_count = count.min(512);
    for _ in 0..safe_count {
        let Some(entry_len) = block
            .get(pos..pos + 4)
            .and_then(read_le_u32)
            .map(|v| v as usize)
        else {
            break;
        };
        pos += 4;
        if pos + entry_len > block.len() {
            break;
        }
        if let Ok(entry) = std::str::from_utf8(&block[pos..pos + entry_len])
            && let Some((k, v)) = entry.split_once('=')
        {
            apply_key_value_tag(k, v, meta);
        }
        pos += entry_len;
    }
}

fn apply_key_value_tag(key: &str, value: &str, meta: &mut AudioTagMetadata) {
    let Some(cleaned) = sanitize_tag_text(value) else {
        return;
    };
    match key.trim().to_ascii_uppercase().as_str() {
        "TITLE" => {
            meta.title.get_or_insert(cleaned);
        }
        "ARTIST" => {
            meta.artist.get_or_insert(cleaned);
        }
        "ALBUMARTIST" | "ALBUM ARTIST" => {
            meta.album_artist.get_or_insert(cleaned);
        }
        "ALBUM" => {
            meta.album.get_or_insert(cleaned);
        }
        "TRACKNUMBER" => {
            if meta.track_number.is_none() {
                meta.track_number = parse_slash_int(&cleaned);
            }
        }
        "DISCNUMBER" => {
            if meta.disc_number.is_none() {
                meta.disc_number = parse_slash_int(&cleaned);
            }
        }
        "DATE" | "YEAR" => {
            if meta.year.is_none() {
                meta.year = parse_year_int(&cleaned);
            }
        }
        "LYRICS" | "UNSYNCEDLYRICS" => {
            meta.lyrics.get_or_insert(cleaned);
        }
        _ => {}
    }
}

fn parse_ogg(data: &[u8]) -> Option<AudioTagMetadata> {
    let mut meta = AudioTagMetadata {
        container: "ogg",
        ..AudioTagMetadata::default()
    };
    let mut pos = 0usize;
    let mut pre_skip = 0u64;
    let mut packet_idx = 0usize;

    while pos + 27 <= data.len() && &data[pos..pos + 4] == b"OggS" && packet_idx < 6 {
        let seg_count = data[pos + 26] as usize;
        let seg_table_end = pos + 27 + seg_count;
        if seg_table_end > data.len() {
            break;
        }
        let body_len: usize = data[pos + 27..seg_table_end]
            .iter()
            .map(|&s| s as usize)
            .sum();
        let body_start = seg_table_end;
        let body_end = (body_start + body_len).min(data.len());
        let pkt = &data[body_start..body_end];

        if pkt.starts_with(b"\x01vorbis") && pkt.len() >= 16 {
            meta.container = "ogg-vorbis";
            meta.channels = Some(pkt[11] as i32);
            meta.sample_rate = read_le_u32(&pkt[12..16]).map(|v| v as i32);
        } else if pkt.starts_with(b"\x03vorbis") && pkt.len() > 7 {
            apply_vorbis_comment(&pkt[7..], &mut meta);
        } else if pkt.starts_with(b"OpusHead") && pkt.len() >= 19 {
            meta.container = "opus";
            meta.channels = Some(pkt[9] as i32);
            pre_skip = read_le_u16(&pkt[10..12]).unwrap_or(0) as u64;
            meta.sample_rate = Some(48_000);
        } else if pkt.starts_with(b"OpusTags") && pkt.len() > 8 {
            apply_vorbis_comment(&pkt[8..], &mut meta);
        }

        pos = body_end;
        packet_idx += 1;
    }

    // Scan tail window for final OggS page granule position to compute accurate duration.
    let tail_start = data.len().saturating_sub(65_536);
    if let Some(last_page) = rfind_subslice(&data[tail_start..], b"OggS") {
        let abs = tail_start + last_page;
        if abs + 14 <= data.len()
            && let Some(granule) = read_le_u64(&data[abs + 6..abs + 14])
            && let Some(sr) = meta.sample_rate.filter(|&s| s > 0)
        {
            let net_samples = granule.saturating_sub(pre_skip) as i64;
            if net_samples > 0 {
                meta.duration_ms = Some((net_samples * 1000) / (sr as i64));
            }
        }
    }

    Some(meta)
}

fn rfind_subslice(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || haystack.len() < needle.len() {
        return None;
    }
    haystack.windows(needle.len()).rposition(|w| w == needle)
}

fn parse_wav(data: &[u8]) -> Option<AudioTagMetadata> {
    if data.len() < 12 || !data.starts_with(b"RIFF") || &data[8..12] != b"WAVE" {
        return None;
    }
    let mut meta = AudioTagMetadata {
        container: "wav",
        ..AudioTagMetadata::default()
    };
    let mut byte_rate = 0u32;
    let mut data_bytes = 0u64;
    let mut pos = 12usize;

    while pos + 8 <= data.len() {
        let id = &data[pos..pos + 4];
        let size = read_le_u32(&data[pos + 4..pos + 8]).unwrap_or(0) as usize;
        pos += 8;
        let end = (pos + size).min(data.len());
        let chunk = &data[pos..end];

        if id == b"fmt " && chunk.len() >= 16 {
            meta.channels = read_le_u16(&chunk[2..4]).map(|v| v as i32);
            meta.sample_rate = read_le_u32(&chunk[4..8]).map(|v| v as i32);
            byte_rate = read_le_u32(&chunk[8..12]).unwrap_or(0);
            meta.bits_per_sample = read_le_u16(&chunk[14..16]).map(|v| v as i32);
        } else if id == b"data" {
            data_bytes = size as u64;
        } else if id == b"LIST" && chunk.starts_with(b"INFO") {
            parse_wav_list_info(&chunk[4..], &mut meta);
        } else if (id == b"id3 " || id == b"ID3 ")
            && chunk.starts_with(b"ID3")
            && let Some(id3_meta) = parse_mp3_with_id3(chunk)
        {
            if meta.title.is_none() {
                meta.title = id3_meta.title;
            }
            if meta.artist.is_none() {
                meta.artist = id3_meta.artist;
            }
            if meta.album.is_none() {
                meta.album = id3_meta.album;
            }
            if meta.year.is_none() {
                meta.year = id3_meta.year;
            }
        }

        pos = end + (size & 1);
    }

    if byte_rate > 0 && data_bytes > 0 {
        meta.duration_ms = Some(((data_bytes * 1000) / (byte_rate as u64)) as i64);
        meta.bitrate_kbps = Some(((byte_rate * 8) / 1000) as i32);
    }
    Some(meta)
}

fn parse_wav_list_info(info: &[u8], meta: &mut AudioTagMetadata) {
    let mut pos = 0usize;
    while pos + 8 <= info.len() {
        let id = &info[pos..pos + 4];
        let size = read_le_u32(&info[pos + 4..pos + 8]).unwrap_or(0) as usize;
        pos += 8;
        let end = (pos + size).min(info.len());
        let val = String::from_utf8_lossy(&info[pos..end]).into_owned();
        if let Some(cleaned) = sanitize_tag_text(&val) {
            match id {
                b"INAM" => meta.title.get_or_insert(cleaned),
                b"IART" => meta.artist.get_or_insert(cleaned),
                b"IPRD" => meta.album.get_or_insert(cleaned),
                b"ICRD" => {
                    if meta.year.is_none() {
                        meta.year = parse_year_int(&cleaned);
                    }
                    pos = end + (size & 1);
                    continue;
                }
                b"ITRK" => {
                    if meta.track_number.is_none() {
                        meta.track_number = parse_slash_int(&cleaned);
                    }
                    pos = end + (size & 1);
                    continue;
                }
                _ => {
                    pos = end + (size & 1);
                    continue;
                }
            };
        }
        pos = end + (size & 1);
    }
}

fn parse_mp4(data: &[u8]) -> Option<AudioTagMetadata> {
    let mut meta = AudioTagMetadata {
        container: "mp4",
        ..AudioTagMetadata::default()
    };
    walk_mp4_boxes(data, 0, data.len(), 0, &mut meta);
    Some(meta)
}

fn walk_mp4_boxes(
    data: &[u8],
    start: usize,
    end: usize,
    depth: usize,
    meta: &mut AudioTagMetadata,
) {
    if depth > 12 {
        return;
    }
    let mut pos = start;
    while pos + 8 <= end {
        let raw_size = read_be_u32(&data[pos..pos + 4]).unwrap_or(0) as u64;
        let box_type = &data[pos + 4..pos + 8];
        let (hdr_len, box_len) = if raw_size == 1 && pos + 16 <= end {
            (
                16usize,
                read_be_u64(&data[pos + 8..pos + 16]).unwrap_or(0) as usize,
            )
        } else if raw_size == 0 {
            (8usize, end - pos)
        } else {
            (8usize, raw_size as usize)
        };
        if box_len < hdr_len || pos + box_len > end {
            break;
        }
        let body_start = pos + hdr_len;
        let body_end = pos + box_len;

        match box_type {
            b"moov" | b"udta" | b"trak" | b"mdia" => {
                walk_mp4_boxes(data, body_start, body_end, depth + 1, meta);
            }
            b"meta" if body_start + 4 <= body_end => {
                walk_mp4_boxes(data, body_start + 4, body_end, depth + 1, meta);
            }
            b"ilst" => parse_mp4_ilst(data, body_start, body_end, meta),
            b"mvhd" => parse_mp4_mvhd(&data[body_start..body_end], meta),
            _ => {}
        }

        pos = body_end;
    }
}

fn parse_mp4_mvhd(body: &[u8], meta: &mut AudioTagMetadata) {
    if body.len() < 20 {
        return;
    }
    let version = body[0];
    if version == 0 && body.len() >= 20 {
        let timescale = read_be_u32(&body[12..16]).unwrap_or(0) as i64;
        let duration = read_be_u32(&body[16..20]).unwrap_or(0) as i64;
        if timescale > 0 && duration > 0 {
            meta.duration_ms = Some((duration * 1000) / timescale);
        }
    } else if version == 1 && body.len() >= 32 {
        let timescale = read_be_u32(&body[20..24]).unwrap_or(0) as i64;
        let duration = read_be_u64(&body[24..32]).unwrap_or(0) as i64;
        if timescale > 0 && duration > 0 {
            meta.duration_ms = Some((duration * 1000) / timescale);
        }
    }
}

fn parse_mp4_ilst(data: &[u8], start: usize, end: usize, meta: &mut AudioTagMetadata) {
    let mut pos = start;
    while pos + 8 <= end {
        let size = read_be_u32(&data[pos..pos + 4]).unwrap_or(0) as usize;
        if size < 8 || pos + size > end {
            break;
        }
        let atom = &data[pos + 4..pos + 8];
        let mut inner = pos + 8;
        let atom_end = pos + size;
        while inner + 8 <= atom_end {
            let sub_size = read_be_u32(&data[inner..inner + 4]).unwrap_or(0) as usize;
            let sub_type = &data[inner + 4..inner + 8];
            if sub_size < 16 || inner + sub_size > atom_end {
                break;
            }
            if sub_type == b"data" {
                let data_type = read_be_u32(&data[inner + 8..inner + 12]).unwrap_or(0);
                let payload_start = inner + 16;
                let payload = &data[payload_start..inner + sub_size];
                match atom {
                    b"\xA9nam" => {
                        meta.title = sanitize_tag_text(&String::from_utf8_lossy(payload));
                    }
                    b"\xA9ART" => {
                        meta.artist = sanitize_tag_text(&String::from_utf8_lossy(payload));
                    }
                    b"aART" => {
                        meta.album_artist = sanitize_tag_text(&String::from_utf8_lossy(payload));
                    }
                    b"\xA9alb" => {
                        meta.album = sanitize_tag_text(&String::from_utf8_lossy(payload));
                    }
                    b"\xA9day" => {
                        meta.year = parse_year_int(&String::from_utf8_lossy(payload));
                    }
                    b"\xA9lyr" => {
                        meta.lyrics = sanitize_tag_text(&String::from_utf8_lossy(payload));
                    }
                    b"trkn" if payload.len() >= 4 => {
                        if let Some(t) = read_be_u16(&payload[2..4]).filter(|&n| n > 0) {
                            meta.track_number = Some(t as i32);
                        }
                    }
                    b"disk" if payload.len() >= 4 => {
                        if let Some(d) = read_be_u16(&payload[2..4]).filter(|&n| n > 0) {
                            meta.disc_number = Some(d as i32);
                        }
                    }
                    b"covr" if meta.artwork_offset.is_none() => {
                        let mime = if data_type == 14 {
                            "image/png"
                        } else {
                            "image/jpeg"
                        };
                        meta.artwork_offset = Some(payload_start);
                        meta.artwork_len = Some(payload.len());
                        meta.artwork_mime = Some(mime.to_string());
                    }
                    _ => {}
                }
            }
            inner += sub_size;
        }
        pos = atom_end;
    }
}

/// Serializes extracted container metadata into JSON for Kotlin `LocalAudioMetadataReader`.
pub fn parse_audio_tags_to_json(data: &[u8]) -> Option<String> {
    let meta = parse_audio_tags(data)?;
    let mut out = String::with_capacity(256);
    out.push_str("{\"container\":");
    push_json_str(&mut out, meta.container);
    if let Some(v) = &meta.title {
        out.push_str(",\"title\":");
        push_json_str(&mut out, v);
    }
    if let Some(v) = &meta.artist {
        out.push_str(",\"artist\":");
        push_json_str(&mut out, v);
    }
    if let Some(v) = &meta.album_artist {
        out.push_str(",\"albumArtist\":");
        push_json_str(&mut out, v);
    }
    if let Some(v) = &meta.album {
        out.push_str(",\"album\":");
        push_json_str(&mut out, v);
    }
    if let Some(v) = meta.track_number {
        let _ = write!(out, ",\"trackNumber\":{v}");
    }
    if let Some(v) = meta.disc_number {
        let _ = write!(out, ",\"discNumber\":{v}");
    }
    if let Some(v) = meta.year {
        let _ = write!(out, ",\"year\":{v}");
    }
    if let Some(v) = meta.duration_ms {
        let _ = write!(out, ",\"durationMs\":{v}");
    }
    if let Some(v) = meta.sample_rate {
        let _ = write!(out, ",\"sampleRate\":{v}");
    }
    if let Some(v) = meta.channels {
        let _ = write!(out, ",\"channels\":{v}");
    }
    if let Some(v) = meta.bits_per_sample {
        let _ = write!(out, ",\"bitsPerSample\":{v}");
    }
    if let Some(v) = meta.bitrate_kbps {
        let _ = write!(out, ",\"bitrateKbps\":{v}");
    }
    if let Some(v) = &meta.lyrics {
        out.push_str(",\"lyrics\":");
        push_json_str(&mut out, v);
    }
    if let Some(v) = meta.artwork_offset {
        let _ = write!(out, ",\"artworkOffset\":{v}");
    }
    if let Some(v) = meta.artwork_len {
        let _ = write!(out, ",\"artworkLength\":{v}");
    }
    if let Some(v) = &meta.artwork_mime {
        out.push_str(",\"artworkMime\":");
        push_json_str(&mut out, v);
    }
    out.push('}');
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn encode_syncsafe(val: u32) -> [u8; 4] {
        [
            ((val >> 21) & 0x7F) as u8,
            ((val >> 14) & 0x7F) as u8,
            ((val >> 7) & 0x7F) as u8,
            (val & 0x7F) as u8,
        ]
    }

    #[test]
    fn parses_synthetic_id3v24_frames() {
        let mut frames = Vec::new();
        for (id, val) in [
            (b"TIT2", "Echoes"),
            (b"TPE1", "Rukamori"),
            (b"TRCK", "04/12"),
            (b"TDRC", "2026-05-01"),
        ] {
            frames.extend_from_slice(id);
            let len = (1 + val.len()) as u32;
            frames.extend_from_slice(&encode_syncsafe(len));
            frames.extend_from_slice(&[0, 0]);
            frames.push(3); // UTF-8
            frames.extend_from_slice(val.as_bytes());
        }
        let mut buf = Vec::new();
        buf.extend_from_slice(b"ID3\x04\x00\x00");
        buf.extend_from_slice(&encode_syncsafe(frames.len() as u32));
        buf.extend_from_slice(&frames);

        let parsed = parse_audio_tags(&buf).unwrap();
        assert_eq!(parsed.container, "mp3");
        assert_eq!(parsed.title.as_deref(), Some("Echoes"));
        assert_eq!(parsed.artist.as_deref(), Some("Rukamori"));
        assert_eq!(parsed.track_number, Some(4));
        assert_eq!(parsed.year, Some(2026));
    }

    #[test]
    fn parses_synthetic_flac_streaminfo_and_vorbis_comment() {
        let mut buf = Vec::new();
        buf.extend_from_slice(b"fLaC");
        // STREAMINFO (type 0, length 34)
        buf.push(0x00);
        buf.extend_from_slice(&[0, 0, 34]);
        let mut si = [0u8; 34];
        // 44100 Hz, 2 ch, 16 bps, 441000 samples (= 10s)
        let packed: u64 = (44_100u64 << 44) | ((2 - 1) << 41) | ((16 - 1) << 36) | 441_000u64;
        si[10..18].copy_from_slice(&packed.to_be_bytes());
        buf.extend_from_slice(&si);

        // VORBIS_COMMENT (type 4, is_last = true)
        let mut vc = Vec::new();
        vc.extend_from_slice(&4u32.to_le_bytes());
        vc.extend_from_slice(b"test");
        vc.extend_from_slice(&2u32.to_le_bytes());
        for kv in [b"TITLE=Nightfall".as_slice(), b"ALBUM=Midnight".as_slice()] {
            vc.extend_from_slice(&(kv.len() as u32).to_le_bytes());
            vc.extend_from_slice(kv);
        }
        buf.push(0x84);
        buf.push(((vc.len() >> 16) & 0xFF) as u8);
        buf.push(((vc.len() >> 8) & 0xFF) as u8);
        buf.push((vc.len() & 0xFF) as u8);
        buf.extend_from_slice(&vc);

        let parsed = parse_audio_tags(&buf).unwrap();
        assert_eq!(parsed.sample_rate, Some(44_100));
        assert_eq!(parsed.channels, Some(2));
        assert_eq!(parsed.bits_per_sample, Some(16));
        assert_eq!(parsed.duration_ms, Some(10_000));
        assert_eq!(parsed.title.as_deref(), Some("Nightfall"));
        assert_eq!(parsed.album.as_deref(), Some("Midnight"));
    }

    #[test]
    fn parses_synthetic_wav_pcm_duration_and_info() {
        let mut buf = Vec::new();
        buf.extend_from_slice(b"RIFF");
        buf.extend_from_slice(&0u32.to_le_bytes());
        buf.extend_from_slice(b"WAVE");
        buf.extend_from_slice(b"fmt ");
        buf.extend_from_slice(&16u32.to_le_bytes());
        buf.extend_from_slice(&1u16.to_le_bytes()); // PCM
        buf.extend_from_slice(&2u16.to_le_bytes()); // 2 ch
        buf.extend_from_slice(&48_000u32.to_le_bytes());
        buf.extend_from_slice(&192_000u32.to_le_bytes()); // byte rate
        buf.extend_from_slice(&4u16.to_le_bytes());
        buf.extend_from_slice(&16u16.to_le_bytes());
        buf.extend_from_slice(b"data");
        buf.extend_from_slice(&1_920_000u32.to_le_bytes()); // 10 seconds

        let parsed = parse_audio_tags(&buf).unwrap();
        assert_eq!(parsed.sample_rate, Some(48_000));
        assert_eq!(parsed.duration_ms, Some(10_000));
    }
}
