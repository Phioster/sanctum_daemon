package org.phioster.sanctumd.ui.arr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.ArrDetail
import org.phioster.sanctumd.model.ArrFileInfo
import org.phioster.sanctumd.model.ArrProfile
import org.phioster.sanctumd.ui.jellyfin.formatFileSize
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.WarnAmber

/** One line for a file, the same in the episode row and under a movie. */
internal fun fileSummary(f: ArrFileInfo): String =
    listOf(f.quality, formatFileSize(f.sizeBytes)).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * What is on disk for one episode or movie, and the way to get rid of it. Deleting the file
 * keeps the entry, so a monitored title is grabbed again, which is how a better copy is forced.
 */
@Composable
internal fun ArrFileDialog(
    file: ArrFileInfo,
    title: String,
    accent: Color,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                FileField("quality", file.quality, if (file.cutoffNotMet) WarnAmber else accent)
                if (file.cutoffNotMet) {
                    Text("below the profile's cutoff, an upgrade is still wanted", fontFamily = Mono, color = WarnAmber, fontSize = 10.sp)
                }
                FileField("size", formatFileSize(file.sizeBytes), accent)
                FileField("video", file.video, accent)
                FileField("audio", file.audio, accent)
                FileField("subtitles", file.subtitles, accent)
                FileField("group", file.releaseGroup, accent)
                Spacer(Modifier.height(6.dp))
                Text("FILE", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp)
                // Paths are regularly wider than the screen; scrolling beats cutting them off.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Text(file.path, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { if (confirm) onDelete() else confirm = true }) {
                    Text(if (confirm) "Tap again to delete the file" else "Delete file", fontFamily = Mono, color = ErrRed)
                }
                if (confirm) {
                    Text(
                        "Removes the file from disk. The entry stays, so if it is monitored it is searched again.",
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
private fun FileField(label: String, value: String, accent: Color) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.width(84.dp))
        Text(value, fontFamily = Mono, color = accent.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}

/**
 * The settings that could only be chosen when a title was added: quality profile, and for a
 * series its type and season folders, for a movie when it counts as available.
 */
@Composable
internal fun ArrEditDialog(
    detail: ArrDetail,
    isSonarr: Boolean,
    accent: Color,
    loadProfiles: suspend () -> List<ArrProfile>,
    onDismiss: () -> Unit,
    onSave: (profileId: Int, seriesType: String?, seasonFolder: Boolean?, minimumAvailability: String?) -> Unit,
) {
    var profiles by remember { mutableStateOf<List<ArrProfile>?>(null) }
    var profileId by remember { mutableStateOf(detail.qualityProfileId) }
    var seriesType by remember { mutableStateOf(detail.seriesType.ifBlank { "standard" }) }
    var seasonFolder by remember { mutableStateOf(detail.seasonFolder) }
    var availability by remember { mutableStateOf(detail.minimumAvailability.ifBlank { "released" }) }
    LaunchedEffect(Unit) { profiles = runCatching { loadProfiles() }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Edit ${detail.title}", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, maxLines = 2) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("QUALITY PROFILE", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                val ps = profiles
                when {
                    ps == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> ps.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { profileId = p.id }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Icon(
                                if (p.id == profileId) AppIcons.Selected else AppIcons.NotSelected,
                                contentDescription = null, tint = accent, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(p.name, fontFamily = Mono, color = if (p.id == profileId) accent else MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (isSonarr) {
                    Text("SERIES TYPE", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("standard", "anime", "daily").forEach { t ->
                            FilterChip(
                                selected = seriesType == t,
                                onClick = { seriesType = t },
                                label = { Text(t, fontFamily = Mono, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("season folders", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = seasonFolder, onCheckedChange = { seasonFolder = it })
                    }
                } else {
                    Text("AVAILABLE WHEN", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("announced" to "announced", "inCinemas" to "in cinemas", "released" to "released").forEach { (v, label) ->
                            FilterChip(
                                selected = availability == v,
                                onClick = { availability = v },
                                label = { Text(label, fontFamily = Mono, fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isSonarr) onSave(profileId, seriesType, seasonFolder, null) else onSave(profileId, null, null, availability)
            }) { Text("Save", fontFamily = Mono, color = accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}
