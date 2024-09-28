package com.example.audioflow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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

        songPath = intent.getStringExtra("songPath")
        songPathTextView.text = songPath

        findViewById<ImageView>(R.id.saveButton).setOnClickListener {
            saveSongMetadata()
        }

        findViewById<Button>(R.id.changeCoverButton).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<ImageView>(R.id.back_btn).setOnClickListener {
            onBackPressed()
        }

        checkPermissionAndLoadMetadata()
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

    private fun loadMetadata() {
        songPath?.let { path ->
            try {
                val file = File(path)
                val audioFile = AudioFileIO.read(file)
                val tag: Tag = audioFile.tag

                titleEditText.setText(tag.getFirst(FieldKey.TITLE))
                artistEditText.setText(tag.getFirst(FieldKey.ARTIST))
                albumEditText.setText(tag.getFirst(FieldKey.ALBUM))

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

                tag.setField(FieldKey.TITLE, titleEditText.text.toString())
                tag.setField(FieldKey.ARTIST, artistEditText.text.toString())
                tag.setField(FieldKey.ALBUM, albumEditText.text.toString())

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

                Toast.makeText(this, "Metadata and cover art saved successfully", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
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