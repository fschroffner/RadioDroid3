# FloRadio

<p align="center">
  <strong>A modern, lightweight, and privacy-focused Internet Radio player for Android.</strong><br>
  Powered by the community-driven <a href="https://www.radio-browser.info">Radio-Browser</a> database.
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0.html"><img src="https://img.shields.io/badge/License-GPLv3-blue.svg" alt="License: GPLv3"></a>
  <img src="https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/Platform-Android%2021%2B%20(Target%2035)-3DDC84.svg" alt="Android">
  <img src="https://img.shields.io/badge/Audio-AndroidX%20Media3-informational.svg" alt="Media3">
</p>

---

## Features

- **Tens of Thousands of Stations:** Search stations worldwide by country, tag, language, bitrate, or votes via the free Radio Browser API.
- **Modern Audio Engine:** Powered by **AndroidX Media3** for rock-solid streaming stability, proper foreground media service handling, and lock screen controls.
- **Clean & Fast:** Fully optimized and rewritten in **100% Kotlin** with Jetpack Compose & modern Android architecture.
- **Privacy First:** No tracking, no analytics, no advertisements. All favorites and history stay strictly local on your device.
- **Dark & OLED Themes:** Includes OLED black theme and dynamic system theming.
- **Sleep Timer:** Automatically stop playback after a customizable duration.
- **Playlist Export / Import:** Easily back up and restore your favorite stations (M3U / JSON).

---

## Tech Stack & Architecture

- **Language:** Kotlin (100%)
- **Audio & Playback:** AndroidX Media3 (`MediaSessionService`, `MediaController`, ExoPlayer)
- **Dependency Injection:** Hilt (Dagger)
- **Database & Storage:** Jetpack Room (KSP) & DataStore
- **Concurrency & Reactivity:** Kotlin Coroutines & StateFlow / Turbine
- **Build & Platform:** Target SDK 35 (Android 15), Java 21 toolchain

---

## Building from Source

Prerequisites:
- Android Studio Ladybug or later / JDK 21
- Android SDK Platform 35

```bash
# Clone the repository
git clone https://github.com/fschroffner/FloRadio.git
cd FloRadio

# Build the free debug APK
./gradlew assembleFreeDebug

# Run unit tests
./gradlew testFreeDebugUnitTest
```

---

## Downloads

Compiled APK releases and debug builds are available under [Releases](https://github.com/fschroffner/FloRadio/releases) and [GitHub Actions artifacts](https://github.com/fschroffner/FloRadio/actions).

---

## License & Attribution

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENCE](LICENCE) for details.

* Originally derived from the open-source project [RadioDroid](https://github.com/segler-alex/RadioDroid) by Alex Segler.
* Station directory provided by [Radio Browser](https://www.radio-browser.info).
