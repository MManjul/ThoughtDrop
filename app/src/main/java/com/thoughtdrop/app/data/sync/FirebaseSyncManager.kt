package com.thoughtdrop.app.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.thoughtdrop.app.data.local.NoteDao
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Synced(val lastSyncTime: Long = System.currentTimeMillis()) : SyncState()
    data class Error(val message: String) : SyncState()
}

class FirebaseSyncManager private constructor() {

    companion object {
        private const val TAG = "FirebaseSyncManager"

        @Volatile
        private var instance: FirebaseSyncManager? = null

        fun getInstance(): FirebaseSyncManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseSyncManager().also { instance = it }
            }
        }
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var notesListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null
    private val gson = Gson()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    suspend fun signInWithGoogleCredential(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in.")
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign in with credential failed", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        stopSync()
        auth.signOut()
        _currentUser.value = null
        _syncState.value = SyncState.Idle
    }

    fun startSync(
        noteDao: NoteDao,
        settingsRepo: SettingsRepository,
        scope: CoroutineScope
    ) {
        val user = auth.currentUser ?: return
        stopSync()

        _syncState.value = SyncState.Syncing

        // 1. Real-time Firestore notes listener
        val notesRef = firestore.collection("users").document(user.uid).collection("notes")
        notesListener = notesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to cloud notes", error)
                _syncState.value = SyncState.Error(error.localizedMessage ?: "Sync error")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val noteIdStr = doc.id
                            val rawCreatedAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            val cloudId = noteIdStr.toLongOrNull() ?: rawCreatedAt

                            val title = data["title"] as? String ?: "Untitled"
                            val category = data["category"] as? String ?: "Idea"
                            val summary = data["summary"] as? String ?: ""
                            val transcript = data["transcript"] as? String ?: ""
                            val isPinned = data["isPinned"] as? Boolean ?: false

                            @Suppress("UNCHECKED_CAST")
                            val keyPointsList = data["keyPoints"] as? List<String> ?: emptyList()
                            @Suppress("UNCHECKED_CAST")
                            val actionItemsList = data["actionItems"] as? List<String> ?: emptyList()

                            val keyPointsJson = gson.toJson(keyPointsList)
                            val actionItemsJson = gson.toJson(actionItemsList)

                            val existingNote = noteDao.getNoteById(cloudId) ?: noteDao.getNoteByCreatedAt(rawCreatedAt)
                            if (existingNote == null) {
                                // New note from web / other device
                                val newNote = NoteEntity(
                                    id = cloudId,
                                    title = title,
                                    category = category,
                                    summary = summary,
                                    keyPointsJson = keyPointsJson,
                                    actionItemsJson = actionItemsJson,
                                    transcript = transcript,
                                    isPinned = isPinned,
                                    createdAt = rawCreatedAt
                                )
                                noteDao.insertNote(newNote)
                            } else {
                                // Check if cloud is newer or updated
                                val updated = existingNote.copy(
                                    title = title,
                                    category = category,
                                    summary = summary,
                                    keyPointsJson = keyPointsJson,
                                    actionItemsJson = actionItemsJson,
                                    isPinned = isPinned
                                )
                                noteDao.updateNote(updated)
                            }
                        }
                        _syncState.value = SyncState.Synced()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error applying cloud notes to local DB", e)
                    }
                }
            }
        }

        // 2. Real-time settings listener (Gemini & Groq API key sync)
        val settingsRef = firestore.collection("users").document(user.uid).collection("settings").document("ai_keys")
        settingsListener = settingsRef.addSnapshotListener { doc, err ->
            if (err != null || doc == null || !doc.exists()) return@addSnapshotListener
            val data = doc.data ?: return@addSnapshotListener
            scope.launch(Dispatchers.IO) {
                val cloudGeminiKey = data["geminiApiKey"] as? String
                val cloudGroqKey = data["groqApiKey"] as? String
                val cloudModel = data["selectedModel"] as? String
                val cloudEngine = data["selectedEngine"] as? String

                if (!cloudGeminiKey.isNullOrBlank()) {
                    settingsRepo.addApiKey(cloudGeminiKey, "Web Sync Key")
                }
                if (!cloudGroqKey.isNullOrBlank()) {
                    settingsRepo.addGroqApiKey(cloudGroqKey, "Web Sync Key")
                }
                if (!cloudModel.isNullOrBlank()) {
                    settingsRepo.setSelectedModel(cloudModel)
                }
                if (!cloudEngine.isNullOrBlank()) {
                    settingsRepo.setSelectedEngine(cloudEngine)
                }
            }
        }

        // 3. Initial push of local notes to cloud if not already synced
        scope.launch(Dispatchers.IO) {
            try {
                val localNotes = noteDao.getAllNotesDirect()
                for (localNote in localNotes) {
                    pushNoteToCloudInternal(user.uid, localNote)
                }
                // Push local API keys, pools and models to cloud so web gets them
                pushSettingsToCloudInternal(user.uid, settingsRepo)
                _syncState.value = SyncState.Synced()
            } catch (e: Exception) {
                Log.e(TAG, "Error in initial push to cloud", e)
            }
        }
    }

    fun stopSync() {
        notesListener?.remove()
        notesListener = null
        settingsListener?.remove()
        settingsListener = null
    }

    suspend fun pushNoteToCloud(note: NoteEntity) {
        val user = auth.currentUser ?: return
        pushNoteToCloudInternal(user.uid, note)
    }

    private suspend fun pushNoteToCloudInternal(userId: String, note: NoteEntity) {
        try {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("notes")
                .document(note.id.toString())

            val data = hashMapOf(
                "id" to note.id.toString(),
                "title" to note.title,
                "category" to note.category,
                "summary" to note.summary,
                "keyPoints" to note.getKeyPoints(),
                "actionItems" to note.getActionItems(),
                "transcript" to note.transcript,
                "isPinned" to note.isPinned,
                "createdAt" to note.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )

            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push note ${note.id} to cloud", e)
        }
    }

    suspend fun deleteNoteFromCloud(noteId: Long) {
        val user = auth.currentUser ?: return
        try {
            firestore.collection("users")
                .document(user.uid)
                .collection("notes")
                .document(noteId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete note $noteId from cloud", e)
        }
    }

    suspend fun pushSettingsToCloud(settingsRepo: SettingsRepository) {
        val user = auth.currentUser ?: return
        pushSettingsToCloudInternal(user.uid, settingsRepo)
    }

    private suspend fun pushSettingsToCloudInternal(userId: String, settingsRepo: SettingsRepository) {
        try {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("settings")
                .document("ai_keys")

            val geminiPool = settingsRepo.getAllKeys().map { entry ->
                mapOf(
                    "id" to entry.id,
                    "key" to entry.key,
                    "label" to entry.label,
                    "isExhausted" to entry.isExhausted,
                    "callCountToday" to entry.callCountToday
                )
            }

            val groqPool = settingsRepo.getAllGroqKeys().map { entry ->
                mapOf(
                    "id" to entry.id,
                    "key" to entry.key,
                    "label" to entry.label,
                    "isExhausted" to entry.isExhausted,
                    "callCountToday" to entry.callCountToday
                )
            }

            val data = hashMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis(),
                "geminiApiKey" to settingsRepo.getApiKey(),
                "groqApiKey" to settingsRepo.getGroqApiKey(),
                "geminiPool" to geminiPool,
                "groqPool" to groqPool,
                "selectedModel" to settingsRepo.getSelectedModel(),
                "selectedEngine" to settingsRepo.getSelectedEngine()
            )

            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push settings to cloud", e)
        }
    }
}
