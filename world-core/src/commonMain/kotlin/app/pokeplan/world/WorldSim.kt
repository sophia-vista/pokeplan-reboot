package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.math.min
import kotlin.random.Random

sealed interface WorldEvent {
    data class FloorCleared(val dungeonId: String, val floor: Int) : WorldEvent
    data class Found(val speciesId: Int, val dungeonId: String, val floor: Int) : WorldEvent
    data class Item(val itemId: String, val count: Int) : WorldEvent
    data class Poke(val amount: Int) : WorldEvent
    data class Treasure(val tier: Int) : WorldEvent
    data class Milestone(val dungeonId: String, val floor: Int, val reward: MilestoneReward) : WorldEvent
    data class Cleared(val dungeonId: String) : WorldEvent
    data class Setback(val dungeonId: String, val floor: Int) : WorldEvent
    data class Retreat(val dungeonId: String, val floor: Int) : WorldEvent
    data class MissionDone(val missionId: String) : WorldEvent
    data class TrialDone(val pokemonName: String, val into: String, val title: String) : WorldEvent
    data class ChoiceOffered(val kind: ChoiceKind, val dungeonId: String, val floor: Int) : WorldEvent
    data class FloorEvent(val kind: FloorEventKind, val dungeonId: String, val floor: Int, val userName: String?, val gave: String) : WorldEvent
    data object GuardianFaced : WorldEvent
    data class LayerGuardianFaced(val layer: WorldLayer) : WorldEvent
}

data class Suitability(val floorMillis: Long, val favored: Int, val size: Int)

data class SpeedExplanation(
    val levels: Int,
    val favoredMembers: Int,
    val favoredBonus: Double,
    val identityBonus: Double = 0.0,
    val identityLines: List<String> = emptyList(),
    val power: Double,
    val baseMillis: Long,
    val afterPowerMillis: Long,
    val atMinimum: Boolean,
    val masteryPercent: Int,
    val legendPercent: Int,
    val floorMillis: Long,
    val skillHelp: Map<WorldSkill, Int>,
)

data class WorldReport(
    val data: WorldData,
    val perTeam: Map<Int, List<WorldEvent>>,
    val memberFloors: Map<String, Int> = emptyMap(),
) {
    val events: List<WorldEvent> get() = perTeam.values.flatten()
    val isEmpty: Boolean get() = events.isEmpty()
}

object WorldSim {
    const val MAX_AWAY_MILLIS = 12L * 60 * 60 * 1000

    const val XP_BASE = 4

    const val FIND_PERCENT = 3

    const val BERRY_PERCENT = 70
    const val TREASURE_PERCENT = 6

    const val COIN_PERCENT = 30
    const val COIN_MAX = 8

    fun power(members: List<OwnedPokemon>, dungeon: DungeonDef): Double =
        members.sumOf { contribution(it, dungeon) }

    fun contribution(p: OwnedPokemon, dungeon: DungeonDef): Double = BattleIdentity.breakdown(p, dungeon).power

    fun bestTeam(dungeon: DungeonDef, candidates: List<OwnedPokemon>, size: Int = WorldTeam.MAX_MEMBERS): List<OwnedPokemon> =
        candidates.sortedWith(
            compareByDescending<OwnedPokemon> { contribution(it, dungeon) }
                .thenByDescending { p -> p.form.types.any { it in dungeon.favored } }
                .thenByDescending { it.level }.thenBy { it.id },
        ).take(size)

    fun floorMillis(dungeon: DungeonDef, power: Double, mastery: Boolean = false, fasterPercent: Int = 0): Long {
        val base = dungeon.baseFloorSeconds * 1000L
        val factor = 1.0 + power / 100.0
        val millis = maxOf(base / 4, (base / factor).toLong())
        val withMastery = if (mastery) millis * 9 / 10 else millis
        return if (fasterPercent > 0) withMastery * (100 - fasterPercent.coerceAtMost(50)) / 100 else withMastery
    }

    fun settle(data: WorldData, roster: List<OwnedPokemon>, now: Long): WorldReport {
        var out = data
        val perTeam = mutableMapOf<Int, List<WorldEvent>>()
        val memberFloors = mutableMapOf<String, Int>()
        var rewound = data.rewoundFrom
        for (team in data.teams) {
            val stored = team.expedition ?: continue
            val dungeon = Dungeons.byId(stored.dungeonId) ?: continue
            val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
            if (members.isEmpty()) continue
            if (stored.settledTo > now) {
                rewound = maxOf(rewound, stored.settledTo)
                out = out.withTeam((out.team(team.id) ?: team).copy(expedition = stored.copy(settledTo = now)))
                continue
            }
            val trip = if (rewound in (stored.settledTo + 1)..now) stored.copy(settledTo = rewound) else stored
            if (trip != stored) out = out.withTeam((out.team(team.id) ?: team).copy(expedition = trip))
            val per = floorMillis(dungeon, power(members, dungeon), data.guardianDone, data.legendFaster(dungeon))
            val until = min(now, trip.settledTo + MAX_AWAY_MILLIS)
            val floors = ((until - trip.settledTo) / per).toInt()
            if (floors <= 0) continue
            val events = mutableListOf<WorldEvent>()
            var done = 0
            var retreated = false
            for (n in 0 until floors) {
                out = climb(out, team.id, trip, dungeon, trip.floorsClimbed + n, trip.settledTo + (n + 1) * per, members, power(members, dungeon), WorldSkill.entries.associateWith { AbilityPerks.bonus(members.map { m -> m.ability }, it) }, events)
                if (events.lastOrNull() is WorldEvent.Retreat) { retreated = true; break }
                done = n + 1
            }
            val settled = if (now - trip.settledTo > MAX_AWAY_MILLIS) now else trip.settledTo + floors * per
            val current = out.team(team.id) ?: team
            out = out.withTeam(current.copy(expedition = if (retreated) null else trip.copy(settledTo = settled, floorsClimbed = trip.floorsClimbed + floors)))
            perTeam[team.id] = events
            members.forEach { memberFloors[it.id] = (memberFloors[it.id] ?: 0) + done }
        }
        val lines = perTeam.flatMap { (teamId, events) -> events.mapNotNull { journalLine(out.team(teamId)?.title ?: "Team $teamId", it, now) } }
            .fold(mutableListOf<Pair<JournalLine, Int>>()) { acc, l ->
                if (acc.lastOrNull()?.first?.text == l.text) acc[acc.lastIndex] = acc.last().first to acc.last().second + 1 else acc += l to 1
                acc
            }.map { (l, n) -> if (n > 1) l.copy(text = l.text.removeSuffix(".") + " ($n times).") else l }
        out = Choices.expire(out, now)
        return WorldReport(out.copy(lastSeenAt = now, rewoundFrom = if (rewound <= now) 0 else rewound, journal = (out.journal + lines).takeLast(WorldData.JOURNAL_SIZE)), perTeam, memberFloors)
    }

    fun nextFloorAt(data: WorldData, roster: List<OwnedPokemon>, now: Long): Long? {
        var next: Long? = null
        for (team in data.teams) {
            val trip = team.expedition ?: continue
            val dungeon = Dungeons.byId(trip.dungeonId) ?: continue
            val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
            if (members.isEmpty()) continue
            val at = if (trip.settledTo > now) now else doneAt(data, trip, 1, floorMillis(dungeon, power(members, dungeon), data.guardianDone, data.legendFaster(dungeon)))
            if (next == null || at < next) next = at
        }
        return next
    }

    fun jobsDoneAt(data: WorldData, roster: List<OwnedPokemon>): Map<String, Long> {
        val out = mutableMapOf<String, Long>()
        for (p in data.missions) {
            if (p.done) continue
            val m = Missions.byId(p.id) ?: continue
            val dungeon = m.dungeonId?.let { Dungeons.byId(it) } ?: continue
            for (team in data.teams) {
                val trip = team.expedition?.takeIf { it.dungeonId == dungeon.id } ?: continue
                val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
                if (members.isEmpty()) continue
                val power = power(members, dungeon)
                if (m.floor > 0 && Outcomes.of(power, dungeon, m.floor) != Outcomes.Outcome.CLEARED) continue
                if (m.kind == MissionKind.INVESTIGATION || m.kind == MissionKind.STORY) continue
                if ((m.kind == MissionKind.ESCORT && members.size < m.count) || (m.kind == MissionKind.DEFEAT && power < m.count) || (m.kind == MissionKind.CHALLENGE && members.size > m.count)) continue
                val next = trip.floorsClimbed % dungeon.floors
                val floors = maxOf(0, m.floor - 1 - next) + 1
                val at = doneAt(data, trip, floors, floorMillis(dungeon, power, data.guardianDone, data.legendFaster(dungeon)))
                if (out[m.id]?.let { it <= at } != true) out[m.id] = at
            }
        }
        return out
    }

    private fun doneAt(data: WorldData, trip: Expedition, floors: Int, per: Long): Long {
        val plain = trip.settledTo + floors * per
        val mark = data.rewoundFrom
        if (mark <= trip.settledTo || plain <= mark) return plain
        return mark + (floors - (mark - trip.settledTo) / per).toInt() * per
    }

    private fun journalLine(title: String, event: WorldEvent, now: Long): JournalLine? {
        var kind = JournalLine.KIND_NOTE
        var place = ""
        val text = when (event) {
            is WorldEvent.Found -> { kind = JournalLine.KIND_FIND; place = Dungeons.byId(event.dungeonId)?.name.orEmpty(); "$title met a ${Dex.species(event.speciesId).name} on floor ${event.floor} of ${Dungeons.byId(event.dungeonId)?.name}." }
            is WorldEvent.Milestone -> { kind = JournalLine.KIND_MILESTONE; place = Dungeons.byId(event.dungeonId)?.name.orEmpty(); "$title reached floor ${event.floor} of ${Dungeons.byId(event.dungeonId)?.name} and was rewarded." }
            is WorldEvent.Cleared -> { kind = JournalLine.KIND_CLEAR; place = Dungeons.byId(event.dungeonId)?.name.orEmpty(); "$title cleared ${Dungeons.byId(event.dungeonId)?.name}." }
            is WorldEvent.FloorEvent -> { if (event.userName == null) return null; kind = JournalLine.KIND_EVENT; place = Dungeons.byId(event.dungeonId)?.name.orEmpty(); "${event.kind.title}: ${event.userName} ${event.kind.opened} on floor ${event.floor} of $place (${event.gave})." }
            is WorldEvent.Setback -> { kind = JournalLine.KIND_SETBACK; place = Dungeons.byId(event.dungeonId)?.name.orEmpty(); "$title had a setback on floor ${event.floor} of ${Dungeons.byId(event.dungeonId)?.name} and went on." }
            is WorldEvent.Retreat -> { kind = JournalLine.KIND_SETBACK; place = Dungeons.byId(event.dungeonId)?.name.orEmpty(); "$title turned back at floor ${event.floor} of ${Dungeons.byId(event.dungeonId)?.name} and came home safely." }
            is WorldEvent.MissionDone -> { kind = JournalLine.KIND_JOB; "A job is done: ${Missions.byId(event.missionId)?.title}. Report back on the board." }
            is WorldEvent.TrialDone -> { kind = JournalLine.KIND_JOB; "Evolution Trial done: ${event.title}. ${event.pokemonName} can now evolve into ${event.into}." }
            WorldEvent.GuardianFaced -> { kind = JournalLine.KIND_GUARDIAN; "The Guardian was faced. The story is complete." }
            is WorldEvent.LayerGuardianFaced -> { kind = JournalLine.KIND_GUARDIAN; "The Guardian of ${event.layer.label} was faced. Its story is complete." }
            else -> return null
        }
        return JournalLine(now, text, kind, place)
    }

    fun explainSpeed(dungeon: DungeonDef, members: List<OwnedPokemon>, data: WorldData): SpeedExplanation {
        val power = power(members, dungeon)
        val base = dungeon.baseFloorSeconds * 1000L
        val afterPower = maxOf(base / 4, (base / (1.0 + power / 100.0)).toLong())
        val favored = members.filter { p -> p.form.types.any { it in dungeon.favored } }
        val legend = data.legendFaster(dungeon).coerceAtMost(50)
        val identity = members.map { BattleIdentity.breakdown(it, dungeon) }
        return SpeedExplanation(
            levels = members.sumOf { it.level }, favoredMembers = favored.size, favoredBonus = favored.sumOf { it.level } * BattleIdentity.FAVORED_TYPE_FACTOR, power = power,
            identityBonus = identity.sumOf { it.identityPower },
            identityLines = identity.filter { it.parts.isNotEmpty() }.map { b -> "${b.member.nickname}: " + b.parts.joinToString(", ") { "${it.label} +${it.percent}%" } + (if (b.percent < b.parts.sumOf { it.percent }) " (capped at +${BattleIdentity.MAX_PERCENT}%)" else "") },
            baseMillis = base, afterPowerMillis = afterPower, atMinimum = afterPower == base / 4,
            masteryPercent = if (data.guardianDone) 10 else 0, legendPercent = legend,
            floorMillis = floorMillis(dungeon, power, data.guardianDone, data.legendFaster(dungeon)),
            skillHelp = WorldSkill.entries.associateWith { AbilityPerks.bonus(members.map { it.ability }, it) }.filterValues { it > 0 },
        )
    }

    fun suitability(dungeon: DungeonDef, members: List<OwnedPokemon>, mastery: Boolean = false, fasterPercent: Int = 0): Suitability =
        Suitability(floorMillis(dungeon, power(members, dungeon), mastery, fasterPercent), members.count { p -> p.form.types.any { it in dungeon.favored } }, members.size)

    private fun climb(data: WorldData, teamId: Int, trip: Expedition, dungeon: DungeonDef, index: Int, at: Long, members: List<OwnedPokemon>, power: Double, perk: Map<WorldSkill, Int>, events: MutableList<WorldEvent>): WorldData {
        val teamSize = members.size
        val firstEvent = events.size
        val floor = index % dungeon.floors + 1
        val random = Random(seed(teamId, trip, index))
        var d = data
        val brewed = at < d.brewUntil
        val outcome = Outcomes.of(power, dungeon, floor)
        if (outcome == Outcomes.Outcome.RETREAT) { events += WorldEvent.Retreat(dungeon.id, floor); return data }
        val partial = outcome == Outcomes.Outcome.SETBACK
        events += if (partial) WorldEvent.Setback(dungeon.id, floor) else WorldEvent.FloorCleared(dungeon.id, floor)
        val gained = (XP_BASE + floor) * (10 + d.facilityLevel(Facility.LAB) + if (brewed) 3 else 0) / 10
        d = d.copy(xp = d.xp + if (partial) gained / 2 else gained, deepest = if (partial) d.deepest else d.deepest + (dungeon.id to maxOf(floor, d.deepest[dungeon.id] ?: 0)))
        val modifier = DungeonModifiers.at(dungeon.id, at)
        var berries = 0
        if (random.nextInt(100) < BERRY_PERCENT + (d.skillLevel(WorldSkill.FORAGING) + perk.getValue(WorldSkill.FORAGING)) + if (modifier == DungeonModifier.BERRY_SEASON) DungeonModifiers.BERRY_POINTS else 0) {
            berries = 1
            val flavor = listOf("sweet", "dry", "sour", "spicy", "bitter").random(random)
            val berry = Items.berry(flavor)
            events += WorldEvent.Item(berry, 1)
            d = d.copy(items = d.items + (berry to (d.items[berry] ?: 0) + 1))
        }
        if (!partial && dungeon.layer == WorldLayer.PALDEA) {
            val coinDice = Random(seed(teamId, trip, index) xor 0x6A09E667L)
            if (coinDice.nextInt(100) < COIN_PERCENT) {
                val n = 1 + coinDice.nextInt(COIN_MAX)
                events += WorldEvent.Item(Items.GIMMIGHOUL_COIN, n)
                d = d.copy(items = d.items + (Items.GIMMIGHOUL_COIN to (d.items[Items.GIMMIGHOUL_COIN] ?: 0) + n))
            }
        }
        val poke = (5 + floor * 2 + random.nextInt(5)) * (100 + 2 * (d.skillLevel(WorldSkill.EXPLORATION) + perk.getValue(WorldSkill.EXPLORATION)) + if (modifier == DungeonModifier.BOUNTY) DungeonModifiers.POKE_PERCENT else 0) / 100
        val paid = if (partial) poke / 2 else poke
        events += WorldEvent.Poke(paid)
        d = d.copy(pokeEarned = d.pokeEarned + paid)
        if (!partial && random.nextInt(100) < TREASURE_PERCENT + d.facilityLevel(Facility.CAMP) + (if (brewed) 5 else 0) + (if (modifier == DungeonModifier.LUCKY) DungeonModifiers.TREASURE_POINTS else 0) + d.legendTreasurePercent(dungeon)) {
            val tier = if (floor >= dungeon.floors - 1) 3 else if (floor >= dungeon.floors / 2) 2 else 1
            events += WorldEvent.Treasure(tier)
            d = d.copy(treasure = d.treasure + (tier to (d.treasure[tier] ?: 0) + 1))
        }
        if (!partial) findFor(dungeon, floor, random, 10 * FIND_PERCENT + 3 * (d.skillLevel(WorldSkill.SCOUTING) + perk.getValue(WorldSkill.SCOUTING)) + (if (brewed) 10 else 0) + (if (modifier == DungeonModifier.CURIOUS) DungeonModifiers.FIND_PERMILLE else 0) + d.legendFindPermille(dungeon))?.let { species ->
            events += WorldEvent.Found(species, dungeon.id, floor)
            d = d.copy(finds = d.finds + Find(species, dungeon.id, floor, at), discovered = d.discovered + species).withSkillXp(WorldSkill.SCOUTING, 20)
        }
        d = Choices.expire(d, at)
        if (!partial) Choices.kindAt(teamId, trip, floor, index)?.let { ck ->
            val pending = PendingChoice(Choices.idFor(teamId, trip, index, dungeon.id), ck.name, dungeon.id, floor, at, at + Choices.WAIT_MILLIS)
            d = if (d.choices.size >= Choices.MAX_PENDING) Choices.answer(d.copy(choices = d.choices + pending), pending, 0, at) else d.copy(choices = d.choices + pending)
            events += WorldEvent.ChoiceOffered(ck, dungeon.id, floor)
        }
        if (!partial) FloorEvents.kindAt(teamId, trip, floor, index)?.let { kind ->
            val user = FloorEvents.opener(kind, members)
            var gave = ""
            if (user != null) {
                val (next, text) = grantEventReward(d, kind.reward, dungeon, floor, Random(eventSeed(teamId, trip, index) + 1), events)
                d = next; gave = text
            }
            events += WorldEvent.FloorEvent(kind, dungeon.id, floor, user?.nickname, gave)
        }
        if (!partial) dungeon.milestones[floor]?.let { reward ->
            val key = "${dungeon.id}:$floor"
            if (key !in d.milestonesPaid) {
                events += WorldEvent.Milestone(dungeon.id, floor, reward)
                d = d.copy(
                    milestonesPaid = d.milestonesPaid + key, pokeEarned = d.pokeEarned + reward.poke,
                    items = reward.itemId?.let { d.items + (it to (d.items[it] ?: 0) + reward.count) } ?: d.items,
                )
            }
        }
        d = d.withSkillXp(WorldSkill.EXPLORATION, 1).withSkillXp(WorldSkill.SCOUTING, 1).withSkillXp(WorldSkill.FORAGING, berries)
        d = Missions.advance(d, dungeon.id, if (partial) 0 else floor, berries, at, teamSize, power, events, opened = events.drop(firstEvent).count { it is WorldEvent.FloorEvent && it.userName != null })
        d = EvolutionTrialSites.advance(d, dungeon.id, if (partial) 0 else floor, members, events)
        if (!partial && floor == dungeon.floors && dungeon.guardian && !d.layerDone(dungeon.layer)) {
            if (dungeon.layer == WorldLayer.EXPLORERS) {
                events += WorldEvent.GuardianFaced
                d = d.copy(guardianDone = true)
            } else {
                events += WorldEvent.LayerGuardianFaced(dungeon.layer)
                d = d.copy(layersDone = d.layersDone + dungeon.layer.id)
            }
        }
        if (!partial && floor == dungeon.floors) {
            events += WorldEvent.Cleared(dungeon.id)
            d = d.copy(clears = d.clears + (dungeon.id to (d.clears[dungeon.id] ?: 0) + 1))
        }
        return d
    }

    private fun findFor(dungeon: DungeonDef, floor: Int, random: Random, permille: Int): Int? {
        if (random.nextInt(1000) >= permille) return null
        val pool = dungeon.wild.filter { floor in it.fromFloor..it.toFloor && Dex.speciesOrNull(it.speciesId)?.let { s -> !s.isSpecial } == true && HatchRules.rarity(it.speciesId) != null }
        val total = pool.sumOf { it.weight }
        if (total == 0) return null
        var roll = random.nextInt(total)
        for (entry in pool) { roll -= entry.weight; if (roll < 0) return entry.speciesId }
        return null
    }

    internal fun eventSeed(teamId: Int, trip: Expedition, index: Int): Long = seed(teamId, trip, index) xor 0x5F3759DFL

    private fun seed(teamId: Int, trip: Expedition, index: Int): Long = (trip.dungeonId.hashCode().toLong() * 31 + teamId) * 1_000_003L + trip.startedAt * 7 + index
}
