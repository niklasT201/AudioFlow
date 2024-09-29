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
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private val songsLiveData = MutableLiveData<List<SongItem>>()
    private lateinit var contentObserver: ContentObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_screen)

        searchView = findViewById(R.id.searchView)
        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        searchResultsContainer = findViewById(R.id.searchResultsContainer)

        // Initialize views from the included layout
        artistsRecyclerView = findViewById(R.id.artistsRecyclerView)
        albumsRecyclerView = findViewById(R.id.albumsRecyclerView)
        songsRecyclerView = findViewById(R.id.songsRecyclerView)
        showAllArtistsButton = findViewById(R.id.showAllArtistsButton)
        showAllAlbumsButton = findViewById(R.id.showAllAlbumsButton)
        showAllSongsButton = findViewById(R.id.showAllSongsButton)

        setupSearchView()

        allSongs = getAllSongs().toMutableList()

        artistAdapter = ArtistAdapter(emptyList()) { artist -> showArtistDetails(artist) }
        albumAdapter = AlbumAdapter(emptyList()) { album -> showAlbumDetails(album) }
        songAdapter = SongAdapter(
            emptyList(),
            { song -> handleSongClick(song) },
            { modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio }
        )

        artistsRecyclerView.layoutManager = LinearLayoutManager(this)
        albumsRecyclerView.layoutManager = LinearLayoutManager(this)
        songsRecyclerView.layoutManager = LinearLayoutManager(this)

        artistsRecyclerView.adapter = artistAdapter
        albumsRecyclerView.adapter = albumAdapter
        songsRecyclerView.adapter = songAdapter

        showAllSongs()

        setupSearchView()
        setupModeRadioGroup()
        setupShowAllButtons()
        setupContentObserver()

        songsLiveData.observe(this, Observer { songs ->
            allSongs = songs.toMutableList()
            performSearch(searchView.query.toString())
        })
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
                if (newText.isNullOrBlank()) {
                    searchResultsContainer.visibility = View.GONE
                    showAllSongs()
                } else {
                    searchResultsContainer.visibility = View.VISIBLE
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
            searchResultsContainer.visibility = View.GONE
            showAllSongs()
            return
        }

        searchResultsContainer.visibility = View.VISIBLE

        val lowercaseQuery = query.lowercase()

        val matchingArtists = allSongs.map { it.artist }.distinct().filter { it.lowercase().contains(lowercaseQuery) }
        val matchingAlbums = allSongs.map { it.album }.distinct().filter { it.lowercase().contains(lowercaseQuery) }
        val matchingSongs = allSongs.filter { song ->
            song.title.lowercase().contains(lowercaseQuery) ||
                    song.artist.lowercase().contains(lowercaseQuery) ||
                    song.album.lowercase().contains(lowercaseQuery)
        }

        updateArtistsList(matchingArtists)
        updateAlbumsList(matchingAlbums)
        updateSongsList(matchingSongs)
    }


    private fun updateArtistsList(artists: List<String>) {
        artistAdapter.updateData(artists.take(5))
        showAllArtistsButton.visibility = if (artists.size > 5) View.VISIBLE else View.GONE
    }

    private fun updateAlbumsList(albums: List<String>) {
        albumAdapter.updateData(albums.take(5))
        showAllAlbumsButton.visibility = if (albums.size > 5) View.VISIBLE else View.GONE
    }

    private fun updateSongsList(songs: List<SongItem>) {
        songAdapter.updateData(songs.take(5))
        showAllSongsButton.visibility = if (songs.size > 5) View.VISIBLE else View.GONE
    }

    private fun showAllSongs() {
        artistAdapter.updateData(emptyList())
        albumAdapter.updateData(emptyList())
        songAdapter.updateData(allSongs)
        showAllArtistsButton.visibility = View.GONE
        showAllAlbumsButton.visibility = View.GONE
        showAllSongsButton.visibility = View.GONE
    }


    private fun setupShowAllButtons() {
        showAllArtistsButton.setOnClickListener { showAllArtists() }
        showAllAlbumsButton.setOnClickListener { showAllAlbums() }
        showAllSongsButton.setOnClickListener { showAllSongs() }
    }

    private fun showAllArtists() {
        val allArtists = allSongs.map { it.artist }.distinct()
        artistAdapter.updateData(allArtists)
        showAllArtistsButton.visibility = View.GONE
    }

    private fun showAllAlbums() {
        val allAlbums = allSongs.map { it.album }.distinct()
        albumAdapter.updateData(allAlbums)
        showAllAlbumsButton.visibility = View.GONE
    }

    private fun showArtistDetails(artist: String) {
        val artistSongs = allSongs.filter { it.artist == artist }
        val artistAlbums = artistSongs.map { it.album }.distinct()

        albumAdapter.updateData(artistAlbums)
        songAdapter.updateData(artistSongs)

        showAllAlbumsButton.visibility = if (artistAlbums.size > 5) View.VISIBLE else View.GONE
        showAllSongsButton.visibility = if (artistSongs.size > 5) View.VISIBLE else View.GONE
    }

    private fun showAlbumDetails(album: String) {
        val albumSongs = allSongs.filter { it.album == album }

        songAdapter.updateData(albumSongs)

        showAllSongsButton.visibility = if (albumSongs.size > 5) View.VISIBLE else View.GONE
    }

    private fun handleSongClick(song: SongItem) {
        if (modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio) {
            playSong(song)
        } else {
            editSongMetadata(song)
        }
    }

    private fun getAllSongs(): List<SongItem> {
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
            // Refresh the entire song list after editing metadata
            refreshSongList()
        }
    }

    private fun refreshSongList() {
        AsyncTask.execute {
            val updatedSongs = getAllSongs()
            runOnUiThread {
                allSongs = updatedSongs.toMutableList()
                performSearch(searchView.query.toString())
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

class SearchResultsAdapter(
    private val context: Context,
    private var items: List<SearchResultItem>
) : BaseAdapter() {

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): SearchResultItem = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 2
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return when (val item = getItem(position)) {
            is SearchResultItem.Header -> getHeaderView(item, convertView, parent)
            is SearchResultItem.Song -> getSongView(item, convertView, parent)
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is SearchResultItem.Header -> 0
        is SearchResultItem.Song -> 1
    }

    private fun getHeaderView(header: SearchResultItem.Header, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.search_header_item, parent, false)
        view.findViewById<TextView>(R.id.headerText).text = header.title
        return view
    }

    private fun getSongView(song: SearchResultItem.Song, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.search_song_item, parent, false)
        view.findViewById<TextView>(R.id.songTitle).text = song.songItem.title
        view.findViewById<TextView>(R.id.artistAlbum).text = "${song.songItem.artist} - ${song.songItem.album}"

        val albumCover = view.findViewById<ImageView>(R.id.albumCover)

        // Set default placeholder immediately
        albumCover.setImageResource(R.drawable.cover_art)

        // Load album art in the background
        LoadAlbumArtTask(albumCover).execute(song.songItem.file.absolutePath)

        return view
    }

    // AsyncTask to load the album art in the background
    private class LoadAlbumArtTask(val imageView: ImageView) : AsyncTask<String, Void, Bitmap?>() {
        override fun doInBackground(vararg params: String): Bitmap? {
            val filePath = params[0]
            val mediaMetadataRetriever = MediaMetadataRetriever()
            try {
                mediaMetadataRetriever.setDataSource(filePath)
                val albumArt = mediaMetadataRetriever.embeddedPicture
                if (albumArt != null) {
                    return BitmapFactory.decodeByteArray(albumArt, 0, albumArt.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mediaMetadataRetriever.release()
            }
            return null
        }

        override fun onPostExecute(result: Bitmap?) {
            if (result != null) {
                imageView.setImageBitmap(result)
            } else {
                imageView.setImageResource(R.drawable.cover_art)  // Set default if no album art
            }
        }
    }


    fun updateData(newItems: List<SearchResultItem>) {
        items = newItems
        notifyDataSetChanged()
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
    private val getPlayMode: () -> Boolean
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

        // Set album cover (you may want to use a library like Glide or Picasso for efficient image loading)
        holder.albumCover.setImageResource(R.drawable.cover_art)

        val isPlayMode = getPlayMode()
        holder.actionButton.setImageResource(
            if (isPlayMode) R.drawable.play_button
            else R.drawable.edit_button
        )

        holder.actionButton.setOnClickListener { onSongClick(song) }

        // Optionally, you can set a click listener for the entire item
        holder.itemView.setOnClickListener { onSongClick(song) }
    }

    override fun getItemCount() = songs.size

    fun updateData(newSongs: List<SongItem>) {
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