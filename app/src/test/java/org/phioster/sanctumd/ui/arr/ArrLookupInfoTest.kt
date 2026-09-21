package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.sanctumd.model.ArrLookupItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the info screen has to say about a title before anyone adds it, and the one thing it must
 * never get wrong: whether the service already holds it. "add" on a title that is in the library
 * creates a duplicate; "open" on one that is not leads nowhere.
 *
 * Posters stay empty here — Robolectric loads no images, and what is asserted is the text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArrLookupInfoTest {

    @get:Rule val compose = createComposeRule()

    private val radarr = ServiceConfig(type = ServiceType.RADARR, label = "Radarr", baseUrl = "https://example.net/")

    private fun item(libraryId: Int = 0) = ArrLookupItem(
        title = "Oppenheimer",
        year = 2023,
        raw = "{}",
        overview = "The story of J. Robert Oppenheimer.",
        genres = "Drama, History",
        facts = listOf("rating" to "8.0", "rated" to "R", "runtime" to "3h 1m", "status" to "released"),
        libraryId = libraryId,
    )

    private fun show(item: ArrLookupItem, onAdd: () -> Unit = {}, onOpen: () -> Unit = {}) =
        compose.setContent {
            Column(Modifier.width(400.dp)) {
                ArrLookupInfo(item, radarr, MatrixGreen, onBack = {}, onAdd = onAdd, onOpenLibrary = onOpen)
            }
        }

    @Test
    fun `a title not in the library offers to add it, with its facts`() {
        show(item())

        compose.onNodeWithText("Oppenheimer").assertIsDisplayed()
        compose.onNodeWithText("not added").assertIsDisplayed()
        compose.onNodeWithText("add to Radarr").assertIsDisplayed()
        compose.onNodeWithText("3h 1m").assertExists()
        compose.onNodeWithText("RUNTIME").assertExists()
        compose.onNodeWithText("Drama, History").assertExists()
        compose.onNodeWithText("The story of J. Robert Oppenheimer.").assertExists()
        compose.onNodeWithText("open in Radarr").assertDoesNotExist()
    }

    @Test
    fun `a title already held leads into the library instead of adding it twice`() {
        show(item(libraryId = 42))

        compose.onNodeWithText("in library").assertIsDisplayed()
        compose.onNodeWithText("open in Radarr").assertIsDisplayed()
        compose.onNodeWithText("add to Radarr").assertDoesNotExist()
    }

    @Test
    fun `the buttons call what they say`() {
        var added = 0
        var opened = 0
        show(item(), onAdd = { added++ }, onOpen = { opened++ })

        compose.onNodeWithText("add to Radarr").performClick()
        assertEquals(1, added)
        assertEquals(0, opened)
    }

    @Test
    fun `a bare result still renders`() {
        show(ArrLookupItem(title = "Unknown", year = 0, raw = "{}"))

        compose.onNodeWithText("Unknown").assertIsDisplayed()
        compose.onNodeWithText("not added").assertIsDisplayed()
    }
}
