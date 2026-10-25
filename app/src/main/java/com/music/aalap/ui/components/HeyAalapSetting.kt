package com.music.aalap.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
            // Outside Aalap the voice card shows over other apps, which Android asks you to allow.
            var overlayOk by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
            val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
            androidx.compose.runtime.DisposableEffect(lifecycle) {
                val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) overlayOk = android.provider.Settings.canDrawOverlays(context) }
                lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
            }
            if (on && !overlayOk) Text(
                "Allow \u201CDisplay over other apps\u201D so the voice card also comes up when Aalap isn't open",
                color = com.music.aalap.ui.theme.OnSaffron, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(com.music.aalap.ui.theme.Saffron)
                    .clickable {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
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

/**
 * Profile → Voice: hold a volume key to ask Aalap (an accessibility shortcut the user switches
 * on in Android's settings), and how to put "Ask Aalap" on the side key.
 */
@Composable
fun AskKeysSetting() {
    val context = LocalContext.current
    var keysOn by remember { mutableStateOf(com.music.aalap.di.AalapKeyService.isOn(context)) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) keysOn = com.music.aalap.di.AalapKeyService.isOn(context) }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Hold a volume key to ask", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Hold volume up or down for a moment, over any app, and the bar comes up listening. A quick press still changes the volume.",
            color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp,
        )
        Text(
            if (keysOn) "On · change in Accessibility" else "Turn on in Accessibility → Aalap",
            color = if (keysOn) com.music.aalap.ui.theme.Saffron else com.music.aalap.ui.theme.OnSaffron, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(if (keysOn) com.music.aalap.ui.theme.SurfaceHigh else com.music.aalap.ui.theme.Saffron)
                .clickable {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    // The optional icon: one tap from the app drawer, or the side key's double press.
    var iconOn by remember { mutableStateOf(com.music.aalap.di.AskAalapIcon.isOn(context)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = iconOn, role = androidx.compose.ui.semantics.Role.Switch) { want ->
            com.music.aalap.di.AskAalapIcon.set(context, want); iconOn = want
        }.padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("\u201CAsk Aalap\u201D icon", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Adds an Ask Aalap icon to your apps: tap it and the bar comes up listening, over whatever is on screen. " +
                    "Put it on the side key: Settings \u2192 Advanced features \u2192 Side button \u2192 Double press \u2192 Open app \u2192 Ask Aalap.",
                color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp,
            )
        }
        Switch(
            checked = iconOn, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = com.music.aalap.ui.theme.OnSaffron, checkedTrackColor = com.music.aalap.ui.theme.AppPalette,
                uncheckedThumbColor = com.music.aalap.ui.theme.TextMuted, uncheckedTrackColor = com.music.aalap.ui.theme.SurfaceHigh,
            ),
        )
    }
}
