package com.example.audioflow

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.renderscript.Element
import android.util.Log
import android.view.WindowManager
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat

class ColorManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("AppTheme", Context.MODE_PRIVATE)
    private val statusBarHeight by lazy {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    fun showColorSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.color_picker_layout, null)
        val dialog = AlertDialog.Builder(context, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .create()

        // Simplified window setup
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val colorButtons = listOf(
            R.id.btnColor1,
            R.id.btnColor2,
            R.id.btnColor3,
            R.id.btnColor4,
            R.id.btnColor5,
            R.id.btnColor6
        )

        // Add radio group for background style
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.rgBackgroundStyle)

        // Set initial selection based on saved preference
        val useBlurBackground = prefs.getBoolean("useBlurBackground", false)
        radioGroup.check(if (useBlurBackground) R.id.rbBlurBackground else R.id.rbSolidColor)

        colorButtons.forEach { buttonId ->
            dialogView.findViewById<Button>(buttonId).setOnClickListener {
                val color = (it.background as ColorDrawable).color
                saveColor(color)
                applyColorToActivity(context as MainActivity)
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

            // Save the background style preference
            val useBlur = radioGroup.checkedRadioButtonId == R.id.rbBlurBackground
            saveBackgroundPreference(useBlur)

            saveColor(color)
            applyColorToActivity(context as MainActivity)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveBackgroundPreference(useBlur: Boolean) {
        prefs.edit().putBoolean("useBlurBackground", useBlur).apply()
    }

    private fun saveColor(color: Int) {
        prefs.edit().putInt("backgroundColor", color).apply()
    }

    private fun getSavedColor(): Int {
        return prefs.getInt("backgroundColor", ContextCompat.getColor(context, R.color.background_color))
    }

    fun updateBackgroundWithAlbumArt(activity: MainActivity, albumArtImageView: ImageView) {
        try {
            val useBlurBackground = prefs.getBoolean("useBlurBackground", false)
            if (!useBlurBackground) {
                applyColorToActivity(activity)
                return
            }

            val playerViewContainer = activity.findViewById<View>(R.id.player_view_container)

            if (albumArtImageView.drawable != null) {
                // Prevent status bar flicker by maintaining transparency
                makeStatusBarTransparent(activity)

                albumArtImageView.isDrawingCacheEnabled = true
                val albumArtBitmap = if (albumArtImageView.drawingCache != null) {
                    Bitmap.createBitmap(albumArtImageView.drawingCache)
                } else {
                    null
                }
                albumArtImageView.isDrawingCacheEnabled = false

                if (albumArtBitmap != null) {
                    Thread {
                        val blurredBackground = createBlurredBackground(albumArtBitmap)
                        activity.runOnUiThread {
                            playerViewContainer?.background = BitmapDrawable(activity.resources, blurredBackground)
                            // Ensure layout stays consistent
                            applyConsistentLayout(activity)
                        }
                        albumArtBitmap.recycle()
                    }.start()
                }
            }
        } catch (e: Exception) {
            Log.e("ColorManager", "Error updating background: ${e.message}")
            applyColorToActivity(activity)
        }
    }

    private fun applySolidColorMode(activity: MainActivity, color: Int) {
        activity.window.apply {
            statusBarColor = color
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        activity.findViewById<View>(R.id.player_view_container)?.setBackgroundColor(color)
    }

    private fun applyConsistentLayout(activity: MainActivity) {
        // Get references to views that need consistent positioning
        val closeButton = activity.findViewById<ImageButton>(R.id.btn_close_player)
        val albumArtCard = activity.findViewById<CardView>(R.id.cv_album_art)

        // Apply consistent padding to maintain positions
        closeButton?.apply {
            (layoutParams as ConstraintLayout.LayoutParams).topMargin =
                statusBarHeight + resources.getDimensionPixelSize(R.dimen.default_padding)
        }

        albumArtCard?.apply {
            (layoutParams as ConstraintLayout.LayoutParams).topMargin =
                resources.getDimensionPixelSize(R.dimen.album_art_top_margin)
        }
    }

    private fun makeStatusBarTransparent(activity: MainActivity) {
        activity.window.apply {
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun resetStatusBar(activity: MainActivity) {
        val defaultColor = ContextCompat.getColor(context, R.color.background_color)
        activity.window.apply {
            statusBarColor = defaultColor
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    }

    // Add this method to handle visibility changes
    fun handlePlayerVisibilityChange(activity: MainActivity, isPlayerVisible: Boolean) {
        if (isPlayerVisible) {
            // Always set the layout to handle insets consistently
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)

            val useBlurBackground = prefs.getBoolean("useBlurBackground", false)
            if (useBlurBackground) {
                makeStatusBarTransparent(activity)
                val albumArtImageView = activity.findViewById<ImageView>(R.id.iv_album_art)
                if (albumArtImageView?.drawable != null) {
                    updateBackgroundWithAlbumArt(activity, albumArtImageView)
                }
            } else {
                // For solid color mode
                val color = getSavedColor()
                applySolidColorMode(activity, color)
            }

            // Apply consistent padding for both modes
            applyConsistentLayout(activity)
        } else {
            resetStatusBar(activity)
        }
    }


    private fun createBlurredBackground(bitmap: Bitmap): Bitmap {
        // Scale down the bitmap for better performance
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 4, bitmap.height / 4, true)

        val renderScript = RenderScript.create(context)
        val input = Allocation.createFromBitmap(renderScript, scaledBitmap)
        val output = Allocation.createTyped(renderScript, input.type)

        // Create blur effect
        val blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        blurScript.setInput(input)
        blurScript.setRadius(25f) // Adjust blur radius (1-25)
        blurScript.forEach(output)

        // Create output bitmap
        val blurredBitmap = Bitmap.createBitmap(scaledBitmap.width, scaledBitmap.height, scaledBitmap.config)
        output.copyTo(blurredBitmap)

        // Scale back up to original size
        val finalBitmap = Bitmap.createScaledBitmap(blurredBitmap, bitmap.width, bitmap.height, true)

        // Apply darker overlay for better text visibility
        val overlay = Color.argb(160, 0, 0, 0) // Increased alpha for better text contrast
        val canvas = Canvas(finalBitmap)
        canvas.drawColor(overlay)

        // Clean up
        renderScript.destroy()
        scaledBitmap.recycle()
        blurredBitmap.recycle()

        return finalBitmap
    }

    fun applyColorToActivity(activity: MainActivity) {
        val color = getSavedColor()
        val useBlurBackground = prefs.getBoolean("useBlurBackground", false)
        val playerViewContainer = activity.findViewById<View>(R.id.player_view_container)

        if (!useBlurBackground) {
            applySolidColorMode(activity, color)
        }

        // Always maintain consistent layout
        applyConsistentLayout(activity)

        val albumArtImageView = activity.findViewById<ImageView>(R.id.iv_album_art)

        if (useBlurBackground && albumArtImageView?.drawable != null && playerViewContainer?.visibility == View.VISIBLE) {
            updateBackgroundWithAlbumArt(activity, albumArtImageView)
        } else {
            playerViewContainer?.setBackgroundColor(color)
            if (playerViewContainer?.visibility == View.VISIBLE) {
                makeStatusBarTransparent(activity)
            } else {
                resetStatusBar(activity)
            }
        }

        // Text color based on background color
        val primaryTextColor = if (color == Color.WHITE) {
            Color.BLACK // Black text for white background
        } else if (color == Color.BLACK) {
            Color.WHITE // White text for black background
        } else {
            ContextCompat.getColor(activity, R.color.primary_text) // Default primary text color
        }

        val seekBar = activity.findViewById<SeekBar>(R.id.seek_bar)
        if (color == Color.WHITE) {
            seekBar?.apply {
                progressTintList = ColorStateList.valueOf(Color.BLACK)
                thumbTintList = ColorStateList.valueOf(Color.BLACK)
            }
        } else {
            seekBar?.apply {
                progressTintList = ColorStateList.valueOf(Color.WHITE)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
            }
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