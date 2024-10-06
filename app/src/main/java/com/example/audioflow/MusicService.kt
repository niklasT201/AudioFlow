package com.example.audioflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MusicService : androidx.lifecycle.LifecycleService(), AudioManager.OnAudioFocusChangeListener {
    private var mediaSession: MediaSessionCompat? = null
    private var mediaPlayer: MediaPlayer? = null
    private val binder = LocalBinder()
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest

    inner class LocalBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize AudioManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setOnAudioFocusChangeListener(this)
                .build()
        }

        createNotificationChannel()
        initializeMediaSession()
    }

    private fun initializeMediaSession() {
        // Create a PendingIntent for launching the MainActivity
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            setSessionActivity(pendingIntent)  // Set the activity to launch
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS or
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (requestAudioFocus()) {
                        mediaPlayer?.start()
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    }
                }

                override fun onPause() {
                    mediaPlayer?.pause()
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    abandonAudioFocus()
                }

                override fun onSkipToNext() {
                    // Implement your skip to next logic here
                    updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
                }

                override fun onSkipToPrevious() {
                    // Implement your skip to previous logic here
                    updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS)
                }

                override fun onSeekTo(pos: Long) {
                    mediaPlayer?.seekTo(pos.toInt())
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }

                override fun onSetRating(rating: RatingCompat) {
                    // Implement your rating (like/unlike) logic here
                }
            })

            isActive = true
        }
        updateNotification()
    }



    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                mediaSession?.controller?.transportControls?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mediaPlayer?.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.start()
            }
        }
    }

    fun setMediaPlayer(player: MediaPlayer) {
        mediaPlayer = player
        updatePlaybackState(if (player.isPlaying)
            PlaybackStateCompat.STATE_PLAYING
        else
            PlaybackStateCompat.STATE_PAUSED
        )
    }

    fun updateMetadata(
        title: String,
        artist: String,
        album: String? = null,
        duration: Long = 0L,
        artworkBitmap: Bitmap? = null
    ) {
        val metadata = MediaMetadataCompat.Builder().apply {
            // These are the key fields that trigger the system player
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            // This is especially important for the album art to show up
            artworkBitmap?.let {
                putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
            }
        }.build()

        mediaSession?.setMetadata(metadata)
        // Make sure the session is active
        mediaSession?.isActive = true
        updateNotification()
        Log.d("Metadata", "Title: $title, Artist: $artist, Album: $album")
    }

    fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L

        val playbackState = PlaybackStateCompat.Builder().apply {
            setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            setState(state, position, 1.0f)
        }.build()

        mediaSession?.setPlaybackState(playbackState)
        updateNotification()
    }

    fun getMediaSession(): MediaSessionCompat? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for the currently playing music"
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val controller = mediaSession?.controller ?: return
        val mediaMetadata = controller.metadata ?: return
        val playbackState = controller.playbackState ?: return

        val builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setContentTitle(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
            setContentText(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
            setSubText(mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
            setSmallIcon(R.drawable.cover_art)  // Your app's icon
            setLargeIcon(mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))

            // Add a "like" action
            addAction(
                R.drawable.no_favorite,
                "Like",
                createActionIntent(ACTION_TOGGLE_FAVORITE)
            )

            // Add transport controls
            addAction(R.drawable.previous, "Previous", createActionIntent(ACTION_PREVIOUS))

            val playPauseIcon = if (playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                R.drawable.pause_button else R.drawable.play_button
            val playPauseAction = if (playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                ACTION_PAUSE else ACTION_PLAY
            addAction(playPauseIcon, "Play/Pause", createActionIntent(playPauseAction))

            addAction(R.drawable.next, "Next", createActionIntent(ACTION_NEXT))

            // Set the MediaStyle
            setStyle(MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(1, 2, 3))  // Show previous, play/pause, next in compact view

            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOngoing(playbackState.state == PlaybackStateCompat.STATE_PLAYING)

            // Make the notification clickable to open the app
            setContentIntent(createContentIntent())
        }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Start with an initial notification immediately
        startForeground(NOTIFICATION_ID, createInitialNotification())

        when (intent?.action) {
            ACTION_PLAY -> mediaSession?.controller?.transportControls?.play()
            ACTION_PAUSE -> mediaSession?.controller?.transportControls?.pause()
            ACTION_NEXT -> mediaSession?.controller?.transportControls?.skipToNext()
            ACTION_PREVIOUS -> mediaSession?.controller?.transportControls?.skipToPrevious()
        }

        return START_STICKY
    }

    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Flow")
            .setContentText("Preparing...")
            .setSmallIcon(R.drawable.play_button)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession?.sessionToken))
            .build()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaPlayer?.release()
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "com.example.audioflow.MUSIC_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.example.audioflow.PLAY"
        const val ACTION_PAUSE = "com.example.audioflow.PAUSE"
        const val ACTION_NEXT = "com.example.audioflow.NEXT"
        const val ACTION_PREVIOUS = "com.example.audioflow.PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "com.example.audioflow.TOGGLE_FAVORITE"
    }
}