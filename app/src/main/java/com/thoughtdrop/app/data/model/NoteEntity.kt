package com.thoughtdrop.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AudioRecord(
    val path: String,
    val durationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String = "Voice Memo"
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val summary: String,
    val keyPointsJson: String = "[]",
    val actionItemsJson: String = "[]",
    val completedActionsJson: String = "[]",
    val transcript: String,
    val audioPath: String? = null,
    val audioListJson: String = "[]",
    val imagePath: String? = null,
    val imageListJson: String = "[]",
    val durationSeconds: Int = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getKeyPoints(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(keyPointsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getActionItems(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(actionItemsJson, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCompletedActions(): Set<String> {
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            Gson().fromJson(completedActionsJson, type) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun getAudioRecords(): List<AudioRecord> {
        return try {
            val type = object : TypeToken<List<AudioRecord>>() {}.type
            val list: List<AudioRecord>? = Gson().fromJson(audioListJson, type)
            if (!list.isNullOrEmpty()) {
                list
            } else if (!audioPath.isNullOrBlank()) {
                listOf(AudioRecord(path = audioPath, durationSeconds = durationSeconds, timestamp = createdAt, label = "Original Memo"))
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            if (!audioPath.isNullOrBlank()) {
                listOf(AudioRecord(path = audioPath, durationSeconds = durationSeconds, timestamp = createdAt, label = "Original Memo"))
            } else {
                emptyList()
            }
        }
    }

    fun getImagePaths(): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val list: List<String>? = Gson().fromJson(imageListJson, type)
            if (!list.isNullOrEmpty()) {
                list
            } else if (!imagePath.isNullOrBlank()) {
                listOf(imagePath)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            if (!imagePath.isNullOrBlank()) {
                listOf(imagePath)
            } else {
                emptyList()
            }
        }
    }
}
