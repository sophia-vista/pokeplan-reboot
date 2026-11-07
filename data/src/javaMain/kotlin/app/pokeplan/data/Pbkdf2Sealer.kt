package app.pokeplan.data

import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * [PassphraseSealer] with PBKDF2WithHmacSHA256 and AES-256-GCM (available on every Android the app supports, API 26 and later, and on the JVM). The text is
 * `p1.<salt>.<iterations>.<iv, ciphertext and tag>`, the parts URL-safe Base64 (so none contains a dot). The passphrase is normalised (NFKC) first, so the same
 * words typed on another keyboard open the same backup. The context string is associated data, so a text sealed for another use never opens here.
 */
class Pbkdf2Sealer(private val iterations: Int = ITERATIONS, private val random: SecureRandom = SecureRandom()) : PassphraseSealer {
    override fun seal(plain: String, passphrase: String): String {
        require(PassphraseSealer.refusal(passphrase) == null) { "the passphrase is too short" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(CONTEXT)
        val body = c.doFinal(plain.encodeToByteArray())
        val b64 = Base64.getUrlEncoder().withoutPadding()
        return PassphraseSealer.PREFIX + b64.encodeToString(salt) + "." + iterations + "." + b64.encodeToString(iv + body)
    }

    override fun open(sealed: String, passphrase: String): String? = try {
        if (!PassphraseSealer.isSealed(sealed)) null else {
            val parts = sealed.substring(PassphraseSealer.PREFIX.length).split('.')
            if (parts.size != 3) null else {
                val d = Base64.getUrlDecoder()
                val salt = d.decode(parts[0])
                val count = parts[1].toInt()
                val raw = d.decode(parts[2])
                // A file cannot make the phone work for minutes: an absurd count is refused rather than run.
                if (count !in 1..MAX_ITERATIONS || salt.size < SALT_BYTES || raw.size <= IV_BYTES) null else {
                    val c = Cipher.getInstance(TRANSFORM)
                    c.init(Cipher.DECRYPT_MODE, key(passphrase, salt, count), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
                    c.updateAAD(CONTEXT)
                    c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES).decodeToString()
                }
            }
        }
    } catch (e: Exception) { null }

    private fun key(passphrase: String, salt: ByteArray, count: Int): SecretKeySpec {
        val spec = PBEKeySpec(Normalizer.normalize(passphrase, Normalizer.Form.NFKC).toCharArray(), salt, count, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    companion object {
        /** OWASP's 2023 figure for PBKDF2-HMAC-SHA256; well under a second on a phone, and the count is stored in each text. */
        const val ITERATIONS = 600_000
        const val MAX_ITERATIONS = 5_000_000
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private val CONTEXT = "pokelife-mint-portable".encodeToByteArray()
    }
}
