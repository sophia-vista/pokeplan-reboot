package app.pokeplan.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.pokeplan.data.db.DataDb

/**
 * Opens the product database in app-private storage. Foreign keys are switched on in `onConfigure`, before the file is
 * used, and [DataStore.open] reads the setting back and refuses to run without it.
 *
 * The live store on Android since 0.129.0 (docs/pokelife/ARCHITECTURE/DATA_AND_MIGRATIONS.md section 4.5).
 */
object AndroidDatabase {
    const val FILE_NAME = "pokelife.db"

    fun open(context: Context, name: String = FILE_NAME): DataStore {
        val callback = object : AndroidSqliteDriver.Callback(DataDb.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
        return DataStore.open(AndroidSqliteDriver(DataDb.Schema, context.applicationContext, name, callback = callback))
    }
}
