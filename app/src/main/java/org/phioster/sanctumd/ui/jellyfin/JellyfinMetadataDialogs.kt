package org.phioster.sanctumd.ui.jellyfin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.JellyMediaDetail
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.JellyMetadata
import org.phioster.sanctumd.net.JellyRemoteImage
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.Field
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/** "Action, Drama ,, Thriller" → the list Jellyfin stores. */
internal fun splitList(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/**
 * The fields Jellyfin's metadata editor offers that matter on a phone. "Lock" keeps the server
 * from overwriting the edit at its next metadata refresh, the usual reason an edit "didn't stick".
 */
@Composable
internal fun JellyfinMetadataDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    target: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<JellyMetadata?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var sortTitle by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("") }
    var locked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(target.id) {
        runCatching { vm.jellyfinMetadataOf(config, target.id) }
            .onSuccess { m ->
                loaded = m
                name = m.name; original = m.originalTitle; sortTitle = m.sortTitle
                year = m.year?.toString().orEmpty(); overview = m.overview
                genres = m.genres.joinToString(", "); tags = m.tags.joinToString(", ")
                rating = m.officialRating; locked = m.locked
            }
            .onFailure { error = it.message ?: "failed" }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Edit metadata", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 12.sp)
                    loaded == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                    else -> {
                        Field("Title", name) { name = it }
                        Field("Original title", original) { original = it }
                        Field("Sort title", sortTitle) { sortTitle = it }
                        Field("Year", year) { v -> year = v.filter { it.isDigit() }.take(4) }
                        OutlinedTextField(
                            value = overview,
                            onValueChange = { overview = it },
                            label = { Text("Overview", fontFamily = Mono) },
                            minLines = 3, maxLines = 8,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        Field("Genres, comma separated", genres) { genres = it }
                        Field("Tags, comma separated", tags) { tags = it }
                        Field("Parental rating", rating) { rating = it }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("lock metadata", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                                Text("refreshes won't overwrite it", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                            Switch(checked = locked, onCheckedChange = { locked = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = loaded != null && name.isNotBlank() && !busy, onClick = {
                busy = true
                val m = JellyMetadata(
                    name = name.trim(), originalTitle = original.trim(), sortTitle = sortTitle.trim(),
                    year = year.toIntOrNull(), overview = overview.trim(),
                    genres = splitList(genres), tags = splitList(tags),
                    officialRating = rating.trim(), locked = locked,
                )
                scope.launch {
                    val msg = vm.jellyfinSaveMeta(config, target.id, m)
                    busy = false
                    onSaved(msg)
                }
            }) { Text(if (busy) "…" else "Save", fontFamily = Mono, color = accent) }
        },
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

/**
 * Pick the poster, backdrop or logo from what the metadata providers offer, or upload one from
 * the phone. The server downloads the chosen image itself; the phone only shows thumbnails.
 */
@Composable
internal fun JellyfinImagesDialog(
    vm: DashboardViewModel,
    config: ServiceConfig,
    target: JellyMediaDetail,
    accent: Color,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var type by remember { mutableStateOf("Primary") }
    var images by remember { mutableStateOf<List<JellyRemoteImage>?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(type) {
        images = null
        images = runCatching { vm.jellyfinRemoteImageList(config, target.id, type) }.getOrDefault(emptyList())
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val msg = if (bytes == null) "error: couldn't read the image" else vm.jellyfinUploadImageBytes(config, target.id, type, bytes, mime)
            busy = false
            onChanged(msg)
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Surface,
        title = { Text("Images", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp) },
        text = {
            Column {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Primary" to "poster", "Backdrop" to "backdrop", "Logo" to "logo", "Thumb" to "thumb").forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(label, fontFamily = Mono, fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                TextButton(enabled = !busy, onClick = { picker.launch("image/*") }) {
                    Text("upload from phone", fontFamily = Mono, color = accent)
                }
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val list = images
                    when {
                        busy -> Text("working…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        list == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        list.isEmpty() -> Text("the providers offer none", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp)
                        else -> {
                            // Posters are tall, everything else is wide.
                            val columns = if (type == "Primary") 3 else 2
                            val ratio = if (type == "Primary") 2f / 3f else 16f / 9f
                            list.chunked(columns).forEach { rowImages ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowImages.forEach { img ->
                                        Column(Modifier.weight(1f).clickable {
                                            busy = true
                                            scope.launch {
                                                val msg = vm.jellyfinPickImage(config, target.id, type, img.url)
                                                busy = false
                                                onChanged(msg)
                                            }
                                        }) {
                                            AsyncImage(
                                                model = img.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = if (type == "Logo") ContentScale.Fit else ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(4.dp)),
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                listOf(img.provider, img.language, if (img.width > 0) "${img.width}x${img.height}" else "")
                                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 8.sp, maxLines = 1,
                                            )
                                        }
                                    }
                                    repeat(columns - rowImages.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close", fontFamily = Mono, color = MatrixGreen) } },
    )
}
