package com.music.spotui.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Twenty more crafts for the Aaj ka Aalap poster (see PosterDesigns.kt). Each paints
// its art on the right and then the solid panel the words sit on.

/** Calls [block] for each cell of a grid laid over the art (odd rows shifted by [shift]). */
private inline fun DrawScope.tiles(step: Float, stepY: Float = step, shift: Float = 0f, block: (Offset, Int, Int) -> Unit) {
    var j = 0
    var y = stepY / 2f
    while (y < size.height + stepY) {
        var i = 0
        var x = size.width * 0.5f + step / 2f + if (j % 2 == 1) shift else 0f
        while (x < size.width + step) { block(Offset(x, y), i, j); x += step; i++ }
        y += stepY; j++
    }
}

private fun dashes(on: Float, off: Float) = PathEffect.dashPathEffect(floatArrayOf(on, off))

/** Japanese shibori tie-dye: soft white rings on indigo. */
private val Shibori = PosterDesign("Shibori", "Japan", Color(0xFF0E1B33)) {
    val h = size.height
    drawRect(Color(0xFF23407A))
    artArea {
        tiles(h * 0.34f, shift = h * 0.17f) { c, _, _ ->
            for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.55f - k * 0.1f), h * 0.035f * k, c, style = Stroke(h * 0.012f))
            for (a in 0 until 8) rotate(a * 45f, c) {
                drawLine(Color.White.copy(alpha = 0.18f), c, Offset(c.x, c.y - h * 0.16f), h * 0.006f)
            }
        }
    }
    panel(Color(0xFF0E1B33), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.3f))
}

/** Malian mudcloth (bògòlanfini): black marks painted on cream cotton. */
private val Bogolan = PosterDesign("Mudcloth", "Mali", Color(0xFF2A1C12)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFE9DCC4))
    val ink = Color(0xFF1E1610)
    val band = h / 5f
    artArea {
        for (j in 0 until 5) {
            val y = band * j + band / 2
            drawLine(ink, Offset(w * 0.5f, band * j), Offset(w, band * j), h * 0.008f)
            var x = w * 0.54f
            while (x < w) {
                when (j % 3) {
                    0 -> { drawLine(ink, Offset(x - band * 0.15f, y), Offset(x + band * 0.15f, y), h * 0.012f); drawLine(ink, Offset(x, y - band * 0.15f), Offset(x, y + band * 0.15f), h * 0.012f) }
                    1 -> drawCircle(ink, h * 0.016f, Offset(x, y))
                    else -> drawPath(Path().apply { moveTo(x - band * 0.2f, y + band * 0.15f); lineTo(x, y - band * 0.15f); lineTo(x + band * 0.2f, y + band * 0.15f) }, ink, style = Stroke(h * 0.012f))
                }
                x += band * 0.55f
            }
        }
    }
    panel(Color(0xFF2A1C12), Edge.STEP)
    hem(Color(0xFFE9DCC4).copy(alpha = 0.4f))
}

/** Phulkari embroidery from Punjab: golden silk diamonds on red. */
private val Phulkari = PosterDesign("Phulkari", "India", Color(0xFF7A0E1E)) {
    val h = size.height
    drawRect(Color(0xFFB3182D))
    val gold = Color(0xFFF2C14E)
    artArea {
        tiles(h * 0.3f, h * 0.3f, shift = h * 0.15f) { c, _, _ ->
            for (k in 4 downTo 1) {
                val r = h * 0.035f * k
                drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() },
                    if (k % 2 == 0) gold else Color(0xFFFFE29A), style = if (k == 1) androidx.compose.ui.graphics.drawscope.Fill else Stroke(h * 0.012f))
            }
        }
    }
    panel(Color(0xFF7A0E1E), Edge.SLANT)
    hem(gold.copy(alpha = 0.6f))
}

/** Kalamkari, pen-drawn and block-dyed: a tree of life. */
private val Kalamkari = PosterDesign("Kalamkari", "India", Color(0xFF3B2414)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFEFE0C4))
    val brown = Color(0xFF6B3E1F); val madder = Color(0xFFA8322A); val indigo = Color(0xFF2F4A73); val ochre = Color(0xFFD49A2A)
    val base = Offset(w * 0.8f, h * 0.98f)
    artArea {
        drawPath(Path().apply { moveTo(base.x - h * 0.03f, base.y); cubicTo(base.x - h * 0.05f, h * 0.6f, base.x + h * 0.04f, h * 0.4f, base.x, h * 0.14f); lineTo(base.x + h * 0.03f, h * 0.14f); cubicTo(base.x + h * 0.07f, h * 0.4f, base.x - h * 0.01f, h * 0.6f, base.x + h * 0.03f, base.y); close() }, brown)
        val branches = listOf(Triple(0.3f, -1f, madder), Triple(0.42f, 1f, indigo), Triple(0.56f, -1f, indigo), Triple(0.68f, 1f, madder), Triple(0.22f, 1f, ochre))
        for ((fy, dir, col) in branches) {
            val from = Offset(base.x, h * fy)
            val to = Offset(base.x + dir * h * 0.26f, h * (fy - 0.1f))
            drawLine(brown, from, to, h * 0.012f, cap = StrokeCap.Round)
            rotate(if (dir > 0) 30f else -30f, to) { drawOval(col, Offset(to.x - h * 0.05f, to.y - h * 0.09f), Size(h * 0.1f, h * 0.18f)) }
            drawCircle(ochre, h * 0.022f, Offset((from.x + to.x) / 2, (from.y + to.y) / 2 + h * 0.03f))
        }
        drawCircle(madder, h * 0.06f, Offset(base.x + h * 0.015f, h * 0.1f))
    }
    panel(Color(0xFF3B2414), Edge.ARCH)
    hem(ochre.copy(alpha = 0.6f))
}

/** Gond painting from central India: leaves filled with lines and dots. */
private val Gond = PosterDesign("Gond", "India", Color(0xFF1A1A2E)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2B2B52))
    val colors = listOf(Color(0xFFFFD166), Color(0xFFEF476F), Color(0xFF06D6A0), Color(0xFF4CC9F0))
    artArea {
        for ((k, spec) in listOf(Triple(0.8f, 0.5f, 0f), Triple(0.64f, 0.32f, -40f), Triple(0.96f, 0.3f, 40f), Triple(0.66f, 0.8f, -110f), Triple(0.94f, 0.8f, 110f)).withIndex()) {
            val (fx, fy, a) = spec
            val c = Offset(w * fx, h * fy); val L = h * if (k == 0) 0.42f else 0.26f
            rotate(a, c) {
                val shape = Path().apply { moveTo(c.x, c.y - L / 2); cubicTo(c.x + L * 0.3f, c.y - L * 0.2f, c.x + L * 0.3f, c.y + L * 0.2f, c.x, c.y + L / 2); cubicTo(c.x - L * 0.3f, c.y + L * 0.2f, c.x - L * 0.3f, c.y - L * 0.2f, c.x, c.y - L / 2) }
                drawPath(shape, colors[k % colors.size])
                var y = c.y - L * 0.38f
                while (y < c.y + L * 0.4f) { drawLine(Color(0xFF1A1A2E), Offset(c.x - L * 0.16f, y), Offset(c.x + L * 0.16f, y), h * 0.005f); y += L * 0.07f }
                drawLine(Color(0xFF1A1A2E), Offset(c.x, c.y - L / 2), Offset(c.x, c.y + L / 2), h * 0.008f)
                drawPath(shape, Color.White, style = Stroke(h * 0.008f, pathEffect = dashes(0.1f, h * 0.022f), cap = StrokeCap.Round))
            }
        }
    }
    panel(Color(0xFF1A1A2E), Edge.SLANT)
    hem(Color(0xFFFFD166).copy(alpha = 0.5f))
}

/** Ajrakh block printing from Kutch and Sindh: indigo stars on madder red. */
private val Ajrakh = PosterDesign("Ajrakh", "India", Color(0xFF1B2440)) {
    val h = size.height
    drawRect(Color(0xFF8E1B1B))
    val indigo = Color(0xFF1F2A55)
    artArea {
        tiles(h * 0.25f) { c, _, _ ->
            val r = h * 0.1f
            for (a in listOf(0f, 45f)) rotate(a, c) { drawRect(indigo, Offset(c.x - r * 0.7f, c.y - r * 0.7f), Size(r * 1.4f, r * 1.4f)) }
            drawCircle(Color(0xFF8E1B1B), r * 0.45f, c)
            for (k in 0 until 8) {
                val a = k * PI / 4
                drawCircle(Color.White, h * 0.008f, Offset(c.x + cos(a).toFloat() * r * 0.3f, c.y + sin(a).toFloat() * r * 0.3f))
            }
            drawCircle(Color.White, h * 0.012f, c)
        }
    }
    panel(Color(0xFF1B2440), Edge.STEP)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Bandhani tie-dye from Gujarat and Rajasthan: little dots tied in diamond rows. */
private val Bandhani = PosterDesign("Bandhani", "India", Color(0xFF6B0F2A)) {
    val h = size.height
    drawRect(Color(0xFFC2185B))
    artArea {
        tiles(h * 0.16f, h * 0.16f, shift = h * 0.08f) { c, i, j ->
            val d = h * 0.018f
            val col = if ((i + j) % 4 == 0) Color(0xFFFFD54F) else Color(0xFFFFF3E0)
            for ((dx, dy) in listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)) drawCircle(col, h * 0.011f, Offset(c.x + dx * d, c.y + dy * d))
        }
    }
    panel(Color(0xFF6B0F2A), Edge.WAVE)
    hem(Color(0xFFFFF3E0).copy(alpha = 0.45f))
}

/** Mola from the Guna people of Panama: layered cut-cloth outlines. */
private val Mola = PosterDesign("Mola", "Panama", Color(0xFF1B1B3A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFE63946))
    val layers = listOf(Color(0xFF111111), Color(0xFFF4A261), Color(0xFF111111), Color(0xFF2A9D8F), Color(0xFF111111), Color(0xFFFFD166))
    artArea {
        for ((cx, cy, r) in listOf(Triple(0.8f, 0.5f, 0.42f), Triple(0.58f, 0.15f, 0.18f), Triple(0.6f, 0.86f, 0.16f))) {
            layers.forEachIndexed { k, col ->
                val rr = h * r * (1f - k * 0.15f)
                drawRoundRect(col, Offset(w * cx - rr, h * cy - rr * 0.8f), Size(rr * 2, rr * 1.6f), CornerRadius(rr * 0.6f))
            }
        }
    }
    panel(Color(0xFF1B1B3A), Edge.STEP)
    hem(Color(0xFFFFD166).copy(alpha = 0.5f))
}

/** Navajo (Diné) weaving: a terraced diamond with zigzag bands. */
private val Navajo = PosterDesign("Navajo weaving", "USA", Color(0xFF3A1F14)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFE8DCC8))
    val red = Color(0xFFA4312A); val black = Color(0xFF2B2420); val grey = Color(0xFF8C8C8C)
    val c = Offset(w * 0.8f, h * 0.5f)
    artArea {
        val steps = 5; val st = h * 0.055f
        for (k in steps downTo 1) {
            val col = if (k % 2 == 0) black else red
            for (s in 0 until k) {
                val halfW = st * (k - s) * 1.2f
                drawRect(col, Offset(c.x - halfW, c.y - st * (s + 1)), Size(halfW * 2, st))
                drawRect(col, Offset(c.x - halfW, c.y + st * s), Size(halfW * 2, st))
            }
        }
        for (band in listOf(h * 0.08f, h * 0.92f)) {
            var x = w * 0.5f
            val path = Path().apply { moveTo(x, band) }
            var up = true
            while (x < w) { x += h * 0.05f; path.lineTo(x, band + if (up) -h * 0.035f else h * 0.035f); up = !up }
            drawPath(path, grey, style = Stroke(h * 0.014f))
        }
    }
    panel(Color(0xFF3A1F14), Edge.STRAIGHT)
    hem(red.copy(alpha = 0.6f))
}

/** Portuguese azulejo tiles: blue quatrefoils on white. */
private val Azulejo = PosterDesign("Azulejo", "Portugal", Color(0xFF0D2C5C)) {
    val h = size.height
    drawRect(Color(0xFFF5F7FA))
    val blue = Color(0xFF1B4F9C)
    val t = h * 0.34f
    artArea {
        tiles(t) { c, _, _ ->
            drawRect(blue.copy(alpha = 0.35f), Offset(c.x - t / 2, c.y - t / 2), Size(t, t), style = Stroke(h * 0.005f))
            for ((dx, dy) in listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)) drawCircle(blue, t * 0.17f, Offset(c.x + dx * t * 0.16f, c.y + dy * t * 0.16f), style = Stroke(h * 0.012f))
            drawCircle(blue, t * 0.08f, c)
            drawLine(blue.copy(alpha = 0.5f), Offset(c.x - t / 2, c.y - t / 2), Offset(c.x - t * 0.32f, c.y - t * 0.32f), h * 0.008f)
            drawLine(blue.copy(alpha = 0.5f), Offset(c.x + t / 2, c.y + t / 2), Offset(c.x + t * 0.32f, c.y + t * 0.32f), h * 0.008f)
        }
    }
    panel(Color(0xFF0D2C5C), Edge.ARCH)
    hem(Color.White.copy(alpha = 0.45f))
}

/** Fair Isle knitting from Shetland: stitched rows of small motifs. */
private val FairIsle = PosterDesign("Fair Isle", "Scotland", Color(0xFF1F2A33)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFE6E1D6))
    val p = h * 0.04f
    val navy = Color(0xFF1F3A5F); val red = Color(0xFFB23A48); val mustard = Color(0xFFD4A017)
    artArea {
        val rows = (h / p).toInt() + 1
        val cols = ((w * 0.5f) / p).toInt() + 2
        for (r in 0 until rows) for (k in 0 until cols) {
            val x = w * 0.5f + k * p; val y = r * p
            val band = r % 8
            val on = when (band) {
                0, 7 -> k % 2 == 0
                1, 6 -> false
                2, 5 -> (k % 6 == 2 || k % 6 == 3)
                else -> (k % 6) in 1..4
            }
            if (on) drawRect(when (band) { 0, 7 -> navy; 2, 5 -> red; else -> mustard }, Offset(x + p * 0.1f, y + p * 0.1f), Size(p * 0.8f, p * 0.8f))
        }
    }
    panel(Color(0xFF1F2A33), Edge.STEP)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Kuba cloth from Congo: maze-like lines on raffia. */
private val Kuba = PosterDesign("Kuba cloth", "Congo", Color(0xFF2B1D10)) {
    val h = size.height
    drawRect(Color(0xFFC8A876))
    val ink = Color(0xFF2B1D10)
    artArea {
        tiles(h * 0.25f) { c, i, j ->
            val r = h * 0.11f
            if ((i + j) % 2 == 0) for (k in 1..3) drawRect(ink, Offset(c.x - r * k / 3, c.y - r * k / 3), Size(r * 2 * k / 3, r * 2 * k / 3), style = Stroke(h * 0.01f))
            else rotate(45f, c) { for (k in 1..2) drawRect(ink, Offset(c.x - r * k / 2.4f, c.y - r * k / 2.4f), Size(r * 2 * k / 2.4f, r * 2 * k / 2.4f), style = Stroke(h * 0.01f)) }
        }
    }
    panel(Color(0xFF2B1D10), Edge.STEP)
    hem(Color(0xFFC8A876).copy(alpha = 0.5f))
}

/** Ndebele house painting from South Africa: bright blocks in bold black lines. */
private val Ndebele = PosterDesign("Ndebele", "South Africa", Color(0xFF111111)) {
    val w = size.width; val h = size.height
    drawRect(Color.White)
    val black = Color(0xFF111111)
    val fills = listOf(Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFFE53935), Color(0xFF43A047))
    artArea {
        val cw = w * 0.125f
        for (k in 0 until 4) {
            val x = w * 0.5f + k * cw
            drawRect(fills[k], Offset(x, 0f), Size(cw, h))
            // Stepped notches, outlined in black.
            for (s in 0 until 4) {
                val y = h * (0.12f + s * 0.22f)
                drawRect(Color.White, Offset(x + cw * 0.25f, y), Size(cw * 0.5f, h * 0.12f))
                drawRect(black, Offset(x + cw * 0.25f, y), Size(cw * 0.5f, h * 0.12f), style = Stroke(h * 0.014f))
            }
            drawLine(black, Offset(x, 0f), Offset(x, h), h * 0.02f)
        }
    }
    panel(Color(0xFF111111), Edge.STRAIGHT)
    hem(Color.White.copy(alpha = 0.4f))
}

/** A Persian carpet: a layered medallion with an ivory border. */
private val PersianCarpet = PosterDesign("Persian carpet", "Iran", Color(0xFF14213D)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF8B1E2D))
    val navy = Color(0xFF14213D); val ivory = Color(0xFFEFE3C8); val gold = Color(0xFFD4A24C)
    val c = Offset(w * 0.8f, h * 0.5f)
    artArea {
        fun lozenge(rx: Float, ry: Float, col: Color) = drawPath(Path().apply { moveTo(c.x, c.y - ry); quadraticBezierTo(c.x + rx * 0.7f, c.y - ry * 0.3f, c.x + rx, c.y); quadraticBezierTo(c.x + rx * 0.7f, c.y + ry * 0.3f, c.x, c.y + ry); quadraticBezierTo(c.x - rx * 0.7f, c.y + ry * 0.3f, c.x - rx, c.y); quadraticBezierTo(c.x - rx * 0.7f, c.y - ry * 0.3f, c.x, c.y - ry); close() }, col)
        lozenge(h * 0.36f, h * 0.44f, ivory)
        lozenge(h * 0.3f, h * 0.37f, navy)
        lozenge(h * 0.18f, h * 0.22f, Color(0xFF8B1E2D))
        lozenge(h * 0.08f, h * 0.1f, gold)
        for (k in 0 until 8) rotate(k * 45f, c) { drawCircle(gold, h * 0.014f, Offset(c.x, c.y - h * 0.27f)) }
        drawLine(ivory, Offset(w - h * 0.06f, 0f), Offset(w - h * 0.06f, h), h * 0.02f)
        drawLine(gold, Offset(w - h * 0.1f, 0f), Offset(w - h * 0.1f, h), h * 0.008f, pathEffect = dashes(h * 0.02f, h * 0.02f))
    }
    panel(Color(0xFF14213D), Edge.SLANT)
    hem(gold.copy(alpha = 0.55f))
}

/** Andean weaving from Peru: stepped crosses and a llama between bright bands. */
private val Andean = PosterDesign("Andean weaving", "Peru", Color(0xFF2A1A3A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFD94F30))
    val cream = Color(0xFFF7E7C6); val yellow = Color(0xFFF2B705); val purple = Color(0xFF5B2A86)
    artArea {
        for ((y, col) in listOf(h * 0.06f to purple, h * 0.94f to purple, h * 0.36f to yellow, h * 0.66f to yellow)) drawRect(col, Offset(w * 0.5f, y - h * 0.02f), Size(w * 0.5f, h * 0.04f))
        // Stepped crosses in the upper band.
        var x = w * 0.58f
        while (x < w) {
            val c = Offset(x, h * 0.2f); val s = h * 0.03f
            drawRect(cream, Offset(c.x - s * 3, c.y - s), Size(s * 6, s * 2)); drawRect(cream, Offset(c.x - s, c.y - s * 3), Size(s * 2, s * 6))
            drawRect(purple, Offset(c.x - s * 0.6f, c.y - s * 0.6f), Size(s * 1.2f, s * 1.2f))
            x += h * 0.24f
        }
        // Llamas in the lower band.
        x = w * 0.6f
        while (x < w) {
            val b = Offset(x, h * 0.84f); val s = h * 0.1f
            drawRect(cream, Offset(b.x - s * 0.5f, b.y - s * 0.3f), Size(s, s * 0.35f))
            drawRect(cream, Offset(b.x + s * 0.32f, b.y - s * 0.9f), Size(s * 0.18f, s * 0.65f))
            drawRect(cream, Offset(b.x + s * 0.32f, b.y - s * 0.95f), Size(s * 0.3f, s * 0.14f))
            for (lx in listOf(-0.45f, -0.2f, 0.2f, 0.4f)) drawRect(cream, Offset(b.x + s * lx, b.y + s * 0.05f), Size(s * 0.08f, s * 0.3f))
            x += h * 0.3f
        }
        x = w * 0.55f
        while (x < w) { drawCircle(cream, h * 0.02f, Offset(x, h * 0.51f)); x += h * 0.08f }
    }
    panel(Color(0xFF2A1A3A), Edge.STEP)
    hem(yellow.copy(alpha = 0.55f))
}

internal val MoreDesigns: List<PosterDesign> = listOf(
    Shibori, Phulkari, Bogolan, Kalamkari, Azulejo, Kuba, Gond, FairIsle,
    Ajrakh, Mola, Bandhani, Navajo, Ndebele, PersianCarpet, Andean,
)
