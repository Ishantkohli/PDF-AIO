# ADF AIO — Data Collection Disclosure

**Last reviewed:** 20 October 2026  
**Operator:** Ishant Kohli  
**Website:** https://ishantkohli.me  
**Contact:** ishantkohlikohli@gmail.com  
**Jurisdiction stated by operator:** India

This disclosure reflects a static source review and owner answers. It is not a production-build or packet-capture audit.

| Category | Finding | Notes |
|---|---|---|
| Accounts | No account requirement found | Recheck if sign-in is added |
| Documents/files | Local selection, scanning, conversion, annotation, and export code exists | Broad storage permission requested; scope depends on OS and user permission |
| Local records | Room database and document/annotation models exist | Exact stored fields and deletion behavior need verification |
| General server uploads | Not established by this review | No general document-upload endpoint was confirmed |
| AI analysis | Gemini API implementation exists; activation conflicts with owner answer “AI provider: none” | If active, title, up to 6,000 characters of excerpt, question, and up to four previous exchanges are sent to Google |
| Analytics | Unverified | Owner unsure; dependencies alone do not prove collection |
| Crash reporting | Unverified | Check SDK initialization and release network traffic |
| Website cookies/analytics | Owner says none; not independently verified | Audit the live website and hosting setup |
| Android backup | Owner says disabled; source manifest sets `allowBackup=true` | Resolve before release |
| External links | Social and website links exist | Third-party processing may occur after a link is opened |
| Location | No location collection established in this review | Verify all permissions and runtime behavior |
| Forms/support/payment | Not established by this Android source review | Check website and other deployed services |

## Release checks
- Resolve the Gemini implementation/owner-confirmation conflict.
- Verify analytics and crash reporting.
- Resolve Android backup configuration.
- Inspect website cookies, scripts, embeds, logs, and network traffic.
- Verify local deletion, temporary-file handling, intended age range, and actual retention periods.
