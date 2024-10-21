package com.example.audioflow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.audioflow.AudioMetadataRetriever.SongItem
import java.io.File

class FavoriteManager(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("Favorites", Context.MODE_PRIVATE)

    fun addFavorite(song: SongItem) {
        val favorites = getFavorites().toMutableSet()
        favorites.add(song.file.absolutePath)
        sharedPreferences.edit().putStringSet("favorites", favorites).apply()
    }

    fun removeFavorite(song: SongItem) {
        val favorites = getFavorites().toMutableSet()
        favorites.remove(song.file.absolutePath)
        sharedPreferences.edit().putStringSet("favorites", favorites).apply()
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
    private lateinit var favoritesListView: ListView
    private lateinit var audioMetadataRetriever: AudioMetadataRetriever

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        favoriteManager = FavoriteManager(this)
        audioMetadataRetriever = AudioMetadataRetriever(contentResolver)
        favoritesListView = findViewById(R.id.favorites_list_view)

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
            findViewById<TextView>(R.id.no_favorites_message).visibility = View.VISIBLE
            favoritesListView.visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.no_favorites_message).visibility = View.GONE
            favoritesListView.visibility = View.VISIBLE

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, favorites.map { it.title })
            favoritesListView.adapter = adapter

            favoritesListView.setOnItemClickListener { _, _, position, _ ->
                val selectedSong = favorites[position]
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("PLAY_SONG", selectedSong.file.absolutePath)
                startActivity(intent)
            }
        }
    }
}