package app.pokeplan.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.pokeplan.data.db.DataDb

object AndroidDatabase {
    const val FILE_NAME = "pokelife.db"

    fun open(context: Context, name: String = FILE_NAME): DataStore {
        val callback = object : AndroidSqliteDriver.Callback(DataDb.Schema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }

            override fun onCorruption(db: SupportSQLiteDatabase) {
                throw android.database.sqlite.SQLiteDatabaseCorruptException("the database is damaged; it is left for the app to move aside")
            }
        }
        return DataStore.open(AndroidSqliteDriver(DataDb.Schema, context.applicationContext, name, callback = callback))
    }
}
