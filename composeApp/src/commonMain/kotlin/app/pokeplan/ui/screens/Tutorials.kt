package app.pokeplan.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.pokeplan.ui.AppModel

@Composable
internal fun FirstRecordEgg(model: AppModel, id: String, records: Int) {
    LaunchedEffect(id, records > 0) {
        if (model.store.firstRecordEgg(id, records) != null) model.haptic()
    }
}

