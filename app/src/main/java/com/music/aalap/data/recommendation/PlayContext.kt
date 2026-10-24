package com.music.aalap.data.recommendation

import com.music.aalap.data.entity.SongsModel
import java.util.Collections

/**
 * How the current music started, for the recommendations that follow it:
 *
 *  - [anchor]: the song you chose. Recommendations keep to its feel (an energetic
 *    pick keeps the queue energetic) until you choose something else.
 *  - [pool]: the playlist you tapped it in (Liked Songs, a playlist…). Its songs are
 *    mixed into the recommendations now and then, at random, when they fit, instead
 *    of the whole list playing in order. (The playlist's Play button still plays it all.)
 *
 * Also remembers which queued songs the engine added ([isAuto]), so a skip can make
 * it rethink the ones still to come.
 */
object PlayContext {
    data class Snapshot(
        val anchor: SongsModel? = null,
        val at: Long = 0,
        val pool: List<SongsModel> = emptyList(),
        val poolName: String = "",
    )

    @Volatile var current: Snapshot = Snapshot()
        private set

    private val auto: MutableSet<Int> = Collections.synchronizedSet(LinkedHashSet())

    /** You chose a single song; what follows is its radio. */
    fun chose(song: SongsModel) {
        current = Snapshot(song, System.currentTimeMillis())
    }

    /** You tapped [song] in [list]; what follows blends recommendations with the list. */
    fun choseFromList(song: SongsModel, list: List<SongsModel>, name: String) {
        current = Snapshot(song, System.currentTimeMillis(), list.filter { it.id != song.id }.distinctBy { it.id }, name)
    }

    /** You started a whole list in order (its Play / Shuffle button). */
    fun playingList(list: List<SongsModel>, name: String) {
        current = Snapshot(null, System.currentTimeMillis(), emptyList(), name)
    }

    fun markAuto(appId: Int) {
        auto += appId
        if (auto.size > 400) synchronized(auto) { auto.iterator().let { repeat(100) { _ -> if (it.hasNext()) { it.next(); it.remove() } } } }
    }

    fun isAuto(appId: Int): Boolean = appId in auto
}
