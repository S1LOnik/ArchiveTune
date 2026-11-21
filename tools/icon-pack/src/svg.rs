use crate::error::SubmissionError;
use quick_xml::events::Event;
use quick_xml::reader::Reader;
use regex::bytes::Regex as BytesRegex;
use std::fs;
use std::io::Cursor;
use std::path::Path;

pub const MAX_SVG_BYTES: u64 = 700_000;

pub fn validate_path(path: &Path) -> Result<(), SubmissionError> {
    let mut current = Some(path);
    while let Some(p) = current {
        if p.as_os_str().is_empty() {
            break;
        }
        if fs::symlink_metadata(p)
            .map(|m| m.file_type().is_symlink())
            .unwrap_or(false)
        {
            return Err(SubmissionError(format!(
                "Refusing symlink: {}",
                p.display()
            )));
        }
        current = p.parent();
    }
    if path.exists() && !path.is_file() {
        return Err(SubmissionError(format!(
            "Expected a regular file: {}",
            path.display()
        )));
    }
    Ok(())
}

pub fn validate_svg(path: &Path) -> Result<(), SubmissionError> {
    let is_symlink = fs::symlink_metadata(path)
        .map(|m| m.file_type().is_symlink())
        .unwrap_or(false);
    if is_symlink || !path.is_file() {
        return Err(SubmissionError(format!(
            "{} must be a regular file.",
            path.display()
        )));
    }

    let meta = fs::metadata(path).map_err(|e| SubmissionError(e.to_string()))?;
    let size = meta.len();
    if size == 0 || size > MAX_SVG_BYTES {
        return Err(SubmissionError(format!(
            "{} must be non-empty and no larger than {} KB.",
            path.display(),
            MAX_SVG_BYTES / 1_000
        )));
    }

    let svg_bytes = fs::read(path).map_err(|e| SubmissionError(e.to_string()))?;
    if svg_bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        return Err(SubmissionError(format!(
            "{} contains PNG data. Export and submit an actual SVG XML file instead of renaming a PNG file.",
            path.display()
        )));
    }

    let upper_bytes: Vec<u8> = svg_bytes.iter().map(|b| b.to_ascii_uppercase()).collect();
    if upper_bytes.windows(8).any(|w| w == b"<!ENTITY") {
        return Err(SubmissionError(format!(
            "{} must not declare an entity.",
            path.display()
        )));
    }

    let doctype_re = BytesRegex::new(
        r#"(?i)<!DOCTYPE\s+svg(?:\s+(?:PUBLIC|SYSTEM)\s+(?:"[^"]*"|'[^']*')(?:\s+(?:"[^"]*"|'[^']*'))?)?\s*>"#,
    )
    .unwrap();

    let mut doctype_count = 0;
    let sanitized_bytes = if let Some(m) = doctype_re.find(&svg_bytes) {
        doctype_count = 1;
        let mut res = Vec::with_capacity(svg_bytes.len() - m.len());
        res.extend_from_slice(&svg_bytes[..m.start()]);
        res.extend_from_slice(&svg_bytes[m.end()..]);
        res
    } else {
        svg_bytes.clone()
    };

    let sanitized_upper: Vec<u8> = sanitized_bytes
        .iter()
        .map(|b| b.to_ascii_uppercase())
        .collect();
    if sanitized_upper.windows(9).any(|w| w == b"<!DOCTYPE") {
        return Err(SubmissionError(format!(
            "{} contains an unsupported DTD declaration.",
            path.display()
        )));
    }

    // Parse XML with quick-xml
    let mut reader = Reader::from_reader(Cursor::new(&sanitized_bytes));
    reader.config_mut().trim_text(true);

    let mut is_first_element = true;
    let mut buf = Vec::new();

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(e)) | Ok(Event::Empty(e)) => {
                let full_name = e.name();
                let local_name = full_name.local_name();
                let local_str = String::from_utf8_lossy(local_name.as_ref()).to_ascii_lowercase();

                if is_first_element {
                    if local_str != "svg" {
                        return Err(SubmissionError(format!(
                            "{} does not have an SVG root element.",
                            path.display()
                        )));
                    }
                    is_first_element = false;
                }

                if local_str == "script" || local_str == "foreignobject" {
                    return Err(SubmissionError(format!(
                        "{} contains unsupported active SVG content.",
                        path.display()
                    )));
                }

                for attr in e.attributes().flatten() {
                    let attr_name = attr.key.local_name();
                    let attr_name_str =
                        String::from_utf8_lossy(attr_name.as_ref()).to_ascii_lowercase();
                    if attr_name_str == "href" {
                        let value = String::from_utf8_lossy(&attr.value);
                        if !value.is_empty() && !value.starts_with('#') {
                            return Err(SubmissionError(format!(
                                "{} contains an external SVG reference.",
                                path.display()
                            )));
                        }
                    }
                }
            }
            Ok(Event::Eof) => break,
            Err(e) => {
                return Err(SubmissionError(format!(
                    "{} is not valid XML: {}",
                    path.display(),
                    e
                )));
            }
            _ => {}
        }
        buf.clear();
    }

    if is_first_element {
        return Err(SubmissionError(format!(
            "{} does not have an SVG root element.",
            path.display()
        )));
    }

    if doctype_count > 0 {
        fs::write(path, &sanitized_bytes).map_err(|e| SubmissionError(e.to_string()))?;
    }

    Ok(())
}
