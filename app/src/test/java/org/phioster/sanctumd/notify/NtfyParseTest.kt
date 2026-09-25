package org.phioster.sanctumd.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every live push goes through this parser. The stream mixes real messages with
 * open/keepalive frames, and services post their webhook payload as the message body,
 * so the interesting cases are the ones that must NOT become a notification and the
 * ones where title/text have to be dug out of nested JSON.
 */
class NtfyParseTest {

    @Test
    fun `a plain message keeps id, time, topic and text`() {
        val m = parseNtfyLine(
            """{"id":"abc123","time":1750000000,"event":"message","topic":"homelab","message":"disk at 91%"}"""
        )!!
        assertEquals("abc123", m.id)
        assertEquals(1750000000L, m.time)
        assertEquals("homelab", m.topic)
        assertEquals("disk at 91%", m.text)
        assertEquals("", m.title)
    }

    @Test
    fun `keepalive and open frames are not messages`() {
        assertNull(parseNtfyLine("""{"id":"k","time":1,"event":"keepalive","topic":"homelab"}"""))
        assertNull(parseNtfyLine("""{"id":"o","time":1,"event":"open","topic":"homelab"}"""))
        assertNull(parseNtfyLine("""{"id":"p","time":1,"event":"poll_request","topic":"homelab"}"""))
    }

    @Test
    fun `garbage lines are skipped instead of throwing`() {
        assertNull(parseNtfyLine(""))
        assertNull(parseNtfyLine("not json at all"))
        assertNull(parseNtfyLine("""{"event":"message","topic":"homelab"}""")) // no body
    }

    @Test
    fun `a webhook payload posted as the body is unwrapped`() {
        val m = parseNtfyLine(
            """{"id":"x","time":2,"event":"message","topic":"homelab","message":"{\"title\":\"Item Added\",\"message\":\"Dune (2021)\"}"}"""
        )!!
        assertEquals("Item Added", m.title)
        assertEquals("Dune (2021)", m.text)
    }

    @Test
    fun `a Radarr style payload falls back to the nested movie title`() {
        val m = parseNtfyLine(
            """{"id":"y","time":3,"event":"message","topic":"homelab","message":"{\"eventType\":\"Grab\",\"movie\":{\"title\":\"Arrival\"}}"}"""
        )!!
        assertEquals("Grab", m.title)
        assertEquals("Arrival", m.text)
    }

    @Test
    fun `the ntfy title wins over one inside the payload`() {
        val m = parseNtfyLine(
            """{"id":"z","time":4,"event":"message","topic":"homelab","title":"from ntfy","message":"{\"title\":\"from payload\",\"message\":\"body\"}"}"""
        )!!
        assertEquals("from ntfy", m.title)
    }

    @Test
    fun `a long non-JSON body is truncated`() {
        val long = "x".repeat(500)
        val m = parseNtfyLine("""{"id":"t","time":5,"event":"message","topic":"homelab","message":"$long"}""")!!
        assertEquals(180, m.text.length)
    }

    @Test
    fun `HTML entities from a webhook template are decoded`() {
        val m = parseNtfyLine(
            """{"id":"e1","time":6,"event":"message","topic":"homelab","message":"{\"title\":\"Neu in der Mediathek\",\"message\":\"Lion Fist -&#160;Kampf der Champions (2026)\"}"}"""
        )!!
        assertEquals("Lion Fist - Kampf der Champions (2026)", m.text)
    }

    @Test
    fun `named, decimal and hex entities all decode, in the title too`() {
        val m = parseNtfyLine(
            """{"id":"e2","time":7,"event":"message","topic":"homelab","title":"Ren&#xE9;e &amp; Co","message":"a &lt;b&gt; &quot;c&quot; &#39;d&#39;"}"""
        )!!
        assertEquals("Renée & Co", m.title)
        assertEquals("a <b> \"c\" 'd'", m.text)
    }

    @Test
    fun `a lone ampersand and an unknown entity are left alone`() {
        val m = parseNtfyLine(
            """{"id":"e3","time":8,"event":"message","topic":"homelab","message":"Tom & Jerry &nosuch; 100&"}"""
        )!!
        assertEquals("Tom & Jerry &nosuch; 100&", m.text)
    }

    @Test
    fun `truncation counts decoded characters, not entity source`() {
        val body = "&amp;".repeat(300)          // 1500 Zeichen Quelle, 300 danach
        val m = parseNtfyLine("""{"id":"e4","time":9,"event":"message","topic":"homelab","message":"$body"}""")!!
        assertEquals(180, m.text.length)
        assertEquals("&".repeat(180), m.text)
    }
}
