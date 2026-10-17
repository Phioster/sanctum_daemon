package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.demo.DemoWorld
import org.phioster.sanctumd.model.ServiceType
import java.io.File

class JellyfinSettingsTest {
    private fun o(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun `a nested change keeps every other field, known or not`() {
        val before = o("""{"ServerName":"a","Unknown":{"x":1},"TrickplayOptions":{"Interval":10000,"Other":true}}""")
        val after = before.with(listOf("TrickplayOptions", "Interval"), JsonPrimitive(5000))
        assertEquals(JsonPrimitive(5000), after.at(listOf("TrickplayOptions", "Interval")))
        assertEquals(JsonPrimitive(true), after.at(listOf("TrickplayOptions", "Other")))
        assertEquals(before["Unknown"], after["Unknown"])
        assertEquals(listOf("TrickplayOptions.Interval"), changedKeys(before, after))
        assertTrue(changedKeys(before, before).isEmpty())
    }

    @Test fun `a missing nested object is created`() {
        val after = JsonObject(emptyMap()).with(listOf("A", "B"), JsonPrimitive(1))
        assertEquals(JsonPrimitive(1), after.at(listOf("A", "B")))
    }

    @Test fun `choices keep the json type the server stored`() {
        assertEquals(JsonPrimitive(30L), typedLike(JsonPrimitive(0), "30"))
        assertEquals(JsonPrimitive("30"), typedLike(JsonPrimitive("0"), "30"))
        assertEquals(JsonPrimitive("qsv"), typedLike(null, "qsv"))
    }

    @Test fun `number and list fields reject what doesn't fit`() {
        val f = JfField.Number("H264Crf", "crf", min = 0.0, max = 51.0)
        assertEquals(JsonPrimitive(23L), parseFormNumber(f, "23"))
        assertNull(parseFormNumber(f, "52"))
        assertNull(parseFormNumber(f, "abc"))
        assertEquals(JsonPrimitive(2.5), parseFormNumber(JfField.Number("B", "b", max = 3.0, decimal = true), "2,5"))
        assertEquals(JsonArray(listOf(JsonPrimitive(320), JsonPrimitive(480))), parseIntList("320, 480"))
        assertNull(parseIntList("320, x"))
    }

    private val available = o("""{"TypeOptions":[{"Type":"Movie",
        "MetadataFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true},{"Name":"OMDb","DefaultEnabled":false}],
        "ImageFetchers":[{"Name":"Fanart","DefaultEnabled":true}],"DefaultImageOptions":[{"Type":"Primary","Limit":1}]}]}""")

    @Test fun `providers never stored use the server defaults`() {
        val f = fetchersOf(JsonObject(emptyMap()), available)
        val meta = f.first { it.kind == "Metadata" }
        assertEquals(listOf("TheMovieDb", "OMDb"), meta.order)
        assertEquals(setOf("TheMovieDb"), meta.enabled)
    }

    @Test fun `stored provider order and choice win, and write back as the server expects`() {
        val stored = o("""{"Keep":1,"TypeOptions":[{"Type":"Movie","MetadataFetchers":["OMDb"],"MetadataFetcherOrder":["OMDb","TheMovieDb"],"ImageOptions":[]}]}""")
        val meta = fetchersOf(stored, available).first { it.kind == "Metadata" }
        assertEquals(listOf("OMDb", "TheMovieDb"), meta.order)
        assertEquals(setOf("OMDb"), meta.enabled)

        val written = stored.withFetchers(meta.copy(order = listOf("TheMovieDb", "OMDb"), enabled = setOf("TheMovieDb", "OMDb")), available)
        val entry = (written["TypeOptions"] as JsonArray).single().jsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive("TheMovieDb"), JsonPrimitive("OMDb"))), entry["MetadataFetchers"])
        assertEquals(JsonArray(listOf(JsonPrimitive("TheMovieDb"), JsonPrimitive("OMDb"))), entry["MetadataFetcherOrder"])
        assertEquals(JsonPrimitive(1), written["Keep"])
    }

    @Test fun `a new type entry carries the image defaults`() {
        val f = fetchersOf(JsonObject(emptyMap()), available).first { it.kind == "Image" }
        val written = JsonObject(emptyMap()).withFetchers(f, available)
        val entry = (written["TypeOptions"] as JsonArray).single().jsonObject
        assertEquals(available.at(listOf("TypeOptions"))!!.let { (it as JsonArray)[0].jsonObject["DefaultImageOptions"] }, entry["ImageOptions"])
        assertEquals(JsonPrimitive("Movie"), entry["Type"])
    }

    @Test fun `library fields follow the library type`() {
        val series = LIBRARY_SETTINGS.flatMap { it.fields }.first { it.key == "EnableAutomaticSeriesGrouping" }
        assertTrue(series.appliesTo("tvshows"))
        assertFalse(series.appliesTo("movies"))
        assertTrue(LIBRARY_SETTINGS.flatMap { it.fields }.first { it.key == "EnableRealtimeMonitor" }.appliesTo("music"))
    }

    @Test fun `every catalog key is unique`() {
        listOf(SERVER_SETTINGS, ENCODING_SETTINGS, LIBRARY_SETTINGS).forEach { c ->
            val keys = c.flatMap { s -> s.fields.map { it.key } }
            assertEquals(keys.size, keys.toSet().size)
        }
    }

    @Test fun `the demo server's settings load through the real code`() = runBlocking {
        DemoWorld.assetReader = { name -> File("src/main/assets/demo/$name").readText() }
        val jf = DemoMode.services.first { it.type == ServiceType.JELLYFIN }
        assertEquals("qsv", jellyfinEncodingSettings(jf).at(listOf("HardwareAccelerationType")).formText())
        assertEquals("de", jellyfinServerSettings(jf).at(listOf("PreferredMetadataLanguage")).formText())
        val lib = jellyfinLibraryOptions(jf, "lib-tv", "tvshows")
        assertTrue(lib.options.at(listOf("EnableRealtimeMonitor")).formBool())
        assertTrue(fetchersOf(lib.options, lib.available).any { it.type == "Episode" })
        val src = jellyfinChoiceSources(jf, lib.available)
        assertTrue(src.lists["cultures"].orEmpty().any { it.first == "de" })
        assertTrue(src.lists["cultures3"].orEmpty().any { it.first == "ger" })
        assertEquals(listOf("Nfo" to "Nfo"), src.lists["savers"])
    }
}
