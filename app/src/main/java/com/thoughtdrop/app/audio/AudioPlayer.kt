package com.thoughtdrop.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class PlaybackState {
    object Idle : PlaybackState()
    data class Playing(
        val noteId: Long,
        val audioPath: String,
        val currentPositionMs: Int = 0,
        val totalDurationMs: Int = 0,
        val isPaused: Boolean = false
    ) : PlaybackState() {
        val progress: Float
            get() = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f
    }
}

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    fun play(noteId: Long, audioPath: String) {
        val current = _playbackState.value
        if (current is PlaybackState.Playing && current.noteId == noteId && current.audioPath == audioPath) {
            if (current.isPaused) {
                resume()
                return
            } else {
                pause()
                return
            }
        }

        stop()

        val file = File(audioPath)
        if (!file.exists()) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                val totalDuration = duration
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                start()
                _playbackState.value = PlaybackState.Playing(
                    noteId = noteId,
                    audioPath = audioPath,
                    currentPositionMs = 0,
                    totalDurationMs = totalDuration,
                    isPaused = false
                )
            }
            startProgressTicker(noteId, audioPath)
        } catch (_: Exception) {
            stop()
        }
    }

    fun pause() {
        try {
            val current = _playbackState.value
            if (current is PlaybackState.Playing && !current.isPaused) {
                mediaPlayer?.pause()
                progressJob?.cancel()
                _playbackState.value = current.copy(
                    isPaused = true,
                    currentPositionMs = mediaPlayer?.currentPosition ?: current.currentPositionMs
                )
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun resume() {
        try {
            val current = _playbackState.value
            if (current is PlaybackState.Playing && current.isPaused) {
                mediaPlayer?.start()
                _playbackState.value = current.copy(isPaused = false)
                startProgressTicker(current.noteId, current.audioPath)
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            val current = _playbackState.value
            if (current is PlaybackState.Playing) {
                mediaPlayer?.seekTo(positionMs)
                _playbackState.value = current.copy(currentPositionMs = positionMs)
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (_: Exception) {}
                try {
                    mp.reset()
                } catch (_: Exception) {}
                try {
                    mp.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            _playbackState.value = PlaybackState.Idle
        }
    }

    private fun startProgressTicker(noteId: Long, audioPath: String) {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    val pos = mp.currentPosition
                    val dur = mp.duration
                    val current = _playbackState.value
                    if (current is PlaybackState.Playing && current.noteId == noteId && current.audioPath == audioPath) {
                        _playbackState.value = current.copy(
                            currentPositionMs = pos,
                            totalDurationMs = dur,
                            isPaused = false
                        )
                    }
                }
                delay(120)
            }
        }
    }
}
