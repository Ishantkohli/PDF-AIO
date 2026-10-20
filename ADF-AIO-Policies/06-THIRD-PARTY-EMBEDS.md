# ADF AIO — Third-Party Services and External Links

**Last updated:** 20 October 2026  
**Contact:** ishantkohlikohli@gmail.com

This document distinguishes code integrations from external links. A dependency’s presence alone does not prove it is initialized or sends data.

## Google Gemini API — implementation present; release status unresolved
`GeminiClient.kt` constructs requests to `generativelanguage.googleapis.com`, and the document view model calls the AI client. The request includes a document title, an excerpt of up to 6,000 characters, the user's question, and up to four previous exchanges. The owner has said the intended AI provider is “none”; whether this integration is active in the distributed app is **unverified**. If active, disclose the transfer clearly before users invoke the feature and provide the applicable Google privacy information.

## Firebase libraries — activity unverified
Firebase-related dependencies appear in Gradle configuration. This review did not establish that Analytics or Crashlytics is initialized. Verify the release build and network traffic before describing either as active.

## External links
The app contains links to the operator’s website and social platforms including Instagram, GitHub, LinkedIn, X, WhatsApp, Reddit, YouTube, and Facebook. These are links; they are not by themselves proof of embedded trackers. Destination providers may process information when users open the links.

## Website embeds and hosting
The live website, hosting/CDN, scripts, and embeds have not been independently audited. Do not list providers merely because the app links to them. Update this document if a webview, embed, analytics SDK, crash SDK, advertising SDK, or other provider is enabled.

Contact: **ishantkohlikohli@gmail.com**
