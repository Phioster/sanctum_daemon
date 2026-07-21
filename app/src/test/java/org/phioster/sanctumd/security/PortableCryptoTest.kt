package org.phioster.sanctumd.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The config export is the only copy of a user's API keys that leaves the device, and
 * the only migration path off it — so both directions are pinned here, including the
 * legacy v1 layout that older backups still use.
 */
class PortableCryptoTest {

    private val config = """{"version":1,"services":[{"id":"a","label":"Radarr"}]}"""

    @Test
    fun `round trip returns the original config`() {
        val blob = PortableCrypto.encrypt(config, "correct horse battery")
        assertEquals(config, PortableCrypto.decrypt(blob, "correct horse battery"))
    }

    @Test
    fun `new exports carry the v2 magic and iteration count`() {
        val blob = PortableCrypto.encrypt(config, "pw")
        assertEquals("SANCTUMD2", String(blob.copyOfRange(0, 9), Charsets.US_ASCII))
        val iterations = ((blob[9].toInt() and 0xFF) shl 24) or ((blob[10].toInt() and 0xFF) shl 16) or
            ((blob[11].toInt() and 0xFF) shl 8) or (blob[12].toInt() and 0xFF)
        assertEquals(210_000, iterations)
    }

    @Test
    fun `wrong password fails the GCM tag check`() {
        val blob = PortableCrypto.encrypt(config, "right")
        try {
            PortableCrypto.decrypt(blob, "wrong")
            fail("decrypt should not accept a wrong password")
        } catch (expected: AEADBadTagException) {
            // the tag check is what protects the exported keys
        }
    }

    @Test
    fun `a foreign file is rejected before any key derivation`() {
        try {
            PortableCrypto.decrypt("just some other file entirely".toByteArray(), "pw")
            fail("decrypt should reject a file without the magic")
        } catch (expected: PortableCrypto.BadFileException) {
            assertTrue(expected.message!!.contains("not a Sanctumd config file"))
        }
    }

    @Test
    fun `a truncated export is rejected`() {
        val blob = PortableCrypto.encrypt(config, "pw")
        try {
            PortableCrypto.decrypt(blob.copyOfRange(0, 20), "pw")
            fail("decrypt should reject a truncated file")
        } catch (expected: PortableCrypto.BadFileException) {
            assertTrue(expected.message!!.contains("truncated"))
        }
    }

    @Test
    fun `v1 backups written before the iteration bump still import`() {
        val blob = legacyV1Export(config, "pw")
        assertEquals(config, PortableCrypto.decrypt(blob, "pw"))
    }

    /** Rebuilds the pre-210k layout: MAGIC1 || salt(16) || iv(12) || ciphertext, 120k iters. */
    private fun legacyV1Export(plain: String, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return "SANCTUMD1".toByteArray(Charsets.US_ASCII) + salt + iv + cipher.doFinal(plain.toByteArray())
    }
}
