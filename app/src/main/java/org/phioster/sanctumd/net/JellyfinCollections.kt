package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.model.ServiceConfig

/** A collection (BoxSet), and whether the item the dialog was opened for is in it. */
data class JellyCollection(val id: String, val name: String, val contains: Boolean)

/**
 * Every collection on the server, the ones holding [itemId] marked. Servers without the
 * membership route answer 404; then nothing is marked rather than the list failing.
 */
suspend fun jellyfinCollectionsFor(config: ServiceConfig, itemId: String): List<JellyCollection> = withContext(Dispatchers.IO) {
    val api = jfApi(config, jellyfinAccessToken(config))
    val uid = jellyfinResolveUserId(config, api)
    val all = api.itemQuery(uid, types = "BoxSet", limit = 500).Items
    val member = runCatching { api.itemCollections(itemId, uid).Items.map { it.Id }.toSet() }.getOrDefault(emptySet())
    all.map { JellyCollection(it.Id, it.Name, it.Id in member) }
}

suspend fun jellyfinCreateCollection(config: ServiceConfig, name: String, itemId: String): String =
    destructive("create Jellyfin collection '$name'") {
        withContext(Dispatchers.IO) {
            try {
                okOr(jfApi(config, jellyfinAccessToken(config)).createCollection(name.trim(), itemId), "collection created")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

/** Adds [itemId] to the collection, or takes it out when [add] is false. */
suspend fun jellyfinSetInCollection(config: ServiceConfig, collectionId: String, itemId: String, add: Boolean): String =
    destructive("${if (add) "add" else "remove"} $itemId ${if (add) "to" else "from"} collection $collectionId") {
        withContext(Dispatchers.IO) {
            try {
                val api = jfApi(config, jellyfinAccessToken(config))
                val r = if (add) api.addToCollection(collectionId, itemId) else api.removeFromCollection(collectionId, itemId)
                okOr(r, if (add) "added" else "removed")
            } catch (t: Throwable) {
                "error: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }
