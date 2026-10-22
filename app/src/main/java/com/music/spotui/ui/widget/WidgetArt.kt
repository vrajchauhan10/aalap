package com.music.spotui.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.music.spotui.ui.components.drawMandala

/**
 * The widgets' hand-made backdrop, in the style of the Aaj ka Aalap poster: a
 * marigold mandala half off the right edge, a toran of little flags along the top,
 * paisley curls and a block-print dotted border. Ink-on-indigo at night,
 * saffron-on-cream by day, kept faint so the text and buttons stay easy to read.
 */
object WidgetArt {
    private val cache = HashMap<String, Bitmap>()

    fun render(context: Context, widthDp: Int, heightDp: Int, large: Boolean): Bitmap? = runCatching {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val density = context.resources.displayMetrics.density
        // Keep the bitmap small enough for RemoteViews (it is sent to the launcher).
        val scale = minOf(density, 900f / widthDp.coerceAtLeast(1))
        val w = (widthDp * scale).toInt().coerceAtLeast(64)
        val h = (heightDp * scale).toInt().coerceAtLeast(32)
        val key = "$w×$h·$night·$large"
        cache[key]?.let { return it }

        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(scale), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            paint(night, large, scale)
        }
        image.asAndroidBitmap().also { if (cache.size > 8) cache.clear(); cache[key] = it }
    }.getOrNull()

    private fun DrawScope.paint(night: Boolean, large: Boolean, dp: Float) {
        val w = size.width; val h = size.height
        val base = if (night) Color(0xFF1C1530) else Color(0xFFFFF6EC)
        val deep = if (night) Color(0xFF2A1A55) else Color(0xFFFFE6CC)
        val ink = if (night) Color(0xFFFFC56B) else Color(0xFFD9600F)
        val accent = if (night) Color(0xFFFF8A3D) else Color(0xFFE5397A)
        val corner = if (large) 24f * dp else h / 2f // the compact widget is a pill
        val card = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(corner)))
        }
        clipPath(card) {
            drawRect(base)
            // A soft indigo/peach wash rising from the right, under the mandala.
            val washR = h * if (large) 1.15f else 1.9f
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(deep, deep, deep.copy(alpha = 0f)), center = Offset(w * 1.02f, h * 0.5f), radius = washR),
                radius = washR, center = Offset(w * 1.02f, h * 0.5f),
            )

            // Mandala, half off the right edge.
            val r = if (large) h * 0.62f else h * 0.95f
            drawMandala(ink.copy(alpha = if (night) 0.32f else 0.26f), Offset(w - r * 0.12f, h * 0.5f), r, 1.4f * dp, accent = accent.copy(alpha = 0.22f))

            if (large) {
                // Toran: a string with little triangular flags along the top.
                val y = 7f * dp
                drawLine(ink.copy(alpha = 0.35f), Offset(corner, y), Offset(w - corner, y), 1f * dp)
                var x = corner + 6f * dp
                var i = 0
                while (x < w - corner - 6f * dp) {
                    val flag = Path().apply { moveTo(x - 4.5f * dp, y); lineTo(x + 4.5f * dp, y); lineTo(x, y + 8f * dp); close() }
                    drawPath(flag, (if (i % 2 == 0) accent else ink).copy(alpha = if (night) 0.45f else 0.38f))
                    x += 16f * dp; i++
                }
                // Paisley curl in the lower left.
                drawPath(Path().apply {
                    moveTo(w * 0.05f, h * 0.96f)
                    cubicTo(w * 0.0f, h * 0.86f, w * 0.03f, h * 0.74f, w * 0.08f, h * 0.76f)
                    cubicTo(w * 0.12f, h * 0.78f, w * 0.11f, h * 0.88f, w * 0.08f, h * 0.88f)
                }, ink.copy(alpha = 0.28f), style = Stroke(1.4f * dp, cap = StrokeCap.Round))
            } else {
                // Compact: a small paisley tucked behind the play button side.
                drawPath(Path().apply {
                    moveTo(w * 0.62f, h * 0.9f)
                    cubicTo(w * 0.6f, h * 0.55f, w * 0.66f, h * 0.2f, w * 0.7f, h * 0.3f)
                    cubicTo(w * 0.73f, h * 0.42f, w * 0.68f, h * 0.55f, w * 0.66f, h * 0.48f)
                }, ink.copy(alpha = 0.22f), style = Stroke(1.2f * dp, cap = StrokeCap.Round))
            }

            // Block-print border: a dotted line just inside the edge.
            val inset = 4f * dp
            drawRoundRect(
                ink.copy(alpha = if (night) 0.4f else 0.32f),
                topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(corner - inset),
                style = Stroke(1.6f * dp, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 6f * dp))),
            )
        }
    }
}
