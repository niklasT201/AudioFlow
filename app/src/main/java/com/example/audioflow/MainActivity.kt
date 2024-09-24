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
import java.io.File
import java.util.*
import android.media.MediaMetadataRetriever
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.text.Collator
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var playPauseButton: ImageView
    private lateinit var previousButton: ImageView
    private lateinit var nextButton: ImageView
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
    private lateinit var playSettingsButton: ImageView
    private lateinit var playerSettingsButton: ImageView
    private var currentPlayMode: PlayMode = PlayMode.NORMAL
    private var isShuffleMode = false
    private var originalPlaylist: List<SongItem> = emptyList()
    private var headerView: View? = null
    private var playModeToast: Toast? = null

    private var alphabetIndexView: AlphabetIndexView? = null
    private var songListView: ListView? = null

    enum class PlayMode {
        NORMAL, REPEAT_ALL, REPEAT_ONE, SHUFFLE
    }

    private var selectedSongUri: Uri? = null
    private var currentFolder: File? = null
    private var currentSongIndex: Int = -1
    private var currentSongs: List<SongItem> = emptyList()
    private var currentPlaylist: List<SongItem> = emptyList()
    private lateinit var playerScreen: View
    private lateinit var homeScreen: View
    private lateinit var songsScreen: View
    private lateinit var searchScreen: View
    private lateinit var settingsScreen: View
    private var folderItems: List<FolderItem> = emptyList()

    private lateinit var contentFrame: FrameLayout
    private lateinit var btnHome: Button
    private lateinit var btnSearch: Button
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

        setupPlaySettings()

        restorePlayMode()

        // Show home screen by default
        showScreen(homeScreen)

        if (checkPermission()) {
            loadMusicFolders()
            restoreLastPlayedSong()
            updatePlayPauseButton()
        } else {
            requestPermission()
        }

        setupFooter()
    }

    private fun initializeViews() {
        contentFrame = findViewById(R.id.content_frame)
        val footer = findViewById<View>(R.id.footer)
        btnHome = footer.findViewById(R.id.btn_home)
        btnSearch = footer.findViewById(R.id.btn_search)
        btnSettings = footer.findViewById(R.id.btn_settings)

        homeScreen = layoutInflater.inflate(R.layout.home_screen, contentFrame, false)
        songsScreen = layoutInflater.inflate(R.layout.songs_screen, contentFrame, false)
        searchScreen = layoutInflater.inflate(R.layout.search_screen, contentFrame, false)
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
        playSettingsButton = findViewById(R.id.btn_play_settings)
        playerSettingsButton = findViewById(R.id.btn_player_settings)

        songListView = songsScreen.findViewById(R.id.song_list_view)
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
        findViewById<Button>(R.id.btn_search).setOnClickListener { showScreen(searchScreen) }
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

        playerScreen.findViewById<ImageView>(R.id.btn_next).setOnClickListener {
            playNextSong()
        }

        playerScreen.findViewById<ImageView>(R.id.btn_previous).setOnClickListener {
            playPreviousSong()
        }

        playerScreen.findViewById<ImageView>(R.id.btn_play_pause).setOnClickListener {
            togglePlayPause()
        }
    }

    private fun showScreen(screen: View) {
        contentFrame.removeAllViews()
        contentFrame.addView(screen)

        // Update visibility of footer and header
        findViewById<View>(R.id.footer)?.visibility = when (screen) {
            homeScreen, searchScreen, settingsScreen -> View.VISIBLE
            else -> View.GONE
        }

        songsScreen.findViewById<View>(R.id.folder_name_header)?.visibility = when (screen) {
            songsScreen -> View.VISIBLE
            else -> View.GONE
        }

        // Update mini player visibility
        val miniPlayer = when (screen) {
            homeScreen -> homeScreen.findViewById<View>(R.id.mini_player)
            searchScreen -> searchScreen.findViewById<View>(R.id.mini_player)
            songsScreen -> songsScreen.findViewById<View>(R.id.mini_player)
            else -> null
        }
        miniPlayer?.visibility = if (lastPlayedSong != null) View.VISIBLE else View.GONE

        // Show player screen if it's the selected screen
        if (screen == playerScreen) {
            playerScreen.visibility = View.VISIBLE
        }
    }

    private val scrollListener = object : AbsListView.OnScrollListener {
        private var isScrolling = false
        private val hideRunnable = Runnable { hideAlphabetIndex() }

        override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
            when (scrollState) {
                AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL,
                AbsListView.OnScrollListener.SCROLL_STATE_FLING -> {
                    isScrolling = true
                    showAlphabetIndex()
                    view?.removeCallbacks(hideRunnable)
                }
                AbsListView.OnScrollListener.SCROLL_STATE_IDLE -> {
                    isScrolling = false
                    view?.postDelayed(hideRunnable, 4000) // Hide after 1 second of inactivity
                }
            }
        }

        override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
            if (isScrolling) {
                showAlphabetIndex()
            }
        }
    }

    private fun showAlphabetIndex() {
        alphabetIndexView?.setVisibilityWithAnimation(true)
    }

    private fun hideAlphabetIndex() {
        alphabetIndexView?.setVisibilityWithAnimation(false)
    }

    private fun setupAlphabetIndex() {
        alphabetIndexView?.onLetterSelectedListener = { letter ->
            showAlphabetIndex()
            val position = findPositionForLetter(letter)
            songListView?.setSelection(position)
        }
    }

    private fun findPositionForLetter(letter: String): Int {
        for (i in currentSongs.indices) {
            if (currentSongs[i].title.startsWith(letter, ignoreCase = true)) {
                return i
            }
        }
        return 0
    }

    private fun savePlayMode() {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("currentPlayMode", currentPlayMode.name)
            apply()
        }
    }

    private fun restorePlayMode() {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        val savedPlayMode = sharedPreferences.getString("currentPlayMode", PlayMode.NORMAL.name)
        currentPlayMode = PlayMode.valueOf(savedPlayMode ?: PlayMode.NORMAL.name)
        updatePlaySettingsIcon()
    }

    private fun setupPlaySettings() {
        playSettingsButton.setOnClickListener {
            currentPlayMode = when (currentPlayMode) {
                PlayMode.NORMAL -> PlayMode.REPEAT_ALL
                PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
                PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE -> PlayMode.NORMAL
            }
            isShuffleMode = currentPlayMode == PlayMode.SHUFFLE
            updatePlaySettingsIcon()
            applyPlayMode()
            savePlayMode()
        }

        // Add a long click listener for shuffle
        playSettingsButton.setOnLongClickListener {
            isShuffleMode = !isShuffleMode
            currentPlayMode = if (isShuffleMode) {
                PlayMode.SHUFFLE
            } else {
                PlayMode.NORMAL
            }
            updatePlaySettingsIcon()
            applyPlayMode()
            savePlayMode()
            true
        }
    }

    private fun updatePlaySettingsIcon() {
        val iconResource = when {
            isShuffleMode -> R.drawable.shuffle
            currentPlayMode == PlayMode.NORMAL -> R.drawable.no_repeat
            currentPlayMode == PlayMode.REPEAT_ALL -> R.drawable.repeat
            currentPlayMode == PlayMode.REPEAT_ONE -> R.drawable.repeat_once
            else -> R.drawable.no_repeat
        }
        playSettingsButton.setImageResource(iconResource)
    }

    private fun showPlayModeToast() {
        val playModeText = when (currentPlayMode) {
            PlayMode.NORMAL -> "Normal Mode"
            PlayMode.REPEAT_ALL -> "Repeat All"
            PlayMode.REPEAT_ONE -> "Repeat One"
            PlayMode.SHUFFLE -> "Shuffle"
        }

        if (playModeToast == null) {
            playModeToast = Toast.makeText(this, playModeText, Toast.LENGTH_SHORT)
        } else {
            playModeToast?.setText(playModeText)
        }

        // Show the toast for 2 seconds
        playModeToast?.show()
    }

    private fun applyPlayMode() {
        when (currentPlayMode) {
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

        // Display the updated play mode toast
        showPlayModeToast()
    }

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

        btnSearch.setOnClickListener {
            Log.d("AudioFlow", "Search button clicked")
            try {
                showScreen(searchScreen)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error showing search view", e)
                Toast.makeText(this, "Error showing search view: ${e.message}", Toast.LENGTH_LONG).show()
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
            FolderItem(folder, folder.name, "$songCount songs", folder.absolutePath == currentFolderPath)
        }

        val adapter = object : ArrayAdapter<FolderItem>(this, R.layout.folder_list_item, folderItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.folder_list_item, parent, false)
                val folder = getItem(position)
                view.findViewById<TextView>(R.id.folder_item_title).text = folder?.name
                view.findViewById<TextView>(R.id.folder_item_count).text = folder?.songCount
                view.findViewById<ImageView>(R.id.folder_current_song_icon).visibility =
                    if (folder?.isCurrentFolder == true) View.VISIBLE else View.GONE
                return view
            }
        }

        homeScreen.findViewById<ListView>(R.id.folder_list_view).adapter = adapter
    }

    private fun loadSongsInFolder(folder: File) {
        currentSongs = getSongsInFolder(folder)

        showScreen(songsScreen)

        songsScreen.findViewById<TextView>(R.id.tv_folder_name).text = folder.name

        // Initialize the header view if it doesn't exist
        if (headerView == null) {
            headerView = layoutInflater.inflate(R.layout.list_header, null)
        }

        // Check if the songListView is not null before adding the header view
        songListView?.let { listView ->
            // Remove any existing header views
            if (listView.headerViewsCount > 0) {
                listView.removeHeaderView(listView.getChildAt(0))
            }

            // Add the header view to the ListView
            listView.addHeaderView(headerView)
        }

        // Update the header view
        val songCountTextView = headerView?.findViewById<TextView>(R.id.tv_song_count)
        songCountTextView?.text = "Play all (${currentSongs.size})"

        // Set up the click listener for the play all button
        val playAllButton = headerView?.findViewById<ImageButton>(R.id.playlist_start_button)
        playAllButton?.setOnClickListener {
            if (currentSongs.isNotEmpty()) {
                currentPlaylist = currentSongs.toList()
                playSong(0)
            }
        }

        // Initialize views here
        alphabetIndexView = songsScreen.findViewById(R.id.alphabet_index)
        //songListView = songsScreen.findViewById(R.id.song_list_view)

        setupAlphabetIndex()

        // Initialize the song list view adapter
        val adapter = createSongListAdapter(currentSongs)
        songListView?.adapter = adapter
        songListView?.setOnScrollListener(scrollListener)

        // Initially hide the alphabet index
        alphabetIndexView?.visibility = View.GONE

        // Set up song list click listener
        songsScreen.findViewById<ListView>(R.id.song_list_view).setOnItemClickListener { _, _, position, _ ->
            val actualPosition = position - 1 // Adjust for header
            if (actualPosition >= 0) { // Ensure we're not clicking the header
                currentPlaylist = currentSongs.toList()
                playSong(actualPosition)
                currentFolderPath = folder.absolutePath
                updateFolderList()
                showScreen(playerScreen)
            }
        }
    }

    private fun createSongListAdapter(songs: List<SongItem>): ArrayAdapter<SongItem> {
        return object : ArrayAdapter<SongItem>(this, R.layout.list_item, songs) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)
                val song = getItem(position)
                view.findViewById<TextView>(R.id.list_item_title).text = song?.title
                view.findViewById<TextView>(R.id.list_item_artist).text = "${song?.artist} - ${song?.album}"
                view.findViewById<ImageView>(R.id.song_current_song_icon).visibility =
                    if (song == lastPlayedSong) View.VISIBLE else View.GONE
                return view
            }
        }
    }

    private fun updateFolderList() {
        folderItems.forEach { it.isCurrentFolder = it.folder.absolutePath == currentFolderPath }
        (homeScreen.findViewById<ListView>(R.id.folder_list_view).adapter as ArrayAdapter<*>).notifyDataSetChanged()
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
            val miniPlayerPlayPause = view.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)
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

        searchScreen.findViewById<View>(R.id.mini_player)?.let { updateMiniPlayerView(it) }

        // Update mini player on songs screen
        songsScreen.findViewById<View>(R.id.mini_player)?.let { updateMiniPlayerView(it) }

        // Save the last played song
        lastPlayedSong = song
        saveLastPlayedSong(song)
    }

    private fun updateMiniPlayerProgress(progress: Float) {
        val miniPlayerPlayPause = homeScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)
        miniPlayerPlayPause?.setProgress(progress)

        val songsScreenMiniPlayerPlayPause = songsScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)
        songsScreenMiniPlayerPlayPause?.setProgress(progress)

        val searchScreenMiniPlayerPlayPause = searchScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)
        searchScreenMiniPlayerPlayPause?.setProgress(progress)
    }

    private fun saveLastPlayedSong(song: SongItem) {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("lastPlayedTitle", song.title)
            putString("lastPlayedArtist", song.artist)
            putString("lastPlayedAlbum", song.album)
            putString("lastPlayedPath", song.file.absolutePath)
            putString("currentFolderPath", currentFolderPath)
            putInt("currentSongIndex", currentSongIndex)

            // Save the current playlist
            putString("currentPlaylist", currentPlaylist.joinToString("|") { it.file.absolutePath })

            apply()
        }
    }

    private fun restoreLastPlayedSong() {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        val lastPlayedTitle = sharedPreferences.getString("lastPlayedTitle", null)
        val lastPlayedArtist = sharedPreferences.getString("lastPlayedArtist", null)
        val lastPlayedAlbum = sharedPreferences.getString("lastPlayedAlbum", null)
        val lastPlayedPath = sharedPreferences.getString("lastPlayedPath", null)
        currentFolderPath = sharedPreferences.getString("currentFolderPath", null)
        currentSongIndex = sharedPreferences.getInt("currentSongIndex", -1)

        if (lastPlayedTitle != null && lastPlayedArtist != null && lastPlayedAlbum != null && lastPlayedPath != null && currentFolderPath != null) {
            lastPlayedSong = SongItem(File(lastPlayedPath), lastPlayedTitle, lastPlayedArtist, lastPlayedAlbum)
            loadSongsInFolder(File(currentFolderPath!!))
            currentPlaylist = currentSongs.toList()  // Ensure currentPlaylist is set
            updateMiniPlayer(lastPlayedSong!!)

            // Update mini player visibility on both screens
            homeScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE
            songsScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE
            searchScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE

            // Prepare the MediaPlayer with the last played song
            try {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    lastPlayedSong!!.file
                )
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(this, uri)
                setupMediaPlayerCompletionListener()  // Set up completion listener

                // Initialize the player screen
                updatePlayerUI(lastPlayedSong!!)

                // Set up click listener for mini player to open player screen
                val miniPlayerClickListener = View.OnClickListener {
                    showScreen(playerScreen)
                    playerScreen.visibility = View.VISIBLE
                }
                homeScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener(miniPlayerClickListener)
                songsScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener(miniPlayerClickListener)
                searchScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener(miniPlayerClickListener)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error preparing last played song", e)
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
                        // At the last song, stop playback
                        mediaPlayer?.pause()
                        updatePlayPauseButton()
                        Toast.makeText(this, "Playlist ended", Toast.LENGTH_SHORT).show()
                    }
                }
                PlayMode.REPEAT_ALL -> playNextSong()
                PlayMode.REPEAT_ONE -> mediaPlayer?.start()
                PlayMode.SHUFFLE -> playRandomSong()
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
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
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
                        Log.e("AudioFlow", "Error retrieving metadata for $path", e)
                        title = file.nameWithoutExtension
                        album = "Unknown Album"
                    } finally {
                        retriever.release()
                    }
                }

                songs.add(SongItem(file, title, artist, album))
            }
        }

        return customSongSort(songs)
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

            setupMediaPlayerCompletionListener()

            if (showPlayerScreen) {
                showScreen(playerScreen)
                findViewById<View>(R.id.player_view_container).visibility = View.VISIBLE
            }

            updatePlayerUI(song)
            updateMiniPlayer(song)
            updateMiniPlayerProgress(0f)

            // Update the folder list to show the current folder icon
            currentFolderPath = song.file.parent
            updateFolderList()

            // Refresh the song list view to show the current song icon
            refreshSongList()

        } catch (e: Exception) {
            Log.e("AudioFlow", "Error playing song", e)
            Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshSongList() {
        val listView = songsScreen.findViewById<ListView>(R.id.song_list_view)
        val adapter = listView.adapter
        if (adapter is HeaderViewListAdapter) {
            val wrappedAdapter = adapter.wrappedAdapter
            if (wrappedAdapter is ArrayAdapter<*>) {
                wrappedAdapter.notifyDataSetChanged()
            }
        } else if (adapter is ArrayAdapter<*>) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun updatePlayerUI(song: SongItem) {
        playerSongTitleTextView.text = song.title
        artistNameTextView.text = song.artist
        updatePlayPauseButton()

        // Update seek bar
        mediaPlayer?.let { player ->
            seekBar.max = player.duration
            seekBar.progress = 0
            updateSeekBar()

            totalTimeTextView.text = formatTime(player.duration)
            currentTimeTextView.text = formatTime(0)  // Always show the current time
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
            seekBar.progress = player.currentPosition
            currentTimeTextView.text = formatTime(player.currentPosition)

            // Update mini player progress
            val progress = player.currentPosition.toFloat() / player.duration.toFloat()
            updateMiniPlayerProgress(progress)

            seekBar.postDelayed({ updateSeekBar() }, 1000)
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

    private fun updatePlayPauseButton() {
        val isPlaying = mediaPlayer?.isPlaying == true
        val resource = if (isPlaying) R.drawable.pause_button else R.drawable.play_button
        val miniResource = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        playPauseButton.setImageResource(resource)
        homeScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)?.setImageResource(miniResource)
        songsScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)?.setImageResource(miniResource)
        searchScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)?.setImageResource(miniResource)
    }

    private fun togglePlayPause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.start()
                    setupMediaPlayerCompletionListener()  // Ensure the completion listener is set
                    updateSeekBar()
                }
                updatePlayPauseButton()
                lastPlayedSong?.let { updateMiniPlayer(it) }
            }
        } catch (e: Exception) {
            Log.e("AudioFlow", "Error toggling play/pause: ${e.message}", e)
            Toast.makeText(this, "Error playing/pausing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPreviousSong() {
        if (currentPlaylist.isNotEmpty()) {
            when (currentPlayMode) {
                PlayMode.NORMAL -> {
                    if (currentSongIndex > 0) {
                        currentSongIndex--
                        playSong(currentSongIndex)
                    } else {
                        // At the first song, do nothing or maybe show a toast
                        Toast.makeText(this, "This is the first song", Toast.LENGTH_SHORT).show()
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
                currentPlaylist = currentSongs.toList()
            }
        }

        if (currentPlaylist.isNotEmpty()) {
            when (currentPlayMode) {
                PlayMode.NORMAL, PlayMode.REPEAT_ALL, PlayMode.REPEAT_ONE -> {
                    currentSongIndex = (currentSongIndex + 1) % currentPlaylist.size
                    playSong(currentSongIndex, showPlayerScreen)
                }
                PlayMode.SHUFFLE -> {
                    currentSongIndex = (0 until currentPlaylist.size).random()
                    playSong(currentSongIndex, showPlayerScreen)
                }
            }
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
            searchScreen -> showScreen(homeScreen)
            else -> super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        loadMusicFolders()
        // If you have a current song playing, update the UI
        lastPlayedSong?.let { updateMiniPlayer(it) }
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
        savePlayMode()
    }

    data class FolderItem(val folder: File, val name: String, val songCount: String, var isCurrentFolder: Boolean = false)
    data class SongItem(val file: File, val title: String, val artist: String, val album: String)
}

// Getting Not Player
// Get Playback for songs
// Options song
// Player Design Options
// Add Playlist Create
// Add/search Folder to List
// Add Play Song next button
// Search Function for Album, Artists, Songs
// add smoother animations to app
// song list settings for one song
// holding song item for settings too

// info screen
// sound changes
// color changing
// cover changing
// maybe driver mode
// timer to close app
// delete/rename file
// Songs metadata changing

// settings screen
// allow song previous button to set time back to 0
// allow app to display being always on
// Timer also available
// About App
// Maybe add small infos about a song

//can you help me with my kotlin android app? I would like to have a special feature. I want that when you hold down the play/pause button of the player screen, for maybe like 2 seconds, then a number in like a small round container appears and this number gets higher how longer you hold down on this button. for example when I don't hold down the button for 2 seconds or longer, then this container will not appear and the value will be like 0, that means that the current playing song will not repeat itself, it will once finish, go to the next song in the playlist, but when the value is over 0, so for example 4, then the current song will repeat itself 4 times after finishing. Song finishes, repeats, number in container gets down to 3, song finishes, repeats, number gets down to 2 and so on. Once the value is 0, the container disappears and the song will when finished go to the next song. hope you get what I mean :) and WITHOUT a library when possible