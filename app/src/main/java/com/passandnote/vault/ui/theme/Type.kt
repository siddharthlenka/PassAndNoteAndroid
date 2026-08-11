package com.passandnote.vault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val rounded = FontFamily.SansSerif

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = rounded, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = rounded, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = rounded, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = rounded, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = rounded, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontFamily = rounded, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)
