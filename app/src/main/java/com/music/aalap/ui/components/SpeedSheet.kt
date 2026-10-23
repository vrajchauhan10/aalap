package com.music.aalap.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.aalap.data.preferences.PLAYBACK_SPEEDS
import com.music.aalap.ui.theme.OnSaffron
import com.music.aalap.ui.theme.Saffron
import com.music.aalap.ui.theme.SaffronGold
import com.music.aalap.ui.theme.Surface
import com.music.aalap.ui.theme.SurfaceHigh
import com.music.aalap.ui.theme.TextMuted
import com.music.aalap.ui.theme.TextPrimary
import kotlin.math.roundToInt

const val MIN_SPEED = 0.5f
const val MAX_SPEED = 2f
private const val SPEED_STEP = 0.05f

/** "1×", "1.1×", "1.25×" — trailing zeros dropped. */
fun formatSpeed(s: Float): String {
    val r = (s * 100).roundToInt() / 100f
    val text = if (r == r.toInt().toFloat()) "${r.toInt()}" else "%.2f".format(r).trimEnd('0').trimEnd('.')
    return "$text×"
}

/** Snap to the 0.05 grid the slider uses. */
private fun snap(s: Float) = ((s / SPEED_STEP).roundToInt() * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)

/**
 * Playback speed picker: a fine slider (0.5×–2× in 0.05 steps, applied live) plus
 * one-tap presets. Used from the player's speed chip and the Sound screen.
 */
@Composable
fun SpeedControl(speed: Float, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playback speed", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatSpeed(speed), color = Saffron, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = speed,
            onValueChange = { onChange(snap(it)) },
            valueRange = MIN_SPEED..MAX_SPEED,
            steps = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP).roundToInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = SaffronGold,
                activeTrackColor = Saffron,
                inactiveTrackColor = SurfaceHigh,
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatSpeed(MIN_SPEED), color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(formatSpeed(MAX_SPEED), color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLAYBACK_SPEEDS.forEach { p ->
                val sel = kotlin.math.abs(p - speed) < 0.001f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Saffron else SurfaceHigh)
                        .clickable { onChange(p) }
                        .padding(vertical = 9.dp),
                ) {
                    Text(formatSpeed(p), color = if (sel) OnSaffron else TextPrimary, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(speed: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            SpeedControl(speed, onChange)
            Text(
                "Voices keep their normal pitch. Handy for podcasts.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
