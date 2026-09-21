package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.sanctumd.model.JellyFileInfo
import org.phioster.sanctumd.model.JellyStream
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the FILE section: it renders, it summarises when collapsed, it reveals
 * the tracks when opened.
 *
 * **These do NOT guard against the layout bug that prompted them**, and that was measured, not
 * assumed: with the original defect restored, the collapsed section still measured 43dp here
 * instead of the screen-high block it produced on a device. Robolectric does not do real text
 * shaping, so the "one character per line" wrap that inflated the row never happens, the whole
 * defect class is invisible to it. A size guard needs an instrumented test on a real emulator.
 *
 * Keeping them anyway: they catch crashes, missing content and broken expand/collapse, for
 * free, in the fast job.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileInfoSectionLayoutTest {

    @get:Rule val compose = createComposeRule()

    private val info = JellyFileInfo(
        container = "mkv",
        sizeBytes = 9_052_398_293L,
        path = "/storage/emulated/0/Movies/Coraline (2009)/Coraline.mkv",
        bitrate = 9_204_000,
        streams = listOf(
            JellyStream(type = "Video", codec = "h264", width = 1920, height = 1080),
            JellyStream(type = "Audio", codec = "eac3", language = "Deutsch", channels = 6),
        ),
    )

    // Measured on a tagged wrapper, not on onRoot(): the root reports the host window's
    // bounds, which stay constant however tall the content grows. A measurement that cannot
    // fail is worse than none.
    private fun show() = compose.setContent {
        Column(Modifier.width(400.dp).testTag("section")) { FileInfoSection(info, MatrixGreen) }
    }

    private fun sectionHeight() = compose.onNodeWithTag("section").getUnclippedBoundsInRoot().height

    @Test
    fun `collapsed it summarises the file instead of hiding everything`() {
        show()
        compose.onNodeWithText("MKV · 8.43 GB").assertIsDisplayed()
    }

    @Test
    fun `expanding reveals the tracks and grows the section`() {
        show()
        val collapsed = sectionHeight()
        compose.onNodeWithText("FILE").performClick()
        compose.onNodeWithText("AUDIO").assertIsDisplayed()
        assertTrue(sectionHeight() > collapsed)
    }
}
