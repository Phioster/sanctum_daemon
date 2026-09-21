package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.ui.theme.ErrRed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ArrParsedRelease
import org.phioster.sanctumd.model.ProwlarrRelease
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.prowlarr.ProwlarrReleaseRow
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The newznab top-level category to search for a given service.
 *
 * 0 means "everything": better to search too widely than to pick a category the indexer files
 * things differently under and come back empty, which is indistinguishable from "not there".
 */
internal fun prowlarrCategoryFor(type: ServiceType): Int = when (type) {
    ServiceType.RADARR -> 2000
    ServiceType.SONARR -> 5000
    ServiceType.LIDARR -> 3000
    else -> 0
}

/**
 * Searches Prowlarr **by text** for something the service is missing, and pushes a chosen
 * release straight back to it.
 *
 * The service's own automatic search goes out by id — so when an indexer carries the wrong id on
 * a release, or none, the right file is invisible to it however often you search. A text search
 * finds it. The term starts as the title but is editable, because the release is often named
 * something else entirely.
 */
@Composable
internal fun ArrProwlarrSearchDialog(
    vm: DashboardViewModel,
    prowlarr: ServiceConfig,
    target: ServiceConfig,
    initialQuery: String,
    accent: Color,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<ProwlarrRelease>?>(null) }
    var judged by remember { mutableStateOf<Map<String, ArrParsedRelease>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun search() {
        busy = true
        judged = emptyMap()
        val found = runCatching { vm.prowlarrSearchList(prowlarr, query.trim(), prowlarrCategoryFor(target.type)) }
            .getOrDefault(emptyList())
        results = found
        busy = false
        // The indexer's listing says nothing about whether a release is worth taking — the
        // service does. Judged after the list is shown, concurrently, so the results are not
        // held back by it, and a service that cannot answer simply leaves the line out.
        judged = coroutineScope {
            found.take(12).map { r -> async { r.title to vm.arrParse(target, r.title) } }.awaitAll()
        }.mapNotNull { (t, v) -> v?.let { t to it } }.toMap()
    }
    LaunchedEffect(Unit) { search() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Search on ${prowlarr.label}", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Field("Search term", query) { query = it }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { scope.launch { search() } },
                    enabled = query.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "searching…" else "Search", fontFamily = Mono, color = accent) }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (val list = results) {
                        null -> Text("…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> if (list.isEmpty()) {
                            Text(
                                if (busy) "searching…" else "nothing found — try the original title",
                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
                            )
                        } else list.forEach { rel ->
                            ProwlarrReleaseRow(
                                item = rel,
                                accent = accent,
                                // One target only: the service this search was opened from.
                                arrTargets = listOf(target),
                                onGrab = { scope.launch { onResult(vm.sendReleaseToArr(target, rel)) } },
                                onSendTo = { t -> scope.launch { onResult(vm.sendReleaseToArr(t, rel)) } },
                                detail = judged[rel.title]?.let { v -> { Verdict(v) } },
                            )

                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
        },
    )
}

/** Quality and language, with the "Unknown" a service returns when it could not tell dropped. */
internal fun verdictPrefix(v: ArrParsedRelease): String = buildList {
    if (v.quality.isNotBlank()) add(v.quality)
    v.languages.split(",").map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("Unknown", true) }
        .takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
}.joinToString(" · ")

/**
 * What the target service makes of this release: quality, language, score and the custom formats
 * behind it.
 *
 * One wrapping Text rather than a Row of them — on a phone the format list runs past the edge,
 * and a row of fixed cells clips it away instead of breaking. The score keeps its own colour
 * through an annotated span, because its sign is the whole message.
 */
@Composable
private fun Verdict(v: ArrParsedRelease) {
    val prefix = verdictPrefix(v)
    Text(
        buildAnnotatedString {
            if (prefix.isNotBlank()) {
                withStyle(SpanStyle(color = MatrixGreen.copy(alpha = 0.75f))) { append(prefix) }
                append("  ")
            }
            withStyle(SpanStyle(color = MatrixGreen.copy(alpha = 0.45f))) { append("score ") }
            withStyle(SpanStyle(color = if (v.score < 0) ErrRed else MatrixGreen)) { append(v.score.toString()) }
            if (v.formats.isNotBlank()) {
                withStyle(SpanStyle(color = MatrixGreen.copy(alpha = 0.45f))) { append("  " + v.formats) }
            }
        },
        fontFamily = Mono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}
