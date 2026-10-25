package com.music.aalap

import com.music.aalap.data.api.Mood
import com.music.aalap.di.VoicePlay
import com.music.aalap.di.VoicePlay.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun aajKaAalapHoweverHeard() {
        for (q in listOf("aaj ka aalap", "aaj ka alap", "aj ka aalaap", "aaj ke aalap", "Aaj Ka Aalaap", "aaj kaa aalap on aalap", "today's aalap", "aaj kal aalap"))
            assertEquals(q, Plan.Today, plan(q))
        for (q in listOf("aaj ki raat", "aaj kal", "aaj se teri", "aap ka aana"))
            assertTrue(q, plan(q) is Plan.Song)
        // The recogniser's other hearing names it: that wins.
        assertEquals(Plan.Today, VoicePlay.planAll(VoicePlay.Request("aaj ki raat", alternatives = listOf("aaj ka aalap"))))
    }

    @Test fun filmNamePicksTheRightSong() {
        val results = listOf(
            com.music.aalap.data.recommendation.engine.Candidate(title = "Saiyaara", artists = "Tanishk Bagchi, Faheem Abdullah, Arslan Nizami", album = "Saiyaara"),
            com.music.aalap.data.recommendation.engine.Candidate(title = "Saiyaara - Lofi", artists = "Someone", album = "Lofi Hits"),
            com.music.aalap.data.recommendation.engine.Candidate(title = "Saiyaara", artists = "Mohit Chauhan, Tarannum Malik", album = "Ek Tha Tiger"),
        )
        assertEquals("Ek Tha Tiger", VoicePlay.bestMatch("sayaara ek tha tiger", results)?.album)
        assertEquals("Ek Tha Tiger", VoicePlay.bestMatch("saiyaara ek the tiger", results)?.album)
        // Just the title: the most played (search's first) one.
        assertEquals("Saiyaara", VoicePlay.bestMatch("saiyaara", results)?.album)
        assertEquals("Lofi Hits", VoicePlay.bestMatch("saiyaara lofi", results)?.album)
    }

    @Test fun languageRequests() {
        assertEquals(Plan.Language("hindi"), plan("play hindi song"))
        assertEquals(Plan.Language("hindi"), plan("hindi songs"))
        assertEquals(Plan.Language("punjabi"), plan("play some punjabi music"))
        assertEquals(Plan.Language("english"), plan("english songs"))
        assertTrue(plan("tere liye") is Plan.Song)
    }
}
