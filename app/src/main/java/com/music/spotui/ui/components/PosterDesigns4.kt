package com.music.spotui.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Twelve more micro designs for the Aaj ka Aalap poster, all geometric (no flowers):
// inlay, lattice, weave and stitch patterns at a fine scale.

private inline fun DrawScope.cells(step: Float, stepY: Float = step, shift: Float = 0f, block: (Offset, Int, Int) -> Unit) {
    var j = 0
    var y = stepY / 2f
    while (y < size.height + stepY) {
        var i = 0
        var x = size.width * 0.5f + step / 2f + if (j % 2 == 1) shift else 0f
        while (x < size.width + step) { block(Offset(x, y), i, j); x += step; i++ }
        y += stepY; j++
    }
}

private fun DrawScope.hexagon(c: Offset, r: Float, color: Color, stroke: Float? = null) {
    val p = Path()
    for (k in 0..5) {
        val a = PI / 3 * k + PI / 6
        val pt = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
        if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
    }
    p.close()
    if (stroke == null) drawPath(p, color) else drawPath(p, color, style = Stroke(stroke))
}

/** Sadeli marquetry from Surat: tiny hexagons of ivory, ebony and sandalwood. */
private val Sadeli = PosterDesign("Sadeli marquetry", "India", Color(0xFF2A1A10)) {
    val h = size.height
    drawRect(Color(0xFF3B2416))
    val woods = listOf(Color(0xFFEFE2C8), Color(0xFFB07A45), Color(0xFF1E1410))
    val r = h * 0.035f
    artArea {
        cells(r * 1.732f, r * 1.5f, shift = r * 0.866f) { c, i, j ->
            hexagon(c, r * 0.95f, woods[(i + j * 2) % 3])
            drawCircle(Color(0xFFD4A24C), h * 0.004f, c)
        }
    }
    panel(Color(0xFF2A1A10), Edge.STEP)
    hem(Color(0xFFD4A24C).copy(alpha = 0.5f))
}

/** Bidriware from Bidar: fine silver inlay chevrons on blackened metal. */
private val Bidri = PosterDesign("Bidriware", "India", Color(0xFF121212)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF1E1E1E))
    val silver = Color(0xFFD9DDE2)
    artArea {
        val row = h * 0.05f
        var y = 0f; var k = 0
        while (y < h + row) {
            var x = w * 0.5f
            val p = Path().apply { moveTo(x, y) }
            while (x < w) { p.lineTo(x + row * 0.5f, y + if (k % 2 == 0) row * 0.5f else -row * 0.5f); p.lineTo(x + row, y); x += row }
            drawPath(p, silver.copy(alpha = if (k % 3 == 0) 0.95f else 0.55f), style = Stroke(h * 0.004f))
            y += row; k++
        }
        drawLine(silver, Offset(w * 0.97f, 0f), Offset(w * 0.97f, h), h * 0.008f)
    }
    panel(Color(0xFF121212), Edge.STRAIGHT)
    hem(silver.copy(alpha = 0.4f))
}

/** Khatamband ceilings from Kashmir: interlocking wooden polygons. */
private val Khatamband = PosterDesign("Khatamband", "India", Color(0xFF2E1F12)) {
    val h = size.height
    drawRect(Color(0xFF9C6B3C))
    val groove = Color(0xFF4A3018)
    val s = h * 0.11f
    artArea {
        cells(s) { c, _, _ ->
            rotate(45f, c) { drawRect(groove, Offset(c.x - s * 0.28f, c.y - s * 0.28f), Size(s * 0.56f, s * 0.56f), style = Stroke(h * 0.005f)) }
            drawRect(groove, Offset(c.x - s * 0.5f, c.y - s * 0.5f), Size(s, s), style = Stroke(h * 0.004f))
            drawCircle(Color(0xFFE2B77E), h * 0.006f, c)
        }
    }
    panel(Color(0xFF2E1F12), Edge.ARCH)
    hem(Color(0xFFE2B77E).copy(alpha = 0.5f))
}

/** Patola double ikat from Patan: a fine grid of stepped diamonds. */
private val Patola = PosterDesign("Patola", "India", Color(0xFF3B0A14)) {
    val h = size.height
    drawRect(Color(0xFF9E1B32))
    val ink = listOf(Color(0xFF111111), Color(0xFFF2C14E), Color(0xFFF7F0E1))
    val p = h * 0.016f
    artArea {
        cells(h * 0.12f) { c, i, j ->
            // A stepped diamond made of little squares.
            for (dy in -3..3) {
                val span = 3 - kotlin.math.abs(dy)
                for (dx in -span..span) {
                    val col = if (kotlin.math.abs(dx) == span) ink[0] else ink[1 + (i + j) % 2]
                    drawRect(col, Offset(c.x + dx * p - p / 2, c.y + dy * p - p / 2), Size(p * 0.9f, p * 0.9f))
                }
            }
        }
    }
    panel(Color(0xFF3B0A14), Edge.STEP)
    hem(Color(0xFFF2C14E).copy(alpha = 0.5f))
}

/** Harris Tweed from the Outer Hebrides: a fine herringbone weave. */
private val HarrisTweed = PosterDesign("Harris Tweed", "Scotland", Color(0xFF1F2A24)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF55624F))
    val light = Color(0xFFC9C2A8); val dark = Color(0xFF2F382C)
    artArea {
        val col = h * 0.06f; val dash = h * 0.03f
        var x = w * 0.5f; var k = 0
        while (x < w + col) {
            var y = 0f
            while (y < h) {
                val dir = if (k % 2 == 0) 1f else -1f
                drawLine(light, Offset(x, y), Offset(x + col * dir * 0.8f, y + dash), h * 0.006f, cap = StrokeCap.Round)
                drawLine(dark, Offset(x, y + dash * 0.5f), Offset(x + col * dir * 0.8f, y + dash * 1.5f), h * 0.004f, cap = StrokeCap.Round)
                y += dash
            }
            x += col; k++
        }
    }
    panel(Color(0xFF1F2A24), Edge.STRAIGHT)
    hem(light.copy(alpha = 0.45f))
}

/** Houndstooth, the Scottish wool check: broken four-point checks. */
private val Houndstooth = PosterDesign("Houndstooth", "Scotland", Color(0xFF111111)) {
    val h = size.height
    drawRect(Color(0xFFF2EEE6))
    val ink = Color(0xFF1A1A1A)
    val s = h * 0.07f
    artArea {
        cells(s) { c, _, _ ->
            val x = c.x - s / 2; val y = c.y - s / 2; val q = s / 4
            drawPath(Path().apply {
                moveTo(x, y + q * 2); lineTo(x + q * 2, y); lineTo(x + q * 3, y); lineTo(x + q * 2, y + q)
                lineTo(x + q * 2, y + q * 2); lineTo(x + q * 4, y + q * 2); lineTo(x + q * 2, y + q * 4)
                lineTo(x + q * 2, y + q * 3); lineTo(x + q, y + q * 4); lineTo(x, y + q * 4); close()
            }, ink)
        }
    }
    panel(Color(0xFF111111), Edge.SLANT)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Hitomezashi sashiko: one-stitch running lines that build a fine maze. */
private val Hitomezashi = PosterDesign("Hitomezashi", "Japan", Color(0xFF0F1A2E)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF203A63))
    val thread = Color(0xFFF1F4F8)
    val u = h * 0.045f
    artArea {
        var row = 0
        var y = u
        while (y < h) {
            var x = w * 0.5f + if (row % 2 == 0) 0f else u
            while (x < w) { drawLine(thread, Offset(x, y), Offset(x + u, y), h * 0.005f, cap = StrokeCap.Round); x += u * 2 }
            y += u; row++
        }
        var col = 0
        var x = w * 0.5f
        while (x < w) {
            var yy = if ((col * 7) % 3 == 0) 0f else u
            while (yy < h) { drawLine(thread.copy(alpha = 0.8f), Offset(x, yy), Offset(x, yy + u), h * 0.005f, cap = StrokeCap.Round); yy += u * 2 }
            x += u; col++
        }
    }
    panel(Color(0xFF0F1A2E), Edge.WAVE)
    hem(thread.copy(alpha = 0.35f))
}

/** Mashrabiya from Cairo: a turned-wood lattice of beads and spindles. */
private val Mashrabiya = PosterDesign("Mashrabiya", "Egypt", Color(0xFF2A1B12)) {
    val h = size.height
    drawRect(Color(0xFFF0DDB8))
    val wood = Color(0xFF5A3A22)
    val s = h * 0.08f
    artArea {
        cells(s) { c, _, _ ->
            drawLine(wood, Offset(c.x - s / 2, c.y), Offset(c.x + s / 2, c.y), h * 0.007f)
            drawLine(wood, Offset(c.x, c.y - s / 2), Offset(c.x, c.y + s / 2), h * 0.007f)
            drawCircle(wood, s * 0.16f, c)
            for ((dx, dy) in listOf(0.5f to 0f, -0.5f to 0f, 0f to 0.5f, 0f to -0.5f)) drawCircle(wood, s * 0.08f, Offset(c.x + dx * s * 0.6f, c.y + dy * s * 0.6f))
        }
    }
    panel(Color(0xFF2A1B12), Edge.ARCH)
    hem(Color(0xFFF0DDB8).copy(alpha = 0.45f))
}

/** Anatolian kilim: small stepped triangles in woven bands. */
private val Kilim = PosterDesign("Kilim", "Türkiye", Color(0xFF2B1320)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFB5452F))
    val bands = listOf(Color(0xFF1F3B5A), Color(0xFFF2D7A6), Color(0xFF2B1320), Color(0xFFD9A441))
    artArea {
        val row = h * 0.09f; val p = row / 4
        var y = 0f; var k = 0
        while (y < h) {
            val col = bands[k % bands.size]
            var x = w * 0.5f
            while (x < w) {
                for (s in 0 until 4) drawRect(col, Offset(x + s * p * 0.5f, y + row - (s + 1) * p), Size(row - s * p, p))
                x += row
            }
            y += row; k++
        }
    }
    panel(Color(0xFF2B1320), Edge.STEP)
    hem(Color(0xFFF2D7A6).copy(alpha = 0.5f))
}

/** Cane (rattan) weave: the fine octagonal mesh of a chair seat. */
private val CaneWeave = PosterDesign("Cane weave", "Indonesia", Color(0xFF2E2212)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF3A2C18))
    val cane = Color(0xFFE3C48A)
    val s = h * 0.07f
    artArea {
        var x = w * 0.5f
        while (x < w) { drawLine(cane, Offset(x, 0f), Offset(x, h), h * 0.006f); x += s }
        var y = 0f
        while (y < h) { drawLine(cane, Offset(w * 0.5f, y), Offset(w, y), h * 0.006f); y += s }
        x = w * 0.5f - h
        while (x < w + h) {
            drawLine(cane.copy(alpha = 0.75f), Offset(x, 0f), Offset(x + h, h), h * 0.005f)
            drawLine(cane.copy(alpha = 0.75f), Offset(x + h, 0f), Offset(x, h), h * 0.005f)
            x += s
        }
        cells(s) { c, _, _ -> drawCircle(Color(0xFF3A2C18), s * 0.22f, Offset(c.x - s / 2, c.y - s / 2)) }
    }
    panel(Color(0xFF2E2212), Edge.STRAIGHT)
    hem(cane.copy(alpha = 0.45f))
}

/** Maasai beadwork from Kenya: rows of tiny beads in bold bands. */
private val Maasai = PosterDesign("Maasai beadwork", "Kenya", Color(0xFF1A1A1A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF1A1A1A))
    val beads = listOf(Color(0xFFD62828), Color(0xFFF7F7F7), Color(0xFF1D70B8), Color(0xFFF4A300), Color(0xFF2A9D3C))
    val b = h * 0.024f
    artArea {
        var y = b; var row = 0
        while (y < h) {
            var x = w * 0.5f + if (row % 2 == 0) 0f else b
            var k = 0
            while (x < w) {
                val band = (row / 3) % beads.size
                val col = if ((k + row) % 6 == 0) beads[(band + 2) % beads.size] else beads[band]
                drawCircle(col, b * 0.85f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.35f), b * 0.25f, Offset(x - b * 0.25f, y - b * 0.25f))
                x += b * 2; k++
            }
            y += b * 1.75f; row++
        }
    }
    panel(Color(0xFF1A1A1A), Edge.WAVE)
    hem(Color(0xFFF4A300).copy(alpha = 0.45f))
}

/** Zulu basketry from KwaZulu-Natal: a tight coil with small triangles. */
private val ZuluBasket = PosterDesign("Zulu basketry", "South Africa", Color(0xFF2B1A0E)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFD8B47A))
    val dark = Color(0xFF3B2414); val rust = Color(0xFFA0472A)
    val c = Offset(w * 0.8f, h * 0.5f)
    artArea {
        var r = h * 0.04f; var k = 0
        while (r < h * 0.9f) {
            drawCircle(dark.copy(alpha = 0.35f), r, c, style = Stroke(h * 0.004f))
            val n = (r / (h * 0.03f)).toInt().coerceAtLeast(6)
            if (k % 2 == 1) for (t in 0 until n) {
                val a = 2 * PI * t / n
                val p = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
                rotate((a * 180 / PI).toFloat() + 90f, p) {
                    drawPath(Path().apply { moveTo(p.x - h * 0.012f, p.y + h * 0.01f); lineTo(p.x, p.y - h * 0.012f); lineTo(p.x + h * 0.012f, p.y + h * 0.01f); close() }, if (t % 2 == 0) dark else rust)
                }
            }
            r += h * 0.04f; k++
        }
    }
    panel(Color(0xFF2B1A0E), Edge.SLANT)
    hem(Color(0xFFD8B47A).copy(alpha = 0.5f))
}

internal val GeometricMicroDesigns: List<PosterDesign> = listOf(
    Sadeli, Houndstooth, Bidri, Hitomezashi, Khatamband, Kilim, Patola, Mashrabiya, HarrisTweed, Maasai, CaneWeave, ZuluBasket,
)
