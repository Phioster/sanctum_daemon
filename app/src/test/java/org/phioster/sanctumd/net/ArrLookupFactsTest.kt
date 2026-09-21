package org.phioster.sanctumd.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The lookup answer is what the info screen shows about a title nobody has added yet, and the
 * three services word the same facts differently, a flat rating here, a nested one there.
 * These are the readings that turn either shape into one line of text.
 */
class ArrLookupFactsTest {

    private fun obj(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    @Test
    fun `a runtime reads as hours and minutes`() {
        assertEquals("", arrRuntime(0))
        assertEquals("", arrRuntime(-5))
        assertEquals("47m", arrRuntime(47))
        assertEquals("1h", arrRuntime(60))
        assertEquals("2h 22m", arrRuntime(142))
        assertEquals("3h", arrRuntime(180))
    }

    @Test
    fun `Radarr nests a rating per source`() {
        assertEquals("7.4", arrRating(obj("""{"ratings":{"tmdb":{"value":7.4,"votes":900}}}""")))
    }

    @Test
    fun `Sonarr and Lidarr answer with a flat rating`() {
        assertEquals("8.6", arrRating(obj("""{"ratings":{"votes":120,"value":8.6}}""")))
    }

    @Test
    fun `a rotten tomatoes score is a percentage`() {
        assertEquals("91%", arrRating(obj("""{"ratings":{"rottenTomatoes":{"value":91}}}""")))
    }

    @Test
    fun `no rating at all stays empty`() {
        assertEquals("", arrRating(obj("""{"title":"x"}""")))
        assertEquals("", arrRating(obj("""{"ratings":{}}""")))
        assertEquals("", arrRating(obj("""{"ratings":{"tmdb":{"value":0}}}""")))
    }

    @Test
    fun `a camelCase status turns into words`() {
        assertEquals("in cinemas", arrStatusLabel("inCinemas"))
        assertEquals("continuing", arrStatusLabel("continuing"))
        assertEquals("", arrStatusLabel(""))
    }

    @Test
    fun `the poster prefers the remote copy a lookup hit actually has`() {
        val images = """{"images":[{"coverType":"fanart","remoteUrl":"https://f"},{"coverType":"poster","remoteUrl":"https://p","url":"/local.jpg"}]}"""
        assertEquals("https://p", arrImageUrl(obj(images)))
        assertEquals("https://f", arrImageUrl(obj(images), "fanart"))
        assertEquals("/only-local.jpg", arrImageUrl(obj("""{"images":[{"coverType":"poster","url":"/only-local.jpg"}]}""")))
        assertEquals("", arrImageUrl(obj("""{"title":"x"}""")))
    }

    @Test
    fun `genres read as one line`() {
        assertEquals("Drama, Thriller", arrGenreLine(obj("""{"genres":["Drama","Thriller"]}""")))
        assertEquals("", arrGenreLine(obj("""{"genres":[]}""")))
        assertEquals("", arrGenreLine(obj("""{"title":"x"}""")))
    }
}
