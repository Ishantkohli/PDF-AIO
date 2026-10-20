# ADF AIO — Privacy Policy

**Effective date:** 20 October 2026  
**Last updated:** 20 October 2026  
**Operator:** Ishant Kohli  
**Website:** https://ishantkohli.me  
**Privacy contact:** ishantkohlikohli@gmail.com

> **Draft — do not publish until the verification items at the end are resolved.** This notice is based on a static source-code review and owner-provided answers, not a live network or production-build audit.

## 1. Who operates ADF AIO?
ADF AIO is operated by Ishant Kohli. Contact: **ishantkohlikohli@gmail.com**. Website: **https://ishantkohli.me**. 

## 2. Information handled
- **Documents and files:** The app includes document selection/scanning, PDF conversion, annotation, and export functionality. Selected or discovered files may be processed on-device. The app requests broad storage access on supported Android versions.
- **Local data:** Source code includes a Room database and document/annotation models. Exact stored fields and deletion behavior require further verification.
- **AI document analysis — important discrepancy:** Source code implements a Gemini API request. The request includes a document title, an excerpt of up to 6,000 characters, the user’s question, and up to four previous conversation exchanges. The owner has said the intended AI provider is “none,” so this policy does not claim Gemini is active in the released app. If the feature is enabled in any release, this policy must be updated before release and users must be told what is sent to Google.
- **Analytics and crash reports:** Unverified. Firebase-related dependencies exist, but their presence alone does not prove analytics or crash reporting is active.
- **Website data:** The owner reports no website cookies or analytics; this has not been independently verified against the live site, hosting, scripts, or embeds.

No account requirement was identified in the reviewed implementation. Recheck if account features are added.

## 3. Uses
Local files may be processed to provide the document features a user selects. Any cloud processing, telemetry, analytics, or other collection must be verified and disclosed before it is enabled.

## 4. Third parties
The app contains links to social platforms and the operator’s website. Opening those links takes the user to third-party services, which apply their own privacy notices. A Gemini integration exists in the source; its release status is unresolved. If active, it transmits the document title, excerpt, question, and recent conversation context to Google for AI analysis. Do not claim that documents always stay on-device unless the AI feature is disabled and this has been tested.

Firebase Analytics/Crashlytics activity is unverified.

## 5. Storage, backup, and retention
The owner reports Android backup is disabled. Everything is Locally Stored or used .

Some data may remain locally until deleted by the user, the app, or Android’s app-data removal process. Exact retention and deletion behavior for every document, annotation, export, temporary file, and log has not been fully verified. No fixed server-side retention period is claimed here.

## 6. Permissions
The app requests internet access and broad storage permissions. Users can manage permissions through Android settings; removing a permission may limit features that depend on it.

## 7. Security
We aim to use reasonable safeguards, but no system can be guaranteed completely secure.
## 8. Children
The intended age range has not been confirmed. Because This App is useable for all type of Age

## 9. Privacy requests
Contact **ishantkohlikohli@gmail.com** with privacy questions or requests. We may need to verify a request and legal exceptions may apply.

## 10. Changes
This notice should be updated whenever data handling, permissions, providers, or hosting practices change.

