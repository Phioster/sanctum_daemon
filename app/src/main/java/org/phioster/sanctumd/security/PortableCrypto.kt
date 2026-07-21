package org.phioster.sanctumd.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based AES-256-GCM for the portable config export. Unlike [Crypto] — whose
 * key lives in this device's Keystore and never leaves it — this derives the key from
 * a user password with PBKDF2, so the exported file can be decrypted on another device.
 *
 * File layout (v2): MAGIC2(9) || iterations(4, big-endian) || salt(16) || iv(12) ||
 *   ciphertext(incl. 16-byte GCM tag). The PBKDF2 iteration count travels in the header
 *   so it can be raised over time without breaking older exports. v1 files (MAGIC1, no
 *   iterations field, fixed 120k) are still read so previously exported backups import.
 * A wrong password fails the GCM tag check (AEADBadTagException) on decrypt. Tampering the
 * header iteration count only derives a different key → the GCM tag fails; it grants no
 * brute-force advantage, so trusting the stored count is safe (clamped to sane bounds).
 */
object PortableCrypto {
    private val MAGIC1 = "SANCTUMD1".toByteArray(Charsets.US_ASCII) // 9 bytes — legacy, implicit 120k iters
    private val MAGIC2 = "SANCTUMD2".toByteArray(Charsets.US_ASCII) // 9 bytes — carries the iteration count
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 210_000        // new exports — OWASP-aligned for PBKDF2-HMAC-SHA256
    private const val LEGACY_ITERATIONS = 120_000 // v1 files were derived with this fixed count
    private const val MAX_ITERATIONS = 5_000_000  // sanity clamp so a malformed header can't hang PBKDF2
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    /** Thrown when the file isn't a Sanctumd export (bad magic / too short / bad header). */
    class BadFileException(message: String) : Exception(message)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    fun encrypt(plain: String, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password.toCharArray(), salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return MAGIC2 + intToBytes(ITERATIONS) + salt + iv + ct
    }

    /** @throws BadFileException wrong file, javax.crypto.AEADBadTagException wrong password. */
    fun decrypt(data: ByteArray, password: String): String {
        val magicLen = MAGIC2.size
        if (data.size <= magicLen) throw BadFileException("not a Sanctumd config file")
        val magic = data.copyOfRange(0, magicLen)
        var off = magicLen
        val iterations: Int = when {
            magic.contentEquals(MAGIC2) -> {
                if (data.size < magicLen + 4 + SALT_LEN + IV_LEN + 1) throw BadFileException("truncated config file")
                bytesToInt(data, off).also { off += 4 }
            }
            magic.contentEquals(MAGIC1) -> {
                if (data.size <= magicLen + SALT_LEN + IV_LEN) throw BadFileException("truncated config file")
                LEGACY_ITERATIONS
            }
            else -> throw BadFileException("not a Sanctumd config file")
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) throw BadFileException("bad iteration count")
        val salt = data.copyOfRange(off, off + SALT_LEN); off += SALT_LEN
        val iv = data.copyOfRange(off, off + IV_LEN); off += IV_LEN
        val ct = data.copyOfRange(off, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password.toCharArray(), salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun intToBytes(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun bytesToInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
}
