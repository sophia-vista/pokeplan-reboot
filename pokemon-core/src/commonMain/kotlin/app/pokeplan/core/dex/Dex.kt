package app.pokeplan.core.dex

object Dex {
    val species: List<SpeciesInfo> by lazy { DexParser.species() }
    val forms: List<FormInfo> by lazy { DexParser.forms() }
    val evolutions: List<Evolution> by lazy { DexParser.evolutions() }

    private val speciesById: Map<Int, SpeciesInfo> by lazy { species.associateBy { it.id } }
    private val formsBySpecies: Map<Int, List<FormInfo>> by lazy { forms.groupBy { it.speciesId } }
    private val formsByIdentifier: Map<String, FormInfo> by lazy { forms.associateBy { it.identifier } }
    private val evolutionsFromId: Map<Int, List<Evolution>> by lazy { evolutions.groupBy { it.from } }
    private val evolutionsIntoId: Map<Int, List<Evolution>> by lazy { evolutions.groupBy { it.to } }

    private val entries: Map<Int, String> by lazy { DexParser.entries() }

    fun entry(speciesId: Int): String = entries.getValue(speciesId)

    private val regionData: Map<String, List<Int>> by lazy { DexParser.regions() }

    fun regionDex(region: Region): List<SpeciesInfo> =
        regionData[region.name].orEmpty().map { speciesById.getValue(it) }

    internal val hisuiSpecies: Set<Int> by lazy { regionData.getValue("HISUI").toSet() }

    val moves: List<MoveInfo> by lazy { DexParser.moves() }
    private val movesById: Map<Int, MoveInfo> by lazy { moves.associateBy { it.id } }
    private val movesByIdentifier: Map<String, MoveInfo> by lazy { moves.associateBy { it.identifier } }
    private val learnsets: Map<Int, Learnset> by lazy { DexParser.learnsets() }

    fun move(id: Int): MoveInfo? = movesById[id]

    fun move(identifier: String): MoveInfo? = movesByIdentifier[identifier]

    fun learnset(form: FormInfo): Learnset {
        val own = if (form.kind == FormKind.MEGA || form.kind == FormKind.GMAX) null else learnsets[form.id]
        return own ?: learnsets[defaultForm(form.speciesId).id] ?: Learnset.EMPTY
    }

    fun learnset(speciesId: Int): Learnset = learnset(defaultForm(speciesId))

    fun movesOfType(type: PokemonType): List<MoveInfo> = moves.filter { it.type == type }.sortedByDescending { it.power ?: 0 }

    fun species(id: Int): SpeciesInfo = speciesById.getValue(id)

    fun speciesOrNull(id: Int): SpeciesInfo? = speciesById[id]

    fun form(identifier: String): FormInfo? = formsByIdentifier[identifier]

    fun formsOf(speciesId: Int): List<FormInfo> = formsBySpecies[speciesId].orEmpty()

    fun defaultForm(speciesId: Int): FormInfo = formsOf(speciesId).first { it.kind == FormKind.DEFAULT }

    fun evolutionsFrom(speciesId: Int): List<Evolution> = evolutionsFromId[speciesId].orEmpty()

    fun evolutionsInto(speciesId: Int): List<Evolution> = evolutionsIntoId[speciesId].orEmpty()

    fun lineRoot(speciesId: Int): Int {
        var current = species(speciesId)
        while (true) current = current.evolvesFrom?.let { species(it) } ?: return current.id
    }

    fun lineEvolutions(speciesId: Int): List<Evolution> {
        val out = mutableListOf<Evolution>()
        val queue = ArrayDeque(listOf(lineRoot(speciesId)))
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            evolutionsFrom(from).sortedBy { it.requiredForm != null || it.evolvedForm != null }.distinctBy { it.to }.sortedBy { it.to }.forEach { e ->
                if (out.none { it.to == e.to }) {
                    out += e
                    queue += e.to
                }
            }
        }
        return out
    }

    private val lineStrengthByRoot: Map<Int, Int> by lazy {
        species.groupBy { lineRoot(it.id) }.mapValues { (_, members) -> members.maxOf { defaultForm(it.id).stats.total } }
    }

    fun lineStrength(speciesId: Int): Int = lineStrengthByRoot.getValue(lineRoot(speciesId))

    fun regionOfForm(form: FormInfo): Region? = when {
        form.kind != FormKind.REGIONAL -> null
        form.identifier.endsWith("-alola") -> Region.ALOLA
        form.identifier.endsWith("-galar") -> Region.GALAR
        form.identifier.endsWith("-hisui") -> Region.SINNOH
        form.identifier.contains("-paldea") -> Region.PALDEA
        else -> null
    }

    fun legendaryQuestSpecies(region: Region): List<SpeciesInfo> = regionDex(region).filter { it.isLegendaryClass }

    val mythicals: List<SpeciesInfo> by lazy { species.filter { it.isMythical } }
}
