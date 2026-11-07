package org.phioster.sanctumd.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.net.PlayerChapter
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/** The chapter [positionMs] falls in, or null before the first one starts. */
internal fun chapterAt(chapters: List<PlayerChapter>, positionMs: Long): PlayerChapter? =
    chapters.lastOrNull { it.startMs <= positionMs }

/** Right-side list of the item's chapters; the current one is marked, a tap jumps there. */
@Composable
internal fun ChaptersPanel(
    chapters: List<PlayerChapter>,
    positionMs: Long,
    fmt: (Long) -> String,
    onPick: (PlayerChapter) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }
    val current = chapterAt(chapters, positionMs)
    Column(
        modifier.fillMaxHeight().width(300.dp).background(Color(0xF0000000))
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("CHAPTERS", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp))
        chapters.forEach { c ->
            val on = c == current
            Row(
                Modifier.fillMaxWidth()
                    .background(if (on) MatrixGreen.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onPick(c) }.padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmt(c.startMs), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.width(64.dp))
                Text(c.name, fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        Text("close", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp,
            modifier = Modifier.clickable { onClose() }.padding(vertical = 10.dp))
    }
}
