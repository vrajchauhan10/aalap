package com.music.aalap.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.aalap.data.preferences.RecoPrefs
import com.music.aalap.data.recommendation.engine.Feedback as F
import com.music.aalap.ui.theme.AppPalette

/**
 * Settings → Recommendations: the discovery dial, the language mix and your feedback.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendationSettings() {
    val context = LocalContext.current
    var dial by remember { mutableStateOf(RecoPrefs.discovery(context)) }
    var levels by remember { mutableStateOf(RecoPrefs.languageLevels(context)) }

    // Your pace lately: what the mixes lean towards (worked out off the main thread).
    val pace by androidx.compose.runtime.produceState<com.music.aalap.data.recommendation.engine.Pace.Target?>(null) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.music.aalap.data.recommendation.engine.RecentPace.measured(context) }
    }

    Column(Modifier.padding(vertical = 8.dp)) {
        // Your pace lately: shown always; mixes follow it only while the switch is on.
        var followPace by remember { mutableStateOf(RecoPrefs.followPace(context)) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(value = followPace, role = androidx.compose.ui.semantics.Role.Switch) {
                followPace = it; RecoPrefs.setFollowPace(context, it)
            },
        ) {
            Column(Modifier.weight(1f)) {
                Text("Follow my pace", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    (pace?.let { p ->
                        "Lately: " + p.label + (p.bpm?.let { " \u00B7 about ${it.toInt()} bpm" } ?: "") + " (your last ${p.songs} songs). "
                    } ?: "Aalap learns your pace (tempo and energy) as you play. ") +
                        if (followPace) "Your Mix and language mixes (\u201Cplay Hindi songs\u201D) lean this way, in any language."
                        else "Off: mixes don't look at your pace.",
                    color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                )
            }
            androidx.compose.material3.Switch(
                checked = followPace, onCheckedChange = null,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = com.music.aalap.ui.theme.OnSaffron, checkedTrackColor = AppPalette,
                    uncheckedThumbColor = com.music.aalap.ui.theme.TextMuted, uncheckedTrackColor = com.music.aalap.ui.theme.SurfaceHigh,
                ),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Discovery", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecoPrefs.Discovery.entries.forEach { d ->
                Chip(d.label, d == dial) { dial = d; RecoPrefs.setDiscovery(context, d) }
            }
        }
        Text(dial.blurb, color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Language mix", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (levels.isEmpty()) "Automatic: follows what you're playing" else "Your mix: tap a language for Off, Some or Lots",
                    color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp,
                )
            }
            Text("Choose", color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Switch(checked = levels.isNotEmpty(), onCheckedChange = { on ->
                levels = if (on) mapOf("hindi" to 2, "punjabi" to 1, "english" to 1) else emptyMap()
                RecoPrefs.setLanguageLevels(context, levels)
            })
        }
        if (levels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecoPrefs.LANGUAGES.forEach { (code, label) ->
                    val level = levels[code] ?: 0
                    Chip("$label · ${RecoPrefs.LEVELS[level]}", level > 0, strong = level == 2) {
                        levels = levels + (code to (level + 1) % 3)
                        RecoPrefs.setLanguageLevels(context, levels)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        FeedbackPanel()
    }
}

/**
 * What your "more / less like this" adds up to: for each side, the artists, the kinds of
 * song and their pace (not a list of songs), each with ✕ to forget just that, and Clear all.
 * Then who you've blocked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackPanel() {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val data by androidx.compose.runtime.produceState<Triple<F.Summary, F.Summary, Pair<List<String>, Int>>?>(null, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Triple(F.summary(context, F.Kind.MORE), F.summary(context, F.Kind.LESS), F.blocked(context))
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun clear(kind: F.Kind, artist: String? = null, feel: com.music.aalap.data.recommendation.engine.Feel? = null) {
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { F.clear(context, kind, artist, feel) }
            version++
        }
    }
    Text("Your feedback", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Text("From \u201CMore like this\u201D and \u201CLess like this\u201D on songs. Tap \u2715 to forget one part.",
        color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
    val d = data ?: return
    for ((title, kind, sum) in listOf(Triple("More like this", F.Kind.MORE, d.first), Triple("Less like this", F.Kind.LESS, d.second))) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (sum.songs > 0) Text("Clear all", color = com.music.aalap.ui.theme.Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { clear(kind) }.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (sum.songs == 0) {
            Text("Nothing yet", color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            continue
        }
        Text("${sum.songs} song" + (if (sum.songs == 1) "" else "s") + (sum.pace?.let { " \u00B7 $it" } ?: ""),
            color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sum.artists.take(12).forEach { (key, name, n) -> ClearChip(if (n > 1) "$name \u00D7$n" else name) { clear(kind, artist = key) } }
            sum.feels.forEach { (feel, n) -> ClearChip(feel.label + if (n > 1) " \u00D7$n" else "", accent = true) { clear(kind, feel = feel) } }
        }
    }
    val (artists, songs) = d.third
    if (artists.isNotEmpty() || songs > 0) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Never play", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Clear all", color = com.music.aalap.ui.theme.Saffron, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { clear(F.Kind.BLOCK_ARTIST); clear(F.Kind.BLOCK_SONG) }.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            artists.forEach { a -> ClearChip(a.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) { clear(F.Kind.BLOCK_ARTIST, artist = a) } }
            if (songs > 0) ClearChip("$songs song" + if (songs == 1) "" else "s") { clear(F.Kind.BLOCK_SONG) }
        }
    }
}

/** A chip with ✕: tapping it forgets that part. */
@Composable
private fun ClearChip(label: String, accent: Boolean = false, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (accent) com.music.aalap.ui.theme.Saffron.copy(alpha = 0.14f) else com.music.aalap.ui.theme.SurfaceHigh)
            .clickable { onClear() }.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Text(label, color = if (accent) com.music.aalap.ui.theme.Saffron else com.music.aalap.ui.theme.TextPrimary, fontSize = 13.sp)
        Text("  \u2715", color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, strong: Boolean = selected, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) com.music.aalap.ui.theme.OnSaffron else com.music.aalap.ui.theme.TextPrimary,
        fontSize = 13.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) (if (strong) AppPalette else AppPalette.copy(alpha = 0.6f)) else com.music.aalap.ui.theme.SurfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
