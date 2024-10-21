package com.example.audioflow

import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat

class FooterManager(private val activity: MainActivity) {

    private lateinit var btnHome: Button
    private lateinit var btnSearch: Button
    private lateinit var btnSettings: Button

    fun setupFooter() {
        val footer = activity.findViewById<View>(R.id.footer)
        btnHome = footer.findViewById(R.id.btn_home)
        btnSearch = footer.findViewById(R.id.btn_search)
        btnSettings = footer.findViewById(R.id.btn_settings)

        btnHome.setOnClickListener {
            Log.d("AudioFlow", "Home button clicked")
            try {
                activity.showScreen(activity.homeScreen)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error showing home view", e)
                Toast.makeText(activity, "Error showing home view: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnSearch.setOnClickListener {
            Log.d("AudioFlow", "Search button clicked")
            try {
                val intent = Intent(activity, SearchActivity::class.java)
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error showing search view", e)
                Toast.makeText(activity, "Error showing search view: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnSettings.setOnClickListener {
            Log.d("AudioFlow", "Settings button clicked")
            try {
                activity.showScreen(activity.settingsScreen)
            } catch (e: Exception) {
                Log.e("AudioFlow", "Error showing settings view", e)
                Toast.makeText(activity, "Error showing settings view: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateFooterHighlight(activeButton: Button) {
        val buttons = listOf(btnHome, btnSearch, btnSettings)
        buttons.forEach { button ->
            if (button == activeButton) {
                button.setTextColor(ContextCompat.getColor(activity, R.color.primary_text))
                button.compoundDrawables[1]?.setTint(ContextCompat.getColor(activity, R.color.primary_text))
            } else {
                button.setTextColor(ContextCompat.getColor(activity, R.color.secondary_text))
                button.compoundDrawables[1]?.setTint(ContextCompat.getColor(activity, R.color.secondary_text))
            }
        }
    }
}