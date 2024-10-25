package com.example.audioflow

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.audioflow.AudioMetadataRetriever.SongItem
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SongOptionsDialog(private val context: Context) {

    fun show(
        song: SongItem,
        position: Int,
        onPlaySelected: (Int) -> Unit,
        onPlaylistUpdated: () -> Unit,
        onAddToFavorites: (SongItem) -> Unit = {},
        onShowAlbum: (String) -> Unit = {},
        onShowArtist: (String) -> Unit = {}
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_song_options, null)
        val dialog = Dialog(context)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set the dialog's dimensions
        val displayMetrics = context.resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        val height = (displayMetrics.heightPixels * 0.8).toInt()
        dialog.window?.setLayout(width, height)

        // Set up the dialog components
        val titleTextView = dialogView.findViewById<TextView>(R.id.current_song_title)
        titleTextView.text = song.title

        // Set up click listeners for each option
        setupOptionItem(dialogView, R.id.item_play) {
            onPlaySelected(position)
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_add_to_playlist) {
            showAddToPlaylistDialog(song)
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_add_to_favorites) {
            val favoriteManager = FavoriteManager(context)
            if (favoriteManager.isFavorite(song)) {
                favoriteManager.removeFavorite(song)
                Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
            } else {
                favoriteManager.addFavorite(song)
                Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
            }

            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_edit_metadata) {
            launchMetadataEditor(song)
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_show_album) {
            Toast.makeText(context, "This feature is not ready and needs a bit more time for development", Toast.LENGTH_LONG).show()
            onShowAlbum(song.album ?: "Unknown Album")
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_show_artist) {
            Toast.makeText(context, "This feature is not ready and needs a bit more time for development", Toast.LENGTH_LONG).show()
            onShowArtist(song.artist ?: "Unknown Artist")
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_share) {
            shareSong(song)
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_rename_file) {
            showRenameDialog(song, onPlaylistUpdated)
            dialog.dismiss()
        }

        setupOptionItem(dialogView, R.id.item_delete_file) {
            showDeleteConfirmation(song, position, onPlaylistUpdated)
            dialog.dismiss()
        }

        // Set up close button
        val closeButton = dialogView.findViewById<Button>(R.id.btn_close_overlay)
        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun setupOptionItem(dialogView: View, itemId: Int, onClick: () -> Unit) {
        dialogView.findViewById<LinearLayout>(itemId)?.setOnClickListener { onClick() }
    }

    private fun launchMetadataEditor(song: SongItem) {
        try {
            val intent = Intent(context, EditMetadataActivity::class.java)
            intent.putExtra("songPath", song.file.absolutePath)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSong(song: SongItem) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    song.file
                ))
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share song"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog(song: SongItem, onPlaylistUpdated: () -> Unit) {
        val themedContext = ContextThemeWrapper(context, R.style.CustomMaterialDialogTheme)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_rename, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = textInputLayout.editText!!

        editText.setText(song.title)

        MaterialAlertDialogBuilder(themedContext)
            .setTitle("Rename Song")
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    try {
                        // Implement rename logic
                        val oldFile = song.file
                        val newFile = File(oldFile.parentFile, "$newName.${oldFile.extension}")

                        if (oldFile.renameTo(newFile)) {
                            // Update the SongItem
                            val updatedSong = song.copy(file = newFile, title = newName)

                            // Update the playlist in MainActivity
                            (context as? MainActivity)?.let { activity ->
                                val index = activity.currentPlaylist.indexOf(song)
                                if (index != -1) {
                                    val updatedPlaylist = activity.currentPlaylist.toMutableList()
                                    updatedPlaylist[index] = updatedSong
                                    activity.currentPlaylist = updatedPlaylist
                                }

                                // If this is the currently playing song, update lastPlayedSong
                                if (activity.lastPlayedSong == song) {
                                    activity.lastPlayedSong = updatedSong
                                    activity.updatePlayerUI(updatedSong)
                                    activity.updateMiniPlayer(updatedSong)
                                }
                            }

                            onPlaylistUpdated()
                            Toast.makeText(context, "Song renamed successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to rename song", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("AudioFlow", "Error renaming song: ${e.message}", e)
                        Toast.makeText(context, "Error renaming: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(song: SongItem, position: Int, onPlaylistUpdated: () -> Unit) {
        val themedContext = ContextThemeWrapper(context, R.style.CustomMaterialDialogTheme)
        MaterialAlertDialogBuilder(themedContext)
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete ${song.title}?")
            .setPositiveButton("Yes") { _, _ ->
                try {
                    if (song.file.delete()) {
                        onPlaylistUpdated()
                        Toast.makeText(context, "${song.title} deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to delete ${song.title}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: SongItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_to_playlist, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderRecyclerView)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialog_button_cancel)

        titleTextView.text = "Add '${song.title}' to Playlist"

        val dialog = AlertDialog.Builder(context, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(context)

        (context as? MainActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
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

        context.contentResolver.query(
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

    private fun confirmAddToPlaylist(song: SongItem, folder: File) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_add_to_playlist, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val yesButton = dialogView.findViewById<Button>(R.id.dialog_button_yes)
        val noButton = dialogView.findViewById<Button>(R.id.dialog_button_no)

        titleTextView.text = "Confirm"
        messageTextView.text = "Do you want to add '${song.title}' to '${folder.name}'?"

        val dialog = AlertDialog.Builder(context, R.style.TransparentAlertDialog)
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

    private fun copySongToFolder(song: SongItem, folder: File) {
        (context as? MainActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
            try {
                val destFile = File(folder, song.file.name)
                song.file.copyTo(destFile, overwrite = true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Song added to playlist", Toast.LENGTH_SHORT).show()
                    refreshMediaStore(destFile.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error adding song to playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshMediaStore(path: String) {
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = Uri.fromFile(File(path))
        context.sendBroadcast(intent)
    }
}