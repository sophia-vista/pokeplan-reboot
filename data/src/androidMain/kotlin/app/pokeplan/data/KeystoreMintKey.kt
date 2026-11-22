package app.pokeplan.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeystoreMintKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.mint.v1", "pokelife-mint")
}

object KeystoreHealKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.heal.v1", "pokelife-heal")
}

object KeystoreBackupKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.backup.v1", "pokelife-backup")
}

object KeystoreOffPhoneKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.offphone.v1", "pokelife-offphone")
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
