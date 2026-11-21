pub mod error;
pub mod metadata;
pub mod preview;
pub mod remove;
pub mod submission;
pub mod svg;

use error::SubmissionError;
use std::env;
use std::path::PathBuf;

fn print_usage() {
    eprintln!(
        "Usage:
  icon-pack-tool submission --mode <pull-request|rebuild|update> [--event-path <path>] [--base-sha <sha>] [--random-digits <6>]
  icon-pack-tool preview generate --base-sha <sha> --metadata <path> --svg-directory <dir> --output-directory <dir> --manifest <path> --renderer <path>
  icon-pack-tool preview comment --manifest <path> --repository <repo> --head-repository <repo> --commit-sha <sha> --pull-request <num> --token <token>
  icon-pack-tool remove --name <name>"
    );
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        print_usage();
        std::process::exit(1);
    }

    let command = args[1].as_str();
    let result = match command {
        "submission" | "process-submission" => handle_submission(&args[2..]),
        "preview" => {
            if args.len() < 3 {
                print_usage();
                std::process::exit(1);
            }
            match args[2].as_str() {
                "generate" => handle_preview_generate(&args[3..]),
                "comment" => handle_preview_comment(&args[3..]),
                other => Err(SubmissionError(format!("Unknown preview command: {other}"))),
            }
        }
        "remove" => handle_remove(&args[2..]),
        "-h" | "--help" | "help" => {
            print_usage();
            Ok(())
        }
        other => Err(SubmissionError(format!("Unknown command: {other}"))),
    };

    if let Err(e) = result {
        eprintln!("::error::{e}");
        std::process::exit(1);
    }
}

fn handle_submission(args: &[String]) -> Result<(), SubmissionError> {
    let mut mode = None;
    let mut event_path: Option<PathBuf> = None;
    let mut base_sha = None;
    let mut random_digits = 6;

    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
            "--mode" => {
                i += 1;
                if i < args.len() {
                    mode = Some(args[i].clone());
                }
            }
            "--event-path" => {
                i += 1;
                if i < args.len() {
                    event_path = Some(PathBuf::from(&args[i]));
                }
            }
            "--base-sha" => {
                i += 1;
                if i < args.len() {
                    base_sha = Some(args[i].clone());
                }
            }
            "--random-digits" => {
                i += 1;
                if i < args.len() {
                    random_digits = args[i].parse().unwrap_or(6);
                }
            }
            other => {
                return Err(SubmissionError(format!(
                    "Unknown submission argument: {other}"
                )))
            }
        }
        i += 1;
    }

    let mode_str = mode.ok_or_else(|| SubmissionError("Missing --mode argument".to_string()))?;
    match mode_str.as_str() {
        "pull-request" => submission::process_pull_request(
            event_path.as_deref(),
            base_sha.as_deref(),
            random_digits,
        ),
        "rebuild" => submission::rebuild_existing_icons(random_digits),
        "update" => submission::update_existing_icons(random_digits),
        other => Err(SubmissionError(format!("Unsupported mode: {other}"))),
    }
}

fn handle_preview_generate(args: &[String]) -> Result<(), SubmissionError> {
    let mut base_sha = None;
    let mut metadata = None;
    let mut svg_directory = None;
    let mut output_directory = None;
    let mut manifest = None;
    let mut renderer = None;

    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
            "--base-sha" => {
                i += 1;
                if i < args.len() {
                    base_sha = Some(args[i].clone());
                }
            }
            "--metadata" => {
                i += 1;
                if i < args.len() {
                    metadata = Some(PathBuf::from(&args[i]));
                }
            }
            "--svg-directory" => {
                i += 1;
                if i < args.len() {
                    svg_directory = Some(PathBuf::from(&args[i]));
                }
            }
            "--output-directory" => {
                i += 1;
                if i < args.len() {
                    output_directory = Some(PathBuf::from(&args[i]));
                }
            }
            "--manifest" => {
                i += 1;
                if i < args.len() {
                    manifest = Some(PathBuf::from(&args[i]));
                }
            }
            "--renderer" => {
                i += 1;
                if i < args.len() {
                    renderer = Some(PathBuf::from(&args[i]));
                }
            }
            other => {
                return Err(SubmissionError(format!(
                    "Unknown preview generate argument: {other}"
                )))
            }
        }
        i += 1;
    }

    let base_sha = base_sha.ok_or_else(|| SubmissionError("Missing --base-sha".to_string()))?;
    let metadata = metadata.ok_or_else(|| SubmissionError("Missing --metadata".to_string()))?;
    let svg_directory =
        svg_directory.ok_or_else(|| SubmissionError("Missing --svg-directory".to_string()))?;
    let output_directory = output_directory
        .ok_or_else(|| SubmissionError("Missing --output-directory".to_string()))?;
    let manifest = manifest.ok_or_else(|| SubmissionError("Missing --manifest".to_string()))?;
    let renderer = renderer.ok_or_else(|| SubmissionError("Missing --renderer".to_string()))?;

    preview::generate_previews(
        &base_sha,
        &metadata,
        &svg_directory,
        &output_directory,
        &manifest,
        &renderer,
    )
}

fn handle_preview_comment(args: &[String]) -> Result<(), SubmissionError> {
    let mut manifest = None;
    let mut repository = None;
    let mut head_repository = None;
    let mut commit_sha = None;
    let mut pull_request = None;
    let mut token = None;

    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
            "--manifest" => {
                i += 1;
                if i < args.len() {
                    manifest = Some(PathBuf::from(&args[i]));
                }
            }
            "--repository" => {
                i += 1;
                if i < args.len() {
                    repository = Some(args[i].clone());
                }
            }
            "--head-repository" => {
                i += 1;
                if i < args.len() {
                    head_repository = Some(args[i].clone());
                }
            }
            "--commit-sha" => {
                i += 1;
                if i < args.len() {
                    commit_sha = Some(args[i].clone());
                }
            }
            "--pull-request" => {
                i += 1;
                if i < args.len() {
                    pull_request = args[i].parse().ok();
                }
            }
            "--token" => {
                i += 1;
                if i < args.len() {
                    token = Some(args[i].clone());
                }
            }
            other => {
                return Err(SubmissionError(format!(
                    "Unknown preview comment argument: {other}"
                )))
            }
        }
        i += 1;
    }

    let manifest = manifest.ok_or_else(|| SubmissionError("Missing --manifest".to_string()))?;
    let repository =
        repository.ok_or_else(|| SubmissionError("Missing --repository".to_string()))?;
    let head_repository =
        head_repository.ok_or_else(|| SubmissionError("Missing --head-repository".to_string()))?;
    let commit_sha =
        commit_sha.ok_or_else(|| SubmissionError("Missing --commit-sha".to_string()))?;
    let pull_request = pull_request
        .ok_or_else(|| SubmissionError("Missing or invalid --pull-request".to_string()))?;
    let token = token.ok_or_else(|| SubmissionError("Missing --token".to_string()))?;

    preview::post_comment(
        &manifest,
        &repository,
        &head_repository,
        &commit_sha,
        pull_request,
        &token,
    )
}

fn handle_remove(args: &[String]) -> Result<(), SubmissionError> {
    let mut name = None;
    let mut i = 0;
    while i < args.len() {
        match args[i].as_str() {
            "--name" => {
                i += 1;
                if i < args.len() {
                    name = Some(args[i].clone());
                }
            }
            other => return Err(SubmissionError(format!("Unknown remove argument: {other}"))),
        }
        i += 1;
    }

    let name = name.ok_or_else(|| SubmissionError("Missing --name argument".to_string()))?;
    remove::remove_icon_pack(&name)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn test_svg_filename_component() {
        assert_eq!(
            metadata::svg_filename_component("Marin Kitagawa", "fallback"),
            "marin_kitagawa"
        );
        assert_eq!(
            metadata::svg_filename_component("Frostwave - Japanese Winter", "fallback"),
            "frostwave_japanese_winter"
        );
        assert_eq!(
            metadata::svg_filename_component("Crème Brûlée", "fallback"),
            "creme_brulee"
        );
        assert_eq!(
            metadata::svg_filename_component("___", "fallback"),
            "fallback"
        );
        assert_eq!(
            metadata::svg_filename_component(
                "A very long name that exceeds the maximum component length of 48 characters definitely",
                "fallback"
            ),
            "a_very_long_name_that_exceeds_the_maximum_compon"
        );
    }

    #[test]
    fn test_svg_asset_stem() {
        assert_eq!(
            metadata::svg_asset_stem("Sakura", "morieeattonkatsu", "5847"),
            "sakura_morieeattonkatsu_5847"
        );
    }

    #[test]
    fn test_parse_form_valid() {
        let body = r#"
Some introduction text
<!-- ICON_METADATA_START -->
```json
{
  "icons": [
    {
      "name": "My Icon",
      "author": "Artist",
      "file": "submissions/my_icon.svg",
      "link": "https://example.com"
    }
  ]
}
```
<!-- ICON_METADATA_END -->
Trailing comment
"#;
        let icons = submission::parse_form(body).expect("parse form");
        assert_eq!(icons.len(), 1);
        assert_eq!(icons[0].name, "My Icon");
        assert_eq!(icons[0].author, "Artist");
        assert_eq!(icons[0].file, "submissions/my_icon.svg");
        assert_eq!(icons[0].link, "https://example.com");
    }

    #[test]
    fn test_parse_form_invalid_paths() {
        let body = r#"
<!-- ICON_METADATA_START -->
```json
{
  "icons": [
    {
      "name": "Traverse",
      "author": "Hacker",
      "file": "submissions/../../etc/passwd.svg"
    }
  ]
}
```
<!-- ICON_METADATA_END -->
"#;
        assert!(submission::parse_form(body).is_err());
    }

    #[test]
    fn test_svg_validation() {
        let tmp_dir = std::env::temp_dir().join(format!("test-icon-pack-{}", std::process::id()));
        let _ = fs::remove_dir_all(&tmp_dir);
        fs::create_dir_all(&tmp_dir).unwrap();

        // Valid SVG
        let valid_svg = tmp_dir.join("valid.svg");
        fs::write(
            &valid_svg,
            r#"<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><circle cx="50" cy="50" r="40" /></svg>"#,
        )
        .unwrap();
        assert!(svg::validate_svg(&valid_svg).is_ok());

        // PNG disguised as SVG
        let fake_svg = tmp_dir.join("fake.svg");
        fs::write(&fake_svg, b"\x89PNG\r\n\x1a\nSome payload").unwrap();
        assert!(svg::validate_svg(&fake_svg).is_err());

        // SVG with script
        let script_svg = tmp_dir.join("script.svg");
        fs::write(
            &script_svg,
            r#"<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>"#,
        )
        .unwrap();
        assert!(svg::validate_svg(&script_svg).is_err());

        // SVG with external reference
        let ext_svg = tmp_dir.join("ext.svg");
        fs::write(
            &ext_svg,
            r#"<svg xmlns="http://www.w3.org/2000/svg"><image href="http://evil.com/leak.png"/></svg>"#,
        )
        .unwrap();
        assert!(svg::validate_svg(&ext_svg).is_err());

        // SVG with internal reference (#id) is allowed
        let int_svg = tmp_dir.join("int.svg");
        fs::write(
            &int_svg,
            r##"<svg xmlns="http://www.w3.org/2000/svg"><use href="#my-shape"/></svg>"##,
        )
        .unwrap();
        assert!(svg::validate_svg(&int_svg).is_ok());

        // SVG with DOCTYPE sanitized
        let doctype_svg = tmp_dir.join("doctype.svg");
        fs::write(
            &doctype_svg,
            r#"<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd"><svg xmlns="http://www.w3.org/2000/svg"></svg>"#,
        )
        .unwrap();
        assert!(svg::validate_svg(&doctype_svg).is_ok());
        let sanitized = fs::read_to_string(&doctype_svg).unwrap();
        assert!(!sanitized.contains("<!DOCTYPE"));

        let _ = fs::remove_dir_all(&tmp_dir);
    }

    #[test]
    fn test_metadata_and_remove() {
        let tmp_dir =
            std::env::temp_dir().join(format!("test-icon-pack-remove-{}", std::process::id()));
        let _ = fs::remove_dir_all(&tmp_dir);
        fs::create_dir_all(tmp_dir.join("svg")).unwrap();
        fs::create_dir_all(tmp_dir.join("previews")).unwrap();
        fs::create_dir_all(tmp_dir.join("submissions")).unwrap();
        fs::create_dir_all(tmp_dir.join("xml")).unwrap();
        fs::create_dir_all(tmp_dir.join("pack")).unwrap();

        let metadata_content = r#"[
  {
    "Id": "123456",
    "Name": "Test Icon",
    "Author": "Tester",
    "Source": "test_icon_tester_123456.svg",
    "Submission": "submissions/test_icon.svg"
  },
  {
    "Id": "654321",
    "Name": "Keep Icon",
    "Author": "Keeper",
    "Source": "keep_icon_keeper_654321.svg",
    "Submission": "submissions/keep_icon.svg"
  }
]"#;
        fs::write(tmp_dir.join("metadata.json"), metadata_content).unwrap();
        fs::write(
            tmp_dir.join("svg/test_icon_tester_123456.svg"),
            "<svg></svg>",
        )
        .unwrap();
        fs::write(
            tmp_dir.join("svg/keep_icon_keeper_654321.svg"),
            "<svg></svg>",
        )
        .unwrap();
        fs::write(tmp_dir.join("previews/123456.png"), "png").unwrap();
        fs::write(tmp_dir.join("submissions/test_icon.svg"), "sub").unwrap();
        fs::write(tmp_dir.join("xml/ic_123456.xml"), "xml").unwrap();
        fs::write(tmp_dir.join("pack/Icon_123456.kt"), "kt").unwrap();

        let prev_dir = std::env::current_dir().unwrap();
        std::env::set_current_dir(&tmp_dir).unwrap();

        let remove_res = remove::remove_icon_pack("test_icon_tester_123456.svg");
        std::env::set_current_dir(&prev_dir).unwrap();

        assert!(remove_res.is_ok());
        assert!(!tmp_dir.join("svg/test_icon_tester_123456.svg").exists());
        assert!(!tmp_dir.join("previews/123456.png").exists());
        assert!(!tmp_dir.join("submissions/test_icon.svg").exists());
        assert!(!tmp_dir.join("xml/ic_123456.xml").exists());
        assert!(!tmp_dir.join("pack/Icon_123456.kt").exists());
        assert!(tmp_dir.join("svg/keep_icon_keeper_654321.svg").exists());

        let updated_meta = fs::read_to_string(tmp_dir.join("metadata.json")).unwrap();
        assert!(!updated_meta.contains("123456"));
        assert!(updated_meta.contains("654321"));

        let _ = fs::remove_dir_all(&tmp_dir);
    }
}
