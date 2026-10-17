package org.phioster.sanctumd.ui.ntfy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.phioster.sanctumd.model.NtfyMessage
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.notify.ntfyPlainTags
import org.phioster.sanctumd.notify.ntfyTagEmoji
import org.phioster.sanctumd.notify.sameServer
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface

private val TOKEN = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\([^)\s]+\)|(?<![*\w])\*(?![*\s])(.+?)(?<![*\s])\*(?![*\w])""")

/** The Markdown a webhook sends, as styled text: bold, italic, code, and links by their label. */
internal fun markdownAnnotated(s: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    TOKEN.findAll(s).forEach { m ->
        append(s.substring(at, m.range.first))
        val (bold, code, link, italic) = m.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x2233FF66))) { append(code) }
            link.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(link) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
        }
        at = m.range.last + 1
    }
    append(s.substring(at))
}

/** One message in the topic list: emoji title, text, labels and the picture it carries. */
@Composable
internal fun NtfyMessageRow(m: NtfyMessage, config: ServiceConfig, accent: Color) {
    val time = if (m.time > 0) java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(m.time * 1000)) else ""
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Emoji in the system font: the mono font has none of them.
            val emoji = ntfyTagEmoji(m.tags)
            if (emoji.isNotEmpty()) Text(emoji, fontSize = 13.sp)
            Text(m.title.ifBlank { m.topic }, fontFamily = Mono, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(time, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp)
        }
        if (m.markdown) Text(markdownAnnotated(m.text), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
        else Text(m.text, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.85f), fontSize = 12.sp)
        val labels = ntfyPlainTags(m.tags)
        if (labels.isNotEmpty()) Text(labels.joinToString(" ") { "#$it" }, fontFamily = Mono, color = accent.copy(alpha = 0.6f), fontSize = 10.sp)
        if (m.imageUrl.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            val req = ImageRequest.Builder(context).data(m.imageUrl).apply {
                // The token goes only to the ntfy server itself, never to a URL a message names.
                if (config.apiKey.isNotBlank() && sameServer(m.imageUrl, config.normalizedBaseUrl)) addHeader("Authorization", "Bearer ${config.apiKey}")
            }.build()
            AsyncImage(
                model = req, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(6.dp)).background(Surface),
            )
        }
    }
}
