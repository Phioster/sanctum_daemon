package org.phioster.sanctumd.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/** Shapes as the three services actually return them from `GET …/manualimport?folder=…`. */
class ArrImportMappingTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    private val radarrRow = obj(
        """{"path":"/dl/Film.mkv","quality":{"quality":{"name":"WEBDL-1080p"}},
            "movie":{"id":42,"title":"Wicked"},"rejections":[]}""",
    )
    private val sonarrRow = obj(
        """{"path":"/dl/Ep.mkv","quality":{"quality":{"name":"WEBDL-2160p"}},
            "series":{"id":7,"title":"Outer Banks"},
            "episodes":[{"id":901,"seasonNumber":5,"episodeNumber":4}],"rejections":[]}""",
    )
    private val lidarrRow = obj(
        """{"path":"/dl/01.flac","quality":{"quality":{"name":"FLAC"}},
            "artist":{"id":3,"artistName":"Made Flesh"},
            "album":{"id":11,"title":"Untitled With Drums"},
            "albumReleaseId":55,
            "tracks":[{"id":501,"trackNumber":"1"},{"id":502,"trackNumber":"2"}],
            "rejections":[]}""",
    )

    @Test fun `a movie row is labelled by its title`() {
        assertEquals("Wicked", importMatchLabel(ServiceType.RADARR, radarrRow))
    }

    @Test fun `an episode row names the series and the episode`() {
        assertEquals("Outer Banks S05E04", importMatchLabel(ServiceType.SONARR, sonarrRow))
    }

    /** Lidarr's artist field is `artistName`, not `title` — reading `title` yields an empty label. */
    @Test fun `a track row names the artist and the album`() {
        assertEquals("Made Flesh — Untitled With Drums", importMatchLabel(ServiceType.LIDARR, lidarrRow))
    }

    @Test fun `a row is matched only when its own service found everything it needs`() {
        assertTrue(importHasMatch(ServiceType.RADARR, radarrRow))
        assertTrue(importHasMatch(ServiceType.SONARR, sonarrRow))
        assertTrue(importHasMatch(ServiceType.LIDARR, lidarrRow))
        // A Lidarr row without tracks cannot be imported: the command needs trackIds.
        assertFalse(importHasMatch(ServiceType.LIDARR, obj("""{"artist":{"id":3},"album":{"id":11},"tracks":[]}""")))
        assertFalse(importHasMatch(ServiceType.SONARR, obj("""{"series":{"id":7},"episodes":[]}""")))
        assertFalse(importHasMatch(ServiceType.RADARR, obj("""{"rejections":[]}""")))
    }

    @Test fun `a movie file is sent with its movieId`() {
        val b = importFileBody(ServiceType.RADARR, radarrRow)
        assertEquals(JsonPrimitive(42), b["movieId"])
        assertEquals(JsonPrimitive("/dl/Film.mkv"), b["path"])
    }

    @Test fun `an episode file is sent with its seriesId and episodeIds`() {
        val b = importFileBody(ServiceType.SONARR, sonarrRow)
        assertEquals(JsonPrimitive(7), b["seriesId"])
        assertEquals(listOf(JsonPrimitive(901)), (b["episodeIds"] as JsonArray).toList())
        assertEquals(null, b["movieId"])
    }

    /**
     * Lidarr needs all four: without `albumReleaseId` it cannot tell which edition of the album
     * the file belongs to, and without `trackIds` it has nothing to attach the file to.
     */
    @Test fun `a track file is sent with artist, album, release and tracks`() {
        val b = importFileBody(ServiceType.LIDARR, lidarrRow)
        assertEquals(JsonPrimitive(3), b["artistId"])
        assertEquals(JsonPrimitive(11), b["albumId"])
        assertEquals(JsonPrimitive(55), b["albumReleaseId"])
        assertEquals(listOf(JsonPrimitive(501), JsonPrimitive(502)), (b["trackIds"] as JsonArray).toList())
        assertEquals(null, b["movieId"])
    }

    /**
     * Lidarr judges each file on its own, so every file of a 20-track album is reported as
     * "Has missing tracks" — the other nineteen are missing *from that one file*. Measured against
     * a live Lidarr 3.1.3 on 2026-08-20: all 20 rows of a complete album carried it.
     *
     * Blocking on it would have shipped a manual import that never imports anything.
     */
    @Test fun `Lidarr's per-file missing-tracks warning does not block the import`() {
        assertTrue(importAllowed(ServiceType.LIDARR, listOf("Has missing tracks")))
    }

    @Test fun `a real Lidarr rejection still blocks`() {
        assertFalse(importAllowed(ServiceType.LIDARR, listOf("Unknown artist")))
        assertFalse(importAllowed(ServiceType.LIDARR, listOf("Has missing tracks", "Unknown artist")))
    }

    @Test fun `for the other services every rejection blocks, as before`() {
        assertTrue(importAllowed(ServiceType.RADARR, emptyList()))
        assertFalse(importAllowed(ServiceType.RADARR, listOf("Has missing tracks")))
        assertFalse(importAllowed(ServiceType.SONARR, listOf("Unknown series")))
    }
}
