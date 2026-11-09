package app.pokeplan.core.dex

internal actual fun dexJson(name: String): String =
    (Dex::class.java.getResourceAsStream("/dex/$name.json") ?: error("The Dex table '$name' is missing from the resources"))
        .use { it.readBytes().decodeToString() }
