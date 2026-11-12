package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.math.min
import kotlin.random.Random

/** One thing that happened on an expedition, for the report the player reads when they come back. */
sealed interface WorldEvent {
    data class FloorCleared(val dungeonId: String, val floor: Int) : WorldEvent
    data class Found(val speciesId: Int, val dungeonId: String, val floor: Int) : WorldEvent
    data class Item(val itemId: String, val count: Int) : WorldEvent
    data class Poke(val amount: Int) : WorldEvent
    data class Treasure(val tier: Int) : WorldEvent
    data class Milestone(val dungeonId: String, val floor: Int, val reward: MilestoneReward) : WorldEvent
    data class Cleared(val dungeonId: String) : WorldEvent
    /** A floor the team was too weak for (see [Outcomes]): not cleared, half the Poké and Exploration XP, nothing else. */
    data class Setback(val dungeonId: String, val floor: Int) : WorldEvent
    /** The team turned back at [floor] and came home safely: the expedition is over. */
    data class Retreat(val dungeonId: String, val floor: Int) : WorldEvent
    data class MissionDone(val missionId: String) : WorldEvent
    /** A floor event: [user] opened it (null: the team went around), and [gave] says what it gave. */
    /** A floor offered a choice, now waiting for an answer (or the default). */
    data class ChoiceOffered(val kind: ChoiceKind, val dungeonId: String, val floor: Int) : WorldEvent
    data class FloorEvent(val kind: FloorEventKind, val dungeonId: String, val floor: Int, val userName: String?, val gave: String) : WorldEvent
    /** The Guardian was faced: the story is complete. */
    data object GuardianFaced : WorldEvent
    /** The Guardian of another layer was faced: that layer's story is complete. */
    data class LayerGuardianFaced(val layer: WorldLayer) : WorldEvent
}

/** A team's fit for a dungeon (see [WorldSim.suitability]). */
data class Suitability(val floorMillis: Long, val favored: Int, val size: Int)

/**
 * How long a floor takes a team, step by step, from the same numbers [WorldSim.floorMillis] uses (so what the player reads is what happens).
 * [skillHelp] is what the members' abilities add to World's skills (more berries, finds or Poké); it does not change [floorMillis].
 */
data class SpeedExplanation(
    val levels: Int,
    val favoredMembers: Int,
    /** The extra power from members of a favoured type: 20% of their levels. */
    val favoredBonus: Double,
    /** What the members' moves, abilities and held items add ([BattleIdentity]); [levels] + [favoredBonus] + [identityBonus] is [power]. */
    val identityBonus: Double = 0.0,
    /** Each member's named parts of that, as lines ("Pikachu: Thunderbolt (electric, favoured here) +6%"). */
    val identityLines: List<String> = emptyList(),
    val power: Double,
    val baseMillis: Long,
    /** The time after power alone, never under a quarter of [baseMillis]. */
    val afterPowerMillis: Long,
    val atMinimum: Boolean,
    /** Temporal Mastery (the home Guardian) and the legendaries given for the layer, in percent faster. */
    val masteryPercent: Int,
    val legendPercent: Int,
    val floorMillis: Long,
    val skillHelp: Map<WorldSkill, Int>,
)

/** What settling did: the new data and, per team, the floors climbed and what happened. */
data class WorldReport(
    val data: WorldData,
    val perTeam: Map<Int, List<WorldEvent>>,
    /**
     * The floors each member climbed in this settle, by Pokémon id: the members of the team at settlement, one count per completed floor
     * (WORLD.md 5.2). The app gives every one of them the full per-floor experience for each. A floor is counted once: the same step that
     * counts it moves the team's cursor (`floorsClimbed` and `settledTo`), so the floor's id (team, expedition start, floor index) is never settled twice.
     */
    val memberFloors: Map<String, Int> = emptyMap(),
) {
    val events: List<WorldEvent> get() = perTeam.values.flatten()
    val isEmpty: Boolean get() = events.isEmpty()
}

/**
 * The dungeon simulation (docs/pokelife/MODULES/WORLD.md). A team climbs one floor after another on its own, and a floor
 * takes [floorMillis] given the team and the dungeon. What each floor gives is rolled from a random generator seeded by the
 * team, the expedition and the floor's number alone, so the result is the same whether the app settles every second or once
 * after a day away (offline progress is exactly as good as staying). Progress while away is capped at [MAX_AWAY_MILLIS].
 *
 * Numbers marked *(tune)* are starting values. Nothing here pays anything itself; the app takes the report and puts items
 * in the Bag, finds in the collection and so on.
 */
object WorldSim {
    /** The longest stretch of away time that counts at once. *(tune)* */
    const val MAX_AWAY_MILLIS = 12L * 60 * 60 * 1000

    /** Exploration XP for each floor: its number plus [XP_BASE]. *(tune)* */
    const val XP_BASE = 4

    /** The chance a floor shows a wild Pokémon that joins the finds, in percent. *(tune)* */
    const val FIND_PERCENT = 3

    /** The chance a floor has a berry, and the chance of a treasure box, in percent. *(tune)* */
    const val BERRY_PERCENT = 70
    const val TREASURE_PERCENT = 6

    /**
     * A team's power: the sum of its members' [contribution]s, each its level, 20% more for a type the dungeon favors, and what its moves, ability and
     * held item add ([BattleIdentity], at most 25% of the level).
     */
    fun power(members: List<OwnedPokemon>, dungeon: DungeonDef): Double =
        members.sumOf { contribution(it, dungeon) }

    /** What [p] adds to a team's power in [dungeon] (the one term of [power]); [BattleIdentity.breakdown] says what it is made of. */
    fun contribution(p: OwnedPokemon, dungeon: DungeonDef): Double = BattleIdentity.breakdown(p, dungeon).power

    /**
     * The strongest team of up to [size] from [candidates] for [dungeon]: [power] is a sum of each member's [contribution], so the best
     * team is simply the biggest contributors (a favored type of a lower level can beat a higher level of another type). Ties go to the
     * favored type, then the higher level, then the id, so the choice is the same every time.
     */
    fun bestTeam(dungeon: DungeonDef, candidates: List<OwnedPokemon>, size: Int = WorldTeam.MAX_MEMBERS): List<OwnedPokemon> =
        candidates.sortedWith(
            compareByDescending<OwnedPokemon> { contribution(it, dungeon) }
                .thenByDescending { p -> p.form.types.any { it in dungeon.favored } }
                .thenByDescending { it.level }.thenBy { it.id },
        ).take(size)

    /** How long a floor takes a team of [power], in milliseconds: faster as the power grows, never under a quarter of the base. */
    fun floorMillis(dungeon: DungeonDef, power: Double, mastery: Boolean = false, fasterPercent: Int = 0): Long {
        val base = dungeon.baseFloorSeconds * 1000L
        val factor = 1.0 + power / 100.0
        val millis = maxOf(base / 4, (base / factor).toLong())
        val withMastery = if (mastery) millis * 9 / 10 else millis
        return if (fasterPercent > 0) withMastery * (100 - fasterPercent.coerceAtMost(50)) / 100 else withMastery
    }

    /**
     * Settles every team at [now]: the floors each has climbed since it was last settled (at most [MAX_AWAY_MILLIS] of them), their
     * finds, items, Poké, treasure, XP and milestones. [roster] is the player's Pokémon, to read the members' levels and types.
     */
    fun settle(data: WorldData, roster: List<OwnedPokemon>, now: Long): WorldReport {
        var out = data
        val perTeam = mutableMapOf<Int, List<WorldEvent>>()
        val memberFloors = mutableMapOf<String, Int>()
        for (team in data.teams) {
            val trip = team.expedition ?: continue
            val dungeon = Dungeons.byId(trip.dungeonId) ?: continue
            val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
            if (members.isEmpty()) continue
            // A clock set back (or a time zone change on a phone that keeps local time) must not freeze the climb until it catches up.
            if (trip.settledTo > now) {
                out = out.withTeam((out.team(team.id) ?: team).copy(expedition = trip.copy(settledTo = now)))
                continue
            }
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
            // The rest of the time carries over (unless the cap cut it, when the clock jumps to now).
            val settled = if (now - trip.settledTo > MAX_AWAY_MILLIS) now else trip.settledTo + floors * per
            val current = out.team(team.id) ?: team
            // A team that turned back is home (WORLD.md 3.6): the expedition ends, the floors before it keep what they paid.
            out = out.withTeam(current.copy(expedition = if (retreated) null else trip.copy(settledTo = settled, floorsClimbed = trip.floorsClimbed + floors)))
            perTeam[team.id] = events
            members.forEach { memberFloors[it.id] = (memberFloors[it.id] ?: 0) + done }
        }
        // A dungeon cleared again and again in one stretch is one line ("... (3 times)"), not three.
        val lines = perTeam.flatMap { (teamId, events) -> events.mapNotNull { journalLine(out.team(teamId)?.title ?: "Team $teamId", it, now) } }
            .fold(mutableListOf<Pair<JournalLine, Int>>()) { acc, l ->
                if (acc.lastOrNull()?.first?.text == l.text) acc[acc.lastIndex] = acc.last().first to acc.last().second + 1 else acc += l to 1
                acc
            }.map { (l, n) -> if (n > 1) l.copy(text = l.text.removeSuffix(".") + " ($n times).") else l }
        out = Choices.expire(out, now)
        return WorldReport(out.copy(lastSeenAt = now, journal = (out.journal + lines).takeLast(WorldData.JOURNAL_SIZE)), perTeam, memberFloors)
    }

    /**
     * The moment the next floor of any team that is out will be done, or null when no team is exploring (WORLD.md 3.1: the screen waits for this instant
     * instead of polling). A team whose clock was set back is due at [now], so it is settled and its cursor moved at once; a team is never due before [now].
     */
    fun nextFloorAt(data: WorldData, roster: List<OwnedPokemon>, now: Long): Long? {
        var next: Long? = null
        for (team in data.teams) {
            val trip = team.expedition ?: continue
            val dungeon = Dungeons.byId(trip.dungeonId) ?: continue
            val members = team.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }
            if (members.isEmpty()) continue
            val at = if (trip.settledTo > now) now else trip.settledTo + floorMillis(dungeon, power(members, dungeon), data.guardianDone, data.legendFaster(dungeon))
            if (next == null || at < next) next = at
        }
        return next
    }

    /**
     * When each accepted, unfinished job will be done if its team keeps exploring, by job id: the moment a team in the job's
     * dungeon climbs to the job's floor (and meets its team size or power). A job no team can finish, and a [MissionKind.COLLECT]
     * job (it depends on what the floors give), has no entry. This is what the "job done" alarm is set for; the alarm settles
     * World, and only a job that really is done is announced.
     */
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
                // A team too weak to clear the job's floor never finishes it (WORLD.md 3.6: a setback floor does not move a job on, and a team that turns back is home).
                if (m.floor > 0 && Outcomes.of(power, dungeon, m.floor) != Outcomes.Outcome.CLEARED) continue
                // An investigation depends on what the floors hold and a story request on the story, so neither has a forecast.
                if (m.kind == MissionKind.INVESTIGATION || m.kind == MissionKind.STORY) continue
                if ((m.kind == MissionKind.ESCORT && members.size < m.count) || (m.kind == MissionKind.DEFEAT && power < m.count) || (m.kind == MissionKind.CHALLENGE && members.size > m.count)) continue
                // The next floor to climb is index floorsClimbed; floors rise to the top of the dungeon, then start again at 1.
                val next = trip.floorsClimbed % dungeon.floors
                val floors = maxOf(0, m.floor - 1 - next) + 1
                val at = trip.settledTo + floors * floorMillis(dungeon, power, data.guardianDone, data.legendFaster(dungeon))
                if (out[m.id]?.let { it <= at } != true) out[m.id] = at
            }
        }
        return out
    }

    /** What the player reads in the expedition log for [event], or null for the many floors that are not worth a line. */
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
            WorldEvent.GuardianFaced -> { kind = JournalLine.KIND_GUARDIAN; "The Guardian was faced. The story is complete." }
            is WorldEvent.LayerGuardianFaced -> { kind = JournalLine.KIND_GUARDIAN; "The Guardian of ${event.layer.label} was faced. Its story is complete." }
            else -> return null
        }
        return JournalLine(now, text, kind, place)
    }

    /** The speed of a team of [members] in [dungeon] for [data] explained in steps; [SpeedExplanation.floorMillis] equals what [floorMillis] gives. */
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

    /** What a team of [members] brings to [dungeon]: how long a floor takes it, and how many of its members are of a favoured type. */
    fun suitability(dungeon: DungeonDef, members: List<OwnedPokemon>, mastery: Boolean = false, fasterPercent: Int = 0): Suitability =
        Suitability(floorMillis(dungeon, power(members, dungeon), mastery, fasterPercent), members.count { p -> p.form.types.any { it in dungeon.favored } }, members.size)

    /** One floor climbed: the floor number in the dungeon is its index modulo the floors, so a cleared dungeon starts again. */
    private fun climb(data: WorldData, teamId: Int, trip: Expedition, dungeon: DungeonDef, index: Int, at: Long, members: List<OwnedPokemon>, power: Double, perk: Map<WorldSkill, Int>, events: MutableList<WorldEvent>): WorldData {
        val teamSize = members.size
        val firstEvent = events.size
        val floor = index % dungeon.floors + 1
        val random = Random(seed(teamId, trip, index))
        var d = data
        val brewed = at < d.brewUntil
        // WORLD.md 3.6: the floor is cleared in full, a setback (half the Poké and Exploration XP, no treasure, find, milestone, side path or choice), or the team turns back.
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
        // A floor choice: choices whose time is up (by this floor's own time) take their default first; then this floor may offer one.
        d = Choices.expire(d, at)
        if (!partial) Choices.kindAt(teamId, trip, floor, index)?.let { ck ->
            val pending = PendingChoice(Choices.idFor(teamId, trip, index, dungeon.id), ck.name, dungeon.id, floor, at, at + Choices.WAIT_MILLIS)
            d = if (d.choices.size >= Choices.MAX_PENDING) Choices.answer(d.copy(choices = d.choices + pending), pending, 0, at) else d.copy(choices = d.choices + pending)
            events += WorldEvent.ChoiceOffered(ck, dungeon.id, floor)
        }
        // A floor event, if the floor has one: a bonus when a team member can open it, nothing lost when none can.
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

    /** The wild Pokémon a floor shows, if it shows one: only ones that can hatch (never a legendary or mythical, which are story discoveries). */
    private fun findFor(dungeon: DungeonDef, floor: Int, random: Random, permille: Int): Int? {
        if (random.nextInt(1000) >= permille) return null
        val pool = dungeon.wild.filter { floor in it.fromFloor..it.toFloor && Dex.speciesOrNull(it.speciesId)?.let { s -> !s.isSpecial } == true && HatchRules.rarity(it.speciesId) != null }
        val total = pool.sumOf { it.weight }
        if (total == 0) return null
        var roll = random.nextInt(total)
        for (entry in pool) { roll -= entry.weight; if (roll < 0) return entry.speciesId }
        return null
    }

    /** A seed for a floor's event, apart from the floor's own so adding events never changes what a floor gave before. */
    internal fun eventSeed(teamId: Int, trip: Expedition, index: Int): Long = seed(teamId, trip, index) xor 0x5F3759DFL

    private fun seed(teamId: Int, trip: Expedition, index: Int): Long = (trip.dungeonId.hashCode().toLong() * 31 + teamId) * 1_000_003L + trip.startedAt * 7 + index
}
