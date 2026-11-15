# PDF (AIO) — Privacy Policy

**Effective date:** 15 November 2026  
**Last updated:** 15 November 2026  
**Application:** PDF (AIO) (`com.aistudio.acrobatreader.hptwqz`, v5.3.0)  
**Operator:** Ishant Kohli (`[TODO: Confirm whether operated solely as an individual developer or through a registered business entity]`)  
**Website:** https://ishantkohli.github.io/Web  
**Privacy & Support Contact:** ishantkohlikohli@gmail.com  
**Repository:** https://github.com/Ishantkohli/PDF-AIO

---

## 1. App Identity and Scope

This Privacy Policy applies to the **PDF (AIO)** Android application (formerly referred to as ADF AIO in earlier documentation) developed by **Ishant Kohli**. It explains strictly and transparently what information is processed on your device, what remains strictly local, what optional outbound network flows exist, and what controls you hold.

## 2. Core Local-First Document Processing Guarantee

PDF (AIO) is engineered so **every document tool runs strictly on your Android device**:

- **Zero AI / ML:** No cloud AI APIs, no third-party LLM integrations, and no offline machine-learning models are included or called.
- **Zero Cloud Document Processing:** Viewing, text selection, redaction, password protection, splitting, merging, Diff comparison, scanning, edge cropping, and Word/TXT/Image conversions execute completely on your device without uploading document contents.
- **Zero Telemetry & Analytics:** No analytics SDKs, crash-reporting SDKs, advertising identifiers, Firebase/Supabase backends, or behavioral tracking libraries are present.

## 3. Information Processed and Stored On-Device

All document content remains on your device under your control:

- **Documents & Exports:** PDF, TXT, DOCX, DOC, and image files you open, scan, compare, redact, or convert remain in your selected device folders (Internal Storage or External Storage / SD card), Storage Access Framework (SAF) URIs, or private app storage (`filesDir`).
- **Local Library & Annotations Database (`pdf_aio.db`, schema v2):** Document titles, local paths/URIs, page counts, last-opened timestamps, pinned/favorite flags, bookmarks, annotations, and vector signatures are stored locally using **Android Room SQLite**.
  - **Honest Storage Disclosure:** The Room SQLite database (`pdf_aio.db`) is stored inside Android's private per-app sandbox and protected by Android OS filesystem isolation, **not** database-level SQLCipher encryption.
- **AES-256 Encrypted PDFs:** When you explicitly encrypt a PDF using PDF Security Tools, the exported PDF file itself is protected with standard AES-256 PDF encryption (passwords are never transmitted off-device and are not stored in plaintext in the database).

## 4. Caches, Staging Files, and Sanitized Logs

- **Rendering & Share Staging Cache:** Temporary share/export copies under `cacheDir/share_staging/` are created locally when preparing exports/shares. Expired staging artifacts older than **2 hours** are automatically cleaned up on initialization and share prep.
- **Update APK Cache:** If you download a release APK via the GitHub Releases updater, it is temporarily saved in `cacheDir/app_updates/` and cleaned automatically after installation completes (`UpdateCleanReceiver`).
- **Diagnostic Logs:** Runtime Android logs avoid recording private document filenames, directory paths, URIs, or extracted text contents. No logs are uploaded automatically.

## 5. Feedback Form & Local Draft Storage

The **Feedback Form** saves every feedback entry locally as a structured draft in private `SharedPreferences` (`user_feedback_records`):

- When you explicitly tap **Submit Feedback to Developer**, the form fields you entered (sender name, problems faced, update suggestion, app suggestion, and app version) are transmitted over HTTPS to **ShipMyForm** (`https://shipmyform.com/to/ishantkohlikohli@gmail.com`) for delivery to the developer.
- No PDF documents, device files, or background telemetry are ever attached automatically.
- If the network is unavailable or remote delivery fails, the UI reports delivery failure honestly while preserving your local draft with **Retry Send**, **Open Email App (`mailto:ishantkohlikohli@gmail.com`)**, **Edit Draft**, and **Delete Draft** controls.

## 6. Network Activity & GitHub Releases Updater

Core PDF features require **no internet connection**. Outbound HTTPS requests occur only for:

1. **GitHub Releases Update Check:** Queries `https://api.github.com/repos/{owner}/{repo}/releases/latest` (defaulting to `ishantkohli/PDF-AIO`, configurable in Update Settings) on app launch, every 8 hours via background alarm (`UpdateCheckAlarmReceiver`, rescheduled after device boot), and whenever you manually tap **Check For Update**. Downloading an available `.apk` runs strictly over HTTPS (`usesCleartextTraffic="false"`).
2. **User-Initiated Feedback Form Submission:** Triggered only when you explicitly submit feedback in the Feedback Form.
3. **External Links:** Tapping links in Credits opens `https://ishantkohli.github.io/Web` or official social pages in your external browser or client apps.

## 7. Android Permissions Used

- **`CAMERA`:** Used solely when you open the in-app Document Scanner or QR Scanner.
- **`READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`), `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="29"`), & `MANAGE_EXTERNAL_STORAGE` (Android 11+, Optional):** Used to browse, scan, rename, and save PDF/TXT documents across Internal Storage and External Storage (SD cards). You may alternatively open individual files or folders via Android SAF without granting All Files Access.
- **`INTERNET`:** Used strictly for the GitHub Releases update checker and user-submitted Feedback Form.
- **`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, & `REQUEST_INSTALL_PACKAGES`:** Used for the 8-hour periodic update check notification and installing user-confirmed APK updates from GitHub Releases.

## 8. Android Backup Disabled

Cloud auto-backup is explicitly disabled (`android:allowBackup="false"`). PDF (AIO) maintains no user accounts, login systems, or cloud sync servers.

## 9. Third-Party Sharing

When you share an exported PDF, DOCX, or TXT file via Android's share sheet, PDF (AIO) grants temporary read permission through a scoped `FileProvider` (`${applicationId}.fileprovider`). Once transferred, the receiving app handles the file under its own terms and privacy policy.

## 10. Data Retention and Your Controls

You hold direct control over all local records:

- Delete library entries, annotations, bookmarks, signatures, or saved feedback drafts inside the app.
- Remove exported files directly from your device storage.
- Clear all app preferences and Room SQLite records via **Android Settings → Apps → PDF (AIO) → Storage → Clear Data**, or uninstall the app.  
See [`05-DATA-RETENTION-AND-DELETION.md`](05-DATA-RETENTION-AND-DELETION.md) for full lifecycle details.

## 11. Security Measures & Limitations

PDF (AIO) enforces HTTPS-only update downloads (`android:usesCleartextTraffic="false"`), scoped `FileProvider` sharing, content-stream/raster verification for PDF redaction, and AES-256 PDF export encryption. However, no software guarantees absolute security on compromised or rooted devices, and Room SQLite metadata (`pdf_aio.db`) is not additionally encrypted below the Android OS sandbox layer.

## 12. Children's Privacy

PDF (AIO) collects no personal profiles, analytics, or online accounts from users of any age. Parents or guardians should oversee device storage and external sharing choices if minors process sensitive school or personal documents.

## 13. Privacy Questions and Requests

Because Ishant Kohli does not store your documents or app databases on remote servers, deletion and access actions are performed directly on your device. For questions, bug reports, or privacy requests, email **ishantkohlikohli@gmail.com**.

## 14. Pending Pre-Publication Verification Items

- `[TODO: Confirm governing law jurisdiction and entity status before publishing to commercial app stores.]`
- `[TODO: Inspect live https://ishantkohli.github.io/Web server/hosting headers before extending zero-cookie statements unconditionally to all web pages.]`
