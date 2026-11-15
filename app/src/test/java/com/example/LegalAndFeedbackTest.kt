package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.FeedbackDispatcher
import com.example.data.FeedbackDraft
import com.example.data.legal.LegalConsentManager
import com.example.data.legal.LegalPoliciesRepository
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.screens.LegalConsentGateScreen
import com.example.ui.screens.LegalPrivacyCenterScreen
import com.example.ui.theme.AcrobatTheme
import com.example.update.UpdateCheckAlarmReceiver
import com.example.update.UpdateViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LegalAndFeedbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_feedback_records", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        LegalConsentManager.clearConsent(context)
    }

    @Test
    fun `legal policies repository contains all 10 consistent policies with honest disclosures`() {
        val docs = LegalPoliciesRepository.documents
        assertEquals(10, docs.size)
        assertEquals((1..10).toList(), docs.map { it.number })

        docs.forEach { doc ->
            assertTrue("Missing title for ${doc.id}", doc.title.isNotBlank())
            assertTrue("Missing sections for ${doc.id}", doc.sections.isNotEmpty())
            val fullText = doc.toPlainText()
            assertTrue("${doc.id} must identify PDF (AIO)", fullText.contains("PDF (AIO)"))
            assertTrue("${doc.id} must identify Ishant Kohli", fullText.contains("Ishant Kohli"))
            assertTrue("${doc.id} must identify contact email", fullText.contains("ishantkohlikohli@gmail.com"))
            assertTrue("${doc.id} must use official website URL", fullText.contains("https://ishantkohli.github.io/Web"))
            assertFalse("${doc.id} must never reference deprecated competitor branding", fullText.contains("Adobe", ignoreCase = true))
        }

        val privacyText = LegalPoliciesRepository.findById("privacy_policy")!!.toPlainText()
        assertTrue(privacyText.contains("pdf_aio.db"))
        assertTrue(privacyText.contains("NOT database-level SQLCipher encryption"))
        assertTrue(privacyText.contains("cacheDir/share_staging/"))
        assertTrue(privacyText.contains("allowBackup=\"false\""))
        assertFalse(privacyText.contains("encrypted Room SQLite database"))
    }

    @Test
    fun `feedback dispatcher rejects empty input and reports failure honestly while keeping local draft`() = runBlocking {
        // 1. Blank input must fail validation cleanly
        val emptyRes = FeedbackDispatcher.dispatchFeedbackWithResult(
            context = context,
            name = "Tester",
            problemsFaced = "   ",
            updateSuggestion = "",
            appSuggestion = ""
        )
        assertFalse(emptyRes.remoteDelivered)
        assertFalse(emptyRes.savedLocally)
        assertNull(emptyRes.draft)

        // 2. Simulated HTTP failure / offline MUST NOT report remoteDelivered = true, and MUST save local draft
        val failRes = FeedbackDispatcher.dispatchFeedbackWithResult(
            context = context,
            name = "Ishant Tester",
            problemsFaced = "Sample issue in viewer",
            updateSuggestion = "Add keyboard shortcuts",
            appSuggestion = "Great dark glass theme",
            httpPoster = { _, _ -> 503 to "Service unavailable (offline)" }
        )
        assertFalse(failRes.remoteDelivered)
        assertTrue(failRes.savedLocally)
        assertNotNull(failRes.draft)
        assertEquals(FeedbackDraft.STATUS_SEND_FAILED, failRes.draft!!.deliveryStatus)

        val draftsAfterFail = FeedbackDispatcher.loadDrafts(context)
        assertEquals(1, draftsAfterFail.size)
        assertEquals(failRes.draft!!.id, draftsAfterFail[0].id)

        // 3. Simulated HTTP 200 retry succeeds and updates status to SENT
        val okRes = FeedbackDispatcher.dispatchFeedbackWithResult(
            context = context,
            name = "Ishant Tester",
            problemsFaced = "Sample issue in viewer",
            updateSuggestion = "Add keyboard shortcuts",
            appSuggestion = "Great dark glass theme",
            existingDraftId = failRes.draft!!.id,
            httpPoster = { _, _ -> 200 to null }
        )
        assertTrue(okRes.remoteDelivered)
        assertEquals(FeedbackDraft.STATUS_SENT, okRes.draft!!.deliveryStatus)

        // 4. Local deletion removes the draft cleanly
        assertTrue(FeedbackDispatcher.deleteDraft(context, okRes.draft!!.id))
        assertTrue(FeedbackDispatcher.loadDrafts(context).isEmpty())
    }

    @Test
    fun `feedback email intent targets developer address with formatted summary`() {
        val draft = FeedbackDraft(
            id = "draft-1",
            name = "Alice",
            problemsFaced = "Export spacing glitch",
            updateSuggestion = "",
            appSuggestion = "Love local privacy",
            updatedAtMillis = 1763200000000L
        )
        val intent = FeedbackDispatcher.buildEmailIntent(draft)
        assertEquals("mailto", intent.data?.scheme)
        assertTrue(intent.data?.schemeSpecificPart?.startsWith("ishantkohlikohli@gmail.com") == true)
        val body = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        assertTrue(body.contains("Export spacing glitch"))
        assertTrue(body.contains("Love local privacy"))
    }

    @Test
    fun `manifest preserves storage permissions and update alarm receiver configuration`() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS
        )
        val perms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        assertTrue(perms.contains(android.Manifest.permission.CAMERA))
        assertTrue(perms.contains(android.Manifest.permission.INTERNET))
        assertTrue(perms.contains(android.Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(perms.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES))
        assertTrue(perms.contains(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE))
        assertTrue(perms.contains(android.Manifest.permission.RECEIVE_BOOT_COMPLETED))
        assertTrue(perms.contains(android.Manifest.permission.SCHEDULE_EXACT_ALARM))

        val receiverNames = pkgInfo.receivers?.map { it.name } ?: emptyList()
        assertTrue(receiverNames.contains(UpdateCheckAlarmReceiver::class.java.name))

        // Periodic scheduler executes safely without throwing
        UpdateCheckAlarmReceiver.schedulePeriodicCheck(context)
        UpdateCheckAlarmReceiver.cancelScheduledAlarm(context)
    }

    @Test
    @Config(sdk = [29])
    fun `manifest requests legacy internal and external SD card storage permissions on API 29`() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet() ?: emptySet()
        assertTrue(perms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(perms.contains(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test
    fun `legal privacy center displays all policies and navigates to detail view`() {
        composeRule.setContent {
            AcrobatTheme {
                LegalPrivacyCenterScreen(onBack = {})
            }
        }

        composeRule.onNodeWithTag("legal_policy_index_list").assertIsDisplayed()
        composeRule.onNodeWithTag("legal_policy_card_privacy_policy").assertIsDisplayed()
        composeRule.onNodeWithTag("legal_policy_card_privacy_policy").performClick()
        composeRule.onNodeWithTag("legal_policy_detail_privacy_policy").assertIsDisplayed()
        composeRule.onNodeWithText("1. App Identity & Operator").assertIsDisplayed()
    }

    @Test
    fun `pdf and update viewModels initialize cleanly without stateFlow NPE`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pdfVm = PdfViewModel(app)
        val updateVm = UpdateViewModel(app)

        assertEquals(AppScreen.HOME, pdfVm.currentScreen.value)
        pdfVm.navigateTo(AppScreen.LEGAL_CENTER)
        assertEquals(AppScreen.LEGAL_CENTER, pdfVm.currentScreen.value)
        assertNotNull(updateVm.updateState.value)
    }

    @Test
    fun `buildConfig and runtime classpath exclude Gemini API keys and AI or Firebase SDKs`() {
        val buildConfigFields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse(buildConfigFields.any { it.contains("GEMINI", ignoreCase = true) || it.contains("FIREBASE", ignoreCase = true) })

        val forbiddenClasses = listOf(
            "com.google.firebase.Firebase",
            "com.google.firebase.ai.FirebaseAI",
            "com.google.ai.client.generativeai.GenerativeModel"
        )
        forbiddenClasses.forEach { fqcn ->
            val exists = try {
                Class.forName(fqcn)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
            assertFalse("Unexpected AI/Firebase class on classpath: $fqcn", exists)
        }
    }

    @Test
    fun `legal consent manager triggers on first install, persists same version, and re-prompts on update or reinstall`() {
        // Fresh install: requires consent with FIRST_INSTALL
        assertTrue(LegalConsentManager.needsConsent(context, currentVersionCode = 530L))
        assertEquals(
            LegalConsentManager.ConsentTriggerReason.FIRST_INSTALL,
            LegalConsentManager.getTriggerReason(context, currentVersionCode = 530L)
        )

        // Accepting persists consent so subsequent launches on same version skip the gate
        assertTrue(LegalConsentManager.recordAcceptance(context, currentVersionCode = 530L, currentVersionName = "5.3.0"))
        assertFalse(LegalConsentManager.needsConsent(context, currentVersionCode = 530L))

        // App update to newer versionCode re-triggers with APP_UPDATE
        assertTrue(LegalConsentManager.needsConsent(context, currentVersionCode = 531L))
        assertEquals(
            LegalConsentManager.ConsentTriggerReason.APP_UPDATE,
            LegalConsentManager.getTriggerReason(context, currentVersionCode = 531L)
        )

        // Reinstall (cleared prefs because allowBackup=false) resets to FIRST_INSTALL
        LegalConsentManager.clearConsent(context)
        assertTrue(LegalConsentManager.needsConsent(context, currentVersionCode = 530L))
        assertEquals(
            LegalConsentManager.ConsentTriggerReason.FIRST_INSTALL,
            LegalConsentManager.getTriggerReason(context, currentVersionCode = 530L)
        )
    }

    @Test
    fun `legal consent gate requires checkbox before enabling Accept and handles Deny`() {
        var acceptedCount = 0
        var deniedCount = 0

        composeRule.setContent {
            AcrobatTheme {
                LegalConsentGateScreen(
                    triggerReason = LegalConsentManager.ConsentTriggerReason.FIRST_INSTALL,
                    onAccept = { acceptedCount++ },
                    onDeny = { deniedCount++ }
                )
            }
        }

        // Full readable text (Privacy Policy + Terms & Conditions) & checkbox & 2 buttons are present
        composeRule.onNodeWithTag("legal_consent_full_text_list")
            .assertIsDisplayed()
            .performScrollToNode(hasText("1. App Identity & Operator"))
        composeRule.onNodeWithText("1. App Identity & Operator").assertIsDisplayed()
        composeRule.onNodeWithTag("legal_consent_full_text_list")
            .performScrollToNode(hasText("2. User Responsibility for Outputs & Security"))
        composeRule.onNodeWithText("2. User Responsibility for Outputs & Security").assertIsDisplayed()
        composeRule.onNodeWithText("I Accept All The Terms , Condition , Policy Mention Above").assertIsDisplayed()

        // Checkbox starts unchecked; Accept disabled; Deny enabled
        composeRule.onNodeWithTag("legal_consent_checkbox").assertIsOff()
        composeRule.onNodeWithTag("legal_consent_accept_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("legal_consent_deny_button").assertIsEnabled()

        // Tapping Deny triggers onDeny immediately without accepting
        composeRule.onNodeWithTag("legal_consent_deny_button").performClick()
        assertEquals(1, deniedCount)
        assertEquals(0, acceptedCount)

        // Checking the confirmation box enables Accept and triggers onAccept
        composeRule.onNodeWithTag("legal_consent_checkbox").performClick()
        composeRule.onNodeWithTag("legal_consent_checkbox").assertIsOn()
        composeRule.onNodeWithTag("legal_consent_accept_button").assertIsEnabled()
        composeRule.onNodeWithTag("legal_consent_accept_button").performClick()
        assertEquals(1, acceptedCount)
    }
}
