package com.universidad.avicola.util

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.universidad.avicola.R

class TutorialOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var spotlightRect = RectF()
    private val eraserPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.verde_claro)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#CC000000")
        isAntiAlias = true
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSpotlight(rect: RectF) {
        spotlightRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (!spotlightRect.isEmpty) {
            val density = resources.displayMetrics.density
            val radius = 12f * density
            canvas.drawRoundRect(spotlightRect, radius, radius, eraserPaint)
            canvas.drawRoundRect(spotlightRect, radius, radius, borderPaint)
        }
    }
}
