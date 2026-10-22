package com.music.spotui.ui.components

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Aaj ka Aalap poster's daily look: a handmade craft from somewhere in the world,
 * a new one each day at midnight. Each design paints the whole poster (the art on the
 * right, a calm solid panel on the left for the words) in its craft's own colours.
 */
class PosterDesign(
    val craft: String,
    val origin: String,
    /** The panel behind the words: always deep enough for cream text. */
    val panel: Color,
    val paint: DrawScope.() -> Unit,
)

/** How the panel meets the art. */
internal enum class Edge { SLANT, WAVE, ARCH, STEP, STRAIGHT }

/** The words' panel on the left, ending in [edge]; drawn last, over the art. */
internal fun DrawScope.panel(color: Color, edge: Edge) {
    val w = size.width; val h = size.height
    val top = w * 0.66f; val bottom = w * 0.56f
    val p = Path().apply {
        moveTo(0f, 0f); lineTo(top, 0f)
        when (edge) {
            Edge.SLANT -> lineTo(bottom, h)
            Edge.STRAIGHT -> lineTo(top, h)
            Edge.WAVE -> {
                val n = 5
                for (i in 1..n) {
                    val y0 = h * (i - 1) / n; val y1 = h * i / n
                    val x = top + (bottom - top) * i / n
                    quadraticBezierTo(x + w * 0.035f * (if (i % 2 == 0) 1 else -1), (y0 + y1) / 2f, x, y1)
                }
            }
            Edge.ARCH -> cubicTo(top + w * 0.09f, h * 0.3f, top + w * 0.09f, h * 0.7f, bottom, h)
            Edge.STEP -> {
                val n = 6
                for (i in 1..n) {
                    val x = top + (bottom - top) * i / n
                    lineTo(x + w * 0.022f, h * (i - 0.5f) / n); lineTo(x, h * i / n)
                }
            }
        }
        lineTo(0f, h); close()
    }
    drawPath(p, color)
}

/** A thin dotted line just inside the poster's edge, like a hand-stitched hem. */
internal fun DrawScope.hem(color: Color) {
    val inset = size.height * 0.035f
    drawRoundRect(
        color, topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.08f),
        style = Stroke(size.height * 0.006f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, size.height * 0.025f))),
    )
}

/** The art's area: the right part of the poster, where the panel isn't. */
internal inline fun DrawScope.artArea(block: DrawScope.() -> Unit) =
    clipRect(left = size.width * 0.5f) { block() }

// ── The designs ─────────────────────────────────────────────────────────────

private val Mandala = PosterDesign("Marigold mandala", "India", Color(0xFFE0661B)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2A1A55))
    drawMandala(Color(0xFFFFC56B), Offset(w * 0.79f, h * 0.46f), h * 0.37f, h * 0.008f, accent = Color(0xFFFF8A3D))
    panel(Color(0xFFE0661B), Edge.SLANT)
    val band = h * 0.92f
    drawLine(Color(0xFFFFC56B).copy(alpha = 0.7f), Offset(0f, band), Offset(w, band), strokeWidth = h * 0.007f)
    var x = h * 0.03f
    while (x < w) { drawCircle(Color(0xFFFFC56B).copy(alpha = 0.55f), h * 0.007f, Offset(x, band + h * 0.04f)); x += h * 0.05f }
}

/** Japanese running-stitch embroidery: seigaiha waves in white thread on indigo. */
private val Sashiko = PosterDesign("Sashiko", "Japan", Color(0xFF14203A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF26386A))
    val thread = Stroke(h * 0.007f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.022f, h * 0.016f)))
    val r = h * 0.12f
    artArea {
        var row = 0
        var y = -r * 0.2f
        while (y < h + r) {
            var x = w * 0.48f + if (row % 2 == 0) 0f else r
            while (x < w + r) {
                for (k in 3 downTo 1) {
                    drawArc(
                        Color.White.copy(alpha = 0.78f), 180f, 180f, false,
                        topLeft = Offset(x - r * k / 3f, y - r * k / 3f), size = Size(r * 2 * k / 3f, r * 2 * k / 3f), style = thread,
                    )
                }
                x += r * 2
            }
            y += r * 0.55f; row++
        }
        // A few red stitches, as sashiko menders sign their work.
        for (i in 0 until 5) drawLine(Color(0xFFD9534F), Offset(w * 0.9f, h * (0.2f + i * 0.04f)), Offset(w * 0.93f, h * (0.2f + i * 0.04f)), strokeWidth = h * 0.008f, cap = StrokeCap.Round)
    }
    panel(Color(0xFF14203A), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.35f))
}

/** Moroccan zellige tilework: eight-point stars on cream. */
private val Zellige = PosterDesign("Zellige", "Morocco", Color(0xFF0F4C4A)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF3E9D2))
    val s = h * 0.2f
    artArea {
        var j = 0
        var y = s * 0.5f
        while (y < h + s) {
            var x = w * 0.52f + if (j % 2 == 0) 0f else s * 0.7f
            while (x < w + s) {
                val c = Offset(x, y)
                val star = if ((x / s + j).toInt() % 3 == 0) Color(0xFFC8553D) else Color(0xFF1E7A6E)
                for (a in listOf(0f, 45f)) rotate(a, c) {
                    drawRect(star, topLeft = Offset(c.x - s * 0.28f, c.y - s * 0.28f), size = Size(s * 0.56f, s * 0.56f))
                }
                drawCircle(Color(0xFFF3E9D2), s * 0.14f, c)
                drawCircle(Color(0xFFE0B04A), s * 0.06f, c)
                x += s * 1.4f
            }
            y += s * 0.85f; j++
        }
    }
    panel(Color(0xFF0F4C4A), Edge.ARCH)
    hem(Color(0xFFE0B04A).copy(alpha = 0.6f))
}

/** Ghanaian kente cloth: blocks of woven stripes. */
private val Kente = PosterDesign("Kente", "Ghana", Color(0xFF0E3B22)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF1B1B1B))
    val gold = Color(0xFFF2B705); val green = Color(0xFF1E8449); val red = Color(0xFFC0392B)
    val b = h / 4f
    artArea {
        var row = 0
        var y = 0f
        while (y < h) {
            var col = 0
            var x = w * 0.5f
            while (x < w) {
                val across = (row + col) % 2 == 0
                val colors = if (across) listOf(gold, green, gold, red) else listOf(gold, red, gold, green)
                val n = 8
                for (k in 0 until n) {
                    val c = colors[k % colors.size]
                    if (across) drawRect(c, Offset(x, y + b * k / n), Size(b, b / n * 0.8f))
                    else drawRect(c, Offset(x + b * k / n, y), Size(b / n * 0.8f, b))
                }
                x += b; col++
            }
            y += b; row++
        }
    }
    panel(Color(0xFF0E3B22), Edge.STEP)
    hem(gold.copy(alpha = 0.55f))
}

/** Warli wall painting: white figures dancing in a circle around a drummer. */
private val Warli = PosterDesign("Warli", "India", Color(0xFF4A1E10)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF8E3B22))
    val ink = Color(0xFFF7EFE3)
    val c = Offset(w * 0.8f, h * 0.48f)
    val ring = h * 0.32f
    val sw = h * 0.008f
    fun figure(at: Offset, s: Float, tilt: Float) = rotate(tilt, at) {
        drawCircle(ink, s * 0.18f, Offset(at.x, at.y - s * 0.62f))
        // Two triangles meeting at the waist.
        drawPath(Path().apply { moveTo(at.x - s * 0.26f, at.y - s * 0.42f); lineTo(at.x + s * 0.26f, at.y - s * 0.42f); lineTo(at.x, at.y); close() }, ink)
        drawPath(Path().apply { moveTo(at.x, at.y); lineTo(at.x - s * 0.24f, at.y + s * 0.4f); lineTo(at.x + s * 0.24f, at.y + s * 0.4f); close() }, ink)
        drawLine(ink, Offset(at.x - s * 0.24f, at.y - s * 0.36f), Offset(at.x - s * 0.5f, at.y - s * 0.12f), sw)
        drawLine(ink, Offset(at.x + s * 0.24f, at.y - s * 0.36f), Offset(at.x + s * 0.5f, at.y - s * 0.12f), sw)
        drawLine(ink, Offset(at.x - s * 0.14f, at.y + s * 0.4f), Offset(at.x - s * 0.2f, at.y + s * 0.72f), sw)
        drawLine(ink, Offset(at.x + s * 0.14f, at.y + s * 0.4f), Offset(at.x + s * 0.2f, at.y + s * 0.72f), sw)
    }
    artArea {
        val n = 11
        for (i in 0 until n) {
            val a = (i / n.toFloat()) * 2 * PI
            figure(Offset(c.x + cos(a).toFloat() * ring, c.y + sin(a).toFloat() * ring), h * 0.13f, (a * 180 / PI).toFloat() + 90f)
        }
        figure(c, h * 0.16f, 0f)
        drawCircle(ink, ring * 1.32f, c, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, h * 0.03f)), cap = StrokeCap.Round))
    }
    panel(Color(0xFF4A1E10), Edge.STRAIGHT)
    drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.66f + h * 0.02f, 0f), Offset(w * 0.66f + h * 0.02f, h), strokeWidth = sw,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, h * 0.03f)), cap = StrokeCap.Round)
}

/** Iznik ceramics: a cobalt tulip with saz leaves on white tile. */
private val Iznik = PosterDesign("Iznik", "Türkiye", Color(0xFF173A78)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF7F4EC))
    val cobalt = Color(0xFF1F4E9E); val turq = Color(0xFF2A9D8F); val red = Color(0xFFC0392B)
    fun tulip(base: Offset, s: Float, tilt: Float) = rotate(tilt, base) {
        drawLine(turq, base, Offset(base.x, base.y - s * 0.9f), s * 0.05f, cap = StrokeCap.Round)
        val top = Offset(base.x, base.y - s)
        drawPath(Path().apply { moveTo(top.x, top.y - s * 0.5f); cubicTo(top.x + s * 0.32f, top.y - s * 0.3f, top.x + s * 0.3f, top.y + s * 0.1f, top.x, top.y + s * 0.12f); cubicTo(top.x - s * 0.3f, top.y + s * 0.1f, top.x - s * 0.32f, top.y - s * 0.3f, top.x, top.y - s * 0.5f) }, cobalt)
        drawPath(Path().apply { moveTo(top.x - s * 0.12f, top.y); cubicTo(top.x - s * 0.45f, top.y - s * 0.2f, top.x - s * 0.4f, top.y - s * 0.5f, top.x - s * 0.3f, top.y - s * 0.55f); cubicTo(top.x - s * 0.22f, top.y - s * 0.3f, top.x - s * 0.1f, top.y - s * 0.15f, top.x - s * 0.12f, top.y) }, red)
        drawPath(Path().apply { moveTo(top.x + s * 0.12f, top.y); cubicTo(top.x + s * 0.45f, top.y - s * 0.2f, top.x + s * 0.4f, top.y - s * 0.5f, top.x + s * 0.3f, top.y - s * 0.55f); cubicTo(top.x + s * 0.22f, top.y - s * 0.3f, top.x + s * 0.1f, top.y - s * 0.15f, top.x + s * 0.12f, top.y) }, red)
        // Saz leaves.
        drawPath(Path().apply { moveTo(base.x, base.y - s * 0.35f); cubicTo(base.x + s * 0.3f, base.y - s * 0.5f, base.x + s * 0.55f, base.y - s * 0.4f, base.x + s * 0.65f, base.y - s * 0.15f); cubicTo(base.x + s * 0.4f, base.y - s * 0.25f, base.x + s * 0.2f, base.y - s * 0.25f, base.x, base.y - s * 0.35f) }, turq)
        drawPath(Path().apply { moveTo(base.x, base.y - s * 0.2f); cubicTo(base.x - s * 0.3f, base.y - s * 0.35f, base.x - s * 0.55f, base.y - s * 0.25f, base.x - s * 0.65f, base.y); cubicTo(base.x - s * 0.4f, base.y - s * 0.1f, base.x - s * 0.2f, base.y - s * 0.1f, base.x, base.y - s * 0.2f) }, turq)
    }
    artArea {
        tulip(Offset(w * 0.8f, h * 0.95f), h * 0.62f, 0f)
        tulip(Offset(w * 0.64f, h * 0.9f), h * 0.36f, -14f)
        tulip(Offset(w * 0.96f, h * 0.9f), h * 0.36f, 14f)
        for (i in 0 until 6) drawCircle(red, h * 0.012f, Offset(w * (0.62f + i * 0.07f), h * 0.12f))
    }
    panel(Color(0xFF173A78), Edge.ARCH)
    hem(Color.White.copy(alpha = 0.5f))
}

/** Central Asian ikat: woven diamonds with soft, feathered edges. */
private val Ikat = PosterDesign("Ikat", "Uzbekistan", Color(0xFF4A1D40)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF0E6D8))
    val plum = Color(0xFF6B2D5C); val mustard = Color(0xFFD4A017); val teal = Color(0xFF2F7F7A)
    fun diamond(c: Offset, rx: Float, ry: Float, color: Color) {
        // A few slightly shifted copies give ikat's blurred, hand-dyed edge.
        for (k in 0 until 3) {
            val dx = (k - 1) * rx * 0.06f
            drawPath(Path().apply { moveTo(c.x + dx, c.y - ry); lineTo(c.x + rx + dx, c.y); lineTo(c.x + dx, c.y + ry); lineTo(c.x - rx + dx, c.y); close() }, color.copy(alpha = 0.45f))
        }
    }
    artArea {
        val rx = h * 0.11f; val ry = h * 0.2f
        var col = 0
        var x = w * 0.56f
        while (x < w + rx) {
            var y = if (col % 2 == 0) 0f else ry
            while (y < h + ry) {
                val c = Offset(x, y)
                diamond(c, rx, ry, if (col % 2 == 0) plum else teal)
                diamond(c, rx * 0.5f, ry * 0.5f, mustard)
                drawCircle(Color(0xFFF0E6D8), rx * 0.14f, c)
                y += ry * 2
            }
            x += rx * 1.9f; col++
        }
    }
    panel(Color(0xFF4A1D40), Edge.STEP)
    hem(mustard.copy(alpha = 0.6f))
}

/** Javanese batik, the kawung motif: four ovals round a point, like palm fruit. */
private val Batik = PosterDesign("Batik kawung", "Indonesia", Color(0xFF2B1A10)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF5A3A22))
    val cream = Color(0xFFEAD7B7)
    val s = h * 0.22f
    artArea {
        var y = 0f
        while (y < h + s) {
            var x = w * 0.5f
            while (x < w + s) {
                val c = Offset(x, y)
                for (a in listOf(45f, 135f, 225f, 315f)) rotate(a, c) {
                    drawOval(cream.copy(alpha = 0.85f), topLeft = Offset(c.x - s * 0.17f, c.y - s * 0.5f), size = Size(s * 0.34f, s * 0.42f), style = Stroke(h * 0.008f))
                    drawOval(cream.copy(alpha = 0.25f), topLeft = Offset(c.x - s * 0.1f, c.y - s * 0.42f), size = Size(s * 0.2f, s * 0.26f), style = Fill)
                }
                drawCircle(Color(0xFFC9963E), s * 0.06f, c)
                x += s
            }
            y += s
        }
    }
    panel(Color(0xFF2B1A10), Edge.WAVE)
    hem(cream.copy(alpha = 0.45f))
}

/** Otomi embroidery from Hidalgo: one bold colour, flowers and a bird on cream. */
private val Otomi = PosterDesign("Otomi", "Mexico", Color(0xFF8E1450)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFFBF6EE))
    val ink = Color(0xFFC2185B)
    fun flower(c: Offset, r: Float) {
        for (i in 0 until 8) rotate(i * 45f, c) {
            drawOval(ink, topLeft = Offset(c.x - r * 0.22f, c.y - r), size = Size(r * 0.44f, r * 0.7f))
        }
        drawCircle(Color(0xFFFBF6EE), r * 0.3f, c)
        drawCircle(ink, r * 0.18f, c)
    }
    artArea {
        flower(Offset(w * 0.82f, h * 0.36f), h * 0.2f)
        flower(Offset(w * 0.64f, h * 0.78f), h * 0.11f)
        // A bird.
        val b = Offset(w * 0.86f, h * 0.76f); val s = h * 0.18f
        drawPath(Path().apply {
            moveTo(b.x - s, b.y)
            cubicTo(b.x - s * 0.6f, b.y - s * 0.6f, b.x + s * 0.2f, b.y - s * 0.6f, b.x + s * 0.5f, b.y - s * 0.4f)
            lineTo(b.x + s * 0.75f, b.y - s * 0.45f); lineTo(b.x + s * 0.55f, b.y - s * 0.28f)
            cubicTo(b.x + s * 0.4f, b.y + s * 0.2f, b.x - s * 0.2f, b.y + s * 0.3f, b.x - s, b.y)
            close()
        }, ink)
        drawPath(Path().apply { moveTo(b.x - s * 0.1f, b.y - s * 0.3f); lineTo(b.x + s * 0.15f, b.y - s * 1.0f); lineTo(b.x + s * 0.3f, b.y - s * 0.35f); close() }, ink)
        drawCircle(Color(0xFFFBF6EE), s * 0.06f, Offset(b.x + s * 0.42f, b.y - s * 0.38f))
        // Leaves.
        for ((i, a) in listOf(-30f, 20f, 70f).withIndex()) rotate(a, Offset(w * 0.68f, h * 0.2f)) {
            drawOval(ink, topLeft = Offset(w * 0.68f - h * 0.03f, h * 0.2f - h * (0.16f + i * 0.01f)), size = Size(h * 0.06f, h * 0.16f))
        }
    }
    panel(Color(0xFF8E1450), Edge.SLANT)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Madhubani painting from Mithila: a pair of fish, lined and filled, on parchment. */
private val Madhubani = PosterDesign("Madhubani", "India", Color(0xFF7A1F16)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF4E3C1))
    val red = Color(0xFFB23A2E); val black = Color(0xFF1E140C); val yellow = Color(0xFFE8B33A)
    val c = Offset(w * 0.8f, h * 0.5f)
    val sw = h * 0.008f
    fun fish(flip: Boolean) = rotate(if (flip) 180f else 0f, c) {
        val body = Path().apply {
            moveTo(c.x - h * 0.3f, c.y - h * 0.12f)
            cubicTo(c.x - h * 0.1f, c.y - h * 0.34f, c.x + h * 0.22f, c.y - h * 0.3f, c.x + h * 0.3f, c.y - h * 0.12f)
            cubicTo(c.x + h * 0.2f, c.y - h * 0.02f, c.x - h * 0.1f, c.y - h * 0.0f, c.x - h * 0.3f, c.y - h * 0.12f)
            close()
        }
        drawPath(body, yellow)
        drawPath(body, black, style = Stroke(sw))
        // Tail.
        drawPath(Path().apply { moveTo(c.x - h * 0.3f, c.y - h * 0.12f); lineTo(c.x - h * 0.4f, c.y - h * 0.22f); lineTo(c.x - h * 0.4f, c.y - h * 0.02f); close() }, red)
        // Scales: little arcs along the body.
        for (i in 0 until 5) drawArc(red, 0f, 180f, false, topLeft = Offset(c.x - h * 0.15f + i * h * 0.07f, c.y - h * 0.2f), size = Size(h * 0.06f, h * 0.06f), style = Stroke(sw))
        drawCircle(black, h * 0.018f, Offset(c.x + h * 0.2f, c.y - h * 0.16f))
    }
    artArea {
        fish(false); fish(true)
        // Double-line border with a row of red dots between.
        val inset = h * 0.07f
        drawRect(black, topLeft = Offset(w * 0.6f, inset), size = Size(w * 0.4f - inset, h - inset * 2), style = Stroke(sw))
        drawRect(black, topLeft = Offset(w * 0.6f + inset * 0.5f, inset * 1.5f), size = Size(w * 0.4f - inset * 2f, h - inset * 3f), style = Stroke(sw * 0.7f))
    }
    panel(Color(0xFF7A1F16), Edge.STRAIGHT)
    hem(yellow.copy(alpha = 0.5f))
}

/**
 * All 50: the first ten here, twenty more in PosterDesigns2 and twenty fine micro
 * patterns in PosterDesigns3, mixed so two micro patterns rarely come on back-to-back days.
 */
val PosterDesigns: List<PosterDesign> = buildList {
    val bold = listOf(Mandala, Sashiko, Zellige, Kente, Warli, Iznik, Ikat, Batik, Otomi, Madhubani) + MoreDesigns
    var m = 0
    bold.forEachIndexed { i, d ->
        add(d)
        if (i % 3 != 2 && m < MicroDesigns.size) add(MicroDesigns[m++])
    }
    addAll(MicroDesigns.drop(m))
}

/** Debug builds can show a chosen design (for checking each one); -1 = today's. */
val PosterDesignOverride = mutableIntStateOf(-1)

/** Today's design: the next in the list every day, changing at midnight. */
fun posterFor(epochDay: Long): PosterDesign =
    PosterDesigns[PosterDesignOverride.intValue.takeIf { it in PosterDesigns.indices } ?: Math.floorMod(epochDay, PosterDesigns.size.toLong()).toInt()]
