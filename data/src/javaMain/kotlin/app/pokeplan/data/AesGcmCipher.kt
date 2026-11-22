package app.pokeplan.data

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmCipher(private val key: SecretKey, private val context: String = "pokelife-mint") : RowCipher {
    override fun seal(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(context.encodeToByteArray())
        val iv = c.iv
        val body = c.doFinal(plain.encodeToByteArray())
        return RowCipher.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv + body)
    }

    override fun open(sealed: String): String? = try {
        if (!RowCipher.isSealed(sealed)) null else {
            val raw = Base64.getUrlDecoder().decode(sealed.substring(RowCipher.PREFIX.length))
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
            c.updateAAD(context.encodeToByteArray())
            c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES).decodeToString()
        }
    } catch (e: Exception) { null }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
