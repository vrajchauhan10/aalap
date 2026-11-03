package com.music.aalap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update check: which version is newer, which links may be opened, how GitHub's answer is read. */
class AppUpdateTest {
    @Test fun versionsCompareByNumber() {
        assertTrue(AppUpdate.isNewer("7.10.0", "7.9.0"))
        assertTrue(AppUpdate.isNewer("v8.0.0", "7.99.9"))
        assertTrue(AppUpdate.isNewer("7.9.1", "7.9"))
        assertFalse(AppUpdate.isNewer("7.9.0", "7.9.0"))
        assertFalse(AppUpdate.isNewer("7.8.0", "7.9.0"))
        assertFalse(AppUpdate.isNewer("7.9.0", "7.9.0-debug"))
    }

    @Test fun onlyThisRepositorysReleasesAreTrusted() {
        assertTrue(AppUpdate.isTrusted("https://github.com/vrajchauhan10/aalap/releases/download/v7.9.0/Aalap-7.9.0.apk"))
        assertFalse(AppUpdate.isTrusted("http://github.com/vrajchauhan10/aalap/releases/download/v7.9.0/Aalap-7.9.0.apk"))
        assertFalse(AppUpdate.isTrusted("https://github.com/someone/aalap/releases/download/v7.9.0/Aalap-7.9.0.apk"))
        assertFalse(AppUpdate.isTrusted("https://evil.example/vrajchauhan10/aalap/releases/x.apk"))
        assertFalse(AppUpdate.isTrusted("https://github.com.evil.example/vrajchauhan10/aalap/releases/x.apk"))
        assertFalse(AppUpdate.isTrusted("https://user@github.com/vrajchauhan10/aalap/releases/x.apk"))
        assertFalse(AppUpdate.isTrusted("https://github.com/vrajchauhan10/aalap/releases/../../other/x.apk"))
    }

    private fun release(tag: String, asset: String, url: String, pre: Boolean = false) = """
        {"tag_name":"$tag","prerelease":$pre,"draft":false,"html_url":"https://github.com/vrajchauhan10/aalap/releases/tag/$tag",
         "assets":[{"name":"$asset","browser_download_url":"$url"}]}
    """

    @Test fun readsALatestRelease() {
        val r = AppUpdate.parse(release("v7.9.0", "Aalap-7.9.0.apk",
            "https://github.com/vrajchauhan10/aalap/releases/download/v7.9.0/Aalap-7.9.0.apk"))!!
        assertEquals("7.9.0", r.version)
        assertTrue(r.apkUrl.endsWith("/Aalap-7.9.0.apk"))
    }

    @Test fun ignoresReleasesWeShouldNotOffer() {
        // An APK hosted somewhere else, a differently named file, a pre-release, garbage.
        assertNull(AppUpdate.parse(release("v7.9.0", "Aalap-7.9.0.apk", "https://evil.example/Aalap-7.9.0.apk")))
        assertNull(AppUpdate.parse(release("v7.9.0", "other.apk",
            "https://github.com/vrajchauhan10/aalap/releases/download/v7.9.0/other.apk")))
        assertNull(AppUpdate.parse(release("v7.9.0", "Aalap-7.9.0.apk",
            "https://github.com/vrajchauhan10/aalap/releases/download/v7.9.0/Aalap-7.9.0.apk", pre = true)))
        assertNull(AppUpdate.parse("not json"))
    }

    @Test fun remindsOncePerVersionThenEveryThreeDays() {
        val day = 24 * 3_600_000L
        assertTrue(AppUpdate.shouldRemind("7.10.0", null, 0L, 10 * day))          // first time
        assertFalse(AppUpdate.shouldRemind("7.10.0", "7.10.0", 10 * day, 11 * day)) // reminded yesterday
        assertTrue(AppUpdate.shouldRemind("7.10.0", "7.10.0", 10 * day, 13 * day))  // three days on
        assertTrue(AppUpdate.shouldRemind("7.11.0", "7.10.0", 10 * day, 10 * day))  // a newer one: at once
    }
}
