package com.music.spotui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

/**
 * The Aalap mark drawn in Compose (same geometry as the launcher icon): a gold replay
 * arc with its arrowhead around a saffron play button. Every part can be animated:
 * [arcProgress] draws the arc in (0..1), [arcRotation] turns it, [playScale] grows
 * the play button, [arrowAlpha] fades the arrowhead.
 */
@Composable
fun AalapMark(
    modifier: Modifier,
    arcColor: Color = Color(0xFFFFC56B),
    playColor: Color = Color(0xFFFF8A3D),
    arcProgress: Float = 1f,
    arcRotation: Float = 0f,
    playScale: Float = 1f,
    arrowAlpha: Float = 1f,
) {
    Canvas(modifier) {
        // The icon is designed on a 108 grid with the mark centred at (54, 54); fit the
        // mark's 60-unit box to this canvas.
        val unit = size.minDimension / 60f
        val c = Offset(size.width / 2, size.height / 2)
        fun p(x: Float, y: Float) = Offset(c.x + (x - 54f) * unit, c.y + (y - 54f) * unit)
        val stroke = 5f * unit
        rotate(arcRotation, c) {
            // Arc from 20° clockwise to 329° (the long way round), drawn in from its tail.
            val r = 23.3f * unit
            drawArc(
                color = arcColor,
                startAngle = 20f + 309f * (1f - arcProgress),
                sweepAngle = 309f * arcProgress,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (arrowAlpha > 0f) {
                val head = Path().apply {
                    moveTo(p(66f, 30f).x, p(66f, 30f).y)
                    lineTo(p(74f, 42f).x, p(74f, 42f).y)
                    lineTo(p(64f, 48f).x, p(64f, 48f).y)
                }
                drawPath(head, arcColor.copy(alpha = arcColor.alpha * arrowAlpha),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (playScale > 0f) {
            // The triangle's visual centre is (54.7, 54).
            scale(playScale, pivot = p(54.7f, 54f)) {
                val tri = Path().apply {
                    moveTo(p(48f, 44f).x, p(48f, 44f).y)
                    lineTo(p(64f, 54f).x, p(64f, 54f).y)
                    lineTo(p(48f, 64f).x, p(48f, 64f).y)
                    close()
                }
                drawPath(tri, playColor)
                drawPath(tri, playColor, style = Stroke(width = 2f * unit, join = StrokeJoin.Round))
            }
        }
    }
}
