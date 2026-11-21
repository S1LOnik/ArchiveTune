use crate::error::SubmissionError;
use crate::metadata::*;
use regex::Regex;
use serde_json::{json, Value};
use std::collections::HashSet;
use std::fs;
use std::path::Path;
use std::process::Command;

pub const COMMENT_MARKER: &str = "<!-- icon-pack-generated-previews -->";

pub fn generate_previews(
    base_sha: &str,
    metadata_path: &Path,
    svg_directory: &Path,
    output_directory: &Path,
    manifest_path: &Path,
    renderer: &Path,
) -> Result<(), SubmissionError> {
    if !renderer.is_file() {
        return Err(SubmissionError(format!(
            "Renderer not found at {}.",
            renderer.display()
        )));
    }

    let metadata = read_metadata(metadata_path)?;
    let base_metadata = read_metadata_at_revision(base_sha)?;

    let mut base_ids = HashSet::new();
    for entry in &base_metadata {
        if let Some(id_val) = entry.get("Id") {
            let id_str = match id_val {
                Value::String(s) => s.clone(),
                Value::Number(n) => n.to_string(),
                _ => continue,
            };
            base_ids.insert(id_str);
        }
    }

    let id_re = Regex::new(r"^\d{4}|\d{6}$").unwrap();
    let mut generated_entries = Vec::new();
    for entry in &metadata {
        let id_val = entry
            .get("Id")
            .ok_or_else(|| SubmissionError("Invalid metadata icon Id \"\".".to_string()))?;
        let id_str = match id_val {
            Value::String(s) => s.clone(),
            Value::Number(n) => n.to_string(),
            _ => {
                return Err(SubmissionError(
                    "Invalid metadata icon Id \"\".".to_string(),
                ))
            }
        };
        if !id_re.is_match(&id_str) {
            return Err(SubmissionError(format!(
                "Invalid metadata icon Id \"{id_str}\"."
            )));
        }

        if !base_ids.contains(&id_str) {
            generated_entries.push((id_str, entry));
        }
    }

    fs::create_dir_all(output_directory)?;
    let mut manifest = Vec::new();

    for (identifier, entry) in generated_entries {
        let name = metadata_text(entry, "Name", &identifier)?;
        let src = source_filename(entry, &identifier)?;
        let source_path = svg_directory.join(&src);

        let is_symlink = fs::symlink_metadata(&source_path)
            .map(|m| m.file_type().is_symlink())
            .unwrap_or(false);
        if !source_path.is_file() || is_symlink {
            return Err(SubmissionError(format!(
                "SVG source not found at {}.",
                source_path.display()
            )));
        }

        let preview_path = output_directory.join(format!("{identifier}.png"));
        let status = Command::new(renderer)
            .args([
                "--width",
                "256",
                "--height",
                "256",
                "--keep-aspect-ratio",
                "--output",
                preview_path.to_str().unwrap(),
                source_path.to_str().unwrap(),
            ])
            .status()
            .map_err(|e| SubmissionError(format!("Renderer failed: {e}")))?;

        if !status.success() {
            return Err(SubmissionError(format!(
                "Renderer failed with exit code: {:?}",
                status.code()
            )));
        }

        let posix_preview = preview_path.to_string_lossy().replace('\\', "/");
        manifest.push(json!({
            "id": identifier,
            "name": name,
            "preview": posix_preview,
        }));
    }

    let manifest_json = serde_json::to_string_pretty(&manifest)
        .map_err(|e| SubmissionError(format!("Failed to serialize manifest: {e}")))?;
    fs::write(manifest_path, format!("{manifest_json}\n"))?;
    println!("Generated {} icon preview(s).", manifest.len());
    Ok(())
}

fn github_request(
    method: &str,
    url: &str,
    token: &str,
    payload: Option<&Value>,
) -> Result<Value, SubmissionError> {
    let mut args = vec![
        "-s".to_string(),
        "-S".to_string(),
        "-X".to_string(),
        method.to_string(),
        url.to_string(),
        "-H".to_string(),
        "Accept: application/vnd.github+json".to_string(),
        "-H".to_string(),
        format!("Authorization: Bearer {token}"),
        "-H".to_string(),
        "User-Agent: ArchiveTune-IconPack".to_string(),
        "-H".to_string(),
        "X-GitHub-Api-Version: 2022-11-28".to_string(),
    ];

    let payload_str;
    if let Some(p) = payload {
        payload_str = serde_json::to_string(p)
            .map_err(|e| SubmissionError(format!("Serialize payload error: {e}")))?;
        args.push("-H".to_string());
        args.push("Content-Type: application/json".to_string());
        args.push("-d".to_string());
        args.push(payload_str);
    }

    let output = Command::new("curl")
        .args(&args)
        .output()
        .map_err(|e| SubmissionError(format!("curl error: {e}")))?;

    if !output.status.success() {
        return Err(SubmissionError(format!(
            "curl failed: {}",
            String::from_utf8_lossy(&output.stderr)
        )));
    }

    if output.stdout.is_empty() {
        return Ok(Value::Null);
    }

    serde_json::from_slice(&output.stdout)
        .map_err(|e| SubmissionError(format!("GitHub API JSON error: {e}")))
}

fn markdown_text(value: &str) -> String {
    let escaped = value
        .replace('\\', "\\\\")
        .replace('|', "\\|")
        .replace('\n', " ");
    // HTML escape
    escaped
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#x27;")
}

fn quote_path(path: &str) -> String {
    // Preserve '/' but percent-encode special characters
    let mut res = String::new();
    for b in path.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' | b'/' => {
                res.push(b as char);
            }
            _ => {
                res.push_str(&format!("%{:02X}", b));
            }
        }
    }
    res
}

pub fn build_comment(manifest: &[Value], head_repository: &str, commit_sha: &str) -> String {
    let mut rows = vec![
        COMMENT_MARKER.to_string(),
        "## Generated icon previews".to_string(),
        String::new(),
        "| Icon | Preview |".to_string(),
        "|:--|:--:|".to_string(),
    ];

    for item in manifest {
        let name = item.get("name").and_then(|v| v.as_str()).unwrap_or("");
        let preview = item.get("preview").and_then(|v| v.as_str()).unwrap_or("");
        let escaped_name = markdown_text(name);
        let preview_path = quote_path(preview);
        let image_url = format!(
            "https://raw.githubusercontent.com/{head_repository}/{commit_sha}/{preview_path}"
        );
        rows.push(format!(
            "| {escaped_name} | <img src=\"{image_url}\" alt=\"{escaped_name}\" width=\"150\" height=\"150\"> |"
        ));
    }

    rows.join("\n")
}

pub fn post_comment(
    manifest_path: &Path,
    repository: &str,
    head_repository: &str,
    commit_sha: &str,
    pull_request: u64,
    token: &str,
) -> Result<(), SubmissionError> {
    let manifest_content = fs::read_to_string(manifest_path)
        .map_err(|e| SubmissionError(format!("Cannot read manifest: {e}")))?;
    let manifest: Value = serde_json::from_str(&manifest_content)
        .map_err(|_| SubmissionError("Preview manifest must contain an array.".to_string()))?;

    let arr = match manifest.as_array() {
        Some(a) => a,
        None => {
            return Err(SubmissionError(
                "Preview manifest must contain an array.".to_string(),
            ))
        }
    };

    if arr.is_empty() {
        println!("No generated icon previews to comment.");
        return Ok(());
    }

    for item in arr {
        let obj = match item.as_object() {
            Some(o) => o,
            None => {
                return Err(SubmissionError(
                    "Preview manifest contains an invalid entry.".to_string(),
                ))
            }
        };
        for field in ["id", "name", "preview"] {
            if !obj.get(field).and_then(|v| v.as_str()).is_some() {
                return Err(SubmissionError(
                    "Preview manifest contains an invalid entry.".to_string(),
                ));
            }
        }
    }

    let sha_re = Regex::new(r"^[0-9a-fA-F]{40}$").unwrap();
    if !sha_re.is_match(commit_sha) {
        return Err(SubmissionError(
            "Generated commit SHA is invalid.".to_string(),
        ));
    }

    let body = build_comment(arr, head_repository, commit_sha);
    let comments_url =
        format!("https://api.github.com/repos/{repository}/issues/{pull_request}/comments");

    let auth_user = github_request("GET", "https://api.github.com/user", token, None)?;
    let auth_login = auth_user
        .get("login")
        .and_then(|v| v.as_str())
        .ok_or_else(|| {
            SubmissionError("Cannot determine the authenticated GitHub user.".to_string())
        })?;

    let mut existing_comment_id: Option<u64> = None;
    let mut page = 1;

    loop {
        let url = format!("{comments_url}?per_page=100&page={page}");
        let comments = github_request("GET", &url, token, None)?;
        let comment_list = comments.as_array().ok_or_else(|| {
            SubmissionError("GitHub returned an invalid comment response.".to_string())
        })?;

        for comment in comment_list {
            let user_login = comment
                .get("user")
                .and_then(|u| u.get("login"))
                .and_then(|l| l.as_str());
            let comment_body = comment.get("body").and_then(|b| b.as_str()).unwrap_or("");
            if user_login == Some(auth_login) && comment_body.contains(COMMENT_MARKER) {
                if let Some(id) = comment.get("id").and_then(|v| v.as_u64()) {
                    existing_comment_id = Some(id);
                    break;
                }
            }
        }

        if existing_comment_id.is_some() || comment_list.len() < 100 {
            break;
        }
        page += 1;
    }

    if let Some(id) = existing_comment_id {
        let patch_url = format!("https://api.github.com/repos/{repository}/issues/comments/{id}");
        github_request("PATCH", &patch_url, token, Some(&json!({ "body": body })))?;
        println!("Updated generated icon preview comment.");
    } else {
        github_request("POST", &comments_url, token, Some(&json!({ "body": body })))?;
        println!("Created generated icon preview comment.");
    }

    Ok(())
}
