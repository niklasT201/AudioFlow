package com.example.audioflow

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SongOptionsHandler(
    private val activity: AppCompatActivity,
    private val songOptionsFooter: LinearLayout,
    private val songListView: ListView,
    private val playerOptionsManager: PlayerOptionsManager,
    private val searchActivity: SearchActivity? = null
) {
    private var selectedPosition: Int = -1

    init {
        setupSongOptions()
    }

    private fun setupSongOptions() {
        songListView.setOnItemLongClickListener { _, _, position, _ ->
            selectedPosition = position
            showFooter()
            true
        }

        // Play song option
        songOptionsFooter.findViewById<View>(R.id.play_song_icon).setOnClickListener {
            if (selectedPosition != -1) {
                (activity as? MainActivity)?.playSong(selectedPosition)
                hideFooter()
            }
        }

        // Edit song option
        songOptionsFooter.findViewById<View>(R.id.edit_song_icon).setOnClickListener {
            if (selectedPosition != -1) {
                try {
                    val intent = Intent(activity, EditMetadataActivity::class.java)
                    intent.putExtra("songPath", (activity as? MainActivity)?.getCurrentSongPath())
                    activity.startActivity(intent)
                    hideFooter()
                } catch (e: Exception) {
                    Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Add to playlist option
        songOptionsFooter.findViewById<View>(R.id.add_to_playlist_icon).setOnClickListener {
            if (selectedPosition != -1) {
                val song = (activity as? MainActivity)?.currentPlaylist?.get(selectedPosition)
                song?.let {
                    val convertedSong = AudioMetadataRetriever.SongItem(it.file, it.title, it.artist, it.album)
                    searchActivity?.showAddToPlaylistDialog(convertedSong) ?:
                    Toast.makeText(activity, "Add to playlist not available", Toast.LENGTH_SHORT).show()
                }
                hideFooter()
            }
        }

        // Delete song option
        songOptionsFooter.findViewById<View>(R.id.delete_song_icon).setOnClickListener {
            if (selectedPosition != -1) {
                showDeleteConfirmationDialog()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        (activity as? MainActivity)?.let {
            playerOptionsManager.showDeleteConfirmationDialog()
            hideFooter()
        }
    }

    private fun showFooter() {
        songOptionsFooter.visibility = View.VISIBLE
    }

    fun hideFooter() {
        songOptionsFooter.visibility = View.GONE
    }
}