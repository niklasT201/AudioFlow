package com.example.audioflow

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import java.io.File

class HomeScreenManager(private val activity: MainActivity) {

    private val ADD_FOLDER_REQUEST_CODE = 2
    private val PICK_AUDIO_FILE_REQUEST_CODE = 3

    fun setupHomeScreen(homeScreen: View) {
        setupAddButton(homeScreen)
        setupAddFileButton(homeScreen)
    }

    private fun setupAddButton(homeScreen: View) {
        val addButton: ImageButton = homeScreen.findViewById(R.id.add_button)
        addButton.setOnClickListener {
            showAddFolderDialog()
        }
    }

    private fun setupAddFileButton(homeScreen: View) {
        // Add this button to your home_screen.xml layout
        val addFileButton: ImageButton = homeScreen.findViewById(R.id.add_file_button)
        addFileButton.setOnClickListener {
            pickAudioFile()
        }
    }

    private fun showAddFolderDialog() {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_folder, null)
        val folderNameEditText: EditText = dialogView.findViewById(R.id.folder_name_edit_text)

        AlertDialog.Builder(activity, R.style.CustomMaterialDialogTheme)
            .setTitle("Add New Playlist/Folder")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val folderName = folderNameEditText.text.toString()
                if (folderName.isNotEmpty()) {
                    addNewFolder(folderName)
                } else {
                    Toast.makeText(activity, "Please enter a folder name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addNewFolder(folderName: String) {
        // Get the app's private external storage directory
        val appExternalDir = activity.getExternalFilesDir(null)

        if (appExternalDir != null) {
            // Create a new folder within the app's private directory
            val newFolder = File(appExternalDir, folderName)

            if (newFolder.mkdir()) {
                // Folder created successfully
                Toast.makeText(activity, "Folder created: $folderName", Toast.LENGTH_SHORT).show()

                // Scan the newly created folder to make it visible in the device's file system
                MediaScannerConnection.scanFile(activity, arrayOf(newFolder.path), null, null)

                // Update your app's folder list or database here
                activity.addFolderToList(newFolder.toUri())
            } else {
                // Failed to create folder
                Toast.makeText(activity, "Failed to create folder", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, "External storage not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/*"
        activity.startActivityForResult(intent, PICK_AUDIO_FILE_REQUEST_CODE)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ADD_FOLDER_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        val folderUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                        // Add the folder to your app's database or list of folders
                        activity.addFolderToList(folderUri)
                    }
                }
            }
            PICK_AUDIO_FILE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        // Play the selected audio file
                        activity.playSingleAudioFile(uri)
                    }
                }
            }
        }
    }
}