package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import org.phioster.sanctumd.model.WatchAvailability
import org.phioster.sanctumd.model.WatchProvider
import org.phioster.sanctumd.model.WatchProviderKind
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

/**
 * The regions the availability lookup can be pinned to, beyond the device's own.
 *
 * Not TMDB's full country list: a chip row is for picking, not for browsing the world. Anyone
 * whose country is missing is already served by the default, which follows the device.
 */
internal val WATCH_REGION_OPTIONS: List<String> = listOf(
    "DE", "AT", "CH", "US", "GB", "IE", "FR", "IT", "ES", "PT", "NL", "BE",
    "DK", "SE", "NO", "FI", "PL", "CZ", "CA", "AU", "NZ", "BR", "MX", "JP", "IN",
)

/**
 * "Where can I watch this?" — the streaming services a title is available on, the way
 * werstreamt.es answers it, for the region the user reads availability in.
 *
 * Draws **nothing** when the title streams nowhere in that region: an empty row would say
 * "unknown" where the honest answer is "nowhere", and a header with no logos under it is worse
 * than no header. The region is spelled out next to the heading, because the same title is on a
 * different set of services one country over.
 */
@Composable
internal fun WatchProviderSection(
    availability: WatchAvailability,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (availability.isEmpty) return
    val context = LocalContext.current
    val open = { if (availability.link.isNotBlank()) openInBrowser(context, availability.link) else Unit }
    Column(modifier.fillMaxWidth()) {
        SectionHeader(
            "STREAMING",
            accent = accent,
            trailing = {
                Text(
                    availability.region,
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            availability.providers.forEach { provider ->
                WatchProviderTile(provider, accent, open)
            }
        }
    }
}

/** One provider: its logo, its name, and — for paid offers — how it is on offer. */
@Composable
private fun WatchProviderTile(provider: WatchProvider, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).padding(end = 10.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (provider.logoUrl.isNotBlank()) {
            AsyncImage(
                model = provider.logoUrl,
                contentDescription = provider.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Surface),
            )
        } else {
            // No logo from TMDB — the initial keeps the row aligned instead of leaving a hole.
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Surface), Alignment.Center) {
                Text(
                    provider.name.take(1).uppercase(),
                    fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            provider.name,
            fontFamily = Mono, color = MatrixGreen, fontSize = 8.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        if (provider.kind != WatchProviderKind.STREAM) {
            Text(
                provider.kind.label,
                fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 8.sp, textAlign = TextAlign.Center,
            )
        }
    }
}
