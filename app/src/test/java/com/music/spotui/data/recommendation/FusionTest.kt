package com.music.spotui.data.recommendation

import com.music.spotui.data.recommendation.engine.Candidate
import com.music.spotui.data.recommendation.engine.Diversifier
import com.music.spotui.data.recommendation.engine.Fusion
import com.music.spotui.data.recommendation.engine.Scored
import com.music.spotui.data.recommendation.engine.Song
import com.music.spotui.data.recommendation.engine.SongMatcher
import com.music.spotui.data.recommendation.engine.Source
import com.music.spotui.data.recommendation.engine.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The user plays "Daru Badnam". Each source says what people play next; fusion must
 * rank songs several sources agree on first and keep single-source (regional) songs.
 */
class FusionTest {

    private val munni = "key:munni badnam|mamta sharma"
    private val desi = "key:desi kalakar|yo yo honey singh"
    private val ohho = "key:ohho|kamal kahlon"
    private val onlyYouTube = "key:yaar beli|kamal kahlon"
    private val onlySaavn = "key:bandook|haryanvi artist"

    private val lists = mapOf(
        Source.YOUTUBE to listOf(munni, desi, ohho, onlyYouTube),
        Source.SPOTIFY to listOf(desi, munni),
        Source.JIOSAAVN to listOf(ohho, munni, onlySaavn),
    )

    private fun rrf(vararg parts: Pair<Source, Int>) = parts.sumOf { (s, r) -> s.weight / (Fusion.K + r) }

    @Test
    fun scoresFollowTheFormula() {
        val fused = Fusion.fuse(lists).associateBy { it.id }
        val eps = 1e-12
        // In all three sources: RRF + 0.6.
        assertEquals(rrf(Source.YOUTUBE to 1, Source.SPOTIFY to 2, Source.JIOSAAVN to 2) + 0.6, fused.getValue(munni).score, eps)
        // In two sources: RRF + 0.3.
        assertEquals(rrf(Source.YOUTUBE to 2, Source.SPOTIFY to 1) + 0.3, fused.getValue(desi).score, eps)
        assertEquals(rrf(Source.YOUTUBE to 3, Source.JIOSAAVN to 1) + 0.3, fused.getValue(ohho).score, eps)
        // One source: no bonus, but kept.
        assertEquals(rrf(Source.YOUTUBE to 4), fused.getValue(onlyYouTube).score, eps)
        assertEquals(rrf(Source.JIOSAAVN to 3), fused.getValue(onlySaavn).score, eps)
    }

    @Test
    fun agreementRanksFirstAndSingleSourceSongsAreKept() {
        val order = Fusion.fuse(lists).map { it.id }
        assertEquals(listOf(munni, desi, ohho, onlyYouTube, onlySaavn), order)
        assertEquals(setOf(Source.YOUTUBE, Source.SPOTIFY, Source.JIOSAAVN), Fusion.fuse(lists).first().sources)
    }

    @Test
    fun duplicateInOneSourceKeepsItsBestRank() {
        val fused = Fusion.fuse(mapOf(Source.YOUTUBE to listOf(munni, munni, desi))).associateBy { it.id }
        assertEquals(1, fused.getValue(munni).ranks[Source.YOUTUBE])
        assertEquals(2, fused.getValue(desi).ranks[Source.YOUTUBE])
    }

    @Test
    fun matcherTreatsUploadsOfOneSongAsTheSame() {
        val spotify = Candidate("Daru Badnaam", "Kamal Kahlon, Param Singh", 214)
        val youtube = Candidate("Kamal Kahlon - Daru Badnam (Official Video) | Latest Punjabi Song", "Kamal Kahlon", 215)
        val lyrical = Candidate("Daru Badnaam (Lyrical)", "Kamal Kahlon", 0)
        val otherSinger = Candidate("Daru Badnaam", "Kamal Digiya", 190)
        val otherSong = Candidate("Munni Badnaam Hui", "Mamta Sharma", 250)
        assertTrue(SongMatcher.sameSong(spotify, youtube))
        assertTrue(SongMatcher.sameSong(spotify, lyrical))
        assertFalse(SongMatcher.sameSong(spotify, otherSinger))
        assertFalse(SongMatcher.sameSong(spotify, otherSong))
        assertEquals(SongMatcher.canonicalKey(spotify), SongMatcher.canonicalKey(youtube))
        assertNotEquals(SongMatcher.canonicalKey(spotify), SongMatcher.canonicalKey(otherSong))
    }

    @Test
    fun personalisationWeightsAndNewListeners() {
        assertEquals(0.6 * 1.0 + 0.2 * 0.5 + 0.2 * 0.25, TasteProfile.final(1.0, 0.5, 0.25, isNew = false), 1e-12)
        assertEquals(0.8, TasteProfile.final(0.8, 1.0, 1.0, isNew = true), 1e-12)
    }

    @Test
    fun diversifierNeverRepeatsTheArtistAndSkipsExcluded() {
        fun s(id: String, artist: String, score: Double) =
            Scored(Song(id, Candidate(id, artist)), score, 0.0, 0.0, score)
        val pool = listOf(
            s("a", "Kamal Kahlon", 1.0),   // same artist as the seed
            s("b", "Mamta Sharma", 0.9),
            s("c", "Yo Yo Honey Singh", 0.8),
            s("d", "Badshah", 0.7),
        )
        repeat(200) { seed ->
            val pick = Diversifier.pick(pool, previousArtists = "Kamal Kahlon", exclude = setOf("c"), rnd = Random(seed.toLong()))!!
            assertTrue(pick.song.id in setOf("b", "d"))
        }
    }
}
