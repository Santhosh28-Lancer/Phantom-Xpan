package com.santhosh.x200tcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.FrameLayout
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.ImageFormat
import android.media.ImageReader
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import java.io.OutputStream
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.Log
import android.view.Gravity
import android.widget.AdapterView
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraPicker: Spinner
    private lateinit var report: TextView
    private lateinit var preview: TextureView
    private lateinit var previewFrame: FrameLayout
    private lateinit var zoomLabel: TextView
    private lateinit var zoomSeek: SeekBar
    private lateinit var exposureLabel: TextView
    private lateinit var exposureSeek: SeekBar
    private lateinit var focusPicker: Spinner
    private lateinit var whiteBalancePicker: Spinner
    private lateinit var photoModePicker: Spinner
    private lateinit var photoModeStrip: LinearLayout
    private var photoModes: List<Pair<String, Int>> = listOf("AUTO" to CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
    private var selectedPhotoSceneMode: Int = CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED
    private enum class AppPhotoMode { NORMAL, PRO, XPAN }
    @Volatile private var appPhotoMode: AppPhotoMode = AppPhotoMode.NORMAL
    private lateinit var captureButton: Button
    private lateinit var hudOverlay: HudOverlay
    private lateinit var flashButton: Button
    private lateinit var gridButton: Button
    private lateinit var filterButton: Button
    private enum class ColorLook(val title: String, val subtitle: String) {
        ORIGINAL("Original", "Natural camera color"),
        HASSELBLAD("Hasselblad Natural", "Soft contrast, restrained saturation, warm highlights"),
        ZEISS("ZEISS Natural", "Natural micro-contrast, neutral color, cool-clean shadows"),
        VINTAGE("Vintage", "Faded warmth and lifted blacks"),
        RETRO("Retro 70s", "Amber warmth, muted greens, film fade"),
        CLASSIC("Classic Chrome", "Muted color with stronger tonal contrast"),
        CINEMA("Cinematic Teal", "Cool shadows and warm highlights"),
        WARM("Golden Hour", "Warm skin tones and golden highlights"),
        MONO("Monochrome", "Classic black and white")
    }
    @Volatile private var selectedColorLook: ColorLook = ColorLook.ORIGINAL
    // High-quality noise reduction is always enabled; there is no user-facing toggle.
    @Volatile private var lastPreviewIso: Int = 0
    @Volatile private var lastPreviewExposureNs: Long = 0L
    private lateinit var switchCameraButton: Button
    private lateinit var controlsToggle: Button
    private lateinit var advancedControls: LinearLayout
    private var gridEnabled = false
    private var torchEnabled = false
    private var captureFlashMode = 0 // 0=off, 1=auto, 2=on
    private var volumeShutterEnabled = true
    private var stabilizationEnabled = true
    private var highQualityJpegEnabled = true
    private var selectedMegapixels = 0 // 0=max available
    private var saveRelativePath = "Pictures/PhantomXpan"
    private lateinit var manualSensorCheck: CheckBox
    private lateinit var isoLabel: TextView
    private lateinit var isoSeek: SeekBar
    private lateinit var shutterLabel: TextView
    private lateinit var shutterSeek: SeekBar
    private lateinit var focusDistanceLabel: TextView
    private lateinit var focusDistanceSeek: SeekBar
    private lateinit var aeLockCheck: CheckBox
    private lateinit var awbLockCheck: CheckBox
    private var minimumFocusDistanceDiopters = 0f
    private var selectedFocusDistanceDiopters = 0f
    private var aeLockSupported = false
    private var awbLockSupported = false
    private var isoMin = 100
    private var isoMax = 1600
    private var exposureTimeMinNs = 1_000_000L
    private var exposureTimeMaxNs = 33_000_000L
    private var manualSensorSupported = false
    private var manualSensorEnabled = false
    private var selectedIso = 100
    private var selectedExposureTimeNs = 10_000_000L
    private var exposureMin = 0
    private var exposureMax = 0
    private var focusModes = intArrayOf()
    private var whiteBalanceModes = intArrayOf()
    @Volatile private var previewRequestBuilder: CaptureRequest.Builder? = null
    @Volatile private var previewSurface: Surface? = null
    @Volatile private var imageReader: ImageReader? = null
    @Volatile private var zoomRatio = 1.0f
    private var cameraIds: Array<String> = emptyArray()
    @Volatile private var activeCameraId: String? = null
    @Volatile private var cameraDevice: CameraDevice? = null
    @Volatile private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    // Invalidates callbacks that arrive after a camera switch or close.
    @Volatile private var cameraGeneration: Long = 0L
    private val permissionRequestCode = 501
    private val maxReportedSizes = 12

    @Suppress("DEPRECATION")
    private fun applyImmersiveMode() {
        // Hide status + navigation bars while keeping transient swipe-to-reveal behavior.
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phantom Xpan is a dedicated landscape-only camera.
        appPhotoMode = AppPhotoMode.XPAN
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val prefs = getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE)
        selectedMegapixels = prefs.getInt("megapixels", 0)
        saveRelativePath = prefs.getString("save_path", "Pictures/PhantomXpan") ?: "Pictures/PhantomXpan"
        captureFlashMode = prefs.getInt("flash_mode", 0).coerceIn(0, 2)
        volumeShutterEnabled = prefs.getBoolean("volume_shutter", true)
        stabilizationEnabled = prefs.getBoolean("stabilization", true)
        highQualityJpegEnabled = prefs.getBoolean("high_quality_jpeg", true)
        selectedColorLook = runCatching { ColorLook.valueOf(prefs.getString("color_look", "ORIGINAL") ?: "ORIGINAL") }.getOrDefault(ColorLook.ORIGINAL)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildUi()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            loadCameras()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequestCode)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("appPhotoMode", appPhotoMode.name)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    private fun buildUi() {
        window.statusBarColor = Color.rgb(5, 7, 9)
        window.navigationBarColor = Color.rgb(5, 7, 9)
        applyImmersiveMode()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(5, 7, 9))
            setPadding(dp(12), dp(6), dp(12), dp(8))
        }

        val header = FrameLayout(this)
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "PHANTOM XPAN"
            textSize = 21f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.rgb(242, 220, 168))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitle = TextView(this).apply {
            text = "65:24 PANORAMIC CAMERA"
            textSize = 9f
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.rgb(160, 167, 175))
        }
        titleBlock.addView(title, LinearLayout.LayoutParams(-2, -2))
        titleBlock.addView(subtitle, LinearLayout.LayoutParams(-2, -2))
        header.addView(titleBlock, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val livePill = TextView(this).apply {
            text = "● LIVE"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(255, 90, 78))
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = roundedBackground(Color.rgb(36, 20, 20), Color.rgb(115, 42, 36), 16)
        }
        header.addView(livePill, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(48)))

        val cameraBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cameraPicker = Spinner(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        cameraPicker.visibility = View.GONE
        cameraBar.addView(cameraPicker, LinearLayout.LayoutParams(0, 1, 1f))
        flashButton = hudButton("ϟ", "Torch")
        gridButton = hudButton("⊞", "Grid")
        filterButton = hudButton("◐", "Color filters: ${selectedColorLook.title}").apply { textSize = 16f }
        filterButton.setOnClickListener { showColorLookPicker() }
        updateColorLookButton()
        switchCameraButton = hudButton("↻", "Switch camera")
        cameraBar.visibility = View.GONE

        previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        preview = TextureView(this)
        preview.surfaceTextureListener = textureListener
        applyPreviewColorLook()
        previewFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        hudOverlay = HudOverlay(this)
        previewFrame.addView(hudOverlay, FrameLayout.LayoutParams(-1, -1))

        // Compact zoom scope inside the viewfinder. Tap the scope to expand lens/zoom choices.
        val zoomOverlay = LinearLayout(this).apply {
            tag = "xpan_zoom_overlay"
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = roundedBackground(Color.argb(218, 8, 10, 13), Color.rgb(70, 76, 84), 16)
            elevation = dp(14).toFloat()
        }
        val zoomScopeButton = Button(this).apply {
            text = "🔍 1.0×"
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.rgb(242, 220, 168))
            background = ColorDrawable(Color.TRANSPARENT)
            minHeight = dp(30)
            minWidth = dp(82)
            setPadding(dp(5), 0, dp(5), 0)
            contentDescription = "Tap magnifier to expand zoom choices"
        }
        val zoomExpanded = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val zoomValueLabel = TextView(this).apply {
            text = "ZOOM  ${String.format(Locale.US, "%.1f", zoomRatio)}×"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        zoomSeek = SeekBar(this).apply {
            max = 100
            progress = 0
            contentDescription = "Viewfinder zoom slider"
        }
        fun getZoomRange(): Pair<Float, Float> {
            val id = activeCameraId ?: return 1f to 1f
            return runCatching {
                val c = cameraManager.getCameraCharacteristics(id)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                    (range?.lower ?: 1f) to (range?.upper ?: c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
                } else 1f to (c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
            }.getOrDefault(1f to 1f)
        }
        fun syncZoomUi() {
            val (minZoom, maxZoom) = getZoomRange()
            zoomValueLabel.text = "ZOOM  ${String.format(Locale.US, "%.1f", zoomRatio)}×"
            zoomSeek.progress = if (maxZoom > minZoom) {
                ((zoomRatio - minZoom) / (maxZoom - minZoom) * 100f).toInt().coerceIn(0, 100)
            } else 0
            zoomScopeButton.text = "🔍 ${String.format(Locale.US, "%.1f", zoomRatio)}×"
        }
        zoomSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val (minZoom, maxZoom) = getZoomRange()
                zoomRatio = minZoom + (maxZoom - minZoom) * progress / 100f
                zoomValueLabel.text = "ZOOM  ${String.format(Locale.US, "%.1f", zoomRatio)}×"
                applyZoom()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        zoomScopeButton.setOnClickListener {
            zoomExpanded.visibility = if (zoomExpanded.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            zoomScopeButton.text = "🔍 ${String.format(Locale.US, "%.1f", zoomRatio)}×"
            zoomOverlay.requestLayout()
        }
        // Premium expanded zoom wheel: a curved scale with a clear selected value, not a stack
        // of tiny buttons and a conventional slider. The compact magnifier remains the toggle.
        val zoomWheel = ZoomWheelView(this, { getZoomRange() }, { ratio ->
            val (minZoom, maxZoom) = getZoomRange()
            zoomRatio = ratio.coerceIn(minZoom, maxZoom)
            zoomValueLabel.text = String.format(Locale.US, "%.1f×  ·  ZOOM", zoomRatio)
            applyZoom()
            syncZoomUi()
        })
        zoomExpanded.addView(zoomValueLabel, LinearLayout.LayoutParams(-1, dp(24)))
        zoomExpanded.addView(zoomWheel, LinearLayout.LayoutParams(-1, dp(150)))
        zoomSeek.visibility = View.GONE
        zoomOverlay.addView(zoomScopeButton, LinearLayout.LayoutParams(-1, dp(30)))
        zoomOverlay.addView(zoomExpanded, LinearLayout.LayoutParams(dp(320), -2))
        previewFrame.addView(zoomOverlay, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(10)
        })

        // Keep flash reachable in the landscape Xpan layout. The legacy camera bar is hidden
        // in this layout, so the flash control must be attached directly to the viewfinder.
        flashButton.apply {
            textSize = 14f
            contentDescription = "Flash: tap to cycle Off, Auto, On; long-press for torch"
            minWidth = dp(42)
            minimumWidth = dp(42)
            minHeight = dp(34)
            minimumHeight = dp(34)
            setPadding(dp(5), 0, dp(5), 0)
        }
        previewFrame.addView(flashButton, FrameLayout.LayoutParams(dp(46), dp(34), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(8)
            rightMargin = dp(8)
        })

        preview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> configurePreviewTransform() }
        previewFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> configurePreviewTransform() }
        // HudOverlay is drawn above TextureView, so touch events must be handled on the
        // overlay itself. A listener on TextureView never receives taps covered by this view.
        hudOverlay.isClickable = true
        hudOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_UP -> {
                    if (event.x >= 0f && event.y >= 0f && event.x < preview.width && event.y < preview.height) {
                        hudOverlay.performClick()
                        focusAtPreviewPoint(event.x, event.y)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> true
                else -> false
            }
        }
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(5)
            bottomMargin = dp(4)
        })

        zoomLabel = TextView(this).apply { text = "1×"; visibility = View.GONE }
        // Reuse the viewfinder zoom slider initialized above; do not reassign it.
        val lensRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        fun lensButton(label: String, targetEquivalentMm: Float, fallbackZoom: Float) =
            hudButton(label, "$label lens").apply {
                textSize = 13f
                setOnClickListener { selectLens(targetEquivalentMm, fallbackZoom) }
            }
        lensRow.addView(lensButton("1×", 24f, 1f), LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
        lensRow.addView(lensButton("2×", 48f, 2f), LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
        lensRow.addView(lensButton("5×", 120f, 5f), LinearLayout.LayoutParams(0, dp(38), 1f))
        // XPAN uses the in-viewfinder zoom scope above; do not add a second lens rail.
        lensRow.visibility = View.GONE

        controlsToggle = hudButton("⌄  CONTROLS", "Show advanced controls").apply {
            visibility = View.GONE
            textSize = 11f
            setOnClickListener {
                advancedControls.visibility = if (advancedControls.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = if (advancedControls.visibility == View.VISIBLE) "✕ CLOSE" else "⚙ CONTROLS"
            }
        }

        advancedControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(8), dp(5), dp(8), dp(4))
            background = roundedBackground(Color.rgb(16, 20, 25), Color.rgb(42, 48, 55), 12)
        }
        val exposureRow = hudSliderRow("EV", "0")
        exposureLabel = exposureRow.first
        exposureSeek = exposureRow.second.apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (exposureMax < exposureMin) return
                    val value = exposureMin + ((exposureMax - exposureMin) * progress / 100f).toInt()
                    exposureLabel.text = String.format(Locale.US, "EV %+d", value)
                    if (fromUser) applyQuickControls(exposure = value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        advancedControls.addView(exposureRow.third, LinearLayout.LayoutParams(-1, dp(34)))

        manualSensorCheck = CheckBox(this).apply {
            text = "MANUAL SENSOR  /  ISO + SHUTTER"
            textSize = 10f
            setTextColor(Color.rgb(242, 220, 168))
            isEnabled = false
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(242, 220, 168))
            setOnCheckedChangeListener { _, checked ->
                manualSensorEnabled = checked && manualSensorSupported
                if (::photoModePicker.isInitialized) photoModePicker.isEnabled = !manualSensorEnabled
                if (::photoModeStrip.isInitialized) rebuildPhotoModeStrip()
                if (manualSensorEnabled && selectedPhotoSceneMode != CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED) {
                    selectedPhotoSceneMode = CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED
                    if (::photoModePicker.isInitialized) photoModePicker.setSelection(0)
                }
                exposureSeek.isEnabled = !manualSensorEnabled && exposureMax > exposureMin
                isoSeek.isEnabled = manualSensorEnabled && manualSensorSupported
                shutterSeek.isEnabled = manualSensorEnabled && manualSensorSupported
                applyManualSensorControls()
            }
        }
        advancedControls.addView(manualSensorCheck, LinearLayout.LayoutParams(-1, dp(30)))

        val isoRow = hudSliderRow("ISO", "100")
        isoLabel = isoRow.first
        isoSeek = isoRow.second.apply {
            max = 100
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedIso = (isoMin + (isoMax - isoMin) * progress / 100f).toInt().coerceIn(isoMin, isoMax)
                    isoLabel.text = "ISO $selectedIso"
                    if (fromUser && manualSensorEnabled) applyManualSensorControls()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        advancedControls.addView(isoRow.third, LinearLayout.LayoutParams(-1, dp(32)))

        val shutterRow = hudSliderRow("SHUTTER", "1/100 s")
        shutterLabel = shutterRow.first
        shutterSeek = shutterRow.second.apply {
            max = 100
            progress = 50
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val minLog = kotlin.math.ln(exposureTimeMinNs.toDouble())
                    val maxLog = kotlin.math.ln(exposureTimeMaxNs.toDouble())
                    selectedExposureTimeNs = kotlin.math.exp(minLog + (maxLog - minLog) * progress / 100.0).toLong()
                        .coerceIn(exposureTimeMinNs, exposureTimeMaxNs)
                    shutterLabel.text = "SHUTTER ${formatExposureTime(selectedExposureTimeNs)}"
                    if (fromUser && manualSensorEnabled) applyManualSensorControls()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        advancedControls.addView(shutterRow.third, LinearLayout.LayoutParams(-1, dp(32)))

        // Retain an internal mode picker for compatibility with existing scene-mode logic;
        // it is not attached to the controls panel. The visible carousel drives it directly.
        photoModePicker = Spinner(this).apply { visibility = View.GONE }
        photoModePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in photoModes.indices) applyPhotoMode(position)
            }
        }

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        focusPicker = Spinner(this)
        whiteBalancePicker = Spinner(this)
        modeRow.addView(focusPicker, LinearLayout.LayoutParams(0, dp(38), 1f))
        modeRow.addView(whiteBalancePicker, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(4) })
        advancedControls.addView(modeRow, LinearLayout.LayoutParams(-1, dp(38)))
        focusPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in focusModes.indices) applyQuickControls(focus = focusModes[position])
            }
        }
        whiteBalancePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in whiteBalanceModes.indices) applyQuickControls(whiteBalance = whiteBalanceModes[position])
            }
        }

        val focusDistanceRow = hudSliderRow("MANUAL FOCUS · ∞", "∞")
        focusDistanceLabel = focusDistanceRow.first
        focusDistanceSeek = focusDistanceRow.second.apply {
            max = 100
            progress = 0
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedFocusDistanceDiopters = minimumFocusDistanceDiopters * progress / 100f
                    focusDistanceLabel.text = if (progress == 0) "FOCUS ∞" else String.format(Locale.US, "FOCUS %.2f D", selectedFocusDistanceDiopters)
                    if (fromUser) applyManualFocusDistance()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        advancedControls.addView(focusDistanceRow.third, LinearLayout.LayoutParams(-1, dp(32)))

        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        aeLockCheck = CheckBox(this).apply {
            text = "AE LOCK"
            textSize = 10f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(242, 220, 168))
            isEnabled = false
            setOnCheckedChangeListener { _, _ -> applyLockControls() }
        }
        awbLockCheck = CheckBox(this).apply {
            text = "AWB LOCK"
            textSize = 10f
            setTextColor(Color.WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(242, 220, 168))
            isEnabled = false
            setOnCheckedChangeListener { _, _ -> applyLockControls() }
        }
        lockRow.addView(aeLockCheck, LinearLayout.LayoutParams(0, dp(32), 1f))
        lockRow.addView(awbLockCheck, LinearLayout.LayoutParams(0, dp(32), 1f))
        advancedControls.addView(lockRow, LinearLayout.LayoutParams(-1, dp(32)))

        // XPAN Pro controls are attached directly to the viewfinder. The legacy controlsToggle
        // is hidden in XPAN mode, so this is the visible entry point.
        lateinit var proControlsButton: Button
        lateinit var proPanelScroll: ScrollView
        proControlsButton = hudButton("PRO ⚙", "Open Pro camera controls").apply {
            tag = "xpan_pro_button"
            visibility = View.VISIBLE
            textSize = 10f
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                val opening = proPanelScroll.visibility != View.VISIBLE
                proPanelScroll.visibility = if (opening) View.VISIBLE else View.GONE
                advancedControls.visibility = if (opening) View.VISIBLE else View.GONE
                text = if (opening) "✕ CLOSE" else "PRO ⚙"
                if (opening) proPanelScroll.bringToFront()
                proControlsButton.bringToFront()
            }
        }
        proPanelScroll = ScrollView(this).apply {
            tag = "xpan_pro_panel"
            visibility = View.GONE
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            background = ColorDrawable(Color.TRANSPARENT)
            elevation = dp(18).toFloat()
            addView(advancedControls, FrameLayout.LayoutParams(-1, -2))
        }
        proControlsButton.elevation = dp(22).toFloat()
        // Fill the remaining viewfinder area and scroll the controls instead of clipping
        // the lower rows at a fixed 236dp height.
        previewFrame.addView(proPanelScroll, FrameLayout.LayoutParams(-1, -1, Gravity.TOP).apply {
            topMargin = dp(44)
            leftMargin = dp(8)
            rightMargin = dp(8)
            bottomMargin = dp(8)
        })
        previewFrame.addView(proControlsButton, FrameLayout.LayoutParams(dp(84), dp(30), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(8)
            topMargin = dp(8)
        })

        // iPhone-inspired capture dock: gallery thumbnail, circular white shutter, camera switch.
        val bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(7), dp(20), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(7, 8, 10))
                cornerRadii = floatArrayOf(dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), dp(18).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        val galleryButton = TextView(this).apply {
            tag = "xpan_gallery_button"
            text = "▧"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(34, 37, 42), Color.rgb(92, 96, 102), 8)
            contentDescription = "Open gallery"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                try {
                    // A media collection URI is not a single image, so ACTION_VIEW often
                    // fails with "Can't find item". ACTION_PICK correctly opens the gallery
                    // picker on the device and lets the user choose an existing photo.
                    val intent = if (Build.VERSION.SDK_INT >= 33) {
                        Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "image/*" }
                    } else {
                        Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                    }
                    startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    try {
                        startActivity(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "No gallery or photo picker available", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Unable to open gallery: ${e.localizedMessage ?: "unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottomControls.addView(galleryButton, LinearLayout.LayoutParams(dp(42), dp(42)).apply { gravity = Gravity.BOTTOM })
        val shutterSpace = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        captureButton = Button(this).apply {
            text = ""
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            isEnabled = false
            background = shutterBackground(false)
            contentDescription = "Capture photo"
            stateListAnimator = null
            setOnClickListener { capturePhoto() }
        }
        shutterSpace.addView(captureButton, FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER))
        bottomControls.addView(shutterSpace, LinearLayout.LayoutParams(0, dp(72), 1f))
        // Dedicated Xpan dock: gallery below the shutter on the side rail; no back/switch button.
        bottomControls.addView(View(this), LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(bottomControls, LinearLayout.LayoutParams(-1, dp(78)))

        report = TextView(this) // retained only as an internal diagnostic sink; not attached to the UI

        flashButton.text = when (captureFlashMode) { 1 -> "⚡A"; 2 -> "⚡ON"; else -> "⚡" }
        // Tap cycles still-photo flash Off -> Auto -> On. Long-press toggles the torch.
        flashButton.setOnClickListener { cycleCaptureFlashMode() }
        flashButton.setOnLongClickListener {
            toggleTorch()
            true
        }
        gridButton.setOnClickListener {
            gridEnabled = !gridEnabled
            hudOverlay.showGrid = gridEnabled
            hudOverlay.invalidate()
            gridButton.background = roundedBackground(
                if (gridEnabled) Color.rgb(78, 62, 30) else Color.rgb(22, 27, 33),
                if (gridEnabled) Color.rgb(242, 190, 80) else Color.rgb(58, 66, 75), 10
            )
        }

        // Landscape camera-console layout inspired by the supplied reference image.
        // Portrait keeps the existing compact layout. Video remains visibly unavailable until recording exists.
        if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            root.removeAllViews()
            root.orientation = LinearLayout.HORIZONTAL
            root.setPadding(dp(5), dp(4), dp(5), dp(4))

            cameraBar.removeView(flashButton)
            cameraBar.removeView(gridButton)
            cameraBar.removeView(switchCameraButton)

            val leftRail = LinearLayout(this).apply {
                tag = "xpan_left_rail"
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            }
            val settingsButton = hudButton("⚙", "Phantom Xpan settings").apply {
                textSize = 22f
                setOnClickListener { showPhantomSettings() }
            }
            bottomControls.removeView(galleryButton)
            leftRail.addView(settingsButton, LinearLayout.LayoutParams(dp(46), dp(46)).apply { bottomMargin = dp(8) })
            leftRail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            leftRail.addView(galleryButton, LinearLayout.LayoutParams(dp(46), dp(46)).apply { topMargin = dp(8) })
            leftRail.visibility = View.VISIBLE
            root.addView(leftRail, LinearLayout.LayoutParams(dp(56), -1))

            val centerColumn = LinearLayout(this).apply { tag = "xpan_center_column"; orientation = LinearLayout.VERTICAL }
            header.tag = "xpan_header"
            cameraBar.tag = "xpan_camera_bar"
            centerColumn.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
            centerColumn.addView(cameraBar, LinearLayout.LayoutParams(-1, dp(38)))
            centerColumn.addView(previewFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = dp(2); bottomMargin = dp(2)
            })
            root.addView(centerColumn, LinearLayout.LayoutParams(0, -1, 1f))

            val rightScroll = ScrollView(this).apply {
                tag = "xpan_right_scroll"
                isFillViewport = true
                clipToPadding = false
                setPadding(dp(2), dp(2), dp(1), dp(2))
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val rightRail = LinearLayout(this).apply {
                tag = "xpan_right_rail"
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            // Reference-inspired control rail: lens selector at top, flash/grid options,
            // shutter centered vertically, with no mode carousel or duplicate switch control.
            bottomControls.orientation = LinearLayout.VERTICAL
            bottomControls.gravity = Gravity.CENTER_HORIZONTAL
            bottomControls.setPadding(0, 0, 0, 0)
            bottomControls.background = ColorDrawable(Color.TRANSPARENT)
            bottomControls.removeView(galleryButton)
            bottomControls.removeView(shutterSpace)
            bottomControls.removeAllViews()
            shutterSpace.layoutParams = LinearLayout.LayoutParams(-1, dp(74)).apply { gravity = Gravity.CENTER_HORIZONTAL }

            // Settings already lives on the left rail. Keep the right rail free of duplicate
            // settings/menu and lens labels; zoom is handled by the compact in-viewfinder scope.
            rightRail.addView(lensRow, LinearLayout.LayoutParams(-1, dp(34)))
            rightRail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            rightRail.addView(shutterSpace, LinearLayout.LayoutParams(-1, dp(78)))
            rightRail.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
            val xpanLabel = TextView(this).apply {
                text = "X P A N"
                textSize = 11f
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(235, 160, 73))
                background = roundedBackground(Color.rgb(16, 16, 18), Color.rgb(67, 54, 42), 10)
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }
            rightRail.addView(xpanLabel, LinearLayout.LayoutParams(dp(58), dp(32)).apply { bottomMargin = dp(4) })
            rightScroll.addView(rightRail)
            root.addView(rightScroll, LinearLayout.LayoutParams(dp(104), -1))
        }

        // Float the controls button and panel above the camera UI. Opening controls overlays
        // the preview instead of changing its measured height or crop/transform.
        val overlayRoot = FrameLayout(this)
        overlayRoot.addView(root, FrameLayout.LayoutParams(-1, -1))
        (controlsToggle.parent as? android.view.ViewGroup)?.removeView(controlsToggle)
        controlsToggle.visibility = View.GONE
        advancedControls.visibility = View.GONE
        controlsToggle.isClickable = true
        controlsToggle.isFocusable = true
        controlsToggle.visibility = if (appPhotoMode == AppPhotoMode.PRO) View.VISIBLE else View.GONE
        controlsToggle.bringToFront()
        advancedControls.elevation = dp(12).toFloat()
        // Pro controls are hosted inside the viewfinder scroll overlay.
        advancedControls.elevation = dp(20).toFloat()
        controlsToggle.elevation = dp(24).toFloat()
        setContentView(overlayRoot)
        setAppPhotoMode(appPhotoMode)
        cameraPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in cameraIds.indices) {
                    val nextId = cameraIds[position]
                    val changedCamera = nextId != activeCameraId
                    activeCameraId = nextId
                    if (changedCamera) {
                        zoomRatio = 1.0f
                        if (::zoomSeek.isInitialized) zoomSeek.progress = 0
                        if (::zoomLabel.isInitialized) zoomLabel.text = "1.0×"
                        selectedFocusDistanceDiopters = 0f
                        if (::focusDistanceSeek.isInitialized) focusDistanceSeek.progress = 0
                    }
                    configurePhotoModes(activeCameraId!!)
                    renderReport(activeCameraId!!)
                    if (::hudOverlay.isInitialized) hudOverlay.invalidate()
                    if (preview.isAvailable && hasCameraPermission()) openCamera(activeCameraId!!)
                }
            }
        }
    }

    private fun hudButton(label: String, description: String): Button = Button(this).apply {
        text = label
        textSize = 19f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        contentDescription = description
        setTextColor(Color.rgb(242, 220, 168))
        background = roundedBackground(Color.rgb(22, 27, 33), Color.rgb(58, 66, 75), 10)
        stateListAnimator = null
    }

    private fun roundedBackground(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    /** iPhone-inspired photo shutter: white outer ring and white inner disc with a subtle dark separator. */
    private fun shutterBackground(enabled: Boolean): LayerDrawable {
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(1), Color.rgb(210, 213, 218))
        }
        val separator = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(28, 30, 34))
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (enabled) Color.WHITE else Color.rgb(170, 172, 176))
        }
        return LayerDrawable(arrayOf(outer, separator, inner)).apply {
            setLayerInset(1, dp(3), dp(3), dp(3), dp(3))
            setLayerInset(2, dp(5), dp(5), dp(5), dp(5))
        }
    }

    private fun hudSectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 9f
        letterSpacing = 0.18f
        setTextColor(Color.rgb(160, 167, 175))
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun hudSliderRow(label: String, initial: String): Triple<TextView, SeekBar, LinearLayout> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val text = TextView(this).apply {
            this.text = "$label $initial"
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
        }
        row.addView(text, LinearLayout.LayoutParams(dp(90), dp(32)))
        val seek = SeekBar(this)
        row.addView(seek, LinearLayout.LayoutParams(0, dp(32), 1f))
        return Triple(text, seek, row)
    }

    private inner class ZoomWheelView(
        context: Context,
        private val rangeProvider: () -> Pair<Float, Float>,
        private val onZoomChanged: (Float) -> Unit
    ) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var lastRatio = 1f
        private val gold = Color.rgb(225, 190, 100)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val (minZ, maxZ) = rangeProvider()
            val safeMax = maxZ.coerceAtLeast(minZ + 0.01f)
            val cx = w / 2f
            val cy = h * 1.02f
            val radius = w * 0.43f
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2).toFloat()
            p.color = Color.rgb(72, 77, 82)
            canvas.drawArc(cx-radius, cy-radius, cx+radius, cy+radius, 205f, 130f, false, p)
            val current = lastRatio.coerceIn(minZ, safeMax)
            val fraction = ((kotlin.math.ln(current) - kotlin.math.ln(minZ)) / (kotlin.math.ln(safeMax) - kotlin.math.ln(minZ))).coerceIn(0f, 1f)
            for (i in 0..60) {
                val f = i / 60f
                val angle = Math.toRadians((205f + 130f * f).toDouble())
                val major = i % 5 == 0
                val r1 = radius - if (major) dp(15) else dp(8)
                val r2 = radius - dp(1)
                val x1 = cx + kotlin.math.cos(angle).toFloat() * r1
                val y1 = cy + kotlin.math.sin(angle).toFloat() * r1
                val x2 = cx + kotlin.math.cos(angle).toFloat() * r2
                val y2 = cy + kotlin.math.sin(angle).toFloat() * r2
                p.color = if (kotlin.math.abs(f - fraction) < 0.018f) gold else if (major) Color.rgb(225, 225, 222) else Color.rgb(135, 140, 143)
                p.strokeWidth = (if (major) dp(2) else dp(1)).toFloat()
                canvas.drawLine(x1, y1, x2, y2, p)
            }
            val pointerAngle = Math.toRadians((205f + 130f * fraction).toDouble())
            p.style = Paint.Style.FILL; p.color = gold
            val px = cx + kotlin.math.cos(pointerAngle).toFloat() * (radius - dp(23))
            val py = cy + kotlin.math.sin(pointerAngle).toFloat() * (radius - dp(23))
            canvas.drawCircle(px, py, dp(4).toFloat(), p)
            p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.color = gold; p.textSize = dp(21).toFloat()
            canvas.drawText(String.format(Locale.US, "%.1f×", current), cx, h * 0.58f, p)
            p.color = Color.rgb(165, 169, 173); p.textSize = dp(10).toFloat(); p.typeface = Typeface.DEFAULT
            canvas.drawText("PINCH OR DRAG TO ZOOM", cx, h * 0.75f, p)
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) {
                val (minZ, maxZ) = rangeProvider()
                val fraction = (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                val ratio = kotlin.math.exp(kotlin.math.ln(minZ.coerceAtLeast(0.01f)) + fraction * (kotlin.math.ln(maxZ.coerceAtLeast(minZ + 0.01f)) - kotlin.math.ln(minZ.coerceAtLeast(0.01f))))
                lastRatio = ratio
                onZoomChanged(ratio)
                invalidate()
                return true
            }
            return event.action == android.view.MotionEvent.ACTION_UP
        }
    }

    private inner class HudOverlay(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var showGrid: Boolean = false
        var showXpanFrame: Boolean = true
        var focusPointX: Float? = null
        var focusPointY: Float? = null
        var focusPointTime: Long = 0L
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return
            // Xpan crop guide: the inner frame uses the exact 65:24 ratio used by JPEG output.
            if (showXpanFrame) {
                val targetRatio = 65f / 24f
                val frameW: Float
                val frameH: Float
                if (w / h > targetRatio) {
                    frameH = h
                    frameW = h * targetRatio
                } else {
                    frameW = w
                    frameH = w / targetRatio
                }
                val left = (w - frameW) / 2f
                val top = (h - frameH) / 2f
                paint.style = Paint.Style.FILL
                paint.color = Color.BLACK
                paint.alpha = 135
                canvas.drawRect(0f, 0f, w, top, paint)
                canvas.drawRect(0f, top + frameH, w, h, paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                paint.alpha = 230
                paint.strokeWidth = dp(1.5f.toInt()).toFloat()
                canvas.drawRect(left, top, left + frameW, top + frameH, paint)
            }
            paint.color = Color.WHITE
            paint.strokeWidth = dp(1).toFloat()
            paint.style = Paint.Style.STROKE
            paint.alpha = 190
            val corner = dp(22).toFloat()
            val inset = dp(12).toFloat()
            // Four subtle framing brackets.
            canvas.drawLine(inset, inset, inset + corner, inset, paint)
            canvas.drawLine(inset, inset, inset, inset + corner, paint)
            canvas.drawLine(w - inset, inset, w - inset - corner, inset, paint)
            canvas.drawLine(w - inset, inset, w - inset, inset + corner, paint)
            canvas.drawLine(inset, h - inset, inset + corner, h - inset, paint)
            canvas.drawLine(inset, h - inset, inset, h - inset - corner, paint)
            canvas.drawLine(w - inset, h - inset, w - inset - corner, h - inset, paint)
            canvas.drawLine(w - inset, h - inset, w - inset, h - inset - corner, paint)
            if (focusPointX != null && focusPointY != null && System.currentTimeMillis() - focusPointTime < 1800L) {
                val fx = focusPointX!!.coerceIn(dp(24).toFloat(), w - dp(24).toFloat())
                val fy = focusPointY!!.coerceIn(dp(24).toFloat(), h - dp(24).toFloat())
                val box = dp(48).toFloat()
                paint.color = Color.rgb(255, 210, 64)
                paint.alpha = 240
                paint.strokeWidth = dp(2).toFloat()
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(fx - box/2, fy - box/2, fx + box/2, fy + box/2, dp(5).toFloat(), dp(5).toFloat(), paint)
                postInvalidateDelayed(80)
                paint.color = Color.WHITE
                paint.strokeWidth = dp(1).toFloat()
            }
            if (showGrid) {
                paint.alpha = 100
                canvas.drawLine(w / 3f, inset, w / 3f, h - inset, paint)
                canvas.drawLine(2f * w / 3f, inset, 2f * w / 3f, h - inset, paint)
                canvas.drawLine(inset, h / 3f, w - inset, h / 3f, paint)
                canvas.drawLine(inset, 2f * h / 3f, w - inset, 2f * h / 3f, paint)
            }
            paint.alpha = 210
            val cx = w / 2f
            val cy = h / 2f
            val gap = dp(5).toFloat()
            val arm = dp(11).toFloat()
            canvas.drawLine(cx - gap - arm, cy, cx - gap, cy, paint)
            canvas.drawLine(cx + gap, cy, cx + gap + arm, cy, paint)
            canvas.drawLine(cx, cy - gap - arm, cx, cy - gap, paint)
            canvas.drawLine(cx, cy + gap, cx, cy + gap + arm, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = dp(9).toFloat()
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.alpha = 220
            canvas.drawText("CAM ${activeCameraId ?: "-"}", inset + dp(2), h - inset - dp(5), paint)
            canvas.drawText(if (manualSensorEnabled) "MNL" else "AUTO", w - inset - dp(36), h - inset - dp(5), paint)
        }
    }

    private fun cycleCaptureFlashMode() {
        val id = activeCameraId ?: return
        val hasFlash = try { cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } catch (_: Exception) { false }
        if (!hasFlash) {
            Toast.makeText(this, "Flash is not available on this camera.", Toast.LENGTH_SHORT).show()
            return
        }
        captureFlashMode = (captureFlashMode + 1) % 3
        getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit().putInt("flash_mode", captureFlashMode).apply()
        flashButton.text = when (captureFlashMode) { 1 -> "⚡A"; 2 -> "⚡ON"; else -> "⚡" }
        flashButton.background = roundedBackground(
            if (captureFlashMode == 0) Color.rgb(22, 27, 33) else Color.rgb(78, 62, 30),
            if (captureFlashMode == 0) Color.rgb(58, 66, 75) else Color.rgb(242, 190, 80), 10
        )
        Toast.makeText(this, "Flash: ${when(captureFlashMode) { 1 -> "Auto"; 2 -> "On"; else -> "Off" }}", Toast.LENGTH_SHORT).show()
    }

    private fun chooseJpegSize(sizes: List<Size>): Size {
        if (sizes.isEmpty()) return Size(1920, 1080)
        if (selectedMegapixels <= 0) return sizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(1920, 1080)
        val targetPixels = selectedMegapixels.toLong() * 1_000_000L
        // Choose the closest sensor-supported output size to the requested megapixel target.
        // Prefer sizes at/above target when distance is equal, preserving detail when possible.
        return sizes.minWithOrNull(compareBy<Size> { kotlin.math.abs(it.width.toLong() * it.height - targetPixels) }
            .thenByDescending { it.width.toLong() * it.height }) ?: sizes.maxByOrNull { it.width.toLong() * it.height }!!
    }

    private fun showPhantomSettings() {
        val options = arrayOf(
            "Photo resolution: ${if (selectedMegapixels <= 0) "Maximum available" else "$selectedMegapixels MP target"}",
            "Save location: $saveRelativePath",
            "Flash: ${when (captureFlashMode) { 1 -> "Auto"; 2 -> "On"; else -> "Off" }}",
            "Color look: ${selectedColorLook.title}",
            "Grid overlay: ${if (gridEnabled) "On" else "Off"}",
            "Volume-key shutter: ${if (volumeShutterEnabled) "On" else "Off"}",
            "Still-image stabilization: ${if (stabilizationEnabled) "On" else "Off"}",
            "JPEG quality: ${if (highQualityJpegEnabled) "Maximum" else "Standard"}",
            "Camera / sensor diagnostics",
            "Output bit depth information"
        )
        AlertDialog.Builder(this)
            .setTitle("PHANTOM XPAN · ALL SETTINGS")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showMegapixelChoices()
                    1 -> showSaveLocationChoices()
                    2 -> flashButton.performClick()
                    3 -> showColorLookPicker()
                    4 -> gridButton.performClick()
                    5 -> {
                        volumeShutterEnabled = !volumeShutterEnabled
                        getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit().putBoolean("volume_shutter", volumeShutterEnabled).apply()
                        Toast.makeText(this, "Volume-key shutter ${if (volumeShutterEnabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                        showPhantomSettings()
                    }
                    6 -> {
                        stabilizationEnabled = !stabilizationEnabled
                        getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit().putBoolean("stabilization", stabilizationEnabled).apply()
                        applyStabilizationToPreview()
                        Toast.makeText(this, "Still stabilization ${if (stabilizationEnabled) "enabled" else "disabled"} (when supported)", Toast.LENGTH_SHORT).show()
                        showPhantomSettings()
                    }
                    7 -> {
                        highQualityJpegEnabled = !highQualityJpegEnabled
                        getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit().putBoolean("high_quality_jpeg", highQualityJpegEnabled).apply()
                        Toast.makeText(this, "JPEG output quality: ${if (highQualityJpegEnabled) "Maximum" else "Standard"}", Toast.LENGTH_SHORT).show()
                        showPhantomSettings()
                    }
                    8 -> showCameraZoomDiagnostics()
                    9 -> showOutputBitDepthInfo()
                }
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showOutputBitDepthInfo() {
        val id = activeCameraId
        val message = if (id == null) {
            "No active camera is selected."
        } else runCatching {
            val c = cameraManager.getCameraCharacteristics(id)
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val raw = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpeg = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
            "Standard JPEG is 8-bit per color channel. Your phone's 10-bit ISP processing does not automatically mean a third-party Camera2 app can save a 10-bit JPEG.\n\n" +
                "Largest Camera2 JPEG: ${jpeg?.let { "${it.width} × ${it.height}" } ?: "not reported"}\n" +
                "RAW_SENSOR capability: ${if (raw) "reported" else "not reported"}\n\n" +
                (if (raw) "A future RAW/DNG capture path can preserve higher-bit-depth sensor samples where the sensor and HAL expose them. It requires a separate RAW capture pipeline; 16-bit and 32-bit RGB JPEG output is not a valid camera format." else "This camera ID does not report RAW_SENSOR capability. Check other rear camera IDs in diagnostics.")
        }.getOrElse { "Could not read output capabilities: ${it.message}" }
        AlertDialog.Builder(this).setTitle("OUTPUT BIT DEPTH")
            .setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun applyStabilizationToPreview() {
        val builder = previewRequestBuilder ?: return
        val camera = cameraDevice ?: return
        val handler = backgroundHandler ?: return
        try {
            val chars = cameraManager.getCameraCharacteristics(camera.id)
            val supported = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            val mode = if (stabilizationEnabled && supported.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON else CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, mode)
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Exception) { Log.w("PhantomXpan", "Optical stabilization setting unavailable", e) }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (volumeShutterEnabled && event?.repeatCount == 0 &&
            (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (::captureButton.isInitialized && captureButton.isEnabled) captureButton.performClick()
            return true
        }
        // Android does not allow ordinary apps to intercept the Power button as a shutter.
        return super.onKeyDown(keyCode, event)
    }


    private fun showCameraZoomDiagnostics() {
        val reportText = StringBuilder()
        reportText.append("PHANTOM XPAN · CAMERA DIAGNOSTICS\n\n")
        reportText.append("Active camera: ${activeCameraId ?: "none"}\n")
        reportText.append("Requested zoom: ${String.format(Locale.US, "%.2f", zoomRatio)}×\n\n")
        try {
            val ids = cameraManager.cameraIdList
            ids.forEach { id ->
                val c = cameraManager.getCameraCharacteristics(id)
                val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }
                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString { "%.2f mm".format(Locale.US, it) } ?: "not reported"
                val sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val pixel = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                val ratioRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
                val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val jpeg = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
                val cfa = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
                val whiteLevel = c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
                val blackPattern = c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
                val awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
                // Camera2 exposes aberration-correction modes as a characteristic; color-correction
                // FAST/HIGH_QUALITY are request modes, not a CameraCharacteristics key.
                val aberrationCorrectionModes = c.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES) ?: intArrayOf()
                val stabilizationModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
                reportText.append("CAMERA $id · $facing\n")
                reportText.append("  Focal length(s): $focal\n")
                reportText.append("  Sensor: ${sensor?.width ?: "?"} × ${sensor?.height ?: "?"} mm\n")
                reportText.append("  Pixel array: ${pixel?.width ?: "?"} × ${pixel?.height ?: "?"}\n")
                reportText.append("  Max digital zoom: ${"%.1f".format(Locale.US, maxZoom)}×\n")
                reportText.append("  Zoom ratio range: ${ratioRange?.let { "${it.lower}×–${it.upper}×" } ?: "not exposed"}\n")
                reportText.append("  Largest JPEG stream: ${jpeg?.let { "${it.width}×${it.height}" } ?: "not reported"}\n")
                reportText.append("  Sensor color filter arrangement: ${cfa ?: "not reported"}\n")
                reportText.append("  Sensor white level: ${whiteLevel ?: "not reported"}; black-level pattern: ${blackPattern ?: "not reported"}\n")
                reportText.append("  AWB modes: ${awbModes.joinToString()}; aberration-correction modes: ${aberrationCorrectionModes.joinToString()}\n")
                reportText.append("  Optical stabilization modes: ${stabilizationModes.joinToString()}\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val physicalIds: Set<String> = c.physicalCameraIds
                    if (physicalIds.isNotEmpty()) {
                        reportText.append("  Logical camera physical IDs: ${physicalIds.joinToString()}\n")
                    }
                }
                reportText.append("\n")
            }
            reportText.append("NOTE: Camera2-reported zoom is not proof of optical lens switching. The system camera HAL may switch lenses at some zoom boundaries; if it does not, magnification is digital and detail can fall.\n")
        } catch (e: Exception) {
            reportText.append("\nDiagnostics error: ${e.message}")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@MainActivity).apply {
                text = reportText.toString()
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(16), dp(12), dp(16), dp(12))
                typeface = android.graphics.Typeface.MONOSPACE
            })
        }
        AlertDialog.Builder(this)
            .setTitle("Camera / Zoom Diagnostics")
            .setView(scroll)
            .setPositiveButton("Copy report") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phantom Xpan camera diagnostics", reportText.toString()))
                Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showMegapixelChoices() {
        val labels = arrayOf("Maximum available", "12 MP", "24 MP", "48 MP")
        val values = intArrayOf(0, 12, 24, 48)
        val selected = values.indexOf(selectedMegapixels).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("JPEG OUTPUT RESOLUTION")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                selectedMegapixels = values[which]
                getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit().putInt("megapixels", selectedMegapixels).apply()
                dialog.dismiss()
                activeCameraId?.let { id -> if (preview.isAvailable && hasCameraPermission()) openCamera(id) }
                Toast.makeText(this, if (selectedMegapixels == 0) "Resolution: Maximum available" else "Resolution target: $selectedMegapixels MP (nearest supported size)", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showSaveLocationChoices() {
        val paths = arrayOf("Pictures/PhantomXpan", "DCIM/PhantomXpan", "Pictures/X200TCameraLab")
        val labels = arrayOf("Pictures / Phantom Xpan", "DCIM / Phantom Xpan", "Pictures / X200T Camera Lab")
        val selected = paths.indexOf(saveRelativePath).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("SAVE LOCATION")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                saveRelativePath = paths[which]
                getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit().putString("save_path", saveRelativePath).apply()
                dialog.dismiss()
                Toast.makeText(this, "Saving to $saveRelativePath", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun toggleTorch() {
        val id = activeCameraId ?: return
        val hasFlash = try {
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } catch (_: Exception) { false }
        if (!hasFlash) {
            Toast.makeText(this, "Flash/torch is not available for this camera.", Toast.LENGTH_SHORT).show()
            return
        }
        torchEnabled = !torchEnabled
        val builder = previewRequestBuilder
        val handler = backgroundHandler
        val session = captureSession
        if (builder == null || handler == null || session == null) {
            torchEnabled = false
            Toast.makeText(this, "Camera preview is not ready for torch.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Torch mode requires AE ON or OFF; keep AE ON for normal preview metering.
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.FLASH_MODE, if (torchEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF)
            session.setRepeatingRequest(builder.build(), null, handler)
            flashButton.background = roundedBackground(
                if (torchEnabled) Color.rgb(78, 62, 30) else Color.rgb(22, 27, 33),
                if (torchEnabled) Color.rgb(242, 190, 80) else Color.rgb(58, 66, 75), 10
            )
            flashButton.text = if (torchEnabled) "ϟ●" else "ϟ"
        } catch (e: Exception) {
            torchEnabled = false
            Toast.makeText(this, "Torch unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            configurePreviewTransform(width, height)
            activeCameraId?.let { if (hasCameraPermission()) openCamera(it) }
        }
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configurePreviewTransform()
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }
    }


    /**
     * Camera2Basic-style TextureView transform.
     *
     * SENSOR_ORIENTATION is used by Camera2 to describe the sensor coordinate system, but it
     * must not be blindly added to a display rotation that the camera/SurfaceTexture pipeline
     * has already accounted for. Doing so can rotate the visible preview twice on devices such
     * as the Vivo X200T. This maps the swapped buffer rectangle into the view, center-crops
     * uniformly, and applies the display's quarter-turn only when the display is rotated.
     */
    private fun configurePreviewTransform(bufferWidth: Int? = null, bufferHeight: Int? = null) {
        if (!::preview.isInitialized || preview.width <= 0 || preview.height <= 0) return
        val id = activeCameraId ?: return
        val size = if (bufferWidth != null && bufferHeight != null) {
            Size(bufferWidth, bufferHeight)
        } else {
            try { choosePreviewSize(id) } catch (_: Exception) { return }
        }

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val viewWidth = preview.width.toFloat()
        val viewHeight = preview.height.toFloat()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
        val swapped = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val bufferRect = if (swapped) {
            RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
        } else {
            RectF(0f, 0f, size.width.toFloat(), size.height.toFloat())
        }

        if (swapped) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight / size.height.toFloat(),
                viewWidth / size.width.toFloat()
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90f * (rotation - 2), centerX, centerY)
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, centerX, centerY)
        }

        preview.setTransform(matrix)
        if (::hudOverlay.isInitialized) hudOverlay.invalidate()
    }

    private fun configurePhotoModes(cameraId: String) {
        // Scene modes caused preview/still request mismatches. Keep Camera Lab modes independent
        // from vendor scene-mode overrides; all three app modes use the same selected camera path.
        photoModes = listOf("AUTO" to CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
        selectedPhotoSceneMode = CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED
        if (::photoModePicker.isInitialized) {
            photoModePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("AUTO"))
            photoModePicker.setSelection(0, false)
        }
        if (::photoModeStrip.isInitialized) rebuildPhotoModeStrip()
    }

    private fun rebuildPhotoModeStrip() {
        if (!::photoModeStrip.isInitialized) return
        photoModeStrip.removeAllViews()
        val modes = listOf(
            AppPhotoMode.NORMAL to "PHOTO",
            AppPhotoMode.PRO to "PRO",
            AppPhotoMode.XPAN to "X PAN"
        )
        modes.forEach { (mode, label) ->
            val selected = appPhotoMode == mode
            val button = TextView(this).apply {
                text = label
                textSize = 12f
                letterSpacing = 0.03f
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(5), dp(15), dp(5))
                setTextColor(if (selected) Color.rgb(242, 205, 72) else Color.WHITE)
                background = if (selected) roundedBackground(Color.rgb(38, 34, 22), Color.rgb(104, 85, 39), 18)
                else roundedBackground(Color.TRANSPARENT, Color.TRANSPARENT, 18)
                contentDescription = "$label camera mode"
                setOnClickListener { setAppPhotoMode(mode) }
            }
            photoModeStrip.addView(button, LinearLayout.LayoutParams(-2, dp(34)).apply {
                leftMargin = dp(3); rightMargin = dp(3)
            })
        }
    }

    private fun setAppPhotoMode(mode: AppPhotoMode) {
        if (mode != AppPhotoMode.XPAN) return
        appPhotoMode = AppPhotoMode.XPAN
        // X PAN is a dedicated landscape UI. Its live viewfinder and saved JPEG both use
        // the 65:24 composition. PHOTO and PRO retain the portrait viewfinder.
        val wantLandscape = mode == AppPhotoMode.XPAN
        // Let the physical device orientation decide the panoramic axis. The HUD remains in
        // its same camera layout; only the 65:24 composition window changes axis.
        // X PAN is deliberately minimal: keep only the shutter beside the wide viewfinder.
        // All other modes retain the normal mode/zoom/gallery/switch controls.
        window.decorView.post {
            // Phantom Xpan is a single-mode camera. Keep its title/settings rail visible and
            // never resurrect the old PHOTO/PRO mode controls.
            window.decorView.findViewWithTag<View>("xpan_left_rail")?.visibility = View.VISIBLE
            window.decorView.findViewWithTag<View>("xpan_header")?.visibility = View.VISIBLE
            window.decorView.findViewWithTag<View>("xpan_camera_bar")?.visibility = View.GONE
            window.decorView.findViewWithTag<View>("xpan_gallery_button")?.visibility = View.VISIBLE
            window.decorView.findViewWithTag<View>("xpan_switch_button")?.visibility = View.GONE
            // Keep the new controls explicitly visible in XPAN mode and above the HUD overlay.
            window.decorView.findViewWithTag<View>("xpan_zoom_overlay")?.apply {
                visibility = View.VISIBLE
                bringToFront()
            }
            window.decorView.findViewWithTag<View>("xpan_pro_button")?.apply {
                visibility = View.VISIBLE
                bringToFront()
            }
            window.decorView.findViewWithTag<View>("xpan_right_scroll")?.layoutParams?.let { lp ->
                lp.width = dp(142)
                window.decorView.findViewWithTag<View>("xpan_right_scroll")?.layoutParams = lp
            }
            previewFrame.post { configurePreviewTransform() }
        }

        if (::previewFrame.isInitialized) {
            val lp = previewFrame.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val parent = previewFrame.parent as? LinearLayout
                if (isLandscape) {
                    lp.width = -1
                    lp.height = 0
                    lp.weight = 1f
                    previewFrame.layoutParams = lp
                    parent?.gravity = Gravity.CENTER_VERTICAL
                    previewFrame.post {
                        val measuredWidth = previewFrame.width.takeIf { it > 0 } ?: parent?.width ?: 0
                        if (measuredWidth > 0) {
                            val targetHeight = (measuredWidth * 24f / 65f).toInt().coerceAtLeast(dp(72))
                            val p = previewFrame.layoutParams as? LinearLayout.LayoutParams
                            if (p != null) {
                                p.height = targetHeight
                                p.weight = 0f
                                p.width = -1
                                previewFrame.layoutParams = p
                                parent?.gravity = Gravity.CENTER_VERTICAL
                            }
                        }
                        configurePreviewTransform()
                    }
                } else {
                    // Portrait-held phone: keep the HUD and dock in their normal locations,
                    // but make the active panoramic window vertical (24:65).
                    lp.width = 0
                    lp.height = 0
                    lp.weight = 1f
                    previewFrame.layoutParams = lp
                    parent?.gravity = Gravity.CENTER_HORIZONTAL
                    previewFrame.post {
                        val measuredHeight = previewFrame.height
                        if (measuredHeight > 0) {
                            val targetWidth = (measuredHeight * 24f / 65f).toInt().coerceAtLeast(dp(72))
                            val p = previewFrame.layoutParams as? LinearLayout.LayoutParams
                            if (p != null) {
                                p.width = targetWidth
                                p.height = 0
                                p.weight = 1f
                                previewFrame.layoutParams = p
                                parent?.gravity = Gravity.CENTER_HORIZONTAL
                            }
                        }
                        configurePreviewTransform()
                    }
                }
                previewFrame.post { configurePreviewTransform() }
            }
        }
        // Normal Photo deliberately exposes no manual controls. Pro owns all manual controls.
        if (mode == AppPhotoMode.XPAN) {
            zoomRatio = 1.0f
            if (::zoomSeek.isInitialized) zoomSeek.progress = 0
            if (::zoomLabel.isInitialized) zoomLabel.text = "1.0×"
            applyZoom()
        }
        val isPro = mode == AppPhotoMode.PRO
        controlsToggle.visibility = if (isPro) View.VISIBLE else View.GONE
        if (::photoModeStrip.isInitialized) photoModeStrip.visibility = if (mode == AppPhotoMode.XPAN) View.GONE else View.VISIBLE
        if (::zoomSeek.isInitialized) zoomSeek.visibility = if (mode == AppPhotoMode.XPAN) View.GONE else View.VISIBLE
        if (::zoomLabel.isInitialized) zoomLabel.visibility = if (mode == AppPhotoMode.XPAN) View.GONE else View.VISIBLE
        if (!isPro) {
            advancedControls.visibility = View.GONE
            controlsToggle.text = "⚙ CONTROLS"
            manualSensorEnabled = false
            if (::manualSensorCheck.isInitialized && manualSensorCheck.isChecked) {
                manualSensorCheck.setOnCheckedChangeListener(null)
                manualSensorCheck.isChecked = false
                manualSensorCheck.setOnCheckedChangeListener { _, checked ->
                    manualSensorEnabled = checked && manualSensorSupported
                    exposureSeek.isEnabled = !manualSensorEnabled && exposureMax > exposureMin
                    isoSeek.isEnabled = manualSensorEnabled && manualSensorSupported
                    shutterSeek.isEnabled = manualSensorEnabled && manualSensorSupported
                    applyManualSensorControls()
                }
            }
        }
        if (mode != AppPhotoMode.XPAN) selectedPhotoSceneMode = CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED
        if (::photoModePicker.isInitialized) photoModePicker.setSelection(0, false)
        if (::photoModeStrip.isInitialized) rebuildPhotoModeStrip()
        val builder = previewRequestBuilder
        val handler = backgroundHandler
        try {
            if (builder != null && handler != null) {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
                val chars = activeCameraId?.let { cameraManager.getCameraCharacteristics(it) }
                val afModes = chars?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                val preferredAf = when {
                    afModes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    afModes.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO) -> CameraCharacteristics.CONTROL_AF_MODE_AUTO
                    afModes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    else -> CameraCharacteristics.CONTROL_AF_MODE_OFF
                }
                builder.set(CaptureRequest.CONTROL_AF_MODE, preferredAf)
                builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, null)
                captureSession?.setRepeatingRequest(builder.build(), null, handler)
                preview.post { configurePreviewTransform() }
            }
        } catch (e: Exception) {
            Log.e("X200TCameraLab", "Mode request failed: $mode", e)
        }
        Toast.makeText(this, when (mode) {
            AppPhotoMode.NORMAL -> "Photo · Auto focus"
            AppPhotoMode.PRO -> "Pro · Manual controls available"
            AppPhotoMode.XPAN -> "X Pan · 65:24 panorama crop"
        }, Toast.LENGTH_SHORT).show()
    }

    private fun applyPhotoMode(position: Int) {
        if (position !in photoModes.indices) return
        val scene = photoModes[position].second
        selectedPhotoSceneMode = scene
        if (::photoModeStrip.isInitialized) rebuildPhotoModeStrip()
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        try {
            if (manualSensorEnabled && scene != CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED) {
                Toast.makeText(this, "Turn off Manual Sensor to use scene modes.", Toast.LENGTH_SHORT).show()
                selectedPhotoSceneMode = CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED
                if (::photoModePicker.isInitialized) photoModePicker.setSelection(0)
                return
            }
            if (scene == CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED) {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, scene)
            }
            val supportedAf = activeCameraId?.let { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) } ?: intArrayOf()
            val preferredAf = when {
                supportedAf.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                supportedAf.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                supportedAf.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO) -> CameraCharacteristics.CONTROL_AF_MODE_AUTO
                else -> CameraCharacteristics.CONTROL_AF_MODE_OFF
            }
            builder.set(CaptureRequest.CONTROL_AF_MODE, preferredAf)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
            hudOverlay.invalidate()
        } catch (e: Exception) {
            Toast.makeText(this, "Photo mode unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Selects the requested framing preset. The camera HAL may switch physical lenses
     * automatically when CONTROL_ZOOM_RATIO is used on a logical multi-camera device.
     */
    private fun selectLens(targetEquivalentMm: Float, fallbackZoom: Float) {
        // targetEquivalentMm is retained for future physical-lens matching. For now, use
        // the requested zoom ratio so this works on devices that expose only one camera ID.
        val chars = activeCameraId?.let { runCatching { cameraManager.getCameraCharacteristics(it) }.getOrNull() }
        val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) chars?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        val minZoom = zoomRange?.lower ?: 1.0f
        val maxZoom = zoomRange?.upper ?: chars?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        zoomRatio = fallbackZoom.coerceIn(minZoom, maxZoom.coerceAtLeast(minZoom))
        if (::zoomLabel.isInitialized) {
            zoomLabel.text = when (fallbackZoom) {
                1.0f -> "1×"
                2.0f -> "2×"
                5.0f -> "5×"
                else -> String.format(Locale.US, "%.1f×", fallbackZoom)
            }
        }
        if (::zoomSeek.isInitialized) {
            val maxZoom = activeCameraId?.let { id ->
                runCatching {
                    val c = cameraManager.getCameraCharacteristics(id)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper
                        ?: c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                    else c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                }.getOrDefault(1f)
            } ?: 1f
            zoomSeek.progress = if (maxZoom > 1f) {
                (kotlin.math.ln(zoomRatio) / kotlin.math.ln(maxZoom) * zoomSeek.max).toInt().coerceIn(0, zoomSeek.max)
            } else 0
        }
        applyZoom()
    }

    private fun applyZoom() {
        val builder = previewRequestBuilder ?: return
        val camera = cameraDevice ?: return
        val handler = backgroundHandler ?: return
        try {
            val chars = cameraManager.getCameraCharacteristics(camera.id)
            val maxDigital = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val range = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                val maxRatio = range?.upper ?: maxDigital
                val minRatio = range?.lower ?: 1.0f
                val ratio = zoomRatio.coerceIn(minRatio, maxRatio)
                // Logical multi-camera HALs can switch physical sensors at lens boundaries.
                // CONTROL_ZOOM_RATIO preserves that path; a hand-built crop region can bypass it.
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, ratio)
            } else {
                val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
                val ratio = zoomRatio.coerceIn(1.0f, maxDigital)
                val cropWidth = (active.width() / ratio).toInt().coerceAtLeast(1)
                val cropHeight = (active.height() / ratio).toInt().coerceAtLeast(1)
                val left = active.left + (active.width() - cropWidth) / 2
                val top = active.top + (active.height() - cropHeight) / 2
                builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + cropWidth, top + cropHeight))
            }
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Exception) {
            runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this, "Zoom unavailable: ${e.localizedMessage ?: "unknown error"}", Toast.LENGTH_SHORT).show() }
        }
    }

    /** Maps a viewfinder tap into the current sensor crop and triggers one-shot autofocus. */
    private fun focusAtPreviewPoint(viewX: Float, viewY: Float) {
        val camera = cameraDevice ?: run { Toast.makeText(this, "Camera is not ready yet.", Toast.LENGTH_SHORT).show(); return }
        val session = captureSession ?: run { Toast.makeText(this, "Camera session is not ready.", Toast.LENGTH_SHORT).show(); return }
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        val id = activeCameraId ?: return
        try {
            val chars = cameraManager.getCameraCharacteristics(id)
            val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
            val maxAfRegions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
            val supportedAf = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            val tapAfMode = when {
                supportedAf.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                supportedAf.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO) -> CameraCharacteristics.CONTROL_AF_MODE_AUTO
                supportedAf.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                else -> CameraCharacteristics.CONTROL_AF_MODE_OFF
            }
            if (tapAfMode == CameraCharacteristics.CONTROL_AF_MODE_OFF) {
                Toast.makeText(this, "This camera does not expose autofocus.", Toast.LENGTH_SHORT).show()
                return
            }
            val vw = preview.width.toFloat().coerceAtLeast(1f)
            val vh = preview.height.toFloat().coerceAtLeast(1f)
            var nx = (viewX / vw).coerceIn(0f, 1f)
            var ny = (viewY / vh).coerceIn(0f, 1f)
            // The TextureView uses center-crop. Remove the invisible cropped margins before
            // converting the touch to sensor coordinates; otherwise taps focus a different
            // part of the scene, especially on the front camera and at non-1x zoom.
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val displayRotationForCrop = when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val relativeForCrop = (sensorOrientation - displayRotationForCrop + 360) % 360
            val activeW = active.width().toFloat()
            val activeH = active.height().toFloat()
            val rotatedSensorAspect = if (relativeForCrop == 90 || relativeForCrop == 270) activeH / activeW else activeW / activeH
            val viewAspect = vw / vh
            if (viewAspect > rotatedSensorAspect) {
                val visibleHeight = (rotatedSensorAspect / viewAspect).coerceIn(0f, 1f)
                ny = ((ny - (1f - visibleHeight) / 2f) / visibleHeight).coerceIn(0f, 1f)
            } else if (viewAspect < rotatedSensorAspect) {
                val visibleWidth = (viewAspect / rotatedSensorAspect).coerceIn(0f, 1f)
                nx = ((nx - (1f - visibleWidth) / 2f) / visibleWidth).coerceIn(0f, 1f)
            }
            // Front preview is mirrored for the user; un-mirror after removing center-crop.
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) nx = 1f - nx
            val displayRotation = when (windowManager.defaultDisplay.rotation) {
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
            val relativeRotation = (sensorOrientation - displayRotation + 360) % 360
            val mapped = when (relativeRotation) {
                90 -> Pair(ny, 1f - nx)
                180 -> Pair(1f - nx, 1f - ny)
                270 -> Pair(1f - ny, nx)
                else -> Pair(nx, ny)
            }
            nx = mapped.first.coerceIn(0f, 1f)
            ny = mapped.second.coerceIn(0f, 1f)
            val crop = builder.get(CaptureRequest.SCALER_CROP_REGION) ?: active
            val cx = crop.left + (nx * crop.width()).toInt()
            val cy = crop.top + (ny * crop.height()).toInt()
            val halfW = (crop.width() * 0.08f).toInt().coerceAtLeast(2)
            val halfH = (crop.height() * 0.08f).toInt().coerceAtLeast(2)
            val rect = Rect(
                (cx - halfW).coerceAtLeast(crop.left), (cy - halfH).coerceAtLeast(crop.top),
                (cx + halfW).coerceAtMost(crop.right), (cy + halfH).coerceAtMost(crop.bottom)
            )
            if (maxAfRegions > 0) {
                val region = android.hardware.camera2.params.MeteringRectangle(rect, android.hardware.camera2.params.MeteringRectangle.METERING_WEIGHT_MAX)
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
            }
            // Disable scene-mode override for the tap request; some HALs ignore AF triggers in scene mode.
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
            builder.set(CaptureRequest.CONTROL_AF_MODE, tapAfMode)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, handler)
            val triggerBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface ?: return)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_SCENE_MODE, CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
                set(CaptureRequest.CONTROL_AF_MODE, tapAfMode)
                if (maxAfRegions > 0) {
                    set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(android.hardware.camera2.params.MeteringRectangle(rect, android.hardware.camera2.params.MeteringRectangle.METERING_WEIGHT_MAX)))
                }
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                builder.get(CaptureRequest.SCALER_CROP_REGION)?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
                builder.get(CaptureRequest.CONTROL_AE_MODE)?.let { set(CaptureRequest.CONTROL_AE_MODE, it) }
                builder.get(CaptureRequest.CONTROL_AWB_MODE)?.let { set(CaptureRequest.CONTROL_AWB_MODE, it) }
            }.build()
            session.capture(triggerBuilder, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                    val afState = result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_STATE)
                    Log.i("X200TCameraLab", "TAP_TO_FOCUS_RESULT state=$afState mode=$tapAfMode regions=$maxAfRegions")
                    try {
                        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                        session.setRepeatingRequest(builder.build(), null, handler)
                    } catch (e: Exception) { Log.w("X200TCameraLab", "AF reset failed", e) }
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                    Log.e("X200TCameraLab", "TAP_TO_FOCUS_CAPTURE_FAILED reason=${failure.reason} wasImageCaptured=${failure.wasImageCaptured()}")
                }
            }, handler)
            if (::hudOverlay.isInitialized) {
                hudOverlay.focusPointX = viewX
                hudOverlay.focusPointY = viewY
                hudOverlay.focusPointTime = System.currentTimeMillis()
                hudOverlay.invalidate()
            }
            Log.i("X200TCameraLab", "TAP_TO_FOCUS x=${viewX.toInt()} y=${viewY.toInt()} sensor=$rect afMode=$tapAfMode regions=$maxAfRegions")
        } catch (e: Exception) {
            Log.e("X200TCameraLab", "Tap-to-focus failed", e)
            Toast.makeText(this, "Tap-to-focus failed: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyQuickControls(exposure: Int? = null, focus: Int? = null, whiteBalance: Int? = null) {
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        try {
            if (exposure != null && exposureMax >= exposureMin) {
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure.coerceIn(exposureMin, exposureMax))
            }
            if (focus != null) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, focus)
            }
            if (whiteBalance != null) {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, whiteBalance)
                if (whiteBalance != CaptureRequest.CONTROL_AWB_MODE_AUTO) builder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
            }
            builder.set(CaptureRequest.CONTROL_AE_LOCK, aeLockCheck.isChecked && aeLockSupported && !manualSensorEnabled)
            val currentWb = builder.get(CaptureRequest.CONTROL_AWB_MODE) ?: CaptureRequest.CONTROL_AWB_MODE_AUTO
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, awbLockCheck.isChecked && awbLockSupported && currentWb == CaptureRequest.CONTROL_AWB_MODE_AUTO)
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Exception) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Control unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyManualFocusDistance() {
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        if (minimumFocusDistanceDiopters <= 0f) return
        try {
            // Manual focus is expressed in diopters: 0 is infinity, larger values focus closer.
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraCharacteristics.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE,
                selectedFocusDistanceDiopters.coerceIn(0f, minimumFocusDistanceDiopters))
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Exception) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Manual focus unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyLockControls() {
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        try {
            // Camera2 only allows AE lock when AE is enabled, and AWB lock when AWB is automatic.
            val aeEnabled = !manualSensorEnabled
            val awbAuto = (builder.get(CaptureRequest.CONTROL_AWB_MODE)
                ?: CaptureRequest.CONTROL_AWB_MODE_AUTO) == CaptureRequest.CONTROL_AWB_MODE_AUTO
            builder.set(CaptureRequest.CONTROL_AE_LOCK, aeLockCheck.isChecked && aeLockSupported && aeEnabled)
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, awbLockCheck.isChecked && awbLockSupported && awbAuto)
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Exception) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Exposure/white-balance lock unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyManualSensorControls() {
        val builder = previewRequestBuilder ?: return
        val handler = backgroundHandler ?: return
        if (!manualSensorSupported) return
        try {
            if (manualSensorEnabled) {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, selectedIso.coerceIn(isoMin, isoMax))
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, selectedExposureTimeNs.coerceIn(exposureTimeMinNs, exposureTimeMaxNs))
                builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(CaptureRequest.CONTROL_AE_LOCK, aeLockCheck.isChecked && aeLockSupported)
            }
            val currentWb = builder.get(CaptureRequest.CONTROL_AWB_MODE) ?: CaptureRequest.CONTROL_AWB_MODE_AUTO
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, awbLockCheck.isChecked && awbLockSupported && currentWb == CaptureRequest.CONTROL_AWB_MODE_AUTO)
            captureSession?.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Exception) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Manual sensor control unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatExposureTime(ns: Long): String {
        val seconds = ns / 1_000_000_000.0
        return if (seconds >= 1.0) String.format(Locale.US, "%.1f s", seconds)
        else "1/${(1.0 / seconds).toInt().coerceAtLeast(1)} s"
    }

    private fun focusName(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_AF_MODE_OFF -> "Focus: Off"
        CameraCharacteristics.CONTROL_AF_MODE_AUTO -> "Focus: Auto"
        CameraCharacteristics.CONTROL_AF_MODE_MACRO -> "Focus: Macro"
        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Focus: Video"
        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Focus: Continuous"
        CameraCharacteristics.CONTROL_AF_MODE_EDOF -> "Focus: EDOF"
        else -> "Focus: $mode"
    }

    private fun whiteBalanceName(mode: Int): String = when (mode) {
        CameraCharacteristics.CONTROL_AWB_MODE_AUTO -> "WB: Auto"
        CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT -> "WB: Incandescent"
        CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT -> "WB: Fluorescent"
        CameraCharacteristics.CONTROL_AWB_MODE_WARM_FLUORESCENT -> "WB: Warm fluorescent"
        CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT -> "WB: Daylight"
        CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT -> "WB: Cloudy"
        CameraCharacteristics.CONTROL_AWB_MODE_TWILIGHT -> "WB: Twilight"
        CameraCharacteristics.CONTROL_AWB_MODE_SHADE -> "WB: Shade"
        else -> "WB: $mode"
    }

    private fun loadCameras() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequestCode)
            return
        }
        try {
            cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) {
                report.text = getString(R.string.no_cameras_reported)
                return
            }
            cameraPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                cameraIds.map { id ->
                    val c = cameraManager.getCameraCharacteristics(id)
                    val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
                        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
                        else -> "UNKNOWN"
                    }
                    "Camera $id Â· $facing"
                })
            activeCameraId = cameraIds.first()
            configurePhotoModes(activeCameraId!!)
            zoomRatio = 1.0f
            if (::zoomSeek.isInitialized) zoomSeek.progress = 0
            renderReport(activeCameraId!!)
            if (preview.isAvailable) openCamera(activeCameraId!!)
        } catch (e: CameraAccessException) {
            report.text = getString(R.string.camera_scan_failed, e.reason, e.message ?: getString(R.string.no_additional_details))
        } catch (_: SecurityException) {
            report.text = getString(R.string.camera_permission_required)
        }
    }

    private fun renderReport(id: String) {
        try {
            val c = cameraManager.getCameraCharacteristics(id)
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }
            val level = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
            val evRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val evStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
            exposureMin = evRange?.lower ?: 0
            exposureMax = evRange?.upper ?: 0
            val availableFocus = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf(CameraCharacteristics.CONTROL_AF_MODE_OFF)
            val focusPriority = listOf(
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                CameraCharacteristics.CONTROL_AF_MODE_AUTO,
                CameraCharacteristics.CONTROL_AF_MODE_MACRO,
                CameraCharacteristics.CONTROL_AF_MODE_EDOF,
                CameraCharacteristics.CONTROL_AF_MODE_OFF
            )
            focusModes = availableFocus.distinct().sortedWith(
                compareBy { mode -> focusPriority.indexOf(mode).let { if (it < 0) Int.MAX_VALUE else it } }
            ).toIntArray()
            val availableWb = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf(CameraCharacteristics.CONTROL_AWB_MODE_AUTO)
            whiteBalanceModes = availableWb
            runOnUiThread {
                manualSensorCheck.isChecked = false
                manualSensorCheck.isEnabled = manualSensorSupported
                isoSeek.isEnabled = false
                shutterSeek.isEnabled = false
                isoSeek.progress = if (isoMax > isoMin) ((selectedIso - isoMin) * 100 / (isoMax - isoMin)).coerceIn(0, 100) else 0
                shutterSeek.progress = if (exposureTimeMaxNs > exposureTimeMinNs) {
                    ((kotlin.math.ln(selectedExposureTimeNs.toDouble()) - kotlin.math.ln(exposureTimeMinNs.toDouble())) * 100.0 /
                            (kotlin.math.ln(exposureTimeMaxNs.toDouble()) - kotlin.math.ln(exposureTimeMinNs.toDouble())).coerceAtLeast(0.0001)).toInt().coerceIn(0, 100)
                } else 0
                isoLabel.text = "ISO $selectedIso"
                shutterLabel.text = "Shutter ${formatExposureTime(selectedExposureTimeNs)}"
                exposureSeek.isEnabled = exposureMax > exposureMin
                exposureSeek.progress = if (exposureMax > exposureMin) ((0 - exposureMin) * 100 / (exposureMax - exposureMin)).coerceIn(0, 100) else 50
                focusPicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, focusModes.map { focusName(it) })
                focusPicker.setSelection(0, false)
                whiteBalancePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, whiteBalanceModes.map { whiteBalanceName(it) })
                focusDistanceSeek.isEnabled = minimumFocusDistanceDiopters > 0f
                focusDistanceSeek.progress = if (minimumFocusDistanceDiopters > 0f) {
                    (selectedFocusDistanceDiopters / minimumFocusDistanceDiopters * 100f).toInt().coerceIn(0, 100)
                } else 0
                focusDistanceLabel.text = if (selectedFocusDistanceDiopters <= 0f) "FOCUS ∞" else String.format(Locale.US, "FOCUS %.2f D", selectedFocusDistanceDiopters)
                aeLockCheck.isEnabled = aeLockSupported
                awbLockCheck.isEnabled = awbLockSupported
                aeLockCheck.isChecked = false
                awbLockCheck.isChecked = false
            }
            val capabilityNames = caps.map { capabilityName(it) }.filterNotNull()
            val sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val exposureRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            val isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureTimeRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            manualSensorSupported = caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) &&
                    isoRange != null && exposureTimeRange != null
            minimumFocusDistanceDiopters = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            selectedFocusDistanceDiopters = selectedFocusDistanceDiopters.coerceIn(0f, minimumFocusDistanceDiopters)
            aeLockSupported = c.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
            awbLockSupported = c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true
            if (isoRange != null) {
                isoMin = isoRange.lower
                isoMax = isoRange.upper
                selectedIso = selectedIso.coerceIn(isoMin, isoMax)
            }
            if (exposureTimeRange != null) {
                exposureTimeMinNs = exposureTimeRange.lower.coerceAtLeast(1L)
                exposureTimeMaxNs = exposureTimeRange.upper.coerceAtLeast(exposureTimeMinNs)
                selectedExposureTimeNs = selectedExposureTimeNs.coerceIn(exposureTimeMinNs, exposureTimeMaxNs)
            }
            manualSensorEnabled = false
            val focusRange = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            val focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val fps = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val stabilization = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            val videoStab = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            val dynamicProfiles = if (Build.VERSION.SDK_INT >= 33) {
                c.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)?.supportedProfiles?.map { "0x${it.toString(16)}" } ?: emptyList()
            } else emptyList()

            val out = StringBuilder()
            out.append("CAMERA ID: $id\nFACING: $facing\nHARDWARE LEVEL: ${hardwareLevel(level)}\n\n")
            out.append("CAPABILITIES\n")
            out.append(if (capabilityNames.isEmpty()) "  Not reported\n" else capabilityNames.joinToString("\n") { "  â€¢ $it" } + "\n")
            out.append("\nSENSOR / LENS\n")
            out.append("  Pixel array: ${pixelArray?.width ?: "?"} Ã— ${pixelArray?.height ?: "?"}\n")
            out.append("  Physical size: ${sensor?.width?.let { String.format(Locale.US, "%.2f", it) } ?: "?"} Ã— ${sensor?.height?.let { String.format(Locale.US, "%.2f", it) } ?: "?"} mm\n")
            out.append("  Focal lengths: ${focalLengths?.joinToString { "${it}mm" } ?: "Not reported"}\n")
            out.append("  Minimum focus distance: ${focusRange?.let { "$it diopters" } ?: "Not reported"}\n")
            out.append("  Exposure range: ${exposureRange?.let { "${it.lower}â€“${it.upper} ns" } ?: "Not reported"}\n")
            out.append("  ISO range: ${isoRange?.let { "${it.lower}â€“${it.upper}" } ?: "Not reported"}\n")
            out.append("  AE target FPS: ${fps?.joinToString { "${it.lower}â€“${it.upper}" } ?: "Not reported"}\n")
            out.append("  Exposure compensation: ${evRange?.let { "${it.lower}..${it.upper} steps, step=${evStep ?: "?"}" } ?: "Not reported"}\n")
            out.append("  AF modes: ${focusModes.joinToString { focusName(it) }}\n")
            out.append("  AWB modes: ${whiteBalanceModes.joinToString { whiteBalanceName(it) }}\n")
            out.append("  Optical stabilization modes: ${stabilization.joinToString()}\n")
            out.append("  Video stabilization modes: ${videoStab.joinToString()}\n")
            if (dynamicProfiles.isNotEmpty()) out.append("  Dynamic-range profile IDs: ${dynamicProfiles.joinToString()}\n")
            out.append("\nOUTPUT SIZES\n")
            out.append("  JPEG: ${sizes(map?.getOutputSizes(ImageFormat.JPEG))}\n")
            out.append("  RAW_SENSOR: ${sizes(map?.getOutputSizes(ImageFormat.RAW_SENSOR))}\n")
            out.append("  YUV_420_888: ${sizes(map?.getOutputSizes(ImageFormat.YUV_420_888))}\n")
            out.append("  PRIVATE preview: ${sizes(map?.getOutputSizes(SurfaceTexture::class.java))}\n")
            out.append("\nNOTE\nThese are Android-reported characteristics for this camera ID. Supported combinations and sustained recording performance still need runtime testing.\n")
            report.text = out.toString()
        } catch (e: Exception) {
            report.text = getString(R.string.camera_characteristics_failed, id, e.javaClass.simpleName, e.message ?: getString(R.string.no_additional_details))
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(id: String) {
        if (!hasCameraPermission() || !preview.isAvailable) {
            if (!hasCameraPermission()) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), permissionRequestCode)
            }
            return
        }

        closeCamera()
        startBackgroundThread()
        val generation = cameraGeneration
        try {
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    // A delayed open callback must not resurrect a camera we already switched away from.
                    if (generation != cameraGeneration || activeCameraId != id || !preview.isAvailable) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    createPreviewSession(camera, generation)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    try { camera.close() } catch (_: Exception) {}
                    if (cameraDevice === camera) {
                        captureSession?.let { try { it.close() } catch (_: Exception) {} }
                        captureSession = null
                        cameraDevice = null
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    try { camera.close() } catch (_: Exception) {}
                    if (cameraDevice === camera) {
                        captureSession?.let { try { it.close() } catch (_: Exception) {} }
                        captureSession = null
                        cameraDevice = null
                    }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this@MainActivity, "Camera error: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            report.append("\n\nPreview error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun createPreviewSession(camera: CameraDevice, generation: Long) {
        val texture = preview.surfaceTexture ?: return
        val size = try {
            choosePreviewSize(camera.id)
        } catch (e: Exception) {
            report.append("\nCould not read preview sizes: ${e.message}")
            return
        }

        texture.setDefaultBufferSize(size.width, size.height)
        preview.post { configurePreviewTransform(size.width, size.height) }
        val surface = Surface(texture)
        val jpegSizes = try {
            cameraManager.getCameraCharacteristics(camera.id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        } catch (_: Exception) { emptyList() }
        val jpegSize = chooseJpegSize(jpegSizes)
        val reader = try { ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2) }
        catch (e: Exception) {
            surface.release()
            report.append("\nPhoto output unavailable: ${e.message}")
            return
        }
        reader.setOnImageAvailableListener({ source ->
            val image = try { source.acquireNextImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            val bytes = try {
                val buffer = image.planes[0].buffer
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            } catch (_: Exception) { null } finally { image.close() }
            if (bytes != null) {
                // Apply the selected creative color look to the full-resolution JPEG, then crop to Xpan.
                // Camera2 noise reduction remains enabled independently of the color look.
                val colorBytes = applyColorLookToJpeg(bytes, selectedColorLook)
                val finalBytes = cropJpegToAspect(colorBytes, 65f / 24f)
                savePhoto(finalBytes)
            }
        }, backgroundHandler)
        try {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                val supportedAfModes = cameraManager.getCameraCharacteristics(camera.id)
                    .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                val defaultAf = when {
                    supportedAfModes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    supportedAfModes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO) -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    supportedAfModes.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO) -> CameraCharacteristics.CONTROL_AF_MODE_AUTO
                    else -> CameraCharacteristics.CONTROL_AF_MODE_OFF
                }
                set(CaptureRequest.CONTROL_AF_MODE, defaultAf)
                set(CaptureRequest.CONTROL_SCENE_MODE, CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
                val previewNrModes = cameraManager.getCameraCharacteristics(camera.id)
                    .get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: intArrayOf()
                val previewNrMode = if (previewNrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY else if (previewNrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST)) CaptureRequest.NOISE_REDUCTION_MODE_FAST else CaptureRequest.NOISE_REDUCTION_MODE_OFF
                if (previewNrModes.contains(previewNrMode)) set(CaptureRequest.NOISE_REDUCTION_MODE, previewNrMode)
            }
            camera.createCaptureSession(listOf(surface, reader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // Camera2 callbacks can arrive after closeCamera() has run. Ignore stale sessions.
                    if (generation != cameraGeneration ||
                        cameraDevice !== camera ||
                        activeCameraId != camera.id ||
                        !preview.isAvailable
                    ) {
                        try { session.close() } catch (_: Exception) {}
                        try { surface.release() } catch (_: Exception) {}
                        try { reader.close() } catch (_: Exception) {}
                        return
                    }

                    captureSession = session
                    previewRequestBuilder = request
                    runOnUiThread { applyPreviewColorLook() }
                    previewSurface = surface
                    imageReader = reader
                    runOnUiThread { captureButton.isEnabled = true; captureButton.background = shutterBackground(true) }
                    try {
                        // Reset to the selected camera's native full field of view. Do not
                        // carry a crop from the rear camera into the front camera (or vice versa).
                        zoomRatio = 1.0f
                        if (::zoomSeek.isInitialized) zoomSeek.progress = 0
                        if (::zoomLabel.isInitialized) zoomLabel.text = "1.0×"
                        val activeArray = cameraManager.getCameraCharacteristics(camera.id)
                            .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        if (activeArray != null) request.set(CaptureRequest.SCALER_CROP_REGION, activeArray)
                        session.setRepeatingRequest(request.build(), object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: android.hardware.camera2.TotalCaptureResult
                            ) {
                                result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)?.let { value: Int -> lastPreviewIso = value }
                                result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)?.let { value: Long -> lastPreviewExposureNs = value }
                            }
                        }, backgroundHandler)
                        applyZoom()
                    } catch (e: Exception) {
                        // Includes CameraAccessException and IllegalStateException if the device
                        // closes between the validity check and setRepeatingRequest().
                        if (captureSession === session) captureSession = null
                        try { session.close() } catch (_: Exception) {}
                        try { surface.release() } catch (_: Exception) {}
                        try { reader.close() } catch (_: Exception) {}
                        runOnUiThread { captureButton.isEnabled = false; captureButton.background = shutterBackground(false) }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                report.append("\nPreview request failed: ${e.javaClass.simpleName}: ${e.message}")
                            }
                        }
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    try { session.close() } catch (_: Exception) {}
                    try { surface.release() } catch (_: Exception) {}
                    try { reader.close() } catch (_: Exception) {}
                    runOnUiThread { captureButton.isEnabled = false; captureButton.background = shutterBackground(false) }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            report.append("\nPreview session configuration failed for camera ${camera.id}.")
                        }
                    }
                }

                override fun onClosed(session: CameraCaptureSession) {
                    if (captureSession === session) captureSession = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            try { surface.release() } catch (_: Exception) {}
            try { reader.close() } catch (_: Exception) {}
            report.append("\nCould not create preview: ${e.javaClass.simpleName}: ${e.message}")
        }
    }


    private fun showPhotoResolutionPicker() {
        showMegapixelChoices()
    }

    private fun capturePhoto() {
        val camera = cameraDevice
        val session = captureSession
        val reader = imageReader
        val handler = backgroundHandler
        if (camera == null || session == null || reader == null || handler == null) {
            Toast.makeText(this, "Camera is not ready yet.", Toast.LENGTH_SHORT).show()
            return
        }
        captureButton.isEnabled = false

        // Continuous-picture AF is the default. For a quick shutter press, trigger a fresh focus
        // scan and capture as soon as AF locks; a short timeout prevents missing a fleeting shot.
        val previewBuilder = previewRequestBuilder
        val afMode = previewBuilder?.get(CaptureRequest.CONTROL_AF_MODE)
            ?: CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        val afAvailable = try {
            val modes = cameraManager.getCameraCharacteristics(camera.id)
                .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            modes.any { it != CameraCharacteristics.CONTROL_AF_MODE_OFF }
        } catch (_: Exception) { false }

        val captureOnce = java.util.concurrent.atomic.AtomicBoolean(false)
        val doCapture = {
            if (captureOnce.compareAndSet(false, true)) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(camera.id)
                    val stillRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        // Never let a scene preset override preview zoom/focus on the still request.
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_SCENE_MODE, CameraCharacteristics.CONTROL_SCENE_MODE_DISABLED)
                        val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        val supportedAeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
                        val requestedAeMode = when (captureFlashMode) {
                            1 -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            2 -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                            else -> CaptureRequest.CONTROL_AE_MODE_ON
                        }
                        val aeFlashMode = if (flashAvailable && supportedAeModes.contains(requestedAeMode)) {
                            requestedAeMode
                        } else {
                            CaptureRequest.CONTROL_AE_MODE_ON
                        }
                        set(CaptureRequest.CONTROL_AE_MODE, aeFlashMode)
                        // In low light, request the device's own Night scene pipeline when advertised.
                        // This gives the HAL a chance to use longer exposure and/or multi-frame fusion.
                        // Never combine Night scene mode with an explicit flash request.
                        val supportedScenes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
                        val looksLikeLowLight = lastPreviewIso >= 800 || lastPreviewExposureNs >= 15_000_000L
                        if (looksLikeLowLight && captureFlashMode == 0 && !torchEnabled &&
                            supportedScenes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT)) {
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                            set(CaptureRequest.CONTROL_SCENE_MODE, CameraCharacteristics.CONTROL_SCENE_MODE_NIGHT)
                        }
                        // Forced flash fallback for HALs that expose a flash LED but omit
                        // AE_MODE_ON_ALWAYS_FLASH from their advertised AE modes.
                        if (flashAvailable && captureFlashMode == 2 && aeFlashMode == CaptureRequest.CONTROL_AE_MODE_ON) {
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                        } else {
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }
                        set(CaptureRequest.JPEG_QUALITY, (if (highQualityJpegEnabled) 100 else 95).toByte())
                        set(CaptureRequest.CONTROL_AF_MODE, afMode)
                        val supportedAwb = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
                        set(CaptureRequest.CONTROL_AWB_MODE, if (supportedAwb.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)) CaptureRequest.CONTROL_AWB_MODE_AUTO else (previewBuilder?.get(CaptureRequest.CONTROL_AWB_MODE) ?: CaptureRequest.CONTROL_AWB_MODE_AUTO))
                        // COLOR_CORRECTION_MODE_FAST is a standard request mode. There is no
                        // COLOR_CORRECTION_AVAILABLE_MODES CameraCharacteristics key.
                        set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
                        val optStab = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
                        set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, if (stabilizationEnabled && optStab.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON else CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
                        previewBuilder?.get(CaptureRequest.CONTROL_AE_LOCK)?.let { set(CaptureRequest.CONTROL_AE_LOCK, it) }
                        previewBuilder?.get(CaptureRequest.CONTROL_AWB_LOCK)?.let { set(CaptureRequest.CONTROL_AWB_LOCK, it) }
                        previewBuilder?.get(CaptureRequest.LENS_FOCUS_DISTANCE)?.let { set(CaptureRequest.LENS_FOCUS_DISTANCE, it) }
                        previewBuilder?.get(CaptureRequest.SENSOR_SENSITIVITY)?.let { set(CaptureRequest.SENSOR_SENSITIVITY, it) }
                        previewBuilder?.get(CaptureRequest.SENSOR_EXPOSURE_TIME)?.let { set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            previewBuilder?.get(CaptureRequest.CONTROL_ZOOM_RATIO)?.let {
                                set(CaptureRequest.CONTROL_ZOOM_RATIO, it)
                            }
                        } else {
                            previewBuilder?.get(CaptureRequest.SCALER_CROP_REGION)?.let {
                                set(CaptureRequest.SCALER_CROP_REGION, it)
                            }
                        }
                        previewBuilder?.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION)?.let { set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, it) }
                        // Match the still image orientation to the actual sensor/display orientation.
                        // X PAN then crops to 65:24 in landscape or 24:65 in portrait.
                        set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation(camera.id))

                        // Ask Camera2 for its highest available single-frame processing modes.
                        // This can improve edge/noise handling, but cannot recreate proprietary
                        // multi-frame/HDR processing used by the manufacturer's camera app.
                        if (chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) == true) {
                            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                        }
                        val availableNrModes = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: intArrayOf()
                        val requestedNrMode = if (availableNrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                        } else if (availableNrModes.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST)) {
                            CaptureRequest.NOISE_REDUCTION_MODE_FAST
                        } else {
                            CaptureRequest.NOISE_REDUCTION_MODE_OFF
                        }
                        if (availableNrModes.contains(requestedNrMode)) set(CaptureRequest.NOISE_REDUCTION_MODE, requestedNrMode)
                        if (chars.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)?.contains(CaptureRequest.SHADING_MODE_HIGH_QUALITY) == true) {
                            set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)
                        }
                        if (chars.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)?.contains(CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY) == true) {
                            set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
                        }
                        if (chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)?.contains(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY) == true) {
                            set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
                        }
                        if (chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)?.contains(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY) == true) {
                            set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                        }
                        // JPEG output is already display-referred/sRGB-like. Applying an extra
                        // gamma 2.2 curve to this encoded JPEG would double-gamma and damage tones.
                        // Let the camera HAL's advertised high-quality tonemap handle linear sensor data.
                    }
                    session.capture(stillRequest.build(), object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                            logCaptureDiagnostics(camera.id, chars, request, result)
                            runOnUiThread { if (!isFinishing && !isDestroyed) captureButton.isEnabled = captureSession != null }
                        }
                        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                            runOnUiThread {
                                captureButton.isEnabled = captureSession != null
                                Toast.makeText(this@MainActivity, "Photo capture failed (${failure.reason}).", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, handler)
                } catch (e: Exception) {
                    runOnUiThread {
                        captureButton.isEnabled = captureSession != null
                        Toast.makeText(this, "Capture failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        if (!afAvailable || previewBuilder == null) {
            doCapture()
            return
        }
        try {
            val surface = previewSurface
            if (surface == null) {
                doCapture()
                return
            }
            val focusRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, afMode)
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                previewBuilder.get(CaptureRequest.SCALER_CROP_REGION)?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
            }.build()
            session.capture(focusRequest, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                    val state = result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_STATE)
                    // Capture as soon as AF reports a settled result. Some vendors report PASSIVE_UNFOCUSED
                    // or NOT_FOCUSED_LOCKED for distant/low-contrast subjects, so don't stall the shutter.
                    if (state == android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        state == android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                        state == android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                        state == android.hardware.camera2.CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED) {
                        doCapture()
                    }
                }
            }, handler)
            handler.postDelayed({ doCapture() }, 650L)
        } catch (_: Exception) {
            doCapture()
        }
    }

    /** Emits actual per-shot Camera2 metadata to Logcat for diagnosing noise and sensor selection. */
    private fun logCaptureDiagnostics(
        cameraId: String,
        chars: CameraCharacteristics,
        request: CaptureRequest,
        result: android.hardware.camera2.TotalCaptureResult
    ) {
        try {
            val iso = result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)
            val exposureNs = result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)
            val afState = result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_STATE)
            val aeState = result.get(android.hardware.camera2.CaptureResult.CONTROL_AE_STATE)
            val nrMode = result.get(android.hardware.camera2.CaptureResult.NOISE_REDUCTION_MODE)
            val edgeMode = result.get(android.hardware.camera2.CaptureResult.EDGE_MODE)
            val hotPixelMode = result.get(android.hardware.camera2.CaptureResult.HOT_PIXEL_MODE)
            val shadingMode = result.get(android.hardware.camera2.CaptureResult.SHADING_MODE)
            val crop = result.get(android.hardware.camera2.CaptureResult.SCALER_CROP_REGION)
            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) chars.physicalCameraIds.joinToString() else "API<28"
            val physicalResultIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { result.physicalCameraResults.keys.joinToString().ifBlank { "none reported for this shot" } }
                catch (_: Exception) { "unavailable" }
            } else "API<28"
            val extensions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { cameraManager.getCameraExtensionCharacteristics(cameraId).supportedExtensions.joinToString() }
                catch (_: Exception) { "query failed" }
            } else "API<31"
            Log.i("X200TCameraLab", "CAPTURE_DIAGNOSTICS camera=$cameraId iso=$iso exposureNs=$exposureNs afState=$afState aeState=$aeState nrMode=$nrMode edgeMode=$edgeMode hotPixelMode=$hotPixelMode shadingMode=$shadingMode crop=$crop requestedNR=${request.get(CaptureRequest.NOISE_REDUCTION_MODE)} physicalIds=[$physicalIds] physicalResults=[$physicalResultIds] extensions=[$extensions]")
        } catch (e: Exception) {
            Log.w("X200TCameraLab", "Could not collect capture diagnostics", e)
        }
    }

    private fun jpegOrientation(cameraId: String): Int {
        return try {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val displayRotation = windowManager.defaultDisplay.rotation
            val deviceDegrees = when (displayRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                (sensorOrientation + deviceDegrees) % 360
            } else {
                (sensorOrientation - deviceDegrees + 360) % 360
            }
        } catch (_: Exception) { 0 }
    }


    private fun showColorLookPicker() {
        val labels = ColorLook.values().map { "${it.title}\n${it.subtitle}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("PHANTOM COLOR LOOKS")
            .setSingleChoiceItems(labels, ColorLook.values().indexOf(selectedColorLook)) { dialog, which ->
                selectedColorLook = ColorLook.values()[which]
                getSharedPreferences("phantom_xpan_settings", MODE_PRIVATE).edit()
                    .putString("color_look", selectedColorLook.name).apply()
                updateColorLookButton()
                applyPreviewColorLook()
                dialog.dismiss()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun updateColorLookButton() {
        if (!::filterButton.isInitialized) return
        filterButton.text = when (selectedColorLook) {
            ColorLook.ORIGINAL -> "◐"
            ColorLook.HASSELBLAD -> "H*"
            ColorLook.ZEISS -> "Z*"
            ColorLook.VINTAGE -> "VNT"
            ColorLook.RETRO -> "70s"
            ColorLook.CLASSIC -> "CC"
            ColorLook.CINEMA -> "CIN"
            ColorLook.WARM -> "SUN"
            ColorLook.MONO -> "B&W"
        }
        filterButton.contentDescription = "Color filter: ${selectedColorLook.title}"
        filterButton.background = roundedBackground(
            if (selectedColorLook == ColorLook.ORIGINAL) Color.rgb(22, 27, 33) else Color.rgb(75, 54, 34),
            if (selectedColorLook == ColorLook.ORIGINAL) Color.rgb(58, 66, 75) else Color.rgb(229, 174, 103), 10
        )
    }

    /** Creative color matrices are applied to preview and final JPEG; no third-party LUTs are used. */
    private fun colorMatrixFor(look: ColorLook): ColorMatrix? {
        val values = when (look) {
            ColorLook.ORIGINAL -> return null
            // ZEISS-inspired creative look only; not an official ZEISS calibration or LUT.
            ColorLook.ZEISS -> floatArrayOf(
                1.02f, 0.00f, -0.01f, 0f, 1f,
                0.00f, 1.00f, 0.00f, 0f, 0f,
                -0.01f, 0.01f, 1.02f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f)
            // Subtle Hasselblad-inspired natural look, not an official Hasselblad profile.
            ColorLook.HASSELBLAD -> floatArrayOf(
                1.04f, 0.02f, 0.00f, 0f, 3f,
                0.01f, 1.00f, 0.01f, 0f, 2f,
                0.00f, 0.02f, 0.96f, 0f, 1f,
                0f, 0f, 0f, 1f, 0f)
            ColorLook.VINTAGE -> floatArrayOf(
                0.88f, 0.08f, 0.02f, 0f, 20f,
                0.04f, 0.86f, 0.04f, 0f, 15f,
                0.02f, 0.06f, 0.76f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f)
            ColorLook.RETRO -> floatArrayOf(
                0.92f, 0.07f, 0.01f, 0f, 18f,
                0.04f, 0.88f, 0.02f, 0f, 10f,
                0.00f, 0.04f, 0.78f, 0f, 2f,
                0f, 0f, 0f, 1f, 0f)
            ColorLook.CLASSIC -> floatArrayOf(
                1.12f, 0.00f, 0.00f, 0f, -8f,
                0.00f, 1.02f, 0.00f, 0f, -4f,
                0.00f, 0.02f, 0.90f, 0f, -2f,
                0f, 0f, 0f, 1f, 0f)
            ColorLook.CINEMA -> floatArrayOf(
                1.04f, 0.02f, 0.00f, 0f, 8f,
                0.00f, 0.98f, 0.02f, 0f, 2f,
                0.02f, 0.04f, 1.04f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f)
            ColorLook.WARM -> floatArrayOf(
                1.08f, 0.02f, 0.00f, 0f, 12f,
                0.02f, 1.00f, 0.00f, 0f, 5f,
                0.00f, 0.00f, 0.88f, 0f, -2f,
                0f, 0f, 0f, 1f, 0f)
            ColorLook.MONO -> floatArrayOf(
                0.213f, 0.715f, 0.072f, 0f, 0f,
                0.213f, 0.715f, 0.072f, 0f, 0f,
                0.213f, 0.715f, 0.072f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)
        }
        val matrix = ColorMatrix(values)
        if (look == ColorLook.VINTAGE || look == ColorLook.RETRO || look == ColorLook.HASSELBLAD) {
            val saturation = when (look) {
                ColorLook.HASSELBLAD -> 0.88f
                ColorLook.VINTAGE -> 0.72f
                else -> 0.80f
            }
            val sat = ColorMatrix().apply { setSaturation(saturation) }
            sat.postConcat(matrix)
            return sat
        }
        if (look == ColorLook.CLASSIC) {
            val sat = ColorMatrix().apply { setSaturation(0.78f) }
            sat.postConcat(matrix)
            return sat
        }
        return matrix
    }

    private fun applyPreviewColorLook() {
        if (!::preview.isInitialized) return
        val matrix = colorMatrixFor(selectedColorLook)
        val paint = Paint().apply { if (matrix != null) colorFilter = ColorMatrixColorFilter(matrix) }
        preview.setLayerType(View.LAYER_TYPE_HARDWARE, if (matrix == null) null else paint)
        preview.invalidate()
    }

    private fun applyColorLookToJpeg(bytes: ByteArray, look: ColorLook): ByteArray {
        val matrix = colorMatrixFor(look) ?: return bytes
        var source: Bitmap? = null
        var filtered: Bitmap? = null
        return try {
            source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            filtered = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(filtered)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
            canvas.drawBitmap(source, 0f, 0f, paint)
            ByteArrayOutputStream().use { out ->
                filtered.compress(Bitmap.CompressFormat.JPEG, if (highQualityJpegEnabled) 100 else 95, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("PhantomXpan", "Color look failed; saving camera JPEG unchanged", e)
            bytes
        } finally {
            if (filtered != null && !filtered.isRecycled) filtered.recycle()
            if (source != null && !source.isRecycled) source.recycle()
        }
    }

    /** Center-crops X Pan output to the requested 65:24 panorama ratio. */
    private fun cropJpegToAspect(bytes: ByteArray, targetAspect: Float): ByteArray {
        var source: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            // Choose integer dimensions in the exact 65:24 ratio. Using a shared integer
            // multiplier prevents rounding drift in the saved JPEG dimensions.
            val multiplier = minOf(source.width / 65, source.height / 24)
            if (multiplier < 1) return bytes
            val cropWidth = 65 * multiplier
            val cropHeight = 24 * multiplier
            val left = (source.width - cropWidth) / 2
            val top = (source.height - cropHeight) / 2
            cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
            return ByteArrayOutputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, if (highQualityJpegEnabled) 100 else 95, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("PhantomXpan", "X Pan crop failed; keeping original JPEG", e)
            return bytes
        } finally {
            if (cropped != null && cropped !== source && !cropped.isRecycled) cropped.recycle()
            if (source != null && !source.isRecycled) source.recycle()
        }
    }

    private fun savePhoto(jpegBytes: ByteArray) {
        var output: OutputStream? = null
        var uri: android.net.Uri? = null
        try {
            val filename = "X200T_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, saveRelativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create gallery entry")
            output = contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not open image output")
            output.write(jpegBytes)
            output.flush()
            output.close()
            output = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Publish only after the file descriptor is closed; some gallery apps otherwise
                // attempt to decode a still-incomplete JPEG and crash back to the camera.
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                contentResolver.update(uri, done, null, null)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, "Saved ${bounds.outWidth}×${bounds.outHeight} · 65:24 · $saveRelativePath", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            if (uri != null) try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this, "Could not save photo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            try { output?.close() } catch (_: Exception) {}
        }
    }

    private fun choosePreviewSize(id: String): Size {
        val map = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
        if (sizes.isEmpty()) return Size(640, 480)
        val targetAspect = if (::preview.isInitialized && preview.width > 0 && preview.height > 0) {
            maxOf(preview.width, preview.height).toFloat() / minOf(preview.width, preview.height).toFloat()
        } else 16f / 9f
        val candidates = sizes.filter { it.width <= 2560 && it.height <= 2560 && it.width.toLong() * it.height <= 4_000_000L }
            .ifEmpty { sizes.filter { it.width.toLong() * it.height <= 4_000_000L }.ifEmpty { sizes } }
        return candidates.minWithOrNull(compareBy<Size> {
            kotlin.math.abs((maxOf(it.width, it.height).toFloat() / minOf(it.width, it.height).toFloat()) - targetAspect)
        }.thenByDescending { it.width.toLong() * it.height }) ?: sizes.first()
    }

    private fun closeCamera() {
        // Invalidate callbacks before releasing resources; Camera2 may deliver callbacks later.
        cameraGeneration++
        val session = captureSession
        captureSession = null
        previewRequestBuilder = null
        previewSurface?.let { try { it.release() } catch (_: Exception) {} }
        previewSurface = null
        imageReader?.let { try { it.close() } catch (_: Exception) {} }
        imageReader = null
        torchEnabled = false
        runOnUiThread {
            if (::captureButton.isInitialized) {
                captureButton.isEnabled = false
                captureButton.background = shutterBackground(false)
            }
            if (::flashButton.isInitialized) {
                flashButton.text = when (captureFlashMode) { 1 -> "⚡A"; 2 -> "⚡ON"; else -> "⚡" }
                flashButton.background = roundedBackground(
                    if (captureFlashMode == 0) Color.rgb(22, 27, 33) else Color.rgb(78, 62, 30),
                    if (captureFlashMode == 0) Color.rgb(58, 66, 75) else Color.rgb(242, 190, 80), 10
                )
            }
        }
        try { session?.close() } catch (_: Exception) {}
        val camera = cameraDevice
        cameraDevice = null
        try { camera?.close() } catch (_: Exception) {}
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("Camera2Background").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try { backgroundThread?.join() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        backgroundThread = null
        backgroundHandler = null
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (hasCameraPermission()) {
            startBackgroundThread()
            // When returning from the system photo picker, TextureView often remains available
            // and does not fire onSurfaceTextureAvailable again. Reopen the camera explicitly.
            if (::preview.isInitialized && preview.isAvailable && cameraDevice == null) {
                activeCameraId?.let { openCamera(it) }
            }
        }
    }

    override fun onPause() {
        // Camera callbacks may arrive while the activity is losing focus; invalidate and close
        // the session before shutting down its handler thread.
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadCameras()
        else report.text = getString(R.string.camera_permission_denied)
    }

    private fun hasCameraPermission() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun sizes(values: Array<Size>?): String {
        if (values.isNullOrEmpty()) return "Not reported"
        val sorted = values.sortedByDescending { it.width.toLong() * it.height }
        val shown = sorted.take(maxReportedSizes).joinToString { "${it.width}Ã—${it.height}" }
        return if (sorted.size > maxReportedSizes) "$shown â€¦ (+${sorted.size - maxReportedSizes} more)" else shown
    }

    private fun hardwareLevel(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "Unknown ($level)"
    }

    private fun capabilityName(value: Int): String? = when (value) {
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
        else -> "CAPABILITY_$value"
    }
}