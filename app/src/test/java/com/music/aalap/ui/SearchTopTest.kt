package com.music.aalap.ui

import com.music.aalap.data.api.SearchTop
import com.music.aalap.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTopTest {
    private fun s(id: Int, t: String, a: String) = SongsModel(id, t, "", a, "", "q:$id")

    @Test fun originalBeatsRemixesAndCoversOnTop() {
        val spotify = listOf(s(1, "Tum Hi Ho - Lofi", "Arijit Singh"), s(2, "Tum Hi Ho (Unplugged)", "Shirley Setia"), s(3, "Tum Hi Ho", "Arijit Singh"))
        val r = SearchTop.merge("tum hi ho", spotify, emptyList())
        assertEquals(3, r.top?.id)
        assertFalse(r.byLyrics)
        // Versions stay in the list, below the original.
        assertEquals(listOf(3, 1, 2).toSet(), r.songs.map { it.id }.toSet())
        assertEquals(3, r.songs.first().id)
    }

    @Test fun askedForVersionWins() {
        val spotify = listOf(s(3, "Tum Hi Ho", "Arijit Singh"), s(2, "Tum Hi Ho (Unplugged)", "Shirley Setia"))
        assertEquals(2, SearchTop.merge("tum hi ho unplugged", spotify, emptyList()).top?.id)
    }

    @Test fun originalOnlyOnYouTubeStillComesFirst() {
        val spotify = listOf(s(1, "Kesariya - Remix", "DJ X"))
        val yt = listOf(s(10, "Kesariya (From Brahmastra)", "Arijit Singh"))
        val r = SearchTop.merge("kesariya", spotify, yt)
        assertEquals(10, r.top?.id)
    }

    @Test fun sameSongFromBothShowsOnce() {
        val spotify = listOf(s(1, "Kesariya (From Brahmastra)", "Arijit Singh, Pritam"))
        val yt = listOf(s(10, "Kesariya", "Arijit Singh"))
        val r = SearchTop.merge("kesariya", spotify, yt)
        assertEquals(1, r.songs.size)
        assertEquals(1, r.top?.id) // Spotify's copy (it has the most details)
    }

    @Test fun lyricsFindTheSong() {
        val spotify = listOf(s(1, "Random Song", "Someone"))
        val yt = listOf(s(10, "Kal Ho Naa Ho", "Sonu Nigam"), s(11, "Other", "X"))
        val r = SearchTop.merge("har ghadi badal rahi hai roop zindagi", spotify, yt)
        assertTrue(r.byLyrics)
        assertEquals(10, r.top?.id)
        // Two words aren't a lyric line: YouTube Music's best match on top, not marked as lyrics.
        assertFalse(SearchTop.merge("some words", spotify, yt).byLyrics)
    }

    @Test fun famousLineFindsTheRealSongNotACoverNamedAfterIt() {
        val spotify = listOf(s(1, "Monika O My Darling", "Akm Present"), s(2, "Piya Tu Ab To Aaja", "Asha Bhosle"))
        val yt = listOf(s(20, "Piya Tu Ab To Aaja", "Asha Bhosle"), s(21, "Monika O My Darling", "Akm Present"))
        val r = SearchTop.merge("monika o my darling", spotify, yt)
        assertEquals(2, r.top?.id)
    }

    @Test fun coverChannelsRankBelowSingers() {
        val r = SearchTop.merge("kesariya", listOf(s(1, "Kesariya", "Mono Guruji Studio"), s(2, "Kesariya", "Arijit Singh")), emptyList())
        assertEquals(2, r.top?.id)
    }

    @Test fun aLanguageYouTurnedOffIsNeverTheTopResult() {
        val mix = mapOf("hindi" to 1.0)
        val spotify = listOf(s(1, "Monica (From Coolie) (Tamil)", "Anirudh Ravichander"), s(2, "Yeh Ek Zindagi (From Monica, O My Darling)", "Achint"))
        val yt = listOf(s(20, "Piya Tu Ab To Aaja", "Asha Bhosle"), s(21, "Yeh Ek Zindagi", "Achint"))
        val r = SearchTop.merge("monica o my darling", spotify, yt, mix)
        assertTrue(r.top?.id != 1)
        assertEquals(1, r.songs.last().id) // the Tamil song is still there, at the bottom
        // Typing the language brings it back up.
        assertEquals(1, SearchTop.merge("monica tamil", spotify, emptyList(), mix).top?.id)
    }

    @Test fun songsFromTheFilmYouNamedRankHigh() {
        val spotify = listOf(s(1, "Random Monica Song", "X"), s(2, "Yeh Ek Zindagi (From Monica, O My Darling)", "Achint"))
        val r = SearchTop.merge("monica o my darling", spotify, emptyList())
        assertEquals(2, r.songs.first().id)
    }

    @Test fun theSongOthersSampleIsTheOriginalOnTop() {
        val yt = listOf(
            s(1, "Parda", "Pritam, Sunidhi Chauhan, Rana Mazumder, Irshad Kamil"),
            s(2, "Run Down The City - Monica", "Shashwat Sachdev, Reble, Asha Bhosle, R.D. Burman"),
            s(3, "Piya Tu Ab To Aaja", "Asha Bhosle, R.D. Burman"),
            s(4, "Monica (From Coolie) (Tamil)", "Sublahshini, Vishnu Edavan"),
            s(5, "Yeh Ek Zindagi (From Monica, O My Darling)", "Achint, Mikey McCleary"),
        )
        val r = SearchTop.merge("monica o my darling", emptyList(), yt, mapOf("hindi" to 1.0))
        assertEquals(3, r.top?.id)
        assertTrue(r.songs.indexOfFirst { it.id == 5 } < r.songs.indexOfFirst { it.id == 4 })
    }
}
