# PDF AIO — Offline Interactive Productivity Suite

A comprehensive, 100% local, on-device interactive feature expansion for **PDF AIO** that runs completely offline with zero network or AI dependencies.

---

### User Review & Confirmed Choices

> [!IMPORTANT]
> The user confirmed the following design and technical specifications:
> - **Zero Network / No AI Constraint**: 100% local execution on device. No internet calls, no AI APIs, no Firebase.
> - **Share App & QR Code**: Featured as a prominent card in the **Home Screen top actions** and the **Developer Tab**.
> - **Theme Customization**: 4 selectable visual themes:
>   1. **Acrobat Dark** (Classic Midnight Blue/Cyan)
>   2. **OLED Pitch Black** (True `#000000` AMOLED)
>   3. **Neon Cyberpunk** (Electric Cyan & Hot Magenta)
>   4. **Executive Emerald** (Obsidian & Emerald Green)
> - **PDF Comparison / Diff Tool**: Split-screen side-by-side view with synchronized scrolling and green/red difference highlighting.

---

### 1. Feature Specifications & UX Architecture

#### 1. Local QR Code Generator & "Share App" Card
* **Offline QR Generator (`QrCodeGenerator.kt`)**: Custom pure-Kotlin 2D QR matrix engine that generates an image bitmap completely offline without external libraries or internet.
* **Share Card Composable (`ShareAppCard.kt`)**:
  - Displays QR code targeting `https://github.com/ishantkohli/PDF-AIO`
  - Developer credits: **"PDF AIO by Ishant"**
  - Buttons: **"Share App Link"** (dispatches native `Intent.ACTION_SEND` text/link) and **"Save QR Card"** (saves high-res QR image to device storage).
* **Placements**:
  - Featured on **Home Screen** top action banner
  - Featured in **Developer / Credits Screen**

#### 2. Interactive Form Filling & Auto-Fill Assistant
* **Form Overlay Tool (`FormFillOverlay.kt`)**:
  - Add interactive form elements onto any loaded PDF page:
    - 🔤 **Text Input Field**: Tap to type text, adjust font size & color.
    - ☑️ **Checkbox & Radio Toggle**: Tap to check/uncheck.
    - 📅 **Date Picker Field**: Tap to pick and stamp date.
    - 🖋️ **Signature Box**: Tap to select and place stored digital signatures.
  - Flatten & save filled form directly onto the PDF file locally.

#### 3. Split-Screen Visual PDF Comparison / Diff Tool
* **Diff Screen (`PdfDiffScreen.kt`)**:
  - Pick Document A and Document B from local device files.
  - Split-screen layout displaying Page N of Document A on left and Page N of Document B on right.
  - **Synchronized Scrolling**: Scrolling Document A automatically scrolls Document B in tandem.
  - **Visual Difference Highlighting**:
    - Added content / text highlighted in **Emerald Green** (`#10B981`)
    - Deleted / modified content highlighted in **Crimson Red** (`#EF4444`)

#### 4. Offline Text-to-Speech (TTS) Reader & Audio Visualizer
* **Offline Audio Engine (`OfflineTtsEngine.kt`)**:
  - Uses Android's built-in `android.speech.tts.TextToSpeech` engine (100% offline).
  - Controls: Play, Pause, Stop, Skip Sentence (`<<` / `>>`).
  - Speed selector pills: `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`.
  - **Interactive Audio Waveform**: Animated real-time frequency bar visualizer drawn on screen during playback.

#### 5. Launcher Quick Shortcuts (`shortcuts.xml`)
* Long-pressing PDF AIO's icon on phone home screen displays quick shortcuts:
  - 📷 **Scan Document** → Opens Camera Scanner
  - ✍️ **Sign PDF** → Opens Signature Vault
  - 🔄 **Convert File** → Opens Conversion Hub

#### 6. Custom Local Theme Switcher
* Theme Manager (`AppThemeManager.kt`):
  - Saves theme choice in `pdf_aio_theme_prefs`.
  - Options: Acrobat Dark, OLED Pitch Black, Neon Cyberpunk, Executive Emerald.
  - Instantly updates colors (`ColorScheme`) across the entire app.

---

### 2. System Architecture & Component Mapping

```
┌────────────────────────────────────────────────────────────────────────┐
│                          MainActivity & Scaffold                       │
│  - AppThemeManager (Dynamic ColorScheme: Dark / OLED / Cyber / Emerald)│
│  - Deep Link & Intent Receiver (PDF Open / App Shortcuts)              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             Navigation Router                          │
├───────────────────┬────────────────────┬───────────────────────────────┤
│                   │                    │                               │
▼                   ▼                    ▼                               ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐          ┌───────────────────┐
│  HomeScreen  │   │ ReaderScreen │   │ ToolsScreen  │          │   CreditScreen    │
│- Share Card  │   │- Offline TTS │   │- Form Filler │          │ - Share QR Card   │
│- Theme Selector  │- Audio Wave  │   │- Visual Diff │          │ - Dev Information │
└──────────────┘   └──────────────┘   └──────────────┘          └───────────────────┘
```

---

### 3. Verification Plan

1. **Offline Integrity Verification**:
   - Ensure zero network calls, zero AI imports, zero Firebase dependencies.
2. **Compilation**:
   - Run `compile_applet` to verify clean build state across all components.
