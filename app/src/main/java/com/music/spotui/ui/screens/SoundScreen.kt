package com.music.spotui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.audio.AudioEffects
import com.music.spotui.data.preferences.EQ_REFERENCE_HZ
import com.music.spotui.data.preferences.EqPreset
import com.music.spotui.data.preferences.PLAYBACK_SPEEDS
import com.music.spotui.data.preferences.currentEqGains
import com.music.spotui.data.preferences.getBassBoost
import com.music.spotui.data.preferences.getEqPreset
import com.music.spotui.data.preferences.getPlaybackSpeed
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isNormalizeVolume
import com.music.spotui.data.preferences.setBassBoost
import com.music.spotui.data.preferences.setCustomEqGains
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqPreset
import com.music.spotui.data.preferences.setNormalizeVolume
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.Hairline
import com.music.spotui.ui.theme.OnSaffron
import com.music.spotui.ui.theme.Saffron
import com.music.spotui.ui.theme.SaffronGold
import com.music.spotui.ui.theme.Surface
import com.music.spotui.ui.theme.SurfaceHigh
import com.music.spotui.ui.theme.TextMuted
import com.music.spotui.ui.theme.TextPrimary

private const val EQ_RANGE_DB = 12f

/** Sound: equalizer with presets, bass boost, volume normalisation and playback speed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundScreen(navController: NavController) {
    val context = LocalContext.current
    var eqOn by remember { mutableStateOf(isEqEnabled(context)) }
    var preset by remember { mutableStateOf(getEqPreset(context)) }
    var gains by remember { mutableStateOf(currentEqGains(context)) }
    var bass by remember { mutableStateOf(getBassBoost(context) / 1000f) }
    var normalize by remember { mutableStateOf(isNormalizeVolume(context)) }
    var speed by remember { mutableStateOf(getPlaybackSpeed(context)) }

    fun push() = AudioEffects.apply(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary,
                modifier = Modifier.size(26.dp).clickable { navController.navigateUp() },
            )
            Spacer(Modifier.width(16.dp))
            Text("Sound", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        ToggleRow("Equalizer", "Shape the sound with a preset or your own curve", eqOn) {
            eqOn = it; setEqEnabled(context, it); push()
        }

        Column(Modifier.alpha(if (eqOn) 1f else 0.45f)) {
            Label("Presets")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPreset.entries.filter { it != EqPreset.CUSTOM || preset == EqPreset.CUSTOM }.forEach { p ->
                    val sel = p == preset
                    Text(
                        p.label,
                        color = if (sel) OnSaffron else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Saffron else SurfaceHigh)
                            .clickable(enabled = eqOn) {
                                preset = p; setEqPreset(context, p)
                                gains = currentEqGains(context); push()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // Five drag-able bands; editing any of them switches to the Custom preset.
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                    .padding(vertical = 16.dp),
            ) {
                gains.forEachIndexed { i, db ->
                    EqBand(
                        db = db,
                        label = EQ_REFERENCE_HZ[i].let { if (it >= 1000) "${it / 1000}k" else "$it" },
                        enabled = eqOn,
                    ) { newDb ->
                        val updated = gains.toMutableList().also { it[i] = newDb }
                        gains = updated
                        setCustomEqGains(context, updated)
                        if (preset != EqPreset.CUSTOM) { preset = EqPreset.CUSTOM; setEqPreset(context, EqPreset.CUSTOM) }
                        push()
                    }
                }
            }
        }

        Label("Bass boost")
        Slider(
            value = bass,
            onValueChange = { bass = it },
            onValueChangeFinished = { setBassBoost(context, (bass * 1000).toInt()); push() },
            colors = SliderDefaults.colors(thumbColor = SaffronGold, activeTrackColor = SaffronGold, inactiveTrackColor = SurfaceHigh),
        )
        Text(if (bass <= 0.01f) "Off" else "${(bass * 100).toInt()}%", color = TextMuted, fontSize = 12.sp)

        Spacer(Modifier.height(8.dp))
        ToggleRow("Normalize volume", "Similar loudness for every song", normalize) {
            normalize = it; setNormalizeVolume(context, it); push()
        }

        Spacer(Modifier.height(16.dp))
        com.music.spotui.ui.components.SpeedControl(speed) { speed = it; SongPlayer.setSpeed(context, it) }
        Text("Handy for podcasts. Voices keep their normal pitch.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(200.dp))
    }
}

fun speedLabel(s: Float): String = com.music.spotui.ui.components.formatSpeed(s)

@Composable
private fun Label(text: String) {
    Text(text, color = Saffron, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles (and is read out as one labelled switch).
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
        .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange).padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnSaffron, checkedTrackColor = Saffron,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = SurfaceHigh,
            ),
        )
    }
}

/** One vertical equalizer band: drag or tap to set -12..+12 dB. */
@Composable
private fun EqBand(db: Float, label: String, enabled: Boolean, onChange: (Float) -> Unit) {
    val heightDp = 150.dp
    fun dbAt(y: Float, h: Float) = ((1f - (y / h).coerceIn(0f, 1f)) * 2f - 1f) * EQ_RANGE_DB
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${if (db > 0) "+" else ""}${db.toInt()}", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .width(36.dp)
                .height(heightDp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { onChange(dbAt(it.y, size.height.toFloat())) }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        onChange(dbAt(change.position.y, size.height.toFloat()))
                    }
                },
        ) {
            // Track, then a fill from the bottom up to the band's level.
            Box(Modifier.width(8.dp).height(heightDp).clip(RoundedCornerShape(4.dp)).background(SurfaceHigh))
            val fraction = ((db + EQ_RANGE_DB) / (2 * EQ_RANGE_DB)).coerceIn(0.03f, 1f)
            Box(Modifier.width(8.dp).height(heightDp * fraction).clip(RoundedCornerShape(4.dp)).background(Saffron))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}
