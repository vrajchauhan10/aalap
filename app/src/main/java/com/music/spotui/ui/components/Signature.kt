package com.music.spotui.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The maker's mark: a small, light, handwritten "crafted by V". */
@Composable
fun Signature(modifier: Modifier = Modifier) {
    Text(
        "crafted by V",
        color = com.music.spotui.ui.theme.TextMuted.copy(alpha = 0.7f),
        fontSize = 14.sp,
        fontFamily = FontFamily.Cursive,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Light,
        modifier = modifier,
    )
}
