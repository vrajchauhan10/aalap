package com.music.aalap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Settings → Share: opens Blend (share your taste code, blend with a friend's, your saved Blends). */
@Composable
fun BlendSettings() {
    Column(Modifier.fillMaxWidth().clickable { BlendUi.open() }.padding(vertical = 10.dp)) {
        Text("Blend with a friend", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Swap taste codes: a shared playlist, a match score, fresh every day", color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp)
    }
}
