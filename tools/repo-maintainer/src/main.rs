// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::collections::{BTreeMap, HashMap, HashSet, VecDeque};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, exit};

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        eprintln!("Usage: repo-maintainer <command> [<subcommand>] [options]");
        eprintln!("Commands:");
        eprintln!("  header find-duplicates [--root <path>]");
        eprintln!("  header remove [--root <path>]");
        eprintln!("  header verify [--root <path>]");
        eprintln!("  cleanup strings [--root <path>] [--dry-run]");
        eprintln!("  cleanup drawables [--root <path>] [--drawable-dir <path>] [--dry-run]");
        exit(1);
    }

    match args[1].as_str() {
        "header" => handle_header(&args[2..]),
        "cleanup" => handle_cleanup(&args[2..]),
        _ => {
            eprintln!("Unknown command: {}", args[1]);
            exit(1);
        }
    }
}

fn get_option_value(args: &[String], flag: &str) -> Option<String> {
    args.iter()
        .position(|arg| arg == flag)
        .and_then(|idx| args.get(idx + 1).cloned())
}

fn has_flag(args: &[String], flag: &str) -> bool {
    args.iter().any(|arg| arg == flag)
}

// -----------------------------------------------------------------------------
// Header Management
// -----------------------------------------------------------------------------

const LICENSE_MARKER: &str = "GPL-3.0 License | Contributors: see git history";
const ARCHIVE_MARKER: &str = "ArchiveTune (";

fn find_kotlin_files(root: &Path) -> Vec<PathBuf> {
    let mut files = Vec::new();
    collect_files_recursive(root, "kt", &mut files);
    files.sort();
    files
}

fn collect_files_recursive(dir: &Path, extension: &str, output: &mut Vec<PathBuf>) {
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            let is_excluded = path
                .file_name()
                .and_then(|n| n.to_str())
                .map(|name| matches!(name, ".git" | ".gradle" | "build" | "target"))
                .unwrap_or(false);
            if is_excluded {
                continue;
            }
            if path.is_dir() {
                collect_files_recursive(&path, extension, output);
            } else if path.is_file() {
                let matches_ext = path
                    .extension()
                    .and_then(|e| e.to_str())
                    .map(|ext| ext == extension)
                    .unwrap_or(false);
                if matches_ext {
                    output.push(path);
                }
            }
        }
    }
}

fn handle_header(args: &[String]) {
    if args.is_empty() {
        eprintln!("Missing header subcommand: find-duplicates, remove, verify");
        exit(1);
    }

    let root_str = get_option_value(args, "--root").unwrap_or_else(|| ".".to_string());
    let root = Path::new(&root_str);

    match args[0].as_str() {
        "find-duplicates" => {
            let duplicates = find_duplicate_headers(root);
            if duplicates.is_empty() {
                println!("No duplicate Kotlin headers found.");
            } else {
                println!("Duplicate Kotlin headers found:");
                for (path, count) in &duplicates {
                    println!("{}: {}", path.display(), count);
                }
            }
        }
        "remove" => {
            let count = remove_existing_headers(root);
            println!("Removed {} existing Kotlin header(s).", count);
        }
        "verify" => {
            let duplicates = find_duplicate_headers(root);
            if duplicates.is_empty() {
                println!("Every Kotlin file has at most one ArchiveTune header.");
            } else {
                println!("Duplicate Kotlin headers remain:");
                for (path, count) in &duplicates {
                    println!("{}: {}", path.display(), count);
                }
                exit(1);
            }
        }
        _ => {
            eprintln!("Unknown header subcommand: {}", args[0]);
            exit(1);
        }
    }
}

pub fn find_duplicate_headers(root: &Path) -> Vec<(PathBuf, usize)> {
    let mut duplicates = Vec::new();
    for file in find_kotlin_files(root) {
        if let Ok(content) = fs::read_to_string(&file) {
            let count = content.matches(LICENSE_MARKER).count();
            if count > 1 {
                duplicates.push((file, count));
            }
        }
    }
    duplicates
}

pub fn remove_existing_headers(root: &Path) -> usize {
    let mut total_removed = 0;
    for file in find_kotlin_files(root) {
        if let Ok(mut text) = fs::read_to_string(&file) {
            let mut removed = 0;
            while text.starts_with("/*") {
                if let Some(end) = text.find("*/") {
                    let header = &text[..end + 2];
                    if header.contains(ARCHIVE_MARKER) && header.contains(LICENSE_MARKER) {
                        text = text[end + 2..].trim_start_matches(['\r', '\n']).to_string();
                        removed += 1;
                        continue;
                    }
                }
                break;
            }
            if removed > 0 {
                if let Err(e) = fs::write(&file, &text) {
                    eprintln!("Failed to write {}: {}", file.display(), e);
                } else {
                    println!("{}: removed {} existing header(s)", file.display(), removed);
                    total_removed += removed;
                }
            }
        }
    }
    total_removed
}

// -----------------------------------------------------------------------------
// Cleanup Management (Strings and Drawables)
// -----------------------------------------------------------------------------

fn handle_cleanup(args: &[String]) {
    if args.is_empty() {
        eprintln!("Missing cleanup subcommand: strings, drawables");
        exit(1);
    }

    let root_str = get_option_value(args, "--root").unwrap_or_else(|| ".".to_string());
    let root = Path::new(&root_str);
    let dry_run = has_flag(args, "--dry-run");

    match args[0].as_str() {
        "strings" => {
            if let Err(e) = clean_strings(root, dry_run) {
                eprintln!("String cleanup failed: {}", e);
                exit(1);
            }
        }
        "drawables" => {
            let drawable_dir_str = get_option_value(args, "--drawable-dir")
                .unwrap_or_else(|| "app/src/main/res/drawable".to_string());
            let drawable_dir = root.join(drawable_dir_str);
            if let Err(e) = clean_drawables(root, &drawable_dir, dry_run) {
                eprintln!("Drawable cleanup failed: {}", e);
                exit(1);
            }
        }
        _ => {
            eprintln!("Unknown cleanup subcommand: {}", args[0]);
            exit(1);
        }
    }
}

// -----------------------------------------------------------------------------
// Strings Cleanup Logic
// -----------------------------------------------------------------------------

#[derive(Debug, Clone)]
pub struct StringResource {
    pub path: PathBuf,
    pub name: String,
    pub start: usize,
    pub end: usize,
    pub is_non_translatable: bool,
}

fn load_resource_documents(root: &Path) -> BTreeMap<PathBuf, String> {
    let mut documents = BTreeMap::new();
    let mut files = Vec::new();
    collect_files_recursive(root, "xml", &mut files);
    for path in files {
        let parent_name = path
            .parent()
            .and_then(|p| p.file_name())
            .and_then(|n| n.to_str())
            .unwrap_or("");
        if !parent_name.starts_with("values") {
            continue;
        }
        let grandparent_name = path
            .parent()
            .and_then(|p| p.parent())
            .and_then(|p| p.file_name())
            .and_then(|n| n.to_str())
            .unwrap_or("");
        if grandparent_name != "res" {
            continue;
        }
        if let Ok(content) = fs::read_to_string(&path) {
            documents.insert(path, content);
        }
    }
    documents
}

fn attribute_value<'a>(text: &'a str, attr_name: &str) -> Option<&'a str> {
    let pattern = format!(r#"{attr_name}="#);
    let mut search_from = 0;
    while let Some(pos) = text[search_from..].find(&pattern) {
        let idx = search_from + pos;
        let before = text[..idx].chars().last();
        if before.is_none() || before.unwrap().is_whitespace() || before.unwrap() == ':' {
            let remainder = &text[idx + pattern.len()..];
            let trimmed = remainder.trim_start();
            if let Some(rest) = trimmed.strip_prefix('"') {
                if let Some(end) = rest.find('"') {
                    return Some(&rest[..end]);
                }
            } else if let Some(end) = trimmed.strip_prefix('\'').and_then(|r| r.find('\'')) {
                return Some(&trimmed[1..1 + end]);
            }
        }
        search_from = idx + pattern.len();
    }
    None
}

fn find_string_resources(documents: &BTreeMap<PathBuf, String>) -> Vec<StringResource> {
    let mut resources = Vec::new();
    for (path, text) in documents {
        let mut idx = 0;
        let bytes = text.as_bytes();
        while idx < bytes.len() {
            if text[idx..].starts_with("<!--") {
                if let Some(end) = text[idx..].find("-->") {
                    idx += end + 3;
                } else {
                    break;
                }
                continue;
            }

            if bytes[idx] == b'<' {
                let tag_start = idx + 1;
                let remainder = &text[tag_start..];
                let is_string_tag = remainder.starts_with("string ")
                    || remainder.starts_with("string>")
                    || remainder.starts_with("string/")
                    || remainder.starts_with("string\n")
                    || remainder.starts_with("string\r\n");
                let is_item_tag = remainder.starts_with("item ")
                    || remainder.starts_with("item>")
                    || remainder.starts_with("item/")
                    || remainder.starts_with("item\n")
                    || remainder.starts_with("item\r\n");

                if is_string_tag || is_item_tag {
                    let tag_name = if is_string_tag { "string" } else { "item" };
                    let close_pattern = format!("</{}>", tag_name);
                    let element_start = idx;

                    if let Some(open_tag_end) = text[element_start..].find('>') {
                        let open_tag_str = &text[element_start..element_start + open_tag_end + 1];
                        let element_end = if open_tag_str.trim_end().ends_with("/>") {
                            element_start + open_tag_end + 1
                        } else if let Some(close_pos) = text[element_start..].find(&close_pattern) {
                            element_start + close_pos + close_pattern.len()
                        } else {
                            idx += 1;
                            continue;
                        };

                        let open_tag_attrs =
                            &open_tag_str[tag_name.len() + 1..open_tag_str.len() - 1];
                        if tag_name == "item"
                            && attribute_value(open_tag_attrs, "type") != Some("string")
                        {
                            idx = element_end;
                            continue;
                        }

                        let name_opt = attribute_value(open_tag_attrs, "name");
                        if let Some(name) = name_opt.filter(|s| !s.is_empty()) {
                            let is_non_trans =
                                attribute_value(open_tag_attrs, "translatable") == Some("false");
                            resources.push(StringResource {
                                path: path.clone(),
                                name: name.to_string(),
                                start: element_start,
                                end: element_end,
                                is_non_translatable: is_non_trans,
                            });
                            idx = element_end;
                            continue;
                        }
                    }
                }
            }
            idx += 1;
        }
    }
    resources
}

fn resource_priority(resource: &StringResource) -> (bool, String, usize) {
    let file_name = resource
        .path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("");
    (
        file_name.to_lowercase() != "strings.xml",
        resource.path.to_string_lossy().to_string(),
        resource.start,
    )
}

fn removal_range(text: &str, start: usize, end: usize) -> (usize, usize) {
    let line_start = text[..start].rfind('\n').map(|p| p + 1).unwrap_or(0);
    let next_newline = text[end..].find('\n');
    let line_end = next_newline.map(|p| end + p + 1).unwrap_or(text.len());

    let prefix_blank = text[line_start..start].trim().is_empty();
    let suffix_blank = text[end..line_end].trim().is_empty();

    if prefix_blank && suffix_blank {
        (line_start, line_end)
    } else {
        (start, end)
    }
}

fn remove_resources_from_docs(
    documents: &BTreeMap<PathBuf, String>,
    resources: &[StringResource],
) -> (BTreeMap<PathBuf, String>, usize) {
    let mut removals_by_path: HashMap<PathBuf, Vec<(usize, usize)>> = HashMap::new();
    for res in resources {
        removals_by_path
            .entry(res.path.clone())
            .or_default()
            .push((res.start, res.end));
    }

    let mut updated = documents.clone();
    let mut total_removed = 0;

    for (path, ranges) in removals_by_path {
        if let Some(text) = updated.get_mut(&path) {
            let mut resolved_ranges: Vec<(usize, usize)> = ranges
                .into_iter()
                .map(|(s, e)| removal_range(text, s, e))
                .collect();
            resolved_ranges.sort_by_key(|&(s, _)| s);
            resolved_ranges.reverse();

            for (start, end) in resolved_ranges {
                text.replace_range(start..end, "");
                total_removed += 1;
            }
        }
    }
    (updated, total_removed)
}

fn is_conflict_marker(line: &str) -> Option<(char, usize)> {
    let trimmed = line.trim_end();
    let first = trimmed.chars().next()?;
    if first == '<' || first == '|' || first == '=' || first == '>' {
        let count = trimmed.chars().take_while(|&c| c == first).count();
        if count >= 7 {
            return Some((first, count));
        }
    }
    None
}

fn strip_conflict_markers(text: &str) -> (String, usize) {
    let mut out = String::new();
    let mut count = 0;
    for line in text.lines() {
        if is_conflict_marker(line).is_some() {
            count += 1;
        } else {
            out.push_str(line);
            out.push('\n');
        }
    }
    (out, count)
}

fn normalize_conflicts(
    documents: &BTreeMap<PathBuf, String>,
) -> (BTreeMap<PathBuf, String>, usize) {
    let mut updated = BTreeMap::new();
    let mut total = 0;
    for (path, text) in documents {
        let (stripped, count) = strip_conflict_markers(text);
        updated.insert(path.clone(), stripped);
        total += count;
    }
    (updated, total)
}

fn find_duplicate_groups(
    documents: &BTreeMap<PathBuf, String>,
) -> BTreeMap<(PathBuf, String), Vec<StringResource>> {
    let mut groups: BTreeMap<(PathBuf, String), Vec<StringResource>> = BTreeMap::new();
    for res in find_string_resources(documents) {
        let parent = res.path.parent().unwrap_or(&res.path).to_path_buf();
        groups
            .entry((parent, res.name.clone()))
            .or_default()
            .push(res);
    }
    groups.retain(|_, v| v.len() > 1);
    groups
}

fn remove_duplicates(
    documents: &BTreeMap<PathBuf, String>,
    duplicate_groups: &BTreeMap<(PathBuf, String), Vec<StringResource>>,
) -> (BTreeMap<PathBuf, String>, usize) {
    let mut duplicates_to_remove = Vec::new();
    for entries in duplicate_groups.values() {
        let mut sorted = entries.clone();
        sorted.sort_by_key(resource_priority);
        duplicates_to_remove.extend(sorted.into_iter().skip(1));
    }
    remove_resources_from_docs(documents, &duplicates_to_remove)
}

fn extract_ident(text: &str, start: usize) -> Option<&str> {
    let bytes = text.as_bytes();
    if start >= bytes.len() {
        return None;
    }
    let first = bytes[start];
    if !first.is_ascii_lowercase() && first != b'_' {
        return None;
    }
    let mut end = start + 1;
    while end < bytes.len() {
        let b = bytes[end];
        if b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_' {
            end += 1;
        } else {
            break;
        }
    }
    Some(&text[start..end])
}

fn find_string_references(text: &str, candidates: &HashSet<String>) -> HashSet<String> {
    let mut refs = HashSet::new();

    let mut search_from = 0;
    while let Some(pos) = text[search_from..].find("R.string.") {
        let idx = search_from + pos + "R.string.".len();
        if let Some(ident) = extract_ident(text, idx).filter(|&id| candidates.contains(id)) {
            refs.insert(ident.to_string());
        }
        search_from = idx;
    }

    search_from = 0;
    while let Some(pos) = text[search_from..].find("string/") {
        let idx = search_from + pos + "string/".len();
        if let Some(ident) = extract_ident(text, idx).filter(|&id| candidates.contains(id)) {
            refs.insert(ident.to_string());
        }
        search_from = idx;
    }

    search_from = 0;
    while let Some(pos) = text[search_from..].find("stringResourceName") {
        let idx = search_from + pos + "stringResourceName".len();
        if let Some(quote_pos) = text[idx..].find(['"', '\'']) {
            let quote_idx = idx + quote_pos + 1;
            if let Some(ident) =
                extract_ident(text, quote_idx).filter(|&id| candidates.contains(id))
            {
                refs.insert(ident.to_string());
            }
        }
        search_from = idx;
    }

    search_from = 0;
    while let Some(pos) = text[search_from..].find("getIdentifier") {
        let idx = search_from + pos + "getIdentifier".len();
        let end_call = text[idx..]
            .find(')')
            .unwrap_or_else(|| text[idx..].len().min(200));
        let call_slice = &text[idx..idx + end_call];
        let matches_type = call_slice.contains(r#""string""#) || call_slice.contains(r#"'string'"#);
        let quote_pos = if matches_type {
            call_slice.find(['"', '\''])
        } else {
            None
        };
        if let Some(quote_pos) = quote_pos {
            let quote_idx = quote_pos + 1;
            if let Some(ident) =
                extract_ident(call_slice, quote_idx).filter(|&id| candidates.contains(id))
            {
                refs.insert(ident.to_string());
            }
        }
        search_from = idx + end_call;
    }

    refs
}

fn load_git_tracked_files(repo_root: &Path) -> Vec<PathBuf> {
    let output = Command::new("git")
        .args(["ls-files", "-z"])
        .current_dir(repo_root)
        .output();

    if let Some(out) = output.ok().filter(|o| o.status.success()) {
        let raw = String::from_utf8_lossy(&out.stdout);
        return raw
            .split('\0')
            .filter(|s| !s.is_empty())
            .map(|s| repo_root.join(s))
            .filter(|p| p.is_file())
            .collect();
    }

    let mut fallback = Vec::new();
    collect_files_recursive(repo_root, "*", &mut fallback);
    fallback
}

pub fn clean_strings(repo_root: &Path, dry_run: bool) -> Result<(), String> {
    let original = load_resource_documents(repo_root);
    let (normalized, conflict_count) = normalize_conflicts(&original);
    println!("Found {} Git conflict block(s).", conflict_count);

    let duplicate_groups = find_duplicate_groups(&normalized);
    println!(
        "Found {} duplicate string resource key(s).",
        duplicate_groups.len()
    );

    let (deduplicated, duplicate_count) = remove_duplicates(&normalized, &duplicate_groups);

    let all_resources = find_string_resources(&deduplicated);
    let candidate_names: HashSet<String> = all_resources.iter().map(|r| r.name.clone()).collect();

    let mut roots: HashSet<String> = all_resources
        .iter()
        .filter(|r| r.is_non_translatable)
        .map(|r| r.name.clone())
        .collect();

    let mut dependencies: HashMap<String, HashSet<String>> = HashMap::new();
    for res in &all_resources {
        if let Some(text) = deduplicated.get(&res.path) {
            let elem = &text[res.start..res.end];
            let refs = find_string_references(elem, &candidate_names);
            dependencies
                .entry(res.name.clone())
                .or_default()
                .extend(refs);
        }
    }

    let tracked = load_git_tracked_files(repo_root);
    let app_src = repo_root.join("app/src");
    let mut preserve_all = false;

    for path in &tracked {
        if !path.starts_with(&app_src) {
            continue;
        }
        if let Ok(text) = fs::read_to_string(path) {
            if text.contains("R.string::class")
                || text.contains("import moe.rukamori.archivetune.R.string.*")
            {
                preserve_all = true;
            }
            let refs = find_string_references(&text, &candidate_names);
            roots.extend(refs);
        }
    }

    let unused_names: Vec<String> = if preserve_all {
        println!("Dynamic string lookup or wildcard import detected; skipping deletion.");
        Vec::new()
    } else {
        let mut reachable = roots.clone();
        let mut queue: VecDeque<String> = roots.into_iter().collect();
        while let Some(current) = queue.pop_front() {
            if let Some(deps) = dependencies.get(&current) {
                for dep in deps {
                    if reachable.insert(dep.clone()) {
                        queue.push_back(dep.clone());
                    }
                }
            }
        }
        let mut unused: Vec<String> = candidate_names.difference(&reachable).cloned().collect();
        unused.sort();
        unused
    };

    println!(
        "Found {} unused string resource key(s).",
        unused_names.len()
    );

    let unused_set: HashSet<String> = unused_names.iter().cloned().collect();
    let unused_resources: Vec<StringResource> = all_resources
        .into_iter()
        .filter(|r| unused_set.contains(&r.name))
        .collect();

    let (updated, unused_entry_count) =
        remove_resources_from_docs(&deduplicated, &unused_resources);

    let changed_paths: Vec<&PathBuf> = updated
        .iter()
        .filter(|(p, text)| original.get(*p) != Some(text))
        .map(|(p, _)| p)
        .collect();

    if !dry_run {
        for (path, text) in &updated {
            if original.get(path) != Some(text) {
                fs::write(path, text)
                    .map_err(|e| format!("Failed to write {}: {}", path.display(), e))?;
            }
        }
    }

    let mode = if dry_run { "Would update" } else { "Updated" };
    println!("{} {} string resource file(s).", mode, changed_paths.len());
    println!("Normalized {} Git conflict block(s).", conflict_count);
    println!(
        "Removed {} duplicate string resource entries.",
        duplicate_count
    );
    println!(
        "Removed {} entries for {} unused string resource key(s).",
        unused_entry_count,
        unused_names.len()
    );

    Ok(())
}

// -----------------------------------------------------------------------------
// Drawables Cleanup Logic
// -----------------------------------------------------------------------------

fn find_drawable_references(text: &str, candidates: &HashSet<String>) -> HashSet<String> {
    let mut refs = HashSet::new();

    let mut search_from = 0;
    while let Some(pos) = text[search_from..].find("R.drawable.") {
        let idx = search_from + pos + "R.drawable.".len();
        if let Some(ident) = extract_ident(text, idx).filter(|&id| candidates.contains(id)) {
            refs.insert(ident.to_string());
        }
        search_from = idx;
    }

    search_from = 0;
    while let Some(pos) = text[search_from..].find("drawable/") {
        let idx = search_from + pos + "drawable/".len();
        if let Some(ident) = extract_ident(text, idx).filter(|&id| candidates.contains(id)) {
            refs.insert(ident.to_string());
        }
        search_from = idx;
    }

    search_from = 0;
    while let Some(pos) = text[search_from..].find("drawableResourceName") {
        let idx = search_from + pos + "drawableResourceName".len();
        if let Some(quote_pos) = text[idx..].find(['"', '\'']) {
            let quote_idx = idx + quote_pos + 1;
            if let Some(ident) =
                extract_ident(text, quote_idx).filter(|&id| candidates.contains(id))
            {
                refs.insert(ident.to_string());
            }
        }
        search_from = idx;
    }

    search_from = 0;
    while let Some(pos) = text[search_from..].find("getIdentifier") {
        let idx = search_from + pos + "getIdentifier".len();
        let end_call = text[idx..]
            .find(')')
            .unwrap_or_else(|| text[idx..].len().min(200));
        let call_slice = &text[idx..idx + end_call];
        let matches_type =
            call_slice.contains(r#""drawable""#) || call_slice.contains(r#"'drawable'"#);
        let quote_pos = if matches_type {
            call_slice.find(['"', '\''])
        } else {
            None
        };
        if let Some(quote_pos) = quote_pos {
            let quote_idx = quote_pos + 1;
            if let Some(ident) =
                extract_ident(call_slice, quote_idx).filter(|&id| candidates.contains(id))
            {
                refs.insert(ident.to_string());
            }
        }
        search_from = idx + end_call;
    }

    refs
}

pub fn clean_drawables(repo_root: &Path, drawable_dir: &Path, dry_run: bool) -> Result<(), String> {
    if !drawable_dir.is_dir() {
        return Err(format!(
            "Drawable directory does not exist: {}",
            drawable_dir.display()
        ));
    }

    let mut candidates: BTreeMap<String, Vec<PathBuf>> = BTreeMap::new();
    if let Ok(entries) = fs::read_dir(drawable_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_file() {
                let base_name = path
                    .file_name()
                    .and_then(|n| n.to_str())
                    .map(|f| f.split('.').next().unwrap_or(""));
                if let Some(name) = base_name {
                    candidates.entry(name.to_string()).or_default().push(path);
                }
            }
        }
    }

    let candidate_names: HashSet<String> = candidates.keys().cloned().collect();
    let mut roots = HashSet::new();
    let mut dependencies: HashMap<String, HashSet<String>> = HashMap::new();
    let tracked = load_git_tracked_files(repo_root);

    for path in &tracked {
        if let Ok(bytes) = fs::read(path) {
            let text = String::from_utf8_lossy(&bytes);
            let refs = find_drawable_references(&text, &candidate_names);

            if path.parent() == Some(drawable_dir) {
                if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                    let base_name = file_name.split('.').next().unwrap_or("");
                    dependencies
                        .entry(base_name.to_string())
                        .or_default()
                        .extend(refs);
                }
            } else {
                roots.extend(refs);
            }
        }
    }

    let mut reachable = roots.clone();
    let mut queue: VecDeque<String> = roots.into_iter().collect();
    while let Some(current) = queue.pop_front() {
        if let Some(deps) = dependencies.get(&current) {
            for dep in deps {
                if reachable.insert(dep.clone()) {
                    queue.push_back(dep.clone());
                }
            }
        }
    }

    let mut unused: Vec<String> = candidate_names.difference(&reachable).cloned().collect();
    unused.sort();

    if unused.is_empty() {
        println!("No unused drawable resources found.");
        return Ok(());
    }

    let mode = if dry_run { "Would remove" } else { "Removing" };
    let mut removed_count = 0;
    for name in &unused {
        if let Some(paths) = candidates.get(name) {
            for p in paths {
                println!("{}: {}", mode, p.display());
                if !dry_run {
                    fs::remove_file(p)
                        .map_err(|e| format!("Failed to delete {}: {}", p.display(), e))?;
                }
                removed_count += 1;
            }
        }
    }

    let action = if dry_run { "Would remove" } else { "Removed" };
    println!(
        "{} {} file(s) for {} unused drawable resource(s).",
        action,
        removed_count,
        unused.len()
    );

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_conflict_marker_detection() {
        assert_eq!(is_conflict_marker("<<<<<<< HEAD"), Some(('<', 7)));
        assert_eq!(is_conflict_marker("======="), Some(('=', 7)));
        assert_eq!(is_conflict_marker(">>>>>>> branch"), Some(('>', 7)));
        assert_eq!(is_conflict_marker("||||||| ancestor"), Some(('|', 7)));
        assert_eq!(is_conflict_marker("normal line"), None);
        assert_eq!(is_conflict_marker("<string name=\"app_name\">"), None);
    }

    #[test]
    fn test_attribute_value_extraction() {
        let xml = r#"<string name="test_name" translatable="false">Hello</string>"#;
        assert_eq!(attribute_value(xml, "name"), Some("test_name"));
        assert_eq!(attribute_value(xml, "translatable"), Some("false"));
        assert_eq!(attribute_value(xml, "type"), None);
    }

    #[test]
    fn test_header_removal() {
        let temp_dir = tempfile_dir("test_header_removal");
        let kt_file = temp_dir.join("Sample.kt");
        let original_content = format!(
            "/*\n * ArchiveTune (2026)\n * {}\n */\npackage test\n",
            LICENSE_MARKER
        );
        fs::write(&kt_file, &original_content).unwrap();

        let removed = remove_existing_headers(&temp_dir);
        assert_eq!(removed, 1);

        let new_content = fs::read_to_string(&kt_file).unwrap();
        assert_eq!(new_content, "package test\n");

        // Clean up
        let _ = fs::remove_dir_all(&temp_dir);
    }

    fn tempfile_dir(prefix: &str) -> PathBuf {
        let p = env::temp_dir().join(format!(
            "{}_{}",
            prefix,
            std::time::SystemTime::now().elapsed().unwrap().as_nanos()
        ));
        fs::create_dir_all(&p).unwrap();
        p
    }
}
