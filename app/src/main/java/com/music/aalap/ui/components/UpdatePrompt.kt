package com.music.aalap.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.music.aalap.AppUpdate

/** "Aalap X is available · Update now / Later": shared by Profile's row and the app-open check. */
@Composable
fun UpdateDialog(release: AppUpdate.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.music.aalap.ui.theme.Surface,
        title = { Text("Aalap ${release.version} is available", color = com.music.aalap.ui.theme.TextPrimary) },
        text = {
            Text(
                "You have ${AppUpdate.current()}. Update now? Your library, likes and settings stay.\n\n" +
                    "If Google Play Protect asks to scan Aalap, tap \"Scan app\", then \"Install\".",
                color = com.music.aalap.ui.theme.TextMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                // From the app-open prompt the Profile row isn't on screen: say it has started.
                android.widget.Toast.makeText(context, "Downloading Aalap ${release.version}…", android.widget.Toast.LENGTH_SHORT).show()
                AppUpdate.update(context, release)
            }) { Text("Update now", color = com.music.aalap.ui.theme.Saffron, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later", color = com.music.aalap.ui.theme.TextMuted) }
        },
    )
}

/**
 * Opening Aalap asks GitHub whether a newer Aalap exists (once per app start, see
 * [AppUpdate.checkAtLaunch]) and, if so, offers it straight away. After "Later" it isn't
 * offered again on opening for a day; Profile, Home's chip and the notification still show it.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val offer by AppUpdate.launchOffer.collectAsState()
    LaunchedEffect(Unit) {
        if (com.music.aalap.data.preferences.isAalapOnboarded(context)) AppUpdate.checkAtLaunch(context)
    }
    offer?.let { r -> UpdateDialog(r) { AppUpdate.launchOffer.value = null } }
}

/**
 * "Aalap X is available · Update" on Home and in Library while a newer Aalap is out (the
 * remembered answer, no network). Tapping Update turns it into a yellow box that fills from
 * left to right as the download goes, then says it's checking and opens the installer.
 */
@androidx.compose.runtime.Composable
fun UpdateBanner(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val context = LocalContext.current
    val offered by AppUpdate.launchOffer.collectAsState()
    val installing by AppUpdate.install.collectAsState()
    val release = androidx.compose.runtime.remember(offered) { AppUpdate.knownRelease(context) } ?: return
    var ask by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (ask) UpdateDialog(release) { ask = false }
    val i = installing
    val busy = i is AppUpdate.Install.Downloading || i == AppUpdate.Install.Verifying
    val target = when (i) {
        is AppUpdate.Install.Downloading -> i.percent / 100f
        AppUpdate.Install.Verifying -> 1f
        else -> 0f
    }
    val fill by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(400), label = "updateFill")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val saffron = com.music.aalap.ui.theme.Saffron
    val on = com.music.aalap.ui.theme.OnSaffron
    androidx.compose.foundation.layout.Box(
        modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(shape)
            // Downloading: a dim yellow box that fills with bright yellow as it goes.
            .background(if (busy) saffron.copy(alpha = 0.28f) else saffron)
            .drawBehind { if (busy) drawRect(saffron, size = size.copy(width = size.width * fill)) }
            .clickable(enabled = !busy) { ask = true },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(androidx.compose.ui.Modifier.weight(1f)) {
                Text(
                    if (busy) "Updating to Aalap ${release.version}" else "Aalap ${release.version} is available",
                    color = on, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    when (i) {
                        is AppUpdate.Install.Downloading -> "Downloading… ${i.percent}%"
                        AppUpdate.Install.Verifying -> "Checking it's a genuine Aalap…"
                        is AppUpdate.Install.Failed -> i.why
                        else -> "New features and fixes. Tap to update."
                    },
                    color = on.copy(alpha = 0.8f), fontSize = 12.sp,
                )
            }
            Text(
                if (i is AppUpdate.Install.Downloading) "${i.percent}%" else if (busy) "…" else "Update",
                color = saffron, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = androidx.compose.ui.Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(on)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
