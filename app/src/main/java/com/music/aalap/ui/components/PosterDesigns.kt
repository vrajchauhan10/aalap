package com.music.aalap.ui.components

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

// ── The maker's V, drawn in each design's own way so it belongs to the pattern ──

/** Distance from [p] to the segment [a]–[b]. */
internal fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a; val len2 = ab.x * ab.x + ab.y * ab.y
    val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}

/** A V made of two petals meeting at the bottom, like the petals around it. */
internal fun DrawScope.petalV(c: Offset, size: Float, color: Color) {
    for (side in listOf(-1f, 1f)) rotate(side * 22f, Offset(c.x, c.y + size / 2)) {
        val top = Offset(c.x, c.y - size / 2); val bottom = Offset(c.x, c.y + size / 2); val wd = size * 0.16f
        drawPath(Path().apply {
            moveTo(bottom.x, bottom.y)
            cubicTo(bottom.x + wd, bottom.y - size * 0.35f, top.x + wd * 1.2f, top.y + size * 0.25f, top.x, top.y)
            cubicTo(top.x - wd * 1.2f, top.y + size * 0.25f, bottom.x - wd, bottom.y - size * 0.35f, bottom.x, bottom.y)
            close()
        }, color)
    }
}

/** A V sewn in running stitch, like the thread around it. */
internal fun DrawScope.stitchV(c: Offset, size: Float, color: Color, thread: Float) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(thread * 2.6f, thread * 1.8f))
    val p = Path().apply { moveTo(c.x - size * 0.42f, c.y - size / 2); lineTo(c.x, c.y + size / 2); lineTo(c.x + size * 0.42f, c.y - size / 2) }
    drawPath(p, color, style = Stroke(thread, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round, pathEffect = dash))
}

/** Where the maker's V sits: right of the words, between the shuffle button and the label. */
internal fun DrawScope.vSpot() = Offset(size.width * 0.82f, size.height * 0.6f)

/** The two arms of a V whose bottom is [apex]: (left top, apex, right top). */
internal fun vArms(apex: Offset, spread: Float, rise: Float) =
    Triple(Offset(apex.x - spread, apex.y - rise), apex, Offset(apex.x + spread, apex.y - rise))

/** True when [p] lies within [tol] of the V's arms. */
internal fun Triple<Offset, Offset, Offset>.near(p: Offset, tol: Float) =
    distToSegment(p, first, second) < tol || distToSegment(p, second, third) < tol

/**
 * The centre of the grid cell nearest [target], for the cell grids the designs use
 * (first centre at half a step in, odd rows shifted by [shift]).
 */
internal fun DrawScope.snapToGrid(target: Offset, step: Float, stepY: Float = step, shift: Float = 0f): Offset {
    val j = kotlin.math.round((target.y - stepY / 2) / stepY).toInt().coerceAtLeast(0)
    val x0 = size.width * 0.5f + step / 2 + if (j % 2 == 1) shift else 0f
    val i = kotlin.math.round((target.x - x0) / step).toInt().coerceAtLeast(0)
    return Offset(x0 + i * step, stepY / 2 + j * stepY)
}

/**
 * The cells of a V in a grid: the bottom cell nearest the V spot and [levels] diagonal
 * neighbours up each side ([dx] across and [dy] up per level). Use [hit] while tiling.
 */
internal fun DrawScope.gridV(step: Float, stepY: Float = step, shift: Float = 0f, levels: Int = 2): List<Offset> {
    val apex = snapToGrid(vSpot(), step, stepY, shift)
    val dx = if (shift > 0f) shift else step
    return buildList { add(apex); for (k in 1..levels) { add(Offset(apex.x - dx * k, apex.y - stepY * k)); add(Offset(apex.x + dx * k, apex.y - stepY * k)) } }
}

internal fun List<Offset>.hit(c: Offset, tol: Float = 2f) = any { (it - c).getDistance() < tol }

/**
 * Re-draws the parts of a line that run along the V in [color]: the pattern's own thread
 * traces the V (as in Sashiko). [points] is the line, sampled finely.
 */
internal fun DrawScope.traceV(points: List<Offset>, v: Triple<Offset, Offset, Offset>, tol: Float, color: Color, width: Float) {
    for (k in 1 until points.size) {
        val a = points[k - 1]; val b = points[k]
        if (v.near(Offset((a.x + b.x) / 2, (a.y + b.y) / 2), tol)) drawLine(color, a, b, width, cap = StrokeCap.Round)
    }
}

/** The art's area: the right part of the poster, where the panel isn't. */
internal inline fun DrawScope.artArea(block: DrawScope.() -> Unit) =
    clipRect(left = size.width * 0.5f) { block() }

// ── The designs ─────────────────────────────────────────────────────────────

private val Mandala = PosterDesign("Marigold mandala", "India", Color(0xFFE0661B)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2A1A55))
    val mc = Offset(w * 0.79f, h * 0.46f)
    val mr = h * 0.37f
    // The V lives in the petals: the two outer petals either side of the top are filled
    // with marigold, and with the heart they make a V. The rest are outlined as usual.
    for (a in listOf(-30f, 30f)) rotate(a, mc) {
        drawPath(Path().apply {
            moveTo(mc.x, mc.y - mr * 0.82f)
            cubicTo(mc.x + mr * 0.14f, mc.y - mr * 0.66f, mc.x + mr * 0.14f, mc.y - mr * 0.5f, mc.x, mc.y - mr * 0.42f)
            cubicTo(mc.x - mr * 0.14f, mc.y - mr * 0.5f, mc.x - mr * 0.14f, mc.y - mr * 0.66f, mc.x, mc.y - mr * 0.82f)
        }, Color(0xFFFF8A3D).copy(alpha = 0.85f))
    }
    drawMandala(Color(0xFFFFC56B), mc, mr, h * 0.008f, accent = Color(0xFFFF8A3D))
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
    // Every wave: its centre and size step (outer ring = 3).
    val waves = ArrayList<Pair<Offset, Int>>()
    run {
        var row = 0
        var y = -r * 0.2f
        while (y < h + r) {
            var x = w * 0.48f + if (row % 2 == 0) 0f else r
            while (x < w + r) { for (k in 3 downTo 1) waves += Offset(x, y) to k; x += r * 2 }
            y += r * 0.55f; row++
        }
    }
    // The V comes from the pattern itself: along a V line, the short run of wave stitches
    // lying right on it is sewn in orange thread, so the stitches trace a V.
    val vBottom = Offset(w * 0.84f, h * 0.62f)
    val vPoints = (0..4).flatMap { i -> listOf(-1f, 1f).map { side -> Offset(vBottom.x + side * h * 0.04f * i, vBottom.y - h * 0.08f * i) } }.distinct()
    artArea {
        for ((c, k) in waves) {
            drawArc(
                Color.White.copy(alpha = 0.78f), 180f, 180f, false,
                topLeft = Offset(c.x - r * k / 3f, c.y - r * k / 3f), size = Size(r * 2 * k / 3f, r * 2 * k / 3f), style = thread,
            )
        }
        for (p in vPoints) {
            // The wave stitch passing closest to this point (upper half of a ring only).
            val best = waves.filter { (c, _) -> p.y < c.y }.minByOrNull { (c, k) -> kotlin.math.abs((p - c).getDistance() - r * k / 3f) } ?: continue
            val (c, k) = best
            val angle = Math.toDegrees(kotlin.math.atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
            drawArc(
                Color(0xFFFF8A3D), angle - 20f, 40f, false,
                topLeft = Offset(c.x - r * k / 3f, c.y - r * k / 3f), size = Size(r * 2 * k / 3f, r * 2 * k / 3f),
                style = Stroke(h * 0.009f, cap = StrokeCap.Round),
            )
        }
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
        val stars = ArrayList<Offset>()
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
                stars += c
                x += s * 1.4f
            }
            y += s * 0.85f; j++
        }
        // Five stars, glazed gold, sit in a V: one, and two up each side of it.
        stars.minByOrNull { (it - vSpot()).getDistance() }?.let { apex ->
            val vStars = (0..2).flatMap { k -> listOf(-1f, 1f).map { side -> Offset(apex.x + side * s * 0.7f * k, apex.y - s * 0.85f * k) } }
            for (c in stars) if (vStars.any { (it - c).getDistance() < s * 0.1f }) {
                for (a in listOf(0f, 45f)) rotate(a, c) {
                    drawRect(Color(0xFFE0B04A), topLeft = Offset(c.x - s * 0.28f, c.y - s * 0.28f), size = Size(s * 0.56f, s * 0.56f))
                }
                drawCircle(Color(0xFFF3E9D2), s * 0.14f, c)
                drawCircle(Color(0xFF1E7A6E), s * 0.06f, c)
            }
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
                val spot = vSpot()
                if (spot.x in x..x + b && spot.y in y..y + b) {
                    // This block is woven in chevrons, so its stripes make a V.
                    drawRect(Color(0xFF1B1B1B), Offset(x, y), Size(b, b))
                    for (k in 0 until 5) {
                        val dy = b * (0.18f + k * 0.15f)
                        drawPath(Path().apply { moveTo(x + b * 0.1f, y + dy - b * 0.3f); lineTo(x + b / 2, y + dy + b * 0.12f); lineTo(x + b * 0.9f, y + dy - b * 0.3f) },
                            if (k == 2) gold else colors[(k + 1) % colors.size], style = Stroke(b / n * 0.8f))
                    }
                    x += b; col++; continue
                }
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
    fun figure(at: Offset, s: Float, tilt: Float, armsUp: Boolean = false) = rotate(tilt, at) {
        drawCircle(ink, s * 0.18f, Offset(at.x, at.y - s * 0.62f))
        // Two triangles meeting at the waist.
        drawPath(Path().apply { moveTo(at.x - s * 0.26f, at.y - s * 0.42f); lineTo(at.x + s * 0.26f, at.y - s * 0.42f); lineTo(at.x, at.y); close() }, ink)
        drawPath(Path().apply { moveTo(at.x, at.y); lineTo(at.x - s * 0.24f, at.y + s * 0.4f); lineTo(at.x + s * 0.24f, at.y + s * 0.4f); close() }, ink)
        if (armsUp) {
            // Both arms thrown up in joy: a V over the head.
            val arm = Color(0xFFF2C14E)
            drawLine(arm, Offset(at.x - s * 0.2f, at.y - s * 0.4f), Offset(at.x - s * 0.5f, at.y - s * 1.0f), sw * 1.6f, cap = StrokeCap.Round)
            drawLine(arm, Offset(at.x + s * 0.2f, at.y - s * 0.4f), Offset(at.x + s * 0.5f, at.y - s * 1.0f), sw * 1.6f, cap = StrokeCap.Round)
        } else {
            drawLine(ink, Offset(at.x - s * 0.24f, at.y - s * 0.36f), Offset(at.x - s * 0.5f, at.y - s * 0.12f), sw)
            drawLine(ink, Offset(at.x + s * 0.24f, at.y - s * 0.36f), Offset(at.x + s * 0.5f, at.y - s * 0.12f), sw)
        }
        drawLine(ink, Offset(at.x - s * 0.14f, at.y + s * 0.4f), Offset(at.x - s * 0.2f, at.y + s * 0.72f), sw)
        drawLine(ink, Offset(at.x + s * 0.14f, at.y + s * 0.4f), Offset(at.x + s * 0.2f, at.y + s * 0.72f), sw)
    }
    artArea {
        val n = 11
        for (i in 0 until n) {
            val a = (i / n.toFloat()) * 2 * PI
            figure(Offset(c.x + cos(a).toFloat() * ring, c.y + sin(a).toFloat() * ring), h * 0.13f, (a * 180 / PI).toFloat() + 90f)
        }
        figure(c, h * 0.16f, 0f, armsUp = true)
        drawCircle(ink, ring * 1.32f, c, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, h * 0.03f)), cap = StrokeCap.Round))
    }
    panel(Color(0xFF4A1E10), Edge.STRAIGHT)
    drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.66f + h * 0.02f, 0f), Offset(w * 0.66f + h * 0.02f, h), strokeWidth = sw,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, h * 0.03f)), cap = StrokeCap.Round)
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
        val centres = ArrayList<Offset>()
        var col = 0
        var x = w * 0.56f
        while (x < w + rx) {
            var y = if (col % 2 == 0) 0f else ry
            while (y < h + ry) {
                val c = Offset(x, y)
                diamond(c, rx, ry, if (col % 2 == 0) plum else teal)
                diamond(c, rx * 0.5f, ry * 0.5f, mustard)
                drawCircle(Color(0xFFF0E6D8), rx * 0.14f, c)
                centres += c
                y += ry * 2
            }
            x += rx * 1.9f; col++
        }
        // In one diamond the lower edges of the small one are dyed deep red, feathered
        // like the rest: a V.
        centres.minByOrNull { (it - vSpot()).getDistance() }?.let { c ->
            for (k in 0 until 3) {
                val dx = (k - 1) * rx * 0.05f
                drawPath(Path().apply { moveTo(c.x - rx * 0.62f + dx, c.y - ry * 0.12f); lineTo(c.x + dx, c.y + ry * 0.55f); lineTo(c.x + rx * 0.62f + dx, c.y - ry * 0.12f) },
                    Color(0xFFB0283A).copy(alpha = 0.55f), style = Stroke(h * 0.016f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
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
        val spot = vSpot()
        var vc: Offset? = null
        var y = 0f
        while (y < h + s) {
            var x = w * 0.5f
            while (x < w + s) {
                val c = Offset(x, y)
                if (vc == null || (c - spot).getDistance() < (vc!! - spot).getDistance()) vc = c
                for (a in listOf(45f, 135f, 225f, 315f)) rotate(a, c) {
                    drawOval(cream.copy(alpha = 0.85f), topLeft = Offset(c.x - s * 0.17f, c.y - s * 0.5f), size = Size(s * 0.34f, s * 0.42f), style = Stroke(h * 0.008f))
                    drawOval(cream.copy(alpha = 0.25f), topLeft = Offset(c.x - s * 0.1f, c.y - s * 0.42f), size = Size(s * 0.2f, s * 0.26f), style = Fill)
                }
                drawCircle(Color(0xFFC9963E), s * 0.06f, c)
                x += s
            }
            y += s
        }
        // One kawung's two upper ovals are filled gold: they lean apart in a V.
        vc?.let { c ->
            for (a in listOf(315f, 45f)) rotate(a, c) {
                drawOval(Color(0xFFE2B04A), topLeft = Offset(c.x - s * 0.17f, c.y - s * 0.5f), size = Size(s * 0.34f, s * 0.42f))
                drawOval(Color(0xFF5A3A22), topLeft = Offset(c.x - s * 0.17f, c.y - s * 0.5f), size = Size(s * 0.34f, s * 0.42f), style = Stroke(h * 0.004f))
            }
        }
    }
    panel(Color(0xFF2B1A10), Edge.WAVE)
    hem(cream.copy(alpha = 0.45f))
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
        // Madhubani fills its ground with fine dots; a V of them is painted red.
        val v = vArms(Offset(c.x + h * 0.02f, c.y + h * 0.04f), h * 0.06f, h * 0.12f)
        var yy = h * 0.04f
        while (yy < h) {
            var xx = w * 0.6f + h * 0.02f
            while (xx < w) {
                val d = Offset(xx, yy)
                if (v.near(d, h * 0.012f)) drawCircle(red, h * 0.008f, d) else drawCircle(black.copy(alpha = 0.35f), h * 0.005f, d)
                xx += h * 0.035f
            }
            yy += h * 0.035f
        }
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
 * All 100: bold crafts (here, PosterDesigns2 and PosterDesigns5) and fine micro patterns
 * (PosterDesigns3, PosterDesigns4 and PosterDesigns5), spread evenly through the days.
 */
val PosterDesigns: List<PosterDesign> = buildList {
    val bold = listOf(Mandala, Sashiko, Zellige, Kente, Warli, Ikat, Batik, Madhubani) + MoreDesigns + HeritageBoldDesigns
    val micro = MicroDesigns + GeometricMicroDesigns + HeritageMicroDesigns + WorldMicroDesigns
    // Spread the two kinds evenly through the days.
    var b = 0; var m = 0
    while (b < bold.size || m < micro.size) {
        val takeMicro = m < micro.size && (b >= bold.size || m * bold.size < b * micro.size)
        if (takeMicro) add(micro[m++]) else add(bold[b++])
    }
}

/** Debug builds can show a chosen design (for checking each one); -1 = today's. */
val PosterDesignOverride = mutableIntStateOf(-1)

/** Today's design: the next in the list every day, changing at midnight. */
fun posterFor(epochDay: Long): PosterDesign =
    PosterDesigns[PosterDesignOverride.intValue.takeIf { it in PosterDesigns.indices } ?: Math.floorMod(epochDay, PosterDesigns.size.toLong()).toInt()]
