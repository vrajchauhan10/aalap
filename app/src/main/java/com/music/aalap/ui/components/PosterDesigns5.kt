package com.music.aalap.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Thirty more crafts for the Aaj ka Aalap poster (4.0). Like the others, each one has
// the maker's V worked into it: some of its own pieces recoloured or set along a V.

private inline fun DrawScope.grid(step: Float, stepY: Float = step, shift: Float = 0f, block: (Offset, Int, Int) -> Unit) {
    var j = 0
    var y = stepY / 2f
    while (y < size.height + stepY) {
        var i = 0
        var x = size.width * 0.5f + step / 2f + if (j % 2 == 1) shift else 0f
        while (x < size.width + step) { block(Offset(x, y), i, j); x += step; i++ }
        y += stepY; j++
    }
}

private fun dash(on: Float, off: Float) = PathEffect.dashPathEffect(floatArrayOf(on, off))

/** [a]–[b] as [n] + 1 points, for [traceV]. */
private fun line(a: Offset, b: Offset, n: Int = 12) = (0..n).map { Offset(a.x + (b.x - a.x) * it / n, a.y + (b.y - a.y) * it / n) }

private fun Path.poly(points: List<Offset>, closed: Boolean = true) = apply {
    moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; if (closed) close()
}

// ── Indian block prints and embroidery ─────────────────────────────────────

/** Paisley (boteh): curled teardrops; a V of them is printed in gold. */
private val Paisley = PosterDesign("Paisley", "India", Color(0xFF3A0E16)) {
    val h = size.height
    drawRect(Color(0xFF9E2A2B))
    val cream = Color(0xFFF4E3C1)
    artArea {
        val all = ArrayList<Offset>()
        grid(h * 0.2f, h * 0.17f, shift = h * 0.1f) { c, _, _ -> all += c }
        val gold = pickV(all, h * 0.2f, h * 0.34f, n = 2).map { all[it] }
        grid(h * 0.2f, h * 0.17f, shift = h * 0.1f) { c, _, _ ->
            val s = h * 0.15f
            val p = Path().apply {
                moveTo(c.x, c.y + s * 0.5f)
                cubicTo(c.x - s * 0.6f, c.y + s * 0.45f, c.x - s * 0.45f, c.y - s * 0.4f, c.x + s * 0.15f, c.y - s * 0.5f)
                cubicTo(c.x + s * 0.32f, c.y - s * 0.52f, c.x + s * 0.3f, c.y - s * 0.28f, c.x + s * 0.12f, c.y - s * 0.3f)
                cubicTo(c.x + s * 0.5f, c.y - s * 0.1f, c.x + s * 0.45f, c.y + s * 0.45f, c.x, c.y + s * 0.5f)
                close()
            }
            drawPath(p, if (gold.any { (it - c).getDistance() < 1f }) Color(0xFFE8B33A) else Color(0xFF1F6F6B))
            drawPath(p, cream, style = Stroke(h * 0.006f))
            drawCircle(cream, s * 0.09f, Offset(c.x - s * 0.02f, c.y + s * 0.18f))
        }
    }
    panel(Color(0xFF3A0E16), Edge.WAVE)
    hem(cream.copy(alpha = 0.5f))
}

/** Buti (boota): tiny dot sprigs scattered over indigo; a V of them in gold. */
private val Buti = PosterDesign("Buti", "India", Color(0xFF0B1E38)) {
    val h = size.height
    drawRect(Color(0xFF14325C))
    artArea {
        val v = gridV(h * 0.1f, shift = h * 0.05f, levels = 3)
        grid(h * 0.1f, shift = h * 0.05f) { c, _, _ ->
            val col = if (v.hit(c, h * 0.02f)) Color(0xFFF2C14E) else Color(0xFFF4F1EA)
            val big = if (col == Color(0xFFF2C14E)) 1.25f else 1f
            drawCircle(col, h * 0.01f * big, c)
            for (k in 0 until 4) {
                val a = k * PI / 2 + PI / 4
                drawCircle(col, h * 0.0055f * big, Offset(c.x + cos(a).toFloat() * h * 0.018f * big, c.y + sin(a).toFloat() * h * 0.018f * big))
            }
        }
    }
    panel(Color(0xFF0B1E38), Edge.ARCH)
    hem(Color(0xFFF2C14E).copy(alpha = 0.45f))
}

/** Ajrakh jaal: an ogee net of black lines on madder; where it crosses a V, it's white. */
private val AjrakhJaal = PosterDesign("Ajrakh jaal", "India", Color(0xFF1F2A55)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF9B1B1B))
    val ink = Color(0xFF1A1A1A); val P = h * 0.32f; val A = h * 0.045f; val gap = h * 0.16f
    val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
    artArea {
        var x0 = w * 0.5f
        while (x0 < w + gap) {
            for (sgn in listOf(1f, -1f)) {
                val pts = ArrayList<Offset>()
                var y = 0f
                while (y <= h) { pts += Offset(x0 + sgn * A * sin(2 * PI * y / P).toFloat(), y); y += h * 0.005f }
                drawPath(Path().poly(pts, closed = false), ink, style = Stroke(h * 0.01f))
                traceV(pts, v, h * 0.022f, Color(0xFFF7EFE3), h * 0.014f)
            }
            var y = 0f; var k = 0
            while (y <= h) {
                drawCircle(Color(0xFFF7EFE3), h * 0.008f, Offset(x0, y))
                drawCircle(Color(0xFF1F2A55), h * 0.016f, Offset(x0 + gap / 2, y + P / 4))
                y += P / 2; k++
            }
            x0 += gap
        }
    }
    panel(Color(0xFF1F2A55), Edge.STEP)
    hem(Color(0xFFF7EFE3).copy(alpha = 0.4f))
}

/** Sanganeri print: small sprigs on white; a V of them has indigo buds. */
private val Sanganeri = PosterDesign("Sanganeri", "India", Color(0xFF7A1F16)) {
    val h = size.height
    drawRect(Color(0xFFF6EFE2))
    val green = Color(0xFF4F7A3A); val red = Color(0xFFC0392B)
    artArea {
        val v = gridV(h * 0.13f, shift = h * 0.065f, levels = 2)
        grid(h * 0.13f, shift = h * 0.065f) { c, _, _ ->
            val s = h * 0.05f
            drawLine(green, Offset(c.x, c.y + s), Offset(c.x, c.y - s * 0.3f), h * 0.004f)
            for (side in listOf(-1f, 1f)) rotate(side * 50f, Offset(c.x, c.y + s * 0.4f)) {
                drawOval(green, Offset(c.x - s * 0.13f, c.y + s * 0.4f - s * 0.55f), Size(s * 0.26f, s * 0.55f))
            }
            drawCircle(if (v.hit(c, h * 0.03f)) Color(0xFF24477F) else red, s * 0.32f, Offset(c.x, c.y - s * 0.45f))
        }
    }
    panel(Color(0xFF7A1F16), Edge.SLANT)
    hem(Color(0xFFC0392B).copy(alpha = 0.4f))
}

/** Kantha from Bengal: rows of wavy running stitch; where they cross a V, the thread is red. */
private val KanthaWave = PosterDesign("Kantha", "India", Color(0xFF2B2A4C)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF2E8D5))
    val threads = listOf(Color(0xFF2B4C8C), Color(0xFF2E7D5B), Color(0xFF3A3A3A))
    val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
    artArea {
        var y0 = h * 0.02f; var k = 0
        while (y0 < h + h * 0.05f) {
            val pts = ArrayList<Offset>()
            var x = w * 0.5f
            while (x <= w) { pts += Offset(x, y0 + sin((x - w * 0.5f) / (h * 0.06f)) * h * 0.012f); x += h * 0.004f }
            drawPath(Path().poly(pts, closed = false), threads[k % threads.size], style = Stroke(h * 0.006f, cap = StrokeCap.Round, pathEffect = dash(h * 0.016f, h * 0.012f)))
            traceV(pts, v, h * 0.02f, Color(0xFFD62828), h * 0.012f)
            y0 += h * 0.045f; k++
        }
    }
    panel(Color(0xFF2B2A4C), Edge.WAVE)
    hem(Color(0xFF2B4C8C).copy(alpha = 0.4f))
}

/** A kantha quilt: diamond rings of running stitch round a lotus with a V at its heart. */
private val KanthaQuilt = PosterDesign("Nakshi kantha", "Bangladesh", Color(0xFF6B1E2B)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF4EBDD))
    val c = Offset(w * 0.82f, h * 0.5f)
    val threads = listOf(Color(0xFFC0392B), Color(0xFF2B4C8C), Color(0xFF2E7D5B), Color(0xFFD49A2A))
    artArea {
        var r = h * 0.11f; var k = 0
        while (r < h * 0.9f) {
            drawPath(Path().poly(listOf(Offset(c.x, c.y - r), Offset(c.x + r, c.y), Offset(c.x, c.y + r), Offset(c.x - r, c.y))), threads[k % threads.size],
                style = Stroke(h * 0.006f, cap = StrokeCap.Round, pathEffect = dash(h * 0.014f, h * 0.012f)))
            r += h * 0.032f; k++
        }
        // Eight stitched petals round the heart; the two either side of the top are sewn
        // in red thread, a V with the heart.
        for (a in 0 until 8) rotate(a * 45f, c) {
            val red = a == 1 || a == 7
            drawOval(if (red) Color(0xFFC0392B) else Color(0xFF2B4C8C), Offset(c.x - h * 0.016f, c.y - h * 0.085f), Size(h * 0.032f, h * 0.05f), style = Stroke(h * 0.005f, pathEffect = dash(h * 0.01f, h * 0.007f)))
        }
        drawCircle(Color(0xFFC0392B), h * 0.012f, c)
    }
    panel(Color(0xFF6B1E2B), Edge.ARCH)
    hem(Color(0xFFC0392B).copy(alpha = 0.4f))
}

/** Rangoli on a red-oxide floor: a rice-flour lattice; a V of marigold powder along it. */
private val Rangoli = PosterDesign("Rangoli", "India", Color(0xFF3D140C)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF8A3324))
    val chalk = Color(0xFFF7F0E1); val d = h * 0.08f
    artArea {
        var n = -30
        while (n < 30) {
            val a = w * 0.5f + n * d
            drawLine(chalk.copy(alpha = 0.8f), Offset(a, 0f), Offset(a + h, h), h * 0.005f)
            drawLine(chalk.copy(alpha = 0.8f), Offset(a + d, 0f), Offset(a + d - h, h), h * 0.005f)
            n++
        }
        // The rice-flour dots where the lines cross: five of them, in a V, are marigold.
        val v = gridV(d, levels = 2)
        val marigold = Color(0xFFFFA62B)
        grid(d) { c, i, j ->
            val isV = v.hit(c, d * 0.3f)
            if ((i + j) % 2 == 0) drawCircle(if (isV) marigold else chalk, d * 0.28f, c, style = Stroke(h * 0.004f))
            drawCircle(if (isV) marigold else chalk, if (isV) h * 0.011f else h * 0.007f, c)
        }
    }
    panel(Color(0xFF3D140C), Edge.STEP)
    hem(chalk.copy(alpha = 0.4f))
}

/** A lotus mandala: rings of pointed petals round a heart of two gold petals in a V. */
private val LotusMandala = PosterDesign("Lotus mandala", "India", Color(0xFF06241B)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF0D3B2E))
    val c = Offset(w * 0.8f, h * 0.47f)
    val pinks = listOf(Color(0xFFF7C8D0), Color(0xFFE8839A), Color(0xFFF4E3C1), Color(0xFFD45D79))
    artArea {
        for (k in 4 downTo 1) {
            val r1 = h * 0.11f * k; val r0 = r1 - h * 0.13f; val n = 6 + k * 4; val wd = h * 0.03f + k * h * 0.006f
            // The inner ring sits square to the top, so its petals either side of it, gilded,
            // make a V with the heart.
            val turn = if (k == 2) 0f else k * 7f
            for (p in 0 until n) rotate(p * 360f / n + turn, c) {
                val petal = Path().apply {
                    moveTo(c.x, c.y - r0)
                    quadraticBezierTo(c.x + wd, c.y - (r0 + r1) / 2, c.x, c.y - r1)
                    quadraticBezierTo(c.x - wd, c.y - (r0 + r1) / 2, c.x, c.y - r0)
                }
                val gild = k == 2 && (p == 1 || p == n - 1)
                drawPath(petal, if (gild) Color(0xFFE9B949) else pinks[k % pinks.size])
                drawPath(petal, Color(0xFF0D3B2E), style = Stroke(h * 0.003f))
            }
        }
        drawCircle(Color(0xFF0D3B2E), h * 0.06f, c)
        drawCircle(Color(0xFFE9B949), h * 0.06f, c, style = Stroke(h * 0.005f))
        drawCircle(Color(0xFFE9B949), h * 0.016f, c)
    }
    panel(Color(0xFF06241B), Edge.SLANT)
    hem(Color(0xFFE9B949).copy(alpha = 0.45f))
}

/** Pochampally ikat: small feathered diamonds; a V of them dyed pink. */
private val Pochampally = PosterDesign("Pochampally ikat", "India", Color(0xFF0D2E2C)) {
    val h = size.height
    drawRect(Color(0xFF1B5E5A))
    artArea {
        val v = gridV(h * 0.1f, h * 0.1f, shift = h * 0.05f, levels = 3)
        grid(h * 0.1f, h * 0.1f, shift = h * 0.05f) { c, _, j ->
            val col = if (v.hit(c, h * 0.02f)) Color(0xFFFF5D8F) else if (j % 2 == 0) Color(0xFFF4EBDC) else Color(0xFF111111)
            val rx = h * 0.034f; val ry = h * 0.048f
            for (k in 0 until 3) {
                val dx = (k - 1) * rx * 0.1f
                drawPath(Path().poly(listOf(Offset(c.x + dx, c.y - ry), Offset(c.x + rx + dx, c.y), Offset(c.x + dx, c.y + ry), Offset(c.x - rx + dx, c.y))), col.copy(alpha = 0.5f))
            }
        }
    }
    panel(Color(0xFF0D2E2C), Edge.STEP)
    hem(Color(0xFFF4EBDC).copy(alpha = 0.4f))
}

/** Toda embroidery from the Nilgiris: red and black diamonds; a V of them stitched solid. */
private val Toda = PosterDesign("Toda embroidery", "India", Color(0xFF1A1A1A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF5F1E6))
    val red = Color(0xFFB3202F); val black = Color(0xFF1A1A1A)
    artArea {
        var y = h * 0.045f
        while (y < h) { drawLine(red, Offset(w * 0.5f, y), Offset(w, y), h * 0.006f); drawLine(black, Offset(w * 0.5f, y + h * 0.008f), Offset(w, y + h * 0.008f), h * 0.004f); y += h * 0.18f }
        val v = gridV(h * 0.09f, shift = h * 0.045f, levels = 3)
        grid(h * 0.09f, shift = h * 0.045f) { c, i, j ->
            val r = h * 0.025f
            val d = Path().poly(listOf(Offset(c.x, c.y - r), Offset(c.x + r, c.y), Offset(c.x, c.y + r), Offset(c.x - r, c.y)))
            if (v.hit(c, h * 0.02f)) drawPath(d, red)
            else drawPath(d, if ((i + j) % 2 == 0) red else black, style = Stroke(h * 0.004f, pathEffect = dash(h * 0.007f, h * 0.004f)))
        }
    }
    panel(Color(0xFF1A1A1A), Edge.STRAIGHT)
    hem(red.copy(alpha = 0.5f))
}

/** Tumpal, the temple border: rows of tall triangles; one gap between them is gilded, a V. */
private val Tumpal = PosterDesign("Tumpal border", "Indonesia", Color(0xFF2A0812)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF5A1426))
    val gold = Color(0xFFD4A24C); val cream = Color(0xFFF7E7C6); val tw = h * 0.12f; val th = h * 0.25f
    artArea {
        val spot = vSpot()
        var gapAt: Offset? = null
        var y0 = 0f
        while (y0 < h) {
            var x = w * 0.5f
            while (x < w + tw) {
                drawPath(Path().poly(listOf(Offset(x, y0 + th), Offset(x + tw / 2, y0), Offset(x + tw, y0 + th))), gold)
                drawPath(Path().poly(listOf(Offset(x + tw * 0.2f, y0 + th * 0.92f), Offset(x + tw / 2, y0 + th * 0.3f), Offset(x + tw * 0.8f, y0 + th * 0.92f))), Color(0xFF8E1B2A))
                drawLine(cream, Offset(x + tw / 2, y0 + th * 0.4f), Offset(x + tw / 2, y0 + th * 0.85f), h * 0.005f)
                val g = Offset(x + tw, y0 + th / 2)
                if (gapAt.let { it == null || (g - spot).getDistance() < (it - spot).getDistance() }) gapAt = g
                x += tw
            }
            drawLine(cream, Offset(w * 0.5f, y0 + th), Offset(w, y0 + th), h * 0.008f)
            y0 += th + h * 0.012f
        }
        gapAt?.let { g ->
            val i = 0.18f
            drawPath(Path().poly(listOf(Offset(g.x - tw / 2 * (1 - i), g.y - th / 2 * (1 - i)), Offset(g.x + tw / 2 * (1 - i), g.y - th / 2 * (1 - i)), Offset(g.x, g.y + th / 2 * (1 - i * 0.6f)))), cream)
        }
    }
    panel(Color(0xFF2A0812), Edge.STRAIGHT)
    hem(gold.copy(alpha = 0.5f))
}

/** Bargello flame stitch: stepped bands dip into Vs; one band in one dip is white. */
private val Bargello = PosterDesign("Bargello", "Italy", Color(0xFF2E0A12)) {
    val w = size.width; val h = size.height
    val shades = listOf(Color(0xFF5C0F1B), Color(0xFF8E1B2A), Color(0xFFC23B22), Color(0xFFE8762B), Color(0xFFF2B33D), Color(0xFFF7DC8A))
    drawRect(shades[0])
    artArea {
        val cw = h * 0.022f; val P = 12; val bh = h * 0.05f; val depth = cw
        fun dip(i: Int) = (P / 2 - abs(Math.floorMod(i, P) - P / 2)) * depth
        val spot = vSpot()
        val pv = Math.floorDiv(((spot.x - w * 0.5f) / cw).toInt(), P)
        val kV = Math.round((spot.y - dip(pv * P + P / 2)) / bh)
        var i = 0; var x = w * 0.5f
        while (x < w) {
            for (k in -8..(h / bh).toInt() + 2) {
                val y0 = k * bh + dip(i)
                val white = Math.floorDiv(i, P) == pv && k == kV
                drawRect(if (white) Color(0xFFFFF4E0) else shades[Math.floorMod(k, shades.size)], Offset(x, y0), Size(cw * 0.9f, bh))
            }
            x += cw; i++
        }
    }
    panel(Color(0xFF2E0A12), Edge.STEP)
    hem(Color(0xFFF7DC8A).copy(alpha = 0.45f))
}

// ── Weaves and checks ──────────────────────────────────────────────────────

/** Granny squares in crochet: rounds of colour; a V of squares in orange and yellow. */
private val GrannySquare = PosterDesign("Granny square", "UK", Color(0xFF1E1530)) {
    val h = size.height
    drawRect(Color(0xFF151515))
    val rounds = listOf(Color(0xFFE8839A) to Color(0xFF2EC4B6), Color(0xFF7B5EA7) to Color(0xFFF7C8D0), Color(0xFF2E7D5B) to Color(0xFF9ED2C6), Color(0xFF3D6FB6) to Color(0xFFF4E3C1))
    artArea {
        val s = h * 0.1f
        val v = gridV(s, levels = 2)
        grid(s) { c, i, j ->
            val (mid, inner) = if (v.hit(c, s * 0.3f)) Color(0xFFFF8A3D) to Color(0xFFFFD166) else rounds[(i * 3 + j) % rounds.size]
            val o = s * 0.46f
            drawRect(Color(0xFFF3E9D2), Offset(c.x - o, c.y - o), Size(o * 2, o * 2))
            drawRect(mid, Offset(c.x - o * 0.75f, c.y - o * 0.75f), Size(o * 1.5f, o * 1.5f))
            drawRect(inner, Offset(c.x - o * 0.42f, c.y - o * 0.42f), Size(o * 0.84f, o * 0.84f))
            drawCircle(Color(0xFF151515), o * 0.12f, c)
            // The gaps between the stitch clusters.
            for (t in listOf(-1f, 1f)) {
                drawLine(Color(0xFF151515), Offset(c.x + t * o * 0.33f, c.y - o), Offset(c.x + t * o * 0.33f, c.y - o * 0.42f), h * 0.003f)
                drawLine(Color(0xFF151515), Offset(c.x - o, c.y + t * o * 0.33f), Offset(c.x - o * 0.42f, c.y + t * o * 0.33f), h * 0.003f)
            }
        }
    }
    panel(Color(0xFF1E1530), Edge.STRAIGHT)
    hem(Color(0xFFF3E9D2).copy(alpha = 0.4f))
}

/** Basketweave: blocks of three strips, turning each way; a V of blocks dyed red. */
private val Basketweave = PosterDesign("Basketweave", "Philippines", Color(0xFF2E1F12)) {
    val h = size.height
    drawRect(Color(0xFF4A3220))
    artArea {
        val b = h * 0.1f
        val v = gridV(b, levels = 2)
        grid(b) { c, i, j ->
            val dyed = v.hit(c, b * 0.3f)
            val light = if (dyed) Color(0xFFB5452F) else Color(0xFFD9B77E)
            val shade = if (dyed) Color(0xFF7A2A1A) else Color(0xFFA9844F)
            for (k in 0 until 3) {
                val t = -b / 2 + k * b / 3
                if ((i + j) % 2 == 0) {
                    drawRect(light, Offset(c.x - b / 2, c.y + t + b * 0.02f), Size(b, b / 3 - b * 0.04f))
                    drawLine(shade, Offset(c.x - b / 2, c.y + t + b / 3 - b * 0.04f), Offset(c.x + b / 2, c.y + t + b / 3 - b * 0.04f), h * 0.004f)
                } else {
                    drawRect(light, Offset(c.x + t + b * 0.02f, c.y - b / 2), Size(b / 3 - b * 0.04f, b))
                    drawLine(shade, Offset(c.x + t + b / 3 - b * 0.04f, c.y - b / 2), Offset(c.x + t + b / 3 - b * 0.04f, c.y + b / 2), h * 0.004f)
                }
            }
        }
    }
    panel(Color(0xFF2E1F12), Edge.SLANT)
    hem(Color(0xFFD9B77E).copy(alpha = 0.45f))
}

/** Herringbone parquet: oak planks meeting in chevrons; one chevron is rosewood. */
private val Herringbone = PosterDesign("Herringbone parquet", "France", Color(0xFF1E140C)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2B1D12))
    val oak = listOf(Color(0xFFC8955A), Color(0xFFB07A45), Color(0xFFD9A86C))
    artArea {
        val cw = h * 0.09f; val step = h * 0.045f
        val spot = vSpot()
        val colV = ((spot.x - w * 0.5f) / cw).toInt()
        var x = w * 0.5f; var col = 0
        while (x < w) {
            var y = -cw; var row = 0
            while (y < h) {
                val wood = if (col == colV && row == ((spot.y + cw / 2) / step).toInt()) Color(0xFF7A4321) else oak[(row + col) % 3]
                drawLine(wood, Offset(x, y), Offset(x + cw / 2, y + cw / 2), step * 0.62f)
                drawLine(wood, Offset(x + cw, y), Offset(x + cw / 2, y + cw / 2), step * 0.62f)
                y += step; row++
            }
            x += cw; col++
        }
    }
    panel(Color(0xFF1E140C), Edge.STRAIGHT)
    hem(oak[2].copy(alpha = 0.45f))
}

/** Gingham: a blue check; where the stripes cross along a V, the squares are red. */
private val Gingham = PosterDesign("Gingham", "Malaysia", Color(0xFF1D3557)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFFAF7F2))
    val blue = Color(0xFF3E6FB0).copy(alpha = 0.5f); val c = h * 0.055f
    artArea {
        var x = w * 0.5f + c / 2
        while (x < w) { drawRect(blue, Offset(x, 0f), Size(c, h)); x += c * 2 }
        var y = c / 2
        while (y < h) { drawRect(blue, Offset(w * 0.5f, y), Size(w * 0.5f, c)); y += c * 2 }
        for (p in gridV(c * 2, levels = 2)) drawRect(Color(0xFFC0392B), Offset(p.x - c / 2, p.y - c / 2), Size(c, c))
    }
    panel(Color(0xFF1D3557), Edge.STEP)
    hem(Color.White.copy(alpha = 0.45f))
}

/** Tartan: a woven sett; where the twill crosses a V, its threads are gold. */
private val Tartan = PosterDesign("Tartan", "Scotland", Color(0xFF102A20)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF1F4E3D))
    val navy = Color(0xFF1E2F5C).copy(alpha = 0.6f); val red = Color(0xFFB3202F); val yellow = Color(0xFFF2C14E)
    val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
    artArea {
        var t = 0f
        while (t < w) {
            drawRect(navy, Offset(w * 0.5f + t, 0f), Size(h * 0.08f, h)); drawRect(navy, Offset(w * 0.5f, t), Size(w * 0.5f, h * 0.08f))
            drawRect(red, Offset(w * 0.5f + t + h * 0.12f, 0f), Size(h * 0.012f, h)); drawRect(red, Offset(w * 0.5f, t + h * 0.12f), Size(w * 0.5f, h * 0.012f))
            drawRect(yellow, Offset(w * 0.5f + t + h * 0.16f, 0f), Size(h * 0.004f, h)); drawRect(yellow, Offset(w * 0.5f, t + h * 0.16f), Size(w * 0.5f, h * 0.004f))
            t += h * 0.2f
        }
        // The twill: fine diagonal lines over everything.
        var a = w * 0.5f - h
        while (a < w) {
            val pts = line(Offset(a, h), Offset(a + h, 0f), 120)
            drawLine(Color.Black.copy(alpha = 0.12f), pts.first(), pts.last(), h * 0.003f)
            traceV(pts, v, h * 0.012f, Color(0xFFF2C14E).copy(alpha = 0.75f), h * 0.003f)
            a += h * 0.012f
        }
    }
    panel(Color(0xFF102A20), Edge.STRAIGHT)
    hem(yellow.copy(alpha = 0.4f))
}

/** Argyle knit: diamonds with dashed overcheck; one diamond's lower edges are gold, a V. */
private val Argyle = PosterDesign("Argyle", "Scotland", Color(0xFF1B2428)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF354F52))
    val dw = h * 0.13f; val dh = h * 0.2f
    artArea {
        val spot = vSpot()
        var best: Offset? = null
        for (half in 0..1) {
            var j = -1
            while (j * dh < h + dh) {
                var i = -1
                while (w * 0.5f + i * dw < w + dw) {
                    val c = Offset(w * 0.5f + i * dw + half * dw / 2, j * dh + half * dh / 2)
                    val d = listOf(Offset(c.x, c.y - dh / 2), Offset(c.x + dw / 2, c.y), Offset(c.x, c.y + dh / 2), Offset(c.x - dw / 2, c.y))
                    if (half == 0) drawPath(Path().poly(d), Color(0xFF52796F))
                    drawPath(Path().poly(d), Color(0xFFCAD2C5), style = Stroke(h * 0.004f, pathEffect = dash(h * 0.012f, h * 0.01f)))
                    if (best.let { it == null || (c - spot).getDistance() < (it - spot).getDistance() }) best = c
                    i++
                }
                j++
            }
        }
        // The overcheck is knitted in pale yarn; on one diamond its two lower edges are in gold.
        best?.let { c ->
            drawPath(Path().poly(listOf(Offset(c.x - dw / 2, c.y), Offset(c.x, c.y + dh / 2), Offset(c.x + dw / 2, c.y)), closed = false), Color(0xFFE9C46A),
                style = Stroke(h * 0.007f, cap = StrokeCap.Round))
        }
    }
    panel(Color(0xFF1B2428), Edge.SLANT)
    hem(Color(0xFFCAD2C5).copy(alpha = 0.4f))
}

/** Missoni-style chevron knit: bright zigzag stripes; one dip of one stripe is white. */
private val Chevron = PosterDesign("Chevron knit", "Italy", Color(0xFF14232A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF264653))
    val stripes = listOf(Color(0xFF2A9D8F), Color(0xFFE9C46A), Color(0xFFF4A261), Color(0xFFE76F51), Color(0xFF264653), Color(0xFF8AB17D))
    artArea {
        val band = h * 0.036f; val period = h * 0.1f; val amp = h * 0.05f
        val spot = vSpot()
        var y = -amp; var k = 0
        var dip: List<Offset>? = null
        while (y < h + amp) {
            val pts = ArrayList<Offset>()
            var x = w * 0.5f - period / 2; var down = true
            while (x < w + period) { pts += Offset(x, y + if (down) 0f else amp); x += period / 2; down = !down }
            drawPath(Path().poly(pts, closed = false), stripes[k % stripes.size], style = Stroke(band, join = StrokeJoin.Miter))
            if (dip == null && y + amp >= spot.y) {
                val i = (1 until pts.size - 1).filter { pts[it].y > y }.minByOrNull { abs(pts[it].x - spot.x) }
                if (i != null) dip = listOf(pts[i - 1], pts[i], pts[i + 1])
            }
            y += band; k++
        }
        dip?.let { drawPath(Path().poly(it, closed = false), Color(0xFFFFF8EC).copy(alpha = 0.7f), style = Stroke(band, join = StrokeJoin.Miter)) }
    }
    panel(Color(0xFF14232A), Edge.WAVE)
    hem(Color(0xFFE9C46A).copy(alpha = 0.45f))
}

// ── Japan, Africa and beyond ───────────────────────────────────────────────

/** Asanoha (hemp leaf) in sashiko: a star lattice in white thread; orange along a V. */
private val Asanoha = PosterDesign("Asanoha sashiko", "Japan", Color(0xFF102244)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF1E3A6B))
    val a = h * 0.13f; val rh = a * 0.866f; val x0 = w * 0.5f - a
    fun p(r: Int, i: Int) = Offset(x0 + i * a + if (r % 2 == 1) a / 2 else 0f, r * rh)
    val thread = Stroke(h * 0.005f, cap = StrokeCap.Round, pathEffect = dash(h * 0.014f, h * 0.01f))
    artArea {
        for (r in 0..(h / rh).toInt() + 1) for (i in 0..((w * 0.5f + a * 2) / a).toInt()) {
            val tris = if (r % 2 == 0) listOf(listOf(p(r, i), p(r, i + 1), p(r + 1, i)), listOf(p(r, i + 1), p(r + 1, i), p(r + 1, i + 1)))
                else listOf(listOf(p(r, i), p(r, i + 1), p(r + 1, i + 1)), listOf(p(r, i), p(r + 1, i), p(r + 1, i + 1)))
            for (t in tris) {
                val g = Offset((t[0].x + t[1].x + t[2].x) / 3, (t[0].y + t[1].y + t[2].y) / 3)
                for (k in 0..2) {
                    for ((s, e) in listOf(t[k] to t[(k + 1) % 3], g to t[k])) {
                        drawLine(Color.White.copy(alpha = 0.8f), s, e, thread.width, cap = StrokeCap.Round, pathEffect = thread.pathEffect)
                    }
                }
            }
        }
    }
    artArea {
        // From the lattice point nearest the V spot, two triangle edges up each side, in orange thread.
        val r = Math.round(vSpot().y / rh); val i = Math.round((vSpot().x - x0 - if (r % 2 == 1) a / 2 else 0f) / a)
        val apex = p(r, i)
        // Sewn over the white, at the same stitch: just the lattice's own edges, in orange.
        drawPath(Path().poly(listOf(Offset(apex.x - a, apex.y - rh * 2), apex, Offset(apex.x + a, apex.y - rh * 2)), closed = false), Color(0xFFFF8A3D),
            style = Stroke(h * 0.005f, cap = StrokeCap.Round, pathEffect = dash(h * 0.014f, h * 0.01f)))
    }
    panel(Color(0xFF102244), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.35f))
}

/** Shweshwe, the indigo discharge print: little white rings; a V of them printed solid. */
private val Shweshwe = PosterDesign("Shweshwe", "South Africa", Color(0xFF0F1F3A)) {
    val h = size.height
    drawRect(Color(0xFF1F3A68))
    val white = Color(0xFFEFF3F8)
    artArea {
        val v = gridV(h * 0.09f, shift = h * 0.045f, levels = 3)
        grid(h * 0.09f, shift = h * 0.045f) { c, _, _ ->
            if (v.hit(c, h * 0.02f)) drawCircle(white, h * 0.021f, c) else drawCircle(white, h * 0.018f, c, style = Stroke(h * 0.004f))
            for (k in 0 until 6) {
                val a = k * PI / 3
                drawCircle(white, h * 0.0035f, Offset(c.x + cos(a).toFloat() * h * 0.032f, c.y + sin(a).toFloat() * h * 0.032f))
            }
        }
    }
    panel(Color(0xFF0F1F3A), Edge.STEP)
    hem(white.copy(alpha = 0.35f))
}

/** Adinkra stamping from Ghana: black stamps in a combed grid; a V of them in red. */
private val Adinkra = PosterDesign("Adinkra", "Ghana", Color(0xFF2B1A0E)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFC08A35))
    val ink = Color(0xFF1E140C); val s = h * 0.11f
    artArea {
        var x = w * 0.5f
        while (x < w) { for (k in 0..2) drawLine(ink.copy(alpha = 0.6f), Offset(x + k * h * 0.006f, 0f), Offset(x + k * h * 0.006f, h), h * 0.002f); x += s }
        var y = 0f
        while (y < h) { for (k in 0..2) drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.5f, y + k * h * 0.006f), Offset(w, y + k * h * 0.006f), h * 0.002f); y += s }
        val v = gridV(s, levels = 2)
        grid(s) { c, i, j ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFF9E1B1B) else ink
            when ((i + j * 2) % 3) {
                0 -> { drawCircle(col, s * 0.3f, c, style = Stroke(h * 0.006f)); drawCircle(col, s * 0.18f, c, style = Stroke(h * 0.006f)); drawCircle(col, s * 0.06f, c) }
                1 -> { drawRect(col, Offset(c.x - s * 0.06f, c.y - s * 0.3f), Size(s * 0.12f, s * 0.6f)); drawRect(col, Offset(c.x - s * 0.3f, c.y - s * 0.06f), Size(s * 0.6f, s * 0.12f)); for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) drawCircle(col, s * 0.07f, Offset(c.x + dx * s * 0.2f, c.y + dy * s * 0.2f)) }
                else -> { drawArc(col, 180f, 270f, false, Offset(c.x - s * 0.26f, c.y - s * 0.26f), Size(s * 0.52f, s * 0.52f), style = Stroke(h * 0.008f)); drawCircle(col, s * 0.08f, c) }
            }
        }
    }
    panel(Color(0xFF2B1A0E), Edge.STRAIGHT)
    hem(ink.copy(alpha = 0.5f))
}

/** Girih: an Islamic eight-point star lattice; where it crosses a V, the lines are gold. */
private val Girih = PosterDesign("Girih", "Iran", Color(0xFF082436)) {
    val h = size.height
    drawRect(Color(0xFF0F3D5C))
    val ivory = Color(0xFFE8E2D0); val s = h * 0.14f
    artArea {
        // Each star's heart is a turquoise inlay; five of them, in a V, are gold.
        val v = gridV(s, levels = 2)
        grid(s) { c, _, _ ->
            val r = s * 0.36f
            for (rot in listOf(0.0, PI / 4)) {
                val corners = (0..3).map { k -> val a = rot + PI / 4 + k * PI / 2; Offset(c.x + cos(a).toFloat() * r * 1.414f, c.y + sin(a).toFloat() * r * 1.414f) }
                for (k in 0..3) {
                    val e = corners[k] to corners[(k + 1) % 4]
                    drawLine(ivory, e.first, e.second, h * 0.004f)
                }
            }
            // Links to the neighbouring stars.
            for ((dx, dy) in listOf(1f to 0f, 0f to 1f)) {
                val a = Offset(c.x + dx * r * 1.414f, c.y + dy * r * 1.414f); val b = Offset(c.x + dx * (s - r * 1.414f), c.y + dy * (s - r * 1.414f))
                drawLine(ivory, a, b, h * 0.004f)
            }
            drawCircle(if (v.hit(c, s * 0.3f)) Color(0xFFE9B949) else Color(0xFF2EA39A), s * 0.08f, c)
        }
    }
    panel(Color(0xFF082436), Edge.ARCH)
    hem(ivory.copy(alpha = 0.4f))
}

/** Celtic knotwork: three-strand plaits; where they cross a V, the strands are red. */
private val Celtic = PosterDesign("Celtic knotwork", "Ireland", Color(0xFF0F1F16)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF1E3B2A))
    val strand = Color(0xFFE8D9A8); val edge = Color(0xFF0F1F16)
    val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
    artArea {
        var y0 = h * 0.1f
        while (y0 < h + h * 0.1f) {
            for (k in 0..2) {
                val pts = ArrayList<Offset>()
                var x = w * 0.5f
                while (x <= w) { pts += Offset(x, y0 + sin((x / (h * 0.07f)) + k * 2 * PI / 3).toFloat() * h * 0.05f); x += h * 0.004f }
                val path = Path().poly(pts, closed = false)
                drawPath(path, edge, style = Stroke(h * 0.026f, cap = StrokeCap.Round))
                drawPath(path, strand, style = Stroke(h * 0.014f, cap = StrokeCap.Round))
                traceV(pts, v, h * 0.016f, Color(0xFFC0392B), h * 0.014f)
            }
            y0 += h * 0.2f
        }
    }
    panel(Color(0xFF0F1F16), Edge.WAVE)
    hem(strand.copy(alpha = 0.4f))
}

/** Arabesque: scrolling vines with curls; where the vine crosses a V it turns turquoise. */
private val Arabesque = PosterDesign("Arabesque", "Morocco", Color(0xFF0B1A33)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF13294B))
    val gold = Color(0xFFD4A24C)
    // The vine's crests: a V of them (one, and one up either side) bloom turquoise.
    val crests = ArrayList<Offset>()
    run {
        var y0 = h * 0.08f; var row = 0
        while (y0 < h + h * 0.1f) {
            val ph = if (row % 2 == 0) 0f else PI.toFloat()
            var t = 0.5f * PI.toFloat() - ph
            while (t * h * 0.05f < w) { val cx = t * h * 0.05f; if (cx > w * 0.5f) crests += Offset(cx, y0 + h * 0.04f * sin(t + ph)); t += PI.toFloat() }
            y0 += h * 0.16f; row++
        }
    }
    val vCrests = pickV(crests, h * 0.16f, h * 0.32f, n = 2).map { crests[it] }
    artArea {
        var y0 = h * 0.08f; var row = 0
        while (y0 < h + h * 0.1f) {
            val pts = ArrayList<Offset>()
            var x = w * 0.5f
            val ph = if (row % 2 == 0) 0f else PI.toFloat()
            while (x <= w) { pts += Offset(x, y0 + sin(x / (h * 0.05f) + ph) * h * 0.04f); x += h * 0.004f }
            drawPath(Path().poly(pts, closed = false), gold, style = Stroke(h * 0.008f, cap = StrokeCap.Round))
            // A curl and a leaf at each crest.
            var t = 0.5f * PI.toFloat() - ph
            while (t * h * 0.05f < w) {
                val cx = t * h * 0.05f
                if (cx > w * 0.5f) {
                    val crest = Offset(cx, y0 + h * 0.04f * sin(t + ph))
                    val turq = vCrests.any { (it - crest).getDistance() < h * 0.03f }
                    drawArc(if (turq) Color(0xFF7FDBDA) else gold, 200f, 260f, false, Offset(crest.x - h * 0.02f, crest.y - h * 0.045f), Size(h * 0.04f, h * 0.04f), style = Stroke(h * 0.005f))
                    drawOval(if (turq) Color(0xFF7FDBDA) else gold.copy(alpha = 0.7f), Offset(crest.x + h * 0.012f, crest.y - h * 0.008f), Size(h * 0.03f, h * 0.014f))
                }
                t += PI.toFloat()
            }
            y0 += h * 0.16f; row++
        }
    }
    panel(Color(0xFF0B1A33), Edge.ARCH)
    hem(gold.copy(alpha = 0.45f))
}

/** Quatrefoils: four-lobed tiles; a V of them filled gold. */
private val Quatrefoil = PosterDesign("Quatrefoil", "Spain", Color(0xFF0A2E38)) {
    val h = size.height
    drawRect(Color(0xFF0F4C5C))
    val cream = Color(0xFFF1E3C4); val s = h * 0.11f
    artArea {
        val v = gridV(s, levels = 2)
        grid(s) { c, _, _ ->
            val gold = v.hit(c, s * 0.3f)
            for ((dx, dy) in listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)) {
                val o = Offset(c.x + dx * s * 0.17f, c.y + dy * s * 0.17f)
                drawCircle(if (gold) Color(0xFFE9B949) else cream.copy(alpha = 0.12f), s * 0.2f, o)
            }
            for ((dx, dy) in listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)) drawCircle(cream, s * 0.2f, Offset(c.x + dx * s * 0.17f, c.y + dy * s * 0.17f), style = Stroke(h * 0.004f))
            drawCircle(if (gold) Color(0xFF0F4C5C) else cream, s * 0.05f, c)
        }
    }
    panel(Color(0xFF0A2E38), Edge.SLANT)
    hem(cream.copy(alpha = 0.4f))
}

/** Terrazzo: stone chips set in cement; along a V the chips are terracotta. */
private val Terrazzo = PosterDesign("Terrazzo", "Italy", Color(0xFF2B2B2B)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFEDE7DF))
    val stones = listOf(Color(0xFF8E8E8E), Color(0xFF5E7D6B), Color(0xFF2B2B2B), Color(0xFFE8B4A0), Color(0xFFBFB5A8))
    val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
    val rnd = kotlin.random.Random(7)
    fun chip(c: Offset, r: Float, col: Color) {
        val n = 5 + rnd.nextInt(3)
        drawPath(Path().poly((0 until n).map { k -> val a = 2 * PI * k / n; val rr = r * (0.65f + rnd.nextFloat() * 0.5f); Offset(c.x + cos(a).toFloat() * rr, c.y + sin(a).toFloat() * rr) }), col)
    }
    artArea {
        repeat(260) {
            val c = Offset(w * 0.5f + rnd.nextFloat() * w * 0.5f, rnd.nextFloat() * h)
            val near = v.near(c, h * 0.03f)
            if (!near) chip(c, h * (0.008f + rnd.nextFloat() * 0.016f), stones[rnd.nextInt(stones.size)])
        }
        for (t in 0..6) for (side in listOf(-1f, 1f)) {
            if (t == 0 && side > 0) continue
            val f = t / 6f
            val end = if (side < 0) v.first else v.third
            chip(Offset(v.second.x + (end.x - v.second.x) * f, v.second.y + (end.y - v.second.y) * f), h * 0.017f, Color(0xFFC0532E))
        }
    }
    panel(Color(0xFF2B2B2B), Edge.STRAIGHT)
    hem(Color(0xFF8E8E8E).copy(alpha = 0.5f))
}

/** Papel picado: cut-paper banners on a string; one banner's centre is cut as a V. */
private val PapelPicado = PosterDesign("Papel picado", "Mexico", Color(0xFF1B3A4B)) {
    val w = size.width; val h = size.height
    val sky = Color(0xFF1F5F7A)
    drawRect(sky)
    val papers = listOf(Color(0xFFFF5D8F), Color(0xFFFFA62B), Color(0xFFF7DC6F), Color(0xFF9B5DE5), Color(0xFF00BBF9))
    artArea {
        val bw = h * 0.13f; val bh = h * 0.24f; val gapX = bw + h * 0.02f
        // Every banner: its top-left corner and colour. Three are cut from white tissue
        // instead, one and one up either side of it on the string above: a V of banners.
        data class Banner(val x: Float, val y0: Float, val k: Int)
        val banners = (0..2).flatMap { r ->
            val y0 = h * 0.06f + r * h * 0.32f
            generateSequence(w * 0.5f + h * 0.01f) { it + gapX }.takeWhile { it < w }.mapIndexed { n, x -> Banner(x, y0, r + n) }.toList()
        }
        val apex = banners.filter { it.y0 > h * 0.2f }.minByOrNull { (Offset(it.x + bw / 2, it.y0 + bh * 0.45f) - vSpot()).getDistance() }
        val white = if (apex == null) emptySet() else banners.filter { b ->
            b == apex || (kotlin.math.abs(b.y0 - (apex.y0 - h * 0.32f)) < 1f && kotlin.math.abs(kotlin.math.abs(b.x - apex.x) - gapX) < 1f)
        }.toSet()
        for (r in 0..2) {
            val y0 = h * 0.06f + r * h * 0.32f
            drawLine(Color(0xFFF4F1EA), Offset(w * 0.5f, y0), Offset(w, y0), h * 0.004f)
        }
        for (b in banners) {
            val pts = arrayListOf(Offset(b.x, b.y0), Offset(b.x + bw, b.y0))
            for (z in 6 downTo 0) pts += Offset(b.x + bw * z / 6, b.y0 + bh - if (z % 2 == 1) h * 0.02f else 0f)
            drawPath(Path().poly(pts), if (b in white) Color(0xFFF4F1EA) else papers[b.k % papers.size])
            val c = Offset(b.x + bw / 2, b.y0 + bh * 0.45f)
            for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                val o = Offset(c.x + dx * bw * 0.28f, c.y + dy * bh * 0.28f); val d = h * 0.014f
                drawPath(Path().poly(listOf(Offset(o.x, o.y - d), Offset(o.x + d, o.y), Offset(o.x, o.y + d), Offset(o.x - d, o.y))), sky)
            }
            drawCircle(sky, bw * 0.17f, c)
        }
    }
    panel(Color(0xFF1B3A4B), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Paj ntaub, Hmong reverse appliqué: square spirals; a V of them in gold and black. */
private val PajNtaub = PosterDesign("Paj ntaub", "Laos", Color(0xFF1E1530)) {
    val h = size.height
    drawRect(Color(0xFF141414))
    val fills = listOf(Color(0xFFE5397A), Color(0xFF2E86DE), Color(0xFF27AE60), Color(0xFF8E44AD))
    artArea {
        val s = h * 0.1f
        val v = gridV(s, levels = 2)
        grid(s) { c, i, j ->
            val isV = v.hit(c, s * 0.3f)
            val o = s * 0.44f
            drawRect(if (isV) Color(0xFFF2C14E) else fills[(i + j * 3) % fills.size], Offset(c.x - o, c.y - o), Size(o * 2, o * 2))
            // A square spiral cut through to the black beneath.
            val p = Path(); var len = o * 0.25f; var x = c.x; var y = c.y; p.moveTo(x, y)
            for (k in 0 until 7) {
                when (k % 4) { 0 -> x += len; 1 -> y += len; 2 -> x -= len; else -> y -= len }
                p.lineTo(x, y); if (k % 2 == 1) len += o * 0.25f
            }
            drawPath(p, if (isV) Color(0xFF141414) else Color(0xFFF4F1EA), style = Stroke(h * 0.006f))
        }
    }
    panel(Color(0xFF1E1530), Edge.STEP)
    hem(Color(0xFFF2C14E).copy(alpha = 0.4f))
}

/** Vyshyvanka cross-stitch from Ukraine: red rhombuses; a V of them stitched in black. */
private val Vyshyvanka = PosterDesign("Vyshyvanka", "Ukraine", Color(0xFF5A0F14)) {
    val h = size.height
    drawRect(Color(0xFFF3EEE4))
    val p = h * 0.014f
    artArea {
        val v = gridV(h * 0.13f, shift = h * 0.065f, levels = 2)
        grid(h * 0.13f, shift = h * 0.065f) { c, _, _ ->
            val col = if (v.hit(c, h * 0.03f)) Color(0xFF1A1A1A) else Color(0xFFB3202F)
            for (dy in -3..3) for (dx in -3..3) {
                val m = abs(dx) + abs(dy)
                if (m == 3 || m == 0 || (m == 1 && col != Color(0xFFB3202F))) {
                    val o = Offset(c.x + dx * p, c.y + dy * p)
                    val stitchCol = if (m == 0) Color(0xFF1A1A1A) else col
                    drawLine(stitchCol, Offset(o.x - p * 0.38f, o.y - p * 0.38f), Offset(o.x + p * 0.38f, o.y + p * 0.38f), h * 0.0035f)
                    drawLine(stitchCol, Offset(o.x + p * 0.38f, o.y - p * 0.38f), Offset(o.x - p * 0.38f, o.y + p * 0.38f), h * 0.0035f)
                }
            }
        }
    }
    panel(Color(0xFF5A0F14), Edge.STRAIGHT)
    hem(Color(0xFFB3202F).copy(alpha = 0.45f))
}

/** Bold crafts added in 4.0. */
internal val HeritageBoldDesigns: List<PosterDesign> = listOf(
    Paisley, Tumpal, Bargello, LotusMandala, Argyle, KanthaQuilt, Chevron, AjrakhJaal,
    Herringbone, Celtic, PapelPicado, Rangoli, Tartan, Arabesque, Gingham,
)

/** Fine patterns added in 4.0. */
internal val HeritageMicroDesigns: List<PosterDesign> = listOf(
    Buti, Asanoha, GrannySquare, Shweshwe, KanthaWave, Quatrefoil, Pochampally, Basketweave,
    Toda, Girih, Sanganeri, Terrazzo, Adinkra, PajNtaub, Vyshyvanka,
)
