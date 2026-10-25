package com.music.aalap.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.music.aalap.di.HeyAalap

/** Profile → Voice: the "Hey Aalap" switch (off by default; asks for the mic once). */
@Composable
fun HeyAalapSetting() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(HeyAalap.isEnabled(context)) }
    var note by remember { mutableStateOf("") }
    val supported = remember { HeyAalap.supported(context) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { on = true; HeyAalap.setEnabled(context, true); note = "" }
        else note = "Aalap needs the microphone to hear “Hey Aalap”."
    }
    val toggle: (Boolean) -> Unit = { want ->
        when {
            !want -> { on = false; HeyAalap.setEnabled(context, false) }
            !supported -> note = "This phone has no offline speech engine, which Hey Aalap needs (so nothing you say is sent online)."
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                mic.launch(android.Manifest.permission.RECORD_AUDIO)
            else -> { on = true; HeyAalap.setEnabled(context, true) }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = on, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = toggle).padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("“Hey Aalap”", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Say “Hey Aalap” or “OK Aalap”, then a song, singer or mood. Listens while Aalap is open or playing; " +
                    "offline, nothing is sent anywhere, and it steps aside for calls and voice notes.",
                color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp,
            )
            if (note.isNotBlank()) Text(note, color = com.music.aalap.ui.theme.Saffron, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(
            checked = on, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = com.music.aalap.ui.theme.OnSaffron, checkedTrackColor = com.music.aalap.ui.theme.AppPalette,
                uncheckedThumbColor = com.music.aalap.ui.theme.TextMuted, uncheckedTrackColor = com.music.aalap.ui.theme.SurfaceHigh,
            ),
        )
    }
}
