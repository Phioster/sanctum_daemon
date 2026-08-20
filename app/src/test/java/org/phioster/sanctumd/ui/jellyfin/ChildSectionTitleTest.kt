package org.phioster.sanctumd.ui.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Test

class ChildSectionTitleTest {
    @Test fun `a series lists seasons and a season lists episodes`() {
        assertEquals("SEASONS", childSectionTitle("Series"))
        assertEquals("EPISODES", childSectionTitle("Season"))
    }

    @Test fun `anything else gets a neutral heading`() {
        assertEquals("CONTENTS", childSectionTitle("MusicAlbum"))
    }
}
