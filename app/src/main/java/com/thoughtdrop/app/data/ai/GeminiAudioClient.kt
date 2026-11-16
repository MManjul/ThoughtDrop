package com.thoughtdrop.app.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

data class ParsedNoteResult(
    @SerializedName("title") val title: String = "Untitled Thought",
    @SerializedName("category") val category: String = "Idea",
    @SerializedName("summary") val summary: String = "",
    @SerializedName("keyPoints") val keyPoints: List<String> = emptyList(),
    @SerializedName("actionItems") val actionItems: List<String> = emptyList(),
    @SerializedName("transcript") val transcript: String = "",
    @SerializedName("hasReminder") val hasReminder: Boolean = false,
    @SerializedName("reminderTask") val reminderTask: String? = null,
    @SerializedName("reminderHour") val reminderHour: Int? = null,
    @SerializedName("reminderMinute") val reminderMinute: Int? = null
)

class GeminiAudioClient {
    companion object {
        private const val TAG = "GeminiAudioClient"
        const val DEFAULT_MODEL = "gemini-4-hydrogen"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeAudio(
        audioFile: File,
        settingsRepo: SettingsRepository,
        preferredModel: String = DEFAULT_MODEL
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Audio file is empty or missing."))
        }

        val promptText = """
            You are an elite thinking partner and executive note taker.
            Listen carefully to this voice note and break it down cleanly into structured, highly readable sections.
            
            Return JSON with EXACTLY these fields:
            - "title": A memorable, punchy title (max 5-7 words) that captures the core concept.
            - "category": One vibrant single-word tag (e.g. "Idea", "Project", "Task", "Insight", "Reflection", "Shopping", "Strategy").
            - "summary": A crisp 2-3 sentence executive synthesis of what was said.
            - "keyPoints": Array of 2 to 5 clear bullet points breaking down details, items, or reasoning mentioned.
            - "actionItems": Array of concrete checkable next steps or to-do checklist items (empty [] only if pure rambling with zero action).
            - "transcript": Exact word-for-word transcript of what was spoken.
            - "hasReminder": boolean true IF the user explicitly or implicitly gives a voice command to set a reminder or alarm (e.g. "remind me to...", "set an alarm at...", "schedule meeting tomorrow 10am"), false otherwise.
            - "reminderTask": brief title of the task to be reminded about if hasReminder is true, otherwise null.
            - "reminderHour": integer hour 0-23 if a reminder time is specified (e.g. 14 for 2pm, 9 for 9am), otherwise 9.
            - "reminderMinute": integer minute 0-59 if specified, otherwise 0.
            
            Return ONLY raw valid JSON matching this schema. No markdown wrapping.
        """.trimIndent()

        executeWithKeyRotation(settingsRepo, preferredModel) { apiKey, model ->
            callGeminiAudioApi(audioFile, apiKey, model, promptText)
        }
    }

    suspend fun analyzeAudio(
        audioFile: File,
        apiKey: String,
        preferredModel: String = DEFAULT_MODEL
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Audio file is empty or missing."))
        }

        val promptText = """
            You are an elite thinking partner and executive note taker.
            Listen carefully to this voice note and break it down cleanly into structured, highly readable sections.
            
            Return JSON with EXACTLY these fields:
            - "title": A memorable, punchy title (max 5-7 words) that captures the core concept.
            - "category": One vibrant single-word tag (e.g. "Idea", "Project", "Task", "Insight", "Reflection", "Shopping", "Strategy").
            - "summary": A crisp 2-3 sentence executive synthesis of what was said.
            - "keyPoints": Array of 2 to 5 clear bullet points breaking down details, items, or reasoning mentioned.
            - "actionItems": Array of concrete checkable next steps or to-do checklist items (empty [] only if pure rambling with zero action).
            - "transcript": Exact word-for-word transcript of what was spoken.
            - "hasReminder": boolean true if voice command specifies a reminder/alarm, false otherwise.
            - "reminderTask": title of task to be reminded about or null.
            - "reminderHour": integer hour 0-23 if specified, otherwise 9.
            - "reminderMinute": integer minute 0-59 if specified, otherwise 0.
            
            Return ONLY raw valid JSON matching this schema. No markdown wrapping.
        """.trimIndent()

        try {
            val result = callGeminiAudioApi(audioFile, apiKey, preferredModel, promptText)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refineExistingNoteWithAudio(
        followUpAudioFile: File,
        existingNote: NoteEntity,
        settingsRepo: SettingsRepository,
        preferredModel: String = DEFAULT_MODEL
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        if (!followUpAudioFile.exists() || followUpAudioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Follow-up audio is empty."))
        }

        val existingImages = existingNote.getImagePaths().map { File(it) }.filter { it.exists() && it.length() > 0L }

        val promptText = if (existingImages.isNotEmpty()) {
            """
                You are an elite thinking partner updating an EXISTING multimodal note that contains both captured image(s) and voice instructions.
                
                CURRENT NOTE STATE:
                - Title: ${existingNote.title}
                - Category: ${existingNote.category}
                - Summary: ${existingNote.summary}
                - Key Points: ${existingNote.keyPointsJson}
                - Action Items: ${existingNote.actionItemsJson}
                - Previous Transcript / OCR: ${existingNote.transcript}
                
                The user has attached photo(s) AND spoken follow-up voice instructions to refine or act upon this note.
                Look at the attached image(s) carefully, listen to the new voice audio clip, and synthesize an updated note:
                1. "title": Refine the title if user commands or if the image/voice clarify the focus.
                2. "category": Keep or update if appropriate.
                3. "summary": Rewrite the summary to seamlessly weave together what is visible in the photo(s) and what was spoken in the voice update.
                4. "keyPoints": Merge the existing key insights with any newly mentioned facts, numbers, or diagram points from the photo and voice.
                5. "actionItems": Keep all pending checklist items and add newly mentioned tasks.
                6. "transcript": The word-for-word transcript of this new audio clip.
                7. "hasReminder": boolean true if voice command asks for a reminder/alarm, false otherwise.
                8. "reminderTask": task title if hasReminder is true, otherwise null.
                9. "reminderHour": integer 0-23 if specified, otherwise 9.
                10. "reminderMinute": integer 0-59 if specified, otherwise 0.
                
                Return ONLY valid JSON matching this schema with keys: title, category, summary, keyPoints, actionItems, transcript, hasReminder, reminderTask, reminderHour, reminderMinute.
            """.trimIndent()
        } else {
            """
                You are an elite thinking partner updating an EXISTING note with new follow-up voice instructions.
                
                CURRENT NOTE STATE:
                - Title: ${existingNote.title}
                - Category: ${existingNote.category}
                - Summary: ${existingNote.summary}
                - Key Points: ${existingNote.keyPointsJson}
                - Action Items: ${existingNote.actionItemsJson}
                - Previous Transcript: ${existingNote.transcript}
                
                The user is speaking additional details, edits, or new checklist tasks to amend this note.
                Listen to the new audio and synthesize an UPDATED note:
                1. "title": Refine the title if the voice instructions rename it or significantly change the focus; otherwise keep it.
                2. "category": Keep or update if appropriate.
                3. "summary": Rewrite the summary to weave in the new points alongside the existing note context.
                4. "keyPoints": Merge the old key points with any newly added ideas or details. Avoid duplicates.
                5. "actionItems": Keep all pending checklist items and add newly mentioned tasks.
                6. "transcript": The word-for-word transcript of this new audio clip.
                7. "hasReminder": boolean true if voice command asks for a reminder/alarm, false otherwise.
                8. "reminderTask": task title if hasReminder is true, otherwise null.
                9. "reminderHour": integer 0-23 if specified, otherwise 9.
                10. "reminderMinute": integer 0-59 if specified, otherwise 0.
                
                Return ONLY valid JSON matching this schema with keys: title, category, summary, keyPoints, actionItems, transcript, hasReminder, reminderTask, reminderHour, reminderMinute.
            """.trimIndent()
        }

        executeWithKeyRotation(settingsRepo, preferredModel) { apiKey, model ->
            if (existingImages.isNotEmpty()) {
                callGeminiAudioAndImageApi(followUpAudioFile, existingImages, apiKey, model, promptText)
            } else {
                callGeminiAudioApi(followUpAudioFile, apiKey, model, promptText)
            }
        }
    }

    // MULTIMODAL VISION: Analyze image to create a new structured note
    suspend fun analyzeImage(
        imageFile: File,
        settingsRepo: SettingsRepository,
        preferredModel: String = DEFAULT_MODEL
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        if (!imageFile.exists() || imageFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Image file is empty."))
        }

        val promptText = """
            You are an elite thinking partner and executive note taker analyzing a photo taken by the user.
            The photo might be a whiteboard drawing, handwritten notes, a slide, a book passage, a receipt, a product, or an object.
            
            Extract ALL useful information and break it down cleanly into structured sections:
            - "title": A punchy, descriptive title (max 5-7 words) capturing the subject of the photo.
            - "category": One vibrant single-word tag ("Project", "Meeting", "Idea", "Task", "Finance", "Reading", "Visual").
            - "summary": A crisp 2-3 sentence executive synthesis explaining what the image depicts, key data, or core takeaway.
            - "keyPoints": Array of 2 to 6 clear bullet points detailing items, diagrams, lists, or insights visible in the photo.
            - "actionItems": Array of concrete checklist items, follow-ups, or to-dos derived from the photo (e.g. from a whiteboard, tasks list, or receipt).
            - "transcript": Full OCR transcription of any legible text, quotes, or notes present in the image.
            - "hasReminder": false
            - "reminderTask": null
            - "reminderHour": null
            - "reminderMinute": null
            
            Return ONLY valid JSON matching this schema.
        """.trimIndent()

        executeWithKeyRotation(settingsRepo, preferredModel) { apiKey, model ->
            callGeminiImageApi(imageFile, apiKey, model, promptText)
        }
    }

    // MULTIMODAL VISION: Refine an existing note by attaching and reading a photo
    suspend fun refineExistingNoteWithImage(
        imageFile: File,
        existingNote: NoteEntity,
        settingsRepo: SettingsRepository,
        preferredModel: String = DEFAULT_MODEL
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        if (!imageFile.exists() || imageFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Image file is empty."))
        }

        val promptText = """
            You are an elite thinking partner updating an EXISTING note with a photo just captured by the user.
            
            CURRENT NOTE:
            - Title: ${existingNote.title}
            - Category: ${existingNote.category}
            - Summary: ${existingNote.summary}
            - Key Points: ${existingNote.keyPointsJson}
            - Action Items: ${existingNote.actionItemsJson}
            - Previous Transcript / OCR: ${existingNote.transcript}
            
            Analyze the attached image (whiteboard diagram, document, slide, receipt, sketch, or visual proof) in relation to this note.
            1. "title": Refine the title if needed, or keep it.
            2. "category": Keep or update.
            3. "summary": Rewrite the summary to weave the new visual details/data from this photo into the existing note context.
            4. "keyPoints": Merge the existing key insights with any new observations, diagram concepts, or facts read from the photo.
            5. "actionItems": Keep unfinished tasks and add any newly discovered action items or to-dos found in the image.
            6. "transcript": Any OCR text, handwritten notes, or labels read from the image.
            
            Return ONLY valid JSON matching this schema with keys: title, category, summary, keyPoints, actionItems, transcript.
        """.trimIndent()

        executeWithKeyRotation(settingsRepo, preferredModel) { apiKey, model ->
            callGeminiImageApi(imageFile, apiKey, model, promptText)
        }
    }

    // MULTI-KEY POOL & MODEL RESILIENCE FAILOVER ENGINE
    private suspend fun executeWithKeyRotation(
        settingsRepo: SettingsRepository,
        preferredModel: String,
        apiCall: (apiKey: String, model: String) -> ParsedNoteResult
    ): Result<ParsedNoteResult> {
        val pool = settingsRepo.getAllKeys()
        if (pool.isEmpty()) {
            return Result.failure(IllegalStateException("No Gemini API keys found. Please add an API key in Settings (⚙️)."))
        }

        var lastException: Exception? = null

        // Prioritized Gemini models with Gemini 4 Hydrogen flagship & Flash-Lite sub-second fallbacks
        val validFallbackModels = listOf(
            preferredModel,
            "gemini-4-hydrogen",
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-3.5-flash"
        ).distinct()

        while (true) {
            val entry = settingsRepo.getAvailableApiKeyEntry() ?: break
            val apiKey = entry.key
            val keyLabel = entry.label

            Log.d(TAG, "Using key: $keyLabel (${entry.getMaskedKey()})")

            var keyFailedWithQuota = false

            for (model in validFallbackModels) {
                try {
                    Log.d(TAG, "Calling Gemini model: $model ($keyLabel)")
                    val result = apiCall(apiKey, model)
                    Log.d(TAG, "Success with $model ($keyLabel)")
                    settingsRepo.recordKeySuccess(apiKey)
                    return Result.success(result)
                } catch (e: Exception) {
                    val msg = e.message.orEmpty()
                    lastException = e
                    Log.w(TAG, "Model $model failed on $keyLabel: $msg")

                    // Check for rate limit / quota exhaustion
                    if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || msg.contains("quota", ignoreCase = true)) {
                        keyFailedWithQuota = true
                        break // Quota exhausted on this key, failover to next key in pool
                    }

                    // For 503 (high demand spike during nighttime), 404, or 500, continue to next model in validFallbackModels
                    Log.i(TAG, "Attempting next fallback model for key $keyLabel...")
                }
            }

            if (keyFailedWithQuota) {
                Log.w(TAG, "Key $keyLabel quota exhausted. Failing over to next key in pool...")
                settingsRepo.markKeyExhausted(apiKey)
                continue
            } else {
                // If all fallback models failed with non-quota error on this key, try the next key
                continue
            }
        }

        val allExhausted = settingsRepo.getAllKeys().isNotEmpty() && settingsRepo.getAllKeys().all { it.isExhausted }
        val errorMsg = if (allExhausted) {
            "All ${settingsRepo.getAllKeys().size} configured API keys have exhausted their free quota for today. Tap ⚙️ Settings to add another free key from another Google account, or wait for midnight reset."
        } else {
            lastException?.message ?: "Failed to process request with Gemini"
        }

        return Result.failure(RuntimeException(errorMsg))
    }

    private fun callGeminiAudioApi(
        audioFile: File,
        apiKey: String,
        model: String,
        promptText: String
    ): ParsedNoteResult {
        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": ${gson.toJson(promptText)} },
                    {
                      "inline_data": {
                        "mime_type": "audio/mp4",
                        "data": "$base64Audio"
                      }
                    }
                  ]
                }
              ],
              "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.2
              }
            }
        """.trimIndent()

        return executeRequest(jsonBody, model, apiKey)
    }

    private fun callGeminiImageApi(
        imageFile: File,
        apiKey: String,
        model: String,
        promptText: String
    ): ParsedNoteResult {
        val compressedBytes = getCompressedImageBytes(imageFile)
        val base64Image = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        val jsonBody = """
            {
              "contents": [
                {
                  "parts": [
                    { "text": ${gson.toJson(promptText)} },
                    {
                      "inline_data": {
                        "mime_type": "image/jpeg",
                        "data": "$base64Image"
                      }
                    }
                  ]
                }
              ],
              "generationConfig": {
                "response_mime_type": "application/json",
                "temperature": 0.2
              }
            }
        """.trimIndent()

        return executeRequest(jsonBody, model, apiKey)
    }

    // MULTIMODAL SIMULTANEOUS AUDIO + IMAGE REFINEMENT
    private fun callGeminiAudioAndImageApi(
        audioFile: File,
        imageFiles: List<File>,
        apiKey: String,
        model: String,
        promptText: String
    ): ParsedNoteResult {
        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val partsArray = com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("text", promptText) })
            // Attach up to 3 images from this note
            for (imgFile in imageFiles.take(3)) {
                val compressedBytes = getCompressedImageBytes(imgFile)
                val base64Img = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                add(JsonObject().apply {
                    add("inline_data", JsonObject().apply {
                        addProperty("mime_type", "image/jpeg")
                        addProperty("data", base64Img)
                    })
                })
            }
            // Attach audio
            add(JsonObject().apply {
                add("inline_data", JsonObject().apply {
                    addProperty("mime_type", "audio/mp4")
                    addProperty("data", base64Audio)
                })
            })
        }

        val requestObj = JsonObject().apply {
            add("contents", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", partsArray)
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("response_mime_type", "application/json")
                addProperty("temperature", 0.2)
            })
        }

        return executeRequest(gson.toJson(requestObj), model, apiKey)
    }

    private fun getCompressedImageBytes(file: File): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file.readBytes()
        val maxDim = 1200
        val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val maxSide = maxOf(bitmap.width, bitmap.height)
            maxDim.toFloat() / maxSide
        } else 1f

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private fun executeRequest(jsonBody: String, model: String, apiKey: String): ParsedNoteResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errObj = gson.fromJson(responseString, JsonObject::class.java)
                errObj.getAsJsonObject("error")?.get("message")?.asString ?: responseString
            } catch (_: Exception) {
                responseString
            }
            throw RuntimeException("Gemini Error (${response.code}): $errorMsg")
        }

        val root = gson.fromJson(responseString, JsonObject::class.java)
        val candidates = root.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) {
            throw RuntimeException("Gemini returned empty candidate response")
        }

        val content = candidates[0].asJsonObject.getAsJsonObject("content")
        val parts = content.getAsJsonArray("parts")
        val textPart = parts[0].asJsonObject.get("text").asString

        return try {
            val cleanedJson = textPart.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            gson.fromJson(cleanedJson, ParsedNoteResult::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON response: $textPart", e)
            ParsedNoteResult(
                title = "Photo Note",
                category = "Visual",
                summary = textPart,
                keyPoints = emptyList(),
                actionItems = emptyList(),
                transcript = textPart
            )
        }
    }
}
