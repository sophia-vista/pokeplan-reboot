package app.pokeplan.world.story

/** Where someone stands in a scene right now, and how they look. */
data class Actor(val id: String, val x: Float, val y: Float, val facing: Facing, val expression: Expression, val emote: String? = null)

/** The line being said: who, what and the face. */
data class Line(val who: String?, val text: String, val expression: Expression)

/**
 * The state of a scene after some of its events: who is on stage, what lies on the floor, the line on screen and the flags set. The
 * runtime that draws it animates the walks and the fades; this state is what each event leaves behind, so skipping to the end is the same
 * as applying every event (spec 7.6: skip reaches a valid final state, nothing is left half done).
 */
data class SceneState(
    val actors: Map<String, Actor> = emptyMap(),
    val items: Map<String, Pair<Float, Float>> = emptyMap(),
    val line: Line? = null,
    val background: String,
    val black: Boolean = false,
    val flags: Set<String> = emptySet(),
    val finished: Boolean = false,
    val letterbox: Boolean = false,
) {
    /** The state after [event], all at once (a walk ends at the last cell of its path). */
    fun apply(event: SceneEvent): SceneState = when (event) {
        is SceneEvent.Show -> copy(actors = actors + (event.who to Actor(event.who, event.x, event.y, event.facing, event.expression)))
        is SceneEvent.Hide -> copy(actors = actors - event.who, line = line?.takeIf { it.who != event.who })
        is SceneEvent.Express -> actors[event.who]?.let { copy(actors = actors + (event.who to it.copy(expression = event.expression))) } ?: this
        is SceneEvent.Face -> actors[event.who]?.let { copy(actors = actors + (event.who to it.copy(facing = event.facing))) } ?: this
        is SceneEvent.Move -> actors[event.who]?.let { a ->
            val last = event.path.lastOrNull() ?: return@let this
            val facing = if (event.path.size >= 1) facingOf(event.path.getOrNull(event.path.size - 2) ?: (a.x to a.y), last, a.facing) else a.facing
            copy(actors = actors + (event.who to a.copy(x = last.first, y = last.second, facing = facing)))
        } ?: this
        is SceneEvent.Say -> {
            val face = event.expression ?: event.who?.let { actors[it]?.expression } ?: Expression.NORMAL
            val updated = if (event.who != null && event.expression != null) actors[event.who]?.let { actors + (event.who to it.copy(expression = event.expression)) } ?: actors else actors
            copy(line = Line(event.who, event.text, face), actors = updated)
        }
        is SceneEvent.Wait -> this
        is SceneEvent.Fade -> copy(black = event.toBlack)
        is SceneEvent.Background -> copy(background = event.id)
        is SceneEvent.Item -> copy(items = items + (event.itemId to (event.x to event.y)))
        is SceneEvent.TakeItem -> copy(items = items - event.itemId)
        is SceneEvent.Emote -> actors[event.who]?.let { copy(actors = actors + (event.who to it.copy(emote = event.mark.takeIf { m -> m.isNotBlank() }))) } ?: this
        is SceneEvent.Flag -> copy(flags = flags + event.name)
        is SceneEvent.Letterbox -> copy(letterbox = event.on)
        is SceneEvent.Choice -> event.options.firstOrNull()?.let { copy(flags = flags + it.flag) } ?: this
        is SceneEvent.Branch -> if (event.flag in flags) event.events.fold(this) { s, e -> s.apply(e) } else this
        is SceneEvent.Shake, is SceneEvent.Flash, is SceneEvent.Cry, is SceneEvent.Sound -> this
        SceneEvent.End -> copy(finished = true, line = null)
    }

    companion object {
        fun start(scene: StoryScene, flags: Set<String> = emptySet()) = SceneState(background = scene.background, flags = flags)

        /** The way a step from [from] to [to] faces: sideways when it goes across more than it goes up or down. */
        fun facingOf(from: Pair<Float, Float>, to: Pair<Float, Float>, keep: Facing): Facing {
            val dx = to.first - from.first; val dy = to.second - from.second
            return when {
                dx == 0f && dy == 0f -> keep
                kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> if (dx > 0) Facing.RIGHT else Facing.LEFT
                else -> if (dy > 0) Facing.DOWN else Facing.UP
            }
        }

        /** The state at the end of [scene]: every event applied in order, what a skip lands on. */
        fun finalOf(scene: StoryScene, flags: Set<String> = emptySet()): SceneState = scene.events.fold(start(scene, flags)) { s, e -> s.apply(e) }.copy(line = null, black = false)
    }
}

/** What is wrong with a scene's script, in words (empty when it is sound): used by the tests and the preview tool so a writer sees mistakes at once. */
fun StoryScene.problems(): List<String> = buildList {
    val ids = cast.map { it.id }
    if (ids.distinct().size != ids.size) add("$id: a character is listed twice")
    if (events.lastOrNull() != SceneEvent.End) add("$id: it does not end with End")
    if (events.count { it == SceneEvent.End } != 1) add("$id: End must appear once, at the end")
    if (type == SceneType.DUNGEON && dungeonId == null) add("$id: a DUNGEON scene needs a dungeonId")
    val shown = mutableSetOf<String>()
    // One checker for the top level and for the lines inside a Branch, so a branch is held to the same rules.
    fun check(list: List<SceneEvent>, at: String, inBranch: Boolean) {
        for ((i, e) in list.withIndex()) {
            val where = "$id #$at$i"
            fun known(who: String) { if (who !in ids) add("$where: $who is not in the cast") }
            fun onStage(who: String) { known(who); if (who !in shown) add("$where: $who is used before being shown") }
            when (e) {
                is SceneEvent.Show -> { known(e.who); shown += e.who }
                is SceneEvent.Hide -> { onStage(e.who); shown -= e.who }
                is SceneEvent.Express -> onStage(e.who)
                is SceneEvent.Face -> onStage(e.who)
                is SceneEvent.Move -> { onStage(e.who); if (e.path.isEmpty()) add("$where: a walk has no path") }
                is SceneEvent.Say -> {
                    e.who?.let { known(it) }
                    if (e.text.isBlank()) add("$where: an empty line")
                    if (e.text.length > 160) add("$where: a line over 160 characters (keep lines short)")
                    if (type != SceneType.STAGE && type != SceneType.DUNGEON && e.who != null && e.who !in shown) add("$where: ${e.who} speaks while not shown")
                }
                is SceneEvent.Emote -> onStage(e.who)
                is SceneEvent.Cry -> known(e.who)
                is SceneEvent.Item -> if (e.itemId.isBlank()) add("$where: an item with no id")
                is SceneEvent.Choice -> {
                    if (inBranch) add("$where: a choice inside a branch")
                    if (e.options.size !in 2..3) add("$where: a choice needs two or three options")
                    if (e.options.any { it.label.length > 40 }) add("$where: a choice label over 40 characters")
                    if (e.options.any { it.flag.isBlank() }) add("$where: a choice option sets no flag")
                }
                is SceneEvent.Branch -> {
                    if (inBranch) add("$where: a branch inside a branch")
                    if (e.flag.isBlank()) add("$where: a branch with no flag")
                    // The branch may or may not run, so it must leave who is on stage as it found them.
                    val before = shown.toSet()
                    check(e.events, "$at$i.", true)
                    if (shown != before) { add("$where: a branch changes who is on stage (show and hide inside it must cancel out)"); shown.clear(); shown += before }
                    if (e.events.any { it == SceneEvent.End }) add("$where: End inside a branch")
                }
                else -> {}
            }
        }
    }
    check(events, "", false)
    if (type == SceneType.DIALOGUE) {
        // A dialogue scene draws at most two characters at once; a third would not appear.
        val on = mutableSetOf<String>()
        var most = 0
        fun count(list: List<SceneEvent>) { for (e in list) when (e) { is SceneEvent.Show -> { on += e.who; most = maxOf(most, on.size) }; is SceneEvent.Hide -> on -= e.who; is SceneEvent.Branch -> count(e.events); else -> {} } }
        count(events)
        if (most > 2) add("$id: a dialogue scene shows $most characters at once (two at most; use a STAGE scene)")
    }
}
