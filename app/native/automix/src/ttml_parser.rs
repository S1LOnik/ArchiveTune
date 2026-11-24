// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

use std::collections::HashMap;
use std::fmt::Write as _;

const MAX_TTML_BYTES: usize = 4 * 1024 * 1024;
const MAX_XML_DEPTH: usize = 64;

#[derive(Debug, Clone, PartialEq)]
pub struct TtmlWord {
    pub text: String,
    pub start_time: f64,
    pub end_time: f64,
    pub is_background: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TtmlLine {
    pub text: String,
    pub start_time: f64,
    pub end_time: f64,
    pub words: Vec<TtmlWord>,
    pub agent: Option<String>,
    pub provider_romanized_text: Option<String>,
    pub provider_romanized_words: Option<Vec<String>>,
    pub provider_romanized_language: Option<String>,
    pub provider_translation_text: Option<String>,
}

#[derive(Debug, Clone)]
struct TimeContext {
    tick_rate: f64,
    frame_rate: f64,
}

impl Default for TimeContext {
    fn default() -> Self {
        Self {
            tick_rate: 1.0,
            frame_rate: 30.0,
        }
    }
}

/// Decodes standard XML entities and numeric character references safely in a single pass.
pub fn unescape_xml(input: &str) -> String {
    if !input.contains('&') {
        return input.to_string();
    }
    let mut out = String::with_capacity(input.len());
    let mut rest = input;
    while let Some(amp) = rest.find('&') {
        out.push_str(&rest[..amp]);
        let after = &rest[amp + 1..];
        if let Some(semi) = after.find(';') {
            let entity = &after[..semi];
            if semi <= 12
                && let Some(ch) = decode_entity(entity)
            {
                out.push(ch);
                rest = &after[semi + 1..];
                continue;
            }
        }
        out.push('&');
        rest = after;
    }
    out.push_str(rest);
    out
}

fn decode_entity(entity: &str) -> Option<char> {
    match entity {
        "amp" => Some('&'),
        "lt" => Some('<'),
        "gt" => Some('>'),
        "quot" => Some('"'),
        "apos" => Some('\''),
        "nbsp" => Some('\u{00A0}'),
        _ if entity.starts_with("#x") || entity.starts_with("#X") => {
            u32::from_str_radix(&entity[2..], 16)
                .ok()
                .and_then(char::from_u32)
        }
        _ if entity.starts_with('#') => entity[1..].parse::<u32>().ok().and_then(char::from_u32),
        _ => None,
    }
}

/// Escapes a Rust `&str` as a valid JSON string literal (including quotes).
pub fn push_json_str(out: &mut String, value: &str) {
    out.push('"');
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            c => out.push(c),
        }
    }
    out.push('"');
}

/// Parses TTML media timing expressions (`HH:MM:SS.fff`, `MM:SS.fff`, `SS.fff`, `ms`, `s`, `m`, `h`, `t`, `f`).
fn parse_ttml_time(raw: &str, ctx: &TimeContext) -> Option<f64> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return None;
    }
    if trimmed.contains(':') {
        let parts: Vec<&str> = trimmed.split(':').collect();
        match parts.as_slice() {
            [h, m, s] => {
                let hours: f64 = h.parse().ok()?;
                let mins: f64 = m.parse().ok()?;
                let secs: f64 = s.parse().ok()?;
                Some(hours * 3600.0 + mins * 60.0 + secs)
            }
            [m, s] => {
                let mins: f64 = m.parse().ok()?;
                let secs: f64 = s.parse().ok()?;
                Some(mins * 60.0 + secs)
            }
            _ => None,
        }
    } else if let Some(val) = trimmed.strip_suffix("ms") {
        val.trim().parse::<f64>().ok().map(|v| v / 1000.0)
    } else if let Some(val) = trimmed.strip_suffix('s') {
        val.trim().parse::<f64>().ok()
    } else if let Some(val) = trimmed.strip_suffix('m') {
        val.trim().parse::<f64>().ok().map(|v| v * 60.0)
    } else if let Some(val) = trimmed.strip_suffix('h') {
        val.trim().parse::<f64>().ok().map(|v| v * 3600.0)
    } else if let Some(val) = trimmed.strip_suffix('t') {
        let rate = if ctx.tick_rate > 0.0 {
            ctx.tick_rate
        } else {
            1.0
        };
        val.trim().parse::<f64>().ok().map(|v| v / rate)
    } else if let Some(val) = trimmed.strip_suffix('f') {
        let rate = if ctx.frame_rate > 0.0 {
            ctx.frame_rate
        } else {
            30.0
        };
        val.trim().parse::<f64>().ok().map(|v| v / rate)
    } else {
        trimmed.parse::<f64>().ok()
    }
}

#[derive(Debug, Clone)]
enum XmlNode {
    Element {
        name: String,
        attrs: HashMap<String, String>,
        children: Vec<XmlNode>,
    },
    Text(String),
}

impl XmlNode {
    fn attr(&self, key: &str) -> Option<&str> {
        match self {
            XmlNode::Element { attrs, .. } => {
                if let Some(v) = attrs.get(key) {
                    return Some(v.as_str());
                }
                let local = key.split(':').next_back().unwrap_or(key);
                attrs
                    .iter()
                    .find(|(k, _)| k.split(':').next_back().unwrap_or(k) == local)
                    .map(|(_, v)| v.as_str())
            }
            XmlNode::Text(_) => None,
        }
    }

    fn local_name(&self) -> Option<&str> {
        match self {
            XmlNode::Element { name, .. } => Some(name.split(':').next_back().unwrap_or(name)),
            XmlNode::Text(_) => None,
        }
    }

    fn collect_text(&self, out: &mut String) {
        match self {
            XmlNode::Text(t) => out.push_str(t),
            XmlNode::Element { name, children, .. } => {
                let local = name.split(':').next_back().unwrap_or(name);
                if local.eq_ignore_ascii_case("br") {
                    out.push('\n');
                    return;
                }
                for child in children {
                    child.collect_text(out);
                }
            }
        }
    }
}

/// Lightweight, XXE-safe DOM XML parser tailored for TTML/MPEG-DASH documents.
fn parse_xml_nodes(xml: &str) -> Result<Vec<XmlNode>, &'static str> {
    if xml.len() > MAX_TTML_BYTES {
        return Err("TTML payload exceeds safety size limit");
    }
    let bytes = xml.as_bytes();
    let len = bytes.len();
    let mut pos = 0usize;
    let mut stack: Vec<(String, HashMap<String, String>, Vec<XmlNode>)> = Vec::new();
    let mut roots: Vec<XmlNode> = Vec::new();

    while pos < len {
        if let Some(lt_rel) = xml[pos..].find('<') {
            if lt_rel > 0 {
                let text = unescape_xml(&xml[pos..pos + lt_rel]);
                push_node(&mut stack, &mut roots, XmlNode::Text(text));
            }
            pos += lt_rel;
            if xml[pos..].starts_with("<!--") {
                if let Some(end) = xml[pos + 4..].find("-->") {
                    pos += 4 + end + 3;
                    continue;
                }
                return Err("unterminated XML comment");
            }
            if xml[pos..].starts_with("<?") {
                if let Some(end) = xml[pos + 2..].find("?>") {
                    pos += 2 + end + 2;
                    continue;
                }
                return Err("unterminated XML processing instruction");
            }
            if xml[pos..].starts_with("<![CDATA[") {
                if let Some(end) = xml[pos + 9..].find("]]>") {
                    let cdata = xml[pos + 9..pos + 9 + end].to_string();
                    push_node(&mut stack, &mut roots, XmlNode::Text(cdata));
                    pos += 9 + end + 3;
                    continue;
                }
                return Err("unterminated CDATA section");
            }
            if xml[pos..].starts_with("<!DOCTYPE") || xml[pos..].starts_with("<!ENTITY") {
                return Err("DOCTYPE/ENTITY declarations are disallowed for XXE safety");
            }
            if xml[pos..].starts_with("</") {
                let close_end = xml[pos + 2..].find('>').ok_or("unterminated close tag")?;
                let tag_name = xml[pos + 2..pos + 2 + close_end].trim();
                pos += 2 + close_end + 1;
                if let Some((open_name, attrs, children)) = stack.pop() {
                    let _ = tag_name;
                    let elem = XmlNode::Element {
                        name: open_name,
                        attrs,
                        children,
                    };
                    push_node(&mut stack, &mut roots, elem);
                }
                continue;
            }

            // Start tag
            let (tag_end, self_closing) = find_tag_end(xml, pos + 1)?;
            let inner = &xml[pos + 1..tag_end];
            let (name, attrs) = parse_tag_header(inner);
            pos = if self_closing {
                tag_end + 2
            } else {
                tag_end + 1
            };
            if name.is_empty() {
                continue;
            }
            if self_closing {
                let elem = XmlNode::Element {
                    name,
                    attrs,
                    children: Vec::new(),
                };
                push_node(&mut stack, &mut roots, elem);
            } else {
                if stack.len() >= MAX_XML_DEPTH {
                    return Err("maximum XML nesting depth exceeded");
                }
                stack.push((name, attrs, Vec::new()));
            }
        } else {
            let text = unescape_xml(&xml[pos..]);
            push_node(&mut stack, &mut roots, XmlNode::Text(text));
            break;
        }
    }

    while let Some((open_name, attrs, children)) = stack.pop() {
        let elem = XmlNode::Element {
            name: open_name,
            attrs,
            children,
        };
        push_node(&mut stack, &mut roots, elem);
    }

    Ok(roots)
}

fn push_node(
    stack: &mut [(String, HashMap<String, String>, Vec<XmlNode>)],
    roots: &mut Vec<XmlNode>,
    node: XmlNode,
) {
    if let Some((_, _, children)) = stack.last_mut() {
        children.push(node);
    } else {
        roots.push(node);
    }
}

fn find_tag_end(xml: &str, start: usize) -> Result<(usize, bool), &'static str> {
    let bytes = xml.as_bytes();
    let mut i = start;
    let mut quote: Option<u8> = None;
    while i < bytes.len() {
        let b = bytes[i];
        match quote {
            Some(q) => {
                if b == q {
                    quote = None;
                }
            }
            None => {
                if b == b'"' || b == b'\'' {
                    quote = Some(b);
                } else if b == b'/' && i + 1 < bytes.len() && bytes[i + 1] == b'>' {
                    return Ok((i, true));
                } else if b == b'>' {
                    return Ok((i, false));
                }
            }
        }
        i += 1;
    }
    Err("unterminated start tag")
}

fn parse_tag_header(raw: &str) -> (String, HashMap<String, String>) {
    let trimmed = raw.trim();
    let mut name_end = 0;
    for (idx, ch) in trimmed.char_indices() {
        if ch.is_whitespace() {
            break;
        }
        name_end = idx + ch.len_utf8();
    }
    let name = trimmed[..name_end].to_string();
    let mut attrs = HashMap::new();
    let mut rest = trimmed[name_end..].trim_start();

    while !rest.is_empty() {
        let Some(eq) = rest.find('=') else {
            break;
        };
        let key = rest[..eq].trim().to_string();
        rest = rest[eq + 1..].trim_start();
        if let Some(q) = rest.chars().next().filter(|&c| c == '"' || c == '\'') {
            let after_q = &rest[1..];
            if let Some(close) = after_q.find(q) {
                let val = unescape_xml(&after_q[..close]);
                if !key.is_empty() {
                    attrs.insert(key, val);
                }
                rest = after_q[close + 1..].trim_start();
            } else {
                break;
            }
        } else {
            let mut val_end = rest.len();
            for (idx, ch) in rest.char_indices() {
                if ch.is_whitespace() {
                    val_end = idx;
                    break;
                }
            }
            let val = unescape_xml(&rest[..val_end]);
            if !key.is_empty() {
                attrs.insert(key, val);
            }
            rest = rest[val_end..].trim_start();
        }
    }

    (name, attrs)
}

#[derive(Default)]
struct ParsedMetadata {
    agents: HashMap<String, String>,
    translations: HashMap<String, String>,
    transliterations: HashMap<String, (String, Vec<String>, Option<String>)>,
}

fn collect_metadata(node: &XmlNode, ctx: &TimeContext, meta: &mut ParsedMetadata) {
    let XmlNode::Element { children, .. } = node else {
        return;
    };
    let local = node.local_name().unwrap_or("");
    if local.eq_ignore_ascii_case("agent") {
        if let Some(id) = node.attr("xml:id").or_else(|| node.attr("id")) {
            let kind = node.attr("type").unwrap_or("person");
            let normalized = match kind {
                "group" => {
                    if id.starts_with('v') {
                        id.to_string()
                    } else {
                        "v1000".to_string()
                    }
                }
                _ => id.to_string(),
            };
            meta.agents.insert(id.to_string(), normalized);
        }
        return;
    }

    if local.eq_ignore_ascii_case("translations") {
        for trans in children {
            if trans
                .local_name()
                .is_some_and(|n| n.eq_ignore_ascii_case("translation"))
                && let XmlNode::Element {
                    children: t_children,
                    ..
                } = trans
            {
                for text_node in t_children {
                    if text_node
                        .local_name()
                        .is_some_and(|n| n.eq_ignore_ascii_case("text"))
                        && let Some(target_key) = text_node.attr("for")
                    {
                        let mut buf = String::new();
                        text_node.collect_text(&mut buf);
                        let cleaned = buf.trim().to_string();
                        if !cleaned.is_empty() {
                            meta.translations.insert(target_key.to_string(), cleaned);
                        }
                    }
                }
            }
        }
        return;
    }

    if local.eq_ignore_ascii_case("transliterations") {
        for tr in children {
            if tr
                .local_name()
                .is_some_and(|n| n.eq_ignore_ascii_case("transliteration"))
            {
                let lang = tr
                    .attr("xml:lang")
                    .or_else(|| tr.attr("lang"))
                    .map(str::to_string);
                if let XmlNode::Element {
                    children: t_children,
                    ..
                } = tr
                {
                    for text_node in t_children {
                        if text_node
                            .local_name()
                            .is_some_and(|n| n.eq_ignore_ascii_case("text"))
                            && let Some(target_key) = text_node.attr("for")
                        {
                            let mut full_text = String::new();
                            let mut words = Vec::new();
                            collect_translit_text_and_words(
                                text_node,
                                ctx,
                                &mut full_text,
                                &mut words,
                            );
                            let cleaned = full_text.trim().to_string();
                            if !cleaned.is_empty() {
                                meta.transliterations
                                    .insert(target_key.to_string(), (cleaned, words, lang.clone()));
                            }
                        }
                    }
                }
            }
        }
        return;
    }

    for child in children {
        collect_metadata(child, ctx, meta);
    }
}

fn collect_translit_text_and_words(
    node: &XmlNode,
    ctx: &TimeContext,
    full: &mut String,
    words: &mut Vec<String>,
) {
    match node {
        XmlNode::Text(t) => full.push_str(t),
        XmlNode::Element { children, .. } => {
            if node
                .local_name()
                .is_some_and(|n| n.eq_ignore_ascii_case("span"))
            {
                let has_time = node
                    .attr("begin")
                    .is_some_and(|b| parse_ttml_time(b, ctx).is_some());
                if has_time {
                    let mut w = String::new();
                    node.collect_text(&mut w);
                    full.push_str(&w);
                    let trimmed = w.trim();
                    if !trimmed.is_empty() {
                        words.push(trimmed.to_string());
                    }
                    return;
                }
            }
            for ch in children {
                collect_translit_text_and_words(ch, ctx, full, words);
            }
        }
    }
}

/// Parses Apple Music / Musixmatch TTML XML into structured word-synced lines.
pub fn parse_ttml(xml: &str) -> Vec<TtmlLine> {
    let trimmed = xml.trim();
    if trimmed.is_empty() {
        return Vec::new();
    }
    let Ok(roots) = parse_xml_nodes(trimmed) else {
        return Vec::new();
    };
    let Some(tt_node) = roots
        .iter()
        .find(|n| {
            n.local_name()
                .is_some_and(|name| name.eq_ignore_ascii_case("tt"))
        })
        .or_else(|| roots.iter().find(|n| matches!(n, XmlNode::Element { .. })))
    else {
        return Vec::new();
    };

    let mut ctx = TimeContext {
        tick_rate: tt_node
            .attr("ttp:tickRate")
            .and_then(|v| v.parse::<f64>().ok())
            .filter(|v| *v > 0.0)
            .unwrap_or(1.0),
        frame_rate: tt_node
            .attr("ttp:frameRate")
            .and_then(|v| v.parse::<f64>().ok())
            .filter(|v| *v > 0.0)
            .unwrap_or(30.0),
    };
    if ctx.tick_rate <= 0.0 {
        ctx.tick_rate = 1.0;
    }

    let mut meta = ParsedMetadata::default();
    collect_metadata(tt_node, &ctx, &mut meta);

    let mut p_nodes: Vec<(&XmlNode, Option<String>)> = Vec::new();
    collect_p_elements(tt_node, None, &mut p_nodes);

    let mut lines = Vec::with_capacity(p_nodes.len());
    for (p, inherited_agent) in p_nodes {
        if let Some(line) = parse_p_element(p, inherited_agent.as_deref(), &ctx, &meta) {
            lines.push(line);
        }
    }

    lines.sort_by(|a, b| a.start_time.total_cmp(&b.start_time));
    lines
}

fn collect_p_elements<'a>(
    node: &'a XmlNode,
    inherited_agent: Option<&str>,
    out: &mut Vec<(&'a XmlNode, Option<String>)>,
) {
    let XmlNode::Element { children, .. } = node else {
        return;
    };
    let current_agent = node
        .attr("ttm:agent")
        .or_else(|| node.attr("agent"))
        .or(inherited_agent);
    if node
        .local_name()
        .is_some_and(|n| n.eq_ignore_ascii_case("p"))
    {
        out.push((node, current_agent.map(str::to_string)));
        return;
    }
    for child in children {
        collect_p_elements(child, current_agent, out);
    }
}

fn parse_p_element(
    p: &XmlNode,
    inherited_agent: Option<&str>,
    ctx: &TimeContext,
    meta: &ParsedMetadata,
) -> Option<TtmlLine> {
    let line_begin = p.attr("begin").and_then(|v| parse_ttml_time(v, ctx));
    let line_end_raw = p
        .attr("end")
        .and_then(|v| parse_ttml_time(v, ctx))
        .or_else(|| {
            p.attr("dur")
                .and_then(|d| parse_ttml_time(d, ctx))
                .and_then(|dur| line_begin.map(|b| b + dur))
        });

    let raw_agent = p
        .attr("ttm:agent")
        .or_else(|| p.attr("agent"))
        .or(inherited_agent)
        .map(|a| meta.agents.get(a).cloned().unwrap_or_else(|| a.to_string()));

    let key = p.attr("itunes:key").or_else(|| p.attr("key"));

    let mut words: Vec<TtmlWord> = Vec::new();
    let mut main_text = String::new();
    let mut inline_translation: Option<String> = None;
    let mut inline_roman: Option<String> = None;

    if let XmlNode::Element { children, .. } = p {
        walk_p_children(
            children,
            ctx,
            false,
            &mut words,
            &mut main_text,
            &mut inline_translation,
            &mut inline_roman,
        );
    }

    trim_parentheses_from_background(&mut words);

    let computed_start = line_begin.or_else(|| words.first().map(|w| w.start_time))?;
    let computed_end = line_end_raw
        .or_else(|| words.last().map(|w| w.end_time))
        .unwrap_or(computed_start)
        .max(computed_start);

    let resolved_text = if !main_text.trim().is_empty() {
        main_text.trim().to_string()
    } else {
        words
            .iter()
            .filter(|w| !w.is_background)
            .map(|w| w.text.as_str())
            .collect::<String>()
            .trim()
            .to_string()
    };

    if resolved_text.is_empty() && words.is_empty() {
        return None;
    }

    let final_text = if resolved_text.is_empty() {
        words
            .iter()
            .map(|w| w.text.as_str())
            .collect::<String>()
            .trim()
            .to_string()
    } else {
        resolved_text
    };

    let (provider_romanized_text, provider_romanized_words, provider_romanized_language) =
        if let Some(k) = key {
            if let Some((txt, r_words, lang)) = meta.transliterations.get(k) {
                (
                    Some(txt.clone()),
                    if r_words.is_empty() {
                        None
                    } else {
                        Some(r_words.clone())
                    },
                    lang.clone(),
                )
            } else {
                (inline_roman, None, None)
            }
        } else {
            (inline_roman, None, None)
        };

    let provider_translation_text = key
        .and_then(|k| meta.translations.get(k).cloned())
        .or(inline_translation);

    Some(TtmlLine {
        text: final_text,
        start_time: computed_start,
        end_time: computed_end,
        words,
        agent: raw_agent,
        provider_romanized_text,
        provider_romanized_words,
        provider_romanized_language,
        provider_translation_text,
    })
}

fn walk_p_children(
    nodes: &[XmlNode],
    ctx: &TimeContext,
    in_background: bool,
    words: &mut Vec<TtmlWord>,
    main_text: &mut String,
    inline_translation: &mut Option<String>,
    inline_roman: &mut Option<String>,
) {
    for node in nodes {
        match node {
            XmlNode::Text(t) => {
                if !in_background {
                    main_text.push_str(t);
                    // Keep separators with foreground words for word highlighting.
                    if !t.is_empty()
                        && t.chars().all(char::is_whitespace)
                        && let Some(last) = words.last_mut()
                        && !last.is_background
                        && !last.text.ends_with(' ')
                    {
                        last.text.push(' ');
                    }
                }
            }
            XmlNode::Element { children, .. } => {
                let role = node.attr("ttm:role").or_else(|| node.attr("role"));
                if matches!(role, Some("x-translation")) {
                    let mut buf = String::new();
                    node.collect_text(&mut buf);
                    let cleaned = buf.trim().to_string();
                    if !cleaned.is_empty() {
                        *inline_translation = Some(cleaned);
                    }
                    continue;
                }
                if matches!(role, Some("x-roman")) {
                    let mut buf = String::new();
                    node.collect_text(&mut buf);
                    let cleaned = buf.trim().to_string();
                    if !cleaned.is_empty() {
                        *inline_roman = Some(cleaned);
                    }
                    continue;
                }

                let is_bg_here = in_background || matches!(role, Some("x-bg"));
                let is_span = node
                    .local_name()
                    .is_some_and(|n| n.eq_ignore_ascii_case("span"));
                if is_span
                    && let Some(begin) = node.attr("begin").and_then(|v| parse_ttml_time(v, ctx))
                {
                    let end = node
                        .attr("end")
                        .and_then(|v| parse_ttml_time(v, ctx))
                        .or_else(|| {
                            node.attr("dur")
                                .and_then(|d| parse_ttml_time(d, ctx))
                                .map(|dur| begin + dur)
                        })
                        .unwrap_or(begin)
                        .max(begin);

                    let mut span_text = String::new();
                    node.collect_text(&mut span_text);
                    if !is_bg_here {
                        main_text.push_str(&span_text);
                    }
                    if !span_text.trim().is_empty() {
                        words.push(TtmlWord {
                            text: span_text,
                            start_time: begin,
                            end_time: end,
                            is_background: is_bg_here,
                        });
                    }
                    continue;
                }

                walk_p_children(
                    children,
                    ctx,
                    is_bg_here,
                    words,
                    main_text,
                    inline_translation,
                    inline_roman,
                );
            }
        }
    }
}

fn trim_parentheses_from_background(words: &mut [TtmlWord]) {
    let bg_indices: Vec<usize> = words
        .iter()
        .enumerate()
        .filter(|(_, w)| w.is_background)
        .map(|(i, _)| i)
        .collect();
    if let (Some(&first_idx), Some(&last_idx)) = (bg_indices.first(), bg_indices.last()) {
        if let Some(trimmed) = words[first_idx].text.strip_prefix('(') {
            words[first_idx].text = trimmed.to_string();
        }
        let has_trailing_space = words[last_idx].text.ends_with(' ');
        let base = words[last_idx].text.trim_end();
        if let Some(stripped) = base.strip_suffix(')') {
            let mut updated = stripped.to_string();
            if has_trailing_space && !updated.ends_with(' ') {
                updated.push(' ');
            }
            words[last_idx].text = updated;
        }
    }
}

/// Serializes parsed TTML lines into JSON consumed directly by Kotlin `LyricsUtils.parseTtml`.
pub fn parse_ttml_to_json(xml: &str) -> String {
    let lines = parse_ttml(xml);
    let mut out = String::with_capacity(lines.len() * 128 + 4);
    out.push('[');
    for (i, line) in lines.iter().enumerate() {
        if i > 0 {
            out.push(',');
        }
        out.push('{');
        out.push_str("\"text\":");
        push_json_str(&mut out, &line.text);
        let _ = write!(
            out,
            ",\"startTime\":{:.6},\"endTime\":{:.6}",
            line.start_time, line.end_time
        );
        out.push_str(",\"words\":[");
        for (wi, w) in line.words.iter().enumerate() {
            if wi > 0 {
                out.push(',');
            }
            out.push_str("{\"text\":");
            push_json_str(&mut out, &w.text);
            let _ = write!(
                out,
                ",\"startTime\":{:.6},\"endTime\":{:.6},\"isBackground\":{}",
                w.start_time, w.end_time, w.is_background
            );
            out.push('}');
        }
        out.push(']');
        if let Some(agent) = &line.agent {
            out.push_str(",\"agent\":");
            push_json_str(&mut out, agent);
        }
        if let Some(rt) = &line.provider_romanized_text {
            out.push_str(",\"providerRomanizedText\":");
            push_json_str(&mut out, rt);
        }
        if let Some(rw) = &line.provider_romanized_words {
            out.push_str(",\"providerRomanizedWords\":[");
            for (ri, word) in rw.iter().enumerate() {
                if ri > 0 {
                    out.push(',');
                }
                push_json_str(&mut out, word);
            }
            out.push(']');
        }
        if let Some(rl) = &line.provider_romanized_language {
            out.push_str(",\"providerRomanizedLanguage\":");
            push_json_str(&mut out, rl);
        }
        if let Some(tt) = &line.provider_translation_text {
            out.push_str(",\"providerTranslationText\":");
            push_json_str(&mut out, tt);
        }
        out.push('}');
    }
    out.push(']');
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_time_units_accurately() {
        let ctx = TimeContext {
            tick_rate: 1000.0,
            frame_rate: 30.0,
        };
        assert!((parse_ttml_time("01:02.500", &ctx).unwrap() - 62.5).abs() < 1e-6);
        assert!((parse_ttml_time("00:01:02.250", &ctx).unwrap() - 62.25).abs() < 1e-6);
        assert!((parse_ttml_time("1500ms", &ctx).unwrap() - 1.5).abs() < 1e-6);
        assert!((parse_ttml_time("2500t", &ctx).unwrap() - 2.5).abs() < 1e-6);
    }

    #[test]
    fn parses_word_spans_agents_and_itunes_translations() {
        let xml = r#"
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
            xmlns:itunes="http://itunes.apple.com/lyricstool/ttml">
          <head>
            <metadata>
              <ttm:agent type="person" xml:id="v1"/>
              <iTunesMetadata>
                <translations>
                  <translation xml:lang="en"><text for="L1">Hello world</text></translation>
                </translations>
              </iTunesMetadata>
            </metadata>
          </head>
          <body>
            <div>
              <p begin="00:01.000" end="00:03.000" ttm:agent="v1" itunes:key="L1">
                <span begin="00:01.000" end="00:02.000">Привет</span> <span
                    begin="00:02.000" end="00:03.000">мир</span>
                <span ttm:role="x-bg"><span begin="00:02.500" end="00:03.000">(yeah)</span></span>
              </p>
            </div>
          </body>
        </tt>"#;
        let parsed = parse_ttml(xml);
        assert_eq!(parsed.len(), 1);
        let line = &parsed[0];
        assert_eq!(line.text, "Привет мир");
        assert_eq!(line.agent.as_deref(), Some("v1"));
        assert_eq!(
            line.provider_translation_text.as_deref(),
            Some("Hello world")
        );
        assert_eq!(line.words.len(), 3);
        assert!(line.words[2].is_background);
        assert_eq!(line.words[2].text, "yeah");
    }

    #[test]
    fn blocks_xxe_doctype() {
        let evil = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><tt/>";
        assert!(parse_ttml(evil).is_empty());
    }
}
