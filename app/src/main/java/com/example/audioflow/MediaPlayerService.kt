package com.example.audioflow

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import java.io.File
import android.graphics.*
import android.graphics.drawable.BitmapDrawable

interface MediaPlayerCallback {
    fun onNextTrack()
    fun onPreviousTrack()
}

class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val binder = LocalBinder()
    private var currentSong: MainActivity.SongItem? = null
    private var isPlayerInitialized = false
    private var callback: MediaPlayerCallback? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AudioFlowMediaChannel"
        private const val REQUEST_CODE = 100
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    fun setCallback(callback: MediaPlayerCallback) {
        this.callback = callback
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeMediaSession()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AudioFlow Media Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for AudioFlow media player"
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "AudioFlowSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    mediaPlayer?.start()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    updateNotification()
                }

                override fun onPause() {
                    mediaPlayer?.pause()
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification()
                }

                override fun onSkipToNext() {
                    callback?.onNextTrack()
                }

                override fun onSkipToPrevious() {
                    callback?.onPreviousTrack()
                }
            })
        }
    }

    fun initializePlayer(mediaPlayer: MediaPlayer) {
        this.mediaPlayer = mediaPlayer
        isPlayerInitialized = true
        updatePlaybackState(if (mediaPlayer.isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED
        )
    }

    fun updateMetadata(song: MainActivity.SongItem) {
        currentSong = song
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(song.file.absolutePath)
            val art = retriever.embeddedPicture
            val albumArt = if (art != null) {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4  // Scale down the image more aggressively
                }
                BitmapFactory.decodeByteArray(art, 0, art.size, options)
            } else {
                BitmapFactory.decodeResource(resources, R.drawable.cover_art)
            }

            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .build()

            mediaSession.setMetadata(metadata)
            updateNotification()
        } finally {
            retriever.release()
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun getRoundedBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        val roundPx = 120f // Adjust this value to control the corner radius

        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    private fun updateNotification() {
        if (!isPlayerInitialized || currentSong == null) return

        val remoteViews = RemoteViews(packageName, R.layout.custom_notification_layout)

        // Set the album art
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(currentSong?.file?.absolutePath)
            val art = retriever.embeddedPicture
            val albumArt = if (art != null) {
                val originalBitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                getRoundedBitmap(originalBitmap)
            } else {
                val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.cover_art)
                getRoundedBitmap(originalBitmap)
            }
            remoteViews.setImageViewBitmap(R.id.notification_icon, albumArt)
        } catch (e: Exception) {
            val fallbackBitmap = BitmapFactory.decodeResource(resources, R.drawable.cover_art)
            remoteViews.setImageViewBitmap(R.id.notification_icon, getRoundedBitmap(fallbackBitmap))
        }

        // Rest of the notification setup remains the same
        remoteViews.setTextViewText(R.id.notification_title, currentSong?.title)
        remoteViews.setTextViewText(R.id.notification_text, currentSong?.artist)

        // Set button images
        remoteViews.setImageViewResource(R.id.previous_button, R.drawable.previous)
        remoteViews.setImageViewResource(R.id.next_button, R.drawable.next)
        remoteViews.setImageViewResource(
            R.id.play_button,
            if (mediaPlayer?.isPlaying == true) R.drawable.pause_button else R.drawable.play_button
        )

        // Create pending intents for buttons
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(if (mediaPlayer?.isPlaying == true) "com.example.audioflow.PAUSE" else "com.example.audioflow.PLAY"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent("com.example.audioflow.NEXT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent("com.example.audioflow.PREVIOUS"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set click listeners
        remoteViews.setOnClickPendingIntent(R.id.play_button, playPauseIntent)
        remoteViews.setOnClickPendingIntent(R.id.next_button, nextIntent)
        remoteViews.setOnClickPendingIntent(R.id.previous_button, previousIntent)

        // Create the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_icon)
            .setCustomContentView(remoteViews)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }
}