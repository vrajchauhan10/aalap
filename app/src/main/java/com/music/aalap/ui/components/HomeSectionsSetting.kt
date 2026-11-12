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
import com.music.aalap.data.preferences.HomeSections

/** A switch per Home section: off hides it from Home (nothing is deleted). */
@Composable
fun HomeSectionsSetting() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HomeSections.load(context) }
    Column {
        HomeSections.ALL.forEach { (key, label) ->
            val on = HomeSections.shows(key)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { HomeSections.set(context, key, !on) }.padding(vertical = 4.dp),
            ) {
                Text(label, color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = on,
                    onCheckedChange = { HomeSections.set(context, key, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = com.music.aalap.ui.theme.Saffron),
                )
            }
        }
    }
}

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

