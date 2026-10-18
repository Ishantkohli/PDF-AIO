package com.example.data.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiDocumentResponse(
    val thinkingProcess: String = "",
    val responseText: String = "",
    val error: String? = null
)

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeDocument(
        documentTitle: String,
        documentExcerpt: String,
        userQuery: String,
        conversationHistory: List<Pair<String, String>> = emptyList() // (User, Assistant)
    ): AiDocumentResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext AiDocumentResponse(
                error = "Gemini API Key is not configured. Please add your GEMINI_API_KEY in the Secrets panel."
            )
        }

        try {
            // Build JSON payload for gemini-4-argon with thinkingLevel: HIGH
            val root = JSONObject()

            // System Instruction
            val sysInstructionObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(
                JSONObject().put(
                    "text",
                    """
                    You are PDF AIO Assistant, an elite, professional PDF intelligence engine.
                    Analyze the user's document thoroughly. 
                    Be precise, highly structured, citation-friendly, and professional.
                    Use markdown formatting (bullet points, bold text, numbered lists) for clarity.
                    Always reference specific sections or context from the provided document.
                    """.trimIndent()
                )
            )
            sysInstructionObj.put("parts", sysParts)
            root.put("systemInstruction", sysInstructionObj)

            // Contents (History + Current Prompt with Document Excerpt)
            val contentsArr = JSONArray()

            // Prior conversation turns
            for ((q, a) in conversationHistory.takeLast(4)) {
                val userContent = JSONObject()
                userContent.put("role", "user")
                val uParts = JSONArray().put(JSONObject().put("text", q))
                userContent.put("parts", uParts)
                contentsArr.put(userContent)

                val modelContent = JSONObject()
                modelContent.put("role", "model")
                val mParts = JSONArray().put(JSONObject().put("text", a))
                modelContent.put("parts", mParts)
                contentsArr.put(modelContent)
            }

            // Current prompt
            val promptText = buildString {
                append("DOCUMENT CONTEXT:\n")
                append("Title: $documentTitle\n")
                append("Content Excerpt:\n")
                append(documentExcerpt.take(6000))
                append("\n\nUSER QUESTION / TASK:\n")
                append(userQuery)
            }

            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val currParts = JSONArray().put(JSONObject().put("text", promptText))
            currentTurn.put("parts", currParts)
            contentsArr.put(currentTurn)

            root.put("contents", contentsArr)

            // Generation config with high thinking level
            val genConfig = JSONObject()
            val thinkingConfig = JSONObject()
            thinkingConfig.put("thinkingLevel", "HIGH")
            genConfig.put("thinkingConfig", thinkingConfig)
            root.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(mediaType)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-4-argon:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMessage = try {
                    val errJson = JSONObject(bodyString)
                    errJson.optJSONObject("error")?.optString("message") ?: "API Error: ${response.code}"
                } catch (e: Exception) {
                    "Request failed with code ${response.code}: $bodyString"
                }
                return@withContext AiDocumentResponse(error = errMessage)
            }

            // Parse response
            val respJson = JSONObject(bodyString)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext AiDocumentResponse(error = "No response candidates returned.")
            }

            val candidate = candidates.getJSONObject(0)
            val contentObj = candidate.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts") ?: JSONArray()

            val thinkingSb = StringBuilder()
            val answerSb = StringBuilder()

            for (i in 0 until partsArr.length()) {
                val part = partsArr.getJSONObject(i)
                val isThought = part.optBoolean("thought", false)
                val text = part.optString("text", "")
                if (isThought) {
                    thinkingSb.append(text).append("\n")
                } else {
                    answerSb.append(text)
                }
            }

            val finalAnswer = if (answerSb.isNotBlank()) {
                answerSb.toString().trim()
            } else if (thinkingSb.isNotBlank()) {
                // If the model output thinking without separate text part
                thinkingSb.toString().trim()
            } else {
                "Document analysis complete."
            }

            AiDocumentResponse(
                thinkingProcess = thinkingSb.toString().trim(),
                responseText = finalAnswer
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AiDocumentResponse(error = "Connection failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
