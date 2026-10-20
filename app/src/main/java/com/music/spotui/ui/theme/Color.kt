package com.music.spotui.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ── Aalap "Saffron dusk" brand palette ──
// Saffron accent on a deep indigo night (dark) or a warm cream page (light).
// Every token below is a getter backed by Compose state, so reading one inside a
// composable subscribes it: flipping [AalapPalette.isLight] recomposes the whole
// UI with the other palette, with no per-screen theme plumbing.

object AalapPalette {
    /** Set by [SpotuiTheme] from the user's choice / the system dark-mode setting. */
    var isLight by mutableStateOf(false)
}

private fun pick(dark: Long, light: Long): Color =
    Color(if (AalapPalette.isLight) light else dark)

/** Primary accent: active states, liked/downloaded tints, selected pills, sliders. */
val Saffron: Color get() = pick(0xFFFF8A3D, 0xFFD9600F)
/** Lighter gold companion to [Saffron]; logo highlight and secondary accents. */
val SaffronGold: Color get() = pick(0xFFFFC56B, 0xFFB86E00)
/** Deep saffron for accents drawn on light backgrounds (logo on white). */
val SaffronDeep = Color(0xFFE0661B)

/** App canvas: indigo-black (dark) / warm cream (light). */
val AppBackground: Color get() = pick(0xFF100B1C, 0xFFFFF8F1)
/** Raised surface: cards, sheets, list rows, the floating bars. */
val Surface: Color get() = pick(0xFF1C1530, 0xFFFFFFFF)
/** Higher surface: unselected pills, chips, grid tiles. */
val SurfaceHigh: Color get() = pick(0xFF2A2140, 0xFFF3E6D9)
/** Primary text and icons on the canvas. */
val TextPrimary: Color get() = pick(0xFFFFFFFF, 0xFF1A1226)
/** Secondary text on the canvas. */
val TextMuted: Color get() = pick(0xFFB8AFC9, 0xFF6E6280)
/** Text and icons drawn on a saffron fill. */
val OnSaffron: Color get() = pick(0xFF1A0E05, 0xFFFFFFFF)
/** Hairline borders around floating surfaces. */
val Hairline: Color get() = pick(0x22FFFFFF, 0x1A1A1226)

val GridBackground: Color get() = SurfaceHigh
/** Legacy name kept so existing call sites pick up the brand accent. */
val AppPalette: Color get() = Saffron

/** Fixed night canvas for immersive screens (player, lyrics) that keep white text
 *  over artwork colours in both themes. */
val ImmersiveBackground = Color(0xFF100B1C)
