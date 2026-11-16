package com.thoughtdrop.app.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thoughtdrop.app.ui.theme.AccentMintBright
import com.thoughtdrop.app.ui.theme.TextOnMint

@Composable
fun ModernGlassCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "checkboxScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(RoundedCornerShape(6.5.dp))
            .background(
                if (checked) AccentMintBright else Color(0x1F223129),
                RoundedCornerShape(6.5.dp)
            )
            .border(
                BorderStroke(
                    1.2.dp,
                    if (checked) AccentMintBright else Color(0x40C5E1A5)
                ),
                RoundedCornerShape(6.5.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed",
                tint = TextOnMint,
                modifier = Modifier.size(size * 0.65f)
            )
        }
    }
}
