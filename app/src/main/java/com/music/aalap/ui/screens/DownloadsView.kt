package com.music.aalap.ui.screens

import com.music.aalap.data.entity.SongsModel

/** How the Downloads page orders and narrows its songs. Pure, so it is unit-tested. */
object DownloadsView {
    enum class Sort(val label: String) { RECENT("Recently added"), TITLE("Title A–Z"), ARTIST("Artist") }
    enum class Show(val label: String) { ALL("All"), LIKED("Liked") }

    data class Item(val song: SongsModel, val addedAt: Long, val bytes: Long)

    fun apply(items: List<Item>, query: String, sort: Sort, show: Show, liked: Set<Int>): List<Item> {
        val q = query.trim()
        val shown = items
            .filter { show == Show.ALL || it.song.id in liked }
            .let { list -> if (q.isEmpty()) list else com.music.aalap.data.local.OfflineLists.search(list.map { it.song }, q).let { found -> val byId = list.associateBy { it.song.id }; found.mapNotNull { byId[it.id] } } }
        // A search keeps its best-match order; otherwise the chosen sort.
        if (q.isNotEmpty()) return shown
        return when (sort) {
            Sort.RECENT -> shown.sortedByDescending { it.addedAt }
            Sort.TITLE -> shown.sortedBy { it.song.title.lowercase() }
            Sort.ARTIST -> shown.sortedWith(compareBy({ it.song.singer.substringBefore(",").trim().lowercase() }, { it.song.title.lowercase() }))
        }
    }
}
