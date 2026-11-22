package app.pokeplan.data

interface PassphraseSealer {
    fun seal(plain: String, passphrase: String): String

    fun open(sealed: String, passphrase: String): String?

    companion object {
        const val PREFIX = "p1."

        const val MIN_LENGTH = 10

        fun isSealed(text: String): Boolean = text.startsWith(PREFIX)

        fun refusal(passphrase: String): String? =
            if (passphrase.length < MIN_LENGTH) "Use at least $MIN_LENGTH characters. A few words in a row is easy to remember and hard to guess." else null
    }
}
