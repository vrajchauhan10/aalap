package com.music.aalap.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.aalap.data.recommendation.Blend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Share: Blend with a friend (share your taste code, or make a Blend from theirs). */
@Composable
fun BlendSettings(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paste by remember { mutableStateOf<String?>(null) }
    var making by remember { mutableStateOf(false) }

    Row2("Share my taste for a Blend", "Your top artists, feels and 25 favourite songs (not your history), as a code to send") {
        scope.launch {
            val text = withContext(Dispatchers.IO) { Blend.shareText(context) }
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your Aalap taste"))
        }
    }
    Row2(if (making) "Making your Blend…" else "Make a Blend", "Paste a friend's taste code: a playlist of both your tastes") {
        if (making) return@Row2
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        paste = if (clip.contains(Blend.PREFIX)) clip else ""
    }

    paste?.let { text ->
        var value by remember { mutableStateOf(text) }
        val card = remember(value) { Blend.decode(value) }
        AlertDialog(
            onDismissRequest = { paste = null },
            title = { Text("Make a Blend") },
            text = {
                Column {
                    Text(card?.let { "With ${it.name}: ${it.artists.take(3).joinToString(", ")}…" } ?: "Paste the message your friend sent.", fontSize = 13.sp)
                    OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), maxLines = 4, placeholder = { Text("AALAP-BLEND:…") })
                }
            },
            confirmButton = {
                TextButton(enabled = card != null, onClick = {
                    val c = card ?: return@TextButton
                    paste = null; making = true
                    scope.launch {
                        val (id, n) = withContext(Dispatchers.IO) { runCatching { Blend.make(context, c) }.getOrDefault("" to 0) }
                        making = false
                        if (n > 0) navController.navigate(com.music.aalap.ui.navigation.playlistRoute(id, "Blend · You + ${c.name}"))
                        else android.widget.Toast.makeText(context, "Couldn't make the Blend, try again", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Make Blend") }
            },
            dismissButton = { TextButton(onClick = { paste = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Row2(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp)) {
        Text(title, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp)
    }
}
