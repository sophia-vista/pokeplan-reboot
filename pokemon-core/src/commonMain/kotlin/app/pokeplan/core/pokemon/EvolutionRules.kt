package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Evolution
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.TimeOfDay

/** What the player has on hand when evolving. [items] maps item id to count. */
data class EvolutionContext(
    val hourOfDay: Int,
    val items: Map<String, Int> = emptyMap(),
    val completedTrials: Set<String> = emptySet(),
)

/** One way a Pokémon could evolve right now. [missing] lists what is still needed; empty means it can evolve. */
data class EvolutionOption(val evolution: Evolution, val target: FormInfo, val missing: List<String>) {
    val available: Boolean get() = missing.isEmpty()
}

data class EvolutionResult(val pokemon: OwnedPokemon, val consumed: Map<String, Int>)

/**
 * Evolution in the app: every game trigger maps to something playable (see "Evolution" in docs/design.md).
 * Level evolutions, friendship, gender, time-of-day, random-branch and trial evolutions cost one Super Candy;
 * stone evolutions use the stone; trade evolutions use a Link Cord.
 */
object EvolutionRules {
    const val SUPER_CANDY = "super-candy"
    const val LINK_CORD = "link-cord"

    /** The level a level-up evolution needs: the species' game level (Charmander at 16), at least 2. */
    fun requiredLevel(e: Evolution): Int? = e.level?.let { maxOf(2, it) }

    fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
        in 6..16 -> TimeOfDay.DAY
        in 17..18 -> TimeOfDay.DUSK
        else -> TimeOfDay.NIGHT
    }

    fun trialId(e: Evolution): String = "${e.from}-${e.to}-${e.requiredForm ?: "any"}"

    /** The evolutions that apply to [p]'s form: rows naming its exact form win over generic rows. */
    private fun applicable(p: OwnedPokemon): List<Evolution> {
        val all = Dex.evolutionsFrom(p.speciesId)
        val exact = all.filter { it.requiredForm == p.formIdentifier }
        return exact.ifEmpty { all.filter { it.requiredForm == null } }
    }

    private fun targetForm(e: Evolution): FormInfo =
        e.evolvedForm?.let { Dex.form(it) } ?: Dex.defaultForm(e.to)

    /** For random-branch evolutions, the one branch this Pokémon's personality picks. */
    private fun randomBranch(p: OwnedPokemon, rows: List<Evolution>): Evolution? {
        val branches = rows.filter { it.kind == EvolutionKind.RANDOM }.sortedBy { it.to }
        if (branches.isEmpty()) return null
        var roll = p.personality % 100
        for (b in branches) {
            roll -= b.chancePercent ?: (100 / branches.size)
            if (roll < 0) return b
        }
        return branches.last()
    }

    fun options(p: OwnedPokemon, ctx: EvolutionContext): List<EvolutionOption> {
        val rows = applicable(p)
        val branch = randomBranch(p, rows)
        return rows.filter { it.kind != EvolutionKind.RANDOM || it == branch }.map { e ->
            EvolutionOption(e, targetForm(e), missing(p, e, ctx))
        }
    }

    private fun candy(ctx: EvolutionContext, missing: MutableList<String>) {
        if ((ctx.items[SUPER_CANDY] ?: 0) < 1) missing += "a Super Candy"
    }

    private fun missing(p: OwnedPokemon, e: Evolution, ctx: EvolutionContext): List<String> {
        val missing = mutableListOf<String>()
        e.gender?.let { if (p.gender != it) missing += "to be ${it.name.lowercase()}" }
        fun level() = requiredLevel(e)?.let { if (p.level < it) missing += "level $it" }
        fun time() = e.timeOfDay?.let {
            if (timeOfDay(ctx.hourOfDay) != it) missing += "to be ${it.name.lowercase()}time"
        }
        when (e.kind) {
            EvolutionKind.LEVEL, EvolutionKind.RANDOM -> {
                level(); candy(ctx, missing)
            }
            EvolutionKind.STONE -> {
                val item = e.item
                if (item != null && (ctx.items[item] ?: 0) < 1) missing += item.replace('-', ' ')
            }
            EvolutionKind.TRADE -> {
                if ((ctx.items[LINK_CORD] ?: 0) < 1) missing += "a Link Cord"
                e.heldItem?.let { if (p.heldItem != it) missing += "to hold ${it.replace('-', ' ')}" }
            }
            EvolutionKind.FRIENDSHIP -> {
                if (p.friendship < FriendshipRules.EVOLVE_AT) missing += "friendship ${FriendshipRules.EVOLVE_AT}"
                time(); candy(ctx, missing)
            }
            EvolutionKind.LEVEL_TIME -> {
                level(); time(); candy(ctx, missing)
            }
            EvolutionKind.GENDER -> {
                val item = e.item
                if (item != null) {
                    if ((ctx.items[item] ?: 0) < 1) missing += item.replace('-', ' ')
                } else {
                    level(); candy(ctx, missing)
                }
            }
            EvolutionKind.TRIAL -> {
                level()
                if (trialId(e) !in ctx.completedTrials) missing += "the evolution trial"
                candy(ctx, missing)
            }
        }
        return missing
    }

    /** What an evolution of this kind uses up. */
    private fun cost(e: Evolution): Map<String, Int> = when (e.kind) {
        EvolutionKind.STONE -> e.item?.let { mapOf(it to 1) }.orEmpty()
        EvolutionKind.TRADE -> mapOf(LINK_CORD to 1)
        EvolutionKind.GENDER -> e.item?.let { mapOf(it to 1) } ?: mapOf(SUPER_CANDY to 1)
        else -> mapOf(SUPER_CANDY to 1)
    }

    fun evolve(p: OwnedPokemon, option: EvolutionOption): EvolutionResult {
        check(option.available) { "${p.nickname} still needs ${option.missing.joinToString()}" }
        val e = option.evolution
        var evolved = p.evolvedInto(e.to, option.target.identifier)
        if (e.kind == EvolutionKind.TRADE && e.heldItem != null) evolved = evolved.withHeldItem(null)
        return EvolutionResult(evolved, cost(e))
    }
}
