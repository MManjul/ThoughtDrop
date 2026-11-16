package com.thoughtdrop.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.thoughtdrop.app.ThoughtDropApp
import com.thoughtdrop.app.audio.AudioPlayer
import com.thoughtdrop.app.audio.PlaybackState
import com.thoughtdrop.app.audio.RecordingState
import com.thoughtdrop.app.audio.VoiceRecorder
import com.thoughtdrop.app.data.ai.GeminiAudioClient
import com.thoughtdrop.app.data.model.AudioRecord
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UiProcessingState {
    object Idle : UiProcessingState()
    object Recording : UiProcessingState()
    data class Processing(val message: String = "Gemini is structuring your idea…") : UiProcessingState()
    data class Error(val error: String) : UiProcessingState()
}

sealed class FollowUpState {
    object Idle : FollowUpState()
    data class Recording(val noteId: Long) : FollowUpState()
    data class Processing(val noteId: Long, val message: String = "Gemini is updating your note…") : FollowUpState()
    data class Error(val message: String) : FollowUpState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ThoughtDropApp
    private val noteDao = app.database.noteDao()
    val settingsRepo = app.settingsRepository

    val voiceRecorder = VoiceRecorder(application)
    val audioPlayer = AudioPlayer(application)
    private val geminiClient = GeminiAudioClient()
    private val groqClient = com.thoughtdrop.app.data.ai.GroqApiClient()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    val categories: StateFlow<List<String>> = noteDao.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = combine(
        _searchQuery,
        _selectedCategory
    ) { query, category ->
        Pair(query, category)
    }.flatMapLatest { (query, category) ->
        when {
            query.isNotBlank() -> noteDao.searchNotes(query.trim())
            category != null -> noteDao.getNotesByCategory(category)
            else -> noteDao.getAllNotes()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiProcessingState = MutableStateFlow<UiProcessingState>(UiProcessingState.Idle)
    val uiProcessingState: StateFlow<UiProcessingState> = _uiProcessingState.asStateFlow()

    private val _followUpState = MutableStateFlow<FollowUpState>(FollowUpState.Idle)
    val followUpState: StateFlow<FollowUpState> = _followUpState.asStateFlow()

    val recordingState: StateFlow<RecordingState> = voiceRecorder.recordingState
    val playbackState: StateFlow<PlaybackState> = audioPlayer.playbackState

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun startRecording(): Boolean {
        val engine = settingsRepo.getSelectedEngine()
        val hasGeminiKeys = settingsRepo.getAllKeys().isNotEmpty()
        val hasGroqKey = settingsRepo.getGroqApiKey().isNotBlank()

        if (engine == SettingsRepository.ENGINE_GROQ && !hasGroqKey) {
            _uiProcessingState.value = UiProcessingState.Error("Please set your Groq API key in Settings (⚙️).")
            return false
        }
        if (engine == SettingsRepository.ENGINE_GEMINI && !hasGeminiKeys) {
            _uiProcessingState.value = UiProcessingState.Error("Please add your Gemini API key in Settings first.")
            return false
        }
        if (engine == SettingsRepository.ENGINE_AUTO && !hasGeminiKeys && !hasGroqKey) {
            _uiProcessingState.value = UiProcessingState.Error("Please configure either a Gemini or Groq key in Settings (⚙️).")
            return false
        }

        val file = voiceRecorder.startRecording()
        if (file != null) {
            _uiProcessingState.value = UiProcessingState.Recording
            return true
        } else {
            _uiProcessingState.value = UiProcessingState.Error("Could not access microphone.")
            return false
        }
    }

    fun stopAndProcess() {
        val (audioFile, duration) = voiceRecorder.stopRecording()
        if (audioFile == null || !audioFile.exists() || audioFile.length() < 1024L) {
            _uiProcessingState.value = UiProcessingState.Error("Audio was too short or empty.")
            return
        }

        val engine = settingsRepo.getSelectedEngine()
        val groqKey = settingsRepo.getGroqApiKey()
        val model = settingsRepo.getSelectedModel()

        viewModelScope.launch {
            val hasGroqKeys = settingsRepo.getAllGroqKeys().isNotEmpty()
            val result = when {
                engine == SettingsRepository.ENGINE_GROQ -> {
                    _uiProcessingState.value = UiProcessingState.Processing("Groq is transcribing & structuring…")
                    groqClient.analyzeAudioWithGroq(audioFile, settingsRepo)
                }
                engine == SettingsRepository.ENGINE_GEMINI -> {
                    _uiProcessingState.value = UiProcessingState.Processing("Gemini is analyzing your voice…")
                    geminiClient.analyzeAudio(audioFile, settingsRepo, model)
                }
                else -> {
                    // ENGINE_AUTO: Try Gemini first, auto-failover to Groq for ANY Gemini failure!
                    _uiProcessingState.value = UiProcessingState.Processing("Analyzing with Gemini…")
                    val geminiResult = geminiClient.analyzeAudio(audioFile, settingsRepo, model)
                    if (geminiResult.isSuccess) {
                        geminiResult
                    } else if (hasGroqKeys) {
                        val errMsg = geminiResult.exceptionOrNull()?.message.orEmpty()
                        android.util.Log.w("MainViewModel", "Gemini failed ($errMsg). Auto-failing over to Groq Cloud...")
                        _uiProcessingState.value = UiProcessingState.Processing("Structuring with Groq Cloud…")
                        groqClient.analyzeAudioWithGroq(audioFile, settingsRepo)
                    } else {
                        geminiResult
                    }
                }
            }

            result.fold(
                onSuccess = { parsed ->
                    val initialRecord = AudioRecord(
                        path = audioFile.absolutePath,
                        durationSeconds = duration,
                        timestamp = System.currentTimeMillis(),
                        label = "Original Voice Memo"
                    )
                    val note = NoteEntity(
                        title = parsed.title.ifBlank { "Voice Note" },
                        category = parsed.category.ifBlank { "Idea" },
                        summary = parsed.summary,
                        keyPointsJson = Gson().toJson(parsed.keyPoints),
                        actionItemsJson = Gson().toJson(parsed.actionItems),
                        completedActionsJson = "[]",
                        transcript = parsed.transcript,
                        audioPath = audioFile.absolutePath,
                        audioListJson = Gson().toJson(listOf(initialRecord)),
                        durationSeconds = duration,
                        createdAt = System.currentTimeMillis()
                    )
                    noteDao.insertNote(note)

                    // Voice Command Handler: Automatically set phone reminder without manual UI interaction
                    if (parsed.hasReminder) {
                        val reminderTitle = parsed.reminderTask?.ifBlank { note.title } ?: note.title
                        val hour = parsed.reminderHour ?: 9
                        val minute = parsed.reminderMinute ?: 0
                        val scheduled = com.thoughtdrop.app.ui.util.CalendarUtils.setSilentDeviceAlarm(
                            context = getApplication(),
                            taskTitle = reminderTitle,
                            hour = hour,
                            minute = minute
                        )
                        if (scheduled) {
                            android.util.Log.i("MainViewModel", "Auto-set device alarm: $hour:$minute for $reminderTitle")
                        }
                    }

                    _uiProcessingState.value = UiProcessingState.Idle
                },
                onFailure = { error ->
                    android.util.Log.e("MainViewModel", "Audio processing failed: ${error.message}", error)
                    _uiProcessingState.value = UiProcessingState.Error(error.message ?: "Failed to process audio")
                }
            )
        }
    }

    // Follow-up voice update for existing notes
    fun startFollowUpRecording(note: NoteEntity): Boolean {
        val engine = settingsRepo.getSelectedEngine()
        val hasGeminiKeys = settingsRepo.getAllKeys().isNotEmpty()
        val hasGroqKey = settingsRepo.getGroqApiKey().isNotBlank()

        if (engine == SettingsRepository.ENGINE_GROQ && !hasGroqKey) {
            _followUpState.value = FollowUpState.Error("Please set your Groq API key in Settings (⚙️).")
            return false
        }
        if (engine == SettingsRepository.ENGINE_GEMINI && !hasGeminiKeys) {
            _followUpState.value = FollowUpState.Error("Please add your Gemini API key in Settings.")
            return false
        }

        val file = voiceRecorder.startRecording()
        if (file != null) {
            _followUpState.value = FollowUpState.Recording(note.id)
            return true
        } else {
            _followUpState.value = FollowUpState.Error("Could not access microphone.")
            return false
        }
    }

    fun stopFollowUpAndRefine(note: NoteEntity) {
        val (audioFile, duration) = voiceRecorder.stopRecording()
        if (audioFile == null || !audioFile.exists() || audioFile.length() < 1024L) {
            _followUpState.value = FollowUpState.Error("Audio was too short.")
            return
        }

        val engine = settingsRepo.getSelectedEngine()
        val groqKey = settingsRepo.getGroqApiKey()
        val model = settingsRepo.getSelectedModel()
        _followUpState.value = FollowUpState.Processing(note.id)

        viewModelScope.launch {
            val hasGroqKeys = settingsRepo.getAllGroqKeys().isNotEmpty()
            val result = when {
                engine == SettingsRepository.ENGINE_GROQ -> {
                    groqClient.refineExistingNoteWithGroq(audioFile, note, settingsRepo)
                }
                engine == SettingsRepository.ENGINE_GEMINI -> {
                    geminiClient.refineExistingNoteWithAudio(audioFile, note, settingsRepo, model)
                }
                else -> {
                    // Try Gemini first, auto-failover to Groq on ANY failure
                    val geminiResult = geminiClient.refineExistingNoteWithAudio(audioFile, note, settingsRepo, model)
                    if (geminiResult.isSuccess) {
                        geminiResult
                    } else if (hasGroqKeys) {
                        val errMsg = geminiResult.exceptionOrNull()?.message.orEmpty()
                        android.util.Log.w("MainViewModel", "Gemini follow-up failed ($errMsg). Failing over to Groq...")
                        _followUpState.value = FollowUpState.Processing(note.id, "Updating with Groq Cloud…")
                        groqClient.refineExistingNoteWithGroq(audioFile, note, settingsRepo)
                    } else {
                        geminiResult
                    }
                }
            }
            result.fold(
                onSuccess = { parsed ->
                    val timeFormat = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                    val formattedTime = timeFormat.format(Date())

                    val appendedTranscript = if (note.transcript.isNotBlank()) {
                        "${note.transcript}\n\n✦ [Update $formattedTime]:\n${parsed.transcript}"
                    } else {
                        parsed.transcript
                    }

                    // Keep completed actions if still part of action items
                    val currentCompleted = note.getCompletedActions()
                    val newCompleted = currentCompleted.filter { parsed.actionItems.contains(it) }.toSet()

                    // Append new audio recording to audio history list (DO NOT OVERWRITE OLD AUDIO!)
                    val existingAudioList = note.getAudioRecords().toMutableList()
                    val updateIndex = existingAudioList.size
                    val timeFormatShort = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val formattedTimeShort = timeFormatShort.format(Date())
                    existingAudioList.add(
                        AudioRecord(
                            path = audioFile.absolutePath,
                            durationSeconds = duration,
                            timestamp = System.currentTimeMillis(),
                            label = "Voice Update #$updateIndex • $formattedTimeShort"
                        )
                    )

                    val updatedNote = note.copy(
                        title = parsed.title.ifBlank { note.title },
                        category = parsed.category.ifBlank { note.category },
                        summary = parsed.summary.ifBlank { note.summary },
                        keyPointsJson = Gson().toJson(parsed.keyPoints),
                        actionItemsJson = Gson().toJson(parsed.actionItems),
                        completedActionsJson = Gson().toJson(newCompleted),
                        transcript = appendedTranscript,
                        audioPath = audioFile.absolutePath, // latest audio
                        audioListJson = Gson().toJson(existingAudioList), // All audio preserved in list!
                        durationSeconds = existingAudioList.sumOf { it.durationSeconds },
                        imagePath = note.imagePath,
                        imageListJson = note.imageListJson
                    )
                    noteDao.updateNote(updatedNote)

                    // Auto-set phone reminder if voice command was detected in follow-up
                    if (parsed.hasReminder) {
                        val reminderTitle = parsed.reminderTask?.ifBlank { updatedNote.title } ?: updatedNote.title
                        val hour = parsed.reminderHour ?: 9
                        val minute = parsed.reminderMinute ?: 0
                        com.thoughtdrop.app.ui.util.CalendarUtils.setSilentDeviceAlarm(
                            context = getApplication(),
                            taskTitle = reminderTitle,
                            hour = hour,
                            minute = minute
                        )
                    }

                    _followUpState.value = FollowUpState.Idle
                },
                onFailure = { error ->
                    _followUpState.value = FollowUpState.Error(error.message ?: "Failed to update note")
                }
            )
        }
    }

    fun cancelFollowUp() {
        voiceRecorder.cancelRecording()
        _followUpState.value = FollowUpState.Idle
    }

    fun dismissFollowUpError() {
        _followUpState.value = FollowUpState.Idle
    }

    // MULTIMODAL PHOTO SUPPORT: Create a new note from photo
    fun processImageNote(imageFile: File) {
        if (settingsRepo.getAllKeys().isEmpty()) {
            _uiProcessingState.value = UiProcessingState.Error("Please add your Gemini API key in Settings first.")
            return
        }

        val model = settingsRepo.getSelectedModel()
        _uiProcessingState.value = UiProcessingState.Processing("Gemini is reading and structuring your photo…")

        viewModelScope.launch {
            val result = geminiClient.analyzeImage(imageFile, settingsRepo, model)
            result.fold(
                onSuccess = { parsed ->
                    val note = NoteEntity(
                        title = parsed.title.ifBlank { "Photo Note" },
                        category = parsed.category.ifBlank { "Visual" },
                        summary = parsed.summary,
                        keyPointsJson = Gson().toJson(parsed.keyPoints),
                        actionItemsJson = Gson().toJson(parsed.actionItems),
                        completedActionsJson = "[]",
                        transcript = parsed.transcript,
                        audioPath = null,
                        audioListJson = "[]",
                        imagePath = imageFile.absolutePath,
                        imageListJson = Gson().toJson(listOf(imageFile.absolutePath)),
                        durationSeconds = 0,
                        createdAt = System.currentTimeMillis()
                    )
                    noteDao.insertNote(note)
                    _uiProcessingState.value = UiProcessingState.Idle
                },
                onFailure = { error ->
                    _uiProcessingState.value = UiProcessingState.Error(error.message ?: "Failed to analyze photo")
                }
            )
        }
    }

    // MULTIMODAL PHOTO SUPPORT: Attach photo to existing note without overwriting existing content
    fun attachImageToNote(note: NoteEntity, imageFile: File) {
        if (settingsRepo.getAllKeys().isEmpty()) {
            _followUpState.value = FollowUpState.Error("Please add your Gemini API key in Settings.")
            return
        }

        val model = settingsRepo.getSelectedModel()
        _followUpState.value = FollowUpState.Processing(note.id, "Gemini is analyzing photo…")

        viewModelScope.launch {
            val result = geminiClient.refineExistingNoteWithImage(imageFile, note, settingsRepo, model)
            result.fold(
                onSuccess = { parsed ->
                    val timeFormat = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                    val formattedTime = timeFormat.format(Date())

                    val appendedTranscript = if (parsed.transcript.isNotBlank()) {
                        if (note.transcript.isNotBlank()) {
                            "${note.transcript}\n\n✦ [Photo OCR $formattedTime]:\n${parsed.transcript}"
                        } else {
                            "✦ [Photo OCR $formattedTime]:\n${parsed.transcript}"
                        }
                    } else {
                        note.transcript
                    }

                    // Preserve all existing images and append new one
                    val currentImages = note.getImagePaths().toMutableList()
                    if (!currentImages.contains(imageFile.absolutePath)) {
                        currentImages.add(imageFile.absolutePath)
                    }

                    // Non-destructive merge: preserve existing items and add newly discovered ones
                    val mergedKeyPoints = if (parsed.keyPoints.isNotEmpty()) {
                        (note.getKeyPoints() + parsed.keyPoints).distinct()
                    } else {
                        note.getKeyPoints()
                    }

                    val mergedActionItems = if (parsed.actionItems.isNotEmpty()) {
                        (note.getActionItems() + parsed.actionItems).distinct()
                    } else {
                        note.getActionItems()
                    }

                    val currentCompleted = note.getCompletedActions()
                    val newCompleted = currentCompleted.filter { mergedActionItems.contains(it) }.toSet()

                    val updatedNote = note.copy(
                        title = parsed.title.ifBlank { note.title },
                        category = if (note.category.isNotBlank() && note.category != "Visual") note.category else parsed.category.ifBlank { note.category },
                        summary = if (parsed.summary.isNotBlank()) {
                            if (note.summary.isNotBlank()) "${note.summary}\n\n✦ Photo Note: ${parsed.summary}" else parsed.summary
                        } else note.summary,
                        keyPointsJson = Gson().toJson(mergedKeyPoints),
                        actionItemsJson = Gson().toJson(mergedActionItems),
                        completedActionsJson = Gson().toJson(newCompleted),
                        transcript = appendedTranscript,
                        imagePath = imageFile.absolutePath,
                        imageListJson = Gson().toJson(currentImages),
                        audioPath = note.audioPath,
                        audioListJson = note.audioListJson,
                        durationSeconds = note.durationSeconds
                    )
                    noteDao.updateNote(updatedNote)
                    _followUpState.value = FollowUpState.Idle
                },
                onFailure = { error ->
                    _followUpState.value = FollowUpState.Error(error.message ?: "Failed to analyze photo")
                }
            )
        }
    }

    fun removeImageFromNote(note: NoteEntity, imagePathToRemove: String) {
        viewModelScope.launch {
            val updatedImages = note.getImagePaths().filter { it != imagePathToRemove }
            val newPrimary = updatedImages.firstOrNull()
            try { File(imagePathToRemove).delete() } catch (_: Exception) {}
            val updatedNote = note.copy(
                imagePath = newPrimary,
                imageListJson = Gson().toJson(updatedImages)
            )
            noteDao.updateNote(updatedNote)
        }
    }

    fun toggleActionItem(note: NoteEntity, actionItem: String) {
        viewModelScope.launch {
            val current = note.getCompletedActions().toMutableSet()
            if (current.contains(actionItem)) {
                current.remove(actionItem)
            } else {
                current.add(actionItem)
            }
            noteDao.updateNote(note.copy(completedActionsJson = Gson().toJson(current)))
        }
    }

    fun cancelRecording() {
        voiceRecorder.cancelRecording()
        _uiProcessingState.value = UiProcessingState.Idle
    }

    fun dismissError() {
        _uiProcessingState.value = UiProcessingState.Idle
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            audioPlayer.stop()
            note.getAudioRecords().forEach { record ->
                try { File(record.path).delete() } catch (_: Exception) {}
            }
            note.imagePath?.let { try { File(it).delete() } catch (_: Exception) {} }
            noteDao.deleteNote(note)
        }
    }

    fun playAudio(note: NoteEntity) {
        val current = playbackState.value
        val defaultAudio = note.getAudioRecords().firstOrNull()?.path ?: note.audioPath
        if (defaultAudio != null) {
            playAudioPath(note.id, defaultAudio)
        }
    }

    fun playAudioPath(noteId: Long, audioPath: String) {
        audioPlayer.play(noteId, audioPath)
    }

    fun seekAudio(positionMs: Int) {
        audioPlayer.seekTo(positionMs)
    }

    fun pauseAudio() {
        audioPlayer.pause()
    }

    fun resumeAudio() {
        audioPlayer.resume()
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        voiceRecorder.cancelRecording()
    }
}
