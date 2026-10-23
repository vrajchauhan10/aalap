package com.music.aalap.ui.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shares Aalap itself: the app installed on this phone, as "Aalap-3.5.0.apk", through
 * WhatsApp, Drive, Quick Share, Gmail… The receiver taps the file to install it (allowing
 * installs from that app the first time).
 */
object ShareApp {
    /** The app file, ready to send (copied once per version, so later shares are instant). */
    suspend fun prepare(context: Context): File = withContext(Dispatchers.IO) {
        val c = context.applicationContext
        val version = c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "Aalap-$version.apk")
        val src = File(c.applicationInfo.sourceDir)
        if (!out.exists() || out.length() != src.length()) {
            dir.listFiles { f -> f.name.startsWith("Aalap-") && f.name.endsWith(".apk") }?.forEach { it.delete() }
            val tmp = File(dir, "partial.tmp")
            src.copyTo(tmp, overwrite = true)
            tmp.renameTo(out)
        }
        out
    }

    suspend fun share(context: Context) {
        val c = context.applicationContext
        val apk = prepare(c)
        val version = apk.name.removePrefix("Aalap-").removeSuffix(".apk")
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", apk)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Aalap $version: all your music in one app. Tap the file to install it.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share Aalap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
