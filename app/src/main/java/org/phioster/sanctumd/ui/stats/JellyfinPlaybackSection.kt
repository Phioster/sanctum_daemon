package org.phioster.sanctumd.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.JellyPlayEntry
import org.phioster.sanctumd.model.JellyPlaybackStats
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/**
 * What was actually watched, and what it cost the server.
 *
 * The library counts elsewhere say what exists; this says what gets used. The transcoding block
 * is the reason it is worth having: every transcode is the phone re-encoding video on the fly,
 * and naming the titles turns "the server is slow sometimes" into a list of files to replace.
 */
@Composable
internal fun JellyfinPlaybackSection(
    stats: JellyPlaybackStats,
    accent: Color,
    onOpenItem: (String) -> Unit,
) {
    if (stats.plays == 0) {
        Text(
            "no playback recorded yet",
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp,
        )
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("%.0f".format(stats.hours), "STUNDEN", accent, Modifier.weight(1f))
        Tile(stats.plays.toString(), "WIEDERGABEN", accent, Modifier.weight(1f))
        Tile(
            stats.transcodes.toString(),
            "TRANSKODIERT",
            // The one number worth being unhappy about, so it is coloured when it isn't zero.
            if (stats.transcodes > 0) Color(0xFFFFAA00) else accent,
            Modifier.weight(1f),
        )
    }
    if (stats.since.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text("seit ${stats.since}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 10.sp)
    }

    Bars("MEISTGESEHEN", stats.topTitles, accent, showHours = true, onOpenItem = onOpenItem)
    Bars("GERÄTE", stats.devices, accent, showHours = true, onOpenItem = null)

    if (stats.forced.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Text("ERZWINGT TRANSKODIERUNG", fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "Diese Titel muss der Server beim Abspielen umrechnen — die teuerste Arbeit, die er kennt.",
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        stats.forced.forEach { e ->
            Column(
                Modifier.fillMaxWidth()
                    .let { m -> if (e.itemId.isNotBlank()) m.clickable { onOpenItem(e.itemId) } else m }
                    .padding(vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.label, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text("${e.plays}×", fontFamily = Mono, color = Color(0xFFFFAA00), fontSize = 11.sp)
                }
                if (e.detail.isNotBlank()) {
                    Text(e.detail, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.45f), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.08f)).padding(10.dp),
    ) {
        Text(value, fontFamily = Mono, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 9.sp)
    }
}

/** A row of labelled bars, each scaled against the largest value in its own group. */
@Composable
private fun Bars(
    title: String,
    entries: List<JellyPlayEntry>,
    accent: Color,
    showHours: Boolean,
    onOpenItem: ((String) -> Unit)?,
) {
    if (entries.isEmpty()) return
    val max = entries.maxOf { it.hours }.coerceAtLeast(0.01)
    Spacer(Modifier.height(18.dp))
    Text(title, fontFamily = Mono, color = accent, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
    entries.forEach { e ->
        Column(
            Modifier.fillMaxWidth()
                .let { m -> if (onOpenItem != null && e.itemId.isNotBlank()) m.clickable { onOpenItem(e.itemId) } else m }
                .padding(vertical = 5.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    e.label, fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (showHours) "%.1f h".format(e.hours) else "${e.plays}×",
                    fontFamily = Mono, color = accent.copy(alpha = 0.9f), fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MatrixGreen.copy(alpha = 0.1f))) {
                Box(
                    Modifier.fillMaxWidth((e.hours / max).toFloat().coerceIn(0.02f, 1f))
                        .height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent),
                )
            }
        }
    }
}
