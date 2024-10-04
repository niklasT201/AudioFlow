package com.example.audioflow

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.SharedPreferences
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.core.content.edit
import com.google.android.material.card.MaterialCardView

class CoverStyleCustomizer(private val context: Context) {
    private lateinit var playerView: View
    private lateinit var albumArtCard: CardView
    private lateinit var albumArtImage: ImageView
    private val prefs: SharedPreferences = context.getSharedPreferences("cover_style_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_CORNER_RADIUS = 16f  // Matching the player screen's corner radius
        const val DEFAULT_COVER_SIZE = 85
        private const val PREF_STYLE = "cover_style"
        private const val PREF_CORNER_RADIUS = "corner_radius"
        private const val PREF_COVER_SIZE = "cover_size"
    }

    fun initialize(playerView: View) {
        this.playerView = playerView
        albumArtCard = playerView.findViewById(R.id.cv_album_art)
        albumArtImage = playerView.findViewById(R.id.iv_album_art)

        // Apply saved preferences on initialization
        applySavedStyle()
    }

    private fun applySavedStyle() {
        val savedStyle = CoverStyle.valueOf(prefs.getString(PREF_STYLE, CoverStyle.DEFAULT.name)!!)
        val savedCornerRadius = prefs.getFloat(PREF_CORNER_RADIUS, DEFAULT_CORNER_RADIUS)
        val savedCoverSize = prefs.getInt(PREF_COVER_SIZE, DEFAULT_COVER_SIZE)

        applyCoverStyle(savedStyle, savedCornerRadius, savedCoverSize)
    }

    private fun savePreferences(style: CoverStyle, cornerRadius: Float, coverSize: Int) {
        prefs.edit {
            putString(PREF_STYLE, style.name)
            putFloat(PREF_CORNER_RADIUS, cornerRadius)
            putInt(PREF_COVER_SIZE, coverSize)
        }
    }

    fun applyCoverStyle(style: CoverStyle, cornerRadius: Float, coverSize: Int) {
        // Reset all constraints and margins first
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.clearAllConstraints()

        when (style) {
            CoverStyle.DEFAULT -> applyDefaultStyle(cornerRadius, coverSize)
            CoverStyle.CIRCULAR -> applyCircularStyle(coverSize)
            CoverStyle.FULL_WIDTH -> applyFullWidthStyle(cornerRadius)
            CoverStyle.EXPANDED_TOP -> applyExpandedTopStyle(cornerRadius)
        }

        savePreferences(style, cornerRadius, coverSize)
    }

    private fun ConstraintLayout.LayoutParams.clearAllConstraints() {
        startToStart = ConstraintLayout.LayoutParams.UNSET
        endToEnd = ConstraintLayout.LayoutParams.UNSET
        topToTop = ConstraintLayout.LayoutParams.UNSET
        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        dimensionRatio = null
    }

    fun resetToDefault() {
        // Clear preferences
        prefs.edit().clear().apply()

        // Reset to default values
        applyCoverStyle(CoverStyle.DEFAULT, DEFAULT_CORNER_RADIUS, DEFAULT_COVER_SIZE)
    }

    private fun applyDefaultStyle(cornerRadius: Float, coverSize: Int) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = 0
        params.height = 0
        params.dimensionRatio = "1:1"
        params.matchConstraintPercentWidth = coverSize / 100f

        // Set margins from resources
        val defaultMargin = context.resources.getDimensionPixelSize(R.dimen.default_cover_top)
        params.setMargins(defaultMargin, defaultMargin, defaultMargin, 0)

        // Set constraints
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToBottom = R.id.btn_close_player

        albumArtCard.layoutParams = params
        albumArtCard.radius = cornerRadius
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyCircularStyle(coverSize: Int) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = 0
        params.height = 0
        params.dimensionRatio = "1:1"
        params.matchConstraintPercentWidth = coverSize / 100f

        // Set constraints
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToBottom = R.id.btn_close_player

        albumArtCard.layoutParams = params
        albumArtCard.post {
            albumArtCard.radius = albumArtCard.width / 2f
        }
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyFullWidthStyle(cornerRadius: Float) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = 0
        params.dimensionRatio = "16:9"  // Fixed aspect ratio

        // Set constraints
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToBottom = R.id.btn_close_player

        // Set margins
        params.setMargins(0, context.resources.getDimensionPixelSize(R.dimen.default_cover_top), 0, 0)

        albumArtCard.layoutParams = params
        albumArtCard.radius = cornerRadius
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyExpandedTopStyle(cornerRadius: Float) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = 0
        params.dimensionRatio = "16:9"  // Fixed aspect ratio to prevent over-stretching

        // Set constraints
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToBottom = R.id.btn_close_player

        albumArtCard.layoutParams = params

        if (albumArtCard is MaterialCardView) {
            (albumArtCard as MaterialCardView).shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setBottomLeftCornerSize(cornerRadius)
                .setBottomRightCornerSize(cornerRadius)
                .setTopLeftCornerSize(0f)
                .setTopRightCornerSize(0f)
                .build()
        } else {
            albumArtCard.radius = cornerRadius
        }

        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    enum class CoverStyle {
        DEFAULT, CIRCULAR, FULL_WIDTH, EXPANDED_TOP
    }
}

// Extension of Activity or Fragment to show the dialog
fun Activity.showCoverStyleCustomization() {
    val dialog = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.layout_cover_style_customization, null)
    dialog.setContentView(view)

    val coverStyleCustomizer = CoverStyleCustomizer(this)
    coverStyleCustomizer.initialize(findViewById(R.id.player_view_container))

    // Load saved preferences to set initial values
    val prefs = getSharedPreferences("cover_style_prefs", Context.MODE_PRIVATE)
    val savedStyle = prefs.getString("cover_style", CoverStyleCustomizer.CoverStyle.DEFAULT.name)
    val savedCornerRadius = prefs.getFloat("corner_radius", CoverStyleCustomizer.DEFAULT_CORNER_RADIUS)
    val savedCoverSize = prefs.getInt("cover_size", CoverStyleCustomizer.DEFAULT_COVER_SIZE)

    // Set initial values in UI
    val radioGroup = view.findViewById<RadioGroup>(R.id.coverStyleGroup)
    val cornerRadiusSeekBar = view.findViewById<SeekBar>(R.id.cornerRadiusSeekBar)
    val coverSizeSeekBar = view.findViewById<SeekBar>(R.id.coverSizeSeekBar)

    // Set the radio button based on saved style
    val radioButtonId = when (savedStyle) {
        CoverStyleCustomizer.CoverStyle.DEFAULT.name -> R.id.styleDefault
        CoverStyleCustomizer.CoverStyle.CIRCULAR.name -> R.id.styleCircle
        CoverStyleCustomizer.CoverStyle.FULL_WIDTH.name -> R.id.styleFullWidth
        CoverStyleCustomizer.CoverStyle.EXPANDED_TOP.name -> R.id.styleExpandedTop
        else -> R.id.styleDefault
    }
    radioGroup.check(radioButtonId)

    // Set the seekbars to saved values
    cornerRadiusSeekBar.progress = savedCornerRadius.toInt()
    coverSizeSeekBar.progress = savedCoverSize

    // Apply button click listener
    view.findViewById<Button>(R.id.btnApplyStyle).setOnClickListener {
        val selectedStyle = when (radioGroup.checkedRadioButtonId) {
            R.id.styleDefault -> CoverStyleCustomizer.CoverStyle.DEFAULT
            R.id.styleCircle -> CoverStyleCustomizer.CoverStyle.CIRCULAR
            R.id.styleFullWidth -> CoverStyleCustomizer.CoverStyle.FULL_WIDTH
            R.id.styleExpandedTop -> CoverStyleCustomizer.CoverStyle.EXPANDED_TOP
            else -> CoverStyleCustomizer.CoverStyle.DEFAULT
        }

        coverStyleCustomizer.applyCoverStyle(
            selectedStyle,
            cornerRadiusSeekBar.progress.toFloat(),
            coverSizeSeekBar.progress
        )
        dialog.dismiss()
    }

    // Add reset button click listener
    view.findViewById<Button>(R.id.btnResetStyle).setOnClickListener {
        coverStyleCustomizer.resetToDefault()
        dialog.dismiss()
    }

    dialog.show()
}