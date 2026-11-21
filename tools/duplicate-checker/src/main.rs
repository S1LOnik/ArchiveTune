use regex::Regex;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};
use std::env;
use std::process::Command;

pub const DUPLICATE_LABEL: &str = "duplicate";
pub const COMMENT_MARKER_PREFIX: &str = "<!-- duplicate-checker:";
pub const SIMILARITY_THRESHOLD: f64 = 0.65;

pub static ENGLISH_STOP_WORDS: &[&str] = &[
    "a",
    "about",
    "above",
    "across",
    "after",
    "afterwards",
    "again",
    "against",
    "all",
    "almost",
    "alone",
    "along",
    "already",
    "also",
    "although",
    "always",
    "am",
    "among",
    "amongst",
    "amoungst",
    "amount",
    "an",
    "and",
    "another",
    "any",
    "anyhow",
    "anyone",
    "anything",
    "anyway",
    "anywhere",
    "are",
    "around",
    "as",
    "at",
    "back",
    "be",
    "became",
    "because",
    "become",
    "becomes",
    "becoming",
    "been",
    "before",
    "beforehand",
    "behind",
    "being",
    "below",
    "beside",
    "besides",
    "between",
    "beyond",
    "bill",
    "both",
    "bottom",
    "but",
    "by",
    "call",
    "can",
    "cannot",
    "cant",
    "co",
    "con",
    "could",
    "couldnt",
    "cry",
    "de",
    "describe",
    "detail",
    "do",
    "done",
    "down",
    "due",
    "during",
    "each",
    "eg",
    "eight",
    "either",
    "eleven",
    "else",
    "elsewhere",
    "empty",
    "enough",
    "etc",
    "even",
    "ever",
    "every",
    "everyone",
    "everything",
    "everywhere",
    "except",
    "few",
    "fifteen",
    "fifty",
    "fill",
    "find",
    "fire",
    "first",
    "five",
    "for",
    "former",
    "formerly",
    "forty",
    "found",
    "four",
    "from",
    "front",
    "full",
    "further",
    "get",
    "give",
    "go",
    "had",
    "has",
    "hasnt",
    "have",
    "he",
    "hence",
    "her",
    "here",
    "hereafter",
    "hereby",
    "herein",
    "hereupon",
    "hers",
    "herself",
    "him",
    "himself",
    "his",
    "how",
    "however",
    "hundred",
    "i",
    "ie",
    "if",
    "in",
    "inc",
    "indeed",
    "interest",
    "into",
    "is",
    "it",
    "its",
    "itself",
    "keep",
    "last",
    "latter",
    "latterly",
    "least",
    "less",
    "ltd",
    "made",
    "many",
    "may",
    "me",
    "meanwhile",
    "might",
    "mill",
    "mine",
    "more",
    "moreover",
    "most",
    "mostly",
    "move",
    "much",
    "must",
    "my",
    "myself",
    "name",
    "namely",
    "neither",
    "never",
    "nevertheless",
    "next",
    "nine",
    "no",
    "nobody",
    "none",
    "noone",
    "nor",
    "not",
    "nothing",
    "now",
    "nowhere",
    "of",
    "off",
    "often",
    "on",
    "once",
    "one",
    "only",
    "onto",
    "or",
    "other",
    "others",
    "otherwise",
    "our",
    "ours",
    "ourselves",
    "out",
    "over",
    "own",
    "part",
    "per",
    "perhaps",
    "please",
    "put",
    "rather",
    "re",
    "same",
    "see",
    "seem",
    "seemed",
    "seeming",
    "seems",
    "serious",
    "several",
    "she",
    "should",
    "show",
    "side",
    "since",
    "sincere",
    "six",
    "sixty",
    "so",
    "some",
    "somehow",
    "someone",
    "something",
    "sometime",
    "sometimes",
    "somewhere",
    "still",
    "such",
    "system",
    "take",
    "ten",
    "than",
    "that",
    "the",
    "their",
    "them",
    "themselves",
    "then",
    "thence",
    "there",
    "thereafter",
    "thereby",
    "therefore",
    "therein",
    "thereupon",
    "these",
    "they",
    "thick",
    "thin",
    "third",
    "this",
    "those",
    "though",
    "three",
    "through",
    "throughout",
    "thru",
    "thus",
    "to",
    "together",
    "too",
    "top",
    "toward",
    "towards",
    "twelve",
    "twenty",
    "two",
    "un",
    "under",
    "until",
    "up",
    "upon",
    "us",
    "very",
    "via",
    "was",
    "we",
    "well",
    "were",
    "what",
    "whatever",
    "when",
    "whence",
    "whenever",
    "where",
    "whereafter",
    "whereas",
    "whereby",
    "wherein",
    "whereupon",
    "wherever",
    "whether",
    "which",
    "while",
    "whither",
    "who",
    "whoever",
    "whole",
    "whom",
    "whose",
    "why",
    "will",
    "with",
    "within",
    "without",
    "would",
    "yet",
    "you",
    "your",
    "yours",
    "yourself",
    "yourselves",
];

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Issue {
    pub number: u64,
    pub title: String,
    pub body: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct DuplicateMatch {
    pub issue: Issue,
    pub score: f64,
}

pub fn weighted_issue_text(title: &str, body: &str) -> String {
    let t = title.trim();
    let b = body.trim();
    let mut parts = Vec::new();
    if !t.is_empty() {
        parts.push(t);
        parts.push(t);
    }
    if !b.is_empty() {
        parts.push(b);
    }
    parts.join("\n")
}

pub fn tokenize(text: &str, stop_words: &HashSet<&str>) -> Vec<String> {
    // Matches scikit-learn (?u)\b\w\w+\b
    let word_re = Regex::new(r"(?u)\b\w\w+\b").unwrap();
    let lower = text.to_lowercase();
    let mut tokens = Vec::new();
    for m in word_re.find_iter(&lower) {
        let token = m.as_str();
        if !stop_words.contains(token) {
            tokens.push(token.to_string());
        }
    }
    tokens
}

pub fn find_duplicate(
    title: &str,
    body: &str,
    existing_issues: &[Issue],
) -> Option<DuplicateMatch> {
    if existing_issues.is_empty() {
        return None;
    }

    let stop_words: HashSet<&str> = ENGLISH_STOP_WORDS.iter().copied().collect();

    let mut documents = Vec::with_capacity(existing_issues.len() + 1);
    documents.push(weighted_issue_text(title, body));
    for issue in existing_issues {
        documents.push(weighted_issue_text(&issue.title, &issue.body));
    }

    let tokenized_docs: Vec<Vec<String>> = documents
        .iter()
        .map(|doc| tokenize(doc, &stop_words))
        .collect();

    // Collect vocabulary
    let mut vocab_set = HashSet::new();
    for doc in &tokenized_docs {
        for token in doc {
            vocab_set.insert(token.as_str());
        }
    }

    if vocab_set.is_empty() {
        eprintln!("INFO: No meaningful terms were available for duplicate comparison.");
        return None;
    }

    let mut vocab: Vec<&str> = vocab_set.into_iter().collect();
    vocab.sort();

    let vocab_indices: HashMap<&str, usize> =
        vocab.iter().enumerate().map(|(i, &w)| (w, i)).collect();

    let n = documents.len();
    let mut df = vec![0usize; vocab.len()];
    for doc in &tokenized_docs {
        let mut seen = HashSet::new();
        for token in doc {
            if seen.insert(token.as_str()) {
                if let Some(&idx) = vocab_indices.get(token.as_str()) {
                    df[idx] += 1;
                }
            }
        }
    }

    // scikit-learn smooth_idf: idf(t) = log((1 + n) / (1 + df(t))) + 1.0
    let idf: Vec<f64> = df
        .iter()
        .map(|&count| ((1.0 + n as f64) / (1.0 + count as f64)).ln() + 1.0)
        .collect();

    // Compute TF-IDF vectors with L2 normalization
    let mut vectors = Vec::with_capacity(documents.len());
    for doc in &tokenized_docs {
        let mut counts = HashMap::new();
        for token in doc {
            *counts.entry(token.as_str()).or_insert(0usize) += 1;
        }

        let mut v = vec![0.0f64; vocab.len()];
        for (token, count) in counts {
            if let Some(&idx) = vocab_indices.get(token) {
                v[idx] = count as f64 * idf[idx];
            }
        }

        let sum_sq: f64 = v.iter().map(|&x| x * x).sum();
        let norm = sum_sq.sqrt();
        if norm > 0.0 {
            for x in &mut v {
                *x /= norm;
            }
        }
        vectors.push(v);
    }

    let target_vec = &vectors[0];
    let mut best_index = 0;
    let mut best_score = -1.0f64;

    for (i, cand_vec) in vectors.iter().enumerate().skip(1) {
        let sim: f64 = target_vec
            .iter()
            .zip(cand_vec.iter())
            .map(|(&x, &y)| x * y)
            .sum();
        if sim > best_score {
            best_score = sim;
            best_index = i - 1; // index into existing_issues
        }
    }

    if best_score <= SIMILARITY_THRESHOLD {
        return None;
    }

    Some(DuplicateMatch {
        issue: existing_issues[best_index].clone(),
        score: best_score,
    })
}

pub struct GitHubClient {
    pub repository: String,
    pub token: String,
    pub api_base_url: String,
}

impl GitHubClient {
    pub fn new(repository: String, token: String, api_base_url: String) -> Self {
        Self {
            repository,
            token,
            api_base_url,
        }
    }

    fn request(
        &self,
        method: &str,
        url: &str,
        payload: Option<&Value>,
    ) -> Result<(u16, String, HashMap<String, String>), String> {
        let mut cmd = Command::new("curl");
        cmd.args([
            "-s",
            "-S",
            "-i",
            "-X",
            method,
            url,
            "-H",
            "Accept: application/vnd.github+json",
            "-H",
            &format!("Authorization: Bearer {}", self.token),
            "-H",
            "User-Agent: local-tfidf-duplicate-checker",
            "-H",
            "X-GitHub-Api-Version: 2022-11-28",
        ]);

        let payload_str;
        if let Some(p) = payload {
            payload_str = serde_json::to_string(p).map_err(|e| e.to_string())?;
            cmd.args(["-H", "Content-Type: application/json", "-d", &payload_str]);
        }

        let output = cmd
            .output()
            .map_err(|e| format!("Failed to run curl: {e}"))?;
        let raw = String::from_utf8_lossy(&output.stdout);

        let mut status_code = 0u16;
        let mut headers = HashMap::new();

        let mut parts = raw.split("\r\n\r\n");
        let header_part = parts.next().unwrap_or("");
        let body = parts.collect::<Vec<_>>().join("\r\n\r\n");

        for (i, line) in header_part.lines().enumerate() {
            if i == 0 {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 {
                    status_code = parts[1].parse().unwrap_or(0);
                }
            } else if let Some((k, v)) = line.split_once(':') {
                headers.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
            }
        }

        Ok((status_code, body, headers))
    }

    pub fn get_open_issues(&self, excluded_issue_number: u64) -> Result<Vec<Issue>, String> {
        let mut url = Some(format!(
            "{}/repos/{}/issues?state=open&per_page=100&page=1",
            self.api_base_url, self.repository
        ));
        let mut issues = Vec::new();

        let link_next_re = Regex::new(r#"<([^>]+)>;\s*rel="next""#).unwrap();

        while let Some(current_url) = url {
            let (status, body, headers) = self.request("GET", &current_url, None)?;
            if status != 200 {
                return Err(format!(
                    "GitHub API returned HTTP {status} while listing issues: {body}"
                ));
            }

            let payload: Value = serde_json::from_str(&body)
                .map_err(|e| format!("GitHub returned non-JSON: {e}"))?;

            let list = match payload.as_array() {
                Some(arr) => arr,
                None => {
                    return Err(
                        "GitHub returned unexpected response while listing issues.".to_string()
                    )
                }
            };

            for item in list {
                if item.get("pull_request").is_some() {
                    continue;
                }
                let number = match item.get("number").and_then(|v| v.as_u64()) {
                    Some(n) => n,
                    None => continue,
                };
                if number == excluded_issue_number {
                    continue;
                }
                let title = match item.get("title").and_then(|v| v.as_str()) {
                    Some(t) => t.to_string(),
                    None => continue,
                };
                let body_str = item
                    .get("body")
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();

                issues.push(Issue {
                    number,
                    title,
                    body: body_str,
                });
            }

            url = None;
            if let Some(link_header) = headers.get("link") {
                if let Some(caps) = link_next_re.captures(link_header) {
                    if let Some(m) = caps.get(1) {
                        url = Some(m.as_str().to_string());
                    }
                }
            }
        }

        Ok(issues)
    }

    pub fn ensure_duplicate_label(&self) -> Result<(), String> {
        let check_url = format!(
            "{}/repos/{}/labels/{}",
            self.api_base_url, self.repository, DUPLICATE_LABEL
        );
        let (status, _, _) = self.request("GET", &check_url, None)?;
        if status == 200 {
            return Ok(());
        }

        let create_url = format!("{}/repos/{}/labels", self.api_base_url, self.repository);
        let payload = json!({
            "name": DUPLICATE_LABEL,
            "color": "d73a4a",
            "description": "Potential duplicate of another issue"
        });
        let (create_status, body, _) = self.request("POST", &create_url, Some(&payload))?;
        if create_status == 201 || create_status == 422 {
            Ok(())
        } else {
            Err(format!(
                "Failed to ensure duplicate label (HTTP {create_status}): {body}"
            ))
        }
    }

    pub fn apply_duplicate_label(&self, issue_number: u64) -> Result<(), String> {
        let url = format!(
            "{}/repos/{}/issues/{issue_number}/labels",
            self.api_base_url, self.repository
        );
        let payload = json!({ "labels": [DUPLICATE_LABEL] });
        let (status, body, _) = self.request("POST", &url, Some(&payload))?;
        if status == 200 || status == 201 {
            Ok(())
        } else {
            Err(format!(
                "Failed to apply duplicate label (HTTP {status}): {body}"
            ))
        }
    }

    pub fn has_comment(&self, issue_number: u64, marker: &str) -> Result<bool, String> {
        let mut url = Some(format!(
            "{}/repos/{}/issues/{issue_number}/comments?per_page=100",
            self.api_base_url, self.repository
        ));
        let link_next_re = Regex::new(r#"<([^>]+)>;\s*rel="next""#).unwrap();

        while let Some(current_url) = url {
            let (status, body, headers) = self.request("GET", &current_url, None)?;
            if status != 200 {
                return Err(format!(
                    "GitHub API returned HTTP {status} while listing comments: {body}"
                ));
            }

            let payload: Value = serde_json::from_str(&body)
                .map_err(|e| format!("GitHub returned non-JSON: {e}"))?;

            let list = match payload.as_array() {
                Some(arr) => arr,
                None => {
                    return Err(
                        "GitHub returned unexpected response while listing comments.".to_string(),
                    )
                }
            };

            for item in list {
                if let Some(comment_body) = item.get("body").and_then(|v| v.as_str()) {
                    if comment_body.contains(marker) {
                        return Ok(true);
                    }
                }
            }

            url = None;
            if let Some(link_header) = headers.get("link") {
                if let Some(caps) = link_next_re.captures(link_header) {
                    if let Some(m) = caps.get(1) {
                        url = Some(m.as_str().to_string());
                    }
                }
            }
        }

        Ok(false)
    }

    pub fn add_duplicate_comment(
        &self,
        issue_number: u64,
        original_issue_number: u64,
    ) -> Result<bool, String> {
        let marker = format!("{COMMENT_MARKER_PREFIX}{original_issue_number} -->");
        if self.has_comment(issue_number, &marker)? {
            return Ok(false);
        }

        let url = format!(
            "{}/repos/{}/issues/{issue_number}/comments",
            self.api_base_url, self.repository
        );
        let payload = json!({
            "body": format!("{marker}\nPotential duplicate of #{original_issue_number}")
        });
        let (status, body, _) = self.request("POST", &url, Some(&payload))?;
        if status == 201 {
            Ok(true)
        } else {
            Err(format!(
                "Failed to add duplicate comment (HTTP {status}): {body}"
            ))
        }
    }
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() != 4 {
        eprintln!("ERROR: Usage: duplicate-checker <issue-title> <issue-body> <owner/repository>");
        std::process::exit(2);
    }

    let title = &args[1];
    let body = &args[2];
    let repository = &args[3];

    let repo_re = Regex::new(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").unwrap();
    if !repo_re.is_match(repository) {
        eprintln!(
            "ERROR: Duplicate check failed: Repository must use the 'owner/repository' format."
        );
        std::process::exit(1);
    }

    let token = match env::var("GITHUB_TOKEN") {
        Ok(t) if !t.trim().is_empty() => t.trim().to_string(),
        _ => {
            eprintln!(
                "ERROR: Duplicate check failed: The GITHUB_TOKEN environment variable is required."
            );
            std::process::exit(1);
        }
    };

    let issue_number: u64 = match env::var("GITHUB_ISSUE_NUMBER") {
        Ok(val) => match val.trim().parse() {
            Ok(n) if n > 0 => n,
            _ => {
                eprintln!("ERROR: Duplicate check failed: The GITHUB_ISSUE_NUMBER environment variable must be a positive integer.");
                std::process::exit(1);
            }
        },
        Err(_) => {
            eprintln!("ERROR: Duplicate check failed: The GITHUB_ISSUE_NUMBER environment variable must be a positive integer.");
            std::process::exit(1);
        }
    };

    let api_base_url = env::var("GITHUB_API_URL")
        .unwrap_or_else(|_| "https://api.github.com".to_string())
        .trim_end_matches('/')
        .to_string();

    let client = GitHubClient::new(repository.clone(), token, api_base_url);

    let existing_issues = match client.get_open_issues(issue_number) {
        Ok(issues) => issues,
        Err(e) => {
            eprintln!("ERROR: Duplicate check failed: {e}");
            std::process::exit(1);
        }
    };

    eprintln!(
        "INFO: Comparing issue #{issue_number} with {} open issues.",
        existing_issues.len()
    );

    let match_opt = find_duplicate(title, body, &existing_issues);
    let matched = match match_opt {
        Some(m) => m,
        None => {
            eprintln!(
                "INFO: No issue exceeded the {SIMILARITY_THRESHOLD:.2} similarity threshold."
            );
            return;
        }
    };

    eprintln!(
        "INFO: Issue #{issue_number} matched issue #{} with similarity {:.4}.",
        matched.issue.number, matched.score
    );

    if let Err(e) = client.ensure_duplicate_label() {
        eprintln!("ERROR: Duplicate check failed: {e}");
        std::process::exit(1);
    }

    if let Err(e) = client.apply_duplicate_label(issue_number) {
        eprintln!("ERROR: Duplicate check failed: {e}");
        std::process::exit(1);
    }

    match client.add_duplicate_comment(issue_number, matched.issue.number) {
        Ok(true) => {
            eprintln!("INFO: Added a duplicate reference comment to issue #{issue_number}.");
        }
        Ok(false) => {
            eprintln!("INFO: The duplicate reference comment already exists.");
        }
        Err(e) => {
            eprintln!("ERROR: Duplicate check failed: {e}");
            std::process::exit(1);
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
    fn test_golden_clear_duplicate() {
        let existing = vec![
            Issue {
                number: 101,
                title: "App crashes when opening settings".to_string(),
                body: "Crash on startup".to_string(),
            },
            Issue {
                number: 102,
                title: "Battery drain".to_string(),
                body: "Fast drain on background playback".to_string(),
            },
        ];
        let m = find_duplicate(
            "Crash on startup",
            "App crashes when opening settings screen",
            &existing,
        )
        .unwrap();
        assert_eq!(m.issue.number, 101);
        assert!((m.score - 0.763356).abs() < 1e-4);
    }

    #[test]
    fn test_golden_borderline_cases() {
        let existing = vec![
            Issue {
                number: 201,
                title: "Bluetooth audio stuttering".to_string(),
                body: "Playback stutters on bluetooth headphones".to_string(),
            },
            Issue {
                number: 202,
                title: "Bluetooth stuttering during playback".to_string(),
                body: "Audio streaming stutters".to_string(),
            },
            Issue {
                number: 203,
                title: "Dark mode theme glitch".to_string(),
                body: "Colors inverted in dark mode".to_string(),
            },
        ];
        let m = find_duplicate(
            "Bluetooth playback stuttering during audio streaming",
            "Audio stutters when connected to bluetooth car stereo",
            &existing,
        )
        .unwrap();
        assert_eq!(m.issue.number, 202);
        assert!((m.score - 0.839863).abs() < 1e-4);
    }

    #[test]
    fn test_golden_tie_breaker() {
        let existing = vec![
            Issue {
                number: 301,
                title: "Equal match query".to_string(),
                body: "same words here".to_string(),
            },
            Issue {
                number: 302,
                title: "Equal match query".to_string(),
                body: "same words here".to_string(),
            },
        ];
        let m = find_duplicate("Equal match query", "same words here", &existing).unwrap();
        assert_eq!(m.issue.number, 301);
        assert!((m.score - 1.0).abs() < 1e-4);
    }

    #[test]
    fn test_golden_below_threshold() {
        let existing = vec![Issue {
            number: 401,
            title: "Import playlist from m3u8".to_string(),
            body: "Importing works but lacks tags".to_string(),
        }];
        let m = find_duplicate(
            "Cannot export playlist to m3u",
            "Exporting fails with permission error",
            &existing,
        );
        assert!(m.is_none());
    }

    #[test]
    fn test_golden_empty_vocabulary() {
        let existing = vec![Issue {
            number: 501,
            title: "it is".to_string(),
            body: "for that".to_string(),
        }];
        let m = find_duplicate("the of and", "in to", &existing);
        assert!(m.is_none());
    }

    #[test]
    fn test_mock_github_client() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();

        let handle = thread::spawn(move || {
            // Page 1 of issues
            let (mut stream1, _) = listener.accept().unwrap();
            let mut buf1 = [0u8; 2048];
            let _ = stream1.read(&mut buf1).unwrap();
            let page1_body = json!([
                {"number": 1, "title": "Crash", "body": "details"},
                {"number": 2, "title": "PR", "body": "pull", "pull_request": {}}
            ])
            .to_string();
            let resp1 = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nLink: <http://127.0.0.1:{port}/page2>; rel=\"next\"\r\nConnection: close\r\n\r\n{}",
                page1_body.len(), page1_body
            );
            stream1.write_all(resp1.as_bytes()).unwrap();

            // Page 2 of issues
            let (mut stream2, _) = listener.accept().unwrap();
            let mut buf2 = [0u8; 2048];
            let _ = stream2.read(&mut buf2).unwrap();
            let page2_body = json!([
                {"number": 3, "title": "Feature", "body": "request"}
            ])
            .to_string();
            let resp2 = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                page2_body.len(), page2_body
            );
            stream2.write_all(resp2.as_bytes()).unwrap();

            // Check label
            let (mut stream3, _) = listener.accept().unwrap();
            let mut buf3 = [0u8; 2048];
            let _ = stream3.read(&mut buf3).unwrap();
            let resp3 = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            stream3.write_all(resp3.as_bytes()).unwrap();

            // Apply label
            let (mut stream4, _) = listener.accept().unwrap();
            let mut buf4 = [0u8; 2048];
            let _ = stream4.read(&mut buf4).unwrap();
            let resp4 = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            stream4.write_all(resp4.as_bytes()).unwrap();

            // Check existing comments
            let (mut stream5, _) = listener.accept().unwrap();
            let mut buf5 = [0u8; 2048];
            let _ = stream5.read(&mut buf5).unwrap();
            let empty_comments = "[]";
            let resp5 = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                empty_comments.len(), empty_comments
            );
            stream5.write_all(resp5.as_bytes()).unwrap();

            // Add duplicate comment
            let (mut stream6, _) = listener.accept().unwrap();
            let mut buf6 = [0u8; 2048];
            let _ = stream6.read(&mut buf6).unwrap();
            let resp6 = "HTTP/1.1 201 Created\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            stream6.write_all(resp6.as_bytes()).unwrap();
        });

        let client = GitHubClient::new(
            "owner/repo".to_string(),
            "token".to_string(),
            format!("http://127.0.0.1:{port}"),
        );

        let issues = client.get_open_issues(999).unwrap();
        assert_eq!(issues.len(), 2);
        assert_eq!(issues[0].number, 1);
        assert_eq!(issues[1].number, 3);

        assert!(client.ensure_duplicate_label().is_ok());
        assert!(client.apply_duplicate_label(10).is_ok());
        assert!(client.add_duplicate_comment(10, 1).unwrap());

        handle.join().unwrap();
    }
}
