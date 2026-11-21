use std::fmt;

#[derive(Debug)]
pub struct SubmissionError(pub String);

impl fmt::Display for SubmissionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for SubmissionError {}

impl From<std::io::Error> for SubmissionError {
    fn from(err: std::io::Error) -> Self {
        SubmissionError(err.to_string())
    }
}

impl From<serde_json::Error> for SubmissionError {
    fn from(err: serde_json::Error) -> Self {
        SubmissionError(err.to_string())
    }
}
