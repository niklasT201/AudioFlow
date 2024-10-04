package com.example.audioflow

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class ColorManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AppTheme", Context.MODE_PRIVATE)

    fun showColorSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.color_picker_layout, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        val colorButtons = listOf(
            R.id.btnColor1,
            R.id.btnColor2,
            R.id.btnColor3,
            R.id.btnColor4,
            R.id.btnColor5,
            R.id.btnColor6
        )

        colorButtons.forEach { buttonId ->
            dialogView.findViewById<Button>(buttonId).setOnClickListener {
                val color = (it.background as ColorDrawable).color
                saveColor(color)
                applyColorToActivity(context as MainActivity)
                dialog.dismiss()
            }
        }

        val seekBarRed = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val seekBarGreen = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val seekBarBlue = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)

        dialogView.findViewById<Button>(R.id.btnApplyColor).setOnClickListener {
            val red = seekBarRed.progress
            val green = seekBarGreen.progress
            val blue = seekBarBlue.progress
            val color = Color.rgb(red, green, blue)

            // Save the custom color in SharedPreferences
            saveColor(color)

            // Apply the color to the activity immediately
            applyColorToActivity(context as MainActivity)

            // Dismiss the dialog
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveColor(color: Int) {
        prefs.edit().putInt("backgroundColor", color).apply()
    }

    private fun getSavedColor(): Int {
        return prefs.getInt("backgroundColor", ContextCompat.getColor(context, R.color.background_color))
    }

    fun applyColorToActivity(activity: MainActivity) {
        val color = getSavedColor()

        // Apply color to the player screen's background if it's currently visible
        val playerViewContainer = activity.findViewById<View>(R.id.player_view_container)
        playerViewContainer?.setBackgroundColor(color)

        // Text color based on background color
        val primaryTextColor = if (color == Color.WHITE) {
            Color.BLACK // Black text for white background
        } else if (color == Color.BLACK) {
            Color.WHITE // White text for black background
        } else {
            ContextCompat.getColor(activity, R.color.primary_text) // Default primary text color
        }

        val secondaryTextColor = if (color == Color.WHITE) {
            Color.BLACK // Black text for white background
        } else if (color == Color.BLACK) {
            Color.GRAY // Lighter gray for black background (instead of pure white)
        } else {
            ContextCompat.getColor(activity, R.color.secondary_text) // Default secondary text color
        }

        // Apply the text color to the relevant views in the player screen
        val songTitleTextView = activity.findViewById<TextView>(R.id.tv_player_song_title)
        val artistNameTextView = activity.findViewById<TextView>(R.id.tv_artist_name)
        val currentTimeTextView = activity.findViewById<TextView>(R.id.tv_current_time)
        val totalTimeTextView = activity.findViewById<TextView>(R.id.tv_total_time)

        // Set primary text color for song title
        songTitleTextView?.setTextColor(primaryTextColor)

        // Set secondary text color for artist name, current time, and total time
        artistNameTextView?.setTextColor(secondaryTextColor)
        currentTimeTextView?.setTextColor(secondaryTextColor)
        totalTimeTextView?.setTextColor(secondaryTextColor)

        // Button color logic when background is white
        val buttonIconColor = if (color == Color.WHITE) {
            Color.BLACK // Black buttons when background is white
        } else {
            Color.WHITE // Default white buttons
        }

        // Update the play/pause, next, and previous buttons' icon colors
        val playPauseButton = activity.findViewById<ImageView>(R.id.btn_play_pause)
        val nextButton = activity.findViewById<ImageView>(R.id.btn_next)
        val previousButton = activity.findViewById<ImageView>(R.id.btn_previous)
        val playSettingsButton = activity.findViewById<ImageView>(R.id.btn_play_settings)
        val playerSettingsButton = activity.findViewById<ImageView>(R.id.btn_player_settings)

        playPauseButton?.setColorFilter(buttonIconColor)
        nextButton?.setColorFilter(buttonIconColor)
        previousButton?.setColorFilter(buttonIconColor)
        playSettingsButton?.setColorFilter(buttonIconColor)
        playerSettingsButton?.setColorFilter(buttonIconColor)

        if (playerViewContainer?.visibility == View.VISIBLE) {
           // Change the status bar color only when the player screen is visible
            activity.window.statusBarColor = color
        } else {
            // Optionally, you can reset the status bar color to a default value when player screen is not visible
            activity.window.statusBarColor = ContextCompat.getColor(activity, R.color.background_color)
        }
    }

}