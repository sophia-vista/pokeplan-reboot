package app.pokeplan.link

object LoreChinese {
    fun animal(a: String): String = when (a) {
        "Rat" -> "Quick-witted, resourceful and charming; notices an opportunity before anyone else."
        "Ox" -> "Dependable, patient and hardworking; says little and does a lot."
        "Tiger" -> "Brave, magnetic and competitive; a natural leader with a streak of rebel."
        "Rabbit" -> "Gentle, tasteful and diplomatic; avoids conflict and loves a calm home."
        "Dragon" -> "Confident, lucky-feeling and larger than life; ambitious and generous."
        "Snake" -> "Wise, private and elegant; thinks deeply and chooses words carefully."
        "Horse" -> "Free-spirited, energetic and sociable; loves movement and independence."
        "Goat" -> "Kind, artistic and gentle; sensitive to atmosphere and good at making things lovely."
        "Monkey" -> "Clever, playful and inventive; solves problems with humour."
        "Rooster" -> "Organised, observant and proud; punctual and fond of dressing the part."
        "Dog" -> "Loyal, honest and protective; a friend who shows up."
        else -> "Warm, generous and easy-going; enjoys good food and good company."
    }

    fun element(e: Element): String = when (e) {
        Element.WOOD -> "Wood: growth, kindness and ideas; reaches outward like a tree."
        Element.FIRE -> "Fire: passion, warmth and expressiveness; lights up a room."
        Element.EARTH -> "Earth: steadiness, care and practicality; the one others lean on."
        Element.METAL -> "Metal: clarity, discipline and principle; sharp and exact."
        Element.WATER -> "Water: depth, adaptability and intuition; finds a way around."
    }

    fun dayMaster(s: Stem): String = when (s) {
        Stem.JIA -> "Jia, Yang Wood: a tall tree. Upright, principled and a natural starter; grows steadily and bends slowly."
        Stem.YI -> "Yi, Yin Wood: a vine or flower. Flexible, charming and persistent; finds a way around obstacles."
        Stem.BING -> "Bing, Yang Fire: the sun. Open, generous and radiant; warms everyone and hates to hide."
        Stem.DING -> "Ding, Yin Fire: a candle or lamp. Thoughtful, refined and quietly intense; lights the way up close."
        Stem.WU -> "Wu, Yang Earth: a mountain. Solid, trustworthy and slow to move; a rock for others."
        Stem.JI -> "Ji, Yin Earth: fertile soil. Nurturing, modest and practical; helps others grow."
        Stem.GENG -> "Geng, Yang Metal: an axe or raw steel. Direct, decisive and loyal; values justice and honest speech."
        Stem.XIN -> "Xin, Yin Metal: a jewel. Fine-tempered, perceptive and image-conscious; polished by pressure."
        Stem.REN -> "Ren, Yang Water: a great river or sea. Adventurous, broad-minded and restless; flows toward the horizon."
        Stem.GUI -> "Gui, Yin Water: rain or mist. Intuitive, gentle and imaginative; sensitive to what is unsaid."
    }

    val pillars = listOf(
        "Year pillar: roots, ancestry and the early years.",
        "Month pillar: the home you grew up in, and career.",
        "Day pillar: the self (the stem is the Day Master) and close partnership.",
        "Hour pillar: later life, creations and children. Needs the birth hour.",
    )

    fun balance(counts: Map<Element, Int>): String {
        val total = counts.values.sum().coerceAtLeast(1)
        val top = counts.maxBy { it.value }
        val missing = counts.filter { it.value == 0 }.keys
        val lead = if (top.value * 2 >= total) "${top.key.label} is very strong in this chart" else "${top.key.label} is the strongest element in this chart"
        val gap = if (missing.isEmpty()) "every element is present, a well-rounded mix" else "${missing.joinToString(" and ") { it.label }} ${if (missing.size == 1) "is" else "are"} not in the visible characters; traditionally a hint to seek that quality outside"
        return "$lead; $gap."
    }

    fun kuaGroup(east: Boolean): String =
        if (east) "East Group (1, 3, 4, 9): traditionally suited to North, East, Southeast and South."
        else "West Group (2, 6, 7, 8): traditionally suited to Southwest, West, Northwest and Northeast."

    val kuaDirections = listOf(
        "Sheng Qi (growth, success)", "Tian Yi (health, support)", "Yan Nian (relationships, longevity)", "Fu Wei (stability, inner peace)",
    )

    fun lifePath(n: Int): String = when (n) {
        1 -> "1, the Leader: independent, original and driven to start."
        2 -> "2, the Peacemaker: cooperative, tactful and tuned to others."
        3 -> "3, the Creator: expressive, social and playful."
        4 -> "4, the Builder: practical, loyal and methodical."
        5 -> "5, the Explorer: curious, adaptable and hungry for experience."
        6 -> "6, the Nurturer: responsible, warm and devoted to home and community."
        7 -> "7, the Seeker: analytical, reflective and drawn to depth."
        8 -> "8, the Achiever: ambitious, organised and good with resources."
        9 -> "9, the Humanitarian: compassionate, idealistic and broad in outlook."
        11 -> "11, a master number, the Visionary: intuitive and inspiring, with high sensitivity."
        22 -> "22, a master number, the Master Builder: turns big ideas into real things."
        else -> "33, a master number, the Teacher: devoted, guiding and generous."
    }
}
