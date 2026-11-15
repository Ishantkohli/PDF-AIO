package com.example.data.legal

/**
 * Structured legal policy section for in-app rendering, copying, and sharing.
 */
data class PolicySection(
    val heading: String,
    val body: String
)

/**
 * Represents one of the 10 official legal/privacy/security policy documents for PDF (AIO) v5.3.0,
 * synchronized 1:1 with the repository documentation in /ADF-AIO-Policies/.
 */
data class LegalPolicyDocument(
    val id: String,
    val number: Int,
    val fileName: String,
    val title: String,
    val shortBadge: String,
    val summary: String,
    val effectiveDate: String,
    val lastUpdated: String,
    val sections: List<PolicySection>
) {
    fun toPlainText(): String = buildString {
        appendLine("PDF (AIO) — $title")
        appendLine("Effective Date: $effectiveDate | Last Updated: $lastUpdated")
        appendLine("Developer: ${LegalPoliciesRepository.DEVELOPER_NAME} | Contact: ${LegalPoliciesRepository.DEVELOPER_EMAIL} | ${LegalPoliciesRepository.DEVELOPER_WEBSITE}")
        appendLine()
        sections.forEach { section ->
            appendLine(section.heading)
            appendLine(section.body)
            appendLine()
        }
    }
}

/**
 * Single source of truth for the In-App Legal & Privacy Center.
 * Synchronized factually with PDF (AIO) v5.3.0 (versionCode 530) source code and /ADF-AIO-Policies/.
 */
object LegalPoliciesRepository {
    const val APP_NAME = "PDF (AIO)"
    const val APP_ID = "com.aistudio.acrobatreader.hptwqz"
    const val APP_VERSION = "5.3.0"
    const val DEVELOPER_NAME = "Ishant Kohli"
    const val DEVELOPER_EMAIL = "ishantkohlikohli@gmail.com"
    const val DEVELOPER_WEBSITE = "https://ishantkohli.github.io/Web"
    const val REPOSITORY_URL = "https://github.com/Ishantkohli/PDF-AIO"
    const val EFFECTIVE_DATE = "15 November 2026"
    const val LAST_UPDATED = "15 November 2026"

    val documents: List<LegalPolicyDocument> by lazy {
        listOf(
            LegalPolicyDocument(
                id = "privacy_policy",
                number = 1,
                fileName = "01-PRIVACY-POLICY.md",
                title = "Privacy Policy",
                shortBadge = "PRIVACY",
                summary = "How PDF (AIO) v5.3.0 processes documents 100% locally, uses unencrypted Room SQLite inside Android's sandbox, and handles user-submitted feedback & GitHub updates.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. App Identity & Operator",
                        "Applies to PDF (AIO) ($APP_ID, v$APP_VERSION; formerly referenced as ADF AIO in earlier documentation), developed by $DEVELOPER_NAME. Contact: $DEVELOPER_EMAIL. Website: $DEVELOPER_WEBSITE. Pending legal confirmation: clarify whether operated solely as an individual developer or through a registered business entity prior to commercial store distribution."
                    ),
                    PolicySection(
                        "2. Core 100% Local Document Processing",
                        "PDF (AIO) executes all document reading, text selection, scanning, cropping, redaction, AES-256 encryption, splitting, merging, diff comparison, and Word/TXT/Image conversions strictly on your device. Zero AI APIs, zero machine-learning models, zero cloud OCR, zero analytics SDKs, zero advertising IDs, and zero Firebase/Supabase backends are included."
                    ),
                    PolicySection(
                        "3. On-Device Data & Honest Storage Disclosure",
                        "PDF, TXT, DOCX, DOC, and scanned image files stay in your selected folders, Storage Access Framework (SAF) URIs, or private app filesDir. Library metadata, bookmarks, annotations, and vector signatures are stored locally in Android Room SQLite (pdf_aio.db, schema v2). Honest Disclosure: Room SQLite uses standard unencrypted SQLite protected by Android OS per-app filesystem isolation, NOT database-level SQLCipher encryption. Password-protected PDF exports use AES-256 PDF encryption, and passwords are not stored in plaintext in the database."
                    ),
                    PolicySection(
                        "4. Caches, Share Staging & Sanitized Logs",
                        "Temporary share/export copies in cacheDir/share_staging/ expire automatically after 2 hours and are cleaned up on app initialization. Downloaded release APKs in cacheDir/app_updates/ are deleted after installation completes (UpdateCleanReceiver). Runtime logs avoid recording private document titles, file paths, URIs, or extracted text."
                    ),
                    PolicySection(
                        "5. Feedback Form & Local Drafts",
                        "Entering feedback inside Feedback Form stores a draft locally in private SharedPreferences (user_feedback_records). When you explicitly tap Submit Feedback to Developer, your entered form fields (name, problem description, update suggestion, app suggestion, and app version) are transmitted over HTTPS to ShipMyForm (https://shipmyform.com/to/$DEVELOPER_EMAIL). No PDF documents or private files are attached. If offline or delivery fails, the draft remains safely on-device with Retry Send, Email App, Edit, and Delete controls."
                    ),
                    PolicySection(
                        "6. GitHub Releases Updater & External Links",
                        "To notify you of new PDF (AIO) versions, the app queries https://api.github.com/repos/ishantkohli/PDF-AIO/releases/latest (configurable in Update Settings) on app launch, via an 8-hour periodic background check (UpdateCheckAlarmReceiver, rescheduled on boot), and when you tap Check For Update. Release APKs are fetched strictly over HTTPS. Tapping portfolio or social links in Credits opens $DEVELOPER_WEBSITE or external apps under their own policies."
                    ),
                    PolicySection(
                        "7. Permissions & Android Backup Disabled",
                        "Declares CAMERA (scanner & QR reader), READ_EXTERNAL_STORAGE (maxSdk 32) & WRITE_EXTERNAL_STORAGE (maxSdk 29) + optional MANAGE_EXTERNAL_STORAGE for Internal & External (SD Card) storage browsing, plus INTERNET, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, and REQUEST_INSTALL_PACKAGES for the GitHub updater and user-submitted feedback. android:allowBackup=\"false\" disables Android cloud auto-backup."
                    ),
                    PolicySection(
                        "8. User Controls, Deletion & Contact",
                        "You may delete library items, bookmarks, annotations, signatures, and saved feedback drafts directly in the app, remove exported files from storage, or erase all app data via Android App Settings -> Clear Data or Uninstall. Contact: $DEVELOPER_EMAIL."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "terms_conditions",
                number = 2,
                fileName = "02-TERMS-AND-CONDITIONS.md",
                title = "Terms & Conditions",
                shortBadge = "TERMS",
                summary = "Rules governing use of the pre-built PDF (AIO) application, user responsibility for outputs/redactions/passwords, and warranty disclaimers.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Service Scope & Eligibility",
                        "PDF (AIO) provides offline PDF reading, scanning, annotations, graphic signatures, splitting, merging, watermarking, compression, format conversion, diff comparison, and redaction tools. By using the App you confirm legal capacity in your jurisdiction or parent/guardian supervision where required."
                    ),
                    PolicySection(
                        "2. User Responsibility for Outputs & Security",
                        "You retain sole ownership and responsibility for your files: verify converted DOCX/TXT outputs and Myers diff reports before critical reliance; retain secure backups of AES-256 PDF passwords (unrecoverable by the developer); visually inspect redacted PDFs across independent viewers before sharing; and confirm whether electronic/graphic signatures meet legal requirements in your jurisdiction."
                    ),
                    PolicySection(
                        "3. Proprietary Ownership",
                        "Original Kotlin/Compose code, UI design, custom icons, and documentation are owned by $DEVELOPER_NAME under the PDF (AIO) Proprietary Software License (root LICENSE). Third-party open-source packages remain governed by their own licenses."
                    ),
                    PolicySection(
                        "4. Disclaimers & Liability",
                        "To the maximum extent permitted by law, PDF (AIO) is provided AS IS and AS AVAILABLE without warranties of uninterrupted availability or error-free conversion. Mandatory statutory consumer protections remain unaffected. Pending legal review: confirm governing law jurisdiction and dispute venue prior to commercial distribution."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "cookie_policy",
                number = 3,
                fileName = "03-COOKIE-POLICY.md",
                title = "Cookie Policy",
                shortBadge = "COOKIES",
                summary = "Zero browser cookies or ad trackers inside the Android app, plus transparent website audit notice for $DEVELOPER_WEBSITE.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Android Application Local Storage",
                        "The PDF (AIO) Android app uses zero browser cookies, zero ad identifiers, and zero analytics SDKs. On-device Room SQLite (pdf_aio.db) and SharedPreferences store only library metadata, theme selection (theme/pdf_aio_theme_prefs), storage access flags, updater repo preferences, and feedback drafts."
                    ),
                    PolicySection(
                        "2. Companion Website ($DEVELOPER_WEBSITE)",
                        "The developer intends $DEVELOPER_WEBSITE to avoid tracking cookies and invasive analytics. Pending verification: independently inspect live HTTP response headers, GitHub Pages hosting behavior, and any third-party scripts/embeds before publishing an unconditional web zero-cookie guarantee."
                    ),
                    PolicySection(
                        "3. External Links",
                        "Opening portfolio or social links launches external browsers or apps that apply their own cookie and privacy policies."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "cookie_form_consent",
                number = 4,
                fileName = "04-COOKIE-AND-FORM-CONSENT.md",
                title = "Consent & User Choice",
                shortBadge = "CONSENT",
                summary = "No intrusive cookie banners in the offline app, and explicit user action required for camera, storage, notifications, and feedback submission.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Android App Choice Model",
                        "PDF (AIO) contains no ad networks or analytics cookies, so it does not show third-party cookie walls inside Android. Sensitive permissions (CAMERA, POST_NOTIFICATIONS, Internal/External Storage access) require explicit OS grant, and you can continue using SAF file/folder picking if All Files Access is declined."
                    ),
                    PolicySection(
                        "2. Feedback Form Choice & Local Draft Control",
                        "Feedback submission occurs only when you intentionally fill the form and tap Submit Feedback to Developer. All responses also save locally as inspectable drafts you can edit, export via your email app, or delete at any time."
                    ),
                    PolicySection(
                        "3. Companion Website Reference Guidance",
                        "If optional web forms, analytics, or embeds are ever deployed on $DEVELOPER_WEBSITE, equal Accept/Reject choices without pre-ticked optional boxes should be used as modeled in consent/consent-copy.html."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "data_retention",
                number = 5,
                fileName = "05-DATA-RETENTION-AND-DELETION.md",
                title = "Data Retention & Deletion",
                shortBadge = "RETENTION",
                summary = "Exact on-device storage locations (pdf_aio.db, share_staging, app_updates, feedback drafts), auto-cleanup rules, and full wipe steps.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Local Store Inventory",
                        "Room SQLite (pdf_aio.db) retains library index records, bookmarks, annotations, and saved signatures until deleted by you. Share staging files (cacheDir/share_staging/) expire automatically after 2 hours. Downloaded APK updates (cacheDir/app_updates/) clean up upon package replacement or cancellation. Feedback drafts (user_feedback_records) persist on-device until deleted in the Feedback screen."
                    ),
                    PolicySection(
                        "2. Backup Disabled & Complete Erasure",
                        "android:allowBackup=\"false\" blocks Google Drive auto-backup. Tapping Clear Data in Android App Settings or uninstalling PDF (AIO) permanently wipes internal databases, preferences, drafts, and caches. Public files exported to Downloads/Documents/SD card remain under your control."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "third_party_services",
                number = 6,
                fileName = "06-THIRD-PARTY-EMBEDS.md",
                title = "Third-Party Services",
                shortBadge = "SERVICES",
                summary = "Accurate inventory of bundled offline libraries (PDFBox-Android, CameraX, Room), GitHub Releases updater, ShipMyForm feedback endpoint, and external links.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Bundled Offline Libraries",
                        "Apache PDFBox-Android (Apache-2.0), Android Jetpack Compose, Room SQLite, CameraX, and Coil execute locally inside the APK with zero telemetry or cloud document processing."
                    ),
                    PolicySection(
                        "2. GitHub Releases Updater",
                        "Uses GitHub's public Releases API (https://api.github.com/repos/ishantkohli/PDF-AIO/releases/latest) at startup, every 8 hours via UpdateCheckAlarmReceiver, and on manual check, downloading release APKs strictly over HTTPS."
                    ),
                    PolicySection(
                        "3. Feedback Form Delivery & External Links",
                        "Submitting the Feedback Form transmits only the typed feedback fields over HTTPS to ShipMyForm (https://shipmyform.com) for delivery to $DEVELOPER_EMAIL. Portfolio ($DEVELOPER_WEBSITE), UPI donation intents, and social links open external browsers or client apps under their own terms."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "proprietary_license",
                number = 7,
                fileName = "07-PROPRIETARY-LICENSE.md",
                title = "Proprietary License",
                shortBadge = "LICENSE",
                summary = "Copyright © 2026 Ishant Kohli. Pre-built app usage permitted; proprietary source code copying, derivative apps, and commercial reuse require written consent.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Permitted Use & Restrictions",
                        "Authoritative license terms live in root LICENSE. Copyright © 2026 $DEVELOPER_NAME. All rights reserved. You may install and use official pre-built binaries for lawful document workflows, but may not copy, redistribute, modify, sell, or build derivative applications from proprietary source code or branding without written permission ($DEVELOPER_EMAIL)."
                    ),
                    PolicySection(
                        "2. Third-Party Open-Source & Statutory Rights",
                        "Upstream open-source libraries (such as Apache PDFBox-Android under Apache-2.0) remain governed by their respective licenses. Mandatory statutory consumer and fair-use exceptions remain unaffected."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "acceptable_use",
                number = 8,
                fileName = "08-ACCEPTABLE-USE-POLICY.md",
                title = "Acceptable Use Policy",
                shortBadge = "AUP",
                summary = "Prohibitions on document forgery, malware distribution, brand impersonation, or unauthorized repackaging of PDF (AIO).",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Prohibited Conduct",
                        "You agree not to use PDF (AIO) to forge signatures or falsify legal/financial documents, distribute malicious payloads disguised as exports, strip copyright notices, redistribute modified or unauthorized builds, or spam GitHub or developer feedback endpoints. Report abuse to $DEVELOPER_EMAIL."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "security_disclosure",
                number = 9,
                fileName = "09-SECURITY-AND-VULNERABILITY-DISCLOSURE.md",
                title = "Security & Disclosure",
                shortBadge = "SECURITY",
                summary = "AES-256 export encryption, verified redaction, HTTPS-only update downloads, unencrypted Room boundary notice, and vulnerability reporting.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Implemented Controls",
                        "Offline local document processing, usesCleartextTraffic=false, HTTPS-only update downloads and redirects, scoped FileProvider URIs with ClipData, AES-256 PDF password encryption, content-stream/raster redaction verification (PdfRedactionEngine.verifySanitizedPdf), allowBackup=false, and sanitized diagnostic logs."
                    ),
                    PolicySection(
                        "2. Honest Boundary & Responsible Reporting",
                        "Room SQLite metadata (pdf_aio.db) uses Android OS sandbox isolation without SQLCipher DB encryption, and sensitive redacted files should also be visually checked in an external viewer. Report vulnerabilities responsibly to $DEVELOPER_EMAIL with subject [Security Report] PDF (AIO) v$APP_VERSION without attaching confidential third-party files."
                    )
                )
            ),
            LegalPolicyDocument(
                id = "accessibility_disclaimer",
                number = 10,
                fileName = "10-ACCESSIBILITY-AND-DISCLAIMER.md",
                title = "Accessibility & Disclaimer",
                shortBadge = "ACCESS",
                summary = "Material 3 accessibility practices, honest notice that formal WCAG auditing is pending, and output verification disclaimers.",
                effectiveDate = EFFECTIVE_DATE,
                lastUpdated = LAST_UPDATED,
                sections = listOf(
                    PolicySection(
                        "1. Accessibility Status (Honest Disclosure)",
                        "PDF (AIO) implements contentDescription labels, scalable typography, adaptive layouts, BackHandler navigation, 48dp minimum touch targets on primary controls, and 6 selectable themes (including Liquid Glass, Midnight Glass, OLED Deep Glass, Sunset Liquid Glass, Emerald Liquid Glass, and Frosted Crystal Glass). Formal WCAG 2.1 AA / Section 508 auditing across custom Canvas overlays and CameraX views has NOT yet been completed. Report barriers to $DEVELOPER_EMAIL."
                    ),
                    PolicySection(
                        "2. Accuracy & Interoperability Disclaimer",
                        "Verify converted DOCX/TXT outputs, Myers diff reports, and redactions before legal, medical, or regulatory reliance. Image-only scanned PDFs intentionally contain no text layer because PDF (AIO) runs zero cloud OCR or hidden AI/ML models."
                    )
                )
            )
        )
    }

    fun findById(id: String?): LegalPolicyDocument? = documents.firstOrNull { it.id == id }
}
