package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.phioster.sanctumd.model.JellyActivity
import org.phioster.sanctumd.model.JellyChannel
import org.phioster.sanctumd.model.JellyDevice
import org.phioster.sanctumd.model.JellyGuideProvider
import org.phioster.sanctumd.model.JellyLiveTv
import org.phioster.sanctumd.model.JellyTuner
import org.phioster.sanctumd.model.JellyLibrary
import org.phioster.sanctumd.model.JellyLogFile
import org.phioster.sanctumd.model.JellyPackage
import org.phioster.sanctumd.model.JellyPlugin
import org.phioster.sanctumd.model.JellySession
import org.phioster.sanctumd.model.JellySystemInfo
import org.phioster.sanctumd.model.JellyTask
import org.phioster.sanctumd.model.JellyUser
import org.phioster.sanctumd.model.SearchResult
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceStatus
import retrofit2.http.Path
import retrofit2.http.Url

internal suspend fun jellyfinSearchResults(config: ServiceConfig, term: String): List<SearchResult> {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = jellyfinResolveUserId(config, api)
    return api.searchItems(uid, term).Items.take(10).map { it ->
        SearchResult(
            serviceId = config.id,
            serviceLabel = config.label,
            serviceType = config.type,
            title = it.Name,
            subtitle = listOfNotNull(it.Type.takeIf { t -> t.isNotBlank() }, it.ProductionYear?.toString(), "on Jellyfin").joinToString(" · "),
            posterUrl = jellyImageUrl(config, it.Id, it.ImageTags?.get("Primary"), token),
            jellyItemId = it.Id,
            year = it.ProductionYear ?: 0,
            inLibrary = true, // it's on the Jellyfin server
            adult = isAdultRating(it.OfficialRating),
        )
    }
}

internal suspend fun jellyfinAccessToken(config: ServiceConfig): String {
    if (!config.useLogin) return config.apiKey
    jellyfinSession[config.id]?.let { return it.first }
    // Serialize login so concurrent cards on cold start don't each authenticate (and race a 401).
    return jellyfinAuthLock.withLock {
        jellyfinSession[config.id]?.let { return@withLock it.first }
        val resp = apiFor<JellyfinAuthApi>(config, mapOf("Authorization" to MB_AUTH))
            .authenticate(JfAuthReq(config.username, config.password))
        val label = (if (resp.User.Policy.IsAdministrator) "admin: " else "user: ") + resp.User.Name
        jellyfinSession[config.id] = resp.AccessToken to label
        resp.AccessToken
    }
}

internal suspend fun jellyfinStatus(config: ServiceConfig): ServiceStatus {
    val token = jellyfinAccessToken(config)
    val note = if (config.useLogin) jellyfinSession[config.id]?.second else null
    val jf = apiFor<JellyfinApi>(config, jellyfinAuth(token))
    val counts = jf.counts()
    val playing = jf.sessions().count { it.NowPlayingItem != null }
    return ServiceStatus(
        ok = true,
        note = note,
        stats = listOf(
            "Movies" to counts.MovieCount.toString(),
            "Series" to counts.SeriesCount.toString(),
            "Songs" to counts.SongCount.toString(),
            "Playing" to playing.toString(),
        ),
    )
}

/** Triggers a full library scan on Jellyfin. Returns a user-facing result line. */
suspend fun runJellyfinScan(config: ServiceConfig): String = destructive("trigger a Jellyfin library scan") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val resp = apiFor<JellyfinApi>(config, jellyfinAuth(token)).refreshLibrary()
            if (resp.isSuccessful) "library scan started" else "error: HTTP ${resp.code()}"
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinSessions(config: ServiceConfig): List<JellySession> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).sessions()
        .filter { it.UserName != null || it.NowPlayingItem != null }
        .map { s ->
            val np = s.NowPlayingItem
            val run = np?.RunTimeTicks ?: 0L
            val pos = s.PlayState?.PositionTicks ?: 0L
            val pct = if (run > 0) (pos.toFloat() / run).coerceIn(0f, 1f) else 0f
            val subtitle = when {
                np == null -> (s.Client ?: "").ifBlank { "idle" }
                np.Type == "Episode" -> np.SeriesName ?: "Episode"
                else -> listOfNotNull(np.Type, np.ProductionYear?.toString()).joinToString(" · ")
            }
            JellySession(
                id = s.Id,
                user = s.UserName ?: "?",
                device = s.DeviceName ?: "",
                client = s.Client ?: "",
                nowPlaying = np?.Name ?: "",
                subtitle = subtitle,
                progressPct = pct,
                paused = s.PlayState?.IsPaused ?: false,
                canControl = s.SupportsRemoteControl,
                lastActivity = (s.LastActivityDate ?: "").take(16).replace('T', ' '),
            )
        }
        .sortedByDescending { it.nowPlaying.isNotEmpty() }
}

suspend fun jellyfinUsers(config: ServiceConfig): List<JellyUser> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).users().map { u ->
        JellyUser(
            id = u.Id,
            name = u.Name,
            lastActivity = (u.LastActivityDate ?: "").take(16).replace('T', ' '),
            admin = u.Policy.IsAdministrator,
            disabled = u.Policy.IsDisabled,
            allowDownloads = u.Policy.EnableContentDownloading,
            enableAllFolders = u.Policy.EnableAllFolders,
            enabledFolders = u.Policy.EnabledFolders,
        )
    }.sortedByDescending { it.lastActivity }
}

suspend fun jellyfinLibraries(config: ServiceConfig): List<JellyLibrary> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    runCatching {
        jfApi(config, token).virtualFolders().map {
            JellyLibrary(id = it.ItemId, name = it.Name, collectionType = it.CollectionType ?: "", locations = it.Locations)
        }
    }.getOrDefault(emptyList())
}

/** Create a user (optionally with an initial password). */
suspend fun jellyfinCreateUser(config: ServiceConfig, name: String, password: String): String = destructive("create Jellyfin user $name") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val body = buildJsonObject {
                put("Name", name)
                if (password.isNotBlank()) put("Password", password)
            }
            okOr(jfApi(config, token).createUser(body), "user created")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinDeleteUser(config: ServiceConfig, userId: String): String = destructive("delete Jellyfin user $userId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).deleteUser(userId), "user deleted")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/**
 * Update a user's policy. Fetches the current full policy first and overlays only the
 * fields we manage, so nothing else on the policy gets reset.
 */
suspend fun jellyfinSetPolicy(
    config: ServiceConfig,
    userId: String,
    admin: Boolean,
    disabled: Boolean,
    allowDownloads: Boolean,
    enableAllFolders: Boolean,
    enabledFolders: List<String>,
): String = destructive("change permissions of Jellyfin user $userId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val api = jfApi(config, token)
            val current = runCatching { api.user(userId)["Policy"]?.jsonObject }.getOrNull()
            val body = buildJsonObject {
                current?.forEach { (k, v) -> put(k, v) }
                put("IsAdministrator", admin)
                put("IsDisabled", disabled)
                put("EnableContentDownloading", allowDownloads)
                put("EnableAllFolders", enableAllFolders)
                putJsonArray("EnabledFolders") { enabledFolders.forEach { add(it) } }
            }
            okOr(api.setPolicy(userId, body), "policy saved")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Reset a user's password to a new value (admin reset; no current password needed). */
suspend fun jellyfinSetPassword(config: ServiceConfig, userId: String, newPassword: String): String = destructive("change the password of Jellyfin user $userId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val api = jfApi(config, token)
            // First clear the existing password, then set the new one (Jellyfin admin-reset flow).
            api.setPassword(userId, buildJsonObject { put("ResetPassword", true) })
            val body = buildJsonObject {
                put("NewPw", newPassword)
                put("ResetPassword", false)
            }
            okOr(api.setPassword(userId, body), "password reset")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

// ---- Media browsing ----

/** Resolve the user id whose libraries we browse (the logged-in user, else an admin). */
internal suspend fun jellyfinResolveUserId(config: ServiceConfig, api: JellyfinApi): String {
    // A token obtained by signing in (TV setup) already knows its user, no lookup, and no reliance
    // on `/Users`, which a non-admin token may not be allowed to read in full.
    if (config.userId.isNotBlank()) return config.userId
    jellyfinUserIdCache[config.id]?.let { return it }
    val users = api.users()
    val chosen = if (config.useLogin) {
        users.firstOrNull { it.Name.equals(config.username, true) } ?: users.firstOrNull()
    } else {
        users.firstOrNull { it.Policy.IsAdministrator } ?: users.firstOrNull()
    }
    val id = chosen?.Id ?: ""
    if (id.isNotBlank()) jellyfinUserIdCache[config.id] = id
    return id
}

suspend fun jellyfinSystemInfo(config: ServiceConfig): JellySystemInfo = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val i = jfApi(config, token).systemInfo()
    JellySystemInfo(version = i.Version, serverName = i.ServerName, os = i.OperatingSystem)
}

suspend fun jellyfinTasks(config: ServiceConfig): List<JellyTask> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).scheduledTasks().map { t ->
        JellyTask(
            id = t.Id,
            name = t.Name,
            state = t.State,
            progress = (t.CurrentProgressPercentage ?: 0.0).toInt(),
            lastResult = t.LastExecutionResult?.Status ?: "",
            lastRun = formatJellyDate(t.LastExecutionResult?.EndTimeUtc),
        )
    }.sortedBy { it.name }
}

suspend fun jellyfinRunTask(config: ServiceConfig, taskId: String): String = destructive("run Jellyfin task $taskId") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).runTask(taskId), "started")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinActivity(config: ServiceConfig): List<JellyActivity> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).activityLog().Items.map { e ->
        JellyActivity(
            name = e.Name,
            date = e.Date.take(16).replace('T', ' '),
            severity = e.Severity,
            overview = e.ShortOverview ?: "",
        )
    }
}

suspend fun jellyfinDevices(config: ServiceConfig): List<JellyDevice> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).devices().Items.map { d ->
        JellyDevice(
            name = d.Name.ifBlank { "?" },
            app = d.AppName,
            user = d.LastUserName ?: "",
            lastActivity = (d.DateLastActivity ?: "").take(16).replace('T', ' '),
        )
    }.sortedByDescending { it.lastActivity }
}

suspend fun jellyfinRestart(config: ServiceConfig): String = destructive("restart the Jellyfin server") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).restartServer(), "restarting")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinLogFiles(config: ServiceConfig): List<JellyLogFile> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).logFiles().map {
        JellyLogFile(
            name = it.Name,
            date = it.DateModified.take(16).replace('T', ' ').drop(5),
            size = humanSize(it.Size),
        )
    }.sortedByDescending { it.date }
}

/** Returns the tail of a server log file (last [maxLines] lines. Files can be several MB). */
suspend fun jellyfinLogContent(config: ServiceConfig, name: String, maxLines: Int = 400): String = withContext(Dispatchers.IO) {
    try {
        val token = jellyfinAccessToken(config)
        val resp = jfApi(config, token).logContent(name)
        if (!resp.isSuccessful) return@withContext "error: HTTP ${resp.code()}"
        val text = resp.body()?.string() ?: return@withContext "error: empty response"
        val lines = text.lines()
        if (lines.size <= maxLines) text
        else "… (${lines.size - maxLines} earlier lines truncated)\n" + lines.takeLast(maxLines).joinToString("\n")
    } catch (t: Throwable) {
        "error: ${t.message ?: t.javaClass.simpleName}"
    }
}

suspend fun jellyfinAddLibrary(config: ServiceConfig, name: String, collectionType: String, path: String): String = destructive("add Jellyfin library $name") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).addVirtualFolder(name, collectionType.ifBlank { null }, listOf(path)), "library added")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinDeleteLibrary(config: ServiceConfig, name: String): String = destructive("delete Jellyfin library $name") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).deleteVirtualFolder(name), "library deleted")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinRenameLibrary(config: ServiceConfig, name: String, newName: String): String = destructive("rename Jellyfin library $name to $newName") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).renameVirtualFolder(name, newName), "library renamed")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinAddLibraryPath(config: ServiceConfig, libraryName: String, path: String): String = destructive("add path $path to Jellyfin library $libraryName") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).addLibraryPath(JfMediaPath(Name = libraryName, PathInfo = JfMediaPathInfo(Path = path))), "path added")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinRemoveLibraryPath(config: ServiceConfig, libraryName: String, path: String): String = destructive("remove path $path from Jellyfin library $libraryName") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).removeLibraryPath(libraryName, path), "path removed")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinPlugins(config: ServiceConfig): List<JellyPlugin> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    jfApi(config, token).plugins().map {
        JellyPlugin(
            id = it.Id,
            version = it.Version,
            name = it.Name,
            description = it.Description,
            status = it.Status,
            canUninstall = it.CanUninstall,
        )
    }.sortedBy { it.name.lowercase() }
}

suspend fun jellyfinSetPluginEnabled(config: ServiceConfig, id: String, version: String, enabled: Boolean): String = destructive("toggle Jellyfin plugin $id") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val api = jfApi(config, token)
            okOr(if (enabled) api.enablePlugin(id, version) else api.disablePlugin(id, version), if (enabled) "plugin enabled (restart server to apply)" else "plugin disabled (restart server to apply)")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinUninstallPlugin(config: ServiceConfig, id: String, version: String): String = destructive("uninstall Jellyfin plugin $id") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).uninstallPlugin(id, version), "plugin uninstalled (restart server to apply)")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** The server's plugin catalog, with installed ones flagged. */
suspend fun jellyfinPackages(config: ServiceConfig): List<JellyPackage> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val installed = runCatching { api.plugins().map { it.Name.lowercase() }.toSet() }.getOrDefault(emptySet())
    api.packages().map {
        JellyPackage(
            name = it.name,
            guid = it.guid,
            description = it.description.ifBlank { it.overview },
            version = it.versions.firstOrNull()?.version ?: "",
            installed = it.name.lowercase() in installed,
        )
    }.sortedBy { it.name.lowercase() }
}

suspend fun jellyfinInstallPackage(config: ServiceConfig, name: String, guid: String): String = destructive("install Jellyfin plugin $name") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).installPackage(name, guid), "installing… (restart server when done)")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinLiveTv(config: ServiceConfig): JellyLiveTv = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val info = api.liveTvInfo()
    val opts = runCatching { api.liveTvOptions() }.getOrDefault(JfLiveTvOptions())
    JellyLiveTv(
        enabled = info.IsEnabled,
        services = info.Services.map { s ->
            buildString {
                append(s.Name.ifBlank { "Live TV" })
                append(": ").append(s.Status.ifBlank { "?" })
                append(" (${s.Tuners.size} tuner${if (s.Tuners.size == 1) "" else "s"})")
                if (!s.StatusMessage.isNullOrBlank()) append(" · ${s.StatusMessage}")
            }
        },
        tuners = opts.TunerHosts.map {
            JellyTuner(id = it.Id, name = it.FriendlyName?.takeIf { n -> n.isNotBlank() } ?: it.Type, type = it.Type, url = it.Url)
        },
        providers = opts.ListingProviders.map {
            JellyGuideProvider(id = it.Id, type = it.Type, path = it.Path ?: it.ListingsId ?: "")
        },
    )
}

suspend fun jellyfinChannels(config: ServiceConfig): List<JellyChannel> = withContext(Dispatchers.IO) {
    val token = jellyfinAccessToken(config)
    val api = jfApi(config, token)
    val uid = runCatching { jellyfinResolveUserId(config, api) }.getOrNull()
    api.liveTvChannels(userId = uid).Items.map {
        JellyChannel(
            id = it.Id,
            number = it.ChannelNumber ?: "",
            name = it.Name,
            nowPlaying = it.CurrentProgram?.Name ?: "",
        )
    }
}

suspend fun jellyfinAddTuner(config: ServiceConfig, type: String, url: String): String = destructive("add Jellyfin tuner at ${hostOnly(url)}") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val body = buildJsonObject { put("Type", type); put("Url", url) }
            okOr(jfApi(config, token).addTunerHost(body), "tuner added")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinDeleteTuner(config: ServiceConfig, id: String): String = destructive("delete Jellyfin tuner $id") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).deleteTunerHost(id), "tuner removed")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

/** Adds an XMLTV guide provider (file path or URL). */
suspend fun jellyfinAddXmltvProvider(config: ServiceConfig, path: String): String = destructive("add Jellyfin guide provider $path") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            val body = buildJsonObject { put("Type", "xmltv"); put("Path", path) }
            okOr(jfApi(config, token).addListingProvider(body), "guide provider added")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}

suspend fun jellyfinDeleteProvider(config: ServiceConfig, id: String): String = destructive("delete Jellyfin guide provider $id") {
    withContext(Dispatchers.IO) {
        try {
            val token = jellyfinAccessToken(config)
            okOr(jfApi(config, token).deleteListingProvider(id), "guide provider removed")
        } catch (t: Throwable) {
            "error: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}
