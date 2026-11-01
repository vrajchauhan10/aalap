package com.music.aalap

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File

/**
 * When Aalap closes unexpectedly, keeps a short report of why (the phone, its Android and
 * system version, Aalap's version and the error), so it can be shared from Settings on the
 * next launch. Nothing is sent anywhere by itself. Made for phones we can't test on (a
 * startup crash on one maker's Android build can't be found without it).
 */
object CrashReport {
    private const val DIR = "crash"
    private const val FILE = "last.txt"

    /** Installed first thing at start; passes every crash on to Android as before. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        File(dir, FILE).writeText(describe(context) + "Thread: ${thread.name}\n\n" + error.stackTraceToString().take(60_000))
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

    fun last(context: Context): File? = File(File(context.filesDir, DIR), FILE).takeIf { it.isFile && it.length() > 0 }

    fun clear(context: Context) { last(context)?.delete() }

    /** Opens the share sheet with the last report (as text, so any app can take it). */
    fun share(context: Context) {
        val text = last(context)?.readText() ?: return
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Aalap crash report")
                    .putExtra(Intent.EXTRA_TEXT, text),
                "Share crash report",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
