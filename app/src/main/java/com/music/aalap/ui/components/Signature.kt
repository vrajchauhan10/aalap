package com.music.aalap.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.music.aalap.R

private val GreatVibes = FontFamily(Font(R.font.great_vibes))
private val Parisienne = FontFamily(Font(R.font.parisienne))

/**
 * The maker's mark, small and light: "crafted by" in Great Vibes and the "V" in
 * Parisienne. Placed in a bottom corner, never centred.
 */
@Composable
fun Signature(modifier: Modifier = Modifier, size: Float = 17f) {
    val ink = com.music.aalap.ui.theme.TextMuted.copy(alpha = 0.6f)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = GreatVibes, fontSize = size.sp)) { append("crafted by ") }
            withStyle(SpanStyle(fontFamily = Parisienne, fontSize = (size + 1f).sp)) { append("V") }
        },
        color = ink,
        modifier = modifier,
    )
}
