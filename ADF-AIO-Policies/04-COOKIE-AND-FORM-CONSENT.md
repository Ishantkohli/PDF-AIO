# Cookie and Form Consent

**Effective date:** [PLACEHOLDER: 2026-10-20]

This document defines recommended consent language and implementation requirements for ADF AIO. Use only the options that correspond to features actually present in the App.

## 1. Cookie banner copy

**Heading:** Your privacy choices

**Message:** ADF AIO may use essential storage to keep the app working. With your permission, we may also use optional technologies for preferences, analytics, or embedded content. You can accept all optional categories, reject optional categories, or choose settings. You can change your choice later.

**Buttons:**
- Accept all
- Reject optional
- Customize

Essential technologies should be separated from optional categories. Do not make “Accept all” the only practical option.

## 2. Suggested preference categories

- **Essential — always active:** required for a feature you request, security, or core operation.
- **Preferences — optional:** saves non-essential preferences.
- **Analytics — optional:** usage measurement and performance analysis.
- **Marketing — optional:** advertising or marketing measurement, only if used.
- **External embeds — optional where required:** third-party content that may contact an external provider.

Keep optional categories off by default where prior consent is required.

## 3. Form consent copy

For a general contact or feedback form:

> **Required acknowledgement:** I have read the Privacy Policy and understand how ADF AIO will use the information I submit to respond to my request.

Use an acknowledgement only if it accurately describes the form. A privacy-policy acknowledgement is not automatically the legal basis for processing.

For optional marketing communications, use a separate unchecked box:

> **Optional:** I would like to receive product updates and announcements from ADF AIO. I can unsubscribe at any time.

Do not bundle marketing consent with a support request or make it a condition of using an unrelated feature.

## 4. Consent records

Where appropriate and lawful, record:
- Consent timestamp (UTC).
- Policy/banner version.
- Categories accepted or rejected.
- Consent method and relevant configuration.
- Withdrawal or preference changes.

Collect only what is necessary to demonstrate the user's choice. Avoid storing unnecessary identifying data or fingerprinting users.

## 5. Implementation checklist

- No optional category is preselected.
- Accept and reject options are comparably easy to use.
- Optional scripts and embeds do not load before required consent.
- The user can reopen settings and withdraw consent.
- Consent controls are keyboard accessible and work with assistive technology.
- The banner does not repeatedly reappear after a valid choice unless the consent expires or the policy materially changes.
- Do not use a consent wall unless legally justified.
- Ensure consent storage itself does not depend on optional analytics.
- Test in a clean browser profile and inspect network requests before and after consent.

## 6. Important

This document is not proof that the current application already implements these controls. The code must be updated and tested to match the published behavior.
