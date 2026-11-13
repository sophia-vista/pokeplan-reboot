package app.pokeplan.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.pokeplan.ui.AppModel

/**
 * A module's first Pokémon comes with its first real record (owner, 13 November 2026; it replaced the 5-second demo cards): once [records]
 * is above zero, the module's first egg hatches, once per save ([app.pokeplan.app.GameStore.firstRecordEgg]). It draws nothing; the egg
 * shows in the new-rewards box on Home. [id] is a [app.pokeplan.app.Tutorials] id.
 */
@Composable
internal fun FirstRecordEgg(model: AppModel, id: String, records: Int) {
    LaunchedEffect(id, records > 0) {
        if (model.store.firstRecordEgg(id, records) != null) model.haptic()
    }
}

/** Transitional: the old demo card's call, now the first-record egg. */
@Composable
internal fun TutorialCard(model: AppModel, id: String, careBreaks: Int = 0, healActivities: Int = 0, mintRecords: Int = 0, homeRecords: Int = 0, linkRecords: Int = 0) =
    FirstRecordEgg(model, id, careBreaks + healActivities + mintRecords + homeRecords + linkRecords)
