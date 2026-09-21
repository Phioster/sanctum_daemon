package org.phioster.sanctumd.ui.dashboard

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
import org.phioster.sanctumd.model.ArrQueueItem
import org.phioster.sanctumd.model.CardType
import org.phioster.sanctumd.model.DashCard
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The three states every dashboard card goes through -- nothing loaded yet, loaded and empty,
 * loaded with rows -- and the two that are easy to confuse: an error, and the top-watchers card
 * whose "no data" means a missing server plugin rather than a failure.
 *
 * A card that cannot tell "still loading" from "nothing there" leaves someone staring at a
 * spinner that was never going to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashCardDataBodyTest {

    @get:Rule val compose = createComposeRule()

    private val radarr = ServiceConfig(type = ServiceType.RADARR, label = "Radarr", baseUrl = "https://example.net/")

    private fun show(type: CardType, data: DashCardData) = compose.setContent {
        Column(Modifier.width(400.dp)) {
            DashCardDataBody(
                card = DashCard(id = "c1", type = type, serviceId = "s1"),
                config = radarr,
                data = data,
                accent = MatrixGreen,
                accentColor = MatrixGreen,
                posterWidth = 120.dp,
                onOpenService = {},
                onOpenItem = {},
                onOpenDiscover = {},
            )
        }
    }

    @Test
    fun `nothing loaded yet says so`() {
        show(CardType.RADARR_QUEUE, DashCardData())
        compose.onNodeWithText("loading…").assertIsDisplayed()
    }

    @Test
    fun `an empty queue is not a spinner`() {
        show(CardType.RADARR_QUEUE, DashCardData().apply { queue = emptyList() })
        compose.onNodeWithText("queue empty").assertIsDisplayed()
        compose.onNodeWithText("loading…").assertDoesNotExist()
    }

    @Test
    fun `a filled queue lists what is downloading`() {
        show(
            CardType.RADARR_QUEUE,
            DashCardData().apply {
                queue = listOf(ArrQueueItem(id = 1, title = "Oppenheimer", status = "downloading", progress = 0.42f))
            },
        )
        compose.onNodeWithText("Oppenheimer").assertIsDisplayed()
        compose.onNodeWithText("loading…").assertDoesNotExist()
    }

    @Test
    fun `a card that failed shows neither rows nor a spinner`() {
        show(CardType.RADARR_QUEUE, DashCardData().apply { error = "Unable to resolve host" })
        compose.onNodeWithText("loading…").assertDoesNotExist()
        compose.onNodeWithText("queue empty").assertDoesNotExist()
    }

    @Test
    fun `no health findings reads as healthy, not as missing data`() {
        show(CardType.RADARR_HEALTH, DashCardData().apply { sysHealth = emptyList() })
        compose.onNodeWithText("all healthy").assertIsDisplayed()
    }

    @Test
    fun `the top watchers card blames the plugin, not the connection`() {
        show(CardType.JELLYFIN_TOP, DashCardData().apply { error = "404" })
        compose.onNodeWithText("no playback data, install the Jellyfin “Playback Reporting” plugin").assertIsDisplayed()
    }
}
