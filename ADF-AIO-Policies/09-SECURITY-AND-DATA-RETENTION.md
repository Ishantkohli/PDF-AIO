# ADF AIO — Security and Data Retention

**Last updated:** 20 October 2026  
**Operator:** Ishant Kohli  
**Contact:** ishantkohlikohli@gmail.com

## Source findings
- Local document and annotation data models and a Room database are present.
- Internet and broad storage permissions are requested.
- The owner reports Android backup is disabled, but the manifest uses `android:allowBackup="true"` and references backup-rule files without explicit exclusions. Correct and test this discrepancy before release.
- A Gemini API integration exists in source, although the owner says no AI provider is intended. Confirm that it is disabled/removed or disclose it and secure the integration.
- Analytics/crash-reporting configuration is unverified.

## Retention
No verified fixed retention periods are available for local records, temporary files, exports, logs, or provider-side processing. Do not publish specific periods until established. Document how users can delete documents and app data.

## Recommended actions
1. Configure and test Android backup behavior on supported versions.
2. Minimize storage permissions; consider Android’s document picker where feasible.
3. Do not embed a reusable secret API key in a public mobile app. Use a backend and restrict credentials if remote AI is enabled.
4. Verify release-build network traffic and remove unused SDKs.
5. Test deletion of local documents, annotations, caches, temporary files, and exports.
6. Maintain a dependency inventory and a security response process.

This draft is not a certification that the app is secure.
