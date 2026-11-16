package com.thoughtdrop.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.thoughtdrop.app.audio.RecordingState
import com.thoughtdrop.app.audio.VoiceRecorder
import com.thoughtdrop.app.data.ai.GeminiAudioClient
import com.thoughtdrop.app.data.ai.ParsedNoteResult
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

sealed class QuickRecordStatus {
    object Idle : QuickRecordStatus()
    object Recording : QuickRecordStatus()
    data class Processing(val elapsedSeconds: Int = 0) : QuickRecordStatus()
    data class Success(val title: String, val category: String) : QuickRecordStatus()
    data class Error(val message: String) : QuickRecordStatus()
}

class QuickRecordActivity : ComponentActivity() {

    private lateinit var voiceRecorder: VoiceRecorder
    private val geminiClient = GeminiAudioClient()
    private val gson = Gson()

    private val statusState = mutableStateOf<QuickRecordStatus>(QuickRecordStatus.Idle)
    private var hasRecordPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasRecordPermission = isGranted
        if (isGranted) {
            startRecording()
        } else {
            statusState.value = QuickRecordStatus.Error("Microphone permission required for Quick Record")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable display over the lockscreen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        voiceRecorder = VoiceRecorder(applicationContext)

        hasRecordPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            ThoughtDropTheme {
                val status by remember { statusState }
                val recordingState by voiceRecorder.recordingState.collectAsState()

                QuickRecordOverlay(
                    status = status,
                    recordingState = recordingState,
                    onStopAndSave = { stopAndProcess() },
                    onCancel = { cancelAndExit() },
                    onUnlockAndOpen = { unlockAndOpenMain() }
                )
            }
        }

        if (hasRecordPermission) {
            startRecording()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val file = voiceRecorder.startRecording()
        if (file != null) {
            statusState.value = QuickRecordStatus.Recording
        } else {
            statusState.value = QuickRecordStatus.Error("Failed to access microphone")
        }
    }

    private fun stopAndProcess() {
        val (audioFile, durationSeconds) = voiceRecorder.stopRecording()
        if (audioFile == null || durationSeconds < 1) {
            cancelAndExit()
            return
        }

        statusState.value = QuickRecordStatus.Processing(0)

        lifecycleScope.launch {
            val app = application as ThoughtDropApp
            val engine = app.settingsRepository.getSelectedEngine()
            val hasGeminiKeys = app.settingsRepository.getAllKeys().isNotEmpty()
            val hasGroqKeys = app.settingsRepository.getAllGroqKeys().isNotEmpty()
            val model = app.settingsRepository.getSelectedModel()

            if (!hasGeminiKeys && !hasGroqKeys) {
                statusState.value = QuickRecordStatus.Error("No API keys configured. Open ThoughtDrop to configure.")
                return@launch
            }

            // Start elapsed counter ticker
            val ticker = launch {
                var sec = 0
                while (true) {
                    delay(1000)
                    sec++
                    if (statusState.value is QuickRecordStatus.Processing) {
                        statusState.value = QuickRecordStatus.Processing(sec)
                    }
                }
            }

            val groqClient = com.thoughtdrop.app.data.ai.GroqApiClient()
            val result = when {
                engine == com.thoughtdrop.app.data.repository.SettingsRepository.ENGINE_GROQ -> {
                    groqClient.analyzeAudioWithGroq(audioFile, app.settingsRepository)
                }
                engine == com.thoughtdrop.app.data.repository.SettingsRepository.ENGINE_GEMINI -> {
                    geminiClient.analyzeAudio(audioFile, app.settingsRepository, model)
                }
                else -> {
                    val geminiResult = geminiClient.analyzeAudio(audioFile, app.settingsRepository, model)
                    if (geminiResult.isSuccess) {
                        geminiResult
                    } else if (hasGroqKeys) {
                        groqClient.analyzeAudioWithGroq(audioFile, app.settingsRepository)
                    } else {
                        geminiResult
                    }
                }
            }
            ticker.cancel()

            result.onSuccess { parsed ->
                saveNoteToDatabase(app, parsed, audioFile, durationSeconds)

                // Voice Command Auto Reminder
                if (parsed.hasReminder) {
                    val task = parsed.reminderTask?.ifBlank { parsed.title } ?: parsed.title
                    val hour = parsed.reminderHour ?: 9
                    val minute = parsed.reminderMinute ?: 0
                    com.thoughtdrop.app.ui.util.CalendarUtils.setSilentDeviceAlarm(
                        context = applicationContext,
                        taskTitle = task,
                        hour = hour,
                        minute = minute
                    )
                }

                statusState.value = QuickRecordStatus.Success(parsed.title, parsed.category)

                // Auto dismiss after 2.5s if untouched
                delay(2500)
                if (statusState.value is QuickRecordStatus.Success) {
                    finish()
                }
            }.onFailure { err ->
                statusState.value = QuickRecordStatus.Error(err.message ?: "AI structuring failed")
            }
        }
    }

    private suspend fun saveNoteToDatabase(
        app: ThoughtDropApp,
        parsed: ParsedNoteResult,
        audioFile: File,
        durationSeconds: Int
    ) {
        val noteEntity = NoteEntity(
            title = parsed.title.ifBlank { "Voice Thought" },
            category = parsed.category.ifBlank { "Idea" },
            summary = parsed.summary,
            keyPointsJson = gson.toJson(parsed.keyPoints),
            actionItemsJson = gson.toJson(parsed.actionItems),
            completedActionsJson = "[]",
            transcript = parsed.transcript,
            audioPath = audioFile.absolutePath,
            durationSeconds = durationSeconds,
            createdAt = System.currentTimeMillis()
        )
        app.database.noteDao().insertNote(noteEntity)
    }

    private fun cancelAndExit() {
        voiceRecorder.cancelRecording()
        finish()
    }

    private fun unlockAndOpenMain() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    launchMainActivity()
                }
            })
        } else {
            launchMainActivity()
        }
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (voiceRecorder.isRecording()) {
            voiceRecorder.cancelRecording()
        }
    }
}

@Composable
fun QuickRecordOverlay(
    status: QuickRecordStatus,
    recordingState: RecordingState,
    onStopAndSave: () -> Unit,
    onCancel: () -> Unit,
    onUnlockAndOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.5.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (status) {
                    is QuickRecordStatus.Recording, QuickRecordStatus.Idle -> {
                        val duration = if (recordingState is RecordingState.Recording) {
                            recordingState.durationSeconds
                        } else 0
                        val minutes = duration / 60
                        val seconds = duration % 60
                        val timerText = String.format("%02d:%02d", minutes, seconds)

                        val amplitude = if (recordingState is RecordingState.Recording) {
                            recordingState.amplitude
                        } else 0f

                        val infiniteTransition = rememberInfiniteTransition(label = "quickAura")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f + (amplitude * 0.3f),
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "quickPulse"
                        )

                        // Top Brand Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NebulaRecordRose.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, NebulaRecordRose.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(NebulaRecordRose, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LOCK SCREEN QUICK RECORD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    color = NebulaRecordRose
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Big Timer
                        Text(
                            text = timerText,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Wavebar audio equalizer
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val baseHeights = listOf(0.3f, 0.6f, 0.9f, 0.45f, 0.85f, 0.35f, 1f, 0.7f, 0.4f, 0.8f, 0.5f, 0.95f)
                            baseHeights.forEachIndexed { _, factor ->
                                val heightFrac = (factor * (0.35f + (amplitude * 0.65f))).coerceIn(0.15f, 1f)
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight(heightFrac)
                                        .background(
                                            Brush.verticalGradient(listOf(NebulaViolet, NebulaRecordRose)),
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Hero Stop/Save Record Button
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(105.dp)
                                    .scale(pulseScale)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(NebulaViolet.copy(alpha = 0.35f), NebulaRecordRose.copy(alpha = 0.15f), Color.Transparent)
                                        ),
                                        shape = CircleShape
                                    )
                            )
                            IconButton(
                                onClick = onStopAndSave,
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(
                                        Brush.linearGradient(listOf(NebulaRecordRose, NebulaViolet)),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop & Save",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Speak your thought. Tap square to save.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ObsidianBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Text("Discard", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = onStopAndSave,
                                modifier = Modifier.weight(1.3f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)
                            ) {
                                Text("Save Thought", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    is QuickRecordStatus.Processing -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(74.dp),
                                color = NebulaViolet,
                                trackColor = ObsidianSurfaceElevated,
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "${status.elapsedSeconds}s",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = NebulaCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "✦ Structuring with Gemini…",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Extracting title, action checklist, and key insights…",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    is QuickRecordStatus.Success -> {
                        Surface(
                            shape = CircleShape,
                            color = NebulaEmerald.copy(alpha = 0.2f),
                            border = BorderStroke(1.5.dp, NebulaEmerald),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NebulaEmeraldLight, modifier = Modifier.size(32.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Thought Saved Securely!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = status.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "#${status.category}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NebulaCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onUnlockAndOpen,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NebulaViolet)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock to View Feed", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    is QuickRecordStatus.Error -> {
                        Surface(
                            shape = CircleShape,
                            color = NebulaRecordRose.copy(alpha = 0.2f),
                            border = BorderStroke(1.5.dp, NebulaRecordRose),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = NebulaRecordRose, modifier = Modifier.size(30.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Unable to Complete",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = status.message,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated)
                        ) {
                            Text("Dismiss", color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
