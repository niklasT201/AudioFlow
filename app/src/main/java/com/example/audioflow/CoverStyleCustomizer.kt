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
import android.util.TypedValue
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

    private var originalPadding: Padding? = null

    data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    companion object {
        const val DEFAULT_CORNER_RADIUS = 16f
        const val DEFAULT_COVER_SIZE = 85
        private const val PREF_STYLE = "cover_style"
        private const val PREF_CORNER_RADIUS = "corner_radius"
        private const val PREF_COVER_SIZE = "cover_size"
    }

    fun initialize(playerView: View) {
        this.playerView = playerView
        albumArtCard = playerView.findViewById(R.id.cv_album_art)
        albumArtImage = playerView.findViewById(R.id.iv_album_art)

        val parentLayout = playerView as? ConstraintLayout
        parentLayout?.let {
            originalPadding = Padding(
                it.paddingLeft,
                it.paddingTop,
                it.paddingRight,
                it.paddingBottom
            )
        }

        applySavedStyle()
    }

    private fun restoreDefaultPadding() {
        val parentLayout = playerView as? ConstraintLayout
        originalPadding?.let { padding ->
            parentLayout?.setPadding(
                padding.left,
                padding.top,
                padding.right,
                padding.bottom
            )
        }
    }

    private fun resetAllLayoutParameters() {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams

        // Clear all constraints
        params.clearAllConstraints()

        // Reset all margins
        params.setMargins(0, 0, 0, 0)

        // Reset all sizes
        params.width = 0
        params.height = 0

        // Reset any dimension ratio
        params.dimensionRatio = null

        // Reset constraint dimension percentages
        params.matchConstraintPercentWidth = 1f
        params.matchConstraintPercentHeight = 1f

        // Reset any bias
        params.horizontalBias = 0.5f
        params.verticalBias = 0.5f

        // Reset the card appearance
        if (albumArtCard is MaterialCardView) {
            (albumArtCard as MaterialCardView).shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCornerSizes(DEFAULT_CORNER_RADIUS)
                .build()
        } else {
            albumArtCard.radius = DEFAULT_CORNER_RADIUS
        }

        // Apply the reset parameters
        albumArtCard.layoutParams = params
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
        // First reset everything to default state
        resetAllLayoutParameters()
        restoreDefaultPadding()

        when (style) {
            CoverStyle.DEFAULT -> applyDefaultStyle(cornerRadius, coverSize)
            CoverStyle.CIRCULAR -> applyCircularStyle(coverSize)
            CoverStyle.FULL_WIDTH -> applyFullWidthStyle(cornerRadius)
            CoverStyle.EXPANDED_TOP -> applyExpandedTopStyle(cornerRadius)
            CoverStyle.SQUARE -> applySquareStyle(coverSize)
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

        // Reset all layout parameters
        resetAllLayoutParameters()

        // Restore original padding
        restoreDefaultPadding()

        // Apply default style
        applyCoverStyle(CoverStyle.DEFAULT, DEFAULT_CORNER_RADIUS, DEFAULT_COVER_SIZE)
    }

    private fun applyDefaultStyle(cornerRadius: Float, coverSize: Int) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams

        // Set exact parameters for default style
        params.width = 0
        params.height = 0
        params.dimensionRatio = "1:1"

        // Important: Set the exact percentage for width constraint
        params.matchConstraintPercentWidth = coverSize / 100f

        // Get the default margin from resources
        val defaultMargin = context.resources.getDimensionPixelSize(R.dimen.default_cover_top)
        params.setMargins(defaultMargin, defaultMargin, defaultMargin, 0)

        // Set the constraints explicitly
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToBottom = R.id.btn_close_player

        // Convert corner radius from dp to pixels
        val cornerRadiusInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            cornerRadius,
            context.resources.displayMetrics
        )

        // Apply corner radius
        albumArtCard.radius = cornerRadiusInPixels

        // Set the scale type
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP

        // Apply the parameters
        albumArtCard.layoutParams = params
    }

    private fun applySquareStyle(coverSize: Int) {
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
        albumArtCard.radius = 0f  // No rounded corners
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
        params.dimensionRatio = null // Clear any previous ratio

        // Clear constraints before setting new ones
        params.clearAllConstraints()

        // Remove horizontal margins only
        params.setMargins(0, 0, 0, 0)

        val parentLayout = playerView as? ConstraintLayout
        parentLayout?.apply {
            setPadding(0, paddingTop, 0, paddingBottom)
        }

        val closeButton = playerView.findViewById<View>(R.id.btn_close_player)

        playerView.post {
            val distanceToTop = closeButton.top + closeButton.height
            val desiredHeight = (albumArtCard.width * 1.0).toInt()

            params.height = desiredHeight
            params.bottomMargin = 0
            params.topMargin = distanceToTop - desiredHeight

            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            albumArtCard.layoutParams = params
        }

        closeButton.elevation = albumArtCard.elevation + 1f

        if (albumArtCard is MaterialCardView) {
            (albumArtCard as MaterialCardView).shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(0f)
                .setTopRightCornerSize(0f)
                .setBottomLeftCornerSize(cornerRadius)
                .setBottomRightCornerSize(cornerRadius)
                .build()
        } else {
            albumArtCard.radius = cornerRadius
        }

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
        DEFAULT, CIRCULAR, FULL_WIDTH, EXPANDED_TOP, SQUARE
    }
}

// Extension of Activity or Fragment to show the dialog
fun Activity.showCoverStyleCustomization() {
    val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
    val view = layoutInflater.inflate(R.layout.layout_cover_style_customization, null)
    dialog.setContentView(view)

    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        decorView.setBackgroundResource(android.R.color.transparent)
    }

    val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
    bottomSheet?.setBackgroundResource(android.R.color.transparent)

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
            R.id.styleSquare -> CoverStyleCustomizer.CoverStyle.SQUARE
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