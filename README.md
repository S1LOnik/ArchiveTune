<div align="center">

  <img src="https://raw.githubusercontent.com/S1LOnik/ArchiveTune/main/fastlane/metadata/android/en-US/images/icon.png" width="160" height="160" alt="ArchiveTune Logo" style="border-radius: 22%">

  <h1>ArchiveTune</h1>
  <p><em>Enhanced Edition • Maintained by <a href="https://github.com/S1LOnik">S1LOnik</a></em></p>

  <p align="center">
    <strong>Redefining the YouTube Music &amp; Spotify Experience on Android.</strong>
    <br />
    <em>High-performance, privacy-focused, zero-stutter playback, and authoritative multi-source library sync.</em>
  </p>

  <p align="center">
    <a href="#-key-improvements--differences-from-upstream"><b>Why This Fork?</b></a> •
    <a href="#-download-releases"><b>Download</b></a> •
    <a href="#-features"><b>Features</b></a> •
    <a href="#-screenshots"><b>Screenshots</b></a> •
    <a href="https://github.com/S1LOnik/ArchiveTune/releases"><b>GitHub Releases</b></a>
  </p>

  <div align="center">
    <a href="https://github.com/S1LOnik/ArchiveTune/releases/latest">
      <img src="https://img.shields.io/github/v/release/S1LOnik/ArchiveTune?style=for-the-badge&color=6366f1&labelColor=1e1e2e&logo=github" alt="Latest Version" />
    </a>
    <a href="https://github.com/S1LOnik/ArchiveTune/releases">
      <img src="https://img.shields.io/github/downloads/S1LOnik/ArchiveTune/total?style=for-the-badge&color=10b981&labelColor=1e1e2e&logo=github" alt="Downloads" />
    </a>
    <a href="https://github.com/S1LOnik/ArchiveTune/stargazers">
      <img src="https://img.shields.io/github/stars/S1LOnik/ArchiveTune?style=for-the-badge&color=f59e0b&labelColor=1e1e2e&logo=github" alt="Stars" />
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/github/license/S1LOnik/ArchiveTune?style=for-the-badge&color=ec4899&labelColor=1e1e2e" alt="License" />
    </a>
    <a href="https://github.com/S1LOnik/ArchiveTune/actions/workflows/build_and_release.yml">
      <img src="https://img.shields.io/github/actions/workflow/status/S1LOnik/ArchiveTune/build_and_release.yml?style=for-the-badge&label=CI%2FCD&labelColor=1e1e2e" alt="Build Status" />
    </a>
  </div>

</div>

<hr />

> [!IMPORTANT]
> **📦 Official Releases & Updates:**  
> All releases, updates, and signed APKs for this fork are published directly on **[GitHub Releases](https://github.com/S1LOnik/ArchiveTune/releases)** via automated GitHub Actions CI/CD.  
> We do **not** distribute APK builds through private Telegram channels, third-party file hosters, or unverified mirrors. Every release includes SHA-256 checksums and automated mapping files.

---

## ⚡ Key Improvements & Differences from Upstream

This repository is an actively maintained and optimized fork of ArchiveTune/OuterTune, focused on rock-solid playback stability, full Spotify library integration, and eliminating real-world playback bottlenecks:

| Feature / Area | Original Upstream | **S1LOnik Fork** |
| :--- | :--- | :--- |
| **Spotify Liked Songs** | ❌ Fails with `No content in fetchPlaylist response` / blank screen | ✅ **Fully fixed.** Intercepts GQL collection queries, maps to `fetchLibraryTracks`, supports in-playlist search, multi-selection & shuffle |
| **Track Loading Speed** | ⚠️ 2–5s blocking network delay on every YouTube track if SoundCloud enabled | ⚡ **Instant loading.** SoundCloud stream resolution is strictly isolated to SoundCloud tracks, removing blocking overhead |
| **High Quality Playback Transition** | ❌ Interrupts and re-prepares audio stream mid-song when switching from background to foreground | ✅ **Zero-stutter.** Playing tracks are never interrupted; candidate resolution happens ahead-of-time in `nextStreamPreload` |
| **Audio Focus on ColorOS / Modern Android** | ⚠️ Stutters or pauses when opening app, switching windows, or using Dynamic Island | ✅ **Smart audio ducking.** Volume ducks to 50% during transient system animations; 1200ms debounce prevents false pauses |
| **Library Synchronization** | ⚠️ Unliking songs remotely leaves stale tracks in local database | ✅ **Authoritative two-way sync** for YouTube Music & Spotify with pull-to-refresh on library tabs |
| **Missing Settings Destinations** | ❌ Crashes when navigating to several provider settings | ✅ **100% stable navigation graph** with all sub-settings routes fully implemented |
| **Automated Release Pipeline** | ⚠️ Manual builds or external links | 🚀 **Automated GitHub Actions CI/CD** generating signed Universal & Arm64-v8a APKs with SHA-256 hashes |

---

## 📥 Download Releases

Always download the latest build directly from GitHub:

<div align="center" id="download-releases">

### 🌟 [Download Latest ArchiveTune Release](https://github.com/S1LOnik/ArchiveTune/releases/latest)

| Architecture | Package | Description |
| :---: | :---: | :---: |
| **Universal (All Devices)** | [`ArchiveTune-v16.1-universal.apk`](https://github.com/S1LOnik/ArchiveTune/releases/latest) | Works on all 64-bit and 32-bit Android devices (Android 8.0+) |
| **ARM64 (Modern Phones)** | [`ArchiveTune-v16.1-arm64-v8a.apk`](https://github.com/S1LOnik/ArchiveTune/releases/latest) | Optimized for modern 64-bit processors (smaller footprint) |

</div>

> [!TIP]
> **Troubleshooting Installation (`App not installed` error):**  
> If Android reports *"An app with the same signature has already been installed"* after uninstalling, check if ArchiveTune was cloned in **App Cloner / Dual Apps** or installed in **System Clone (User 11 / Second Space)**. Use `adb uninstall --user 11 moe.rukamori.archivetune` or delete the clone profile before installing.

---

## 📸 Screenshots

<div align="center" id="screenshots">

<img src="https://raw.githubusercontent.com/S1LOnik/ArchiveTune/main/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_1.jpg" alt="Browse" width="31%" />
<img src="https://raw.githubusercontent.com/S1LOnik/ArchiveTune/main/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_2.jpg" alt="Live Lyrics" width="31%" />
<img src="https://raw.githubusercontent.com/S1LOnik/ArchiveTune/main/fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_7.jpg" alt="Player" width="31%" />

</div>

---

## ✨ Features

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>🎵 Playback &amp; Sources</h3>
      <ul>
        <li>Ad-free background playback with caching &amp; offline downloads</li>
        <li>Dual source routing: YouTube Music &amp; Spotify Web API integration</li>
        <li>Universal fallback matrix: automatically picks the highest bitrate source</li>
        <li>Lossless FLAC playback via custom providers</li>
        <li>Full local audio file and folder playback support</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>🔊 Audio Processing</h3>
      <ul>
        <li>EBU R128 loudness normalization and replay gain</li>
        <li>Seamless crossfade between queue items</li>
        <li>Tempo, pitch, and variable speed playback adjustments</li>
        <li>System equalizer &amp; spatial audio integration</li>
        <li>Smart audio ducking during notifications &amp; transient system prompts</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🎤 Lyrics &amp; Recognition</h3>
      <ul>
        <li>Synchronized word-by-word scrolling lyrics</li>
        <li>Multi-provider lyrics: Musixmatch, LRCLIB, Paxsenix, BetterLyrics</li>
        <li>Built-in music recognition (ShazamKit engine)</li>
        <li>AI lyrics translation and romanization</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>🎨 Customization &amp; Interface</h3>
      <ul>
        <li>Modern Material 3 design with dynamic Monet color palette</li>
        <li>Over 9 distinct player themes and animated canvas backgrounds</li>
        <li>Persistent queue state across app launches and device reboots</li>
        <li>Last.fm and ListenBrainz scrobbling support</li>
        <li>Discord Rich Presence with album art and playback state</li>
      </ul>
    </td>
  </tr>
</table>

---

## 🛠️ Building from Source

To compile ArchiveTune locally using Gradle:

```bash
# Clone the repository
git clone https://github.com/S1LOnik/ArchiveTune.git
cd ArchiveTune

# Build Universal Release APK
./gradlew :app:assembleGmsMobileUniversalRelease

# Build Arm64 Release APK
./gradlew :app:assembleGmsMobileArm64Release
```

The compiled APKs will be located in `app/build/outputs/apk/gmsMobile/release/`.

---

## ⚖️ License & Attribution

* ArchiveTune is licensed under the **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.
* Based on the original ArchiveTune by [rukamori](https://github.com/rukamori/ArchiveTune) and [OuterTune](https://github.com/dd injustices/OuterTune).
* This project is independent and not affiliated with Google LLC, YouTube, or Spotify.

<div align="center">
  <p><b>If you enjoy this fork, please give it a ⭐ on GitHub!</b></p>
</div>
