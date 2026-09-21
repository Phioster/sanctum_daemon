package org.phioster.sanctumd.ui.seerr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.SeerrSearchItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*

/** Search Seerr by title. A hit opens the detail sheet rather than requesting blind. */
@Composable
internal fun SeerrSearchDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onDismiss: () -> Unit,
    onOpen: (org.phioster.sanctumd.model.SeerrDiscoverItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searchTerm by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrDiscoverItem>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Search ${config.label}", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Field("Search title", searchTerm) { searchTerm = it }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { searchResults = runCatching { vm.seerrSearchList(config, searchTerm) }.getOrElse { emptyList() } } },
                    enabled = searchTerm.isNotBlank(),
                ) { Text("Search", fontFamily = Mono) }
                Spacer(Modifier.height(8.dp))
                // A hit opens the same detail sheet the discover rows open: the poster, the plot
                // and the request status are in the search answer already, and requesting a
                // title unseen is how the wrong one gets requested.
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val res = searchResults
                    when {
                        res == null -> {}
                        res.isEmpty() -> Text("no results", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> res.forEach { r ->
                            SeerrDiscoverRow(r, accent) { onOpen(r) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/**
 * Confirm a request. Folder and quality appear only where Seerr offers more than one, and a
 * series asks which seasons -- everything keyed to [item], so a different title starts clean.
 */
@Composable
internal fun SeerrRequestDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    item: SeerrSearchItem,
    onDismiss: () -> Unit,
    onRequested: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rootFolders by remember(item) { mutableStateOf<List<org.phioster.sanctumd.model.SeerrRootFolder>>(emptyList()) }
    var chosenFolder by remember(item) { mutableStateOf<org.phioster.sanctumd.model.SeerrRootFolder?>(null) }
    var profiles by remember(item) { mutableStateOf<List<org.phioster.sanctumd.model.SeerrProfile>>(emptyList()) }
    var chosenProfile by remember(item) { mutableStateOf<org.phioster.sanctumd.model.SeerrProfile?>(null) }
    var defaultProfileId by remember(item) { mutableStateOf(0) }
    var seasons by remember(item) { mutableStateOf<List<org.phioster.sanctumd.model.SeerrSeason>?>(null) }
    var selectedSeasons by remember(item) { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(item) {
        // Only worth offering when there is something to choose between; a single-option
        // setup keeps the dialog exactly as it was.
        val opts = runCatching { vm.seerrOptionsOf(config, item.mediaType) }.getOrNull()
        if (opts != null) {
            if (opts.rootFolders.size > 1) {
                rootFolders = opts.rootFolders
                chosenFolder = opts.rootFolders.firstOrNull { it.isDefault } ?: opts.rootFolders.first()
            }
            if (opts.profiles.size > 1) {
                profiles = opts.profiles
                defaultProfileId = opts.defaultProfileId
                chosenProfile = opts.profiles.firstOrNull { it.id == opts.defaultProfileId } ?: opts.profiles.first()
            }
        }
        if (item.mediaType == "tv") {
            val s = runCatching { vm.seerrSeasonsList(config, item.tmdbId) }.getOrDefault(emptyList())
            seasons = s
            selectedSeasons = s.map { it.seasonNumber }.toSet() // default: all
        }
    }
    val isTv = item.mediaType == "tv"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Request: ${item.title}", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column {
                Text(
                    "${if (isTv) "Series" else "Movie"}${if (item.year.isNotBlank()) " (${item.year})" else ""}",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 13.sp,
                )
                if (rootFolders.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    DropdownField(
                        "Folder",
                        chosenFolder?.path?.substringAfterLast('/').orEmpty(),
                        rootFolders.map { it.path },
                    ) { i -> chosenFolder = rootFolders[i] }
                }
                if (profiles.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    DropdownField(
                        "Quality",
                        chosenProfile?.name.orEmpty(),
                        profiles.map { it.name },
                    ) { i -> chosenProfile = profiles[i] }
                }
                if (isTv) {
                    Spacer(Modifier.height(8.dp))
                    val ss = seasons
                    if (ss == null) {
                        Text("loading seasons…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    } else {
                        val allSel = selectedSeasons.size == ss.size && ss.isNotEmpty()
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedSeasons = if (allSel) emptySet() else ss.map { it.seasonNumber }.toSet()
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (allSel) "[x] " else "[ ] ", fontFamily = Mono, color = if (allSel) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 13.sp)
                            Text("All seasons", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
                        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                            ss.forEach { s ->
                                val checked = s.seasonNumber in selectedSeasons
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        selectedSeasons = if (checked) selectedSeasons - s.seasonNumber else selectedSeasons + s.seasonNumber
                                    }.padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (checked) "[x] " else "[ ] ", fontFamily = Mono, color = if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), fontSize = 12.sp)
                                    Text("${s.name} · ${s.episodeCount} ep", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isTv || selectedSeasons.isNotEmpty(),
                onClick = {
                    val chosen = if (!isTv) null else selectedSeasons.toList().sorted()
                    // Only send a folder when the user steered away from Seerr's default.
                    val folder = chosenFolder?.takeIf { !it.isDefault }
                    // Same rule as the folder: only send it when steered off the default.
                    val profile = chosenProfile?.takeIf { it.id != defaultProfileId }
                    onDismiss()
                    scope.launch {
                        onRequested(vm.seerrRequestMedia(config, item.tmdbId, item.mediaType, chosen, folder?.path, folder?.serverId, profile?.id))
                    }
                },
            ) { Text("Request", fontFamily = Mono, color = MatrixGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/** Seerr's own request counters and its user list. */
@Composable
internal fun SeerrStatsDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    accent: Color,
    onDismiss: () -> Unit,
) {
    var stats by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var users by remember { mutableStateOf<List<org.phioster.sanctumd.model.SeerrUserInfo>?>(null) }
    LaunchedEffect(Unit) {
        stats = runCatching { vm.seerrStats(config) }.getOrDefault(emptyList())
        users = runCatching { vm.seerrUserList(config) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Users & stats", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SectionHeader("REQUESTS")
                Spacer(Modifier.height(6.dp))
                val st = stats
                when {
                    st == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> st.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            pair.forEach { (k, v) ->
                                Column(Modifier.weight(1f)) {
                                    Text(v, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(k.uppercase(), fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
                Spacer(Modifier.height(8.dp))
                SectionHeader("USERS")
                Spacer(Modifier.height(6.dp))
                val us = users
                when {
                    us == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    us.isEmpty() -> Text("no users", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> us.forEach { u ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(u.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (u.email.isNotBlank()) Text(u.email, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${u.requestCount} req", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/**
 * One issue with its comments. [onListChanged] is for the list behind it, which goes stale
 * the moment an issue is resolved, reopened or deleted.
 */
@Composable
internal fun SeerrIssueDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    issueId: Int,
    accent: Color,
    onMessage: (String) -> Unit,
    onListChanged: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var issueDetail by remember(issueId) { mutableStateOf<org.phioster.sanctumd.model.SeerrIssueDetail?>(null) }
    var commentText by remember(issueId) { mutableStateOf("") }
    LaunchedEffect(issueId) { issueDetail = runCatching { vm.seerrIssueDetailOf(config, issueId) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Surface,
        title = { Text(issueDetail?.title ?: "Issue #$issueId", fontFamily = Mono, color = MatrixGreen, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val d = issueDetail
            Column(Modifier.heightIn(max = 460.dp)) {
                if (d == null) {
                    Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                } else {
                    Text("${d.type} · ${d.status}", fontFamily = Mono, color = if (d.status == "open") WarnAmber else MatrixGreen, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (d.description.isNotBlank()) {
                            Text(d.description, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        d.comments.forEach { c ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text("${c.author} · ${c.date}", fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 10.sp)
                                Text(c.message, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Field("Add comment", commentText) { commentText = it }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        OutlinedButton(
                            enabled = commentText.isNotBlank(),
                            onClick = {
                                val msg = commentText; commentText = ""
                                scope.launch {
                                    onMessage(vm.seerrComment(config, issueId, msg))
                                    issueDetail = runCatching { vm.seerrIssueDetailOf(config, issueId) }.getOrNull()
                                }
                            },
                        ) { Text("Comment", fontFamily = Mono) }
                        Spacer(Modifier.width(8.dp))
                        val resolved = d.status == "resolved"
                        OutlinedButton(onClick = {
                            scope.launch {
                                onMessage(vm.seerrIssueStatus(config, issueId, !resolved))
                                issueDetail = runCatching { vm.seerrIssueDetailOf(config, issueId) }.getOrNull()
                                onListChanged()
                            }
                        }) { Text(if (resolved) "Reopen" else "Resolve", fontFamily = Mono) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onListChanged(); onClose() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
        dismissButton = {
            TextButton(onClick = {
                onClose()
                scope.launch { onMessage(vm.seerrDeleteIssue(config, issueId)); onListChanged() }
            }) { Text("Delete", fontFamily = Mono, color = ErrRed) }
        },
    )
}
