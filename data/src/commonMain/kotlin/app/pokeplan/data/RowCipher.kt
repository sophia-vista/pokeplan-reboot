package app.pokeplan.data

interface RowCipher {
    fun seal(plain: String): String

    fun open(sealed: String): String?

    companion object {
        const val PREFIX = "m1."

        fun isSealed(text: String): Boolean = text.startsWith(PREFIX)
    }
}

class MintLockedException : IllegalStateException("Mint's records are sealed with a key this phone does not have")

class HealLockedException : IllegalStateException("Heal's records are sealed with a key this phone does not have")
