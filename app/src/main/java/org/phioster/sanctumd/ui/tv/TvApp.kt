package org.phioster.sanctumd.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.theme.Black

/**
 * The whole TV client: sign-in when no server is configured, otherwise the browse stack with the
 * player as an overlay on top.
 *
 * The player is not a route because it must survive whatever is underneath it, leaving playback
 * returns to exactly the screen (and scroll position) it was started from.
 */
@UnstableApi
@Composable
fun TvApp(vm: TvViewModel, onExit: () -> Unit) {
    val services by vm.services.collectAsState()
    // Derived from the collected list rather than read off the ViewModel, so the sign-in screen
    // hands over to the browser the instant the save lands.
    val config = services?.firstOrNull { it.type == ServiceType.JELLYFIN }

    Box(Modifier.fillMaxSize().background(Black)) {
        when {
            services == null -> TvMessage("starting…")

            config == null -> TvSetupScreen(onConfigured = { vm.saveServer(it) })

            else -> {
                /**
                 * Series and seasons open their detail page, not the grid. That page carries the
                 * "carry on watching" button, so getting back into a show is one press rather than
                 * a hunt through seasons for the episode you were on. Libraries and other containers
                 * still drill straight in, and anything playable opens its own page.
                 */
                val open: (JellyMediaItem) -> Unit = { item ->
                    when {
                        item.kind in FOLDER_KINDS -> vm.open(TvRoute.Detail(item.id, item.name))
                        item.isFolder -> vm.open(TvRoute.Browse(item.id, item.name))
                        else -> vm.open(TvRoute.Detail(item.id, item.name))
                    }
                }

                when (val route = vm.current) {
                    TvRoute.Home -> TvHomeScreen(
                        config = config,
                        refreshTick = vm.refreshTick,
                        onOpen = open,
                        onSwitchServer = { vm.forgetServer() },
                    )
                    is TvRoute.Browse -> TvBrowseScreen(
                        config = config,
                        parentId = route.id,
                        title = route.title,
                        refreshTick = vm.refreshTick,
                        onOpen = open,
                    )
                    is TvRoute.Detail -> TvDetailScreen(
                        config = config,
                        itemId = route.id,
                        fallbackTitle = route.title,
                        refreshTick = vm.refreshTick,
                        onPlay = { id, name -> vm.playing = TvPlayRequest(id, name) },
                        onBrowse = { id, name -> vm.open(TvRoute.Browse(id, name)) },
                    )
                }

                vm.playing?.let { request ->
                    TvPlayerScreen(
                        config = config,
                        itemId = request.itemId,
                        title = request.title,
                        onLeave = {
                            vm.playing = null
                            // Watched state and resume positions moved on while we were playing.
                            vm.refresh()
                        },
                    )
                }

                // The player brings its own back handling; this one only runs underneath it.
                BackHandler(enabled = vm.playing == null) {
                    if (!vm.back()) onExit()
                }
            }
        }
    }
}
