package com.example.audioflow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.ByteArrayOutputStream
import java.io.File

class EditMetadataActivity : AppCompatActivity() {
    private lateinit var titleEditText: EditText
    private lateinit var artistEditText: EditText
    private lateinit var albumEditText: EditText
    private lateinit var coverImageView: ImageView
    private lateinit var songPathTextView: TextView
    private var songPath: String? = null
    private var newCoverUri: Uri? = null

    private lateinit var saveButton: ImageView
    private lateinit var changeCoverButton: Button
    private var isEditMode = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                loadMetadata()
            } else {
                Toast.makeText(this, "Permission denied. Cannot edit metadata.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            newCoverUri = it
            coverImageView.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_metadata)

        titleEditText = findViewById(R.id.titleEditText)
        artistEditText = findViewById(R.id.artistEditText)
        albumEditText = findViewById(R.id.albumEditText)
        coverImageView = findViewById(R.id.coverImageView)
        songPathTextView = findViewById(R.id.songPathTextView)
        saveButton = findViewById(R.id.saveButton)
        changeCoverButton = findViewById(R.id.changeCoverButton)

        songPath = intent.getStringExtra("songPath")
        songPathTextView.text = songPath

        saveButton.setOnClickListener {
            if (isEditMode) {
                saveSongMetadata()
            } else {
                enableEditMode()
            }
        }

        changeCoverButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<ImageView>(R.id.back_btn).setOnClickListener {
            onBackPressed()
        }

        checkPermissionAndLoadMetadata()
        disableEditMode() // Start in view mode
    }

    private fun checkPermissionAndLoadMetadata() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadMetadata()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) -> {
                // Show an explanation to the user
                Toast.makeText(this, "Storage permission is needed to edit metadata", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun enableEditMode() {
        isEditMode = true
        saveButton.setImageResource(R.drawable.save_icon)
        changeCoverButton.visibility = View.VISIBLE
        setEditTextsEditable(true)
    }

    private fun disableEditMode() {
        isEditMode = false
        saveButton.setImageResource(R.drawable.edit_button)
        changeCoverButton.visibility = View.GONE
        setEditTextsEditable(false)
    }

    private fun setEditTextsEditable(editable: Boolean) {
        titleEditText.isEnabled = editable
        artistEditText.isEnabled = editable
        albumEditText.isEnabled = editable
    }

    private fun loadMetadata() {
        songPath?.let { path ->
            try {
                val file = File(path)
                val audioFile = AudioFileIO.read(file)
                val tag: Tag = audioFile.tag

                titleEditText.setText(tag.getFirst(FieldKey.TITLE))
                artistEditText.setText(tag.getFirst(FieldKey.ARTIST))
                albumEditText.setText(tag.getFirst(FieldKey.ALBUM))

                // Set default values if metadata is empty or null
                titleEditText.setText(tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() } ?: file.nameWithoutExtension)
                artistEditText.setText(tag.getFirst(FieldKey.ARTIST).takeIf { it.isNotBlank() } ?: "Unknown Artist")
                albumEditText.setText(tag.getFirst(FieldKey.ALBUM).takeIf { it.isNotBlank() } ?: "Unknown Album")

                disableEditMode()

                val artworkBinaryData = tag.firstArtwork?.binaryData
                if (artworkBinaryData != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artworkBinaryData, 0, artworkBinaryData.size)
                    coverImageView.setImageBitmap(bitmap)
                } else {
                    coverImageView.setImageResource(R.drawable.cover_art)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error loading metadata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(this, "Song path is missing", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveSongMetadata() {
        songPath?.let { path ->
            try {
                val file = File(path)
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tagOrCreateAndSetDefault

                // Ensure we're not saving empty values
                val title = titleEditText.text.toString().takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                val artist = artistEditText.text.toString().takeIf { it.isNotBlank() } ?: "Unknown Artist"
                val album = albumEditText.text.toString().takeIf { it.isNotBlank() } ?: "Unknown Album"

                tag.setField(FieldKey.TITLE, title)
                tag.setField(FieldKey.ARTIST, artist)
                tag.setField(FieldKey.ALBUM, album)

                // Save new cover art if selected
                newCoverUri?.let { uri ->
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                        val imageData = baos.toByteArray()

                        val artwork = ArtworkFactory.getNew()
                        artwork.binaryData = imageData
                        artwork.mimeType = "image/jpeg"

                        tag.deleteArtworkField()
                        tag.setField(artwork)
                    }
                }

                audioFile.commit()

                // After successfully saving, set the result
                val resultIntent = Intent().apply {
                    putExtra("updatedSongPath", path)
                    putExtra("updatedTitle", titleEditText.text.toString())
                    putExtra("updatedArtist", artistEditText.text.toString())
                    putExtra("updatedAlbum", albumEditText.text.toString())
                }
                setResult(Activity.RESULT_OK, resultIntent)
                disableEditMode()
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to save metadata and cover art: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(this, "Song path is missing", Toast.LENGTH_SHORT).show()
        }
    }
}