<div align="center">

# 🎙️ ThoughtDrop 💎
### *Zero-Friction AI Voice Note Taker for Android*
**Built with Native Jetpack Compose, Living Optical Glass UI & Gemini Multi-Modal Intelligence**

<p align="center">
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=for-the-badge&logo=android&logoColor=white" alt="Jetpack Compose" /></a>
  <a href="https://ai.google.dev/"><img src="https://img.shields.io/badge/Google%20Gemini-Multi--Modal%20AI-F59E0B.svg?style=for-the-badge&logo=google&logoColor=white" alt="Google Gemini" /></a>
  <a href="https://developer.android.com/training/data-storage/room"><img src="https://img.shields.io/badge/Room%20DB-v5%20SQLite-10B981.svg?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room DB" /></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge" alt="License" /></a>
</p>

<p align="center">
  <b>Never lose a spontaneous thought again.</b><br>
  Capture voice ideas, meeting audio, and whiteboard photos in 1-tap — automatically synthesized into structured, actionable notes using on-device Room storage and Gemini AI.
</p>

---

</div>

## 💡 The Philosophy: Zero Friction

Most note-taking apps fail at the exact moment inspiration strikes:
1. Unlock phone $\rightarrow$ 2. Find note app $\rightarrow$ 3. Tap `+` $\rightarrow$ 4. Choose keyboard $\rightarrow$ 5. Start typing...

By the time you finish step 3, the fleeting idea has vanished. **ThoughtDrop** is engineered for **Zero Friction**:
* **Notification Shade Quick Tile**: Pull down the Android shade from anywhere (even the lock screen) and tap **"Quick Idea"** to speak immediately.
* **Home Screen Widget**: A 1-tap dedicated home screen widget with a live pulsing recording indicator.
* **Floating Studio Dock**: In-app 46dp camera lens circle, 62dp radiant amber hero mic orb, and 46dp gallery upload circle.

---

## ✨ Key Features & Innovations

### 1. 🌌 Living Optical Glass UI & 3D Particle Mesh Orb
A bespoke design system crafted in 100% pure Jetpack Compose without heavyweight 3D web engines:
* **3D Woven Particle Mesh Orb (`GlowingEmberOrb.kt`)**:
  * 960 undulating particle points organized in 24 latitude rings $\times$ 40 longitude harmonic sample points.
  * Two-pass depth projection with an inner obsidian spherical core shadow, creating authentic 3D volume and occlusion.
  * Top-right specular highlight brilliance with warm champagne-gold (`#FEF3C7`) and crystal-white (`#FFFDF5`) light halos.
  * Silk-smooth organic breathing and wave undulation (1.10 rad/s sweet spot).
* **Translucent Frosted Acrylic Surfaces**:
  * Specular top-lit refractive rims using custom gradient brushes (`GlassBorderBrush`) starting at 80% white opacity.
  * Translucent emerald acrylic gradients (`0x381E5437` $\rightarrow$ `0x1F0B1C13`).
  * **Ambient Optical Caustics**: Background canvas radiating dynamic emerald and sunset amber caustics that breathe through the glass cards.

### 2. ⚡ Multi-Modal Voice & Vision Intelligence (Gemini 4 Hydrogen / Gemini 2.5)
* **Direct Audio Ingestion**: High-fidelity AAC/M4A voice audio is streamed directly to Gemini without relying on slow intermediate speech-to-text engines.
* **Multi-Photo Non-Destructive OCR**: Attach whiteboard sketches, paper invoices, architecture diagrams, or handwritten notes. Gemini synthesizes audio + visual context simultaneously.
* **Automatic Intelligent Structuring**:
  * 🏷️ **Contextual Category Capsules**: `#ARCHITECTURE`, `#STRATEGY`, `#DEV`, `#PRODUCT`, `#PERSONAL`.
  * 📌 **Punchy Title & Two-Line Executive Summary**: Distills rambling voice memos into crisp key takeaways.
  * ✅ **Checklist Action Items**: Automatically detects tasks and creates checkable to-do items.
  * 📜 **Full Verbatim Transcript**: Preserves complete historical context.

### 3. 🔒 100% Private, Local-First & BYOK (Bring Your Own Key)
* **On-Device SQLite Storage**: All transcripts, summaries, checklist items, and audio files reside strictly on your device inside a local **Room SQLite Database (v5 schema)**.
* **No Developer Tracking**: Zero third-party telemetry, analytics, or centralized backend servers.
* **Publish-Safe BYOK**: Enter your own free Google AI Studio key directly in the app. Keys are stored securely in Android's private app storage with multi-key pool rotation and automated quota failover.

---

## 🏛️ System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        THOUGHTDROP ANDROID APP                         │
├───────────────────────────────┬────────────────────────────────────────┤
│         CAPTURE TRIGGERS      │            CORE ENGINE                 │
│  • Quick Settings Tile        │  • VoiceRecorder (AAC / 16kHz Mono)    │
│  • Home Screen AppWidget      │  • ImageUtils (Matrix Sub-sampling)    │
│  • In-App Floating Studio Dock│  • AudioPlayer (Waveform Equalizer)    │
└──────────────┬────────────────┴──────────────────┬─────────────────────┘
               │                                   │
               ▼                                   ▼
┌───────────────────────────────┐ ┌──────────────────────────────────────┐
│     AI SYNTHESIS PIPELINE     │ │        LOCAL DATA LAYER              │
│  • GeminiAudioClient          │ │  • Room SQLite Database (v5)         │
│  • GroqApiClient (Fallback)   │ │  • NoteEntity & NoteDao              │
│  • Multi-Key Quota Rotator    │ │  • SettingsRepository (Encrypted)    │
└──────────────┬────────────────┘ └────────────────┬─────────────────────┘
               │                                   │
               └─────────────────┬─────────────────┘
                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      REACTIVE JETPACK COMPOSE UI                       │
│  • HomeScreen (Living Optical Caustics & Category Pills)               │
│  • GlowingEmberOrb (Pure Canvas 3D Particle Mesh Engine)               │
│  • NoteCard (Living Glass Surface & Interactive Audio Waveform Strip)  │
│  • RecordBottomSheet & ModernGlassCheckbox                             │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 📱 User Interface Tour

| Component | Description |
| :--- | :--- |
| **Hero Card with 3D Orb** | Houses the live **3D Woven Particle Mesh Orb** alongside the `✦ Tap Orb To Speak` instant-action button. |
| **Living Optical Glass NoteCard** | Frosted acrylic container with top-lit specular rim borders, dynamic `#CATEGORY` pills, pink `★ PINNED` badges, and audio scrub bars. |
| **Interactive Audio Strip** | Embedded waveform visualizer with amber circular play/pause button and monospace duration counter. |
| **Photo Attachment Row** | Compact `[🖼 filename OCR]` capsules supporting full-screen pinch-to-zoom image inspection. |
| **Floating Studio Dock** | Suspended frosted glass capsule with 46dp Camera lens circle, 62dp center radiant amber record orb, and 46dp gallery circle. |

---

## 🛠️ Tech Stack & Libraries

* **Language**: [Kotlin 2.0.0](https://kotlinlang.org/)
* **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & [Material 3](https://m3.material.io/)
* **Reactive Architecture**: MVVM with Kotlin `Coroutines` & `StateFlow`
* **Local Persistence**: [AndroidX Room](https://developer.android.com/training/data-storage/room) v2.6.1 (SQLite)
* **Network & Serialization**: [OkHttp3](https://square.github.io/okhttp/) & [Google Gson](https://github.com/google/gson)
* **Media Processing**: Android `MediaRecorder`, `MediaPlayer`, and custom `Canvas` equalizers
* **Build System**: Gradle 8.4+ with Kotlin DSL (`build.gradle.kts`)

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio**: Ladybug (2024.2+) or newer
* **JDK**: Version 17 or higher
* **Android Device / Emulator**: Running Android 8.0+ (API Level 26+)
* **Gemini API Key**: Free tier key from [Google AI Studio](https://aistudio.google.com/apikey)

### Build & Run from Source

```powershell
# 1. Clone the repository
git clone https://github.com/MManjul/ThoughtDrop.git
cd ThoughtDrop

# 2. Build the Debug APK
.\gradlew.bat assembleDebug

# 3. Install directly to a connected Android phone via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. Launch ThoughtDrop on device
adb shell am start -n com.thoughtdrop.app/.MainActivity
```

---

## 🔑 Configuration (BYOK)

1. Launch **ThoughtDrop** on your Android device.
2. Tap the **M Avatar / Settings (⚙️)** in the top-right brand header.
3. Paste your Gemini API key (from [aistudio.google.com](https://aistudio.google.com/apikey)).
4. *(Optional)* Add secondary Gemini or Groq fallback keys for multi-key automatic quota rotation.

---

## 🤝 Contributing

Contributions, feature suggestions, and bug reports are welcome!
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

<div align="center">
  <sub>Crafted with passion for high-performance Android UI & AI. Star ⭐ the repository if you find it helpful!</sub>
</div>
