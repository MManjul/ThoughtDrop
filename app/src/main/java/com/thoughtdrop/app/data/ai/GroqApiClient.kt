package com.thoughtdrop.app.data.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class GroqApiClient {
    companion object {
        private const val TAG = "GroqApiClient"
        private const val USER_AGENT = "ThoughtDrop/1.0 (Android)"

        // Active models on Groq free cloud (in order of priority)
        val DEFAULT_CHAT_MODELS = listOf(
            "qwen/qwen3.8-27b",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "llama-3.3-70b-versatile",
            "llama-3.1-70b-versatile",
            "llama-3.1-8b-instant"
        )

        // Free models on OpenRouter
        val OPENROUTER_FREE_MODELS = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-r1:free",
            "qwen/qwen-2.5-72b-instruct:free",
            "google/gemini-2.0-flash-exp:free"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun analyzeAudioWithGroq(
        audioFile: File,
        settingsRepo: SettingsRepository,
        preferredModel: String = DEFAULT_CHAT_MODELS.first()
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        val pool = settingsRepo.getAllGroqKeys()
        if (pool.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Groq API keys found. Please add a Groq key in Settings (⚙️)."))
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Audio file is empty or missing."))
        }

        var lastException: Exception? = null

        while (true) {
            val entry = settingsRepo.getAvailableGroqKeyEntry() ?: break
            val apiKey = entry.key
            val keyLabel = entry.label

            Log.d(TAG, "Using Groq key: $keyLabel (${entry.getMaskedKey()})")

            val transcriptResult = transcribeAudio(audioFile, apiKey)
            if (transcriptResult.isFailure) {
                val err = transcriptResult.exceptionOrNull()?.message.orEmpty()
                lastException = transcriptResult.exceptionOrNull() as? Exception
                Log.e(TAG, "Transcription failed with key $keyLabel: $err")
                if (err.contains("429") || err.contains("rate_limit", ignoreCase = true) || err.contains("quota", ignoreCase = true)) {
                    Log.w(TAG, "Groq key $keyLabel quota exhausted on transcription. Failing over to next Groq key...")
                    settingsRepo.markGroqKeyExhausted(apiKey)
                    continue
                }
                return@withContext Result.failure(lastException ?: RuntimeException("Whisper transcription failed: $err"))
            }

            val transcript = transcriptResult.getOrNull().orEmpty()
            if (transcript.isBlank()) {
                Log.w(TAG, "Audio transcription was blank/silent.")
                return@withContext Result.failure(IllegalStateException("Audio was silent or empty."))
            }

            Log.d(TAG, "Transcription successful (${transcript.length} chars): ${transcript.take(80)}...")

            val structResult = structureTranscriptWithFallback(transcript, apiKey, preferredModel)
            if (structResult.isFailure) {
                val err = structResult.exceptionOrNull()?.message.orEmpty()
                lastException = structResult.exceptionOrNull() as? Exception
                Log.e(TAG, "Structuring failed with key $keyLabel: $err")
                if (err.contains("429") || err.contains("rate_limit", ignoreCase = true) || err.contains("quota", ignoreCase = true)) {
                    Log.w(TAG, "Groq key $keyLabel quota exhausted on structuring. Failing over to next Groq key...")
                    settingsRepo.markGroqKeyExhausted(apiKey)
                    continue
                }
                return@withContext structResult
            }

            Log.d(TAG, "Structuring completed successfully!")
            settingsRepo.recordGroqKeySuccess(apiKey)
            return@withContext structResult
        }

        val allExhausted = settingsRepo.getAllGroqKeys().isNotEmpty() && settingsRepo.getAllGroqKeys().all { it.isExhausted }
        val msg = if (allExhausted) {
            "All ${settingsRepo.getAllGroqKeys().size} Groq API keys in your pool have exhausted their quota for today. Please add another Groq key in Settings (⚙️)."
        } else {
            lastException?.message ?: "Groq processing failed."
        }
        Result.failure(RuntimeException(msg))
    }

    suspend fun refineExistingNoteWithGroq(
        followUpAudioFile: File,
        existingNote: NoteEntity,
        settingsRepo: SettingsRepository,
        preferredModel: String = DEFAULT_CHAT_MODELS.first()
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        val pool = settingsRepo.getAllGroqKeys()
        if (pool.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Groq API keys found. Please add a Groq key in Settings (⚙️)."))
        }

        var lastException: Exception? = null

        while (true) {
            val entry = settingsRepo.getAvailableGroqKeyEntry() ?: break
            val apiKey = entry.key
            val keyLabel = entry.label

            val transcriptResult = transcribeAudio(followUpAudioFile, apiKey)
            if (transcriptResult.isFailure) {
                val err = transcriptResult.exceptionOrNull()?.message.orEmpty()
                lastException = transcriptResult.exceptionOrNull() as? Exception
                if (err.contains("429") || err.contains("rate_limit", ignoreCase = true) || err.contains("quota", ignoreCase = true)) {
                    settingsRepo.markGroqKeyExhausted(apiKey)
                    continue
                }
                return@withContext Result.failure(lastException ?: RuntimeException("Whisper failed"))
            }

            val newTranscript = transcriptResult.getOrNull().orEmpty()
            val refineResult = refineNoteWithTranscriptWithFallback(existingNote, newTranscript, apiKey, preferredModel)
            if (refineResult.isFailure) {
                val err = refineResult.exceptionOrNull()?.message.orEmpty()
                lastException = refineResult.exceptionOrNull() as? Exception
                if (err.contains("429") || err.contains("rate_limit", ignoreCase = true) || err.contains("quota", ignoreCase = true)) {
                    settingsRepo.markGroqKeyExhausted(apiKey)
                    continue
                }
                return@withContext refineResult
            }

            settingsRepo.recordGroqKeySuccess(apiKey)
            return@withContext refineResult
        }

        Result.failure(lastException ?: RuntimeException("Groq note update failed"))
    }

    // Direct single-key overload for backwards compatibility
    suspend fun analyzeAudioWithGroq(
        audioFile: File,
        apiKey: String,
        preferredModel: String = DEFAULT_CHAT_MODELS.first()
    ): Result<ParsedNoteResult> = withContext(Dispatchers.IO) {
        val transcriptResult = transcribeAudio(audioFile, apiKey)
        val transcript = transcriptResult.getOrElse { error ->
            return@withContext Result.failure(error)
        }
        structureTranscriptWithFallback(transcript, apiKey, preferredModel)
    }

    private fun transcribeAudio(audioFile: File, apiKey: String): Result<String> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("response_format", "json")
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", USER_AGENT)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(responseBody)
                Log.e(TAG, "Whisper API error (${response.code}): $errorMsg")
                return Result.failure(RuntimeException("Groq Whisper error (${response.code}): $errorMsg"))
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val text = json.get("text")?.asString.orEmpty()
            Result.success(text.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Whisper network exception", e)
            Result.failure(e)
        }
    }

    private fun structureTranscriptWithFallback(
        transcript: String,
        apiKey: String,
        preferredModel: String
    ): Result<ParsedNoteResult> {
        val systemPrompt = """
            You are an elite thinking partner and executive note taker.
            Analyze the following transcript of a voice note and break it down cleanly into structured, highly readable sections.
            
            Return JSON with EXACTLY these fields:
            - "title": A memorable, punchy title (max 5-7 words) that captures the core concept.
            - "category": One vibrant single-word tag (e.g. "Idea", "Project", "Task", "Insight", "Reflection", "Shopping", "Strategy").
            - "summary": A crisp 2-3 sentence executive synthesis of what was said.
            - "keyPoints": Array of 2 to 5 clear bullet points breaking down details, items, or reasoning mentioned.
            - "actionItems": Array of concrete checkable next steps or to-do checklist items (empty [] only if pure rambling with zero action).
            - "transcript": The original transcript.
            - "hasReminder": boolean true IF the user explicitly or implicitly gives a voice command to set a reminder or alarm (e.g. "remind me to...", "set an alarm at...", "schedule meeting tomorrow 10am"), false otherwise.
            - "reminderTask": brief title of the task to be reminded about if hasReminder is true, otherwise null.
            - "reminderHour": integer hour 0-23 if a reminder time is specified (e.g. 14 for 2pm, 9 for 9am), otherwise 9.
            - "reminderMinute": integer minute 0-59 if specified, otherwise 0.
            
            Return ONLY raw valid JSON matching this schema. No markdown backticks.
        """.trimIndent()

        val userPrompt = "VOICE TRANSCRIPT:\n$transcript"

        // Build priority models list (preferred model first, then the rest)
        val modelsToTry = (listOf(preferredModel) + DEFAULT_CHAT_MODELS).distinct()
        var lastErr: Throwable? = null

        for (m in modelsToTry) {
            Log.d(TAG, "Attempting Groq chat structuring with model: $m")
            val res = callGroqChat(systemPrompt, userPrompt, apiKey, m)
            if (res.isSuccess) {
                return res
            }
            val err = res.exceptionOrNull()
            lastErr = err
            val msg = err?.message.orEmpty()
            // If model does not exist or was decommissioned (404 / model_not_found), fall back to next model!
            if (msg.contains("model_not_found", ignoreCase = true) || msg.contains("does not exist", ignoreCase = true) || msg.contains("404")) {
                Log.w(TAG, "Model $m not available on Groq, trying next candidate model...")
                continue
            }
            // For rate limit (429), fail immediately so key rotation can kick in
            if (msg.contains("429") || msg.contains("rate_limit", ignoreCase = true)) {
                return res
            }
        }

        return Result.failure(lastErr ?: RuntimeException("All Groq chat models failed."))
    }

    private fun refineNoteWithTranscriptWithFallback(
        existingNote: NoteEntity,
        newTranscript: String,
        apiKey: String,
        preferredModel: String
    ): Result<ParsedNoteResult> {
        val systemPrompt = """
            You are an elite thinking partner updating an EXISTING note with new follow-up voice instructions.
            
            CURRENT NOTE STATE:
            - Title: ${existingNote.title}
            - Category: ${existingNote.category}
            - Summary: ${existingNote.summary}
            - Key Points: ${existingNote.keyPointsJson}
            - Action Items: ${existingNote.actionItemsJson}
            - Previous Transcript: ${existingNote.transcript}
            
            Synthesize an UPDATED note matching this schema:
            1. "title": Refine the title if the voice instructions rename it or significantly change the focus; otherwise keep it.
            2. "category": Keep or update if appropriate.
            3. "summary": Rewrite the summary to weave in the new points alongside the existing note context.
            4. "keyPoints": Merge the old key points with any newly added ideas or details. Avoid duplicates.
            5. "actionItems": Keep all pending checklist items and add newly mentioned tasks.
            6. "transcript": Return the new transcript clip.
            
            Return ONLY valid JSON matching this schema with keys: title, category, summary, keyPoints, actionItems, transcript.
        """.trimIndent()

        val userPrompt = "FOLLOW-UP VOICE TRANSCRIPT:\n$newTranscript"
        val modelsToTry = (listOf(preferredModel) + DEFAULT_CHAT_MODELS).distinct()
        var lastErr: Throwable? = null

        for (m in modelsToTry) {
            val res = callGroqChat(systemPrompt, userPrompt, apiKey, m)
            if (res.isSuccess) return res
            val err = res.exceptionOrNull()
            lastErr = err
            val msg = err?.message.orEmpty()
            if (msg.contains("model_not_found", ignoreCase = true) || msg.contains("does not exist", ignoreCase = true) || msg.contains("404")) {
                continue
            }
            if (msg.contains("429") || msg.contains("rate_limit", ignoreCase = true)) {
                return res
            }
        }

        return Result.failure(lastErr ?: RuntimeException("All Groq refine models failed."))
    }

    private fun callGroqChat(
        systemPrompt: String,
        userPrompt: String,
        apiKey: String,
        model: String
    ): Result<ParsedNoteResult> {
        val isOpenRouter = apiKey.startsWith("sk-or-")
        val effectiveModel = if (isOpenRouter && !model.contains("/")) {
            OPENROUTER_FREE_MODELS.first()
        } else model

        val jsonPayload = JsonObject().apply {
            addProperty("model", effectiveModel)
            addProperty("temperature", 0.2)

            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userPrompt)
                })
            }
            add("messages", messages)

            if (!isOpenRouter) {
                val responseFormat = JsonObject().apply {
                    addProperty("type", "json_object")
                }
                add("response_format", responseFormat)
            }
        }

        val url = if (isOpenRouter) "https://openrouter.ai/api/v1/chat/completions" else "https://api.groq.com/openai/v1/chat/completions"

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

        if (isOpenRouter) {
            requestBuilder.header("HTTP-Referer", "https://thoughtdrop.app")
            requestBuilder.header("X-Title", "ThoughtDrop")
        }

        val request = requestBuilder.build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = extractErrorMessage(responseBody)
                Log.e(TAG, "Groq Chat error ($model, ${response.code}): $errorMsg")
                return Result.failure(RuntimeException("Groq Chat error ($model, ${response.code}): $errorMsg"))
            }

            val root = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = root.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                Log.e(TAG, "Groq returned empty candidates")
                return Result.failure(RuntimeException("Groq returned empty candidates"))
            }

            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val content = message.get("content").asString.trim()

            val cleanedJson = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = gson.fromJson(cleanedJson, ParsedNoteResult::class.java)
            Result.success(parsed)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Groq response for model $model", e)
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(responseBody: String): String {
        return try {
            val obj = gson.fromJson(responseBody, JsonObject::class.java)
            obj.getAsJsonObject("error")?.get("message")?.asString ?: responseBody
        } catch (_: Exception) {
            responseBody
        }
    }
}
