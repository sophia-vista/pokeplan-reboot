package app.pokeplan.app.gear

import app.pokeplan.app.quest.Quest
import app.pokeplan.app.quest.Quests
import app.pokeplan.app.quest.Requirement
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.Egg
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.CandyRules
import app.pokeplan.core.pokemon.EvolutionContext
import app.pokeplan.core.pokemon.EvolutionOption
import app.pokeplan.core.pokemon.EvolutionRules
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Guardians
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.ItemCategory
import app.pokeplan.core.pokemon.ItemEffects
import app.pokeplan.core.pokemon.ItemUse
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.GearTool
import app.pokeplan.world.Missions
import app.pokeplan.world.WorldReport
import app.pokeplan.world.ReportSummary
import app.pokeplan.world.WorldSim
import app.pokeplan.core.pokemon.Moveset
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.PerkBonuses
import app.pokeplan.core.pokemon.PerkSource
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.pokemon.Transfer
import app.pokeplan.core.pokemon.TransferBlock
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.reward.RewardRules
import app.pokeplan.core.reward.TownFinds
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.Shop
import app.pokeplan.core.reward.AchievementEvent
import app.pokeplan.core.reward.BefriendEvent
import app.pokeplan.core.reward.VisitorInheritance
import app.pokeplan.core.reward.CareEvent
import app.pokeplan.core.reward.CareKind
import app.pokeplan.core.reward.HealEvent
import app.pokeplan.core.reward.HealKind
import app.pokeplan.core.reward.BaseEvent
import app.pokeplan.core.reward.BaseKind
import app.pokeplan.core.reward.LinkEvent
import app.pokeplan.core.reward.LinkMoment
import app.pokeplan.core.reward.MintEvent
import app.pokeplan.core.reward.MintKind
import app.pokeplan.heal.HealRules
import app.pokeplan.core.reward.EffortEvent
import app.pokeplan.core.reward.Feature
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.core.reward.Reward
import app.pokeplan.core.reward.RewardEngine
import app.pokeplan.plan.LinkKind
import app.pokeplan.plan.TaskLink
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusEvent
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Goal
import app.pokeplan.plan.Insights
import app.pokeplan.plan.GoalRules
import app.pokeplan.plan.Habit
import app.pokeplan.plan.EventRepeat
import app.pokeplan.plan.PlanCalendar
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.Milestone
import app.pokeplan.plan.MiniQuest
import app.pokeplan.plan.MiniQuests
import app.pokeplan.plan.PickForMe
import app.pokeplan.plan.MilestoneResult
import app.pokeplan.plan.Reviews
import app.pokeplan.plan.FrequencyProgress
import app.pokeplan.plan.FrequencyTarget
import app.pokeplan.plan.Frequency
import app.pokeplan.plan.HabitTarget
import app.pokeplan.plan.Phase
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.DueReminder
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.RecurringTasks
import app.pokeplan.plan.Streaks
import app.pokeplan.plan.Subtask
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.TaskRules
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.TaskViews
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.random.Random
import app.pokeplan.app.gear.GearTimers
import app.pokeplan.app.plan.LinkOwner
import app.pokeplan.app.plan.LinkState
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.app.storage.SavedDocument
import app.pokeplan.app.storage.Storage
import app.pokeplan.app.storage.newId
import app.pokeplan.app.GameStore
import app.pokeplan.app.GearData
import app.pokeplan.app.GearHelper
import app.pokeplan.app.GearRates
import app.pokeplan.app.GearTimer
import app.pokeplan.app.Places

/** Pokégear's actions on the shared save: helper Pokémon, recents, favourites, timers and the stopwatch. Split out of [GameStore]. */
// ----- Pokégear: tools and their helper Pokémon (cosmetic: nothing here pays, records effort or reaches the server) -----

/** The owned Pokémon who can help [tool], the friendliest first (then the higher level, then by id so it is stable). */
fun GameStore.gearCandidates(tool: GearTool): List<OwnedPokemon> =
    data.player.pokemon.filter { tool.helps(it.speciesId) && Places.movable(data, it.id) }.sortedWith(compareByDescending<OwnedPokemon> { it.friendship }.thenByDescending { it.level }.thenBy { it.id })

/** Who helps [tool] now: the one the player chose (while they still own them), else no one. */
fun GameStore.gearHelper(tool: GearTool): GearHelper {
    // Only a helper the player chose counts: picking one from the collection by itself would take it out of the PC (one place only).
    val owned = data.gearHelpers[tool.name]?.let { id -> data.player.pokemon.firstOrNull { it.id == id && tool.helps(it.speciesId) } }
    return when {
        owned != null -> GearHelper.Owned(owned)
        else -> GearHelper.None
    }
}

/**
 * Makes [pokemonId] the helper of [tool]; false if they are not owned, cannot help it or are on an expedition. A Pokémon helps one tool
 * and is in no other place, so it is taken from its party, the buddy, a team or another tool.
 */
fun GameStore.assignGearHelper(tool: GearTool, pokemonId: String): Boolean {
    val mon = data.player.pokemon.firstOrNull { it.id == pokemonId } ?: return false
    if (!tool.helps(mon.speciesId)) return false
    return moveTo(pokemonId) { d -> d.copy(gearHelpers = d.gearHelpers - tool.name + (tool.name to pokemonId)) }
}

/** Forgets the chosen helper: it goes back to the PC, and the tool shows its own icon. */
fun GameStore.clearGearHelper(tool: GearTool) = update { it.copy(gearHelpers = it.gearHelpers - tool.name) }

fun GameStore.setPickerOptions(options: List<String>) = update { it.copy(pickerOptions = options.map { o -> o.trim() }.filter { o -> o.isNotEmpty() }.distinct().take(GameStore.MAX_PICKER_OPTIONS)) }

fun GameStore.saveGearRates(rates: GearRates) = update { it.copy(gearRates = rates) }

/** Changes what the Pokégear tools remember. Device-only; changes nothing else and pays nothing. */
fun GameStore.changeGear(change: (GearData) -> GearData) = update { it.copy(gear = change(it.gear)) }

// --- Pokégear timers and stopwatch: utilities that pay nothing ---------------------------------

fun GameStore.timerAdd(label: String, durationMs: Long): GearTimer? {
    val id = newId()
    val next = GearTimers.add(data.gear, id, label, durationMs) ?: return null
    update { it.copy(gear = next) }
    return next.timers.first { it.id == id }
}

fun GameStore.timerStart(id: String) = changeGear { GearTimers.start(it, id, nowMillis) }
fun GameStore.timerPause(id: String) = changeGear { GearTimers.pause(it, id, nowMillis) }
fun GameStore.timerAddTime(id: String, ms: Long) = changeGear { GearTimers.addTime(it, id, ms, nowMillis) }
fun GameStore.timerReset(id: String) = changeGear { GearTimers.reset(it, id) }
fun GameStore.timerEdit(id: String, label: String, durationMs: Long) = changeGear { GearTimers.edit(it, id, label, durationMs) }
fun GameStore.timerRemove(id: String) = changeGear { GearTimers.remove(it, id) }
fun GameStore.timersDismissFinished() = changeGear { GearTimers.dismissFinished(it) }

/** Marks the timers that ran out as finished and returns them (the screen and the notification call this; calling it again returns none). */
fun GameStore.timersTick(): List<GearTimer> {
    val (next, ran) = GearTimers.tick(data.gear, nowMillis)
    if (ran.isNotEmpty()) update { it.copy(gear = next) }
    return ran
}

fun GameStore.stopwatchStart() = changeGear { GearTimers.stopwatchStart(it, nowMillis) }
fun GameStore.stopwatchPause() = changeGear { GearTimers.stopwatchPause(it, nowMillis) }
fun GameStore.stopwatchLap() = changeGear { GearTimers.stopwatchLap(it, nowMillis) }
fun GameStore.stopwatchReset() = changeGear { GearTimers.stopwatchReset(it) }

/** Notes that [tool] was opened: it goes to the front of the recents (a short list), nothing more. */
fun GameStore.gearUsed(tool: GearTool) = changeGear { g -> g.copy(recents = (listOf(tool.name) + g.recents.filter { it != tool.name }).take(GearData.MAX_RECENTS)) }

/** Stars or unstars a tool. */
fun GameStore.gearFavorite(tool: GearTool, on: Boolean) = changeGear { g -> g.copy(favorites = if (on) (g.favorites.filter { it != tool.name } + tool.name) else g.favorites - tool.name) }
