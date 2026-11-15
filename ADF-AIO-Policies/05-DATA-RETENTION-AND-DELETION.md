# PDF (AIO) — Data Retention and Deletion Policy

**Effective date:** 15 November 2026  
**Last updated:** 15 November 2026  
**Application:** PDF (AIO) (`com.aistudio.acrobatreader.hptwqz`, v5.3.0)  
**Operator:** Ishant Kohli  
**Contact:** ishantkohlikohli@gmail.com

---

## 1. Purpose

This policy documents every category of local data created or stored by **PDF (AIO)** on your Android device, how long each item is retained, and how you can delete it permanently. PDF (AIO) maintains **no remote user document databases or cloud accounts**.

## 2. Inventory of Local Data & Retention Lifecycle

| Data Category | Storage Mechanism | Retention Period | How to Delete |
| :--- | :--- | :--- | :--- |
| **Library Index, Bookmarks & Annotations** | Room SQLite (`pdf_aio.db`, schema v2) | Until deleted by you, App Data cleared, or App uninstalled | Delete document/bookmark/annotation inside App, or Android **Clear Data** / Uninstall |
| **Saved Handwritten / Seal Signatures** | Room SQLite (`signatures` table) | Until deleted in Signature Vault or App uninstalled | Delete signature inside **Signature Vault**, or Android **Clear Data** / Uninstall |
| **User-Created Exports & Converted PDFs/DOCX/TXT** | Device storage / SD Card / SAF URI / App `filesDir` | Retained permanently until deleted by you | Delete via system File Manager or App Library |
| **Share Staging Files (`share_staging/`)** | App `cacheDir/share_staging/` | Auto-deleted on app launch & share prep when older than **2 hours** | Automatic cleanup or Android **Clear Cache** |
| **Downloaded Update APK (`app_updates/`)** | App `cacheDir/app_updates/` | Cleaned automatically on package replacement (`UpdateCleanReceiver`) or cancellation | Automatic on install/cancel, or Android **Clear Cache** |
| **Feedback Drafts (`user_feedback_records`)** | Private `SharedPreferences` | Retained locally until deleted by you or App uninstalled | Tap **Delete Draft** / trash icon in Feedback screen, or **Clear Data** |
| **App Preferences (Theme, Storage Grant Flag, Update Repo)** | Private `SharedPreferences` | Until changed, App Data cleared, or App uninstalled | Android **Clear Data** or Uninstall |

## 3. Zero Cloud Backup

`android:allowBackup="false"` is configured in `AndroidManifest.xml`, preventing Android Auto Backup from copying internal app databases or preferences to Google Drive cloud backups.

## 4. Permanent Wipe Instructions

To immediately erase all local library metadata, annotations, bookmarks, signatures, feedback drafts, caches, and preferences held by PDF (AIO):

1. Open **Android Settings → Apps → PDF (AIO) → Storage & Cache**.
2. Tap **Clear Storage / Clear Data**, or uninstall the application.

*(Note: Files you explicitly exported into public Internal Storage folders or external SD cards remain under your ownership until you delete them separately.)*

## 5. Questions

Contact **ishantkohlikohli@gmail.com** for questions about local storage behavior.
