package com.music.aalap.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.music.aalap.data.entity.SongsModel
import com.music.aalap.data.preferences.StreamQuality
import com.music.aalap.ui.components.springyClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-phone checks of screens that changed: they draw what they should and the taps do what
 * they say. Run with `./gradlew :app:connectedDebugAndroidTest` (installs the debug copy only).
 */
@RunWith(AndroidJUnit4::class)
class ScreenChecksTest {
    @get:Rule val rule = createComposeRule()

    private val songs = List(10) { i -> SongsModel(i, "Song $i", "Album", "Artist", "", "q:$i", durationMs = 200_000) }

    @Test
    fun downloadChooserHidesLosslessAndDownloadsThePick() {
        var chose: StreamQuality? = null
        rule.setContent {
            // The sheet opens in its own window over a screen, as it does in the app.
            androidx.compose.foundation.layout.Box(Modifier.size(400.dp)) { Text("page") }
            com.music.aalap.ui.components.DownloadChooserSheet(songs, onDismiss = {}, onChoose = { chose = it })
        }
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("High")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("High").assertExists()
        rule.onNodeWithText("Normal").assertExists()
        rule.onNodeWithText("Data saver").assertExists()
        assertEquals(0, rule.onAllNodes(androidx.compose.ui.test.hasText("Lossless FLAC")).fetchSemanticsNodes().size)
        rule.onNodeWithText("Data saver").performScrollTo().performClick()
        rule.onNode(androidx.compose.ui.test.hasText("Download ·", substring = true)).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(StreamQuality.LOW, chose)
    }

    @Test
    fun springyButtonsStillClick() {
        var taps = 0
        rule.setContent { Text("Skip", Modifier.size(80.dp).springyClick { taps++ }) }
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Skip").performClick()
        rule.waitForIdle()
        assertEquals(2, taps)
    }

    @Test
    fun homeSectionsDialogListsEverySection() {
        rule.setContent { com.music.aalap.ui.components.HomeSectionsDialog(onDismiss = {}) }
        rule.onNodeWithText("Sections on Home").assertIsDisplayed()
        rule.onNodeWithText("Recently played").assertIsDisplayed()
        rule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun selfCheckSummaryReadsRight() {
        val ok = com.music.aalap.ui.screens.SelfCheck.Result("Songs: main source", com.music.aalap.ui.screens.SelfCheck.Status.OK, "")
        val bad = com.music.aalap.ui.screens.SelfCheck.Result("Storage", com.music.aalap.ui.screens.SelfCheck.Status.FAIL, "")
        assertTrue(com.music.aalap.ui.screens.SelfCheck.summary(listOf(ok, bad)).startsWith("Music plays"))
    }
}
