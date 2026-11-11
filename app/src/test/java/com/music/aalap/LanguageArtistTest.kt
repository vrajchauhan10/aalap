package com.music.aalap

import com.music.aalap.data.recommendation.engine.LanguageGuess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageArtistTest {
    private val south = setOf("tamil", "telugu", "kannada", "malayalam")

    /** The language a song gets from what Aalap knows offline: label, script, singers, words. */
    private fun lang(title: String, artists: String, album: String = "") =
        LanguageGuess.labelled(title, album)
            .ifBlank { LanguageGuess.script("$title $artists") }
            .ifBlank { LanguageGuess.ofArtist(artists) }
            .ifBlank { LanguageGuess.of(title, album) }

    @Test fun punjabiSingers() {
        assertEquals("punjabi", LanguageGuess.ofArtist("Diljit Dosanjh, Sia"))
        assertEquals("punjabi", LanguageGuess.ofArtist("Karan Aujla & Ikky"))
        assertEquals("", LanguageGuess.ofArtist("Arijit Singh"))
        assertEquals("", LanguageGuess.ofArtist("B Praak"))   // sings in both: the title decides
    }

    /** What played after "Dayre" on the S24 (11 Nov): every one of them is South Indian. */
    @Test fun southSongsThatFollowedDayre() {
        listOf(
            "Inkem Inkem Inkem Kaavaale" to "Gopi Sundar, Sid Sriram",
            "Sweety" to "Siddharth Mahadevan, Rabbit Mac",
            "Endhuko Emo" to "Harris Jayaraj, Aalaap Raju",
            "Choosa Choosa" to "Padmalatha, Sniggy",
            "Ninnila" to "Thaman S, Armaan Malik",
            "Gulabi Kallu Rendu Mullu" to "Yuvan Shankar Raja, Javed Ali",
            "Kanulanu Thaake" to "Anup Rubens, Arijit Singh",
            "Neeve" to "G. V. Prakash, Anantha Sriram",
            "Ala Bolelo" to "Anirudh Ravichander, Arunraja Kamaraj",
        ).forEach { (t, a) -> assertTrue("$t should be South Indian, was '${lang(t, a)}'", lang(t, a) in south) }
    }

    /** Hindi songs, including ones with South composers or singers who also do Hindi. */
    @Test fun hindiSongsStayHindi() {
        listOf(
            "Dayre" to "Pritam, Arijit Singh", "Teri Ore" to "Pritam, Rahat Fateh Ali Khan",
            "Mann Mera" to "Gajendra Verma", "Aankhon Mein Doob Jaane Ko" to "THE 9TEEN, Sandesh Shandilya",
            "Saibo" to "Sachin-Jigar, Shreya Ghoshal", "Tujhe Sochta Hoon" to "Pritam, KK",
            "Jogi Re" to "Vijay Prakash, Tapas Relia", "Tere Liye" to "Atif Aslam, Shreya Ghoshal",
            "Chaleya" to "Anirudh Ravichander, Arijit Singh, Shilpa Rao", "Jai Ho" to "A.R. Rahman, Sukhwinder Singh",
            "Tum Hi Ho" to "Arijit Singh, Mithoon", "Kesariya" to "Pritam, Arijit Singh", "Gerua" to "Pritam, Arijit Singh",
            "Maine Khud Ko" to "Mustafa Zahid", "Lutt Le Gaya" to "Shashwat Sachdev", "Ishq Sufiyana" to "Vishal-Shekhar",
            "Agar Tum Saath Ho" to "Alka Yagnik, Arijit Singh", "Channa Mereya" to "Pritam, Arijit Singh",
            "Ilahi" to "Pritam, Arijit Singh", "Bekhayali" to "Sachet Tandon", "Raabta" to "Pritam, Arijit Singh",
            "Apna Bana Le" to "Sachin-Jigar, Arijit Singh", "Saadi Galli Aaja" to "Ayushmann Khurrana, Neeti Mohan",
            "Kabhi Jo Baadal Barse" to "Arijit Singh", "Srivalli (Hindi)" to "Devi Sri Prasad, Javed Ali",
        ).forEach { (t, a) -> assertFalse("$t is Hindi, was '${lang(t, a)}'", lang(t, a) in south) }
    }

    @Test fun labelsAndScripts() {
        assertEquals("hindi", LanguageGuess.labelled("Srivalli (Hindi)"))
        assertEquals("telugu", LanguageGuess.labelled("Naatu Naatu (Telugu)"))
        assertEquals("tamil", LanguageGuess.labelled("Kaavaalaa - Tamil Version"))
        assertEquals("", LanguageGuess.labelled("Tamil Hits Mashup"))
        assertEquals("tamil", LanguageGuess.script("காதல்"))
        assertEquals("kannada", LanguageGuess.script("ಪ್ರೀತಿ"))
        assertEquals("malayalam", LanguageGuess.script("പ്രണയം"))
        assertTrue(LanguageGuess.same("urdu", "hindi"))
        assertFalse(LanguageGuess.same("telugu", "hindi"))
    }
}
