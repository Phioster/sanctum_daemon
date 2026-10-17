package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.demo.DemoWorld
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.ui.nzbget.volumeLabel
import java.io.File

class NzbgetServersTest {
    private fun o(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun `sizes come back together from their two halves`() {
        assertEquals(5L * 1024 * 1024 * 1024, splitSize(o("""{"SizeLo":1073741824,"SizeHi":1}""")))
        assertEquals(4_294_967_295L, splitSize(o("""{"TotalSizeLo":4294967295,"TotalSizeHi":0}"""), "TotalSize"))
        assertEquals(0L, splitSize(o("{}")))
    }

    @Test fun `the day window ends at today's slot`() {
        val days = (1..10).toList()
        assertEquals(listOf(4, 5, 6), lastSlots(days, 5, 3))
        assertEquals(listOf(1, 2), lastSlots(days, 1, 7))
        assertEquals(listOf(8, 9, 10), lastSlots(days, 99, 3))
        assertTrue(lastSlots(emptyList<Int>(), 3, 3).isEmpty())
    }

    @Test fun `the request asks for days and articles only`() {
        val p = serverVolumesBody()["params"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("false", "false", "false", "true", "true"), p)
        assertEquals(listOf("3", "CUSTOM"), resetVolumeBody(3)["params"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun `server names come from the options and passwords are dropped`() {
        val map = configMap(Json.parseToJsonElement(
            """[{"Name":"Server1.Name","Value":"Eweka"},{"Name":"Server1.Password","Value":"x"},{"Name":"Server1.Username","Value":"u"},{"Name":"Server1.Host","Value":"h"}]""",
        ))
        assertEquals(setOf("Server1.Name", "Server1.Host"), map.keys)
        val v = volumeOf(o("""{"ServerID":1,"DaySlot":1,"BytesPerDays":[{"SizeLo":100,"SizeHi":0},{"SizeLo":50,"SizeHi":0}],
            "ArticlesPerDays":[{"Success":90,"Failed":10},{"Success":100,"Failed":0}],"TotalSizeLo":999,"TotalSizeHi":0,"CustomTime":7}"""), map)
        assertEquals("Eweka", v.name)
        assertEquals(50L, v.todayBytes)
        assertEquals(150L, v.weekBytes)
        assertEquals(999L, v.totalBytes)
        assertEquals(0.95, v.successRate!!, 0.0001)
        assertEquals("all servers", volumeOf(o("""{"ServerID":0}"""), map).name)
        assertEquals("server 4", volumeOf(o("""{"ServerID":4}"""), map).name)
        assertNull(volumeOf(o("""{"ServerID":4}"""), map).successRate)
        assertFalse(volumeOf(o("""{"ServerID":2}"""), mapOf("Server2.Active" to "no")).active)
    }

    @Test fun `only real nzb files get through`() {
        assertTrue(looksLikeNzb("""<?xml version="1.0"?><!DOCTYPE nzb><nzb xmlns="x"></nzb>""".toByteArray()))
        assertFalse(looksLikeNzb("PK\u0003\u0004 zip".toByteArray()))
        assertFalse(looksLikeNzb(ByteArray(0)))
        assertEquals("Movie.2024.nzb", nzbFileName("content://x/Movie.2024.nzb"))
        assertEquals("release.nzb", nzbFileName("release"))
        assertEquals("upload.nzb", nzbFileName(null))
    }

    @Test fun `the upload is base64 in the v13 parameter order`() {
        val p = appendFileBody("a.nzb", "<nzb/>".toByteArray(), "tv", true)["params"]!!.jsonArray
        assertEquals(JsonPrimitive("a.nzb"), p[0])
        assertEquals("<nzb/>", String(java.util.Base64.getDecoder().decode(p[1].jsonPrimitive.content)))
        assertEquals(JsonPrimitive("tv"), p[2])
        assertEquals(JsonPrimitive(true), p[5])
        assertEquals(9, p.size)
    }

    @Test fun `volumes read the way a person says them`() {
        assertEquals("812 MB", volumeLabel(812L * 1024 * 1024))
        assertEquals("23.5 GB", volumeLabel((23.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("0 B", volumeLabel(0))
    }

    @Test fun `the demo answers every part through the real code`() = runBlocking {
        DemoWorld.assetReader = { name -> File("src/main/assets/demo/$name").readText() }
        val nzb = DemoMode.services.first { it.type == ServiceType.NZBGET }
        val vols = nzbServerVolumes(nzb)
        assertEquals(listOf("all servers", "Newshosting", "Eweka", "Blocknews (fill)"), vols.map { it.name })
        assertEquals(30, vols[0].days.size)
        assertTrue(vols[0].monthBytes >= vols[1].monthBytes + vols[2].monthBytes)
        assertEquals(1, vols[3].level)
        assertTrue(vols[3].successRate!! < vols[1].successRate!!)
        assertEquals(listOf("movies", "tv", "music", "software"), nzbCategories(nzb))
        assertTrue(runNzbAppendFile(nzb, "x.nzb", "<nzb></nzb>".toByteArray(), "tv", false).startsWith("added"))
        assertTrue(runNzbAppendFile(nzb, "x.zip", "PK".toByteArray(), "", false).startsWith("error"))
        assertEquals("counter reset", runNzbResetVolume(nzb, 1, "Eweka"))
    }
}
