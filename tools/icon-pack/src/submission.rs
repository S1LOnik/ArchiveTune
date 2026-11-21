use crate::error::SubmissionError;
use crate::metadata::*;
use crate::svg::validate_svg;
use regex::Regex;
use serde_json::{json, Map, Value};
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::process::Command;

pub const METADATA_START: &str = "<!-- ICON_METADATA_START -->";
pub const METADATA_END: &str = "<!-- ICON_METADATA_END -->";

#[derive(Debug, Clone)]
pub struct SubmittedIcon {
    pub name: String,
    pub author: String,
    pub file: String,
    pub link: String,
}

pub fn parse_form(body: &str) -> Result<Vec<SubmittedIcon>, SubmissionError> {
    let start = body.find(METADATA_START);
    let end = body.find(METADATA_END);
    let markers_missing = start.is_none() && end.is_none();

    let json_content = if markers_missing {
        body.trim()
    } else {
        match (start, end) {
            (Some(s), Some(e)) if e > s => {
                let section = body[s + METADATA_START.len()..e].trim();
                let fenced_re = Regex::new(r"(?s)^```json\s*(.*?)\s*```$").unwrap();
                match fenced_re.captures(section) {
                    Some(caps) => caps.get(1).unwrap().as_str(),
                    None => {
                        return Err(SubmissionError(
                            "Icon metadata must remain inside the template's JSON block."
                                .to_string(),
                        ));
                    }
                }
            }
            _ => {
                return Err(SubmissionError(
                    "The icon metadata markers are missing or out of order.".to_string(),
                ));
            }
        }
    };

    let form: Value = serde_json::from_str(json_content)
        .map_err(|e| SubmissionError(format!("Icon metadata is not valid JSON: {e}")))?;

    let form_obj = match form.as_object() {
        Some(obj) if obj.len() == 1 && obj.contains_key("icons") => obj,
        _ => {
            return Err(SubmissionError(
                "Icon metadata must contain only an \"icons\" array.".to_string(),
            ));
        }
    };

    let raw_icons = match form_obj.get("icons").and_then(|v| v.as_array()) {
        Some(arr) if !arr.is_empty() => arr,
        _ => {
            return Err(SubmissionError(
                "The \"icons\" array must contain at least one icon.".to_string(),
            ));
        }
    };

    let allowed_fields: HashSet<&str> = ["name", "author", "file", "link"].into_iter().collect();
    let mut icons = Vec::new();
    let mut seen_files = HashSet::new();

    for (index, raw_icon) in raw_icons.iter().enumerate() {
        let icon_obj = match raw_icon.as_object() {
            Some(obj) => obj,
            None => {
                return Err(SubmissionError(format!(
                    "Icon #{} must be a JSON object.",
                    index + 1
                )));
            }
        };

        let mut unknown_fields = Vec::new();
        for key in icon_obj.keys() {
            if !allowed_fields.contains(key.as_str()) {
                unknown_fields.push(key.clone());
            }
        }
        if !unknown_fields.is_empty() {
            unknown_fields.sort();
            return Err(SubmissionError(format!(
                "Icon #{} contains unsupported fields: {}.",
                index + 1,
                unknown_fields.join(", ")
            )));
        }

        let name = match icon_obj.get("name").and_then(|v| v.as_str()) {
            Some(s) if !s.trim().is_empty() => s.trim().to_string(),
            _ => {
                return Err(SubmissionError(format!(
                    "Icon #{} requires a non-empty \"name\".",
                    index + 1
                )));
            }
        };

        let author = match icon_obj.get("author").and_then(|v| v.as_str()) {
            Some(s) if !s.trim().is_empty() => s.trim().to_string(),
            _ => {
                return Err(SubmissionError(format!(
                    "Icon #{} requires a non-empty \"author\".",
                    index + 1
                )));
            }
        };

        let raw_file = match icon_obj.get("file").and_then(|v| v.as_str()) {
            Some(s) if !s.trim().is_empty() => s.trim().to_string(),
            _ => {
                return Err(SubmissionError(format!(
                    "Icon #{} requires a non-empty \"file\".",
                    index + 1
                )));
            }
        };

        if name.chars().count() > 120 || author.chars().count() > 120 {
            return Err(SubmissionError(format!(
                "Icon #{} name and author must not exceed 120 characters.",
                index + 1
            )));
        }

        let link = match icon_obj.get("link") {
            Some(Value::String(s)) => s.trim().to_string(),
            Some(_) => {
                return Err(SubmissionError(format!(
                    "Icon #{} field \"link\" must be a string.",
                    index + 1
                )));
            }
            None => String::new(),
        };

        if !link.is_empty() {
            let valid_url = (link.starts_with("http://") || link.starts_with("https://"))
                && link
                    .split("://")
                    .nth(1)
                    .map(|rest| !rest.split('/').next().unwrap_or("").is_empty())
                    .unwrap_or(false);
            if !valid_url {
                return Err(SubmissionError(format!(
                    "Icon #{} field \"link\" must be an HTTP(S) URL or empty.",
                    index + 1
                )));
            }
        }

        let file_path = Path::new(&raw_file);
        let components: Vec<_> = file_path
            .components()
            .map(|c| c.as_os_str().to_str().unwrap_or(""))
            .collect();
        let valid_file = !file_path.is_absolute()
            && components.len() == 2
            && components[0] == "submissions"
            && components[1].to_ascii_lowercase().ends_with(".svg")
            && components[1] != ".svg"
            && components[1] != "..";

        if !valid_file {
            return Err(SubmissionError(format!(
                "Icon #{} file must be \"submissions/<filename>.svg\".",
                index + 1
            )));
        }

        let normalized_file = format!("submissions/{}", components[1]);
        if seen_files.contains(&normalized_file) {
            return Err(SubmissionError(format!(
                "Duplicate file in metadata: \"{normalized_file}\"."
            )));
        }
        seen_files.insert(normalized_file.clone());

        icons.push(SubmittedIcon {
            name,
            author,
            file: normalized_file,
            link,
        });
    }

    Ok(icons)
}

pub fn changed_paths(base_sha: &str) -> Result<Vec<(String, String)>, SubmissionError> {
    let output = Command::new("git")
        .args([
            "diff",
            "--name-status",
            "--no-renames",
            &format!("{base_sha}...HEAD"),
        ])
        .output()
        .map_err(|e| SubmissionError(format!("git diff failed: {e}")))?;

    if !output.status.success() {
        return Err(SubmissionError(format!(
            "git diff failed with {}",
            output.status
        )));
    }

    let text = String::from_utf8_lossy(&output.stdout);
    let mut changes = Vec::new();
    for line in text.lines() {
        if !line.is_empty() {
            let mut parts = line.split('\t');
            if let (Some(status), Some(path)) = (parts.next(), parts.next()) {
                changes.push((status.to_string(), path.to_string()));
            }
        }
    }
    Ok(changes)
}

pub fn validate_pr_changes(
    changes: &[(String, String)],
    icons: &[SubmittedIcon],
) -> Result<bool, SubmissionError> {
    let submitted_files: HashSet<&str> = icons.iter().map(|i| i.file.as_str()).collect();
    let actual_submissions: HashSet<&str> = changes
        .iter()
        .filter(|(_, p)| p.starts_with("submissions/"))
        .map(|(_, p)| p.as_str())
        .collect();

    if actual_submissions.is_empty() {
        let generated_change = changes
            .iter()
            .any(|(_, p)| p == "metadata.json" || p.starts_with("svg/"));
        if generated_change {
            println!("No unprocessed SVG submissions remain; nothing to do.");
            return Ok(false);
        }
        return Err(SubmissionError(
            "No SVG files were added under submissions/.".to_string(),
        ));
    }

    let mut invalid_changes = Vec::new();
    for (status, path) in changes {
        if status != "A" || !submitted_files.contains(path.as_str()) {
            invalid_changes.push(format!("{status} {path}"));
        }
    }
    if !invalid_changes.is_empty() {
        return Err(SubmissionError(format!(
            "An icon submission PR may initially add only the SVG files listed in its metadata:\n  {}",
            invalid_changes.join("\n  ")
        )));
    }

    if actual_submissions != submitted_files {
        let mut details = Vec::new();
        let mut missing: Vec<_> = submitted_files
            .difference(&actual_submissions)
            .copied()
            .collect();
        missing.sort();
        if !missing.is_empty() {
            details.push(format!("missing: {}", missing.join(", ")));
        }
        let mut unlisted: Vec<_> = actual_submissions
            .difference(&submitted_files)
            .copied()
            .collect();
        unlisted.sort();
        if !unlisted.is_empty() {
            details.push(format!("not listed: {}", unlisted.join(", ")));
        }
        return Err(SubmissionError(format!(
            "SVG file mismatch ({}).",
            details.join("; ")
        )));
    }

    Ok(true)
}

pub fn read_pr_body(event_path: &Path) -> Result<String, SubmissionError> {
    let data = fs::read_to_string(event_path)
        .map_err(|e| SubmissionError(format!("Cannot read the pull request body: {e}")))?;
    let val: Value = serde_json::from_str(&data)
        .map_err(|e| SubmissionError(format!("Cannot read the pull request body: {e}")))?;
    match val
        .get("pull_request")
        .and_then(|pr| pr.get("body"))
        .and_then(|b| b.as_str())
    {
        Some(body) => Ok(body.to_string()),
        None => Err(SubmissionError(
            "The pull request body is empty.".to_string(),
        )),
    }
}

pub fn read_pr_author_url(event_path: &Path) -> Result<String, SubmissionError> {
    let data = fs::read_to_string(event_path)
        .map_err(|e| SubmissionError(format!("Cannot read the pull request author: {e}")))?;
    let val: Value = serde_json::from_str(&data)
        .map_err(|e| SubmissionError(format!("Cannot read the pull request author: {e}")))?;
    let login = val
        .get("pull_request")
        .and_then(|pr| pr.get("user"))
        .and_then(|u| u.get("login"))
        .and_then(|l| l.as_str())
        .ok_or_else(|| SubmissionError("The pull request author login is invalid.".to_string()))?;

    let login_re = Regex::new(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$").unwrap();
    if !login_re.is_match(login) {
        return Err(SubmissionError(
            "The pull request author login is invalid.".to_string(),
        ));
    }

    Ok(format!("https://github.com/{login}"))
}

pub fn warn_duplicate_authors(icons: &[SubmittedIcon], metadata: &[Value]) {
    let existing_authors: HashSet<&str> = metadata
        .iter()
        .filter_map(|e| e.get("Author").and_then(|v| v.as_str()))
        .collect();

    let mut dupes: Vec<&str> = icons
        .iter()
        .map(|i| i.author.as_str())
        .filter(|a| existing_authors.contains(a))
        .collect();
    dupes.sort();
    dupes.dedup();

    for author in dupes {
        let msg = format!(
            "Author \"{author}\" already exists in metadata.json. Confirm that this submission uses the intended author name."
        );
        let escaped = msg
            .replace('%', "%25")
            .replace('\r', "%0D")
            .replace('\n', "%0A");
        println!("::warning title=Duplicate author name::{escaped}");
    }
}

pub fn synchronize_metadata_assets(
    metadata: &mut [Value],
    base_metadata: &[Value],
    svg_directory: &Path,
) -> Result<usize, SubmissionError> {
    let entries_by_id = metadata_by_id(metadata, "metadata.json")?;
    let base_entries_by_id = metadata_by_id(base_metadata, "base metadata.json")?;
    let mut synchronized_count = 0;

    for (identifier, &idx) in &entries_by_id {
        let entry = &mut metadata[idx];
        if let Some(obj) = entry.as_object_mut() {
            obj.remove("Filename");
        }
        if !base_entries_by_id.contains_key(identifier) {
            continue;
        }

        let name = metadata_text(entry, "Name", identifier)?;
        let author = metadata_text(entry, "Author", identifier)?;
        let current_path = current_svg_path(entry, identifier, svg_directory)?;
        let expected_filename = format!("{}.svg", svg_asset_stem(&name, &author, identifier));
        let expected_path = svg_directory.join(&expected_filename);

        if expected_path != current_path {
            if expected_path.exists() {
                return Err(SubmissionError(format!(
                    "Cannot rename {} because {} already exists.",
                    current_path.display(),
                    expected_path.display()
                )));
            }
            fs::rename(&current_path, &expected_path)?;
            synchronized_count += 1;
        }

        if entry.get("Source").and_then(|v| v.as_str()) != Some(&expected_filename) {
            entry["Source"] = Value::String(expected_filename);
            synchronized_count += 1;
        }
        entry["Id"] = Value::String(identifier.clone());
        entry["Name"] = Value::String(name);
        entry["Author"] = Value::String(author);
    }

    Ok(synchronized_count)
}

pub fn remove_unreferenced_svg_files(
    metadata: &[Value],
    svg_directory: &Path,
) -> Result<usize, SubmissionError> {
    let mut referenced_files = HashSet::new();
    for entry in metadata {
        let id = match entry.get("Id") {
            Some(Value::String(s)) => s.clone(),
            Some(Value::Number(n)) => n.to_string(),
            _ => continue,
        };
        if let Ok(src) = source_filename(entry, &id) {
            referenced_files.insert(src);
        }
    }

    let mut removed_count = 0;
    if svg_directory.is_dir() {
        if let Ok(entries) = fs::read_dir(svg_directory) {
            for dir_entry in entries.flatten() {
                let path = dir_entry.path();
                let name = match path.file_name().and_then(|n| n.to_str()) {
                    Some(n) => n,
                    None => continue,
                };
                if name == ".gitkeep" {
                    continue;
                }
                let is_symlink = fs::symlink_metadata(&path)
                    .map(|m| m.file_type().is_symlink())
                    .unwrap_or(false);
                if path.is_dir() && !is_symlink {
                    continue;
                }
                if !referenced_files.contains(name) {
                    fs::remove_file(&path)?;
                    removed_count += 1;
                }
            }
        }
    }

    Ok(removed_count)
}

pub fn reconcile_existing_icons(
    metadata: &mut [Value],
    svg_directory: &Path,
) -> Result<(usize, usize), SubmissionError> {
    let entries_by_id = metadata_by_id(metadata, "metadata.json")?;
    let mut expected_filenames = HashSet::new();
    let mut synchronized_count = 0;

    for (identifier, &idx) in &entries_by_id {
        let entry = &mut metadata[idx];
        let name = metadata_text(entry, "Name", identifier)?;
        let author = metadata_text(entry, "Author", identifier)?;
        let current_path = current_svg_path(entry, identifier, svg_directory)?;
        let expected_filename = format!("{}.svg", svg_asset_stem(&name, &author, identifier));

        if expected_filenames.contains(&expected_filename) {
            return Err(SubmissionError(format!(
                "Duplicate generated SVG filename \"{expected_filename}\"."
            )));
        }
        expected_filenames.insert(expected_filename.clone());

        let expected_path = svg_directory.join(&expected_filename);
        if expected_path != current_path {
            if expected_path.exists() {
                return Err(SubmissionError(format!(
                    "Cannot rename {} because {} already exists.",
                    current_path.display(),
                    expected_path.display()
                )));
            }
            fs::rename(&current_path, &expected_path)?;
            synchronized_count += 1;
        }

        if entry.get("Source").and_then(|v| v.as_str()) != Some(&expected_filename) {
            entry["Source"] = Value::String(expected_filename);
            synchronized_count += 1;
        }
        if let Some(obj) = entry.as_object_mut() {
            if obj.remove("Filename").is_some() {
                synchronized_count += 1;
            }
        }
        entry["Id"] = Value::String(identifier.clone());
        entry["Name"] = Value::String(name);
        entry["Author"] = Value::String(author);
    }

    let removed_count = remove_unreferenced_svg_files(metadata, svg_directory)?;
    Ok((synchronized_count, removed_count))
}

pub fn process_pull_request(
    event_path: Option<&Path>,
    base_sha: Option<&str>,
    random_digits: usize,
) -> Result<(), SubmissionError> {
    let event_path = event_path.ok_or_else(|| {
        SubmissionError("Pull-request mode requires --event-path and --base-sha.".to_string())
    })?;
    let base_sha = base_sha.ok_or_else(|| {
        SubmissionError("Pull-request mode requires --event-path and --base-sha.".to_string())
    })?;

    let changes = changed_paths(base_sha)?;
    let metadata_path = Path::new("metadata.json");
    let svg_dir = Path::new("svg");

    if !changes.iter().any(|(_, p)| p.starts_with("submissions/")) {
        if !changes.iter().any(|(_, p)| p == "metadata.json") {
            println!("No unprocessed SVG submissions remain; nothing to do.");
            return Ok(());
        }
        let mut metadata = read_metadata(metadata_path)?;
        let base_metadata = read_metadata_at_revision(base_sha)?;
        let sync_count = synchronize_metadata_assets(&mut metadata, &base_metadata, svg_dir)?;
        write_metadata(metadata_path, &metadata)?;
        println!("Synchronized {sync_count} SVG asset change(s).");
        return Ok(());
    }

    let pr_body = read_pr_body(event_path)?;
    let icons = parse_form(&pr_body)?;
    if !validate_pr_changes(&changes, &icons)? {
        return Ok(());
    }

    let github_author_url = read_pr_author_url(event_path)?;
    let mut metadata = read_metadata(metadata_path)?;
    let base_metadata = read_metadata_at_revision(base_sha)?;
    warn_duplicate_authors(&icons, &base_metadata);

    let mut identifiers = used_ids(&metadata, random_digits, svg_dir);
    fs::create_dir_all(svg_dir)?;

    let mut pending_assets = Vec::new();
    for icon in &icons {
        let submission = Path::new(&icon.file);
        validate_svg(submission)?;
        let identifier = next_id(&mut identifiers, random_digits)?;
        let destination_filename = format!(
            "{}.svg",
            svg_asset_stem(&icon.name, &icon.author, &identifier)
        );
        let destination = svg_dir.join(&destination_filename);
        if destination.exists() {
            return Err(SubmissionError(format!(
                "{} already exists.",
                destination.display()
            )));
        }

        let mut entry = Map::new();
        entry.insert("Id".to_string(), json!(identifier));
        entry.insert("Name".to_string(), json!(icon.name));
        entry.insert("Author".to_string(), json!(icon.author));
        entry.insert("Source".to_string(), json!(destination_filename));
        entry.insert("Submission".to_string(), json!(icon.file));
        entry.insert("Link".to_string(), json!(icon.link));
        entry.insert("GitHubAuthorUrl".to_string(), json!(github_author_url));

        pending_assets.push((submission.to_path_buf(), destination, Value::Object(entry)));
    }

    for (submission, destination, entry) in pending_assets {
        fs::rename(&submission, &destination)?;
        metadata.push(entry);
    }

    write_metadata(metadata_path, &metadata)?;
    println!("Stored {} submitted SVG icon(s).", icons.len());
    Ok(())
}

pub fn rebuild_existing_icons(_random_digits: usize) -> Result<(), SubmissionError> {
    let metadata_path = Path::new("metadata.json");
    let svg_dir = Path::new("svg");
    let mut metadata = read_metadata(metadata_path)?;
    metadata_by_id(&metadata, "metadata.json")?;

    let id_re = Regex::new(r"^\d{4}|\d{6}$").unwrap();
    let submissions_dir = Path::new("submissions");
    let mut submissions = Vec::new();
    if submissions_dir.is_dir() {
        if let Ok(entries) = fs::read_dir(submissions_dir) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_file()
                    && p.extension()
                        .and_then(|e| e.to_str())
                        .map(|e| e.eq_ignore_ascii_case("svg"))
                        .unwrap_or(false)
                {
                    submissions.push(p);
                }
            }
        }
    }
    submissions.sort();

    let mut rebuilt_count = 0;
    for submission in submissions {
        let sub_str = submission.to_str().unwrap_or("");
        let sub_name = submission
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("");

        let mut matches = Vec::new();
        for (i, entry) in metadata.iter().enumerate() {
            let id = match entry.get("Id") {
                Some(Value::String(s)) => s.clone(),
                Some(Value::Number(n)) => n.to_string(),
                _ => String::new(),
            };
            let source = entry.get("Source").and_then(|v| v.as_str());
            let orig_sub = entry.get("Submission").and_then(|v| v.as_str());

            if orig_sub == Some(sub_str)
                || source == Some(sub_name)
                || (id_re.is_match(&id) && sub_name == format!("{id}.svg"))
            {
                matches.push(i);
            }
        }

        if matches.is_empty() {
            return Err(SubmissionError(format!(
                "{} has no matching metadata entry.",
                submission.display()
            )));
        }
        if matches.len() > 1 {
            return Err(SubmissionError(format!(
                "{} matches multiple metadata entries.",
                submission.display()
            )));
        }

        let idx = matches[0];
        let entry = &mut metadata[idx];
        let identifier = match entry.get("Id") {
            Some(Value::String(s)) => s.clone(),
            Some(Value::Number(n)) => n.to_string(),
            _ => String::new(),
        };
        let name = metadata_text(entry, "Name", &identifier)?;
        let author = metadata_text(entry, "Author", &identifier)?;
        validate_svg(&submission)?;

        let expected_filename = format!("{}.svg", svg_asset_stem(&name, &author, &identifier));
        let expected_path = svg_dir.join(&expected_filename);
        let previous_path = current_svg_path(entry, &identifier, svg_dir)?;

        if expected_path != previous_path && expected_path.exists() {
            return Err(SubmissionError(format!(
                "{} already exists.",
                expected_path.display()
            )));
        }

        fs::create_dir_all(svg_dir)?;
        fs::rename(&submission, &expected_path)?;
        if previous_path != expected_path && previous_path.exists() {
            fs::remove_file(&previous_path)?;
        }

        if let Some(obj) = entry.as_object_mut() {
            obj.remove("Filename");
        }
        entry["Id"] = Value::String(identifier);
        entry["Name"] = Value::String(name);
        entry["Author"] = Value::String(author);
        entry["Source"] = Value::String(expected_filename);
        rebuilt_count += 1;
    }

    let (update_count, removed_count) = reconcile_existing_icons(&mut metadata, svg_dir)?;
    write_metadata(metadata_path, &metadata)?;
    println!(
        "Rebuilt {rebuilt_count} SVG icon(s), synchronized {update_count} metadata asset(s), and removed {removed_count} unreferenced SVG file(s)."
    );
    Ok(())
}

pub fn update_existing_icons(_random_digits: usize) -> Result<(), SubmissionError> {
    let metadata_path = Path::new("metadata.json");
    let svg_dir = Path::new("svg");
    let mut metadata = read_metadata(metadata_path)?;
    let (sync_count, removed_count) = reconcile_existing_icons(&mut metadata, svg_dir)?;
    write_metadata(metadata_path, &metadata)?;
    println!(
        "Updated {} SVG icon(s), synchronized {sync_count} metadata asset(s), and removed {removed_count} unreferenced SVG file(s).",
        metadata.len()
    );
    Ok(())
}
