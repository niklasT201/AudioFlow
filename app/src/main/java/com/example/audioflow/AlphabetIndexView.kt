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
        textAlign = Paint.Align.CENTER
    }

    private var selectedLetter: Char? = null
    private val defaultTextColor = ContextCompat.getColor(context, R.color.alphabet_text_color)
    private val selectedTextColor = ContextCompat.getColor(context, R.color.selected_letter) // or any color you prefer

    var onLetterSelectedListener: ((String) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val charHeight = height / alphabet.size

        alphabet.forEachIndexed { index, char ->
            paint.color = if (char == selectedLetter) selectedTextColor else defaultTextColor
            canvas.drawText(
                char.toString(),
                width / 2f,
                charHeight * (index + 1),
                paint
            )
        }
    }

    fun setVisibilityWithAnimation(isVisible: Boolean) {
        if (isVisible) {
            this.visibility = View.VISIBLE
            this.alpha = 0f
            this.animate().alpha(1f).setDuration(200).start()
        } else {
            this.animate().alpha(0f).setDuration(200).withEndAction {
                this.visibility = View.GONE
                selectedLetter = null  // Reset selection when hiding
                invalidate()
            }.start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val y = event.y
                val index = (y / height * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                selectedLetter = alphabet[index]
                invalidate()
                onLetterSelectedListener?.invoke(alphabet[index].toString())
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}