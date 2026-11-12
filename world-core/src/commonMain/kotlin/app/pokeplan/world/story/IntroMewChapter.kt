package app.pokeplan.world.story

import app.pokeplan.world.story.Expression.ANGRY
import app.pokeplan.world.story.Expression.DETERMINED
import app.pokeplan.world.story.Expression.HAPPY
import app.pokeplan.world.story.Expression.JOYOUS
import app.pokeplan.world.story.Expression.NORMAL
import app.pokeplan.world.story.Expression.SURPRISED
import app.pokeplan.world.story.Expression.WORRIED

/**
 * The opening of World: Mew's Unofficial Welcome (docs/world-story-rewrite.md, "Opening tutorial"). A Mythical Pokémon's prank
 * is the tutorial: a chase out of town, a short first dungeon, a job, a team, and a test at dusk, after which Mew joins. It
 * replaces the old chapter 1 (the guild letter); the scenes are `intro-mew-*`. Mew is added to Finds by [MEW_JOINED] once
 * the episode is finished (GameStore.worldEpisodeDone), so replaying it never gives a second Mew.
 *
 * What the rest of the story leans on (keep it when rewriting): what Mew takes is the pair's lunch; Pelly keeps the request board
 * and Braviary the team register; every other arc begins with a Mythical turning up and ends with the pair walking back to this board.
 */
object IntroMewChapter {
    private val buddy = buddyCharacter()
    private val mew = npc("mew", "Mew", 151)
    private val pelly = npc("pelly", "Pelly", 279)
    private val braviary = npc("braviary", "Braviary", 628)
    private val sentret = npc("sentret", "Sentret", 161)

    fun episode() = StoryEpisode(
        null, 1, "A New Explorer",
        listOf(suspiciousInvitation(), firstSteps(), firstExpedition(), jobBecomesPromise(), theTeam(), mewsRealTest(), adventureBoard()),
    )

    private fun suspiciousInvitation() = stage("intro-mew-1", "A very suspicious invitation", "city", listOf(buddy, mew)) {
        show(BUDDY, -1f, 3.2f)
        walk(BUDDY, 3.2f to 3.2f)
        narr("Midday in the settlement. {player} and {buddy} are carrying lunch to their usual wall when something small and pink darts between them.")
        show("mew", 5.2f, 2.2f, Facing.LEFT)
        emote("mew", "!")
        walk("mew", 7.2f to 2.8f, 9.4f to 2.8f)
        hide("mew")
        narr("The lunch bundle is gone. So is the pink thing, around the corner, humming.")
        say(BUDDY, "Hey! That was ours! I queued for that!", SURPRISED)
        walk(BUDDY, 6.6f to 3.2f)
        show("mew", 9.6f, 2.8f, Facing.LEFT)
        narr("At the far end of the lane stands the baker's Furret, waving. Then it ripples like a puddle and is a small pink Pokémon again.")
        say(BUDDY, "Wait. You were the one who took it?", SURPRISED)
        say("mew", "Mew!", HAPPY)
        say(BUDDY, "Give it back! There are two buns in there and one of them is {player}'s!", ANGRY)
        narr("Mew floats backwards toward the edge of town, swinging the bundle just out of reach. It keeps checking that they are following.")
        say(BUDDY, "It's a trap.", DETERMINED)
        wait(700)
        say(BUDDY, "...We're going, aren't we?", NORMAL)
        walk("mew", 11f to 2.8f); hide("mew")
        walk(BUDDY, 11f to 3.2f); hide(BUDDY)
    }

    private fun firstSteps() = stage("intro-mew-2", "First steps outside town", "meadow", listOf(buddy, mew, sentret)) {
        show(BUDDY, -1f, 3.4f)
        walk(BUDDY, 3.4f to 3.4f)
        show("mew", 6.8f, 2.6f, Facing.LEFT)
        show("sentret", 4.8f, 1.8f, Facing.DOWN)
        narr("Mew leads them down a path they have never used. In the grass lies a small satchel, and a Sentret stands guard over it on the tip of its tail.")
        say("sentret", "It isn't mine! It was here when I got here! I'm only watching it until somebody sensible comes!", WORRIED)
        say(BUDDY, "We're not sure we count. We're chasing our lunch.", NORMAL)
        face("mew", Facing.DOWN)
        narr("Mew points at the satchel. Then at a narrow gap in the trees, where the light goes green and strange.")
        say(BUDDY, "That's a Mystery Dungeon. You want us to go in there?", WORRIED)
        emote("mew", "!")
        say("sentret", "Somebody small went in this morning and didn't come out. I counted. I'm good at counting.", WORRIED)
        say(BUDDY, "Fine. We'll look. But if this is another prank, pink one, you're carrying the bag home.", DETERMINED)
        walk(BUDDY, 11f to 3.4f); hide(BUDDY)
    }

    private fun firstExpedition() = dungeon("intro-mew-3", "The first expedition", "tiny-woods", listOf(buddy, mew, sentret)) {
        show(BUDDY, -1f, 2.8f)
        walk(BUDDY, 2.5f to 2.8f, 4.4f to 2.8f)
        narr("The woods are quiet. Sunlight comes down in pieces, and every few steps something small rustles out of the way.")
        say(BUDDY, "Stay close, {player}. The paths in here are never the same twice. That's the whole trouble with them.", NORMAL)
        show("mew", 8.8f, 1.8f, Facing.LEFT)
        show("sentret", 8.2f, 3.2f, Facing.LEFT)
        walk("sentret", 6.4f to 3.2f)
        narr("Between two roots sits a second Sentret, much smaller than the first, trying very hard not to cry.")
        say("sentret", "My bag! You found my bag! I got split from my big brother and then I dropped it and then the path moved!", SURPRISED)
        say(BUDDY, "So this wasn't only about chasing us. Someone actually needed help.", WORRIED)
        face("mew", Facing.RIGHT)
        narr("Mew watches from a low branch, unusually quiet. It has stopped swinging the lunch.")
        say(BUDDY, "Your brother is standing on his tail at the edge of the woods, counting. Come on. We'll walk you out.", HAPPY)
        say("sentret", "He counts when he's scared. Don't tell him I told you.", HAPPY)
        walk(BUDDY, 11f to 2.8f); hide(BUDDY)
    }

    private fun jobBecomesPromise() = stage("intro-mew-4", "A job becomes a promise", "city", listOf(buddy, pelly, mew, sentret)) {
        show(BUDDY, 3f, 3.2f); show("pelly", 7f, 2.6f, Facing.LEFT); show("mew", 1.4f, 2.4f, Facing.RIGHT); show("sentret", 5f, 1.8f, Facing.DOWN)
        narr("Back in town, two Sentret are talking over each other. Pelly, who keeps the request board at the exploration centre, has heard all of it twice.")
        say("pelly", "So. You two went into the woods, found the little one, and brought the bag out as well?", HAPPY)
        say(BUDDY, "We were following a thief.", NORMAL)
        face("mew", Facing.UP)
        narr("Mew studies a cloud with great interest.")
        say("pelly", "That is not how I would recommend finding your first job. But somebody is going home tonight because of it.", NORMAL)
        say("sentret", "They didn't even ask for anything! I have three acorns. They can have two!", JOYOUS)
        say(BUDDY, "Keep your acorns. ...Is this what exploration teams do, then? Take requests and help Pokémon?", NORMAL)
        say("pelly", "That's the idea. Rescue, delivery, retrieval, escort, investigation. Some jobs are simple. Some take a whole team.", HAPPY)
        narr("Pelly tips her beak toward the request board by the door. It is crowded with notes in every kind of pawwriting.")
        say("pelly", "You choose what to take. Read the objective and the reward before you set out. And eat first. I can hear your stomach from here.", NORMAL)
    }

    private fun theTeam() = stage("intro-mew-5", "The team", "city", listOf(buddy, braviary, mew)) {
        show(BUDDY, 2.6f, 3.2f); show("braviary", 7.4f, 2.4f, Facing.LEFT); show("mew", 4.6f, 1.8f, Facing.DOWN)
        narr("Braviary keeps the team register. He reads the report in silence, one talon holding the page flat. It takes a little while.")
        say("braviary", "You went into an unfamiliar dungeon without checking the route, your supplies, or what lived there.", NORMAL)
        say(BUDDY, "We had a reason.", WORRIED)
        emote("mew", "!")
        say("braviary", "I see. The reason appears to be floating right there, eating a bun.", NORMAL)
        say(BUDDY, "That's MY bun!", ANGRY)
        narr("Braviary closes the report. Something at the corner of his beak might, on a kinder bird, be a smile.")
        say("braviary", "You also brought a child home. So. If you mean to keep exploring, register as a team and learn to prepare.", NORMAL)
        say("braviary", "Choose who goes, check what they are good at, and make sure the assignment suits them. A bigger team is not automatically a better one.", NORMAL)
        say(BUDDY, "A team. Us. ...{player}, did we just get a job?", SURPRISED)
    }

    private fun mewsRealTest() = dungeon("intro-mew-6", "Mew's real test", "tiny-woods", listOf(buddy, mew, sentret)) {
        show(BUDDY, -1f, 2.8f)
        walk(BUDDY, 3f to 2.8f)
        narr("That evening, Mew turns up at the edge of the settlement and splits into copies. One carries a small bundle. Another pretends to be a frightened Pokémon.")
        show("mew", 8.4f, 2f, Facing.LEFT)
        say(BUDDY, "Again?", ANGRY)
        walk("mew", 10.8f to 1.4f)
        narr("Pink shapes scatter into the trees, giggling in six directions at once. {buddy} takes one step after the nearest, and stops.")
        say(BUDDY, "No. It's testing us. It wants to see if we chase the first thing we notice.", DETERMINED)
        narr("Only one trail leaves anything behind: an acorn, then another, set down carefully. Someone really is out here, and that matters more than catching Mew.")
        show("sentret", 6.6f, 3.2f, Facing.LEFT)
        say("sentret", "The pink ones all ran past me! One of them said to wait right here and be brave, so I'm being brave!", SURPRISED)
        say(BUDDY, "You're doing it very well. Be brave a moment longer. We'll take you home.", HAPPY)
        narr("When the little one is safe, the copies pop away one by one, like bubbles. The real Mew floats down in front of the pair.")
        say("mew", "Mew!", HAPPY)
        say(BUDDY, "Was that your idea of fun?", NORMAL)
        emote("mew", "!")
        narr("Mew holds out the lunch bundle from this morning. One bun is left, a little squashed. It bows its head, and peeks up to see how that went.")
        say(BUDDY, "You're... asking to come with us?", SURPRISED)
        cry("mew")
        say("mew", "Mew mew!", JOYOUS)
        flag(IntroMewChapter.MEW_JOINED)
        narr("{buddy} looks at you. You are already breaking the last bun into three.")
        say(BUDDY, "All right. You're in. But no more stealing our lunch.", HAPPY)
        narr("Mew has already noticed the food stall on the way home.")
    }

    private fun adventureBoard() = stage("intro-mew-7", "The adventure board", "city", listOf(buddy, pelly, braviary, mew)) {
        show(BUDDY, 2.4f, 3.2f); show("pelly", 6.2f, 2.6f, Facing.LEFT); show("braviary", 8.2f, 2.4f, Facing.LEFT); show("mew", 4.4f, 1.8f, Facing.DOWN)
        narr("Morning at the exploration centre. The team's name is in Braviary's register, the ink barely dry, and the board is full.")
        say("pelly", "Your first proper expedition board! There are jobs here for teams of every rank. Take your time. The notes don't bite.", HAPPY)
        say("braviary", "Your rank follows your work. Finish assignments, help others, and take on harder places when you are ready. Not before.", NORMAL)
        say(BUDDY, "So we just... choose where to go?", HAPPY)
        say("braviary", "Within what your team can do. The world is larger than this settlement. You will find it one expedition at a time.", NORMAL)
        say("pelly", "And come back and tell me about it. I only ever hear the endings when a team remembers to report.", HAPPY)
        narr("Mew taps a notice with its tail. The notice is upside down. The place on it is nowhere near the Frontier.")
        say(BUDDY, "I don't think that's how you read a job. ...{player}, why is it smiling like that?", NORMAL)
        emote("mew", "?")
    }

    /** The story flag set when Mew has agreed to join; the episode's end then puts Mew in Finds, once. */
    const val MEW_JOINED = "intro-mew-joined"
}
