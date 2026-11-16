# ThoughtDrop 🎙️💎
> **Zero-Friction AI Voice Note Taker for Android with Living Optical Glass & 3D Particle Mesh UI**

ThoughtDrop is a native Android application built in Kotlin & Jetpack Compose designed to capture spontaneous thoughts, voice memos, and whiteboard notes the instant they strike, without fumbling through menus.

---

## ✨ Features & Architecture

* **Living Optical Glass Interface**:
  * **Translucent Frosted Acrylic Surfaces**: Translucent glass containers with emerald-tinted gradients (`Brush.verticalGradient`).
  * **Top-Lit Specular Rim**: Custom gradient border brushes emulating real physical glass refractions (`GlassBorderBrush`).
  * **Ambient Optical Caustics**: Radiant background aurora blooms (Emerald & Sunset Amber) that breathe through the glass cards.
  * **3D Woven Particle Mesh Orb**: Undulating 3D particle lattice with depth perspective, inner obsidian spherical core shadow, and top-right crystal warm-white specular lighting.

* **Floating Studio Dock**:
  * 46dp Quick Camera Lens Circle for instant document and whiteboard photo capture.
  * 62dp Center Hero Radiant Amber Record Orb with tactile animations.
  * 46dp Quick Gallery Circle for importing photos and diagrams.

* **AI Native Multi-Modal Synthesis (Gemini 4 Hydrogen / Gemini 2.5)**:
  * Direct audio streaming to Gemini (compressed high-fidelity AAC/M4A).
  * Multi-photo attachment support with non-destructive OCR and diagram understanding.
  * Automatically extracts:
    * 🏷️ **Category & Topic Tags** (`#ARCHITECTURE`, `#STRATEGY`, `#DEV`, etc.)
    * 📌 **Punchy Title & Executive Summary**
    * ✅ **Action Items & Checklist Steps**
    * 📜 **Full Audio Transcript**

* **Local-First & 100% Private**:
  * Persistent storage via **Room SQLite** (v5 schema with multi-photo support).
  * In-app audio player with interactive waveform equalizer bars.
  * Publish-safe BYOK (Bring Your Own Key) architecture.

---

## 🛠️ Tech Stack

* **Language**: Kotlin 2.0+
* **UI**: Jetpack Compose & Material 3
* **Asynchronous / Concurrency**: Kotlin Coroutines & Flow
* **Local Database**: AndroidX Room (SQLite)
* **AI Engine**: Google Gemini API (Multi-modal Voice & Vision)
* **Audio Engine**: Android `MediaRecorder` & `MediaPlayer` (AAC / 16kHz Mono)
* **Build Tooling**: Gradle Kotlin DSL (`build.gradle.kts`)

---

## 📲 Building & Running

### Requirements
* Android Studio Ladybug | 2024.2+ or Android SDK (API 34+)
* JDK 17 or higher
* Android device running Android 9.0+ (API 28+)

### Command Line
```powershell
# Clone the repository
git clone https://github.com/MManjul/ThoughtDrop.git
cd ThoughtDrop

# Assemble the debug APK
.\gradlew.bat assembleDebug

# Install on a connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
