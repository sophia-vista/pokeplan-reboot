package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.FormInfo
import app.pokeplan.core.dex.FormKind
import kotlin.random.Random

/** How a player gets an alternate or Gigantamax form (docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 3.3.1). */
enum class FormRoute {
    /** Shown, and counted in the form dex, while the Pokémon holds the form's item (or the Dynamax Band for Gigantamax). */
    ITEM,

    /** Reached by an ordinary evolution row (Lycanroc's forms, Wormadam's cloaks). */
    EVOLUTION,

    /** A find of its own, like Unown's letters: a hatch of the species rolls among its variants. */
    ENCOUNTER,

    /** The same, but rare (a costume, an event-only form). */
    RARE_ENCOUNTER,

    /** A battle form or ride mode: runtime state, not owned identity. Never counted. */
    BATTLE,

    /** A Totem or boss form. Never counted. */
    BOSS,
}

/** A key item that makes a Pokémon show one of its forms while held; [forms] maps a species id to its form. */
data class FormItem(val id: String, val name: String, val forms: Map<Int, String>)

object FormRoutes {
    const val RARE_ODDS = 20

    private fun item(id: String, name: String, vararg forms: Pair<Int, String>) = FormItem(id, name, forms.toMap())

    val formItems: List<FormItem> = listOf(
        item("meteorite-attack", "Meteorite (Attack)", 386 to "deoxys-attack"),
        item("meteorite-defense", "Meteorite (Defense)", 386 to "deoxys-defense"),
        item("meteorite-speed", "Meteorite (Speed)", 386 to "deoxys-speed"),
        item("gracidea", "Gracidea", 492 to "shaymin-sky"),
        item("griseous-orb", "Griseous Orb", 487 to "giratina-origin"),
        item("adamant-crystal", "Adamant Crystal", 483 to "dialga-origin"),
        item("lustrous-globe", "Lustrous Globe", 484 to "palkia-origin"),
        item("appliance-heat", "Heat Appliance", 479 to "rotom-heat"),
        item("appliance-wash", "Wash Appliance", 479 to "rotom-wash"),
        item("appliance-frost", "Frost Appliance", 479 to "rotom-frost"),
        item("appliance-fan", "Fan Appliance", 479 to "rotom-fan"),
        item("appliance-mow", "Mow Appliance", 479 to "rotom-mow"),
        item("reveal-glass", "Reveal Glass", 641 to "tornadus-therian", 642 to "thundurus-therian", 645 to "landorus-therian", 905 to "enamorus-therian"),
        item("dark-stone", "Dark Stone", 646 to "kyurem-black"),
        item("light-stone", "Light Stone", 646 to "kyurem-white"),
        item("prison-bottle", "Prison Bottle", 720 to "hoopa-unbound"),
        item("yellow-nectar", "Yellow Nectar", 741 to "oricorio-pom-pom"),
        item("pink-nectar", "Pink Nectar", 741 to "oricorio-pau"),
        item("purple-nectar", "Purple Nectar", 741 to "oricorio-sensu"),
        item("zygarde-cell", "Zygarde Cell", 718 to "zygarde-10"),
        item("zygarde-cube", "Zygarde Cube", 718 to "zygarde-complete"),
        item("n-solarizer", "N-Solarizer", 800 to "necrozma-dusk"),
        item("n-lunarizer", "N-Lunarizer", 800 to "necrozma-dawn"),
        item("ultranecrozium-z", "Ultranecrozium Z", 800 to "necrozma-ultra"),
        item("rusted-sword", "Rusted Sword", 888 to "zacian-crowned"),
        item("rusted-shield", "Rusted Shield", 889 to "zamazenta-crowned"),
        item("scroll-of-waters", "Scroll of Waters", 892 to "urshifu-rapid-strike"),
        item("reins-of-unity-ice", "Reins of Unity (Ice)", 898 to "calyrex-ice"),
        item("reins-of-unity-shadow", "Reins of Unity (Shadow)", 898 to "calyrex-shadow"),
        item("wellspring-mask", "Wellspring Mask", 1017 to "ogerpon-wellspring-mask"),
        item("hearthflame-mask", "Hearthflame Mask", 1017 to "ogerpon-hearthflame-mask"),
        item("cornerstone-mask", "Cornerstone Mask", 1017 to "ogerpon-cornerstone-mask"),
    )

    private val itemsById: Map<String, FormItem> = formItems.associateBy { it.id }
    private val itemOfForm: Map<String, String> = formItems.flatMap { i -> i.forms.values.map { it to i.id } }.toMap()

    /** Reached by an ordinary evolution row, so the evolution closure already covers them. */
    private val byEvolution = setOf(
        "wormadam-sandy", "wormadam-trash", "lycanroc-midnight", "lycanroc-dusk", "darmanitan-galar-standard", "toxtricity-low-key",
        "basculin-white-striped", "dudunsparce-three-segment", "maushold-family-of-three",
    )

    /** Natural variants of a wild or hatched Pokémon: a size, a plumage, a colour, a style. */
    private val encounters = setOf(
        "pumpkaboo-small", "pumpkaboo-large", "pumpkaboo-super", "gourgeist-small", "gourgeist-large", "gourgeist-super",
        "squawkabilly-blue-plumage", "squawkabilly-yellow-plumage", "squawkabilly-white-plumage",
        "tatsugiri-droopy", "tatsugiri-stretchy", "basculin-blue-striped", "gimmighoul-roaming", "rockruff-own-tempo",
        "minior-orange-meteor", "minior-yellow-meteor", "minior-green-meteor", "minior-blue-meteor", "minior-indigo-meteor", "minior-violet-meteor",
    )

    /** Costume, partner and event-only variants. */
    private val rareEncounters = setOf(
        "pikachu-rock-star", "pikachu-belle", "pikachu-pop-star", "pikachu-phd", "pikachu-libre", "pikachu-cosplay",
        "pikachu-original-cap", "pikachu-hoenn-cap", "pikachu-sinnoh-cap", "pikachu-unova-cap", "pikachu-kalos-cap",
        "pikachu-alola-cap", "pikachu-partner-cap", "pikachu-world-cap", "pikachu-starter", "eevee-starter",
    )

    private val boss = setOf(
        "eternatus-eternamax", "dialga-primal", "gumshoos-totem", "vikavolt-totem", "lurantis-totem", "salazzle-totem",
        "mimikyu-totem-disguised", "mimikyu-totem-busted", "kommo-o-totem", "marowak-totem", "ribombee-totem",
        "araquanid-totem", "togedemaru-totem",
        // One unique NPC or event Pokémon each, in no wild table or egg pool.
        "floette-eternal", "magearna-original", "zarude-dada", "ursaluna-bloodmoon",
    )

    /** Stances, Zen Mode, Ash-Greninja, Terastal, ride modes, a Minior core: runtime state (spec 3.3). */
    private val battle = setOf(
        "castform-sunny", "castform-rainy", "castform-snowy", "darmanitan-zen", "darmanitan-galar-zen", "meloetta-pirouette",
        "keldeo-resolute", "aegislash-blade", "wishiwashi-school", "greninja-battle-bond", "greninja-ash",
        "zygarde-10-power-construct", "zygarde-50-power-construct", "mimikyu-busted", "cramorant-gulping", "cramorant-gorging",
        "eiscue-noice", "morpeko-hangry", "palafin-hero", "terapagos-terastal", "terapagos-stellar",
        "minior-red", "minior-orange", "minior-yellow", "minior-green", "minior-blue", "minior-indigo", "minior-violet",
        "koraidon-limited-build", "koraidon-sprinting-build", "koraidon-swimming-build", "koraidon-gliding-build",
        "miraidon-low-power-mode", "miraidon-drive-mode", "miraidon-aquatic-mode", "miraidon-glide-mode",
    )

    /** The route of an alternate or Gigantamax form; null for any other kind, and for a form nobody has classified yet. */
    fun route(form: FormInfo): FormRoute? {
        val id = form.identifier
        return when (form.kind) {
            FormKind.GMAX -> if (id in boss) FormRoute.BOSS else FormRoute.ITEM
            FormKind.ALTERNATE -> when (id) {
                in itemOfForm -> FormRoute.ITEM
                in byEvolution -> FormRoute.EVOLUTION
                in encounters -> FormRoute.ENCOUNTER
                in rareEncounters -> FormRoute.RARE_ENCOUNTER
                in battle -> FormRoute.BATTLE
                in boss -> FormRoute.BOSS
                else -> null
            }
            else -> null
        }
    }

    fun isFormItem(id: String): Boolean = id in itemsById

    fun formItem(id: String): FormItem? = itemsById[id]

    /** The item that shows [form], or the Dynamax Band for a Gigantamax form. */
    fun itemFor(form: FormInfo): String? = if (form.kind == FormKind.GMAX) Items.DYNAMAX_BAND else itemOfForm[form.identifier]

    /** The form [p] shows because of what it holds, if any: a form item, or Gigantamax with the Dynamax Band. */
    fun heldForm(p: OwnedPokemon): FormInfo? {
        val held = p.heldItem ?: return null
        if (held == Items.DYNAMAX_BAND) return Dex.form("${p.formIdentifier}-gmax")?.takeIf { it.kind == FormKind.GMAX && route(it) == FormRoute.ITEM }
        return itemsById[held]?.forms?.get(p.speciesId)?.let { Dex.form(it) }
    }

    /** True for a form the form dex counts: one a player can have. */
    fun counts(form: FormInfo): Boolean = when (route(form)) {
        FormRoute.ITEM, FormRoute.EVOLUTION, FormRoute.ENCOUNTER, FormRoute.RARE_ENCOUNTER -> true
        else -> false
    }

    private val variantsBySpecies: Map<Int, Pair<List<FormInfo>, Boolean>> by lazy {
        Dex.forms.filter { it.kind == FormKind.ALTERNATE }
            .mapNotNull { f -> route(f)?.takeIf { it == FormRoute.ENCOUNTER || it == FormRoute.RARE_ENCOUNTER }?.let { f to (it == FormRoute.RARE_ENCOUNTER) } }
            .groupBy { it.first.speciesId }
            .mapValues { (_, list) -> list.map { it.first } to list.any { it.second } }
    }

    /** The separate-encounter variants of a species, in dex order. */
    fun variants(speciesId: Int): List<FormInfo> = variantsBySpecies[speciesId]?.first.orEmpty()

    /**
     * Rolls the form a new Pokémon of [speciesId] is found in, given its [base] form. A species with natural variants
     * picks uniformly among its base form and them; a species with only rare ones picks a variant one time in
     * [RARE_ODDS]. Draws nothing from [rng] for a species without variants, so existing rolls are unchanged.
     */
    fun rollVariant(speciesId: Int, base: FormInfo, rng: Random): FormInfo {
        val (list, _) = variantsBySpecies[speciesId] ?: return base
        if (base.kind != FormKind.DEFAULT) return base
        val natural = list.filter { route(it) == FormRoute.ENCOUNTER }
        if (natural.isNotEmpty()) return (listOf(base) + natural)[rng.nextInt(natural.size + 1)]
        return if (rng.nextInt(RARE_ODDS) == 0) list[rng.nextInt(list.size)] else base
    }

    /** The form an evolved Pokémon of [toSpecies] takes from its pre-evolution's variant (Pumpkaboo Large to Gourgeist Large). */
    fun carryOver(from: FormInfo, toSpecies: Int): FormInfo? {
        if (from.kind != FormKind.ALTERNATE) return null
        val suffix = from.identifier.removePrefix(Dex.species(from.speciesId).identifier)
        if (suffix.isEmpty() || suffix == from.identifier) return null
        return Dex.form(Dex.species(toSpecies).identifier + suffix)?.takeIf { it.speciesId == toSpecies && it.kind == FormKind.ALTERNATE }
    }
}
