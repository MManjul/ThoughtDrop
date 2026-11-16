package com.thoughtdrop.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val durationSeconds: Int, val amplitude: Float) : RecordingState()
}

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var timerJob: Job? = null
    private var startTimeMillis: Long = 0L

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    fun isRecording(): Boolean = _recordingState.value is RecordingState.Recording

    fun startRecording(): File? {
        if (isRecording()) return currentOutputFile

        val audioDir = File(context.filesDir, "audio_notes").apply { mkdirs() }
        val fileName = "thought_${System.currentTimeMillis()}.m4a"
        val outputFile = File(audioDir, fileName)

        recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(48000) // Fast, lightweight, crystal clear for human speech
            setAudioSamplingRate(24000)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
            } catch (e: IOException) {
                Log.e("VoiceRecorder", "Failed to start MediaRecorder", e)
                release()
                return null
            }
        }

        currentOutputFile = outputFile
        startTimeMillis = System.currentTimeMillis()

        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                val amp = try {
                    val maxAmp = recorder?.maxAmplitude ?: 0
                    (maxAmp / 32767f).coerceIn(0f, 1f)
                } catch (_: Exception) {
                    0f
                }
                _recordingState.value = RecordingState.Recording(elapsed, amp)
                delay(100)
            }
        }

        return outputFile
    }

    fun stopRecording(): Pair<File?, Int> {
        timerJob?.cancel()
        timerJob = null
        val duration = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
        val file = currentOutputFile

        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w("VoiceRecorder", "Error stopping recorder (audio might be too short)", e)
        } finally {
            recorder = null
            _recordingState.value = RecordingState.Idle
        }

        return Pair(file, duration)
    }

    fun cancelRecording() {
        timerJob?.cancel()
        timerJob = null
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            recorder = null
            currentOutputFile?.delete()
            currentOutputFile = null
            _recordingState.value = RecordingState.Idle
        }
    }
}
