package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.Response
import com.music.spotui.data.api.YouTubeLibrary
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.local.AalapPlaylists
import com.music.spotui.ui.screens.AalapSearchBar
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

/** Name a playlist (create or rename). */
@Composable
fun PlaylistNameDialog(title: String, initial: String = "", confirm: String = "Create", onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    // Cursor starts in the name field so you can type straight away.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("My playlist", color = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.focusRequester(focus),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Saffron, unfocusedBorderColor = Hairline, cursorColor = Saffron,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name.trim().ifBlank { "My playlist" }) }) { Text(confirm, color = Saffron, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextPrimary) } },
    )
}

/**
 * Add songs to an Aalap playlist: search anything, or pick from your liked songs.
 * Each row toggles the song in/out of the playlist right away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun AddSongsSheet(playlistId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val version by AalapPlaylists.version.collectAsState()
    val inPlaylist = remember(version) { AalapPlaylists.get(context, playlistId)?.songs?.mapTo(HashSet()) { it.id }.orEmpty() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongsModel>>(emptyList()) }
    var liked by remember { mutableStateOf<List<SongsModel>>(emptyList()) }

    LaunchedEffect(Unit) { liked = withContext(Dispatchers.IO) { YouTubeLibrary.unifiedLiked(context) } }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = withContext(Dispatchers.IO) {
            runCatching {
                Api(context).searchTracks(query).toList().filterIsInstance<Response.Success<List<SongsModel>>>().lastOrNull()?.data.orEmpty()
            }.getOrDefault(emptyList())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Surface) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Text("Add songs", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            AalapSearchBar(query, onFocusChange = {}, placeholder = "Search songs") { query = it }
            Text(
                if (query.isBlank()) "From your liked songs" else "Results",
                color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(if (query.isBlank()) liked else results, key = { it.id }) { s ->
                    val added = s.id in inPlaylist
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { AalapPlaylists.toggle(context, playlistId, s) }.padding(horizontal = 20.dp, vertical = 7.dp),
                    ) {
                        GlideImage(
                            model = s.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.placeholder),
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.singer, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (added) Saffron else SurfaceHigh),
                        ) {
                            Icon(if (added) Icons.Default.Check else Icons.Default.Add, contentDescription = if (added) "Added" else "Add",
                                tint = if (added) OnSaffron else TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

/** Add songs / Rename / Delete, under an Aalap playlist's header. */
@Composable
fun AalapPlaylistActions(onAdd: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill("+  Add songs", filled = true, onClick = onAdd)
        Spacer(Modifier.width(8.dp))
        Pill("Rename", onClick = onRename)
        Spacer(Modifier.width(8.dp))
        Pill("Delete", onClick = onDelete)
    }
}

@Composable
private fun Pill(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (filled) OnSaffron else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (filled) Saffron else SurfaceHigh)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/**
 * Removing a playlist. An Aalap playlist is deleted. A Spotify playlist is only hidden
 * in Aalap: it stays on Spotify, and syncing won't bring it back here.
 */
@Composable
fun RemovePlaylistDialog(playlistId: String, name: String, onDismiss: () -> Unit, onRemoved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val local = com.music.spotui.data.local.AalapPlaylists.isLocal(playlistId)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.music.spotui.ui.theme.Surface,
        title = { Text(if (local) "Delete \"$name\"?" else "Remove \"$name\" from Aalap?", color = com.music.spotui.ui.theme.TextPrimary) },
        text = {
            Text(
                if (local) "The playlist is removed from Aalap. The songs stay in your library."
                else "It stays in your Spotify account and won't come back here when Aalap syncs. You can show it again from your account menu in Library.",
                color = com.music.spotui.ui.theme.TextMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (local) com.music.spotui.data.local.AalapPlaylists.delete(context, playlistId)
                else com.music.spotui.data.local.HiddenPlaylists.hide(context, playlistId)
                onRemoved()
            }) { Text(if (local) "Delete" else "Remove", color = androidx.compose.ui.graphics.Color(0xFFE57373)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = com.music.spotui.ui.theme.TextPrimary) } },
    )
}
