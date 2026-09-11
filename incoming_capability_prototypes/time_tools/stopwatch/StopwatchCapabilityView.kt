package com.example.methodmesh.modules.time_tools.stopwatch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.methodmesh.modules.time_tools.timing.TimerStatus
import com.example.methodmesh.modules.time_tools.ui.TimePillButton
import com.example.methodmesh.modules.time_tools.ui.TimeSectionLabel
import com.example.methodmesh.modules.time_tools.ui.TimeToolsVisuals
import com.example.methodmesh.modules.time_tools.ui.TimerRingView
import com.example.methodmesh.modules.time_tools.ui.verticalStack

/** Pixel-inspired but MethodMesh-owned stopwatch surface, framework-only Android Views. */
class StopwatchCapabilityView(
    context: Context,
    private val controller: StopwatchCapabilityScreen = StopwatchCapabilityScreen()
) : LinearLayout(context) {
    private val ring = TimerRingView(context)
    private val primary = TimePillButton(context, "Start")
    private val lap = TimePillButton(context, "Lap")
    private val stop = TimePillButton(context, "Stop")
    private val laps = LinearLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            if (controller.uiState().status == TimerStatus.Running) handler.postDelayed(this, 80L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(TimeToolsVisuals.BG)
        addView(build())
        primary.setOnClickListener {
            when (controller.uiState().status) {
                TimerStatus.Configured -> controller.start()
                TimerStatus.Running -> controller.pause()
                TimerStatus.Paused -> controller.resume()
                else -> Unit
            }
            render()
        }
        lap.setOnClickListener { controller.lap(); render() }
        stop.setOnClickListener { controller.stop(); render() }
        render()
    }

    private fun build(): View = verticalStack(context).apply {
        addView(TextView(context).apply {
            text = "Stopwatch"
            textSize = 28f
            setTextColor(TimeToolsVisuals.TEXT)
        })
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, TimeToolsVisuals.dp(context, 330)).apply {
            topMargin = TimeToolsVisuals.dp(context, 16)
            bottomMargin = TimeToolsVisuals.dp(context, 14)
        })
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(lap, LayoutParams(0, TimeToolsVisuals.dp(context, 60), 1f).apply { marginEnd = TimeToolsVisuals.dp(context, 6) })
            addView(primary, LayoutParams(0, TimeToolsVisuals.dp(context, 60), 1f).apply { marginStart = TimeToolsVisuals.dp(context, 6); marginEnd = TimeToolsVisuals.dp(context, 6) })
            addView(stop, LayoutParams(0, TimeToolsVisuals.dp(context, 60), 1f).apply { marginStart = TimeToolsVisuals.dp(context, 6) })
        })
        addView(TimeSectionLabel(context, "LAPS"))
        laps.orientation = VERTICAL
        addView(laps)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    fun render() {
        val state = controller.uiState()
        ring.primaryText = state.formattedElapsed
        ring.secondaryText = when (state.status) {
            TimerStatus.Configured -> "Ready"
            TimerStatus.Running -> "Running"
            TimerStatus.Paused -> "Paused"
            TimerStatus.Completed -> "Stopped"
            TimerStatus.Cancelled -> "Cancelled"
            is TimerStatus.Failed -> "Failed"
        }
        ring.progress = 1f
        primary.text = when (state.status) {
            TimerStatus.Configured -> "Start"
            TimerStatus.Running -> "Pause"
            TimerStatus.Paused -> "Resume"
            TimerStatus.Completed -> "Done"
            TimerStatus.Cancelled -> "Done"
            is TimerStatus.Failed -> "Retry"
        }
        lap.isEnabled = state.canLap
        stop.isEnabled = state.canStop
        laps.removeAllViews()
        if (state.laps.isEmpty()) {
            laps.addView(TextView(context).apply {
                text = "Your laps will appear here"
                textSize = 15f
                setTextColor(TimeToolsVisuals.MUTED)
                setPadding(0, TimeToolsVisuals.dp(context, 8), 0, TimeToolsVisuals.dp(context, 8))
            })
        } else {
            state.laps.asReversed().forEachIndexed { index, item ->
                laps.addView(TextView(context).apply {
                    text = "Lap ${state.laps.size - index}    ${item.formattedLap()}    ${item.formattedTotal()}"
                    textSize = 17f
                    setTextColor(TimeToolsVisuals.TEXT)
                    setPadding(0, TimeToolsVisuals.dp(context, 10), 0, TimeToolsVisuals.dp(context, 10))
                })
            }
        }
    }

    private fun com.example.methodmesh.modules.time_tools.timing.Lap.formattedLap(): String =
        com.example.methodmesh.modules.time_tools.timing.TimeFormatting.duration(lapDurationMs, true)

    private fun com.example.methodmesh.modules.time_tools.timing.Lap.formattedTotal(): String =
        com.example.methodmesh.modules.time_tools.timing.TimeFormatting.duration(totalDurationMs, true)
}
