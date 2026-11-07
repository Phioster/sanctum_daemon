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

class SyncCorrectionTest {
    @Test fun `drift is measured against where the group is now`() {
        // Group unpaused at 10 s position at t=1000; at t=4000 it is at 13 s.
        org.junit.Assert.assertEquals(500L, org.phioster.sanctumd.ui.player.syncDrift(13_500, 10_000, 1_000, 4_000))
        org.junit.Assert.assertEquals(-1_000L, org.phioster.sanctumd.ui.player.syncDrift(12_000, 10_000, 1_000, 4_000))
    }

    @Test fun `ahead slows down, behind speeds up, within limits`() {
        org.junit.Assert.assertEquals(0.75f, org.phioster.sanctumd.ui.player.catchUpSpeed(500), 0.001f)
        org.junit.Assert.assertEquals(1.5f, org.phioster.sanctumd.ui.player.catchUpSpeed(-1_000), 0.001f)
        org.junit.Assert.assertEquals(1.5f, org.phioster.sanctumd.ui.player.catchUpSpeed(-2_500), 0.001f)
    }
}
