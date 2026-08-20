package org.phioster.sanctumd.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shape Radarr 6.3 actually returned on 2026-08-20, trimmed to the fields that are read. */
class ArrCollectionsTest {

    private val jackass = Json.parseToJsonElement(
        """{
          "id": 5, "title": "Jackass Collection", "tmdbId": 17178, "monitored": false,
          "qualityProfileId": 10, "rootFolderPath": "/storage/emulated/0/Movies",
          "missingMovies": 7,
          "movies": [
            {"title":"Jackass: The Movie","tmdbId":9843,"year":2002,"isExisting":true,"isExcluded":false},
            {"title":"Jackass Number Two","tmdbId":9844,"year":2006,"isExisting":false,"isExcluded":false},
            {"title":"Jackass 3D","tmdbId":38356,"year":2010,"isExisting":false,"isExcluded":true}
          ]
        }""",
    ).jsonObject

    @Test fun `a collection carries where Radarr would put a new film`() {
        val c = parseArrCollection(jackass)!!
        assertEquals(5, c.id)
        assertEquals("Jackass Collection", c.title)
        assertEquals(17178, c.tmdbId)
        assertEquals(10, c.qualityProfileId)
        assertEquals("/storage/emulated/0/Movies", c.rootFolderPath)
    }

    @Test fun `each film says whether it is already in the library`() {
        val c = parseArrCollection(jackass)!!
        assertEquals(3, c.movies.size)
        assertEquals(listOf(true, false, false), c.movies.map { it.existing })
        assertEquals("Jackass: The Movie", c.movies[0].title)
        assertEquals(2002, c.movies[0].year)
    }

    /** An excluded film was deliberately rejected before; offering to add it again would undo that. */
    @Test fun `an excluded film is not offered as missing`() {
        val c = parseArrCollection(jackass)!!
        assertEquals(listOf("Jackass Number Two"), c.missing.map { it.title })
    }

    @Test fun `a collection without a tmdb id cannot be joined to anything`() {
        assertNull(parseArrCollection(Json.parseToJsonElement("""{"id":1,"title":"x"}""").jsonObject))
    }

    @Test fun `a complete collection reports nothing missing`() {
        val done = Json.parseToJsonElement(
            """{"id":1,"title":"Vomit Gore Trilogy","tmdbId":331117,"movies":[
                 {"title":"a","tmdbId":1,"isExisting":true},{"title":"b","tmdbId":2,"isExisting":true}]}""",
        ).jsonObject
        assertTrue(parseArrCollection(done)!!.missing.isEmpty())
    }
}
