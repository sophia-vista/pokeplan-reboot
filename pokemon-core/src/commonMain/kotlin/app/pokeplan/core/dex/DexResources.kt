package app.pokeplan.core.dex

/**
 * The text of the Dex table [name] (`species`, `forms`, `evolutions`, `regions`, `entries`, `moves` or `learnsets`): a JSON file in
 * `src/commonMain/resources/dex`, written by tools/species/generate.py. On the JVM and Android it is read from the resources; the browser has no
 * resource files it can read at once, so the web build gets the same files built into its code (the `generateDexWebSources` task).
 */
internal expect fun dexJson(name: String): String
