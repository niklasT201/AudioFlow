package com.example.audioflow

import android.app.AlertDialog
import android.content.Intent
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SongOptionsHandler(
    private val activity: AppCompatActivity,
    private val songOptionsFooter: LinearLayout,
    private val songListView: ListView,
    private val playerOptionsManager: PlayerOptionsManager?
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
                    showAddToPlaylistDialog(convertedSong)
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

    private fun showAddToPlaylistDialog(song: AudioMetadataRetriever.SongItem) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_to_playlist, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderRecyclerView)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialog_button_cancel)

        titleTextView.text = "Add '${song.title}' to Playlist"

        val dialog = AlertDialog.Builder(activity, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(activity)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val audioFolders = getAudioFolders()
            withContext(Dispatchers.Main) {
                val adapter = FolderAdapter(audioFolders) { selectedFolder ->
                    dialog.dismiss()
                    confirmAddToPlaylist(song, selectedFolder)
                }
                recyclerView.adapter = adapter
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun getAudioFolders(): List<File> {
        val audioFolders = mutableSetOf<File>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATA} ASC"

        activity.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(columnIndex)
                val file = File(path)
                audioFolders.add(file.parentFile)
            }
        }

        return audioFolders.toList()
    }

    private fun confirmAddToPlaylist(song: AudioMetadataRetriever.SongItem, folder: File) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_confirm_add_to_playlist, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val yesButton = dialogView.findViewById<Button>(R.id.dialog_button_yes)
        val noButton = dialogView.findViewById<Button>(R.id.dialog_button_no)

        titleTextView.text = "Confirm"
        messageTextView.text = "Do you want to add '${song.title}' to '${folder.name}'?"

        val dialog = AlertDialog.Builder(activity, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .create()

        yesButton.setOnClickListener {
            dialog.dismiss()
            copySongToFolder(song, folder)
        }

        noButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun copySongToFolder(song: AudioMetadataRetriever.SongItem, folder: File) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val destFile = File(folder, song.file.name)
                song.file.copyTo(destFile, overwrite = true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Song added to playlist", Toast.LENGTH_SHORT).show()
                    refreshMediaStore(destFile.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Error adding song to playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshMediaStore(path: String) {
        MediaScannerConnection.scanFile(
            activity,
            arrayOf(path),
            null
        ) { _, uri ->
            activity.runOnUiThread {
                uri?.let {
                    activity.contentResolver.notifyChange(it, null)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        (activity as? MainActivity)?.let {
            playerOptionsManager?.showDeleteConfirmationDialog()
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