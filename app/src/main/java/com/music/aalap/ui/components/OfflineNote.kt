package com.music.aalap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A small "no internet" strip that says what you're seeing instead (your downloads). */
@Composable
fun OfflineNote(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(com.music.aalap.ui.theme.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFFB547)))
        Spacer(Modifier.width(10.dp))
        Text(text, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp)
    }
}
