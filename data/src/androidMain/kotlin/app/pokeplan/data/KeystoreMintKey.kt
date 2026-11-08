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
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.mint.v1", "pokelife-mint")
}

/**
 * Heal's key (0.152.0, owner decision 6): its own AES-256-GCM key in the Android Keystore, apart from Mint's, so one module's key can be lost or reset without the
 * other, and a text sealed for one never opens as the other (the associated data differs too). Made the first time Heal is sealed; non-exportable; no screen-lock
 * prompt. Not yet exercised on a device.
 */
object KeystoreHealKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.heal.v1", "pokelife-heal")
}

internal object KeystoreKeys {
    fun cipher(alias: String, context: String): AesGcmCipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
        }
        return AesGcmCipher(store.getKey(alias, null) as SecretKey, context)
    }
}
