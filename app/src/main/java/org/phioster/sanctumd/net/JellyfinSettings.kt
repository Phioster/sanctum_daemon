package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.phioster.sanctumd.model.ServiceConfig

/*
 * Jellyfin's server, transcoding and library settings. Each is one big object that the server
 * replaces as a whole on save, so it is read raw, only the touched keys change, and everything
 * else (fields this app doesn't know) goes back exactly as it came.
 */

internal fun JsonObject.at(path: List<String>): JsonElement? =
    path.fold<String, JsonElement?>(this) { e, k -> (e as? JsonObject)?.get(k) }

internal fun JsonObject.with(path: List<String>, value: JsonElement): JsonObject {
    val k = path.first()
    if (path.size == 1) return JsonObject(this + (k to value))
    val child = (this[k] as? JsonObject) ?: JsonObject(emptyMap())
    return JsonObject(this + (k to child.with(path.drop(1), value)))
}

/** Keys whose value differs between two versions of a settings object, dotted for nested ones. */
internal fun changedKeys(before: JsonObject, after: JsonObject, prefix: String = ""): List<String> =
    (before.keys + after.keys).distinct().flatMap { k ->
        val a = before[k]
        val b = after[k]
        if (a is JsonObject && b is JsonObject) changedKeys(a, b, "$prefix$k.") else if (a != b) listOf("$prefix$k") else emptyList()
    }

/** One editable setting. [key] is dotted for nested objects ("TrickplayOptions.Interval"). */
sealed class JfField(val key: String, val label: String, val hint: String, val only: Set<String>) {
    val path get() = key.split('.')

    class Toggle(key: String, label: String, hint: String = "", only: Set<String> = emptySet()) : JfField(key, label, hint, only)
    class Text(key: String, label: String, hint: String = "", only: Set<String> = emptySet()) : JfField(key, label, hint, only)
    class Number(key: String, label: String, hint: String = "", val min: Double = 0.0, val max: Double = 1e9, val decimal: Boolean = false, only: Set<String> = emptySet()) :
        JfField(key, label, hint, only)
    class IntList(key: String, label: String, hint: String = "", only: Set<String> = emptySet()) : JfField(key, label, hint, only)

    /** [source] names a list the server provides ("cultures", "countries"…); otherwise [options] are fixed. */
    class Choice(key: String, label: String, val options: List<Pair<String, String>> = emptyList(), val source: String? = null, hint: String = "", only: Set<String> = emptySet()) :
        JfField(key, label, hint, only)
    class Multi(key: String, label: String, val options: List<Pair<String, String>> = emptyList(), val source: String? = null, hint: String = "", only: Set<String> = emptySet()) :
        JfField(key, label, hint, only)
}

data class JfSection(val title: String, val fields: List<JfField>)

private fun opts(vararg v: String) = v.map { it to it }

internal val SERVER_SETTINGS = listOf(
    JfSection("general", listOf(
        JfField.Text("ServerName", "Server name"),
        JfField.Toggle("QuickConnectAvailable", "Quick Connect", "Sign in TVs by a code from the phone"),
        JfField.Toggle("DisplaySpecialsWithinSeasons", "Specials inside their seasons"),
        JfField.Toggle("EnableGroupingMoviesIntoCollections", "Group films into their collections"),
        JfField.Toggle("EnableGroupingShowsIntoCollections", "Group shows into their collections"),
        JfField.Toggle("EnableFolderView", "Folder view", "Show the raw media folders as a library"),
    )),
    JfSection("metadata", listOf(
        JfField.Choice("PreferredMetadataLanguage", "Metadata language", source = "cultures"),
        JfField.Choice("MetadataCountryCode", "Metadata country", source = "countries", hint = "For age ratings and release dates"),
        JfField.Choice("ChapterImageResolution", "Chapter image size", opts("MatchSource", "P144", "P240", "P360", "P480", "P720", "P1080", "P1440", "P2160")),
        JfField.Number("DummyChapterDuration", "Chapter marks every (s)", "0 = off; for files without chapters", 0.0, 36_000.0),
    )),
    JfSection("resume", listOf(
        JfField.Number("MinResumePct", "Resume from (%)", "Watched less: starts from the beginning again", 0.0, 100.0),
        JfField.Number("MaxResumePct", "Counts as watched from (%)", "", 1.0, 100.0),
        JfField.Number("MinResumeDurationSeconds", "Shortest resumable item (s)", "", 0.0, 36_000.0),
        JfField.Number("InactiveSessionThreshold", "Close idle sessions after (min)", "0 = never", 0.0, 1440.0),
    )),
    JfSection("library scan", listOf(
        JfField.Number("LibraryMonitorDelay", "Wait after a file change (s)", "Real-time monitoring settles this long before scanning", 0.0, 3600.0),
        JfField.Number("LibraryScanFanoutConcurrency", "Parallel scan tasks", "0 = automatic", 0.0, 64.0),
        JfField.Number("LibraryMetadataRefreshConcurrency", "Parallel metadata refreshes", "0 = automatic", 0.0, 64.0),
        JfField.Number("ParallelImageEncodingLimit", "Parallel image encodes", "0 = automatic", 0.0, 64.0),
    )),
    JfSection("trickplay", listOf(
        JfField.Toggle("TrickplayOptions.EnableHwAcceleration", "Hardware decoding"),
        JfField.Toggle("TrickplayOptions.EnableHwEncoding", "Hardware encoding"),
        JfField.Toggle("TrickplayOptions.EnableKeyFrameOnlyExtraction", "Key frames only", "Much faster, less precise"),
        JfField.Choice("TrickplayOptions.ScanBehavior", "During library scans", listOf("NonBlocking" to "in the background", "Blocking" to "scan waits for it")),
        JfField.Choice("TrickplayOptions.ProcessPriority", "Process priority", opts("Idle", "BelowNormal", "Normal", "AboveNormal", "High")),
        JfField.Number("TrickplayOptions.Interval", "Picture every (ms)", "", 1000.0, 600_000.0),
        JfField.IntList("TrickplayOptions.WidthResolutions", "Widths (px)", "Comma separated, e.g. 320"),
        JfField.Number("TrickplayOptions.JpegQuality", "JPEG quality", "", 1.0, 100.0),
        JfField.Number("TrickplayOptions.ProcessThreads", "ffmpeg threads", "0 = automatic", 0.0, 64.0),
    )),
    JfSection("housekeeping", listOf(
        JfField.Number("ActivityLogRetentionDays", "Keep activity for (days)", "", 0.0, 3650.0),
        JfField.Number("LogFileRetentionDays", "Keep log files for (days)", "", 0.0, 3650.0),
    )),
)

internal val ENCODING_SETTINGS = listOf(
    JfSection("hardware acceleration", listOf(
        JfField.Choice("HardwareAccelerationType", "Acceleration", listOf(
            "none" to "none (software)", "nvenc" to "NVIDIA NVENC", "qsv" to "Intel Quick Sync", "vaapi" to "VA-API",
            "amf" to "AMD AMF", "v4l2m2m" to "V4L2 (Raspberry Pi…)", "rkmpp" to "Rockchip MPP", "videotoolbox" to "Apple VideoToolbox",
        )),
        JfField.Text("VaapiDevice", "VA-API device", "Only for VA-API, e.g. /dev/dri/renderD128"),
        JfField.Text("QsvDevice", "QSV device", "Only for Quick Sync, usually empty"),
        JfField.Multi("HardwareDecodingCodecs", "Decode in hardware", opts("h264", "hevc", "mpeg2video", "mpeg4", "vc1", "vp8", "vp9", "av1")),
        JfField.Toggle("EnableDecodingColorDepth10Hevc", "10-bit HEVC decoding"),
        JfField.Toggle("EnableDecodingColorDepth10Vp9", "10-bit VP9 decoding"),
        JfField.Toggle("PreferSystemNativeHwDecoder", "Prefer the OS's native decoder"),
        JfField.Toggle("EnableHardwareEncoding", "Hardware encoding"),
        JfField.Toggle("AllowHevcEncoding", "Allow HEVC encoding"),
        JfField.Toggle("AllowAv1Encoding", "Allow AV1 encoding"),
    )),
    JfSection("tone mapping", listOf(
        JfField.Toggle("EnableTonemapping", "Tone mapping", "HDR to SDR for screens without HDR"),
        JfField.Toggle("EnableVppTonemapping", "VPP tone mapping (Intel)"),
        JfField.Choice("TonemappingAlgorithm", "Algorithm", opts("none", "clip", "linear", "gamma", "reinhard", "hable", "mobius", "bt2390")),
        JfField.Choice("TonemappingMode", "Mode", opts("auto", "max", "rgb", "lum", "itp")),
        JfField.Choice("TonemappingRange", "Range", opts("auto", "tv", "pc")),
    )),
    JfSection("encoding", listOf(
        JfField.Choice("EncoderPreset", "Encoder preset", opts("auto", "veryslow", "slower", "slow", "medium", "fast", "faster", "veryfast", "superfast", "ultrafast"), hint = "Faster = bigger files, less CPU"),
        JfField.Number("H264Crf", "H.264 quality (CRF)", "Lower = better, 23 is the default", 0.0, 51.0),
        JfField.Number("H265Crf", "H.265 quality (CRF)", "Lower = better, 28 is the default", 0.0, 51.0),
        JfField.Number("EncodingThreadCount", "Encoder threads", "-1 = automatic", -1.0, 128.0),
        JfField.Choice("DeinterlaceMethod", "Deinterlacing", opts("yadif", "bwdif")),
        JfField.Choice("DownMixStereoAlgorithm", "Stereo downmix", opts("None", "Dave750", "NightmodeDialogue", "Rfc7845", "Ac4")),
        JfField.Number("DownMixAudioBoost", "Downmix boost", "", 0.5, 3.0, decimal = true),
        JfField.Toggle("EnableAudioVbr", "Variable audio bitrate"),
    )),
    JfSection("transcode files", listOf(
        JfField.Text("TranscodingTempPath", "Transcode folder"),
        JfField.Toggle("EnableThrottling", "Throttle", "Pause transcoding when far enough ahead of the player"),
        JfField.Number("ThrottleDelaySeconds", "Throttle after (s)", "", 10.0, 3600.0),
        JfField.Toggle("EnableSegmentDeletion", "Delete played segments"),
        JfField.Number("SegmentKeepSeconds", "Keep segments for (s)", "", 10.0, 3600.0),
        JfField.Toggle("EnableSubtitleExtraction", "Extract subtitles on the fly"),
    )),
)

private val MOVIES = setOf("movies")
private val TV = setOf("tvshows")
private val MUSIC = setOf("music")

internal val LIBRARY_SETTINGS = listOf(
    JfSection("scanning", listOf(
        JfField.Toggle("Enabled", "Library enabled"),
        JfField.Toggle("EnableRealtimeMonitor", "Real-time monitoring", "Scan new files as soon as they land"),
        JfField.Choice("AutomaticRefreshIntervalDays", "Refresh metadata", listOf("0" to "never", "30" to "every 30 days", "60" to "every 60 days", "90" to "every 90 days")),
        JfField.Toggle("EnablePhotos", "Include photos", only = setOf("homevideos", "")),
        JfField.Toggle("EnableLUFSScan", "Loudness scan (LUFS)", "For normalised volume", only = MUSIC),
    )),
    JfSection("metadata", listOf(
        JfField.Choice("PreferredMetadataLanguage", "Metadata language", source = "cultures", hint = "Empty = the server's"),
        JfField.Choice("MetadataCountryCode", "Metadata country", source = "countries", hint = "Empty = the server's"),
        JfField.Multi("MetadataSavers", "Save metadata as", source = "savers", hint = "e.g. NFO files next to the media"),
        JfField.Toggle("SaveLocalMetadata", "Save artwork into the media folders"),
        JfField.Toggle("EnableEmbeddedTitles", "Use titles embedded in files", only = MOVIES + ""),
        JfField.Toggle("EnableEmbeddedExtrasTitles", "Use embedded titles for extras", only = MOVIES + TV + ""),
        JfField.Toggle("EnableEmbeddedEpisodeInfos", "Use episode info embedded in files", only = TV),
        JfField.Toggle("EnableAutomaticSeriesGrouping", "Merge shows spread over several folders", only = TV),
        JfField.Text("SeasonZeroDisplayName", "Name for season 0", only = TV),
        JfField.Toggle("AutomaticallyAddToCollection", "Add films to their collections", only = MOVIES),
        JfField.Toggle("PreferNonstandardArtistsTag", "Prefer the ARTISTS tag", only = MUSIC),
        JfField.Toggle("SaveLyricsWithMedia", "Save lyrics next to the music", only = MUSIC),
    )),
    JfSection("chapters & trickplay", listOf(
        JfField.Toggle("EnableChapterImageExtraction", "Chapter images", only = MOVIES + TV + setOf("homevideos", "musicvideos", "")),
        JfField.Toggle("ExtractChapterImagesDuringLibraryScan", "…during the library scan", "Otherwise in the nightly task", only = MOVIES + TV + setOf("homevideos", "musicvideos", "")),
        JfField.Toggle("EnableTrickplayImageExtraction", "Trickplay previews", only = MOVIES + TV + setOf("homevideos", "musicvideos", "")),
        JfField.Toggle("ExtractTrickplayImagesDuringLibraryScan", "…during the library scan", only = MOVIES + TV + setOf("homevideos", "musicvideos", "")),
        JfField.Toggle("SaveTrickplayWithMedia", "Save previews next to the media", only = MOVIES + TV + setOf("homevideos", "musicvideos", "")),
    )),
    JfSection("subtitles", listOf(
        JfField.Choice("AllowEmbeddedSubtitles", "Embedded subtitles", listOf("AllowAll" to "all", "AllowText" to "text only", "AllowImage" to "images only", "AllowNone" to "none"), only = MOVIES + TV + setOf("homevideos", "musicvideos", "")),
        JfField.Multi("SubtitleDownloadLanguages", "Download subtitles in", source = "cultures3", only = MOVIES + TV + ""),
        JfField.Toggle("SkipSubtitlesIfEmbeddedSubtitlesPresent", "Skip when the file has subtitles", only = MOVIES + TV + ""),
        JfField.Toggle("SkipSubtitlesIfAudioTrackMatches", "Skip when the audio is in that language", only = MOVIES + TV + ""),
        JfField.Toggle("RequirePerfectSubtitleMatch", "Only exact matches", only = MOVIES + TV + ""),
        JfField.Toggle("SaveSubtitlesWithMedia", "Save subtitles next to the media", only = MOVIES + TV + ""),
    )),
)

/** A field shown for this library type; fields without a restriction are for every type. */
internal fun JfField.appliesTo(collectionType: String) = only.isEmpty() || collectionType in only

/** One fetcher list of a library: which providers run, in which order. */
data class JfFetchers(val type: String, val kind: String, val order: List<String>, val enabled: Set<String>)

private fun names(a: JsonElement?): List<String> = (a as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

/**
 * The metadata and image providers per item type, as the server offers them, merged with what
 * the library has stored. Never stored yet means the server's defaults.
 */
internal fun fetchersOf(options: JsonObject, available: JsonObject): List<JfFetchers> {
    val stored = (options["TypeOptions"] as? JsonArray).orEmptyObjects()
    return (available["TypeOptions"] as? JsonArray).orEmptyObjects().flatMap { t ->
        val type = jsStr(t, "Type").orEmpty()
        val mine = stored.firstOrNull { jsStr(it, "Type") == type }
        listOf("Metadata", "Image").mapNotNull { kind ->
            val offered = (t["${kind}Fetchers"] as? JsonArray).orEmptyObjects()
            if (offered.isEmpty()) return@mapNotNull null
            val all = offered.map { jsStr(it, "Name").orEmpty() }
            val defaults = offered.filter { jsBool(it, "DefaultEnabled") == true }.map { jsStr(it, "Name").orEmpty() }.toSet()
            val storedOrder = names(mine?.get("${kind}FetcherOrder")).filter { it in all }
            val order = storedOrder + all.filterNot { it in storedOrder }
            val enabled = mine?.get("${kind}Fetchers")?.let { names(it).toSet() } ?: defaults
            JfFetchers(type, kind, order, enabled)
        }
    }
}

/** Writes one fetcher list back into the library options, creating the type entry if needed. */
internal fun JsonObject.withFetchers(f: JfFetchers, available: JsonObject): JsonObject {
    val list = (this["TypeOptions"] as? JsonArray).orEmptyObjects().toMutableList()
    val i = list.indexOfFirst { jsStr(it, "Type") == f.type }
    val base = if (i >= 0) list[i] else buildJsonObject {
        put("Type", f.type)
        // A new entry needs the image defaults, as the web client sends them.
        val defaults = (available["TypeOptions"] as? JsonArray).orEmptyObjects().firstOrNull { jsStr(it, "Type") == f.type }?.get("DefaultImageOptions")
        put("ImageOptions", defaults ?: JsonArray(emptyList()))
    }
    val updated = JsonObject(base + mapOf(
        "${f.kind}Fetchers" to JsonArray(f.order.filter { it in f.enabled }.map(::JsonPrimitive)),
        "${f.kind}FetcherOrder" to JsonArray(f.order.map(::JsonPrimitive)),
    ))
    if (i >= 0) list[i] = updated else list += updated
    return JsonObject(this + ("TypeOptions" to JsonArray(list)))
}

private fun JsonArray?.orEmptyObjects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject } ?: emptyList()

/** Choices the server provides: languages, countries, subtitle languages, metadata savers. */
data class JfChoiceSources(val lists: Map<String, List<Pair<String, String>>>)

suspend fun jellyfinChoiceSources(config: ServiceConfig, available: JsonObject? = null): JfChoiceSources = withContext(Dispatchers.IO) {
    val api = jfApi(config, jellyfinAccessToken(config))
    val cultures = runCatching { api.cultures() }.getOrDefault(emptyList()).sortedBy { jsStr(it, "DisplayName") }
    val countries = runCatching { api.countries() }.getOrDefault(emptyList()).sortedBy { jsStr(it, "DisplayName") }
    JfChoiceSources(mapOf(
        "cultures" to cultures.mapNotNull { c -> jsStr(c, "TwoLetterISOLanguageName")?.let { it to (jsStr(c, "DisplayName") ?: it) } }.distinctBy { it.first },
        "cultures3" to cultures.mapNotNull { c -> jsStr(c, "ThreeLetterISOLanguageName")?.let { it to (jsStr(c, "DisplayName") ?: it) } }.distinctBy { it.first },
        "countries" to countries.mapNotNull { c -> jsStr(c, "TwoLetterISORegionName")?.let { it to (jsStr(c, "DisplayName") ?: it) } },
        "savers" to (available?.get("MetadataSavers") as? JsonArray).orEmptyObjects().mapNotNull { jsStr(it, "Name") }.map { it to it },
    ))
}

suspend fun jellyfinServerSettings(config: ServiceConfig): JsonObject = withContext(Dispatchers.IO) {
    jfApi(config, jellyfinAccessToken(config)).serverConfiguration()
}

suspend fun jellyfinSaveServerSettings(config: ServiceConfig, settings: JsonObject): String = destructive("change Jellyfin server settings") {
    withContext(Dispatchers.IO) {
        runCatching { okOr(jfApi(config, jellyfinAccessToken(config)).setServerConfiguration(settings), "server settings saved") }
            .getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
    }
}

suspend fun jellyfinEncodingSettings(config: ServiceConfig): JsonObject = withContext(Dispatchers.IO) {
    jfApi(config, jellyfinAccessToken(config)).encodingConfiguration()
}

suspend fun jellyfinSaveEncodingSettings(config: ServiceConfig, settings: JsonObject): String = destructive("change Jellyfin transcoding settings") {
    withContext(Dispatchers.IO) {
        runCatching { okOr(jfApi(config, jellyfinAccessToken(config)).setEncodingConfiguration(settings), "transcoding settings saved") }
            .getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
    }
}

/** A library's stored options and what the server offers for its type. */
data class JfLibraryOptions(val options: JsonObject, val available: JsonObject)

suspend fun jellyfinLibraryOptions(config: ServiceConfig, libraryId: String, collectionType: String): JfLibraryOptions = withContext(Dispatchers.IO) {
    val api = jfApi(config, jellyfinAccessToken(config))
    val folder = api.virtualFoldersRaw().firstOrNull { jsStr(it, "ItemId") == libraryId }
        ?: error("library not found")
    val available = runCatching { api.availableLibraryOptions(collectionType.ifBlank { null }, false) }.getOrDefault(JsonObject(emptyMap()))
    JfLibraryOptions((folder["LibraryOptions"] as? JsonObject) ?: JsonObject(emptyMap()), available)
}

suspend fun jellyfinSaveLibraryOptions(config: ServiceConfig, libraryId: String, libraryName: String, options: JsonObject): String =
    destructive("change options of Jellyfin library $libraryName") {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject { put("Id", libraryId); put("LibraryOptions", options) }
                okOr(jfApi(config, jellyfinAccessToken(config)).setLibraryOptions(body), "library options saved")
            }.getOrElse { "error: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

/** A JSON value as the form shows it. */
internal fun JsonElement?.formText(): String = when (this) {
    null, is kotlinx.serialization.json.JsonNull -> ""
    is JsonPrimitive -> contentOrNull.orEmpty()
    is JsonArray -> joinToString(",") { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
    else -> toString()
}

internal fun JsonElement?.formBool(): Boolean = (this as? JsonPrimitive)?.contentOrNull?.toBoolean() == true

internal fun JsonElement?.formList(): List<String> = names(this)

/** Parses what was typed into a number field; null when it isn't a number in range. */
internal fun parseFormNumber(field: JfField.Number, text: String): JsonPrimitive? {
    val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (v < field.min || v > field.max) return null
    return if (field.decimal) JsonPrimitive(v) else JsonPrimitive(v.toLong())
}

/** Comma separated whole numbers, e.g. trickplay widths; null when one isn't. */
internal fun parseIntList(text: String): JsonArray? {
    val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val nums = parts.map { it.toIntOrNull() ?: return null }
    return JsonArray(nums.map { JsonPrimitive(it) })
}

/**
 * A chosen value in the JSON type the setting already has: the server is .NET and rejects "30"
 * where it stored 30. Unknown before (missing key) means a string, which is what choices mostly are.
 */
internal fun typedLike(old: JsonElement?, value: String): JsonPrimitive {
    val p = old as? JsonPrimitive
    if (p != null && !p.isString) {
        value.toLongOrNull()?.let { return JsonPrimitive(it) }
        value.toDoubleOrNull()?.let { return JsonPrimitive(it) }
        value.toBooleanStrictOrNull()?.let { return JsonPrimitive(it) }
    }
    return JsonPrimitive(value)
}
