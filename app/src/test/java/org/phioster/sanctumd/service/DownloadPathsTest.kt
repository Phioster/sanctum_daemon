package org.phioster.sanctumd.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A media server must not be able to choose where its bytes land. */
class DownloadPathsTest {

    @Test
    fun `an ordinary item keeps its name and extension`() {
        assertEquals("a1b2c3d4.mkv", downloadFileName("a1b2c3d4", "mkv"))
        assertEquals("some-item_2.mp4", downloadFileName("some-item_2", "MP4"))
    }

    @Test
    fun `a container that climbs out of the directory cannot`() {
        // The real payload: one level up is mpv's CA bundle, two is the credential store.
        val name = downloadFileName("a1b2", "../cacert.pem")
        assertFalse(name, name.contains("/"))
        assertFalse(name, name.contains(".."))
        assertEquals("a1b2.cacertpe", name) // 8 chars of extension is all that survives
    }

    @Test
    fun `an item id that climbs out of the directory cannot`() {
        val name = downloadFileName("../../datastore/nexarr_services.preferences_pb", "mkv")
        assertFalse(name, name.contains("/"))
        assertFalse(name, name.contains(".."))
    }

    @Test
    fun `separators, dots and spaces never survive`() {
        for (hostile in listOf("a/b", "a\\b", "a.b", "a b", "a\u0000b", "..", ".", "a%2fb")) {
            val name = downloadFileName(hostile, hostile)
            assertFalse(name, name.contains('/') || name.contains('\\'))
            assertEquals("exactly one dot in $name", 1, name.count { it == '.' })
        }
    }

    @Test
    fun `empty input still yields a usable name`() {
        assertEquals("item.bin", downloadFileName("", ""))
        assertEquals("item.bin", downloadFileName("///", "..."))
    }

    @Test
    fun `the prefix matches what the name builder produces`() {
        val id = "a1b2-c3"
        assertEquals(true, downloadFileName(id, "mkv").startsWith(downloadFilePrefix(id)))
    }
}
