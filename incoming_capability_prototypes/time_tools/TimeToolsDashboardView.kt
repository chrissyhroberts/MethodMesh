package com.example.methodmesh.modules.time_tools

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.methodmesh.modules.time_tools.ui.TimeToolsVisuals

/**
 * Optional visual launcher for native browsing. It never owns execution: every tile emits
 * the same first-class method ID used by presets, protocols, widgets and ODK.
 */
class TimeToolsDashboardView(
    context: Context,
    private val onLaunchMethod: (String) -> Unit
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(TimeToolsVisuals.BG)
        setPadding(
            TimeToolsVisuals.dp(context, 20),
            TimeToolsVisuals.dp(context, 18),
            TimeToolsVisuals.dp(context, 20),
            TimeToolsVisuals.dp(context, 28)
        )

        addView(TextView(context).apply {
            text = "Time"
            textSize = 31f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(TimeToolsVisuals.TEXT)
        })
        addView(TextView(context).apply {
            text = "Timers, stopwatch and time calculations"
            textSize = 14f
            setTextColor(TimeToolsVisuals.MUTED)
            setPadding(0, TimeToolsVisuals.dp(context, 4), 0, TimeToolsVisuals.dp(context, 20))
        })

        val grid = GridLayout(context).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        TimeToolsDashboardScreen.items.forEachIndexed { index, item ->
            grid.addView(tile(item.label, item.methodId), GridLayout.LayoutParams().apply {
                width = 0
                height = TimeToolsVisuals.dp(context, if (index < 2) 184 else 142)
                columnSpec = GridLayout.spec(index % 2, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(
                    if (index % 2 == 0) 0 else TimeToolsVisuals.dp(context, 7),
                    TimeToolsVisuals.dp(context, 7),
                    if (index % 2 == 0) TimeToolsVisuals.dp(context, 7) else 0,
                    TimeToolsVisuals.dp(context, 7)
                )
            })
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun tile(label: String, methodId: String): View = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 19f
        gravity = Gravity.BOTTOM or Gravity.START
        setPadding(
            TimeToolsVisuals.dp(context, 18),
            TimeToolsVisuals.dp(context, 18),
            TimeToolsVisuals.dp(context, 18),
            TimeToolsVisuals.dp(context, 20)
        )
        setTextColor(TimeToolsVisuals.TEXT)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TimeToolsVisuals.dp(context, 28).toFloat()
            setColor(TimeToolsVisuals.SURFACE)
        }
        setOnClickListener { onLaunchMethod(methodId) }
        contentDescription = "Open $label"
    }
}
