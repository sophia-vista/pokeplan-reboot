package app.pokeplan.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Mint's key: an AES-256-GCM key made inside the Android Keystore the first time Mint is opened, non-exportable (the app can ask the Keystore to
 * seal and open, never to show the key), in hardware where the phone has it. It needs no screen-lock prompt: the optional access gate of MINT.md 13.2 is a
 * separate feature. It does not travel with a copy of the database to another phone, which is the point: a copy of the database restored on another phone (or a backup
 * file the player saves) then holds Mint only as text nobody else can open. Android's own cloud backup is off (`allowBackup` is false). Not yet exercised on a device.
 */
object KeystoreMintKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.mint.v1", "pokelife-mint")
}

/**
 * Heal's key (0.154.0, owner decision 6): its own AES-256-GCM key in the Android Keystore, apart from Mint's, so one module's key can be lost or reset without the
 * other, and a text sealed for one never opens as the other (the associated data differs too). Made the first time Heal is sealed; non-exportable; no screen-lock
 * prompt. Not yet exercised on a device.
 */
object KeystoreHealKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.heal.v1", "pokelife-heal")
}

/**
 * The key that seals the app's own daily backup files (every document in them, Care's journal and the rest, not only Mint and Heal): its own AES-256-GCM key in
 * the Android Keystore, non-exportable, so a copy of the backup folder opens for nobody else. A backup to open anywhere is the passphrase one instead.
 */
object KeystoreBackupKey {
    fun cipher(): AesGcmCipher = KeystoreKeys.cipher("pokelife.backup.v1", "pokelife-backup")
}

/**
 * The key that seals the passphrase of the backup off the phone (REP-034) where the app keeps it, so the daily job can seal Mint and Heal without asking. Its own
 * AES-256-GCM key in the Android Keystore, non-exportable, and it never leaves this phone: a lost or wiped phone loses it, and the player's own memory of the
 * passphrase is what opens the copies then.
 */
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
