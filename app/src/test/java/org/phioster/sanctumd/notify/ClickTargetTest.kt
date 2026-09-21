package org.phioster.sanctumd.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickTargetTest {

    @Test fun `our own address yields the item id`() {
        assertEquals(
            "d4282e3a5df524ada8cbc8c3244287fd",
            jellyfinItemIdFromClick("sanctumd://item/d4282e3a5df524ada8cbc8c3244287fd"),
        )
    }

    @Test fun `a query is not part of the id`() {
        assertEquals("abc123", jellyfinItemIdFromClick("sanctumd://item/abc123?from=ntfy"))
    }

    /** A message with no click, or one pointing somewhere else, must open the app normally. */
    @Test fun `anything else points at nothing`() {
        assertNull(jellyfinItemIdFromClick(null))
        assertNull(jellyfinItemIdFromClick(""))
        assertNull(jellyfinItemIdFromClick("https://example.com/item/1"))
        assertNull(jellyfinItemIdFromClick("sanctumd://item/"))
    }

    /**
     * An id is put straight into an Intent extra, so anything that is not a plain id is refused
     * rather than passed on. A template can be edited by hand and a typo must not travel.
     */
    @Test fun `a malformed id is refused`() {
        assertNull(jellyfinItemIdFromClick("sanctumd://item/../../etc"))
        assertNull(jellyfinItemIdFromClick("sanctumd://item/{{ItemId}}"))
    }
}
