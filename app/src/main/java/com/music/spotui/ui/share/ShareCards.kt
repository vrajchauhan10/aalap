package com.music.spotui.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.music.spotui.R

/**
 * Story-sized (1080×1920) share images drawn with Android's Canvas, so they can be
 * produced off-screen and handed to any app (WhatsApp, Instagram, …).
 */
object ShareCards {
    private const val W = 1080
    private const val H = 1920
    private const val INDIGO = 0xFF100B1C.toInt()
    private const val SAFFRON = 0xFFFF8A3D.toInt()
    private const val GOLD = 0xFFFFC56B.toInt()
    private const val MUTED = 0xFFB8AFC9.toInt()

    private fun font(ctx: Context, bold: Boolean): Typeface =
        runCatching { ResourcesCompat.getFont(ctx, if (bold) R.font.poppins_bold else R.font.poppins_regular) }
            .getOrNull() ?: Typeface.DEFAULT

    private fun text(ctx: Context, size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = font(ctx, bold)
    }

    /** Draws wrapped text and returns the height used. */
    private fun Canvas.block(text: String, paint: TextPaint, x: Float, y: Float, width: Int, maxLines: Int = 6): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        save(); translate(x, y); layout.draw(this); restore()
        return layout.height.toFloat()
    }

    private fun Canvas.background(top: Int) {
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), top, INDIGO, Shader.TileMode.CLAMP) }
        drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
    }

    /** Small Aalap mark + name at the bottom of every card. */
    private fun Canvas.footer(ctx: Context) {
        val cx = 150f; val cy = H - 110f
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f; color = GOLD; strokeCap = Paint.Cap.ROUND }
        drawArc(RectF(cx - 46, cy - 46, cx + 46, cy + 46), 31f, 298f, false, arc)
        val play = android.graphics.Path().apply { moveTo(cx - 12, cy - 20); lineTo(cx + 20, cy); lineTo(cx - 12, cy + 20); close() }
        drawPath(play, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SAFFRON })
        drawText("Aalap", cx + 80, cy + 18, text(ctx, 52f, 0xFFFFFFFF.toInt(), true))
    }

    /** A lyric line as a quote card, tinted with the song's artwork colour. */
    fun lyricCard(ctx: Context, line: String, title: String, artist: String, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            background(accent)
            drawText("“", 100f, 560f, text(ctx, 320f, GOLD, true))
            val used = block(line, text(ctx, 86f, 0xFFFFFFFF.toInt(), true), 110f, 640f, W - 220, maxLines = 7)
            drawRect(110f, 700f + used, 230f, 712f + used, Paint().apply { color = SAFFRON })
            block(title, text(ctx, 48f, 0xFFFFFFFF.toInt(), true), 110f, 760f + used, W - 220, maxLines = 2)
            block(artist, text(ctx, 40f, MUTED, false), 110f, 830f + used, W - 220, maxLines = 1)
            footer(ctx)
        }
        return bmp
    }

    /** The Wrapped summary card. */
    fun wrappedCard(
        ctx: Context, period: String, minutes: Long, plays: Int, topArtist: String,
        topSongs: List<String>, topArtists: List<String>, personality: String,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            background(0xFF7A3A12.toInt())
            var y = 150f
            y += block("MY $period IN MUSIC".uppercase(), text(ctx, 40f, GOLD, true), 100f, y, W - 200)
            y += block("$minutes", text(ctx, 170f, 0xFFFFFFFF.toInt(), true), 90f, y, W - 200, maxLines = 1)
            y += block("minutes · $plays plays", text(ctx, 50f, MUTED, false), 100f, y, W - 200)
            y += 50f
            y += block("TOP ARTIST", text(ctx, 36f, SAFFRON, true), 100f, y, W - 200)
            y += block(topArtist, text(ctx, 84f, 0xFFFFFFFF.toInt(), true), 100f, y, W - 200, maxLines = 1)
            y += 70f
            val half = (W - 260) / 2
            val songsTop = y
            block("TOP SONGS", text(ctx, 36f, SAFFRON, true), 100f, y, half)
            block("TOP ARTISTS", text(ctx, 36f, SAFFRON, true), 160f + half, y, half)
            y += 70f
            var ys = y; var ya = y
            // One line per item so long titles can't push the list into the footer.
            topSongs.take(5).forEachIndexed { i, s -> ys += block("${i + 1}  $s", text(ctx, 40f, 0xFFFFFFFF.toInt(), false), 100f, ys, half, 1) + 18f }
            topArtists.take(5).forEachIndexed { i, a -> ya += block("${i + 1}  $a", text(ctx, 40f, 0xFFFFFFFF.toInt(), false), 160f + half, ya, half, 1) + 18f }
            y = maxOf(ys, ya, songsTop + 200f) + 50f
            y += block("YOU'RE A", text(ctx, 36f, SAFFRON, true), 100f, y, W - 200)
            block(personality, text(ctx, 70f, GOLD, true), 100f, y, W - 200, maxLines = 1)
            footer(ctx)
        }
        return bmp
    }

    /** Saves [bitmap] to the share cache and opens the Android share sheet. */
    fun share(ctx: Context, bitmap: Bitmap, name: String, message: String) {
        val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = java.io.File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
