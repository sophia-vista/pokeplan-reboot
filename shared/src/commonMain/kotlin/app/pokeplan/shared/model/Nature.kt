package app.pokeplan.shared.model

import kotlin.random.Random

object Nature {
    val all: List<String> = listOf(
        "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile",
        "Relaxed", "Impish", "Lax", "Timid", "Hasty", "Serious", "Jolly",
        "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm",
        "Gentle", "Sassy", "Careful", "Quirky",
    )

    fun random(rng: Random): String = all[rng.nextInt(all.size)]
}
