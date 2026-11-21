package app.pokeplan

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

abstract class OpenAppTile(private val label: String, private val icon: Int, private val place: String, private val addTask: Boolean) : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = this@OpenAppTile.label
            icon = Icon.createWithResource(this@OpenAppTile, this@OpenAppTile.icon)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_TAB, place)
            .putExtra(MainActivity.EXTRA_ADD_TASK, addTask)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, place.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class AddTaskTile : OpenAppTile("Add task", R.drawable.ic_tile_check, "PLANNER", addTask = true)

class FocusTile : OpenAppTile("Focus", R.drawable.ic_tile_timer, "FOCUS", addTask = false)
