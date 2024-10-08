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
import android.graphics.Color
import android.util.TypedValue
import android.view.WindowManager
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
    private lateinit var parentLayout: ConstraintLayout
    private var isFullWidthMode = false

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

        // Add default padding values from your layout
        private const val DEFAULT_PADDING = 20 // Match your layout's padding (20dp from your XML)
    }

    fun initialize(playerView: View) {
        this.playerView = playerView
        albumArtCard = playerView.findViewById(R.id.cv_album_art)
        albumArtImage = playerView.findViewById(R.id.iv_album_art)

        // Store the parent layout
        parentLayout = playerView as ConstraintLayout

        // Convert default padding to pixels
        val defaultPaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DEFAULT_PADDING.toFloat(),
            context.resources.displayMetrics
        ).toInt()

        // Store original padding values
        originalPadding = Padding(
            defaultPaddingPx,
            defaultPaddingPx,
            defaultPaddingPx,
            defaultPaddingPx
        )

        applySavedStyle()
    }

    private fun restoreDefaultPadding() {
        originalPadding?.let { padding ->
            parentLayout.setPadding(
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

        // Explicitly restore default padding
        restoreDefaultPadding()

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
        isFullWidthMode = false

        when (style) {
            CoverStyle.DEFAULT -> applyDefaultStyle(cornerRadius, coverSize)
            CoverStyle.CIRCULAR -> applyCircularStyle(coverSize)
            CoverStyle.FULL_WIDTH -> applyFullWidthStyle(cornerRadius)
            CoverStyle.EXPANDED_TOP -> applyExpandedTopStyle(cornerRadius)
            CoverStyle.SQUARE -> applySquareStyle(coverSize)
        }

        updateWindowDecorations(style)
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
        isFullWidthMode = false
        // Clear preferences
        prefs.edit().clear().apply()

        // Reset all layout parameters and restore padding
        resetAllLayoutParameters()

        // Apply default style
        applyCoverStyle(CoverStyle.DEFAULT, DEFAULT_CORNER_RADIUS, DEFAULT_COVER_SIZE)
        updateWindowDecorations(CoverStyle.DEFAULT)
    }

    private fun applyDefaultStyle(cornerRadius: Float, coverSize: Int) {
        // First restore default padding
        restoreDefaultPadding()

        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams

        // Set exact parameters for default style
        params.width = 0
        params.height = 0
        params.dimensionRatio = "1:1"
        params.matchConstraintPercentWidth = coverSize / 100f

        // Get the default margin from resources
        val defaultMargin = context.resources.getDimensionPixelSize(R.dimen.default_cover_top)
        params.setMargins(defaultMargin, defaultMargin, defaultMargin, 0)

        // Set the constraints explicitly
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.topToBottom = R.id.btn_close_player

        val cornerRadiusInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            cornerRadius,
            context.resources.displayMetrics
        )

        albumArtCard.radius = cornerRadiusInPixels
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP

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

    private fun updateWindowDecorations(style: CoverStyle) {
        val activity = context as? Activity ?: return
        val window = activity.window

        when (style) {
            CoverStyle.FULL_WIDTH -> {
                // Make status bar transparent and remove blur
                window.statusBarColor = Color.TRANSPARENT
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

                // Remove any blur effect (implementation depends on how you're applying blur)
                // If using WindowManager.LayoutParams.FLAG_BLUR_BEHIND:
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            else -> {
                // Restore blur effect for other styles if needed
                if (playerView.visibility == View.VISIBLE) {
                    // Apply your normal blur effect here
                    // For example:
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
    }

    private fun applyFullWidthStyle(cornerRadius: Float) {
        isFullWidthMode = true
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams

        params.clearAllConstraints()

        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        //params.dimensionRatio = null

        params.setMargins(0, 0, 0, 0)

        parentLayout.setPadding(0, 0, 0, parentLayout.paddingBottom)

        val statusBarHeight = context.resources.getDimensionPixelSize(
            context.resources.getIdentifier("status_bar_height", "dimen", "android")
        )

        val closeButton = playerView.findViewById<View>(R.id.btn_close_player)

        params.apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        }

        playerView.post {
            val desiredHeight = (albumArtCard.width * 1.0).toInt()

            // Set the top margin to half the status bar height
            params.topMargin = -statusBarHeight / 2

            params.height = desiredHeight + statusBarHeight +50
            params.bottomMargin = 0

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
        updateWindowDecorations(CoverStyle.FULL_WIDTH)
    }

    private fun applyExpandedTopStyle(cornerRadius: Float) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = 0
        params.dimensionRatio = "16:16"  // Fixed aspect ratio to prevent over-stretching

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