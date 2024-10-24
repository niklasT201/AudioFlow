package com.example.audioflow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.audioflow.AudioMetadataRetriever.SongItem
import java.io.File

class FavoriteManager(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("Favorites", Context.MODE_PRIVATE)
    private var listeners = mutableListOf<() -> Unit>()

    fun addFavoriteChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeFavoriteChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyFavoriteChanged() {
        listeners.forEach { it.invoke() }
    }

    fun addFavorite(song: SongItem) {
        val favorites = getFavorites().toMutableSet()
        favorites.add(song.file.absolutePath)
        sharedPreferences.edit().putStringSet("favorites", favorites).apply()
        notifyFavoriteChanged()
    }

    fun removeFavorite(song: SongItem) {
        val favorites = getFavorites().toMutableSet()
        favorites.remove(song.file.absolutePath)
        sharedPreferences.edit().putStringSet("favorites", favorites).apply()
        notifyFavoriteChanged()
    }

    fun isFavorite(song: SongItem): Boolean {
        return getFavorites().contains(song.file.absolutePath)
    }

    fun getFavorites(): Set<String> {
        return sharedPreferences.getStringSet("favorites", emptySet()) ?: emptySet()
    }
}

class FavoritesActivity : AppCompatActivity() {
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var songListView: ListView
    private lateinit var audioMetadataRetriever: AudioMetadataRetriever
    private lateinit var songOptionsHandler: SongOptionsHandler
    private val favoriteChangeListener: () -> Unit = {
        updateFavoritesList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.songs_screen)

        favoriteManager = FavoriteManager(this)
        audioMetadataRetriever = AudioMetadataRetriever(contentResolver)
        songListView = findViewById(R.id.song_list_view)

        findViewById<AlphabetIndexView>(R.id.alphabet_index).visibility = View.GONE

        findViewById<TextView>(R.id.tv_folder_name).text = "Favorite Songs"

        findViewById<ImageButton>(R.id.back_btn).setOnClickListener {
            finish()
        }

        // Initialize SongOptionsHandler
        songOptionsHandler = SongOptionsHandler(
            activity = this,
            songOptionsFooter = findViewById(R.id.song_options_footer),
            songListView = songListView,
            playerOptionsManager = null
        )

        favoriteManager.addFavoriteChangeListener(favoriteChangeListener)
        updateFavoritesList()
    }

    private fun updateFavoritesList() {
        val favorites = favoriteManager.getFavorites().mapNotNull { path ->
            val file = File(path)
            if (file.exists()) {
                audioMetadataRetriever.getSongsInFolder(file.parentFile).find { it.file == file }
            } else {
                null
            }
        }

        if (favorites.isEmpty()) {
            songListView.visibility = View.GONE
            findViewById<TextView>(R.id.no_favorites_message)?.apply {
                visibility = View.VISIBLE
                text = "No favorite songs yet"
            }
        } else {
            songListView.visibility = View.VISIBLE
            findViewById<TextView>(R.id.no_favorites_message)?.visibility = View.GONE

            val adapter = createSongListAdapter(favorites)
            songListView.adapter = adapter

            songListView.setOnItemClickListener { _, _, position, _ ->
                val selectedSong = favorites[position]
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("PLAY_SONG", selectedSong.file.absolutePath)
                intent.putExtra("FROM_FAVORITES", true)
                startActivity(intent)
                finish() // Add this to close the FavoritesActivity
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
                view.findViewById<ImageView>(R.id.song_current_song_icon).visibility = View.GONE

                // Add click listener to the settings icon
                view.findViewById<ImageView>(R.id.song_settings_icon).setOnClickListener {
                    song?.let { showSongOptionsDialog(it, position) }
                }

                return view
            }
        }
    }

    private fun showSongOptionsDialog(song: SongItem, position: Int) {
        // Implement your song options dialog here
        // You can reuse the logic from MainActivity or create a simplified version
    }
}