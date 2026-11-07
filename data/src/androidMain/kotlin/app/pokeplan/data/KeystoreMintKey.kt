package app.pokeplan.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Mint's key: an AES-256-GCM key made inside the Android Keystore the first time Mint is opened, non-exportable (the app can ask the Keystore to
 * seal and open, never to show the key), in hardware where the phone has it. It needs no screen-lock prompt: the optional access gate of MINT.md 13.2 is a
 * separate feature. It does not travel with a copy of the database to another phone, which is the point: Android's own cloud backup of the app's files then
 * holds Mint only as text nobody else can open. Not yet exercised on a device.
 */
object KeystoreMintKey {
    private const val ALIAS = "pokelife.mint.v1"

    fun cipher(): AesGcmCipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
        }
        return AesGcmCipher(store.getKey(ALIAS, null) as SecretKey)
    }
}
