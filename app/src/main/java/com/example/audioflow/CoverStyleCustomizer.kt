package com.example.audioflow

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

class CoverStyleCustomizer(private val context: Context) {
    private lateinit var playerView: View
    private lateinit var albumArtCard: CardView
    private lateinit var albumArtImage: ImageView

    fun initialize(playerView: View) {
        this.playerView = playerView
        albumArtCard = playerView.findViewById(R.id.cv_album_art)
        albumArtImage = playerView.findViewById(R.id.iv_album_art)
    }

    fun applyCoverStyle(style: CoverStyle, cornerRadius: Float, coverSize: Int) {
        when (style) {
            CoverStyle.DEFAULT -> applyDefaultStyle(cornerRadius, coverSize)
            CoverStyle.CIRCULAR -> applyCircularStyle(coverSize)
            CoverStyle.FULL_WIDTH -> applyFullWidthStyle(cornerRadius)
            CoverStyle.EXPANDED_TOP -> applyExpandedTopStyle(cornerRadius)
        }
    }

    private fun applyDefaultStyle(cornerRadius: Float, coverSize: Int) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = 0
        params.height = 0
        params.dimensionRatio = "1:1"
        params.matchConstraintPercentWidth = coverSize / 100f
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
        albumArtCard.layoutParams = params

        // Make it perfectly circular
        albumArtCard.post {
            albumArtCard.radius = albumArtCard.width / 2f
        }
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyFullWidthStyle(cornerRadius: Float) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = 0
        params.dimensionRatio = "16:9"
        params.matchConstraintPercentWidth = 1f
        albumArtCard.layoutParams = params

        albumArtCard.radius = cornerRadius
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyExpandedTopStyle(cornerRadius: Float) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        params.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        params.dimensionRatio = ""  // Remove ratio constraint
        params.topMargin = 0
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        albumArtCard.layoutParams = params

        // Only round bottom corners for Material CardView
        if (albumArtCard is com.google.android.material.card.MaterialCardView) {
            (albumArtCard as MaterialCardView).shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setBottomLeftCornerSize(cornerRadius)
                .setBottomRightCornerSize(cornerRadius)
                .build()
        } else {
            // Fallback for regular CardView
            albumArtCard.radius = cornerRadius
        }

        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    enum class CoverStyle {
        DEFAULT, CIRCULAR, FULL_WIDTH, EXPANDED_TOP
    }
}

fun Activity.showCoverStyleCustomization() {
    val dialog = BottomSheetDialog(this)  // 'this' refers to the Activity
    val view = layoutInflater.inflate(R.layout.layout_cover_style_customization, null)
    dialog.setContentView(view)

    val coverStyleCustomizer = CoverStyleCustomizer(this)
    coverStyleCustomizer.initialize(findViewById(R.id.player_view_container))

    // Set up listeners for radio buttons and seek bars
    view.findViewById<Button>(R.id.btnApplyStyle).setOnClickListener {
        val selectedStyle = when (view.findViewById<RadioGroup>(R.id.coverStyleGroup).checkedRadioButtonId) {
            R.id.styleDefault -> CoverStyleCustomizer.CoverStyle.DEFAULT
            R.id.styleCircle -> CoverStyleCustomizer.CoverStyle.CIRCULAR
            R.id.styleFullWidth -> CoverStyleCustomizer.CoverStyle.FULL_WIDTH
            R.id.styleExpandedTop -> CoverStyleCustomizer.CoverStyle.EXPANDED_TOP
            else -> CoverStyleCustomizer.CoverStyle.DEFAULT
        }

        val cornerRadius = view.findViewById<SeekBar>(R.id.cornerRadiusSeekBar).progress.toFloat()
        val coverSize = view.findViewById<SeekBar>(R.id.coverSizeSeekBar).progress

        coverStyleCustomizer.applyCoverStyle(selectedStyle, cornerRadius, coverSize)
        dialog.dismiss()
    }

    dialog.show()
}