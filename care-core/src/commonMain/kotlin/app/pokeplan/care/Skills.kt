package app.pokeplan.care

/**
 * The four skill groups of dialectical behaviour therapy (DBT), the frame of Care's skills library. The how-tos are short
 * reminders in the app's own words, not a course: the library points to the well-known skills, and a therapist or a DBT
 * skills group teaches them.
 */
enum class SkillGroup(val label: String, val line: String) {
    MINDFULNESS("Mindfulness", "Notice what is happening, here and now"),
    DISTRESS_TOLERANCE("Distress tolerance", "Get through a hard moment without making it worse"),
    EMOTION_REGULATION("Emotion regulation", "Understand a feeling and change what you can"),
    INTERPERSONAL("Interpersonal effectiveness", "Ask, say no and keep your relationships and self-respect"),
}

/** One skill: what it is for and its steps. [calmBreak] marks the one practised as a calm break (it pays as that). */
data class Skill(val id: String, val group: SkillGroup, val name: String, val use: String, val steps: List<String>, val calmBreak: Boolean = false)

object Skills {
    val all: List<Skill> = listOf(
        // Mindfulness
        Skill(
            "wise-mind", SkillGroup.MINDFULNESS, "Wise mind", "When feelings and facts pull in different directions.",
            listOf("Notice what your feelings say.", "Notice what the facts say.", "Breathe and ask: what do I know, deep down, is right here?", "Let the answer come; it is often quiet."),
        ),
        Skill(
            "observe", SkillGroup.MINDFULNESS, "Observe", "To step back from a thought or feeling instead of being carried by it.",
            listOf("Pick one thing: your breath, a sound, a feeling in your body.", "Just notice it, without words if you can.", "When your mind wanders, notice that, and come back."),
        ),
        Skill(
            "describe", SkillGroup.MINDFULNESS, "Describe", "To put words to what is happening without adding a story.",
            listOf("Say what you notice: \"I feel tightness in my chest\", \"I'm having the thought that...\".", "Stick to what you can observe.", "Leave out guesses about why."),
        ),
        Skill(
            "participate", SkillGroup.MINDFULNESS, "Participate", "To be fully in what you are doing.",
            listOf("Choose one activity.", "Throw yourself into it, one thing at a time.", "When you drift, gently come back to it."),
        ),
        Skill(
            "non-judging", SkillGroup.MINDFULNESS, "Non-judgemental stance", "When you catch yourself calling things good, bad, stupid or awful.",
            listOf("Notice the judgement.", "Say the facts instead: what happened, what you feel.", "Don't judge yourself for judging."),
        ),
        // Distress tolerance
        Skill(
            "stop", SkillGroup.DISTRESS_TOLERANCE, "STOP", "When you are about to act on an urge.",
            listOf("Stop: freeze for a moment.", "Take a step back: breathe.", "Observe what is going on, inside and around you.", "Proceed mindfully: what will help, not hurt?"),
        ),
        Skill(
            "tipp", SkillGroup.DISTRESS_TOLERANCE, "TIPP", "When a feeling is so strong you can't think.",
            listOf("Temperature: cool your face with cold water.", "Intense movement: a short burst, like a brisk walk.", "Paced breathing: breathe out longer than you breathe in.", "Paired muscle relaxation: tense as you breathe in, let go as you breathe out."),
        ),
        Skill(
            "paced-breathing", SkillGroup.DISTRESS_TOLERANCE, "Paced breathing", "To calm your body in a minute or two.",
            listOf("Breathe in for about 4 seconds.", "Breathe out for about 6.", "Keep going for a minute or two; the calm break paces it for you."), calmBreak = true,
        ),
        Skill(
            "self-soothe", SkillGroup.DISTRESS_TOLERANCE, "Self-soothe", "To be kind to yourself through the five senses.",
            listOf("Sight: look at something you find beautiful.", "Sound: music or a sound you like.", "Smell, taste and touch: something warm, a favourite scent, a soft blanket."),
        ),
        Skill(
            "accepts", SkillGroup.DISTRESS_TOLERANCE, "Distract with ACCEPTS", "To get through a moment until it passes.",
            listOf("Activities, Contributing (do something for someone), Comparisons.", "Emotions: bring up a different one (a funny video, a song).", "Pushing away for now, Thoughts (count, a puzzle), Sensations (hold ice, a hot shower)."),
        ),
        Skill(
            "radical-acceptance", SkillGroup.DISTRESS_TOLERANCE, "Radical acceptance", "When something can't be changed right now.",
            listOf("Notice that you are fighting reality.", "Remind yourself: this is how it is right now.", "Accept it with your mind and body: relax your face, open your hands.", "Accepting is not approving; it frees you to act."),
        ),
        Skill(
            "pros-cons", SkillGroup.DISTRESS_TOLERANCE, "Pros and cons", "Before acting on a strong urge.",
            listOf("List the pros and cons of acting on the urge.", "List the pros and cons of not acting on it.", "Look at the short and the long term."),
        ),
        // Emotion regulation
        Skill(
            "name-it", SkillGroup.EMOTION_REGULATION, "Name the emotion", "When you feel something but can't tell what.",
            listOf("What happened just before?", "What do you notice in your body?", "What do you want to do?", "Put a name to it: sad, scared, angry, ashamed..."),
        ),
        Skill(
            "check-facts", SkillGroup.EMOTION_REGULATION, "Check the facts", "When a feeling may not fit the situation.",
            listOf("What happened, just the facts?", "What am I assuming?", "Does the feeling, and how strong it is, fit the facts?"),
        ),
        Skill(
            "opposite-action", SkillGroup.EMOTION_REGULATION, "Opposite action", "When a feeling's urge isn't helping you.",
            listOf("Name the feeling and its urge (hide, attack, avoid).", "If acting on it wouldn't help, do the opposite, all the way.", "Approach instead of avoid; be gentle instead of attacking."),
        ),
        Skill(
            "please", SkillGroup.EMOTION_REGULATION, "Look after your body (PLEASE)", "Feelings are harder when the body is run down.",
            listOf("Treat physical illness.", "Eat balanced meals.", "Avoid mood-altering substances.", "Sleep enough, and Exercise a little."),
        ),
        Skill(
            "build-positive", SkillGroup.EMOTION_REGULATION, "Build positive experiences", "To have more good moments, on purpose.",
            listOf("Do one small pleasant thing today.", "Be present while you do it.", "Plan something you look forward to."),
        ),
        // Interpersonal effectiveness
        Skill(
            "dear-man", SkillGroup.INTERPERSONAL, "DEAR MAN", "To ask for something or say no.",
            listOf("Describe the situation; Express how you feel; Assert what you want; Reinforce why it helps them too.", "Stay Mindful of your goal, Appear confident, Negotiate."),
        ),
        Skill(
            "give", SkillGroup.INTERPERSONAL, "GIVE", "To keep the relationship while you ask.",
            listOf("Be Gentle.", "Act Interested.", "Validate their side.", "Use an Easy manner."),
        ),
        Skill(
            "fast", SkillGroup.INTERPERSONAL, "FAST", "To keep your self-respect.",
            listOf("Be Fair to yourself and them.", "No needless Apologies.", "Stick to your values.", "Be Truthful."),
        ),
    )

    fun byId(id: String): Skill? = all.firstOrNull { it.id == id }

    fun inGroup(group: SkillGroup): List<Skill> = all.filter { it.group == group }
}
