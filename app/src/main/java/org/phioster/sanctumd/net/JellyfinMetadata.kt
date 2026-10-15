package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.phioster.sanctumd.model.ServiceConfig

/** The fields of an item the metadata editor shows, as the server has them now. */
data class JellyMetadata(
    val name: String,
    val originalTitle: String,
    val sortTitle: String,
    val year: Int?,
    val overview: String,
    val genres: List<String>,
    val tags: List<String>,
    val officialRating: String,
    /** Jellyfin keeps its hands off the whole item while this is on. */
    val locked: Boolean,
)

/** One image a metadata provider offers for an item. */
data class JellyRemoteImage(
    val url: String,
    val thumbnailUrl: String,
    val provider: String,
    val width: Int,
    val height: Int,
    val language: String,
)

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }

internal fun metadataOf(o: JsonObject) = JellyMetadata(
    name = jsStr(o, "Name").orEmpty(),
    originalTitle = jsStr(o, "OriginalTitle").orEmpty(),
    sortTitle = jsStr(o, "ForcedSortName").orEmpty(),
    year = (o["ProductionYear"] as? JsonPrimitive)?.intOrNull,
    overview = jsStr(o, "Overview").orEmpty(),
    genres = o.strings("Genres"),
    tags = o.strings("Tags"),
    officialRating = jsStr(o, "OfficialRating").orEmpty(),
    locked = jsBool(o, "LockData") == true,
)

/**
 * The item with [m] written over it. POST /Items/{id} replaces the whole item, so a body with
 * only the edited fields would wipe genres, people and provider ids: the edit goes onto the full
 * item the server sent, the way Jellyfin's own editor does it.
 */
internal fun withMetadata(raw: JsonObject, m: JellyMetadata): JsonObject = JsonObject(
    raw.toMutableMap().apply {
        put("Name", JsonPrimitive(m.name))
        put("OriginalTitle", JsonPrimitive(m.originalTitle))
        put("ForcedSortName", JsonPrimitive(m.sortTitle))
        put("ProductionYear", m.year?.let { JsonPrimitive(it) } ?: JsonNull)
        put("Overview", JsonPrimitive(m.overview))
        put("Genres", JsonArray(m.genres.map { JsonPrimitive(it) }))
        put("Tags", JsonArray(m.tags.map { JsonPrimitive(it) }))
        put("OfficialRating", JsonPrimitive(m.officialRating))
        put("LockData", JsonPrimitive(m.locked))
    },
)

suspend fun jellyfinMetadata(config: ServiceConfig, itemId: String): JellyMetadata = withContext(Dispatchers.IO) {
    val api = jfApi(config, jellyfinAccessToken(config))
    metadataOf(api.itemRaw(itemId, jellyfinResolveUserId(config, api)))
}

suspend fun jellyfinSaveMetadata(config: ServiceConfig, itemId: String, m: JellyMetadata): String =
    destructive("edit Jellyfin metadata of $itemId") {
        withContext(Dispatchers.IO) {
            try {
                val api = jfApi(config, jellyfinAccessToken(config))
                val raw = api.itemRaw(itemId, jellyfinResolveUserId(config, api))
                okOr(api.updateItem(itemId, withMetadata(raw, m)), "saved")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** What the providers offer for one image type, best rated first (the server's own order). */
suspend fun jellyfinRemoteImages(config: ServiceConfig, itemId: String, type: String): List<JellyRemoteImage> =
    withContext(Dispatchers.IO) {
        jfApi(config, jellyfinAccessToken(config)).remoteImages(itemId, type).Images.map { remoteImageOf(it) }
            .filter { it.url.isNotBlank() }
    }

internal fun remoteImageOf(o: JsonObject) = JellyRemoteImage(
    url = jsStr(o, "Url").orEmpty(),
    thumbnailUrl = jsStr(o, "ThumbnailUrl")?.takeIf { it.isNotBlank() } ?: jsStr(o, "Url").orEmpty(),
    provider = jsStr(o, "ProviderName").orEmpty(),
    width = (o["Width"] as? JsonPrimitive)?.intOrNull ?: 0,
    height = (o["Height"] as? JsonPrimitive)?.intOrNull ?: 0,
    language = jsStr(o, "Language").orEmpty(),
)

suspend fun jellyfinPickRemoteImage(config: ServiceConfig, itemId: String, type: String, url: String): String =
    destructive("set Jellyfin $type image of $itemId") {
        withContext(Dispatchers.IO) {
            try {
                okOr(jfApi(config, jellyfinAccessToken(config)).downloadRemoteImage(itemId, type, url), "image set")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** Jellyfin takes an uploaded image as base64 text in the body, not as raw bytes. */
suspend fun jellyfinUploadImage(config: ServiceConfig, itemId: String, type: String, bytes: ByteArray, mimeType: String): String =
    destructive("upload Jellyfin $type image of $itemId") {
        withContext(Dispatchers.IO) {
            try {
                val body = java.util.Base64.getEncoder().encodeToString(bytes).toRequestBody(mimeType.toMediaType())
                okOr(jfApi(config, jellyfinAccessToken(config)).uploadImage(itemId, type, body), "image uploaded")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }
