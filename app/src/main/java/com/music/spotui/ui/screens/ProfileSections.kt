package com.music.spotui.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.local.AppleMusicImport
import com.music.spotui.data.preferences.isGaanaEnabled
import com.music.spotui.data.preferences.isJioSaavnEnabled
import com.music.spotui.data.preferences.isYoutubeLoggedIn
import com.music.spotui.data.preferences.setGaanaEnabled
import com.music.spotui.data.preferences.setJioSaavnEnabled
import com.music.spotui.data.recommendation.engine.Personalizer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile → Connected: every service Aalap uses, in one list. Accounts (Spotify,
 * YouTube Music) show who is signed in; JioSaavn and Gaana need no login and can be
 * switched off; Apple Music is imported from an export file.
 */
@Composable
fun ConnectedServicesSection(navController: NavController) {
    val context = LocalContext.current
    // Re-read after coming back from a connect screen.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    val spotifyOk = remember(tick) { SpotifySession.spDc(context).isNotBlank() }
    val spotifyName = com.music.spotui.data.api.ProfileCache.name
    val youtubeOk = remember(tick) { isYoutubeLoggedIn(context) }
    var saavnOn by remember { mutableStateOf(isJioSaavnEnabled(context)) }
    var gaanaOn by remember { mutableStateOf(isGaanaEnabled(context)) }
    var appleCount by remember { mutableIntStateOf(AppleMusicImport.tracks(context).size) }
    var appleGuide by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    // The unified library: counts from both services together, each song/artist once.
    val spotifyLiked = remember(tick) {
        context.getSharedPreferences("LikedSongsFromSpotify", android.content.Context.MODE_PRIVATE).getStringSet("ids", null)?.size ?: 0
    }
    val ytLiked = remember(tick, syncing) { com.music.spotui.data.api.YouTubeLibrary.likedSongs(context).size }
    val ytArtists = remember(tick, syncing) { com.music.spotui.data.api.YouTubeLibrary.artists(context).size }

    ProfileSectionTitle("Connected")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp)),
    ) {
        ServiceLine(
            logo = com.music.spotui.R.drawable.ic_brand_spotify,
            name = "Spotify",
            status = if (spotifyOk) "Connected" + (spotifyName?.let { " as $it" } ?: "") + " · $spotifyLiked liked songs" else "Not connected",
            action = if (spotifyOk) "Reconnect" else "Connect",
        ) { navController.navigate(Routes.SpotifyConnect.route) }
        Divider()
        ServiceLine(
            logo = com.music.spotui.R.drawable.ic_brand_ytmusic,
            name = "YouTube Music",
            status = when {
                !youtubeOk -> "Not connected · sign in to bring your likes and artists"
                syncing -> "Syncing your library…"
                else -> "Connected · $ytLiked liked songs, $ytArtists artists" +
                    com.music.spotui.data.api.YouTubeLibrary.lastSync(context).let { at ->
                        if (at > 0) " · synced " + android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), 60_000L) else ""
                    }
            },
            action = when { !youtubeOk -> "Connect"; syncing -> null; else -> "Sync now" },
        ) {
            if (!youtubeOk) navController.navigate(Routes.YouTubeLogin.route)
            else if (!syncing) {
                syncing = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { com.music.spotui.data.api.YouTubeLibrary.sync(context) }.getOrDefault(false) }
                    syncing = false
                    Toast.makeText(context, if (ok) "YouTube Music library synced" else "Couldn't sync right now", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Divider()
        ServiceLine(
            logo = com.music.spotui.R.drawable.ic_brand_jiosaavn,
            name = "JioSaavn",
            status = "Backup audio in 320 kbps · no login needed",
            switch = saavnOn,
        ) { saavnOn = !saavnOn; setJioSaavnEnabled(context, saavnOn) }
        Divider()
        ServiceLine(
            logo = com.music.spotui.R.drawable.ic_brand_gaana,
            name = "Gaana",
            status = "Last backup source · no login needed",
            switch = gaanaOn,
        ) { gaanaOn = !gaanaOn; setGaanaEnabled(context, gaanaOn) }
        Divider()
        ServiceLine(
            logo = com.music.spotui.R.drawable.ic_brand_apple_music,
            name = "Apple Music",
            status = if (appleCount > 0) "$appleCount songs imported · shaping your taste" else "Import your library to shape your taste",
            action = if (appleCount > 0) "Update" else "Import",
        ) { appleGuide = true }
    }

    if (appleGuide) AppleMusicImportDialog(onDismiss = { appleGuide = false }) { appleCount = it }
}

@Composable
private fun Divider() = HorizontalDivider(color = Hairline, thickness = 1.dp, modifier = Modifier.padding(start = 66.dp))

@Composable
internal fun ServiceLine(
    logo: Int,
    name: String,
    status: String,
    action: String? = null,
    switch: Boolean? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(logo),
            contentDescription = name,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(status, color = TextMuted, fontSize = 12.sp, lineHeight = 15.sp)
        }
        when {
            switch != null -> Switch(
                checked = switch, onCheckedChange = { onClick() },
                colors = SwitchDefaults.colors(checkedThumbColor = OnSaffron, checkedTrackColor = Saffron, uncheckedTrackColor = SurfaceHigh),
            )
            action != null -> Text(
                action, color = Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Two ways to export from Apple Music, then pick the file. */
@Composable
internal fun AppleMusicImportDialog(onDismiss: () -> Unit, onImported: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { AppleMusicImport.import(context, uri) }.getOrDefault(0) }
            busy = false
            if (n > 0) {
                onImported(n)
                Toast.makeText(context, "Imported $n songs. Your taste is updated.", Toast.LENGTH_LONG).show()
                onDismiss()
            } else {
                Toast.makeText(context, "No songs found in that file. Pick the tracks .json, .csv or Library.xml file.", Toast.LENGTH_LONG).show()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Import from Apple Music", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideStep("Option 1 · Any phone", "Open privacy.apple.com → Request a copy of your data → Apple Media Services. When the email arrives (a few days), unzip it and pick \"Apple Music Library Tracks.json\".")
                GuideStep("Option 2 · Mac or PC (quicker)", "In the Music app (or iTunes): File → Library → Export Library… Send the Library.xml file to your phone and pick it.")
                Text("Aalap reads the song and artist names only, on your phone.", color = TextMuted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/json", "text/*", "application/xml", "text/xml", "*/*")) }) {
                Text(if (busy) "Importing…" else "Choose file", color = Saffron, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = TextPrimary) } },
    )
}

@Composable
private fun GuideStep(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceHigh).padding(12.dp),
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = TextMuted, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/** Profile → Your taste: what Aalap has learned, updated automatically as you listen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YourTasteSection() {
    val context = LocalContext.current
    val summary by produceState<Personalizer.Summary?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                com.music.spotui.data.recommendation.engine.QueueEngine.ensureImported(context)
                Personalizer.summary(context)
            }.getOrNull()
        }
    }
    val s = summary ?: return
    if (s.plays == 0) return
    ProfileSectionTitle("Your taste")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            "Learned from what you play, finish, replay, skip, like and follow, and when you listen. It updates by itself.",
            color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp,
        )
        if (s.artists.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Artists you love", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.artists.forEach { Chip(it) }
            }
        }
        if (s.languages.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Languages", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.languages.forEach { Chip(it) } }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            // Plays from before 2.5 carry no skip data; show the finish rate once skips are being seen.
            if (s.skipRate > 0) "${s.plays} plays · you finish ${(100 - s.skipRate * 100).toInt()}% of songs" else "${s.plays} plays",
            color = TextMuted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun ProfileSectionTitle(text: String) {
    // Same look as the other Profile section titles.
    Text(
        text, color = Saffron, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
