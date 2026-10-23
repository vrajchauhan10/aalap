package com.music.aalap.data.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The photo you pick for your Aalap profile. Kept in the app's private files, one per
 * account (by email), so it comes back when you sign in again. Shown instead of the
 * connected account's picture wherever your avatar appears.
 */
object ProfilePhoto {
    /** Bumped on every change, so avatars on screen reload. */
    val version = MutableStateFlow(0)

    private fun file(c: Context): File {
        val who = getAalapEmail(c).trim().lowercase().ifBlank { "local" }
        val hash = MessageDigest.getInstance("SHA-256").digest(who.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        return File(File(c.filesDir, "avatars").apply { mkdirs() }, "$hash.jpg")
    }

    /** The saved photo, or null. */
    fun get(c: Context): File? = file(c).takeIf { it.exists() }

    /** Saves the picked image as a square 512 px photo (turned the right way up). */
    suspend fun set(c: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            val raw = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val rotation = c.contentResolver.openInputStream(uri)?.use {
                when (androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            } ?: 0f
            val side = minOf(raw.width, raw.height)
            val m = Matrix().apply { postRotate(rotation); val s = 512f / side; postScale(s, s) }
            val square = Bitmap.createBitmap(raw, (raw.width - side) / 2, (raw.height - side) / 2, side, side, m, true)
            file(c).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            version.value++
            true
        }.getOrDefault(false)
    }

    fun remove(c: Context) {
        file(c).delete()
        version.value++
    }
}
