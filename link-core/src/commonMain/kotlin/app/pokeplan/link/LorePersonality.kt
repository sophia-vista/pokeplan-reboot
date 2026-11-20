package app.pokeplan.link

/**
 * Plain-language readings for the personality systems a person can note (LINK.md 2.6). The user types the result of a test they took elsewhere; these only
 * explain the label, in the spirit of each system. They are not scores, diagnoses or judgments, and LINK never infers a type for anyone.
 */
object LorePersonality {
    fun mbti(code: String): String = when (code) {
        "INTJ" -> "The Architect: strategic, independent and long-range; likes a plan and high standards."
        "INTP" -> "The Logician: curious, analytical and inventive; loves figuring out how things work."
        "ENTJ" -> "The Commander: decisive, organised and ambitious; leads from the front."
        "ENTP" -> "The Debater: quick, playful and idea-hungry; loves to test a good argument."
        "INFJ" -> "The Advocate: insightful, principled and quietly driven; cares about meaning."
        "INFP" -> "The Mediator: idealistic, gentle and imaginative; guided by personal values."
        "ENFJ" -> "The Protagonist: warm, persuasive and encouraging; brings people together."
        "ENFP" -> "The Campaigner: enthusiastic, creative and sociable; sees possibility everywhere."
        "ISTJ" -> "The Logistician: dependable, thorough and orderly; keeps their word."
        "ISFJ" -> "The Defender: caring, careful and loyal; remembers what matters to people."
        "ESTJ" -> "The Executive: practical, direct and organised; likes things done properly."
        "ESFJ" -> "The Consul: sociable, considerate and community-minded; loves looking after people."
        "ISTP" -> "The Virtuoso: calm, hands-on and adaptable; a quiet problem-solver."
        "ISFP" -> "The Adventurer: gentle, artistic and spontaneous; lives by feel."
        "ESTP" -> "The Entrepreneur: bold, energetic and action-first; thrives in the moment."
        else -> "The Entertainer: lively, warm and fun-loving; makes any moment feel like a party."
    }

    fun enneagram(type: Int): String = when (type) {
        1 -> "Type 1, the Reformer: principled, purposeful and self-controlled; wants to do right."
        2 -> "Type 2, the Helper: generous, warm and people-pleasing; wants to be needed and loved."
        3 -> "Type 3, the Achiever: driven, adaptable and image-aware; wants to be valued for success."
        4 -> "Type 4, the Individualist: expressive, introspective and sensitive; wants to be uniquely themselves."
        5 -> "Type 5, the Investigator: perceptive, private and curious; wants to understand."
        6 -> "Type 6, the Loyalist: committed, responsible and watchful; wants security and trust."
        7 -> "Type 7, the Enthusiast: spontaneous, versatile and upbeat; wants freedom and fun."
        8 -> "Type 8, the Challenger: strong, protective and direct; wants to be in control of their own life."
        else -> "Type 9, the Peacemaker: easygoing, accepting and steady; wants harmony."
    }

    fun loveLanguage(l: LoveLanguage): String = when (l) {
        LoveLanguage.WORDS -> "Feels loved through kind, honest words: compliments, thank-yous, \"I'm proud of you\"."
        LoveLanguage.TIME -> "Feels loved through undivided attention: shared time, real conversation, phone away."
        LoveLanguage.GIFTS -> "Feels loved through thoughtful gifts; it is the thought and the remembering that counts, not the price."
        LoveLanguage.SERVICE -> "Feels loved when someone helps: a task taken off their plate without being asked."
        LoveLanguage.TOUCH -> "Feels loved through closeness: hugs, a hand on the shoulder, sitting side by side."
    }

    fun attachment(a: Attachment): String = when (a) {
        Attachment.SECURE -> "Comfortable with closeness and with space; trusts easily and talks things through."
        Attachment.ANXIOUS -> "Craves closeness and reassurance; may worry when things go quiet. Clear, kind check-ins help."
        Attachment.AVOIDANT -> "Values independence and space; may pull back when things feel intense. Room to breathe helps."
        Attachment.FEARFUL -> "Wants closeness and also fears it; can swing between the two. Patience and consistency help."
    }

    fun disc(d: Disc): String = when (d) {
        Disc.D -> "D, Dominance: direct, results-focused and decisive. Likes it brief and to the point."
        Disc.I -> "I, Influence: outgoing, enthusiastic and persuasive. Likes people and energy."
        Disc.S -> "S, Steadiness: calm, patient and supportive. Likes consistency and no surprises."
        Disc.C -> "C, Conscientiousness: precise, analytical and careful. Likes accuracy and good reasons."
    }

    fun bigFive(t: Trait, l: Level): String = when (t) {
        Trait.OPENNESS -> when (l) {
            Level.LOW -> "Practical and down-to-earth; prefers the familiar and the proven."
            Level.MID -> "Balances curiosity with practicality."
            Level.HIGH -> "Imaginative and curious; enjoys new ideas, art and experiences."
        }
        Trait.CONSCIENTIOUSNESS -> when (l) {
            Level.LOW -> "Flexible and spontaneous; relaxed about plans and deadlines."
            Level.MID -> "Organised when it matters, easygoing when it does not."
            Level.HIGH -> "Organised, dependable and goal-focused; likes a plan."
        }
        Trait.EXTRAVERSION -> when (l) {
            Level.LOW -> "Quiet and reflective; recharges alone. An introvert."
            Level.MID -> "Enjoys company and solitude about equally (an ambivert)."
            Level.HIGH -> "Outgoing and energised by people; talkative and active."
        }
        Trait.AGREEABLENESS -> when (l) {
            Level.LOW -> "Direct and sceptical; says what they think and values honesty over comfort."
            Level.MID -> "Cooperative, and will hold their ground when needed."
            Level.HIGH -> "Kind, trusting and cooperative; puts people at ease."
        }
        Trait.NEUROTICISM -> when (l) {
            Level.LOW -> "Calm and steady under stress."
            Level.MID -> "Feels stress like most people do, and recovers."
            Level.HIGH -> "Feels things deeply and may worry more; sensitive and attentive to what could go wrong."
        }
    }

    const val DISCLAIMER = "These describe a label someone chose; they do not measure or diagnose anyone."
}
