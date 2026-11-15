# PDF (AIO) — Consent and User Choice Notice

**Effective date:** 15 November 2026  
**Last updated:** 15 November 2026  
**Application:** PDF (AIO) (`com.aistudio.acrobatreader.hptwqz`, v5.3.0)  
**Website:** https://ishantkohli.github.io/Web  
**Contact:** ishantkohlikohli@gmail.com

---

## 1. Android App Consent Model (No Ad/Cookie Wall)

Because **PDF (AIO) v5.3.0** operates with **zero analytics SDKs, zero advertising identifiers, and zero browser cookies**, the Android application does **not** show intrusive third-party ad/tracking consent banners.

## 2. Explicit User-Controlled Permissions & Actions

Device permissions and external submissions operate under explicit Android OS controls or user action:

- **Document Scanner / QR Scanner (`CAMERA`):** Requested only when you open the camera scanner workflow.
- **Internal & External (SD Card) Storage Access:** Prompts you with transparent Android permissions (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`) for full library discovery, while also letting you use Android's zero-permission file picker / SAF folder picker at any time.
- **Feedback Form & Local Drafts:** Nothing is sent unless you explicitly fill out the form and tap **Submit Feedback to Developer**. Drafts are saved locally (`user_feedback_records`) so you can retry, open your email app, edit, or delete entries anytime.
- **Updates Notifications (`POST_NOTIFICATIONS`):** Requested transparently on Android 13+ so update availability alerts from the GitHub Releases checker can be displayed if granted.

## 3. Website Form & Browser Reference Guidance (`https://ishantkohli.github.io/Web`)

If optional web forms, analytics, or embeds are ever deployed on the companion website (`https://ishantkohli.github.io/Web`), the developer should follow the static consent pattern in `consent/consent-copy.html` ensuring:

- No optional tracking or marketing checkbox is pre-selected.
- Accept and Reject choices are equally accessible before any optional scripts load.
- Privacy acknowledgements clearly explain where any web form delivers submitted data.

`[TODO: Verify live https://ishantkohli.github.io/Web web form configuration before deploying any live consent banner there.]`
