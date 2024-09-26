package com.example.audioflow

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

class EditMetadataActivity : AppCompatActivity() {
    private lateinit var titleEditText: EditText
    private lateinit var artistEditText: EditText
    private lateinit var albumEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_metadata)

        titleEditText = findViewById(R.id.titleEditText)
        artistEditText = findViewById(R.id.artistEditText)
        albumEditText = findViewById(R.id.albumEditText)

        val songPath = intent.getStringExtra("songPath") ?: return
        titleEditText.setText(intent.getStringExtra("songTitle"))
        artistEditText.setText(intent.getStringExtra("songArtist"))
        albumEditText.setText(intent.getStringExtra("songAlbum"))

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSongMetadata(songPath)
        }
    }

    private fun saveSongMetadata(songPath: String) {
        try {
            val file = File(songPath)
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, titleEditText.text.toString())
            tag.setField(FieldKey.ARTIST, artistEditText.text.toString())
            tag.setField(FieldKey.ALBUM, albumEditText.text.toString())

            audioFile.commit()

            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save metadata", Toast.LENGTH_SHORT).show()
        }
    }
}