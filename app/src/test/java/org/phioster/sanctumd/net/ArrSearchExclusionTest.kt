package org.phioster.sanctumd.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/** Checked against the Sonarr/Radarr/Lidarr sources and the live Sonarr on 2026-11-07. */
class ArrSearchExclusionTest {
    @Test fun `sonarr searches one series by its singular seriesId`() {
        val b = librarySearchBody(ServiceType.SONARR, 12)
        assertEquals(JsonPrimitive("SeriesSearch"), b["name"])
        assertEquals(JsonPrimitive(12), b["seriesId"])
        assertNull(b["seriesIds"])
    }

    @Test fun `lidarr searches one artist by its singular artistId`() {
        val b = librarySearchBody(ServiceType.LIDARR, 3)
        assertEquals(JsonPrimitive("ArtistSearch"), b["name"])
        assertEquals(JsonPrimitive(3), b["artistId"])
        assertNull(b["artistIds"])
    }

    @Test fun `radarr keeps its movieIds list`() {
        val b = librarySearchBody(ServiceType.RADARR, 7)
        assertEquals(JsonPrimitive("MoviesSearch"), b["name"])
        assertEquals(JsonArray(listOf(JsonPrimitive(7))), b["movieIds"])
    }

    @Test fun `the exclusion flag has each service's own name`() {
        assertEquals("addImportExclusion", exclusionParam(ServiceType.RADARR))
        assertEquals("addImportListExclusion", exclusionParam(ServiceType.SONARR))
        assertEquals("addImportListExclusion", exclusionParam(ServiceType.LIDARR))
    }
}
