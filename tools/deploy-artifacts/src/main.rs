use glob::glob;
use grammers_client::media::InputMedia;
use grammers_client::message::InputMessage;
use grammers_client::session::storages::MemorySession;
use grammers_client::{tl, Client, SenderPool};
use std::collections::HashSet;
use std::env;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::Arc;

pub fn get_git_commit_info(repo_root: &Path) -> Result<(String, String, String, String), String> {
    fn run_git(repo_root: &Path, fmt: &str) -> Result<String, String> {
        let output = Command::new("git")
            .args(["log", "-1", &format!("--pretty=format:{fmt}")])
            .current_dir(repo_root)
            .output()
            .map_err(|e| format!("Failed to run git: {e}"))?;
        if !output.status.success() {
            return Err(format!(
                "git log exited with {}: {}",
                output.status,
                String::from_utf8_lossy(&output.stderr)
            ));
        }
        String::from_utf8(output.stdout).map_err(|e| format!("Invalid utf-8 from git: {e}"))
    }

    let author = run_git(repo_root, "%an")?;
    let message = run_git(repo_root, "%s")?;
    let hash = run_git(repo_root, "%H")?;
    let hash_short = run_git(repo_root, "%h")?;
    Ok((author, message, hash, hash_short))
}

pub fn human_readable_size(size: u64, decimal_places: usize) -> String {
    let units = ["B", "KB", "MB", "GB", "TB"];
    let mut s = size as f64;
    let mut unit_idx = 0;
    while s >= 1024.0 && unit_idx < units.len() - 1 {
        s /= 1024.0;
        unit_idx += 1;
    }
    format!("{s:.decimal_places$} {}", units[unit_idx])
}

pub fn resolve_apk_paths(path_value: &str) -> Vec<PathBuf> {
    if path_value.trim().is_empty() {
        return Vec::new();
    }

    let patterns: Vec<&str> = path_value
        .split(';')
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .collect();

    let mut resolved = Vec::new();
    for pattern in patterns {
        let p = Path::new(pattern);
        if p.is_dir() {
            let sub_pattern = format!("{}/*.apk", p.display());
            if let Ok(entries) = glob(&sub_pattern) {
                for entry in entries.flatten() {
                    if entry.is_file() {
                        resolved.push(entry);
                    }
                }
            }
            continue;
        }

        if let Ok(entries) = glob(pattern) {
            for entry in entries.flatten() {
                if entry.is_file() {
                    resolved.push(entry);
                } else if entry.is_dir() {
                    let sub_pattern = format!("{}/*.apk", entry.display());
                    if let Ok(sub_entries) = glob(&sub_pattern) {
                        for sub_entry in sub_entries.flatten() {
                            if sub_entry.is_file() {
                                resolved.push(sub_entry);
                            }
                        }
                    }
                }
            }
        }
    }

    let mut unique_files = Vec::new();
    let mut seen = HashSet::new();

    for path in resolved {
        if let Ok(canonical) = path.canonicalize() {
            if canonical.is_file() && seen.insert(canonical.clone()) {
                unique_files.push(path);
            }
        } else if path.is_file() && seen.insert(path.clone()) {
            unique_files.push(path);
        }
    }

    unique_files
}

pub fn extract_abi_name(file_path: &Path) -> &'static str {
    let file_name = file_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();

    let known_abis = ["arm64", "armeabi", "x86_64", "x86", "universal"];
    for abi in known_abis {
        if file_name.contains(abi) {
            return abi;
        }
    }
    "universal"
}

pub fn extract_device_name(file_path: &Path) -> &'static str {
    let file_name = file_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("")
        .to_ascii_lowercase();

    let known_devices = ["mobile", "tv"];
    for device in known_devices {
        if file_name.contains(&format!("-{device}-"))
            || file_name.starts_with(&format!("app-{device}-"))
        {
            return device;
        }
    }
    "mobile"
}

pub fn build_caption(
    commit_author: &str,
    commit_message: &str,
    commit_hash_short: &str,
    existing_files: &[PathBuf],
) -> String {
    let mut device_names: Vec<&str> = existing_files
        .iter()
        .map(|p| extract_device_name(p))
        .collect();
    device_names.sort();
    device_names.dedup();

    let mut abi_names: Vec<&str> = existing_files.iter().map(|p| extract_abi_name(p)).collect();
    abi_names.sort();
    abi_names.dedup();

    format!(
        "**Commit by:** {commit_author}\n\
        **Commit message:** {commit_message}\n\
        **Commit hash:** #{commit_hash_short}\n\
        **Device:** {}\n\
        **ABI:** {}\n\
        **Files:** {}\n\
        **Version:** Android >= 8",
        device_names.join(", "),
        abi_names.join(", "),
        existing_files.len()
    )
}

fn telegram_caption(markdown: &str) -> (String, Vec<tl::enums::MessageEntity>) {
    let mut text = String::with_capacity(markdown.len());
    let mut entities = Vec::new();
    let mut offset = 0;
    for line in markdown.lines() {
        if !text.is_empty() {
            text.push('\n');
            offset += 1;
        }
        // Bold only the generated label. Commit metadata stays literal.
        if let Some((label, value)) = line
            .strip_prefix("**")
            .and_then(|rest| rest.split_once("**"))
        {
            let length = label.encode_utf16().count() as i32;
            entities.push(tl::types::MessageEntityBold { offset, length }.into());
            text.push_str(label);
            text.push_str(value);
            offset += length + value.encode_utf16().count() as i32;
        } else {
            text.push_str(line);
            offset += line.encode_utf16().count() as i32;
        }
    }
    (text, entities)
}

pub async fn send_files_mtproto(
    api_id: i32,
    api_hash: &str,
    bot_token: &str,
    group_id: i64,
    topic_id: Option<i32>,
    existing_files: &[PathBuf],
    caption: &str,
) -> Result<(), String> {
    if existing_files.is_empty() {
        println!("No valid files to send");
        return Ok(());
    }

    println!(
        "Sending {} files to the Telegram group",
        existing_files.len()
    );

    let session = Arc::new(MemorySession::default());
    let pool = SenderPool::new(session, api_id);
    let handle = pool.handle;
    tokio::spawn(pool.runner.run());
    let client = Client::new(handle);

    client
        .bot_sign_in(bot_token, api_hash)
        .await
        .map_err(|e| format!("Bot sign in failed: {e}"))?;

    let peer = if group_id < -1000000000000 {
        tl::enums::InputPeer::Channel(tl::types::InputPeerChannel {
            channel_id: -group_id - 1000000000000,
            access_hash: 0,
        })
    } else if group_id < 0 {
        tl::enums::InputPeer::Chat(tl::types::InputPeerChat { chat_id: -group_id })
    } else {
        tl::enums::InputPeer::User(tl::types::InputPeerUser {
            user_id: group_id,
            access_hash: 0,
        })
    };

    let (caption_text, caption_entities) = telegram_caption(caption);
    // Telegram permits up to ten documents per album.
    for files in existing_files.chunks(10) {
        if let [file] = files {
            let uploaded = client
                .upload_file(file)
                .await
                .map_err(|e| format!("Upload failed for {}: {e}", file.display()))?;
            let message = InputMessage::new()
                .file(uploaded)
                .text(&caption_text)
                .fmt_entities(caption_entities.clone())
                .reply_to(topic_id);
            client
                .send_message(&peer, message)
                .await
                .map_err(|e| format!("Send document failed: {e}"))?;
            continue;
        }

        let mut album = Vec::with_capacity(files.len());
        for file in files {
            let uploaded = client
                .upload_file(file)
                .await
                .map_err(|e| format!("Upload failed for {}: {e}", file.display()))?;
            let mut document = InputMedia::new().document(uploaded);
            if album.is_empty() {
                document = document
                    .caption(&caption_text)
                    .fmt_entities(caption_entities.clone())
                    .reply_to(topic_id);
            }
            album.push(document);
        }
        client
            .send_album(&peer, album)
            .await
            .map_err(|e| format!("Send APK album failed: {e}"))?;
    }

    println!("\nFiles sent successfully");
    Ok(())
}

#[tokio::main]
async fn main() {
    let api_id: i32 = match env::var("API_ID") {
        Ok(v) => match v.trim().parse() {
            Ok(n) => n,
            Err(_) => {
                eprintln!("Invalid API_ID environment variable");
                std::process::exit(1);
            }
        },
        Err(_) => {
            eprintln!("Missing API_ID environment variable");
            std::process::exit(1);
        }
    };

    let api_hash = match env::var("API_HASH") {
        Ok(v) if !v.trim().is_empty() => v.trim().to_string(),
        _ => {
            eprintln!("Missing API_HASH environment variable");
            std::process::exit(1);
        }
    };

    let bot_token = match env::var("BOT_TOKEN") {
        Ok(v) if !v.trim().is_empty() => v.trim().to_string(),
        _ => {
            eprintln!("Missing BOT_TOKEN environment variable");
            std::process::exit(1);
        }
    };

    let chat_id: i64 = match env::var("CHAT_ID") {
        Ok(v) => match v.trim().parse() {
            Ok(n) => n,
            Err(_) => {
                eprintln!("Invalid CHAT_ID environment variable");
                std::process::exit(1);
            }
        },
        Err(_) => {
            eprintln!("Missing CHAT_ID environment variable");
            std::process::exit(1);
        }
    };

    let topic_id: Option<i32> = env::var("TOPIC_ID")
        .ok()
        .and_then(|v| v.trim().parse().ok());
    let apk_path = env::var("APK_PATH").unwrap_or_default();

    let apk_files = resolve_apk_paths(&apk_path);
    if apk_files.is_empty() {
        println!("File not found {apk_path}");
        std::process::exit(1);
    }

    let repo_root = Path::new(".");
    let (commit_author, commit_message, _commit_hash, commit_hash_short) =
        match get_git_commit_info(repo_root) {
            Ok(info) => info,
            Err(e) => {
                eprintln!("Failed to get git commit info: {e}");
                std::process::exit(1);
            }
        };

    let caption = build_caption(
        &commit_author,
        &commit_message,
        &commit_hash_short,
        &apk_files,
    );

    if env::var("MOCK_DEPLOY")
        .map(|v| v == "1" || v == "true")
        .unwrap_or(false)
    {
        println!(
            "MOCK_DEPLOY: contract validated for {} files.",
            apk_files.len()
        );
        println!("MOCK_DEPLOY: caption:\n{caption}");
        return;
    }

    match send_files_mtproto(
        api_id, &api_hash, &bot_token, chat_id, topic_id, &apk_files, &caption,
    )
    .await
    {
        Ok(()) => {}
        Err(e) => {
            println!("Failed to send files: {e}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_human_readable_size() {
        assert_eq!(human_readable_size(500, 2), "500.00 B");
        assert_eq!(human_readable_size(1024, 2), "1.00 KB");
        assert_eq!(human_readable_size(1536, 1), "1.5 KB");
        assert_eq!(human_readable_size(1048576, 2), "1.00 MB");
        assert_eq!(human_readable_size(58720256, 2), "56.00 MB");
    }

    #[test]
    fn test_extract_abi_name() {
        assert_eq!(
            extract_abi_name(Path::new("app-gms-mobile-arm64-release.apk")),
            "arm64"
        );
        assert_eq!(
            extract_abi_name(Path::new("app-gms-mobile-armeabi-release.apk")),
            "armeabi"
        );
        assert_eq!(
            extract_abi_name(Path::new("app-gms-mobile-x86_64-release.apk")),
            "x86_64"
        );
        assert_eq!(
            extract_abi_name(Path::new("app-gms-mobile-x86-release.apk")),
            "x86"
        );
        assert_eq!(
            extract_abi_name(Path::new("app-gms-mobile-universal-release.apk")),
            "universal"
        );
        assert_eq!(extract_abi_name(Path::new("unknown-name.apk")), "universal");
    }

    #[test]
    fn test_extract_device_name() {
        assert_eq!(
            extract_device_name(Path::new("app-mobile-universal-debug.apk")),
            "mobile"
        );
        assert_eq!(
            extract_device_name(Path::new("app-tv-universal-debug.apk")),
            "tv"
        );
        assert_eq!(
            extract_device_name(Path::new("app-gms-mobile-arm64-release.apk")),
            "mobile"
        );
        assert_eq!(extract_device_name(Path::new("unknown.apk")), "mobile");
    }

    #[test]
    fn test_resolve_apk_paths() {
        let tmp = std::env::temp_dir().join(format!("test-resolve-apk-{}", std::process::id()));
        let _ = fs::remove_dir_all(&tmp);
        fs::create_dir_all(&tmp).unwrap();

        let apk1 = tmp.join("app-one.apk");
        let apk2 = tmp.join("app-two.apk");
        let not_apk = tmp.join("ignore.txt");
        fs::write(&apk1, "apk1").unwrap();
        fs::write(&apk2, "apk2").unwrap();
        fs::write(&not_apk, "txt").unwrap();

        // Pattern
        let pattern = format!("{}/*.apk", tmp.display());
        let resolved = resolve_apk_paths(&pattern);
        assert_eq!(resolved.len(), 2);

        // Directory pattern
        let resolved_dir = resolve_apk_paths(tmp.to_str().unwrap());
        assert_eq!(resolved_dir.len(), 2);

        // Semicolon separated with duplicate
        let multi = format!("{};{}", apk1.display(), apk1.display());
        let resolved_multi = resolve_apk_paths(&multi);
        assert_eq!(resolved_multi.len(), 1);

        let _ = fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_telegram_caption_preserves_lines_and_literal_commit_metadata() {
        let (text, entities) =
            telegram_caption("**Commit by:** 🎵 User\n**Commit message:** [Fix](link) **literal**");
        assert_eq!(
            text,
            "Commit by: 🎵 User\nCommit message: [Fix](link) **literal**"
        );
        assert_eq!(
            entities,
            vec![
                tl::types::MessageEntityBold {
                    offset: 0,
                    length: 10
                }
                .into(),
                tl::types::MessageEntityBold {
                    offset: 19,
                    length: 15
                }
                .into(),
            ]
        );
    }

    #[test]
    fn test_build_caption_contract() {
        let files = vec![
            PathBuf::from("app-gms-mobile-arm64-release.apk"),
            PathBuf::from("app-gms-mobile-universal-release.apk"),
        ];
        let caption = build_caption("Test Author", "feat: add feature", "abcdef1", &files);
        assert!(caption.contains("**Commit by:** Test Author"));
        assert!(caption.contains("**Commit message:** feat: add feature"));
        assert!(caption.contains("**Commit hash:** #abcdef1"));
        assert!(caption.contains("**Device:** mobile"));
        assert!(caption.contains("**ABI:** arm64, universal"));
        assert!(caption.contains("**Files:** 2"));
        assert!(caption.contains("**Version:** Android >= 8"));
    }
}
