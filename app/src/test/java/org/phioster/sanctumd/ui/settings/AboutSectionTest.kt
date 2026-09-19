package org.phioster.sanctumd.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The about screen was three lines in a corner; what matters now is that all four blocks actually
 * turn up, and that the diagnostics block on screen says kinds of services rather than anything
 * that identifies a server. The no-leak rule itself is pinned in [DiagnosticsTest]; this checks
 * that the screen shows the guarded text and not something it built on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutSectionTest {

    @get:Rule val compose = createComposeRule()

    // Scrollable, like the settings screen the section actually lives in: the page is longer than
    // a phone, so anything below the fold is simply not "displayed" until it is scrolled to.
    private fun show(serviceTypes: List<String>) = compose.setContent {
        Column(Modifier.width(400.dp).verticalScroll(rememberScrollState())) { AboutBody(serviceTypes) }
    }

    private fun see(text: String, substring: Boolean = false) =
        compose.onNodeWithText(text, substring = substring).performScrollTo().assertIsDisplayed()

    @Test
    fun `every block is on the screen`() {
        show(listOf("JELLYFIN", "RADARR"))
        for (heading in listOf("> what it talks to", "> project", "> diagnostics", "> built with")) {
            see(heading)
        }
        see("> sanctumd_")
    }

    @Test
    fun `the project links are there to be tapped`() {
        show(emptyList())
        for (row in listOf("source", "changelog", "report a problem", "licence")) {
            see(row)
        }
    }

    @Test
    fun `the diagnostics block names kinds and offers itself for copying`() {
        show(listOf("JELLYFIN", "RADARR", "RADARR"))
        see("jellyfin, radarr", substring = true)
        see("3 configured", substring = true)
        see("copy for an issue")
    }

    @Test
    fun `with nothing set up it says so instead of showing an empty list`() {
        show(emptyList())
        see("none configured", substring = true)
    }

    @Test
    fun `libmpv is not passed off as Apache like the rest`() {
        show(emptyList())
        see("libmpv-android", substring = true)
        see("bundled mpv & FFmpeg are GPL-3.0", substring = true)
    }
}
