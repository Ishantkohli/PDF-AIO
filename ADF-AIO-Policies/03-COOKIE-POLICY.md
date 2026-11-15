# PDF (AIO) — Cookie and Similar Technologies Policy

**Effective date:** 15 November 2026  
**Last updated:** 15 November 2026  
**Application:** PDF (AIO) (`com.aistudio.acrobatreader.hptwqz`, v5.3.0)  
**Companion Website:** https://ishantkohli.github.io/Web  
**Contact:** ishantkohlikohli@gmail.com

---

## 1. Android Application (PDF (AIO))

The **PDF (AIO)** Android application does **not** use browser cookies, advertising identifiers, cross-app tracking pixels, or analytics SDKs.

Instead, the App uses standard Android on-device storage solely for functional operation and user-chosen settings:

- **Room SQLite Database (`pdf_aio.db`, schema v2):** Tracks your local PDF library index, bookmarks, annotations, and saved signatures.
- **SharedPreferences:** Stores local app settings (`pdf_aio_theme_prefs`, `pdf_aio_storage_prefs`, `pdf_aio_updater_prefs` repository settings, and feedback drafts/status in `user_feedback_records`).

None of these local Android preferences are transmitted to advertising networks or analytics services.

## 2. Companion Website (https://ishantkohli.github.io/Web)

The developer intends `https://ishantkohli.github.io/Web` to operate without tracking cookies or third-party analytics.

`[TODO: Independently inspect live HTTP response headers, GitHub Pages hosting headers, and any third-party scripts or embeds on https://ishantkohli.github.io/Web before publishing an unconditional "zero cookies" guarantee for the website.]`

## 3. Third-Party Links Opened Outside the App

Tapping external social or website links in the Developer/Credits screen launches your device browser or external app. Any website you visit afterward may set its own cookies or storage technologies under its own privacy and cookie policies.

## 4. Contact

Questions regarding this policy may be sent to **ishantkohlikohli@gmail.com**.
