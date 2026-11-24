// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5

/// Unicode script classification for lyrics language detection and romanization routing.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ScriptKind {
    Latin,
    Han,
    Hiragana,
    Katakana,
    Hangul,
    Devanagari,
    Cyrillic,
    Arabic,
    Thai,
    Hebrew,
    Greek,
    Armenian,
    Georgian,
    Common,
    Other,
}

/// Classifies a single Unicode scalar value into a musical/linguistic script bucket.
pub fn classify_char(ch: char) -> ScriptKind {
    let cp = ch as u32;
    if ch.is_ascii() {
        return if ch.is_ascii_alphabetic() {
            ScriptKind::Latin
        } else {
            ScriptKind::Common
        };
    }
    match cp {
        0x00C0..=0x024F | 0x1E00..=0x1EFF => ScriptKind::Latin,
        0x0370..=0x03FF | 0x1F00..=0x1FFF => ScriptKind::Greek,
        0x0400..=0x052F => ScriptKind::Cyrillic,
        0x0530..=0x058F => ScriptKind::Armenian,
        0x0590..=0x05FF => ScriptKind::Hebrew,
        0x0600..=0x06FF | 0x0750..=0x077F | 0x08A0..=0x08FF => ScriptKind::Arabic,
        0x0900..=0x097F => ScriptKind::Devanagari,
        0x0E00..=0x0E7F => ScriptKind::Thai,
        0x10A0..=0x10FF | 0x1C90..=0x1CBF => ScriptKind::Georgian,
        0x1100..=0x11FF | 0x3130..=0x318F | 0xA960..=0xA97F | 0xAC00..=0xD7AF => ScriptKind::Hangul,
        0x3040..=0x309F => ScriptKind::Hiragana,
        0x30A0..=0x30FF | 0x31F0..=0x31FF | 0xFF65..=0xFF9F => ScriptKind::Katakana,
        0x4E00..=0x9FFF | 0x3400..=0x4DBF | 0xF900..=0xFAFF => ScriptKind::Han,
        0x2000..=0x206F | 0x3000..=0x303F | 0xFF00..=0xFF64 => ScriptKind::Common,
        _ if !ch.is_alphabetic() => ScriptKind::Common,
        _ => ScriptKind::Other,
    }
}

/// Counts non-common script occurrences across input text.
#[derive(Debug, Default, Clone, Copy)]
pub struct ScriptCounts {
    pub latin: usize,
    pub han: usize,
    pub hiragana: usize,
    pub katakana: usize,
    pub hangul: usize,
    pub devanagari: usize,
    pub cyrillic: usize,
    pub arabic: usize,
    pub thai: usize,
    pub hebrew: usize,
    pub greek: usize,
    pub armenian: usize,
    pub georgian: usize,
    pub other: usize,
}

impl ScriptCounts {
    pub fn total_letters(&self) -> usize {
        self.latin
            + self.han
            + self.hiragana
            + self.katakana
            + self.hangul
            + self.devanagari
            + self.cyrillic
            + self.arabic
            + self.thai
            + self.hebrew
            + self.greek
            + self.armenian
            + self.georgian
            + self.other
    }
}

pub fn count_scripts(text: &str) -> ScriptCounts {
    let mut counts = ScriptCounts::default();
    for ch in text.chars() {
        match classify_char(ch) {
            ScriptKind::Latin => counts.latin += 1,
            ScriptKind::Han => counts.han += 1,
            ScriptKind::Hiragana => counts.hiragana += 1,
            ScriptKind::Katakana => counts.katakana += 1,
            ScriptKind::Hangul => counts.hangul += 1,
            ScriptKind::Devanagari => counts.devanagari += 1,
            ScriptKind::Cyrillic => counts.cyrillic += 1,
            ScriptKind::Arabic => counts.arabic += 1,
            ScriptKind::Thai => counts.thai += 1,
            ScriptKind::Hebrew => counts.hebrew += 1,
            ScriptKind::Greek => counts.greek += 1,
            ScriptKind::Armenian => counts.armenian += 1,
            ScriptKind::Georgian => counts.georgian += 1,
            ScriptKind::Other => counts.other += 1,
            ScriptKind::Common => {}
        }
    }
    counts
}

pub fn looks_japanese(text: &str) -> bool {
    let counts = count_scripts(text);
    (counts.hiragana + counts.katakana) > 0
}

pub fn looks_korean(text: &str) -> bool {
    count_scripts(text).hangul > 0
}

pub fn looks_chinese(text: &str) -> bool {
    let counts = count_scripts(text);
    counts.han > 0 && counts.hiragana == 0 && counts.katakana == 0 && counts.hangul == 0
}

pub fn looks_hindi(text: &str) -> bool {
    count_scripts(text).devanagari > 0
}

/// Detects the dominant ISO language tag used by ArchiveTune's romanization pipeline.
pub fn detect_dominant_language(text: &str) -> &'static str {
    let counts = count_scripts(text);
    let total = counts.total_letters();
    if total == 0 {
        return "other";
    }
    if counts.hiragana + counts.katakana > 0 {
        return "ja";
    }
    if counts.hangul > 0 {
        return "ko";
    }
    if counts.han > 0 {
        return "zh";
    }
    if counts.devanagari > 0 {
        return "hi";
    }
    let candidates = [
        ("ru", counts.cyrillic),
        ("ar", counts.arabic),
        ("th", counts.thai),
        ("he", counts.hebrew),
        ("el", counts.greek),
        ("hy", counts.armenian),
        ("ka", counts.georgian),
        ("en", counts.latin),
    ];
    candidates
        .into_iter()
        .max_by_key(|&(_, count)| count)
        .filter(|&(_, count)| count > 0)
        .map(|(code, _)| code)
        .unwrap_or("other")
}

/// Checks if a single line contains characters requiring secondary ("other" / ICU-style)
/// romanization when the dominant lyric language is known.
pub fn needs_other_romanization(line: &str, dominant_lang: &str) -> bool {
    let script_allowed = |kind: ScriptKind| -> bool {
        if matches!(kind, ScriptKind::Latin | ScriptKind::Common) {
            return true;
        }
        match dominant_lang {
            "ja" => matches!(
                kind,
                ScriptKind::Han | ScriptKind::Hiragana | ScriptKind::Katakana
            ),
            "ko" => matches!(kind, ScriptKind::Hangul),
            "zh" => matches!(kind, ScriptKind::Han),
            "hi" => matches!(kind, ScriptKind::Devanagari),
            "ar" => matches!(kind, ScriptKind::Arabic),
            "ru" => matches!(kind, ScriptKind::Cyrillic),
            "th" => matches!(kind, ScriptKind::Thai),
            "he" => matches!(kind, ScriptKind::Hebrew),
            "el" => matches!(kind, ScriptKind::Greek),
            "hy" => matches!(kind, ScriptKind::Armenian),
            "ka" => matches!(kind, ScriptKind::Georgian),
            _ => false,
        }
    };
    let excluded_script = |kind: ScriptKind| -> bool {
        matches!(
            kind,
            ScriptKind::Latin
                | ScriptKind::Common
                | ScriptKind::Han
                | ScriptKind::Hiragana
                | ScriptKind::Katakana
                | ScriptKind::Hangul
                | ScriptKind::Devanagari
        )
    };
    line.chars().any(|ch| {
        let kind = classify_char(ch);
        !script_allowed(kind) && !excluded_script(kind)
    })
}

/// Converts Hiragana codepoints (`\u{3041}..=\u{3096}`) to their Katakana counterparts.
pub fn hiragana_to_katakana(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for ch in text.chars() {
        let cp = ch as u32;
        if (0x3041..=0x3096).contains(&cp) {
            if let Some(mapped) = char::from_u32(cp + 0x60) {
                out.push(mapped);
                continue;
            }
        } else if ch == 'ゝ' {
            out.push('ヽ');
            continue;
        } else if ch == 'ゞ' {
            out.push('ヾ');
            continue;
        }
        out.push(ch);
    }
    out
}

fn lookup_two_char_kana(a: char, b: char) -> Option<&'static str> {
    match (a, b) {
        ('キ', 'ャ') => Some("kya"),
        ('キ', 'ュ') => Some("kyu"),
        ('キ', 'ョ') => Some("kyo"),
        ('シ', 'ャ') => Some("sha"),
        ('シ', 'ュ') => Some("shu"),
        ('シ', 'ョ') => Some("sho"),
        ('シ', 'ェ') => Some("she"),
        ('チ', 'ャ') => Some("cha"),
        ('チ', 'ュ') => Some("chu"),
        ('チ', 'ョ') => Some("cho"),
        ('チ', 'ェ') => Some("che"),
        ('ニ', 'ャ') => Some("nya"),
        ('ニ', 'ュ') => Some("nyu"),
        ('ニ', 'ョ') => Some("nyo"),
        ('ニ', 'ェ') => Some("nye"),
        ('ヒ', 'ャ') => Some("hya"),
        ('ヒ', 'ュ') => Some("hyu"),
        ('ヒ', 'ョ') => Some("hyo"),
        ('ヒ', 'ェ') => Some("hye"),
        ('ミ', 'ャ') => Some("mya"),
        ('ミ', 'ュ') => Some("myu"),
        ('ミ', 'ョ') => Some("myo"),
        ('リ', 'ャ') => Some("rya"),
        ('リ', 'ュ') => Some("ryu"),
        ('リ', 'ョ') => Some("ryo"),
        ('ギ', 'ャ') => Some("gya"),
        ('ギ', 'ュ') => Some("gyu"),
        ('ギ', 'ョ') => Some("gyo"),
        ('ジ', 'ャ') => Some("ja"),
        ('ジ', 'ュ') => Some("ju"),
        ('ジ', 'ョ') => Some("jo"),
        ('ジ', 'ェ') => Some("je"),
        ('ヂ', 'ャ') => Some("ja"),
        ('ヂ', 'ュ') => Some("ju"),
        ('ヂ', 'ョ') => Some("jo"),
        ('ビ', 'ャ') => Some("bya"),
        ('ビ', 'ュ') => Some("byu"),
        ('ビ', 'ョ') => Some("byo"),
        ('ピ', 'ャ') => Some("pya"),
        ('ピ', 'ュ') => Some("pyu"),
        ('ピ', 'ョ') => Some("pyo"),
        ('テ', 'ィ') => Some("ti"),
        ('テ', 'ュ') => Some("tyu"),
        ('デ', 'ィ') => Some("di"),
        ('デ', 'ュ') => Some("dyu"),
        ('ト', 'ゥ') => Some("tu"),
        ('ド', 'ゥ') => Some("du"),
        ('フ', 'ァ') => Some("fa"),
        ('フ', 'ィ') => Some("fi"),
        ('フ', 'ェ') => Some("fe"),
        ('フ', 'ォ') => Some("fo"),
        ('フ', 'ュ') => Some("fyu"),
        ('ウ', 'ィ') => Some("wi"),
        ('ウ', 'ェ') => Some("we"),
        ('ウ', 'ォ') => Some("wo"),
        ('ヴ', 'ァ') => Some("va"),
        ('ヴ', 'ィ') => Some("vi"),
        ('ヴ', 'ェ') => Some("ve"),
        ('ヴ', 'ォ') => Some("vo"),
        ('ヴ', 'ュ') => Some("vyu"),
        ('ツ', 'ァ') => Some("tsa"),
        ('ツ', 'ィ') => Some("tsi"),
        ('ツ', 'ェ') => Some("tse"),
        ('ツ', 'ォ') => Some("tso"),
        ('ス', 'ィ') => Some("si"),
        ('ズ', 'ィ') => Some("zi"),
        ('イ', 'ェ') => Some("ye"),
        ('キ', 'ェ') => Some("kye"),
        ('グ', 'ァ') => Some("gwa"),
        ('ク', 'ァ') => Some("kwa"),
        ('ク', 'ィ') => Some("kwi"),
        ('ク', 'ェ') => Some("kwe"),
        ('ク', 'ォ') => Some("kwo"),
        _ => None,
    }
}

fn lookup_one_char_kana(ch: char) -> Option<&'static str> {
    match ch {
        'ア' | 'ァ' => Some("a"),
        'イ' | 'ィ' => Some("i"),
        'ウ' | 'ゥ' => Some("u"),
        'エ' | 'ェ' => Some("e"),
        'オ' | 'ォ' => Some("o"),
        'カ' | 'ヵ' => Some("ka"),
        'キ' => Some("ki"),
        'ク' => Some("ku"),
        'ケ' | 'ヶ' => Some("ke"),
        'コ' => Some("ko"),
        'サ' => Some("sa"),
        'シ' => Some("shi"),
        'ス' => Some("su"),
        'セ' => Some("se"),
        'ソ' => Some("so"),
        'タ' => Some("ta"),
        'チ' => Some("chi"),
        'ツ' => Some("tsu"),
        'テ' => Some("te"),
        'ト' => Some("to"),
        'ナ' => Some("na"),
        'ニ' => Some("ni"),
        'ヌ' => Some("nu"),
        'ネ' => Some("ne"),
        'ノ' => Some("no"),
        'ハ' => Some("ha"),
        'ヒ' => Some("hi"),
        'フ' => Some("fu"),
        'ヘ' => Some("he"),
        'ホ' => Some("ho"),
        'マ' => Some("ma"),
        'ミ' => Some("mi"),
        'ム' => Some("mu"),
        'メ' => Some("me"),
        'モ' => Some("mo"),
        'ヤ' | 'ャ' => Some("ya"),
        'ユ' | 'ュ' => Some("yu"),
        'ヨ' | 'ョ' => Some("yo"),
        'ラ' => Some("ra"),
        'リ' => Some("ri"),
        'ル' => Some("ru"),
        'レ' => Some("re"),
        'ロ' => Some("ro"),
        'ワ' | 'ヮ' => Some("wa"),
        'ヲ' => Some("wo"),
        'ン' => Some("n"),
        'ガ' => Some("ga"),
        'ギ' => Some("gi"),
        'グ' => Some("gu"),
        'ゲ' => Some("ge"),
        'ゴ' => Some("go"),
        'ザ' => Some("za"),
        'ジ' => Some("ji"),
        'ズ' => Some("zu"),
        'ゼ' => Some("ze"),
        'ゾ' => Some("zo"),
        'ダ' => Some("da"),
        'ヂ' => Some("ji"),
        'ヅ' => Some("zu"),
        'デ' => Some("de"),
        'ド' => Some("do"),
        'バ' => Some("ba"),
        'ビ' => Some("bi"),
        'ブ' => Some("bu"),
        'ベ' => Some("be"),
        'ボ' => Some("bo"),
        'パ' => Some("pa"),
        'ピ' => Some("pi"),
        'プ' => Some("pu"),
        'ペ' => Some("pe"),
        'ポ' => Some("po"),
        'ヴ' => Some("vu"),
        'ヰ' => Some("wi"),
        'ヱ' => Some("we"),
        '・' | '　' => Some(" "),
        _ => None,
    }
}

/// Converts Katakana (with optional lookahead token for terminal small-tsu `ッ`) to Hepburn Romaji.
pub fn katakana_to_romaji(katakana: &str, next_katakana: Option<&str>) -> String {
    if katakana.is_empty() {
        return String::new();
    }
    let chars: Vec<char> = katakana.chars().collect();
    let mut out = String::with_capacity(katakana.len());
    let mut i = 0;

    while i < chars.len() {
        if i + 1 < chars.len()
            && let Some(digraph) = lookup_two_char_kana(chars[i], chars[i + 1])
        {
            out.push_str(digraph);
            i += 2;
            continue;
        }

        let current = chars[i];
        if current == 'ッ' || current == 'っ' {
            let next_char = chars
                .get(i + 1)
                .copied()
                .or_else(|| next_katakana.and_then(|s| s.chars().next()));
            if let Some(nc) = next_char {
                let nc_kata = if ('\u{3041}'..='\u{3096}').contains(&nc) {
                    char::from_u32((nc as u32) + 0x60).unwrap_or(nc)
                } else {
                    nc
                };
                let next_rom = lookup_one_char_kana(nc_kata);
                if let Some(rom) = next_rom {
                    if let Some(first) = rom.chars().next()
                        && first.is_ascii_alphabetic()
                    {
                        if rom.starts_with("ch") {
                            out.push('t');
                        } else {
                            out.push(first.to_ascii_lowercase());
                        }
                    }
                } else if nc.is_ascii_alphabetic() {
                    out.push(nc.to_ascii_lowercase());
                }
            }
            i += 1;
            continue;
        }

        if current == 'ー' {
            if let Some(last) = out.chars().last()
                && matches!(last, 'a' | 'i' | 'u' | 'e' | 'o')
            {
                out.push(last);
            }
            i += 1;
            continue;
        }

        // Convert stray hiragana on the fly in case non-normalized text was passed directly.
        let normalized = if ('\u{3041}'..='\u{3096}').contains(&current) {
            char::from_u32((current as u32) + 0x60).unwrap_or(current)
        } else {
            current
        };
        if let Some(rom) = lookup_one_char_kana(normalized) {
            out.push_str(rom);
        } else {
            for lc in current.to_lowercase() {
                out.push(lc);
            }
        }
        i += 1;
    }

    out
}

const HANGUL_INITIALS: [&str; 19] = [
    "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p",
    "h",
];

const HANGUL_MEDIALS: [&str; 21] = [
    "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we",
    "wi", "yu", "eu", "ui", "i",
];

const HANGUL_FINALS: [&str; 28] = [
    "", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l", "l", "l", "l", "l", "m", "p", "p",
    "t", "t", "ng", "t", "t", "k", "t", "p", "t",
];

const INITIAL_JAMO_CHARS: [char; 19] = [
    'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ',
    'ㅌ', 'ㅍ', 'ㅎ',
];

const FINAL_JAMO_CHARS: [char; 28] = [
    '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ',
    'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
];

#[derive(Clone, Copy)]
struct HangulParts {
    initial_idx: usize,
    medial_idx: usize,
    final_idx: usize,
}

fn decompose_hangul(ch: char) -> Option<HangulParts> {
    let cp = ch as u32;
    if !(0xAC00..=0xD7A3).contains(&cp) {
        return None;
    }
    let idx = (cp - 0xAC00) as usize;
    Some(HangulParts {
        initial_idx: idx / (21 * 28),
        medial_idx: (idx / 28) % 21,
        final_idx: idx % 28,
    })
}

fn assimilate_hangul_boundary(
    coda_idx: usize,
    next_onset_idx: usize,
) -> (&'static str, Option<&'static str>) {
    let coda_jamo = FINAL_JAMO_CHARS[coda_idx];
    let onset_jamo = INITIAL_JAMO_CHARS[next_onset_idx];

    match (coda_jamo, onset_jamo) {
        // Liaison when followed by silent placeholder 'ㅇ'
        ('ㄱ', 'ㅇ') => ("", Some("g")),
        ('ㄲ', 'ㅇ') => ("", Some("kk")),
        ('ㄳ', 'ㅇ') => ("k", Some("s")),
        ('ㄴ', 'ㅇ') => ("", Some("n")),
        ('ㄵ', 'ㅇ') => ("n", Some("j")),
        ('ㄶ', 'ㅇ') => ("n", Some("")),
        ('ㄷ', 'ㅇ') => ("", Some("d")),
        ('ㄹ', 'ㅇ') => ("", Some("r")),
        ('ㄺ', 'ㅇ') => ("l", Some("g")),
        ('ㄻ', 'ㅇ') => ("l", Some("m")),
        ('ㄼ', 'ㅇ') => ("l", Some("b")),
        ('ㄽ', 'ㅇ') => ("l", Some("s")),
        ('ㄾ', 'ㅇ') => ("l", Some("t")),
        ('ㄿ', 'ㅇ') => ("l", Some("p")),
        ('ㅀ', 'ㅇ') => ("l", Some("")),
        ('ㅁ', 'ㅇ') => ("", Some("m")),
        ('ㅂ', 'ㅇ') => ("", Some("b")),
        ('ㅄ', 'ㅇ') => ("p", Some("s")),
        ('ㅅ', 'ㅇ') => ("", Some("s")),
        ('ㅆ', 'ㅇ') => ("", Some("ss")),
        ('ㅈ', 'ㅇ') => ("", Some("j")),
        ('ㅊ', 'ㅇ') => ("", Some("ch")),
        ('ㅋ', 'ㅇ') => ("", Some("k")),
        ('ㅌ', 'ㅇ') => ("", Some("t")),
        ('ㅍ', 'ㅇ') => ("", Some("p")),
        ('ㅎ', 'ㅇ') => ("", Some("")),

        // Nasalization before ㄴ / ㅁ
        ('ㄱ' | 'ㄲ' | 'ㅋ', 'ㄴ' | 'ㅁ') => ("ng", None),
        ('ㄷ' | 'ㅅ' | 'ㅆ' | 'ㅈ' | 'ㅊ' | 'ㅌ' | 'ㅎ', 'ㄴ' | 'ㅁ') => ("n", None),
        ('ㅂ' | 'ㅍ', 'ㄴ' | 'ㅁ') => ("m", None),

        // Liquid assimilation
        ('ㄴ', 'ㄹ') | ('ㄹ', 'ㄴ') | ('ㄹ', 'ㄹ') => ("l", Some("l")),
        ('ㄱ' | 'ㅋ', 'ㄹ') => ("ng", Some("n")),
        ('ㅁ' | 'ㅇ', 'ㄹ') => ("m", Some("n")),
        ('ㅂ' | 'ㅍ', 'ㄹ') => ("m", Some("n")),

        // Aspiration with ㅎ
        ('ㅎ', 'ㄱ') | ('ㄱ', 'ㅎ') => ("", Some("k")),
        ('ㅎ', 'ㄷ') | ('ㄷ', 'ㅎ') => ("", Some("t")),
        ('ㅎ', 'ㅂ') | ('ㅂ', 'ㅎ') => ("", Some("p")),
        ('ㅎ', 'ㅈ') | ('ㅈ', 'ㅎ') => ("", Some("ch")),

        _ => (HANGUL_FINALS[coda_idx], None),
    }
}

/// Romanizes Korean Hangul syllables according to Revised Romanization of Korean
/// with standard syllable-boundary phonological rules (liaison, nasalization, liquid
/// assimilation, and h-aspiration).
pub fn romanize_korean(text: &str) -> String {
    let chars: Vec<char> = text.chars().collect();
    let mut out = String::with_capacity(text.len() * 2);
    let mut forced_next_onset: Option<&'static str> = None;

    for (idx, &ch) in chars.iter().enumerate() {
        if let Some(parts) = decompose_hangul(ch) {
            let onset = forced_next_onset
                .take()
                .unwrap_or(HANGUL_INITIALS[parts.initial_idx]);
            out.push_str(onset);
            out.push_str(HANGUL_MEDIALS[parts.medial_idx]);

            if parts.final_idx != 0 {
                let next_parts = chars.get(idx + 1).copied().and_then(decompose_hangul);
                if let Some(np) = next_parts {
                    let (coda, next_onset) =
                        assimilate_hangul_boundary(parts.final_idx, np.initial_idx);
                    out.push_str(coda);
                    forced_next_onset = next_onset;
                } else {
                    out.push_str(HANGUL_FINALS[parts.final_idx]);
                }
            }
        } else {
            forced_next_onset = None;
            if let Some(compat) = romanize_compat_jamo(ch) {
                out.push_str(compat);
            } else {
                out.push(ch);
            }
        }
    }

    out
}

fn romanize_compat_jamo(ch: char) -> Option<&'static str> {
    match ch {
        'ㄱ' => Some("g"),
        'ㄲ' => Some("kk"),
        'ㄴ' => Some("n"),
        'ㄷ' => Some("d"),
        'ㄸ' => Some("tt"),
        'ㄹ' => Some("r"),
        'ㅁ' => Some("m"),
        'ㅂ' => Some("b"),
        'ㅃ' => Some("pp"),
        'ㅅ' => Some("s"),
        'ㅆ' => Some("ss"),
        'ㅇ' => Some(""),
        'ㅈ' => Some("j"),
        'ㅉ' => Some("jj"),
        'ㅊ' => Some("ch"),
        'ㅋ' => Some("k"),
        'ㅌ' => Some("t"),
        'ㅍ' => Some("p"),
        'ㅎ' => Some("h"),
        'ㅏ' => Some("a"),
        'ㅐ' => Some("ae"),
        'ㅑ' => Some("ya"),
        'ㅒ' => Some("yae"),
        'ㅓ' => Some("eo"),
        'ㅔ' => Some("e"),
        'ㅕ' => Some("yeo"),
        'ㅖ' => Some("ye"),
        'ㅗ' => Some("o"),
        'ㅘ' => Some("wa"),
        'ㅙ' => Some("wae"),
        'ㅚ' => Some("oe"),
        'ㅛ' => Some("yo"),
        'ㅜ' => Some("u"),
        'ㅝ' => Some("wo"),
        'ㅞ' => Some("we"),
        'ㅟ' => Some("wi"),
        'ㅠ' => Some("yu"),
        'ㅡ' => Some("eu"),
        'ㅢ' => Some("ui"),
        'ㅣ' => Some("i"),
        _ => None,
    }
}

fn devanagari_independent_vowel(ch: char) -> Option<&'static str> {
    match ch {
        'अ' => Some("a"),
        'आ' => Some("aa"),
        'इ' => Some("i"),
        'ई' => Some("ee"),
        'उ' => Some("u"),
        'ऊ' => Some("oo"),
        'ऋ' => Some("ri"),
        'ॠ' => Some("rii"),
        'ऌ' => Some("lri"),
        'ॡ' => Some("lrii"),
        'ए' => Some("e"),
        'ऐ' => Some("ai"),
        'ओ' => Some("o"),
        'औ' => Some("au"),
        'ऑ' => Some("o"),
        'ॲ' | 'ऍ' => Some("ae"),
        'ॐ' => Some("om"),
        _ => None,
    }
}

fn devanagari_consonant(ch: char) -> Option<&'static str> {
    match ch {
        'क' => Some("k"),
        'ख' => Some("kh"),
        'ग' => Some("g"),
        'घ' => Some("gh"),
        'ङ' => Some("ng"),
        'च' => Some("ch"),
        'छ' => Some("chh"),
        'ज' => Some("j"),
        'झ' => Some("jh"),
        'ञ' => Some("ny"),
        'ट' => Some("t"),
        'ठ' => Some("th"),
        'ड' => Some("d"),
        'ढ' => Some("dh"),
        'ण' => Some("n"),
        'त' => Some("t"),
        'थ' => Some("th"),
        'द' => Some("d"),
        'ध' => Some("dh"),
        'न' => Some("n"),
        'प' => Some("p"),
        'फ' => Some("ph"),
        'ब' => Some("b"),
        'भ' => Some("bh"),
        'म' => Some("m"),
        'य' => Some("y"),
        'र' => Some("r"),
        'ल' => Some("l"),
        'व' => Some("v"),
        'श' => Some("sh"),
        'ष' => Some("sh"),
        'स' => Some("s"),
        'ह' => Some("h"),
        'ळ' => Some("l"),
        '\u{0958}' => Some("q"),
        '\u{0959}' => Some("kh"),
        '\u{095A}' => Some("gh"),
        '\u{095B}' => Some("z"),
        '\u{095C}' => Some("r"),
        '\u{095D}' => Some("rh"),
        '\u{095E}' => Some("f"),
        '\u{095F}' => Some("y"),
        _ => None,
    }
}

fn devanagari_matra(ch: char) -> Option<&'static str> {
    match ch {
        'ा' => Some("aa"),
        'ि' => Some("i"),
        'ी' => Some("ee"),
        'ु' => Some("u"),
        'ू' => Some("oo"),
        'ृ' => Some("ri"),
        'ॄ' => Some("rii"),
        'ॢ' => Some("lri"),
        'ॣ' => Some("lrii"),
        'े' => Some("e"),
        'ै' => Some("ai"),
        'ो' => Some("o"),
        'ौ' => Some("au"),
        'ॉ' => Some("o"),
        'ॅ' => Some("ae"),
        _ => None,
    }
}

/// Romanizes Hindi / Devanagari text into clean Hinglish phonetics.
pub fn romanize_hindi(text: &str) -> String {
    let chars: Vec<char> = text.chars().collect();
    let mut out = String::with_capacity(text.len() * 2);
    let mut i = 0;

    while i < chars.len() {
        let ch = chars[i];
        if let Some(cons_base) = devanagari_consonant(ch) {
            // Check nukta sign (\u{093C}) attached after base consonant
            let (cons, next_idx) = if chars.get(i + 1) == Some(&'\u{093C}') {
                let nukta_cons = match ch {
                    'क' => "q",
                    'ख' => "kh",
                    'ग' => "gh",
                    'ज' => "z",
                    'ड' => "r",
                    'ढ' => "rh",
                    'फ' => "f",
                    _ => cons_base,
                };
                (nukta_cons, i + 2)
            } else {
                (cons_base, i + 1)
            };
            out.push_str(cons);

            match chars.get(next_idx).copied() {
                Some('\u{094D}') => {
                    // Virama / halant: suppress inherent 'a'
                    i = next_idx + 1;
                }
                Some(matra_ch) if devanagari_matra(matra_ch).is_some() => {
                    if let Some(matra) = devanagari_matra(matra_ch) {
                        out.push_str(matra);
                    }
                    i = next_idx + 1;
                }
                Some(next_ch) => {
                    // Inherent 'a' suppressed at end of word unless preceded by conjunct
                    let word_continues = devanagari_consonant(next_ch).is_some()
                        || devanagari_independent_vowel(next_ch).is_some()
                        || matches!(next_ch, 'ं' | 'ँ' | 'ः');
                    if word_continues {
                        out.push('a');
                    }
                    i = next_idx;
                }
                None => {
                    i = next_idx;
                }
            }
            continue;
        }

        if let Some(vowel) = devanagari_independent_vowel(ch) {
            out.push_str(vowel);
            i += 1;
            continue;
        }

        match ch {
            'ं' => {
                // Anusvara assimilates to following consonant place of articulation when possible
                let nasal = match chars.get(i + 1).copied() {
                    Some('प' | 'फ' | 'ब' | 'भ' | 'म') => "m",
                    _ => "n",
                };
                out.push_str(nasal);
            }
            'ँ' => out.push('n'),
            'ः' => out.push('h'),
            'ऽ' => out.push('\''),
            '।' | '॥' => out.push('.'),
            '०'..='९' => {
                let d = char::from_u32(('0' as u32) + (ch as u32 - '०' as u32)).unwrap_or(ch);
                out.push(d);
            }
            '\u{093C}' | '\u{094D}' => {}
            _ => out.push(ch),
        }
        i += 1;
    }

    out
}

/// Romanizes Russian/Ukrainian/Bulgarian/Serbian Cyrillic script to Latin.
pub fn romanize_cyrillic(text: &str) -> String {
    let mut out = String::with_capacity(text.len() * 2);
    for ch in text.chars() {
        let lower = ch.to_lowercase().next().unwrap_or(ch);
        let mapped = match lower {
            'а' => "a",
            'б' => "b",
            'в' => "v",
            'г' => "g",
            'ґ' => "g",
            'д' => "d",
            'е' => "e",
            'ё' => "yo",
            'є' => "ye",
            'ж' => "zh",
            'з' => "z",
            'и' => "i",
            'і' => "i",
            'ї' => "yi",
            'й' => "y",
            'к' => "k",
            'л' => "l",
            'м' => "m",
            'н' => "n",
            'о' => "o",
            'п' => "p",
            'р' => "r",
            'с' => "s",
            'т' => "t",
            'у' => "u",
            'ф' => "f",
            'х' => "kh",
            'ц' => "ts",
            'ч' => "ch",
            'ш' => "sh",
            'щ' => "shch",
            'ъ' | 'ь' => "",
            'ы' => "y",
            'э' => "e",
            'ю' => "yu",
            'я' => "ya",
            'ђ' => "dj",
            'ј' => "j",
            'љ' => "lj",
            'њ' => "nj",
            'ћ' => "c",
            'џ' => "dz",
            _ => {
                out.push(ch);
                continue;
            }
        };
        if ch.is_uppercase() && !mapped.is_empty() {
            let mut mchars = mapped.chars();
            if let Some(first) = mchars.next() {
                for up in first.to_uppercase() {
                    out.push(up);
                }
                out.push_str(mchars.as_str());
            }
        } else {
            out.push_str(mapped);
        }
    }
    out
}

/// Romanizes Greek script to readable Latin transliteration.
pub fn romanize_greek(text: &str) -> String {
    let mut out = String::with_capacity(text.len() * 2);
    for ch in text.chars() {
        let lower = ch.to_lowercase().next().unwrap_or(ch);
        let mapped = match lower {
            'α' | 'ά' => "a",
            'β' => "v",
            'γ' => "g",
            'δ' => "d",
            'ε' | 'έ' => "e",
            'ζ' => "z",
            'η' | 'ή' => "i",
            'θ' => "th",
            'ι' | 'ί' | 'ϊ' | 'ΐ' => "i",
            'κ' => "k",
            'λ' => "l",
            'μ' => "m",
            'ν' => "n",
            'ξ' => "x",
            'ο' | 'ό' => "o",
            'π' => "p",
            'ρ' => "r",
            'σ' | 'ς' => "s",
            'τ' => "t",
            'υ' | 'ύ' | 'ϋ' | 'ΰ' => "y",
            'φ' => "f",
            'χ' => "ch",
            'ψ' => "ps",
            'ω' | 'ώ' => "o",
            _ => {
                out.push(ch);
                continue;
            }
        };
        out.push_str(mapped);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn katakana_handles_digraphs_sokuon_and_choonpu() {
        assert_eq!(hiragana_to_katakana("しゃしん"), "シャシン");
        assert_eq!(katakana_to_romaji("シャシン", None), "shashin");
        assert_eq!(katakana_to_romaji("キップ", None), "kippu");
        assert_eq!(katakana_to_romaji("ヤッ", Some("パ")), "yap");
        assert_eq!(katakana_to_romaji("カー", None), "kaa");
    }

    #[test]
    fn korean_applies_liaison_and_nasalization() {
        assert_eq!(romanize_korean("사랑해"), "saranghae");
        assert_eq!(romanize_korean("음악"), "eumak");
        assert_eq!(romanize_korean("한국어"), "hangugeo");
        assert_eq!(romanize_korean("신라"), "silla");
    }

    #[test]
    fn hindi_handles_matras_halant_and_nukta() {
        assert_eq!(romanize_hindi("नमस्ते"), "namaste");
        assert_eq!(romanize_hindi("फ़िर"), "fir");
        assert_eq!(romanize_hindi("संगीत"), "sangeet");
    }

    #[test]
    fn cyrillic_and_script_detection_work() {
        assert_eq!(detect_dominant_language("Привет мир"), "ru");
        assert_eq!(detect_dominant_language("こんにちは世界"), "ja");
        assert_eq!(detect_dominant_language("안녕하세요"), "ko");
        assert_eq!(detect_dominant_language("你好世界"), "zh");
        assert_eq!(romanize_cyrillic("Привет, мир!"), "Privet, mir!");
        assert!(!needs_other_romanization("Привет!", "ru"));
        assert!(needs_other_romanization("Привет!", "en"));
    }
}
