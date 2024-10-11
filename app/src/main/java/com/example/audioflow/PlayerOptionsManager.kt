package com.example.audioflow

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PlayerOptionsManager(
    private val activity: MainActivity,  // Change this line
    private val overlay: View,
    private val currentSongTitle: TextView,
    private val playbackSpeedSeekBar: SeekBar,
    private val playbackSpeedText: TextView,
    private val mediaPlayer: MediaPlayer?,
    private val colorManager: ColorManager,
    private val coverStyleCustomizer: CoverStyleCustomizer?
) {

    init {
        setupPlayerOptionsOverlay()
    }

    private fun setupPlayerOptionsOverlay() {
        // Setup player settings button click listener
        activity.findViewById<ImageView>(R.id.btn_player_settings).setOnClickListener {
            showOverlay()
        }

        // Setup close button
        activity.findViewById<Button>(R.id.btn_close_overlay).setOnClickListener {
            hideOverlay()
        }

        // Setup playback speed control
        setupPlaybackSpeedControl()

        // Setup other option items
        setupOptionItems()
    }

    private fun setupPlaybackSpeedControl() {
        playbackSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress / 100f
                playbackSpeedText.text = String.format("%.1fx", speed)
                mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed)
                    ?: PlaybackParams().setSpeed(speed)
                activity.updatePlayPauseButton()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupOptionItems() {
        // Rename file option
        activity.findViewById<LinearLayout>(R.id.item_rename_file).setOnClickListener {
            showRenameDialog()
        }

        // Delete file option
        activity.findViewById<LinearLayout>(R.id.item_delete_file).setOnClickListener {
            showDeleteConfirmationDialog()
        }

        // Change color option
        activity.findViewById<LinearLayout>(R.id.item_change_color).setOnClickListener {
            colorManager.showColorSelectionDialog()
        }

        // Add timer option
        activity.findViewById<LinearLayout>(R.id.item_add_timer).setOnClickListener {
            activity.settingsManager.showTimerDialog()
        }

        // Customize player option
        activity.findViewById<LinearLayout>(R.id.item_customize_player).setOnClickListener {
            (activity as? Activity)?.showCoverStyleCustomization()
        }

        // Edit metadata option
        activity.findViewById<LinearLayout>(R.id.item_edit_metadata)?.setOnClickListener {
            try {
                val intent = Intent(activity, EditMetadataActivity::class.java)
                intent.putExtra("songPath", activity.getCurrentSongPath())
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.e("EditMetadata", "Error launching EditMetadataActivity: ${e.message}")
                Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameDialog() {
        val themedContext = ContextThemeWrapper(activity, R.style.CustomMaterialDialogTheme)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_rename, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = textInputLayout.editText!!

        editText.setText(activity.getCurrentSongTitle())

        MaterialAlertDialogBuilder(themedContext)
            .setTitle("Rename File")
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    activity.renameSongFile(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog() {
        val themedContext = ContextThemeWrapper(activity, R.style.CustomMaterialDialogTheme)
        MaterialAlertDialogBuilder(themedContext)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete this file? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                activity.deleteCurrentSong()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        currentSongTitle.text = activity.getCurrentSongTitle()
    }

    fun hideOverlay() {
        overlay.visibility = View.GONE
    }

    fun isOverlayVisible(): Boolean {
        return overlay.visibility == View.VISIBLE
    }
}