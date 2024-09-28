package com.example.audioflow

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.provider.MediaStore
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.AsyncTask
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import java.io.File

class SearchActivity : AppCompatActivity() {
    private lateinit var searchView: SearchView
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var searchResultsList: ListView
    private lateinit var allSongs: List<SongItem>
    private lateinit var adapter: SearchResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_screen)

        searchView = findViewById(R.id.searchView)
        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        searchResultsList = findViewById(R.id.searchResultsList)

        allSongs = getAllSongs()
        adapter = SearchResultsAdapter(this, allSongs.map { SearchResultItem.Song(it) })
        searchResultsList.adapter = adapter

        searchResultsList.divider = ContextCompat.getDrawable(this, R.drawable.list_divider)
        searchResultsList.dividerHeight = 1

        setupSearchView()
        setupModeRadioGroup()
        setupListViewClickListener()
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                performSearch(newText)
                return true
            }
        })
    }

    private fun setupModeRadioGroup() {
        modeRadioGroup.setOnCheckedChangeListener { _, _ ->
            adapter.notifyDataSetChanged()
        }
    }

    private fun setupListViewClickListener() {
        searchResultsList.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            if (item is SearchResultItem.Song) {
                if (modeRadioGroup.checkedRadioButtonId == R.id.playModeRadio) {
                    playSong(item.songItem)
                } else {
                    editSongMetadata(item.songItem)
                }
            }
        }
    }

    private fun performSearch(query: String?) {
        if (query.isNullOrBlank()) {
            adapter.updateData(emptyList())
            return
        }

        val lowercaseQuery = query.lowercase()
        val matchingSongs = allSongs.filter { song ->
            song.title.lowercase().contains(lowercaseQuery) ||
                    song.artist.lowercase().contains(lowercaseQuery) ||
                    song.album.lowercase().contains(lowercaseQuery)
        }

        val groupedResults = matchingSongs.groupBy { song ->
            when {
                song.artist.lowercase().contains(lowercaseQuery) -> "Artists"
                song.title.lowercase().contains(lowercaseQuery) -> "Songs"
                song.album.lowercase().contains(lowercaseQuery) -> "Albums"
                else -> "Other"
            }
        }

        val sortedResults = groupedResults.flatMap { (category, songs) ->
            listOf(SearchResultItem.Header(category)) + songs.map { SearchResultItem.Song(it) }
        }

        adapter.updateData(sortedResults)
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
            // Refresh the song list after metadata edit
            allSongs = getAllSongs()
            performSearch(searchView.query.toString())
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

sealed class SearchResultItem {
    data class Header(val title: String) : SearchResultItem()
    data class Song(val songItem: SongItem) : SearchResultItem()
}

// Make sure this SongItem data class matches the one you're using in your MainActivity
data class SongItem(val file: File, val title: String, val artist: String, val album: String)