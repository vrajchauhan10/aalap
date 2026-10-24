package com.music.aalap.ui.components

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

// Twenty more fine patterns from around the world (5.3). As in the rest, the maker's V
// is never drawn on top: it's five of the pattern's own motifs, dyed or glazed in
// another of the craft's colours, sitting in a V.

private inline fun DrawScope.motifs(step: Float, stepY: Float = step, shift: Float = 0f, block: (Offset, Int, Int) -> Unit) {
    var j = 0
    var y = stepY / 2f
    while (y < size.height + stepY) {
        var i = 0
        var x = size.width * 0.5f + step / 2f + if (j % 2 == 1) shift else 0f
        while (x < size.width + step) { block(Offset(x, y), i, j); x += step; i++ }
        y += stepY; j++
    }
}

private fun Path.poly(points: List<Offset>) = apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }

private fun ring(c: Offset, r: Float, n: Int, turn: Double = -PI / 2) =
    (0 until n).map { k -> val a = turn + 2 * PI * k / n; Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r) }

/** Sakura komon: tiny cherry blossoms on indigo; a V of them in gold. */
private val SakuraKomon = PosterDesign("Sakura komon", "Japan", Color(0xFF141E3C)) {
    val h = size.height; val s = h * 0.09f
    drawRect(Color(0xFF1D2B53))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFFFC56B) else Color(0xFFF6D6E0)
            for (p in ring(c, s * 0.13f, 5)) drawCircle(col, s * 0.1f, p)
            drawCircle(Color(0xFFE5397A), s * 0.05f, c)
        }
    }
    panel(Color(0xFF141E3C), Edge.WAVE)
    hem(Color(0xFFF6D6E0).copy(alpha = 0.35f))
}

/** Ichimatsu: the chequerboard of kimono and samurai crests; a V of squares in gold. */
private val Ichimatsu = PosterDesign("Ichimatsu", "Japan", Color(0xFF13281E)) {
    val h = size.height; val s = h * 0.08f
    drawRect(Color(0xFF1F4A37))
    artArea {
        val v = gridV(s)
        motifs(s) { c, i, j ->
            val gold = v.hit(c, s * 0.3f)
            if (gold || (i + j) % 2 == 0) drawRect(if (gold) Color(0xFFD9B25F) else Color(0xFF3C8A68), Offset(c.x - s / 2, c.y - s / 2), Size(s, s))
        }
    }
    panel(Color(0xFF13281E), Edge.STRAIGHT)
    hem(Color(0xFFD9B25F).copy(alpha = 0.4f))
}

/** Uroko: rows of fish-scale triangles; a V of them in red. */
private val Uroko = PosterDesign("Uroko", "Japan", Color(0xFF1A1A1A)) {
    val h = size.height; val s = h * 0.1f; val sy = s * 0.866f
    drawRect(Color(0xFF2B2B2B))
    artArea {
        val v = gridV(s, sy, shift = s / 2)
        motifs(s, sy, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFC0392B) else Color(0xFFC9A227)
            drawPath(Path().poly(listOf(Offset(c.x, c.y - sy * 0.45f), Offset(c.x + s * 0.45f, c.y + sy * 0.45f), Offset(c.x - s * 0.45f, c.y + sy * 0.45f))), col)
        }
    }
    panel(Color(0xFF1A1A1A), Edge.STEP)
    hem(Color(0xFFC9A227).copy(alpha = 0.4f))
}

/** Pois, the French polka dot: white on navy; a V of them in red. */
private val Pois = PosterDesign("Pois", "France", Color(0xFF14243C)) {
    val h = size.height; val s = h * 0.075f
    drawRect(Color(0xFF1D3557))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ -> drawCircle(if (v.hit(c, s * 0.3f)) Color(0xFFE63946) else Color(0xFFF1FAEE), s * 0.18f, c) }
    }
    panel(Color(0xFF14243C), Edge.ARCH)
    hem(Color(0xFFF1FAEE).copy(alpha = 0.35f))
}

/** Iznik tiles: small cobalt tulips; a V of them in Iznik's famous tomato red. */
private val Iznik = PosterDesign("Iznik tulips", "Türkiye", Color(0xFF13294B)) {
    val h = size.height; val s = h * 0.12f
    drawRect(Color(0xFFF7F3EA))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ ->
            val cup = if (v.hit(c, s * 0.3f)) Color(0xFFC0392B) else Color(0xFF1F4E9E)
            drawLine(Color(0xFF2A9D8F), Offset(c.x, c.y + s * 0.05f), Offset(c.x, c.y + s * 0.35f), h * 0.006f, cap = StrokeCap.Round)
            drawOval(Color(0xFF2A9D8F), Offset(c.x - s * 0.2f, c.y + s * 0.14f), Size(s * 0.16f, s * 0.08f))
            drawPath(Path().apply {
                moveTo(c.x - s * 0.15f, c.y - s * 0.2f); lineTo(c.x - s * 0.05f, c.y - s * 0.08f); lineTo(c.x, c.y - s * 0.24f)
                lineTo(c.x + s * 0.05f, c.y - s * 0.08f); lineTo(c.x + s * 0.15f, c.y - s * 0.2f)
                quadraticBezierTo(c.x + s * 0.16f, c.y + s * 0.06f, c.x, c.y + s * 0.06f)
                quadraticBezierTo(c.x - s * 0.16f, c.y + s * 0.06f, c.x - s * 0.15f, c.y - s * 0.2f); close()
            }, cup)
        }
    }
    panel(Color(0xFF13294B), Edge.SLANT)
    hem(Color(0xFF1F4E9E).copy(alpha = 0.35f))
}

/** Delft tiles: blue-framed tiles with a star; in a V of them the star is glazed orange. */
private val Delft = PosterDesign("Delft tiles", "Netherlands", Color(0xFF15305B)) {
    val h = size.height; val s = h * 0.15f
    val blue = Color(0xFF2E5AA8)
    drawRect(Color(0xFFF4F6FA))
    artArea {
        val v = gridV(s, levels = 1)
        motifs(s) { c, _, _ ->
            drawRect(blue.copy(alpha = 0.5f), Offset(c.x - s / 2, c.y - s / 2), Size(s, s), style = Stroke(h * 0.004f))
            for (corner in ring(c, s * 0.62f, 4, PI / 4)) drawCircle(blue, s * 0.06f, Offset(c.x + (corner.x - c.x) * 0.75f, c.y + (corner.y - c.y) * 0.75f))
            val star = if (v.hit(c, s * 0.3f)) Color(0xFFE07A2E) else blue
            drawPath(Path().poly(ring(c, s * 0.24f, 8).mapIndexed { k, p -> if (k % 2 == 0) p else Offset(c.x + (p.x - c.x) * 0.4f, c.y + (p.y - c.y) * 0.4f) }), star)
        }
    }
    panel(Color(0xFF15305B), Edge.STRAIGHT)
    hem(Color.White.copy(alpha = 0.45f))
}

/** Pysanky: eight-pointed stars as on Ukrainian Easter eggs; a V of them in red. */
private val Pysanky = PosterDesign("Pysanky stars", "Ukraine", Color(0xFF2A0E0E)) {
    val h = size.height; val s = h * 0.11f
    drawRect(Color(0xFF141414))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFD62828) else Color(0xFFF2C14E)
            for (a in listOf(0f, 45f)) rotate(a, c) { drawRect(col, Offset(c.x - s * 0.2f, c.y - s * 0.2f), Size(s * 0.4f, s * 0.4f)) }
            drawCircle(Color(0xFF141414), s * 0.07f, c)
        }
    }
    panel(Color(0xFF2A0E0E), Edge.WAVE)
    hem(Color(0xFFF2C14E).copy(alpha = 0.35f))
}

/** Talavera tiles from Puebla: blue four-petal rosettes; a V of them in yellow. */
private val Talavera = PosterDesign("Talavera", "Mexico", Color(0xFF0E2F6B)) {
    val h = size.height; val s = h * 0.12f
    val blue = Color(0xFF1B4BA0); val yellow = Color(0xFFF2B705)
    drawRect(Color(0xFFFBF6EA))
    artArea {
        val v = gridV(s)
        motifs(s) { c, _, _ ->
            val isV = v.hit(c, s * 0.3f)
            for (p in ring(c, s * 0.17f, 4, PI / 4)) drawCircle(if (isV) yellow else blue, s * 0.13f, p)
            drawCircle(if (isV) blue else yellow, s * 0.08f, c)
        }
    }
    panel(Color(0xFF0E2F6B), Edge.ARCH)
    hem(yellow.copy(alpha = 0.45f))
}

/** Liberty print: a scatter of tiny flowers on teal; a V of them in mustard. */
private val Liberty = PosterDesign("Liberty florals", "England", Color(0xFF0E2A33)) {
    val h = size.height; val s = h * 0.08f
    drawRect(Color(0xFF16404D))
    val petals = listOf(Color(0xFFF6C6D0), Color(0xFFF7E7C6), Color(0xFFB8D8E0))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, i, j ->
            // A little hand-drawn wobble, the same every time.
            val p = Offset(c.x + sin((i * 13 + j * 7).toFloat()) * s * 0.12f, c.y + cos((i * 5 + j * 11).toFloat()) * s * 0.12f)
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFF2B05E) else petals[(i + j) % petals.size]
            for (q in ring(p, s * 0.1f, 5, (i + j).toDouble())) drawCircle(col, s * 0.08f, q)
            drawCircle(Color(0xFF16404D), s * 0.04f, p)
        }
    }
    panel(Color(0xFF0E2A33), Edge.WAVE)
    hem(petals[1].copy(alpha = 0.35f))
}

/** Selbu roses, the eight-petal star of Norwegian mittens; a V of them in red. */
private val Selbu = PosterDesign("Selbu roses", "Norway", Color(0xFF1A1A1A)) {
    val h = size.height; val s = h * 0.13f
    drawRect(Color(0xFFF2EEE6))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFB3202F) else Color(0xFF1A1A1A)
            for (k in 0 until 8) rotate(k * 45f, c) {
                drawPath(Path().poly(listOf(c, Offset(c.x + s * 0.07f, c.y - s * 0.17f), Offset(c.x, c.y - s * 0.34f), Offset(c.x - s * 0.07f, c.y - s * 0.17f))), col)
            }
        }
    }
    panel(Color(0xFF1A1A1A), Edge.STEP)
    hem(Color(0xFFB3202F).copy(alpha = 0.4f))
}

/** Xiangyun: auspicious cloud scrolls in gold on red; a V of them in jade. */
private val Xiangyun = PosterDesign("Xiangyun clouds", "China", Color(0xFF5A0E0E)) {
    val h = size.height; val s = h * 0.12f
    drawRect(Color(0xFF9E1B1B))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFF5FB49C) else Color(0xFFE9B949)
            val st = Stroke(h * 0.006f, cap = StrokeCap.Round)
            drawArc(col, 180f, 270f, false, Offset(c.x - s * 0.24f, c.y - s * 0.12f), Size(s * 0.2f, s * 0.2f), style = st)
            drawArc(col, 180f, 180f, false, Offset(c.x - s * 0.12f, c.y - s * 0.22f), Size(s * 0.26f, s * 0.26f), style = st)
            drawArc(col, 270f, 270f, false, Offset(c.x + s * 0.06f, c.y - s * 0.12f), Size(s * 0.2f, s * 0.2f), style = st)
            drawLine(col, Offset(c.x - s * 0.24f, c.y + s * 0.02f), Offset(c.x + s * 0.26f, c.y + s * 0.02f), h * 0.006f, cap = StrokeCap.Round)
        }
    }
    panel(Color(0xFF5A0E0E), Edge.ARCH)
    hem(Color(0xFFE9B949).copy(alpha = 0.45f))
}

/** Calçada portuguesa: black and white stone waves; along a V the stones are terracotta. */
private val Calcada = PosterDesign("Calçada", "Portugal", Color(0xFF1E1E1E)) {
    val h = size.height; val s = h * 0.036f
    drawRect(Color(0xFF9A9A9A))
    artArea {
        val v = vArms(vSpot(), h * 0.1f, h * 0.22f)
        motifs(s) { c, i, j ->
            val wave = sin(c.x / (h * 0.06f)) * h * 0.06f
            val black = ((c.y + wave) / (h * 0.14f)).toInt() % 2 == 0
            val col = when {
                v.near(c, s * 0.32f) -> Color(0xFFB5542A)
                black -> Color(0xFF222222)
                else -> Color(0xFFF2EFE8)
            }
            val jit = s * 0.06f * sin((i * 3 + j * 5).toFloat())
            drawRect(col, Offset(c.x - s * 0.44f + jit, c.y - s * 0.44f), Size(s * 0.86f, s * 0.86f))
        }
    }
    panel(Color(0xFF1E1E1E), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.35f))
}

/** Roman mosaic (opus tessellatum): an ochre field of small tesserae; a V of them in red. */
private val Tessellatum = PosterDesign("Roman mosaic", "Italy", Color(0xFF3B2A1A)) {
    val h = size.height; val s = h * 0.034f
    val field = listOf(Color(0xFFD8C3A0), Color(0xFFCDB58C), Color(0xFFE2D0B0))
    drawRect(Color(0xFF6B5A44))
    artArea {
        val v = vArms(vSpot(), h * 0.1f, h * 0.22f)
        motifs(s) { c, i, j ->
            val border = j % 9 == 0 || j % 9 == 8
            val col = when {
                v.near(c, s * 0.32f) -> Color(0xFFA83A2A)
                border -> Color(0xFF3B2A1A)
                else -> field[(i * 7 + j * 3) % field.size]
            }
            drawRect(col, Offset(c.x - s * 0.44f, c.y - s * 0.44f), Size(s * 0.88f, s * 0.88f))
        }
    }
    panel(Color(0xFF3B2A1A), Edge.STRAIGHT)
    hem(field[0].copy(alpha = 0.4f))
}

/** Tibeb: the woven bands of Ethiopian cotton; little crosses, a V of them in black. */
private val Tibeb = PosterDesign("Tibeb", "Ethiopia", Color(0xFF1E4D2B)) {
    val h = size.height; val s = h * 0.07f
    val threads = listOf(Color(0xFF2E8B57), Color(0xFFF2C14E), Color(0xFFC0392B))
    drawRect(Color(0xFFF7F4EC))
    artArea {
        val v = gridV(s)
        motifs(s) { c, _, j ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFF1A1A1A) else threads[j % threads.size]
            drawRect(col, Offset(c.x - s * 0.3f, c.y - s * 0.08f), Size(s * 0.6f, s * 0.16f))
            drawRect(col, Offset(c.x - s * 0.08f, c.y - s * 0.3f), Size(s * 0.16f, s * 0.6f))
        }
    }
    panel(Color(0xFF1E4D2B), Edge.STEP)
    hem(threads[1].copy(alpha = 0.45f))
}

/** Suzani: embroidered red rosettes on ivory; a V of them in indigo. */
private val Suzani = PosterDesign("Suzani", "Uzbekistan", Color(0xFF5A1414)) {
    val h = size.height; val s = h * 0.15f
    drawRect(Color(0xFFF3EAD8))
    artArea {
        val v = gridV(s, shift = s / 2, levels = 1)
        motifs(s, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFF2E3A87) else Color(0xFFB3202F)
            for (p in ring(c, s * 0.24f, 10)) drawCircle(col.copy(alpha = 0.85f), s * 0.05f, p)
            drawCircle(col, s * 0.17f, c)
            drawCircle(Color(0xFFF3EAD8), s * 0.1f, c, style = Stroke(h * 0.005f))
            drawCircle(Color(0xFF2B2B2B), s * 0.04f, c)
        }
    }
    panel(Color(0xFF5A1414), Edge.SLANT)
    hem(Color(0xFFB3202F).copy(alpha = 0.4f))
}

/** Matyó embroidery: bright little roses on black; a V of them in pink. */
private val Matyo = PosterDesign("Matyó roses", "Hungary", Color(0xFF1B0F14)) {
    val h = size.height; val s = h * 0.1f
    drawRect(Color(0xFF121212))
    val roses = listOf(Color(0xFFD62828), Color(0xFF1D70B8), Color(0xFFF4A300))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, i, j ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFFF6FA5) else roses[(i + j) % roses.size]
            drawCircle(col, s * 0.26f, c)
            drawCircle(Color(0xFF121212), s * 0.18f, c, style = Stroke(h * 0.004f))
            drawCircle(Color(0xFFF7F0E1), s * 0.07f, c)
            for (p in ring(c, s * 0.38f, 4, PI / 4)) drawCircle(Color(0xFF2E8B57), s * 0.04f, p)
        }
    }
    panel(Color(0xFF1B0F14), Edge.WAVE)
    hem(roses[2].copy(alpha = 0.4f))
}

/** Wycinanki: Polish paper-cut rosettes in bright paper; a V of them cut from black. */
private val Wycinanki = PosterDesign("Wycinanki", "Poland", Color(0xFF7A1424)) {
    val h = size.height; val s = h * 0.13f
    val papers = listOf(Color(0xFFD62828), Color(0xFF2E8B57), Color(0xFF1D70B8), Color(0xFFF4A300))
    drawRect(Color(0xFFFAF7F0))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, i, j ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFF1A1A1A) else papers[(i * 2 + j) % papers.size]
            for (p in ring(c, s * 0.26f, 8)) drawCircle(col, s * 0.1f, p)
            drawCircle(col, s * 0.27f, c)
            drawCircle(Color(0xFFFAF7F0), s * 0.12f, c)
            drawCircle(col, s * 0.06f, c)
        }
    }
    panel(Color(0xFF7A1424), Edge.ARCH)
    hem(papers[0].copy(alpha = 0.4f))
}

/** Dancheong: the painted rosettes of Korean temple eaves; a V of them ringed in gold. */
private val Dancheong = PosterDesign("Dancheong", "Korea", Color(0xFF0F3A2C)) {
    val h = size.height; val s = h * 0.12f
    drawRect(Color(0xFF1E6B52))
    artArea {
        val v = gridV(s)
        motifs(s) { c, _, _ ->
            val isV = v.hit(c, s * 0.3f)
            drawCircle(if (isV) Color(0xFFF2C14E) else Color(0xFFC0392B), s * 0.36f, c)
            drawCircle(Color(0xFFF7F4EC), s * 0.27f, c)
            drawCircle(Color(0xFF1D4E89), s * 0.19f, c)
            drawCircle(Color(0xFFF7F4EC), s * 0.08f, c)
        }
    }
    panel(Color(0xFF0F3A2C), Edge.STRAIGHT)
    hem(Color(0xFFF2C14E).copy(alpha = 0.4f))
}

/** Batik truntum: small star flowers "blooming again"; a V of them in gold. */
private val Truntum = PosterDesign("Batik truntum", "Indonesia", Color(0xFF1C110A)) {
    val h = size.height; val s = h * 0.08f
    drawRect(Color(0xFF2B1A10))
    artArea {
        val v = gridV(s, shift = s / 2)
        motifs(s, shift = s / 2) { c, _, _ ->
            val col = if (v.hit(c, s * 0.3f)) Color(0xFFE0A526) else Color(0xFFEADBC0)
            drawPath(Path().poly(ring(c, s * 0.26f, 8).mapIndexed { k, p -> if (k % 2 == 0) p else Offset(c.x + (p.x - c.x) * 0.35f, c.y + (p.y - c.y) * 0.35f) }), col)
            drawCircle(Color(0xFF2B1A10), s * 0.05f, c)
        }
    }
    panel(Color(0xFF1C110A), Edge.SLANT)
    hem(Color(0xFFEADBC0).copy(alpha = 0.35f))
}

/** Adire: Yoruba indigo resist-dye, circles and dashes; a V of circles left pale. */
private val Adire = PosterDesign("Adire", "Nigeria", Color(0xFF0F1D3A)) {
    val h = size.height; val s = h * 0.1f
    drawRect(Color(0xFF1A2F5A))
    artArea {
        val v = gridV(s)
        motifs(s) { c, i, j ->
            if ((i + j) % 2 == 0 || v.hit(c, s * 0.3f)) {
                val col = if (v.hit(c, s * 0.3f)) Color(0xFFB9D6F2) else Color.White.copy(alpha = 0.55f)
                drawCircle(col, s * 0.3f, c, style = Stroke(h * 0.006f))
                drawCircle(col, s * 0.12f, c)
            } else for (k in -1..1) drawLine(Color.White.copy(alpha = 0.4f), Offset(c.x - s * 0.3f, c.y + k * s * 0.18f), Offset(c.x + s * 0.3f, c.y + k * s * 0.18f), h * 0.005f, cap = StrokeCap.Round)
        }
    }
    panel(Color(0xFF0F1D3A), Edge.STEP)
    hem(Color.White.copy(alpha = 0.35f))
}

/** Fine patterns added in 5.3. */
internal val WorldMicroDesigns: List<PosterDesign> = listOf(
    SakuraKomon, Ichimatsu, Uroko, Pois, Iznik, Delft, Pysanky, Talavera, Liberty, Selbu,
    Xiangyun, Calcada, Tessellatum, Tibeb, Suzani, Matyo, Wycinanki, Dancheong, Truntum, Adire,
)
