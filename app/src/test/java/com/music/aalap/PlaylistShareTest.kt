package com.music.aalap

import com.music.aalap.data.entity.SongsModel
import com.music.aalap.ui.share.PlaylistShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistShareTest {
    @Test fun roundTrip() {
        val songs = listOf(SongsModel(1, "Tum Hi Ho", "Aashiqui 2", "Arijit Singh", "c", "spotify:track:x", "x", durationMs = 262000))
        val msg = PlaylistShare.message("Road trip", songs)
        val (name, back) = PlaylistShare.decode("Look at this!\n$msg")!!
        assertEquals("Road trip", name)
        assertEquals(songs, back)
    }

    @Test fun junkIsIgnored() {
        assertNull(PlaylistShare.decode("AALAP-PLAYLIST:notacode"))
        assertNull(PlaylistShare.decode("hello"))
    }
}
