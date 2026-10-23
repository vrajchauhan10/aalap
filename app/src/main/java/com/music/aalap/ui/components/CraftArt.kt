package com.music.aalap.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.music.aalap.data.api.Mood
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hand-drawn Indian craft motifs for Aalap's cards: rangoli, paisley, bandhani,
 * mirror work, block print, lotus, mandala, jaali and leheriya. Drawn as vectors,
 * so they're crisp at any size and tint to the card.
 */
enum class Craft(val label: String) {
    RANGOLI("Rangoli"), PAISLEY("Paisley"), BANDHANI("Bandhani"), MIRROR("Mirror work"),
    BLOCK("Block print"), LOTUS("Diya and lotus"), MANDALA("Mandala"), JAALI("Jaali"), LEHERIYA("Leheriya"),
    KOLAM("Kolam"), WARLI("Warli"), MADHUBANI("Madhubani"), PHULKARI("Phulkari"), AJRAK("Ajrak"),
    TORAN("Toran"), PATOLA("Patola"), DHOKRA("Dhokra"),
}

/** The craft and colours for each mood (background, motif). */
fun Mood.craft(): Craft = when (this) {
    Mood.ENERGETIC -> Craft.LEHERIYA
    Mood.HAPPY -> Craft.RANGOLI
    Mood.SAD -> Craft.BANDHANI
    Mood.HEARTBROKEN -> Craft.MADHUBANI
    Mood.ROMANTIC -> Craft.PAISLEY
    Mood.CHILL -> Craft.BLOCK
    Mood.PARTY -> Craft.MIRROR
    Mood.DEVOTIONAL -> Craft.LOTUS
    Mood.FOCUS -> Craft.JAALI
    Mood.ROAD_TRIP -> Craft.TORAN
}

/** Motif colour for a card colour: a light tint of it. */
fun craftInk(base: Color): Color = lerp(base, Color(0xFFFFF3E0), 0.72f)

/**
 * Draws [craft] on a card of [background]. The motif sits toward the top-right so text
 * can go bottom-left.
 */
@Composable
fun CraftCard(craft: Craft, background: Color, modifier: Modifier, ink: Color = craftInk(background)) {
    Canvas(modifier) {
        drawRect(background)
        // A soft shade low on the card so the white title always reads.
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
            0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.32f),
        ))
        // The motif stays in the top-right corner, clear of the title at the bottom-left.
        clipRect(left = size.width * 0.40f, bottom = size.height * 0.62f) {
            drawCraft(craft, ink.copy(alpha = 0.9f), center = Offset(size.width * 0.74f, size.height * 0.30f), radius = min(size.width, size.height) * 0.24f)
        }
    }
}

fun DrawScope.drawCraft(craft: Craft, ink: Color, center: Offset, radius: Float) {
    val sw = radius * 0.06f
    val line = Stroke(width = sw, cap = StrokeCap.Round)
    when (craft) {
        Craft.RANGOLI -> {
            drawCircle(ink, radius, center, style = Stroke(sw, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 1.5f, sw * 3f))))
            drawCircle(ink, radius * 0.62f, center, style = line)
            drawCircle(ink, radius * 0.22f, center)
            for (i in 0 until 8) rotate(i * 45f, center) {
                val p = Path().apply {
                    moveTo(center.x, center.y - radius * 0.98f)
                    lineTo(center.x + radius * 0.2f, center.y - radius * 0.62f)
                    lineTo(center.x, center.y - radius * 0.4f)
                    lineTo(center.x - radius * 0.2f, center.y - radius * 0.62f); close()
                }
                drawPath(p, ink.copy(alpha = if (i % 2 == 0) 1f else 0.6f))
            }
        }
        Craft.PAISLEY -> {
            fun paisley(c: Offset, r: Float, a: Float) = rotate(a, c) {
                val p = Path().apply {
                    moveTo(c.x, c.y + r)
                    cubicTo(c.x - r * 1.3f, c.y + r * 0.6f, c.x - r * 1.0f, c.y - r * 1.2f, c.x + r * 0.2f, c.y - r * 1.1f)
                    cubicTo(c.x + r * 0.9f, c.y - r * 1.0f, c.x + r * 0.8f, c.y - r * 0.2f, c.x + r * 0.2f, c.y - r * 0.2f)
                    cubicTo(c.x - r * 0.2f, c.y - r * 0.2f, c.x - r * 0.1f, c.y + r * 0.5f, c.x, c.y + r)
                }
                drawPath(p, ink, style = line)
                drawCircle(ink, r * 0.14f, Offset(c.x + r * 0.05f, c.y - r * 0.55f))
            }
            paisley(center, radius * 0.75f, -20f)
            paisley(Offset(center.x - radius * 1.1f, center.y + radius * 0.5f), radius * 0.42f, 25f)
            drawCircle(ink, sw * 1.4f, Offset(center.x + radius * 0.9f, center.y + radius * 0.9f))
            drawCircle(ink, sw, Offset(center.x + radius * 1.15f, center.y + radius * 0.65f))
        }
        Craft.BANDHANI -> {
            val step = radius * 0.34f
            for (row in -3..3) for (col in -3..3) {
                if ((row + col) % 2 != 0) continue
                val o = Offset(center.x + col * step, center.y + row * step)
                if ((o - center).getDistance() > radius * 1.25f) continue
                drawCircle(ink, step * 0.24f, o, style = Stroke(sw * 0.9f))
                drawCircle(ink, step * 0.07f, o)
            }
        }
        Craft.MIRROR -> {
            listOf(Offset(0f, 0f) to 0.42f, Offset(0.8f, 0.55f) to 0.3f, Offset(-0.75f, 0.6f) to 0.26f, Offset(0.55f, -0.75f) to 0.22f).forEach { (d, r) ->
                val c = Offset(center.x + d.x * radius, center.y + d.y * radius)
                drawCircle(ink, radius * r, c, style = line)
                drawCircle(ink.copy(alpha = 0.55f), radius * r * 0.55f, c)
                for (k in 0 until 10) {
                    val a = k * 36.0 * Math.PI / 180
                    val o = Offset(c.x + (radius * r * 1.25f * cos(a)).toFloat(), c.y + (radius * r * 1.25f * sin(a)).toFloat())
                    drawCircle(ink, sw * 0.7f, o)
                }
            }
        }
        Craft.BLOCK -> {
            val step = radius * 0.36f
            for (row in -3..3) {
                val y = center.y + row * step
                val p = Path().apply {
                    moveTo(center.x - radius * 1.4f, y)
                    var x = center.x - radius * 1.4f
                    while (x < center.x + radius * 1.6f) { quadraticBezierTo(x + step * 0.5f, y - step * 0.45f, x + step, y); x += step; quadraticBezierTo(x + step * 0.5f, y + step * 0.45f, x + step, y); x += step }
                }
                drawPath(p, ink, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Craft.LOTUS -> {
            fun petal(a: Float, len: Float, wid: Float) = rotate(a, Offset(center.x, center.y + radius * 0.55f)) {
                val b = Offset(center.x, center.y + radius * 0.55f)
                val p = Path().apply {
                    moveTo(b.x, b.y)
                    cubicTo(b.x - wid, b.y - len * 0.4f, b.x - wid * 0.4f, b.y - len * 0.9f, b.x, b.y - len)
                    cubicTo(b.x + wid * 0.4f, b.y - len * 0.9f, b.x + wid, b.y - len * 0.4f, b.x, b.y)
                }
                drawPath(p, ink, style = line)
            }
            petal(0f, radius * 1.2f, radius * 0.45f); petal(-38f, radius * 1.0f, radius * 0.4f); petal(38f, radius * 1.0f, radius * 0.4f)
            petal(-70f, radius * 0.75f, radius * 0.3f); petal(70f, radius * 0.75f, radius * 0.3f)
            // diya flame above
            val f = Offset(center.x, center.y - radius * 0.95f)
            val flame = Path().apply {
                moveTo(f.x, f.y - radius * 0.35f)
                cubicTo(f.x + radius * 0.2f, f.y - radius * 0.1f, f.x + radius * 0.12f, f.y + radius * 0.1f, f.x, f.y + radius * 0.1f)
                cubicTo(f.x - radius * 0.12f, f.y + radius * 0.1f, f.x - radius * 0.2f, f.y - radius * 0.1f, f.x, f.y - radius * 0.35f)
            }
            drawPath(flame, ink)
        }
        Craft.MANDALA -> drawMandala(ink, center, radius, sw)
        Craft.KOLAM, Craft.WARLI, Craft.MADHUBANI, Craft.PHULKARI, Craft.AJRAK,
        Craft.TORAN, Craft.PATOLA, Craft.DHOKRA -> drawMore(craft, ink, center, radius, sw)
        Craft.JAALI -> {
            val step = radius * 0.42f
            for (row in -3..3) for (col in -3..3) {
                val o = Offset(center.x + col * step, center.y + row * step)
                if ((o - center).getDistance() > radius * 1.3f) continue
                val d = Path().apply {
                    moveTo(o.x, o.y - step * 0.5f); lineTo(o.x + step * 0.5f, o.y); lineTo(o.x, o.y + step * 0.5f); lineTo(o.x - step * 0.5f, o.y); close()
                }
                drawPath(d, ink, style = Stroke(sw * 0.8f))
                drawCircle(ink, sw * 0.8f, o)
            }
        }
        Craft.LEHERIYA -> {
            // Rajasthani tie-dye: diagonal waves in alternating weights.
            for (i in -6..6) {
                val off = i * radius * 0.28f
                val p = Path().apply {
                    moveTo(center.x - radius * 1.6f + off, center.y + radius * 1.4f)
                    var t = 0f
                    while (t < 1f) {
                        val x = center.x - radius * 1.6f + off + t * radius * 2.8f
                        val y = center.y + radius * 1.4f - t * radius * 2.8f
                        quadraticBezierTo(x + radius * 0.14f, y - radius * 0.02f, x + radius * 0.14f, y - radius * 0.14f)
                        t += 0.1f
                    }
                }
                drawPath(p, ink.copy(alpha = if (i % 2 == 0) 0.95f else 0.45f), style = Stroke(sw * if (i % 2 == 0) 1.2f else 0.8f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawMore(craft: Craft, ink: Color, center: Offset, radius: Float, sw: Float) {
    val line = Stroke(width = sw, cap = StrokeCap.Round)
    when (craft) {
        Craft.KOLAM -> {
            // Tamil kolam: a dot grid with a loop drawn around every dot.
            val step = radius * 0.5f
            for (r in -2..2) for (c in -2..2) {
                if (kotlin.math.abs(r) + kotlin.math.abs(c) > 3) continue
                val o = Offset(center.x + c * step, center.y + r * step)
                drawCircle(ink, sw * 0.9f, o)
                rotate(45f, o) { drawRoundRect(ink, Offset(o.x - step * 0.38f, o.y - step * 0.38f), androidx.compose.ui.geometry.Size(step * 0.76f, step * 0.76f), androidx.compose.ui.geometry.CornerRadius(step * 0.3f), style = Stroke(sw * 0.8f)) }
            }
        }
        Craft.WARLI -> {
            // Warli dancers in a ring.
            for (i in 0 until 7) {
                val a = Math.toRadians(i * 360.0 / 7 - 90)
                val o = Offset(center.x + (radius * 0.85f * cos(a)).toFloat(), center.y + (radius * 0.85f * sin(a)).toFloat())
                rotate((i * 360f / 7), o) {
                    val u = radius * 0.13f
                    drawCircle(ink, u * 0.45f, Offset(o.x, o.y - u * 1.5f))
                    drawPath(Path().apply { moveTo(o.x - u * 0.6f, o.y - u); lineTo(o.x + u * 0.6f, o.y - u); lineTo(o.x, o.y); close() }, ink)
                    drawPath(Path().apply { moveTo(o.x, o.y); lineTo(o.x - u * 0.6f, o.y + u); lineTo(o.x + u * 0.6f, o.y + u); close() }, ink)
                    drawLine(ink, Offset(o.x - u * 0.6f, o.y - u), Offset(o.x - u * 1.3f, o.y - u * 0.3f), sw * 0.8f, StrokeCap.Round)
                    drawLine(ink, Offset(o.x + u * 0.6f, o.y - u), Offset(o.x + u * 1.3f, o.y - u * 0.3f), sw * 0.8f, StrokeCap.Round)
                }
            }
            drawCircle(ink, radius * 0.18f, center, style = line)
        }
        Craft.MADHUBANI -> {
            // Madhubani fish with scales and a double outline.
            val body = Path().apply {
                moveTo(center.x - radius, center.y)
                quadraticBezierTo(center.x - radius * 0.1f, center.y - radius * 0.75f, center.x + radius * 0.6f, center.y)
                quadraticBezierTo(center.x - radius * 0.1f, center.y + radius * 0.75f, center.x - radius, center.y)
            }
            drawPath(body, ink, style = line)
            drawPath(Path().apply { moveTo(center.x + radius * 0.6f, center.y); lineTo(center.x + radius, center.y - radius * 0.35f); lineTo(center.x + radius, center.y + radius * 0.35f); close() }, ink, style = line)
            drawCircle(ink, radius * 0.07f, Offset(center.x - radius * 0.7f, center.y - radius * 0.05f))
            for (k in 0 until 4) {
                val x = center.x - radius * 0.45f + k * radius * 0.25f
                drawArc(ink, 90f, 180f, false, Offset(x - radius * 0.12f, center.y - radius * 0.25f), androidx.compose.ui.geometry.Size(radius * 0.24f, radius * 0.5f), style = Stroke(sw * 0.8f))
            }
        }
        Craft.PHULKARI -> {
            // Punjabi phulkari: eight-point star flowers.
            listOf(Offset(0f, 0f) to 0.5f, Offset(-0.9f, 0.5f) to 0.3f, Offset(0.75f, -0.75f) to 0.28f, Offset(0.8f, 0.75f) to 0.24f).forEach { (d, r) ->
                val c = Offset(center.x + d.x * radius, center.y + d.y * radius)
                val sz = radius * r
                drawRect(ink, Offset(c.x - sz / 2, c.y - sz / 2), androidx.compose.ui.geometry.Size(sz, sz), style = Stroke(sw))
                rotate(45f, c) { drawRect(ink, Offset(c.x - sz / 2, c.y - sz / 2), androidx.compose.ui.geometry.Size(sz, sz), style = Stroke(sw)) }
                drawCircle(ink, sz * 0.16f, c)
            }
        }
        Craft.AJRAK -> {
            // Ajrak: four-petal stars in a tiled grid with borders.
            val step = radius * 0.62f
            for (r in -1..1) for (c in -1..1) {
                val o = Offset(center.x + c * step, center.y + r * step)
                drawRect(ink, Offset(o.x - step / 2, o.y - step / 2), androidx.compose.ui.geometry.Size(step, step), style = Stroke(sw * 0.6f))
                for (k in 0 until 4) rotate(k * 90f, o) {
                    drawPath(Path().apply {
                        moveTo(o.x, o.y); quadraticBezierTo(o.x + step * 0.16f, o.y - step * 0.2f, o.x, o.y - step * 0.4f)
                        quadraticBezierTo(o.x - step * 0.16f, o.y - step * 0.2f, o.x, o.y)
                    }, ink)
                }
            }
        }
        Craft.TORAN -> {
            // Door toran: a string with hanging mango leaves and bells.
            val top = center.y - radius * 0.8f
            drawLine(ink, Offset(center.x - radius * 1.5f, top), Offset(center.x + radius * 1.5f, top), sw)
            for (k in -4..4) {
                val x = center.x + k * radius * 0.34f
                if (k % 2 == 0) {
                    drawPath(Path().apply {
                        moveTo(x, top); quadraticBezierTo(x + radius * 0.16f, top + radius * 0.5f, x, top + radius * 0.9f)
                        quadraticBezierTo(x - radius * 0.16f, top + radius * 0.5f, x, top)
                    }, ink, style = line)
                } else {
                    drawLine(ink, Offset(x, top), Offset(x, top + radius * 0.4f), sw * 0.7f)
                    drawCircle(ink, radius * 0.08f, Offset(x, top + radius * 0.5f))
                }
            }
        }
        Craft.PATOLA -> {
            // Patola ikat: nested stepped diamonds.
            for (k in 1..4) {
                val r = radius * 0.28f * k
                drawPath(Path().apply {
                    moveTo(center.x, center.y - r); lineTo(center.x + r, center.y); lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close()
                }, ink.copy(alpha = if (k % 2 == 0) 1f else 0.55f), style = Stroke(sw * if (k % 2 == 0) 1.3f else 0.9f))
            }
            drawCircle(ink, radius * 0.08f, center)
        }
        Craft.DHOKRA -> {
            // Dhokra sun: spiral centre with wavy rays.
            val spiral = Path()
            var a = 0.0; var first = true
            while (a < 6 * Math.PI) {
                val r = radius * 0.06f + radius * 0.07f * (a / (2 * Math.PI)).toFloat()
                val p = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
                if (first) { spiral.moveTo(p.x, p.y); first = false } else spiral.lineTo(p.x, p.y)
                a += 0.2
            }
            drawPath(spiral, ink, style = line)
            drawCircle(ink, radius * 0.5f, center, style = line)
            for (k in 0 until 12) rotate(k * 30f, center) {
                drawPath(Path().apply {
                    moveTo(center.x, center.y - radius * 0.58f)
                    quadraticBezierTo(center.x + radius * 0.1f, center.y - radius * 0.75f, center.x, center.y - radius * 0.9f)
                    quadraticBezierTo(center.x - radius * 0.1f, center.y - radius * 1.02f, center.x, center.y - radius * 1.12f)
                }, ink, style = Stroke(sw * 0.9f, cap = StrokeCap.Round))
            }
        }
        else -> {}
    }
}

/** A marigold mandala: dotted rings, petals and leaves. Also the hero of the Today poster. */
fun DrawScope.drawMandala(ink: Color, center: Offset, radius: Float, sw: Float, accent: Color = ink) {
    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 1.2f, sw * 3.2f))
    drawCircle(ink, radius, center, style = Stroke(sw, pathEffect = dash))
    drawCircle(ink, radius * 0.84f, center, style = Stroke(sw))
    drawCircle(ink, radius * 0.6f, center, style = Stroke(sw * 0.9f, pathEffect = dash))
    drawCircle(ink, radius * 0.38f, center, style = Stroke(sw))
    drawCircle(ink, radius * 0.14f, center)
    for (i in 0 until 12) rotate(i * 30f, center) {
        val p = Path().apply {
            moveTo(center.x, center.y - radius * 0.82f)
            cubicTo(center.x + radius * 0.14f, center.y - radius * 0.66f, center.x + radius * 0.14f, center.y - radius * 0.5f, center.x, center.y - radius * 0.42f)
            cubicTo(center.x - radius * 0.14f, center.y - radius * 0.5f, center.x - radius * 0.14f, center.y - radius * 0.66f, center.x, center.y - radius * 0.82f)
        }
        drawPath(p, ink, style = Stroke(sw))
    }
    for (i in 0 until 8) rotate(i * 45f + 22.5f, center) {
        val p = Path().apply {
            moveTo(center.x, center.y - radius * 0.56f)
            cubicTo(center.x + radius * 0.08f, center.y - radius * 0.48f, center.x + radius * 0.08f, center.y - radius * 0.42f, center.x, center.y - radius * 0.38f)
            cubicTo(center.x - radius * 0.08f, center.y - radius * 0.42f, center.x - radius * 0.08f, center.y - radius * 0.48f, center.x, center.y - radius * 0.56f)
        }
        drawPath(p, accent, style = Fill)
    }
}
