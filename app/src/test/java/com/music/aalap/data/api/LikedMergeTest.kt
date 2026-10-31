package com.music.aalap.data.api

import com.music.aalap.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Liked Songs across providers: a song liked on Spotify and on YouTube Music shows once (as
 * its Spotify listing); different songs and different takes stay separate; nothing breaks on
 * empty libraries or missing artwork.
 */
class LikedMergeTest {
    private fun s(id: Int, title: String, singer: String, sec: Int = 0, cover: String = "c$id", album: String = "") =
        SongsModel(id, title, album, singer, cover, "$title $singer", durationMs = sec * 1000)

    @Test fun sameSongOnBothServicesShowsOnceAsTheSpotifyCopy() {
        val spotify = listOf(s(1, "Kesariya", "Pritam, Arijit Singh", 268))
        val youtube = listOf(s(101, "Kesariya (From \"Brahmastra\")", "Arijit Singh", 269))
        val merged = YouTubeLibrary.mergeLiked(spotify, youtube)
        assertEquals(listOf(1), merged.map { it.id })
    }

    @Test fun youtubeOnlySongsAreKept() {
        val spotify = listOf(s(1, "Kesariya", "Arijit Singh", 268))
        val youtube = listOf(s(102, "Paro", "Aditya Rikhari", 160))
        val merged = YouTubeLibrary.mergeLiked(spotify, youtube)
        assertEquals(setOf(1, 102), merged.map { it.id }.toSet())
    }

    @Test fun anotherTakeIsNotTheSameSong() {
        val spotify = listOf(s(1, "Paro", "Aditya Rikhari", 160))
        val youtube = listOf(s(103, "Paro (Unplugged)", "Aditya Rikhari", 175))
        val merged = YouTubeLibrary.mergeLiked(spotify, youtube)
        assertEquals(setOf(1, 103), merged.map { it.id }.toSet())
    }

    @Test fun sameTitleByAnotherArtistIsKept() {
        val spotify = listOf(s(1, "Tum Hi Ho", "Arijit Singh", 262))
        val youtube = listOf(s(104, "Tum Hi Ho", "Some Cover Band", 240))
        assertEquals(2, YouTubeLibrary.mergeLiked(spotify, youtube).size)
    }

    @Test fun duplicatesWithinOneServiceShowOnce() {
        val youtube = listOf(s(105, "Paro", "Aditya Rikhari"), s(105, "Paro", "Aditya Rikhari"))
        assertEquals(listOf(105), YouTubeLibrary.mergeLiked(emptyList(), youtube).map { it.id })
    }

    @Test fun emptyLibraries() {
        assertTrue(YouTubeLibrary.mergeLiked(emptyList(), emptyList()).isEmpty())
        val one = listOf(s(1, "Kesariya", "Arijit Singh"))
        assertEquals(one, YouTubeLibrary.mergeLiked(one, emptyList()))
        assertEquals(one, YouTubeLibrary.mergeLiked(emptyList(), one))
    }

    @Test fun missingArtworkAndLengthDoNotMatter() {
        val spotify = listOf(s(1, "Kesariya", "Arijit Singh", cover = ""))
        val youtube = listOf(s(106, "Kesariya", "Arijit Singh", cover = ""))
        assertEquals(listOf(1), YouTubeLibrary.mergeLiked(spotify, youtube).map { it.id })
    }

    @Test fun bothListsKeepTheirOwnOrderInterleaved() {
        val spotify = listOf(s(1, "A song", "X"), s(2, "B song", "Y"))
        val youtube = listOf(s(201, "C song", "Z"), s(202, "D song", "W"))
        val merged = YouTubeLibrary.mergeLiked(spotify, youtube).map { it.id }
        assertTrue(merged.indexOf(1) < merged.indexOf(2))
        assertTrue(merged.indexOf(201) < merged.indexOf(202))
        assertEquals(4, merged.size)
    }
}
