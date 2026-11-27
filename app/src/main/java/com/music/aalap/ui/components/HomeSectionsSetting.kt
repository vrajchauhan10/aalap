package com.music.aalap.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
 * Home's sections in order: drag a row by its handle (≡) to move it, like the queue; the switch
 * shows or hides it. Aaj ka Aalap stays first and always shows.
 */
@Composable
fun HomeSectionsSetting() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HomeSections.load(context) }
    val rowHeight = 50.dp
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowHeight.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val view = androidx.compose.ui.platform.LocalView.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(rowHeight)) {
            Text(HomeSections.label(HomeSections.PINNED), color = com.music.aalap.ui.theme.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text("Always first", color = com.music.aalap.ui.theme.TextMuted, fontSize = 12.sp)
        }
        val order = HomeSections.order
        // Each row keeps its identity while the order changes, so one drag can carry it several places.
        order.forEach { key -> androidx.compose.runtime.key(key) {
            val on = HomeSections.shows(key)
            val isDragging = dragging == key
            val lift by androidx.compose.animation.core.animateFloatAsState(if (isDragging) 1f else 0f, label = "lift")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) offset else 0f
                        scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift
                        shadowElevation = 12f * lift
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDragging) com.music.aalap.ui.theme.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Drag ${HomeSections.label(key)} to move it",
                    tint = com.music.aalap.ui.theme.TextMuted,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(10.dp)
                        .pointerInput(key) {
                            detectDragGestures(
                                onDragStart = {
                                    dragging = key; offset = 0f
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDragEnd = { dragging = null; offset = 0f },
                                onDragCancel = { dragging = null; offset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val at = HomeSections.order.indexOf(key)
                                    // Past half a row: it swaps with its neighbour and keeps following the finger.
                                    if (offset > rowPx / 2 && at < HomeSections.order.lastIndex) {
                                        HomeSections.move(context, key, +1); offset -= rowPx
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    } else if (offset < -rowPx / 2 && at > 0) {
                                        HomeSections.move(context, key, -1); offset += rowPx
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                },
                            )
                        },
                )
                Text(
                    HomeSections.label(key),
                    color = if (on) com.music.aalap.ui.theme.TextPrimary else com.music.aalap.ui.theme.TextMuted,
                    fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = on,
                    onCheckedChange = { HomeSections.set(context, key, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = com.music.aalap.ui.theme.Saffron),
                )
            }
        } }
        if (order != HomeSections.defaultOrder()) Text(
            "Reset order", color = com.music.aalap.ui.theme.Saffron, fontSize = 13.sp,
            modifier = Modifier.clickable { HomeSections.resetOrder(context) }.padding(vertical = 10.dp),
        )
    }
}

/** "Edit Home" from the bottom of Home: the same switches in a dialog. */
@Composable
fun HomeSectionsDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.music.aalap.ui.theme.Surface,
        title = { Text("Sections on Home", color = com.music.aalap.ui.theme.TextPrimary) },
        // The list scrolls inside a capped height, so Done always stays on screen.
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) { HomeSectionsSetting() } },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done", color = com.music.aalap.ui.theme.Saffron) }
        },
    )
}

