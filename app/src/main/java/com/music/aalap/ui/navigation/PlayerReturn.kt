package com.music.aalap.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController

/**
 * A page opened from the player (artist, album, sound) goes back to the player, not to the page
 * under it. Pages opened from there in turn go back as usual; only when the first one closes
 * does the player come back. Tapping a bottom tab cancels it (you chose somewhere else).
 */
object PlayerReturn {
    private var armed: Any? = null
    private var instant = false

    /** True once, for the player shown again on back: it appears in place, with no slide. */
    fun takeInstant(): Boolean = instant.also { instant = false }

    fun open(nav: NavController, route: String) {
        nav.navigate(route)
        val entry = nav.currentBackStackEntry ?: return
        val token = Any()
        armed = token
        entry.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
                if (event != Lifecycle.Event.ON_DESTROY) return
                source.lifecycle.removeObserver(this)
                if (armed !== token) return
                armed = null
                // The app closing or turning the screen also ends the page: not a "back".
                val act = nav.context as? android.app.Activity
                if (act != null && (act.isFinishing || act.isChangingConfigurations || act.isDestroyed)) return
                if (nav.currentDestination?.route != Routes.Player.route) runCatching { instant = true; nav.navigate(Routes.Player.route) }
            }
        })
    }

    /** A bottom tab was tapped: don't reopen the player. */
    fun cancel() { armed = null }
}
