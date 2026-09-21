package org.phioster.sanctumd.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.phioster.sanctumd.net.NextEpisode
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/** The little overlay that a brightness or volume swipe puts in the middle of the picture. */
@Composable
internal fun BoxScope.SwipeHud(isBrightness: Boolean, value: Float) {
        Row(
            Modifier.align(Alignment.Center)
                .background(Color(0xB3000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (isBright) Icons.Filled.BrightnessMedium else AppIcons.Audio,
                contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(22.dp),
            )
            Text("${(value * 100).roundToInt()}%", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** The jump marker that a double tap leaves at the left or right edge. */
@Composable
internal fun BoxScope.SeekHud(direction: Int) {
        Text(
            if (direction > 0) "»  +10s" else "«  −10s",
            fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(if (direction > 0) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 40.dp)
                .background(Color(0xB3000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
}

/** Offered while playback sits inside an intro or outro the server marked. */
@Composable
internal fun BoxScope.SkipSegmentButton(isOutro: Boolean, bottomPadding: Dp, onSkip: () -> Unit) {
        Text(
            if (isOutro) "skip outro  »" else "skip intro  »",
            fontFamily = Mono, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomPadding)
                .clip(RoundedCornerShape(8.dp))
                .background(MatrixGreen)
                .clickable(onClick = onSkip)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
}

/** The card that offers the next episode, with its countdown when one is running. */
@Composable
internal fun BoxScope.NextEpisodeCard(
    next: NextEpisode,
    countdown: Int,
    bottomPadding: Dp,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomPadding)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE6000000))
                .clickable(onClick = onPlay)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                if (countdown > 0) "NEXT IN ${countdown}s" else "NEXT EPISODE",
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(next.name, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (next.subtitle.isNotBlank()) {
                Text(next.subtitle, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(MatrixGreen).clickable(onClick = onPlay)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Play, contentDescription = null, tint = Black, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("play now", fontFamily = Mono, color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text("dismiss", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
}

/** Asked once when the server remembers a position: carry on there, or start from the top. */
@Composable
internal fun BoxScope.ResumePrompt(label: String, onResume: () -> Unit, onStartOver: () -> Unit) {
        Column(
            Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF0000000))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("continue watching?", fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row {
                Text("resume $label", fontFamily = Mono, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(MatrixGreen)
                        .clickable(onClick = onResume)
                        .padding(horizontal = 14.dp, vertical = 8.dp))
                Spacer(Modifier.width(10.dp))
                Text("start over", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0x33FFFFFF))
                        .clickable(onClick = onStartOver)
                        .padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
}
