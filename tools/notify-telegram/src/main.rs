use std::env;
use std::path::Path;
use std::process::Command;

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

pub fn escape_parentheses(text: &str) -> String {
    let mut out = String::with_capacity(text.len() * 2);
    for c in text.chars() {
        if c == '(' || c == ')' {
            out.push('\\');
        }
        out.push(c);
    }
    out
}

pub fn escape_markdown_v2(text: &str) -> String {
    let mut out = String::with_capacity(text.len() * 2);
    for c in text.chars() {
        if matches!(
            c,
            '_' | '~' | '`' | '#' | '+' | '-' | '=' | '|' | '{' | '}' | '.' | '!'
        ) {
            out.push('\\');
        }
        out.push(c);
    }
    out
}

pub fn build_message(
    commit_author: &str,
    commit_message: &str,
    commit_hash: &str,
    commit_hash_short: &str,
) -> String {
    let escaped_parens_msg = escape_parentheses(commit_message);
    let message = format!(
        "A new [commit](https://github.com/rukamori/ArchiveTune/commit/{commit_hash}) has been merged to the repository by *{commit_author}*.\n\n\
        *What has changed:*\n>{escaped_parens_msg}\n\n\
        I'm currently building it and will send you the APKs here within ~9 mins if the build is successful.\n\n#{commit_hash_short}"
    );
    escape_markdown_v2(&message)
}

pub fn escape_json_string(text: &str) -> String {
    let mut out = String::with_capacity(text.len() * 2);
    for c in text.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{0008}' => out.push_str("\\b"),
            '\u{000C}' => out.push_str("\\f"),
            c if c.is_control() => {
                out.push_str(&format!("\\u{:04x}", c as u32));
            }
            c => out.push(c),
        }
    }
    out
}

pub fn build_payload(chat_id: &str, text: &str, topic_id: Option<&str>) -> String {
    let escaped_chat_id = escape_json_string(chat_id);
    let escaped_text = escape_json_string(text);
    if let Some(topic) = topic_id {
        let topic_trimmed = topic.trim();
        if !topic_trimmed.is_empty() {
            let escaped_topic = escape_json_string(topic_trimmed);
            return format!(
                "{{\"chat_id\":\"{escaped_chat_id}\",\"text\":\"{escaped_text}\",\"parse_mode\":\"markdownv2\",\"disable_web_page_preview\":true,\"message_thread_id\":\"{escaped_topic}\"}}"
            );
        }
    }
    format!(
        "{{\"chat_id\":\"{escaped_chat_id}\",\"text\":\"{escaped_text}\",\"parse_mode\":\"markdownv2\",\"disable_web_page_preview\":true}}"
    )
}

pub fn send_message(
    api_url: &str,
    bot_token: &str,
    payload: &str,
) -> Result<(u16, String), String> {
    let base_url = api_url.trim_end_matches('/');
    let url = format!("{base_url}/bot{bot_token}/sendMessage");

    let output = Command::new("curl")
        .args([
            "-s",
            "-S",
            "-w",
            "\n%{http_code}",
            "-X",
            "POST",
            &url,
            "-H",
            "Content-Type: application/json",
            "-d",
            payload,
        ])
        .output()
        .map_err(|e| format!("Failed to execute curl: {e}"))?;

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);

    if !output.status.success() {
        return Err(format!(
            "curl failed with status {}: {}",
            output.status, stderr
        ));
    }

    // Parse status code from last line
    let (body, status_code_str) = match stdout.rfind('\n') {
        Some(idx) => (&stdout[..idx], stdout[idx + 1..].trim()),
        None => ("", stdout.trim()),
    };

    let status_code: u16 = status_code_str
        .parse()
        .map_err(|e| format!("Failed to parse HTTP status code '{status_code_str}': {e}"))?;

    Ok((status_code, body.to_string()))
}

fn main() {
    let bot_token = match env::var("BOT_TOKEN") {
        Ok(val) => val,
        Err(_) => {
            eprintln!("Missing required environment variable BOT_TOKEN");
            std::process::exit(1);
        }
    };

    let chat_id = match env::var("CHAT_ID") {
        Ok(val) => val,
        Err(_) => {
            eprintln!("Missing required environment variable CHAT_ID");
            std::process::exit(1);
        }
    };

    let topic_id = env::var("TOPIC_ID").ok();
    let api_url =
        env::var("TELEGRAM_API_URL").unwrap_or_else(|_| "https://api.telegram.org".to_string());

    let repo_root = Path::new(".");
    let (commit_author, commit_message, commit_hash, commit_hash_short) =
        match get_git_commit_info(repo_root) {
            Ok(info) => info,
            Err(e) => {
                eprintln!("Failed to get git commit info: {e}");
                std::process::exit(1);
            }
        };

    let message = build_message(
        &commit_author,
        &commit_message,
        &commit_hash,
        &commit_hash_short,
    );
    let payload = build_payload(&chat_id, &message, topic_id.as_deref());

    match send_message(&api_url, &bot_token, &payload) {
        Ok((200, _body)) => {
            println!("Message sent successfully.");
        }
        Ok((code, body)) => {
            println!("Failed to send message: {code} {body}");
        }
        Err(e) => {
            println!("Failed to send message: {e}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::thread;

    #[test]
    fn test_escaping_rules() {
        let author = "Alice";
        let msg = "feat: initial commit";
        let h = "1111111111111111111111111111111111111111";
        let hs = "1111111";
        let expected = "A new [commit](https://github\\.com/rukamori/ArchiveTune/commit/1111111111111111111111111111111111111111) has been merged to the repository by *Alice*\\.\n\n*What has changed:*\n>feat: initial commit\n\nI'm currently building it and will send you the APKs here within \\~9 mins if the build is successful\\.\n\n\\#1111111";
        assert_eq!(build_message(author, msg, h, hs), expected);

        let author2 = "Bob & Charlie";
        let msg2 = "fix(core): handle 404 (closes #42) [v1.0.0]";
        let h2 = "2222222222222222222222222222222222222222";
        let hs2 = "2222222";
        let expected2 = "A new [commit](https://github\\.com/rukamori/ArchiveTune/commit/2222222222222222222222222222222222222222) has been merged to the repository by *Bob & Charlie*\\.\n\n*What has changed:*\n>fix\\(core\\): handle 404 \\(closes \\#42\\) [v1\\.0\\.0]\n\nI'm currently building it and will send you the APKs here within \\~9 mins if the build is successful\\.\n\n\\#2222222";
        assert_eq!(build_message(author2, msg2, h2, hs2), expected2);

        let author3 = "user_name";
        let msg3 = "test: _~`#+-=|{}.!()[]";
        let h3 = "3333333333333333333333333333333333333333";
        let hs3 = "3333333";
        let expected3 = "A new [commit](https://github\\.com/rukamori/ArchiveTune/commit/3333333333333333333333333333333333333333) has been merged to the repository by *user\\_name*\\.\n\n*What has changed:*\n>test: \\_\\~\\`\\#\\+\\-\\=\\|\\{\\}\\.\\!\\(\\)[]\n\nI'm currently building it and will send you the APKs here within \\~9 mins if the build is successful\\.\n\n\\#3333333";
        assert_eq!(build_message(author3, msg3, h3, hs3), expected3);
    }

    #[test]
    fn test_payload_builder() {
        let p1 = build_payload("12345", "hello", None);
        assert_eq!(
            p1,
            r#"{"chat_id":"12345","text":"hello","parse_mode":"markdownv2","disable_web_page_preview":true}"#
        );

        let p2 = build_payload("12345", "hello", Some("999"));
        assert_eq!(
            p2,
            r#"{"chat_id":"12345","text":"hello","parse_mode":"markdownv2","disable_web_page_preview":true,"message_thread_id":"999"}"#
        );
    }

    #[test]
    fn test_mock_http_server_success() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
        let port = listener.local_addr().unwrap().port();

        let handle = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut buf = [0u8; 1024];
            let n = stream.read(&mut buf).expect("read");
            let req = String::from_utf8_lossy(&buf[..n]);
            assert!(req.contains("POST /botTEST_TOKEN/sendMessage"));
            assert!(req.contains("chat_id"));

            let body = "{\"ok\":true}\n";
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            );
            stream.write_all(response.as_bytes()).expect("write");
        });

        let api_url = format!("http://127.0.0.1:{port}");
        let payload = build_payload("123", "test", None);
        let res = send_message(&api_url, "TEST_TOKEN", &payload).expect("send");
        assert_eq!(res.0, 200);
        assert_eq!(res.1.trim(), "{\"ok\":true}");

        handle.join().unwrap();
    }

    #[test]
    fn test_mock_http_server_error() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
        let port = listener.local_addr().unwrap().port();

        let handle = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let mut buf = [0u8; 1024];
            let _ = stream.read(&mut buf).expect("read");

            let body = "{\"error\":\"Bad Request\"}\n";
            let response = format!(
                "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            );
            stream.write_all(response.as_bytes()).expect("write");
        });

        let api_url = format!("http://127.0.0.1:{port}");
        let payload = build_payload("123", "test", None);
        let res = send_message(&api_url, "TEST_TOKEN", &payload).expect("send");
        assert_eq!(res.0, 400);
        assert_eq!(res.1.trim(), "{\"error\":\"Bad Request\"}");

        handle.join().unwrap();
    }
}
