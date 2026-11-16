package com.thoughtdrop.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =========================================================================
// Leafora Balanced Obsidian & Luminous Mint Palette
// Rich deep obsidian canvas, frosted charcoal-sage acrylic surfaces,
// balanced multi-color category highlights, and radiant mint hero accents.
// =========================================================================

// Deep rich obsidian charcoal canvas (Dark & sleek, eliminating muddy green wash)
val StudioBg = Color(0xFF090D0B)                 // Pure deep obsidian
val StudioBgTopGlow = Color(0xFF101713)          // Subtle ambient canopy glow
val StudioBgMidGlow = Color(0xFF0C120E)          // Subtle ambient mid glow

// Frosted Glass Surfaces (Semi-transparent smoky charcoal-obsidian acrylic)
val GlassSurface = Color(0xD9101613)             // 85% opacity smoky obsidian glass
val GlassSurfaceElevated = Color(0xE6141D18)     // 90% opacity elevated glass
val GlassSurfaceHighlight = Color(0xF218241E)    // Active / pressed glass
val GlassSurfacePill = Color(0xB3111914)         // Capsule pill glass
val GlassSurfaceSubtle = Color(0x800C120F)       // Ultra-sheer glass

// Refractive Mint Glass Borders (Top-lit specular highlights)
val GlassBorderTop = Color(0x38C5E1A5)           // Luminous mint rim highlight
val GlassBorderMid = Color(0x14FFFFFF)           // Neutral glass rim
val GlassBorderBottom = Color(0x08FFFFFF)        // Sheer rim shadow

val GlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xCCFFFFFF), // 80% luminous white specular rim
        Color(0x38FFFFFF), // 22% white
        Color(0x10FFFFFF)  // 6% white bottom rim
    )
)

val SpecularGlassBorderBrush = GlassBorderBrush

val LivingGlassSurface = Color(0x18FFFFFF)        // 10% translucent white living glass
val LivingGlassEmeraldGlow = Color(0x4D10B981)    // 30% vibrant emerald bloom


val GlassBorderMint = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFC5E1A5),
        Color(0x804ADE80),
        Color(0x2222C55E)
    )
)

// Standard surface aliases
val StudioSurface = GlassSurface
val StudioSurfaceElevated = GlassSurfaceElevated
val StudioSurfaceHighlight = GlassSurfaceHighlight
val StudioBorder = Color(0x2486EFAC)
val StudioBorderSubtle = Color(0x1486EFAC)
val StudioBorderFocus = Color(0xFF86EFAC)

// Radiant Luminous Mint & Emerald Accents (Reference Leafora buttons & badges)
val AccentMint = Color(0xFF86EFAC)               // Soft luminous mint
val AccentMintBright = Color(0xFFC5E1A5)         // Radiant pale lime-mint
val AccentMintGlow = Color(0x6686EFAC)           // Mint ambient drop-shadow glow
val AccentEmerald = Color(0xFF22C55E)            // Vivid emerald
val AccentAmber = Color(0xFFF59E0B)              // Warm amber indicator
val AccentGold = Color(0xFFFBBF24)               // Warm gold star
val AccentOrange = Color(0xFFFB923C)             // Sunset orange
val AccentCoral = Color(0xFFF87171)              // Recording crimson
val AccentCoralGlow = Color(0x4DF87171)          // Soft recording ambient glow
val AccentViolet = Color(0xFF86EFAC)             // Brand accent mapped to Mint
val AccentSky = Color(0xFF38BDF8)                // Sky cyan for reminders

// Signature Radiant Luminous Mint Button Gradient (Leafora hero button)
val AmberGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFC5E1A5),
        Color(0xFFA3E635),
        Color(0xFF86EFAC)
    )
)

val AmberGradientVertical = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFC5E1A5),
        Color(0xFF86EFAC)
    )
)

val MintGradient = AmberGradient
val MintGradientVertical = AmberGradientVertical
val GlassBorderAmber = GlassBorderMint

// High-Contrast Leafora Typography
val TextPrimary = Color(0xFFF4FAF6)              // Crisp pure mint-white
val TextSecondary = Color(0xFFA3C9B0)            // Soft luminous sage
val TextMuted = Color(0xFF6B8F77)                // Deep forest sage
val TextDisabled = Color(0xFF3E5747)             // Disabled micro-caption

// Button Content (Dark text on radiant mint pill)
val TextOnMint = Color(0xFF06180D)

// Backwards-compatible legacy aliases for QuickRecordActivity and other views
val ObsidianBg = StudioBg
val ObsidianSurface = StudioSurface
val ObsidianSurfaceElevated = StudioSurfaceElevated
val ObsidianSurfaceHighlight = StudioSurfaceHighlight
val ObsidianBorder = StudioBorder
val ObsidianBorderSubtle = StudioBorderSubtle
val AccentAmberGlow = AccentMintGlow
val AccentGoldLight = AccentMintBright
val AccentAmberLight = AccentMintBright
val NebulaViolet = AccentMint
val NebulaVioletLight = AccentMintBright
val NebulaCyan = AccentSky
val NebulaCyanLight = AccentSky
val NebulaPink = Color(0xFFF472B6)
val NebulaPinkLight = Color(0xFFF472B6)
val NebulaAmber = AccentMint
val NebulaAmberLight = AccentMintBright
val NebulaRecordRose = AccentCoral
val NebulaEmerald = AccentEmerald
val NebulaEmeraldLight = AccentMintBright

data class CategoryStyle(
    val primary: Color,
    val bg: Color,
    val iconSymbol: String = ""
)

fun getCategoryStyle(category: String): CategoryStyle {
    val lower = category.lowercase().trim()
    return when {
        lower.contains("work") || lower.contains("meet") || lower.contains("client") -> CategoryStyle(
            primary = Color(0xFFA78BFA), // Elegant violet
            bg = Color(0x26A78BFA),
            iconSymbol = "work"
        )
        lower.contains("idea") || lower.contains("brainstorm") -> CategoryStyle(
            primary = Color(0xFFFBBF24), // Warm radiant gold
            bg = Color(0x26FBBF24),
            iconSymbol = "idea"
        )
        lower.contains("task") || lower.contains("todo") || lower.contains("check") -> CategoryStyle(
            primary = Color(0xFF34D399), // Fresh emerald mint
            bg = Color(0x2634D399),
            iconSymbol = "task"
        )
        lower.contains("personal") || lower.contains("life") || lower.contains("home") -> CategoryStyle(
            primary = Color(0xFFF472B6), // Soft rose
            bg = Color(0x26F472B6),
            iconSymbol = "personal"
        )
        lower.contains("urgent") || lower.contains("important") || lower.contains("alert") -> CategoryStyle(
            primary = Color(0xFFF87171), // Coral alert
            bg = Color(0x26F87171),
            iconSymbol = "urgent"
        )
        else -> CategoryStyle(
            primary = AccentMintBright,
            bg = Color(0x26C5E1A5),
            iconSymbol = "general"
        )
    }
}
