package app.pokeplan.world.story

import app.pokeplan.world.story.Expression.*

/**
 * The seven follow-up Mythical introductions from docs/world-story-rewrite.md: short, self-contained
 * optional adventures that belong to no region, the way the opening ([IntroMewChapter]) does.
 *
 * [MythicalRecruit] is the single table that the story archive and the save both read, so the
 * episode key, the join flag and the species can never drift apart. Finishing an episode sets its
 * join flag; the save then hands that Pokémon to the collection once (a replay grants nothing).
 * Joining is a character decision recorded by the flag, never a gate on a reward.
 */
data class MythicalRecruit(
    /** The episode's chapter number in the layerless opening story. */
    val chapter: Int,
    /** The episode key the runtime records in `WorldData.episodesDone`, e.g. "intro:2". */
    val key: String,
    val name: String,
    val title: String,
    /** The species that joins when the episode is finished (the default form's id). */
    val speciesId: Int,
    /** Set by the episode's last scene, the way `IntroMewChapter.MEW_JOINED` is. */
    val joinFlag: String,
    val summary: String,
)

object MythicalChapters {
    const val FIRST_CHAPTER = 2

    const val HOOPA_JOINED = "mythical-hoopa-joined"
    const val VICTINI_JOINED = "mythical-victini-joined"
    const val CELEBI_JOINED = "mythical-celebi-joined"
    const val JIRACHI_JOINED = "mythical-jirachi-joined"
    const val MARSHADOW_JOINED = "mythical-marshadow-joined"
    const val SHAYMIN_JOINED = "mythical-shaymin-joined"
    const val DARKRAI_JOINED = "mythical-darkrai-joined"

    /**
     * Every follow-up episode, in the order the brief introduces them. One row per Mythical; the
     * archive lists [all] and the save reads this table to make a finished episode's Pokémon join.
     */
    val recruits: List<MythicalRecruit> = listOf(
        MythicalRecruit(2, "intro:2", "Hoopa", "The Wrong Door", 720, HOOPA_JOINED,
            "Hoopa opens seven doors and forgets which one holds its favourite thing"),
        MythicalRecruit(3, "intro:3", "Victini", "The Perfect Run", 494, VICTINI_JOINED,
            "Victini keeps changing the rules of a race, because it wants a contest worth finishing"),
        MythicalRecruit(4, "intro:4", "Celebi", "One More Try", 251, CELEBI_JOINED,
            "Celebi is rewinding one small afternoon, trying to catch a falling Budew"),
        MythicalRecruit(5, "intro:5", "Jirachi", "A Little Wish", 385, JIRACHI_JOINED,
            "Jirachi hears a wish it cannot place, and the wish is not a treasure at all"),
        MythicalRecruit(6, "intro:6", "Marshadow", "The Unnoticed Path", 802, MARSHADOW_JOINED,
            "Marshadow leaves clues through a cave to help a lost Togepi find its way home"),
        MythicalRecruit(7, "intro:7", "Shaymin", "The Long Way Around", 492, SHAYMIN_JOINED,
            "Shaymin escorts a small family and a handful of seeds the long way to a bare meadow"),
        MythicalRecruit(8, "intro:8", "Darkrai", "A Dream with Teeth", 491, DARKRAI_JOINED,
            "A shared nightmare is blamed on Darkrai, but the fear at its heart belongs to someone else"),
    )

    fun recruitFor(key: String): MythicalRecruit? = recruits.firstOrNull { it.key == key }

    fun recruitForChapter(chapter: Int): MythicalRecruit? = recruits.firstOrNull { it.chapter == chapter }

    fun all(): List<StoryEpisode> = listOf(hoopa(), victini(), celebi(), jirachi(), marshadow(), shaymin(), darkrai())

    private fun episode(recruit: MythicalRecruit, scenes: List<StoryScene>) =
        StoryEpisode(null, recruit.chapter, recruit.title, scenes, recruit.summary)

    private val buddy = buddyCharacter()
    private val pelly = npc("pelly", "Pelly", 279)
    private val kecleon = npc("kecleon", "Kecleon", 352)
    private val persian = npc("persian", "Persian", 53)
    private val sentret = npc("sentret", "Sentret", 161)
    private val makuhita = npc("makuhita", "Makuhita", 296)

    private val hoopa = npc("hoopa", "Hoopa", 720)
    private val bronzor = npc("bronzor", "Bronzor", 436)
    private val victini = npc("victini", "Victini", 494)
    private val krabby = npc("krabby", "Krabby", 98)
    private val celebi = npc("celebi", "Celebi", 251)
    private val budew = npc("budew", "Budew", 406)
    private val jirachi = npc("jirachi", "Jirachi", 385)
    private val marshadow = npc("marshadow", "Marshadow", 802)
    private val togepi = npc("togepi", "Togepi", 175)
    private val onix = npc("onix", "Onix", 95)
    private val shaymin = npc("shaymin", "Shaymin", 492)
    private val sunflora = npc("sunflora", "Sunflora", 192)
    private val darkrai = npc("darkrai", "Darkrai", 491)
    private val spoink = npc("spoink", "Spoink", 325)

    // ------------------------------------------------------------------ Hoopa

    private fun hoopa() = episode(recruits[0], listOf(
        stage("mythical-hoopa-1", "Seven doors on a Tuesday", StoryPlaces.SQUARE_TOWN, listOf(buddy, hoopa, pelly)) {
            show(BUDDY, 3.0f, 3.2f)
            show("hoopa", 7.2f, 2.0f, Facing.LEFT)
            show("pelly", 5.4f, 2.8f, Facing.RIGHT)
            narr("Seven rings stand in the grass outside the gate, each one showing a different afternoon.")
            narr("Pelly has counted them twice. She does not like the number.")
            say("hoopa", "Ta-da! The great Hoopa has opened the finest doors in the Frontier! Pick one!", HAPPY)
            say(BUDDY, "It's the same field on the other side of all of them.", NORMAL)
            say("hoopa", "That is because you are not looking with style.", JOYOUS)
            say("pelly", "Doors. Doors that lean. Do doors usually lean like that?", WORRIED)
            narr("One ring shows a red cliff. One shows a cave mouth. One shows this same lane, but at dusk, empty.")
            say("hoopa", "Portals! Passages! The great Hoopa is a connoisseur of places you cannot walk to.", HAPPY)
            narr("It pats the air above each ring as it names it, then stops at the last and smallest ring and pats that twice.")
            say("hoopa", "...Also a small unimportant thing has fallen through one of them. A ring. A chipped one.", NORMAL)
            say(BUDDY, "You lost something.", NORMAL)
            say("hoopa", "The great Hoopa MISPLACED it. In one of the six. Or seven.", SURPRISED)
            say("pelly", "You don't know which one.", SAD)
            say("hoopa", "...The great Hoopa is also, today, a little bit embarrassed.", SAD)
            say(BUDDY, "All right. How did you drop it through seven doors at once?", DETERMINED)
            say("hoopa", "That is the trouble. I was demonstrating all of them AT THE SAME TIME.", HAPPY)
        },
        stage("mythical-hoopa-2", "Blackberry jam and old stone", StoryPlaces.WILD_PLAINS, listOf(buddy, hoopa)) {
            show(BUDDY, 3.2f, 3.2f)
            show("hoopa", 7.4f, 2.2f, Facing.LEFT)
            narr("Hoopa lines the rings up on the grass. Each one holds a different weather and a different smell of it.")
            say("hoopa", "We smell them! Every place smells of itself. The great Hoopa has a nose for doors.", HAPPY)
            say(BUDDY, "The market one smells of blackberry jam. The cold one smells of clean ice.", NORMAL)
            say("hoopa", "And that one? Dust. Old rain. Someone polishing something, very patiently.", NORMAL)
            narr("From the fifth ring comes a small sound: tuk, tuk, tuk. Like a cloth on metal.")
            choice(
                "How do you find the right door?",
                "Smell every ring, one by one" to "mythical-hoopa-smelled",
                "Ask the rings to show their floors" to "mythical-hoopa-asked",
                "Follow the polishing sound" to "mythical-hoopa-listened",
            )
            branch("mythical-hoopa-smelled") {
                say(BUDDY, "Dust, old rain, and a smell like a cellar that has been shut for years.", NORMAL)
                say("hoopa", "You are smelling with your whole face. I respect it enormously.", HAPPY)
            }
            branch("mythical-hoopa-asked") {
                narr("Buddy leans through a ring and looks down. The floor on the other side is made of broken tiles.")
                say(BUDDY, "The floors are different. The dust one has broken tiles, and something has been sweeping them.", SURPRISED)
            }
            branch("mythical-hoopa-listened") {
                narr("The pair stand still and listen. Tuk, tuk. Pause. Tuk, tuk.")
                say(BUDDY, "It's counting. Or polishing. Or both, the way Pelly counts.", HAPPY)
            }
            say("hoopa", "The chipped ring! It was a present. A small one. It does not matter. It only matters a bit.", WORRIED)
            say(BUDDY, "Which is why you opened seven doors looking for it.", NORMAL)
            say("hoopa", "...The great Hoopa does not like losing presents.", SAD)
            narr("The fifth ring brightens. Beyond it lies a corridor of broken tile, and a smell like a shut-up cellar.")
            hide("hoopa")
            say(BUDDY, "Right. The unfamiliar door. In we go, and we come back the way we came.", DETERMINED)
            narr("The ring folds shut behind them with a sound like a page being turned.")
        },
        dungeon("mythical-hoopa-3", "The door that was not on the list", "ancient-ruins", listOf(buddy, hoopa, bronzor)) {
            enter(BUDDY, 3.5f, 2.8f)
            show("hoopa", 5.0f, 1.8f, Facing.LEFT)
            narr("The ruins go down and sideways and, in one place, briefly upward. Every corridor looks like the last one.")
            say("hoopa", "I know this place! I have absolutely been here. Possibly never. It is hard to tell!", HAPPY)
            say(BUDDY, "That's not a route. That's a mood.", WORRIED)
            narr("Around a corner the corridor opens into a round room full of broken pillars and one very tidy pile of pebbles.")
            show("bronzor", 8.2f, 2.6f, Facing.LEFT)
            narr("Something small and round is polishing a chipped ring with a corner of its own cloth. It counts as it works.")
            say("bronzor", "Third. Third. Third. ...Oh. Visitors. I am not finished counting the room yet.", SURPRISED)
            say(BUDDY, "That ring. Where did you get it?", NORMAL)
            say("bronzor", "It fell out of the ceiling. Everything falls out of the ceiling here. It is my best one.", HAPPY)
            say("hoopa", "It is MY best one!", ANGRY)
            narr("Bronzor holds the ring tighter. Its eyes turn very small and very round.")
            say("bronzor", "You can borrow it. If you count the room with me first.", WORRIED)
            say(BUDDY, "We can do that. What are we counting?", NORMAL)
            say("bronzor", "The doors. So nobody gets lost. There are nine. I have checked eight.", NORMAL)
            say("hoopa", "...Eight? There are four.", WORRIED)
            narr("Bronzor is right. Behind the pillars, doors stand in a quiet row: nine of them, each one exactly like home.")
            say(BUDDY, "Hoopa. Which door did we come in through?", WORRIED)
            say("hoopa", "The one... near the... that is a doorway-shaped question.", SURPRISED)
        },
        dungeon("mythical-hoopa-4", "Nine doors, one field", "ancient-ruins", listOf(buddy, hoopa, bronzor)) {
            show("bronzor", 4.5f, 2.4f, Facing.RIGHT)
            show("hoopa", 7.5f, 2.0f, Facing.LEFT)
            enter(BUDDY, 3.0f, 3.2f)
            narr("Bronzor hops from door to door, tapping each one and listening. Hoopa opens the first. It shows a beach.")
            say("hoopa", "Close! The same beach I opened this morning. Wrong afternoon.", NORMAL)
            narr("The second door shows the market. The blackberry jam smell is so strong that Hoopa's stomach answers.")
            say("hoopa", "That one is home. Almost. That is home on a Tuesday, and it is Wednesday.", WORRIED)
            say(BUDDY, "A door can be the right place and the wrong day?", SURPRISED)
            say("bronzor", "I counted this one already. It goes round the room and comes back. Leave a pebble, see?", HAPPY)
            narr("Bronzor sets a pebble in the doorway. It rolls out of the room behind them and bumps their ankle.")
            say("hoopa", "The great Hoopa is beginning to feel that doors are not a maze. They are a JOKE. At me.", ANGRY)
            say(BUDDY, "Then we stop guessing and start counting. Bronzor, show us the ninth.", DETERMINED)
            narr("The ninth door is the smallest and the only one with a draught under it. The air smells of blackberry jam.")
            say("bronzor", "I did not open that one. It is not finished yet.", WORRIED)
            say("hoopa", "It is finished. It is just shy. The small ones always are.", HAPPY)
            narr("Hoopa puts its hand on the ninth door. It opens onto the field outside the gate, at dusk, empty and waiting.")
            say("bronzor", "The counting room is behind me now. I will put it back before anyone misses it.", NORMAL)
            say("hoopa", "Bring the ring. The small unimportant one. ...It is not unimportant.", NORMAL)
            exit("bronzor", 2.4f, toRight = false)
            say(BUDDY, "You just said it wasn't unimportant.", HAPPY)
            say("hoopa", "I said it quietly. Do not make it a thing.", WORRIED)
            narr("They step through, one after another, and the door folds shut like a shop at closing time.")
        },
        stage("mythical-hoopa-5", "A present, slightly travelled", StoryPlaces.SQUARE_TOWN, listOf(buddy, hoopa, bronzor, pelly)) {
            show(BUDDY, 3.0f, 3.2f)
            show("hoopa", 6.6f, 2.2f, Facing.LEFT)
            show("bronzor", 8.4f, 3.0f, Facing.LEFT)
            show("pelly", 1.6f, 2.6f, Facing.RIGHT)
            narr("Pelly has swept the grass where the rings were. There are still seven little flattened circles in it.")
            say("pelly", "You went through a door you couldn't describe, and came back with the ring AND a new friend.", HAPPY)
            say("bronzor", "I am keeping the pebble. It is a good pebble. I counted it.", HAPPY)
            say("hoopa", "And the ring! The very chipped, very important, very unimportant ring.", JOYOUS)
            narr("Hoopa holds it up. In the last of the light the chip in it looks less like damage and more like a map.")
            say(BUDDY, "Who gave it to you?", NORMAL)
            narr("Hoopa does not answer at once. It turns the ring over, once, in the light.")
            say("hoopa", "Someone who moved away. It was a trade. A ring for a promise to visit.", SAD)
            say(BUDDY, "And you opened seven doors to get it back.", NORMAL)
            say("hoopa", "Six. The seventh was also for the ring. ...The great Hoopa is not good at ordinary routes.", WORRIED)
            say("pelly", "Most of us just take the road.", HAPPY)
            say("hoopa", "Yes. And it is full of Pokémon who would help. I watched you take it.", NORMAL)
            narr("Hoopa looks at the pair, then at the ring, then at the pair again. It has the face of a door deciding to open.")
            say("hoopa", "You did not lose your way once. Even when the way was not on any map.", NORMAL)
            say(BUDDY, "We had a very loud guide.", HAPPY)
            say("hoopa", "Then take the loud guide with you! The great Hoopa travels with the team! For journeys! For doors!", JOYOUS)
            say(BUDDY, "For jobs, mostly. They're not all doors.", NORMAL)
            say("hoopa", "Everything is a door if you are confident enough.", HAPPY)
            narr("Pelly puts away the broom. Above the square, a ring opens on its own, showing a quiet road, and closes again.")
            narr("{buddy} looks at you. You are already working out where the first door should go.")
            flash()
            narr("Hoopa joins the team, carrying its chipped ring, and a pebble it did not have this morning.")
            flag(HOOPA_JOINED)
        },
    ))

    // ------------------------------------------------------------------ Victini

    private fun victini() = episode(recruits[1], listOf(
        stage("mythical-victini-1", "A race, apparently", StoryPlaces.WILD_PLAINS, listOf(buddy, victini)) {
            show(BUDDY, 3.2f, 3.2f)
            show("victini", 7.4f, 2.4f, Facing.LEFT)
            narr("A small orange shape is already running circles around them before it says hello.")
            say("victini", "You two! You're the two who walked into the fog last week and came out the other side!", JOYOUS)
            say(BUDDY, "That was one time, and it was a shortcut.", WORRIED)
            say("victini", "It was THE BEST. Which is why we are racing. Right now. To the stone bell under Beach Cave!", HAPPY)
            say(BUDDY, "We didn't agree to that.", NORMAL)
            say("victini", "Rule one: no running off alone. Rule two: no being boring. Rule three: GO!", JOYOUS)
            narr("Victini is already at the cliff path. It is also already explaining the rules again, at speed, while running.")
            say("victini", "I forgot rule four! Rule four is: there is no rule four!", HAPPY)
            say(BUDDY, "Do you always change the rules while racing?", SURPRISED)
            say("victini", "Only when the race gets predictable! A race is supposed to be a GOOD TIME, not a formality.", NORMAL)
            narr("{buddy} looks at you and sighs in the shape of a smile, and starts running.")
        },
        dungeon("mythical-victini-2", "Rule one, rule two, rule three", "beach-cave", listOf(buddy, victini, krabby)) {
            enter(BUDDY, 3.5f, 2.8f)
            show("victini", 6.0f, 2.2f, Facing.LEFT)
            narr("Beach Cave is full of tide pools and small echoes. Victini's flame makes a warm little sun of every puddle.")
            show("krabby", 8.4f, 3.2f, Facing.LEFT)
            narr("Beside a fallen rock, a Krabby is very deliberately not panicking. Its left claw is pinned under the stone.")
            say("krabby", "I am fine! This is fine! I have another claw! It is my second-best claw!", WORRIED)
            say("victini", "Ooh, a snag. New rule! Rule five: anyone who runs past a Pokémon who needs a hand is OUT.", DETERMINED)
            say(BUDDY, "You just made that rule because you saw the Krabby.", NORMAL)
            say("victini", "I made that rule because it is a GOOD rule. Now: race you to the rock!", JOYOUS)
            narr("The rock is much heavier than one flame and one buddy. It takes both of them, a good grip and a count of three.")
            say("krabby", "Three? I like three. I can never get past two on my own.", HAPPY)
            shake(300)
            narr("The rock rolls aside. Krabby flexes its claw, tests it, and waves them both on.")
            say("victini", "That's one mark for you! I'm keeping count! I am extremely keeping count!", HAPPY)
            say(BUDDY, "Marks for helping?", SURPRISED)
            say("victini", "Marks for playing right. Times are for clocks.", NORMAL)
        },
        dungeon("mythical-victini-3", "The perfect line", "beach-cave", listOf(buddy, victini)) {
            enter(BUDDY, 3.0f, 2.8f)
            narr("Past the tide pools the cave narrows into a corridor of standing water and long, clean light.")
            show("victini", 7.0f, 2.2f, Facing.LEFT)
            say("victini", "This is the perfect line! This is the line I have been looking for all morning!", JOYOUS)
            narr("Victini runs at the narrow gap and its flame catches in a snagged fishing net, and then in another one.")
            shake(350)
            say("victini", "...The perfect line has nets in it.", SURPRISED)
            say(BUDDY, "Hold still. You're making it worse with enthusiasm.", DETERMINED)
            narr("Victini holds still for almost two seconds, which for Victini is a career.")
            say("victini", "Go on, then. Finish the race! I'll catch up. I always catch up.", NORMAL)
            choice(
                "What does the team do?",
                "Go back and cut it free" to "mythical-victini-freed",
                "Take the clean run to the bell" to "mythical-victini-ran",
            )
            branch("mythical-victini-freed") {
                say(BUDDY, "We're not finishing a two-team race alone. That's not a race, that's a parade.", NORMAL)
                say("victini", "You're losing SECONDS.", SURPRISED)
                say(BUDDY, "We're gaining a friend.", HAPPY)
            }
            branch("mythical-victini-ran") {
                narr("The pair run for the bell. The corridor stays quiet behind them, in a way that feels like a question.")
                say(BUDDY, "...No. No, this is stupid. It's a bell. It'll still be a bell in a minute.", ANGRY)
                narr("They turn around and run back. Victini is exactly where they left it, grinning through the net.")
                say("victini", "You CAME BACK! You came back and you were winning! That's the best thing!", JOYOUS)
            }
            narr("The net comes away in a few smart tugs. Victini shakes itself out, flame flaring bright again.")
            say("victini", "You know what the trouble is? I keep winning. Everyone lets me win. It's so boring.", SAD)
            say("victini", "I want a contest worth finishing. One where the other team is still there at the end.", NORMAL)
            say(BUDDY, "Then finish this one. With us. Nobody out front.", HAPPY)
            say("victini", "One more rule. Rule six: the race ends when everyone reaches the bell. ALL of everyone.", JOYOUS)
        },
        stage("mythical-victini-4", "The scoreboard", StoryPlaces.SEA_CLIFF, listOf(buddy, victini)) {
            show(BUDDY, 3.4f, 3.2f)
            show("victini", 7.0f, 2.4f, Facing.LEFT)
            narr("The stone bell stands at the cliff's foot with the sea behind it. It is much smaller than the race suggested.")
            say("victini", "Right. Times! The times are: forget the times.", HAPPY)
            say("victini", "Scores! One mark for helping Krabby. One mark for not leaving me in a net. One for the clean line.", NORMAL)
            say("victini", "And one for the moment you turned around. That was the best mark. It was worth three.", HAPPY)
            say(BUDDY, "So who won?", NORMAL)
            say("victini", "Nobody! Everybody! It was a GOOD RACE. Don't make me choose, I hate choosing.", JOYOUS)
            narr("Victini sits on the bell, which is exactly the right size for sitting on, and kicks its heels.")
            say("victini", "Do you know how many races I have run? Neither do I. Not one had a team in it that stayed.", NORMAL)
            say("victini", "I am very good at being fast. It is the only thing that never needed anyone else.", SAD)
            say(BUDDY, "Then it's a good thing you're slow at making friends.", HAPPY)
            say("victini", "I could practise. With you. Every day. New rules, new teams, new routes.", INSPIRED)
            say("victini", "I can test your team builds! I'm excellent at finding the flaw in a plan at full speed.", HAPPY)
            say(BUDDY, "That... is actually going to be useful.", SURPRISED)
            narr("The tide comes in under the bell. Victini's flame makes a small warm circle on the stone.")
            cry("victini")
            say("victini", "Then it's decided. I'm coming with you. Someone has to shout the rules.", JOYOUS)
            flash()
            narr("Victini joins the team. It has already announced a rematch for tomorrow morning.")
            flag(VICTINI_JOINED)
        },
    ))

    // ------------------------------------------------------------------ Celebi

    private fun celebi() = episode(recruits[2], listOf(
        dungeon("mythical-celebi-1", "The same afternoon, three times", "tiny-woods", listOf(buddy, budew, celebi)) {
            enter(BUDDY, 3.5f, 2.8f)
            narr("A sunbeam falls across a broken ledge, and on the ledge a Budew is reaching for something just out of reach.")
            show("budew", 7.6f, 2.4f, Facing.LEFT)
            narr("A gust comes through the hollow log. The ledge creaks. Budew slips, and the world goes white.")
            flash()
            narr("The pair are back at the mouth of the wood. The same sunbeam. The same Budew. The same gust coming.")
            say(BUDDY, "We were just here. We just WATCHED that.", SURPRISED)
            show("celebi", 1.4f, 2.0f, Facing.RIGHT)
            narr("A small green shape drifts at the edge of the light, its wings barely moving. It is watching the ledge too.")
            say("celebi", "You came back with it. Most do not notice the second time.", SAD)
            say(BUDDY, "Notice what? That it's a loop? Of course we came back, we're standing here!", WORRIED)
            say("celebi", "This is my fault. And my second attempt. And my ... I have lost count. Again.", TEARY)
        },
        dialogue("mythical-celebi-2", "Why rewind an afternoon", StoryPlaces.FOREST_CLEARING, listOf(buddy, celebi)) {
            narr("Celebi leads them to a small clearing where the light goes green and holds still, the way a held breath holds.")
            say("celebi", "I can pull a moment back. A short one. A few minutes, no more.", NORMAL)
            say(BUDDY, "You've been pulling this one back again and again.", NORMAL)
            say("celebi", "The little one falls. I catch the fall each time, but the gust comes first, and I am always a step late.", SAD)
            say(BUDDY, "Why not just catch the Budew before it climbs?", SURPRISED)
            say("celebi", "Because then I am changing the afternoon, not saving it. I thought that was cheating.", WORRIED)
            say("celebi", "Time is not a story you may rewrite. It is only a page you may look at twice.", NORMAL)
            say(BUDDY, "You've been looking at the same page for a very long time.", SAD)
            narr("Celebi's wings droop. In the clearing, sunlight stands still on the grass.")
            say("celebi", "It fell. It was frightened. And I could not bear for it to be frightened again.", TEARY)
            say(BUDDY, "Then let us change the page. Not the story. The afternoon.", DETERMINED)
            say("celebi", "...You would try again? For a third time?", SURPRISED)
            say(BUDDY, "We've already seen it twice. Third time, we know what to look at.", HAPPY)
        },
        dungeon("mythical-celebi-3", "Third time, look at the log", "tiny-woods", listOf(buddy, budew, celebi)) {
            enter(BUDDY, 3.5f, 2.8f)
            narr("The sunbeam. The ledge. The Budew, reaching. This time the pair stand still and watch the whole of it.")
            show("budew", 7.6f, 2.4f, Facing.LEFT)
            narr("The gust does not come from the sky. It comes out of the hollow log at the foot of the ledge, in one long breath.")
            say(BUDDY, "There. The log. Something's draughting through it.", SURPRISED)
            show("celebi", 1.4f, 2.2f, Facing.RIGHT)
            say("celebi", "The log is new. It fell after the storm. I have been catching the fall, not moving the log.", SAD)
            choice(
                "How do you stop the gust?",
                "Wedge the log opening" to "mythical-celebi-wedged",
                "Call Budew down first" to "mythical-celebi-called",
            )
            branch("mythical-celebi-wedged") {
                narr("The pair roll a flat stone into the log's mouth. The next gust dies in the wood with a soft, offended sigh.")
                say(BUDDY, "No gust, no slip. The ledge still holds.", HAPPY)
                say("celebi", "A stone. It was a stone the whole time.", SURPRISED)
            }
            branch("mythical-celebi-called") {
                narr("The pair crouch low and call softly up at the ledge. Budew turns, surprised to be noticed.")
                say("budew", "Oh! Is it the gust time? I always forget the gust time.", HAPPY)
                say(BUDDY, "Come down for a moment. We'll look at the thing you wanted together.", NORMAL)
                say("celebi", "It listened to you. It has never listened to me.", SURPRISED)
            }
            narr("The ledge creaks, but the creak no longer matters. Budew hops down into the pair's waiting paws.")
            say("budew", "I wanted the shiny leaf! The one in the sunbeam! It is for my sister!", JOYOUS)
            say("budew", "She is too little to climb, so I climb. I am very good at falling now.", HAPPY)
        },
        dungeon("mythical-celebi-4", "One more try, once", "tiny-woods", listOf(buddy, budew, celebi)) {
            show("budew", 5.0f, 3.0f, Facing.RIGHT)
            enter(BUDDY, 3.0f, 2.8f)
            show("celebi", 7.0f, 1.8f, Facing.LEFT)
            narr("The pair lift Budew up to the sunbeam, and this time the ledge holds, and the shiny leaf comes away in its paw.")
            say("budew", "I got it! I got it and I did not fall!", JOYOUS)
            say("celebi", "You did not fall. I did not rewind. The afternoon is going forward again.", HAPPY)
            say("budew", "Why is the green one crying? Did it want a leaf too?", WORRIED)
            say(BUDDY, "It's happy. Some Pokémon make water when things go right.", HAPPY)
            say("celebi", "I will remember this. All of it. Even the parts where I failed.", NORMAL)
            narr("The world takes a breath and moves on. The sunbeam slides off the ledge and the shadows get long, without asking.")
            say("budew", "Thank you, green one. Thank you, big ones. I am going home before the gust time.", HAPPY)
            hide("budew")
            say("celebi", "Three times I watched it fall. You only had to look at the log.", SAD)
            say(BUDDY, "We had a good reason. You kept the afternoon still so we could.", NORMAL)
        },
        stage("mythical-celebi-5", "Everything happens once", StoryPlaces.FOREST_CLEARING, listOf(buddy, celebi)) {
            show(BUDDY, 4.0f, 3.2f)
            show("celebi", 7.0f, 2.4f, Facing.LEFT)
            narr("Evening in the clearing. The light no longer holds still. It moves like light is supposed to.")
            say("celebi", "I watch a great many afternoons. Most of them happen once and are gone.", NORMAL)
            say("celebi", "I keep the ones that need saving. That is the whole of what I do. It is very small work.", SAD)
            say(BUDDY, "It isn't small to the one who was falling.", NORMAL)
            narr("Celebi is quiet for a while. A moth crosses the clearing and is not rewound.")
            say("celebi", "You were patient with the same moment. Twice. Three times.", HAPPY)
            say("celebi", "Pokémon usually ask me to take them back to something. Nobody offers to stay in the difficult afternoon.", NORMAL)
            say(BUDDY, "We might need a little help with the difficult afternoons.", HAPPY)
            say("celebi", "Then let me come with you. I will not rewind them. I would only like to see them happen.", INSPIRED)
            cry("celebi"); flash()
            narr("Celebi joins the team. Behind it, the little clearing lets the last of the light go, gently.")
            flag(CELEBI_JOINED)
        },
    ))

    // ------------------------------------------------------------------ Jirachi

    private fun jirachi() = episode(recruits[3], listOf(
        dialogue("mythical-jirachi-1", "A wish without a name", StoryPlaces.QUIET_STARS, listOf(buddy, jirachi)) {
            narr("Jirachi is awake far too early. It sits in the grass with its streamers in a tangle, listening to nothing very hard.")
            say("jirachi", "There is a wish. It has been in my head since the bright hour. I cannot find who it belongs to.", WORRIED)
            say(BUDDY, "Whose else would it be? You're the one who hears them.", NORMAL)
            say("jirachi", "That is the thing. I hear it, but it has no name on it. It is warm and small and it sticks.", NORMAL)
            say("jirachi", "Something that creaks. Something that counts to three. Someone who is always last to be picked.", NORMAL)
            say(BUDDY, "That's a shopping list of a wish.", HAPPY)
            say("jirachi", "And underneath all that... it is not asking for a treasure. I have listened for treasure. There is none.", SAD)
            narr("Jirachi holds out a paw and closes it around nothing, as if the wish were a pebble it could hand over.")
            say("jirachi", "Will you help me find it? I do not want to answer the wrong wish again.", WORRIED)
            say(BUDDY, "We'll find it. Small and warm and sticking. We can do that.", DETERMINED)
        },
        stage("mythical-jirachi-2", "The obvious answer", StoryPlaces.SQUARE_TOWN, listOf(buddy, jirachi, kecleon, pelly)) {
            show(BUDDY, 3.4f, 3.2f)
            show("jirachi", 5.2f, 2.2f, Facing.RIGHT)
            show("kecleon", 8.2f, 2.4f, Facing.LEFT)
            show("pelly", 1.4f, 2.8f, Facing.RIGHT)
            narr("Word gets round the square in the time it takes Kecleon to turn his sign around.")
            say("kecleon", "A wish! A wish means treasure, my friends. A warm creak and a count to three?", JOYOUS)
            say("kecleon", "That is plainly the old champion's belt in the dojo storeroom! I have a map. The map is nearly free.", HAPPY)
            say("pelly", "The dojo has no treasure. It has a mat and a very large makuhita.", NORMAL)
            say("kecleon", "The belt! The belt is the treasure! Ask Makuhita! Actually, do not. Makuhita gets quiet about the belt.", WORRIED)
            say("jirachi", "A belt is not warm and small and sticking.", NORMAL)
            say("kecleon", "That is what a person with no sense of treasure would say.", HAPPY)
            hide("kecleon")
            say("pelly", "You'll want Makuhita. Even if there's no belt, the dojo is where the counting happens.", NORMAL)
            say(BUDDY, "The dojo. Of course it is.", HAPPY)
        },
        stage("mythical-jirachi-3", "The champion's belt", StoryPlaces.DOJO, listOf(buddy, jirachi, makuhita)) {
            show(BUDDY, 3.2f, 3.2f)
            show("jirachi", 5.0f, 2.2f, Facing.RIGHT)
            show("makuhita", 7.8f, 2.8f, Facing.LEFT)
            narr("The dojo smells of dust, chalk and a long time of feet. Makuhita is polishing the edging of the mat.")
            say("makuhita", "Visitors! If you've come to fight, I warn you, my knee has opinions now.", HAPPY)
            say(BUDDY, "We've come about a wish. Something that creaks, counts to three, and is always last to be picked.", NORMAL)
            narr("Makuhita's polishing cloth stops in the middle of a stroke.")
            say("makuhita", "The mat creaks. I count to three before a round. And I am always last to be picked, yes.", SAD)
            say("jirachi", "That is it! That is the wish! But you never said it out loud. Why did I hear it?", SURPRISED)
            say("makuhita", "I say it in my head when I polish. Every night. It's not a wish, it's a habit.", WORRIED)
            say("jirachi", "A habit you keep wishing is a wish.", NORMAL)
            say("makuhita", "Then you should know what it is before you answer it. Follow me.", NORMAL)
            narr("Makuhita lifts a loose board at the back of the dojo and takes out a belt, wide and worn and mended in three places.")
            say("makuhita", "My old partner gave me this. She moved to her family's farm. She said the farm needed her more than a mat did.", NORMAL)
            say(BUDDY, "And you still train alone.", NORMAL)
            say("makuhita", "Every evening. I keep the mat ready. It is a very clean mat.", SAD)
        },
        stage("mythical-jirachi-4", "Three rounds", StoryPlaces.DOJO, listOf(buddy, jirachi, makuhita)) {
            show(BUDDY, 3.0f, 3.2f)
            show("jirachi", 4.8f, 2.2f, Facing.RIGHT)
            show("makuhita", 7.8f, 2.8f, Facing.LEFT)
            say("makuhita", "I am out of practise at saying it. So I will say it plainly.", NORMAL)
            say("makuhita", "I wish the mat still had four feet on it after the bell. That is the whole wish. Four feet.", SAD)
            choice(
                "How does the team answer the wish?",
                "Train until the dojo feels full" to "mythical-jirachi-trained",
                "Search the storeroom for the belt" to "mythical-jirachi-searched",
            )
            branch("mythical-jirachi-trained") {
                narr("The pair step onto the mat. It creaks. Makuhita counts to three, and the round begins.")
                say(BUDDY, "Two feet, four feet, six if Jirachi joins. Count again, Makuhita!", HAPPY)
            }
            branch("mythical-jirachi-searched") {
                narr("The storeroom holds three mops, a crate of chalk and one belt. The belt is the treasure. It is a belt.")
                say(BUDDY, "Kecleon would call this a very reasonable treasure.", HAPPY)
                say("makuhita", "Put it back. The treasure was never the belt. It was who was standing on the mat.", NORMAL)
            }
            narr("They train until the light in the high window goes orange. Makuhita wins every round and loses none of them.")
            say("makuhita", "Hah! You two have the balance of a hat rack! But you do not GIVE UP!", JOYOUS)
            say("jirachi", "The wish is changing. It is quieter. It is saying: one more round.", INSPIRED)
            say("makuhita", "One more round, then. And then you will still be here for it.", HAPPY)
            say(BUDDY, "We'll be here. That's the wish, isn't it. Someone still on the mat when the counting ends.", NORMAL)
            narr("Makuhita laughs, and the mat creaks, and the dojo sounds like a full room rather than an empty one.")
        },
        stage("mythical-jirachi-5", "The little wish", StoryPlaces.DOJO, listOf(buddy, jirachi, makuhita)) {
            show(BUDDY, 3.2f, 3.2f)
            show("jirachi", 5.0f, 2.2f, Facing.RIGHT)
            show("makuhita", 7.6f, 2.8f, Facing.LEFT)
            narr("Afterward, Makuhita folds the mat away and hangs the belt back on its nail, and leaves it there.")
            say("makuhita", "I will see you tomorrow. That is a new thing for me to say. It is a good new thing.", HAPPY)
            say("jirachi", "The wish is finished. It did not want a treasure. It only wanted company at the count.", HAPPY)
            say("makuhita", "Most wishes are like that. The treasure is what we say out loud. The wish is underneath.", NORMAL)
            narr("Makuhita closes the dojo door. The pair walk home under a sky that Jirachi has been awake far too long to see.")
            say("jirachi", "I have answered a thousand wishes. I have never once asked what was underneath.", NORMAL)
            say("jirachi", "How many small ones do you think are out there? Quiet ones. Unsaid ones.", INSPIRED)
            say(BUDDY, "A lot. You could come and find them with us.", HAPPY)
            say("jirachi", "I could stay awake. Not for seven nights. For the ordinary ones.", NORMAL)
            cry("jirachi")
            narr("Jirachi dozes off mid-sentence, wakes with a start, and makes itself sit very straight.")
            say("jirachi", "I am awake. I am definitely awake. Which way is the next small hope?", HAPPY)
            flash()
            narr("Jirachi joins the team. It falls asleep twice on the walk home, and insists it did not.")
            flag(JIRACHI_JOINED)
        },
    ))

    // ------------------------------------------------------------------ Marshadow

    private fun marshadow() = episode(recruits[4], listOf(
        dialogue("mythical-marshadow-1", "The note without a sender", StoryPlaces.SQUARE_VILLAGE, listOf(buddy, pelly)) {
            narr("A note is pinned to the request board in handwriting so small the pair have to lean in to read it.")
            say("pelly", "No sender. No reward. Just this. 'Little one lost in Crystal Cave. Something large sleeps inside.'", WORRIED)
            say("pelly", "And underneath, very small: 'Do not wake it. Please do not wake it.'", WORRIED)
            say(BUDDY, "Whoever wrote it knew a lot about a cave they didn't go into.", SURPRISED)
            say("pelly", "There has been a quiet one at the edge of the lamplight all week. It follows you two.", NORMAL)
            say(BUDDY, "Follows us? We'd have noticed.", SURPRISED)
            say("pelly", "That is rather the point of a quiet one.", NORMAL)
            narr("At the edge of the lamplight, the dark moves in a way that dark does not usually move, and then is still.")
            say(BUDDY, "Crystal Cave. Don't wake the large thing. Find the little one. Got it.", DETERMINED)
        },
        dungeon("mythical-marshadow-2", "Small things, out of place", "crystal-cave", listOf(buddy, onix)) {
            enter(BUDDY, 3.5f, 2.8f)
            narr("Crystal Cave is all cold glitter and long shadows. It is very easy to be loud in it, and very unwise.")
            show("onix", 7.6f, 2.2f, Facing.LEFT)
            narr("An Onix lies across the corridor, asleep, its great head tucked under the wall. Every scale of it is listening.")
            say(BUDDY, "Don't wake it. That's three times I've said it to myself now.", WORRIED)
            narr("On the near side of the Onix, small things are out of place: a pebble moved, a claw print in the dust, a draught.")
            say(BUDDY, "Those prints are half our size. And something walked beside them, without leaving any.", SURPRISED)
            narr("Ahead, in the dark between two crystals, something ducks away. It is not the lost little one. It is quicker than that.")
            say(BUDDY, "So we are being helped. Or watched. Either way, someone wants us to follow the pebbles.", NORMAL)
        },
        dungeon("mythical-marshadow-3", "The loudest thing is not always the largest", "crystal-cave", listOf(buddy, togepi)) {
            enter(BUDDY, 3.0f, 2.8f)
            narr("The pebbles lead through a gap too narrow for the Onix and far too quiet for comfort, and then stop.")
            show("togepi", 7.8f, 3.0f, Facing.LEFT)
            narr("Behind a crystal, a small Togepi is curled into a ball with its paws over its eyes.")
            say("togepi", "Is it gone? Is the big shadow with the red eyes gone?", TEARY)
            say(BUDDY, "We haven't seen a big shadow. We've seen a big Onix, but it's asleep.", NORMAL)
            say("togepi", "It wasn't the sleeper. It came out of the dark and chased me! I ran and ran and now I'm lost!", WORRIED)
            choice(
                "What does the team do?",
                "Sit quietly beside Togepi" to "mythical-marshadow-quiet",
                "Look for the shadow's tracks" to "mythical-marshadow-tracks",
                "Call out softly to it" to "mythical-marshadow-called",
            )
            branch("mythical-marshadow-quiet") {
                narr("The pair sit down on the cold floor, close, and say nothing at all for a while.")
                say("togepi", "...You're not running. Everyone runs.", SURPRISED)
                say(BUDDY, "We're not in a hurry. We came a long way to find you.", HAPPY)
            }
            branch("mythical-marshadow-tracks") {
                narr("The pair study the dust. The tracks around Togepi's hiding place are not chasing prints. They circle it.")
                say(BUDDY, "These aren't the tracks of something hunting. They're the tracks of something keeping watch.", SURPRISED)
            }
            branch("mythical-marshadow-called") {
                narr("The pair call into the dark, very softly. There is a long pause, and then a soft answer that is not a word.")
                say("togepi", "It answered you! It never answers ME. It just appears and I run!", SURPRISED)
                say(BUDDY, "Maybe that's because you run. Come on. Let's go and meet it properly.", NORMAL)
            }
            say("togepi", "My name is Togepi. I came in to see the shiny crystals. I didn't mean to wake anything.", SAD)
            say(BUDDY, "You didn't wake anything. The Onix is still asleep. Now stand up, and walk between us.", DETERMINED)
        },
        dungeon("mythical-marshadow-4", "The path nobody used", "crystal-cave", listOf(buddy, togepi, marshadow)) {
            show("togepi", 5.0f, 3.0f, Facing.RIGHT)
            enter(BUDDY, 3.0f, 2.8f)
            narr("The way back is not the way they came. It is a low, quiet passage that was never there a moment ago.")
            show("marshadow", 8.4f, 2.4f, Facing.LEFT)
            narr("A small figure stands in it, grey as a shadow and just as still. Its eyes catch the crystal light and glow red.")
            say("togepi", "THAT'S IT! That's the shadow!", TEARY)
            say(BUDDY, "It's been leading us to you since the cave mouth. And leaving us a path out.", NORMAL)
            say("marshadow", "The sleeper wakes if it hears a footfall it does not know. The child's footsteps were loud.", NORMAL)
            say("marshadow", "So I walked ahead. And made the quiet path. And could not say so, because the child ran.", SAD)
            say("togepi", "You were... helping me?", SURPRISED)
            say("marshadow", "The Onix is a mother. Her nest is under the third crystal. Small feet near it are not welcome.", NORMAL)
            say("togepi", "I didn't know. I would have been very quiet.", WORRIED)
            say("marshadow", "You would have been. Follow the path. Walk where I walk. Do not wake the mother.", NORMAL)
            narr("Marshadow walks ahead without sound. Togepi follows, stepping exactly where it steps, very badly at first.")
            say(BUDDY, "Look at that. The quietest way out of a cave nobody else noticed.", HAPPY)
            narr("Behind them, the Onix shifts in its sleep once, and settles, and does not wake.")
        },
        stage("mythical-marshadow-5", "What was always there", StoryPlaces.SQUARE_TOWN, listOf(buddy, marshadow, togepi, pelly)) {
            show(BUDDY, 3.4f, 3.2f)
            show("togepi", 5.2f, 3.4f, Facing.RIGHT)
            show("marshadow", 8.2f, 2.2f, Facing.LEFT)
            show("pelly", 1.4f, 2.8f, Facing.RIGHT)
            narr("Togepi's family are at the square before the pair are, having run the whole way on the strength of a rumour.")
            say("togepi", "I went in through the little hole! And a shadow chased me! But it was only showing me the way!", JOYOUS)
            say("pelly", "The quiet one is standing in the middle of the square. In daylight.", SURPRISED)
            say("marshadow", "It is easier to be seen by two than by a crowd.", NORMAL)
            say(BUDDY, "You left the note on the board.", NORMAL)
            say("marshadow", "The cave was too loud for me to search alone. I hoped somebody would read it. I did not expect it to be you.", NORMAL)
            say(BUDDY, "Why us?", NORMAL)
            narr("Marshadow considers. The square is noisy, and it does not flinch from it.")
            say("marshadow", "You noticed the pebbles. Most Pokémon walk over a hundred small things and see none.", NORMAL)
            say("marshadow", "The world is full of hidden paths. I would like to walk them with someone who looks.", HAPPY)
            say("togepi", "You can come to my house! I have a very good hiding place!", JOYOUS)
            say("marshadow", "...Perhaps not that hidden.", HAPPY)
            cry("marshadow"); flash()
            narr("Marshadow joins the team. It is standing where it was a moment ago, and also somehow closer.")
            flag(MARSHADOW_JOINED)
        },
    ))

    // ------------------------------------------------------------------ Shaymin

    private fun shaymin() = episode(recruits[5], listOf(
        stage("mythical-shaymin-1", "A patch that stopped growing", StoryPlaces.OVERGROWN_FOREST, listOf(buddy, shaymin)) {
            show(BUDDY, 3.4f, 3.2f)
            show("shaymin", 7.4f, 2.4f, Facing.LEFT)
            narr("The far edge of the forest is not overgrown at all. It is a bald, grey patch where nothing is growing.")
            say("shaymin", "It was a meadow last spring. Then the water changed course, and the hill slipped, and now it is this.", SAD)
            say("shaymin", "I have seeds from the good side. I have water. What I do not have is a way to carry them both.", WORRIED)
            say(BUDDY, "You're a grass Pokémon. Can't you just... grow things?", SURPRISED)
            say("shaymin", "I can help a seed decide to be a flower. I cannot carry a family, a seed bag and a watering pot up a ravine.", NORMAL)
            narr("A family of small Pokémon wait at the good side of the forest: an old Sunflora and two Budew.")
            say("shaymin", "They have to move to the new patch. Their old corner floods now. The direct path is gone.", WORRIED)
            say(BUDDY, "Then we take the long way around. Nobody rushes a meadow.", DETERMINED)
            say("shaymin", "The long way is a forest, a ravine and a stream. It will take all afternoon.", NORMAL)
            say(BUDDY, "Then we start now.", HAPPY)
        },
        stage("mythical-shaymin-2", "The long way around", StoryPlaces.HEALING_FOREST, listOf(buddy, shaymin, sunflora, budew)) {
            show(BUDDY, 3.0f, 3.2f)
            show("shaymin", 4.8f, 2.2f, Facing.RIGHT)
            show("sunflora", 7.6f, 2.6f, Facing.LEFT)
            show("budew", 8.6f, 3.4f, Facing.LEFT)
            narr("The seed bag is heavy, the water pot is heavier, and the pair have been given one of each.")
            say("sunflora", "I am too old to be carried. I will walk, but I will walk slowly. You should know that before we start.", NORMAL)
            say("budew", "I can walk fast! Look! I can walk SO fast!", JOYOUS)
            narr("The second Budew says nothing. It stands close to Sunflora and looks at the ravine, and then at the ground.")
            say("shaymin", "The little one is frightened of the ravine. We will go around. Nobody walks close to the edge.", NORMAL)
            choice(
                "Which way around?",
                "The ravine path, above the water" to "mythical-shaymin-ravine",
                "The old logging trail under the trees" to "mythical-shaymin-trail",
            )
            branch("mythical-shaymin-ravine") {
                say(BUDDY, "Above the water is faster. But we all hold paws and nobody looks down.", NORMAL)
                say("shaymin", "The wind is stronger up there. Hold the seed bag with both paws, not one.", NORMAL)
            }
            branch("mythical-shaymin-trail") {
                say(BUDDY, "Under the trees is slower and quieter. The little one can see the sky between the leaves.", HAPPY)
                say("shaymin", "And the roots will slow us down, which is not a bad thing today.", NORMAL)
            }
            narr("They set off at Sunflora's pace, which is the pace of a flower turning to follow the sun.")
            say("budew", "Are we going to live by the grey patch? Will it be grey forever?", WORRIED)
            say("shaymin", "Not forever. Only until someone plants it. That is what we are carrying.", HAPPY)
        },
        dungeon("mythical-shaymin-3", "The slow road", "petalburg-woods", listOf(buddy, shaymin, sunflora, budew)) {
            enter(BUDDY, 3.0f, 2.8f)
            show("shaymin", 4.6f, 2.2f, Facing.RIGHT)
            show("sunflora", 7.4f, 2.8f, Facing.LEFT)
            show("budew", 8.4f, 3.4f, Facing.LEFT)
            narr("The forest is kind to slow travellers. There are berries at the low branches and shade where they need it.")
            say("sunflora", "I am stopping. Do not tell me we are nearly there. I know exactly where we are.", NORMAL)
            say("budew", "Gran, there's a stream. A big one. I can see it through the trees.", SURPRISED)
            narr("The stream is faster than it looks. The stepping stones are spaced for bigger feet than a Budew's.")
            say("shaymin", "This is the part I was worried about.", WORRIED)
            say(BUDDY, "There's a fallen branch on the far bank. If two of us go across, we can float it back and make a bridge.", DETERMINED)
            narr("It takes a long time. The branch is heavy, the water is cold, and Sunflora supervises from the bank throughout.")
            say("sunflora", "Wrong end. Lift. Lift. Yes. Now you have made a bridge, and the little one can cross without being brave.", HAPPY)
            say("budew", "...I could have jumped.", WORRIED)
            say("sunflora", "You could have. Now you do not have to.", NORMAL)
            narr("Beside the crossing, a narrow path leads off through the trees, where more seedlings are growing wild.")
            choice(
                "There is a side path with more seedlings",
                "Take the side path and gather them" to "mythical-shaymin-gathered",
                "Keep the family on the road" to "mythical-shaymin-stayed",
            )
            branch("mythical-shaymin-gathered") {
                narr("The pair walk the side path and come back with their paws full of seedlings. The sun is lower than it was.")
                say("shaymin", "That is three more patches than we left with. Nobody will mind we are late.", HAPPY)
            }
            branch("mythical-shaymin-stayed") {
                narr("The pair stay on the road. The seedlings beside the path will be there another day, and so will they.")
                say("shaymin", "You did not take everything you could have. There is a kind of patience in that.", HAPPY)
            }
            say("sunflora", "The grey patch. I can smell it from here. It smells of nothing.", NORMAL)
        },
        stage("mythical-shaymin-4", "Planting, not rushing", StoryPlaces.HEALING_FOREST, listOf(buddy, shaymin, sunflora, budew)) {
            show(BUDDY, 3.0f, 3.2f)
            show("shaymin", 4.8f, 2.2f, Facing.RIGHT)
            show("sunflora", 7.4f, 2.8f, Facing.LEFT)
            show("budew", 8.4f, 3.4f, Facing.LEFT)
            narr("The grey patch is just as grey. They water it first, which seems to matter more than the seeds.")
            say("shaymin", "Now the seeds. One at a time. Do not press them in too hard. They have to want to come up.", NORMAL)
            say("budew", "I planted one! I planted one right here! Will it be a flower by dinner?", HAPPY)
            say("shaymin", "No. It will be a flower by next spring, if we are lucky and the rain is kind.", HAPPY)
            say("budew", "...That is a very long time.", SAD)
            say("sunflora", "It is. That is why we plant, and then we go and live our lives, and come back.", NORMAL)
            narr("Shaymin stands in the middle of the grey patch with its eyes closed. The air smells briefly of rain and cut grass.")
            say("shaymin", "There. They have decided. The rest is weather.", NORMAL)
            narr("By the time they walk back to the good side of the forest, one small green shoot is standing where there was grey.")
            say("budew", "It's THERE. It's actually THERE! It's tiny and it's there!", JOYOUS)
            say(BUDDY, "And it doesn't need us to stay and watch it grow.", HAPPY)
            say("shaymin", "No. That is the hardest part of restoration. You do the work, and then you trust the place.", NORMAL)
        },
        stage("mythical-shaymin-5", "A little more green", StoryPlaces.HEALING_FOREST, listOf(buddy, shaymin, sunflora)) {
            show(BUDDY, 3.4f, 3.2f)
            show("shaymin", 6.8f, 2.4f, Facing.LEFT)
            show("sunflora", 1.6f, 2.8f, Facing.RIGHT)
            narr("Sunflora and the two Budew go home by the long way, at the pace of a flower turning, and wave from the trees.")
            say("sunflora", "Come back in spring. We will have a meadow, and I will pretend I planted it.", HAPPY)
            say("shaymin", "You did plant it. You planted the ones who carried the seeds.", HAPPY)
            narr("Shaymin sits down at the edge of the grey patch, which is a little less grey, and looks at the shoot.")
            say("shaymin", "There are a hundred patches like this. Somewhere. Everywhere. I cannot do all of them.", SAD)
            say(BUDDY, "No. But you don't have to do them alone, either.", NORMAL)
            say("shaymin", "You carried a seed bag and a watering pot and an old Sunflora up a ravine, and you did not complain once.", NORMAL)
            say(BUDDY, "We complained once. At the third rest stop.", HAPPY)
            say("shaymin", "That is not once, that is a specific and reasonable complaint.", HAPPY)
            say("shaymin", "I want to see more places recover. The ones you will walk through, and the ones you would not find alone.", INSPIRED)
            say(BUDDY, "Then walk with us. One patch at a time.", HAPPY)
            cry("shaymin"); flash()
            narr("Shaymin joins the team. It walks at the pace of a flower turning, and somehow the pair do not mind.")
            flag(SHAYMIN_JOINED)
        },
    ))

    // ------------------------------------------------------------------ Darkrai

    private fun darkrai() = episode(recruits[6], listOf(
        stage("mythical-darkrai-1", "The dream in the square", StoryPlaces.SQUARE_TOWN, listOf(buddy, pelly, persian, sentret)) {
            show(BUDDY, 3.4f, 3.4f)
            show("pelly", 5.2f, 2.6f, Facing.LEFT)
            show("persian", 7.4f, 2.6f, Facing.LEFT)
            show("sentret", 8.6f, 3.4f, Facing.LEFT)
            narr("Dawn, and half the square is already awake and not happy about it. Everyone had the same dream.")
            say("pelly", "A long corridor. Too many doors. Something behind the doors with teeth. Everyone, exactly the same.", WORRIED)
            say("persian", "I counted my dreams. I always count. This one was the same dream twice, and I only slept once.", WORRIED)
            say("sentret", "I don't want to go to sleep tonight. I know it's silly. I know.", TEARY)
            narr("At the edge of the lane, a tall figure stands in the last of the dark, watching. Nobody is standing near it.")
            say("persian", "It has been there since the second dream started. Darkrai. It walks in dreams. What else would you call that?", WORRIED)
            say("pelly", "Careful. Suspicion is not proof. But it is the best we have before breakfast.", NORMAL)
            say(BUDDY, "Then we'll go into the dream and ask it ourselves.", DETERMINED)
        },
        dialogue("mythical-darkrai-2", "The one who walks in dreams", StoryPlaces.NIGHTMARE, listOf(buddy, darkrai)) {
            narr("The pair sleep, and are standing somewhere dark, on a floor with no walls to it and a long corridor running both ways.")
            narr("Darkrai stands at the end of it, and its shadow goes in the wrong direction.")
            say("darkrai", "You came looking for me. That is unusual. Usually I am found by accident.", NORMAL)
            say(BUDDY, "The whole square is having a nightmare about a corridor with teeth. You're in the middle of the dark.", DETERMINED)
            say("darkrai", "I am in the middle of it because I have been holding it back.", NORMAL)
            say(BUDDY, "Holding it back. For how long?", NORMAL)
            say("darkrai", "This dream is not mine. It is being dreamt by someone here. I walk in it to keep it small.", SAD)
            say("darkrai", "Every night it grows. Every night I stand in front of it. And every morning the square looks at me.", NORMAL)
            say(BUDDY, "So someone in the square is afraid, and their fear is doing this?", WORRIED)
            say("darkrai", "Fear is not proof of evil. You are allowed to hate me. But come and help me find who is dreaming.", NORMAL)
        },
        dungeon("mythical-darkrai-3", "The corridor of doors", "distortion-rift", listOf(buddy, darkrai)) {
            enter(BUDDY, 3.0f, 2.8f)
            show("darkrai", 7.2f, 2.2f, Facing.LEFT)
            narr("The dream is a corridor of doors, and behind each door the same shape is waiting. It has far too many teeth.")
            say("darkrai", "Here the dream decides everything. Say a thing is true and it will be. Say it is nothing and it will be nothing.", NORMAL)
            say(BUDDY, "That's a terrible rule.", WORRIED)
            say("darkrai", "It is a dream's rule. The trick is not to argue with it. The trick is to notice what it keeps showing.", NORMAL)
            narr("They walk. Behind one door: an empty square. Behind another: a meal with nobody at the table.")
            say(BUDDY, "These aren't monsters. They're being alone. And left. And the lights going out.", SURPRISED)
            say("darkrai", "Yes. The teeth are only what a small frightened thing calls the dark.", SAD)
            narr("At the corridor's end there is a small round shape, bouncing, on and on, and not stopping for anything.")
            say(BUDDY, "That's a Spoink. I know that Spoink. It helps at the market. It never stops bouncing.", SURPRISED)
            say("darkrai", "That is who is dreaming. And it is trapped in its own fear with the door shut.", WORRIED)
        },
        dungeon("mythical-darkrai-4", "The source", "distortion-rift", listOf(buddy, darkrai, spoink)) {
            enter(BUDDY, 3.0f, 2.8f)
            show("darkrai", 6.4f, 2.0f, Facing.LEFT)
            show("spoink", 8.4f, 3.0f, Facing.LEFT)
            narr("Spoink bounces on the spot. Its eyes are open but not seeing the pair, or the dark, or anything else.")
            say("spoink", "If I stop, everyone will forget me. Spoink stopped once, and everyone walked past.", TEARY)
            say(BUDDY, "Spoink. It's us. From the square. We brought the wrong change twice last week and you laughed.", NORMAL)
            say("spoink", "That's in the day. In the night I stop and I am gone and nobody notices.", WORRIED)
            say("darkrai", "It has been dreaming that alone for eleven nights. I could not go closer without being the thing it feared.", SAD)
            choice(
                "How does the team reach Spoink?",
                "Ask what it is really afraid of" to "mythical-darkrai-asked",
                "Promise it will never be forgotten" to "mythical-darkrai-promised",
                "Tell it the truth, gently" to "mythical-darkrai-truth",
            )
            branch("mythical-darkrai-asked") {
                say(BUDDY, "What are you actually afraid of? Not the teeth. The thing underneath the teeth.", NORMAL)
                say("spoink", "Of stopping. Of everyone walking past the place where I stopped.", TEARY)
            }
            branch("mythical-darkrai-promised") {
                say(BUDDY, "We won't forget you. Nobody in the square will.", NORMAL)
                say("spoink", "You can't promise that. Nobody can promise forever. That's why the teeth come.", SAD)
                say(BUDDY, "No. I can't promise forever. Let me try again.", NORMAL)
            }
            branch("mythical-darkrai-truth") {
                say(BUDDY, "We can't promise nobody will ever walk past. That would be a lie, and the dream is made of lies.", NORMAL)
                say("spoink", "Then what can you promise?", WORRIED)
            }
            say(BUDDY, "We can promise somebody is here now. Tonight. And tomorrow, if the market's open.", NORMAL)
            say(BUDDY, "A fear doesn't have to be false to be bearable. It just needs company.", HAPPY)
            narr("Spoink stops. It has been bouncing for so long that stopping is loud. The corridor holds its breath.")
            say("spoink", "...Oh. The floor is still here.", SURPRISED)
            narr("Behind the last door, the shape with the teeth turns out to be a door. Just a door, and a very dark room.")
            say("darkrai", "There. It is smaller. It may never be gone, but it will fit back in a night again.", NORMAL)
        },
        dialogue("mythical-darkrai-5", "What fear is not", StoryPlaces.NIGHTMARE, listOf(buddy, darkrai)) {
            narr("The dream loosens. Spoink drifts off into an ordinary, unremarkable sleep, and the corridor becomes a corridor.")
            say("darkrai", "You could have stayed in the square. You could have decided I was the nightmare.", NORMAL)
            say(BUDDY, "We did think it. At first. You're very good at looking guilty.", NORMAL)
            say("darkrai", "I have had a great deal of practice being looked at.", HAPPY)
            say(BUDDY, "We'll try to look properly from now on.", HAPPY)
            narr("Darkrai's shadow settles into the correct direction. It has the same shape as the rest of the dark now.")
            say("darkrai", "The square will still be afraid of me tomorrow. Fear does not care what it has learned.", SAD)
            say(BUDDY, "Maybe. But we'll know. We can say so.", DETERMINED)
            say("darkrai", "That is a strange thing to offer someone who has only ever been accused.", SURPRISED)
            say(BUDDY, "It's what we've got. Night after night, if you need it.", HAPPY)
            say("darkrai", "Then I would like to walk with you. Not only in dreams. I am better company awake. Ask anyone who has slept.", NORMAL)
            narr("Somewhere above the dream, dawn is starting. Darkrai is already dissolving at the edges.")
            say("darkrai", "Go home. Let the square wake up lighter. I will be at the gate at noon, if you still mean it.", NORMAL)
        },
        stage("mythical-darkrai-6", "Morning, with no teeth in it", StoryPlaces.SQUARE_TOWN, listOf(buddy, darkrai, sentret, persian)) {
            show(BUDDY, 3.4f, 3.2f)
            show("sentret", 5.2f, 3.4f, Facing.RIGHT)
            show("persian", 7.4f, 2.6f, Facing.LEFT)
            show("darkrai", 9.0f, 2.4f, Facing.LEFT)
            narr("Morning in the square. The market is open. Everyone slept, and nobody counts the same dream twice.")
            say("sentret", "I slept all night! I dreamt about the market and there were NO teeth!", JOYOUS)
            say("persian", "I dreamed of numbers. Only numbers. It was the finest sleep of my life.", HAPPY)
            narr("At noon, Darkrai comes to the gate exactly as promised, which nobody except the pair expected.")
            say("persian", "I am watching you. That is all. I am watching you, and I am not afraid. I am counting.", NORMAL)
            say("darkrai", "That is more than I have had from a settlement in a long time.", HAPPY)
            say("sentret", "Are you coming with them? Is that allowed? A Pokémon like you?", WORRIED)
            say("darkrai", "A Pokémon like me is asking. It is a strange day.", NORMAL)
            say(BUDDY, "We said we'd be here. You came. That's the whole of it.", HAPPY)
            narr("Darkrai steps out of the shade and into the noon, and its shadow points the right way, and stays.")
            cry("darkrai"); flash()
            narr("Darkrai joins the team. That night, the square sleeps with somebody walking the edge of its dreams, on purpose.")
            flag(DARKRAI_JOINED)
        },
    ))
}
