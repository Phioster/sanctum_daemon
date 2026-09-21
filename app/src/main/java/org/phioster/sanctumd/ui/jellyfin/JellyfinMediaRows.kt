package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.AppIcons

// Posters, cards and rows for the Media tab, what a viewer browses.

@Composable
internal fun JellyPoster(url: String, config: ServiceConfig, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, scale: ContentScale) {
    val ctx = LocalContext.current
    val model = ImageRequest.Builder(ctx).data(url).apply {
        config.customHeaders.forEach { (k, v) -> addHeader(k, v) }
        org.phioster.sanctumd.net.jellyfinImageHeaders(config).forEach { (k, v) -> addHeader(k, v) }
    }.build()
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = scale,
        modifier = modifier.clip(shape).background(Surface),
    )
}

/**
 * "Fully watched" marker: a filled accent dot with a black check, the way Jellyfin's own web client
 * flags played items. Meant to be dropped on a poster [Box] with `Modifier.align(Alignment.TopEnd)`.
 */
@Composable
internal fun WatchedBadge(
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .padding(4.dp)
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(accent)
            .let { if (onClick != null) it.clickable { onClick() } else it },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = "watched", tint = Black, modifier = Modifier.size(size * 0.7f))
    }
}

/** Unwatched-episode count for a folder (Series/Season), Jellyfin's blue-dot equivalent. */
@Composable
internal fun UnplayedBadge(
    count: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .padding(4.dp)
            .heightIn(min = size)
            .widthIn(min = size)
            .clip(RoundedCornerShape(size / 2))
            .background(accent)
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            fontFamily = Mono, color = Black,
            fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The watched marker for one media item: a check when it's fully played, the unwatched-episode count
 * for a part-watched folder, nothing otherwise.
 *
 * [onSetWatched] makes it tappable; it takes the state the user asked for and returns whether that
 * was applied right away. A folder toggle goes through a confirmation dialog first, so the badge
 * must not flip until the caller says so.
 */
@Composable
internal fun WatchedMarker(
    item: org.phioster.sanctumd.model.JellyMediaItem,
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    onSetWatched: ((Boolean) -> Boolean)? = null,
) {
    // Optimistic: a tap flips the badge immediately, the next list load brings the server's truth.
    var played by remember(item.id, item.played) { mutableStateOf(item.played) }
    val tap: (() -> Unit)? = onSetWatched?.let { set -> { if (set(!played)) played = !played } }
    when {
        played -> WatchedBadge(accent, modifier, size, tap)
        item.isFolder && item.unplayedCount > 0 -> UnplayedBadge(item.unplayedCount, accent, modifier, size, tap)
    }
}

@Composable
internal fun JellyPosterCard(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, accent: Color, width: androidx.compose.ui.unit.Dp = 120.dp, caption: Boolean = true, onSetWatched: ((Boolean) -> Boolean)? = null, onClick: () -> Unit) {
    val h = width * 1.5f
    Column(Modifier.width(width).padding(end = 10.dp).clickable { onClick() }) {
        Box {
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(item.posterUrl, config, Modifier.width(width).height(h), RoundedCornerShape(6.dp), ContentScale.Crop)
            } else {
                Box(Modifier.width(width).height(h).clip(RoundedCornerShape(6.dp)).background(Surface))
            }
            if (item.progressPct > 0.01f) {
                LinearProgressIndicator(
                    progress = { item.progressPct },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = accent, trackColor = Black.copy(alpha = 0.6f),
                )
            }
            WatchedMarker(item, accent, Modifier.align(Alignment.TopEnd), if (width < 100.dp) 16.dp else 20.dp, onSetWatched)
        }
        if (caption) {
            Spacer(Modifier.height(4.dp))
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Media-home section label, delegates to the app-wide [SectionHeader] so every screen matches. */
@Composable
internal fun MediaSectionHeader(label: String, accent: Color, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) =
    SectionHeader(label, accent, modifier, trailing)

/** The big featured card at the top of the Media home: backdrop-cropped poster + scrim + title +
 *  Play/Resume button. Tapping the body opens detail; the button plays. */
@Composable
internal fun MediaHero(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, accent: Color, onSetWatched: ((Boolean) -> Boolean)? = null, onPlay: () -> Unit, onOpen: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen() }) {
        if (item.posterUrl.isNotBlank()) {
            JellyPoster(item.posterUrl, config, Modifier.matchParentSize(), RoundedCornerShape(14.dp), ContentScale.Crop)
        } else {
            Box(Modifier.matchParentSize().background(Surface))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Black.copy(alpha = 0.88f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(accent).clickable { onPlay() }.padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Icon(AppIcons.Play, contentDescription = null, tint = Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (item.progressPct > 0.01f) "Resume" else "Play", fontFamily = Mono, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (item.progressPct > 0.01f) {
            LinearProgressIndicator(
                progress = { item.progressPct },
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = accent, trackColor = Black.copy(alpha = 0.5f),
            )
        }
        WatchedMarker(item, accent, Modifier.align(Alignment.TopEnd).padding(6.dp), 24.dp, onSetWatched)
    }
}

/** A poster tile that fills its grid cell (2:3 poster + caption) for the library browse grid. */
@Composable
internal fun MediaGridCard(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, accent: Color, modifier: Modifier, onSetWatched: ((Boolean) -> Boolean)? = null, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) {
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(item.posterUrl, config, Modifier.matchParentSize(), RoundedCornerShape(6.dp), ContentScale.Crop)
            } else {
                Box(Modifier.matchParentSize().background(Surface))
            }
            if (item.progressPct > 0.01f) {
                LinearProgressIndicator(
                    progress = { item.progressPct },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    color = accent, trackColor = Black.copy(alpha = 0.6f),
                )
            }
            WatchedMarker(item, accent, Modifier.align(Alignment.TopEnd), onSetWatched = onSetWatched)
        }
        Spacer(Modifier.height(4.dp))
        Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.subtitle.isNotBlank()) {
            Text(item.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Browse-header action chip, delegates to the app-wide [AppChip]. */
@Composable
internal fun BrowseChip(
    label: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) = AppChip(label, accent, icon, onClick)

/** A library shown as a landscape tile (poster-cropped + scrim + name) in the Media home grid. */
@Composable
internal fun MediaLibraryTile(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(92.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() }) {
        if (item.posterUrl.isNotBlank()) {
            JellyPoster(item.posterUrl, config, Modifier.matchParentSize(), RoundedCornerShape(10.dp), ContentScale.Crop)
        } else {
            Box(Modifier.matchParentSize().background(Surface))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Black.copy(alpha = 0.8f)))))
        Text(
            item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

/** A horizontal poster row for the Media home, honouring a [MediaRowStyle] (poster size + optional
 *  Ken-Burns fanart background). */
@Composable
internal fun MediaPosterRow(
    items: List<org.phioster.sanctumd.model.JellyMediaItem>,
    config: ServiceConfig,
    accent: Color,
    style: org.phioster.sanctumd.model.MediaRowStyle,
    onSetWatched: ((org.phioster.sanctumd.model.JellyMediaItem, Boolean) -> Boolean)? = null,
    onOpen: (org.phioster.sanctumd.model.JellyMediaItem) -> Unit,
) {
    val w = when (style.posterSize) { "small" -> 84.dp; "large" -> 150.dp; else -> 120.dp }
    val bgUrl = items.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl
    if (style.background && bgUrl != null) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
            KenBurnsBackground(bgUrl, config)
            Box(Modifier.matchParentSize().background(Black.copy(alpha = 0.3f)))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                items.forEach { m -> JellyPosterCard(m, config, accent, width = w, onSetWatched = onSetWatched?.let { f -> { want: Boolean -> f(m, want) } }) { onOpen(m) } }
            }
        }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            items.forEach { m -> JellyPosterCard(m, config, accent, width = w) { onOpen(m) } }
        }
    }
}

/** Settings sheet for one Media-home row: accent, poster size + fanart bg (poster rows only), hide. */
@Composable
internal fun MediaRowConfigDialog(
    title: String,
    style: org.phioster.sanctumd.model.MediaRowStyle,
    serviceColor: Color,
    posterOptions: Boolean,
    onSave: (org.phioster.sanctumd.model.MediaRowStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var accent by remember { mutableStateOf(style.accent) }
    var poster by remember { mutableStateOf(style.posterSize) }
    var bg by remember { mutableStateOf(style.background) }
    var hidden by remember { mutableStateOf(style.hidden) }
    val label = @Composable { t: String -> Text(t, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("$title settings", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                label("ACCENT (1st = service default)")
                Spacer(Modifier.height(6.dp))
                AccentPickerRow(accent, serviceColor) { accent = it }
                if (posterOptions) {
                    Spacer(Modifier.height(16.dp))
                    label("POSTER SIZE")
                    Spacer(Modifier.height(6.dp))
                    Row {
                        listOf("small" to "S", "" to "M", "large" to "L").forEach { (value, lbl) ->
                            val sel = poster == value
                            Box(
                                Modifier.padding(end = 8.dp).size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) MatrixGreen else Surface)
                                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable { poster = value },
                                contentAlignment = Alignment.Center,
                            ) { Text(lbl, fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 15.sp) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    MediaCheckRow("Fanart background (Ken Burns)", bg) { bg = it }
                }
                Spacer(Modifier.height(16.dp))
                MediaCheckRow("Hide this row", hidden) { hidden = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(org.phioster.sanctumd.model.MediaRowStyle(accent, poster, bg, hidden)); onDismiss() }) {
                Text("Save", fontFamily = Mono, color = MatrixGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
    )
}

@Composable
private fun MediaCheckRow(text: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                .background(if (checked) MatrixGreen else Surface)
                .border(1.dp, if (checked) MatrixGreen else MatrixGreen.copy(alpha = 0.4f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(AppIcons.Done, contentDescription = null, tint = Black, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(text, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
    }
}

@Composable
internal fun JellyMediaRow(item: org.phioster.sanctumd.model.JellyMediaItem, config: ServiceConfig, accent: Color, onSetWatched: ((Boolean) -> Boolean)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(item.posterUrl, config, Modifier.width(46.dp).height(68.dp), RoundedCornerShape(4.dp), ContentScale.Crop)
            } else {
                Box(Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
            }
            WatchedMarker(item, accent, Modifier.align(Alignment.TopEnd), 14.dp, onSetWatched)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.isFolder) Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 18.sp)
    }
}

/**
 * A collapsible season / album section: the header row toggles its children open in place, so a
 * series' seasons (or an artist's albums) can be skimmed without drilling in and back out.
 *
 * [children] null means "still loading". The trailing `›` still opens the folder's own page. That's
 * where the batch actions live (download all, play album, rescan), so expanding doesn't cost them.
 */
@Composable
internal fun ExpandableFolderRow(
    folder: org.phioster.sanctumd.model.JellyMediaItem,
    children: List<org.phioster.sanctumd.model.JellyMediaItem>?,
    config: ServiceConfig,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenChild: (org.phioster.sanctumd.model.JellyMediaItem) -> Unit,
    onSetWatched: ((org.phioster.sanctumd.model.JellyMediaItem, Boolean) -> Boolean)? = null,
) {
    val album = folder.kind == "MusicAlbum"
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) AppIcons.Expanded else AppIcons.Collapsed,
                contentDescription = if (expanded) "collapse" else "expand",
                tint = accent, modifier = Modifier.width(18.dp).size(16.dp),
            )
            Box {
                if (folder.posterUrl.isNotBlank()) {
                    JellyPoster(folder.posterUrl, config, Modifier.width(40.dp).height(60.dp), RoundedCornerShape(4.dp), ContentScale.Crop)
                } else {
                    Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                }
                WatchedMarker(folder, accent, Modifier.align(Alignment.TopEnd), 14.dp, onSetWatched?.let { f -> { want: Boolean -> f(folder, want) } })
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = children?.let { "${it.size} ${if (album) "tracks" else "episodes"}" } ?: folder.subtitle
                if (sub.isNotBlank()) {
                    Text(sub, fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "›",
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 20.sp,
                modifier = Modifier.clickable { onOpenFolder() }.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 18.dp)) {
                when {
                    children == null -> Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    children.isEmpty() -> Text("empty", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    else -> children.forEach { c ->
                        // Inside a season the show name is noise, lead with the episode number instead.
                        val shown = if (!album && c.number != null) c.copy(name = "E%02d · %s".format(c.number, c.name), subtitle = "") else c
                        JellyMediaRow(shown, config, accent, onSetWatched?.let { f -> { want: Boolean -> f(c, want) } }) { onOpenChild(c) }
                    }
                }
            }
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }
}
