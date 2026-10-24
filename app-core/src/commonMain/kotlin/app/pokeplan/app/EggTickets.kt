package app.pokeplan.app

import kotlinx.serialization.Serializable

/** A random seed the server issued to this device: one focus egg is rolled from it. See "Egg tickets" under Focus in docs/design.md. */
@Serializable
data class EggTicket(val id: String, val seed: Int, val issuedAtMillis: Long)

/** A ticket a session spent: when the session started and the Pokémon it hatched (null if the session failed). */
@Serializable
data class SpentTicket(val id: String, val startedAtMillis: Long, val pokemonId: String?)

/**
 * This device's egg tickets. They stay on the device: they are not part of the cloud save, since another device holding
 * the same tickets could spend them twice.
 */
@Serializable
data class EggTickets(
    /** Names this device to the server, so each device has its own stock. Made the first time tickets are asked for. */
    val device: String? = null,
    /** Unspent tickets, oldest first. */
    val stock: List<EggTicket> = emptyList(),
    /** The ticket the running session spent when it started. */
    val inSession: EggTicket? = null,
    /** Spent tickets the server has not been told about yet. */
    val spent: List<SpentTicket> = emptyList(),
)
