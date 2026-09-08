package com.example.methodmesh.modules.psychomotorvigilance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.security.SecureRandom
import java.time.Instant
import kotlin.math.min

/**
 * Full-screen PVT response surface.
 *
 * The stimulus software timestamp is captured at the first View.onDraw that
 * paints the active counter. ACTION_DOWN uses MotionEvent.eventTime, which is
 * expressed in the same SystemClock.uptimeMillis time base on Android.
 *
 * This intentionally does not apply an unmeasured device-latency correction.
 */
class PvtReactionView(
    context: Context,
    private val protocol: PvtProtocol,
    private val countdownSeconds: Int,
    private val listener: Listener
) : View(context) {

    interface Listener {
        fun onSessionComplete(session: PvtSession)
        fun onCancelled()
    }

    private enum class Phase { IDLE, COUNTDOWN, WAITING, STIMULUS, FEEDBACK, COMPLETE, CANCELLED }

    private val handler = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val trials = mutableListOf<PvtTrial>()
    private val backgroundPaint = Paint().apply { color = Color.rgb(18, 18, 18) }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 40, 40)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val counterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 220, 0)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    private val instructionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private var phase = Phase.IDLE
    private var countdownRemaining = countdownSeconds.coerceIn(0, 10)
    private var feedbackText = ""
    private var startedTimeIso = ""
    private var testStartUptimeMs = 0L
    private var testDeadlineUptimeMs = 0L
    private var pendingIsiMs: Long? = null
    private var stimulusOnsetUptimeMs: Long? = null
    private var stimulusOnsetTimeIso: String? = null
    private var durationExpiredWhileStimulusVisible = false
    private var completed = false

    private val stimulusRunnable = Runnable { showStimulusIfInTime() }
    private val timeoutRunnable = Runnable { handleTimeout() }
    private val testDeadlineRunnable = Runnable {
        if (phase == Phase.STIMULUS) {
            durationExpiredWhileStimulusVisible = true
        } else {
            finishSession()
        }
    }

    init {
        isFocusable = true
        isClickable = true
        keepScreenOn = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun start() {
        if (phase != Phase.IDLE) return
        requestFocus()
        if (countdownRemaining > 0) {
            phase = Phase.COUNTDOWN
            invalidate()
            scheduleCountdownTick()
        } else {
            beginTest()
        }
    }

    fun cancelTest(notify: Boolean = true) {
        if (completed || phase == Phase.CANCELLED) return
        phase = Phase.CANCELLED
        handler.removeCallbacksAndMessages(null)
        keepScreenOn = false
        invalidate()
        if (notify) listener.onCancelled()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        keepScreenOn = false
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val responseTime = event.eventTime
        when (phase) {
            Phase.WAITING -> handleFalseStart(responseTime)
            Phase.STIMULUS -> handleResponse(responseTime)
            else -> Unit
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val boxWidth = width * 0.72f
        val boxHeight = min(height * 0.22f, boxWidth * 0.38f)
        val left = (width - boxWidth) / 2f
        val top = (height - boxHeight) / 2f
        val box = RectF(left, top, left + boxWidth, top + boxHeight)
        canvas.drawRoundRect(box, 18f, 18f, boxPaint)

        counterPaint.textSize = min(width, height) * 0.095f
        instructionPaint.textSize = min(width, height) * 0.038f

        when (phase) {
            Phase.COUNTDOWN -> {
                canvas.drawText(countdownRemaining.toString(), width / 2f, height / 2f + counterPaint.textSize / 3f, counterPaint)
                canvas.drawText("Get ready", width / 2f, top - 42f, instructionPaint)
            }
            Phase.STIMULUS -> {
                if (stimulusOnsetUptimeMs == null) {
                    val onset = SystemClock.uptimeMillis()
                    stimulusOnsetUptimeMs = onset
                    stimulusOnsetTimeIso = Instant.now().toString()
                    handler.removeCallbacks(timeoutRunnable)
                    handler.postAtTime(timeoutRunnable, onset + protocol.responseTimeoutMs)
                }
                val elapsed = (SystemClock.uptimeMillis() - (stimulusOnsetUptimeMs ?: SystemClock.uptimeMillis())).coerceAtLeast(0L)
                canvas.drawText(elapsed.toString(), width / 2f, height / 2f + counterPaint.textSize / 3f, counterPaint)
                postInvalidateOnAnimation()
            }
            Phase.FEEDBACK -> {
                canvas.drawText(feedbackText, width / 2f, height / 2f + counterPaint.textSize / 3f, counterPaint)
            }
            Phase.WAITING -> Unit
            Phase.COMPLETE -> canvas.drawText("Complete", width / 2f, height / 2f + counterPaint.textSize / 3f, counterPaint)
            Phase.CANCELLED -> canvas.drawText("Cancelled", width / 2f, height / 2f + counterPaint.textSize / 3f, instructionPaint)
            Phase.IDLE -> canvas.drawText("Tap Start", width / 2f, height / 2f + counterPaint.textSize / 3f, instructionPaint)
        }
    }

    private fun scheduleCountdownTick() {
        handler.postDelayed({
            countdownRemaining -= 1
            if (countdownRemaining <= 0) beginTest() else {
                invalidate()
                scheduleCountdownTick()
            }
        }, 1_000L)
    }

    private fun beginTest() {
        startedTimeIso = Instant.now().toString()
        testStartUptimeMs = SystemClock.uptimeMillis()
        testDeadlineUptimeMs = testStartUptimeMs + protocol.taskDurationMs
        phase = Phase.WAITING
        invalidate()
        handler.postAtTime(testDeadlineRunnable, testDeadlineUptimeMs)
        scheduleNextStimulus(testStartUptimeMs)
    }

    private fun scheduleNextStimulus(anchorUptimeMs: Long) {
        if (completed || phase == Phase.CANCELLED) return
        handler.removeCallbacks(stimulusRunnable)
        val isi = randomLongInclusive(protocol.minIsiMs, protocol.maxIsiMs)
        val due = anchorUptimeMs + isi
        pendingIsiMs = isi
        if (due >= testDeadlineUptimeMs) return
        handler.postAtTime(stimulusRunnable, due)
    }

    private fun showStimulusIfInTime() {
        if (completed || phase == Phase.CANCELLED) return
        val now = SystemClock.uptimeMillis()
        if (now >= testDeadlineUptimeMs) {
            finishSession()
            return
        }
        phase = Phase.STIMULUS
        stimulusOnsetUptimeMs = null
        stimulusOnsetTimeIso = null
        durationExpiredWhileStimulusVisible = false
        invalidate()
    }

    private fun handleResponse(responseUptimeMs: Long) {
        val onset = stimulusOnsetUptimeMs
        if (onset == null) {
            handleFalseStart(responseUptimeMs)
            return
        }
        val rt = (responseUptimeMs - onset).coerceAtLeast(0L)
        handler.removeCallbacks(timeoutRunnable)
        if (rt < protocol.falseStartThresholdMs) {
            addTrial(
                isiMs = pendingIsiMs,
                stimulusOnset = onset,
                response = responseUptimeMs,
                rt = rt,
                outcome = PvtTrialOutcome.FALSE_START,
                onsetIso = stimulusOnsetTimeIso
            )
            feedbackText = "False start"
        } else {
            val outcome = if (rt >= protocol.lapseThresholdMs) PvtTrialOutcome.LAPSE else PvtTrialOutcome.VALID
            addTrial(
                isiMs = pendingIsiMs,
                stimulusOnset = onset,
                response = responseUptimeMs,
                rt = rt,
                outcome = outcome,
                onsetIso = stimulusOnsetTimeIso
            )
            feedbackText = rt.toString()
        }
        stimulusOnsetUptimeMs = null
        stimulusOnsetTimeIso = null
        if (durationExpiredWhileStimulusVisible || responseUptimeMs >= testDeadlineUptimeMs) {
            finishSession()
            return
        }
        showFeedbackAndScheduleNext(responseUptimeMs)
    }

    private fun handleFalseStart(responseUptimeMs: Long) {
        handler.removeCallbacks(stimulusRunnable)
        handler.removeCallbacks(timeoutRunnable)
        addTrial(
            isiMs = pendingIsiMs,
            stimulusOnset = stimulusOnsetUptimeMs,
            response = responseUptimeMs,
            rt = stimulusOnsetUptimeMs?.let { (responseUptimeMs - it).coerceAtLeast(0L) },
            outcome = PvtTrialOutcome.FALSE_START,
            onsetIso = stimulusOnsetTimeIso
        )
        stimulusOnsetUptimeMs = null
        stimulusOnsetTimeIso = null
        if (responseUptimeMs >= testDeadlineUptimeMs) {
            finishSession()
            return
        }
        feedbackText = "False start"
        showFeedbackAndScheduleNext(responseUptimeMs)
    }

    private fun handleTimeout() {
        if (phase != Phase.STIMULUS || completed) return
        val onset = stimulusOnsetUptimeMs ?: return
        val responseTime = onset + protocol.responseTimeoutMs
        addTrial(
            isiMs = pendingIsiMs,
            stimulusOnset = onset,
            response = null,
            rt = protocol.responseTimeoutMs,
            outcome = PvtTrialOutcome.TIMEOUT,
            onsetIso = stimulusOnsetTimeIso
        )
        stimulusOnsetUptimeMs = null
        stimulusOnsetTimeIso = null
        feedbackText = protocol.responseTimeoutMs.toString()
        if (durationExpiredWhileStimulusVisible || responseTime >= testDeadlineUptimeMs) {
            finishSession()
        } else {
            showFeedbackAndScheduleNext(responseTime)
        }
    }

    private fun showFeedbackAndScheduleNext(anchorUptimeMs: Long) {
        phase = Phase.FEEDBACK
        invalidate()
        scheduleNextStimulus(anchorUptimeMs)
        val feedbackEnd = anchorUptimeMs + protocol.feedbackDurationMs
        handler.postAtTime({
            if (!completed && phase == Phase.FEEDBACK) {
                phase = Phase.WAITING
                invalidate()
            }
        }, feedbackEnd)
    }

    private fun addTrial(
        isiMs: Long?,
        stimulusOnset: Long?,
        response: Long?,
        rt: Long?,
        outcome: PvtTrialOutcome,
        onsetIso: String?
    ) {
        trials += PvtTrial(
            sequence = trials.size + 1,
            isiMs = isiMs,
            stimulusOnsetUptimeMs = stimulusOnset,
            responseUptimeMs = response,
            reactionTimeMs = rt,
            outcome = outcome,
            stimulusOnsetTimeIso = onsetIso
        )
    }

    private fun finishSession() {
        if (completed || phase == Phase.CANCELLED || testStartUptimeMs == 0L) return
        completed = true
        phase = Phase.COMPLETE
        handler.removeCallbacksAndMessages(null)
        keepScreenOn = false
        val endUptime = SystemClock.uptimeMillis()
        invalidate()
        listener.onSessionComplete(
            PvtSession(
                protocol = protocol,
                countdownSeconds = countdownSeconds,
                startedTimeIso = startedTimeIso,
                endedTimeIso = Instant.now().toString(),
                startedUptimeMs = testStartUptimeMs,
                endedUptimeMs = endUptime,
                trials = trials.toList(),
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                displayRefreshRateHz = display?.refreshRate ?: 0f,
                screenWidthPx = width,
                screenHeightPx = height
            )
        )
    }

    private fun randomLongInclusive(minimum: Long, maximum: Long): Long {
        if (maximum <= minimum) return minimum
        val span = maximum - minimum + 1L
        val positive = random.nextLong().ushr(1)
        return minimum + (positive % span)
    }
}
