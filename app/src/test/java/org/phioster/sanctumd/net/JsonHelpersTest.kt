package org.phioster.sanctumd.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `JsonNull` is a `JsonPrimitive` in kotlinx.serialization, and its `content` is the literal
 * four-character string `"null"`. So a helper that reads `.content` turns every JSON null into
 * the text "null" — which is not blank, so the usual `takeIf { it.isNotBlank() }` guards let it
 * straight through and into the UI.
 *
 * These helpers are used ~94 times across the parsing layer, so this is pinned here rather than
 * worked around at each call site.
 */
class JsonHelpersTest {

    private val o = Json.parseToJsonElement(
        """{"present":"value","empty":"","nulled":null,"num":7,"flag":true}""",
    ).jsonObject

    @Test fun `a present string is returned`() {
        assertEquals("value", jsStr(o, "present"))
    }

    @Test fun `a json null reads as absent, not as the text null`() {
        assertNull(jsStr(o, "nulled"))
    }

    @Test fun `a missing key reads as absent`() {
        assertNull(jsStr(o, "nothing"))
    }

    @Test fun `an empty string stays an empty string`() {
        assertEquals("", jsStr(o, "empty"))
    }

    @Test fun `numbers and booleans are unaffected by the null handling`() {
        assertEquals(7, jsInt(o, "num"))
        assertNull(jsInt(o, "nulled"))
        assertTrue(jsBool(o, "flag") == true)
    }
}
