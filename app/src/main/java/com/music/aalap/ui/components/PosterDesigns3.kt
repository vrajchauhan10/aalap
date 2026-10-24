package com.music.aalap.ui.components

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

// Twenty micro-pattern designs for the Aaj ka Aalap poster: small, dense motifs
// repeated closely, the way fine embroidery, block prints and lace look up close.

/** A fine grid over the art: [block] per cell, odd rows shifted by [shift]. */
private inline fun DrawScope.micro(step: Float, stepY: Float = step, shift: Float = 0f, block: (Offset, Int, Int) -> Unit) {
    var j = 0
    var y = stepY / 2f
    while (y < size.height + stepY) {
        var i = 0
        var x = size.width * 0.5f + step / 2f + if (j % 2 == 1) shift else 0f
        while (x < size.width + step) { block(Offset(x, y), i, j); x += step; i++ }
        y += stepY; j++
    }
}

/** Kasuti from Karnataka: tiny counted cross-stitch motifs. */
private val Kasuti = PosterDesign("Kasuti", "India", Color(0xFF1E1E3F)) {
    val h = size.height
    drawRect(Color(0xFF7A1035))
    val gold = Color(0xFFF5C26B); val white = Color(0xFFFFF4E0)
    val p = h * 0.022f
    artArea {
        val vCell = snapToGrid(vSpot(), h * 0.14f)
        micro(h * 0.14f) { c, i, j ->
            val isV = (c - vCell).getDistance() < h * 0.03f
            val col = if (isV) Color(0xFFFF8A3D) else if ((i + j) % 2 == 0) gold else white
            // A stepped cross made of little x stitches (or, once, a V of them).
            val stitches = if (isV) listOf(0 to 2, -1 to 1, 1 to 1, -2 to 0, 2 to 0, -3 to -1, 3 to -1, -4 to -2, 4 to -2)
                else listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1, -2 to 0, 2 to 0, 0 to -2, 0 to 2)
            for ((dx, dy) in stitches) {
                val o = Offset(c.x + dx * p, c.y + dy * p)
                drawLine(col, Offset(o.x - p * 0.35f, o.y - p * 0.35f), Offset(o.x + p * 0.35f, o.y + p * 0.35f), h * if (isV) 0.006f else 0.004f)
                drawLine(col, Offset(o.x + p * 0.35f, o.y - p * 0.35f), Offset(o.x - p * 0.35f, o.y + p * 0.35f), h * if (isV) 0.006f else 0.004f)
            }
        }
    }
    panel(Color(0xFF1E1E3F), Edge.STEP)
    hem(gold.copy(alpha = 0.5f))
}

/** Mirror work (shisha) from Kutch: little mirrors held by stitched rays. */
private val Shisha = PosterDesign("Mirror work", "India", Color(0xFF0F2A3F)) {
    val h = size.height
    drawRect(Color(0xFF1F4E5F))
    artArea {
        val v = gridV(h * 0.15f, shift = h * 0.075f, levels = 2)
        micro(h * 0.15f, shift = h * 0.075f) { c, i, j ->
            val isV = v.hit(c, h * 0.03f)
            val ray = if (isV) Color.White else listOf(Color(0xFFFF8A3D), Color(0xFFE5397A), Color(0xFFF2C14E))[(i + j) % 3]
            for (k in 0 until 12) rotate(k * 30f, c) { drawLine(ray, Offset(c.x, c.y - h * 0.03f), Offset(c.x, c.y - h * 0.05f), h * 0.005f, cap = StrokeCap.Round) }
            drawCircle(if (isV) Color(0xFFF2C14E) else Color(0xFFDDE6EA), h * 0.026f, c)
            drawCircle(Color.White, h * 0.009f, Offset(c.x - h * 0.008f, c.y - h * 0.008f))
        }
    }
    panel(Color(0xFF0F2A3F), Edge.ARCH)
    hem(Color(0xFFF2C14E).copy(alpha = 0.5f))
}

/** Batik parang from Java: fine diagonal blade-and-dot bands. */
private val Parang = PosterDesign("Batik parang", "Indonesia", Color(0xFF2A1A0E)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF8A5A2B))
    val cream = Color(0xFFF2E3C6); val dark = Color(0xFF3B2412)
    // Along a V, the blades are dyed gold instead of cream.
    val v = vArms(vSpot(), h * 0.12f, h * 0.26f)
    artArea {
        val gap = h * 0.13f
        var x = w * 0.5f - h
        while (x < w + h) {
            drawLine(dark, Offset(x, h), Offset(x + h, 0f), gap * 0.42f)
            var t = 0f
            while (t < 1f) {
                val c = Offset(x + h * t, h - h * t)
                rotate(-45f, c) { drawOval(if (v.near(c, h * 0.035f)) Color(0xFFF2B33D) else cream, Offset(c.x - h * 0.012f, c.y - h * 0.03f), Size(h * 0.024f, h * 0.06f)) }
                drawCircle(cream, h * 0.006f, Offset(c.x + gap * 0.4f, c.y + gap * 0.05f))
                t += 0.07f
            }
            x += gap
        }
    }
    panel(Color(0xFF2A1A0E), Edge.SLANT)
    hem(cream.copy(alpha = 0.4f))
}

/** Edo komon, Japanese kimono dyeing: minute dots set in fan-shaped arcs. */
private val Komon = PosterDesign("Edo komon", "Japan", Color(0xFF1C2541)) {
    val h = size.height
    drawRect(Color(0xFF3A506B))
    artArea {
        val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
        micro(h * 0.14f, h * 0.09f, shift = h * 0.07f) { c, _, _ ->
            for (ring in 1..3) {
                val r = h * 0.02f * ring
                var a = 200.0
                while (a <= 340.0) {
                    val rad = a * PI / 180
                    val d = Offset(c.x + cos(rad).toFloat() * r, c.y + sin(rad).toFloat() * r + h * 0.03f)
                    if (v.near(d, h * 0.016f)) drawCircle(Color(0xFFFF9E80), h * 0.0065f, d)
                    else drawCircle(Color(0xFFE0E7F0).copy(alpha = 0.85f), h * 0.004f, d)
                    a += 140.0 / (ring * 3)
                }
            }
        }
    }
    panel(Color(0xFF1C2541), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.35f))
}

/** Kumiko woodwork: the asanoha (hemp leaf) lattice in fine lines. */
private val Kumiko = PosterDesign("Kumiko", "Japan", Color(0xFF2E2416)) {
    val h = size.height
    drawRect(Color(0xFFD8B98A))
    val wood = Color(0xFF6B4A2B)
    val s = h * 0.12f
    artArea {
        val vCell = snapToGrid(vSpot(), s, s * 0.866f, shift = s / 2)
        micro(s, s * 0.866f, shift = s / 2) { c, _, _ ->
            // Six spokes to the hexagon's corners and the leaf lines between them.
            for (k in 0 until 6) {
                val a = k * PI / 3 + PI / 6
                val p = Offset(c.x + cos(a).toFloat() * s * 0.577f, c.y + sin(a).toFloat() * s * 0.577f)
                drawLine(wood, c, p, h * 0.004f)
                val b = (k + 1) * PI / 3 + PI / 6
                drawLine(wood, p, Offset(c.x + cos(b).toFloat() * s * 0.577f, c.y + sin(b).toFloat() * s * 0.577f), h * 0.004f)
            }
            if ((c - vCell).getDistance() < s * 0.2f) {
                // The spokes rising left and right from the centre, in red-stained wood.
                for (a in listOf(-150.0, -30.0)) {
                    val rad = a * PI / 180
                    drawLine(Color(0xFFA8322A), c, Offset(c.x + cos(rad).toFloat() * s * 0.577f, c.y + sin(rad).toFloat() * s * 0.577f), h * 0.009f, cap = StrokeCap.Round)
                }
            }
        }
    }
    panel(Color(0xFF2E2416), Edge.STRAIGHT)
    hem(Color(0xFFD8B98A).copy(alpha = 0.5f))
}

/** Aran knitting from Ireland: small cables and moss-stitch diamonds. */
private val Aran = PosterDesign("Aran knit", "Ireland", Color(0xFF2F3E46)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFEDE6D6))
    val yarn = Color(0xFFB8AC93)
    var vDiamond: Offset? = null
    artArea {
        val col = h * 0.12f
        var x = w * 0.5f + col / 2; var k = 0
        while (x < w + col) {
            var y = 0f
            while (y < h) {
                if (k % 2 == 0) {
                    // Cable: two strands twisting.
                    drawArc(yarn, 300f, 120f, false, Offset(x - col * 0.35f, y), Size(col * 0.7f, col * 0.7f), style = Stroke(h * 0.012f))
                    drawArc(yarn, 120f, 120f, false, Offset(x - col * 0.35f, y), Size(col * 0.7f, col * 0.7f), style = Stroke(h * 0.012f))
                } else {
                    drawPath(Path().apply { moveTo(x, y); lineTo(x + col * 0.3f, y + col * 0.35f); lineTo(x, y + col * 0.7f); lineTo(x - col * 0.3f, y + col * 0.35f); close() }, yarn, style = Stroke(h * 0.008f))
                    drawCircle(yarn, h * 0.008f, Offset(x, y + col * 0.35f))
                    val top = Offset(x, y)
                    if (vDiamond.let { it == null || (top - vSpot()).getDistance() < (it - vSpot()).getDistance() }) vDiamond = top
                }
                y += col * 0.7f
            }
            drawLine(yarn.copy(alpha = 0.5f), Offset(x + col / 2, 0f), Offset(x + col / 2, h), h * 0.004f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.01f, h * 0.01f)))
            x += col; k++
        }
    }
    artArea {
        vDiamond?.let { (x, y) ->
            val col = h * 0.12f
            drawPath(Path().apply { moveTo(x - col * 0.3f, y + col * 0.35f); lineTo(x, y + col * 0.7f); lineTo(x + col * 0.3f, y + col * 0.35f) },
                Color(0xFFB5542A), style = Stroke(h * 0.012f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
    panel(Color(0xFF2F3E46), Edge.STEP)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Beni Ourain rugs from the Atlas: fine dark diamond lattice on cream wool. */
private val BeniOurain = PosterDesign("Beni Ourain rug", "Morocco", Color(0xFF2B2622)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF2EDE3))
    val ink = Color(0xFF3B3430)
    artArea {
        val s = h * 0.09f
        var x = w * 0.5f - h
        while (x < w + h) {
            drawLine(ink, Offset(x, 0f), Offset(x + h * 0.6f, h), h * 0.0045f)
            drawLine(ink, Offset(x + h * 0.6f, 0f), Offset(x, h), h * 0.0045f)
            x += s
        }
        // The tufts between the lines: along a V (at the lattice's own slant) they're
        // knotted in terracotta wool.
        val v = vArms(vSpot(), h * 0.13f, h * 0.22f)
        micro(s * 0.6f) { c, i, j ->
            if (v.near(c, s * 0.3f)) drawCircle(Color(0xFFC0613A), h * 0.009f, c)
            else if ((i + j) % 3 == 0) drawCircle(ink.copy(alpha = 0.5f), h * 0.006f, c)
        }
    }
    panel(Color(0xFF2B2622), Edge.STRAIGHT)
    hem(Color(0xFFF2EDE3).copy(alpha = 0.4f))
}

/** Çintemani, the Ottoman textile motif: three balls over two wavy stripes. */
private val Cintemani = PosterDesign("Çintemani", "Türkiye", Color(0xFF13294B)) {
    val h = size.height
    drawRect(Color(0xFFF4EBDC))
    val red = Color(0xFFB3202F); val blue = Color(0xFF1F4E9E); val gold = Color(0xFFD4A24C)
    artArea {
        val v = gridV(h * 0.16f, shift = h * 0.08f, levels = 2)
        micro(h * 0.16f, shift = h * 0.08f) { c, _, _ ->
            val r = h * 0.014f
            for ((dx, dy) in listOf(-1f to 0f, 1f to 0f, 0f to -1.6f)) drawCircle(if (v.hit(c, h * 0.03f)) red else gold, r, Offset(c.x + dx * r * 1.4f, c.y - h * 0.025f + dy * r))
            for (k in 0..1) {
                val y = c.y + h * 0.012f + k * h * 0.02f
                drawPath(Path().apply { moveTo(c.x - h * 0.04f, y); quadraticBezierTo(c.x - h * 0.02f, y - h * 0.012f, c.x, y); quadraticBezierTo(c.x + h * 0.02f, y + h * 0.012f, c.x + h * 0.04f, y) }, if (k == 0) red else blue, style = Stroke(h * 0.007f, cap = StrokeCap.Round))
            }
        }
    }
    panel(Color(0xFF13294B), Edge.ARCH)
    hem(gold.copy(alpha = 0.55f))
}

/** Guatemalan huipil weaving: tight rows of bright zigzags. */
private val Huipil = PosterDesign("Huipil", "Guatemala", Color(0xFF1F1147)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFF2B1B5A))
    val cols = listOf(Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFFFF8FAB))
    artArea {
        val row = h * 0.07f
        var y = row / 2; var k = 0
        var vDip: List<Offset>? = null
        while (y < h + row) {
            val pts = arrayListOf(Offset(w * 0.5f, y))
            var x = w * 0.5f; var up = true
            while (x < w) { x += row * 0.5f; pts += Offset(x, y + if (up) -row * 0.28f else row * 0.28f); up = !up }
            drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, cols[k % cols.size], style = Stroke(h * 0.008f))
            if (vDip == null && y >= vSpot().y) {
                val i = (1 until pts.size - 1).filter { pts[it].y > y }.minByOrNull { kotlin.math.abs(pts[it].x - vSpot().x) }
                if (i != null) vDip = listOf(pts[i - 1], pts[i], pts[i + 1])
            }
            y += row; k++
        }
        vDip?.let { (a, b, c) ->
            // One dip of one row woven in white yarn, at the row's own weight.
            drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y) }, Color.White.copy(alpha = 0.92f), style = Stroke(h * 0.008f))
        }
    }
    panel(Color(0xFF1F1147), Edge.STEP)
    hem(Color(0xFFFFD93D).copy(alpha = 0.45f))
}

/** Songket from the Malay world: gold-thread stars woven into maroon silk. */
private val Songket = PosterDesign("Songket", "Malaysia", Color(0xFF3D0C1C)) {
    val h = size.height
    drawRect(Color(0xFF6E1430))
    val gold = Color(0xFFE6B84C)
    artArea {
        val v = gridV(h * 0.12f, shift = h * 0.06f, levels = 2)
        micro(h * 0.12f, shift = h * 0.06f) { c, i, j ->
            val r = h * 0.03f
            val thread = if (v.hit(c, h * 0.025f)) Color(0xFFF4F1EA) else gold
            for (a in listOf(0f, 45f)) rotate(a, c) { drawRect(thread, Offset(c.x - r * 0.5f, c.y - r * 0.5f), Size(r, r), style = Stroke(h * if (thread == gold) 0.004f else 0.007f)) }
            drawCircle(thread, h * 0.006f, c)
            if ((i + j) % 2 == 0) drawCircle(gold.copy(alpha = 0.6f), h * 0.004f, Offset(c.x + h * 0.06f, c.y))
        }
    }
    panel(Color(0xFF3D0C1C), Edge.WAVE)
    hem(gold.copy(alpha = 0.5f))
}

/** Shippo (seven treasures): overlapping circles in a fine chain. */
private val Shippo = PosterDesign("Shippo", "Japan", Color(0xFF0B3D3A)) {
    val h = size.height
    drawRect(Color(0xFF1E6F6A))
    val line = Color(0xFFF1E3C4)
    val s = h * 0.1f
    artArea {
        micro(s) { c, _, _ -> drawCircle(line, s * 0.5f * 1.414f, c, style = Stroke(h * 0.004f)) }
        val v = vArms(snapToGrid(vSpot(), s).let { Offset(it.x + s / 2, it.y + s / 2) }, s * 2, s * 2)
        micro(s, s) { c, _, _ ->
            val d = Offset(c.x + s / 2, c.y + s / 2)
            if (v.near(d, s * 0.2f)) drawCircle(Color(0xFFFF8A65), h * 0.013f, d) else drawCircle(Color(0xFFE6B84C), h * 0.006f, d)
            if (v.near(c, s * 0.2f)) drawCircle(Color(0xFFFF8A65), h * 0.013f, c)
        }
    }
    panel(Color(0xFF0B3D3A), Edge.ARCH)
    hem(line.copy(alpha = 0.45f))
}

/** The Greek key (meander): a fine continuous fret. */
private val GreekKey = PosterDesign("Greek key", "Greece", Color(0xFF0D2847)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF4F1EA))
    val blue = Color(0xFF1D4E89)
    artArea {
        val u = h * 0.03f
        // Fret units sit 4u apart in rows 5u apart; a V of them is drawn in red.
        val vRow = ((vSpot().y - u * 2) / (u * 5)).toInt(); val vCol = ((vSpot().x - w * 0.5f) / (u * 4)).toInt()
        var row = 0
        var y = u * 2
        while (y < h) {
            var x = w * 0.5f
            val p = Path().apply { moveTo(x, y + u * 3) }
            val red = Path()
            var col = 0
            while (x < w + u * 4) {
                val up = vRow - row
                val q = if (up in 0..2 && kotlin.math.abs(col - vCol) == up) red else p
                q.moveTo(x, y + u * 3)
                q.lineTo(x, y); q.lineTo(x + u * 3, y); q.lineTo(x + u * 3, y + u * 2); q.lineTo(x + u, y + u * 2); q.lineTo(x + u, y + u); q.lineTo(x + u * 2, y + u)
                x += u * 4; col++
            }
            drawPath(p, if (row % 2 == 0) blue else Color(0xFFB08D57), style = Stroke(h * 0.006f))
            drawPath(red, Color(0xFFC0392B), style = Stroke(h * 0.009f))
            drawLine(blue.copy(alpha = 0.4f), Offset(w * 0.5f, y + u * 3.6f), Offset(w, y + u * 3.6f), h * 0.004f)
            y += u * 5; row++
        }
    }
    panel(Color(0xFF0D2847), Edge.STRAIGHT)
    hem(Color.White.copy(alpha = 0.45f))
}

/** Tapa (masi) bark cloth from Fiji: rows of small dark triangles on tan. */
private val Tapa = PosterDesign("Tapa cloth", "Fiji", Color(0xFF2A1B10)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFD9B98C))
    val ink = Color(0xFF3B2416)
    artArea {
        val r = h * 0.06f
        val vRow = (vSpot().y / (r * 1.4f)).toInt(); val vCol = ((vSpot().x - w * 0.5f) / r).toInt()
        var y = 0f; var k = 0
        while (y < h) {
            var x = w * 0.5f; var i = 0
            while (x < w) {
                val up = vRow - k
                val c = if (up in 0..3 && kotlin.math.abs(i - vCol) == up) Color(0xFFB3412A) else ink
                if (k % 3 != 2) drawPath(Path().apply { moveTo(x, y + r); lineTo(x + r * 0.5f, y); lineTo(x + r, y + r); close() }, c)
                else drawCircle(c, h * if (c == ink) 0.008f else 0.014f, Offset(x + r / 2, y + r / 2))
                i++
                x += r
            }
            drawLine(ink, Offset(w * 0.5f, y + r * 1.15f), Offset(w, y + r * 1.15f), h * 0.005f)
            y += r * 1.4f; k++
        }
    }
    panel(Color(0xFF2A1B10), Edge.STEP)
    hem(Color(0xFFD9B98C).copy(alpha = 0.45f))
}

/** Bojagi, Korean wrapping cloth: small patchwork panes with fine seams. */
private val Bojagi = PosterDesign("Bojagi", "Korea", Color(0xFF22313F)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFEFE8DA))
    val panes = listOf(Color(0xFFF2C6C2), Color(0xFFC9DDE8), Color(0xFFF4E1A1), Color(0xFFD5E8C8), Color(0xFFE8D3EC), Color(0xFFFFFFFF))
    val seam = Color(0xFF7A6A58)
    artArea {
        var y = 0f; var j = 0
        val spot = vSpot()
        // Three panes are cut from a persimmon cloth: one, and one up-left and up-right of it.
        val vPoints = listOf(spot, Offset(spot.x - h * 0.1f, spot.y - h * 0.13f), Offset(spot.x + h * 0.1f, spot.y - h * 0.13f))
        while (y < h) {
            val rowH = h * (0.08f + (j % 3) * 0.03f)
            var x = w * 0.5f; var i = 0
            while (x < w) {
                val cw = h * (0.07f + ((i * 7 + j * 3) % 4) * 0.025f)
                val isV = vPoints.any { it.x in x..x + cw && it.y in y..y + rowH }
                drawRect(if (isV) Color(0xFFE8896B) else panes[(i + j * 2) % panes.size], Offset(x, y), Size(cw, rowH))
                drawRect(seam, Offset(x, y), Size(cw, rowH), style = Stroke(h * 0.004f))
                x += cw; i++
            }
            y += rowH; j++
        }
    }
    panel(Color(0xFF22313F), Edge.STRAIGHT)
    hem(Color.White.copy(alpha = 0.4f))
}

/** Lahariya tie-dye from Rajasthan: fine diagonal waves of colour. */
private val Lahariya = PosterDesign("Lahariya", "India", Color(0xFF4A1048)) {
    val w = size.width; val h = size.height
    drawRect(Color(0xFFF7C948))
    val waves = listOf(Color(0xFFE5397A), Color(0xFF7B2CBF), Color(0xFF2EC4B6))
    artArea {
        val gap = h * 0.06f
        val v = vArms(vSpot(), h * 0.1f, h * 0.24f)
        var x = w * 0.5f - h; var k = 0
        while (x < w + h) {
            val pts = ArrayList<Offset>()
            var t = 0f
            while (t <= 1f) { pts += Offset(x + h * t + sin(t * 40f) * h * 0.008f, h - h * t); t += 0.005f }
            drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.forEach { lineTo(it.x, it.y) } }, waves[k % waves.size].copy(alpha = 0.85f), style = Stroke(h * 0.012f, cap = StrokeCap.Round))
            // Where a wave crosses the V it took the deep plum dye instead.
            traceV(pts, v, h * 0.016f, Color(0xFF4A1048), h * 0.012f)
            x += gap; k++
        }
    }
    panel(Color(0xFF4A1048), Edge.WAVE)
    hem(Color.White.copy(alpha = 0.45f))
}

internal val MicroDesigns: List<PosterDesign> = listOf(
    Komon, Kumiko, Kasuti, Shisha, Parang, Aran,
    BeniOurain, Shippo, Cintemani, Lahariya, Huipil, Songket, GreekKey, Bojagi, Tapa,
)
