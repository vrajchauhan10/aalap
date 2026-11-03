package com.music.aalap.ui.components

import androidx.compose.material3.AlertDialog
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
        text = { Text("You have ${AppUpdate.current()}. Update now? Your library, likes and settings stay.", color = com.music.aalap.ui.theme.TextMuted) },
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
