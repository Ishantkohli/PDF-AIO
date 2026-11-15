<div align="center">

<img src="assets/adf-aio-banner.gif" alt="PDF (AIO) animated banner" width="100%">

# PDF (AIO) — v5.3.0

### 100% Local, Offline-First Android PDF Reader, Scanner, Editor & Document Suite

**Local On-Device Document Processing • Zero AI APIs or ML Models • Zero Tracking or Analytics**

[Developer Website](https://ishantkohli.github.io/Web) · [Privacy Policy](ADF-AIO-Policies/01-PRIVACY-POLICY.md) · [License](LICENSE)

</div>

---

## Proprietary Notice

Copyright © 2026 **Ishant Kohli**. All rights reserved.  
You may install and use the official pre-built **PDF (AIO)** application for its intended document-processing purposes. Public visibility of source files does **not** grant permission to copy, redistribute, modify, or build derivative applications from proprietary source code without prior written permission (`ishantkohlikohli@gmail.com`). See [`LICENSE`](LICENSE) for complete terms.

---

## Core Architecture & Privacy Guarantees

**PDF (AIO)** processes every document entirely on your Android device:

- **100% On-Device Document Processing:** Reading, text selection, annotations, redaction, scanning, format conversion, compression, page management, and PDF comparison execute locally without internet connectivity or document uploads.
- **No AI / No ML / No Analytics:** Zero cloud AI APIs, zero offline machine-learning models, zero analytics SDKs, zero Firebase/Supabase backends, and `android:allowBackup="false"`.
- **Transparent Optional Network Features:**
  - **GitHub Releases Updater:** Queries `ishantkohli/PDF-AIO` on GitHub Releases at startup, via an 8-hour periodic background alarm (`UpdateCheckAlarmReceiver`), or when you tap **Check For Update**, downloading `.apk` releases strictly over HTTPS (`usesCleartextTraffic="false"`).
  - **Feedback Form & Local Drafts:** Stores local feedback drafts in `SharedPreferences` (`user_feedback_records`) and submits explicitly entered feedback fields over HTTPS via ShipMyForm when you tap **Submit Feedback to Developer**, with honest failure reporting plus **Retry Send**, **Email App**, **Edit**, and **Delete** controls.
- **In-App Legal & Privacy Center:** Browse, copy, or share all 10 privacy, retention, security, licensing, and usage policies directly inside the app via **Core Suite Menu** or **Credits & Developer**.

---

## Key Features (v5.3.0)

- **High-Clarity PDF Reader & Pure Viewer Mode**
  - Text-aware PDF rendering via Android `PdfRenderer` + Apache `PDFBox-Android`.
  - Long-press & dedicated toolbar **Text Select** mode with glyph hit-testing, word/sentence/paragraph expansion, and honest fallback guidance on image-only scanned PDFs.
  - Persistent bookmarks, recent history, outline navigation, and distraction-free **Pure Viewer** mode.
- **Dedicated Document Scanner (CameraX)**
  - In-app CameraX capture pipeline with automatic quad document boundary detection, 5-frame stability tracking (`QuadStabilityTracker`), manual corner adjustment, perspective cropping, enhancement filters, and local multi-page PDF assembly.
- **Permanent Redaction & Security Engine**
  - Raster/content-stream sanitization redaction workflow with `verifySanitizedPdf` verification checks against text extraction, annotations, and metadata leaks.
  - AES-256 PDF password protection, decryption, and metadata management via `PdfSecurityEngine`.
- **Document Conversion & Page Management Suite**
  - Offline conversions: **PDF ↔ Word (`.docx`)**, **PDF ↔ TXT**, **Images ↔ PDF**, **Merge**, **Split** (page ranges & fixed chunks), **Watermark Studio**, and **Size Compression**.
  - **Page Manager** to reorder, rotate (90°/180°), and remove pages.
  - **PDF Diff / Compare** using Myers line diff and pixel variance overlay to compare two PDFs side-by-side and export Markdown comparison reports.
- **Fill & Sign Vault**
  - Reusable local signature drawing & stamp vault synced with Room (`pdf_aio.db`, schema v2).
- **Storage Discovery, Atomic Rename & Multi-Mode Share**
  - Discovers PDF and TXT documents across Internal Storage, External Storage (SD cards), MediaStore, and SAF DocumentTree folders.
  - Atomic local file rename synchronized with Room DB, plus Storage Access Framework (`Save Copy As`) fallback if an external provider denies direct rename.
  - 4 sharing modes via `FileProvider` + `ClipData`: Share as PDF, Share as TXT, Share as DOCX, or Share as PDF with custom filename.
- **Visual Customization & Diagnostics**
  - Six themes: **Liquid Glass**, **Midnight Glass**, **OLED Deep Glass**, **Sunset Liquid Glass**, **Emerald Liquid Glass**, and **Frosted Crystal Glass**.
  - Hardware refresh-rate selector (60–165 Hz where supported) with optional FPS HUD and 7 home screen app widgets/shortcuts.

---

## Technical Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material Design 3
- **PDF & Office Processing:** Apache PDFBox-Android (Apache-2.0), Android `PdfRenderer`, local OOXML `.docx` & `.txt` engines
- **Camera & Imaging:** AndroidX CameraX, local pixel/edge processing (`DocumentEdgeDetector`)
- **Local Persistence:** Room SQLite (`pdf_aio.db`, schema v2, `MIGRATION_1_2`), Android `SharedPreferences`
- **Build Configuration:** Gradle Kotlin DSL (`minSdk 24`, `targetSdk 36`, `versionCode 530`, `versionName "5.3.0"`)

---

## Building & Testing Locally

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest --console=plain
```

> Requires Android SDK Platform 36 and JDK 11+. Use `gradle` directly.

---

## Legal, Privacy & Policy Documentation

All official policies live in [`ADF-AIO-Policies/`](ADF-AIO-Policies) and are mirrored inside the app's **Legal & Privacy Center**:

1. [Privacy Policy](ADF-AIO-Policies/01-PRIVACY-POLICY.md)
2. [Terms and Conditions](ADF-AIO-Policies/02-TERMS-AND-CONDITIONS.md)
3. [Cookie Policy](ADF-AIO-Policies/03-COOKIE-POLICY.md)
4. [Cookie and Form Consent](ADF-AIO-Policies/04-COOKIE-AND-FORM-CONSENT.md)
5. [Data Retention and Deletion](ADF-AIO-Policies/05-DATA-RETENTION-AND-DELETION.md)
6. [Third-Party Services and External Links](ADF-AIO-Policies/06-THIRD-PARTY-EMBEDS.md)
7. [Proprietary License Summary](ADF-AIO-Policies/07-PROPRIETARY-LICENSE.md)
8. [Acceptable Use Policy](ADF-AIO-Policies/08-ACCEPTABLE-USE-POLICY.md)
9. [Security and Vulnerability Disclosure](ADF-AIO-Policies/09-SECURITY-AND-VULNERABILITY-DISCLOSURE.md)
10. [Accessibility and Disclaimer](ADF-AIO-Policies/10-ACCESSIBILITY-AND-DISCLAIMER.md)

---

## Contact

- **Developer:** Ishant Kohli
- **Email:** `ishantkohlikohli@gmail.com`
- **Website:** [https://ishantkohli.github.io/Web](https://ishantkohli.github.io/Web)
- **Repository:** [https://github.com/Ishantkohli/PDF-AIO](https://github.com/Ishantkohli/PDF-AIO)
