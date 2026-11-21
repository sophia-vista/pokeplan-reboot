package app.pokeplan.app

import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.PlayerState

sealed interface Place {
    data object Buddy : Place
    data class Party(val partyId: String) : Place
    data class Guardian(val module: String) : Place
    data class WorldTeam(val team: Int) : Place
    data class GearHelper(val tool: String) : Place
    data object Pc : Place
}

object Places {
    fun of(d: AppData, id: String): Place {
        val p = d.player.pokemon.firstOrNull { it.id == id } ?: return Place.Pc
        d.player.moduleGuardians.entries.firstOrNull { it.value == id }?.let { return Place.Guardian(it.key) }
        d.world.teams.firstOrNull { id in it.members }?.let { return Place.WorldTeam(it.id) }
        if (d.player.buddyId == id) return Place.Buddy
        if (p.inParty) return Place.Party(p.partyId)
        d.gearHelpers.entries.firstOrNull { it.value == id }?.let { return Place.GearHelper(it.key) }
        return Place.Pc
    }

    private fun partyName(partyId: String): String = when (partyId) {
        PartyIds.PLAN -> "Plan"; PartyIds.CARE -> "Care"; PartyIds.HEAL -> "Heal"; PartyIds.MINT -> "Mint"; PartyIds.BASE -> "Base"
        else -> partyId.replaceFirstChar { it.uppercase() }
    }

    fun label(d: AppData, id: String): String = when (val place = of(d, id)) {
        Place.Buddy -> "Your buddy"
        is Place.Party -> {
            val lead = when (place.partyId) {
                PartyIds.PLAN -> d.player.planLeadId; PartyIds.CARE -> d.player.careLeadId; PartyIds.HEAL -> d.player.healLeadId
                PartyIds.MINT -> d.player.mintLeadId; PartyIds.BASE -> d.player.baseLeadId; else -> null
            }
            if (lead == id) "${partyName(place.partyId)}'s lead, in ${partyName(place.partyId)}'s party" else "In ${partyName(place.partyId)}'s party"
        }
        is Place.Guardian -> "${partyName(place.module)}'s Guardian"
        is Place.WorldTeam -> "On ${d.world.team(place.team)?.title ?: "a World team"}"
        is Place.GearHelper -> "Helping the ${app.pokeplan.core.pokemon.GearTool.entries.firstOrNull { it.name == place.tool }?.label ?: "Pokégear"}"
        Place.Pc -> "Resting in the PC"
    }

    fun short(d: AppData, id: String, isLead: Boolean): String? = when (val place = of(d, id)) {
        Place.Buddy -> "Buddy"
        is Place.Party -> if (isLead) "${partyName(place.partyId)} lead" else partyName(place.partyId)
        is Place.Guardian -> "${partyName(place.module)} Guardian"
        is Place.WorldTeam -> "World"
        is Place.GearHelper -> "Pokégear"
        Place.Pc -> null
    }

    fun movable(d: AppData, id: String): Boolean = d.world.teams.none { id in it.members && it.expedition != null }

    fun vacate(d: AppData, id: String): AppData {
        if (!movable(d, id)) return d
        val player = d.player.copy(
            buddyId = d.player.buddyId.takeIf { it != id },
            guardians = d.player.guardians - id,
            moduleGuardians = d.player.moduleGuardians.filterValues { it != id },
            pokemon = d.player.pokemon.map { if (it.id == id && it.inParty) it.copy(inParty = false) else it },
        )
        val teams = d.world.teams.map { t -> if (id in t.members) t.copy(members = t.members - id) else t }
        return d.copy(player = player, world = d.world.copy(teams = teams), gearHelpers = d.gearHelpers.filterValues { it != id })
    }

    fun leads(p: PlayerState): PlayerState {
        fun lead(party: String, chosen: String?): String? {
            val members = p.pokemon.filter { it.inParty && it.partyId == party }
            return chosen?.takeIf { c -> members.any { it.id == c } } ?: members.firstOrNull()?.id
        }
        return p.copy(planLeadId = lead(PartyIds.PLAN, p.planLeadId), careLeadId = lead(PartyIds.CARE, p.careLeadId), healLeadId = lead(PartyIds.HEAL, p.healLeadId), mintLeadId = lead(PartyIds.MINT, p.mintLeadId), baseLeadId = lead(PartyIds.BASE, p.baseLeadId), linkLeadId = lead(PartyIds.LINK, p.linkLeadId))
    }

    fun guardiansToModules(d: AppData): AppData {
        if (d.player.guardians.isEmpty()) return d
        val taken = d.player.moduleGuardians.toMutableMap()
        val free = Guardians.MODULES.filter { it !in taken }.toMutableList()
        for (id in d.player.guardians) {
            val p = d.player.pokemon.firstOrNull { it.id == id } ?: continue
            if (!Guardians.canBe(p) || taken.containsValue(id)) continue
            taken[free.removeFirstOrNull() ?: break] = id
        }
        return d.copy(player = d.player.copy(guardians = emptyList(), moduleGuardians = taken))
    }

    const val VERSION = 1

    fun resolve(d: AppData): AppData {
        if (d.placesVersion >= VERSION) return d
        var next = d
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

    private fun keepOnly(d: AppData, id: String, keep: Place): AppData {
        var next = d
        if (keep !is Place.Buddy && next.player.buddyId == id) next = next.copy(player = next.player.copy(buddyId = null))
        if (keep !is Place.Guardian && next.player.moduleGuardians.containsValue(id)) next = next.copy(player = next.player.copy(moduleGuardians = next.player.moduleGuardians.filterValues { it != id }))
        if (keep !is Place.WorldTeam && next.world.teams.any { id in it.members }) next = next.copy(world = next.world.copy(teams = next.world.teams.map { t -> if (id in t.members) t.copy(members = t.members - id) else t }))
        if (keep !is Place.Party && next.player.pokemon.any { it.id == id && it.inParty }) next = next.copy(player = next.player.copy(pokemon = next.player.pokemon.map { if (it.id == id) it.copy(inParty = false) else it }))
        if (keep !is Place.GearHelper && next.gearHelpers.values.any { it == id }) next = next.copy(gearHelpers = next.gearHelpers.filterValues { it != id })
        return next
    }
}
