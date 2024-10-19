package com.example.audioflow

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.audioflow.AudioMetadataRetriever.SongItem

class SongOptionsDialog(private val context: Context) {

    fun show(song: SongItem, position: Int, onPlaySelected: (Int) -> Unit, onPlaylistUpdated: () -> Unit, onAddToPlaylist: (SongItem) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_song_options, null)
        val dialog = Dialog(context)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set up the dialog components
        val titleTextView = dialogView.findViewById<TextView>(R.id.current_song_title)
        titleTextView.text = song.title

        // Set up click listeners for each option
        setupOptionItem(dialogView, R.id.item_play) { onPlaySelected(position); dialog.dismiss() }
        setupOptionItem(dialogView, R.id.item_add_to_playlist) { onAddToPlaylist(song); dialog.dismiss() }
        setupOptionItem(dialogView, R.id.item_share) { shareSong(song); dialog.dismiss() }
        setupOptionItem(dialogView, R.id.item_rename_file) { showRenameDialog(song, onPlaylistUpdated); dialog.dismiss() }
        setupOptionItem(dialogView, R.id.item_delete_file) { showDeleteConfirmation(song, position, onPlaylistUpdated); dialog.dismiss() }

        // Set up close button
        val closeButton = dialogView.findViewById<Button>(R.id.btn_close_overlay)
        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun setupOptionItem(dialogView: View, itemId: Int, onClick: () -> Unit) {
        dialogView.findViewById<LinearLayout>(itemId).setOnClickListener { onClick() }
    }

    private fun shareSong(song: SongItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                song.file
            ))
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share song"))
    }

    private fun showRenameDialog(song: SongItem, onPlaylistUpdated: () -> Unit) {
        // Implement rename dialog here
        // After renaming, call onPlaylistUpdated()
    }

    private fun showDeleteConfirmation(song: SongItem, position: Int, onPlaylistUpdated: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Delete Song")
            .setMessage("Are you sure you want to delete ${song.title}?")
            .setPositiveButton("Yes") { _, _ ->
                if (song.file.delete()) {
                    onPlaylistUpdated()
                    Toast.makeText(context, "${song.title} deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete ${song.title}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}