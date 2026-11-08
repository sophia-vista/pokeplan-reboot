package app.pokeplan.app

import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.PlayerState

/**
 * Where an owned Pokémon is (docs/pokelife `FEATURES/POKEMON_ECOSYSTEM.md` section 2.2, owner decision 7 November 2026): **one place
 * only**. The place is derived from the references the save keeps (the buddy id, each Pokémon's party, the Guardian slots, the World
 * teams, the Pokégear helpers), so [vacate] first takes a Pokémon out of whatever it was in and every assignment starts with it:
 * no state with two places can be written.
 */
sealed interface Place {
    data object Buddy : Place
    data class Party(val partyId: String) : Place
    data object Guardian : Place
    data class WorldTeam(val team: Int) : Place
    data class GearHelper(val tool: String) : Place
    data object Pc : Place
}

object Places {
    /** Where [id] is, by the priority that resolves a save with two ([resolve]): Guardian, World team, buddy, party, Pokégear helper, else the PC. */
    fun of(d: AppData, id: String): Place {
        val p = d.player.pokemon.firstOrNull { it.id == id } ?: return Place.Pc
        if (id in d.player.guardians) return Place.Guardian
        d.world.teams.firstOrNull { id in it.members }?.let { return Place.WorldTeam(it.id) }
        if (d.player.buddyId == id) return Place.Buddy
        if (p.inParty) return Place.Party(p.partyId)
        d.gearHelpers.entries.firstOrNull { it.value == id }?.let { return Place.GearHelper(it.key) }
        return Place.Pc
    }

    /** The name a player reads for a module's party: "Plan", "Care", "Heal", "Mint" or "Home". */
    private fun partyName(partyId: String): String = when (partyId) {
        PartyIds.PLAN -> "Plan"; PartyIds.CARE -> "Care"; PartyIds.HEAL -> "Heal"; PartyIds.MINT -> "Mint"; PartyIds.HOME -> "Home"
        else -> partyId.replaceFirstChar { it.uppercase() }
    }

    /**
     * Where [id] is, in words, for every place there is (the party and PC screens and the detail page read this one function, so no
     * place is ever shown as "the PC" by mistake): the buddy, a party (and its lead), a Guardian slot, a World team, a tool it helps, the PC.
     */
    fun label(d: AppData, id: String): String = when (val place = of(d, id)) {
        Place.Buddy -> "Your buddy"
        is Place.Party -> {
            val lead = when (place.partyId) {
                PartyIds.PLAN -> d.player.planLeadId; PartyIds.CARE -> d.player.careLeadId; PartyIds.HEAL -> d.player.healLeadId
                PartyIds.MINT -> d.player.mintLeadId; PartyIds.HOME -> d.player.homeLeadId; else -> null
            }
            if (lead == id) "${partyName(place.partyId)}'s lead, in ${partyName(place.partyId)}'s party" else "In ${partyName(place.partyId)}'s party"
        }
        Place.Guardian -> "In a Guardian slot"
        is Place.WorldTeam -> "On ${d.world.team(place.team)?.title ?: "a World team"}"
        is Place.GearHelper -> "Helping a Gear tool"
        Place.Pc -> "Resting in the PC"
    }

    /** Whether [id] can be taken from its place now: not while its World team is out on an expedition. */
    fun movable(d: AppData, id: String): Boolean = d.world.teams.none { id in it.members && it.expedition != null }

    /**
     * [d] with [id] out of every place it is in: no longer the buddy, in no party, not a Guardian, off its World team and not a helper.
     * It goes to the PC. Leads are re-derived by the caller. A Pokémon on an expedition under way is left where it is.
     */
    fun vacate(d: AppData, id: String): AppData {
        if (!movable(d, id)) return d
        val player = d.player.copy(
            buddyId = d.player.buddyId.takeIf { it != id },
            guardians = d.player.guardians - id,
            pokemon = d.player.pokemon.map { if (it.id == id && it.inParty) it.copy(inParty = false) else it },
        )
        val teams = d.world.teams.map { t -> if (id in t.members) t.copy(members = t.members - id) else t }
        return d.copy(player = player, world = d.world.copy(teams = teams), gearHelpers = d.gearHelpers.filterValues { it != id })
    }

    /** A party's lead and the chosen leads, after the members changed: the lead stays if it is still in the party, else the party's first member. */
    fun leads(p: PlayerState): PlayerState {
        fun lead(party: String, chosen: String?): String? {
            val members = p.pokemon.filter { it.inParty && it.partyId == party }
            return chosen?.takeIf { c -> members.any { it.id == c } } ?: members.firstOrNull()?.id
        }
        return p.copy(planLeadId = lead(PartyIds.PLAN, p.planLeadId), careLeadId = lead(PartyIds.CARE, p.careLeadId), healLeadId = lead(PartyIds.HEAL, p.healLeadId), mintLeadId = lead(PartyIds.MINT, p.mintLeadId), homeLeadId = lead(PartyIds.HOME, p.homeLeadId))
    }

    /** The version a save carries once [resolve] has run. */
    const val VERSION = 1

    /**
     * A save from before the rule (docs section 2.2.3): a Pokémon in two places keeps the highest by Guardian, World team, buddy, party,
     * helper, and the others are taken from it. If the buddy is lost the best Pokémon in the PC (highest level, friendliest, then id) becomes
     * the buddy; with none, the buddy stays empty. Nothing is removed from the collection, nothing is paid, and it runs once.
     */
    fun resolve(d: AppData): AppData {
        if (d.placesVersion >= VERSION) return d
        var next = d
        // A Pokémon on a team and in a party: the team wins; a Guardian that is also on a team or in a party: the Guardian wins.
        for (p in d.player.pokemon) {
            val keep = of(d, p.id)
            if (keep is Place.Guardian || keep is Place.WorldTeam || keep is Place.Party || keep is Place.GearHelper || keep is Place.Buddy) next = keepOnly(next, p.id, keep)
        }
        var player = Places.leads(next.player)
        if (d.player.buddyId != null && next.player.buddyId == null) {
            val free = player.pokemon.filter { of(next, it.id) == Place.Pc && !Guardians.canBe(it) }
                .sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenByDescending { it.friendship }.thenBy { it.id }).firstOrNull()
            player = player.copy(buddyId = free?.id)
        }
        return next.copy(player = player, placesVersion = VERSION)
    }

    /** [d] with [id] in [keep] only. */
    private fun keepOnly(d: AppData, id: String, keep: Place): AppData {
        var next = d
        if (keep !is Place.Buddy && next.player.buddyId == id) next = next.copy(player = next.player.copy(buddyId = null))
        if (keep !is Place.Guardian && id in next.player.guardians) next = next.copy(player = next.player.copy(guardians = next.player.guardians - id))
        if (keep !is Place.WorldTeam && next.world.teams.any { id in it.members }) next = next.copy(world = next.world.copy(teams = next.world.teams.map { t -> if (id in t.members) t.copy(members = t.members - id) else t }))
        if (keep !is Place.Party && next.player.pokemon.any { it.id == id && it.inParty }) next = next.copy(player = next.player.copy(pokemon = next.player.pokemon.map { if (it.id == id) it.copy(inParty = false) else it }))
        if (keep !is Place.GearHelper && next.gearHelpers.values.any { it == id }) next = next.copy(gearHelpers = next.gearHelpers.filterValues { it != id })
        return next
    }
}
