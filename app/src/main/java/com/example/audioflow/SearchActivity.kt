package com.example.audioflow

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.provider.MediaStore
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
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

    private lateinit var searchResultsList: RecyclerView

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
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache
        )

        artistsRecyclerView.adapter = artistAdapter
        albumsRecyclerView.adapter = albumAdapter
        songsRecyclerView.adapter = songAdapter


        setupRecyclerView()
        setupSearchView()
        setupModeRadioGroup()
        setupShowAllButtons()
        setupContentObserver()

        // Restore last search query if it exists
        savedInstanceState?.let {
            lastSearchQuery = it.getString("lastSearchQuery", "")
            searchView.setQuery(lastSearchQuery, false)
        }

        // Load all songs immediately
        loadAllSongs()
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

    private fun setupRecyclerView() {
        searchResultsList.layoutManager = LinearLayoutManager(this)
        songAdapter = SongAdapter(
            emptyList(),
            { song -> handleSongClick(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio },
            albumArtCache
        )
        searchResultsList.adapter = songAdapter
        Log.d("SearchActivity", "RecyclerView setup complete")
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

        searchResultsList.visibility = View.GONE
        searchResultsContainer.visibility = View.VISIBLE

        val lowercaseQuery = query.lowercase()

        val matchingArtists = allSongs.map { it.artist }.distinct().filter { it.lowercase().contains(lowercaseQuery) }
        val matchingAlbums = allSongs.map { it.album }.distinct().filter { it.lowercase().contains(lowercaseQuery) }
        val matchingSongs = allSongs.filter { song ->
            song.title.lowercase().contains(lowercaseQuery)
        }

        updateArtistsList(matchingArtists)
        updateAlbumsList(matchingAlbums)
        updateSongsList(matchingSongs)

        // Show headers
        artistsHeader.visibility = if (matchingArtists.isNotEmpty()) View.VISIBLE else View.GONE
        albumsHeader.visibility = if (matchingAlbums.isNotEmpty()) View.VISIBLE else View.GONE
        songsHeader.visibility = if (matchingSongs.isNotEmpty()) View.VISIBLE else View.GONE

        // Show/hide sections based on search results
        artistsRecyclerView.visibility = if (matchingArtists.isNotEmpty()) View.VISIBLE else View.GONE
        albumsRecyclerView.visibility = if (matchingAlbums.isNotEmpty()) View.VISIBLE else View.GONE
        songsRecyclerView.visibility = if (matchingSongs.isNotEmpty()) View.VISIBLE else View.GONE

        Log.d("SearchActivity", "Search performed. Artists: ${matchingArtists.size}, Albums: ${matchingAlbums.size}, Songs: ${matchingSongs.size}")
    }

    private fun updateArtistsList(artists: List<String>) {
        artistsRecyclerView.visibility = if (artists.isNotEmpty()) View.VISIBLE else View.GONE
        artistAdapter.updateData(artists.take(5))
        showAllArtistsButton.visibility = if (artists.size > 5) View.VISIBLE else View.GONE
    }

    private fun updateAlbumsList(albums: List<String>) {
        albumsRecyclerView.visibility = if (albums.isNotEmpty()) View.VISIBLE else View.GONE
        albumAdapter.updateData(albums.take(5))
        showAllAlbumsButton.visibility = if (albums.size > 5) View.VISIBLE else View.GONE
    }

    private fun updateSongsList(songs: List<SongItem>) {
        songsRecyclerView.visibility = if (songs.isNotEmpty()) View.VISIBLE else View.GONE
        (songsRecyclerView.adapter as? SongAdapter)?.updateData(songs.take(5))
        showAllSongsButton.visibility = if (songs.size > 5) View.VISIBLE else View.GONE
        Log.d("SearchActivity", "Updated songs list. Showing ${songs.take(5).size} out of ${songs.size} songs")
    }

    private fun showAllSongs() {
        Log.d("SearchActivity", "Showing all songs")
        searchResultsList.visibility = View.VISIBLE
        searchResultsContainer.visibility = View.GONE
        songAdapter.updateData(allSongs)
        Log.d("SearchActivity", "Updated adapter with ${allSongs.size} songs")
        showAllSongsButton.visibility = View.GONE
    }

    private fun showAllSongsResults() {
        val query = searchView.query.toString()
        val allMatchingSongs = allSongs.filter { song ->
            song.title.lowercase().contains(query.lowercase())
      //              song.artist.lowercase().contains(query.lowercase()) ||
       //             song.album.lowercase().contains(query.lowercase())
        }
        Log.d("SearchActivity", "Showing all songs. Found ${allMatchingSongs.size} matching songs")

        // Update the songsRecyclerView
        songsRecyclerView.visibility = View.VISIBLE
        (songsRecyclerView.adapter as? SongAdapter)?.updateData(allMatchingSongs)

        // Hide the "Show All Songs" button
        showAllSongsButton.visibility = View.GONE

        // Ensure the songs section is visible
        songsHeader.visibility = View.VISIBLE
    }

    private fun setupShowAllButtons() {
        showAllArtistsButton.setOnClickListener { showAllArtists() }
        showAllAlbumsButton.setOnClickListener { showAllAlbums() }
        showAllSongsButton.setOnClickListener { showAllSongsResults() }
    }

    private fun showAllArtists() {
        val query = searchView.query.toString()
        val allMatchingArtists = allSongs.map { it.artist }.distinct().filter { it.lowercase().contains(query.lowercase()) }
        artistAdapter.updateData(allMatchingArtists)
        showAllArtistsButton.visibility = View.GONE
    }

    private fun showAllAlbums() {
        val query = searchView.query.toString()
        val allMatchingAlbums = allSongs.map { it.album }.distinct().filter { it.lowercase().contains(query.lowercase()) }
        albumAdapter.updateData(allMatchingAlbums)
        showAllAlbumsButton.visibility = View.GONE
    }

    private fun showArtistDetails(artist: String) {
        val artistSongs = allSongs.filter { it.artist == artist }
        val artistAlbums = artistSongs.map { it.album }.distinct()

        // Update UI visibility
        artistsHeader.visibility = View.GONE
        artistsRecyclerView.visibility = View.GONE
        showAllArtistsButton.visibility = View.GONE

        albumsHeader.visibility = View.VISIBLE
        albumsRecyclerView.visibility = View.VISIBLE
        songsHeader.visibility = View.VISIBLE
        songsRecyclerView.visibility = View.VISIBLE

        // Update adapters
        albumAdapter.updateData(artistAlbums)
        songAdapter.updateData(artistSongs)

        // Update "Show All" buttons
        showAllAlbumsButton.visibility = if (artistAlbums.size > 5) View.VISIBLE else View.GONE
        showAllSongsButton.visibility = if (artistSongs.size > 5) View.VISIBLE else View.GONE
    }

    private fun showAlbumDetails(album: String) {
        val albumSongs = allSongs.filter { it.album == album }

        // Update UI visibility
        artistsHeader.visibility = View.GONE
        artistsRecyclerView.visibility = View.GONE
        showAllArtistsButton.visibility = View.GONE
        albumsHeader.visibility = View.GONE
        albumsRecyclerView.visibility = View.GONE
        showAllAlbumsButton.visibility = View.GONE

        songsHeader.visibility = View.VISIBLE
        songsRecyclerView.visibility = View.VISIBLE

        // Update adapter
        songAdapter.updateData(albumSongs)

        // Hide "Show All Songs" button as we're already showing all songs for this album
        showAllSongsButton.visibility = View.GONE
    }

    private fun handleSongClick(song: SongItem) {
        if (modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio) {
            playSong(song)
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
        // Implement this method to play the selected song
        // You can use the existing code from MainActivity to play a song
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
        holder.itemView.setOnClickListener { onArtistClick(artist) }
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
        holder.itemView.setOnClickListener { onAlbumClick(album) }
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
    private val getPlayMode: () -> Boolean,
    private val albumArtCache: LruCache<String, Bitmap>
) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val albumCover: ImageView = view.findViewById(R.id.albumCover)
        val titleTextView: TextView = view.findViewById(R.id.songTitle)
        val artistAlbumTextView: TextView = view.findViewById(R.id.artistAlbum)
        val actionButton: ImageButton = view.findViewById(R.id.actionButton)
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

        // Set default placeholder
        holder.albumCover.setImageResource(R.drawable.cover_art)

        // Load album art
        LoadAlbumArtTask(holder.albumCover, albumArtCache).execute(song.file.absolutePath)

        val isPlayMode = getPlayMode()
        holder.actionButton.setImageResource(
            if (isPlayMode) R.drawable.play_button
            else R.drawable.edit_button
        )

        holder.actionButton.setOnClickListener { onSongClick(song) }
        holder.itemView.setOnClickListener { onSongClick(song) }
    }

    override fun getItemCount() = songs.size

    fun updateData(newSongs: List<SongItem>) {
        Log.d("SongAdapter", "Updating data with ${newSongs.size} songs")
        songs = newSongs
        notifyDataSetChanged()
    }
}

sealed class SearchResultItem {
    data class Header(val title: String) : SearchResultItem()
    data class Song(val songItem: SongItem) : SearchResultItem()
}

// Make sure this SongItem data class matches the one you're using in your MainActivity
data class SongItem(val file: File, val title: String, val artist: String, val album: String)