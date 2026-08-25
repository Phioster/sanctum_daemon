package org.phioster.sanctumd.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/**
 * Pins the trickplay maths the ambient glow rides on: which sheet and which cell a playback
 * position lands in, which width variant gets picked, and the tile URL. Getting the row/column
 * order wrong shows up as a glow that jumps to the wrong scene, which is easy to miss by eye.
 */
class TrickplayTest {

    private val info = TrickplayInfo(
        width = 320, height = 180, tileWidth = 10, tileHeight = 10,
        thumbnailCount = 734, intervalMs = 10_000,
    )

    @Test fun `thumbnail index follows the interval`() {
        assertEquals(0, info.thumbIndexAt(0))
        assertEquals(0, info.thumbIndexAt(9_999))
        assertEquals(1, info.thumbIndexAt(10_000))
        assertEquals(9, info.thumbIndexAt(95_000))
    }

    @Test fun `index is clamped to what the server generated`() {
        assertEquals(733, info.thumbIndexAt(99_000_000))
    }

    @Test fun `sheet and cell are row major`() {
        assertEquals(1, info.sheetIndexOf(137))
        assertEquals(7 to 3, info.cellOf(137))
        assertEquals(0, info.sheetIndexOf(0))
        assertEquals(0 to 0, info.cellOf(0))
        assertEquals(9 to 9, info.cellOf(99))
    }

    @Test fun `parses the widest small variant`() {
        val item = Json.parseToJsonElement(
            """
            {"Name":"Movie","Trickplay":{"src-1":{
              "320":{"Width":320,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":734,"Interval":10000},
              "640":{"Width":640,"Height":360,"TileWidth":10,"TileHeight":10,"ThumbnailCount":734,"Interval":10000}
            }}}
            """.trimIndent(),
        ).jsonObject
        assertEquals(320, parseTrickplay(item)?.width)
        assertEquals(640, parseTrickplay(item, maxWidth = 1080)?.width)
    }

    @Test fun `no trickplay on the item is not an error`() {
        assertNull(parseTrickplay(Json.parseToJsonElement("""{"Name":"Movie"}""").jsonObject))
        assertNull(parseTrickplay(Json.parseToJsonElement("""{"Trickplay":{}}""").jsonObject))
    }

    @Test fun `incomplete trickplay entries are rejected`() {
        val item = Json.parseToJsonElement(
            """{"Trickplay":{"src-1":{"320":{"Width":320,"Height":180,"TileWidth":10,"TileHeight":10,"ThumbnailCount":0,"Interval":10000}}}}""",
        ).jsonObject
        assertNull(parseTrickplay(item))
    }

    @Test fun `tile url carries no token`() {
        val config = ServiceConfig(
            id = "jf", type = ServiceType.JELLYFIN, label = "Jellyfin",
            baseUrl = "https://media.example/", apiKey = "secret",
        )
        assertEquals(
            "https://media.example/Videos/abc/Trickplay/320/2.jpg",
            trickplayTileUrl(config, "abc", info, 2),
        )
    }
}
