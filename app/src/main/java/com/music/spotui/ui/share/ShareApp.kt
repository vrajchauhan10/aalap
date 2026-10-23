package com.music.spotui.ui.share

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
    suspend fun share(context: Context) {
        val c = context.applicationContext
        val version = c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""
        val apk = withContext(Dispatchers.IO) {
            val dir = File(c.cacheDir, "share").apply { mkdirs() }
            dir.listFiles { f -> f.name.startsWith("Aalap-") && f.name.endsWith(".apk") }?.forEach { it.delete() }
            File(dir, "Aalap-$version.apk").also { out -> File(c.applicationInfo.sourceDir).copyTo(out, overwrite = true) }
        }
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
