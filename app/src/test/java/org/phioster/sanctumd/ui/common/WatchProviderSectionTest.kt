package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.phioster.sanctumd.model.WatchAvailability
import org.phioster.sanctumd.model.WatchProvider
import org.phioster.sanctumd.model.WatchProviderKind
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one rule the streaming row has to keep: it is there when the title streams somewhere, and
 * gone — heading and all — when it does not. A lone "STREAMING" header over an empty row reads
 * as a failed lookup rather than as "nowhere".
 *
 * Logos are left blank on purpose: Robolectric loads no images, so the tiles under test are the
 * name-initial fallback. What is asserted here is which tiles exist, not what they look like.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchProviderSectionTest {

    @get:Rule val compose = createComposeRule()

    private fun show(availability: WatchAvailability) = compose.setContent {
        Column(Modifier.width(400.dp)) { WatchProviderSection(availability, MatrixGreen) }
    }

    @Test
    fun `available titles list every service, with the paid offers marked`() {
        show(
            WatchAvailability(
                region = "DE",
                providers = listOf(
                    WatchProvider(8, "Netflix", "", WatchProviderKind.STREAM),
                    WatchProvider(2, "Apple TV", "", WatchProviderKind.BUY),
                ),
                link = "https://www.themoviedb.org/movie/603/watch?locale=DE",
            ),
        )

        compose.onNodeWithText("STREAMING").assertIsDisplayed()
        compose.onNodeWithText("DE").assertIsDisplayed()
        compose.onNodeWithText("Netflix").assertIsDisplayed()
        compose.onNodeWithText("Apple TV").assertIsDisplayed()
        // A subscription needs no tag; a purchase does, or the two look the same.
        compose.onNodeWithText("buy").assertIsDisplayed()
        compose.onNodeWithText("stream").assertDoesNotExist()
    }

    @Test
    fun `a title that streams nowhere shows no section at all`() {
        show(WatchAvailability(region = "DE"))

        compose.onNodeWithText("STREAMING").assertDoesNotExist()
        compose.onNodeWithText("DE").assertDoesNotExist()
    }
}
