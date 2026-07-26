# ⚡ Akam

<div align="center">

![Akam Banner](akam-windows/src/assets/logo.png)

### **The Blazing Fast, Local-First Knowledge & Note Engine**
*Cross-platform desktop (Windows) and mobile (Android) app built with a shared Rust core engine, SQLite, Supabase sync, and UniFFI.*

[![Rust](https://img.shields.io/badge/Rust-1.80%2B-orange.svg?style=for-the-badge&logo=rust)](https://www.rust-lang.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![Tauri](https://img.shields.io/badge/Tauri-v2-24C8D8.svg?style=for-the-badge&logo=tauri)](https://tauri.app/)
[![Supabase](https://img.shields.io/badge/Supabase-Sync-3FCF8E.svg?style=for-the-badge&logo=supabase)](https://supabase.com/)
[![Releases](https://img.shields.io/badge/Download-v1.0.0_Release-blue.svg?style=for-the-badge&logo=github)](https://github.com/sayedalmarwan/akam/releases/tag/v1.0.0)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

</div>

---

## ✨ Features

### 🚀 1. Shared Rust Core & Local-First Speed
- **Zero Latency**: Powered by `akam-core`, a shared Rust engine compiled natively via FFI / UniFFI.
- **SQLite Engine**: Full offline support with FTS5 (Full-Text Search) and automatic 30-day trash retention.

### 🔐 2. Supabase Cloud Sync & Auth
- **Local-First Sync**: Instant local reads/writes backed by background PostgREST sync to Supabase.
- **Built-in Auth**: Login and Sign Up screens integrated directly into app settings.

### 📜 3. Note Revision History & Edit Snapshots
- **Automatic Versioning**: Edit snapshots are saved in SQLite on significant edits.
- **One-Click Revert**: View past edit timestamps in the Note Info modal and restore previous note states anytime.

### 📤 4. Markdown & PDF Export
- **Export as Markdown (`.md`)**: Instant plain-text markdown download.
- **Export as PDF (`📄`)**: Clean print layout with tailored `@media print` CSS.

### 🔗 5. Wiki-Links & Backlinks Graph
- **Wiki-Links (`[[Note Title]]`)**: Extract links between notes and track cross-references.
- **Backlinks Query**: Query live backlinks to find all notes referencing the current note.

### 🏷️ 6. Bear-Style `#hashtags` & Smart Date Bucketing
- **Dynamic Hashtags**: Nestable tags (e.g. `#work`, `#perf`) extracted automatically.
- **Smart Date Bucketing**: Groups notes into *Pinned*, *Today*, *Previous 7 Days*, and *Month* headers.

### 👆 7. Touchpad & Mobile Gestures
- **Mobile Gestures**:
  - **Swipe Right**: Pin / Unpin note with smooth M3 spring animation.
  - **Swipe Left**: Move note to Trash.
- **Windows Touchpad & Mouse Gestures**:
  - **Two-Finger Horizontal Swipe**: Swipe left to Trash, Swipe right to Pin.
  - **`Ctrl` + Mouse Wheel**: Dynamic editor font zoom (`12px` to `28px`).
  - **Mouse Back Button (`Mouse4`) / `Escape`**: Instant back navigation.



---

## 🛠️ Building & Setup Guide

### Prerequisites
- **Rust**: `rustup target add aarch64-linux-android x86_64-linux-android`
- **Node.js**: `v18+` (for `akam-windows`)
- **Android SDK / NDK**: `r26+` (for `akam-android`)

### 1. Building `akam-core` (Rust Engine)
```bash
cd akam-core
cargo test
```

### 2. Building & Running `akam-windows` (Desktop App)
```bash
cargo build --bin akam-windows
cargo run --bin akam-windows
```

### 3. Building `akam-android` (Mobile App)
```bash
cd akam-android
.\gradlew.bat assembleDebug
```

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.
