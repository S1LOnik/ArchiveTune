use crate::error::SubmissionError;
use regex::Regex;
use serde_json::Value;
use std::collections::{HashMap, HashSet};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use unicode_normalization::UnicodeNormalization;

pub const MAX_FILENAME_COMPONENT_LENGTH: usize = 48;

pub fn read_metadata(path: &Path) -> Result<Vec<Value>, SubmissionError> {
    let content = match fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => {
            return Err(SubmissionError(format!(
                "Cannot read {}: {}",
                path.display(),
                e
            )))
        }
    };
    let parsed: Value = serde_json::from_str(&content)
        .map_err(|e| SubmissionError(format!("Cannot read {}: {}", path.display(), e)))?;

    match parsed {
        Value::Array(arr) => {
            for item in &arr {
                if !item.is_object() {
                    return Err(SubmissionError(format!(
                        "{} must be an array of objects.",
                        path.display()
                    )));
                }
            }
            Ok(arr)
        }
        _ => Err(SubmissionError(format!(
            "{} must be an array of objects.",
            path.display()
        ))),
    }
}

pub fn read_metadata_at_revision(revision: &str) -> Result<Vec<Value>, SubmissionError> {
    let output = Command::new("git")
        .args(["show", &format!("{revision}:metadata.json")])
        .output()
        .map_err(|e| {
            SubmissionError(format!(
                "Cannot read metadata.json at revision {revision}: {e}"
            ))
        })?;

    if !output.status.success() {
        return Err(SubmissionError(format!(
            "Cannot read metadata.json at revision {revision}: git show failed with {}",
            output.status
        )));
    }

    let parsed: Value = serde_json::from_slice(&output.stdout).map_err(|e| {
        SubmissionError(format!(
            "Cannot read metadata.json at revision {revision}: {e}"
        ))
    })?;

    match parsed {
        Value::Array(arr) => {
            for item in &arr {
                if !item.is_object() {
                    return Err(SubmissionError(format!(
                        "metadata.json at revision {revision} must be an array of objects."
                    )));
                }
            }
            Ok(arr)
        }
        _ => Err(SubmissionError(format!(
            "metadata.json at revision {revision} must be an array of objects."
        ))),
    }
}

pub fn metadata_by_id(
    metadata: &[Value],
    source: &str,
) -> Result<HashMap<String, usize>, SubmissionError> {
    let id_re = Regex::new(r"^\d{4}|\d{6}$").unwrap();
    let mut entries_by_id = HashMap::new();

    for (index, entry) in metadata.iter().enumerate() {
        let identifier_val = match entry.get("Id") {
            Some(v) => v,
            None => {
                return Err(SubmissionError(format!(
                    "{source} entry #{} requires an Id.",
                    index + 1
                )))
            }
        };

        let identifier = match identifier_val {
            Value::String(s) => s.clone(),
            Value::Number(n) => n.to_string(),
            _ => {
                return Err(SubmissionError(format!(
                    "{source} entry #{} requires an Id.",
                    index + 1
                )))
            }
        };

        if !id_re.is_match(&identifier) {
            return Err(SubmissionError(format!(
                "{source} entry #{} has invalid Id \"{identifier}\".",
                index + 1
            )));
        }

        if entries_by_id.contains_key(&identifier) {
            return Err(SubmissionError(format!(
                "{source} contains duplicate Id \"{identifier}\"."
            )));
        }

        entries_by_id.insert(identifier, index);
    }

    Ok(entries_by_id)
}

pub fn metadata_text(
    entry: &Value,
    field: &str,
    identifier: &str,
) -> Result<String, SubmissionError> {
    match entry.get(field) {
        Some(Value::String(s)) if !s.trim().is_empty() => Ok(s.trim().to_string()),
        _ => Err(SubmissionError(format!(
            "Metadata entry with Id \"{identifier}\" requires a {field}."
        ))),
    }
}

pub fn source_filename(entry: &Value, identifier: &str) -> Result<String, SubmissionError> {
    match entry.get("Source") {
        Some(Value::String(s))
            if !s.is_empty()
                && Path::new(s).file_name().and_then(|f| f.to_str()) == Some(s)
                && s.to_ascii_lowercase().ends_with(".svg") =>
        {
            Ok(s.clone())
        }
        _ => Err(SubmissionError(format!(
            "Metadata entry with Id \"{identifier}\" requires a valid SVG Source."
        ))),
    }
}

pub fn current_svg_path(
    entry: &Value,
    identifier: &str,
    svg_directory: &Path,
) -> Result<PathBuf, SubmissionError> {
    let filename = source_filename(entry, identifier)?;
    let declared_path = svg_directory.join(&filename);
    if declared_path.is_file()
        && fs::symlink_metadata(&declared_path)
            .map(|m| !m.file_type().is_symlink())
            .unwrap_or(false)
    {
        return Ok(declared_path);
    }

    let mut candidates = Vec::new();
    if svg_directory.is_dir() {
        if let Ok(entries) = fs::read_dir(svg_directory) {
            for dir_entry in entries.flatten() {
                let path = dir_entry.path();
                let is_symlink = fs::symlink_metadata(&path)
                    .map(|m| m.file_type().is_symlink())
                    .unwrap_or(false);
                if path.is_file()
                    && !is_symlink
                    && path
                        .extension()
                        .and_then(|e| e.to_str())
                        .map(|e| e.eq_ignore_ascii_case("svg"))
                        .unwrap_or(false)
                {
                    if let Some(stem) = path.file_stem().and_then(|s| s.to_str()) {
                        if stem.ends_with(&format!("_{identifier}")) {
                            candidates.push(path);
                        }
                    }
                }
            }
        }
    }

    if candidates.is_empty() {
        return Err(SubmissionError(format!(
            "Cannot find current SVG asset for Id \"{identifier}\"."
        )));
    }
    if candidates.len() > 1 {
        candidates.sort();
        let filenames: Vec<String> = candidates
            .iter()
            .filter_map(|p| {
                p.file_name()
                    .and_then(|n| n.to_str())
                    .map(|s| s.to_string())
            })
            .collect();
        return Err(SubmissionError(format!(
            "Multiple SVG assets match Id \"{identifier}\": {}.",
            filenames.join(", ")
        )));
    }

    Ok(candidates.remove(0))
}

pub fn svg_filename_component(value: &str, fallback: &str) -> String {
    let ascii: String = value.nfkd().filter(|c| c.is_ascii()).collect();
    let re = Regex::new(r"[^a-z0-9]+").unwrap();
    let lower = ascii.to_ascii_lowercase();
    let replaced = re.replace_all(&lower, "_");
    let trimmed = replaced.trim_matches('_');
    let truncated = if trimmed.len() > MAX_FILENAME_COMPONENT_LENGTH {
        &trimmed[..MAX_FILENAME_COMPONENT_LENGTH]
    } else {
        trimmed
    };
    let result = truncated.trim_end_matches('_');
    if result.is_empty() {
        fallback.to_string()
    } else {
        result.to_string()
    }
}

pub fn svg_asset_stem(name: &str, author: &str, identifier: &str) -> String {
    let icon_name = svg_filename_component(name, "icon");
    let author_name = svg_filename_component(author, "author");
    format!("{icon_name}_{author_name}_{identifier}")
}

pub fn used_ids(metadata: &[Value], random_digits: usize, svg_directory: &Path) -> HashSet<String> {
    let id_pattern = Regex::new(&format!(r"^\d{{{random_digits}}}$")).unwrap();
    let filename_pattern = Regex::new(&format!(r"_(\d{{{random_digits}}})(?:\.|$)")).unwrap();

    let mut identifiers = HashSet::new();

    for entry in metadata {
        if let Some(id_val) = entry.get("Id") {
            let id_str = match id_val {
                Value::String(s) => s.clone(),
                Value::Number(n) => n.to_string(),
                _ => continue,
            };
            if id_pattern.is_match(&id_str) {
                identifiers.insert(id_str);
            }
        }
    }

    if svg_directory.is_dir() {
        if let Ok(entries) = fs::read_dir(svg_directory) {
            for entry in entries.flatten() {
                if let Some(name) = entry.file_name().to_str() {
                    if let Some(caps) = filename_pattern.captures(name) {
                        if let Some(m) = caps.get(1) {
                            identifiers.insert(m.as_str().to_string());
                        }
                    }
                }
            }
        }
    }

    identifiers
}

pub fn next_id(
    existing: &mut HashSet<String>,
    random_digits: usize,
) -> Result<String, SubmissionError> {
    let lower_bound: u64 = 10u64.pow((random_digits - 1) as u32);
    let available: u64 = 9 * lower_bound;

    if existing.len() as u64 >= available {
        return Err(SubmissionError(format!(
            "All {random_digits}-digit icon IDs are already in use."
        )));
    }

    // Pseudorandom generation
    use std::time::{SystemTime, UNIX_EPOCH};
    let mut seed = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos() as u64;

    loop {
        // xorshift64star
        seed ^= seed >> 12;
        seed ^= seed << 25;
        seed ^= seed >> 27;
        let rand_val = seed.wrapping_mul(0x2545F4914F6CDD1D);
        let id_num = lower_bound + (rand_val % available);
        let id_str = id_num.to_string();
        if !existing.contains(&id_str) {
            existing.insert(id_str.clone());
            return Ok(id_str);
        }
    }
}

pub fn write_metadata(path: &Path, metadata: &[Value]) -> Result<(), SubmissionError> {
    let mut buf = Vec::new();
    let formatter = serde_json::ser::PrettyFormatter::with_indent(b"  ");
    let mut ser = serde_json::Serializer::with_formatter(&mut buf, formatter);
    serde::Serialize::serialize(&metadata, &mut ser)
        .map_err(|e| SubmissionError(format!("Failed to serialize metadata: {e}")))?;
    buf.push(b'\n');
    fs::write(path, buf)
        .map_err(|e| SubmissionError(format!("Failed to write {}: {}", path.display(), e)))?;
    Ok(())
}
