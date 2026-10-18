# What Data Does ADF AIO Collect?

**Last verified:** [PLACEHOLDER: YYYY-MM-DD]  
**Owner:** [PLACEHOLDER: legal name or business name]

This page is intended to give users a plain-language explanation of data practices. Complete it only after reviewing the deployed app, source code, hosting configuration, network requests, and all third-party providers.

## Data inventory

| Data category | Collected? | Why | Where stored / recipient | Retention |
|---|---|---|---|---|
| Contact or feedback form details | [Yes/No/Feature-dependent] | Respond to requests | [Provider/system] | [Period] |
| Account details | [Yes/No] | Authentication/account operation | [Provider/system] | [Period] |
| Device/browser information | [Yes/No/Provider-dependent] | Compatibility/security | [Provider/system] | [Period] |
| Server/security logs | [Yes/No/Hosting-dependent] | Security and troubleshooting | [Hosting provider] | [Period] |
| Cookies/local/session storage | [Yes/No; list names] | [Purpose] | User's browser / provider | [Duration] |
| Analytics events | [Yes/No] | [Purpose] | [Analytics provider] | [Period] |
| Location data | [Yes/No] | [Purpose, if any] | [Recipient] | [Period] |
| Uploaded files | [Yes/No] | [Feature purpose] | [Storage provider] | [Period] |
| Payment data | [Yes/No] | [Purpose, if applicable] | [Payment provider] | [Period] |
| Consent records | [Yes/No] | Record privacy choices | [System] | [Period] |

## Information we do not intentionally request

List information the app does not need and should not ask users to submit, such as passwords for unrelated third-party accounts, government ID numbers, or sensitive personal details. Do not promise that such information can never appear in technical logs unless verified.

## External platforms

If a user clicks an Instagram, GitHub, LinkedIn, X, YouTube, WhatsApp, Facebook, Reddit, or other external link, the destination platform may process data according to its own policies. A simple outbound link is different from an embedded widget; document the actual behavior.

## How to request access or deletion

Contact [PLACEHOLDER: privacy email] with a description of your request. We may need to verify your identity and may retain certain records where law permits or requires it.

## Audit notes before publication

1. Inspect frontend code for `fetch`, form submissions, analytics tags, pixels, external scripts, and embeds.
2. Inspect cookies, local storage, session storage, IndexedDB, and service workers.
3. Review hosting/CDN/server logs and their retention.
4. Review API providers and processors.
5. Test the app in a fresh browser profile and inspect network traffic.
6. Update every table entry to reflect actual behavior.
