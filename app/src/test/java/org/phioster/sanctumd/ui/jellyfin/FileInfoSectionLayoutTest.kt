package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
 * Guards the FILE section's *size*, which no unit test could see.
 *
 * It once collapsed into a screen-high empty block: the header is a fillMaxWidth Row, and
 * nesting it inside another Row left the summary text ~0 width, so it wrapped to one character
 * per line and inflated the row. Everything compiled, all 120 unit tests passed, and the screen
 * was unusable. A height assertion is what catches that class of defect.
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

    private fun show() = compose.setContent {
        Column(Modifier.width(400.dp)) { FileInfoSection(info, MatrixGreen) }
    }

    @Test
    fun `collapsed the section stays about one line tall`() {
        show()
        val height = compose.onRoot().getUnclippedBoundsInRoot().height
        // A correct header is ~30dp. The bug produced 13 wrapped lines, well past 100dp.
        assertTrue("collapsed section is $height tall", height < 80.dp)
    }

    @Test
    fun `collapsed it summarises the file instead of hiding everything`() {
        show()
        compose.onNodeWithText("MKV · 8.43 GB").assertIsDisplayed()
    }

    @Test
    fun `expanding reveals the tracks and grows the section`() {
        show()
        val collapsed = compose.onRoot().getUnclippedBoundsInRoot().height
        compose.onNodeWithText("FILE").performClick()
        compose.onNodeWithText("AUDIO").assertIsDisplayed()
        assertTrue(compose.onRoot().getUnclippedBoundsInRoot().height > collapsed)
    }
}
