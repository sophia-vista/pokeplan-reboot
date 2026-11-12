package app.pokeplan.data

/**
 * Seals a private text under a **passphrase the player chose**, so a backup of Mint opens on any phone that is given the passphrase (docs/pokelife
 * `MODULES/MINT.md` section 22, `ARCHITECTURE/DATA_AND_MIGRATIONS.md` section 10.4.1). The device-sealed form (`m1.`, [RowCipher]) opens only on the phone that holds the Keystore key;
 * this is the portable form (`p1.`), made only for a backup file the player asks to be portable, never for the tables.
 *
 * The passphrase is stretched with PBKDF2-HMAC-SHA256 (a random salt and a stored iteration count, so the count can be raised later without breaking a file),
 * and the 256-bit result is the key of AES-256-GCM. The passphrase is never stored anywhere; a lost one cannot be recovered, and the screen says so before it is used.
 * [open] returns null for a wrong passphrase, a damaged text or a text that is not sealed this way, and never throws.
 */
interface PassphraseSealer {
    fun seal(plain: String, passphrase: String): String

    fun open(sealed: String, passphrase: String): String?

    companion object {
        const val PREFIX = "p1."

        /** The shortest passphrase accepted. Length matters more than anything else against a guessed passphrase. */
        const val MIN_LENGTH = 10

        fun isSealed(text: String): Boolean = text.startsWith(PREFIX)

        /** Why [passphrase] is not acceptable, in the player's words, or null when it is. */
        fun refusal(passphrase: String): String? =
            if (passphrase.length < MIN_LENGTH) "Use at least $MIN_LENGTH characters. A few words in a row is easy to remember and hard to guess." else null
    }
}
