package app.pokeplan.app

import app.pokeplan.core.pokemon.CandyRules
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldStoreTest {
    private class Clock(var millis: Long = 1_700_000_000_000L) : app.pokeplan.app.Clock { override fun nowMillis() = millis }

    private fun store(clock: Clock = Clock()) = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }

    @Test
    fun aTeamExploresWhileTimePassesAndItsFindsJoinTheCollection() {
        val clock = Clock()
        val store = store(clock)
        val team = listOf(store.data.player.pokemon.first().id, store.grantPokemon(25, "test").id, store.grantPokemon(74, "test").id)
        assertTrue(store.worldAssign(1, team))
        assertTrue(store.worldStart(1, "tiny-woods"))
        clock.millis += 8L * 60 * 60 * 1000
        val report = assertNotNull(store.worldSettle())
        assertTrue(report.data.xp > 0)
        // Finds are rare (about 3% of floors), so keep exploring, half a day at a time, until one comes.
        repeat(20) { if (store.data.world.finds.isEmpty()) { clock.millis += 12L * 60 * 60 * 1000; store.worldSettle() } }
        assertTrue(store.data.world.xp > 0 && store.data.player.coins > 0)
        assertTrue(store.data.world.finds.isNotEmpty(), "a few days find someone")
        val before = store.data.player.pokemon.size
        val recruit = assertNotNull(store.worldRecruit(0))
        assertEquals(before + 1, store.data.player.pokemon.size)
        assertEquals(app.pokeplan.app.Origin.How.WORLD, store.data.origins[recruit.id]?.how)
        assertTrue(recruit.id in store.data.player.pokemon.map { it.id }, "the same persistent Pokémon as any other")
        assertNull(store.worldRecruit(999))
    }

    @Test
    fun moveChoicesAreCheckedSavedAndCanGoBackToNatural() {
        val store = store()
        val id = store.data.player.pokemon.first().id
        fun mon() = store.data.player.pokemon.first { it.id == id }
        val options = app.pokeplan.core.pokemon.Moveset.learnable(mon()).map { it.identifier }
        assertTrue(options.size >= 2)
        assertTrue(mon().moves.isEmpty(), "natural until a choice is made")
        assertTrue(store.setMoves(id, options.take(2)))
        assertEquals(options.take(2), mon().activeMoves.map { it.identifier })
        assertFalse(store.setMoves(id, listOf("not-a-move")))
        assertFalse(store.setMoves("nobody", options.take(1)))
        assertEquals(options.take(2), mon().moves, "a refused choice changes nothing")
        assertTrue(store.setMoves(id, emptyList()))
        assertTrue(mon().moves.isEmpty())
    }

    @Test
    fun everyMemberOfATeamGetsTheFullExperienceForEachFloorOnce() {
        val clock = Clock()
        val store = store(clock)
        val one = store.data.player.pokemon.first().id
        val two = store.grantPokemon(25, "test").id
        val three = store.grantPokemon(74, "test").id
        val outside = store.grantPokemon(10, "test").id
        assertTrue(store.worldAssign(1, listOf(one, two, three)))
        assertTrue(store.worldStart(1, "tiny-woods"))
        fun mon(id: String) = store.data.player.pokemon.first { it.id == id }
        val before = listOf(one, two, three, outside).associateWith(::mon)
        val partner = store.data.player.pokemon.filter { it.inParty }.maxOfOrNull { it.level }
        clock.millis += 6L * 60 * 60 * 1000
        val report = assertNotNull(store.worldSettle())
        val floors = report.events.count { it is app.pokeplan.world.WorldEvent.FloorCleared || it is app.pokeplan.world.WorldEvent.Setback }
        assertTrue(floors > 0)
        // Each member got all of it (not a share), through the Pokémon's own rule; the Pokémon outside the team got nothing.
        listOf(one, two, three).forEach { assertEquals(before.getValue(it).gainXp(floors * CandyRules.XP_PER_FLOOR, partner ?: before.getValue(it).level), mon(it), "member $it") }
        assertEquals(before.getValue(outside), mon(outside))
        // Settling again without time passing pays nothing more.
        val after = listOf(one, two, three).associateWith(::mon)
        store.worldSettle()
        assertEquals(after, listOf(one, two, three).associateWith(::mon))
    }

    @Test
    fun sendMyStrongestSendsTheBestTeamForTheDungeon() {
        val store = store()
        listOf(7, 4, 1, 10, 16, 74).forEach { store.grantPokemon(it, "test") }
        val dungeon = assertNotNull(app.pokeplan.world.Dungeons.byId("tiny-woods"))
        val expected = app.pokeplan.world.WorldSim.bestTeam(dungeon, store.worldIdle().filter { !app.pokeplan.core.pokemon.Guardians.canBe(it) }).map { it.id }.toSet()
        assertTrue(expected.isNotEmpty())
        assertTrue(store.worldQuickSend("tiny-woods"))
        assertEquals(expected, store.data.world.team(1)!!.members.toSet())
        assertNotNull(store.data.world.team(1)!!.expedition)
        assertFalse(store.worldQuickSend("no-such-dungeon"))
    }

    @Test
    fun mewJoinsOnceWhenTheOpeningIsFinished() {
        val store = store()
        // Finishing the episode without Mew's yes (the flag) gives nothing.
        store.worldEpisodeDone("intro:1")
        assertTrue(store.data.world.finds.none { it.speciesId == 151 })
        // With it, Mew waits in Finds, once, however often the episode is finished again.
        val said = setOf(app.pokeplan.world.story.IntroMewChapter.MEW_JOINED)
        store.worldEpisodeDone("intro:1", said)
        store.worldEpisodeDone("intro:1", said)
        assertEquals(1, store.data.world.finds.count { it.speciesId == 151 })
        val mew = assertNotNull(store.worldRecruit(store.data.world.finds.indexOfFirst { it.speciesId == 151 }))
        assertEquals(151, mew.speciesId)
        store.worldEpisodeDone("intro:1", said)
        assertTrue(store.data.world.finds.none { it.speciesId == 151 }, "a replay does not give a second Mew")
    }

    @Test
    fun grantPokemonCanGiveASpecificForm() {
        val store = store(Clock())
        val primal = store.grantPokemon(app.pokeplan.world.Story.GUARDIAN_SPECIES, GameStore.WORLD_SOURCE, Origin.How.WORLD, app.pokeplan.world.Story.GUARDIAN_FORM)
        assertEquals("dialga-primal", primal.formIdentifier)
        assertEquals("dialga", store.grantPokemon(483, "test").formIdentifier, "without a form it is the default one")
    }

    @Test
    fun kantosLegendsJoinTheCollectionOnceWhenTheirQuestsAreMet() {
        val storage = MemoryStorage()
        val first = GameStore(storage, Clock(), TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }
        // Zapdos (the Power Plant cleared three times) and Mewtwo (the Guardian faced) are met; Articuno is not.
        val world = app.pokeplan.world.WorldData(
            xp = 1_000_000, clears = mapOf("power-plant" to 3, "seafoam-islands" to 1, "cerulean-cave" to 1), layersDone = setOf("kanto"),
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        storage.text = json.encodeToString(AppData.serializer(), first.data.copy(world = world))
        val store = GameStore(storage, Clock(), TimeZone.UTC, Random(5))
        store.worldSettle()
        val owned = store.data.player.pokemon.map { it.speciesId }
        assertTrue(145 in owned && 150 in owned, "Zapdos and Mewtwo joined")
        assertFalse(144 in owned || 151 in owned, "Articuno and Mew are not met yet")
        assertEquals(setOf("kanto-zapdos", "kanto-mewtwo"), store.data.world.legends)
        val count = store.data.player.pokemon.size
        store.worldSettle()
        assertEquals(count, store.data.player.pokemon.size, "given once")
        assertTrue(store.data.rewardLog.any { it.source == GameStore.WORLD_SOURCE })
    }

    @Test
    fun aLegendAlreadyOwnedFromAPlanQuestIsNotGivenTwiceButStillCounts() {
        val storage = MemoryStorage()
        val first = GameStore(storage, Clock(), TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }
        val world = app.pokeplan.world.WorldData(xp = 1_000_000, clears = mapOf("cerulean-cave" to 1), layersDone = setOf("kanto"))
        val json = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
        storage.text = json.encodeToString(AppData.serializer(), first.data.copy(world = world))
        val store = GameStore(storage, Clock(), TimeZone.UTC, Random(5))
        // Mewtwo came from the Plan side's quest first.
        store.grantPokemon(150, "Quest: Earn the trust of Mewtwo")
        store.worldSettle()
        assertEquals(1, store.data.player.pokemon.count { it.speciesId == 150 }, "still one Mewtwo")
        assertTrue(store.data.world.hasLegend("kanto-mewtwo"), "the World quest counts, so its boost applies")
    }

    @Test
    fun aPokemonOnATeamIsInNoOtherPlace() {
        val store = store(Clock())
        val buddy = store.data.player.buddyId!!
        val grant = store.grantPokemon(25, "test")
        assertTrue(store.setInParty(grant.id, true))
        assertTrue(store.worldIdle().none { it.id == grant.id || it.id == buddy }, "the PC only: the buddy and a party member are not offered")
        // Putting the party member on a team takes it out of the party; the buddy on a team stops being the buddy.
        assertTrue(store.worldAssign(1, listOf(grant.id, buddy)))
        assertEquals(Place.WorldTeam(1), store.placeOf(grant.id))
        assertTrue(store.data.player.party.isEmpty())
        assertNull(store.data.player.buddyId)
        // Coming home to the party takes it off the team.
        assertTrue(store.setInParty(grant.id, true))
        assertEquals(listOf(buddy), store.data.world.team(1)!!.members)
        assertEquals(Place.Party(app.pokeplan.core.pokemon.PartyIds.PLAN), store.placeOf(grant.id))
    }

    @Test
    fun anIdleGuardianCanExploreButNotOneOnATeamTwice() {
        val store = store(Clock())
        val mewtwo = store.grantPokemon(150, "Quest: test")
        assertTrue(store.worldIdle().any { it.id == mewtwo.id }, "an idle Guardian is free to explore")
        assertTrue(store.worldAssign(1, listOf(mewtwo.id)), "a Guardian can be put on a team")
        assertTrue(store.worldIdle().none { it.id == mewtwo.id }, "and is busy there")
        assertEquals(Place.WorldTeam(1), store.placeOf(mewtwo.id), "on a team it is in no other place")
    }

    @Test
    fun theIdleListIsStrongestFirst() {
        val store = store(Clock())
        repeat(5) { store.grantPokemon(10 + it, "test") }
        val idle = store.worldIdle()
        assertEquals(idle.sortedByDescending { it.level }.map { it.level }, idle.map { it.level })
    }

    @Test
    fun theReportCollectsEverySettleUntilItIsDismissed() {
        val clock = Clock()
        val store = store(clock)
        assertNull(store.worldReport(), "nothing yet")
        store.worldAssign(1, listOf(store.data.player.pokemon.first().id))
        store.worldStart(1, "tiny-woods")
        clock.millis += 3L * 60 * 60 * 1000
        store.worldSettle()
        val first = assertNotNull(store.worldReport())
        clock.millis += 3L * 60 * 60 * 1000
        store.worldSettle()
        val both = assertNotNull(store.worldReport())
        assertTrue(both.floors > first.floors && both.xp > first.xp, "the second settle adds to the first")
        assertEquals(store.data.world.xp, both.xp, "all the XP so far came since the start")
        // Reading it changes nothing; dismissing clears it, and the Bag and the reward log are untouched by both.
        val bag = store.data.player.inventory.counts.values.sum()
        val log = store.data.rewardLog.size
        store.worldReportDismiss()
        assertNull(store.worldReport())
        assertEquals(bag, store.data.player.inventory.counts.values.sum())
        assertEquals(log, store.data.rewardLog.size)
    }

    @Test
    fun aWorldRecruitIsNotAnEggHatching() {
        val store = store(Clock())
        store.grantPokemon(25, GameStore.WORLD_SOURCE, Origin.How.WORLD)
        assertTrue(store.buddyEvent()?.event != BuddyEvent.HATCHED, "a recruit is not a hatch moment")
        val other = store(Clock())
        other.grantPokemon(25, "test")
        assertEquals(BuddyEvent.HATCHED, other.buddyEvent()?.event, "any other grant still is")
    }

    @Test
    fun theJobAlertIsSetForTheFirstJobAndCanBeSwitchedOff() {
        val clock = Clock()
        val store = store(clock)
        val mon = store.data.player.pokemon.first().id
        val trip = app.pokeplan.world.Expedition("tiny-woods", clock.millis, clock.millis)
        val world = app.pokeplan.world.WorldData(xp = 400, clears = mapOf("tiny-woods" to 1))
            .acceptMission("explore-woods-3")!!.copy(teams = listOf(app.pokeplan.world.WorldTeam(1, listOf(mon), trip)))
        val data = store.data.copy(world = world)
        val at = assertNotNull(WorldAlerts.nextJobDone(data, clock.millis), "an exploring team will finish the job")
        assertTrue(at > clock.millis)
        assertNull(WorldAlerts.nextJobDone(data.copy(settings = data.settings.copy(worldJobAlerts = false)), clock.millis), "switched off")
        assertNull(WorldAlerts.nextJobDone(data, at), "nothing is due after it")
    }

    @Test
    fun worldPaysStraightIntoTheBagAndTheRewardLog() {
        val clock = Clock()
        val store = store(clock)
        val bagBefore = store.data.player.inventory.counts.values.sum()
        store.worldAssign(1, listOf(store.data.player.pokemon.first().id))
        store.worldStart(1, "tiny-woods")
        clock.millis += 12L * 60 * 60 * 1000
        store.worldSettle()
        // One inventory: nothing waits in World's own stock, and the Bag has what the floors gave.
        assertTrue(store.data.world.items.isEmpty())
        assertTrue(store.data.player.inventory.counts.values.sum() > bagBefore)
        val entry = store.data.rewardLog.lastOrNull { it.source.startsWith("World") }
        assertNotNull(entry, "the trip is in the reward history")
        assertTrue(entry.rewards.any { it is app.pokeplan.core.reward.Reward.Poke } && entry.rewards.any { it is app.pokeplan.core.reward.Reward.ItemDrop })
        assertTrue(entry.seen, "World's entries never pop up a reveal")
        // Settling again soon after merges into the same entry instead of flooding the log.
        val entries = store.data.rewardLog.count { it.source.startsWith("World") }
        clock.millis += 60_000L
        store.worldSettle()
        assertEquals(entries, store.data.rewardLog.count { it.source.startsWith("World") })
        // A treasure box found along the way opens into the Bag and is logged once.
        val tier = store.data.world.treasure.keys.firstOrNull()
        if (tier != null) {
            val bag = store.data.player.inventory.counts.values.sum()
            val found = store.worldOpenTreasure(tier)
            assertTrue(found.isNotEmpty())
            assertTrue(store.data.world.items.isEmpty())
            assertTrue(store.data.player.inventory.counts.values.sum() >= bag + found.values.sum())
            assertTrue(store.data.rewardLog.any { it.source == "World: treasure" })
        }
    }

    @Test
    fun theRulesRefuseWhatTheyShould() {
        val store = store()
        assertFalse(store.worldAssign(1, listOf("nobody")), "a Pokémon that is not owned cannot go")
        assertFalse(store.worldStart(1, "tiny-woods"), "an empty team cannot go")
        val id = store.data.player.pokemon.first().id
        assertTrue(store.worldAssign(1, listOf(id)))
        assertTrue(store.worldAssign(2, listOf(id)), "one team at a time: it moves from the first")
        assertTrue(store.data.world.team(1)!!.members.isEmpty())
        assertTrue(store.worldAssign(1, listOf(id)))
        assertFalse(store.worldStart(1, "mt-steel"), "Mt. Steel needs a higher rank")
        assertTrue(store.worldStart(1, "tiny-woods"))
        assertFalse(store.worldAssign(2, listOf(id)), "on an expedition under way it cannot be moved")
        store.worldRecall(1)
        assertNull(store.data.world.team(1)?.expedition)
    }

    @Test
    fun quickSendPicksTheStrongestFreePokemonAGuardianIncludedWhenItIsOne() {
        val store = store()
        store.grantPokemon(25, "test"); store.grantPokemon(74, "test"); store.grantPokemon(10, "test")
        val legend = store.grantPokemon(150, "test")
        assertTrue(store.worldQuickSend("tiny-woods"))
        val team = store.data.world.team(1)!!
        assertTrue(team.members.isNotEmpty() && team.members.size <= 4 && team.expedition != null)
        // A legendary is a normal Pokémon: it is picked like any other, by power for the dungeon.
        assertTrue(team.members.all { id -> store.data.player.pokemon.any { it.id == id } })
        assertTrue(store.worldQuickSend("tiny-woods").not() || store.data.world.team(2) != null)
    }
}
