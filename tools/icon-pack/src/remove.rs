use crate::error::SubmissionError;
use crate::metadata::*;
use crate::svg::validate_path;
use regex::Regex;
use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};

pub fn remove_icon_pack(name: &str) -> Result<(), SubmissionError> {
    let name_re = Regex::new(r"^[A-Za-z0-9][A-Za-z0-9_-]*(?:\.svg)?$").unwrap();
    if !name_re.is_match(name) {
        return Err(SubmissionError(
            "Use an exact filename from svg/, with or without .svg.".to_string(),
        ));
    }

    let filename = if name.ends_with(".svg") {
        name.to_string()
    } else {
        format!("{name}.svg")
    };

    let metadata_path = Path::new("metadata.json");
    validate_path(metadata_path)?;
    let metadata = read_metadata(metadata_path)?;
    metadata_by_id(&metadata, "metadata.json")?;

    let mut matches = Vec::new();
    for (i, entry) in metadata.iter().enumerate() {
        if entry.get("Source").and_then(|v| v.as_str()) == Some(&filename) {
            matches.push(i);
        }
    }

    if matches.len() != 1 {
        return Err(SubmissionError(format!(
            "Expected exactly one metadata entry for {filename}; found {}.",
            matches.len()
        )));
    }

    let match_idx = matches[0];
    let entry = &metadata[match_idx];
    let identifier = match entry.get("Id") {
        Some(serde_json::Value::String(s)) => s.clone(),
        Some(serde_json::Value::Number(n)) => n.to_string(),
        _ => return Err(SubmissionError("Missing Id in metadata entry".to_string())),
    };

    let source = Path::new("svg").join(&filename);
    validate_path(&source)?;
    if !source.is_file() {
        return Err(SubmissionError(format!(
            "Source does not exist: {}",
            source.display()
        )));
    }

    let remaining: Vec<_> = metadata
        .iter()
        .enumerate()
        .filter(|&(i, _)| i != match_idx)
        .map(|(_, item)| item.clone())
        .collect();

    let mut submissions = HashSet::new();
    submissions.insert(Path::new("submissions").join(&filename));
    submissions.insert(Path::new("submissions").join(format!("{identifier}.svg")));

    if let Some(orig) = entry.get("Submission").and_then(|v| v.as_str()) {
        let orig_re = Regex::new(r"^submissions/[^/\\\x00-\x1f]+\.svg$").unwrap();
        if !orig_re.is_match(orig) {
            return Err(SubmissionError(
                "Invalid Submission path in metadata.".to_string(),
            ));
        }
        submissions.insert(PathBuf::from(orig));
    }

    let mut shared_submissions = HashSet::new();
    for item in &remaining {
        if let Some(s) = item.get("Submission").and_then(|v| v.as_str()) {
            shared_submissions.insert(s.to_string());
        }
        if let Some(src) = item.get("Source").and_then(|v| v.as_str()) {
            shared_submissions.insert(format!("submissions/{src}"));
        }
        if let Some(id_val) = item.get("Id") {
            let id_str = match id_val {
                serde_json::Value::String(s) => s.clone(),
                serde_json::Value::Number(n) => n.to_string(),
                _ => String::new(),
            };
            shared_submissions.insert(format!("submissions/{id_str}.svg"));
        }
    }

    let mut targets = HashSet::new();
    targets.insert(source);
    targets.insert(Path::new("previews").join(format!("{identifier}.png")));

    for path in submissions {
        let posix_str = path.to_string_lossy().replace('\\', "/");
        if !shared_submissions.contains(&posix_str) {
            targets.insert(path);
        }
    }

    for (directory, suffix) in [("xml", ".xml"), ("pack", ".kt")] {
        let root = Path::new(directory);
        if fs::symlink_metadata(root)
            .map(|m| m.file_type().is_symlink())
            .unwrap_or(false)
        {
            return Err(SubmissionError(format!(
                "Refusing symlink: {}",
                root.display()
            )));
        }
        if root.exists() && !root.is_dir() {
            return Err(SubmissionError(format!(
                "Expected a directory: {}",
                root.display()
            )));
        }
        if root.is_dir() {
            if let Ok(entries) = fs::read_dir(root) {
                for entry in entries.flatten() {
                    let path = entry.path();
                    if path.is_file()
                        && path
                            .file_name()
                            .and_then(|n| n.to_str())
                            .map(|n| n.ends_with(suffix))
                            .unwrap_or(false)
                    {
                        if let Some(stem) = path.file_stem().and_then(|s| s.to_str()) {
                            if stem.ends_with(&identifier) {
                                let prefix_len = stem.len() - identifier.len();
                                let char_before = stem[..prefix_len].chars().next_back();
                                let is_digit_before =
                                    char_before.map(|c| c.is_ascii_digit()).unwrap_or(false);
                                if !is_digit_before {
                                    targets.insert(path);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    for path in &targets {
        validate_path(path)?;
    }

    let mut sorted_targets: Vec<_> = targets.into_iter().collect();
    sorted_targets.sort();

    for path in &sorted_targets {
        if path.exists() {
            fs::remove_file(path)?;
            println!("Removed {}", path.display());
        }
    }

    write_metadata(metadata_path, &remaining)?;
    println!("Removed metadata entry {identifier} ({filename}).");
    Ok(())
}
