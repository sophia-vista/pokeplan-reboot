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
        SceneEvent.End -> copy(finished = true, line = null)
    }

    companion object {
        fun start(scene: StoryScene) = SceneState(background = scene.background)

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
        fun finalOf(scene: StoryScene): SceneState = scene.events.fold(start(scene)) { s, e -> s.apply(e) }.copy(line = null, black = false)
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
    for ((i, e) in events.withIndex()) {
        fun known(who: String) { if (who !in ids) add("$id #$i: $who is not in the cast") }
        fun onStage(who: String) { known(who); if (who !in shown) add("$id #$i: $who is used before being shown") }
        when (e) {
            is SceneEvent.Show -> { known(e.who); shown += e.who }
            is SceneEvent.Hide -> { onStage(e.who); shown -= e.who }
            is SceneEvent.Express -> onStage(e.who)
            is SceneEvent.Face -> onStage(e.who)
            is SceneEvent.Move -> { onStage(e.who); if (e.path.isEmpty()) add("$id #$i: a walk has no path") }
            is SceneEvent.Say -> {
                e.who?.let { known(it) }
                if (e.text.isBlank()) add("$id #$i: an empty line")
                if (e.text.length > 160) add("$id #$i: a line over 160 characters (keep lines short)")
                if (type != SceneType.STAGE && type != SceneType.DUNGEON && e.who != null && e.who !in shown) add("$id #$i: ${e.who} speaks while not shown")
            }
            is SceneEvent.Emote -> onStage(e.who)
            else -> {}
        }
    }
}
