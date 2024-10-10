//Implement this shit

package com.example.audioflow

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.IBinder
import android.util.Log
import androidx.core.content.FileProvider
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayMode: PlayMode = PlayMode.NORMAL
    private var isShuffleMode = false
    private var originalPlaylist: List<SongItem> = emptyList()
    private var currentPlaylist: List<SongItem> = emptyList()
    private var currentSongIndex: Int = -1
    private var resetPreviousEnabled = false
    private var lastPlayedSong: SongItem? = null
    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false

    // Interfaces for callbacks
    interface PlaybackCallback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onSongChanged(song: SongItem)
        fun onPlaybackProgressChanged(progress: Float)
        fun onDurationChanged(duration: Int)
        fun onCurrentTimeChanged(position: Int)
    }

    private var playbackCallback: PlaybackCallback? = null

    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            bound = true

            // Initialize the service with the current MediaPlayer if it exists
            mediaPlayer?.let { mediaPlayerService?.initializePlayer(it) }
            //  lastPlayedSong?.let { mediaPlayerService?.updateMetadata(it) }

            mediaPlayerService?.setCallback(object : MediaPlayerCallback {
                override fun onNextTrack() {
                    playNextSong()
                }
                override fun onPreviousTrack() {
                    playPreviousSong()
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlayerService = null
            bound = false
        }
    }

    init {
        // Bind to MediaPlayerService
        Intent(context, MediaPlayerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        restorePlayMode()
    }

    fun setPlaybackCallback(callback: PlaybackCallback) {
        this.playbackCallback = callback
    }

    fun setCurrentPlaylist(playlist: List<SongItem>) {
        currentPlaylist = playlist
        originalPlaylist = playlist.toList()
    }

    fun getCurrentSong(): SongItem? =
        if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            currentPlaylist[currentSongIndex]
        } else null

    fun playSong(position: Int) {
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

            setupMediaPlayerCompletionListener()

            lastPlayedSong = song
            playbackCallback?.onSongChanged(song)
            playbackCallback?.onPlaybackStateChanged(true)
            playbackCallback?.onDurationChanged(mediaPlayer?.duration ?: 0)

            mediaPlayerService?.initializePlayer(mediaPlayer!!)
            //mediaPlayerService?.updateMetadata(song)

            startProgressUpdates()

        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException, is IOException -> {
                    Log.e("AudioPlayer", "Error playing song: ${e.message}", e)
                    removeCurrentSongAndPlayNext()
                }
                else -> {
                    Log.e("AudioPlayer", "Unexpected error playing song", e)
                }
            }
        }
    }

    private fun setupMediaPlayerCompletionListener() {
        mediaPlayer?.setOnCompletionListener {
            when (currentPlayMode) {
                PlayMode.NORMAL -> {
                    if (currentSongIndex < currentPlaylist.size - 1) {
                        playNextSong()
                    } else {
                        mediaPlayer?.pause()
                        playbackCallback?.onPlaybackStateChanged(false)
                    }
                }
                PlayMode.REPEAT_ALL -> playNextSong()
                PlayMode.REPEAT_ONE -> {
                    mediaPlayer?.start()
                    playbackCallback?.onPlaybackStateChanged(true)
                }
                PlayMode.SHUFFLE -> playRandomSong()
            }
        }
    }

    fun togglePlayPause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.start()
                    setupMediaPlayerCompletionListener()
                    startProgressUpdates()
                }
                playbackCallback?.onPlaybackStateChanged(player.isPlaying)
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error toggling play/pause: ${e.message}", e)
        }
    }

    fun playNextSong() {
        if (currentPlaylist.isEmpty()) return

        when (currentPlayMode) {
            PlayMode.NORMAL, PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE -> {
                currentSongIndex = (currentSongIndex + 1) % currentPlaylist.size
                playSong(currentSongIndex)
            }
            PlayMode.SHUFFLE -> {
                currentSongIndex = (0 until currentPlaylist.size).random()
                playSong(currentSongIndex)
            }
        }
    }

    fun playPreviousSong() {
        if (currentPlaylist.isEmpty()) return

        val currentPosition = mediaPlayer?.currentPosition ?: 0
        val totalDuration = mediaPlayer?.duration ?: 0

        if (resetPreviousEnabled && currentPosition > 10000 && totalDuration > 30000) {
            mediaPlayer?.seekTo(0)
            playbackCallback?.onCurrentTimeChanged(0)
        } else {
            when (currentPlayMode) {
                PlayMode.NORMAL -> {
                    if (currentSongIndex > 0) {
                        currentSongIndex--
                        playSong(currentSongIndex)
                    }
                }
                else -> {
                    currentSongIndex = if (currentSongIndex > 0) currentSongIndex - 1 else currentPlaylist.size - 1
                    playSong(currentSongIndex)
                }
            }
        }
    }

    private fun playRandomSong() {
        if (currentPlaylist.isEmpty()) return
        val randomIndex = (currentSongIndex + 1 + Random().nextInt(currentPlaylist.size - 1)) % currentPlaylist.size
        playSong(randomIndex)
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        playbackCallback?.onCurrentTimeChanged(position)
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun setPlayMode(mode: PlayMode) {
        currentPlayMode = mode
        isShuffleMode = mode == PlayMode.SHUFFLE

        when (mode) {
            PlayMode.NORMAL, PlayMode.REPEAT_ALL -> {
                mediaPlayer?.isLooping = false
                if (isShuffleMode) {
                    shufflePlaylist()
                } else {
                    restoreOriginalPlaylist()
                }
            }
            PlayMode.REPEAT_ONE -> {
                mediaPlayer?.isLooping = true
                restoreOriginalPlaylist()
            }
            PlayMode.SHUFFLE -> {
                mediaPlayer?.isLooping = false
                shufflePlaylist()
            }
        }

        savePlayMode()
    }

    fun getPlayMode(): PlayMode = currentPlayMode

    private fun shufflePlaylist() {
        if (!isShuffleMode) {
            isShuffleMode = true
            originalPlaylist = currentPlaylist.toList()
            val currentSong = currentPlaylist[currentSongIndex]
            currentPlaylist = currentPlaylist.shuffled()
            currentSongIndex = currentPlaylist.indexOf(currentSong)
        }
    }

    private fun restoreOriginalPlaylist() {
        if (isShuffleMode) {
            isShuffleMode = false
            val currentSong = currentPlaylist[currentSongIndex]
            currentPlaylist = originalPlaylist.toList()
            currentSongIndex = currentPlaylist.indexOf(currentSong)
        }
    }

    private fun removeCurrentSongAndPlayNext() {
        if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            currentPlaylist = currentPlaylist.filterIndexed { index, _ -> index != currentSongIndex }
            if (currentPlaylist.isNotEmpty()) {
                currentSongIndex = currentSongIndex.coerceAtMost(currentPlaylist.size - 1)
                playSong(currentSongIndex)
            } else {
                mediaPlayer?.release()
                mediaPlayer = null
                lastPlayedSong = null
                playbackCallback?.onPlaybackStateChanged(false)
            }
        }
    }

    private fun startProgressUpdates() {
        Thread {
            while (mediaPlayer?.isPlaying == true) {
                try {
                    val progress = getCurrentPosition().toFloat() / getDuration().toFloat()
                    playbackCallback?.onPlaybackProgressChanged(progress)
                    playbackCallback?.onCurrentTimeChanged(getCurrentPosition())
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Error updating progress: ${e.message}", e)
                }
            }
        }.start()
    }

    private fun savePlayMode() {
        val sharedPreferences = context.getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("currentPlayMode", currentPlayMode.name)
            apply()
        }
    }

    private fun restorePlayMode() {
        val sharedPreferences = context.getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        val savedPlayMode = sharedPreferences.getString("currentPlayMode", PlayMode.NORMAL.name)
        currentPlayMode = PlayMode.valueOf(savedPlayMode ?: PlayMode.NORMAL.name)
    }

    fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed) ?: PlaybackParams().setSpeed(speed)
    }

    fun cleanup() {
        if (bound) {
            context.unbindService(serviceConnection)
            bound = false
        }
        mediaPlayer?.release()
        mediaPlayer = null
        playbackCallback = null
    }

    enum class PlayMode {
        NORMAL, REPEAT_ALL, REPEAT_ONE, SHUFFLE
    }
}