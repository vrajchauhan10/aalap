package com.music.aalap.di

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Offline downloads: resolves a track's best source and saves it under filesDir/downloads. */
object Downloads {
    private const val TAG = "Downloads"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks which song queries are mid-download so the UI can show a spinner and
    // we don't kick off the same download twice.
    private val downloading = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    @Volatile var onDownloadsChanged: (() -> Unit)? = null

    // Per-query download progress, 0..100. Present only while a download is active.
    private val downloadProgress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    // The actual SongsModel of each in-progress download, so the Downloads screen can
    // render it (with a progress bar) before the file exists / it's added to prefs.
    private val downloadingSongs =
        java.util.concurrent.ConcurrentHashMap<String, com.music.aalap.data.entity.SongsModel>()

    fun isDownloading(query: String): Boolean = downloading.contains(query)

    /**
     * Songs downloaded since you last opened Downloads: a small dot on the Downloads chip
     * and tile says something new is there. Kept across restarts.
     */
    val unseen = kotlinx.coroutines.flow.MutableStateFlow(0)
    private const val UNSEEN_PREF = "downloads_unseen"
    fun loadUnseen(c: Context) { unseen.value = c.getSharedPreferences(UNSEEN_PREF, Context.MODE_PRIVATE).getInt("n", 0) }
    fun markSeen(c: Context) {
        unseen.value = 0
        c.getSharedPreferences(UNSEEN_PREF, Context.MODE_PRIVATE).edit().putInt("n", 0).apply()
    }
    private fun addUnseen(c: Context) {
        unseen.value += 1
        c.getSharedPreferences(UNSEEN_PREF, Context.MODE_PRIVATE).edit().putInt("n", unseen.value).apply()
    }

    /** Current download progress (0..100) for a query, or -1 if unknown/not downloading. */
    fun downloadProgress(query: String): Int = downloadProgress[query] ?: -1

    /** Snapshot of the currently-downloading tracks paired with their percent (0..100). */
    fun downloadingSnapshot(): List<Pair<com.music.aalap.data.entity.SongsModel, Int>> =
        downloadingSongs.entries.map { (q, song) -> song to (downloadProgress[q] ?: 0) }

    // Last download failure reason, surfaced to the user as a Toast for diagnosis.
    @Volatile var lastDownloadError: String? = null

    // googlevideo stream URLs 403 without a browser User-Agent and need redirects
    // followed (http↔https) — ExoPlayer does both, so a raw URLConnection must too.
    private fun openDownloadConn(url: String): java.net.HttpURLConnection =
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
        }

    /**
     * Download [url] to [tmpFile] using HTTP **Range** requests in chunks, reporting
     * progress (0..100) for [query]. A single full-file GET of a googlevideo stream gets
     * reset partway through (`SocketException: Connection reset`) — the server expects the
     * audio fetched in byte ranges, which is how ExoPlayer/NewPipe get it. Each chunk is a
     * short connection (retried a few times on reset); writing is append-continuous so a
     * retried chunk resumes from the current byte position. Returns true iff the whole
     * file was written. Falls back gracefully if the server ignores Range (HTTP 200).
     */
    private fun httpDownloadRanged(url: String, tmpFile: java.io.File, query: String): Boolean {
        val chunk = 8L * 1024 * 1024 // 8 MB
        var total = -1L
        var position = 0L
        downloadProgress[query] = 0
        try {
            java.io.BufferedOutputStream(tmpFile.outputStream()).use { output ->
                outer@ while (true) {
                    val end = if (total > 0) minOf(position + chunk - 1, total - 1) else position + chunk - 1
                    var attempt = 0
                    var fullBody = false
                    while (true) {
                        attempt++
                        val conn = openDownloadConn(url)
                        conn.setRequestProperty("Range", "bytes=$position-$end")
                        try {
                            val code = conn.responseCode
                            if (code !in 200..299) {
                                lastDownloadError = "Stream returned HTTP $code"
                                return false
                            }
                            if (total < 0) {
                                total = conn.getHeaderField("Content-Range")
                                    ?.substringAfter('/')?.toLongOrNull()
                                    ?: conn.contentLengthLong
                            }
                            fullBody = code == 200 // server ignored Range → whole file in one body
                            conn.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = input.read(buf)
                                    if (r < 0) break
                                    output.write(buf, 0, r)
                                    position += r
                                    if (total > 0) {
                                        val pct = ((position * 100) / total).toInt().coerceIn(0, 100)
                                        if (downloadProgress[query] != pct) {
                                            downloadProgress[query] = pct
                                            onDownloadsChanged?.invoke()
                                        }
                                    }
                                }
                            }
                            break // this chunk completed
                        } catch (e: Exception) {
                            Log.w(TAG, "chunk @${position} failed (attempt $attempt): ${e.message}")
                            if (attempt >= 4) {
                                lastDownloadError = e.message ?: "Connection reset"
                                return false
                            }
                            // retry the remainder of this chunk from the current position
                        } finally {
                            conn.disconnect()
                        }
                    }
                    if (fullBody) { total = position; break@outer }
                    if (total in 1..position) break@outer
                    if (total < 0) break@outer // couldn't determine size; assume done
                }
            }
            downloadProgress[query] = 100
            return total <= 0 || position >= total
        } catch (e: Exception) {
            lastDownloadError = e.message ?: "Download error"
            return false
        }
    }

    /** A whole album or playlist downloading: one bar for all of it. */
    data class Batch(val name: String, val total: Int, val done: Int, val failed: Int = 0) {
        val finished get() = done + failed >= total
    }
    val batch = kotlinx.coroutines.flow.MutableStateFlow<Batch?>(null)

    /** Download every track in a list (album/playlist), shown as one bar named [name]. */
    /** False when nothing started (Wi-Fi only on mobile data, or all already saved). */
    fun downloadAll(
        songs: List<com.music.aalap.data.entity.SongsModel>,
        context: Context,
        name: String = "",
        quality: com.music.aalap.data.preferences.StreamQuality? = null,
    ): Boolean {
        if (!allowedNow(context)) return false
        val todo = songs.filter { !com.music.aalap.data.preferences.isDownloaded(context.applicationContext, it.id.toString()) }
        if (todo.isEmpty()) return false
        batch.value = Batch(name, todo.size, 0)
        val app = context.applicationContext
        todo.forEach { song ->
            downloadSong(song, context, quiet = true, quality = quality) { ok ->
                val b = batch.value?.takeIf { it.name == name }?.let { if (ok) it.copy(done = it.done + 1) else it.copy(failed = it.failed + 1) }
                batch.value = b
                // One message for the whole list, only if some songs couldn't be saved anywhere.
                if (b != null && b.finished && b.failed > 0) android.widget.Toast.makeText(
                    app, "${b.done} of ${b.total} saved. ${b.failed} weren't available anywhere right now; download again later to retry them.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        return true
    }

    /**
     * "Download on Wi-Fi only": on mobile data a download doesn't start; Aalap says why.
     * (No waiting in the background for Wi-Fi: that would mean watching the network forever.)
     */
    fun wifiOnly(c: Context) = c.getSharedPreferences("downloads", Context.MODE_PRIVATE).getBoolean("wifi_only", false)
    fun setWifiOnly(c: Context, on: Boolean) = c.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().putBoolean("wifi_only", on).apply()

    private fun allowedNow(context: Context): Boolean {
        if (!wifiOnly(context)) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (!cm.isActiveNetworkMetered) return true
        android.widget.Toast.makeText(context.applicationContext, "Downloads are set to Wi-Fi only. Connect to Wi-Fi, or change it in Profile.", android.widget.Toast.LENGTH_LONG).show()
        return false
    }

    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    /**
     * Stop every pending download. Tracks still waiting for a slot stop at once; the
     * few already transferring finish (blocking reads don't see cancellation).
     */
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        onDownloadsChanged?.invoke()
    }

    // A whole playlist at once floods the sources with lookups and gets rate limited.
    private val downloadSlots = Semaphore(3)

    /** True once every track in [songs] is downloaded (for the album's "downloaded" state). */
    fun allDownloaded(
        songs: List<com.music.aalap.data.entity.SongsModel>,
        context: Context,
    ): Boolean {
        if (songs.isEmpty()) return false
        val appContext = context.applicationContext
        return songs.all {
            com.music.aalap.data.preferences.isDownloaded(appContext, it.id.toString())
        }
    }

    /**
     * Resolve the track's stream and save the audio to local storage for offline
     * playback. Runs on the IO scope; invokes [onComplete] (main thread) with whether
     * it succeeded. No-op if it's already downloaded or downloading.
     *
     * A song gets three full rounds through every source (best quality first), with a pause
     * between rounds, before Aalap gives up on it: a busy source or a passing network drop
     * no longer fails the song, and nothing is shown while it's still trying.
     */
    fun downloadSong(
        song: com.music.aalap.data.entity.SongsModel,
        context: Context,
        quiet: Boolean = false,
        // The quality picked in the download chooser; null = the saved download quality.
        quality: com.music.aalap.data.preferences.StreamQuality? = null,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        val query = song.url
        if (!allowedNow(context)) { onComplete(false); return }
        if (query.isBlank() ||
            com.music.aalap.data.preferences.isDownloaded(appContext, song.id.toString()) ||
            !downloading.add(query)
        ) return
        downloadingSongs[query] = song
        downloadProgress[query] = 0
        onDownloadsChanged?.invoke()
        jobs[query] = scope.launch {
            var ok = false
            for (round in 0 until ROUND_PAUSES_MS.size + 1) {
                if (round > 0) kotlinx.coroutines.delay(ROUND_PAUSES_MS[round - 1])
                ok = runCatching { downloadSlots.withPermit { downloadToFile(song, appContext, quality ?: com.music.aalap.data.preferences.getDownloadQuality(appContext)) } }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.w(TAG, "round $round: ${it.message}") }
                    .getOrDefault(false)
                if (ok) break
                Log.w(TAG, "round $round found no source for ${song.title}; trying again")
            }
            jobs.remove(query)
            downloading.remove(query)
            downloadProgress.remove(query)
            downloadingSongs.remove(query)
            withContext(Dispatchers.Main) {
                if (ok) addUnseen(appContext)
                // Only once every source failed three times, and only for a single song: a
                // playlist reports its count on its own bar instead of one message per song.
                if (!ok && !quiet) android.widget.Toast.makeText(
                    appContext,
                    "Couldn't save \"${song.title}\" right now. Tap download again later.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                onDownloadsChanged?.invoke()
                onComplete(ok)
            }
        }
    }

    /** Pauses before the second and third rounds: long enough for a refusing source to recover. */
    private val ROUND_PAUSES_MS = longArrayOf(8_000L, 30_000L)

    // YouTube decodes each stream in a hidden web page; one song at a time there keeps a
    // playlist from flooding it (and from YouTube refusing a burst of requests).
    private val youTubeSlot = Semaphore(1)

    private fun dir(c: Context) = java.io.File(c.filesDir, "downloads").apply { mkdirs() }

    /** Save [url] as the song's file with [ext]; true when the whole file is there. */
    private fun saveDirect(c: Context, song: com.music.aalap.data.entity.SongsModel, url: String, ext: String, from: String): Boolean {
        val outFile = java.io.File(dir(c), "${song.id}.$ext")
        val tmpFile = java.io.File(dir(c), "${song.id}.$ext.part")
        if (httpDownloadRanged(url, tmpFile, song.url) && tmpFile.length() > MIN_AUDIO_BYTES && tmpFile.renameTo(outFile)) {
            com.music.aalap.data.preferences.addDownload(c, song, outFile.absolutePath)
            Log.d(TAG, "downloaded from $from: ${song.title}")
            return true
        }
        runCatching { tmpFile.delete() }
        return false
    }

    // Anything smaller is an error page or a preview clip, not a song.
    private const val MIN_AUDIO_BYTES = 200_000L

    /**
     * Every source in order of quality; the first that delivers the whole file wins. The
     * source switches in Settings decide what *plays*; a download tries them all, so the
     * best copy available anywhere is saved. Soulseek stays off unless you turned it on
     * (it shows your address to other people).
     */
    private suspend fun downloadToFile(
        song: com.music.aalap.data.entity.SongsModel,
        appContext: Context,
        q: com.music.aalap.data.preferences.StreamQuality,
    ): Boolean {
        val sec = song.durationMs / 1000
        val search = "${song.title} ${song.singer}"
        val saavnKbps = when (q) {
            com.music.aalap.data.preferences.StreamQuality.LOW -> 96
            com.music.aalap.data.preferences.StreamQuality.NORMAL -> 160
            else -> 320
        }
        suspend fun lossless(): Boolean {
            if (song.spotifyTrackId.isBlank()) return false
            val r = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(45_000) {
                    com.music.aalap.lossless.LosslessSource.resolve(
                        appContext, song.spotifyTrackId,
                        com.music.aalap.lossless.LosslessSource.TrackInfo(song.title, song.singer.substringBefore(",").trim(), sec),
                        preferHiRes = SongPlayer.losslessHiRes,
                    )
                }
            }.getOrNull()
            return r is com.music.aalap.lossless.LosslessSource.Result.Success &&
                saveDirect(appContext, song, r.track.url, "flac", "lossless ${r.track.provider}")
        }

        // 1) Lossless FLAC first, when "Best" was picked.
        if (q.lossless && lossless()) return true

        // 2) JioSaavn AAC, at most the picked bitrate.
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(25_000) { com.music.aalap.saavn.JioSaavnSource.resolve(song.title, song.singer, sec, search, maxKbps = saavnKbps) }
        }.getOrNull()?.let { m -> if (saveDirect(appContext, song, m.url, "m4a", "JioSaavn ${m.kbps} kbps")) return true }

        // 3) YouTube in the picked quality (song uploads, then other uploads such as lyric videos).
        val yt = youTubeSlot.withPermit {
            runCatching { SongPlayer.resolveYtPlayback(song.url, q.audioQuality, appContext, allowVideos = true) }.getOrNull()
        }
        if (yt != null && saveDirect(appContext, song, yt.streamUrl, "m4a", "YouTube")) return true

        // 4) Gaana (HLS: its pieces are joined into one file).
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(25_000) { com.music.aalap.gaana.GaanaSource.resolve(song.title, song.singer, sec, "", search) }
        }.getOrNull()?.let { m -> if (saveHls(appContext, song, m.url)) return true }

        // 5) JioSaavn with a looser match (same song, title written differently).
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(25_000) { com.music.aalap.saavn.JioSaavnSource.resolve(song.title, song.singer, sec, search, relaxed = true, maxKbps = saavnKbps) }
        }.getOrNull()?.let { m -> if (saveDirect(appContext, song, m.url, "m4a", "JioSaavn (loose)")) return true }

        // A smaller quality was picked but nothing else had the song: a lossless copy beats none.
        if (!q.lossless && lossless()) return true

        // 6) Soulseek FLAC, only when you turned it on.
        if (com.music.aalap.data.preferences.isSoulseekEnabled(appContext)) {
            val f = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(90_000) {
                    com.music.aalap.soulseek.SoulseekSource.flac(appContext, "dl_${song.id}", song.title, song.singer, sec)
                }
            }.getOrNull()
            if (f != null && f.length() > MIN_AUDIO_BYTES) {
                val out = java.io.File(dir(appContext), "${song.id}.flac")
                if (runCatching { f.copyTo(out, overwrite = true) }.isSuccess) {
                    com.music.aalap.data.preferences.addDownload(appContext, song, out.absolutePath)
                    return true
                }
            }
        }
        return false
    }

    /**
     * Saves an HLS stream (Gaana) as one file: the best variant's pieces in order, joined.
     * MPEG-TS pieces are saved as .ts, fMP4 pieces (with their header piece) as .mp4; the
     * player reads both. Encrypted streams are skipped.
     */
    private fun saveHls(c: Context, song: com.music.aalap.data.entity.SongsModel, url: String): Boolean {
        fun get(u: String) = openDownloadConn(u).run { try { if (responseCode in 200..299) inputStream.use { it.readBytes() } else null } finally { disconnect() } }
        fun abs(base: String, ref: String) = java.net.URL(java.net.URL(base), ref).toString()
        return runCatching {
            var listUrl = url
            var text = get(listUrl)?.toString(Charsets.UTF_8) ?: return false
            if ("#EXT-X-STREAM-INF" in text) {
                val lines = text.lines()
                val best = lines.indices.filter { lines[it].startsWith("#EXT-X-STREAM-INF") }
                    .maxByOrNull { Regex("BANDWIDTH=(\\d+)").find(lines[it])?.groupValues?.get(1)?.toLongOrNull() ?: 0 }
                    ?: return false
                listUrl = abs(listUrl, lines.drop(best + 1).first { it.isNotBlank() && !it.startsWith("#") }.trim())
                text = get(listUrl)?.toString(Charsets.UTF_8) ?: return false
            }
            if (Regex("#EXT-X-KEY:.*METHOD=(?!NONE)").containsMatchIn(text)) return false
            val init = Regex("#EXT-X-MAP:.*URI=\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            val parts = text.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
            if (parts.isEmpty()) return false
            val ext = if (init != null) "mp4" else "ts"
            val outFile = java.io.File(dir(c), "${song.id}.$ext")
            val tmpFile = java.io.File(dir(c), "${song.id}.$ext.part")
            java.io.BufferedOutputStream(tmpFile.outputStream()).use { out ->
                if (init != null) out.write(get(abs(listUrl, init)) ?: return false)
                parts.forEachIndexed { i, part ->
                    var bytes: ByteArray? = null
                    for (attempt in 1..3) { bytes = runCatching { get(abs(listUrl, part)) }.getOrNull(); if (bytes != null) break }
                    out.write(bytes ?: return false)
                    downloadProgress[song.url] = ((i + 1) * 100 / parts.size)
                    onDownloadsChanged?.invoke()
                }
            }
            if (tmpFile.length() > MIN_AUDIO_BYTES && tmpFile.renameTo(outFile)) {
                com.music.aalap.data.preferences.addDownload(c, song, outFile.absolutePath)
                Log.d(TAG, "downloaded from Gaana: ${song.title}")
                true
            } else { tmpFile.delete(); false }
        }.getOrDefault(false)
    }

}
