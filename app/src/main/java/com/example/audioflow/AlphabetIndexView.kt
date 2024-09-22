package com.example.audioflow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class AlphabetIndexView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimensionPixelSize(R.dimen.alphabet_text_size).toFloat()
        color = ContextCompat.getColor(context, R.color.alphabet_text_color)
        textAlign = Paint.Align.CENTER
    }

    var onLetterSelectedListener: ((String) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val charHeight = height / alphabet.size

        alphabet.forEachIndexed { index, char ->
            canvas.drawText(
                char.toString(),
                width / 2f,
                charHeight * (index + 1),
                paint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                val index = (y / height * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                onLetterSelectedListener?.invoke(alphabet[index].toString())
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}