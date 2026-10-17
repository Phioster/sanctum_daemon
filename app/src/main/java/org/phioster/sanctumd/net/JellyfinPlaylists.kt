package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.phioster.sanctumd.model.JellyMediaItem
import org.phioster.sanctumd.model.ServiceConfig

/** A playlist as the home row lists it. */
data class JellyPlaylist(val id: String, val name: String, val count: Int, val posterUrl: String)

/**
 * One line of a playlist. [entryId] is the playlist's own id for it: the same film can sit in
 * a playlist twice, so removing and moving go by entry, not by item.
 */
data class JellyPlaylistEntry(val entryId: String, val item: JellyMediaItem, val durationMs: Long, val isAudio: Boolean)

data class JellyPlaylistDetail(
    val id: String,
    val name: String,
    val entries: List<JellyPlaylistEntry>,
    /** Every entry in order as a playable track, when the whole list is music. */
    val tracks: List<MusicTrack>,
    val public: Boolean,
    /** User ids it is shared with, and whether each may edit. */
    val shares: Map<String, Boolean>,
)

private suspend fun <T> withJf(config: ServiceConfig, block: suspend (JellyfinApi, String, String) -> T): T = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    block(api, token, jellyfinResolveUserId(config, api))
}

private suspend fun write(op: String, success: String, block: suspend () -> retrofit2.Response<okhttp3.ResponseBody>): String =
    destructive(op) {
        withContext(Dispatchers.IO) {
            try {
                okOr(block(), success)
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

suspend fun jellyfinPlaylists(config: ServiceConfig): List<JellyPlaylist> = withJf(config) { api, token, uid ->
    api.playlists(uid).Items.map {
        val m = it.toMediaItem(config, token)
        JellyPlaylist(it.Id, it.Name, it.ChildCount ?: 0, m.posterUrl)
    }
}

suspend fun jellyfinPlaylistDetail(config: ServiceConfig, id: String): JellyPlaylistDetail = withJf(config) { api, token, uid ->
    val base = config.normalizedBaseUrl
    val headers = config.customHeaders + jellyfinAuth(token)
    val name = api.itemDetail(id = id, uid = uid).Name
    val items = api.playlistItems(id, uid).Items
    val entries = items.map {
        JellyPlaylistEntry(it.PlaylistItemId.orEmpty(), it.toMediaItem(config, token), ticksToMs(it.RunTimeTicks), it.Type == "Audio")
    }
    // A mixed list has no single queue: the music player can't show a film.
    val tracks = if (items.isNotEmpty() && items.all { it.Type == "Audio" }) items.map {
        MusicTrack(
            id = it.Id,
            title = it.Name,
            artist = it.AlbumArtist.orEmpty(),
            album = name,
            streamUrl = audioStreamUrl(base, it.Id),
            artUrl = if (!it.ImageTags?.get("Primary").isNullOrBlank()) primaryArtUrl(base, it.Id) else "",
            headers = headers,
            number = it.IndexNumber,
            durationMs = ticksToMs(it.RunTimeTicks),
        )
    } else emptyList()
    // Sharing needs Jellyfin 10.9; an older server just shows it private.
    val info = runCatching { api.playlistInfo(id) }.getOrNull()
    JellyPlaylistDetail(
        id = id,
        name = name,
        entries = entries,
        tracks = tracks,
        public = info?.let { jsBool(it, "OpenAccess") } == true,
        shares = (info?.get("Shares") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .associate { jsStr(it, "UserId").orEmpty() to (jsBool(it, "CanEdit") == true) },
    )
}

/** A new playlist, optionally with its first items. Returns its id, or an "error: …" message. */
suspend fun jellyfinCreatePlaylist(config: ServiceConfig, name: String, itemIds: List<String>): String =
    destructive("create Jellyfin playlist '$name'") {
        try {
            withJf(config) { api, _, uid ->
                val body = buildJsonObject {
                    put("Name", name.trim())
                    put("UserId", uid)
                    putJsonArray("Ids") { itemIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                }
                jsStr(api.createPlaylist(body), "Id") ?: "error: no id returned"
            }
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

suspend fun jellyfinAddToPlaylist(config: ServiceConfig, playlistId: String, itemIds: List<String>): String =
    write("add ${itemIds.size} item(s) to playlist $playlistId", "added ${itemIds.size}") {
        withJf(config) { api, _, uid -> api.addToPlaylist(playlistId, itemIds.joinToString(","), uid) }
    }

suspend fun jellyfinRemoveFromPlaylist(config: ServiceConfig, playlistId: String, entryIds: List<String>): String =
    write("remove ${entryIds.size} entr(ies) from playlist $playlistId", "removed") {
        withJf(config) { api, _, _ -> api.removeFromPlaylist(playlistId, entryIds.joinToString(",")) }
    }

/** [newIndex] counts from 0, in the playlist's own order. */
suspend fun jellyfinMovePlaylistEntry(config: ServiceConfig, playlistId: String, entryId: String, newIndex: Int): String =
    write("move entry $entryId in playlist $playlistId", "moved") {
        withJf(config) { api, _, _ -> api.movePlaylistItem(playlistId, entryId, newIndex) }
    }

/** Renames and/or sets public; a null field is left as it is (UpdatePlaylistDto). */
internal fun playlistUpdateBody(name: String?, public: Boolean?): JsonObject = buildJsonObject {
    name?.let { put("Name", it.trim()) }
    public?.let { put("IsPublic", it) }
}

suspend fun jellyfinUpdatePlaylist(config: ServiceConfig, playlistId: String, name: String? = null, public: Boolean? = null): String =
    write("update playlist $playlistId", "saved") {
        withJf(config) { api, _, _ -> api.updatePlaylist(playlistId, playlistUpdateBody(name, public)) }
    }

/** Shares with one user ([canEdit] lets them change it too); null unshares. */
suspend fun jellyfinSharePlaylist(config: ServiceConfig, playlistId: String, userId: String, canEdit: Boolean?): String =
    write("change sharing of playlist $playlistId", if (canEdit == null) "unshared" else "shared") {
        withJf(config) { api, _, _ ->
            if (canEdit == null) api.unsharePlaylist(playlistId, userId)
            else api.sharePlaylist(playlistId, userId, buildJsonObject { put("CanEdit", canEdit) })
        }
    }

/** Deletes the playlist itself; the films and songs in it stay. */
suspend fun jellyfinDeletePlaylist(config: ServiceConfig, playlistId: String): String =
    write("delete playlist $playlistId", "playlist deleted") {
        withJf(config) { api, _, _ -> api.deleteItem(playlistId) }
    }

/** Folds several films into one item with versions; the first id becomes the main one. */
suspend fun jellyfinMergeVersions(config: ServiceConfig, itemIds: List<String>): String =
    write("merge ${itemIds.size} Jellyfin items into one", "merged ${itemIds.size} versions") {
        withJf(config) { api, _, _ -> api.mergeVersions(itemIds.joinToString(",")) }
    }

/** What follows position [pos] of a play queue; null at its end or when not playing from one. */
internal fun <T> queueAfter(queue: List<T>, pos: Int): T? = if (pos < 0) null else queue.getOrNull(pos + 1)

/**
 * The video part of a playlist as a play queue, and where the tapped entry sits in it. Songs
 * are left out: they belong to the music player, not the film player.
 */
internal fun videoQueue(entries: List<JellyPlaylistEntry>, tapped: Int): Pair<List<NextEpisode>, Int> {
    val videos = entries.withIndex().filter { !it.value.isAudio }
    val queue = videos.map { NextEpisode(it.value.item.id, it.value.item.name, it.value.item.subtitle) }
    return queue to videos.indexOfFirst { it.index == tapped }.coerceAtLeast(0)
}
