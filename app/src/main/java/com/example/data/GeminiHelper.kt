package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

object GeminiHelper {
    private const val TAG = "GeminiHelper"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Helper to check if API key exists and is valid (not placeholder)
     */
    fun isApiKeyAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && !key.contains("PLACEHOLDER")
    }

    /**
     * Dynamic Template and Storyboard generator.
     * Takes user ideas and yields a ready-to-play VideoScene list from Gemini!
     */
    suspend fun generateStoryboard(
        userIdea: String,
        videoType: String, // e.g. "Travel Vlog", "Product Review", "Tutorial", "TikTok Reel"
        durationSecondsTarget: Int // e.g. 15, 30, 45
    ): Result<List<VideoScene>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyAvailable()) {
            return@withContext Result.failure(Exception("Gemini API Key is not configured in the Secrets panel!"))
        }

        val prompt = """
            Design a storyboard based on the following:
            - Video Type: $videoType
            - Idea: "$userIdea"
            - Desired Total Length: approximately $durationSecondsTarget seconds.
            
            You must reply with a valid JSON Array containing individual Scene Objects. Each Scene Object MUST contain these exact keys:
            - "visualPresetId": Must be one of the these allowed presets: "nature_lake", "city_night", "tech_close_up", "cozy_cafe", "sports_run", "cosmic_space", "abstract_shapes", "retro_clouds". Pick the one that fits the scene best.
            - "durationSeconds": An integer, typically 3 to 6. Ensure the sum of all scenes is close to $durationSecondsTarget seconds.
            - "captionText": A clear, engaging, easy-to-read, punchy text overlay string (keep it under 60 characters). Add 1 cute relevant Emoji.
            - "suggestedPrompt": A vivid 1-sentence prompt describing the visual scene action.
            
            Return ONLY the valid JSON Array. No markdown wraps, no trailing quotes.
            Example Response:
            [
              {
                "visualPresetId": "cozy_cafe",
                "durationSeconds": 4,
                "captionText": "Morning coffee & code! ☕✨",
                "suggestedPrompt": "Close-up of steam rising from cup with soft focus bokeh background"
              }
            ]
        """.trimIndent()

        val systemInstructionText = """
            You are a professional Cinematic Video Director and AI Assistant inside Easy Video Editor. 
            Your goal is to help absolute beginners who have zero editing knowledge outline high-retention video structures.
            Always maintain a creative, premium, ultra-helpful persona. You create beautiful, concise storyboards.
            You must only reply in pure, valid, parseable JSON arrays. Do not add conversational text or codeblock wrappers.
        """.trimIndent()

        // Build request payload using standard JSONObject
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        requestJson.put("contents", contentsArray)

        // System Instruction
        val systemInstructionObj = JSONObject()
        val systemPartsArray = JSONArray()
        val systemPartObj = JSONObject()
        systemPartObj.put("text", systemInstructionText)
        systemPartsArray.put(systemPartObj)
        systemInstructionObj.put("parts", systemPartsArray)
        requestJson.put("systemInstruction", systemInstructionObj)

        // Generation config
        val genConfig = JSONObject()
        genConfig.put("responseMimeType", "application/json")
        genConfig.put("temperature", 0.8)
        requestJson.put("generationConfig", genConfig)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val url = "$BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
            }

            val bodyString = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty API response."))

            Log.d(TAG, "Raw returned storyboard JSON response: $bodyString")
            
            // Parse custom body string
            val rootObj = JSONObject(bodyString)
            val candidatesArray = rootObj.optJSONArray("candidates")
            if (candidatesArray == null || candidatesArray.length() == 0) {
                val errorObj = rootObj.optJSONObject("error")
                val errorMsg = errorObj?.optString("message") ?: "No candidates returned from Gemini API."
                return@withContext Result.failure(Exception("Gemini error: $errorMsg"))
            }
            
            val firstCandidate = candidatesArray.getJSONObject(0)
            val contentObject = firstCandidate.optJSONObject("content")
            val partsArr = contentObject?.optJSONArray("parts")
            if (partsArr == null || partsArr.length() == 0) {
                return@withContext Result.failure(Exception("No content parts returned."))
            }
            
            val rawText = partsArr.getJSONObject(0).optString("text")
            val scenes = parseScenesFromJson(rawText)
            Result.success(scenes)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating storyboard with Gemini", e)
            Result.failure(e)
        }
    }

    /**
     * AI Text overlay optimizer.
     * Rewrites input captions to make them punchier, humorous, or add emojis.
     */
    suspend fun polishCaption(
        captionText: String,
        style: String // e.g. "Professional", "Funny", "Epic", "Cute", "TikTok Hook"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyAvailable()) {
            return@withContext Result.failure(Exception("Gemini API Key is not configured."))
        }

        val prompt = """
            Optimize the following overlay caption text: "$captionText".
            Make it match the style of "$style". Keep it extremely short (under 50 characters) so it fits beautifully on a phone screen.
            Add 1 or 2 high-retention emojis if appropriate, and keep it incredibly punchy.
            Return ONLY the optimized text. Do not provide quotes, explanations, or multiple options.
        """.trimIndent()

        val systemInstructionText = "You are a social media copywriter. You specialize in short, high-energy text captions."

        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val partsArray = JSONArray()
        val partObj = JSONObject()
        partObj.put("text", prompt)
        partsArray.put(partObj)
        contentObj.put("parts", partsArray)
        contentsArray.put(contentObj)
        requestJson.put("contents", contentsArray)

        // System Instruction
        val systemInstructionObj = JSONObject()
        val systemPartsArray = JSONArray()
        val systemPartObj = JSONObject()
        systemPartObj.put("text", systemInstructionText)
        systemPartsArray.put(systemPartObj)
        systemInstructionObj.put("parts", systemPartsArray)
        requestJson.put("systemInstruction", systemInstructionObj)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val url = "$BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}"))
            }

            val bodyString = response.body?.string() ?: return@withContext Result.failure(Exception("No response."))
            val rootObj = JSONObject(bodyString)
            val candidatesArray = rootObj.optJSONArray("candidates")
            if (candidatesArray == null || candidatesArray.length() == 0) {
                return@withContext Result.failure(Exception("No reply candidates."))
            }
            val rawText = candidatesArray.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .optString("text", "")
                .trim()

            Result.success(rawText.removeSurrounding("\""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScenesFromJson(jsonStr: String): List<VideoScene> {
        val list = mutableListOf<VideoScene>()
        try {
            // Clean markdown wraps
            var cleanStr = jsonStr.trim()
            if (cleanStr.startsWith("```json")) {
                cleanStr = cleanStr.substringAfter("```json")
            }
            if (cleanStr.endsWith("```")) {
                cleanStr = cleanStr.substringBeforeLast("```")
            }
            cleanStr = cleanStr.trim()

            val arr = JSONArray(cleanStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VideoScene(
                        id = "ai_scene_${System.currentTimeMillis() + i}",
                        visualPresetId = obj.optString("visualPresetId", "nature_lake"),
                        durationSeconds = obj.optInt("durationSeconds", 4),
                        captionText = obj.optString("captionText", ""),
                        speedMultiplier = 1.0f,
                        suggestedPrompt = obj.optString("suggestedPrompt", ""),
                        stickerId = ""
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing scene JSON output: $jsonStr", e)
        }
        return list
    }
}
