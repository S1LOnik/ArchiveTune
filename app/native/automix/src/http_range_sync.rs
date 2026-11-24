// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

/// Parsed HTTP `Content-Range: bytes start-end/total` response metadata.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ContentRangeMeta {
    pub start: u64,
    pub end: u64,
    pub total_len: Option<u64>,
}

/// Inclusive byte chunk `[start, end]` scheduled for parallel or resumable streaming.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ByteRangeChunk {
    pub start: u64,
    pub end: u64,
}

impl ByteRangeChunk {
    pub fn len_bytes(&self) -> u64 {
        self.end.saturating_sub(self.start).saturating_add(1)
    }
}

/// Parses an HTTP `Content-Range` header (e.g. `"bytes 0-524287/4194304"` or `"bytes 0-99/*"`).
pub fn parse_content_range(header: &str) -> Option<ContentRangeMeta> {
    let trimmed = header.trim();
    let rest = trimmed
        .strip_prefix("bytes ")
        .or_else(|| trimmed.strip_prefix("BYTES "))?;
    let (range_part, total_part) = rest.split_once('/')?;
    let (start_str, end_str) = range_part.split_once('-')?;
    let start: u64 = start_str.trim().parse().ok()?;
    let end: u64 = end_str.trim().parse().ok()?;
    if end < start {
        return None;
    }
    let total_len = if total_part.trim() == "*" {
        None
    } else {
        let t: u64 = total_part.trim().parse().ok()?;
        if t <= end {
            return None;
        }
        Some(t)
    };
    Some(ContentRangeMeta {
        start,
        end,
        total_len,
    })
}

/// Partitions `[start_offset, total_len)` into fixed-size HTTP byte ranges for buffered prefetch.
pub fn plan_byte_range_chunks(
    start_offset: u64,
    total_len: u64,
    chunk_size: u64,
) -> Vec<ByteRangeChunk> {
    if chunk_size == 0 || start_offset >= total_len {
        return Vec::new();
    }
    let safe_chunk = chunk_size.max(4_096);
    let remaining = total_len - start_offset;
    let estimated = (remaining.div_ceil(safe_chunk)).min(8_192) as usize;
    let mut chunks = Vec::with_capacity(estimated);
    let mut cursor = start_offset;
    while cursor < total_len {
        let end = cursor
            .saturating_add(safe_chunk - 1)
            .min(total_len - 1);
        chunks.push(ByteRangeChunk { start: cursor, end });
        cursor = end.saturating_add(1);
    }
    chunks
}

/// Coalesces overlapping or contiguous downloaded byte spans so range-cache lookups avoid gaps.
pub fn merge_byte_ranges(mut spans: Vec<ByteRangeChunk>) -> Vec<ByteRangeChunk> {
    if spans.len() <= 1 {
        return spans;
    }
    spans.sort_by_key(|s| (s.start, s.end));
    let mut merged: Vec<ByteRangeChunk> = Vec::with_capacity(spans.len());
    for span in spans {
        if span.end < span.start {
            continue;
        }
        if let Some(last) = merged.last_mut() {
            if span.start <= last.end.saturating_add(1) {
                last.end = last.end.max(span.end);
                continue;
            }
        }
        merged.push(span);
    }
    merged
}

/// Computes bounded retry backoff delay in milliseconds using deterministic full-jitter SplitMix64.
pub fn retry_backoff_ms(attempt: u32, base_ms: u64, max_ms: u64, seed: u64) -> u64 {
    let clamped_exp = attempt.min(16);
    let raw = base_ms.saturating_mul(1u64 << clamped_exp).min(max_ms.max(base_ms));
    let half = raw / 2;
    let mix = seed
        .wrapping_add((attempt as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15))
        .wrapping_mul(0xBF58_476D_1CE4_E5B9);
    let spread = (raw - half).max(1);
    half + (mix % spread)
}

/// Selects LRU/frequency-weighted cache entry indices to evict until `total_bytes <= max_bytes`.
pub fn select_cache_evictions(
    sizes: &[u64],
    last_access_ms: &[i64],
    pin_flags: &[bool],
    max_bytes: u64,
) -> Vec<usize> {
    let n = sizes.len().min(last_access_ms.len()).min(pin_flags.len());
    let mut total: u64 = sizes.iter().take(n).sum();
    if total <= max_bytes {
        return Vec::new();
    }
    let mut candidates: Vec<(i64, usize)> = (0..n)
        .filter(|&i| !pin_flags[i])
        .map(|i| (last_access_ms[i], i))
        .collect();
    candidates.sort_by_key(|&(ts, idx)| (ts, idx));

    let mut evicted = Vec::new();
    for (_, idx) in candidates {
        if total <= max_bytes {
            break;
        }
        total = total.saturating_sub(sizes[idx]);
        evicted.push(idx);
    }
    evicted
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_content_range_and_plans_chunks() {
        let meta = parse_content_range("bytes 512-1023/4096").unwrap();
        assert_eq!(meta.start, 512);
        assert_eq!(meta.end, 1023);
        assert_eq!(meta.total_len, Some(4096));

        let chunks = plan_byte_range_chunks(0, 10_000, 4_096);
        assert_eq!(chunks.len(), 3);
        assert_eq!(chunks[0].len_bytes(), 4_096);
        assert_eq!(chunks[2].end, 9_999);
    }

    #[test]
    fn merges_contiguous_spans_and_selects_lru_evictions() {
        let merged = merge_byte_ranges(vec![
            ByteRangeChunk { start: 100, end: 199 },
            ByteRangeChunk { start: 0, end: 99 },
            ByteRangeChunk { start: 300, end: 350 },
        ]);
        assert_eq!(merged.len(), 2);
        assert_eq!(merged[0], ByteRangeChunk { start: 0, end: 199 });

        let sizes = [500, 400, 300, 200];
        let accessed = [100, 50, 200, 10];
        let pinned = [false, false, false, true];
        let evicted = select_cache_evictions(&sizes, &accessed, &pinned, 600);
        // idx 3 is pinned; idx 1 (ts=50, 400B -> 1000 left), idx 0 (ts=100, 500B -> 500 left)
        assert_eq!(evicted, vec![1, 0]);
    }
}
