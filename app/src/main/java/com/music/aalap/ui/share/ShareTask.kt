package com.music.aalap.ui.share

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Runs one share at a time, outside any screen. Shares used to run in the menu's own
 * scope, and closing the menu cancelled them: the first "Share Aalap" (copying the 14 MB
 * app takes a moment) did nothing and only the second tap opened the share sheet.
 * While a share is being prepared, more taps are ignored (no double share sheets), and if
 * it takes more than a moment a short "Getting … ready" message says something is happening.
 */
object ShareTask {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True while a share is being prepared (the player shows a spinner on its share icon). */
    val busy = MutableStateFlow(false)

    fun run(context: Context, what: String, block: suspend (Context) -> Unit) {
        if (busy.value) return
        val app = context.applicationContext
        busy.value = true
        scope.launch {
            val hint = launch {
                delay(400)
                Toast.makeText(app, "Getting $what ready to share…", Toast.LENGTH_SHORT).show()
            }
            runCatching { block(app) }.onFailure {
                Toast.makeText(app, "Couldn't share that. Try again.", Toast.LENGTH_SHORT).show()
            }
            hint.cancel()
            busy.value = false
        }
    }
}
