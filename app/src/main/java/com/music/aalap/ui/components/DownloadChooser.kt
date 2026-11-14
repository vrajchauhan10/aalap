package com.music.aalap.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.preferences.StreamQuality
import com.music.aalap.ui.theme.Hairline
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SaffronGold
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary

/** How each download mode looks in the chooser, and roughly how many bits a second it saves. */
private data class Mode(
    val q: StreamQuality,
    val title: String,
    val format: String,
    val badge: String,
    val line: String,
    val avgKbps: Int,
    val colors: List<Color>,
    val tag: String? = null,
)

private val MODES = listOf(
    Mode(StreamQuality.LOSSLESS, "Best", "Lossless FLAC", "FLAC", "Studio-master sound, every detail just as it was recorded", 900,
        listOf(Color(0xFFFFB547), Color(0xFFE5397A)), tag = "Best sound"),
    Mode(StreamQuality.HIGH, "High", "AAC 320 kbps", "320", "Sounds great on headphones and speakers", 300,
        listOf(Color(0xFFFF8A3D), Color(0xFFFFC94D))),
    Mode(StreamQuality.NORMAL, "Normal", "AAC 160 kbps", "160", "Good for everyday listening", 160,
        listOf(Color(0xFF6E8BFF), Color(0xFF9B6CFF))),
    Mode(StreamQuality.LOW, "Data saver", "96 kbps", "96", "Smallest files, fits the most songs", 96,
        listOf(Color(0xFF2FBF9B), Color(0xFF6EDBC0)), tag = "Least space"),
)

/** Bytes a list takes in a mode; songs with no length count as 3½ minutes. */
private fun estimate(songs: List<SongsModel>, kbps: Int): Long =
    songs.sumOf { s -> val ms = s.durationMs.toLong(); (if (ms > 0) ms / 1000 else 210L) * kbps * 1000L / 8 }

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000L -> String.format("%.1f GB", b / 1e9)
    b >= 1_000_000L -> String.format("%.0f MB", b / 1e6)
    else -> String.format("%.0f KB", b / 1e3)
}

private fun formatLength(songs: List<SongsModel>): String {
    val min: Long = songs.sumOf { s -> val ms = s.durationMs.toLong(); if (ms > 0) ms / 60_000 else 3L }
    return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
}

/** Whether to show the chooser before a download (off once "Remember my choice" was on). */
fun shouldAskDownloadQuality(c: android.content.Context) =
    c.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE).getBoolean("ask_quality", true)
fun setAskDownloadQuality(c: android.content.Context, ask: Boolean) =
    c.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE).edit().putBoolean("ask_quality", ask).apply()

/**
 * Before a download: pick the sound quality, seeing what each costs in space and how much the
 * phone has free. The pick becomes the download quality in Settings; "Remember my choice"
 * skips this sheet next time (turn it back on in Settings → Audio quality).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadChooserSheet(songs: List<SongsModel>, onDismiss: () -> Unit, onChoose: (StreamQuality) -> Unit) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf(com.music.aalap.data.preferences.getDownloadQuality(context)) }
    var remember by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf(com.music.aalap.di.Downloads.wifiOnly(context)) }
    val free = remember { runCatching { android.os.StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L) }
    val sizes = remember(songs) { MODES.associate { it.q to estimate(songs, it.avgKbps) } }
    val biggest = sizes.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val need = sizes[picked] ?: 0L
    val fits = free <= 0L || need < free

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // Header: what's being saved.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Saffron, SaffronGold))),
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(com.music.aalap.R.drawable.ic_download),
                        contentDescription = null, tint = OnSaffron, modifier = Modifier.size(24.dp),
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        if (songs.size == 1) "Download \"${songs.first().title}\"" else "Download ${songs.size} songs",
                        color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text("${formatLength(songs)} of music · choose the sound quality", color = TextMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))

            MODES.forEach { m ->
                ModeCard(m, sizes[m.q] ?: 0L, biggest, selected = picked == m.q) { picked = m.q }
                Spacer(Modifier.height(10.dp))
            }

            // Storage: how much of what's free this download uses.
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceHigh).padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Space on your phone", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (free > 0) Text("${formatBytes(free)} free", color = TextMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                val share by animateFloatAsState(if (free > 0) (need.toFloat() / free).coerceIn(0.01f, 1f) else 0f, label = "share")
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Hairline)) {
                    Box(
                        Modifier.fillMaxWidth(share).height(10.dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(if (fits) listOf(Saffron, SaffronGold) else listOf(Color(0xFFE57373), Color(0xFFFF8A80)))),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (fits) "This uses about ${formatBytes(need)}" + (if (free > 0) ", ${formatBytes(free - need)} left after" else "")
                    else "Not enough space: about ${formatBytes(need)} needed. Pick a smaller quality or free up space.",
                    color = if (fits) TextMuted else Color(0xFFE57373), fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            ToggleRow("Only on Wi-Fi", "Saves your mobile data", wifi) { wifi = it; com.music.aalap.di.Downloads.setWifiOnly(context, it) }
            ToggleRow("Remember my choice", "Don't ask again; change it in Settings → Audio quality", remember) { remember = it }

            Spacer(Modifier.height(14.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(if (fits) Brush.horizontalGradient(listOf(Saffron, SaffronGold)) else Brush.horizontalGradient(listOf(Hairline, Hairline)))
                    .clickable(enabled = fits) {
                        com.music.aalap.data.preferences.setDownloadQuality(context, picked)
                        if (remember) setAskDownloadQuality(context, false)
                        onDismiss()
                        onChoose(picked)
                    }
                    .padding(vertical = 15.dp),
            ) {
                Text(
                    "Download · ${formatBytes(need)}",
                    color = if (fits) OnSaffron else TextMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ModeCard(m: Mode, bytes: Long, biggest: Long, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) m.colors.first() else Hairline, label = "border")
    val bar by animateFloatAsState((bytes.toFloat() / biggest).coerceIn(0.04f, 1f), label = "bar")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) m.colors.first().copy(alpha = 0.10f) else SurfaceHigh)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        // The format as a coloured badge: FLAC, 320, 160, 96.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(m.colors)),
        ) {
            Text(m.badge, color = Color.White, fontSize = if (m.badge.length > 3) 13.sp else 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                m.tag?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        it, color = m.colors.first(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(m.colors.first().copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(m.format, color = m.colors.first(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(m.line, color = TextMuted, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            // Relative size against the biggest choice.
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Hairline)) {
                Box(Modifier.fillMaxWidth(bar).height(5.dp).clip(CircleShape).background(Brush.horizontalGradient(m.colors)))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatBytes(bytes), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("about", color = TextMuted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(22.dp).clip(CircleShape)
                    .background(if (selected) m.colors.first() else Color.Transparent)
                    .border(2.dp, if (selected) m.colors.first() else TextMuted, CircleShape),
            ) { if (selected) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = TextMuted, fontSize = 12.sp)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Saffron))
    }
}

/**
 * The whole "download these" flow in one place: the chooser when it should ask, else straight
 * to downloading in the saved quality. [start] gets the quality to use.
 */
@Composable
fun DownloadAsk(songs: List<SongsModel>?, onDone: () -> Unit, start: (StreamQuality) -> Unit) {
    val context = LocalContext.current
    if (songs == null) return
    if (!shouldAskDownloadQuality(context)) {
        androidx.compose.runtime.LaunchedEffect(songs) { onDone(); start(com.music.aalap.data.preferences.getDownloadQuality(context)) }
        return
    }
    DownloadChooserSheet(songs, onDismiss = onDone, onChoose = start)
}

