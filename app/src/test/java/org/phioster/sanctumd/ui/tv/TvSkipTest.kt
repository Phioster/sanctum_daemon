package org.phioster.sanctumd.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.phioster.sanctumd.net.MediaSegment

class TvSkipTest {
    private val segs = listOf(
        MediaSegment("Intro", 90_000, 180_000),
        MediaSegment("Outro", 1_300_000, 1_390_000),
    )

    @Test fun `inside the intro the intro is offered`() {
        assertEquals(SkipOffer(0, SkipKind.INTRO, 180_000), skipOffer(segs, 100_000, emptySet(), true))
    }

    @Test fun `before and after a segment nothing is offered`() {
        assertNull(skipOffer(segs, 30_000, emptySet(), true))
        assertNull(skipOffer(segs, 180_000, emptySet(), true))
    }

    @Test fun `credits offer the next episode when there is one`() {
        assertEquals(SkipKind.NEXT_EPISODE, skipOffer(segs, 1_310_000, emptySet(), true)?.kind)
        assertEquals(SkipKind.CREDITS, skipOffer(segs, 1_310_000, emptySet(), false)?.kind)
    }

    @Test fun `a used offer does not come back`() {
        assertNull(skipOffer(segs, 100_000, setOf(0), true))
    }

    @Test fun `other segment types are ignored`() {
        assertNull(skipOffer(listOf(MediaSegment("Preview", 0, 50_000)), 10_000, emptySet(), true))
    }
}
