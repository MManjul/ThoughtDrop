package com.thoughtdrop.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thoughtdrop.app.ui.theme.*

/**
 * Living Optical Glass Privacy Shield
 * Displayed when device lock is enabled and the app is locked:
 * - Frosted acrylic shield with specular rim
 * - Pulsing lock icon / fingerprint badge
 * - "✦ Unlock with Biometric / PIN" hero action
 */
@Composable
fun AppLockPrivacyScreen(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentAmber = AccentAmber
    val accentGlow = Color(0x66F59E0B)

    val infiniteTransition = rememberInfiniteTransition(label = "lockPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A08)),
        contentAlignment = Alignment.Center
    ) {
        // Living Ambient Caustics in background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x38F59E0B), Color(0x14FBBF24), Color.Transparent),
                    center = Offset(size.width * 0.50f, size.height * 0.35f),
                    radius = size.width * 0.70f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2810B981), Color.Transparent),
                    center = Offset(size.width * 0.50f, size.height * 0.70f),
                    radius = size.width * 0.60f
                )
            )
        }

        // Frosted Glass Privacy Shield Container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .shadow(32.dp, RoundedCornerShape(32.dp), spotColor = Color(0x40F59E0B)),
            border = BorderStroke(1.2.dp, GlassBorderBrush)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x26FFFFFF),
                                Color(0x1F18231C),
                                Color(0x280D140E)
                            )
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Pulsing Lock Icon Circle
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(24.dp, CircleShape, spotColor = accentAmber)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x38F59E0B),
                                        Color(0x1AF59E0B),
                                        Color(0x0A000000)
                                    )
                                )
                            )
                            .border(BorderStroke(1.2.dp, GlassBorderBrush), CircleShape)
                            .clickable { onUnlockClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fingerprint,
                            contentDescription = "Device Lock",
                            tint = accentAmber,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "✦ THOUGHTDROP LOCKED",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentAmber,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Notes are Protected",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = (-0.4).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Confirm your fingerprint, PIN, or pattern to view, browse, and edit your notes.",
                        fontSize = 12.5.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // "✦ Unlock with Biometric / PIN" Hero Button
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = accentAmber,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = accentGlow)
                            .clickable { onUnlockClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unlock ThoughtDrop",
                                color = Color.Black,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}
