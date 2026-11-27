package com.music.aalap

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

/**
 * When Aalap closes unexpectedly, keeps a report of why, by itself: the phone, its Android
 * and system version, Aalap's version, the error, and Aalap's own last log lines (what it was
 * doing just before). Freezes ("Aalap isn't responding") and crashes in native code, which
 * the app can't catch itself, are read from Android's own record at the next start.
 *
 * The last [KEEP] reports stay on the phone; Settings and "Is everything working?" offer to
 * share them as one file. Nothing is sent anywhere by itself.
 */
object CrashReport {
    private const val DIR = "crash"
    private const val KEEP = 10

    private fun dir(c: Context) = File(c.filesDir, DIR).apply { mkdirs() }

    /** Installed first thing at start; passes every crash on to Android as before. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                save(app, "Crash", describe(app) + "Thread: ${thread.name}\n\n" + error.stackTraceToString().take(60_000) + recentLog())
            }
            previous?.uncaughtException(thread, error)
        }
        // Freezes and native crashes from earlier runs, off the main thread.
        Thread { runCatching { readExitReasons(app) } }.start()
    }

    private fun save(c: Context, kind: String, text: String, at: Long = System.currentTimeMillis()) {
        File(dir(c), "${at}_$kind.txt").writeText(text)
        dir(c).listFiles().orEmpty().sortedByDescending { it.name }.drop(KEEP).forEach { it.delete() }
    }

    /**
     * Aalap's own last log lines (Android lets an app read its own). The most useful part of a
     * report: what it was doing right before. Capped at 2 seconds so a crash isn't held up.
     */
    private fun recentLog(): String = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "400", "--pid=${android.os.Process.myPid()}")
            .redirectErrorStream(true).start()
        val out = StringBuilder()
        val reader = Thread { runCatching { out.append(p.inputStream.bufferedReader().readText()) } }.apply { start() }
        reader.join(2_000)
        p.destroy()
        "\n\n--- Recent Aalap log ---\n" + scrub(out.toString().takeLast(80_000))
    }.getOrDefault("")

    /**
     * Masks sign-in secrets and email addresses in log lines, so a shared report never carries
     * a login (cookies, tokens, keys) or who you are.
     */
    fun scrub(text: String): String = text
        .replace(Regex("(?i)\\bbearer\\s+[^\\s\"';,&]+"), "Bearer ***")
        .replace(Regex("(?i)(sp_dc|sp_key|cookie|authorization|bearer|token|access_token|api[_-]?key|visitor_?data|password)([\"']?\\s*[:= ]\\s*[\"']?)[^\\s\"';,&]+"), "$1$2***")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "***@***")

    /** Android keeps why each run ended: record freezes and native crashes we haven't seen yet. */
    private fun readExitReasons(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = c.getSystemService(android.app.ActivityManager::class.java) ?: return
        val prefs = c.getSharedPreferences("crash_report", Context.MODE_PRIVATE)
        val seen = prefs.getLong("exit_seen", 0L)
        var newest = seen
        am.getHistoricalProcessExitReasons(c.packageName, 0, 8).forEach { e ->
            if (e.timestamp <= seen) return@forEach
            newest = maxOf(newest, e.timestamp)
            val kind = when (e.reason) {
                android.app.ApplicationExitInfo.REASON_ANR -> "Freeze"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "NativeCrash"
                else -> return@forEach // Java crashes are already saved by the handler above
            }
            val trace = runCatching { e.traceInputStream?.bufferedReader()?.use { it.readText().take(80_000) } }.getOrNull().orEmpty()
            save(c, kind, describe(c) + "What happened: ${if (kind == "Freeze") "Aalap stopped responding" else "Aalap closed (native code)"}\n" +
                "When: ${java.time.Instant.ofEpochMilli(e.timestamp)}\nAndroid says: ${e.description.orEmpty()}\n" +
                "Process: ${e.processName}, importance ${e.importance}\n\n" + trace, e.timestamp)
        }
        if (newest > seen) prefs.edit().putLong("exit_seen", newest).apply()
    }

    /** The phone and versions, the part of a report that's useful even without a crash. */
    fun describe(context: Context): String {
        val app = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) app?.longVersionCode else app?.versionCode?.toLong()
        return buildString {
            appendLine("Aalap ${app?.versionName} ($code)")
            appendLine("Time: ${java.time.ZonedDateTime.now()}")
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }

    /** Saved reports, newest first. */
    fun reports(context: Context): List<File> =
        dir(context).listFiles().orEmpty().filter { it.isFile && it.length() > 0 }.sortedByDescending { it.name }

    /** Reports from the last [days] days. */
    fun recentCount(context: Context, days: Int = 7): Int {
        val cut = System.currentTimeMillis() - days * 86_400_000L
        return reports(context).count { (it.name.substringBefore('_').toLongOrNull() ?: 0L) >= cut }
    }

    fun last(context: Context): File? = reports(context).firstOrNull()

    fun clear(context: Context) { reports(context).forEach { it.delete() } }

    /** Opens the share sheet with every saved report in one text file (any app can take it). */
    fun share(context: Context) {
        val all = reports(context)
        if (all.isEmpty()) return
        val text = all.joinToString("\n\n==========\n\n") { f -> "[${f.name.substringAfter('_').removeSuffix(".txt")}]\n" + f.readText() }
        val out = File(File(context.cacheDir, "share").apply { mkdirs() }, "aalap-problem-report.txt").apply { writeText(text) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", out)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Aalap problem report")
                    .putExtra(Intent.EXTRA_TEXT, "Aalap problem report: ${all.size} report${if (all.size == 1) "" else "s"} attached.\n\n" + text.take(4_000))
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share problem report",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
