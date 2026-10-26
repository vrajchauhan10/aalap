package com.music.aalap.ui.share

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
import com.music.aalap.R

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

    /** Aalap's own app icon + name at the bottom of every card (the same icon as on the phone). */
    private fun Canvas.footer(ctx: Context) {
        val cx = 150f; val cy = H - 110f; val r = 56f
        val icon = androidx.core.content.ContextCompat.getDrawable(ctx, com.music.aalap.R.mipmap.ic_launcher)
        if (icon != null) {
            save()
            val box = RectF(cx - r, cy - r, cx + r, cy + r)
            clipPath(android.graphics.Path().apply { addRoundRect(box, r * 0.46f, r * 0.46f, android.graphics.Path.Direction.CW) })
            icon.setBounds(box.left.toInt(), box.top.toInt(), box.right.toInt(), box.bottom.toInt())
            icon.draw(this)
            restore()
        }
        drawText("Aalap", cx + 84, cy + 18, text(ctx, 52f, 0xFFFFFFFF.toInt(), true))
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

    /**
     * A "now listening" card: the artwork large and rounded on a gradient taken from
     * the artwork itself, with the title, artist and the Aalap mark.
     */
    fun songCard(ctx: Context, artwork: Bitmap?, title: String, artist: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val accent = artwork?.let {
            runCatching { androidx.palette.graphics.Palette.from(it).generate().getDarkVibrantColor(0xFF3A1A08.toInt()) }.getOrNull()
        } ?: 0xFF3A1A08.toInt()
        Canvas(bmp).apply {
            background(accent)
            drawText("NOW LISTENING", 110f, 260f, text(ctx, 40f, GOLD, true))
            val size = 860f
            val left = (W - size) / 2f
            val top = 340f
            val rect = RectF(left, top, left + size, top + size)
            if (artwork != null) {
                val shader = android.graphics.BitmapShader(artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val scale = maxOf(size / artwork.width, size / artwork.height)
                shader.setLocalMatrix(android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(left + (size - artwork.width * scale) / 2f, top + (size - artwork.height * scale) / 2f)
                })
                drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
            } else {
                drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2140.toInt() })
            }
            var y = top + size + 70f
            y += block(title, text(ctx, 78f, 0xFFFFFFFF.toInt(), true), 110f, y, W - 220, maxLines = 2)
            block(artist, text(ctx, 44f, MUTED, false), 110f, y + 10f, W - 220, maxLines = 2)
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

    /** Colour of a Blend score's band (cool for opposites, warm and gold for soulmates). */
    fun blendColor(score: Int): Int = when {
        score < 30 -> 0xFF1F5C7A.toInt()
        score < 50 -> 0xFF4B3A8C.toInt()
        score < 70 -> 0xFF9C2F5A.toInt()
        score < 85 -> 0xFFB8461B.toInt()
        else -> 0xFFC98A0E.toInt()
    }

    /** The Blend result: both names, the score, its name, and the artists you share. */
    fun blendCard(ctx: Context, me: String, friend: String, score: Int, band: String, blurb: String, shared: List<String>, songs: List<String>): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            background(blendColor(score))
            // Two overlapping circles, closer the better you match.
            val gap = 260f * (1f - score / 100f)
            val cy = 470f; val r = 190f
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            ring.color = 0x66FF8A3D; drawCircle(W / 2f - 95f - gap / 2, cy, r, ring)
            ring.color = 0x66FFC56B; drawCircle(W / 2f + 95f + gap / 2, cy, r, ring)
            val initials = text(ctx, 120f, 0xFFFFFFFF.toInt(), true).apply { textAlign = Paint.Align.CENTER }
            drawText(me.take(1).uppercase(), W / 2f - 150f - gap / 2, cy + 42f, initials)
            drawText(friend.take(1).uppercase(), W / 2f + 150f + gap / 2, cy + 42f, initials)
            var y = 760f
            y += block("OUR AALAP BLEND", text(ctx, 40f, GOLD, true), 110f, y, W - 220)
            y += block("$me + $friend", text(ctx, 62f, 0xFFFFFFFF.toInt(), true), 110f, y + 10f, W - 220, maxLines = 1) + 10f
            y += block("$score%", text(ctx, 230f, 0xFFFFFFFF.toInt(), true), 100f, y, W - 200, maxLines = 1) - 20f
            y += block(band, text(ctx, 72f, GOLD, true), 110f, y, W - 220, maxLines = 1)
            y += block(blurb, text(ctx, 40f, MUTED, false), 110f, y + 6f, W - 220, maxLines = 2) + 40f
            if (shared.isNotEmpty()) {
                y += block("WE BOTH LOVE", text(ctx, 36f, SAFFRON, true), 110f, y, W - 220)
                y += block(shared.take(4).joinToString(" · "), text(ctx, 44f, 0xFFFFFFFF.toInt(), false), 110f, y + 6f, W - 220, maxLines = 2) + 30f
            }
            if (songs.isNotEmpty() && y < H - 420f) {
                y += block("ON OUR BLEND", text(ctx, 36f, SAFFRON, true), 110f, y, W - 220)
                songs.take(3).forEach { y += block(it, text(ctx, 40f, 0xFFFFFFFF.toInt(), false), 110f, y + 6f, W - 220, maxLines = 1) + 4f }
            }
            footer(ctx)
        }
        return bmp
    }

    /** Saves [bitmap] to the share cache and opens the Android share sheet. */
    fun share(ctx: Context, bitmap: Bitmap, name: String, message: String, link: String? = null) {
        val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = java.io.File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // The link is never shown as a raw URL: plain-text apps get just the message;
            // apps that render rich text get "Listen on Aalap" as a hyperlink.
            putExtra(Intent.EXTRA_TEXT, message)
            if (link != null) {
                putExtra(Intent.EXTRA_HTML_TEXT, "${android.text.TextUtils.htmlEncode(message)}<br><a href=\"$link\">Listen on Aalap</a>")
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
