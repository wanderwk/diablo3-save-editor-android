package com.wanderwk.d3saveeditor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wanderwk.d3saveeditor.R

// Cinzel: display/brand headings (screen titles, save name, paragon big number,
// license-gate title). Roboto is the Android system default, used for body/UI.
val CinzelFamily = FontFamily(
    Font(R.font.cinzel, weight = FontWeight.Normal),
    Font(R.font.cinzel, weight = FontWeight.Medium),
    Font(R.font.cinzel, weight = FontWeight.SemiBold),
    Font(R.font.cinzel, weight = FontWeight.Bold),
)
val RobotoFamily = FontFamily.Default

val ParagonNumberStyle = TextStyle(
    fontFamily = CinzelFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 42.sp,
)
val CardHeadingStyle = TextStyle(
    fontFamily = CinzelFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
)

val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = CinzelFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = CinzelFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    ),
)
