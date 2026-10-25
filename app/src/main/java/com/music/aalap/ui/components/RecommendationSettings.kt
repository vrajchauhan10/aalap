package com.music.aalap.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
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
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.music.aalap.data.recommendation.engine.RecentPace.target(context) }
    }

    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Your pace lately", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            pace?.let { p ->
                "${p.label.replaceFirstChar { it.uppercase() }}" + (p.bpm?.let { " \u00B7 about ${it.toInt()} bpm" } ?: "") +
                    " \u00B7 from your last ${p.songs} songs. Your Mix and language mixes (\u201Cplay Hindi songs\u201D) lean this way, in any language."
            } ?: "Play a few more songs and Aalap learns your pace: the tempo and energy your mixes lean towards, in any language.",
            color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
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
        var cleared by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = !cleared) {
                com.music.aalap.data.recommendation.engine.Feedback.clearAll(context)
                cleared = true
            }.padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Clear \"more / less like this\"", color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (cleared) "Cleared: blocked songs and artists can play again"
                    else "Forget your feedback, including songs and artists you blocked",
                    color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp,
                )
            }
        }
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
