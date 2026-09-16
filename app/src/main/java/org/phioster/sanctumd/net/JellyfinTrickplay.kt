package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.phioster.sanctumd.model.ServiceConfig
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---- Trickplay: the scrubbing preview tiles, used here as the ambient-glow colour source ----

/**
 * One set of trickplay tile sheets the server generated for an item.
 *
 * Jellyfin renders the previews into sheets of [tileWidth] × [tileHeight] thumbnails, each
 * thumbnail [width] × [height] px and [intervalMs] apart. Sheet and cell for a playback position
 * are pure maths — see [thumbIndexAt], [sheetIndexOf] and [cellOf].
 */
data class TrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val intervalMs: Int,
) {
    /** Thumbnails per sheet. */
    val perSheet: Int get() = (tileWidth * tileHeight).coerceAtLeast(1)

    /** The thumbnail covering [positionMs], clamped to the range the server actually generated. */
    fun thumbIndexAt(positionMs: Long): Int {
        if (intervalMs <= 0) return 0
        val raw = (positionMs / intervalMs).toInt()
        return raw.coerceIn(0, (thumbnailCount - 1).coerceAtLeast(0))
    }

    /** Which tile sheet holds [thumbIndex]. */
    fun sheetIndexOf(thumbIndex: Int): Int = thumbIndex / perSheet

    /** Column/row of [thumbIndex] within its sheet (sheets are laid out row by row). */
    fun cellOf(thumbIndex: Int): Pair<Int, Int> {
        val within = thumbIndex % perSheet
        return (within % tileWidth) to (within / tileWidth)
    }
}

/**
 * Reads the trickplay set to use out of an item's `Trickplay` field, or null when the server has
 * none for this item (not generated yet, or a pre-10.9 server).
 *
 * The field is keyed by media source, then by thumbnail width. Picks the widest variant that is
 * still small — the glow is blurred to nothing anyway, so a big sheet would only cost data.
 */
internal fun parseTrickplay(item: JsonObject, maxWidth: Int = 480): TrickplayInfo? {
    val bySource = item["Trickplay"] as? JsonObject ?: return null
    val byWidth = bySource.values.filterIsInstance<JsonObject>().firstOrNull { it.isNotEmpty() } ?: return null
    val chosen = byWidth.entries
        .mapNotNull { (key, value) -> (key.toIntOrNull() ?: return@mapNotNull null) to (value as? JsonObject ?: return@mapNotNull null) }
        .sortedBy { it.first }
        .let { widths -> widths.lastOrNull { it.first <= maxWidth } ?: widths.firstOrNull() }
        ?: return null
    val o = chosen.second
    fun int(name: String) = o[name]?.jsonPrimitive?.intOrNull ?: 0
    val info = TrickplayInfo(
        width = int("Width").takeIf { it > 0 } ?: chosen.first,
        height = int("Height"),
        tileWidth = int("TileWidth"),
        tileHeight = int("TileHeight"),
        thumbnailCount = int("ThumbnailCount"),
        intervalMs = int("Interval"),
    )
    val usable = info.width > 0 && info.height > 0 && info.tileWidth > 0 &&
        info.tileHeight > 0 && info.thumbnailCount > 0 && info.intervalMs > 0
    return if (usable) info else null
}

/** URL of tile sheet [sheetIndex] for [itemId]. The token travels as a header, never in the URL. */
internal fun trickplayTileUrl(config: ServiceConfig, itemId: String, info: TrickplayInfo, sheetIndex: Int): String =
    "${config.normalizedBaseUrl}Videos/$itemId/Trickplay/${info.width}/$sheetIndex.jpg"

internal interface JellyfinTrickplayApi {
    @GET("Items/{id}")
    suspend fun itemFields(
        @Path("id") id: String,
        @Query("userId") uid: String,
        @Query("Fields") fields: String = "Trickplay",
    ): JsonObject
}

internal fun jfTrickplayApi(config: ServiceConfig, token: String) =
    apiFor<JellyfinTrickplayApi>(config, jellyfinAuth(token))

/** The trickplay set for [itemId], or null when the server has none (the caller then skips the glow). */
suspend fun jellyfinTrickplay(config: ServiceConfig, itemId: String): TrickplayInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val token = jellyfinAccessToken(config)
        val api = jfTrickplayApi(config, token)
        val uid = jellyfinResolveUserId(config, jfApi(config, token))
        parseTrickplay(api.itemFields(id = itemId, uid = uid))
    }.getOrNull()
}

/** Raw JPEG bytes of one tile sheet, or null when the request fails. */
suspend fun jellyfinTrickplayTile(
    config: ServiceConfig,
    itemId: String,
    info: TrickplayInfo,
    sheetIndex: Int,
): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val token = jellyfinAccessToken(config)
        val req = Request.Builder().url(trickplayTileUrl(config, itemId, info, sheetIndex)).build()
        okClient(config, jellyfinAuth(token)).newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.bytes() else null
        }
    }.getOrNull()
}

// ---- Fallback colour source: the item's own artwork, when there is no trickplay ----

/**
 * A small version of the item's artwork — backdrop first (landscape, closer to a frame), else the
 * poster. Requested tiny on purpose: it only ever becomes a blurred wash, so 64px is plenty and the
 * download is a few kilobytes.
 */
suspend fun jellyfinAmbientArtwork(config: ServiceConfig, itemId: String): ByteArray? = withContext(Dispatchers.IO) {
    val token = runCatching { jellyfinAccessToken(config) }.getOrNull() ?: return@withContext null
    val client = okClient(config, jellyfinAuth(token))
    val candidates = listOf(
        "${config.normalizedBaseUrl}Items/$itemId/Images/Backdrop/0?maxWidth=64",
        "${config.normalizedBaseUrl}Items/$itemId/Images/Primary?maxWidth=64",
    )
    candidates.firstNotNullOfOrNull { url ->
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes()?.takeIf { it.isNotEmpty() } else null
            }
        }.getOrNull()
    }
}
