package com.thoughtdrop.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thoughtdrop.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ApiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val label: String,
    val callCountToday: Int = 0,
    val isExhausted: Boolean = false,
    val exhaustedTimestamp: Long = 0L,
    val lastUsedDate: String = getTodayDateString()
) {
    fun getMaskedKey(): String {
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "••••••••"
        return "${trimmed.take(6)}...${trimmed.takeLast(4)}"
    }
}

fun getTodayDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date())
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("thoughtdrop_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    // Gemini Pool
    private val _apiKeyPoolFlow: MutableStateFlow<List<ApiKeyEntry>>
    val apiKeyPoolFlow: StateFlow<List<ApiKeyEntry>>

    private val _apiKeyFlow: MutableStateFlow<String>
    val apiKeyFlow: StateFlow<String>

    // Groq Pool
    private val _groqPoolFlow: MutableStateFlow<List<ApiKeyEntry>>
    val groqPoolFlow: StateFlow<List<ApiKeyEntry>>

    private val _groqApiKeyFlow: MutableStateFlow<String>
    val groqApiKeyFlow: StateFlow<String>

    private val _aiEngineFlow: MutableStateFlow<String>
    val aiEngineFlow: StateFlow<String>

    init {
        // Load Gemini Pool
        val loadedGeminiPool = loadStoredPool(KEY_API_POOL_JSON, KEY_API_KEY, BuildConfig.DEFAULT_GEMINI_KEY, "Gemini Account")
        _apiKeyPoolFlow = MutableStateFlow(loadedGeminiPool)
        apiKeyPoolFlow = _apiKeyPoolFlow.asStateFlow()

        val primaryGeminiKey = getActiveKeyFromPool(loadedGeminiPool)
        _apiKeyFlow = MutableStateFlow(primaryGeminiKey)
        apiKeyFlow = _apiKeyFlow.asStateFlow()

        // Load Groq Pool
        val loadedGroqPool = loadStoredPool(KEY_GROQ_POOL_JSON, KEY_GROQ_API_KEY, "", "Groq Account")
        _groqPoolFlow = MutableStateFlow(loadedGroqPool)
        groqPoolFlow = _groqPoolFlow.asStateFlow()

        val primaryGroqKey = getActiveKeyFromPool(loadedGroqPool)
        _groqApiKeyFlow = MutableStateFlow(primaryGroqKey)
        groqApiKeyFlow = _groqApiKeyFlow.asStateFlow()

        val storedEngine = prefs.getString(KEY_AI_ENGINE, ENGINE_AUTO) ?: ENGINE_AUTO
        _aiEngineFlow = MutableStateFlow(storedEngine)
        aiEngineFlow = _aiEngineFlow.asStateFlow()
    }

    private fun loadStoredPool(
        poolKey: String,
        legacySingleKey: String,
        defaultKey: String,
        defaultLabelPrefix: String
    ): List<ApiKeyEntry> {
        val json = prefs.getString(poolKey, null)
        val today = getTodayDateString()

        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<ApiKeyEntry>>() {}.type
                val rawList: List<ApiKeyEntry> = gson.fromJson(json, type) ?: emptyList()
                return rawList.map { entry ->
                    if (entry.lastUsedDate != today) {
                        entry.copy(
                            callCountToday = 0,
                            isExhausted = false,
                            exhaustedTimestamp = 0L,
                            lastUsedDate = today
                        )
                    } else {
                        entry
                    }
                }
            } catch (_: Exception) {
                // fall through to legacy migration
            }
        }

        val legacy = prefs.getString(legacySingleKey, "") ?: ""
        val initialList = mutableListOf<ApiKeyEntry>()

        if (legacy.isNotBlank()) {
            initialList.add(
                ApiKeyEntry(
                    key = legacy.trim(),
                    label = "$defaultLabelPrefix 1",
                    callCountToday = 0,
                    isExhausted = false,
                    lastUsedDate = today
                )
            )
        } else if (defaultKey.isNotBlank()) {
            initialList.add(
                ApiKeyEntry(
                    key = defaultKey.trim(),
                    label = "$defaultLabelPrefix (Default)",
                    callCountToday = 0,
                    isExhausted = false,
                    lastUsedDate = today
                )
            )
        }

        savePoolInternal(poolKey, initialList)
        return initialList
    }

    private fun savePoolInternal(poolKey: String, pool: List<ApiKeyEntry>) {
        val json = gson.toJson(pool)
        prefs.edit().putString(poolKey, json).apply()
    }

    private fun getActiveKeyFromPool(pool: List<ApiKeyEntry>): String {
        return pool.firstOrNull { !it.isExhausted }?.key
            ?: pool.firstOrNull()?.key
            ?: ""
    }

    // ==========================================
    // GEMINI POOL METHODS
    // ==========================================
    private fun saveGeminiPool(pool: List<ApiKeyEntry>) {
        savePoolInternal(KEY_API_POOL_JSON, pool)
        _apiKeyPoolFlow.value = pool
        val active = getActiveKeyFromPool(pool)
        prefs.edit().putString(KEY_API_KEY, active).apply()
        _apiKeyFlow.value = active
    }

    fun getApiKey(): String = _apiKeyFlow.value

    fun getAvailableApiKeyEntry(): ApiKeyEntry? {
        val today = getTodayDateString()
        var updated = false
        val currentPool = _apiKeyPoolFlow.value.map { entry ->
            if (entry.lastUsedDate != today) {
                updated = true
                entry.copy(
                    callCountToday = 0,
                    isExhausted = false,
                    exhaustedTimestamp = 0L,
                    lastUsedDate = today
                )
            } else {
                entry
            }
        }
        if (updated) {
            saveGeminiPool(currentPool)
        }
        return currentPool.firstOrNull { !it.isExhausted }
    }

    fun getAllKeys(): List<ApiKeyEntry> = _apiKeyPoolFlow.value

    fun addApiKey(key: String, label: String = ""): Boolean {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return false

        val currentPool = _apiKeyPoolFlow.value.toMutableList()
        if (currentPool.any { it.key == trimmedKey }) {
            return false
        }

        val nextIndex = currentPool.size + 1
        val computedLabel = label.trim().ifBlank { "Account $nextIndex" }

        val newEntry = ApiKeyEntry(
            key = trimmedKey,
            label = computedLabel,
            callCountToday = 0,
            isExhausted = false,
            lastUsedDate = getTodayDateString()
        )
        currentPool.add(newEntry)
        saveGeminiPool(currentPool)
        return true
    }

    fun removeApiKey(id: String) {
        val currentPool = _apiKeyPoolFlow.value.filterNot { it.id == id }
        saveGeminiPool(currentPool)
    }

    fun resetKeyStatus(id: String) {
        val currentPool = _apiKeyPoolFlow.value.map { entry ->
            if (entry.id == id) {
                entry.copy(isExhausted = false, exhaustedTimestamp = 0L)
            } else {
                entry
            }
        }
        saveGeminiPool(currentPool)
    }

    fun markKeyExhausted(keyString: String) {
        val currentPool = _apiKeyPoolFlow.value.map { entry ->
            if (entry.key == keyString.trim()) {
                entry.copy(isExhausted = true, exhaustedTimestamp = System.currentTimeMillis())
            } else {
                entry
            }
        }
        saveGeminiPool(currentPool)
    }

    fun recordKeySuccess(keyString: String) {
        val currentPool = _apiKeyPoolFlow.value.map { entry ->
            if (entry.key == keyString.trim()) {
                entry.copy(
                    callCountToday = entry.callCountToday + 1,
                    lastUsedDate = getTodayDateString()
                )
            } else {
                entry
            }
        }
        saveGeminiPool(currentPool)
    }

    fun setApiKey(key: String) {
        addApiKey(key, "Primary Key")
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_MODEL, "gemini-4-hydrogen") ?: "gemini-4-hydrogen"
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    // ==========================================
    // GROQ MULTI-KEY POOL METHODS
    // ==========================================
    private fun saveGroqPool(pool: List<ApiKeyEntry>) {
        savePoolInternal(KEY_GROQ_POOL_JSON, pool)
        _groqPoolFlow.value = pool
        val active = getActiveKeyFromPool(pool)
        prefs.edit().putString(KEY_GROQ_API_KEY, active).apply()
        _groqApiKeyFlow.value = active
    }

    fun getGroqApiKey(): String = _groqApiKeyFlow.value

    fun getAvailableGroqKeyEntry(): ApiKeyEntry? {
        val today = getTodayDateString()
        var updated = false
        val currentPool = _groqPoolFlow.value.map { entry ->
            if (entry.lastUsedDate != today) {
                updated = true
                entry.copy(
                    callCountToday = 0,
                    isExhausted = false,
                    exhaustedTimestamp = 0L,
                    lastUsedDate = today
                )
            } else {
                entry
            }
        }
        if (updated) {
            saveGroqPool(currentPool)
        }
        return currentPool.firstOrNull { !it.isExhausted }
    }

    fun getAllGroqKeys(): List<ApiKeyEntry> = _groqPoolFlow.value

    fun addGroqApiKey(key: String, label: String = ""): Boolean {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return false

        val currentPool = _groqPoolFlow.value.toMutableList()
        if (currentPool.any { it.key == trimmedKey }) {
            return false
        }

        val nextIndex = currentPool.size + 1
        val computedLabel = label.trim().ifBlank { "Groq Account $nextIndex" }

        val newEntry = ApiKeyEntry(
            key = trimmedKey,
            label = computedLabel,
            callCountToday = 0,
            isExhausted = false,
            lastUsedDate = getTodayDateString()
        )
        currentPool.add(newEntry)
        saveGroqPool(currentPool)
        return true
    }

    fun removeGroqApiKey(id: String) {
        val currentPool = _groqPoolFlow.value.filterNot { it.id == id }
        saveGroqPool(currentPool)
    }

    fun resetGroqKeyStatus(id: String) {
        val currentPool = _groqPoolFlow.value.map { entry ->
            if (entry.id == id) {
                entry.copy(isExhausted = false, exhaustedTimestamp = 0L)
            } else {
                entry
            }
        }
        saveGroqPool(currentPool)
    }

    fun markGroqKeyExhausted(keyString: String) {
        val currentPool = _groqPoolFlow.value.map { entry ->
            if (entry.key == keyString.trim()) {
                entry.copy(isExhausted = true, exhaustedTimestamp = System.currentTimeMillis())
            } else {
                entry
            }
        }
        saveGroqPool(currentPool)
    }

    fun recordGroqKeySuccess(keyString: String) {
        val currentPool = _groqPoolFlow.value.map { entry ->
            if (entry.key == keyString.trim()) {
                entry.copy(
                    callCountToday = entry.callCountToday + 1,
                    lastUsedDate = getTodayDateString()
                )
            } else {
                entry
            }
        }
        saveGroqPool(currentPool)
    }

    fun setGroqApiKey(key: String) {
        addGroqApiKey(key, "Primary Groq Key")
    }

    // ==========================================
    // TOTAL USAGE & ENGINE ROUTING
    // ==========================================
    fun getTodayTotalCalls(): Int {
        val geminiCalls = _apiKeyPoolFlow.value.sumOf { it.callCountToday }
        val groqCalls = _groqPoolFlow.value.sumOf { it.callCountToday }
        return geminiCalls + groqCalls
    }

    fun getSelectedEngine(): String = _aiEngineFlow.value

    fun setSelectedEngine(engine: String) {
        val valid = when (engine) {
            ENGINE_GROQ, ENGINE_GEMINI -> engine
            else -> ENGINE_AUTO
        }
        prefs.edit().putString(KEY_AI_ENGINE, valid).apply()
        _aiEngineFlow.value = valid
    }

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_API_POOL_JSON = "gemini_api_pool_json"
        private const val KEY_MODEL = "gemini_model_name"

        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_GROQ_POOL_JSON = "groq_api_pool_json"
        private const val KEY_AI_ENGINE = "selected_ai_engine"

        const val ENGINE_AUTO = "auto"     // Gemini primary -> Groq fallback
        const val ENGINE_GEMINI = "gemini" // Pure Gemini Multimodal
        const val ENGINE_GROQ = "groq"     // Pure Groq Whisper + Llama
    }
}
