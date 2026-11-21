# Rust migration progress

## Phase 1 — AutoMix magnitude spectrum

- Baseline `158f1bc0cd8aa0e281e055574a48ba3cb5f69ae3` from `origin/main`, ArchiveTune 16.5.6 (`versionCode=162`), Android API 26 minimum for mobile, API 28 for automotive, compile SDK 37.2.
- `app/native/automix` already computed spectra through optional JNI at baseline. Phase 1 turns it into the sole FFT backend in Android builds and exposes a safe Rust `Spectrum` API as an `rlib` for desktop use.
- One production component switched to Rust across all app variants. Removed production Kotlin `Fft` (plan setup and `magnitudeSpectrum`) and its fallback state/dispatch from `AutoMixSpectrum`. The existing `AutoMixDsp.kt` still implements the Hann window and robust averaging. No new crate or dependency was needed.
- Runtime path `AutoMixAnalyzer.analyze → decodeAndExtract → AutoMixSpectrum.magnitudeSpectrum → JNI → Spectrum`. Analysis runs on `Dispatchers.Default`. Both 512- and 1024-sample windows keep the same bin count and ordering. The plan and scratch buffers live for one decoder invocation. The Kotlin owner serializes calculation and release. The private JNI bridge validates array lengths and catches unwinding panics. The caller saves overlap before FFT and restores it afterward. R8 retains the bridge names via `@Keep`.

## Build and verification

Install JDK 21, Android SDK platform `android-37.2`, and side-by-side NDK `30.0.16248370`. CI pins Rust 1.100.0. The Gradle build uses Cargo with `--locked`.

```sh
sdkmanager 'platforms;android-37.2' 'ndk;30.0.16248370'
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
./gradlew :app:testGmsMobileUniversalDebugUnitTest :app:assembleGmsMobileUniversalDebug
./gradlew :app:assembleGmsMobileUniversalRelease
./gradlew :app:assembleGmsMobileArm64Release
./gradlew :app:connectedGmsMobileUniversalDebugAndroidTest
(cd app/native/automix && cargo fmt --check && cargo clippy --locked --all-targets -- -D warnings && cargo test --locked)
```

The APK tasks build all four Rust ABI outputs once. Android packaging selects the applicable ABI filters. `-PautoMixNdk=/path/to/ndk` can select a standalone installation. JVM tests build and load a separate host JNI library. The JVM parity fixture captures 4,608 bins from the baseline Kotlin implementation for silence, an offset impulse, DC, Nyquist, a tone and seeded noise. Tests also exercise concurrent use, invalid lengths and double close. Rust tests independently compare against a direct DFT. The Android test calls the packaged library for both sizes.

| Check | Result |
| --- | --- |
| Baseline JVM tests | 180 passed, 1 failed. `ModifiedSettingsRegistryTest` expected the original appearance route. |
| Baseline Universal Debug | Assembled after limiting parallelism and raising the Kotlin compiler heap. The first multi-variant build exceeded the default heap. |
| Phase 1 JVM tests | 182 passed, 1 failed with the identical pre-existing settings assertion. All 3 AutoMix JNI tests passed in a targeted Gradle run and a standalone HotSpot run with `-Xcheck:jni`. |
| Cargo | Formatting, Clippy with warnings denied, and all 4 unit tests passed. |
| NDK / Universal Debug APK | Built and packaged `libarchivetune_automix.so` for arm64-v8a, armeabi-v7a, x86 and x86_64. APK signature and 16 KiB ZIP alignment verified. A repeat Gradle build produced a byte-identical APK. The four generated libraries matched the independent NDK cross-build byte for byte. |
| Android instrumentation | 1 passed on Galaxy S21 / Android 15. Both FFT sizes ran through JNI from the APK. The test used a temporary application ID to protect existing installations. |
| Release APKs | Universal and ARM64 Release built with R8 and passed lintVital. APK signatures, ZIP alignment, native ABIs, and JNI exports verified. Universal included four ABIs and ARM64 included arm64-v8a. |
| Android Lint | Universal Debug completed. The HTML report lists 323 errors and 2,063 warnings. Lint is configured to continue on errors. No findings point to lines changed by this migration. The existing Conscrypt dependency has a 16 KiB ELF-alignment warning. Both 64-bit AutoMix libraries have 16 KiB-aligned ELF load segments. |
| CI | actionlint passed for five modified workflows. Translation generation/snapshot behavior checked locally. GitHub-hosted jobs were outside the local run. |

On builds that exceed the default compiler heap, use a single build invocation per variant or `--no-parallel --max-workers=4 -Pkotlin.daemon.jvmargs=-Xmx10g`.

## Measurements

Measured on a Galaxy S21 SM-G991B with Android 15 (API 35), arm64, NDK r30/API 26 library built in Cargo release mode. Baseline uses the original Kotlin FFT and clears its imaginary buffer for each call. The comparison uses the new Kotlin bridge and the release Rust library in Android ART via `app_process`. Each of five process runs compares identical precomputed Hann-windowed two-tone/noise PCM, with 24,000 warm-up calls and 20,000 samples per size and implementation in alternating batches. The table reports the median of the five per-run statistics. The corpus used 128 frames with a half-window hop, sine amplitudes 0.4 and 0.25 at 0.021 and 0.093 cycles per sample, and uniform noise at amplitude 0.25 from Kotlin `Random(windowSize)`.

| Samples | Kotlin p50 | Rust/JNI p50 | Kotlin p95 | Rust/JNI p95 | Kotlin CPU/frame | Rust/JNI CPU/frame |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 512 | 8.231 µs | 1.769 µs | 8.462 µs | 2.000 µs | 8.575 µs | 2.117 µs |
| 1024 | 17.500 µs | 2.962 µs | 18.000 µs | 3.462 µs | 19.125 µs | 4.427 µs |

Worst absolute magnitude difference across the benchmark frames was `2.29e-5`. Wall timing starts after input staging and covers the JNI call, both array copies, validation and synchronization. Thread CPU includes the measurement loop and input staging. JNI cost by itself, process memory, and end-to-end playback and UI responsiveness were outside the measurement.

On HotSpot 21 / Intel i5-14600KF, six alternating 20,000-frame blocks showed virtually zero steady-state Java allocations per frame for both implementations (`ThreadMXBean`, excludes native allocations and plan construction). Median p50 was 6.009 → 0.407 µs for 512 and 13.194 → 0.770 µs for 1024. The two platforms used different corpus generators and timing harnesses.

| Metric | Before | After | Change |
| --- | ---: | ---: | ---: |
| Kotlin LOC (including Gradle Kotlin DSL) | 613,444 | 613,543 | +99, including tests and build logic |
| Java LOC | 155,722 | 155,722 | 0 |
| Rust LOC | 93 | 260 | +167 |
| Python LOC | 1,822 | 1,758 | −64 |
| Kotlin share by source bytes | 80.79% | ~80.78% | ~−0.01 pp |
| Java share by source bytes | 18.91% | ~18.90% | ~−0.01 pp |
| Rust share by source bytes | 0.0098% | ~0.0297% | ~+0.0199 pp |
| Python share by source bytes | 0.0528% | ~0.0445% | ~−0.0083 pp |
| Newly completed Rust migrations | 0 | 1 | +1 FFT component |
| Universal Debug APK | 108,811,817 B | 111,650,758 B | +2,838,941 B (+2.61%) |
| Universal Release APK | Baseline attempt exceeded compiler heap | 58,681,368 B | — |
| ARM64 Release APK | Baseline attempt exceeded compiler heap | 47,874,006 B | — |

LOC counts physical lines in tracked files by extension. Production Kotlin in the migrated component fell by 69 lines. The baseline percentages use GitHub’s 27,717,971 counted bytes for the default branch. After percentages estimate the local branch by applying tracked source byte deltas to those counts. The estimates assume unchanged language classification. Rust grew from 2,710 to 8,233 tracked source bytes. [GitHub Languages API](https://api.github.com/repos/S1LOnik/ArchiveTune/languages)

## Python and CI

Removed `scripts/generate_translation_contributors.py`. The replacement uses Git, Bash, and jq. Synthetic histories verified byte-identical JSON and stdout across ordering, filtering, duplicate authors, and Unicode. The fork's baseline Git history contains no qualifying Weblate commit. CI explicitly allows the existing Git-tracked translation snapshot for that case after verifying its contents. Shallow history, invalid snapshots, and tool failures still fail.

Six active Python files remain. The Telegram notifier and deployment tool use requests/Telethon. The duplicate issue checker depends on scikit-learn and requests. Three IconPack scripts drive preview, submission, and removal. Inline workflow Python still handles warning filters, maintenance and IconPack JSON. There were no tracked Python package manifests or Python invocations from Gradle. Removing these tools requires integration tests against their workflows.

## Phase 2 candidates

Each candidate has a standalone core suitable for delegation and a Kotlin adapter for Android integration.

| Priority | Candidate | Expected benefit and desktop suitability | Complexity, dependencies and risk | Parity check | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | `AutoMixAnalyzer.estimateTempo` | Move spectral-flux autocorrelation and beat inference closer to the Rust DSP. High desktop reuse. | Medium. Pass the full flux window once. Preserve BPM bounds, tie handling and confidence. | Synthetic click tracks, half/double-time ambiguity, silence and recorded flux fixtures. | **Completed** (`5f1af708c`) |
| 2 | `TrackMatching.best` | Share catalog matching across streaming sources and desktop. | Medium. Batch candidate metadata. Preserve Locale.US normalization, Unicode decomposition, title qualifiers and input tie order. | Provider fixtures with duration mismatches, Cyrillic, Japanese, diacritics and ties. | **Completed** (`f6efb0bc4`) |
| 3 | `EmbeddedLyricsExtractor` | Reuse tag parsing for desktop file imports. | Medium/high. Start with one ID3 or Vorbis format. Keep Android ContentResolver and stream ownership in Kotlin. Guard malformed offsets and encodings. | Byte-level fixtures, truncated files and encoding variants from local library scans. | **Completed** (`3a76394b3`) |
| 4 | `LyricsUtils.parseLyrics` LRC/QRC/YRC branches | Reuse timed lyric parsing across Android and desktop. | High. Keep TTML and Kotlin UI models separate. Preserve word timing, translation indexing, Unicode and ordering. | Multilingual provider payloads, malformed timestamps and duplicate lines. | **Completed** (`5152a039f`) |
| 5 | `AutoMixPlanner.plan` | Share a deterministic transition planner with the desktop player. | Medium/high. Send feature curves and track state once per transition. Preserve gain and timing decisions. | Existing Smart DJ planner tests and golden feature curves near thresholds. | **Completed** (`d2da256ae`) |

## Phase 2 — Python elimination and Rust expansion to ≥ 1.00%

### Overview and Success Criteria
- Base commit `68a6519e5a04f49a77d4a19de8cdf70f14c6b6` on `migration/rust-phase-1`.
- Working branch `migration/rust-phase-2`.
- **Criterion 1 (Rust share ≥ 1.00%):** Reached **1.0155%** (283,809 bytes of 27,946,814 total programming language bytes) replacing real application and CI tooling logic without artificial filler or library vendoring.
  - Android application Rust: **125,677 bytes** (44.28%) in `app/native/automix/src/*.rs`.
  - CI utility Rust: **158,132 bytes** (55.72%) in `tools/**/*.rs`.
- **Criterion 2 (100% Python elimination):** Zero `.py` files tracked in Git (`git ls-files | grep '\.py$'` returns 0). Zero active/operational invocations of `python`, `python3`, `pip`, or `actions/setup-python` in GitHub Actions workflows or scripts.

### Phase 2 Commit History
1. `5f1af708c`: `refactor(automix): migrate estimateTempo algorithm to Rust` — migrated spectral flux autocorrelation, decimation, and beat candidate inference to `app/native/automix/src/tempo.rs`.
2. `49370cd06`: `build(ci): replace inline Python scripts with repo-maintainer CLI and jq/awk` — created `tools/repo-maintainer` and removed inline Python blocks from `cleanup.yml`, `header.yml`, `build.yml`, `build_pull_request.yml`, `release.yml`, and `IconPack`.
3. `b994269e1`: `build(ci): migrate Telegram notification script to Rust CLI` — replaced `.github/workflows/notify_telegram.py` with `tools/notify-telegram` Rust CLI.
4. `1421de380`: `build(icon-pack): migrate submission and preview scripts to Rust CLI` — consolidated `icon_preview.py`, `process_icon_submission.py`, and `remove_icon_pack.py` into unified `tools/icon-pack` Rust CLI (`icon-pack-tool`).
5. `56ba2500a`: `build(ci): replace issue duplicate checker with Rust implementation` — replaced `duplicate_checker.py` with `tools/duplicate-checker` Rust CLI implementing exact scikit-learn TF-IDF cosine similarity.
6. `2fbe4b607`: `build(ci): migrate artifact deployment to Rust MTProto CLI` — replaced `.github/workflows/deploy_artifacts.py` with `tools/deploy-artifacts` Rust CLI using `grammers-client` MTProto.
7. `f6efb0bc4`: `feat(automix): migrate track matching algorithms to Rust` — implemented catalog matching, Unicode normalization, token overlap, and batch `best_match_index` in `app/native/automix/src/track_matching.rs`.
8. `3a76394b3`: `feat(localmedia): migrate embedded lyrics extraction to Rust` — implemented ID3v2 USLT/SYLT, MP4 ilst `\xa9lyr`, and Vorbis comment parsing in `app/native/automix/src/embedded_lyrics.rs`.
9. `d2da256ae`: `feat(automix): migrate transition planner to Rust` — ported transition planning, curve calculations, beat alignment, and fallback strategy to `app/native/automix/src/planner.rs`.
10. `5152a039f`: `feat(lyrics): migrate LRC, QRC, and timing sync parser to Rust` — implemented timed lyrics parsing, word sync extraction, QRC parsing, and translation merging in `app/native/automix/src/lyrics_parser.rs`.

### Verification Suite Results

| Check | Result |
| --- | --- |
| Unit Tests (Cargo - `app/native/automix`) | 36 passed, 0 failed. Locked dependencies, warnings denied. |
| Unit Tests (Cargo - `tools/*`) | 24 passed across all 5 tool crates (`deploy-artifacts`: 5, `duplicate-checker`: 6, `icon-pack`: 6, `notify-telegram`: 4, `repo-maintainer`: 3). |
| Formatting & Clippy | `cargo fmt --check` and `cargo clippy --locked --all-targets -- -D warnings` clean across all 6 crates. |
| JVM Unit Tests (`:app:testGmsMobileUniversalDebugUnitTest`) | 209 passed, 1 failed (`ModifiedSettingsRegistryTest` — known pre-existing baseline assertion). All JNI tests (`AutoMixSpectrumTest`, `AutoMixTempoTest`, `TrackMatchingTest`, `EmbeddedLyricsExtractorTest`, `AutoMixPlannerTest`, `LyricsUtilsTest`) passed. |
| Packaging & Universal Debug APK | Built `app-gms-mobile-universal-debug.apk` (122,102,303 B). Packaged `libarchivetune_automix.so` for all 4 ABIs (`arm64-v8a`: 2,699,904 B, `armeabi-v7a`: 1,692,408 B, `x86`: 2,598,444 B, `x86_64`: 3,428,560 B). |
| Python Elimination Check | 0 `.py` files in git. 0 operational python/pip calls across workflows and codebase. |

### Codebase Metrics

| Metric | Phase 1 Baseline | Phase 2 Result | Net Change |
| --- | ---: | ---: | ---: |
| Kotlin LOC | 613,543 | 613,165 | −378 |
| Java LOC | 155,722 | 155,722 | 0 |
| Rust LOC | 260 | 8,902 | +8,642 |
| Python LOC | 1,758 | 0 | −1,758 (100% eliminated) |
| Kotlin share by source bytes | ~80.78% | 80.22% | −0.56 pp |
| Java share by source bytes | ~18.90% | 18.75% | −0.15 pp |
| Rust share by source bytes | ~0.0297% | **1.0155%** | **+0.9858 pp (≥ 1.00% target met)** |
| Python share by source bytes | ~0.0445% | **0.0000%** | **−0.0445 pp** |
| Android App Rust bytes | 8,233 B | 125,677 B | +117,444 B (44.28% of Rust) |
| CI Tools Rust bytes | 0 B | 158,132 B | +158,132 B (55.72% of Rust) |
| Total Rust bytes | 8,233 B | **283,809 B** | +275,576 B |
| Universal Debug APK size | 111,650,758 B | 122,102,303 B | +10,451,545 B |

