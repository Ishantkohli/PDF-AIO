# ADF AIO — Source-Code Audit Notes

**Audit date:** 20 October 2026  
**Scope:** Static inspection of the supplied ZIP only. No installation, dynamic testing, packet capture, or independent website audit was performed.

## Owner-confirmed details
- Operator: Ishant Kohli
- Contact: ishantkohlikohli@gmail.com
- Website: https://ishantkohli.me
- Jurisdiction stated: India
- Owner reports no website cookies/analytics.
- Owner reports Android backup disabled.
- Analytics/crash reporting: unknown.
- Owner says intended AI provider is none; their document-upload answer says AI-only.

## Discrepancies and risks
1. **Gemini implementation exists.** `GeminiClient.kt` creates a request to Google Gemini, and `PdfViewModel.kt` calls it. If used, the request includes document title, up to 6,000 characters of document excerpt, user question, and up to four prior conversation exchanges. Resolve this conflict before publishing a policy or releasing the feature.
2. **Backup setting conflicts with owner answer.** The manifest sets `android:allowBackup="true"` and backup-rule XML has no explicit exclusions. Verify final behavior and correct the build.
3. **Analytics/crash reporting is unresolved.** Firebase-related dependencies exist, but this does not prove Analytics or Crashlytics is initialized.
4. **Website cookies are unverified.** Owner says none; the live site and hosting configuration were not audited.
5. **Broad storage permission.** `MANAGE_EXTERNAL_STORAGE` is requested. Review necessity and distribution-policy requirements; consider narrower file-picker access.
6. **API key exposure.** The Gemini implementation reads `BuildConfig.GEMINI_API_KEY`. A key shipped in an Android app can be extracted; consider a backend and restrictions.

## This audit does not prove
- That every discovered file is uploaded.
- That analytics, crash reporting, ads, location collection, or website tracking is active.
- Provider-side retention, exact local deletion behavior, or legal compliance.

## Before release
- [ ] Disable/remove Gemini or confirm it is active and disclose it accurately.
- [ ] Resolve backup configuration.
- [ ] Verify Firebase initialization and network traffic.
- [ ] Audit live website cookies, scripts, embeds, and hosting.
- [ ] Confirm retention/deletion, age range, and legal operator details.
- [ ] Obtain legal review for applicable Indian requirements.
