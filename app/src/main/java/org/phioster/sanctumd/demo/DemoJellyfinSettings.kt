package org.phioster.sanctumd.demo

import kotlinx.serialization.json.JsonObject

/** The made-up server's settings: an Intel box with Quick Sync, German metadata, monitored libraries. */
internal object DemoJellyfinSettings {
    private val server = obj(
        "ServerName" to "Wohnzimmer-Server", "QuickConnectAvailable" to true, "DisplaySpecialsWithinSeasons" to true,
        "EnableGroupingMoviesIntoCollections" to true, "EnableGroupingShowsIntoCollections" to false, "EnableFolderView" to false,
        "PreferredMetadataLanguage" to "de", "MetadataCountryCode" to "DE", "ChapterImageResolution" to "MatchSource", "DummyChapterDuration" to 0,
        "MinResumePct" to 5, "MaxResumePct" to 90, "MinResumeDurationSeconds" to 300, "InactiveSessionThreshold" to 0,
        "LibraryMonitorDelay" to 60, "LibraryScanFanoutConcurrency" to 0, "LibraryMetadataRefreshConcurrency" to 0, "ParallelImageEncodingLimit" to 0,
        "ActivityLogRetentionDays" to 30, "LogFileRetentionDays" to 3,
        "TrickplayOptions" to obj(
            "EnableHwAcceleration" to true, "EnableHwEncoding" to true, "EnableKeyFrameOnlyExtraction" to true, "ScanBehavior" to "NonBlocking",
            "ProcessPriority" to "BelowNormal", "Interval" to 10_000, "WidthResolutions" to listOf(320), "TileWidth" to 10, "TileHeight" to 10,
            "Qscale" to 4, "JpegQuality" to 90, "ProcessThreads" to 1,
        ),
    )

    private val encoding = obj(
        "HardwareAccelerationType" to "qsv", "QsvDevice" to "", "VaapiDevice" to "/dev/dri/renderD128",
        "HardwareDecodingCodecs" to listOf("h264", "hevc", "mpeg2video", "vc1", "vp9", "av1"),
        "EnableDecodingColorDepth10Hevc" to true, "EnableDecodingColorDepth10Vp9" to true, "PreferSystemNativeHwDecoder" to true,
        "EnableHardwareEncoding" to true, "AllowHevcEncoding" to true, "AllowAv1Encoding" to false,
        "EnableTonemapping" to true, "EnableVppTonemapping" to false, "TonemappingAlgorithm" to "bt2390", "TonemappingMode" to "auto", "TonemappingRange" to "auto",
        "EncoderPreset" to "auto", "H264Crf" to 23, "H265Crf" to 28, "EncodingThreadCount" to -1, "DeinterlaceMethod" to "yadif",
        "DownMixStereoAlgorithm" to "None", "DownMixAudioBoost" to 2.0, "EnableAudioVbr" to false,
        "TranscodingTempPath" to "/transcode", "EnableThrottling" to true, "ThrottleDelaySeconds" to 180,
        "EnableSegmentDeletion" to true, "SegmentKeepSeconds" to 720, "EnableSubtitleExtraction" to true,
    )

    fun libraryOptions(type: String): JsonObject = obj(
        "Enabled" to true, "EnableRealtimeMonitor" to true, "AutomaticRefreshIntervalDays" to 30, "EnablePhotos" to false,
        "PreferredMetadataLanguage" to "", "MetadataCountryCode" to "", "MetadataSavers" to listOf("Nfo"), "SaveLocalMetadata" to false,
        "EnableChapterImageExtraction" to (type != "music"), "ExtractChapterImagesDuringLibraryScan" to false,
        "EnableTrickplayImageExtraction" to (type != "music"), "ExtractTrickplayImagesDuringLibraryScan" to false,
        "AllowEmbeddedSubtitles" to "AllowAll", "SubtitleDownloadLanguages" to listOf("ger", "eng"),
        "SkipSubtitlesIfEmbeddedSubtitlesPresent" to true, "SkipSubtitlesIfAudioTrackMatches" to true, "SaveSubtitlesWithMedia" to true,
        "EnableAutomaticSeriesGrouping" to true, "SeasonZeroDisplayName" to "Specials", "EnableLUFSScan" to (type == "music"),
        "TypeOptions" to emptyList<Any>(),
    )

    private fun fetchers(vararg n: Pair<String, Boolean>) = n.map { (name, on) -> obj("Name" to name, "DefaultEnabled" to on) }

    fun available(type: String?): JsonObject {
        val types = when (type) {
            "tvshows" -> listOf("Series", "Season", "Episode")
            "music" -> listOf("MusicArtist", "MusicAlbum", "Audio")
            else -> listOf("Movie")
        }
        val meta = if (type == "music") fetchers("MusicBrainz" to true, "TheAudioDB" to true) else fetchers("TheMovieDb" to true, "The Open Movie Database" to true, "TheTVDB" to false)
        val images = if (type == "music") fetchers("TheAudioDB" to true, "Fanart" to true, "Embedded Image Extractor" to true)
            else fetchers("TheMovieDb" to true, "Fanart" to true, "Screen Grabber" to (type == "tvshows"), "Embedded Image Extractor" to true)
        return obj(
            "MetadataSavers" to fetchers("Nfo" to false),
            "TypeOptions" to types.map { obj("Type" to it, "MetadataFetchers" to meta, "ImageFetchers" to images, "DefaultImageOptions" to emptyList<Any>()) },
        )
    }

    private val cultures = listOf(
        Triple("de", "ger", "German"), Triple("en", "eng", "English"), Triple("fr", "fre", "French"), Triple("es", "spa", "Spanish"),
        Triple("it", "ita", "Italian"), Triple("ja", "jpn", "Japanese"), Triple("nl", "dut", "Dutch"), Triple("pl", "pol", "Polish"),
        Triple("tr", "tur", "Turkish"), Triple("sv", "swe", "Swedish"), Triple("ko", "kor", "Korean"), Triple("pt", "por", "Portuguese"),
        Triple("da", "dan", "Danish"),
    ).map { (two, three, name) -> obj("DisplayName" to name, "Name" to name, "TwoLetterISOLanguageName" to two, "ThreeLetterISOLanguageName" to three) }

    private val countries = listOf("DE" to "Germany", "AT" to "Austria", "CH" to "Switzerland", "GB" to "United Kingdom", "US" to "United States", "FR" to "France", "NL" to "Netherlands")
        .map { (code, name) -> obj("DisplayName" to name, "Name" to code, "TwoLetterISORegionName" to code) }

    /** Null when the path isn't a settings one; writes fall through to the interceptor's quiet 200. */
    fun handle(r: DemoRequest): DemoAnswer? = when (r.path) {
        "System/Configuration" -> json(server)
        "System/Configuration/encoding" -> json(encoding)
        "Libraries/AvailableOptions" -> json(available(r.q("libraryContentType")))
        "Localization/Cultures" -> json(arr(cultures))
        "Localization/Countries" -> json(arr(countries))
        else -> null
    }
}
