package com.thoughtdrop.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtdrop.app.audio.PlaybackState
import com.thoughtdrop.app.data.model.NoteEntity
import com.thoughtdrop.app.ui.theme.*
import com.thoughtdrop.app.ui.util.ModernGlassCheckbox
import java.io.File

/**
 * Living Optical Glass NoteCard
 * Exact visual match to the prototype screenshot:
 * - Frosted deep dark glass surface with top-lit specular rim
 * - Category capsule: #CATEGORY in green/mint
 * - ★ PINNED pill: Rose/Amber accent
 * - Timestamp in small monospace
 * - Modern vector Star/Pin and Delete trashcan
 * - Audio strip with amber circular play button, amber equalizer bars, and monospace duration
 * - Photo attachments row: [🖼 filename OCR] compact capsules
 * - Actions checklist with squircle checkboxes
 */
@Composable
fun NoteCard(
    note: NoteEntity,
    playbackState: PlaybackState,
    onCardClick: () -> Unit,
    onPlayAudio: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleActionItem: ((String) -> Unit)? = null,
    onScheduleClick: (() -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    accentColor: Color = Color(0xFFF59E0B)
) {
    val actionItems = remember(note.actionItemsJson) { note.getActionItems() }
    val completedActions = remember(note.completedActionsJson) { note.getCompletedActions() }
    val isPlaying = playbackState is PlaybackState.Playing && playbackState.noteId == note.id && !playbackState.isPaused

    val infiniteTransition = rememberInfiniteTransition(label = "notecard_wave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    val relativeTime = remember(note.createdAt) {
        val span = DateUtils.getRelativeTimeSpanString(
            note.createdAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        if (span.startsWith("in ") || span == "0 minutes ago") "Just now" else span
    }

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = Color(0x6610B981), // Living Emerald Caustic Bloom
                ambientColor = Color.Black
            )
            .clickable { onCardClick() },
        border = BorderStroke(1.2.dp, GlassBorderBrush)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x381E5437), // Luminous emerald glass tint at top
                            Color(0x24112E20), // Translucent deep emerald acrylic
                            Color(0x1F0B1C13)  // Translucent obsidian-emerald glass base
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
            // 1. Header: #CATEGORY + ★ PINNED + Timestamp + Pin & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // #CATEGORY capsule in emerald
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x2610B981),
                    border = BorderStroke(0.8.dp, Color(0x4D10B981))
                ) {
                    Text(
                        text = "#${note.category.uppercase()}",
                        color = Color(0xFF34D399),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                    )
                }

                if (note.isPinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x26F43F5E),
                        border = BorderStroke(0.8.dp, Color(0x4DF43F5E))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "PINNED",
                                color = Color(0xFFFB7185),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Timestamp
                Text(
                    text = relativeTime,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Star/Pin button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (note.isPinned) Color(0x33F43F5E) else Color(0x1AFFFFFF),
                            CircleShape
                        )
                        .clickable { onTogglePin() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (note.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Pin",
                        tint = if (note.isPinned) Color(0xFFFB7185) else Color(0xFFCCCCCC),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Delete trashcan button
                if (onDelete != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFFFFFF), CircleShape)
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Title: Bold crisp white text
            Text(
                text = note.title.ifBlank { "Untitled Note" },
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp
            )

            // 3. Subtitle / Summary
            if (note.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.summary,
                    fontSize = 12.5.sp,
                    color = Color(0xFFD1D5DB),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.5.sp
                )
            }

            // 4. Audio Player Strip (Matching Prototype: Warm Amber Theme)
            if (!note.audioPath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x660B120E),
                    border = BorderStroke(
                        0.9.dp,
                        if (isPlaying) accentColor.copy(alpha = 0.6f) else Color(0x1FFFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAudio() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause Circle in Amber
                        Surface(
                            shape = CircleShape,
                            color = accentColor,
                            modifier = Modifier
                                .size(30.dp)
                                .shadow(6.dp, CircleShape, spotColor = accentColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Waveform Equalizer Bars in Amber
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
                        ) {
                            val barWeights = listOf(
                                0.35f, 0.65f, 0.95f, 0.5f, 0.8f, 0.3f, 1f, 0.7f,
                                0.45f, 0.85f, 0.4f, 0.9f, 0.6f, 0.75f, 0.35f, 0.7f, 0.5f
                            )
                            barWeights.forEachIndexed { index, weight ->
                                val barHeight = if (isPlaying) {
                                    val stagger = ((index % 3) * 0.15f)
                                    (weight * (waveScale + stagger)).coerceIn(0.2f, 1f)
                                } else {
                                    weight
                                }

                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .fillMaxHeight(barHeight)
                                        .background(
                                            if (isPlaying) accentColor else accentColor.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(1.5.dp)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Duration text
                        Text(
                            text = if (isPlaying) "Playing…" else "${note.durationSeconds}s",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }

            // 5. Photos Row: [🖼 whiteboard_arch.j... OCR] compact capsules
            val images = remember(note.imageListJson, note.imagePath) { note.getImagePaths() }
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    images.forEach { imgPath ->
                        val fileName = File(imgPath).name
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x24FFFFFF),
                            border = BorderStroke(0.8.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.clickable { onImageClick?.invoke(imgPath) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = fileName.take(15) + (if (fileName.length > 15) "…" else ""),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE5E7EB)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0x3310B981)
                                ) {
                                    Text(
                                        text = "OCR",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34D399),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Action Checklist (ACTIONS ... DONE)
            if (actionItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(11.dp))
                val completedCount = completedActions.size
                val totalCount = actionItems.size
                val isAllDone = completedCount == totalCount

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x26000000), RoundedCornerShape(14.dp))
                        .border(BorderStroke(0.8.dp, Color(0x14FFFFFF)), RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIONS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isAllDone) "ALL DONE ✓" else "$completedCount/$totalCount DONE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllDone) Color(0xFF34D399) else accentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    actionItems.take(2).forEach { item ->
                        val isChecked = completedActions.contains(item)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ModernGlassCheckbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleActionItem?.invoke(item) },
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item,
                                fontSize = 12.sp,
                                color = if (isChecked) TextMuted else TextPrimary,
                                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleActionItem?.invoke(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
