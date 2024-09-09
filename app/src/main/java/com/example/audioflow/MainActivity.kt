package com.example.audioflow


import android.media.MediaPlayer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.util.*
import android.media.MediaMetadataRetriever
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var playPauseButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var playButton: Button
    private lateinit var selectButton: Button
    private lateinit var listView: ListView
    private lateinit var songTitleTextView: TextView
    private lateinit var playerSongTitleTextView: TextView
    private lateinit var artistNameTextView: TextView
    private lateinit var albumArtImageView: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeTextView: TextView
    private lateinit var totalTimeTextView: TextView
    private var selectedSongUri: Uri? = null
    private var currentFolder: File? = null
    private var currentSongIndex: Int = -1
    private var currentSongs: List<SongItem> = emptyList()
    private lateinit var playerScreen: View
    private lateinit var listScreen: View

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val READ_EXTERNAL_STORAGE_REQUEST = 1
        private const val PICK_AUDIO_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupListeners()

        if (checkPermission()) {
            loadMusicFolders()
        } else {
            requestPermission()
        }
    }

    private fun initializeViews() {
        selectButton = findViewById(R.id.btn_select)
        listView = findViewById(R.id.list_view)
        songTitleTextView = findViewById(R.id.tv_song_title)
        playButton = findViewById(R.id.btn_play)
        artistNameTextView = findViewById(R.id.tv_artist_name)
        albumArtImageView = findViewById(R.id.iv_album_art)
        seekBar = findViewById(R.id.seek_bar)
        currentTimeTextView = findViewById(R.id.tv_current_time)
        totalTimeTextView = findViewById(R.id.tv_total_time)
        playPauseButton = findViewById(R.id.btn_play_pause)
        previousButton = findViewById(R.id.btn_previous)
        nextButton = findViewById(R.id.btn_next)
        playerScreen = findViewById(R.id.player_view_container)
        listScreen = findViewById(R.id.list_view_container)
        playerSongTitleTextView = findViewById(R.id.tv_player_song_title)

    }

    private fun setupListeners() {
        selectButton.setOnClickListener { openMusicSelector() }
        playButton.setOnClickListener { playMusic() }
        playPauseButton.setOnClickListener { togglePlayPause() }
        previousButton.setOnClickListener { playPreviousSong() }
        nextButton.setOnClickListener { playNextSong() }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            READ_EXTERNAL_STORAGE_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_EXTERNAL_STORAGE_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicFolders()
            } else {
                // Permission denied, handle this case (e.g., show a message to the user)
            }
        }
    }

    private fun loadMusicFolders() {
        val musicFolders = getMusicFolders()
        Log.d("AudioFlow", "Found ${musicFolders.size} music folders")
        if (musicFolders.isEmpty()) {
            Log.d("AudioFlow", "No music folders found")
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No music folders found"))
            return
        }

        val folderItems = musicFolders.map { folder ->
            val songCount = getSongsInFolder(folder).size
            FolderItem(folder, "${folder.name} ($songCount songs)")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, folderItems.map { it.displayName })
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            loadSongsInFolder(folderItems[position].folder)
        }
    }

    data class FolderItem(val folder: File, val displayName: String)

    private fun loadSongsInFolder(folder: File) {
        currentFolder = folder
        currentSongs = getSongsInFolder(folder)
        Log.d("AudioFlow", "Found ${currentSongs.size} songs in folder ${folder.name}")
        if (currentSongs.isEmpty()) {
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No songs found in this folder"))
            return
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, currentSongs.map { it.title })
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            playSong(position)
            showPlayerView()
        }
    }

    data class SongItem(val file: File, val title: String, val artist: String)

    private fun showPlayerView() {
        findViewById<View>(R.id.list_view_container).visibility = View.GONE
        findViewById<View>(R.id.player_view_container).visibility = View.VISIBLE
        supportActionBar?.hide()  // Hide the ActionBar
    }

    private fun showListView() {
        findViewById<View>(R.id.player_view_container).visibility = View.GONE
        findViewById<View>(R.id.list_view_container).visibility = View.VISIBLE
        supportActionBar?.show()  // Show the ActionBar
    }

    private fun getSongsInFolder(folder: File): List<SongItem> {
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.DATA + " LIKE ?"
        val selectionArgs = arrayOf("${folder.absolutePath}%")
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
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
            while (cursor.moveToNext()) {
                val path = cursor.getString(pathColumn)
                var title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val file = File(path)

                if (title.isNullOrEmpty() || artist.isNullOrEmpty()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(path)
                        title = title ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.nameWithoutExtension
                    } catch (e: Exception) {
                        Log.e("AudioFlow", "Error retrieving metadata for $path", e)
                        title = file.nameWithoutExtension
                    } finally {
                        retriever.release()
                    }
                }

                songs.add(SongItem(file, title, artist))
            }
        }

        return songs
    }

    private fun getMusicFolders(): List<File> {
        val musicFolders = mutableSetOf<File>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                val path = it.getString(columnIndex)
                val file = File(path)
                musicFolders.add(file.parentFile)
            }
        }

        Log.d("AudioFlow", "Found ${musicFolders.size} music folders")
        return musicFolders.toList()
    }

    private fun findMusicFoldersRecursively(dir: File, musicFolders: MutableList<File>) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    if (file.listFiles { it -> it.extension.lowercase(Locale.ROOT) == "mp3" }?.isNotEmpty() == true) {
                        musicFolders.add(file)
                    } else {
                        findMusicFoldersRecursively(file, musicFolders)
                    }
                }
            }
        }
    }

    private fun playSong(position: Int) {
        if (position < 0 || position >= currentSongs.size) return

        currentSongIndex = position
        val song = currentSongs[position]

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                song.file
            )
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, uri)
            mediaPlayer?.start()
            playButton.text = getString(R.string.pause)
            updatePlayerUI(song)
            showPlayerView()  // Show the player view and hide ActionBar

            songTitleTextView.text = song.title

            mediaPlayer?.setOnCompletionListener {
                playNextSong()
            }
        } catch (e: Exception) {
            Log.e("AudioFlow", "Error playing song", e)
            Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayerUI(song: SongItem) {
        findViewById<TextView>(R.id.tv_player_song_title).text = song.title
        songTitleTextView.text = song.title
        artistNameTextView.text = song.artist
        playPauseButton.setImageResource(android.R.drawable.ic_media_pause)

        // Update seek bar
        mediaPlayer?.let { player ->
            seekBar.max = player.duration
            seekBar.progress = 0
            updateSeekBar()
        }

        // Set album art
        val albumArt = getAlbumArt(song.file.absolutePath)
        if (albumArt != null) {
            albumArtImageView.setImageBitmap(albumArt)
        } else {
            albumArtImageView.setImageResource(R.drawable.default_album_art) // Make sure to add a default album art drawable
        }
    }

    private fun updateSeekBar() {
        mediaPlayer?.let { player ->
            seekBar.progress = player.currentPosition
            currentTimeTextView.text = formatTime(player.currentPosition)
            totalTimeTextView.text = formatTime(player.duration)

            if (player.isPlaying) {
                seekBar.postDelayed({ updateSeekBar() }, 1000)
            }
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun getAlbumArt(path: String): android.graphics.Bitmap? {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val art = retriever.embeddedPicture
        return if (art != null) {
            BitmapFactory.decodeByteArray(art, 0, art.size)
        } else {
            null
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                player.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                updateSeekBar()
            }
        }
    }

    private fun playPreviousSong() {
        if (currentSongIndex > 0) {
            playSong(currentSongIndex - 1)
        } else {
            // Optional: loop back to the last song
            playSong(currentSongs.size - 1)
        }
    }

    private fun openMusicSelector() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/*"
        startActivityForResult(intent, PICK_AUDIO_REQUEST)
    }

    private fun playNextSong() {
        if (currentSongIndex < currentSongs.size - 1) {
            playSong(currentSongIndex + 1)
        } else {
            // Optional: loop back to the first song
            playSong(0)
        }
    }

    private fun playMusic() {
        if (mediaPlayer == null) {
            if (currentSongIndex == -1 && currentSongs.isNotEmpty()) {
                playSong(0)
            } else {
                selectedSongUri?.let { uri ->
                    mediaPlayer = MediaPlayer.create(this, uri)
                    mediaPlayer?.start()
                    playButton.text = getString(R.string.pause)
                }
            }
        } else if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            playButton.text = getString(R.string.play)
        } else {
            mediaPlayer?.start()
            playButton.text = getString(R.string.pause)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedSongUri = uri
                songTitleTextView.text = getFileName(uri)
                playButton.isEnabled = true
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "Unknown"
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}