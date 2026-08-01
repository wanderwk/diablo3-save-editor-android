package com.wanderwk.d3saveeditor.ui.theme

import androidx.compose.ui.graphics.Color

// Global design tokens (see design_handoff_d3_save_editor/README.md)
val BgBase = Color(0xFF14100D)
val SurfaceContainer = Color(0xFF1A1310)
val SurfaceContainerHigh1 = Color(0xFF1E1712)
val SurfaceContainerHigh2 = Color(0xFF1C1310)
val SurfaceContainerHigh3 = Color(0xFF1C1512)
val PrimaryContainer = Color(0xFF4A2E3A)
val PrimaryAccent = Color(0xFFF5C563) // gold
val SecondaryGold = Color(0xFFE8C468)
val BrandRed = Color(0xFF7A2432)
val TextPrimary = Color(0xFFE9DFC9)
val TextMuted = Color(0xFF9C8B70)
val TextFaint = Color(0xFF7A6A55)
val TextFaint2 = Color(0xFF5F5548)
val DividerOutline = Color(0xFF3A2A1C)
val SuccessGreen = Color(0xFF3FAE4A)
val ErrorOrange = Color(0xFFE8956B)

val ActiveCardTonal = Color(0xFF3A2530)
val ChipDark1 = Color(0xFF241A1E)
val ChipDark2 = Color(0xFF2E2417)

// Bottom nav per-tab accents
val NavHome = Color(0xFFE8956B)
val NavCoins = Color(0xFFC9A227)
val NavItems = Color(0xFF5FAE6A)
val NavGems = Color(0xFFA06BD9)
val NavParagon = Color(0xFFF5C563)
val NavExport = Color(0xFF5B9BD5)
val NavSupport = Color(0xFFE05C7A)
val NavInactive = Color(0xFF5F5548)

// Rarity colors
val RarityLegendary = Color(0xFFFF8000)
val RaritySet = Color(0xFF3FAE4A)
val RarityRare = Color(0xFFF4D03F)
val RarityMagic = Color(0xFF4A90D9)
val RarityCommon = Color(0xFFC8C8C8)
val RarityAncient = Color(0xFFB07CE0)
val RarityPrimal = Color(0xFF5FD0D0)

// Gem colors
val GemRuby = Color(0xFFC0392B)
val GemEmerald = Color(0xFF27AE60)
val GemTopaz = Color(0xFFE8C468)
val GemAmethyst = Color(0xFF8E44AD)
val GemDiamond = Color(0xFFDFE6E9)

fun rarityColor(rarity: String): Color = when (rarity.lowercase()) {
    "legendary" -> RarityLegendary
    "set" -> RaritySet
    "rare" -> RarityRare
    "magic" -> RarityMagic
    "ancient" -> RarityAncient
    "primal" -> RarityPrimal
    else -> RarityCommon
}

fun gemColor(type: String): Color = when (type.lowercase()) {
    "ruby", "rubi" -> GemRuby
    "emerald", "esmeralda" -> GemEmerald
    "topaz", "topázio", "topazio" -> GemTopaz
    "amethyst", "ametista" -> GemAmethyst
    "diamond", "diamante" -> GemDiamond
    else -> RarityCommon
}
