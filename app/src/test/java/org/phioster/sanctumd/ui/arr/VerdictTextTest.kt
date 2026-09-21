package org.phioster.sanctumd.ui.arr

import org.junit.Assert.assertEquals
import org.junit.Test
import org.phioster.sanctumd.model.ArrParsedRelease

/**
 * "Unknown" is what a Servarr app returns when it could not tell, printing it as a language
 * fills the line with a word that says nothing, on a line that has to fit on a phone.
 */
class VerdictTextTest {

    private fun v(quality: String = "WEBDL-1080p", languages: String = "German") =
        ArrParsedRelease(quality = quality, score = 50, formats = "1080p", languages = languages, matchedTitle = "X")

    @Test fun `quality and a real language are shown together`() {
        assertEquals("WEBDL-1080p · German", verdictPrefix(v()))
    }

    @Test fun `an unknown language is dropped rather than printed`() {
        assertEquals("WEBDL-1080p", verdictPrefix(v(languages = "Unknown")))
    }

    @Test fun `unknown among real languages is dropped, the rest stays`() {
        assertEquals("WEBDL-1080p · German", verdictPrefix(v(languages = "German, Unknown")))
    }

    @Test fun `without a quality only the language remains`() {
        assertEquals("German", verdictPrefix(v(quality = "")))
    }

    @Test fun `with nothing to say the prefix is empty`() {
        assertEquals("", verdictPrefix(v(quality = "", languages = "Unknown")))
    }
}
