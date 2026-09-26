package org.phioster.sanctumd.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.ui.jellyfin.JellyPoster
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.SurfaceHi

/**
 * Shared TV widgets.
 *
 * Everything here is built on plain Compose focus rather than `androidx.tv:tv-material`: the app
 * already paints itself from [org.phioster.sanctumd.ui.theme.ThemeState], so a second Material
 * theme would only fight it. `Modifier.clickable` is focusable and fires on the D-pad centre key,
 * which is all a ten-foot UI needs. Plus a visible focus state, which is the part that must never
 * be subtle. On a TV you cannot see a cursor, so the focused element has to be unmistakable.
 */

/** Overscan margin. TVs crop the panel edges; nothing important may live inside this. */
val TvSidePad = 44.dp
val TvTopPad = 28.dp

/** Focus ring + lift shared by every focusable TV surface. */
@Composable
internal fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    focusRequester: FocusRequester? = null,
    scaleUp: Float = 1.06f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleUp else 1f, tween(120), label = "tvFocusScale")
    Box(
        modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            // The scale goes *after* the focus target, and that ordering is the whole trick.
            //
            // Modifiers apply outside-in, so a scale placed first puts the focusable inside the
            // scaled layer, and the rectangle `bringIntoView` then asks the enclosing list to
            // reveal is the *grown* one, which no longer fits the row. The list dutifully scrolled
            // to accommodate it, and since the scale animates on every focus change, moving sideways
            // made the whole page bob up and down. Placed here, the focus node keeps its true
            // bounds and only the drawing grows.
            .scale(scale)
            .clip(shape)
            .border(2.dp, if (focused) MatrixGreen else Color.Transparent, shape),
        // Explicit receiver: `content` is a BoxScope extension, so the Box's own scope has to be
        // handed to it for `Modifier.align` to resolve inside callers.
        content = { this.content(focused) },
    )
}

/** Primary action button, filled when focused, outlined otherwise. */
@Composable
internal fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) MatrixGreen else Color.Transparent)
            .border(1.dp, MatrixGreen.copy(alpha = if (focused) 1f else 0.45f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.let {
                Icon(it, contentDescription = null, tint = if (focused) Black else MatrixGreen, modifier = Modifier.size(17.dp))
            }
            Text(
                text,
                color = if (focused) Black else MatrixGreen,
                fontFamily = Mono,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A full-width row in a list of choices (servers, tracks, settings). */
@Composable
internal fun TvListRow(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    trailing: String = "",
    onClick: () -> Unit,
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        scaleUp = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .background(if (focused) SurfaceHi else Surface)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MatrixGreen, fontFamily = Mono, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = MatrixGreen.copy(alpha = 0.55f),
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing.isNotBlank()) {
                Text(trailing, color = MatrixGreen.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 13.sp)
            }
        }
    }
}

/** Section heading above a row or grid. */
@Composable
internal fun TvSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = MatrixGreen,
        fontFamily = Mono,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
    )
}

/**
 * Poster tile. [width] drives the whole card; posters are 2:3, the way Jellyfin serves them.
 *
 * Two things here exist to keep focus movement calm, and both matter more than they look:
 *
 * The card has a **fixed height**. The caption always reserves two lines, even when the item has no
 * subtitle. Cards of differing heights make `bringIntoView` request a differently-sized rectangle for
 * each neighbour, and the enclosing list then nudges itself vertically every time you move sideways.
 *
 * The **focus ring is drawn inside a padded box**. The scale-up is a draw effect that spills past the
 * layout bounds, and a list clips its items. So at the top of the screen the ring was being sliced
 * off by the bar above it. The padding gives the growth somewhere to go.
 */
@Composable
internal fun TvPosterCard(
    item: JellyMediaItem,
    config: ServiceConfig,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val posterHeight = width * 1.5f
    Column(modifier.width(width).padding(vertical = 10.dp)) {
        TvFocusSurface(
            onClick = onClick,
            modifier = Modifier.width(width).height(posterHeight),
            shape = RoundedCornerShape(8.dp),
            focusRequester = focusRequester,
        ) { _ ->
            if (item.posterUrl.isNotBlank()) {
                JellyPoster(
                    item.posterUrl,
                    config,
                    Modifier.width(width).height(posterHeight),
                    RoundedCornerShape(8.dp),
                    ContentScale.Crop,
                )
            } else {
                Box(Modifier.width(width).height(posterHeight).background(Surface), Alignment.Center) {
                    Text(
                        item.name.take(2).uppercase(),
                        color = MatrixGreen.copy(alpha = 0.5f),
                        fontFamily = Mono,
                        fontSize = 26.sp,
                    )
                }
            }
            // Resume bar, so a half-watched episode is recognisable from the sofa.
            if (item.progressPct > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Black.copy(alpha = 0.6f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progressPct.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(MatrixGreen),
                    )
                }
            }
            if (item.played) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
                        .clip(RoundedCornerShape(9.dp)).background(MatrixGreen),
                    Alignment.Center,
                ) { Icon(AppIcons.Done, contentDescription = "watched", tint = Black, modifier = Modifier.size(12.dp)) }
            } else if (item.unplayedCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .clip(RoundedCornerShape(9.dp)).background(MatrixGreen)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (item.unplayedCount > 99) "99+" else item.unplayedCount.toString(),
                        color = Black, fontFamily = Mono, fontSize = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            color = MatrixGreen,
            fontFamily = Mono,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Always rendered, blank or not: an absent second line would make this card shorter than its
        // neighbours and set the list jittering vertically as focus moves along the row.
        Text(
            item.subtitle,
            color = MatrixGreen.copy(alpha = 0.5f),
            fontFamily = Mono,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A titled horizontal shelf of posters. Empty lists render nothing at all. */
@Composable
internal fun TvPosterRow(
    title: String,
    items: List<JellyMediaItem>,
    config: ServiceConfig,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 148.dp,
    firstItemFocus: FocusRequester? = null,
    onClick: (JellyMediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        TvSectionTitle(title, Modifier.padding(start = TvSidePad, bottom = 10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvSidePad),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                TvPosterCard(
                    item = item,
                    config = config,
                    width = cardWidth,
                    focusRequester = firstItemFocus?.takeIf { index == 0 },
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

/** Centred status text for loading / empty / error states. */
@Composable
internal fun TvMessage(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Box(modifier.fillMaxWidth().padding(TvSidePad), Alignment.Center) {
        Text(
            text,
            color = if (error) org.phioster.sanctumd.ui.theme.ErrRed else MatrixGreen.copy(alpha = 0.65f),
            fontFamily = Mono,
            fontSize = 15.sp,
        )
    }
}
