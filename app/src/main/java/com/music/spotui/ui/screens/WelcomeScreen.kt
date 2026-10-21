package com.music.spotui.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.R
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.preferences.isYoutubeLoggedIn
import com.music.spotui.data.preferences.setAalapAccount
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary

/** First screen: the Aalap account (name + email). Next: connect your music. */
@Composable
fun WelcomeScreen(navController: NavController) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(com.music.spotui.data.preferences.getAalapName(context)) }
    var email by remember { mutableStateOf(com.music.spotui.data.preferences.getAalapEmail(context)) }
    var error by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Saffron.copy(alpha = 0.22f), AppBackground, AppBackground)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Image(painterResource(R.drawable.logo), contentDescription = "Aalap", modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(18.dp))
        Text("Aalap", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("All your music. One app.", color = TextMuted, fontSize = 16.sp)
        Spacer(Modifier.height(40.dp))

        AalapField(name, "Your name", KeyboardType.Text) { name = it; error = "" }
        Spacer(Modifier.height(12.dp))
        AalapField(email, "Email", KeyboardType.Email) { email = it; error = "" }
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFE57373), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Spacer(Modifier.height(22.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Saffron)
                .clickable {
                    when {
                        name.isBlank() -> error = "Enter your name"
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> error = "Enter a valid email"
                        else -> {
                            setAalapAccount(context, name, email)
                            navController.navigate(Routes.MusicSource.route) {
                                popUpTo(Routes.Login.route) { inclusive = true }
                            }
                        }
                    }
                }
                .padding(vertical = 16.dp),
        ) {
            Text("Continue", color = OnSaffron, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Next you'll connect your music once. Use the same Google account everywhere and it's a tap each.",
            color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AalapField(value: String, label: String, type: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedBorderColor = Saffron, unfocusedBorderColor = Hairline,
            focusedLabelColor = Saffron, unfocusedLabelColor = TextMuted,
            cursorColor = Saffron,
            focusedContainerColor = Surface, unfocusedContainerColor = Surface,
        ),
    )
}

/**
 * "Connect your music": each service once, with a tick when done. Spotify brings the
 * library (required); YouTube Music the main audio; JioSaavn and Gaana need no login.
 * Apple Music favourites are imported later from Profile.
 */
@Composable
fun ConnectServicesScreen(navController: NavController) {
    val context = LocalContext.current
    // Re-read when coming back from a connect screen.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
    val spotifyOk = remember(tick) { SpotifySession.spDc(context).isNotBlank() }
    val youtubeOk = remember(tick) { isYoutubeLoggedIn(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("Connect your music", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Aalap brings everything together. Connect once; it stays connected.",
            color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
        )
        ServiceRow(
            name = "Spotify",
            role = "Your library, playlists and likes",
            done = spotifyOk,
            required = true,
        ) { navController.navigate(Routes.SpotifyConnect.route) }
        ServiceRow(
            name = "YouTube Music",
            role = "Main audio source, explicit songs included",
            done = youtubeOk,
        ) { navController.navigate("${Routes.YouTubeLogin.route}?next=") }
        ServiceRow(name = "JioSaavn", role = "Backup audio in 320 kbps", done = true, auto = true) {}
        ServiceRow(name = "Gaana", role = "Last backup source", done = true, auto = true) {}
        ServiceRow(name = "Apple Music", role = "Import favourites later from Profile", done = false, later = true) {}

        Spacer(Modifier.height(26.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (spotifyOk) Saffron else SurfaceHigh)
                .clickable(enabled = spotifyOk) {
                    com.music.spotui.data.preferences.setPrimaryMusicSource(
                        context, com.music.spotui.data.preferences.MusicSource.YOUTUBE_MUSIC,
                    )
                    navController.navigate(Routes.Home.route) { popUpTo(0) { inclusive = true } }
                }
                .padding(vertical = 16.dp),
        ) {
            Text(
                if (spotifyOk) "Start listening" else "Connect Spotify to continue",
                color = if (spotifyOk) OnSaffron else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ServiceRow(
    name: String,
    role: String,
    done: Boolean,
    required: Boolean = false,
    auto: Boolean = false,
    later: Boolean = false,
    onConnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, if (done && !auto) Saffron.copy(alpha = 0.5f) else Hairline, RoundedCornerShape(18.dp))
            .clickable(enabled = !done && !auto && !later, onClick = onConnect)
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (required) {
                    Spacer(Modifier.width(8.dp))
                    Text("Required", color = Saffron, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(role, color = TextMuted, fontSize = 12.sp)
        }
        when {
            done -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Saffron),
            ) { Text("✓", color = OnSaffron, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            later -> Text("Later", color = TextMuted, fontSize = 13.sp, modifier = Modifier.alpha(0.8f))
            else -> Text("Connect", color = Saffron, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
