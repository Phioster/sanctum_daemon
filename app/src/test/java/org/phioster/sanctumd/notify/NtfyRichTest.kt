package org.phioster.sanctumd.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pictures, tags and Markdown in ntfy messages (fields from ntfy's JSON message format). */
class NtfyRichTest {

    private fun line(extra: String) = """{"id":"a","time":1,"event":"message","topic":"t","message":"Body"$extra}"""

    @Test fun `an image attachment is the picture, else the icon`() {
        assertEquals(
            "https://ntfy.example/file/x.jpg",
            parseNtfyLine(line(""","attachment":{"name":"x.jpg","type":"image/jpeg","url":"https://ntfy.example/file/x.jpg"},"icon":"https://i/icon.png""""))!!.imageUrl,
        )
        assertEquals("https://i/icon.png", parseNtfyLine(line(""","attachment":{"name":"a.pdf","type":"application/pdf","url":"https://n/a.pdf"},"icon":"https://i/icon.png""""))!!.imageUrl)
        assertEquals("https://p/poster.webp?w=3", parseNtfyLine(line(""","attachment":{"url":"https://p/poster.webp?w=3"}"""))!!.imageUrl)
    }

    @Test fun `only http addresses become pictures`() {
        assertEquals("", parseNtfyLine(line(""","icon":"file:///sdcard/x.png""""))!!.imageUrl)
        assertEquals("", parseNtfyLine(line(""))!!.imageUrl)
    }

    @Test fun `tags and markdown are read`() {
        val m = parseNtfyLine(line(""","tags":["warning","radarr"],"content_type":"text/markdown""""))!!
        assertEquals(listOf("warning", "radarr"), m.tags)
        assertTrue(m.markdown)
        assertFalse(parseNtfyLine(line(""))!!.markdown)
    }

    @Test fun `emoji tags lead the title, the rest stay labels`() {
        assertEquals("⚠️🔥 ", ntfyTagEmoji(listOf("warning", "radarr", "FIRE")))
        assertEquals("", ntfyTagEmoji(listOf("radarr")))
        assertEquals(listOf("radarr"), ntfyPlainTags(listOf("warning", "radarr", " ")))
    }

    @Test fun `markdown is flattened for the shade`() {
        assertEquals(
            "Grabbed The Matrix in 1080p, see site\n• one",
            stripMarkdown("Grabbed **The Matrix** in `1080p`, see [site](https://x.y)\n- one"),
        )
        assertEquals("a *literal star", stripMarkdown("a *literal star"))
        assertEquals("Heading", stripMarkdown("## Heading"))
    }

    @Test fun `the token only goes to the ntfy server itself`() {
        assertTrue(sameServer("https://ntfy.example/file/x.jpg", "https://ntfy.example/"))
        assertFalse(sameServer("https://evil.example/x.jpg", "https://ntfy.example/"))
        assertFalse(sameServer("http://ntfy.example/x.jpg", "https://ntfy.example/"))
        assertFalse(sameServer("https://ntfy.example:8443/x.jpg", "https://ntfy.example/"))
    }

    @Test fun `pictures are scaled down by powers of two to about 1024 px`() {
        assertEquals(1, sampleSizeFor(800, 600))
        assertEquals(2, sampleSizeFor(2048, 1000))
        assertEquals(2, sampleSizeFor(4000, 3000))
        assertEquals(4, sampleSizeFor(8000, 6000))
    }
}
