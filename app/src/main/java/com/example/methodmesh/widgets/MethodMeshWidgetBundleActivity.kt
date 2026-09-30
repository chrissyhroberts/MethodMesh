package com.example.methodmesh.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MethodMeshWidgetBundleActivity : Activity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val config = MethodMeshWidgetRepository.get(this, appWidgetId)
        if (
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            config == null ||
            config.targetType != MethodMeshWidgetTargetType.BUNDLE
        ) {
            finish()
            return
        }

        configureWindow()
        setContentView(buildPopup(config))
        window.setLayout(dp(310), WindowManager.LayoutParams.WRAP_CONTENT)
        positionNearSourceBounds()
        setFinishOnTouchOutside(true)
    }

    private fun configureWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.CENTER)
    }


    private fun positionNearSourceBounds() {
        val source = intent.sourceBounds ?: return
        window.decorView.post {
            val popupWidth = dp(310)
            val popupHeight = window.decorView.height
            if (popupHeight <= 0) return@post

            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            val margin = dp(12)
            val gap = dp(8)

            val maxX = (screenWidth - popupWidth - margin).coerceAtLeast(margin)
            val x = (source.centerX() - popupWidth / 2).coerceIn(margin, maxX)

            val below = source.bottom + gap
            val above = source.top - popupHeight - gap
            val preferredY = if (below + popupHeight <= screenHeight - margin) {
                below
            } else {
                above
            }
            val maxY = (screenHeight - popupHeight - margin).coerceAtLeast(margin)
            val y = preferredY.coerceIn(margin, maxY)

            window.attributes = window.attributes.apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
        }
    }

    private fun buildPopup(config: MethodMeshWidgetConfig): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedRect(
                color = 0xF4F7F5F2.toInt(),
                radiusDp = 22f,
                strokeColor = 0x66FFFFFF,
                strokeWidthDp = 1
            )
            elevation = dp(14).toFloat()
        }

        card.addView(buildHeader(config))

        val divider = View(this).apply {
            setBackgroundColor(0x1F302A28)
        }
        card.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(9)
                bottomMargin = dp(5)
            }
        )

        if (config.bundleTargets.isEmpty()) {
            card.addView(
                TextView(this).apply {
                    text = "This bundle is empty"
                    setTextColor(0x99302A28.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(18), dp(10), dp(18))
                }
            )
            return card
        }

        val items = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        config.bundleTargets.forEach { target ->
            items.addView(buildTargetRow(config, target))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = config.bundleTargets.size > MAX_VISIBLE_ITEMS
            addView(
                items,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        card.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (config.bundleTargets.size > MAX_VISIBLE_ITEMS) {
                    dp(ITEM_HEIGHT_DP * MAX_VISIBLE_ITEMS)
                } else {
                    LinearLayout.LayoutParams.WRAP_CONTENT
                }
            )
        )

        return card
    }

    private fun buildHeader(config: MethodMeshWidgetConfig): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(this).apply {
            text = MethodMeshWidgetRepository.resolveIconKey(
                this@MethodMeshWidgetBundleActivity,
                config
            ).emoji
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            background = circleDrawable(
                config.colour.argb,
                if (config.appearance == MethodMeshWidgetAppearance.FROSTED) 0xB8 else 0xF2
            )
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleBlock.addView(
            TextView(this).apply {
                text = MethodMeshWidgetRepository.resolveTitle(
                    this@MethodMeshWidgetBundleActivity,
                    config
                )
                setTextColor(0xFF302A28.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )
        titleBlock.addView(
            TextView(this).apply {
                text = "${config.bundleTargets.size} tools"
                setTextColor(0x99302A28.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            }
        )
        row.addView(
            titleBlock,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        return row
    }

    private fun buildTargetRow(
        config: MethodMeshWidgetConfig,
        target: MethodMeshWidgetTarget
    ): View {
        val exists = MethodMeshWidgetRepository.targetExists(this, target)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            minimumHeight = dp(ITEM_HEIGHT_DP)
            alpha = if (exists) 1f else 0.45f
            background = selectableBackground()
            isClickable = exists
            isFocusable = exists
        }

        val icon = TextView(this).apply {
            text = MethodMeshWidgetRepository.resolveTargetIconKey(
                this@MethodMeshWidgetBundleActivity,
                target
            ).emoji
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            background = circleDrawable(config.colour.argb, 0xD8)
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(4), 0)
        }

        textBlock.addView(
            TextView(this).apply {
                text = MethodMeshWidgetRepository.resolveTargetName(
                    this@MethodMeshWidgetBundleActivity,
                    target
                ).ifBlank { "Unavailable item" }
                setTextColor(0xFF302A28.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        )

        val state = MethodMeshWidgetRepository.targetSubtitle(this, target)
        if (state.isNotBlank()) {
            textBlock.addView(
                TextView(this).apply {
                    text = state
                    setTextColor(
                        if (exists) 0x99302A28.toInt() else 0xFF8A4B45.toInt()
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    maxLines = 1
                }
            )
        }

        row.addView(
            textBlock,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        if (exists) {
            row.setOnClickListener {
                sendBroadcast(
                    Intent(this, MethodMeshWidgetProvider::class.java)
                        .setAction(MethodMeshWidgetProvider.ACTION_BUNDLE_TARGET)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        .putExtra(MethodMeshWidgetProvider.EXTRA_TARGET_TYPE, target.type.name)
                        .putExtra(MethodMeshWidgetProvider.EXTRA_TARGET_ID, target.id)
                )
                finish()
            }
        }

        return row
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) {
            getDrawable(value.resourceId)
        } else {
            null
        }
    }

    private fun circleDrawable(baseColour: Int, alpha: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((alpha shl 24) or (baseColour and 0x00FFFFFF))
            setStroke(dp(1), 0x88FFFFFF.toInt())
        }
    }

    private fun roundedRect(
        color: Int,
        radiusDp: Float,
        strokeColor: Int,
        strokeWidthDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(strokeWidthDp), strokeColor)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_VISIBLE_ITEMS = 6
        private const val ITEM_HEIGHT_DP = 56
    }
}
