package com.thoughtdrop.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * 3D Woven Particle Mesh Orb
 * Exact 1:1 match to the prototype reference:
 * - Dense 3D spherical lattice of luminous champagne-gold and crystal-white particles
 * - Top-right specular highlight brilliance with soft luminous halos
 * - Two-pass depth rendering with inner obsidian spherical core shadow for dramatic volume
 * - Silk-smooth harmonic undulating waviness (1.10 rad/s)
 * - Scaled strictly in DP for crisp display across all device densities (Galaxy S23 Ultra xxxhdpi)
 */
@Composable
fun GlowingEmberOrb(
    modifier: Modifier = Modifier,
    size: Dp = 125.dp,
    amplitude: Float = 0f,
    isThinking: Boolean = false,
    primaryAccent: Color = Color(0xFFF59E0B),
    secondaryAccent: Color = Color(0xFFFBBF24)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emberOrbMotion")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 3200 else 5400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(
        modifier = modifier.size(size)
    ) {
        val w = size.toPx()
        val h = size.toPx()
        val center = Offset(w / 2f, h / 2f)

        // Sphere radius sized so ambient glow never clips
        val maxSafeRadius = (min(w, h) / 2f) * 0.90f
        val baseRadius = ((min(w, h) * 0.35f) * breatheScale * (1f + amplitude * 0.12f)).coerceAtMost(maxSafeRadius / 1.25f)
        val glowRadius = baseRadius * 1.35f

        // ----------------------------------------------------
        // 1. Soft atmospheric ambient glow behind orb
        // ----------------------------------------------------
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryAccent.copy(alpha = 0.42f),
                    secondaryAccent.copy(alpha = 0.18f),
                    Color(0x08F59E0B),
                    Color.Transparent
                ),
                center = center,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = center
        )

        // ----------------------------------------------------
        // 2. Prepare 3D undulating points
        // ----------------------------------------------------
        val numRings = 24
        val pointsPerRing = 40
        val waveFreq = 1.7f
        val waveSpeed = if (isThinking) 1.9f else 1.25f

        // Data class for pre-calculating particle projections
        class OrbPoint(
            val px: Float,
            val py: Float,
            val isFront: Boolean,
            val depthFactor: Float,
            val specularFactor: Float,
            val ringIndex: Int,
            val pointIndex: Int
        )

        val allPoints = ArrayList<OrbPoint>(numRings * pointsPerRing)

        for (i in 0 until numRings) {
            val v = (i.toFloat() / (numRings - 1)) * PI.toFloat() - (PI.toFloat() / 2f)
            val ringRadius = cos(v) * baseRadius
            val ringY = sin(v) * baseRadius

            for (j in 0 until pointsPerRing) {
                val u = (j.toFloat() / pointsPerRing) * 2f * PI.toFloat()

                // Harmonic ripple deformation
                val wave1 = sin(u * waveFreq + phase * waveSpeed + v * 1.5f)
                val wave2 = cos(u * 1.2f - phase * 0.65f + v * 2.2f)
                val displacement = (wave1 * 0.05f + wave2 * 0.03f) * (1f + amplitude * 0.35f)

                val effectiveR = (ringRadius * (1f + displacement)).coerceAtLeast(4f)
                val px = center.x + effectiveR * cos(u)
                // 0.32f perspective tilt angles the rings ~19° down, revealing the top pole
                val py = center.y + ringY + (effectiveR * sin(u) * 0.32f)

                // Depth factor: z axis pointing out of screen
                // u = PI/4 (front-right) is closest to viewer
                val depthFactor = (sin(u + PI.toFloat() / 4f) + 1f) / 2f
                val isFront = sin(u) >= -0.15f // points in front half

                // Specular factor for top-right highlight pop (matches reference image)
                val specU = cos(u - 0.45f).coerceAtLeast(0f)
                val specV = cos(v - 0.25f).coerceAtLeast(0f)
                val specularFactor = (specU * specV)

                allPoints.add(OrbPoint(px, py, isFront, depthFactor, specularFactor, i, j))
            }
        }

        // ----------------------------------------------------
        // 3. PASS 1: Render back particles (behind core shadow)
        // ----------------------------------------------------
        val backDotMin = 1.2.dp.toPx()
        val backDotMax = 2.0.dp.toPx()

        for (pt in allPoints) {
            if (!pt.isFront) {
                val dotSize = backDotMin + pt.depthFactor * (backDotMax - backDotMin)
                val alpha = (0.12f + pt.depthFactor * 0.22f).coerceIn(0.10f, 0.35f)
                drawCircle(
                    color = primaryAccent.copy(alpha = alpha),
                    radius = dotSize,
                    center = Offset(pt.px, pt.py)
                )
            }
        }

        // ----------------------------------------------------
        // 4. Inner Obsidian Spherical Core Shadow
        // Creates contrast so front particles pop with 3D volume
        // ----------------------------------------------------
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0C0702),
                    Color(0xE6140D04),
                    Color(0x801F1206),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius * 0.78f
            ),
            radius = baseRadius * 0.78f,
            center = center
        )

        // ----------------------------------------------------
        // 5. PASS 2: Render front particles and woven mesh filaments
        // ----------------------------------------------------
        val frontDotMin = 1.8.dp.toPx()
        val frontDotMax = 3.6.dp.toPx()
        val filamentWidth = 0.9.dp.toPx()

        for (k in allPoints.indices) {
            val pt = allPoints[k]
            if (pt.isFront) {
                // Determine particle color palette based on depth & specular highlight
                val dotColor = when {
                    pt.specularFactor > 0.60f -> Color(0xFFFFFDF5) // Brilliant crystal warm white
                    pt.depthFactor > 0.70f -> Color(0xFFFEF3C7)     // Luminous champagne gold
                    pt.depthFactor > 0.45f -> secondaryAccent       // Bright warm gold (0xFFFBBF24)
                    pt.depthFactor > 0.25f -> primaryAccent         // Radiant amber (0xFFF59E0B)
                    else -> Color(0xFFD97706)                       // Deep sunset ember
                }

                val alpha = (0.35f + pt.depthFactor * 0.65f).coerceIn(0.25f, 1f)
                val dotSize = (frontDotMin + pt.depthFactor * (frontDotMax - frontDotMin) + (if (isThinking) 0.4f else 0f)) * (1f + amplitude * 0.2f)

                // Soft specular glow for highlight particles
                if (pt.specularFactor > 0.65f) {
                    drawCircle(
                        color = Color(0x66FFFBEB),
                        radius = dotSize * 1.7f,
                        center = Offset(pt.px, pt.py)
                    )
                }

                // Core particle disc
                drawCircle(
                    color = dotColor.copy(alpha = alpha),
                    radius = dotSize,
                    center = Offset(pt.px, pt.py)
                )

                // Connecting woven mesh filaments along the ring
                if (pt.pointIndex % 2 == 0 && pt.depthFactor > 0.32f) {
                    // Find next point on same ring
                    val nextPtIndex = if (pt.pointIndex == pointsPerRing - 1) {
                        k - (pointsPerRing - 1)
                    } else {
                        k + 1
                    }
                    if (nextPtIndex < allPoints.size) {
                        val nextPt = allPoints[nextPtIndex]
                        if (nextPt.isFront) {
                            drawLine(
                                color = dotColor.copy(alpha = (alpha * 0.35f).coerceIn(0.08f, 0.45f)),
                                start = Offset(pt.px, pt.py),
                                end = Offset(nextPt.px, nextPt.py),
                                strokeWidth = filamentWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}
