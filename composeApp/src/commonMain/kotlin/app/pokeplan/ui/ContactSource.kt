package app.pokeplan.ui

import app.pokeplan.link.ContactCard

interface ContactSource {
    fun permitted(): Boolean

    fun requestPermission(done: (Boolean) -> Unit)

    fun contacts(): List<ContactCard>
}
