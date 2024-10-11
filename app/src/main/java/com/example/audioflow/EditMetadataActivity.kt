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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var originalTitle: String = ""
    private var originalArtist: String = ""
    private var originalAlbum: String = ""
    private var originalCoverArt: ByteArray? = null
    private var removeCover: Boolean = false

    private lateinit var saveButton: ImageView
    private lateinit var changeCoverButton: Button
    private lateinit var removeCoverCheckBox: CheckBox
    private var isEditMode = false
    private lateinit var optionsButton: ImageView

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

        removeCoverCheckBox = findViewById(R.id.removeCoverCheckBox)
        optionsButton = findViewById(R.id.optionsButton)

        songPath = intent.getStringExtra("songPath")
        songPathTextView.text = songPath

        setupButtons()
        setupOptionsMenu()
        checkPermissionAndLoadMetadata()
        disableEditMode()
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

    private fun setupOptionsMenu() {
        optionsButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.metadata_options_menu, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog()
                        true
                    }
                    R.id.action_reset -> {
                        showResetConfirmationDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setupButtons() {
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

        removeCoverCheckBox.setOnClickListener {
            removeCover = removeCoverCheckBox.isChecked
            if (removeCover) {
                coverImageView.setImageResource(R.drawable.cover_art)
                newCoverUri = null
            } else {
                // Restore original cover if available
                originalCoverArt?.let {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    coverImageView.setImageBitmap(bitmap)
                }
            }
        }

        findViewById<ImageView>(R.id.back_btn).setOnClickListener {
            onBackPressed()
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

                originalTitle = tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                originalArtist = tag.getFirst(FieldKey.ARTIST).takeIf { it.isNotBlank() } ?: "Unknown Artist"
                originalAlbum = tag.getFirst(FieldKey.ALBUM).takeIf { it.isNotBlank() } ?: "Unknown Album"
                originalCoverArt = tag.firstArtwork?.binaryData

                // Set default values if metadata is empty or null
                titleEditText.setText(originalTitle)
                artistEditText.setText(originalArtist)
                albumEditText.setText(originalAlbum)

                disableEditMode()

                val artworkBinaryData = tag.firstArtwork?.binaryData
                if (artworkBinaryData != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artworkBinaryData, 0, artworkBinaryData.size)
                    coverImageView.setImageBitmap(bitmap)
                    removeCoverCheckBox.visibility = View.VISIBLE
                } else {
                    coverImageView.setImageResource(R.drawable.cover_art)
                    removeCoverCheckBox.visibility = View.GONE
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

    private fun showDeleteConfirmationDialog() {
        val themedContext = ContextThemeWrapper(this, R.style.CustomMaterialDialogTheme)
        MaterialAlertDialogBuilder(themedContext)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete this file? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteFile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetConfirmationDialog() {
        val themedContext = ContextThemeWrapper(this, R.style.CustomMaterialDialogTheme)
        MaterialAlertDialogBuilder(themedContext)
            .setTitle("Reset Changes")
            .setMessage("Are you sure you want to reset all changes? This will restore the original metadata.")
            .setPositiveButton("Reset") { dialog, _ ->
                resetChanges()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun resetChanges() {
        titleEditText.setText(originalTitle)
        artistEditText.setText(originalArtist)
        albumEditText.setText(originalAlbum)
        removeCoverCheckBox.isChecked = false
        removeCover = false
        newCoverUri = null

        originalCoverArt?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            coverImageView.setImageBitmap(bitmap)
        } ?: coverImageView.setImageResource(R.drawable.cover_art)

        Toast.makeText(this, "Changes reset to original", Toast.LENGTH_SHORT).show()
    }

    private fun deleteFile() {
        songPath?.let { path ->
            try {
                val file = File(path)
                if (file.delete()) {
                    Toast.makeText(this, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK, Intent().putExtra("fileDeleted", true))
                    finish()
                } else {
                    Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error deleting file: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

                // Handle cover art
                if (removeCover && tag.firstArtwork != null) {
                    tag.deleteArtworkField()
                } else if (newCoverUri != null) {
                    contentResolver.openInputStream(newCoverUri!!)?.use { inputStream ->
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

                val resultIntent = Intent().apply {
                    putExtra("updatedSongPath", path)
                    putExtra("updatedTitle", title)
                    putExtra("updatedArtist", artist)
                    putExtra("updatedAlbum", album)
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