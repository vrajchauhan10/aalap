package com.music.aalap

import com.music.aalap.data.api.SearchRank
import com.music.aalap.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankTest {
    private fun s(t: String, a: String = "x") = SongsModel(t.hashCode(), t, "", a, "", "")

    @Test fun originalBeforeVersions() {
        val list = listOf(s("Tum Hi Ho - Lofi"), s("Tum Hi Ho Bandhu"), s("Tum Hi Ho (Cover)"), s("Tum Hi Ho", "Arijit Singh"))
        assertEquals("Tum Hi Ho", SearchRank.rank("tum hi ho", list).first().title)
    }

    @Test fun versionWhenAskedFor() {
        val list = listOf(s("Tum Hi Ho"), s("Tum Hi Ho - Unplugged"))
        assertEquals("Tum Hi Ho - Unplugged", SearchRank.rank("tum hi ho unplugged", list).first().title)
    }

    @Test fun keepsOrderOtherwise() {
        val list = listOf(s("Kesariya"), s("Kesariya (From \"Brahmastra\")"))
        assertEquals(list, SearchRank.rank("kesariya", list))
    }
}
