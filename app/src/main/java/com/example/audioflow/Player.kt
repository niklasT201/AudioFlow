package com.example.audioflow

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class Player (private val context: Context){

    var mediaPlayer: MediaPlayer? = null
    var currentSongIndex: Int = -1

    fun playSong(position: Int, currentPlaylist: List<SongItem>, onSongPrepared: (SongItem) -> Unit) {
        if (position < 0 || position >= currentPlaylist.size) return

        currentSongIndex = position
        val song = currentPlaylist[position]

        try {
            if (!song.file.exists()) {
                throw FileNotFoundException("The audio file does not exist")
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                song.file
            )
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(context, uri)

            if (mediaPlayer == null) {
                throw IOException("Failed to create MediaPlayer for this file")
            }

            mediaPlayer?.start()

            onSongPrepared(song)

        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException, is IOException -> {
                    Log.e("AudioFlow", "Error playing song: ${e.message}", e)
                    Toast.makeText(context, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.e("AudioFlow", "Unexpected error playing song", e)
                    Toast.makeText(context, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}