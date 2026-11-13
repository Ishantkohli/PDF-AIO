package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dispatches in-app user feedback directly to the developer's email (ishantkohlikohli@gmail.com)
 * using the same Web3Forms backend and access key configured on https://ishantkohli.me
 * along with direct form email routing, without opening any external mail app.
 */
object FeedbackDispatcher {
    private const val TAG = "FeedbackDispatcher"
    // Access key directly from ishantkohli.me contact form
    private const val WEB3FORMS_KEY = "ae3db375-a739-4ac1-a28b-db45849765ae"
    private const val DEVELOPER_EMAIL = "ishantkohlikohli@gmail.com"

    suspend fun dispatchFeedback(
        context: Context,
        name: String,
        problemsFaced: String,
        updateSuggestion: String,
        appSuggestion: String
    ): Boolean = withContext(Dispatchers.IO) {
        val userName = name.trim().ifBlank { "PDF (AIO) User" }
        val problems = problemsFaced.trim().ifBlank { "None reported" }
        val update = updateSuggestion.trim().ifBlank { "None reported" }
        val app = appSuggestion.trim().ifBlank { "None reported" }

        val formattedMessage = buildString {
            appendLine("=========================================")
            appendLine("PDF (AIO) IN-APP USER FEEDBACK")
            appendLine("=========================================")
            appendLine("Name : $userName")
            appendLine()
            appendLine("Any Problems Faced :")
            appendLine(problems)
            appendLine()
            appendLine("Suggestion for new update :")
            appendLine(update)
            appendLine()
            appendLine("Suggestion on the app :")
            appendLine(app)
            appendLine()
            appendLine("=========================================")
            appendLine("Sent automatically from PDF (AIO) v${BuildConfig.VERSION_NAME} Android App")
        }

        // Cache submission locally as durable offline backup
        try {
            val prefs = context.getSharedPreferences("user_feedback_records", Context.MODE_PRIVATE)
            val timestamp = System.currentTimeMillis()
            prefs.edit().putString("feedback_$timestamp", formattedMessage).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Local cache warning: ${e.message}")
        }

        var dispatched = false

        // 1. Direct Web3Forms submission (Same provider and key as https://ishantkohli.me)
        var web3Conn: HttpURLConnection? = null
        try {
            val web3Url = URL("https://api.web3forms.com/submit")
            web3Conn = (web3Url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Origin", "https://ishantkohli.me")
                setRequestProperty("Referer", "https://ishantkohli.me/")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
            }

            val json = JSONObject().apply {
                put("access_key", WEB3FORMS_KEY)
                put("name", userName)
                put("email", DEVELOPER_EMAIL)
                put("subject", "New Feedback Submission from $userName (PDF (AIO) App)")
                put("message", formattedMessage)
                put("Any Problems Faced", problems)
                put("Suggestion for new update", update)
                put("Suggestion on the app", app)
                put("from_name", "PDF (AIO) Android App")
            }

            OutputStreamWriter(web3Conn.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            val respCode = web3Conn.responseCode
            Log.i(TAG, "Web3Forms submission HTTP response: $respCode")
            if (respCode in 200..299) {
                dispatched = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Web3Forms error: ${e.message}")
        } finally {
            try { web3Conn?.disconnect() } catch (_: Exception) {}
        }

        // 2. Direct email delivery to ishantkohlikohli@gmail.com
        var shipConn: HttpURLConnection? = null
        try {
            val shipUrl = URL("https://shipmyform.com/to/$DEVELOPER_EMAIL")
            shipConn = (shipUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Origin", "https://ishantkohli.me")
                setRequestProperty("Referer", "https://ishantkohli.me/")
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
            }

            val json = JSONObject().apply {
                put("name", userName)
                put("email", DEVELOPER_EMAIL)
                put("_subject", "PDF (AIO) Feedback: $userName")
                put("message", formattedMessage)
                put("Any Problems Faced", problems)
                put("Suggestion for new update", update)
                put("Suggestion on the app", app)
            }

            OutputStreamWriter(shipConn.outputStream).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }

            val respCode = shipConn.responseCode
            Log.i(TAG, "Direct mail submission HTTP response: $respCode")
            if (respCode in 200..299) {
                dispatched = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct mail submission error: ${e.message}")
        } finally {
            try { shipConn?.disconnect() } catch (_: Exception) {}
        }

        dispatched
    }
}
