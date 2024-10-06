package com.example.audioflow

import android.app.*
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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import java.io.File

class MediaPlayerService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val binder = LocalBinder()
    private var currentSong: MainActivity.SongItem? = null
    private var isPlayerInitialized = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MediaPlayerService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
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
            "Media Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media player controls"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
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
                    (applicationContext as? MainActivity)?.playNextSong(false)
                }

                override fun onSkipToPrevious() {
                    (applicationContext as? MainActivity)?.playPreviousSong()
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
                BitmapFactory.decodeByteArray(art, 0, art.size)
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

    private fun updateNotification() {
        if (!isPlayerInitialized || currentSong == null) return

        val retriever = MediaMetadataRetriever()
        var albumArt: Bitmap? = null

        try {
            retriever.setDataSource(currentSong?.file?.absolutePath)
            val art = retriever.embeddedPicture
            albumArt = if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                BitmapFactory.decodeResource(resources, R.drawable.cover_art)
            }
        } catch (e: Exception) {
            albumArt = BitmapFactory.decodeResource(resources, R.drawable.cover_art)
        } finally {
            retriever.release()
        }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.cover_art)
            .setLargeIcon(albumArt)
            .setContentTitle(currentSong?.title)
            .setContentText("${currentSong?.artist} - ${currentSong?.album}")
            .setStyle(style)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.previous,
                "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
            .addAction(
                if (mediaPlayer?.isPlaying == true) R.drawable.pause_button else R.drawable.play_button,
                if (mediaPlayer?.isPlaying == true) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    if (mediaPlayer?.isPlaying == true)
                        PlaybackStateCompat.ACTION_PAUSE
                    else
                        PlaybackStateCompat.ACTION_PLAY
                )
            )
            .addAction(
                R.drawable.next,
                "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
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