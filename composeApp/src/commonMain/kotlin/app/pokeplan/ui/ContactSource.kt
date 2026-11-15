package app.pokeplan.ui

import app.pokeplan.link.ContactCard

/**
 * The phone's address book for LINK's optional import (LINK.md 3.5 and 6.1), read-only. Only the Android app provides one. LINK reads a name, phones,
 * emails, an organization and a birthday, and nothing else: no photo, call, message or account. Callers run [contacts] off the main thread.
 */
interface ContactSource {
    /** Whether the app may read the contacts. Never true unless the user allowed it for an import. */
    fun permitted(): Boolean

    /** Asks for the contacts permission; [done] gets the answer. Called only after the user chose Import contacts. */
    fun requestPermission(done: (Boolean) -> Unit)

    /** Every contact that has a name, with the fields above, ordered by name. Empty when the permission is missing. */
    fun contacts(): List<ContactCard>
}
