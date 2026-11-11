package org.phioster.sanctumd.ui.tv

import org.phioster.sanctumd.net.MediaSegment

/** What the skip prompt does: jump past the intro, jump past the credits, or start the next episode. */
enum class SkipKind { INTRO, CREDITS, NEXT_EPISODE }

/** The skip on offer right now. [index] identifies the segment so a used offer stays gone. */
data class SkipOffer(val index: Int, val kind: SkipKind, val targetMs: Long)

/**
 * The skip to offer at [positionMs], or null. Credits with a following episode offer that episode
 * instead of the few seconds after them. That is what anyone pressing it wants.
 */
fun skipOffer(
    segments: List<MediaSegment>,
    positionMs: Long,
    skipped: Set<Int>,
    hasNextEpisode: Boolean,
): SkipOffer? {
    if (positionMs <= 0) return null
    segments.forEachIndexed { i, s ->
        if (i in skipped || positionMs !in s.startMs until s.endMs) return@forEachIndexed
        when {
            s.kind.equals("Intro", true) -> return SkipOffer(i, SkipKind.INTRO, s.endMs)
            s.kind.equals("Outro", true) -> return SkipOffer(
                i,
                if (hasNextEpisode) SkipKind.NEXT_EPISODE else SkipKind.CREDITS,
                s.endMs,
            )
        }
    }
    return null
}

fun skipLabel(kind: SkipKind): String = when (kind) {
    SkipKind.INTRO -> "skip intro"
    SkipKind.CREDITS -> "skip credits"
    SkipKind.NEXT_EPISODE -> "next episode"
}
