package app.pokeplan.data

import app.pokeplan.core.pokemon.Guardians
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `pokemon_place` table stores a Guardian's slot number, not its module, so the slot order in :data must be exactly the
 * order of `Guardians.MODULES` in pokemon-core forever: a reordered or extended list in one place would silently map Guardians
 * to the wrong module. :data cannot depend on pokemon-core itself, so this cross-check lives in its JVM tests (which see both).
 */
class GuardianModulesTest {
    @Test
    fun theStoredSlotOrderIsGuardiansModuleOrder() {
        assertEquals(Guardians.MODULES, PokemonTables.GUARDIAN_MODULES)
    }
}
