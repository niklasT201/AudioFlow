package com.example.audioflow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.AsyncTask
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference

class LoadAlbumArtTask(
    imageView: ImageView,
    private val albumArtCache: LruCache<String, Bitmap>
) : AsyncTask<String, Void, Bitmap?>() {

    private val imageViewReference: WeakReference<ImageView> = WeakReference(imageView)

    override fun doInBackground(vararg params: String): Bitmap? {
        val filePath = params[0]

        // Check if the bitmap is in the cache
        albumArtCache.get(filePath)?.let { return it }

        val mediaMetadataRetriever = MediaMetadataRetriever()
        try {
            mediaMetadataRetriever.setDataSource(filePath)
            val albumArt = mediaMetadataRetriever.embeddedPicture
            if (albumArt != null) {
                val bitmap = BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
                // Store the bitmap in the cache
                albumArtCache.put(filePath, bitmap)
                return bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaMetadataRetriever.release()
        }
        return null
    }

    override fun onPostExecute(result: Bitmap?) {
        val imageView = imageViewReference.get()
        if (imageView != null) {
            if (result != null) {
                imageView.setImageBitmap(result)
            } else {
                imageView.setImageResource(R.drawable.cover_art)  // Set default if no album art
            }
        }
    }
}