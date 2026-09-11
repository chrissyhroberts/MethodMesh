package com.example.methodmesh.modules.time_tools.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * Module-owned visual language for Time Tools. Uses Android framework Views only:
 * no Compose, lifecycle-viewmodel-compose, Material dependency, or AndroidX requirement.
 */
object TimeToolsVisuals {
    const val BG = 0xFF11100F.toInt()
    const val SURFACE = 0xFF1E1A18.toInt()
    const val SURFACE_ACTIVE = 0xFF66544D.toInt()
    const val TEXT = 0xFFF6EEE9.toInt()
    const val MUTED = 0xFFC9BDB7.toInt()
    const val TRACK = 0xFF5B514D.toInt()
    const val ACCENT = 0xFFF2D4C8.toInt()
    const val ACCENT_STRONG = 0xFFFFD1A6.toInt()

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

class TimerRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = TimeToolsVisuals.TRACK
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = TimeToolsVisuals.ACCENT
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TimeToolsVisuals.TEXT
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TimeToolsVisuals.MUTED
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    var progress: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var primaryText: String = "05:00"
        set(value) { field = value; invalidate() }
    var secondaryText: String = "Ready"
        set(value) { field = value; invalidate() }
    var active: Boolean = false
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val stroke = size * 0.042f
        track.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        val pad = stroke * 1.8f
        val rect = RectF((w-size)/2+pad, (h-size)/2+pad, (w+size)/2-pad, (h+size)/2-pad)
        canvas.drawArc(rect, -90f, 360f, false, track)
        canvas.drawArc(rect, -90f, 360f * progress, false, progressPaint)

        timePaint.textSize = size * 0.235f
        captionPaint.textSize = size * 0.072f
        val cx = w / 2f
        val cy = h / 2f
        val baseline = cy - (timePaint.ascent() + timePaint.descent()) / 2f - size * 0.025f
        canvas.drawText(primaryText, cx, baseline, timePaint)
        canvas.drawText(secondaryText, cx, baseline + size * 0.17f, captionPaint)
    }
}

class TimePillButton(context: Context, textValue: String) : Button(context) {
    init {
        text = textValue
        isAllCaps = false
        textSize = 17f
        setTextColor(TimeToolsVisuals.TEXT)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TimeToolsVisuals.dp(context, 28).toFloat()
            setColor(TimeToolsVisuals.SURFACE)
        }
        minHeight = TimeToolsVisuals.dp(context, 52)
        gravity = Gravity.CENTER
        setPadding(TimeToolsVisuals.dp(context, 18), 0, TimeToolsVisuals.dp(context, 18), 0)
    }
}

class TimeSectionLabel(context: Context, textValue: String) : TextView(context) {
    init {
        text = textValue
        textSize = 13f
        setTextColor(TimeToolsVisuals.MUTED)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, TimeToolsVisuals.dp(context, 10), 0, TimeToolsVisuals.dp(context, 8))
    }
}

fun verticalStack(context: Context): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundColor(TimeToolsVisuals.BG)
    setPadding(
        TimeToolsVisuals.dp(context, 20),
        TimeToolsVisuals.dp(context, 18),
        TimeToolsVisuals.dp(context, 20),
        TimeToolsVisuals.dp(context, 24)
    )
}
