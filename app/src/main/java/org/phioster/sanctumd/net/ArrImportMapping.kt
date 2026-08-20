package org.phioster.sanctumd.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.phioster.sanctumd.model.ServiceType

/**
 * How a scanned manual-import row is read, and what the import command is told about it.
 *
 * This used to sit inline in the scan and execute calls, where it could not be tested — and that
 * is exactly where a Sonarr row once carried a Radarr `movie` object, producing a command with
 * neither `seriesId` nor `episodeIds`: a button that looked like it worked and did nothing.
 */
internal fun importMatchLabel(type: ServiceType, o: JsonObject): String = ""

internal fun importHasMatch(type: ServiceType, o: JsonObject): Boolean = false

internal fun importFileBody(type: ServiceType, o: JsonObject): JsonObject = JsonObject(emptyMap())
