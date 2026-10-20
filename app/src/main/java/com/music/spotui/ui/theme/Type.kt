package com.music.spotui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.music.spotui.R

/**
 * Aalap's typeface: Poppins (Indian Type Foundry, SIL Open Font License).
 * One geometric family for body and headings; headings use the heavier cuts.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, weight = FontWeight.Normal),
    Font(R.font.poppins_medium, weight = FontWeight.Medium),
    Font(R.font.poppins_semibold, weight = FontWeight.SemiBold),
    Font(R.font.poppins_bold, weight = FontWeight.Bold),
    Font(R.font.poppins_extrabold, weight = FontWeight.ExtraBold),
    Font(R.font.poppins_extrabold, weight = FontWeight.Black),
)

// Kept as an alias so existing references (Theme.kt etc.) don't break.
val Montserrat = Poppins

private val base = TextStyle(fontFamily = Poppins)
private val title = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold)

val Typography = Typography(
    displayLarge = title, displayMedium = title, displaySmall = title,
    headlineLarge = title, headlineMedium = title, headlineSmall = title,
    titleLarge = base.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base, bodySmall = base,
    labelLarge = base, labelMedium = base, labelSmall = base,
)
