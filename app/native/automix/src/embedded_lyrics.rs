// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

pub const ID3_HEADER: &[u8; 3] = b"ID3";
pub const FLAC_MARKER: &[u8; 4] = b"fLaC";
pub const OPUS_TAGS_MARKER: &[u8; 8] = b"OpusTags";
pub const VORBIS_COMMENT_MARKER: &[u8; 7] = &[0x03, b'v', b'o', b'r', b'b', b'i', b's'];
pub const MP4_LYRICS_BOX_BYTES: &[u8; 4] = &[0xA9, b'l', b'y', b'r'];

const MAX_VORBIS_COMMENTS: usize = 512;
const FLAC_VORBIS_COMMENT_BLOCK_TYPE: u8 = 4;
const ID3_ENCODING_ISO_8859_1: u8 = 0;
const ID3_ENCODING_UTF16: u8 = 1;
const ID3_ENCODING_UTF16_BE: u8 = 2;
const ID3_ENCODING_UTF8: u8 = 3;
const ID3_TIMESTAMP_MILLISECONDS: u8 = 2;
const MP4_HEADER_SIZE: usize = 8;
const MP4_DATA_HEADER_SIZE: usize = 8;

pub fn extract_embedded_lyrics(
    bytes: &[u8],
    extension: Option<&str>,
    mime_type: Option<&str>,
) -> Option<String> {
    if bytes.is_empty() {
        return None;
    }

    if let Some(id3) = parse_id3_from_file_bytes(bytes) {
        return Some(id3);
    }

    let ext = extension.unwrap_or("").to_ascii_lowercase();
    let mime = mime_type
        .unwrap_or("")
        .split(';')
        .next()
        .unwrap_or("")
        .trim()
        .to_ascii_lowercase();

    let format_lyrics = if is_mp4(&ext, &mime) {
        parse_mp4_lyrics(bytes)
    } else if is_flac(&ext, &mime) {
        parse_flac_lyrics(bytes)
    } else if is_ogg(&ext, &mime) {
        parse_vorbis_comment_lyrics(bytes)
    } else {
        None
    };

    if let Some(lyrics) = format_lyrics {
        return Some(lyrics);
    }

    // Fallback: try Vorbis, MP4, FLAC in sequence
    parse_vorbis_comment_lyrics(bytes)
        .or_else(|| parse_flac_lyrics(bytes))
        .or_else(|| parse_mp4_lyrics(bytes))
}

fn is_mp4(ext: &str, mime: &str) -> bool {
    matches!(ext, "m4a" | "m4b" | "m4p" | "mp4" | "3ga" | "3gp")
        || matches!(
            mime,
            "audio/mp4" | "audio/x-m4a" | "video/mp4" | "video/3gpp"
        )
}

fn is_flac(ext: &str, mime: &str) -> bool {
    ext == "flac" || matches!(mime, "audio/flac" | "audio/x-flac")
}

fn is_ogg(ext: &str, mime: &str) -> bool {
    matches!(ext, "oga" | "ogg" | "opus")
        || matches!(mime, "audio/ogg" | "audio/opus" | "application/ogg")
}

// ---------------------------------------------------------------------------
// ID3 Parser
// ---------------------------------------------------------------------------

pub fn parse_id3_from_file_bytes(bytes: &[u8]) -> Option<String> {
    if bytes.len() < 10 || !bytes.starts_with(ID3_HEADER) {
        return None;
    }

    let major_version = bytes[3];
    if !(2..=4).contains(&major_version) {
        return None;
    }

    let tag_size = read_synchsafe_int(bytes, 6)? as usize;
    if tag_size == 0 {
        return None;
    }

    let header_size = 10;
    let available_tag = if bytes.len() >= header_size + tag_size {
        &bytes[header_size..header_size + tag_size]
    } else {
        &bytes[header_size..]
    };

    let flags = bytes[5];
    let frame_bytes = if (flags & 0x80) != 0 {
        remove_id3_unsynchronization(available_tag)
    } else {
        available_tag.to_vec()
    };

    parse_id3_lyrics(&frame_bytes, major_version)
}

pub fn parse_id3_lyrics(bytes: &[u8], major_version: u8) -> Option<String> {
    let mut offset = 0;
    let mut synced_lyrics: Option<String> = None;
    let mut unsynced_lyrics: Option<String> = None;

    while offset < bytes.len() {
        let (id, frame_size, payload_offset) = match read_id3_frame(bytes, offset, major_version) {
            Some(f) => f,
            None => break,
        };

        if frame_size == 0 || payload_offset + frame_size > bytes.len() {
            break;
        }

        let payload = &bytes[payload_offset..payload_offset + frame_size];
        match id.as_str() {
            "USLT" | "ULT" => {
                if let Some(lyrics) = decode_unsynchronized_lyrics_frame(payload) {
                    unsynced_lyrics = Some(lyrics);
                }
            }
            "SYLT" | "SLT" => {
                if let Some(lyrics) = decode_synchronized_lyrics_frame(payload) {
                    synced_lyrics = Some(lyrics);
                }
            }
            _ => {}
        }

        offset = payload_offset + frame_size;
        if synced_lyrics
            .as_deref()
            .map(|s| !s.trim().is_empty())
            .unwrap_or(false)
        {
            return synced_lyrics;
        }
    }

    synced_lyrics.or(unsynced_lyrics)
}

fn read_id3_frame(
    bytes: &[u8],
    offset: usize,
    major_version: u8,
) -> Option<(String, usize, usize)> {
    if major_version == 2 {
        if offset + 6 > bytes.len() {
            return None;
        }
        let id_bytes = &bytes[offset..offset + 3];
        if id_bytes.contains(&0) {
            return None;
        }
        let id = decode_iso_8859_1(id_bytes);
        if id.trim().is_empty() {
            return None;
        }
        let size = read_uint24(bytes, offset + 3)? as usize;
        Some((id, size, offset + 6))
    } else {
        if offset + 10 > bytes.len() {
            return None;
        }
        let id_bytes = &bytes[offset..offset + 4];
        if id_bytes.contains(&0) {
            return None;
        }
        let id = decode_iso_8859_1(id_bytes);
        if id.trim().is_empty() {
            return None;
        }
        let size = if major_version == 4 {
            read_synchsafe_int(bytes, offset + 4)? as usize
        } else {
            read_int(bytes, offset + 4)? as usize
        };
        Some((id, size, offset + 10))
    }
}

fn decode_unsynchronized_lyrics_frame(bytes: &[u8]) -> Option<String> {
    if bytes.len() <= 5 {
        return None;
    }
    let encoding = bytes[0];
    let term_len = terminator_length(encoding);
    let desc_end = find_id3_terminator(bytes, 4, encoding)?;
    let start = desc_end + term_len;
    if start >= bytes.len() {
        return None;
    }
    decode_id3_text(&bytes[start..], encoding)
}

fn decode_synchronized_lyrics_frame(bytes: &[u8]) -> Option<String> {
    if bytes.len() <= 7 {
        return None;
    }
    let encoding = bytes[0];
    let timestamp_format = bytes[4];
    if timestamp_format != ID3_TIMESTAMP_MILLISECONDS {
        return None;
    }

    let term_len = terminator_length(encoding);
    let desc_end = find_id3_terminator(bytes, 6, encoding)?;
    let mut offset = desc_end + term_len;
    let mut entries: Vec<(u64, String)> = Vec::new();

    while offset < bytes.len() {
        let text_end = match find_id3_terminator(bytes, offset, encoding) {
            Some(end) => end,
            None => break,
        };
        if text_end + term_len + 4 > bytes.len() {
            break;
        }

        let raw_text = decode_id3_text(&bytes[offset..text_end], encoding);
        offset = text_end + term_len;
        let timestamp_ms = (read_int(bytes, offset)? as u32) as u64;
        offset += 4;

        if let Some(text) = raw_text {
            let collapsed = text.split_whitespace().collect::<Vec<_>>().join(" ");
            let trimmed = collapsed.trim();
            if !trimmed.is_empty() {
                entries.push((timestamp_ms, trimmed.to_string()));
            }
        }
    }

    if entries.is_empty() {
        return None;
    }

    entries.sort_by_key(|&(t, _)| t);
    let formatted: Vec<String> = entries
        .into_iter()
        .map(|(ms, text)| format!("{}{}", format_lrc_timestamp(ms), text))
        .collect();

    Some(formatted.join("\n"))
}

fn find_id3_terminator(bytes: &[u8], start: usize, encoding: u8) -> Option<usize> {
    if encoding == ID3_ENCODING_UTF16 || encoding == ID3_ENCODING_UTF16_BE {
        let mut idx = start;
        while idx + 1 < bytes.len() {
            if bytes[idx] == 0 && bytes[idx + 1] == 0 {
                return Some(idx);
            }
            idx += 2;
        }
        None
    } else {
        bytes
            .iter()
            .enumerate()
            .skip(start)
            .find(|&(_, b)| *b == 0)
            .map(|(idx, _)| idx)
    }
}

fn terminator_length(encoding: u8) -> usize {
    if encoding == ID3_ENCODING_UTF16 || encoding == ID3_ENCODING_UTF16_BE {
        2
    } else {
        1
    }
}

fn decode_id3_text(bytes: &[u8], encoding: u8) -> Option<String> {
    if bytes.is_empty() {
        return None;
    }

    let decoded = match encoding {
        ID3_ENCODING_ISO_8859_1 => decode_iso_8859_1(bytes),
        ID3_ENCODING_UTF16 => decode_utf16_bom(bytes),
        ID3_ENCODING_UTF16_BE => decode_utf16_be(bytes),
        ID3_ENCODING_UTF8 => String::from_utf8_lossy(bytes).into_owned(),
        _ => String::from_utf8_lossy(bytes).into_owned(),
    };

    let cleaned = decoded
        .strip_prefix('\u{FEFF}')
        .unwrap_or(&decoded)
        .replace('\0', "");
    let trimmed = cleaned.trim().to_string();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

fn remove_id3_unsynchronization(bytes: &[u8]) -> Vec<u8> {
    let mut result = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        let b = bytes[i];
        result.push(b);
        if b == 0xFF && i + 1 < bytes.len() && bytes[i + 1] == 0x00 {
            i += 1;
        }
        i += 1;
    }
    result
}

fn format_lrc_timestamp(time_ms: u64) -> String {
    let minutes = time_ms / 60_000;
    let seconds = (time_ms % 60_000) / 1_000;
    let centiseconds = (time_ms % 1_000) / 10;
    format!("[{:02}:{:02}.{:02}]", minutes, seconds, centiseconds)
}

// ---------------------------------------------------------------------------
// Vorbis Comment Parser (FLAC, Ogg, Opus)
// ---------------------------------------------------------------------------

pub fn parse_vorbis_comment_lyrics(bytes: &[u8]) -> Option<String> {
    if let Some(opus_idx) = find_subsequence(bytes, OPUS_TAGS_MARKER) {
        let offset = opus_idx + OPUS_TAGS_MARKER.len();
        if let Some(lyrics) = parse_vorbis_comments(bytes, offset) {
            return Some(lyrics);
        }
    }

    if let Some(vorbis_idx) = find_subsequence(bytes, VORBIS_COMMENT_MARKER) {
        let offset = vorbis_idx + VORBIS_COMMENT_MARKER.len();
        if let Some(lyrics) = parse_vorbis_comments(bytes, offset) {
            return Some(lyrics);
        }
    }

    None
}

pub fn parse_vorbis_comments(bytes: &[u8], offset: usize) -> Option<String> {
    let mut cursor = offset;
    let vendor_length = read_le_u32(bytes, cursor)? as usize;
    cursor += 4 + vendor_length;
    if cursor + 4 > bytes.len() {
        return None;
    }

    let comment_count = (read_le_u32(bytes, cursor)? as usize).min(MAX_VORBIS_COMMENTS);
    cursor += 4;

    let mut unsynced_lyrics: Option<String> = None;
    let mut synced_lyrics: Option<String> = None;

    for _ in 0..comment_count {
        let length = match read_le_u32(bytes, cursor) {
            Some(l) => l as usize,
            None => break,
        };
        cursor += 4;
        if cursor + length > bytes.len() {
            break;
        }

        let comment = String::from_utf8_lossy(&bytes[cursor..cursor + length]);
        cursor += length;

        let (key, value) = match comment.split_once('=') {
            Some((k, v)) => (k.to_ascii_uppercase(), v.trim()),
            None => continue,
        };

        if value.is_empty() {
            continue;
        }

        match key.as_str() {
            "SYNCEDLYRICS" | "SYNCLYRICS" => {
                synced_lyrics = Some(value.to_string());
            }
            "LYRICS" | "UNSYNCEDLYRICS" | "UNSYNCED LYRICS" => {
                unsynced_lyrics = Some(value.to_string());
            }
            _ => {}
        }
    }

    synced_lyrics.or(unsynced_lyrics)
}

// ---------------------------------------------------------------------------
// FLAC Parser
// ---------------------------------------------------------------------------

pub fn parse_flac_lyrics(bytes: &[u8]) -> Option<String> {
    if bytes.len() < 4 || !bytes.starts_with(FLAC_MARKER) {
        return None;
    }

    let mut cursor = 4;
    let mut is_last_block = false;

    while !is_last_block && cursor + 4 <= bytes.len() {
        let header_byte = bytes[cursor];
        is_last_block = (header_byte & 0x80) != 0;
        let block_type = header_byte & 0x7F;
        let length = read_uint24(bytes, cursor + 1)? as usize;
        cursor += 4;

        if cursor + length > bytes.len() {
            return None;
        }

        if block_type == FLAC_VORBIS_COMMENT_BLOCK_TYPE {
            return parse_vorbis_comments(bytes, cursor);
        }

        cursor += length;
    }

    None
}

// ---------------------------------------------------------------------------
// MP4 Box Parser
// ---------------------------------------------------------------------------

pub fn parse_mp4_lyrics(bytes: &[u8]) -> Option<String> {
    parse_mp4_boxes(bytes, 0, bytes.len())
}

fn parse_mp4_boxes(bytes: &[u8], start: usize, end: usize) -> Option<String> {
    let mut cursor = start;
    while cursor + MP4_HEADER_SIZE <= end {
        let (box_size, header_size, box_type) = match read_mp4_header(bytes, cursor, end) {
            Some(h) => h,
            None => break,
        };

        if box_size < header_size || cursor + box_size > end {
            break;
        }

        let payload_offset = cursor + header_size;
        let _payload_size = box_size - header_size;

        match &box_type {
            b"meta" => {
                // meta box has 4 bytes of flags/version before child boxes
                let child_start = (payload_offset + 4).min(cursor + box_size);
                if let Some(lyrics) = parse_mp4_boxes(bytes, child_start, cursor + box_size) {
                    return Some(lyrics);
                }
            }
            b"moov" | b"udta" | b"ilst" => {
                if let Some(lyrics) = parse_mp4_boxes(bytes, payload_offset, cursor + box_size) {
                    return Some(lyrics);
                }
            }
            t if t == MP4_LYRICS_BOX_BYTES => {
                if let Some(lyrics) = parse_mp4_data_item(&bytes[payload_offset..cursor + box_size])
                {
                    return Some(lyrics);
                }
            }
            _ => {}
        }

        cursor += box_size;
    }

    None
}

fn read_mp4_header(bytes: &[u8], offset: usize, end: usize) -> Option<(usize, usize, [u8; 4])> {
    if offset + MP4_HEADER_SIZE > end {
        return None;
    }
    let small_size = read_int(bytes, offset)? as u32 as usize;
    let mut box_type = [0u8; 4];
    box_type.copy_from_slice(&bytes[offset + 4..offset + 8]);

    if small_size == 1 {
        // Extended 64-bit size
        if offset + 16 > end {
            return None;
        }
        let ext_size = read_u64(bytes, offset + 8)? as usize;
        Some((ext_size, 16, box_type))
    } else if small_size == 0 {
        // Box extends to EOF
        Some((end - offset, MP4_HEADER_SIZE, box_type))
    } else {
        Some((small_size, MP4_HEADER_SIZE, box_type))
    }
}

fn parse_mp4_data_item(bytes: &[u8]) -> Option<String> {
    let mut offset = 0;
    while offset + MP4_HEADER_SIZE <= bytes.len() {
        let size = (read_int(bytes, offset)? as u32) as usize;
        if size < MP4_HEADER_SIZE || offset + size > bytes.len() {
            return None;
        }
        if &bytes[offset + 4..offset + 8] == b"data" {
            let payload_offset = offset + MP4_HEADER_SIZE + MP4_DATA_HEADER_SIZE;
            if payload_offset <= offset + size {
                let payload = &bytes[payload_offset..offset + size];
                return decode_mp4_payload(payload);
            }
        }
        offset += size;
    }
    None
}

fn decode_mp4_payload(bytes: &[u8]) -> Option<String> {
    let utf8 = String::from_utf8_lossy(bytes).into_owned();
    let cleaned_utf8 = utf8.strip_prefix('\u{FEFF}').unwrap_or(&utf8);
    let trimmed_utf8 = cleaned_utf8.trim_matches(|c: char| c.is_whitespace() || c == '\0');

    let null_count = trimmed_utf8.chars().filter(|&c| c == '\0').count();
    if null_count <= trimmed_utf8.len() / 8 && !trimmed_utf8.is_empty() {
        return Some(trimmed_utf8.to_string());
    }

    let utf16 = decode_utf16_bom(bytes);
    let cleaned_utf16 = utf16.strip_prefix('\u{FEFF}').unwrap_or(&utf16);
    let trimmed_utf16 = cleaned_utf16.trim_matches(|c: char| c.is_whitespace() || c == '\0');
    if !trimmed_utf16.is_empty() {
        Some(trimmed_utf16.to_string())
    } else if !trimmed_utf8.is_empty() {
        Some(trimmed_utf8.to_string())
    } else {
        None
    }
}

// ---------------------------------------------------------------------------
// Helpers and Utilities
// ---------------------------------------------------------------------------

fn find_subsequence(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || haystack.len() < needle.len() {
        return None;
    }
    haystack.windows(needle.len()).position(|w| w == needle)
}

fn decode_iso_8859_1(bytes: &[u8]) -> String {
    bytes.iter().map(|&b| b as char).collect()
}

fn decode_utf16_bom(bytes: &[u8]) -> String {
    if bytes.len() < 2 {
        return String::new();
    }
    if bytes[0] == 0xFE && bytes[1] == 0xFF {
        decode_utf16_be(&bytes[2..])
    } else if bytes[0] == 0xFF && bytes[1] == 0xFE {
        decode_utf16_le(&bytes[2..])
    } else {
        decode_utf16_le(bytes)
    }
}

fn decode_utf16_le(bytes: &[u8]) -> String {
    let u16s: Vec<u16> = bytes
        .as_chunks::<2>()
        .0
        .iter()
        .map(|chunk| u16::from_le_bytes(*chunk))
        .collect();
    char::decode_utf16(u16s)
        .map(|r| r.unwrap_or('\u{FFFD}'))
        .collect()
}

fn decode_utf16_be(bytes: &[u8]) -> String {
    let u16s: Vec<u16> = bytes
        .as_chunks::<2>()
        .0
        .iter()
        .map(|chunk| u16::from_be_bytes(*chunk))
        .collect();
    char::decode_utf16(u16s)
        .map(|r| r.unwrap_or('\u{FFFD}'))
        .collect()
}

fn read_int(bytes: &[u8], offset: usize) -> Option<i32> {
    if offset + 4 > bytes.len() {
        return None;
    }
    Some(i32::from_be_bytes([
        bytes[offset],
        bytes[offset + 1],
        bytes[offset + 2],
        bytes[offset + 3],
    ]))
}

fn read_u64(bytes: &[u8], offset: usize) -> Option<u64> {
    if offset + 8 > bytes.len() {
        return None;
    }
    let mut arr = [0u8; 8];
    arr.copy_from_slice(&bytes[offset..offset + 8]);
    Some(u64::from_be_bytes(arr))
}

fn read_uint24(bytes: &[u8], offset: usize) -> Option<u32> {
    if offset + 3 > bytes.len() {
        return None;
    }
    Some(
        ((bytes[offset] as u32) << 16)
            | ((bytes[offset + 1] as u32) << 8)
            | (bytes[offset + 2] as u32),
    )
}

fn read_synchsafe_int(bytes: &[u8], offset: usize) -> Option<u32> {
    if offset + 4 > bytes.len() {
        return None;
    }
    Some(
        (((bytes[offset] & 0x7F) as u32) << 21)
            | (((bytes[offset + 1] & 0x7F) as u32) << 14)
            | (((bytes[offset + 2] & 0x7F) as u32) << 7)
            | ((bytes[offset + 3] & 0x7F) as u32),
    )
}

fn read_le_u32(bytes: &[u8], offset: usize) -> Option<u32> {
    if offset + 4 > bytes.len() {
        return None;
    }
    Some(u32::from_le_bytes([
        bytes[offset],
        bytes[offset + 1],
        bytes[offset + 2],
        bytes[offset + 3],
    ]))
}

// ---------------------------------------------------------------------------
// Unit Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_format_lrc_timestamp() {
        assert_eq!(format_lrc_timestamp(0), "[00:00.00]");
        assert_eq!(format_lrc_timestamp(61_234), "[01:01.23]");
        assert_eq!(format_lrc_timestamp(125_678), "[02:05.67]");
    }

    #[test]
    fn test_parse_id3_uslt() {
        let mut tag = Vec::new();
        tag.extend_from_slice(ID3_HEADER);
        tag.push(3); // majorVersion = 3 (ID3v2.3)
        tag.push(0); // revision
        tag.push(0); // flags

        // USLT frame payload: encoding 0 (ISO-8859-1), lang "eng", desc "\0", text "Hello lyrics"
        let mut frame_payload = vec![0, b'e', b'n', b'g', 0];
        frame_payload.extend_from_slice(b"Hello lyrics");

        let mut frame = Vec::new();
        frame.extend_from_slice(b"USLT");
        frame.extend_from_slice(&(frame_payload.len() as u32).to_be_bytes());
        frame.extend_from_slice(&[0, 0]); // flags
        frame.extend_from_slice(&frame_payload);

        let synchsafe_tag_size = ((frame.len() as u32) & 0x7F)
            | (((frame.len() as u32 >> 7) & 0x7F) << 8)
            | (((frame.len() as u32 >> 14) & 0x7F) << 16)
            | (((frame.len() as u32 >> 21) & 0x7F) << 24);

        tag.extend_from_slice(&synchsafe_tag_size.to_be_bytes());
        tag.extend_from_slice(&frame);

        let lyrics = extract_embedded_lyrics(&tag, Some("mp3"), Some("audio/mpeg"));
        assert_eq!(lyrics.as_deref(), Some("Hello lyrics"));
    }

    #[test]
    fn test_parse_id3_sylt() {
        let mut tag = Vec::new();
        tag.extend_from_slice(ID3_HEADER);
        tag.push(3);
        tag.push(0);
        tag.push(0);

        // SYLT payload: encoding 0, lang "eng", ts format 2, type 1, desc "\0"
        let mut frame_payload = vec![0, b'e', b'n', b'g', 2, 1, 0];
        // Line 1: "First line\0", timestamp 1000ms
        frame_payload.extend_from_slice(b"First line\0");
        frame_payload.extend_from_slice(&1000u32.to_be_bytes());
        // Line 2: "Second line\0", timestamp 5000ms
        frame_payload.extend_from_slice(b"Second line\0");
        frame_payload.extend_from_slice(&5000u32.to_be_bytes());

        let mut frame = Vec::new();
        frame.extend_from_slice(b"SYLT");
        frame.extend_from_slice(&(frame_payload.len() as u32).to_be_bytes());
        frame.extend_from_slice(&[0, 0]);
        frame.extend_from_slice(&frame_payload);

        let tag_size = frame.len() as u32;
        let synchsafe = (tag_size & 0x7F)
            | (((tag_size >> 7) & 0x7F) << 8)
            | (((tag_size >> 14) & 0x7F) << 16)
            | (((tag_size >> 21) & 0x7F) << 24);

        tag.extend_from_slice(&synchsafe.to_be_bytes());
        tag.extend_from_slice(&frame);

        let lyrics = extract_embedded_lyrics(&tag, Some("mp3"), Some("audio/mpeg"));
        assert_eq!(
            lyrics.as_deref(),
            Some("[00:01.00]First line\n[00:05.00]Second line")
        );
    }

    #[test]
    fn test_parse_vorbis_comments() {
        let mut buf = Vec::new();
        let vendor = b"reference-vendor";
        buf.extend_from_slice(&(vendor.len() as u32).to_le_bytes());
        buf.extend_from_slice(vendor);

        // 2 comments: TITLE=Test, SYNCEDLYRICS=[00:01.00]Synced
        buf.extend_from_slice(&2u32.to_le_bytes());

        let c1 = b"TITLE=Test";
        buf.extend_from_slice(&(c1.len() as u32).to_le_bytes());
        buf.extend_from_slice(c1);

        let c2 = b"SYNCEDLYRICS=[00:01.00]Synced";
        buf.extend_from_slice(&(c2.len() as u32).to_le_bytes());
        buf.extend_from_slice(c2);

        let res = parse_vorbis_comments(&buf, 0);
        assert_eq!(res.as_deref(), Some("[00:01.00]Synced"));
    }

    #[test]
    fn test_parse_flac_vorbis_comment() {
        let mut flac = Vec::new();
        flac.extend_from_slice(FLAC_MARKER);

        // Block type 4, isLast = true (0x84)
        let mut comment_payload = Vec::new();
        let vendor = b"flac-test";
        comment_payload.extend_from_slice(&(vendor.len() as u32).to_le_bytes());
        comment_payload.extend_from_slice(vendor);
        comment_payload.extend_from_slice(&1u32.to_le_bytes());
        let comment = b"LYRICS=Plain flac lyrics";
        comment_payload.extend_from_slice(&(comment.len() as u32).to_le_bytes());
        comment_payload.extend_from_slice(comment);

        flac.push(0x84);
        let len = comment_payload.len() as u32;
        flac.push(((len >> 16) & 0xFF) as u8);
        flac.push(((len >> 8) & 0xFF) as u8);
        flac.push((len & 0xFF) as u8);
        flac.extend_from_slice(&comment_payload);

        let lyrics = extract_embedded_lyrics(&flac, Some("flac"), Some("audio/flac"));
        assert_eq!(lyrics.as_deref(), Some("Plain flac lyrics"));
    }

    #[test]
    fn test_parse_mp4_lyrics() {
        let text = b"MP4 Embedded Lyric Text";
        let mut data_box = Vec::new();
        let data_box_size = (MP4_HEADER_SIZE + MP4_DATA_HEADER_SIZE + text.len()) as u32;
        data_box.extend_from_slice(&data_box_size.to_be_bytes());
        data_box.extend_from_slice(b"data");
        data_box.extend_from_slice(&[0, 0, 0, 1]); // type flags
        data_box.extend_from_slice(&[0, 0, 0, 0]); // locale
        data_box.extend_from_slice(text);

        let mut lyr_box = Vec::new();
        let lyr_box_size = (MP4_HEADER_SIZE + data_box.len()) as u32;
        lyr_box.extend_from_slice(&lyr_box_size.to_be_bytes());
        lyr_box.extend_from_slice(MP4_LYRICS_BOX_BYTES);
        lyr_box.extend_from_slice(&data_box);

        let mut ilst_box = Vec::new();
        let ilst_size = (MP4_HEADER_SIZE + lyr_box.len()) as u32;
        ilst_box.extend_from_slice(&ilst_size.to_be_bytes());
        ilst_box.extend_from_slice(b"ilst");
        ilst_box.extend_from_slice(&lyr_box);

        let mut moov_box = Vec::new();
        let moov_size = (MP4_HEADER_SIZE + ilst_box.len()) as u32;
        moov_box.extend_from_slice(&moov_size.to_be_bytes());
        moov_box.extend_from_slice(b"moov");
        moov_box.extend_from_slice(&ilst_box);

        let lyrics = extract_embedded_lyrics(&moov_box, Some("m4a"), Some("audio/mp4"));
        assert_eq!(lyrics.as_deref(), Some("MP4 Embedded Lyric Text"));
    }

    #[test]
    fn test_malformed_and_truncated_inputs_safe() {
        assert_eq!(extract_embedded_lyrics(&[], None, None), None);
        assert_eq!(
            extract_embedded_lyrics(b"ID3\x03\x00\x00\x7F\x7F\x7F\x7F", None, None),
            None
        );
        assert_eq!(
            extract_embedded_lyrics(b"fLaC\x84\x00\x00\xFF", None, None),
            None
        );
        assert_eq!(
            extract_embedded_lyrics(b"\x00\x00\x00\x20moov", None, None),
            None
        );
    }
}
