package org.phioster.sanctumd.ui.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.util.Locale

/**
 * libmpv implementation of [MediaPlayerEngine]. Plays essentially any codec/container the file uses
 * (no server transcode) and renders image/ASS subtitles — the "plays everything" engine. Uses the
 * prebuilt `dev.jdtech.mpv:libmpv` AAR; created behind a runCatching in [PlayerScreen] so a missing
 * native lib falls back to ExoPlayer.
 */
class MpvPlayerEngine(
    context: Context,
    tvTuning: Boolean = false,
    /**
     * Hand decoded frames straight to the Android surface instead of copying them out of the
     * decoder and back through the GPU. On a TV stick that copy is the single most expensive thing
     * in the pipeline. The cost of avoiding it: mpv no longer composites, so **subtitles and the
     * on-screen display are not drawn**.
     */
    private val directOutput: Boolean = false,
) : MediaPlayerEngine {

    /** The video output mpv should use once a surface exists. */
    private val videoOut = if (directOutput) "mediacodec_embed" else "gpu"

    private val mpv: MPVLib = MPVLib.create(context) ?: error("libmpv create failed")

    @Volatile private var released = false
    @Volatile private var posSec = 0.0
    @Volatile private var durSec = 0.0
    @Volatile private var paused = false
    @Volatile private var buffering = false
    @Volatile private var eof = false
    private var lastError: String? = null

    private val observer = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {}
        override fun eventProperty(property: String, value: Double) {
            when (property) { "time-pos" -> posSec = value; "duration" -> durSec = value }
        }
        override fun eventProperty(property: String, value: Boolean) {
            when (property) {
                "pause" -> paused = value
                "paused-for-cache" -> buffering = value
                "eof-reached" -> eof = value
            }
        }
        override fun eventProperty(property: String, value: String) {}
        override fun event(eventId: Int) {}
    }

    init {
        // mpv has no system CA store on Android; copy the bundled Mozilla CA bundle to a real path so
        // TLS can be verified properly (instead of tls-verify=no).
        val caFile = File(context.filesDir, "cacert.pem")
        runCatching {
            context.assets.open("cacert.pem").use { input -> caFile.outputStream().use { input.copyTo(it) } }
        }
        with(mpv) {
            setOptionString("config", "no")
            setOptionString("vo", videoOut)
            setOptionString("gpu-context", "android")
            setOptionString("opengl-es", "yes")
            when {
                // Zero-copy: MediaCodec renders into the surface itself, nothing round-trips through
                // the GPU. Measured on the target stick, the copy path below dropped frames even on
                // a 1 Mbit/s H.264 file and produced coloured noise for HEVC — so on a television
                // this is the path that actually works, at the price of mpv-drawn subtitles.
                directOutput -> setOptionString("hwdec", "mediacodec")

                // GPU path on weak hardware: decode through MediaCodec explicitly rather than
                // letting `auto` fall back to software, and strip every optional rendering nicety.
                // None of them are visible from a sofa.
                tvTuning -> {
                    setOptionString("hwdec", "mediacodec-copy")
                    setOptionString("profile", "fast")
                    setOptionString("scale", "bilinear")
                    setOptionString("dscale", "bilinear")
                    setOptionString("cscale", "bilinear")
                    setOptionString("dither", "no")
                    setOptionString("deband", "no")
                    setOptionString("interpolation", "no")
                    setOptionString("correct-downscaling", "no")
                    setOptionString("sigmoid-upscaling", "no")
                }

                else -> {
                    setOptionString("hwdec", "auto")
                    setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
                }
            }
            if (tvTuning) {
                // Applies to both television paths: a stick's wifi is the other half of the problem,
                // so buffer generously enough that a dip in throughput never becomes a visible stall.
                setOptionString("video-sync", "audio")
                setOptionString("demuxer-max-bytes", "64MiB")
                setOptionString("demuxer-readahead-secs", "20")
                setOptionString("cache-secs", "30")
            }
            setOptionString("ao", "audiotrack,opensles")
            // Always verify. If the bundle could not be written, mpv has no CA store and TLS
            // simply fails — the user gets a playback error. The old fallback turned verification
            // OFF instead and said nothing, which is the one outcome nobody would have chosen:
            // the stream carries auth headers, so a silent downgrade hands them to anyone on path.
            setOptionString("tls-verify", "yes")
            if (caFile.exists() && caFile.length() > 0) {
                setOptionString("tls-ca-file", caFile.absolutePath)
            }
            setOptionString("cache", "yes")
            setOptionString("force-window", "no")
            setOptionString("idle", "yes")
            setOptionString("keep-open", "always")
            init()
            addObserver(observer)
            observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        }
    }

    override fun prepare(url: String, isHls: Boolean, startPositionMs: Long, headers: Map<String, String>) {
        if (released) return
        lastError = null; eof = false; posSec = 0.0; durSec = 0.0
        // Each header goes in on its own, through the list option's `-append` form. The plain
        // option is a comma-separated list and a Jellyfin Authorization header is made of commas;
        // appending hands the value over whole. See [headerLines].
        mpv.setOptionString("http-header-fields-clr", "")
        headerLines(headers).forEach { mpv.setOptionString("http-header-fields-append", it) }
        // The resume position goes in as the `start` option, NOT as a loadfile argument: since mpv
        // 0.38 the third loadfile parameter is the playlist *index* (an integer), so the old
        // `loadfile <url> replace start=120` failed to parse and the file never loaded at all — i.e.
        // every partially-watched item refused to play (and the dead player then reported position 0
        // to Jellyfin, which wiped its resume point). The bundled libmpv 1.0.0 is mpv 0.41.
        // Always set it explicitly so a previous file's value can't leak into the next one.
        val startSec = startPositionMs / 1000.0
        mpv.setOptionString("start", if (startSec > 0) "$startSec" else "none")
        mpv.command(arrayOf("loadfile", url, "replace"))
        mpv.setPropertyBoolean("pause", false)
    }

    override fun play() { if (!released) mpv.setPropertyBoolean("pause", false) }
    override fun pause() { if (!released) mpv.setPropertyBoolean("pause", true) }
    override fun togglePlay() { if (!released) mpv.command(arrayOf("cycle", "pause")) }
    override fun seekTo(positionMs: Long) { if (!released) mpv.command(arrayOf("seek", "${positionMs / 1000.0}", "absolute")) }
    override fun seekBy(deltaMs: Long) { if (!released) mpv.command(arrayOf("seek", "${deltaMs / 1000.0}", "relative")) }

    override fun snapshot(): PlaybackState = PlaybackState(
        positionMs = (posSec * 1000).toLong().coerceAtLeast(0),
        durationMs = (durSec * 1000).toLong().coerceAtLeast(0),
        bufferedMs = 0,
        isPlaying = !paused && !eof,
        isBuffering = buffering,
        ended = eof,
        error = lastError,
    )

    override fun videoAspect(): Float {
        if (released) return 0f
        return displayAspect(
            reported = (mpv.getPropertyDouble("video-params/aspect") ?: 0.0).toFloat(),
            displayWidth = mpv.getPropertyInt("video-params/dw") ?: mpv.getPropertyInt("dwidth") ?: 0,
            displayHeight = mpv.getPropertyInt("video-params/dh") ?: mpv.getPropertyInt("dheight") ?: 0,
        )
    }

    override fun stats(): PlaybackStats {
        if (released) return PlaybackStats()
        return PlaybackStats(
            width = mpv.getPropertyInt("video-params/w") ?: mpv.getPropertyInt("dwidth") ?: 0,
            height = mpv.getPropertyInt("video-params/h") ?: mpv.getPropertyInt("dheight") ?: 0,
            videoCodec = (mpv.getPropertyString("video-format") ?: "").lowercase(),
            audioCodec = (mpv.getPropertyString("audio-codec-name") ?: "").lowercase(),
            bitrateKbps = ((mpv.getPropertyDouble("video-bitrate") ?: 0.0) / 1000).toInt().coerceAtLeast(0),
            fps = (mpv.getPropertyDouble("estimated-vf-fps") ?: mpv.getPropertyDouble("container-fps") ?: 0.0).toFloat(),
            bufferedPercent = (mpv.getPropertyInt("cache-buffering-state") ?: 0).coerceIn(0, 100),
            hwDecode = (mpv.getPropertyString("hwdec-current") ?: "").takeUnless { it == "no" }.orEmpty(),
            droppedFrames = mpv.getPropertyInt("frame-drop-count") ?: 0,
            delayedFrames = mpv.getPropertyInt("vo-delayed-frame-count") ?: 0,
            containerFps = (mpv.getPropertyDouble("container-fps") ?: 0.0).toFloat(),
        )
    }

    private fun typeName(kind: TrackKind) = if (kind == TrackKind.AUDIO) "audio" else "sub"

    override fun tracks(kind: TrackKind): List<TrackOption> {
        if (released) return emptyList()
        val want = typeName(kind)
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val out = mutableListOf<TrackOption>()
        for (i in 0 until count) {
            if (mpv.getPropertyString("track-list/$i/type") != want) continue
            val id = mpv.getPropertyInt("track-list/$i/id") ?: continue
            val title = mpv.getPropertyString("track-list/$i/title")
            val lang = mpv.getPropertyString("track-list/$i/lang")
            val selected = mpv.getPropertyBoolean("track-list/$i/selected") ?: false
            val label = title
                ?: lang?.let { runCatching { Locale(it).displayLanguage.ifBlank { it } }.getOrDefault(it) }
                ?: "track $id"
            out += TrackOption(id.toString(), label, selected)
        }
        return out
    }

    override fun selectTrack(kind: TrackKind, id: String?) {
        if (released) return
        val prop = if (kind == TrackKind.AUDIO) "aid" else "sid"
        mpv.setPropertyString(prop, id ?: "no")
    }

    /** Track ids of [kind] whose language or title matches [langs], with their forced flag. */
    private fun matches(kind: TrackKind, langs: Set<String>): List<Pair<Int, Boolean>> {
        val want = typeName(kind)
        val count = mpv.getPropertyInt("track-list/count") ?: 0
        val out = mutableListOf<Pair<Int, Boolean>>()
        for (i in 0 until count) {
            if (mpv.getPropertyString("track-list/$i/type") != want) continue
            val id = mpv.getPropertyInt("track-list/$i/id") ?: continue
            val lang = mpv.getPropertyString("track-list/$i/lang")?.lowercase()
            val title = mpv.getPropertyString("track-list/$i/title")?.lowercase().orEmpty()
            if (lang !in langs && langs.none { title.contains(it) }) continue
            val forced = (mpv.getPropertyBoolean("track-list/$i/forced") ?: false) ||
                title.contains("forced") || title.contains("erzwungen")
            out += id to forced
        }
        return out
    }

    override fun applyTrackPreferences(audioLang: String, subLang: String, subMode: String) {
        if (released) return
        if (audioLang.isNotBlank()) {
            matches(TrackKind.AUDIO, languageAliases(audioLang)).firstOrNull()?.let {
                mpv.setPropertyString("aid", it.first.toString())
            }
        }
        if (subMode == "off" || subLang.isBlank()) {
            mpv.setPropertyString("sid", "no")
            return
        }
        val subs = matches(TrackKind.SUBTITLE, languageAliases(subLang))
        val pick = subs.firstOrNull { it.second } ?: subs.firstOrNull().takeIf { subMode == "any" }
        // No match in "forced" mode means subtitles stay off on purpose — otherwise the file's own
        // default track would show up, which is exactly what the preference exists to prevent.
        mpv.setPropertyString("sid", pick?.first?.toString() ?: "no")
    }

    override fun setSubtitleScale(scale: Float) { if (!released) mpv.setPropertyDouble("sub-scale", scale.toDouble()) }
    override fun setSubtitleDelay(delayMs: Long) { if (!released) mpv.setPropertyDouble("sub-delay", delayMs / 1000.0) }

    override fun setSpeed(speed: Float) { if (!released) mpv.setPropertyDouble("speed", speed.toDouble()) }
    override fun currentSpeed(): Float = if (released) 1f else (mpv.getPropertyDouble("speed")?.toFloat() ?: 1f)

    override fun release() {
        if (released) return
        released = true
        runCatching { mpv.removeObserver(observer) }
        runCatching { mpv.destroy() }
    }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            if (released) return
                            mpv.attachSurface(h.surface)
                            mpv.setOptionString("force-window", "yes")
                            mpv.setOptionString("vo", videoOut)
                        }
                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
                            if (!released) mpv.setPropertyString("android-surface-size", "${width}x$height")
                        }
                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            if (released) return
                            mpv.setPropertyString("vo", "null")
                            mpv.setOptionString("force-window", "no")
                            mpv.detachSurface()
                        }
                    })
                }
            },
        )
    }
}

/**
 * One `Name: value` line per header, for mpv's `http-header-fields`.
 *
 * Kept separate from the setting because *how* they are set is the whole problem: the option is a
 * comma-separated list, and a Jellyfin `Authorization` header is made of commas —
 * `MediaBrowser Token="…", Client="…", Device="…"`. Set in one go it arrived as four broken header
 * lines, the server answered 400 Bad Request, and the player sat on "buffering…" forever with
 * nothing on screen to say why. They are appended one at a time instead.
 */
internal fun headerLines(headers: Map<String, String>): List<String> =
    headers.entries.map { (k, v) -> "$k: $v" }
