package com.example.audioflow

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

class RepeatManager(
    private val repeatCounterView: TextView,
    private val mainActivity: MainActivity
) {
    private var repeatCounter = 0
    private var isLongPressing = false
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    fun setupRepeatListener(playPauseButton: View) {
        playPauseButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressing = false
                    longPressRunnable = Runnable {
                        isLongPressing = true
                        incrementRepeatCounter()
                        handler.postDelayed(longPressRunnable!!, 400)
                    }
                    handler.postDelayed(longPressRunnable!!, 700)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable!!)
                    if (!isLongPressing) {
                        // Handle normal click (play/pause)
                        mainActivity.togglePlayPause()
                    }
                }
            }
            true
        }
    }

    private fun incrementRepeatCounter() {
        repeatCounter++
        updateRepeatCounterView()
    }

    private fun updateRepeatCounterView() {
        if (repeatCounter > 0) {
            repeatCounterView.visibility = View.VISIBLE
            repeatCounterView.text = repeatCounter.toString()
        } else {
            repeatCounterView.visibility = View.GONE
        }
    }

    fun onSongFinished(): Boolean {
        if (repeatCounter > 0) {
            repeatCounter--
            updateRepeatCounterView()
            return true // Repeat the song
        }
        return false // Don't repeat, move to next song
    }

    fun reset() {
        repeatCounter = 0
        updateRepeatCounterView()
    }
}