package com.example.audioflow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat

class CircularProgressButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()
    private var progress = 0f

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = ContextCompat.getColor(context, R.color.secondary_text)
    }

    fun setProgress(value: Float) {
        progress = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - (paint.strokeWidth / 2f)

        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        canvas.drawArc(rectF, -90f, 360f * progress, false, paint)
    }
}