package org.phioster.sanctumd.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlayTest {
    @Test fun `clock offset and round trip from one exchange`() {
        // Phone is 500 ms behind the server; 40 ms each way, 10 ms on the server.
        val (offset, rtt) = clockSample(sentAt = 1_000, serverReceived = 1_540, serverSent = 1_550, receivedAt = 1_090)
        assertEquals(500L, offset)
        assertEquals(80L, rtt)
    }

    @Test fun `the playing entry of a queue update`() {
        val d = Json.parseToJsonElement(
            """{"Reason":"NewPlaylist","Playlist":[{"ItemId":"a","PlaylistItemId":"p1"},{"ItemId":"b","PlaylistItemId":"p2"}],
               "PlayingItemIndex":1,"StartPositionTicks":1200000000,"IsPlaying":true}""",
        ).jsonObject
        assertEquals(SyncQueue("b", "p2", 120_000, true), syncQueueOf(d))
    }

    @Test fun `a group as the server lists it`() {
        val g = syncGroupOf(Json.parseToJsonElement("""{"GroupId":"g","GroupName":"Kino","State":"Idle","Participants":["SekVII","Missi"]}""").jsonObject)
        assertEquals(SyncGroup("g", "Kino", listOf("SekVII", "Missi"), "Idle"), g)
    }
}
