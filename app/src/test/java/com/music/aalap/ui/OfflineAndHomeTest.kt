package com.music.aalap.ui

import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.local.OfflineLists
import com.music.aalap.data.preferences.HomeSections
import com.music.aalap.ui.screens.DownloadsView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAndHomeTest {
    private fun s(id: Int, t: String, a: String, al: String = "") = SongsModel(id, t, al, a, "", "q:$id")
    private val songs = listOf(
        s(1, "Tum Hi Ho", "Arijit Singh", "Aashiqui 2"),
        s(2, "Mango Shake", "Bali, Bhuvan Bam"),
        s(3, "Kesariya", "Arijit Singh", "Brahmastra"),
        s(4, "Hum Tum", "Alka Yagnik"),
    )

    @Test fun offlineSearchFindsDownloadsByAnyWord() {
        assertEquals(listOf(1, 3), OfflineLists.search(songs, "arijit").map { it.id })
        assertEquals(listOf(1), OfflineLists.search(songs, "tum arijit").map { it.id })
        assertEquals(listOf(2), OfflineLists.search(songs, "MANGO").map { it.id })
        assertEquals(listOf(3), OfflineLists.search(songs, "brahmastra").map { it.id })
        assertTrue(OfflineLists.search(songs, "   ").isEmpty())
        // Title starting with the words ranks first.
        assertEquals(4, OfflineLists.search(songs, "hum").first().id)
    }

    @Test fun downloadsSortFilterAndSearch() {
        val items = songs.mapIndexed { i, x -> DownloadsView.Item(x, addedAt = (i + 1) * 1000L, bytes = 1L) }
        assertEquals(listOf(4, 3, 2, 1), DownloadsView.apply(items, "", DownloadsView.Sort.RECENT, DownloadsView.Show.ALL, emptySet()).map { it.song.id })
        assertEquals(listOf(4, 3, 2, 1), DownloadsView.apply(items, "", DownloadsView.Sort.TITLE, DownloadsView.Show.ALL, emptySet()).map { it.song.id })
        assertEquals(listOf(4, 3, 1, 2), DownloadsView.apply(items, "", DownloadsView.Sort.ARTIST, DownloadsView.Show.ALL, emptySet()).map { it.song.id })
        assertEquals(listOf(2), DownloadsView.apply(items, "", DownloadsView.Sort.RECENT, DownloadsView.Show.LIKED, setOf(2)).map { it.song.id })
        assertEquals(listOf(1, 3), DownloadsView.apply(items, "arijit", DownloadsView.Sort.TITLE, DownloadsView.Show.ALL, emptySet()).map { it.song.id })
    }

    @Test fun homeOrderKeepsTodayFirstAndAddsNewSections() {
        val def = HomeSections.defaultOrder()
        assertTrue(HomeSections.PINNED !in def)
        assertEquals("recent", def.first())
        // A saved order from an older version: unknown keys dropped, new sections appended.
        val n = HomeSections.normalise(listOf("quick", "recent", "gone", "quick"))
        assertEquals(listOf("quick", "recent"), n.take(2))
        assertEquals(def.toSet(), n.toSet())
        assertEquals(def.size, n.size)
    }
}

class CrashScrubTest {
    @Test fun secretsAndEmailsAreMasked() {
        val out = com.music.aalap.CrashReport.scrub(
            "D/Api: cookie: sp_dc=AQB12345xyz; other\nI/Auth: Authorization: Bearer abc.def.ghi\nuser me@gmail.com token=XYZ123 ok")
        assertTrue(out, "AQB12345xyz" !in out && "abc.def.ghi" !in out && "XYZ123" !in out && "me@gmail.com" !in out)
        assertTrue(out.contains("ok") && out.contains("D/Api"))
    }
}
