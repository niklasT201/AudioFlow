package com.example.audioflow


import android.media.MediaPlayer
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.net.Uri
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.util.*
import android.media.PlaybackParams
import android.os.IBinder
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import java.io.FileNotFoundException
import java.io.IOException
import com.example.audioflow.AudioMetadataRetriever.SongItem

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var coverStyleCustomizer: CoverStyleCustomizer? = null
    private lateinit var audioMetadataRetriever: AudioMetadataRetriever
    private lateinit var footerManager: FooterManager
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var songOptionsHandler: SongOptionsHandler
    private lateinit var colorManager: ColorManager
    private lateinit var homeScreenManager: HomeScreenManager
    lateinit var settingsManager: SettingsManager
    private lateinit var playerOptionsManager: PlayerOptionsManager
    private lateinit var repeatManager: RepeatManager
    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false
    private lateinit var songOptionsFooter: LinearLayout

    private lateinit var playPauseButton: ImageView
    private lateinit var previousButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var playButton: Button
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
    private val shuffleHistory = mutableListOf<Int>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            bound = true

            // Initialize the service with the current MediaPlayer if it exists
            mediaPlayer?.let { mediaPlayerService?.initializePlayer(it) }
            lastPlayedSong?.let { mediaPlayerService?.updateMetadata(it) }

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

    private lateinit var playerOptionsOverlay: View
    private lateinit var currentsongtitle: TextView
    private lateinit var playbackSpeedSeekBar: SeekBar
    private lateinit var playbackSpeedText: TextView

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
    private var currentSongIndex: Int = -1
    private var currentSongs: List<SongItem> = emptyList()
    var currentPlaylist: List<SongItem> = emptyList()
    private lateinit var playerScreen: View
    lateinit var homeScreen: View
    private lateinit var songsScreen: View
    lateinit var settingsScreen: View
    private var folderItems: List<FolderItem> = emptyList()

    private lateinit var contentFrame: FrameLayout
    private lateinit var aboutScreen: View
    var lastPlayedSong: SongItem? = null
    private var currentFolderPath: String? = null

    companion object {
        private const val READ_EXTERNAL_STORAGE_REQUEST = 1
        private const val PICK_AUDIO_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeViews()

        audioMetadataRetriever = AudioMetadataRetriever(contentResolver)
        colorManager = ColorManager(this)
        colorManager.applyColorToActivity(this)

        playerOptionsManager = PlayerOptionsManager(
            activity = this,
            overlay = playerOptionsOverlay,
            currentSongTitle = currentsongtitle,
            playbackSpeedSeekBar = playbackSpeedSeekBar,
            playbackSpeedText = playbackSpeedText,
            mediaPlayer = mediaPlayer,
            colorManager = colorManager,
            coverStyleCustomizer = coverStyleCustomizer
        )

        coverStyleCustomizer = CoverStyleCustomizer(this)
        coverStyleCustomizer!!.initialize(findViewById(R.id.player_view_container))

        favoriteManager = FavoriteManager(this)

        settingsManager = SettingsManager(this)
        settingsManager.setupSettings(settingsScreen, aboutScreen)

        homeScreenManager = HomeScreenManager(this)
        homeScreenManager.setupHomeScreen(homeScreen)

        repeatManager = RepeatManager(findViewById(R.id.repeat_counter_view), this)
        repeatManager.setupRepeatListener(playPauseButton)

        intent.getStringExtra("PLAY_SONG")?.let { songPath ->
            val songFile = File(songPath)
            if (songFile.exists()) {
                val song = audioMetadataRetriever.getSongsInFolder(songFile.parentFile).find { it.file == songFile }
                if (song != null) {
                    currentPlaylist = listOf(song)
                    currentSongIndex = 0
                    playSong(0)
                    showScreen(playerScreen)
                } else {
                    Toast.makeText(this, "Error loading song", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Song file not found", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up navigation
        setupNavigation()

        // Load music folders
        loadMusicFolders()

        setupSeekBar()

        setupPlaySettings()

        restorePlayMode()

        playerOptionsManager.setPlaybackSpeedChangeListener(object : PlayerOptionsManager.PlaybackSpeedChangeListener {
            override fun onPlaybackSpeedChanged(speed: Float) {
                mediaPlayer?.let { player ->
                    val params = player.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    player.playbackParams = params
                }
            }
        })

        footerManager = FooterManager(this)
        footerManager.setupFooter()

        songOptionsHandler = SongOptionsHandler(
            activity = this,
            songOptionsFooter = songOptionsFooter,
            songListView = songListView!!,
            playerOptionsManager = playerOptionsManager
        )


        Intent(this, MediaPlayerService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        // Show home screen by default
        showScreen(homeScreen)


        findViewById<View>(R.id.favorites_container).setOnClickListener {
            //if (favoriteManager.getFavorites().isNotEmpty()) {
            //    val intent = Intent(this, FavoritesActivity::class.java)
            //    startActivity(intent)
            //}

            Toast.makeText(this, "This Feature is not ready and needs a bit more Time for Development", Toast.LENGTH_LONG).show()
        }

        updateFavoriteCount()

        if (checkPermission()) {
            loadMusicFolders()
            restoreLastPlayedSong()
            updatePlayPauseButton()
        } else {
            requestPermission()
        }

        intent.getStringExtra("PLAY_SONG")?.let { songPath ->
            val songFile = File(songPath)
            if (songFile.exists()) {
                val song = audioMetadataRetriever.getSongsInFolder(songFile.parentFile).find { it.file == songFile }
                if (song != null) {
                    currentPlaylist = listOf(song)
                    currentSongIndex = 0
                    playSong(0)
                    // Only update folder list if not coming from favorites
                    if (!intent.getBooleanExtra("FROM_FAVORITES", false)) {
                        updateFolderList()
                    }
                    showScreen(playerScreen)
                } else {
                    Toast.makeText(this, "Error loading song", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Song file not found", Toast.LENGTH_SHORT).show()
            }
        }

        if (intent.getBooleanExtra("FROM_FAVORITES", false)) {
            handleFavoritesPlaylist()
        }
    }

    private fun handleFavoritesPlaylist() {
        if (intent.getBooleanExtra("FROM_FAVORITES", false)) {
            val favorites = favoriteManager.getFavorites().mapNotNull { path ->
                val file = File(path)
                if (file.exists()) {
                    audioMetadataRetriever.getSongsInFolder(file.parentFile).find { it.file == file }
                } else {
                    null
                }
            }
            currentPlaylist = favorites
            currentSongIndex = currentPlaylist.indexOfFirst { it.file.absolutePath == intent.getStringExtra("PLAY_SONG") }
        }
    }

    private fun initializeViews() {
        contentFrame = findViewById(R.id.content_frame)

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
        playSettingsButton = findViewById(R.id.btn_play_settings)
        playerSettingsButton = findViewById(R.id.btn_player_settings)
        aboutScreen = layoutInflater.inflate(R.layout.about_screen, contentFrame, false)

        songListView = songsScreen.findViewById(R.id.song_list_view)
        songOptionsFooter = songsScreen.findViewById(R.id.song_options_footer)

        playerOptionsOverlay = findViewById(R.id.player_options_overlay)
        currentsongtitle = findViewById(R.id.current_song_title)
        playbackSpeedSeekBar = findViewById(R.id.playback_speed_seekbar)
        playbackSpeedText = findViewById(R.id.playback_speed_text)
        playerSettingsButton = findViewById(R.id.btn_player_settings)

        findViewById<ImageButton>(R.id.btn_share_song).setOnClickListener {
            currentPlaylist.getOrNull(currentSongIndex)?.let { song ->
                shareSong(song)
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

    private fun shareSong(song: SongItem) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                song.file
            )
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share ${song.title}"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        findViewById<Button>(R.id.btn_home).setOnClickListener { showScreen(homeScreen) }
        findViewById<Button>(R.id.btn_settings).setOnClickListener { showScreen(settingsScreen) }

        homeScreen.findViewById<ListView>(R.id.folder_list_view).setOnItemClickListener { _, _, position, _ ->
            loadSongsInFolder(folderItems[position].folder)
            showScreen(songsScreen)
        }

        songsScreen.findViewById<ListView>(R.id.song_list_view).setOnItemClickListener { _, _, position, _ ->
            playSong(position)
            showScreen(playerScreen)
            showPlayerScreen()
        }

        songsScreen.findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            showScreen(homeScreen)
            songOptionsHandler.hideFooter()
        }

        // Set up close player button
        playerScreen.findViewById<ImageButton>(R.id.btn_close_player).setOnClickListener {
            showScreen(songsScreen)
            hidePlayerScreen()
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

    fun showScreen(screen: View) {
        contentFrame.removeAllViews()
        contentFrame.addView(screen)

        // Update visibility of footer and header
        findViewById<View>(R.id.footer)?.visibility = when (screen) {
            homeScreen, settingsScreen -> View.VISIBLE
            else -> View.GONE
        }

        when (screen) {
            homeScreen -> footerManager.updateFooterHighlight(findViewById(R.id.btn_home))
            songsScreen -> footerManager.updateFooterHighlight(findViewById(R.id.btn_search))
            settingsScreen -> footerManager.updateFooterHighlight(findViewById(R.id.btn_settings))
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

        // Show player screen if it's the selected screen
        if (screen == playerScreen) {
            playerScreen.visibility = View.VISIBLE
        }

        colorManager.handlePlayerVisibilityChange(this, false)
    }

    private fun showPlayerScreen() {
        try {
            val playerContainer = findViewById<View>(R.id.player_view_container)
            playerContainer.visibility = View.VISIBLE
            updateFavoriteButton()

            // Delay the background update slightly to ensure views are properly laid out
            playerContainer.post {
                colorManager.handlePlayerVisibilityChange(this, true)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing player screen: ${e.message}")
        }
    }

    private fun hidePlayerScreen() {
        try {
            findViewById<View>(R.id.player_view_container).visibility = View.GONE
            colorManager.handlePlayerVisibilityChange(this, false)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error hiding player screen: ${e.message}")
        }
    }

    private fun updateFavoriteButton() {
        val favoriteButton = findViewById<ImageView>(R.id.btn_favorite)
        val currentSong = currentPlaylist.getOrNull(currentSongIndex)
        if (currentSong != null) {
            favoriteButton.setImageResource(
                if (favoriteManager.isFavorite(currentSong)) R.drawable.favorite
                else R.drawable.no_favorite
            )
            favoriteButton.setOnClickListener {
                if (favoriteManager.isFavorite(currentSong)) {
                    favoriteManager.removeFavorite(currentSong)
                } else {
                    favoriteManager.addFavorite(currentSong)
                }
                updateFavoriteButton()
            }
        }
    }

    private fun updateFavoriteCount() {
        val count = favoriteManager.getFavorites().size
        Log.d("FavoriteManager", "Updating favorite count: $count")
        val favoritesContainer = findViewById<View>(R.id.favorites_container)
        favoritesContainer.visibility = if (count == 0) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.favorite_count).text =
            if (count == 1) "1 song" else "$count songs"
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun setOnCompletionListener(listener: () -> Unit) {
        mediaPlayer?.setOnCompletionListener { listener() }
    }

    fun cleanupAndFinish() {
        mediaPlayer?.release()
        stopService(Intent(this, MediaPlayerService::class.java))
        finish()
    }

    fun getCurrentSongTitle(): String {
        return lastPlayedSong?.title ?: "Unknown Title"
    }

    fun getCurrentSongPath(): String {
        return lastPlayedSong?.file?.absolutePath ?: ""
    }

    fun renameSongFile(newName: String) {
        val currentSong = currentPlaylist[currentSongIndex]
        val newFile = File(currentSong.file.parent, "$newName.mp3")
        if (currentSong.file.renameTo(newFile)) {
            currentPlaylist = currentPlaylist.toMutableList().apply {
                set(currentSongIndex, currentSong.copy(file = newFile, title = newName))
            }
            updatePlayerUI(currentPlaylist[currentSongIndex])
            Toast.makeText(this, "File renamed successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to rename file", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteCurrentSong() {
        val currentSong = currentPlaylist[currentSongIndex]
        if (currentSong.file.delete()) {
            updateMediaStoreAfterDeletion(currentSong.file.absolutePath)
            currentPlaylist = currentPlaylist.filterIndexed { index, _ -> index != currentSongIndex }
            if (currentPlaylist.isEmpty()) {
                finish()
            } else {
                currentSongIndex = currentSongIndex.coerceAtMost(currentPlaylist.size - 1)
                playSong(currentSongIndex)
            }
            Toast.makeText(this, "File deleted successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMediaStoreAfterDeletion(filePath: String) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val where = MediaStore.Audio.Media.DATA + "=?"
        val selectionArgs = arrayOf(filePath)
        contentResolver.delete(uri, where, selectionArgs)
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
            putBoolean("isShuffleMode", isShuffleMode)
            apply()
        }
    }

    private fun restorePlayMode() {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        val savedPlayMode = sharedPreferences.getString("currentPlayMode", PlayMode.NORMAL.name)
        currentPlayMode = PlayMode.valueOf(savedPlayMode ?: PlayMode.NORMAL.name)
        isShuffleMode = sharedPreferences.getBoolean("isShuffleMode", false)
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

        shuffleHistory.clear()
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



    private fun loadMusicFolders() {
        folderItems = audioMetadataRetriever.getMusicFolders().map { folder ->
            val songCount = audioMetadataRetriever.getSongsInFolder(folder).size
            FolderItem(folder, folder.name, "$songCount songs", folder.absolutePath == currentFolderPath)
        }

        val noFoldersMessage = homeScreen.findViewById<TextView>(R.id.no_folders_message)

        if (folderItems.isEmpty()) {
            homeScreen.findViewById<ListView>(R.id.folder_list_view).visibility = View.GONE
            noFoldersMessage.visibility = View.VISIBLE
        } else {
            homeScreen.findViewById<ListView>(R.id.folder_list_view).visibility = View.VISIBLE
            noFoldersMessage.visibility = View.GONE

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
    }

    private fun loadSongsInFolder(folder: File) {
        Log.d("AudioFlow", "Loading folder: ${folder.name}")
        Log.d("AudioFlow", "Current song index: $currentSongIndex")

        currentSongs = audioMetadataRetriever.getSongsInFolder(folder).filter { it.file.exists() }

        Log.d("AudioFlow", "Song count of folder: ${currentSongs.size}")

        showScreen(songsScreen)

        songsScreen.findViewById<TextView>(R.id.tv_folder_name).text = folder.name

        // Check if the songListView is not null before manipulating it
        songListView?.let { listView ->
            // Remove any existing header views
            listView.removeHeaderView(headerView)
            headerView = null

            // Create and add a new header view
            headerView = layoutInflater.inflate(R.layout.list_header, listView, false)
            headerView?.findViewById<TextView>(R.id.tv_song_count)?.text = "Play all (${currentSongs.size})"
            val playAllButton = headerView?.findViewById<ImageButton>(R.id.playlist_start_button)
            playAllButton?.setOnClickListener {
                if (currentSongs.isNotEmpty()) {
                    currentPlaylist = currentSongs.toList()
                    playSong(0)
                }else {
                    Toast.makeText(this, "No songs available in this folder", Toast.LENGTH_SHORT).show()
                }
            }
            listView.addHeaderView(headerView)

            // Initialize the song list view adapter
            val adapter = createSongListAdapter(currentSongs)
            listView.adapter = adapter
            listView.setOnScrollListener(scrollListener)

            // Set up song list click listener
            listView.setOnItemClickListener { _, _, position, _ ->
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

        // Initialize views here
        alphabetIndexView = songsScreen.findViewById(R.id.alphabet_index)
        setupAlphabetIndex()

        // Initially hide the alphabet index
        alphabetIndexView?.visibility = View.GONE

        Log.d("AudioFlow", "ListView header count: ${songListView?.headerViewsCount}")
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

                // Add click listener to the settings icon
                view.findViewById<ImageView>(R.id.song_settings_icon).setOnClickListener {
                    song?.let { showSongOptionsDialog(it, position) }
                }

                return view
            }
        }
    }

    private fun showSongOptionsDialog(song: SongItem, position: Int) {
        val songOptionsDialog = SongOptionsDialog(this)
        songOptionsDialog.show(
            song = song,
            position = position,
            onPlaySelected = { pos -> playSong(pos) },
            onPlaylistUpdated = {
                currentFolderPath?.let { File(it) }?.let { loadSongsInFolder(it) }
                updateMiniPlayer(currentPlaylist[currentSongIndex])
            },
            onAddToFavorites = { songItem -> /* Implement add to playlist functionality */  },
            onShowAlbum = { albumName -> /* Implement add to playlist functionality */  },
            onShowArtist = { artistName -> /* Implement add to playlist functionality */  }
        )
    }

    private fun updateFolderList() {
        try {
            val folderListView = homeScreen.findViewById<ListView>(R.id.folder_list_view)
            if (folderListView != null && folderListView.adapter != null) {
                folderItems.forEach { it.isCurrentFolder = it.folder.absolutePath == currentFolderPath }
                (folderListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
            }
        } catch (e: Exception) {
            // Log the error but don't crash the app
            Log.e("AudioFlow", "Error updating folder list: ${e.message}")
        }
    }


    fun updateMiniPlayer(song: SongItem) {
        val updateMiniPlayerView = { view: View ->
            view.findViewById<TextView>(R.id.mini_player_title).text = song.title
            view.findViewById<TextView>(R.id.mini_player_artist).text = song.artist
            view.visibility = View.VISIBLE

            // Update album art
            val albumArt = audioMetadataRetriever.getAlbumArt(song.file.absolutePath)
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
                showPlayerScreen()
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

    private fun updateMiniPlayerProgress(progress: Float) {
        val miniPlayerPlayPause = homeScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)
        miniPlayerPlayPause?.setProgress(progress)

        val songsScreenMiniPlayerPlayPause = songsScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)
        songsScreenMiniPlayerPlayPause?.setProgress(progress)
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
        val lastPlayedPath = sharedPreferences.getString("lastPlayedPath", null)

        if (lastPlayedPath != null) {
            val file = File(lastPlayedPath)
            if (file.exists()) {
                try {
                    val lastPlayedTitle = sharedPreferences.getString("lastPlayedTitle", null) ?: file.nameWithoutExtension
                    val lastPlayedArtist = sharedPreferences.getString("lastPlayedArtist", null) ?: "Unknown Artist"
                    val lastPlayedAlbum = sharedPreferences.getString("lastPlayedAlbum", null) ?: "Unknown Album"
                    currentFolderPath = sharedPreferences.getString("currentFolderPath", null)
                    currentSongIndex = sharedPreferences.getInt("currentSongIndex", -1)

                    lastPlayedSong = SongItem(file, lastPlayedTitle, lastPlayedArtist, lastPlayedAlbum)
                    currentFolderPath?.let { loadSongsInFolder(File(it)) }
                    currentPlaylist = currentSongs.toList()
                    updateMiniPlayer(lastPlayedSong!!)

                    homeScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE
                    songsScreen.findViewById<View>(R.id.mini_player)?.visibility = View.VISIBLE

                    // Prepare the MediaPlayer with the last played song
                    val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer.create(this, uri)
                    setupMediaPlayerCompletionListener()

                    // Initialize the player screen
                    updatePlayerUI(lastPlayedSong!!)

                    val miniPlayerClickListener = View.OnClickListener {
                        showScreen(playerScreen)
                        playerScreen.visibility = View.VISIBLE
                    }
                    homeScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener(miniPlayerClickListener)
                    songsScreen.findViewById<View>(R.id.mini_player)?.setOnClickListener(miniPlayerClickListener)
                } catch (e: Exception) {
                    Log.e("AudioFlow", "Error restoring last played song", e)
                    // Clear the last played song data if there's an error
                    clearLastPlayedSongData()
                }
            } else {
                // File doesn't exist, clear the last played song data
                clearLastPlayedSongData()
            }
        }
    }

    private fun clearLastPlayedSongData() {
        val sharedPreferences = getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            remove("lastPlayedTitle")
            remove("lastPlayedArtist")
            remove("lastPlayedAlbum")
            remove("lastPlayedPath")
            remove("currentFolderPath")
            remove("currentSongIndex")
            apply()
        }
        lastPlayedSong = null
        currentFolderPath = null
        currentSongIndex = -1
    }

    private fun setupMediaPlayerCompletionListener() {
        mediaPlayer?.setOnCompletionListener {
            if (repeatManager.onSongFinished()) {
                mediaPlayer?.start()
            } else {
                when (currentPlayMode) {
                    PlayMode.NORMAL -> {
                        if (currentSongIndex < currentPlaylist.size - 1) {
                            playNextSong()
                        } else {
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
    }

    fun playSong(position: Int, showPlayerScreen: Boolean = true) {
        if (position < 0 || position >= currentPlaylist.size) return

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        currentSongIndex = position
        val song = currentPlaylist[position]

        try {
            if (!song.file.exists()) {
                throw FileNotFoundException("The audio file does not exist")
            }

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                song.file
            )
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, uri)
            playerOptionsManager.updateMediaPlayer(mediaPlayer)

            if (mediaPlayer == null) {
                throw IOException("Failed to create MediaPlayer for this file")
            }

            mediaPlayer?.start()

            setupMediaPlayerCompletionListener()

            if (showPlayerScreen) {
                showScreen(playerScreen)
                findViewById<View>(R.id.player_view_container).visibility = View.VISIBLE
            }

            updatePlayerUI(song)
            if (showPlayerScreen) {
                updateFavoriteButton()
            }

            repeatManager.reset()

            updateMiniPlayer(song)
            updateMiniPlayerProgress(0f)

            // Current Folder Name Showing
            if (!intent.getBooleanExtra("FROM_FAVORITES", false)) {
                currentFolderPath = song.file.parent
                updateFolderList()
            }

            // Refresh the song list view to show the current song icon
            refreshSongList()

        } catch (e: Exception) {
            when (e) {
                is FileNotFoundException, is IOException -> {
                    Log.e("AudioFlow", "Error playing song: ${e.message}", e)
                    Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
                    removeCurrentSongAndPlayNext()
                }
                else -> {
                    Log.e("AudioFlow", "Unexpected error playing song", e)
                    Toast.makeText(this, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        mediaPlayerService?.initializePlayer(mediaPlayer!!)
        mediaPlayerService?.updateMetadata(song)
    }

    private fun removeCurrentSongAndPlayNext() {
        if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            currentPlaylist = currentPlaylist.filterIndexed { index, _ -> index != currentSongIndex }
            if (currentPlaylist.isNotEmpty()) {
                currentSongIndex = currentSongIndex.coerceAtMost(currentPlaylist.size - 1)
                playSong(currentSongIndex)
            } else {
                // No more songs in the playlist
                mediaPlayer?.release()
                mediaPlayer = null
                updatePlayerUI(SongItem(File(""), "No songs available", "", ""))
                Toast.makeText(this, "No more songs available", Toast.LENGTH_SHORT).show()
            }
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

    fun updatePlayerUI(song: SongItem) {
        playerSongTitleTextView.text = song.title.takeIf { it.isNotBlank() } ?: "Unknown Title"
        artistNameTextView.text = song.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        updatePlayPauseButton()

        // Update seek bar
        mediaPlayer?.let { player ->
            seekBar.max = player.duration
            seekBar.progress = 0
            updateSeekBar()

            totalTimeTextView.text = formatTime(player.duration)
            currentTimeTextView.text = formatTime(0)
        }

        // Set album art
        val albumArt = audioMetadataRetriever.getAlbumArt(song.file.absolutePath)
        if (albumArt != null) {
            albumArtImageView.setImageBitmap(albumArt)

            albumArtImageView.post {
                colorManager.updateBackgroundWithAlbumArt(this, albumArtImageView)
            }
        } else {
            albumArtImageView.setImageResource(R.drawable.cover_art)

            albumArtImageView.post {
                colorManager.updateBackgroundWithAlbumArt(this, albumArtImageView)
            }
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
                // Maybe later adding something here :)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Maybe later adding something here :)
            }
        })
    }

    private fun updateSeekBar() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {  // Only update if the player is actually playing
                    seekBar.progress = player.currentPosition
                    currentTimeTextView.text = formatTime(player.currentPosition)

                    // Update mini player progress
                    val progress = player.currentPosition.toFloat() / player.duration.toFloat()
                    updateMiniPlayerProgress(progress)

                    seekBar.postDelayed({ updateSeekBar() }, 1000)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating seekbar: ${e.message}")
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    fun updatePlayPauseButton() {
        val isPlaying = mediaPlayer?.isPlaying == true
        val resource = if (isPlaying) R.drawable.pause_button else R.drawable.play_button
        val miniResource = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        playPauseButton.setImageResource(resource)
        homeScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)?.setImageResource(miniResource)
        songsScreen.findViewById<CircularProgressButton>(R.id.mini_player_play_pause)?.setImageResource(miniResource)
    }

    fun togglePlayPause() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.start()
                    setupMediaPlayerCompletionListener()
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


    fun playPreviousSong() {
        if (currentPlaylist.isNotEmpty()) {
            val currentPosition = mediaPlayer?.currentPosition ?: 0
            val totalDuration = mediaPlayer?.duration ?: 0

            if (settingsManager.isResetPreviousEnabled() &&
                currentPosition > 10000 && // 10 seconds
                totalDuration > 30000 // 30 seconds
            ) {
                mediaPlayer?.seekTo(0)
                updateSeekBar()
            } else {
                when (currentPlayMode) {
                    PlayMode.NORMAL -> {
                        if (currentSongIndex > 0) {
                            currentSongIndex--
                            playSong(currentSongIndex)
                        } else {
                            Toast.makeText(this, "This is the first song", Toast.LENGTH_SHORT).show()
                        }
                    }
                    PlayMode.SHUFFLE -> {
                        // Implement shuffled previous song logic
                        currentSongIndex = if (shuffleHistory.isNotEmpty()) {
                            shuffleHistory.removeLast()
                        } else {
                            // Play a random previous song if there's no history
                            (0 until currentPlaylist.size).random()
                        }
                        playSong(currentSongIndex)
                    }
                    else -> {
                        currentSongIndex = if (currentSongIndex > 0) currentSongIndex - 1 else currentPlaylist.size - 1
                        playSong(currentSongIndex)
                    }
                }
            }
        }
    }

    private fun playRandomSong() {
        if (currentPlaylist.isEmpty()) return

        val randomIndex = (currentSongIndex + 1 + Random().nextInt(currentPlaylist.size - 1)) % currentPlaylist.size
        playSong(randomIndex)
    }

    fun playNextSong(showPlayerScreen: Boolean = true) {
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
                    shuffleHistory.add(currentSongIndex)
                    currentSongIndex = (currentSongIndex + 1) % currentPlaylist.size
                    playSong(currentSongIndex, showPlayerScreen)
                }
                PlayMode.SHUFFLE -> {
                    shuffleHistory.add(currentSongIndex)
                    currentSongIndex = (0 until currentPlaylist.size).random()
                    playSong(currentSongIndex, showPlayerScreen)
                }
            }
        } else {
            Log.e("AudioFlow", "No songs available to play")
            Toast.makeText(this, "No songs available to play", Toast.LENGTH_SHORT).show()
        }
    }

    fun addFolderToList(folderUri: Uri) {
        loadMusicFolders()
    }

    fun playSingleAudioFile(uri: Uri) {
        try {
            // Stop the previous song
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            // Get file details
            val fileName = audioMetadataRetriever.getFileName(uri)
            val mimeType = contentResolver.getType(uri)

            // Create a temporary file in the app's cache directory
            val cacheDir = externalCacheDir
            val tempFile = File(cacheDir, "temp_audio.${mimeType?.substringAfter('/') ?: "mp3"}")

            // Copy content from Uri to the temporary file
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Create SongItem
            val song = SongItem(tempFile, fileName, "Unknown Artist", "Unknown Album")

            // Update currentPlaylist and currentSongIndex
            currentPlaylist = listOf(song)
            currentSongIndex = 0

            // Play the song
            playSong(0)

            // Update UI
            updateMiniPlayer(song)
            showPlayerScreen()
        } catch (e: Exception) {
            Log.e("AudioFlow", "Error playing single audio file: ${e.message}", e)
            Toast.makeText(this, "Error playing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        homeScreenManager.handleActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedSongUri = uri
                songTitleTextView.text = audioMetadataRetriever.getFileName(uri)
                playButton.isEnabled = true
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
    }

    override fun onBackPressed() {
        when {
            playerOptionsManager.isOverlayVisible() -> {
                playerOptionsManager.hideOverlay()
            }
            songOptionsFooter.isVisible -> {
                songOptionsHandler.hideFooter()
            }
            contentFrame.getChildAt(0) == playerScreen -> {
                if (intent.getBooleanExtra("FROM_FAVORITES", false)) {
                    // Go back to FavoritesActivity
                    showScreen(songsScreen)
                    hidePlayerScreen()
                    val intent = Intent(this, FavoritesActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Go back to songs screen
                    showScreen(songsScreen)
                    hidePlayerScreen()
                }
            }
            contentFrame.getChildAt(0) == songsScreen -> {
                if (intent.getBooleanExtra("FROM_FAVORITES", false)) {
                    // Go back to home screen when in favorites
                    showScreen(homeScreen)
                    songOptionsFooter.isVisible = false
                    finish()
                } else {
                    // Go back to home screen
                    showScreen(homeScreen)
                    songOptionsFooter.isVisible = false
                }
            }
            contentFrame.getChildAt(0) == settingsScreen -> {
                showScreen(homeScreen)
            }
            contentFrame.getChildAt(0) == aboutScreen -> {
                showScreen(settingsScreen)
            }
            contentFrame.getChildAt(0) == homeScreen -> {
                // If we're on the home screen, exit the app
                super.onBackPressed()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        colorManager.applyColorToActivity(this)
        colorManager.handleActivityResume(this)
        loadMusicFolders()
        lastPlayedSong?.let { updateMiniPlayer(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        settingsManager.cleanup()
        favoriteManager.removeFavoriteChangeListener { updateFavoriteCount() }
        coverStyleCustomizer?.cleanup()
        mediaPlayer?.release()
        lastPlayedSong?.let { saveLastPlayedSong(it) }
        savePlayMode()
    }

    data class FolderItem(val folder: File, val name: String, val songCount: String, var isCurrentFolder: Boolean = false)
}


// Add Play Song next button
// add smoother animations to app

// show album func
// show artist func
// search func for songs list

// fix favorite songs list
// player screen showing empty list
// no songs but fav container visible

// Full width/expand width bugging when cover customizer open
// rotate feature fixen (hopefully)

// short lag, playback after skipping some songs

// bottom blurry always visible and not visible when mode switching and blurrier
// updating buttons when playback

// search screen
// improve search filter (maybe)

// info screen
// maybe add color change to more screen and optional
// sound changes
// maybe driver mode

// settings screen
// Maybe add small infos about a song (where you found it, who told you of it)