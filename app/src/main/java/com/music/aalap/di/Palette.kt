package com.music.aalap.di

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class Palette {
    companion object {
        // Colours already read, per cover and kind, so reopening a page is instant.
        private val cache = CappedMap<String, Color>(400)

        /**
         * Keeps a cover colour dark enough for white text on top: anything brighter than about
         * a third of full brightness is scaled down, keeping its hue. Full opacity.
         */
        fun readableTint(rgb: Int, maxLuminance: Double = 0.30): Int {
            fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
            var r = (rgb shr 16) and 0xFF; var g = (rgb shr 8) and 0xFF; var b = rgb and 0xFF
            repeat(12) {
                val l = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
                if (l <= maxLuminance) return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                r = (r * 0.85).toInt(); g = (g * 0.85).toInt(); b = (b * 0.85).toInt()
            }
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

//    fun extractColorFromImageUrl(context: Context, imageUrl: String, onPaletteGenerated: (Palette) -> Unit) {
//        Glide.with(context)
//            .asBitmap()
//            .load(imageUrl)
//            .into(object : CustomTarget<Bitmap>() {
//                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
//                    Palette.from(resource).generate { palette ->
//                        palette?.let { onPaletteGenerated(it) }
//                    }
//                }
//
//                override fun onLoadCleared(placeholder: Drawable?) {
//                    // Handle cleanup here if necessary
//                }
//            })
//    }

    fun extractFirstColorFromImageUrl(context: Context, imageUrl: String, onColorExtracted: (Color) -> Unit) {
        cache["1|$imageUrl"]?.let { onColorExtracted(it); return }
        val report: (Color) -> Unit = { cache["1|$imageUrl"] = it; onColorExtracted(it) }
        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .override(96, 96) // a small copy is plenty for picking a colour
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    Palette.from(resource).generate { palette ->
                        // Dark vibrant suits the mini player; covers without one fall back to the
                        // next darkest tone instead of keeping the last song's colour.
                        val rgb = palette?.darkVibrantSwatch?.rgb
                            ?: palette?.darkMutedSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb?.let { readableTint(it, 0.12) }
                        rgb?.let { report(Color(it or (0xFF shl 24))) }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Handle cleanup here if necessary
                }
            })
    }
    fun extractSecondColorFromCoverUrl(context: Context, imageUrl: String, onColorExtracted: (Color) -> Unit) {
        cache["2|$imageUrl"]?.let { onColorExtracted(it); return }
        val report: (Color) -> Unit = { cache["2|$imageUrl"] = it; onColorExtracted(it) }
        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .override(96, 96) // a small copy is plenty for picking a colour
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Create a scaled down version of the bitmap
                    val scaledBitmap = Bitmap.createScaledBitmap(resource, 50, 50, true)

                    Palette
                        .from(scaledBitmap)
                        .generate { palette ->
                        // The cover's most colourful tone that still keeps white text readable:
                        // muted first (it suits a background), then the others, then whatever
                        // colour fills most of the cover. Before, a cover with no muted tone kept
                        // the last song's colour.
                        val rgb = palette?.mutedSwatch?.rgb
                            ?: palette?.darkVibrantSwatch?.rgb
                            ?: palette?.vibrantSwatch?.rgb
                            ?: palette?.darkMutedSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                        rgb?.let { report(Color(readableTint(it))) }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // Handle cleanup here if necessary
                }
            })
    }
}
