package com.example.audioflow

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.TypedValue
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import com.google.android.material.card.MaterialCardView

class CoverStyleCustomizer(private val context: Context) {
    private lateinit var playerView: View
    private lateinit var albumArtCard: CardView
    private lateinit var albumArtImage: ImageView
    private val prefs: SharedPreferences = context.getSharedPreferences("cover_style_prefs", Context.MODE_PRIVATE)

    private var originalPadding: Padding? = null
    private lateinit var parentLayout: ConstraintLayout
    private var isFullWidthMode = false

    private var rotationAnimator: ObjectAnimator? = null
    private var lastRotation = 0f
    private var isRotating = false

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
        private const val PREF_IS_ROTATING = "is_rotating"
        private const val PREF_LAST_ROTATION = "last_rotation"

        // Add default padding values from your layout
        private const val DEFAULT_PADDING = 20
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

        // Load rotation state
        isRotating = prefs.getBoolean(PREF_IS_ROTATING, false)
        lastRotation = prefs.getFloat(PREF_LAST_ROTATION, 0f)

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
            putBoolean(PREF_IS_ROTATING, isRotating)
            putFloat(PREF_LAST_ROTATION, lastRotation)
        }
    }

    private fun startRotation() {
        stopRotation()

        if (!isRotating) return

        rotationAnimator = ObjectAnimator.ofFloat(albumArtCard, View.ROTATION, lastRotation, lastRotation + 360f).apply {
            duration = 8000 // Increased duration to reduce lag
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            addUpdateListener { animation ->
                lastRotation = (animation.animatedValue as Float) % 360f
            }
            // Add cleanup listener
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isRotating) {
                        albumArtCard.rotation = 0f
                        lastRotation = 0f
                    }
                }
            })
        }
        rotationAnimator?.start()
    }

    private fun stopRotation() {
        rotationAnimator?.apply {
            cancel()
            removeAllListeners()
            removeAllUpdateListeners()
        }
        rotationAnimator = null

        // Reset rotation only if we're not in circular mode
        if (!isRotating || albumArtCard.parent?.let {
                (it as? ViewGroup)?.findViewById<RadioGroup>(R.id.coverStyleGroup)?.checkedRadioButtonId
            } != R.id.styleCircle) {
            albumArtCard.animate().rotation(0f).setDuration(300).start()
            lastRotation = 0f
        }
    }

    private fun applyStyle(style: CoverStyle, cornerRadius: Float, coverSize: Int, forceUpdate: Boolean = false) {
        if (!::albumArtCard.isInitialized) return

        // First stop any existing rotation
        if (style != CoverStyle.CIRCULAR && isRotating) {
            isRotating = false
            stopRotation()
        }

        // Reset everything to default state
        resetAllLayoutParameters()
        isFullWidthMode = false

        // Use post to ensure layout measurements are ready
        albumArtCard.post {
            when (style) {
                CoverStyle.DEFAULT -> applyDefaultStyle(cornerRadius, coverSize)
                CoverStyle.CIRCULAR -> applyCircularStyle(coverSize)
                CoverStyle.FULL_WIDTH -> applyFullWidthStyle(cornerRadius)
                CoverStyle.EXPANDED_TOP -> applyExpandedTopStyle(cornerRadius)
                CoverStyle.SQUARE -> applySquareStyle(coverSize)
            }

            // Force layout update
            if (forceUpdate) {
                albumArtCard.requestLayout()
                parentLayout.requestLayout()
            }
        }

        updateWindowDecorations(style)
        if (!forceUpdate) {
            savePreferences(style, cornerRadius, coverSize)
        }
    }


    fun applyCoverStyle(style: CoverStyle, cornerRadius: Float, coverSize: Int) {
        applyStyle(style, cornerRadius, coverSize)
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
        isRotating = false
        stopRotation()

        // Clear preferences
        prefs.edit().clear().apply()

        // Reset all layout parameters and restore padding
        resetAllLayoutParameters()

        // Apply default style with force update
        applyStyle(CoverStyle.DEFAULT, DEFAULT_CORNER_RADIUS, DEFAULT_COVER_SIZE, true)
        updateWindowDecorations(CoverStyle.DEFAULT)
    }

    private fun applyDefaultStyle(cornerRadius: Float, coverSize: Int) {
        // First restore default padding
        restoreDefaultPadding()

        isRotating = false
        stopRotation()

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
            albumArtCard.elevation = 4f
            // Only start rotation after the layout is complete
            if (isRotating) {
                startRotation()
            }
        }
        albumArtImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    fun setRotating(rotating: Boolean) {
        isRotating = rotating
        if (isRotating) {
            startRotation()
        } else {
            stopRotation()
        }
        // Save the rotation state
        prefs.edit {
            putBoolean(PREF_IS_ROTATING, isRotating)
            putFloat(PREF_LAST_ROTATION, lastRotation)
        }
    }

    private fun isBackgroundBlurry(): Boolean {
        return prefs.getBoolean("useBlurBackground", false)
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
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)

                if (isBackgroundBlurry()) {
                    applyBottomBlurEffect()
                }
            }
            else -> {
                // Restore blur effect for other styles if needed
                if (playerView.visibility == View.VISIBLE) {
                    // Apply your normal blur effect here
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
    }

    private fun applyFullWidthStyle(cornerRadius: Float) {
        val params = albumArtCard.layoutParams as ConstraintLayout.LayoutParams
        params.clearAllConstraints()

        // Get the status bar height
        val statusBarHeight = context.resources.getDimensionPixelSize(
            context.resources.getIdentifier("status_bar_height", "dimen", "android")
        )

        params.apply {
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
            height = ConstraintLayout.LayoutParams.WRAP_CONTENT
            topMargin = -statusBarHeight // Set the top margin to negative status bar height
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            parentLayout.setPadding(0, 0, 0, parentLayout.paddingBottom)
        }

        playerView.post {
            // Get the width of the albumArtCard
            val width = albumArtCard.width

            // Set the height to match the width
            params.height = width

            albumArtCard.layoutParams = params
            applyBottomBlurEffect()
        }

        val closeButton = playerView.findViewById<View>(R.id.btn_close_player)
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

    private fun applyBottomBlurEffect() {
        val blurHeight = context.resources.getDimensionPixelSize(R.dimen.bottom_blur_height)

        albumArtImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                albumArtImage.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val originalBitmap = albumArtImage.drawable.toBitmap()
                val blurredBitmap = createBottomBlurredBitmap(originalBitmap, blurHeight)

                val layerDrawable = LayerDrawable(arrayOf(
                    BitmapDrawable(context.resources, originalBitmap),
                    BitmapDrawable(context.resources, blurredBitmap)
                ))

                albumArtImage.setImageDrawable(layerDrawable)
            }
        })
    }

    private fun createBottomBlurredBitmap(original: Bitmap, blurHeight: Int): Bitmap {
        val width = original.width
        val height = original.height

        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // Extract the bottom part of the original image
        val bottomPart = Bitmap.createBitmap(original, 0, height - blurHeight, width, blurHeight)

        // Create a blurred version of the bottom part
        val blurredBottomBitmap = Bitmap.createBitmap(width, blurHeight, Bitmap.Config.ARGB_8888)
        blurBitmap(bottomPart, blurredBottomBitmap)

        // Create a gradient mask for smooth transition
        val gradientMask = createGradientMask(width, blurHeight)

        // Apply the gradient mask to the blurred bottom part
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawBitmap(blurredBottomBitmap, 0f, (height - blurHeight).toFloat(), null)
        canvas.drawBitmap(gradientMask, 0f, (height - blurHeight).toFloat(), paint)

        return outputBitmap
    }

    private fun blurBitmap(input: Bitmap, output: Bitmap) {
        val rs = RenderScript.create(context)
        val allocIn = Allocation.createFromBitmap(rs, input)
        val allocOut = Allocation.createFromBitmap(rs, output)

        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        blur.setInput(allocIn)
        blur.setRadius(25f)  // Increase blur radius for more noticeable effect
        blur.forEach(allocOut)

        allocOut.copyTo(output)
        rs.destroy()
    }

    private fun createGradientMask(width: Int, height: Int): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)

        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            null,
            Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        return mask
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

    fun cleanup() {
        if (::albumArtCard.isInitialized) {
            stopRotation()
            isRotating = false
            lastRotation = 0f
            albumArtCard.clearAnimation()
        }
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
    val isRotating = prefs.getBoolean("is_rotating", false)

    // Set initial values in UI
    val radioGroup = view.findViewById<RadioGroup>(R.id.coverStyleGroup)
    val cornerRadiusSeekBar = view.findViewById<SeekBar>(R.id.cornerRadiusSeekBar)
    val coverSizeSeekBar = view.findViewById<SeekBar>(R.id.coverSizeSeekBar)
    val circularOptionsContainer = view.findViewById<LinearLayout>(R.id.circularOptionsContainer)
    val circularOptionsGroup = view.findViewById<RadioGroup>(R.id.circularOptionsGroup)
    val circularRotating = view.findViewById<RadioButton>(R.id.circularRotating)

    radioGroup.setOnCheckedChangeListener { _, checkedId ->
        circularOptionsContainer.visibility = if (checkedId == R.id.styleCircle) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    if (savedStyle == CoverStyleCustomizer.CoverStyle.CIRCULAR.name && isRotating) {
        circularRotating.isChecked = true
    }

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

        if (selectedStyle == CoverStyleCustomizer.CoverStyle.CIRCULAR) {
            coverStyleCustomizer.setRotating(circularRotating.isChecked)
        } else {
            coverStyleCustomizer.setRotating(false)
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