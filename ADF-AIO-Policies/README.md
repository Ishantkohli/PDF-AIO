# ADF AIO — Legal, Privacy & Compliance Documents

This folder contains draft policy and disclosure documents for ADF AIO.

**Important:** These are professional starting-point templates, not legal advice. They must be checked against the app's actual code, hosting, analytics, authentication, forms, third-party integrations, and business arrangements before publication. Do not claim that the app does or does not collect data until that has been verified.

## Included documents

1. `01-PRIVACY-POLICY.md` — privacy notice and user rights
2. `02-TERMS-AND-CONDITIONS.md` — user terms and service rules
3. `03-COOKIE-POLICY.md` — cookies, local storage, and similar technologies
4. `04-COOKIE-AND-FORM-CONSENT.md` — consent language and implementation checklist
5. `05-DATA-COLLECTION-DISCLOSURE.md` — plain-language data inventory template
6. `06-THIRD-PARTY-EMBEDS.md` — embedded content and external services
7. `07-PROPRIETARY-LICENSE.md` — restrictive app/source-code license draft
8. `08-ACCEPTABLE-USE-POLICY.md` — prohibited uses
9. `09-SECURITY-AND-DATA-RETENTION.md` — security, retention, deletion, and incident handling
10. `10-ACCESSIBILITY-AND-DISCLAIMER.md` — accessibility and general disclaimers
11. `consent/consent-copy.html` — accessible consent UI example
12. `PUBLISHING-CHECKLIST.md` — items to verify before publishing

## Replace these placeholders

Search all files for `[PLACEHOLDER: ...]` and fill them in, including:

- Legal owner / business name
- Contact email and privacy contact
- Effective date and last-updated date
- Applicable governing law and dispute location
- Hosting provider and any analytics/error-reporting services
- Actual data collected, purpose, retention period, and recipients
- Cookie/local-storage inventory and consent behavior
- Age eligibility and process for handling children's data
- Actual third-party embeds and external services

## Important implementation notes

- A policy does not make a non-compliant implementation compliant. The app must behave as described.
- Do not use pre-ticked optional consent boxes.
- Essential storage and optional analytics/marketing storage should be distinguished.
- Consent should be recorded with a timestamp, policy version, consent categories, and withdrawal/change mechanism where appropriate.
- Do not load non-essential cookies or third-party embeds before consent where consent is legally required.
- A restrictive source-code license should be paired with a clear copyright notice and should not claim rights over third-party or open-source components that the owner does not own.
- Obtain a qualified legal review for the jurisdictions in which the app is offered.
