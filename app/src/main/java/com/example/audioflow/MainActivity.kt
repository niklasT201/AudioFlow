package com.example.audioflow


import android.media.MediaPlayer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.Context
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
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var playPauseButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var playButton: Button
    private lateinit var selectButton: Button
    private lateinit var songTitleTextView: TextView
    private lateinit var playerSongTitleTextView: TextView
    private lateinit var artistNameTextView: TextView
    private lateinit var closePlayerButton: ImageButton
    private lateinit var albumArtImageView: ImageView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeTextView: TextView
    private lateinit var totalTimeTextView: TextView

    private lateinit var playAllButton: Button
    private lateinit var songCountTextView: TextView

    private var selectedSongUri: Uri? = null
    private var currentFolder: File? = null
    private var currentSongIndex: Int = -1
    private var currentSongs: List<SongItem> = emptyList()
    private var currentPlaylist: List<SongItem> = emptyList()
    private lateinit var playerScreen: View
    private lateinit var homeScreen: View
    private lateinit var songsScreen: View
    private lateinit var settingsScreen: View
    private var folderItems: List<FolderItem> = emptyList()
    private lateinit var contentFrame: FrameLayout
    private lateinit var btnHome: Button
    private lateinit var btnSettings: Button

    private var lastPlayedSong: SongItem? = null
    private var currentFolderPath: String? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val READ_EXTERNAL_STORAGE_REQUEST = 1
        private const val PICK_AUDIO_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Initialize screens
        initializeViews()

        // Set up navigation
        setupNavigation()

        // Load music folders
        loadMusicFolders()

        setupSeekBar()

        // Show home screen by default
        showScreen(homeScreen)

        if (checkPermission()) {
            loadMusicFolders()
            restoreLastPlayedSong()
        } else {
            requestPermission()
        }

        setupFooter()
    }

    private fun initializeViews() {
        contentFrame = findViewById(R.id.content_frame)
        val footer = findViewById<View>(R.id.footer)
        btnHome = footer.findViewById(R.id.btn_home)
        btnSettings = footer.findViewById(R.id.btn_settings)

        homeScreen = layoutInflater.inflate(R.layout.home_screen, contentFrame, false)
        songsScreen = layoutInflater.inflate(R.layout.songs_screen, contentFrame, false)
        settingsScreen = layoutInflater.inflate(R.layout.settings_screen, contentFrame, false)
        playerScreen = findViewById(R.id.player_view_container)

        playerSongTitleTextView = findViewById(R.id.tv_player_song_title)
        artistNameTextView = findViewById(R.id.tv_artist_name)
        playPauseButton = findViewById(R.id.btn_play_pause)
        seekBar = findViewById(R.id.seek_bar)
        albumArtImageView = findViewById(R.id.iv_album_art)
        previousButton = findViewById(R.id.btn_previous)
        nextButton = findViewById(R.id.btn_next)
        closePlayerButton = findViewById(R.id.btn_close_player)
        currentTimeTextView = findViewById(R.id.tv_current_time)
        totalTimeTextView = findViewById(R.id.tv_total_time)

        playAllButton = songsScreen.findViewById(R.id.btn_play_all)
        songCountTextView = songsScreen.findViewById(R.id.tv_song_count)

        playAllButton.setOnClickListener {
            if (currentSongs.isNotEmpty()) {
                playSong(0)
            }
        }
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

    private fun setupNavigation() {
        findViewById<Button>(R.id.btn_home).setOnClickListener { showScreen(homeScreen) }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showScreen(settingsScreen) }

        // Set up folder list click listener
        homeScreen.findViewById<ListView>(R.id.folder_list_view).setOnItemClickListener { _, _, position, _ ->
            loadSongsInFolder(folderItems[position].folder)
            showScreen(songsScreen)
        }

        // Set up song list click listener
        songsScreen.findViewById<ListView>(R.id.song_list_view).setOnItemClickListener { _, _, position, _ ->
            playSong(position)
            showScreen(playerScreen)
        }

        songsScreen.findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            showScreen(homeScreen)
        }

        // Set up close player button
        playerScreen.findViewById<ImageButton>(R.id.btn_close_player).setOnClickListener {
            showScreen(songsScreen)
        }

        playerScreen.findViewById<ImageButton>(R.id.btn_next).setOnClickListener {
            playNextSong()
        }

        playerScreen.findViewById<ImageButton>(R.id.btn_previous).setOnClickListener {
            playPreviousSong()
        }

        playerScreen.findViewById<ImageButton>(R.id.btn_play_pause).setOnClickListener {
            togglePlayPause()
        }
    }

    private fun showScreen(screen: View) {
        contentFrame.removeAllViews()
        contentFrame.addView(screen)

        // Update visibility of footer and header
        findViewById<View>(R.id.footer)?.visibility = when (screen) {
            homeScreen, settingsScreen -> View.VISIBLE
            else -> View.GONE
        }

        songsScreen.findViewById<View>(R.id.folder_name_header)?.visibility = when (screen) {
            songsScreen -> View.VISIBLE
            else -> View.GONE
        }

        // Update mini player visibility
        val miniPlayer = when (screen) {
            homeScreen -> homeScreen.findViewById<View>(R.id.mini_player)
            songsScreen -> songsScreen.findViewById<View>(R.id.mini_player)
            else -> null
        }
        miniPlayer?.visibility = if (lastPlayedSong != null) View.VISIBLE else View.GONE
    }

    private fun setupFooter() {
        btnHome.setOnClickListener {
            Log.d("AudioFlow", "Home button clicked")
            try {
                showScreen(homeScreen)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error showing home view", e)
                Toast.makeText(this, "Error showing home view: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnSettings.setOnClickListener {
            Log.d("AudioFlow", "Settings button clicked")
            try {
                showScreen(settingsScreen)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error showing settings view", e)
                Toast.makeText(this, "Error showing settings view: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMusicFolders() {
        folderItems = getMusicFolders().map { folder ->
            val songCount = getSongsInFolder(folder).size
            FolderItem(folder, folder.name, "$songCount songs")
        }

        val adapter = object : ArrayAdapter<FolderItem>(this, R.layout.folder_list_item, folderItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.folder_list_item, parent, false)
                val folder = getItem(position)
                view.findViewById<TextView>(R.id.folder_item_title).text = folder?.name
                view.findViewById<TextView>(R.id.folder_item_count).text = folder?.songCount
                return view
            }
        }

        homeScreen.findViewById<ListView>(R.id.folder_list_view).adapter = adapter
    }

    private fun loadSongsInFolder(folder: File) {
        currentSongs = getSongsInFolder(folder)
        currentPlaylist = currentSongs.toList()
        currentFolderPath = folder.absolutePath

        songsScreen.findViewById<TextView>(R.id.tv_folder_name).text = folder.name
        songCountTextView.text = "${currentSongs.size} songs"

        val adapter = object : ArrayAdapter<SongItem>(this, R.layout.list_item, currentSongs) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)
                val song = getItem(position)
                view.findViewById<TextView>(R.id.list_item_title).text = song?.title
                view.findViewById<TextView>(R.id.list_item_artist).text = song?.artist
                return view
            }
        }
        songsScreen.findViewById<ListView>(R.id.song_list_view).adapter = adapter
    }


    private fun updateMiniPlayer(song: SongItem) {
        val updateMiniPlayerView = { view: View ->
            view.findViewById<TextView>(R.id.mini_player_title).text = song.title
            view.findViewById<TextView>(R.id.mini_player_artist).text = song.artist
            view.visibility = View.VISIBLE

            // Update album art
            val albumArt = getAlbumArt(song.file.absolutePath)
            if (albumArt != null) {
                view.findViewById<ImageView>(R.id.mini_player_cover).setImageBitmap(albumArt)
            } else {
                view.findViewById<ImageView>(R.id.mini_player_cover).setImageResource(R.drawable.cover_art)
            }

            // Update play/pause button state
            val miniPlayerPlayPause = view.findViewById<ImageButton>(R.id.mini_player_play_pause)
            val playPauseResource = if (mediaPlayer?.isPlaying == true)
                android.R.drawable.ic_media_pause
            else
                android.R.drawable.ic_media_play
            miniPlayerPlayPause.setImageResource(playPauseResource)

            // Set up click listeners for mini player controls
            miniPlayerPlayPause.setOnClickListener { togglePlayPause() }
            view.findViewById<ImageButton>(R.id.mini_player_next).setOnClickListener { playNextSong(false) }
            view.setOnClickListener {
                showScreen(playerScreen)
                updateSeekBar()
            }
        }

        // Update mini player on home screen
        homeScreen.findViewById<View>(R.id.mini_player)?.let { updateMiniPlayerView(it) }

        // Update mini player on songs screen
        songsScreen.findViewById<View>(R.id.mini_player)?.let { updateMiniPlayerView(it) }

        // Save the last played song
        lastPlayedSong = song
        saveLastPlayedSong(song)
    }

    private fun saveLastPlayedSong(song: SongItem) {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("lastPlayedTitle", song.title)
            putString("lastPlayedArtist", song.artist)
            putString("lastPlayedPath", song.file.absolutePath)
            putString("currentFolderPath", currentFolderPath)
            putInt("currentSongIndex", currentSongIndex)
            apply()
        }
    }

    private fun restoreLastPlayedSong() {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        val lastPlayedTitle = sharedPreferences.getString("lastPlayedTitle", null)
        val lastPlayedArtist = sharedPreferences.getString("lastPlayedArtist", null)
        val lastPlayedPath = sharedPreferences.getString("lastPlayedPath", null)
        currentFolderPath = sharedPreferences.getString("currentFolderPath", null)
        currentSongIndex = sharedPreferences.getInt("currentSongIndex", -1)

        if (lastPlayedTitle != null && lastPlayedArtist != null && lastPlayedPath != null && currentFolderPath != null) {
            lastPlayedSong = SongItem(File(lastPlayedPath), lastPlayedTitle, lastPlayedArtist)
            loadSongsInFolder(File(currentFolderPath!!))
            updateMiniPlayer(lastPlayedSong!!)

            // Update mini player visibility on both screens
            homeScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE
            songsScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE

            // Prepare the MediaPlayer with the last played song
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    lastPlayedSong!!.file
                )
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(this, uri)

                // Initialize the player screen
                updatePlayerUI(lastPlayedSong!!)

                // Set up click listener for mini player to open player screen
                homeScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener {
                    showScreen(playerScreen)
                    playerScreen.visibility = View.VISIBLE
                }
                songsScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener {
                    showScreen(playerScreen)
                    playerScreen.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error preparing last played song", e)
            }
        }
    }

    private fun showSelectSingle() {
        findViewById<View>(R.id.single_song_selector).visibility = View.VISIBLE
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

    private fun playSong(position: Int, showPlayerScreen: Boolean = true) {
        if (position < 0 || position >= currentPlaylist.size) return

        currentSongIndex = position
        val song = currentPlaylist[position]

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                song.file
            )
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, uri)
            mediaPlayer?.start()

            if (showPlayerScreen) {
                showScreen(playerScreen)
                findViewById<View>(R.id.player_view_container).visibility = View.VISIBLE
            }

            updatePlayerUI(song)
            updateMiniPlayer(song)

            mediaPlayer?.setOnCompletionListener {
                playNextSong()
            }
        } catch (e: Exception) {
            Log.e("AudioFlow", "Error playing song", e)
            Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayerUI(song: SongItem) {
        playerSongTitleTextView.text = song.title
        artistNameTextView.text = song.artist
        playPauseButton.setImageResource(android.R.drawable.ic_media_pause)

        // Update seek bar
        mediaPlayer?.let { player ->
            seekBar.max = player.duration
            seekBar.progress = 0
            updateSeekBar()

            totalTimeTextView.text = formatTime(player.duration)
        }

        // Set album art
        val albumArt = getAlbumArt(song.file.absolutePath)
        if (albumArt != null) {
            albumArtImageView.setImageBitmap(albumArt)
        } else {
            albumArtImageView.setImageResource(R.drawable.cover_art)
        }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    currentTimeTextView.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // You can add code here if needed
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // You can add code here if needed
            }
        })
    }

    private fun updateSeekBar() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                seekBar.progress = player.currentPosition
                currentTimeTextView.text = formatTime(player.currentPosition)
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
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                    homeScreen.findViewById<ImageButton>(R.id.mini_player_play_pause)?.setImageResource(android.R.drawable.ic_media_play)
                    songsScreen.findViewById<ImageButton>(R.id.mini_player_play_pause)?.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    player.start()
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                    homeScreen.findViewById<ImageButton>(R.id.mini_player_play_pause)?.setImageResource(android.R.drawable.ic_media_pause)
                    songsScreen.findViewById<ImageButton>(R.id.mini_player_play_pause)?.setImageResource(android.R.drawable.ic_media_pause)
                    updateSeekBar()
                }
                lastPlayedSong?.let { updateMiniPlayer(it) }
            }
        } catch (e: Exception) {
            Log.e("AudioFlow", "Error toggling play/pause: ${e.message}", e)
            Toast.makeText(this, "Error playing/pausing: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun playNextSong(showPlayerScreen: Boolean = true) {
        if (currentPlaylist.isEmpty()) {
            // If there are no songs loaded, try to reload the current folder
            currentFolderPath?.let { path ->
                loadSongsInFolder(File(path))
            }
        }

        if (currentPlaylist.isNotEmpty()) {
            val currentSongFile = lastPlayedSong?.file
            val currentIndex = currentPlaylist.indexOfFirst { it.file == currentSongFile }
            val nextIndex = if (currentIndex != -1) {
                (currentIndex + 1) % currentPlaylist.size
            } else {
                0
            }
            playSong(nextIndex, showPlayerScreen)
        } else {
            Log.e("AudioFlow", "No songs available to play")
            Toast.makeText(this, "No songs available to play", Toast.LENGTH_SHORT).show()
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

    override fun onBackPressed() {
        when (contentFrame.getChildAt(0)) {
            songsScreen -> showScreen(homeScreen)
            playerScreen -> showScreen(songsScreen)
            settingsScreen -> showScreen(homeScreen)
            else -> super.onBackPressed()
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
        lastPlayedSong?.let { saveLastPlayedSong(it) }
    }

    data class FolderItem(val folder: File, val name: String, val songCount: String)
    data class SongItem(val file: File, val title: String, val artist: String)
}

// Getting Not Player
// Adding Repeat, Repeat Once, Repeat choose
// Songs Options
// Player Design Options
// Maybe change icons player screen
// Add Playlist Create
// Add Play Song next button
// Time around play/pause button
// Sorting System changing numbers to last place
// Search Function for Album, Artists, Songs

// I have a problem. when i for example listen to a song from folder 1, then going to folder 2 while listening the folder 1 song, then press the next button on the mini player, instead of the next song playing from folder 1, the first song of folder 2 starts playing. do you understand my problem
// list_header-xml fixed instead of scrolling with the list