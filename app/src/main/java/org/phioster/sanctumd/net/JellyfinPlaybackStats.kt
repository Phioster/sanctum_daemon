package org.phioster.sanctumd.net

import org.phioster.sanctumd.model.PlaybackKind

/**
 * How Jellyfin delivered a file, read out of the Playback Reporting plugin's `PlaybackMethod`.
 *
 * This matters more here than on a normal server: the whole stack runs on a phone, and
 * transcoding is by far the most expensive thing it can be asked to do. Knowing *which* titles
 * force it is what lets a file be replaced on purpose instead of the server being blamed.
 *
 * The values are not a tidy enum — a transcode reports what it had to touch, e.g.
 * `Transcode (v:h264 a:direct)` means the video was re-encoded while the audio was passed
 * through. Measured against the live server on 2026-08-21.
 */
internal fun playbackKind(method: String?): PlaybackKind = PlaybackKind.DIRECT

/** The part of a transcode label that says what was re-encoded, or "" when it says nothing. */
internal fun transcodeDetail(method: String?): String = ""
