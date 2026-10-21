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
 * "Connect your music". Two sign-ins come first: Spotify (required: your library,
 * playlists and likes) and YouTube Music (recommended: the main audio, including
 * explicit and age-restricted songs). Then the backup sources, which need no login
 * and can be switched off, and an Apple Music import.
 */
@Composable
fun ConnectServicesScreen(navController: NavController) {
    val context = LocalContext.current
    // Re-read when coming back from a sign-in screen.
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
    var saavnOn by remember { mutableStateOf(com.music.spotui.data.preferences.isJioSaavnEnabled(context)) }
    var gaanaOn by remember { mutableStateOf(com.music.spotui.data.preferences.isGaanaEnabled(context)) }
    var appleCount by remember { mutableIntStateOf(com.music.spotui.data.local.AppleMusicImport.tracks(context).size) }
    var appleDialog by remember { mutableStateOf(false) }

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
            "Sign in once. Aalap stays connected and brings it all together.",
            color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        StepLabel("Sign in")
        SignInRow(
            logo = R.drawable.ic_brand_spotify,
            name = "Spotify",
            tag = "Required",
            role = "Your library, playlists and likes",
            connected = spotifyOk,
        ) { navController.navigate(Routes.SpotifyConnect.route) }
        SignInRow(
            logo = R.drawable.ic_brand_ytmusic,
            name = "YouTube Music",
            tag = "Recommended",
            role = "Plays your songs in high quality, explicit and age-restricted songs included",
            connected = youtubeOk,
        ) { navController.navigate("${Routes.YouTubeLogin.route}?next=") }

        StepLabel("Backup sources · no login")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Hairline, RoundedCornerShape(18.dp))) {
            ServiceLine(
                logo = R.drawable.ic_brand_jiosaavn, name = "JioSaavn",
                status = "Plays in 320 kbps when YouTube can't", switch = saavnOn,
            ) { saavnOn = !saavnOn; com.music.spotui.data.preferences.setJioSaavnEnabled(context, saavnOn) }
            androidx.compose.material3.HorizontalDivider(color = Hairline, modifier = Modifier.padding(start = 66.dp))
            ServiceLine(
                logo = R.drawable.ic_brand_gaana, name = "Gaana",
                status = "Last backup, so a song always plays", switch = gaanaOn,
            ) { gaanaOn = !gaanaOn; com.music.spotui.data.preferences.setGaanaEnabled(context, gaanaOn) }
        }

        StepLabel("Bring your taste · optional")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Hairline, RoundedCornerShape(18.dp))) {
            ServiceLine(
                logo = R.drawable.ic_brand_apple_music, name = "Apple Music",
                status = if (appleCount > 0) "$appleCount songs imported" else "Import your library so recommendations know you",
                action = if (appleCount > 0) "Update" else "Import",
            ) { appleDialog = true }
        }
        if (appleDialog) AppleMusicImportDialog(onDismiss = { appleDialog = false }) { appleCount = it }

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
                if (spotifyOk) "Start listening" else "Sign in to Spotify to continue",
                color = if (spotifyOk) OnSaffron else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        if (spotifyOk && !youtubeOk) {
            Text(
                "You can sign in to YouTube Music later from Profile.",
                color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text.uppercase(), color = Saffron, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

/** A sign-in card: logo, what it brings, and a clear Sign in / Connected state. */
@Composable
private fun SignInRow(logo: Int, name: String, tag: String, role: String, connected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, if (connected) Saffron.copy(alpha = 0.5f) else Hairline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Image(painterResource(logo), contentDescription = name, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(tag, color = Saffron, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Text(role, color = TextMuted, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (connected) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Saffron),
                ) { Text("✓", color = OnSaffron, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Text("Change", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        } else {
            Text(
                "Sign in", color = OnSaffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Saffron).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
