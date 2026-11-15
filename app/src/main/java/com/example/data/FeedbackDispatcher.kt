package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a user-authored feedback draft saved locally on-device.
 */
data class FeedbackDraft(
    val id: String,
    val name: String,
    val problemsFaced: String,
    val updateSuggestion: String,
    val appSuggestion: String,
    val updatedAtMillis: Long,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deliveryStatus: String = STATUS_LOCAL_DRAFT
) {
    fun hasFeedbackBody(): Boolean {
        return problemsFaced.isNotBlank() || updateSuggestion.isNotBlank() || appSuggestion.isNotBlank()
    }

    companion object {
        const val STATUS_LOCAL_DRAFT = "LOCAL_DRAFT"
        const val STATUS_SENT = "SENT"
        const val STATUS_SEND_FAILED = "SEND_FAILED"
    }
}

/**
 * Structured feedback dispatch result so UI never reports false remote success
 * when offline or when HTTP submission fails.
 */
data class FeedbackDispatchResult(
    val remoteDelivered: Boolean,
    val savedLocally: Boolean,
    val draft: FeedbackDraft?,
    val statusCode: Int = 0,
    val errorMessage: String? = null
)

/**
 * Feedback submission & local draft manager for PDF (AIO).
 *
 * Saves every submission locally as a structured draft in SharedPreferences (`user_feedback_records`)
 * before attempting HTTPS delivery to the developer feedback form endpoint (`shipmyform.com` /
 * optional `web3forms.com` if configured), and reports true HTTP status back to the UI so failed
 * or offline submissions never masquerade as delivered.
 */
object FeedbackDispatcher {
    private const val TAG = "FeedbackDispatcher"
    private const val PREFS_NAME = "user_feedback_records"
    private const val KEY_DRAFTS_JSON = "feedback_drafts_v2"
    const val DEVELOPER_EMAIL = "ishantkohlikohli@gmail.com"
    const val SHIPMYFORM_ENDPOINT = "https://shipmyform.com/to/ishantkohlikohli@gmail.com"

    /**
     * Submits feedback remotely and saves local backup draft. Returns `true` ONLY if
     * HTTP delivery actually succeeded (2xx).
     */
    suspend fun dispatchFeedback(
        context: Context,
        name: String,
        problemsFaced: String,
        updateSuggestion: String,
        appSuggestion: String
    ): Boolean {
        return dispatchFeedbackWithResult(
            context = context,
            name = name,
            problemsFaced = problemsFaced,
            updateSuggestion = updateSuggestion,
            appSuggestion = appSuggestion
        ).remoteDelivered
    }

    /**
     * Dispatches feedback with complete delivery telemetry and local draft persistence.
     * [httpPoster] allows deterministic testing in JVM/Robolectric without hitting external hosts.
     */
    suspend fun dispatchFeedbackWithResult(
        context: Context,
        name: String,
        problemsFaced: String,
        updateSuggestion: String,
        appSuggestion: String,
        existingDraftId: String? = null,
        httpPoster: (suspend (endpoint: String, payload: Map<String, String>) -> Pair<Int, String?>)? = null
    ): FeedbackDispatchResult = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanProblems = problemsFaced.trim()
        val cleanUpdate = updateSuggestion.trim()
        val cleanApp = appSuggestion.trim()

        if (cleanProblems.isEmpty() && cleanUpdate.isEmpty() && cleanApp.isEmpty()) {
            return@withContext FeedbackDispatchResult(
                remoteDelivered = false,
                savedLocally = false,
                draft = null,
                errorMessage = "Please enter a problem report, update idea, or app feedback before submitting."
            )
        }

        val initialSave = saveDraft(
            context = context,
            name = cleanName,
            problemsFaced = cleanProblems,
            updateSuggestion = cleanUpdate,
            appSuggestion = cleanApp,
            existingDraftId = existingDraftId,
            deliveryStatus = FeedbackDraft.STATUS_LOCAL_DRAFT
        )
        val savedDraft = initialSave.getOrNull()

        val payload = linkedMapOf(
            "subject" to "PDF (AIO) v${BuildConfig.VERSION_NAME} Feedback",
            "name" to (cleanName.ifBlank { "PDF (AIO) User" }),
            "problems_faced" to (cleanProblems.ifBlank { "None reported" }),
            "update_suggestion" to (cleanUpdate.ifBlank { "None reported" }),
            "app_suggestion" to (cleanApp.ifBlank { "None reported" }),
            "app_version" to BuildConfig.VERSION_NAME
        )

        val (statusCode, failureReason) = try {
            if (httpPoster != null) {
                httpPoster.invoke(SHIPMYFORM_ENDPOINT, payload)
            } else {
                postFormHttps(SHIPMYFORM_ENDPOINT, payload)
            }
        } catch (e: Exception) {
            0 to (e.localizedMessage ?: "Network unavailable")
        }

        val delivered = statusCode in 200..299
        val finalStatus = if (delivered) FeedbackDraft.STATUS_SENT else FeedbackDraft.STATUS_SEND_FAILED
        val finalDraft = if (savedDraft != null) {
            saveDraft(
                context = context,
                name = savedDraft.name,
                problemsFaced = savedDraft.problemsFaced,
                updateSuggestion = savedDraft.updateSuggestion,
                appSuggestion = savedDraft.appSuggestion,
                existingDraftId = savedDraft.id,
                deliveryStatus = finalStatus
            ).getOrDefault(savedDraft.copy(deliveryStatus = finalStatus))
        } else {
            null
        }

        FeedbackDispatchResult(
            remoteDelivered = delivered,
            savedLocally = finalDraft != null,
            draft = finalDraft,
            statusCode = statusCode,
            errorMessage = if (delivered) null else (failureReason ?: "Server returned HTTP $statusCode")
        )
    }

    private fun postFormHttps(endpoint: String, payload: Map<String, String>): Pair<Int, String?> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("User-Agent", "PDF-AIO/${BuildConfig.VERSION_NAME} (Android)")
            }

            val encodedBody = payload.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(encodedBody)
                writer.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                code to null
            } else {
                code to "Remote delivery failed (HTTP $code: ${connection.responseMessage ?: "Error"})"
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTPS feedback submission failed")
            0 to (e.localizedMessage ?: "Offline or unable to reach feedback server")
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {}
        }
    }

    suspend fun saveDraft(
        context: Context,
        name: String,
        problemsFaced: String,
        updateSuggestion: String,
        appSuggestion: String,
        existingDraftId: String? = null,
        deliveryStatus: String = FeedbackDraft.STATUS_LOCAL_DRAFT
    ): Result<FeedbackDraft> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanProblems = problemsFaced.trim()
        val cleanUpdate = updateSuggestion.trim()
        val cleanApp = appSuggestion.trim()

        if (cleanProblems.isEmpty() && cleanUpdate.isEmpty() && cleanApp.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter at least one problem, update idea, or app suggestion before saving.")
            )
        }

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = loadDraftsSync(context).toMutableList()
            val draft = FeedbackDraft(
                id = existingDraftId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                name = cleanName.ifBlank { "PDF (AIO) User" },
                problemsFaced = cleanProblems,
                updateSuggestion = cleanUpdate,
                appSuggestion = cleanApp,
                updatedAtMillis = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                deliveryStatus = deliveryStatus
            )

            existing.removeAll { it.id == draft.id }
            existing.add(0, draft)

            val committed = prefs.edit()
                .putString(KEY_DRAFTS_JSON, serializeDrafts(existing))
                .commit()

            if (!committed) {
                return@withContext Result.failure(IllegalStateException("Could not write feedback draft to device storage."))
            }
            Result.success(draft)
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving feedback draft locally", e)
            Result.failure(e)
        }
    }

    suspend fun loadDrafts(context: Context): List<FeedbackDraft> = withContext(Dispatchers.IO) {
        loadDraftsSync(context)
    }

    fun loadDraftsSync(context: Context): List<FeedbackDraft> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_DRAFTS_JSON, null)
            val drafts = if (!raw.isNullOrBlank()) {
                parseDrafts(raw)
            } else {
                emptyList()
            }

            // Migrate any legacy feedback_<timestamp> entries read-only if present
            val legacyEntries = prefs.all
                .filterKeys { it.startsWith("feedback_") && it != KEY_DRAFTS_JSON }
                .mapNotNull { (key, value) ->
                    val text = value as? String ?: return@mapNotNull null
                    val ts = key.removePrefix("feedback_").toLongOrNull() ?: 0L
                    FeedbackDraft(
                        id = key,
                        name = "Saved Feedback",
                        problemsFaced = text,
                        updateSuggestion = "",
                        appSuggestion = "",
                        updatedAtMillis = ts,
                        appVersion = BuildConfig.VERSION_NAME,
                        deliveryStatus = FeedbackDraft.STATUS_LOCAL_DRAFT
                    )
                }

            (drafts + legacyEntries.filter { legacy -> drafts.none { it.id == legacy.id } })
                .sortedByDescending { it.updatedAtMillis }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading local feedback drafts", e)
            emptyList()
        }
    }

    suspend fun deleteDraft(context: Context, draftId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val updated = loadDraftsSync(context).filterNot { it.id == draftId }
            val editor = prefs.edit().putString(KEY_DRAFTS_JSON, serializeDrafts(updated))
            if (draftId.startsWith("feedback_")) {
                editor.remove(draftId)
            }
            editor.commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting local feedback draft", e)
            false
        }
    }

    fun formatDraftText(draft: FeedbackDraft): String {
        val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(Date(draft.updatedAtMillis))
        return buildString {
            appendLine("PDF (AIO) Feedback")
            appendLine("App Version: v${draft.appVersion}")
            appendLine("Saved: $dateText")
            appendLine("Status: ${draft.deliveryStatus}")
            appendLine("Sender Name: ${draft.name.ifBlank { "PDF (AIO) User" }}")
            appendLine()
            appendLine("Problems Faced:")
            appendLine(draft.problemsFaced.ifBlank { "None reported" })
            appendLine()
            appendLine("Suggestions for Next Update:")
            appendLine(draft.updateSuggestion.ifBlank { "None reported" })
            appendLine()
            appendLine("General App Feedback:")
            appendLine(draft.appSuggestion.ifBlank { "None reported" })
            appendLine()
            appendLine("Note: No PDF documents or private files are attached.")
        }
    }

    /**
     * Builds an explicit user-initiated mailto intent to the developer.
     */
    fun buildEmailIntent(draft: FeedbackDraft): Intent {
        val subject = "PDF (AIO) v${draft.appVersion} Feedback — ${draft.name.ifBlank { "User" }}"
        val body = formatDraftText(draft)
        val uri = Uri.parse(
            "mailto:$DEVELOPER_EMAIL" +
                "?subject=${Uri.encode(subject)}" +
                "&body=${Uri.encode(body)}"
        )
        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    /**
     * Builds an explicit user-initiated plain-text share intent.
     */
    fun buildShareIntent(draft: FeedbackDraft): Intent {
        val subject = "PDF (AIO) v${draft.appVersion} Feedback"
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, formatDraftText(draft))
        }
    }

    private fun serializeDrafts(drafts: List<FeedbackDraft>): String {
        val array = JSONArray()
        drafts.forEach { d ->
            array.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("problemsFaced", d.problemsFaced)
                    put("updateSuggestion", d.updateSuggestion)
                    put("appSuggestion", d.appSuggestion)
                    put("updatedAtMillis", d.updatedAtMillis)
                    put("appVersion", d.appVersion)
                    put("deliveryStatus", d.deliveryStatus)
                }
            )
        }
        return array.toString()
    }

    private fun parseDrafts(raw: String): List<FeedbackDraft> {
        val array = JSONArray(raw)
        val result = ArrayList<FeedbackDraft>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(
                FeedbackDraft(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "PDF (AIO) User"),
                    problemsFaced = obj.optString("problemsFaced", ""),
                    updateSuggestion = obj.optString("updateSuggestion", ""),
                    appSuggestion = obj.optString("appSuggestion", ""),
                    updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
                    appVersion = obj.optString("appVersion", BuildConfig.VERSION_NAME),
                    deliveryStatus = obj.optString("deliveryStatus", FeedbackDraft.STATUS_LOCAL_DRAFT)
                )
            )
        }
        return result
    }
}
