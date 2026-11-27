package com.music.aalap.ui

import com.music.aalap.data.entity.PodcastModel
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.local.Podcasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastsTest {
    private fun show(id: String) = PodcastModel(id, "Show $id", "Pub", "")
    private fun ep(id: Int, show: String) = SongsModel(id, "Ep $id", show, show, "", "episode:$id")

    @Test fun popularTakesEachLanguageInTurnOnce() {
        val hindi = listOf(show("a"), show("b"), show("c"))
        val english = listOf(show("x"), show("a"), show("y"))
        assertEquals(listOf("a", "x", "b", "c", "y"), Podcasts.interleave(listOf(hindi, english)).map { it.id })
    }

    @Test fun finishedNearTheEnd() {
        assertTrue(Podcasts.Progress(ep(1, "S"), 59 * 60_000L + 30_000, 60 * 60_000L, 0).finished)
        assertFalse(Podcasts.Progress(ep(1, "S"), 30 * 60_000L, 60 * 60_000L, 0).finished)
        assertEquals(0.5f, Podcasts.Progress(ep(1, "S"), 30 * 60_000L, 60 * 60_000L, 0).fraction, 0.001f)
    }

    @Test fun topShowsByMinutesListened() {
        val list = listOf(
            Podcasts.Progress(ep(1, "Ranveer Show"), 40 * 60_000L, 60 * 60_000L, 3, "rs"),
            Podcasts.Progress(ep(2, "Ranveer Show"), 20 * 60_000L, 60 * 60_000L, 5),
            Podcasts.Progress(ep(3, "Figuring Out"), 50 * 60_000L, 60 * 60_000L, 4),
        )
        val top = Podcasts.topShows(list, 5)
        assertEquals(listOf("Ranveer Show", "Figuring Out"), top.map { it.name })
        assertEquals(60L, top[0].minutes)
        assertEquals(2, top[0].episodes)
        assertEquals("rs", top[0].showId)
        assertEquals(2, top[0].latest.episode.id) // most recent of that show
    }
}

class PodcastMatchTest {
    @Test fun episodeFindsItsFullUpload() {
        val ep = "15,000 Crore Ka Humble Malik - Diamond King Of India's Story On TRS | Savji Dholakia"
        val vids = listOf(
            "a" to "EMI WALA SWAG | Gen Z Ki Sachchi Kahani | Hindi Rap Story",
            "b" to "15,000 Crore Ka Humble Malik - Diamond King Of India's Story | Savji Dholakia | TRS हिंदी",
            "c" to "Diamond King Savji Dholakia shorts",
        )
        assertEquals("b", com.music.aalap.playback.PodcastMatch.best(ep, vids))
        assertEquals(null, com.music.aalap.playback.PodcastMatch.best(ep, vids.take(1)))
    }
}

class PodcastFeedTest {
    @Test fun readsEpisodesFromRss() {
        val xml = """<rss><channel><title>Show</title>
            <item><title><![CDATA[15,000 Crore Ka Humble Malik - Diamond King | Savji]]></title><enclosure url="https://a.fm/play/1.mp3?x=1&amp;y=2" type="audio/mpeg"/></item>
            <item><title>Ekta Kapoor On TRS &amp; More</title><enclosure length="1" url="https://a.fm/2.mp3"/></item>
            <item><title>No audio</title></item></channel></rss>"""
        val items = com.music.aalap.playback.PodcastFeed.parse(xml)
        assertEquals(2, items.size)
        assertEquals("15,000 Crore Ka Humble Malik - Diamond King | Savji", items[0].title)
        assertEquals("https://a.fm/play/1.mp3?x=1&y=2", items[0].audio)
        assertEquals("Ekta Kapoor On TRS & More", items[1].title)
    }

    @Test fun picksTheRightShowFromTheDirectory() {
        val results = listOf(
            Triple("The Ranveer Show", "BeerBiceps aka Ranveer Allahbadia", "https://en.rss"),
            Triple("Dismay over Deepika", "Bollywood Hungama", "https://other.rss"),
            Triple("The Ranveer Show हिंदी", "BeerBiceps", "https://hi.rss"),
        )
        assertEquals("https://hi.rss", com.music.aalap.playback.PodcastFeed.bestShow("The Ranveer Show हिंदी", "BeerBiceps", results))
        assertEquals("https://en.rss", com.music.aalap.playback.PodcastFeed.bestShow("The Ranveer Show", "BeerBiceps", results))
        assertEquals(null, com.music.aalap.playback.PodcastFeed.bestShow("Some Spotify Original", "", results))
    }
}

class PodcastNextTest {
    private fun e(i: Int) = SongsModel(i, "Ep $i", "S", "S", "", "episode:$i")
    @Test fun nextEpisodesFollowTheShowSkippingFinished() {
        val list = (1..6).map(::e) // newest first
        assertEquals(listOf(4, 6), Podcasts.pickNext(list, "episode:3", exclude = emptySet(), finished = setOf("episode:5"), n = 2).map { it.id })
        // Reached the oldest: back to the newest ones you haven't finished.
        assertEquals(listOf(1, 2), Podcasts.pickNext(list, "episode:6", emptySet(), emptySet(), 2).map { it.id })
        assertEquals(listOf(1), Podcasts.pickNext(list, "episode:99", setOf(2, 3, 4, 5, 6), emptySet(), 3).map { it.id })
    }
}
