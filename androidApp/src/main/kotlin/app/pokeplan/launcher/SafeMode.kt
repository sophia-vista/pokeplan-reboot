package app.pokeplan.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.pokeplan.ui.launcher.LauncherApp

/**
 * The launcher after it crashed three times in a minute: a plain list of the installed apps and two buttons, so the phone can still open its apps.
 * Built from plain Android views on purpose, with nothing from the normal screen (no Compose, no save, no pictures, no widgets, no town), because the
 * normal screen is what keeps crashing.
 */
internal class SafeModeView(
    private val activity: Activity,
    private val onOpen: (String) -> Unit,
    private val onCopyLog: () -> Boolean,
    private val onTryAgain: () -> Unit,
) {
    private var keys: List<String> = emptyList()
    private val labels = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, mutableListOf())

    val root: View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#14181F"))
        fitsSystemWindows = true
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)
        addView(text("Safe mode", 24f, bold = true))
        addView(text("PokéLife stopped three times in a minute, so it started plain. Your apps still open from here.", 15f, "#B8C0CC"))
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            val copy = Button(activity).apply { text = "Copy crash log"; setOnClickListener { text = if (onCopyLog()) "Copied" else "Could not copy" } }
            val again = Button(activity).apply { text = "Try again"; setOnClickListener { onTryAgain() } }
            addView(copy)
            addView(again)
        })
        addView(ListView(activity).apply {
            adapter = labels
            setOnItemClickListener { _, _, position, _ -> keys.getOrNull(position)?.let(onOpen) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Shows [apps] in the list, by name. */
    fun show(apps: List<LauncherApp>) {
        val sorted = apps.sortedBy { it.label.lowercase() }
        keys = sorted.map { it.key }
        labels.clear()
        labels.addAll(sorted.map { it.label })
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()

    private fun text(value: String, sp: Float, color: String = "#FFFFFF", bold: Boolean = false) = TextView(activity).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }
}
