package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.demo.DemoWorld
import org.phioster.sanctumd.model.ServiceType
import java.io.File

class ArrToolsTest {
    private fun o(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun `a rename row names the episode and the file`() {
        val r = renameOf(ServiceType.SONARR, o("""{"seasonNumber":2,"episodeNumbers":[3,4],"episodeFileId":77,"existingPath":"a.mkv","newPath":"b.mkv"}"""))
        assertEquals(ArrRename(77, "S02 E03,04", "a.mkv", "b.mkv"), r)
        assertEquals(12, renameOf(ServiceType.RADARR, o("""{"movieFileId":12,"existingPath":"x","newPath":"y"}""")).fileId)
        assertEquals("track 5", renameOf(ServiceType.LIDARR, o("""{"trackNumbers":[5],"trackFileId":3}""")).label)
    }

    @Test fun `tag usage reads like a sentence`() {
        val t = tagOf(o("""{"id":4,"label":"4k","seriesIds":[1,2,3],"indexerIds":[9],"notificationIds":[]}"""))
        assertEquals("3 series · 1 indexers", t.uses)
        assertEquals(4, t.useCount)
        assertEquals("", tagOf(o("""{"id":1,"label":"x"}""")).uses)
    }

    @Test fun `tag labels are cleaned the way the arrs accept them`() {
        assertEquals("german-dub", cleanTagLabel(" German Dub "))
        assertEquals("4k", cleanTagLabel("4K!"))
        assertEquals("", cleanTagLabel("  "))
    }

    @Test fun `the tag editor body fits each service`() {
        val (path, body) = tagEditorBody(ServiceType.SONARR, listOf(5), listOf(1, 2), "replace")
        assertEquals("series/editor", path)
        assertEquals(JsonArray(listOf(JsonPrimitive(5))), body["seriesIds"])
        assertEquals(JsonPrimitive("replace"), body["applyTags"])
        assertEquals("movie/editor", tagEditorBody(ServiceType.RADARR, listOf(1), emptyList(), "replace").first)
        assertEquals("artist/editor", tagEditorBody(ServiceType.LIDARR, listOf(1), emptyList(), "replace").first)
        val (pp, pb) = tagEditorBody(ServiceType.PROWLARR, listOf(3), listOf(1), "replace")
        assertEquals("indexer/bulk", pp)
        assertTrue("ids" in pb)
    }

    @Test fun `test results come from a 400 body as well`() {
        val r = testResults("""[{"id":1,"isValid":true,"validationFailures":[]},{"id":2,"isValid":false,"validationFailures":[{"errorMessage":"refused"},{"errorMessage":"timeout"}]}]""")
        assertNull(r[1])
        assertEquals("refused; timeout", r[2])
        assertTrue(testResults("not json").isEmpty())
    }

    @Test fun `providers read the field names each arr uses`() {
        assertEquals(false, importListOf(o("""{"id":1,"name":"a","enabled":false}""")).enabled)
        assertEquals("adds automatically", importListOf(o("""{"id":1,"enableAutomaticAdd":true}""")).facts.first())
        assertEquals("adds automatically", importListOf(o("""{"id":1,"enableAuto":true}""")).facts.first())
        assertEquals(listOf("usenet", "priority 1"), downloadClientOf(o("""{"id":1,"protocol":"usenet","priority":1}""")).facts)
        assertEquals("no tags: used by no indexer", proxyOf(o("""{"id":1,"tags":[]}""")).facts.single())
    }

    @Test fun `the demo answers every dialog through the real code`() = runBlocking {
        DemoWorld.assetReader = { name -> File("src/main/assets/demo/$name").readText() }
        val sonarr = DemoMode.services.first { it.type == ServiceType.SONARR }
        val radarr = DemoMode.services.first { it.type == ServiceType.RADARR }
        val prowlarr = DemoMode.services.first { it.type == ServiceType.PROWLARR }
        assertTrue(arrTags(sonarr).first { it.label == "4k" }.uses.contains("series"))
        assertTrue(arrRenamePreview(sonarr, 1).isNotEmpty())
        assertTrue(arrRenamePreview(radarr, 1).single().renamed.endsWith(".mkv"))
        assertEquals(3, arrImportLists(radarr).size)
        val tests = arrTestDownloadClients(radarr)
        assertNull(tests[1])
        assertTrue(tests[2]!!.contains("refused"))
        assertEquals(2, prowlarrProxies(prowlarr).size)
        assertEquals(listOf(1), arrItemTags(prowlarr, 3))
    }
}
