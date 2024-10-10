package com.example.audioflow

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat.getSystemService

class SettingsManager(private val activity: Activity) {
    private lateinit var switchKeepScreenOn: Switch
    private lateinit var switchResetPrevious: Switch
    private lateinit var switchTimer: Switch
    private lateinit var switchNotification: Switch
    private lateinit var versionText: TextView
    private lateinit var dateText: TextView

    private var mediaPlayerService: MediaPlayerService? = null
    private var timer: CountDownTimer? = null
    private var timerDuration: Long = 0
    private var remainingTime: Long = 0
    private var finishWithSong: Boolean = false
    private var isTimerActive = false
    private var selectedTimerMinutes: Int = 0
    private var resetPreviousEnabled = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.getService()
            mediaPlayerService?.setNotificationVisibility(switchNotification.isChecked)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlayerService = null
        }
    }

    fun setupSettings(settingsScreen: View, aboutScreen: View) {
        initializeViews(settingsScreen, aboutScreen)
        loadPreferences()
        setupListeners(settingsScreen, aboutScreen)
        bindMediaService()
    }

    private fun initializeViews(settingsScreen: View, aboutScreen: View) {
        switchKeepScreenOn = settingsScreen.findViewById(R.id.switch_keep_screen_on)
        switchResetPrevious = settingsScreen.findViewById(R.id.switch_reset_previous)
        switchTimer = settingsScreen.findViewById(R.id.switch_timer)
        switchNotification = settingsScreen.findViewById(R.id.switch_notification_visibility)
        versionText = aboutScreen.findViewById(R.id.version_text)
        dateText = aboutScreen.findViewById(R.id.date_text)
    }

    private fun loadPreferences() {
        val sharedPreferences = activity.getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        resetPreviousEnabled = sharedPreferences.getBoolean("reset_previous", false)
        val keepScreenOn = sharedPreferences.getBoolean("screen_on", false)
        isTimerActive = sharedPreferences.getBoolean("timer_active", false)
        val notificationEnabled = sharedPreferences.getBoolean("notification_enabled", true)

        switchKeepScreenOn.isChecked = keepScreenOn
        switchResetPrevious.isChecked = resetPreviousEnabled
        switchTimer.isChecked = isTimerActive
        switchNotification.isChecked = notificationEnabled

        applyScreenTimeoutSetting(keepScreenOn)
    }

    private fun setupListeners(settingsScreen: View, aboutScreen: View) {
        val sharedPreferences = activity.getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)

        switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("screen_on", isChecked).apply()
            applyScreenTimeoutSetting(isChecked)
        }

        switchResetPrevious.setOnCheckedChangeListener { _, isChecked ->
            resetPreviousEnabled = isChecked
            sharedPreferences.edit().putBoolean("reset_previous", isChecked).apply()
        }

        switchTimer.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                buttonView.isChecked = false
                showTimerDialog()
            } else {
                cancelTimer()
            }
        }

        switchNotification.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("notification_enabled", isChecked).apply()
            mediaPlayerService?.setNotificationVisibility(isChecked)
        }

        setupNavigationListeners(settingsScreen, aboutScreen)
    }

    private fun setupNavigationListeners(settingsScreen: View, aboutScreen: View) {
        settingsScreen.findViewById<LinearLayout>(R.id.about_app_button).setOnClickListener {
            (activity as? MainActivity)?.showScreen(aboutScreen)
        }

        aboutScreen.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            (activity as? MainActivity)?.showScreen(settingsScreen)
        }
    }

    private fun bindMediaService() {
        val serviceIntent = Intent(activity, MediaPlayerService::class.java)
        activity.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        try {
            val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            versionText.text = "Version ${packageInfo.versionName}"
            dateText.text = "October 2024"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showTimerDialog() {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.timer_dialog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val timeOptions = listOf(15, 30, 45, 60)
        val radioGroup = dialog.findViewById<RadioGroup>(R.id.timer_radio_group)
        val finishSongCheckbox = dialog.findViewById<CheckBox>(R.id.finish_song_checkbox)

        val sharedPreferences = activity.getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
        finishWithSong = sharedPreferences.getBoolean("finish_with_song", false)
        finishSongCheckbox.isChecked = finishWithSong

        setupTimerDialogOptions(dialog, timeOptions, radioGroup, finishSongCheckbox, sharedPreferences)
        dialog.show()
    }

    private fun setupTimerDialogOptions(
        dialog: Dialog,
        timeOptions: List<Int>,
        radioGroup: RadioGroup,
        finishSongCheckbox: CheckBox,
        sharedPreferences: SharedPreferences
    ) {
        timeOptions.forEachIndexed { index, minutes ->
            val radioButton = RadioButton(activity)
            radioButton.id = index
            radioButton.text = "$minutes minutes"
            radioButton.setTextColor(Color.WHITE)
            radioGroup.addView(radioButton)
        }

        dialog.findViewById<Button>(R.id.start_timer_button).setOnClickListener {
            handleTimerStart(dialog, radioGroup, timeOptions, finishSongCheckbox, sharedPreferences)
        }

        dialog.findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (!isTimerActive) {
                switchTimer.isChecked = false
            }
        }
    }

    private fun handleTimerStart(
        dialog: Dialog,
        radioGroup: RadioGroup,
        timeOptions: List<Int>,
        finishSongCheckbox: CheckBox,
        sharedPreferences: SharedPreferences
    ) {
        val selectedId = radioGroup.checkedRadioButtonId
        if (selectedId != -1) {
            switchTimer.isChecked = true
            isTimerActive = true

            finishWithSong = finishSongCheckbox.isChecked
            sharedPreferences.edit()
                .putBoolean("finish_with_song", finishWithSong)
                .putBoolean("timer_active", true)
                .apply()

            val minutes = timeOptions[selectedId]
            timerDuration = minutes * 60 * 1000L
            startTimer()
            dialog.dismiss()

            Toast.makeText(activity, "Timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Please select a time duration", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timerDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = millisUntilFinished
            }

            override fun onFinish() {
                (activity as? MainActivity)?.let { mainActivity ->
                    if (finishWithSong && mainActivity.isPlaying()) {
                        mainActivity.setOnCompletionListener {
                            finishApp()
                        }
                    } else {
                        finishApp()
                    }
                }
            }
        }.start()
    }

    fun cancelTimer() {
        timer?.cancel()
        timer = null
        isTimerActive = false
        activity.getSharedPreferences("AudioFlowPrefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("timer_active", false)
            .apply()
    }

    private fun finishApp() {
        cancelTimer()
        (activity as? MainActivity)?.cleanupAndFinish()
    }

    private fun applyScreenTimeoutSetting(keepScreenOn: Boolean) {
        if (keepScreenOn) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun isResetPreviousEnabled() = resetPreviousEnabled

    fun cleanup() {
        timer?.cancel()
        activity.unbindService(serviceConnection)
    }
}