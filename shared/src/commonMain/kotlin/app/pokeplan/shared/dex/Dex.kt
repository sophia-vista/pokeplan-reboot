package app.pokeplan.shared.dex

/** The National Dex: every species through Gen 9, their forms, evolutions and the regional dex lists. */
object Dex {
    val species: List<SpeciesInfo> by lazy { DexParser.species() }
    val forms: List<FormInfo> by lazy { DexParser.forms() }
    val evolutions: List<Evolution> by lazy { DexParser.evolutions() }

    private val speciesById: Map<Int, SpeciesInfo> by lazy { species.associateBy { it.id } }
    private val formsBySpecies: Map<Int, List<FormInfo>> by lazy { forms.groupBy { it.speciesId } }
    private val formsByIdentifier: Map<String, FormInfo> by lazy { forms.associateBy { it.identifier } }
    private val evolutionsFromId: Map<Int, List<Evolution>> by lazy { evolutions.groupBy { it.from } }
    private val evolutionsIntoId: Map<Int, List<Evolution>> by lazy { evolutions.groupBy { it.to } }

    private val regionData: Map<String, List<Int>> by lazy { DexParser.regions() }

    /** Species of [region] in dex order. For [Region.HOME] the position is the Home Region's own number. */
    fun regionDex(region: Region): List<SpeciesInfo> =
        regionData.getValue(region.name).map { speciesById.getValue(it) }

    /** Species that have a Hisuian form in the Hisui dex (used to pick the Hisuian variant in Sinnoh). */
    internal val hisuiSpecies: Set<Int> by lazy { regionData.getValue("HISUI").toSet() }

    fun species(id: Int): SpeciesInfo = speciesById.getValue(id)

    fun speciesOrNull(id: Int): SpeciesInfo? = speciesById[id]

    fun form(identifier: String): FormInfo? = formsByIdentifier[identifier]

    fun formsOf(speciesId: Int): List<FormInfo> = formsBySpecies[speciesId].orEmpty()

    fun defaultForm(speciesId: Int): FormInfo = formsOf(speciesId).first { it.kind == FormKind.DEFAULT }

    fun evolutionsFrom(speciesId: Int): List<Evolution> = evolutionsFromId[speciesId].orEmpty()

    fun evolutionsInto(speciesId: Int): List<Evolution> = evolutionsIntoId[speciesId].orEmpty()

    /** The first species of the evolution line [speciesId] belongs to. */
    fun lineRoot(speciesId: Int): Int {
        var current = species(speciesId)
        while (true) current = current.evolvesFrom?.let { species(it) } ?: return current.id
    }

    private val lineStrengthByRoot: Map<Int, Int> by lazy {
        species.groupBy { lineRoot(it.id) }.mapValues { (_, members) -> members.maxOf { defaultForm(it.id).stats.total } }
    }

    /** The strongest base stat total (default forms) in the species' whole evolution line. */
    fun lineStrength(speciesId: Int): Int = lineStrengthByRoot.getValue(lineRoot(speciesId))

    /** The region a regional form belongs to (Hisuian forms belong to Sinnoh), or null for other forms. */
    fun regionOfForm(form: FormInfo): Region? = when {
        form.kind != FormKind.REGIONAL -> null
        form.identifier.endsWith("-alola") -> Region.ALOLA
        form.identifier.endsWith("-galar") -> Region.GALAR
        form.identifier.endsWith("-hisui") -> Region.SINNOH
        form.identifier.contains("-paldea") -> Region.PALDEA
        else -> null
    }

    /** Legendary-class species that appear in [region]: the targets of its legendary quests. */
    fun legendaryQuestSpecies(region: Region): List<SpeciesInfo> = regionDex(region).filter { it.isLegendaryClass }

    /** Mythical species: milestone rewards, not tied to a region. */
    val mythicals: List<SpeciesInfo> by lazy { species.filter { it.isMythical } }
}
