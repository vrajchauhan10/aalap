package com.music.aalap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.music.aalap.data.preferences.HomeSections

/**
 * Home's sections in order: arrows move one up or down, the switch shows or hides it.
 * Aaj ka Aalap stays first and always shows.
 */
@Composable
fun HomeSectionsSetting() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HomeSections.load(context) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(HomeSections.label(HomeSections.PINNED), color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("Always first", color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp)
        }
        val order = HomeSections.order
        order.forEachIndexed { i, key ->
            val on = HomeSections.shows(key)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Text(
                    HomeSections.label(key),
                    color = if (on) com.music.aalap.ui.theme.TextPrimary else com.music.aalap.ui.theme.TextMuted,
                    fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                Arrow("▲", i > 0, "Move ${HomeSections.label(key)} up") { HomeSections.move(context, key, -1) }
                Arrow("▼", i < order.lastIndex, "Move ${HomeSections.label(key)} down") { HomeSections.move(context, key, +1) }
                Switch(
                    checked = on,
                    onCheckedChange = { HomeSections.set(context, key, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = com.music.aalap.ui.theme.Saffron),
                )
            }
        }
        if (order != HomeSections.defaultOrder()) Text(
            "Reset order", color = com.music.aalap.ui.theme.Saffron, fontSize = 13.sp,
            modifier = Modifier.clickable { HomeSections.resetOrder(context) }.padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, description: String, onClick: () -> Unit) {
    Text(
        glyph,
        color = if (enabled) com.music.aalap.ui.theme.TextPrimary else com.music.aalap.ui.theme.TextMuted.copy(alpha = 0.3f),
        fontSize = 14.sp,
        modifier = Modifier
            .androidx_semantics(description)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

private fun Modifier.androidx_semantics(d: String) = this.semantics { contentDescription = d }

/** "Edit Home" from the bottom of Home: the same switches in a dialog. */
@Composable
fun HomeSectionsDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.music.aalap.ui.theme.Surface,
        title = { Text("Sections on Home", color = com.music.aalap.ui.theme.TextPrimary) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { HomeSectionsSetting() } },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done", color = com.music.aalap.ui.theme.Saffron) }
        },
    )
}

