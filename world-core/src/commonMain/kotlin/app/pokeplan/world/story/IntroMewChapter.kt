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
        narr("{player} and {buddy} are on their way through the settlement when something small and pink darts between them.")
        show("mew", 5.2f, 2.2f, Facing.LEFT)
        emote("mew", "!")
        walk("mew", 7.2f to 2.8f, 9.4f to 2.8f)
        hide("mew")
        narr("It snatched something on the way past. And it is gone around the corner.")
        say(BUDDY, "Hey! That was ours!", Expression.SURPRISED)
        walk(BUDDY, 6.6f to 3.2f)
        show("mew", 9.6f, 2.8f, Facing.LEFT)
        narr("The trail ends at the far end of the lane. The pink Pokémon waves, then ripples into another shape and back again.")
        say(BUDDY, "Wait. You were the one who took it?", SURPRISED)
        say("mew", "Mew!", HAPPY)
        say(BUDDY, "Give it back!", ANGRY)
        narr("Mew floats backwards toward the edge of town, holding the thing just out of reach. It is very clearly an invitation.")
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
        narr("Mew leads them to a path they have never used. A few small Pokémon stand around a dropped satchel.")
        say(BUDDY, "Is this what you wanted to show us?", NORMAL)
        face("mew", Facing.DOWN)
        narr("Mew points at the satchel, then at a narrow way into the Mystery Dungeon beyond.")
        say(BUDDY, "You want us to go in there?", WORRIED)
        emote("mew", "!")
        say(BUDDY, "Fine. But if this is another prank, you're carrying the bag home.", DETERMINED)
        walk(BUDDY, 11f to 3.4f); hide(BUDDY)
    }

    private fun firstExpedition() = dungeon("intro-mew-3", "The first expedition", "tiny-woods", listOf(buddy, mew, sentret)) {
        show(BUDDY, -1f, 2.8f)
        walk(BUDDY, 2.5f to 2.8f, 4.4f to 2.8f)
        narr("The woods are quiet. Sunlight comes down in pieces, and every few steps something small rustles out of the way.")
        show("mew", 8.8f, 1.8f, Facing.LEFT)
        show("sentret", 8.2f, 3.2f, Facing.LEFT)
        walk("sentret", 6.4f to 3.2f)
        say("sentret", "Oh! You found my bag! I got split from my group and... and I lost it in the roots.", Expression.SURPRISED)
        say(BUDDY, "So this wasn't just about chasing us. Someone actually needed help.", WORRIED)
        face("mew", Facing.RIGHT)
        narr("Mew watches from a low branch, unusually quiet.")
        say(BUDDY, "Let's get the satchel back to where it belongs.", DETERMINED)
        walk(BUDDY, 11f to 2.8f); hide(BUDDY)
    }

    private fun jobBecomesPromise() = stage("intro-mew-4", "A job becomes a promise", "city", listOf(buddy, pelly, mew, sentret)) {
        show(BUDDY, 3f, 3.2f); show("pelly", 7f, 2.6f, Facing.LEFT); show("mew", 1.4f, 2.4f, Facing.RIGHT); show("sentret", 5f, 1.8f, Facing.DOWN)
        narr("Back in town, the satchel's owner is much happier. Pelly, who handles requests for the local exploration centre, has already heard.")
        say("pelly", "You two went into the woods, found the owner, and brought the satchel back?", HAPPY)
        say(BUDDY, "We were following a thief.", NORMAL)
        face("mew", Facing.UP)
        say("pelly", "That is not how I would recommend finding your first job. But it did help someone.", NORMAL)
        say(BUDDY, "So this is what exploration teams do? Take requests and help Pokémon?", NORMAL)
        say("pelly", "That is the idea. Rescue, delivery, retrieval, escort, investigation. Some jobs are simple. Some take a whole team.", HAPPY)
        narr("Pelly nods toward the request board by the door.")
        say("pelly", "You choose what to take. Read the objective and the reward before you set out.", NORMAL)
    }

    private fun theTeam() = stage("intro-mew-5", "The team", "city", listOf(buddy, braviary, mew)) {
        show(BUDDY, 2.6f, 3.2f); show("braviary", 7.4f, 2.4f, Facing.LEFT); show("mew", 4.6f, 1.8f, Facing.DOWN)
        narr("Braviary reads the team's report in silence. It takes a little while.")
        say("braviary", "You went into an unfamiliar dungeon without checking the route, your supplies, or what lived there.", NORMAL)
        say(BUDDY, "We had a reason.", WORRIED)
        emote("mew", "!")
        say("braviary", "I see. The reason appears to be standing right there.", NORMAL)
        say("braviary", "If you mean to keep exploring, register as a team and learn to prepare.", NORMAL)
        say("braviary", "Choose who goes, check what they are good at, and make sure the assignment suits them. A bigger team is not automatically a better one.", NORMAL)
    }

    private fun mewsRealTest() = dungeon("intro-mew-6", "Mew's real test", "tiny-woods", listOf(buddy, mew, sentret)) {
        show(BUDDY, -1f, 2.8f)
        walk(BUDDY, 3f to 2.8f)
        narr("That evening, Mew turns up at the edge of the settlement and splits into copies. One carries a small bundle. Another pretends to be a frightened Pokémon.")
        show("mew", 8.4f, 2f, Facing.LEFT)
        say(BUDDY, "Again?", ANGRY)
        walk("mew", 10.8f to 1.4f)
        say(BUDDY, "It's testing us. It wants to see if we chase the first thing we notice.", DETERMINED)
        narr("The real trail is the one that leaves small things behind. Someone really is lost in all this, and finding them matters more than catching Mew.")
        show("sentret", 6.6f, 3.2f, Facing.LEFT)
        say("sentret", "The pink ones all ran past me! One of them told me to wait here!", Expression.SURPRISED)
        say(BUDDY, "Wait here a moment longer. We will take you home.", HAPPY)
        narr("When the confusion is settled, the copies pop away one by one. The real Mew floats down in front of them.")
        say("mew", "Mew!", HAPPY)
        say(BUDDY, "Was that your idea of fun?", NORMAL)
        emote("mew", "!")
        narr("Mew hands back the first thing it took and bows its head. Then it looks at the pair, a little shyly.")
        say(BUDDY, "You're... asking to come with us?", SURPRISED)
        cry("mew")
        say("mew", "Mew mew!", JOYOUS)
        flag(IntroMewChapter.MEW_JOINED)
        say(BUDDY, "All right. But no stealing our lunch.", HAPPY)
        narr("Mew has already noticed the food stall on the way home.")
    }

    private fun adventureBoard() = stage("intro-mew-7", "The adventure board", "city", listOf(buddy, pelly, braviary, mew)) {
        show(BUDDY, 2.4f, 3.2f); show("pelly", 6.2f, 2.6f, Facing.LEFT); show("braviary", 8.2f, 2.4f, Facing.LEFT); show("mew", 4.4f, 1.8f, Facing.DOWN)
        narr("The team's registration is done. The board now shows work for teams of every rank.")
        say("pelly", "Your first proper expedition board. There are jobs here for teams of different ranks.", HAPPY)
        say("braviary", "Your rank follows your work. Finish assignments, help others, and take on harder places when you are ready.", NORMAL)
        say(BUDDY, "So we can choose where to go?", HAPPY)
        say("braviary", "Within what your team can do. The world is larger than this settlement. You will find it one expedition at a time.", NORMAL)
        narr("Mew taps a notice with its tail. The notice is upside down.")
        say(BUDDY, "I don't think that's how you read a job.", NORMAL)
        emote("mew", "?")
    }

    /** The story flag set when Mew has agreed to join; the episode's end then puts Mew in Finds, once. */
    const val MEW_JOINED = "intro-mew-joined"
}
