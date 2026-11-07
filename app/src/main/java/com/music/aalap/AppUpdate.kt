package com.music.aalap

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Up to date" / "Update available" in Profile, from the latest release on Aalap's GitHub
 * repository. Checked only while Profile is open, at most once every [CHECK_EVERY_MS] (the
 * answer is remembered), so nothing runs in the background.
 *
 * Safe by construction:
 *  - only Aalap's own repository is asked, over HTTPS, and only a link to an `Aalap-<version>.apk`
 *    file in that repository's releases is ever opened;
 *  - the APK is downloaded by Aalap and checked before it's offered: same app id, a newer
 *    version, and signed with the same key as the installed Aalap ([verify]); anything else is
 *    deleted. Android's installer then checks the signature again and updates in place, so the
 *    library, likes and settings stay. Nothing is ever uninstalled.
 */
object AppUpdate {
    const val REPO = "vrajchauhan10/aalap"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 3_600_000L
    private const val PREF = "app_update"

    data class Release(val version: String, val apkUrl: String, val pageUrl: String)

    sealed interface State {
        data object Checking : State
        data class UpToDate(val latest: String) : State
        data class Available(val release: Release) : State
        /** No published release yet, or GitHub couldn't be reached. */
        data object Unknown : State
    }

    val state = MutableStateFlow<State?>(null)

    /** Progress of an in-app update, shown on the Profile row. */
    sealed interface Install {
        data class Downloading(val percent: Int) : Install
        data object Verifying : Install
        /** Android needs "Install unknown apps" allowed for Aalap (asked once). */
        data object NeedsPermission : Install
        data class Failed(val why: String) : Install
    }

    val install = MutableStateFlow<Install?>(null)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate)

    // ── pure (tested) ───────────────────────────────────────────────────────

    /** "7.10.0" is newer than "7.9.2"; a leading "v" and a "-debug" style suffix are ignored. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Only HTTPS links into this repository's releases are ever opened. */
    fun isTrusted(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val path = u.path ?: return false
        return u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 &&
            path.startsWith("/$REPO/releases/") && ".." !in path
    }

    /** Reads GitHub's "latest release" answer; null when it has no Aalap APK we'd trust. */
    fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.optString("tag_name").removePrefix("v").ifBlank { return null }
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == "Aalap-$version.apk" }
            ?.optString("browser_download_url")?.takeIf { isTrusted(it) } ?: return null
        val page = o.optString("html_url").takeIf { isTrusted(it) } ?: "https://github.com/$REPO/releases/latest"
        Release(version, apk, page)
    }.getOrNull()

    /**
     * Whether to remind about [version] now: the first time it's seen, then again every
     * [REMIND_EVERY_MS] until the phone is updated (a newer version is reminded at once).
     */
    fun shouldRemind(version: String, lastVersion: String?, lastAt: Long, now: Long, every: Long = REMIND_EVERY_MS): Boolean =
        version != lastVersion || now - lastAt >= every

    // ── reminding ───────────────────────────────────────────────────────────

    private const val REMIND_EVERY_MS = 3 * 24 * 3_600_000L
    private const val ASK_EVERY_MS = 24 * 3_600_000L
    const val ACTION_OPEN_UPDATE = "com.music.aalap.OPEN_UPDATE"

    /** Set by the notification: open Profile and offer the update there. */
    val openRequest = MutableStateFlow(false)

    /** The newer version last found, from the remembered answer (no network): for Home's chip. */
    fun knownNewer(context: Context): String? = knownRelease(context)?.version

    /** The newer release last found (no network), or null once you're on it or newer. */
    fun knownRelease(context: Context): Release? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("json", null)
            ?.let { parse(it) }?.takeIf { isNewer(it.version, current()) }

    /**
     * Called from the hourly library sync, so it adds no wake-ups of its own: asks GitHub at
     * most once a day and, when a newer Aalap exists, shows "Aalap X is available" (again
     * every 3 days until updated). Tapping it opens Profile with Update now.
     */
    suspend fun remindIfDue(context: Context) {
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("asked_at", 0L) < ASK_EVERY_MS) return
        p.edit().putLong("asked_at", now).apply()
        // Fresh, not the remembered answer: that can be hours old and say "up to date" when a
        // release has come out since (seen on the S24: no reminder, then none for a day).
        check(app, force = true)
        val r = (state.value as? State.Available)?.release ?: return
        if (!shouldRemind(r.version, p.getString("reminded", null), p.getLong("reminded_at", 0L), now)) return
        p.edit().putString("reminded", r.version).putLong("reminded_at", now).apply()
        runCatching {
            val nm = app.getSystemService(android.app.NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                nm.createNotificationChannel(android.app.NotificationChannel("updates", "App updates", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val open = android.app.PendingIntent.getActivity(app, 11,
                Intent(app, MainActivity::class.java).setAction(ACTION_OPEN_UPDATE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE)
            nm.notify(NOTIFICATION_ID, androidx.core.app.NotificationCompat.Builder(app, "updates")
                .setSmallIcon(R.drawable.media3_notification_small_icon)
                .setContentTitle("Aalap ${r.version} is available")
                .setContentText("You have ${current()}. Tap to update; your library, likes and settings stay.")
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }

    private const val NOTIFICATION_ID = 7310

    // ── on opening Aalap ────────────────────────────────────────────────────

    /** A newer Aalap found when the app opened: the app shows "Update now" over any screen. */
    val launchOffer = MutableStateFlow<Release?>(null)
    @Volatile private var launchChecked = false

    /**
     * Once per app start: asks GitHub (fresh unless asked in the last hour) and offers a newer
     * Aalap. After "Later" the same version isn't offered on opening again for a day.
     */
    suspend fun checkAtLaunch(context: Context) {
        if (launchChecked) return
        launchChecked = true
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        check(app, force = now - p.getLong("at", 0L) > 3_600_000L)
        val r = (state.value as? State.Available)?.release ?: return
        if (!shouldRemind(r.version, p.getString("offered", null), p.getLong("offered_at", 0L), now, every = ASK_EVERY_MS)) return
        p.edit().putString("offered", r.version).putLong("offered_at", now).apply()
        launchOffer.value = r
    }

    // ── checking ────────────────────────────────────────────────────────────

    fun current(): String = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Shows the remembered answer at once, and asks GitHub again when it's older than 6 hours. */
    suspend fun check(context: Context, force: Boolean = false) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val cached = p.getString("json", null)
        val at = p.getLong("at", 0L)
        cached?.let { state.value = stateFor(parse(it), it) }
        if (!force && cached != null && System.currentTimeMillis() - at < CHECK_EVERY_MS) return
        if (state.value == null) state.value = State.Checking
        val body = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() }
        if (body == null) {
            if (cached == null) state.value = State.Unknown
            return
        }
        p.edit().putString("json", body).putLong("at", System.currentTimeMillis()).apply()
        state.value = stateFor(parse(body), body)
    }

    private const val NO_RELEASE = "{\"none\":true}"

    private fun stateFor(r: Release?, body: String? = null): State = when {
        body == NO_RELEASE -> State.UpToDate(current())
        r == null -> State.Unknown
        isNewer(r.version, current()) -> State.Available(r)
        else -> State.UpToDate(r.version)
    }

    private fun fetch(): String? {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "Aalap/${current()}")
            return when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }
                404 -> NO_RELEASE // the repository has no published release yet: nothing newer exists
                else -> null
            }
        } finally {
            c.disconnect()
        }
    }

    /**
     * One tap: download the release, check it ([verify]) and open Android's installer. Runs
     * outside the screen, so leaving Profile doesn't stop it; a second tap while it runs is ignored.
     */
    fun update(context: Context, release: Release) {
        val app = context.applicationContext
        val busy = install.value
        if (busy is Install.Downloading || busy == Install.Verifying) return
        if (!isTrusted(release.apkUrl)) { install.value = Install.Failed("That download link isn't Aalap's."); return }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            install.value = Install.NeedsPermission
            runCatching {
                app.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        install.value = Install.Downloading(0)
        runCatching { app.getSystemService(android.app.NotificationManager::class.java).cancel(NOTIFICATION_ID) }
        scope.launch {
            val dir = java.io.File(app.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // an older update file is never offered
            val apk = java.io.File(dir, "Aalap-${release.version}.apk")
            val ok = withContext(Dispatchers.IO) {
                runCatching { downloadTo(release.apkUrl, apk) { install.value = Install.Downloading(it) } }.isSuccess
            }
            if (!ok) { apk.delete(); install.value = Install.Failed("Download didn't finish. Check the connection and tap again."); return@launch }
            install.value = Install.Verifying
            val problem = withContext(Dispatchers.IO) { verify(app, apk) }
            if (problem != null) { apk.delete(); install.value = Install.Failed(problem); return@launch }
            install.value = null
            openInstaller(app, apk)
        }
    }

    private fun downloadTo(url: String, out: java.io.File, progress: (Int) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.instanceFollowRedirects = true // github.com → its release file host (HTTPS only)
            c.setRequestProperty("User-Agent", "Aalap/${current()}")
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            if (c.url.protocol != "https") error("not HTTPS")
            val total = c.contentLengthLong.takeIf { it > 0 }
            var done = 0L; var shown = -1
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (done > MAX_APK_BYTES) error("too large")
                        val pct = total?.let { (done * 100 / it).toInt() } ?: 0
                        if (pct != shown) { shown = pct; progress(pct) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    /**
     * Null when [apk] is a genuine, newer Aalap: same app id, a higher version code, and
     * signed by exactly the key(s) the installed Aalap is signed with. Otherwise why not.
     */
    fun verify(context: Context, apk: java.io.File): String? {
        val pm = context.packageManager
        val sigFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") android.content.pm.PackageManager.GET_SIGNATURES
        val file = pm.getPackageArchiveInfo(apk.path, sigFlag) ?: return "The downloaded file isn't a working app."
        val mine = pm.getPackageInfo(context.packageName, sigFlag)
        if (file.packageName != context.packageName) return "The downloaded file is a different app."
        if (versionCodeOf(file) <= versionCodeOf(mine)) return "The downloaded version isn't newer than this one."
        if (signers(file) != signers(mine) || signers(mine).isEmpty()) return "The download isn't signed by Aalap's key, so it wasn't installed."
        // And it must be Aalap's official key, not just the same key as this copy: a modified
        // copy signed with someone else's key could otherwise accept that person's "updates".
        if (!BuildConfig.DEBUG && OFFICIAL_SIGNER !in signers(file)) return "The download isn't an official Aalap release, so it wasn't installed."
        return null
    }

    /**
     * SHA-256 of Aalap's release signing certificate (scripts/release-signer.sha256). Only the
     * owner of the private key, which is not in the repository, can sign an app with it.
     */
    const val OFFICIAL_SIGNER = "1c1e8f2cfb03494bcde526b2a3ca25d0ddbcd9334b648600b174a46581a3e6c1"

    /**
     * False when this installed copy was not signed with Aalap's key: someone rebuilt or
     * modified it. Debug builds (signed with the debug key) count as genuine. Read once.
     */
    fun genuine(context: Context): Boolean = genuineCache ?: run {
        val ok = BuildConfig.DEBUG || runCatching {
            val sigFlag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") android.content.pm.PackageManager.GET_SIGNATURES
            OFFICIAL_SIGNER in signers(context.packageManager.getPackageInfo(context.packageName, sigFlag))
        }.getOrDefault(true)
        genuineCache = ok
        ok
    }
    @Volatile private var genuineCache: Boolean? = null

    @Suppress("DEPRECATION")
    private fun versionCodeOf(p: android.content.pm.PackageInfo): Long =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    /** SHA-256 of each signing certificate. */
    @Suppress("DEPRECATION")
    private fun signers(p: android.content.pm.PackageInfo): Set<String> {
        val certs = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val si = p.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else p.signatures
        // With key rotation the history lists the older keys too; [verify] compares the sets.
        return certs.orEmpty().map { c ->
            java.security.MessageDigest.getInstance("SHA-256").digest(c.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun openInstaller(context: Context, apk: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { install.value = Install.Failed("Couldn't open the installer.") }
    }
}
