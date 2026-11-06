package app.pokeplan

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import app.pokeplan.data.DeviceSelfCheck

/** Debug builds only: runs the on-device database checks off the main thread and prints pass or fail per line. */
class DbCheckActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { setPadding(32, 64, 32, 32); textSize = 14f; text = "Running database checks…" }
        setContentView(ScrollView(this).apply { addView(text) })
        Thread {
            val lines = DeviceSelfCheck.run(applicationContext)
            val report = lines.joinToString("\n\n") { (if (it.ok) "PASS  " else "FAIL  ") + it.name + "\n      " + it.detail } +
                "\n\n" + (if (lines.all { it.ok }) "ALL PASSED" else "SOME FAILED") +
                "\n\nUses a throwaway file; your save is untouched."
            runOnUiThread { text.text = report }
        }.start()
    }
}
