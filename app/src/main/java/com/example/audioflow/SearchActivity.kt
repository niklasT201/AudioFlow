package com.example.audioflow

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.provider.MediaStore
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import com.example.audioflow.AudioMetadataRetriever.SongItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SearchActivity : AppCompatActivity() {
    private lateinit var searchView: SearchView
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var searchResultsContainer: FrameLayout
    private lateinit var artistsRecyclerView: RecyclerView
    private lateinit var albumsRecyclerView: RecyclerView
    private lateinit var songsRecyclerView: RecyclerView
    private lateinit var showAllArtistsButton: Button
    private lateinit var showAllAlbumsButton: Button
    private lateinit var showAllSongsButton: Button
    private lateinit var allSongs: MutableList<SongItem>
    private lateinit var artistAdapter: ArtistAdapter
    private lateinit var albumAdapter: AlbumAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var artistsHeader: TextView
    private lateinit var albumsHeader: TextView
    private lateinit var songsHeader: TextView
    private lateinit var albumArtCache: LruCache<String, Bitmap>
    private val songsLiveData = MutableLiveData<List<SongItem>>()
    private lateinit var contentObserver: ContentObserver
    private var lastSearchQuery: String = ""
    private val navigationStack = mutableListOf<() -> Unit>()

    private lateinit var searchResultsList: RecyclerView

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingSong: SongItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_screen)

        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        albumArtCache = LruCache(cacheSize)

        searchView = findViewById(R.id.searchView)
        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        searchResultsContainer = findViewById(R.id.searchResultsContainer)
        searchResultsList = findViewById(R.id.searchResultsList)

        // Initialize views from the included layout
        artistsRecyclerView = findViewById(R.id.artistsRecyclerView)
        albumsRecyclerView = findViewById(R.id.albumsRecyclerView)
        songsRecyclerView = findViewById(R.id.songsRecyclerView)
        showAllArtistsButton = findViewById(R.id.showAllArtistsButton)
        showAllAlbumsButton = findViewById(R.id.showAllAlbumsButton)
        artistsHeader = findViewById(R.id.artistsHeader)
        albumsHeader = findViewById(R.id.albumsHeader)
        songsHeader = findViewById(R.id.songsHeader)
        showAllSongsButton = findViewById(R.id.showAllSongsButton)

        setupSearchView()

        allSongs = getAllSongs().toMutableList()

        artistsRecyclerView.layoutManager = LinearLayoutManager(this)
        albumsRecyclerView.layoutManager = LinearLayoutManager(this)
        songsRecyclerView.layoutManager = LinearLayoutManager(this)

        artistAdapter = ArtistAdapter(emptyList()) { artist -> showArtistDetails(artist) }
        albumAdapter = AlbumAdapter(emptyList()) { album -> showAlbumDetails(album) }
        songAdapter = SongAdapter(
            emptyList(),
            { song -> handleSongClick(song) },
            { song -> showAddToPlaylistDialog(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache,
            shouldShowCovers()
        )

        artistsRecyclerView.adapter = artistAdapter
        albumsRecyclerView.adapter = albumAdapter
        songsRecyclerView.adapter = songAdapter


        setupRecyclerView()
        setupSearchView()
        setupModeRadioGroup()
        setupContentObserver()

        // Restore last search query if it exists
        savedInstanceState?.let {
            lastSearchQuery = it.getString("lastSearchQuery", "")
            searchView.setQuery(lastSearchQuery, false)
        }

        // Load all songs immediately
        loadAllSongs()
    }

    override fun onBackPressed() {
        if (navigationStack.size > 1) {
            // Remove the current state
            navigationStack.removeAt(navigationStack.size - 1)
            // Execute the previous state
            navigationStack.last().invoke()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("lastSearchQuery", lastSearchQuery)
    }

    override fun onResume() {
        super.onResume()
        // Reapply the last search when returning to this activity
        if (lastSearchQuery.isNotEmpty()) {
            performSearch(lastSearchQuery)
        }
    }

    private fun shouldShowCovers(): Boolean {
        return getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
            .getBoolean("show_covers", true)
    }

    private fun setupRecyclerView() {
        searchResultsList.layoutManager = LinearLayoutManager(this)
        songAdapter = SongAdapter(
            emptyList(),
            { song -> handleSongClick(song) },
            { song -> showAddToPlaylistDialog(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache,
            shouldShowCovers()
        )
        searchResultsList.adapter = songAdapter
    }

    private fun loadAllSongs() {
        lifecycleScope.launch(Dispatchers.IO) {
            allSongs = getAllSongs()
            Log.d("SearchActivity", "Loaded ${allSongs.size} songs")
            withContext(Dispatchers.Main) {
                showAllSongs()
            }
        }
    }

    private fun setupContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                songsLiveData.postValue(getAllSongs())
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentSong()
        contentResolver.unregisterContentObserver(contentObserver)
    }


    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                lastSearchQuery = newText ?: ""
                if (newText.isNullOrBlank()) {
                    showAllSongs()
                } else {
                    performSearch(newText)
                }
                return true
            }
        })
    }

    private fun setupModeRadioGroup() {
        modeRadioGroup.setOnCheckedChangeListener { _, _ ->
            // Refresh the adapters to update the click behavior
            artistAdapter.notifyDataSetChanged()
            albumAdapter.notifyDataSetChanged()
            songAdapter.notifyDataSetChanged()
        }
    }

    private fun performSearch(query: String?) {
        if (query.isNullOrBlank()) {
            showAllSongs()
            return
        }

        lastSearchQuery = query

        // Reset the search state
        searchResultsContainer.removeAllViews()
        searchResultsContainer.visibility = View.VISIBLE
        searchResultsList.visibility = View.GONE

        val lowercaseQuery = query.lowercase()

        val allMatchingArtists = allSongs.map { it.artist }.distinct()
            .filter { it.lowercase().contains(lowercaseQuery) }
        val allMatchingAlbums = allSongs.map { it.album }.distinct()
            .filter { it.lowercase().contains(lowercaseQuery) }
        val allMatchingSongs = allSongs.filter { song ->
            song.title.lowercase().contains(lowercaseQuery)
        }

        // Inflate the search_results layout
        val searchResultsView = layoutInflater.inflate(R.layout.search_results, null)

        // Set up the RecyclerViews
        val artistsRecyclerView = searchResultsView.findViewById<RecyclerView>(R.id.artistsRecyclerView)
        val albumsRecyclerView = searchResultsView.findViewById<RecyclerView>(R.id.albumsRecyclerView)
        val songsRecyclerView = searchResultsView.findViewById<RecyclerView>(R.id.songsRecyclerView)

        // Get the buttons
        val showAllArtistsButtonNew = searchResultsView.findViewById<Button>(R.id.showAllArtistsButton)
        val showAllAlbumsButtonNew = searchResultsView.findViewById<Button>(R.id.showAllAlbumsButton)
        val showAllSongsButtonNew = searchResultsView.findViewById<Button>(R.id.showAllSongsButton)

        artistsRecyclerView.layoutManager = LinearLayoutManager(this)
        albumsRecyclerView.layoutManager = LinearLayoutManager(this)
        songsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set up the adapters
        artistAdapter = ArtistAdapter(allMatchingArtists) { artist -> showArtistDetails(artist) }
        albumAdapter = AlbumAdapter(allMatchingAlbums) { album -> showAlbumDetails(album) }
        songAdapter = SongAdapter(
            allMatchingSongs.take(5),
            { song -> handleSongClick(song) },
            { song -> showAddToPlaylistDialog(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache,
            shouldShowCovers()
        )

        artistsRecyclerView.adapter = artistAdapter
        albumsRecyclerView.adapter = albumAdapter
        songsRecyclerView.adapter = songAdapter

        // Set up click listeners for the new buttons
        showAllArtistsButtonNew.setOnClickListener {
            artistAdapter.updateData(allMatchingArtists)
            showAllArtistsButtonNew.visibility = View.GONE
        }

        showAllAlbumsButtonNew.setOnClickListener {
            albumAdapter.updateData(allMatchingAlbums)
            showAllAlbumsButtonNew.visibility = View.GONE
        }

        showAllSongsButtonNew.setOnClickListener {
            songAdapter.updateData(allMatchingSongs)
            showAllSongsButtonNew.visibility = View.GONE
        }

        // Show/hide sections based on search results
        searchResultsView.findViewById<TextView>(R.id.artistsHeader).visibility =
            if (allMatchingArtists.isNotEmpty()) View.VISIBLE else View.GONE
        artistsRecyclerView.visibility =
            if (allMatchingArtists.isNotEmpty()) View.VISIBLE else View.GONE
        showAllArtistsButtonNew.visibility =
            if (allMatchingArtists.size > 5) View.VISIBLE else View.GONE

        searchResultsView.findViewById<TextView>(R.id.albumsHeader).visibility =
            if (allMatchingAlbums.isNotEmpty()) View.VISIBLE else View.GONE
        albumsRecyclerView.visibility =
            if (allMatchingAlbums.isNotEmpty()) View.VISIBLE else View.GONE
        showAllAlbumsButtonNew.visibility =
            if (allMatchingAlbums.size > 5) View.VISIBLE else View.GONE

        searchResultsView.findViewById<TextView>(R.id.songsHeader).visibility =
            if (allMatchingSongs.isNotEmpty()) View.VISIBLE else View.GONE
        songsRecyclerView.visibility =
            if (allMatchingSongs.isNotEmpty()) View.VISIBLE else View.GONE
        showAllSongsButtonNew.visibility =
            if (allMatchingSongs.size > 5) View.VISIBLE else View.GONE

        // Update the searchResultsContainer
        searchResultsContainer.addView(searchResultsView)

        navigationStack.clear()
        navigationStack.add { performSearch(query) }

        Log.d("SearchActivity", "Search performed. Artists: ${allMatchingArtists.size}, Albums: ${allMatchingAlbums.size}, Songs: ${allMatchingSongs.size}")
    }

    private fun showAllSongs() {
        Log.d("SearchActivity", "Showing all songs")
        searchResultsList.visibility = View.VISIBLE
        searchResultsContainer.visibility = View.GONE
        songAdapter.updateData(allSongs)
        Log.d("SearchActivity", "Updated adapter with ${allSongs.size} songs")
        showAllSongsButton.visibility = View.GONE
    }

    private fun showArtistDetails(artist: String) {
        val artistSongs = allSongs.filter { it.artist == artist }
        val artistAlbums = artistSongs.map { it.album }.distinct()

        // Inflate the artist_detail layout
        val artistDetailView = layoutInflater.inflate(R.layout.artist_detail, null)

        // Set up the RecyclerViews
        val albumsRecyclerView = artistDetailView.findViewById<RecyclerView>(R.id.artistAlbumsRecyclerView)
        val songsRecyclerView = artistDetailView.findViewById<RecyclerView>(R.id.artistSongsRecyclerView)

        albumsRecyclerView.layoutManager = LinearLayoutManager(this)
        songsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set up the adapters
        val albumAdapter = AlbumAdapter(artistAlbums) { album -> showAlbumDetails(album) }
        val songAdapter = SongAdapter(
            artistSongs,
            { song -> handleSongClick(song) },
            { song -> showAddToPlaylistDialog(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache,
            shouldShowCovers()
        )

        albumsRecyclerView.adapter = albumAdapter
        songsRecyclerView.adapter = songAdapter

        // Set the artist name
        artistDetailView.findViewById<TextView>(R.id.artistNameTextView).text = artist

        // Update the searchResultsList
        searchResultsList.adapter = null
        searchResultsContainer.removeAllViews()
        searchResultsContainer.addView(artistDetailView)
        searchResultsContainer.visibility = View.VISIBLE
        searchResultsList.visibility = View.GONE

        navigationStack.add { showArtistDetails(artist) }

        Log.d("SearchActivity", "Showing details for artist: $artist. Found ${artistSongs.size} songs and ${artistAlbums.size} albums.")
    }

    private fun showAlbumDetails(album: String) {
        val albumSongs = allSongs.filter { it.album == album }

        // Inflate the search_results layout
        val albumDetailView = layoutInflater.inflate(R.layout.album_detail, null)

        // Set up the RecyclerView
        val songsRecyclerView = albumDetailView.findViewById<RecyclerView>(R.id.albumSongsRecyclerView)
        songsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set up the adapter
        songAdapter = SongAdapter(
            emptyList(),
            { song -> handleSongClick(song) },
            { song -> showAddToPlaylistDialog(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache,
            shouldShowCovers()
        )

        songsRecyclerView.adapter = songAdapter

        // Set the album name
        albumDetailView.findViewById<TextView>(R.id.albumNameTextView).text = album
        albumDetailView.findViewById<TextView>(R.id.songsHeader).text = "Songs in $album"

        // Update the searchResultsList
        searchResultsList.adapter = null
        searchResultsContainer.removeAllViews()
        searchResultsContainer.addView(albumDetailView)
        searchResultsContainer.visibility = View.VISIBLE
        searchResultsList.visibility = View.GONE

        navigationStack.add { showAlbumDetails(album) }

        Log.d("SearchActivity", "Showing details for album: $album. Found ${albumSongs.size} songs.")
    }

    private fun handleSongClick(song: SongItem) {
        if (modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio) {
            // If the same song is clicked again, stop it
            if (currentlyPlayingSong?.file?.absolutePath == song.file.absolutePath) {
                stopCurrentSong()
            } else {
                playSong(song)
            }
        } else {
            editSongMetadata(song)
        }
    }

    private fun getAllSongs(): MutableList<SongItem> {
        val songs = mutableListOf<SongItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val data = cursor.getString(dataColumn)

                songs.add(SongItem(File(data), title, artist, album))
            }
        }

        return songs
    }

    private fun playSong(song: SongItem) {
        try {
            stopCurrentSong()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(song.file.absolutePath)
                prepare()
                start()
            }

            currentlyPlayingSong = song

            // Show a toast to indicate which song is playing
            Toast.makeText(this, "Now playing: ${song.title}", Toast.LENGTH_SHORT).show()

            // Set up completion listener to clean up resources
            mediaPlayer?.setOnCompletionListener {
                stopCurrentSong()
            }

        } catch (e: Exception) {
            Log.e("SearchActivity", "Error playing song: ${e.message}")
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCurrentSong() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
            mediaPlayer = null
            currentlyPlayingSong = null
            Toast.makeText(this, "Playback stopped", Toast.LENGTH_SHORT).show()
        }
    }


    private fun editSongMetadata(song: SongItem) {
        val intent = Intent(this, EditMetadataActivity::class.java)
        intent.putExtra("songPath", song.file.absolutePath)
        intent.putExtra("songTitle", song.title)
        intent.putExtra("songArtist", song.artist)
        intent.putExtra("songAlbum", song.album)
        startActivityForResult(intent, EDIT_METADATA_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_METADATA_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->
                val updatedSongPath = intent.getStringExtra("updatedSongPath")
                val updatedTitle = intent.getStringExtra("updatedTitle")
                val updatedArtist = intent.getStringExtra("updatedArtist")
                val updatedAlbum = intent.getStringExtra("updatedAlbum")

                updatedSongPath?.let { path ->
                    updateLocalSongData(path, updatedTitle, updatedArtist, updatedAlbum)
                    updateMediaStore(path, updatedTitle, updatedArtist, updatedAlbum)
                    refreshMediaStore(path)
                }
            }
            // Refresh the song list and reapply the last search
            refreshSongList()
        }
    }

    private fun refreshSongList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedSongs = getAllSongs()
            withContext(Dispatchers.Main) {
                allSongs = updatedSongs.toMutableList()
                performSearch(lastSearchQuery)
            }
        }
    }

    private fun updateLocalSongData(path: String, title: String?, artist: String?, album: String?) {
        val index = allSongs.indexOfFirst { it.file.absolutePath == path }
        if (index != -1) {
            val updatedSong = allSongs[index].copy(
                title = title ?: allSongs[index].title,
                artist = artist ?: allSongs[index].artist,
                album = album ?: allSongs[index].album
            )
            allSongs[index] = updatedSong
            performSearch(searchView.query.toString())
        }
    }

    private fun updateMediaStore(path: String, title: String?, artist: String?, album: String?) {
        val resolver: ContentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(path)

        val values = ContentValues().apply {
            title?.let { put(MediaStore.Audio.Media.TITLE, it) }
            artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
            album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
        }

        resolver.update(uri, values, selection, selectionArgs)
    }

    private fun refreshMediaStore(path: String) {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(path),
            null
        ) { _, uri ->
            runOnUiThread {
                uri?.let {
                    contentResolver.notifyChange(it, null)
                }
            }
        }
    }

    private fun showAddToPlaylistDialog(song: SongItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_to_playlist, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.folderRecyclerView)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val cancelButton = dialogView.findViewById<Button>(R.id.dialog_button_cancel)

        titleTextView.text = "Add '${song.title}' to Playlist"

        val dialog = AlertDialog.Builder(this, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .create()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val audioFolders = getAudioFolders()
            withContext(Dispatchers.Main) {
                val adapter = FolderAdapter(audioFolders) { selectedFolder ->
                    dialog.dismiss()
                    confirmAddToPlaylist(song, selectedFolder)
                }
                recyclerView.adapter = adapter
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun getAudioFolders(): List<File> {
        val audioFolders = mutableSetOf<File>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATA} ASC"

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
                audioFolders.add(file.parentFile)
            }
        }

        return audioFolders.toList()
    }

    private fun File.isAudioFile(): Boolean {
        val audioExtensions = listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac")
        return audioExtensions.any { this.name.endsWith(it, ignoreCase = true) }
    }

    private fun confirmAddToPlaylist(song: SongItem, folder: File) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_add_to_playlist, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val yesButton = dialogView.findViewById<Button>(R.id.dialog_button_yes)
        val noButton = dialogView.findViewById<Button>(R.id.dialog_button_no)

        titleTextView.text = "Confirm"
        messageTextView.text = "Do you want to add '${song.title}' to '${folder.name}'?"

        val dialog = AlertDialog.Builder(this, R.style.TransparentAlertDialog)
            .setView(dialogView)
            .create()

        yesButton.setOnClickListener {
            dialog.dismiss()
            copySongToFolder(song, folder)
        }

        noButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun copySongToFolder(song: SongItem, folder: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val destFile = File(folder, song.file.name)
                song.file.copyTo(destFile, overwrite = true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SearchActivity, "Song added to playlist", Toast.LENGTH_SHORT).show()
                    refreshMediaStore(destFile.absolutePath)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SearchActivity, "Error adding song to playlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val EDIT_METADATA_REQUEST = 1
    }
}

class ArtistAdapter(
    private var artists: List<String>,
    private val onArtistClick: (String) -> Unit
) : RecyclerView.Adapter<ArtistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = artists[position]
        holder.textView.text = artist
        holder.itemView.setOnClickListener {
            onArtistClick(artist)
        }
    }

    override fun getItemCount() = artists.size

    fun updateData(newArtists: List<String>) {
        artists = newArtists
        notifyDataSetChanged()
    }
}

class AlbumAdapter(
    private var albums: List<String>,
    private val onAlbumClick: (String) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums[position]
        holder.textView.text = album
        holder.itemView.setOnClickListener {
            onAlbumClick(album)
        }
    }

    override fun getItemCount() = albums.size

    fun updateData(newAlbums: List<String>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}

class SongAdapter(
    private var songs: List<SongItem>,
    private val onSongClick: (SongItem) -> Unit,
    private val onAddToPlaylistClick: (SongItem) -> Unit,
    private val getPlayMode: () -> Boolean,
    private val albumArtCache: LruCache<String, Bitmap>,
    private val showCovers: Boolean
) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val albumCover: ImageView = view.findViewById(R.id.albumCover)
        val titleTextView: TextView = view.findViewById(R.id.songTitle)
        val artistAlbumTextView: TextView = view.findViewById(R.id.artistAlbum)
        val actionButton: ImageButton = view.findViewById(R.id.actionButton)
        val addToPlaylistButton: ImageButton = view.findViewById(R.id.addToPlaylistButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_song_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs[position]
        holder.titleTextView.text = song.title
        holder.artistAlbumTextView.text = "${song.artist} - ${song.album}"

        // Control visibility of album cover
        if (showCovers) {
            holder.albumCover.visibility = View.VISIBLE
            // Set default placeholder
            holder.albumCover.setImageResource(R.drawable.cover_art)
            // Load album art
            LoadAlbumArtTask(holder.albumCover, albumArtCache).execute(song.file.absolutePath)
        } else {
            holder.albumCover.visibility = View.GONE
        }

        val isPlayMode = getPlayMode()
        holder.actionButton.setImageResource(
            if (isPlayMode) R.drawable.play_button
            else R.drawable.edit_button
        )

        holder.actionButton.setOnClickListener { onSongClick(song) }
        holder.itemView.setOnClickListener { onSongClick(song) }
        holder.addToPlaylistButton.setOnClickListener { onAddToPlaylistClick(song) }
    }

    override fun getItemCount() = songs.size

    fun updateData(newSongs: List<SongItem>) {
        Log.d("SongAdapter", "Updating data with ${newSongs.size} songs")
        songs = newSongs
        notifyDataSetChanged()
    }
}

class FolderAdapter(
    private val folders: List<File>,
    private val onFolderClick: (File) -> Unit
) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.folder_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.folderName.text = folder.name
        holder.itemView.setOnClickListener { onFolderClick(folder) }
    }

    override fun getItemCount() = folders.size
}
