package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Evolution
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.Gender
import app.pokeplan.core.dex.TimeOfDay

/** What the player has on hand when evolving. [items] maps item id to count. */
data class EvolutionContext(
    val hourOfDay: Int,
    val items: Map<String, Int> = emptyMap(),
    /** Done trials (WORLD jobs and phone actions), as [EvolutionTrials.doneKey]. */
    val completedTrials: Set<String> = emptySet(),
    /** The other members of the Pokémon's party, for evolutions that look at the party (Mantyke, Pancham). */
    val partyMates: List<OwnedPokemon> = emptyList(),
)

/** One way a Pokémon could evolve right now. [missing] lists what is still needed; empty means it can evolve. */
data class EvolutionOption(val evolution: Evolution, val target: FormInfo, val missing: List<String>) {
    val available: Boolean get() = missing.isEmpty()
}

data class EvolutionResult(val pokemon: OwnedPokemon, val consumed: Map<String, Int>)

/**
 * Evolution in the app: every game trigger maps to something playable (see "Evolution" in docs/design.md).
 * Level evolutions, friendship, gender, time-of-day, random-branch and trial evolutions cost one Super Candy;
 * stone evolutions use the stone; trade evolutions use a Link Cord. A special evolution ([EvolutionKind.TRIAL]) asks what
 * [EvolutionTrials.needs] reads from its game condition: checked on the spot, done with the phone, or an Evolution Trial in WORLD.
 * A Pokémon holding an Everstone does not evolve.
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

    /** One id per evolution row: two rows between the same species (Mr. Mime and Galarian Mr. Mime) differ in the form they give. */
    fun trialId(e: Evolution): String = "${e.from}-${e.to}-${e.requiredForm ?: "any"}" + (e.evolvedForm?.let { "-$it" } ?: "")

    /** The evolutions that apply to [p]'s form: rows naming its exact form win over generic rows. */
    fun applicable(p: OwnedPokemon): List<Evolution> {
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

    /** A Pokémon holding an Everstone never evolves (the games' rule): every option says so and none is available. */
    fun options(p: OwnedPokemon, ctx: EvolutionContext): List<EvolutionOption> {
        val rows = applicable(p)
        val branch = randomBranch(p, rows)
        val held = p.heldItem == EvolutionTrials.EVERSTONE
        return rows.filter { it.kind != EvolutionKind.RANDOM || it == branch }.map { e ->
            EvolutionOption(e, targetForm(e), (if (held) listOf(EVERSTONE_HELD) else emptyList()) + missing(p, e, ctx))
        }
    }

    /** What an option lists while its Pokémon holds an Everstone. */
    const val EVERSTONE_HELD = "to let go of its Everstone"

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
                level(); time()
                trial(p, e, ctx, missing)
                if (!EvolutionTrials.spendsInstead(e)) candy(ctx, missing)
            }
        }
        return missing
    }

    private fun trial(p: OwnedPokemon, e: Evolution, ctx: EvolutionContext, missing: MutableList<String>) {
        val done = EvolutionTrials.doneKey(e, p.id) in ctx.completedTrials
        for (n in EvolutionTrials.needs(e)) when (n) {
            is TrialNeed.Move -> if (p.activeMoves.none { it.identifier == n.move }) missing += "to know ${moveName(n.move)}"
            is TrialNeed.MoveType -> if (p.activeMoves.none { it.type == n.type }) missing += "to know a ${n.type.label} move"
            TrialNeed.Friendship -> if (p.friendship < FriendshipRules.EVOLVE_AT) missing += "friendship ${FriendshipRules.EVOLVE_AT}"
            is TrialNeed.Hold -> if (p.heldItem != n.item) missing += "to hold ${Items.name(n.item)}"
            is TrialNeed.PartySpecies -> if (ctx.partyMates.none { it.speciesId == n.speciesId }) missing += "a ${app.pokeplan.core.dex.Dex.species(n.speciesId).name} in its party"
            is TrialNeed.PartyType -> if (ctx.partyMates.none { n.type in it.form.types }) missing += "a ${n.type.label} type in its party"
            is TrialNeed.Stats -> {
                val s = p.stats
                val ok = when { n.sign > 0 -> s.attack > s.defense; n.sign < 0 -> s.attack < s.defense; else -> s.attack == s.defense }
                if (!ok) missing += when { n.sign > 0 -> "Attack above Defense"; n.sign < 0 -> "Defense above Attack"; else -> "Attack equal to Defense" }
            }
            is TrialNeed.Natures -> if (p.nature !in n.natures) missing += "another nature"
            is TrialNeed.Spend -> if ((ctx.items[n.item] ?: 0) < n.count) missing += "${n.count} ${Items.name(n.item)}"
            TrialNeed.Linked -> if ((ctx.items[LINK_CORD] ?: 0) < 1) missing += "a Link Cord"
            is TrialNeed.Device -> if (!done) missing += n.action.label.replaceFirstChar { it.lowercase() }
            is TrialNeed.World -> if (!done) missing += "its Evolution Trial in World"
        }
    }

    private fun moveName(id: String): String = app.pokeplan.core.dex.Dex.move(id)?.name ?: id.split('-').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    /** A trial's needs in a few words, for the Pokédex. */
    private fun trialHowTo(e: Evolution): List<String?> = EvolutionTrials.needs(e).map { n ->
        when (n) {
            is TrialNeed.Move -> "knowing ${moveName(n.move)}"
            is TrialNeed.MoveType -> "knowing a ${n.type.label} move"
            TrialNeed.Friendship -> "Friendship"
            is TrialNeed.Hold -> "holding ${Items.name(n.item)}"
            is TrialNeed.PartySpecies -> "${app.pokeplan.core.dex.Dex.species(n.speciesId).name} in the party"
            is TrialNeed.PartyType -> "a ${n.type.label} type in the party"
            is TrialNeed.Stats -> when { n.sign > 0 -> "Attack > Defense"; n.sign < 0 -> "Attack < Defense"; else -> "Attack = Defense" }
            is TrialNeed.Natures -> "by nature"
            is TrialNeed.Spend -> "${n.count} ${Items.name(n.item)}"
            TrialNeed.Linked -> "Link Cord"
            is TrialNeed.Device -> n.action.label
            is TrialNeed.World -> "Evolution Trial in World"
        }
    }

    /**
     * How an evolution works in the app, in a few words for the Pokédex: "Lv. 16, Super Candy", "Fire Stone",
     * "Link Cord, holding Metal Coat", "Friendship, Super Candy, at night".
     */
    fun howTo(e: Evolution): String {
        val level = requiredLevel(e)?.let { "Lv. $it" }
        val time = e.timeOfDay?.let { if (it == TimeOfDay.DAY) "in the day" else "at ${it.name.lowercase()}" }
        val gender = e.gender?.let { if (it == Gender.FEMALE) "female" else "male" }
        val parts = when (e.kind) {
            EvolutionKind.LEVEL -> listOf(level, "Super Candy")
            EvolutionKind.RANDOM -> listOf(level, "Super Candy", "decided at hatch")
            EvolutionKind.STONE -> listOf(e.item?.let { Items.name(it) })
            EvolutionKind.TRADE -> listOf("Link Cord", e.heldItem?.let { "holding ${Items.name(it)}" })
            EvolutionKind.FRIENDSHIP -> listOf("Friendship", "Super Candy", time)
            EvolutionKind.LEVEL_TIME -> listOf(level, "Super Candy", time)
            EvolutionKind.GENDER -> listOf(e.item?.let { Items.name(it) } ?: level, if (e.item == null) "Super Candy" else null, gender)
            EvolutionKind.TRIAL -> listOf(level) + trialHowTo(e) + listOf(if (EvolutionTrials.spendsInstead(e)) null else "Super Candy", time)
        }
        return parts.filterNotNull().joinToString(", ")
    }

    /** What an evolution of this kind uses up. */
    private fun cost(e: Evolution): Map<String, Int> = when (e.kind) {
        EvolutionKind.STONE -> e.item?.let { mapOf(it to 1) }.orEmpty()
        EvolutionKind.TRADE -> mapOf(LINK_CORD to 1)
        EvolutionKind.GENDER -> e.item?.let { mapOf(it to 1) } ?: mapOf(SUPER_CANDY to 1)
        EvolutionKind.TRIAL -> buildMap {
            val needs = EvolutionTrials.needs(e)
            if (!EvolutionTrials.spendsInstead(e)) put(SUPER_CANDY, 1)
            needs.filterIsInstance<TrialNeed.Spend>().forEach { put(it.item, (get(it.item) ?: 0) + it.count) }
            if (TrialNeed.Linked in needs) put(LINK_CORD, 1)
        }
        else -> mapOf(SUPER_CANDY to 1)
    }

    fun evolve(p: OwnedPokemon, option: EvolutionOption): EvolutionResult {
        check(option.available) { "${p.nickname} still needs ${option.missing.joinToString()}" }
        val e = option.evolution
        var evolved = p.evolvedInto(e.to, option.target.identifier)
        // The item it held to evolve is used up (a Metal Coat on a trade, an Oval Stone, a Razor Claw, a Sweet).
        if ((e.kind == EvolutionKind.TRADE || e.kind == EvolutionKind.TRIAL) && e.heldItem != null) evolved = evolved.withHeldItem(null)
        return EvolutionResult(evolved, cost(e))
    }
}
