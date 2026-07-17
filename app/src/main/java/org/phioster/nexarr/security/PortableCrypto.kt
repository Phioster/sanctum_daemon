package org.phioster.nexarr.security

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
 * File layout: MAGIC(9) || salt(16) || iv(12) || ciphertext(incl. 16-byte GCM tag).
 * A wrong password fails the GCM tag check (AEADBadTagException) on decrypt.
 */
object PortableCrypto {
    private val MAGIC = "SANCTUMD1".toByteArray(Charsets.US_ASCII) // 9 bytes
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    /** Thrown when the file isn't a Sanctumd export (bad magic / too short). */
    class BadFileException(message: String) : Exception(message)

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    fun encrypt(plain: String, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password.toCharArray(), salt), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return MAGIC + salt + iv + ct
    }

    /** @throws BadFileException wrong file, javax.crypto.AEADBadTagException wrong password. */
    fun decrypt(data: ByteArray, password: String): String {
        val headLen = MAGIC.size + SALT_LEN + IV_LEN
        if (data.size <= headLen || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BadFileException("not a Sanctumd config file")
        }
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = data.copyOfRange(MAGIC.size + SALT_LEN, headLen)
        val ct = data.copyOfRange(headLen, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password.toCharArray(), salt), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
