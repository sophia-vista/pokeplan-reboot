package app.pokeplan.data

/**
 * Seals and opens a private text (the encryption decision for MINT, docs/pokelife `evidence/REP-014-mint.md` section 6, owner delegated 8 November 2026).
 * A sealed text starts with [PREFIX] so it can never be mistaken for JSON; [open] returns null for a text sealed with another key, a damaged one, or one that
 * is not sealed, and never throws. The Android implementation uses a key that lives in the Android Keystore and never leaves it (`KeystoreMintKey`).
 */
interface RowCipher {
    fun seal(plain: String): String

    fun open(sealed: String): String?

    companion object {
        const val PREFIX = "m1."

        fun isSealed(text: String): Boolean = text.startsWith(PREFIX)
    }
}

/** Raised when Mint's stored rows cannot be opened with this phone's key (a database restored from another phone, or a key that was lost). Nothing is deleted. */
class MintLockedException : IllegalStateException("Mint's records are sealed with a key this phone does not have")
