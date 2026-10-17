package org.phioster.sanctumd.notify

/**
 * ntfy's tag shortcodes that stand for an emoji, the common part of its list. A tag that is one
 * of these shows as the emoji in front of the title, like the ntfy app does; any other tag is a
 * plain label.
 */
private val TAG_EMOJI = mapOf(
    "+1" to "👍", "-1" to "👎", "warning" to "⚠️", "rotating_light" to "🚨",
    "no_entry" to "⛔", "no_entry_sign" to "🚫", "x" to "❌", "heavy_check_mark" to "✔️",
    "white_check_mark" to "✅", "check" to "✅", "tada" to "🎉", "partying_face" to "🥳",
    "skull" to "💀", "fire" to "🔥", "bell" to "🔔", "loudspeaker" to "📢",
    "information_source" to "ℹ️", "question" to "❓", "exclamation" to "❗", "bangbang" to "‼️",
    "bug" to "🐛", "computer" to "💻", "floppy_disk" to "💾", "cd" to "💿",
    "dvd" to "📀", "movie_camera" to "🎥", "clapper" to "🎬", "film_strip" to "🎞️",
    "tv" to "📺", "popcorn" to "🍿", "musical_note" to "🎵", "headphones" to "🎧",
    "inbox_tray" to "📥", "outbox_tray" to "📤", "package" to "📦", "arrow_down" to "⬇️",
    "arrow_up" to "⬆️", "hourglass" to "⌛", "stopwatch" to "⏱️", "alarm_clock" to "⏰",
    "calendar" to "📅", "lock" to "🔒", "unlock" to "🔓", "key" to "🔑",
    "zap" to "⚡", "battery" to "🔋", "electric_plug" to "🔌", "wrench" to "🔧",
    "hammer" to "🔨", "gear" to "⚙️", "rocket" to "🚀", "sparkles" to "✨",
    "star" to "⭐", "heart" to "❤️", "eyes" to "👀", "ghost" to "👻",
    "red_circle" to "🔴", "green_circle" to "🟢", "yellow_circle" to "🟡", "large_blue_circle" to "🔵",
    "cloud" to "☁️", "sunny" to "☀️", "house" to "🏠", "globe_with_meridians" to "🌐",
    "mailbox" to "📫", "email" to "📧", "speech_balloon" to "💬", "trophy" to "🏆",
)

/** The emojis of the tags that have one, in order, e.g. "⚠️🔥 ". Blank when none. */
fun ntfyTagEmoji(tags: List<String>): String =
    tags.mapNotNull { TAG_EMOJI[it.trim().lowercase()] }.joinToString("").let { if (it.isEmpty()) "" else "$it " }

/** The tags that are not emojis; ntfy shows these as labels under the text. */
fun ntfyPlainTags(tags: List<String>): List<String> =
    tags.map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() !in TAG_EMOJI }

private val MD_LINK = Regex("""\[([^\]]+)]\(([^)\s]+)\)""")
private val MD_BOLD = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
private val MD_ITALIC = Regex("""(?<![*\w])[*_](?![*_\s])(.+?)(?<![*_\s])[*_](?![*\w])""")
private val MD_CODE = Regex("""`([^`]+)`""")
private val MD_HEADING = Regex("""(?m)^#{1,6}\s+""")
private val MD_BULLET = Regex("""(?m)^\s*[-*]\s+""")

/** Markdown as plain text, for the notification shade, which can't render it. */
fun stripMarkdown(s: String): String = s
    .replace(MD_LINK) { it.groupValues[1] }
    .replace(MD_BOLD) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
    .replace(MD_CODE) { it.groupValues[1] }
    .replace(MD_ITALIC) { it.groupValues[1] }
    .replace(MD_HEADING, "")
    .replace(MD_BULLET, "• ")
