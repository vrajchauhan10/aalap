package com.music.aalap.ui.components

/** "1 song", "2 songs": a count with the right word for it. */
fun counted(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
