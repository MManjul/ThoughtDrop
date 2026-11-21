package com.thoughtdrop.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.thoughtdrop.app.audio.PlaybackState
import com.thoughtdrop.app.audio.RecordingState
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.ui.theme.*
import com.thoughtdrop.app.ui.util.FullScreenImageViewerDialog
import com.thoughtdrop.app.ui.util.ImageUtils
import com.thoughtdrop.app.ui.util.ModernGlassCheckbox
import com.thoughtdrop.app.ui.util.NoteImageCard
import com.thoughtdrop.app.ui.util.PdfExportUtils
import com.thoughtdrop.app.ui.viewmodel.FollowUpState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailBottomSheet(
    note: NoteEntity,
    playbackState: PlaybackState,
    followUpState: FollowUpState,
    recordingState: RecordingState,
    onDismiss: () -> Unit,
    onPlayAudio: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleActionItem: (String) -> Unit,
    onStartFollowUp: () -> Unit,
    onStopFollowUp: () -> Unit,
    onCancelFollowUp: () -> Unit,
    onDismissFollowUpError: () -> Unit,
    onAttachPhoto: (File) -> Unit,
    onDelete: () -> Unit,
    onDeleteImage: ((String) -> Unit)? = null,
    onPlayAudioPath: ((Long, String) -> Unit)? = null,
    onSeekAudio: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var isTranscriptExpanded by remember { mutableStateOf(false) }
    var taskToSchedule by remember { mutableStateOf<String?>(null) }
    var previewingImage by remember { mutableStateOf<String?>(null) }

    // Camera and Gallery launchers for photo attachment
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraLaunchUri by remember { mutableStateOf<Uri?>(null) }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraFile != null) {
            onAttachPhoto(cameraFile!!)
        }
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingCameraLaunchUri != null) {
            takePhotoLauncher.launch(pendingCameraLaunchUri!!)
        }
    }

    val launchCameraSafe: (Uri) -> Unit = { uri ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePhotoLauncher.launch(uri)
        } else {
            pendingCameraLaunchUri = uri
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val copied = ImageUtils.copyUriToFile(context, uri)
            if (copied != null) {
                onAttachPhoto(copied)
            }
        }
    }

    val keyPoints = remember(note.keyPointsJson) { note.getKeyPoints() }
    val actionItems = remember(note.actionItemsJson) { note.getActionItems() }
    val completedActions = remember(note.completedActionsJson) { note.getCompletedActions() }
    val isPlaying = playbackState is PlaybackState.Playing && playbackState.noteId == note.id

    val isFollowUpRecording = followUpState is FollowUpState.Recording && followUpState.noteId == note.id
    val isFollowUpProcessing = followUpState is FollowUpState.Processing && followUpState.noteId == note.id

    val style = remember(note.category) { getCategoryStyle(note.category) }
    val accentColor = style.primary

    val relativeTime = remember(note.createdAt) {
        DateUtils.getRelativeTimeSpanString(
            note.createdAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isFollowUpProcessing) {
                if (isFollowUpRecording) {
                    onCancelFollowUp()
                }
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = Color(0xF207120C),
        scrimColor = Color.Black.copy(alpha = 0.82f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0x4DFFFFFF))
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.2.dp, GlassBorderBrush),
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x3086EFAC), // Ambient mint specular reflection
                            Color(0x050E2016),
                            Color(0x6607120C)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header Row: Circular Glass Back Button + Category Tag + Circular Actions (NO OVERLAP)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button `<` in frosted glass circle (Clean Box with no 48dp component enforcement)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GlassSurfaceHighlight, CircleShape)
                        .border(BorderStroke(1.dp, GlassBorderBrush), CircleShape)
                        .clickable {
                            if (!isFollowUpProcessing) {
                                if (isFollowUpRecording) onCancelFollowUp()
                                onDismiss()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Category Tag Pill
                val catStyle = getCategoryStyle(note.category)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = catStyle.bg,
                    border = BorderStroke(1.dp, catStyle.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(catStyle.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = note.category.uppercase(),
                            color = catStyle.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Actions Row: Calendar, Pin, Share, Delete (Cleanly spaced, zero overlap!)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Schedule Note Reminder (Google Calendar / Device Alarm)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceHighlight, CircleShape)
                            .border(BorderStroke(0.8.dp, GlassBorderBrush), CircleShape)
                            .clickable { taskToSchedule = note.title },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = "Schedule Reminder",
                            tint = AccentSky,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Pin / Favorite
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (note.isPinned) Color(0x33C5E1A5) else GlassSurfaceHighlight,
                                CircleShape
                            )
                            .border(
                                BorderStroke(
                                    0.8.dp,
                                    if (note.isPinned) Color(0x80C5E1A5) else Color(0x26FFFFFF)
                                ),
                                CircleShape
                            )
                            .clickable { onTogglePin() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) AccentMintBright else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Export High-Quality Executive PDF
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceHighlight, CircleShape)
                            .border(BorderStroke(0.8.dp, GlassBorderBrush), CircleShape)
                            .clickable {
                                PdfExportUtils.exportNoteAsPdf(context, note)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.PictureAsPdf,
                            contentDescription = "Export PDF",
                            tint = AccentMintBright,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Share
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceHighlight, CircleShape)
                            .border(BorderStroke(0.8.dp, GlassBorderBrush), CircleShape)
                            .clickable { shareNote(context, note) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Delete
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(GlassSurfaceHighlight, CircleShape)
                            .border(BorderStroke(0.8.dp, GlassBorderBrush), CircleShape)
                            .clickable { showDeleteConfirm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = AccentCoral.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Full Prominent Title (Phone 2 style)
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 29.sp
            )

            // AI Engine & Reading Time Stat Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Engine badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = AccentMintBright,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Gemini 4 Hydrogen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentMintBright
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "•", fontSize = 12.sp, color = TextMuted)
                Spacer(modifier = Modifier.width(10.dp))

                val wordCount = remember(note.summary, note.transcript) {
                    (note.summary + " " + note.transcript).split("\\s+".toRegex()).count { it.isNotBlank() }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "~${maxOf(1, wordCount / 150)} min read",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = relativeTime,
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }

            // Attached Photo(s) Display with Full-Screen Review
            val images = remember(note.imageListJson, note.imagePath) { note.getImagePaths() }
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = AccentSky,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CAPTURED PHOTOS (${images.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSky,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (images.size == 1) {
                    NoteImageCard(
                        imagePath = images.first(),
                        maxHeight = 260,
                        onClick = { previewingImage = images.first() }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        images.forEach { imgPath ->
                            NoteImageCard(
                                imagePath = imgPath,
                                modifier = Modifier.weight(1f),
                                maxHeight = 160,
                                onClick = { previewingImage = imgPath }
                            )
                        }
                    }
                }
            }

            // Recorded Audio Memos Playlist with Interactive Seek Bar (Supports older & newer recordings!)
            val audioRecords = remember(note.audioListJson, note.audioPath) { note.getAudioRecords() }
            if (audioRecords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = AccentMintBright,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOICE MEMOS (${audioRecords.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentMintBright,
                        letterSpacing = 0.8.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    audioRecords.forEachIndexed { index, record ->
                        val isThisPlaying = playbackState is PlaybackState.Playing &&
                                playbackState.noteId == note.id &&
                                playbackState.audioPath == record.path
                        val isPaused = isThisPlaying && (playbackState as PlaybackState.Playing).isPaused
                        val currentMs = if (isThisPlaying) (playbackState as PlaybackState.Playing).currentPositionMs else 0
                        val totalMs = if (isThisPlaying && (playbackState as PlaybackState.Playing).totalDurationMs > 0) {
                            (playbackState as PlaybackState.Playing).totalDurationMs
                        } else {
                            (record.durationSeconds * 1000).coerceAtLeast(1000)
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isThisPlaying) GlassSurfaceHighlight else GlassSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (isThisPlaying) GlassBorderMint else GlassBorderBrush
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Play / Pause Circle in Radiant Mint
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .shadow(if (isThisPlaying) 8.dp else 0.dp, CircleShape, spotColor = AccentMintGlow)
                                            .background(AccentMintBright, CircleShape)
                                            .clickable {
                                                if (onPlayAudioPath != null) {
                                                    onPlayAudioPath(note.id, record.path)
                                                } else {
                                                    onPlayAudio()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isThisPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isThisPlaying && !isPaused) "Pause" else "Play",
                                            tint = TextOnMint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = record.label.ifBlank { "Voice Memo #${index + 1}" },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val curSec = currentMs / 1000
                                        val totSec = totalMs / 1000
                                        Text(
                                            text = if (isThisPlaying) {
                                                "${String.format("%02d:%02d", curSec / 60, curSec % 60)} / ${String.format("%02d:%02d", totSec / 60, totSec % 60)}"
                                            } else {
                                                "${record.durationSeconds}s duration"
                                            },
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isThisPlaying) AccentMintBright else TextSecondary
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isThisPlaying) Color(0x33C5E1A5) else Color(0x1FFFFFFF),
                                        border = BorderStroke(0.8.dp, if (isThisPlaying) Color(0x66C5E1A5) else Color(0x14FFFFFF))
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThisPlaying) AccentMintBright else TextMuted,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                        )
                                    }
                                }

                                // Interactive Seek Bar / Slider
                                Spacer(modifier = Modifier.height(6.dp))
                                Slider(
                                    value = currentMs.toFloat().coerceIn(0f, totalMs.toFloat()),
                                    valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
                                    onValueChange = { newPos ->
                                        if (isThisPlaying) {
                                            onSeekAudio?.invoke(newPos.toInt())
                                        } else {
                                            if (onPlayAudioPath != null) {
                                                onPlayAudioPath(note.id, record.path)
                                                onSeekAudio?.invoke(newPos.toInt())
                                            }
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = AccentMintBright,
                                        activeTrackColor = AccentMintBright,
                                        inactiveTrackColor = Color(0x2EFFFFFF)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =================================================================
            // INTERACTIVE: VOICE UPDATE & PHOTO ATTACHMENT
            // =================================================================
            Spacer(modifier = Modifier.height(14.dp))

            when {
                isFollowUpRecording -> {
                    // Active Follow-up Recording HUD
                    val duration = if (recordingState is RecordingState.Recording) {
                        recordingState.durationSeconds
                    } else 0
                    val minutes = duration / 60
                    val seconds = duration % 60
                    val timerText = String.format("%02d:%02d", minutes, seconds)
                    val amplitude = if (recordingState is RecordingState.Recording) recordingState.amplitude else 0f

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, AccentCoral.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).background(AccentCoral, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "RECORDING VOICE UPDATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = AccentCoral
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = timerText,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Equalizer preview
                            Row(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val factors = listOf(0.4f, 0.7f, 1f, 0.5f, 0.9f, 0.4f, 0.8f, 0.6f, 0.95f, 0.5f)
                                factors.forEach { f ->
                                    val h = (f * (0.3f + amplitude * 0.7f)).coerceIn(0.15f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(h)
                                            .background(
                                                AccentCoral,
                                                RoundedCornerShape(1.5.dp)
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Speak your changes, additional details, or new tasks.",
                                fontSize = 11.5.sp,
                                color = TextSecondary
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCancelFollowUp,
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                                ) {
                                    Text("Discard", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = onStopFollowUp,
                                    modifier = Modifier.weight(1.3f).height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Update Note", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }

                isFollowUpProcessing -> {
                    // Processing Indicator
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, AccentViolet.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentViolet,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "✦ Gemini is updating note with your input…",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Analyzing content, merging insights & checklists…",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Action Buttons Row (Phone 2 style): Add Photo pill + Radiant Amber Voice Update Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Add Photo with Camera/Gallery (Frosted glass pill)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = GlassSurfaceElevated,
                            border = BorderStroke(1.dp, GlassBorderBrush),
                            modifier = Modifier
                                .height(50.dp)
                                .clickable { showPhotoSourceDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CameraAlt,
                                    contentDescription = "Add Photo",
                                    tint = AccentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = "Photo",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    color = TextPrimary
                                )
                            }
                        }

                        // 2. Radiant Luminous Mint Button (Leafora action button)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AccentMintBright,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .shadow(14.dp, RoundedCornerShape(16.dp), spotColor = AccentMintGlow)
                                .clickable { onStartFollowUp() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = TextOnMint,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✦ Voice Update Note",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextOnMint
                                )
                            }
                        }
                    }
                }
            }

            // Follow-up Error banner (if any)
            if (followUpState is FollowUpState.Error) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCoral.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentCoral.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = followUpState.message,
                            fontSize = 12.sp,
                            color = AccentCoral,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissFollowUpError, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AccentCoral, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Summary Block (Liquid Frosted Glass)
            if (note.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.2.dp, GlassBorderBrush),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(52.dp)
                                .background(AmberGradient, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = note.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary.copy(alpha = 0.95f),
                            lineHeight = 22.sp,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }

            // Key Insights (Bullet Points with Glowing Sparkle)
            if (keyPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "✦ KEY INSIGHTS",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGold,
                        letterSpacing = 0.9.sp
                    )
                }
                keyPoints.forEach { point ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 10.dp)
                                .size(5.dp)
                                .background(AccentGold, CircleShape)
                        )
                        Text(
                            text = point,
                            fontSize = 13.5.sp,
                            color = TextPrimary.copy(alpha = 0.92f),
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Action Items (Interactive Checkboxes with 1-Tap Calendar Integration - Phone 2 style)
            if (actionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "ACTION CHECKLIST",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentMintBright,
                        letterSpacing = 0.9.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x3386EFAC),
                        border = BorderStroke(0.8.dp, Color(0x6686EFAC))
                    ) {
                        Text(
                            text = "${completedActions.size}/${actionItems.size}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentMintBright,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                actionItems.forEach { item ->
                    val isChecked = completedActions.contains(item)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isChecked) Color(0x660B1A12) else GlassSurfaceElevated,
                        border = if (isChecked) BorderStroke(1.dp, StudioBorderSubtle) else BorderStroke(1.dp, GlassBorderBrush),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onToggleActionItem(item) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                fontSize = 13.5.sp,
                                color = if (isChecked) TextMuted else TextPrimary,
                                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                lineHeight = 19.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0x2B38BDF8),
                                border = BorderStroke(0.8.dp, Color(0x5938BDF8)),
                                modifier = Modifier.clickable { taskToSchedule = item }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.CalendarMonth,
                                        contentDescription = "Remind",
                                        tint = AccentSky,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Remind",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AccentSky
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            // Modern Glass Checkbox (bouncy spring checkmark)
                            ModernGlassCheckbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleActionItem(item) },
                                size = 22.dp
                            )
                        }
                    }
                }
            }

            // Collapsible Full Transcript / OCR text
            if (note.transcript.isNotBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GlassSurfaceElevated,
                    border = BorderStroke(1.dp, GlassBorderBrush),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTranscriptExpanded = !isTranscriptExpanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTranscriptExpanded) "Hide Full Transcript & OCR" else "View Full Transcript & OCR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentViolet
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                if (isTranscriptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = AccentViolet
                            )
                        }

                        AnimatedVisibility(visible = isTranscriptExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = note.transcript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 18.sp,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Photo Source Picker Dialog (Camera vs Gallery)
    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            containerColor = StudioSurfaceElevated,
            title = {
                Text(
                    text = "Add Photo & Read into Note",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Snap a photo of a whiteboard, receipt, document, or slide. Gemini will read and weave it into your note.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Camera option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                val photoFile = ImageUtils.createTempImageFile(context)
                                cameraFile = photoFile
                                val uri = ImageUtils.getImageUri(context, photoFile)
                                launchCameraSafe(uri)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Take Photo with Camera", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gallery option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                pickGalleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, tint = AccentSky, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Choose from Gallery", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Schedule Action Item Dialog (1-Tap Calendar & Alarm)
    taskToSchedule?.let { task ->
        ScheduleTaskDialog(
            taskItem = task,
            parentNote = note,
            onDismiss = { taskToSchedule = null }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = StudioSurfaceElevated,
            title = { Text("Delete Note?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this idea note?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDismiss()
                        onDelete()
                    }
                ) {
                    Text("Delete", color = AccentCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Full Screen Image Viewer / Reviewer Lightbox
    previewingImage?.let { imgPath ->
        FullScreenImageViewerDialog(
            imagePath = imgPath,
            onDismiss = { previewingImage = null },
            onDelete = { onDeleteImage?.invoke(imgPath) }
        )
    }
}

private fun shareNote(context: Context, note: NoteEntity) {
    val keyPoints = note.getKeyPoints()
    val actionItems = note.getActionItems()

    val keyPointsText = if (keyPoints.isNotEmpty()) {
        "\n\n✦ Key Insights:\n" + keyPoints.joinToString("\n") { "• $it" }
    } else ""

    val actionItemsText = if (actionItems.isNotEmpty()) {
        "\n\n☑ Next Steps:\n" + actionItems.joinToString("\n") { "[ ] $it" }
    } else ""

    val shareText = """
        💡 ${note.title}
        Tag: #${note.category}
        
        Summary:
        ${note.summary}$keyPointsText$actionItemsText
        
        Transcript:
        ${note.transcript}
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, note.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Idea"))
}
