package org.phioster.sanctumd.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.phioster.sanctumd.model.NzbHistoryEntry
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * NZBGet refuses an NZB whose content sits in its hidden duplicate history. The block list is
 * that history by name; unblocking clears every entry of one name in a single call.
 */
class NzbgetBlockListTest {

    private lateinit var server: MockWebServer

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        SafeMode.enabled = false
    }
    @After fun stop() {
        server.shutdown()
        SafeMode.enabled = false
    }

    private fun config() = ServiceConfig(
        id = "nzb", type = ServiceType.NZBGET, label = "NZBGet",
        baseUrl = server.url("/").toString(), username = "nzbadmin", password = "hunter2",
    )

    private val history = listOf(
        NzbHistoryEntry(781, "Film.2006.DVDRip", "DELETED/MANUAL", 810, kind = "DUP", time = 300),
        NzbHistoryEntry(493, "Film.2006.DVDRip", "SUCCESS/HIDDEN", 810, kind = "DUP", time = 100),
        NzbHistoryEntry(500, "Other.Show.S01E01", "FAILURE/HIDDEN", 400, kind = "DUP", time = 200),
        NzbHistoryEntry(900, "Still.Visible.2024", "SUCCESS/ALL", 2000, kind = "NZB", time = 400),
    )

    private val historyJson = """{"result":[
        {"NZBID":781,"Kind":"DUP","Name":"Film.2006.DVDRip","Status":"DELETED/MANUAL","FileSizeMB":810,"HistoryTime":300},
        {"NZBID":493,"Kind":"DUP","Name":"film.2006.dvdrip","Status":"SUCCESS/HIDDEN","FileSizeMB":810,"HistoryTime":100},
        {"NZBID":900,"Kind":"NZB","Name":"Still.Visible.2024","Status":"SUCCESS/ALL","FileSizeMB":2000,"HistoryTime":400}]}"""

    @Test fun `one row per name, newest first, visible history left out`() {
        val blocks = nzbBlocks(history)
        assertEquals(listOf("Film.2006.DVDRip", "Other.Show.S01E01"), blocks.map { it.name })
        assertEquals(listOf(781, 493), blocks.first().ids)
        assertEquals("DELETED/MANUAL", blocks.first().status)
    }

    @Test fun `unblocking takes every hidden entry of the name and the row that asked`() {
        assertEquals(listOf(781, 493), nzbUnblockIds(history, "film.2006.dvdrip"))
        assertEquals(listOf(900), nzbUnblockIds(history, "Still.Visible.2024", alsoId = 900))
        assertEquals(listOf(781, 493), nzbUnblockIds(history, "Film.2006.DVDRip", alsoId = 781))
    }

    @Test fun `the history carries kind and time`() = runBlocking {
        server.enqueue(MockResponse().setBody(historyJson))
        val h = nzbgetHistory(config(), hidden = true)
        assertEquals(listOf("DUP", "DUP", "NZB"), h.map { it.kind })
        assertEquals(300L, h.first().time)
    }

    @Test fun `unblock deletes all entries for good in one call`() = runBlocking {
        server.enqueue(MockResponse().setBody(historyJson))
        server.enqueue(MockResponse().setBody("""{"result":true}"""))
        assertEquals("unblocked (2 entries)", runNzbUnblock(config(), "Film.2006.DVDRip"))
        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("HistoryFinalDelete"))
        assertTrue(body, body.contains("[781,493]"))
    }

    @Test fun `nothing to unblock sends no delete`() = runBlocking {
        server.enqueue(MockResponse().setBody(historyJson))
        assertEquals("nothing is blocking this release", runNzbUnblock(config(), "Unknown.Release"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `safe mode keeps unblock off the network`() = runBlocking {
        SafeMode.enabled = true
        runNzbUnblock(config(), "Film.2006.DVDRip")
        assertEquals(0, server.requestCount)
    }
}
