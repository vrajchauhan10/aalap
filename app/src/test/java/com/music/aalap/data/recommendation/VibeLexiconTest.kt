package com.music.aalap.data.recommendation

import com.music.aalap.data.recommendation.engine.Feel
import com.music.aalap.data.recommendation.engine.Vibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What titles say about a song's feel, across languages, and words that must not mislead. */
class VibeLexiconTest {

    private fun main(title: String, album: String = ""): Feel? = Vibe.lexicon(title, album).maxByOrNull { it.value }?.key

    @Test
    fun filmSongTitlesAreNotDevotional() {
        // "From" ends in "om": it used to make every film song look devotional.
        for (t in listOf("Gehra Hua (From \"Dhurandhar\")", "Sau Tarah Ke (From \"Dishoom\")", "Humraah (From \"Malang\")", "Room", "Bloom", "Freedom"))
            assertFalse(t, Vibe.lexicon(t).containsKey(Feel.DEVOTIONAL))
    }

    @Test
    fun devotional() {
        for (t in listOf("Shree Hanuman Chalisa", "Om Namah Shivaya", "Ganpati Aarti", "Jai Shri Ram", "Waheguru Simran", "Kun Faaya Kun", "Achyutam Keshavam Krishna"))
            assertEquals(t, Feel.DEVOTIONAL, main(t))
    }

    @Test
    fun partyAndEnergetic() {
        assertEquals(Feel.PARTY, main("Kala Chashma (Remix)"))
        assertEquals(Feel.PARTY, main("Daaru Party"))
        assertEquals(Feel.PARTY, main("Chogada", "Garba Night"))
        assertEquals(Feel.ENERGETIC, main("Gym Workout Mix"))
        assertEquals(Feel.ENERGETIC, main("Drill Anthem"))
    }

    @Test
    fun sadRomanticChillFocus() {
        assertEquals(Feel.SAD, main("Dard Bhare Gaane"))
        assertEquals(Feel.SAD, main("Bewafa Tera Masoom Chehra"))
        assertEquals(Feel.ROMANTIC, main("Pehla Pyaar"))
        assertEquals(Feel.ROMANTIC, main("Ishq Wala Love"))
        assertEquals(Feel.CHILL, main("Kesariya (Lofi Slowed + Reverb)"))
        assertEquals(Feel.FOCUS, main("Interstellar Main Theme (Piano Instrumental)"))
    }

    @Test
    fun wholeWordsOnly() {
        // "Ram" inside "Rammstein" / "dj" inside "Djo" don't count.
        assertFalse(Vibe.lexicon("Rammstein Sonne").containsKey(Feel.DEVOTIONAL))
        assertFalse(Vibe.lexicon("End of Beginning", "Djo").containsKey(Feel.PARTY))
        assertTrue(Vibe.styles("Kesariya Lofi").contains("lofi"))
        assertTrue(Vibe.styles("Mere Gully Mein", artists = "DIVINE, Naezy").isEmpty())
    }
}
