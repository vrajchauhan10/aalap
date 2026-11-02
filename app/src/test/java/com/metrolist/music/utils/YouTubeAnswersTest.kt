package com.metrolist.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bot check must not be mistaken for an age limit (that switched the stream decoder off). */
class YouTubeAnswersTest {
    @Test fun botChecksSeenOnPhones() {
        assertTrue(YouTubeAnswers.isBotCheck("LOGIN_REQUIRED", "Sign in to confirm you’re not a bot"))
        assertTrue(YouTubeAnswers.isBotCheck("LOGIN_REQUIRED", "Please sign in"))
    }

    @Test fun realAgeLimitsStayAgeLimits() {
        assertFalse(YouTubeAnswers.isBotCheck("LOGIN_REQUIRED", "Sign in to confirm your age"))
        assertFalse(YouTubeAnswers.isBotCheck("LOGIN_REQUIRED", "This video may be inappropriate for some users."))
        assertFalse(YouTubeAnswers.isBotCheck("AGE_VERIFICATION_REQUIRED", "Sign in to confirm your age"))
        assertFalse(YouTubeAnswers.isBotCheck("LOGIN_REQUIRED", null)) // no reason: keep the careful path
    }

    @Test fun otherStatusesAreNotBotChecks() {
        assertFalse(YouTubeAnswers.isBotCheck("OK", null))
        assertFalse(YouTubeAnswers.isBotCheck("UNPLAYABLE", "The page needs to be reloaded."))
    }
}

/** A client that refused this phone is rested for a while, then tried again. */
class ClientMemoryTest {
    @Test fun refusedClientRestsThenComesBack() {
        ClientMemory.clear()
        val t = 1_000_000L
        ClientMemory.refused("ANDROID_VR/1.65.10", t)
        assertTrue(ClientMemory.resting("ANDROID_VR/1.65.10", t + 60_000))
        assertFalse(ClientMemory.resting("ANDROID_VR/1.65.10", t + ClientMemory.REST_MS + 1))
        assertFalse(ClientMemory.resting("IOS/21.26.4", t + 60_000))
    }

    @Test fun workingAgainClearsIt() {
        ClientMemory.clear()
        ClientMemory.refused("IOS/21.26.4", 5L)
        ClientMemory.worked("IOS/21.26.4")
        assertFalse(ClientMemory.resting("IOS/21.26.4", 6L))
    }

    @Test fun whatCountsAsARefusal() {
        assertTrue(ClientMemory.isRefusal("LOGIN_REQUIRED"))
        assertFalse(ClientMemory.isRefusal("UNPLAYABLE")) // can be just that video
        assertTrue(ClientMemory.isRefusal("OK", httpCode = 403))
        assertFalse(ClientMemory.isRefusal("OK"))
        assertFalse(ClientMemory.isRefusal("ERROR")) // a broken video, not the client
    }
}
