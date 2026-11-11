package org.phioster.sanctumd.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.net.NextEpisode
import org.phioster.sanctumd.ui.jellyfin.JellyPoster
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

/** Subtitle line drawn by the app; above the control bar while that is showing. */
@Composable
internal fun TvSubtitle(text: String, raised: Boolean) {
    Box(
        Modifier.fillMaxSize().padding(start = TvSidePad, end = TvSidePad, bottom = if (raised) 210.dp else 56.dp),
        Alignment.BottomCenter,
    ) {
        Text(
            text.trim(),
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 8f)),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Black.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** Bottom-right pill like the big streaming apps; OK takes it, a tap on a phone too. */
@Composable
internal fun TvSkipPrompt(label: String, onTake: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = TvSidePad, vertical = 48.dp), Alignment.BottomEnd) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onTake() }
                .background(Black.copy(alpha = 0.8f))
                .border(2.dp, MatrixGreen, RoundedCornerShape(8.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = MatrixGreen, fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text("OK", color = MatrixGreen.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 14.sp)
        }
    }
}

/** Credits are running: what plays next, and in how many seconds. OK now, Back stays. */
@Composable
internal fun TvNextCard(next: NextEpisode, seconds: Int, config: ServiceConfig, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = TvSidePad, vertical = 48.dp), Alignment.BottomEnd) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onPlay() }
                .background(Black.copy(alpha = 0.85f))
                .border(2.dp, MatrixGreen, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JellyPoster(
                "${config.normalizedBaseUrl}Items/${next.id}/Images/Primary?maxHeight=240&quality=85",
                config,
                Modifier.width(192.dp).height(108.dp),
                RoundedCornerShape(6.dp),
                ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.width(300.dp)) {
                Text("next episode in $seconds", color = MatrixGreen.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    next.name, color = MatrixGreen, fontFamily = Mono, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (next.subtitle.isNotBlank()) {
                    Text(next.subtitle, color = MatrixGreen.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 13.sp, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                Text("OK play now · back stay", color = MatrixGreen.copy(alpha = 0.5f), fontFamily = Mono, fontSize = 12.sp)
            }
        }
    }
}
