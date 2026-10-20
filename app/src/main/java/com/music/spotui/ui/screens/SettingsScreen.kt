package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.music.spotui.data.preferences.CROSSFADE_MAX_MS
import com.music.spotui.data.preferences.StreamQuality
import com.music.spotui.data.preferences.getCellularQuality
import com.music.spotui.data.preferences.getCrossfadeMs
import com.music.spotui.data.preferences.setCrossfadeMs
import com.music.spotui.data.preferences.getDownloadQuality
import com.music.spotui.data.preferences.getLyricTranslateLang
import com.music.spotui.data.preferences.isVideoFallbackEnabled
import com.music.spotui.data.preferences.getWifiQuality
import com.music.spotui.data.preferences.setCellularQuality
import com.music.spotui.data.preferences.setDownloadQuality
import com.music.spotui.data.preferences.setLyricTranslateLang
import com.music.spotui.data.preferences.setVideoFallbackEnabled
import com.music.spotui.data.preferences.setWifiQuality
import com.music.spotui.data.preferences.MusicSource
import com.music.spotui.data.preferences.getPrimaryMusicSource
import com.music.spotui.data.preferences.isYoutubeLoggedIn
import com.music.spotui.data.preferences.setPrimaryMusicSource
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import java.util.Locale

private val translateLanguages by lazy {
    Locale.getISOLanguages()
        .map { it to Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
        .filter { it.second.isNotBlank() }
        .sortedBy { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var wifiQ by remember { mutableStateOf(getWifiQuality(context)) }
    var cellQ by remember { mutableStateOf(getCellularQuality(context)) }
    var dlQ by remember { mutableStateOf(getDownloadQuality(context)) }
    var crossfadeMs by remember { mutableStateOf(getCrossfadeMs(context).toFloat()) }
    var videoFallback by remember { mutableStateOf(isVideoFallbackEnabled(context)) }
    var selectedLanguage by remember { mutableStateOf(getLyricTranslateLang(context)) }
    var languageQuery by remember { mutableStateOf("") }
    val youtubeConnected = isYoutubeLoggedIn(context)
    var primarySource by remember {
        mutableStateOf(getPrimaryMusicSource(context) ?: MusicSource.YOUTUBE_MUSIC)
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile", color = com.music.spotui.ui.theme.TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = com.music.spotui.ui.theme.TextPrimary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ProfileHeader(navController)
            SectionTitle("Appearance")
            // Applies instantly: ThemeState drives the Aalap palette app-wide.
            com.music.spotui.data.preferences.ThemeMode.entries.forEach { mode ->
                SourceSettingRow(
                    title = mode.label,
                    subtitle = when (mode) {
                        com.music.spotui.data.preferences.ThemeMode.SYSTEM -> "Match your phone's dark mode"
                        com.music.spotui.data.preferences.ThemeMode.LIGHT -> "Warm cream background"
                        com.music.spotui.data.preferences.ThemeMode.DARK -> "Deep indigo background"
                    },
                    selected = com.music.spotui.ui.theme.ThemeState.mode == mode,
                ) { com.music.spotui.ui.theme.ThemeState.set(context, mode) }
            }

            SectionTitle("Audio quality")
            QualityPicker(
                title = "Streaming over Wi-Fi",
                selected = wifiQ,
            ) { wifiQ = it; setWifiQuality(context, it) }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
            ) { cellQ = it; setCellularQuality(context, it) }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
            ) { dlQ = it; setDownloadQuality(context, it) }

            // Lossless FLAC comes from the Tidal community backend (no login). Falls back
            // to best-quality YouTube on a miss.
            Text(
                "Lossless uses free community FLAC servers; if a song isn't there, Aalap plays High quality instead.",
                color = com.music.spotui.ui.theme.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionTitle("Lyrics")
            val languageMatches = remember(languageQuery) {
                val query = languageQuery.trim()
                if (query.isBlank()) {
                    emptyList()
                } else {
                    translateLanguages.filter { (code, name) ->
                        code.contains(query, true) || name.contains(query, true)
                    }.sortedWith(
                        compareByDescending<Pair<String, String>> {
                            it.first.equals(query, true) || it.second.equals(query, true)
                        }.thenBy { it.second }
                    ).take(8)
                }
            }
            OutlinedTextField(
                value = languageQuery,
                onValueChange = {
                    languageQuery = it
                    if (it.isBlank()) {
                        selectedLanguage = ""
                        setLyricTranslateLang(context, "")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                singleLine = true,
                label = { Text("Translate lyrics to", color = com.music.spotui.ui.theme.TextMuted) },
                placeholder = { Text("Search languages", color = Color(0xFF72728A)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = com.music.spotui.ui.theme.TextPrimary,
                    unfocusedTextColor = com.music.spotui.ui.theme.TextPrimary,
                    cursorColor = AppPalette,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF3A3A46),
                    focusedLabelColor = AppPalette,
                    unfocusedLabelColor = com.music.spotui.ui.theme.TextMuted,
                    focusedContainerColor = AppBackground,
                    unfocusedContainerColor = AppBackground,
                ),
            )
            languageMatches.forEach { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(com.music.spotui.ui.theme.Surface)
                        .clickable {
                            selectedLanguage = code
                            setLyricTranslateLang(context, code)
                            languageQuery = ""
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(code, color = AppPalette, fontSize = 13.sp)
                }
            }
            Text(
                text = if (selectedLanguage.isBlank()) {
                    "Using phone language"
                } else {
                    "Selected: ${translateLanguages.firstOrNull { it.first == selectedLanguage }?.second ?: selectedLanguage} ($selectedLanguage)"
                },
                color = com.music.spotui.ui.theme.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionTitle("Crossfade")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crossfade", color = com.music.spotui.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (crossfadeMs <= 0f) "Off" else "${(crossfadeMs / 1000f).let { String.format("%.0f", it) }}s",
                    color = if (crossfadeMs <= 0f) com.music.spotui.ui.theme.TextMuted else AppPalette,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Blend the end of a song into the start of the next",
                color = com.music.spotui.ui.theme.TextMuted,
                fontSize = 13.sp,
            )
            Slider(
                value = crossfadeMs,
                onValueChange = { crossfadeMs = it },
                onValueChangeFinished = { setCrossfadeMs(context, crossfadeMs.toInt()) },
                valueRange = 0f..CROSSFADE_MAX_MS.toFloat(),
                steps = (CROSSFADE_MAX_MS / 1000) - 1, // 1-second stops
                colors = SliderDefaults.colors(
                    thumbColor = AppPalette,
                    activeTrackColor = AppPalette,
                    inactiveTrackColor = com.music.spotui.ui.theme.SurfaceHigh,
                ),
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("Music source")
            SourceSettingRow(
                title = "YouTube Music",
                subtitle = if (youtubeConnected) "Connected — explicit and age-restricted playback enabled" else "Login required for explicit and age-restricted songs",
                selected = primarySource == MusicSource.YOUTUBE_MUSIC,
            ) {
                if (youtubeConnected) {
                    primarySource = MusicSource.YOUTUBE_MUSIC
                    setPrimaryMusicSource(context, primarySource)
                } else {
                    navController.navigate(com.music.spotui.ui.navigation.Routes.YouTubeLogin.route)
                }
            }
            Text(
                text = if (youtubeConnected) "Reconnect YouTube Music" else "Log in to YouTube Music",
                color = AppPalette,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { navController.navigate(com.music.spotui.ui.navigation.Routes.YouTubeLogin.route) }
                    .padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            // Power-user options, folded away so the page stays about everyday choices.
            var showAdvanced by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(top = 16.dp, bottom = 4.dp),
            ) {
                Text("Advanced", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (showAdvanced) "Hide" else "Show", color = com.music.spotui.ui.theme.TextMuted, fontSize = 13.sp)
            }
            androidx.compose.animation.AnimatedVisibility(visible = showAdvanced) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    SubTitle("Song matching")
                    SettingsSwitchRow(
                        title = "Allow video fallback",
                        subtitle = "Use regular YouTube videos only after Music song results fail",
                        checked = videoFallback,
                    ) {
                        videoFallback = it
                        setVideoFallbackEnabled(context, it)
                    }

                    Spacer(Modifier.height(12.dp))
                    SubTitle("Soulseek (experimental)")
                    var soulseekOn by remember { mutableStateOf(com.music.spotui.data.preferences.isSoulseekEnabled(context)) }
                    SettingsSwitchRow(
                        title = "Soulseek lossless fallback",
                        subtitle = "When the FLAC servers miss, fetch FLACs from Soulseek (peer-to-peer). Uses data and shows your IP to peers.",
                        checked = soulseekOn,
                    ) {
                        soulseekOn = it
                        com.music.spotui.data.preferences.setSoulseekEnabled(context, it)
                        // Off: leave the network now and close the listening port.
                        if (!it) Thread { com.music.spotui.soulseek.SoulseekClient.disconnect() }.start()
                    }

                }
            }

            Spacer(Modifier.height(28.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE57373).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable {
                        com.music.spotui.data.api.SpotifySession.setSpDc(context, "")
                        com.music.spotui.data.api.Api.HomeCache.clear()
                        navController.navigate(com.music.spotui.ui.navigation.Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    .padding(vertical = 14.dp),
            ) {
                Text("Log out", color = Color(0xFFE57373), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Aalap ${com.music.spotui.BuildConfig.VERSION_NAME}",
                color = com.music.spotui.ui.theme.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            // Clears the floating mini player + tab bar so Log out can scroll fully into view.
            Spacer(Modifier.height(190.dp))
        }
    }
}

@Composable
private fun SourceSettingRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (selected) com.music.spotui.ui.theme.Surface else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AppPalette, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text = text,
        color = com.music.spotui.ui.theme.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppPalette,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = com.music.spotui.ui.theme.OnSaffron,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = com.music.spotui.ui.theme.TextMuted,
                uncheckedTrackColor = com.music.spotui.ui.theme.SurfaceHigh,
            ),
        )
    }
}

@Composable
private fun QualityPicker(
    title: String,
    selected: StreamQuality,
    onSelect: (StreamQuality) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        // One compact row of choices instead of a four-item list per setting.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StreamQuality.entries.forEach { q ->
                val isSel = q == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSel) AppPalette else com.music.spotui.ui.theme.SurfaceHigh)
                        .clickable { onSelect(q) }
                        .padding(vertical = 9.dp),
                ) {
                    Text(
                        q.label,
                        color = if (isSel) com.music.spotui.ui.theme.OnSaffron else com.music.spotui.ui.theme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(selected.detail, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/** Profile card: who is signed in, at-a-glance listening stats, and shortcuts. */
@OptIn(com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class)
@Composable
private fun ProfileHeader(navController: NavController) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { com.music.spotui.data.api.ProfileCache.ensure(context) }
    val name = com.music.spotui.data.api.ProfileCache.name
    val avatar = com.music.spotui.data.api.ProfileCache.imageUrl
    val liked = remember { com.music.spotui.data.preferences.getLikedSongIds(context).size }
    val downloads = remember { com.music.spotui.data.preferences.getDownloadedSongs(context).size }
    val plays = remember { com.music.spotui.data.preferences.getListeningHistory(context).size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(AppPalette.copy(alpha = 0.28f), com.music.spotui.ui.theme.Surface)
                )
            )
            .border(1.dp, com.music.spotui.ui.theme.Hairline, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(AppPalette),
            ) {
                if (avatar != null) {
                    com.bumptech.glide.integration.compose.GlideImage(
                        model = avatar,
                        contentDescription = "Profile photo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    Text(
                        name?.trim()?.firstOrNull()?.uppercase() ?: "A",
                        color = com.music.spotui.ui.theme.OnSaffron,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name ?: "Your profile",
                    color = com.music.spotui.ui.theme.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text("Connected to Spotify", color = com.music.spotui.ui.theme.TextMuted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStat("$liked", "Liked", Modifier.weight(1f)) {
                navController.navigate(com.music.spotui.ui.navigation.Routes.Liked.route)
            }
            ProfileStat("$downloads", "Downloads", Modifier.weight(1f)) {
                navController.navigate(com.music.spotui.ui.navigation.Routes.Downloads.route)
            }
            ProfileStat("$plays", "Plays", Modifier.weight(1f)) {
                navController.navigate(com.music.spotui.ui.navigation.Routes.History.route)
            }
        }
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(com.music.spotui.ui.theme.AppBackground.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(value, color = com.music.spotui.ui.theme.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = com.music.spotui.ui.theme.TextMuted, fontSize = 12.sp)
    }
}
