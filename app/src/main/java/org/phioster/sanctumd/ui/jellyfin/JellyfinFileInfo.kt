package org.phioster.sanctumd.ui.jellyfin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.model.JellyFileInfo
import org.phioster.sanctumd.model.JellyStream
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import kotlin.math.abs

// ---- Formatting ----

/**
 * Bytes as the largest unit that still reads at a glance; 0 means "the server didn't say".
 * Binary units, because that is what every other tool reports a media file's size in.
 */
internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

/** Bitrates stay in kbps until they get long enough to be worth reading as Mbps. */
internal fun formatBitrate(bps: Int): String = when {
    bps <= 0 -> ""
    bps < 1_000_000 -> "${bps / 1_000} kbps"
    else -> "%.1f Mbps".format(bps / 1_000_000.0)
}

/** Frame rates print as integers when they are one (25, 30) and to three places when they are not. */
internal fun formatFrameRate(fps: Double): String = when {
    fps <= 0.0 -> ""
    abs(fps - fps.toInt()) < 0.001 -> "${fps.toInt()} fps"
    else -> "%.3f fps".format(fps)
}

/**
 * The headline for one track — what you read before deciding whether to expand further.
 * Language first, because "is there a German audio track" is the question this answers.
 */
internal fun streamHeadline(s: JellyStream): String = buildList {
    if (s.language.isNotBlank()) add(s.language)
    if (s.codec.isNotBlank()) add(s.codec.uppercase())
    when (s.type) {
        "Video" -> if (s.width > 0 && s.height > 0) add("${s.width}x${s.height}")
        "Audio" -> if (s.channelLayout.isNotBlank()) add(s.channelLayout) else if (s.channels > 0) add("${s.channels}ch")
    }
}.joinToString(" · ").ifBlank { s.type }

/** Every remaining detail Jellyfin gave us, as label/value rows. Absent values are dropped. */
internal fun streamDetails(s: JellyStream, assumedLanguage: String = ""): List<Pair<String, String>> = buildList {
    // An untagged track can borrow the film's original language, but it stays labelled: it is an
    // inference about the film, not something read out of this file. Unlabelled it would be
    // indistinguishable from a real tag, and a dual-language rip would be quietly mislabelled.
    if (s.type == "Audio" && s.language.isBlank() && assumedLanguage.isNotBlank()) {
        add("language" to "$assumedLanguage (assumed)")
    }
    if (s.profile.isNotBlank()) add("profile" to s.profile)
    // Jellyfin reports VideoRange on audio streams too, as "Unknown". Printing it put a
    // meaningless `range Unknown` row under every audio track.
    if (s.type == "Video" && s.videoRange.isNotBlank() && !s.videoRange.equals("Unknown", true)) {
        add("range" to s.videoRange)
    }
    formatFrameRate(s.frameRate).takeIf { it.isNotBlank() }?.let { add("framerate" to it) }
    if (s.bitDepth > 0) add("bit depth" to "${s.bitDepth} bit")
    if (s.sampleRate > 0) add("sample rate" to "${s.sampleRate} Hz")
    if (s.channels > 0) add("channels" to s.channels.toString())
    formatBitrate(s.bitrate).takeIf { it.isNotBlank() }?.let { add("bitrate" to it) }
    if (s.displayTitle.isNotBlank()) add("title" to s.displayTitle)
    val flags = buildList {
        if (s.isDefault) add("default")
        if (s.isForced) add("forced")
        if (s.isExternal) add("external")
    }
    if (flags.isNotEmpty()) add("flags" to flags.joinToString(", "))
}

// ---- UI ----

/**
 * The FILE section of the detail sheet: collapsed to a single tappable header, expanded to
 * everything the server knows about the file. Collapsed by default — this is reference
 * information, not something you read on every visit.
 */
@Composable
internal fun FileInfoSection(info: JellyFileInfo, accent: Color, assumedLanguage: String = "") {
    var expanded by remember { mutableStateOf(false) }

    // SectionHeader is itself a fillMaxWidth Row and offers a trailing slot for exactly this.
    // Nesting it inside another Row leaves the siblings no width, and an unbounded Text then
    // wraps to one character per line and inflates the row to a screen-high empty block.
    MediaSectionHeader(
        "FILE",
        accent,
        Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp),
    ) {
        if (!expanded) {
            Text(
                listOfNotNull(
                    info.container.uppercase().ifBlank { null },
                    formatFileSize(info.sizeBytes).ifBlank { null },
                ).joinToString(" · "),
                fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (expanded) "▾" else "▸",
            fontFamily = Mono, color = accent.copy(alpha = 0.8f), fontSize = 11.sp,
        )
    }

    if (!expanded) return

    Spacer(Modifier.height(6.dp))
    InfoRows(
        buildList {
            if (info.container.isNotBlank()) add("container" to info.container)
            formatFileSize(info.sizeBytes).takeIf { it.isNotBlank() }?.let { add("size" to it) }
            formatBitrate(info.bitrate).takeIf { it.isNotBlank() }?.let { add("bitrate" to it) }
        },
    )
    if (info.path.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text("path", fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp)
        // The path is regularly wider than the screen; scrolling beats truncating it away.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(info.path, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
        }
    }

    listOf("Video" to "VIDEO", "Audio" to "AUDIO", "Subtitle" to "SUBTITLES").forEach { (type, label) ->
        val tracks = info.streams.filter { it.type == type }
        if (tracks.isEmpty()) return@forEach
        Spacer(Modifier.height(12.dp))
        Text(label, fontFamily = Mono, color = accent.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        tracks.forEach { s ->
            Spacer(Modifier.height(6.dp))
            Text(streamHeadline(s), fontFamily = Mono, color = MatrixGreen, fontSize = 12.sp)
            InfoRows(streamDetails(s, assumedLanguage), indent = 10.dp)
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>, indent: androidx.compose.ui.unit.Dp = 0.dp) {
    Column(Modifier.padding(start = indent)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(label, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.width(90.dp))
                Text(value, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 10.sp)
            }
        }
    }
}
