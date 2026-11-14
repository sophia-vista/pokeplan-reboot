package app.pokeplan.world.story

/**
 * The real places of the two Mystery Dungeon games that scenes are set on: a scene's background "map:<name>" is a ground map of Red Rescue Team
 * (`assets/rescue-maps`, the name lower-cased with hyphens) or of Explorers of Sky (`assets/sky-maps`, "sky-<code>" lower-cased), bundled by
 * `composeApp/build.gradle.kts` (`storyRescueMaps`, `storySkyMaps`). "fa-..." is a Rescue Team Friend Area. A place that is a dungeon is
 * drawn with [StoryScene.dungeonId]'s tiles instead.
 */
internal object StoryPlaces {
    // Red Rescue Team
    const val SQUARE_TOWN = "map:pokemon-square"
    const val SQUARE_VILLAGE = "map:square"
    const val DOJO = "map:makuhita-dojo"
    const val POST_OFFICE = "map:pelipper-post-office"
    const val SUNSET_CLIFF = "map:hill-of-the-ancients"
    const val LUMINOUS_CAVE = "map:luminous-cave"
    const val COMET = "map:comet"
    const val STARS = "map:night-sky-1"
    const val QUIET_STARS = "map:night-sky-2"
    const val SUMMIT = "map:summit-sunset"
    const val SKY_TOWER = "map:sky-tower"
    const val NIGHTMARE = "map:nightmare"
    const val SNOW_FOREST = "map:fugitives-snow-forest"
    const val SNOW_ROAD = "map:fugitives-snow-road"
    const val BLIZZARD_ROAD = "map:fugitives-blizzard-road"
    const val DARK_WOODS = "map:sinister-woods-entry"
    const val GREAT_CANYON = "map:great-canyon-entry"
    const val MT_THUNDER = "map:mt-thunder-entry"
    const val LAPIS_CAVE = "map:lapis-cave-entry"
    const val FROSTY_FOREST = "map:frosty-forest-entry"
    const val MT_STEEL = "map:mt-steel-entry"
    const val WILD_PLAINS = "fa-wild-plains"
    const val SKY_BLUE_PLAINS = "fa-sky-blue-plains"
    const val LEGENDARY_ISLAND = "fa-legendary-island"
    const val HEALING_FOREST = "fa-healing-forest"
    const val OVERGROWN_FOREST = "fa-overgrown-forest"

    // Explorers of Sky (the codes name the ground map: T Treasure Town, G the Guild, V and P scenes, H cliffs, D dungeon backgrounds)
    const val TREASURE_TOWN = "map:sky-t00p02"
    const val TREASURE_TOWN_EAST = "map:sky-t00p03"
    const val GUILD_GATE = "map:sky-g01p01a"
    const val GUILD_GATE_SUNSET = "map:sky-g01p01b"
    const val GUILD_GATE_NIGHT = "map:sky-g01p01c"
    const val GUILD_HALL = "map:sky-g01p02a"
    const val GUILD_FLOOR = "map:sky-g01p03a"
    const val GUILD_MESS = "map:sky-g01p06a"
    const val GUILD_BEDROOM = "map:sky-g01p07a"
    const val SEA_CLIFF = "map:sky-h01p99a"
    const val SEA_CLIFF_DUSK = "map:sky-h01p99c"
    const val TREASURE_TOWN_SOUTH = "map:sky-t01p01a"
    const val TREASURE_TOWN_NORTH = "map:sky-t01p02a"
    const val SPINDA_CAFE = "map:sky-p01p04a"
    const val GUILD_SUMMIT = "map:sky-t00p04a"
    const val HIDDEN_LAND = "map:sky-v09p04a"
    const val TEMPORAL_TOWER_RED = "map:sky-v25p01a"
    const val TEMPORAL_TOWER_CALM = "map:sky-v26p02a"
    const val DARK_FUTURE = "map:sky-p05p01a"
    const val ICE_POND = "map:sky-d51p41a"
    const val FOREST_CLEARING = "map:sky-p02p01a"
    const val NIGHT_SKY = "map:sky-v31p01a"
    const val SUNSET_PEAKS = "map:sky-v38p06c"
    const val MEADOW_VALLEY = "map:sky-d73p11a"
}
