package com.thoughtdrop.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioColorScheme = darkColorScheme(
    primary = AccentAmberGlow,
    onPrimary = Color.White,
    secondary = AccentOrange,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = StudioBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder
)

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun ThoughtDropTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = StudioColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                try {
                    window.statusBarColor = StudioBg.toArgb()
                    window.navigationBarColor = StudioBg.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
                } catch (_: Exception) {
                    // Safe fallback for edge-to-edge
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
