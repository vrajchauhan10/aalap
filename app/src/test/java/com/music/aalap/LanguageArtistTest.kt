package com.music.aalap

import com.music.aalap.data.recommendation.engine.LanguageGuess
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageArtistTest {
    @Test fun punjabiSingers() {
        assertEquals("punjabi", LanguageGuess.ofArtist("Diljit Dosanjh, Sia"))
        assertEquals("punjabi", LanguageGuess.ofArtist("Karan Aujla & Ikky"))
        assertEquals("", LanguageGuess.ofArtist("Arijit Singh"))
        assertEquals("", LanguageGuess.ofArtist("B Praak"))   // sings in both: the title decides
    }
}
