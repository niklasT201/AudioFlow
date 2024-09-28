package com.example.audioflow

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

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    // Required permissions list
    private val requiredPermissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_metadata)

        titleEditText = findViewById(R.id.titleEditText)
        artistEditText = findViewById(R.id.artistEditText)
        albumEditText = findViewById(R.id.albumEditText)
        coverImageView = findViewById(R.id.coverImageView)
        songPathTextView = findViewById(R.id.songPathTextView)

        songPath = intent.getStringExtra("songPath")
        titleEditText.setText(intent.getStringExtra("songTitle"))
        artistEditText.setText(intent.getStringExtra("songArtist"))
        albumEditText.setText(intent.getStringExtra("songAlbum"))
        songPathTextView.text = intent.getStringExtra("songPath")

        // Check for storage permissions
        if (!hasStoragePermissions()) {
            requestStoragePermissions()
        }

        findViewById<ImageView>(R.id.saveButton).setOnClickListener {
            saveSongMetadata()
        }

        findViewById<Button>(R.id.changeCoverButton).setOnClickListener {
            openGallery()
        }

        findViewById<ImageView>(R.id.back_btn).setOnClickListener {
            onBackPressed()
        }

        loadCoverArt()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            newCoverUri = data.data
            coverImageView.setImageURI(newCoverUri)
        }
    }

    private fun loadCoverArt() {
        songPath?.let { path ->
            try {
                val file = File(path)
                val audioFile = AudioFileIO.read(file)
                val tag: Tag = audioFile.tag

                val artworkBinaryData = tag.firstArtwork?.binaryData
                if (artworkBinaryData != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artworkBinaryData, 0, artworkBinaryData.size)
                    coverImageView.setImageBitmap(bitmap)
                } else {
                    coverImageView.setImageResource(R.drawable.cover_art)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                coverImageView.setImageResource(R.drawable.cover_art)
            }
        } ?: run {
            coverImageView.setImageResource(R.drawable.cover_art)
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
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                    val imageData = baos.toByteArray()

                    // Create artwork manually
                    val artwork = ArtworkFactory.getNew()
                    artwork.binaryData = imageData
                    artwork.mimeType = "image/jpeg"

                    tag.deleteArtworkField()
                    tag.setField(artwork)
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

    // Check if storage permissions are granted
    private fun hasStoragePermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Request storage permissions
    private fun requestStoragePermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, proceed with saving metadata
                saveSongMetadata()
            } else {
                // Permissions not granted
                Toast.makeText(this, "Storage permissions are required to modify files.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}