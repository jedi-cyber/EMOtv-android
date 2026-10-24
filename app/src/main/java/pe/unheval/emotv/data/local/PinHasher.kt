package pe.unheval.emotv.data.local

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Deriva el hash del PIN con PBKDF2-HMAC-SHA256 y una sal aleatoria (APIs del sistema,
 * disponibles desde API 26). Formato: `pbkdf2-sha256$iteraciones$sal$hash`, en Base64.
 */
class PinHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(pin, salt, iterations)
        return listOf(PREFIX, iterations.toString(), encode(salt), encode(derived))
            .joinToString(SEPARATOR)
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val rounds = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt, rounds), expected)
    }

    private fun derive(pin: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().withoutPadding().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)

    companion object {
        const val DEFAULT_ITERATIONS = 120_000
        private const val PREFIX = "pbkdf2-sha256"
        private const val SEPARATOR = "$"
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
