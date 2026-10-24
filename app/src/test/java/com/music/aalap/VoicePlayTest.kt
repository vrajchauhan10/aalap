package com.music.aalap

import com.music.aalap.data.api.Mood
import com.music.aalap.di.VoicePlay
import com.music.aalap.di.VoicePlay.Plan
import org.junit.Assert.assertEquals
import org.junit.Test

/** How spoken requests ("play … on Aalap") are understood. */
class VoicePlayTest {

    private fun plan(q: String, focus: String = "", artist: String = "", title: String = "", playlist: String = "") =
        VoicePlay.plan(VoicePlay.Request(q, focus = focus, artist = artist, title = title, playlist = playlist))

    @Test fun songs() {
        assertEquals(Plan.Song("kesariya"), plan("Kesariya"))
        assertEquals(Plan.Song("kesariya"), plan("play Kesariya on Aalap"))
        assertEquals(Plan.Song("kesariya by arijit singh"), plan("Play Kesariya by Arijit Singh on the Aalap app"))
        assertEquals(Plan.Song("tum hi ho"), plan("can you play tum hi ho in aalap"))
        // A song whose name has a mood word is still that song.
        assertEquals(Plan.Song("love me like you do"), plan("Love Me Like You Do"))
        assertEquals(Plan.Song("sad song by we the kings"), plan("play Sad Song by We The Kings"))
    }

    @Test fun nothingInParticular() {
        for (q in listOf("", "music", "play music on Aalap", "play some songs", "play something", "play aalap")) assertEquals(q, Plan.Resume, plan(q))
    }

    @Test fun likedTodayMix() {
        assertEquals(Plan.Liked, plan("play my liked songs"))
        assertEquals(Plan.Liked, plan("my favourite songs on aalap"))
        assertEquals(Plan.Liked, plan("my likes"))
        assertEquals(Plan.Liked, plan("x", focus = "vnd.android.cursor.item/playlist", playlist = "Liked Songs"))
        assertEquals(Plan.Today, plan("play aaj ka aalap"))
        assertEquals(Plan.Today, plan("today's playlist"))
        assertEquals(Plan.YourMix, plan("play my mix"))
    }

    @Test fun moods() {
        assertEquals(Plan.Mood(Mood.PARTY), plan("play party songs"))
        assertEquals(Plan.Mood(Mood.ENERGETIC), plan("play some workout music on aalap"))
        assertEquals(Plan.Mood(Mood.SAD), plan("sad songs"))
        assertEquals(Plan.Mood(Mood.DEVOTIONAL), plan("play bhajans"))
        assertEquals(Plan.Mood(Mood.CHILL), plan("play lofi"))
        assertEquals(Plan.Mood(Mood.ROMANTIC), plan("play hindi romantic songs"))
        assertEquals(Plan.Mood(Mood.FOCUS), plan("study music"))
        assertEquals(Plan.Mood(Mood.ROAD_TRIP), plan("road trip songs"))
    }

    private fun c(title: String, artists: String) = com.music.aalap.data.recommendation.engine.Candidate(title = title, artists = artists)

    @Test fun picksTheSongMeant() {
        val results = listOf(c("Kesariyo Rang", "Lijo George-Dj Chetas, Asees Kaur"), c("Kesariya (From \"Brahmastra\")", "Pritam, Arijit Singh"), c("Kesariya Balam", "Mame Khan"))
        assertEquals("Kesariya (From \"Brahmastra\")", VoicePlay.bestMatch("kesariya", results)?.title)
        assertEquals("Kesariya Balam", VoicePlay.bestMatch("kesariya balam", results)?.title)
        assertEquals("Kesariya (From \"Brahmastra\")", VoicePlay.bestMatch("kesariya by arijit singh", results)?.title)
        // Your own version wins a tie.
        val two = listOf(c("Tum Hi Ho", "Some Cover Band"), c("Tum Hi Ho", "Arijit Singh"))
        assertEquals("Arijit Singh", VoicePlay.bestMatch("tum hi ho", two, known = setOf("tum hi ho|arijit singh"))?.artists)
        // Spelled a little differently still finds it; nonsense finds nothing.
        assertEquals("Kesariya (From \"Brahmastra\")", VoicePlay.bestMatch("kesriya arijit", results)?.title)
        assertEquals(null, VoicePlay.bestMatch("xqzv blorpt nonsense", listOf(c("X-COOL! - Instrumental - Slowed", "tienanh109, HDN"))))
    }

    @Test fun artists() {
        assertEquals(Plan.Artist("arijit singh"), plan("play songs by Arijit Singh"))
        assertEquals(Plan.Artist("diljit dosanjh"), plan("play Diljit Dosanjh songs"))
        assertEquals(Plan.Artist("arijit singh"), plan("arijit singh ke gaane"))
        assertEquals(Plan.Artist("Shreya Ghoshal"), plan("Shreya Ghoshal", focus = "vnd.android.cursor.item/artist", artist = "Shreya Ghoshal"))
        assertEquals(Plan.Song("Kesariya Arijit Singh"), plan("kesariya", focus = "vnd.android.cursor.item/audio", title = "Kesariya", artist = "Arijit Singh"))
    }
}
