package com.example.methodmesh.modules.time_tools.countdown

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

/**
 * Polished, module-owned Android View renderer for time.countdown.
 * The controller remains independently callable by presets/protocols/ODK.
 */
class CountdownCapabilityView(
    context: Context,
    private val controller: CountdownCapabilityScreen = CountdownCapabilityScreen()
) : LinearLayout(context) {
    private val ring = TimerRingView(context)
    private val status = TextView(context)
    private val mainAction = TimePillButton(context, "Start")
    private val addMinute = TimePillButton(context, "+1:00")
    private val subtractMinute = TimePillButton(context, "−1:00")
    private val addTenSeconds = TimePillButton(context, "+0:10")
    private val quickRow = LinearLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            if (controller.uiState().status == TimerStatus.Running) handler.postDelayed(this, 200L)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(TimeToolsVisuals.BG)
        addView(build())
        bindActions()
        render()
    }

    private fun build(): View = verticalStack(context).apply {
        addView(TextView(context).apply {
            text = "Timer"
            textSize = 28f
            setTextColor(TimeToolsVisuals.TEXT)
        })
        addView(status.apply {
            textSize = 14f
            setTextColor(TimeToolsVisuals.MUTED)
            setPadding(0, TimeToolsVisuals.dp(context, 4), 0, TimeToolsVisuals.dp(context, 8))
        })

        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, TimeToolsVisuals.dp(context, 330)).apply {
            topMargin = TimeToolsVisuals.dp(context, 8)
            bottomMargin = TimeToolsVisuals.dp(context, 10)
        })

        addView(TimeSectionLabel(context, "QUICK START"))
        quickRow.orientation = HORIZONTAL
        listOf(1L, 5L, 10L, 15L, 25L, 30L).forEach { minutes ->
            val chip = TimePillButton(context, if (minutes < 10) "${minutes}m" else "$minutes")
            chip.textSize = 15f
            chip.setOnClickListener { controller.setDuration(minutes * 60_000L); render() }
            quickRow.addView(chip, LayoutParams(0, TimeToolsVisuals.dp(context, 50), 1f).apply {
                marginEnd = TimeToolsVisuals.dp(context, 5)
            })
        }
        addView(quickRow)

        addView(TimeSectionLabel(context, "ADJUST"))
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(subtractMinute, LayoutParams(0, TimeToolsVisuals.dp(context, 56), 1f).apply { marginEnd = TimeToolsVisuals.dp(context, 7) })
            addView(addTenSeconds, LayoutParams(0, TimeToolsVisuals.dp(context, 56), 1f).apply { marginStart = TimeToolsVisuals.dp(context, 7); marginEnd = TimeToolsVisuals.dp(context, 7) })
            addView(addMinute, LayoutParams(0, TimeToolsVisuals.dp(context, 56), 1f).apply { marginStart = TimeToolsVisuals.dp(context, 7) })
        })

        addView(mainAction, LayoutParams(LayoutParams.MATCH_PARENT, TimeToolsVisuals.dp(context, 64)).apply {
            topMargin = TimeToolsVisuals.dp(context, 18)
        })
    }

    private fun bindActions() {
        subtractMinute.setOnClickListener { controller.subtractMinute(); render() }
        addTenSeconds.setOnClickListener { controller.addSeconds(10); render() }
        addMinute.setOnClickListener { controller.addMinute(); render() }
        mainAction.setOnClickListener {
            when (controller.uiState().status) {
                TimerStatus.Configured -> controller.start()
                TimerStatus.Running -> controller.pause()
                TimerStatus.Paused -> controller.resume()
                TimerStatus.Completed, TimerStatus.Cancelled, is TimerStatus.Failed -> Unit
            }
            render()
        }
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
        ring.primaryText = state.formattedRemaining
        ring.progress = if (state.requestedDurationMs <= 0L) 0f else (state.remainingMs.toFloat() / state.requestedDurationMs.toFloat()).coerceIn(0f, 1f)
        ring.secondaryText = when (state.status) {
            TimerStatus.Configured -> "Ready"
            TimerStatus.Running -> "Running"
            TimerStatus.Paused -> "Paused"
            TimerStatus.Completed -> "Done"
            TimerStatus.Cancelled -> "Cancelled"
            is TimerStatus.Failed -> "Failed"
        }
        ring.active = state.status == TimerStatus.Running
        status.text = when (state.status) {
            TimerStatus.Configured -> "Tap Start when you’re ready"
            TimerStatus.Running -> "Visible on your lock screen while running"
            TimerStatus.Paused -> "Paused — your place is preserved"
            TimerStatus.Completed -> "Timer complete"
            TimerStatus.Cancelled -> "Timer cancelled"
            is TimerStatus.Failed -> "Timer failed"
        }
        mainAction.text = when (state.status) {
            TimerStatus.Configured -> "Start"
            TimerStatus.Running -> "Pause"
            TimerStatus.Paused -> "Resume"
            TimerStatus.Completed -> "Done"
            TimerStatus.Cancelled -> "Done"
            is TimerStatus.Failed -> "Retry"
        }
        val enabledAdjust = state.canAdjust || state.status == TimerStatus.Configured
        addMinute.isEnabled = enabledAdjust
        subtractMinute.isEnabled = enabledAdjust
        addTenSeconds.isEnabled = enabledAdjust
        quickRow.isEnabled = state.status == TimerStatus.Configured
        for (i in 0 until quickRow.childCount) quickRow.getChildAt(i).isEnabled = state.status == TimerStatus.Configured
    }
}
