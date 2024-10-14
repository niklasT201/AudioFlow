package com.example.audioflow

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.text.Collator
import java.util.*

class AudioMetadataRetriever(private val contentResolver: ContentResolver) {

    private fun customSongSort(songs: List<SongItem>): List<SongItem> {
        val germanCollator = Collator.getInstance(Locale.GERMAN).apply {
            strength = Collator.PRIMARY
        }

        return songs.sortedWith(compareBy<SongItem> { song ->
            val title = song.title.lowercase(Locale.GERMAN)
            when {
                title.first().isDigit() -> "zzz$title"  // Move numbers to the end
                title.startsWith("ä") -> "a" + title.substring(1)
                title.startsWith("ö") -> "o" + title.substring(1)
                title.startsWith("ü") -> "u" + title.substring(1)
                else -> title.replace("ä", "a")
                    .replace("ö", "o")
                    .replace("ü", "u")
                    .replace("ß", "ss")
            }
        }.thenBy { germanCollator.getCollationKey(it.title) })
    }

    fun getSongsInFolder(folder: File): List<SongItem> {
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("${folder.absolutePath}%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val songs = mutableListOf<SongItem>()

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                var title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                var album = cursor.getString(albumColumn)
                val file = File(path)

                if (title.isNullOrEmpty() || artist.isNullOrEmpty() || album.isNullOrEmpty()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        title = title ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                        album = album ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
                    } catch (e: Exception) {
                        title = file.nameWithoutExtension
                        album = "Unknown Album"
                    } finally {
                        retriever.release()
                    }
                }

                songs.add(SongItem(file, title, artist ?: "Unknown Artist", album))
            }
        }

        return customSongSort(songs)
    }

    fun getAlbumArt(path: String): android.graphics.Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            if (art != null) {
                android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: IllegalArgumentException) {
            null
        } finally {
            retriever.release()
        }
    }

    fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "Unknown"
    }

    fun getMusicFolders(): List<File> {
        val musicFolders = mutableSetOf<File>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
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
                musicFolders.add(file.parentFile)
            }
        }

        return musicFolders.toList()
    }

    data class SongItem(val file: File, val title: String, val artist: String, val album: String)
}