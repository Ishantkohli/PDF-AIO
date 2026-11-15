# PDF (AIO) — Third-Party Services and External Links

**Effective date:** 15 November 2026  
**Last updated:** 15 November 2026  
**Application:** PDF (AIO) (`com.aistudio.acrobatreader.hptwqz`, v5.3.0)  
**Developer:** Ishant Kohli  
**Website:** https://ishantkohli.github.io/Web  
**Contact:** ishantkohlikohli@gmail.com

This document strictly distinguishes **bundled offline libraries**, **GitHub Releases updater**, **user-submitted feedback forwarding**, and **external links** inside **PDF (AIO)**.

---

## 1. Bundled On-Device Libraries (Zero Cloud Document Traffic)

PDF (AIO) links these libraries locally inside the APK for offline execution:

- **Apache PDFBox-Android (Apache-2.0):** On-device PDF parsing, text layout extraction, splitting, redaction scrubbing, metadata updates, and AES-256 encryption.
- **Android Jetpack (Compose, Material 3, Room SQLite, CameraX, Core KTX) & Coil:** Local UI, camera document capture pipeline, image rendering, and local SQLite persistence (`pdf_aio.db`).
- **No AI / No ML / No Analytics:** Zero Firebase, Supabase, Google Analytics, Crashlytics, ad SDKs, or cloud OCR endpoints are present.

## 2. GitHub Releases Updater (`UpdateManager` & `UpdateCheckAlarmReceiver`)

The App queries GitHub's public Releases REST API on app startup, every 8 hours via periodic background alarm (`UpdateCheckAlarmReceiver`, re-registered after reboot), and on explicit user tap of **Check For Update**:

- **Endpoint:** `https://api.github.com/repos/{owner}/{repo}/releases/latest` (defaults to `ishantkohli/PDF-AIO`, configurable in Update Repository settings).
- **Asset Hosting:** Release `.apk` downloads and HTTP redirects run strictly over HTTPS (`usesCleartextTraffic="false"`).
- **Provider:** GitHub, Inc. (`https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement`).

## 3. User-Submitted Feedback Delivery (`FeedbackDispatcher`)

When you fill out the in-app **Feedback Form** and tap **Submit Feedback to Developer**:

- Your entered form text (name, problem report, update suggestion, app suggestion, and app version) is sent over HTTPS POST to **ShipMyForm** (`https://shipmyform.com/to/ishantkohlikohli@gmail.com`).
- Every entry is also saved locally on device (`user_feedback_records`) so offline or failed sends never lose your draft and can be retried, exported via your email app, edited, or deleted.

## 4. User-Initiated External Links, UPI Intents & Choosers

Tapping optional links inside the App launches external apps handled by your device OS:

- **Email & Share Chooser:** Feedback **Email App** (`mailto:ishantkohlikohli@gmail.com`) and **Share** actions invoke your chosen email or messaging app via Android `Intent` / `FileProvider`.
- **UPI Donation Intent:** Tapping **Support Here** launches installed UPI apps (`upi://pay?...`). No payment card data touches PDF (AIO).
- **Developer Website & Social Links:** Opening `https://ishantkohli.github.io/Web`, GitHub, Instagram, LinkedIn, X/Twitter, WhatsApp, Reddit, YouTube, or Facebook launches your external browser or installed client under their own policies.

## 5. Companion Website Verification Item

- `[TODO: Inspect live https://ishantkohli.github.io/Web hosting/CDN headers, scripts, and any third-party embeds before making unconditional claims about website third-party connections.]`

## 6. Contact

For questions regarding third-party integrations, contact **ishantkohlikohli@gmail.com**.
