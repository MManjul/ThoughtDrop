package com.thoughtdrop.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtdrop.app.audio.RecordingState
import com.thoughtdrop.app.ui.theme.*
import com.thoughtdrop.app.ui.viewmodel.UiProcessingState
import kotlinx.coroutines.delay

/**
 * Quillia Assistant / ThinkFlow full-bleed voice recording experience.
 * Matches the reference art:
 * - Ambient warm amber top-glow & deep espresso background
 * - Top back arrow + "Quillia Assistant"
 * - "What's on your mind? ✦" + "Need help with something? Ask me anything"
 * - Glowing 3D undulating ember mesh orb
 * - 2x2 suggestion capsules ("Organize My Notes", "Quick Voice Note", etc.)
 * - Bottom row: [✕] Glass cancel, Glowing Amber Mic Orb + monospace duration, [✓] Glass finish
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordBottomSheet(
    processingState: UiProcessingState,
    recordingState: RecordingState,
    onStopAndProcess: () -> Unit,
    onCancel: () -> Unit,
    onQuickPromptSelected: ((String) -> Unit)? = null
) {
    val isProcessing = processingState is UiProcessingState.Processing
    val duration = if (recordingState is RecordingState.Recording) {
        recordingState.durationSeconds
    } else 0
    val amplitude = if (recordingState is RecordingState.Recording) {
        recordingState.amplitude
    } else 0f

    val hours = duration / 3600
    val minutes = (duration % 3600) / 60
    val seconds = duration % 60
    val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    ModalBottomSheet(
        onDismissRequest = {
            if (!isProcessing) {
                onCancel()
            }
        },
        containerColor = Color(0xF207120C),
        scrimColor = Color.Black.copy(alpha = 0.82f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.2.dp, GlassBorderBrush),
                    RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x3386EFAC), // Ambient mint refraction
                            Color(0x000E2016),
                            Color(0x8007120C)
                        )
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 36.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Navigation Bar: Back Arrow `<` on left, "Quillia Assistant" centered
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // Back button
                    IconButton(
                        onClick = {
                            if (!isProcessing) onCancel()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(42.dp)
                            .background(GlassSurfaceHighlight, CircleShape)
                            .border(BorderStroke(1.dp, GlassBorderBrush), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Header Pill: Gemini 4 Hydrogen
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x2486EFAC),
                        border = BorderStroke(1.dp, Color(0x4D86EFAC))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(AccentMintBright, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GEMINI 4 HYDROGEN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentMintBright,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Greeting & Subtitle
                Text(
                    text = "What's on your mind? ✦",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.4).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isProcessing) "Structuring your thoughts with AI…" else "Need help with something? Ask me anything",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Hero Centerpiece: Glowing Undulating 3D Ember Mesh Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(230.dp)
                ) {
                    GlowingEmberOrb(
                        size = 220.dp,
                        amplitude = amplitude,
                        isThinking = isProcessing
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Processing View vs Recording View
                if (isProcessing) {
                    var elapsedSeconds by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1000)
                            elapsedSeconds++
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, StudioBorderSubtle)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccentAmberGlow
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Processing audio (${elapsedSeconds}s)…",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Extracting key insights, tags & to-do items",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    // 2x2 Quick Action Suggestion Capsules
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SuggestionCapsule(
                                text = "Organize My Notes",
                                modifier = Modifier.weight(1f),
                                onClick = { onQuickPromptSelected?.invoke("Organize My Notes") }
                            )
                            SuggestionCapsule(
                                text = "Quick Voice Note",
                                modifier = Modifier.weight(1f),
                                onClick = { onQuickPromptSelected?.invoke("Quick Voice Note") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SuggestionCapsule(
                                text = "Generate Ideas",
                                modifier = Modifier.weight(1f),
                                onClick = { onQuickPromptSelected?.invoke("Generate Ideas") }
                            )
                            SuggestionCapsule(
                                text = "Tell Me Something Fun",
                                modifier = Modifier.weight(1f),
                                onClick = { onQuickPromptSelected?.invoke("Tell Me Something Fun") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Bottom Control Bar
                    // [✕] Cancel button | Glowing Radiant Amber Mic Orb | [✓] Finish button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Circular glass button [✕]
                        Surface(
                            shape = CircleShape,
                            color = GlassSurfaceElevated,
                            border = BorderStroke(1.2.dp, GlassBorderBrush),
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { onCancel() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel Recording",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Center: Radiant Glowing Amber Mic Orb + Duration Timer Below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
                            val glowScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.18f + (amplitude * 0.25f),
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "glow"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(76.dp)
                            ) {
                                // Pulsing ambient mint glow halo
                                Box(
                                    modifier = Modifier
                                        .size(74.dp)
                                        .scale(glowScale)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0x6686EFAC),
                                                    Color(0x224ADE80),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )

                                // Radiant Luminous Mint Mic Circle (Leafora style)
                                Surface(
                                    shape = CircleShape,
                                    color = AccentMintBright,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .shadow(16.dp, CircleShape, spotColor = AccentMintGlow)
                                        .clickable { onStopAndProcess() }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Mic,
                                            contentDescription = "Mic Active",
                                            tint = TextOnMint,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Monospace Timer text: 00:00:27
                            Text(
                                text = timerText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Right: Circular glass button [✓] (Finish & Save)
                        Surface(
                            shape = CircleShape,
                            color = GlassSurfaceElevated,
                            border = BorderStroke(1.2.dp, GlassBorderBrush),
                            modifier = Modifier
                                .size(54.dp)
                                .clickable { onStopAndProcess() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Save Note",
                                    tint = AccentMintBright,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Capsule button for quick suggestions in Quillia Assistant
 */
@Composable
private fun SuggestionCapsule(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        color = GlassSurfaceElevated,
        border = BorderStroke(1.dp, GlassBorderBrush)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
