package app.pokeplan.world.story

/**
 * A small writing aid for scene scripts: a scene is a block of calls ("say", "walk", "shake"), turned into the typed events the runtime plays.
 * Dialogue scenes show their first two cast members for you; every scene ends with End for you; a cinematic gets its black bars.
 */
internal class Script {
    val events = mutableListOf<SceneEvent>()

    fun show(who: String, x: Float, y: Float, facing: Facing = Facing.RIGHT) { events += SceneEvent.Show(who, x, y, facing) }
    fun hide(who: String) { events += SceneEvent.Hide(who) }
    fun say(who: String, text: String, e: Expression? = null) { events += SceneEvent.Say(who, text, e) }
    fun narr(text: String) { events += SceneEvent.Say(null, text) }
    fun walk(who: String, vararg cells: Pair<Float, Float>) { events += SceneEvent.Move(who, cells.toList()) }
    fun face(who: String, facing: Facing) { events += SceneEvent.Face(who, facing) }
    fun emote(who: String, mark: String = "!") { events += SceneEvent.Emote(who, mark) }
    fun wait(millis: Int = 600) { events += SceneEvent.Wait(millis) }
    fun fade(toBlack: Boolean, millis: Int = 500) { events += SceneEvent.Fade(toBlack, millis) }
    fun shake(millis: Int = 450) { events += SceneEvent.Shake(millis) }
    fun flash() { events += SceneEvent.Flash() }
    fun cry(who: String) { events += SceneEvent.Cry(who) }
    fun sound(name: String) { events += SceneEvent.Sound(name) }
    fun item(itemId: String, x: Float, y: Float) { events += SceneEvent.Item(itemId, x, y) }
    fun take(itemId: String) { events += SceneEvent.TakeItem(itemId) }

    /** Walks [who] in from off the left edge to [x] on row [y]. */
    fun enter(who: String, x: Float, y: Float = 3.0f, fromLeft: Boolean = true) {
        show(who, if (fromLeft) -1f else 11f, y, if (fromLeft) Facing.RIGHT else Facing.LEFT)
        walk(who, x to y)
    }

    /** Walks [who] off the right (or left) edge and takes them off the stage. */
    fun exit(who: String, y: Float = 3.0f, toRight: Boolean = true) { walk(who, (if (toRight) 11f else -1f) to y); hide(who) }

    /** Asks a question with two or three answers (each sets its flag), then plays [after] for the ones that need a reply. */
    fun choice(prompt: String?, vararg options: Pair<String, String>) { events += SceneEvent.Choice(prompt, options.map { StoryChoice(it.first, it.second) }) }

    /** Lines that play only after [flag] was chosen. */
    fun branch(flag: String, build: Script.() -> Unit) { events += SceneEvent.Branch(flag, Script().apply(build).events) }
}

internal fun buddyCharacter() = StoryCharacter(BUDDY, "{buddy}")
internal fun npc(id: String, name: String, species: Int) = StoryCharacter(id, name, species)

/** A conversation: the first two of [cast] are on stage as the script begins. */
internal fun dialogue(id: String, title: String, background: String, cast: List<StoryCharacter>, build: Script.() -> Unit): StoryScene {
    val s = Script()
    cast.take(2).forEachIndexed { i, c -> s.show(c.id, 0f, 0f, if (i == 0) Facing.RIGHT else Facing.LEFT) }
    s.build()
    s.events += SceneEvent.End
    return StoryScene(id, title, SceneType.DIALOGUE, background, cast, s.events)
}

/** A stage: the script places everyone. */
internal fun stage(id: String, title: String, background: String, cast: List<StoryCharacter>, build: Script.() -> Unit): StoryScene {
    val s = Script().apply(build)
    s.events += SceneEvent.End
    return StoryScene(id, title, SceneType.STAGE, background, cast, s.events)
}

/** A scripted walk across [dungeonId]'s own tiles. */
internal fun dungeon(id: String, title: String, dungeonId: String, cast: List<StoryCharacter>, build: Script.() -> Unit): StoryScene {
    val s = Script().apply(build)
    s.events += SceneEvent.End
    return StoryScene(id, title, SceneType.DUNGEON, dungeonId, cast, s.events, dungeonId = dungeonId)
}

/** A cinematic: black bars, a big picture. A character whose id starts with "big_" is drawn large. */
internal fun cinematic(id: String, title: String, background: String, cast: List<StoryCharacter>, dungeonId: String? = null, build: Script.() -> Unit): StoryScene {
    val s = Script()
    s.events += SceneEvent.Letterbox(true)
    s.build()
    s.events += SceneEvent.Letterbox(false)
    s.events += SceneEvent.End
    return StoryScene(id, title, SceneType.CINEMATIC, background, cast, s.events, dungeonId = dungeonId)
}
