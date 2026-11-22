package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Evolution
import app.pokeplan.core.pokemon.DeviceAction
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.EvolutionTrials
import app.pokeplan.core.pokemon.OwnedPokemon

data class TrialSite(val key: String, val dungeonId: String, val floor: Int, val title: String, val story: String)

object EvolutionTrialSites {
    val all: List<TrialSite> = listOf(
        TrialSite("290-292-any", "petalburg-woods", 4, "The empty shell", "Deep in the woods, Nincada sheds its shell. Something stirs inside the husk."),
        TrialSite("705-706-any", "grande-vallee-way", 6, "Rain on the marsh", "Sliggoo only grows in heavy rain. Grande Vallée Way is never dry for long."),
        TrialSite("104-105-any-marowak-alola", "wela-volcano-park", 6, "Fire dance on the volcano", "Alola's Cubone dance at night on Wela Volcano, and the flames answer."),
        TrialSite("109-110-any-weezing-galar", "wild-area", 6, "Clean air of the Wild Area", "Koffing that drink Galar's polluted air turn it clean, and grow top hats of smoke."),
        TrialSite("439-122-any-mr-mime-galar", "route-10", 6, "Tap dance on the ice", "Mime Jr. that learn Mimic in Galar's snow start to tap-dance."),
        TrialSite("83-865-farfetchd-galar", "galar-mine", 8, "Three critical hits", "A Galarian Farfetch'd that lands three critical hits in one battle grows into a knight."),
        TrialSite("562-867-yamask-galar", "dusty-bowl", 8, "The stone in the Dusty Bowl", "Under the stone arch of the Dusty Bowl, a Yamask that has taken a heavy blow finds its old body."),
        TrialSite("156-157-any-typhlosion-hisui", "coronet-highlands", 6, "Hisui's flame", "In old Hisui, Quilava's fire burned with the spirits of the mountain."),
        TrialSite("502-503-any-samurott-hisui", "cobalt-coastlands", 6, "Hisui's tide", "Dewott that train on Hisui's rough coast grow fierce and dark."),
        TrialSite("723-724-any-decidueye-hisui", "obsidian-fieldlands", 8, "Hisui's arrow", "Dartrix of Hisui's fields learn to fight with their whole body."),
        TrialSite("627-628-any-braviary-hisui", "coronet-highlands", 8, "Hisui's wind", "Rufflet of the Hisuian highlands grow into psychic hunters of the cliffs."),
        TrialSite("704-705-any-sliggoo-hisui", "crimson-mirelands", 4, "A shell of metal", "Goomy of the Mirelands grow a shell of metal from the bog."),
        TrialSite("705-706-sliggoo-hisui-goodra-hisui", "crimson-mirelands", 6, "Rain on the Mirelands", "Hisuian Sliggoo evolve in the rain, as all Sliggoo do."),
        TrialSite("712-713-any-avalugg-hisui", "alabaster-icelands", 6, "A jaw of ice", "Bergmite of the Icelands grow into Avalugg with jaws like glaciers."),
        TrialSite("234-899-any", "obsidian-fieldlands", 6, "Psyshield Bash in agile style", "A Stantler that uses Psyshield Bash in agile style twenty times grows into Wyrdeer."),
        TrialSite("550-902-basculin-white-striped-basculegion-male", "crimson-mirelands", 8, "The white-striped school", "A white-striped Basculin that takes heavy recoil and keeps going becomes Basculegion."),
        TrialSite("550-902-basculin-white-striped-basculegion-female", "crimson-mirelands", 8, "The white-striped school", "A white-striped Basculin that takes heavy recoil and keeps going becomes Basculegion."),
        TrialSite("211-904-qwilfish-hisui", "cobalt-coastlands", 8, "Barb Barrage in strong style", "A Hisuian Qwilfish that uses Barb Barrage in strong style twenty times grows into Overqwil."),
        TrialSite("57-979-any", "tagtree-thicket", 8, "Twenty Rage Fists", "A Primeape whose rage never ends, after twenty Rage Fists, goes beyond death."),
        TrialSite("625-983-any", "dalizapa-passage", 8, "Three Bisharp leaders", "Bisharp that defeat three Bisharp leading Pawniard become the leader of all of them."),
    )

    private val byKey = all.associateBy { it.key }

    const val WALK_FLOORS = 5

    fun of(e: Evolution): TrialSite? = byKey[EvolutionRules.trialId(e)]

    fun open(p: OwnedPokemon, done: Set<String>): List<Pair<Evolution, TrialSite>> =
        EvolutionRules.applicable(p).filter { EvolutionTrials.needsWorld(it) && (EvolutionRules.requiredLevel(it) ?: 0) <= p.level && EvolutionTrials.doneKey(it, p.id) !in done }
            .mapNotNull { e -> of(e)?.let { e to it } }

    internal fun advance(data: WorldData, dungeonId: String, floor: Int, members: List<OwnedPokemon>, events: MutableList<WorldEvent>): WorldData {
        if (floor <= 0) return data
        var d = data
        for (p in members) for ((e, site) in open(p, d.evolutionTrials)) {
            if (site.dungeonId != dungeonId || floor < site.floor) continue
            d = d.copy(evolutionTrials = d.evolutionTrials + EvolutionTrials.doneKey(e, p.id))
            events += WorldEvent.TrialDone(p.nickname, Dex.form(e.evolvedForm ?: "")?.name ?: Dex.species(e.to).name, site.title)
        }
        if (floor >= WALK_FLOORS) for (p in members) for (e in EvolutionRules.applicable(p)) {
            if (EvolutionTrials.device(e) != DeviceAction.STEPS || (EvolutionRules.requiredLevel(e) ?: 0) > p.level) continue
            val key = EvolutionTrials.doneKey(e, p.id)
            if (key in d.evolutionTrials) continue
            d = d.copy(evolutionTrials = d.evolutionTrials + key)
            events += WorldEvent.TrialDone(p.nickname, Dex.species(e.to).name, "A long walk")
        }
        return d
    }
}
