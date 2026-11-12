package com.music.aalap.ui.navigation

import androidx.navigation.NavController

/**
 * Pages opened from the player (artist, album, sound) open as a layer on top of the player,
 * which stays open underneath: back slides the page off and the player is simply there, like
 * a stack of cards. (They used to replace the player, which then had to rise again.)
 */
object PlayerReturn {
    const val ROUTE = "overplayer"

    fun open(nav: NavController, route: String) = nav.navigate("$ROUTE/${android.net.Uri.encode(route)}")
}
