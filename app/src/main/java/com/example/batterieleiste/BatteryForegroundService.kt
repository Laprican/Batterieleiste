package com.example.batterieleiste

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import com.example.batterieleiste.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs

class BatteryForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var settingsRepository: SettingsRepository

    private val _batteryLevel = MutableStateFlow(0)
    private val _batteryStats = MutableStateFlow(BatteryStats())
    private val _isCharging = MutableStateFlow(false)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private var currentThickness = 4
    private var currentTransparency = 1.0f
    private var selectedGradientIndex = 0
    private var currentPosition = 0 // 0: Top, 1: Bottom
    private var useDynamicColors = false
    private var useLevelColors = false
    private var useNotchHandling = false
    private var hideOnLockscreen = false
    private var alertFullBattery = false
    private var alertLowBattery = false
    
    // Ring Mode
    private var useRingMode = false
    private var currentRingX = 50
    private var currentRingY = 20
    private var currentRingSize = 50

    private var isScreenOn = true
    private var isLocked = false


    private val ringRect = RectF()
    private val ringMatrix = Matrix()
    
    private var screenWidth = 0
    private var density = 1.0f

    data class BatteryStats(
        val level: Int = 0,
        val health: String = "Unbekannt",
        val status: String = "Unbekannt",
        val temperature: Float = 0f,
        val voltage: Int = 0,
        val current: Int = 0,
        val technology: String = "Unbekannt",
        val pluggedSource: String = "Akku",
        val remainingTime: String = "Berechne...",
        val cycleCount: Int = -1
    )

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> updateBatteryStateFromIntent(intent)
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    checkLockState()
                    overlayView?.postInvalidate()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    updateOverlayVisibility()
                }
                Intent.ACTION_USER_PRESENT -> {
                    isLocked = false
                    updateOverlayVisibility()
                }
            }
        }
    }

    private fun checkLockState() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        isLocked = km.isKeyguardLocked
        updateOverlayVisibility()
    }

    private fun updateOverlayVisibility() {
        val shouldShow = isScreenOn && !(hideOnLockscreen && isLocked)
        overlayView?.visibility = if (shouldShow) View.VISIBLE else if (!isScreenOn) View.GONE else View.INVISIBLE
        if (shouldShow) overlayView?.postInvalidate()
    }

    private fun updateBatteryStateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val tempInt = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val voltInt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unbekannt"
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        
        var cycles = -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
        }

        // Maximale Empfindlichkeit: Wenn ÜBERHAUPT Strom fließt (plugged > 0), dann laden wir
        val isChargingNow = plugged > 0 || statusInt == BatteryManager.BATTERY_STATUS_CHARGING || statusInt == BatteryManager.BATTERY_STATUS_FULL
        
        // Loggen für Debugging
        android.util.Log.d("BatteryService", "Battery Change: Status=$statusInt, Plugged=$plugged, isChargingNow=$isChargingNow")
        
        _isCharging.value = isChargingNow

        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        if (level != -1 && scale != -1) {
            _batteryLevel.value = (level * 100 / scale.toFloat()).toInt()
        }

        val normalizedCurrent = if (abs(currentNow) > 5000) currentNow / 1000 else currentNow

        // Check for alerts
        if (alertFullBattery && _batteryLevel.value == 100 && !lastAlertWasFull) {
            sendAlert("Akku geladen", "Dein Akku hat 100% erreicht.")
            lastAlertWasFull = true
        } else if (_batteryLevel.value < 100) {
            lastAlertWasFull = false
        }

        if (alertLowBattery && _batteryLevel.value <= 15 && !lastAlertWasLow) {
            sendAlert("Akku schwach", "Dein Akkustand ist bei ${_batteryLevel.value}%.")
            lastAlertWasLow = true
        } else if (_batteryLevel.value > 20) {
            lastAlertWasLow = false
        }

        _batteryStats.value = BatteryStats(
            level = _batteryLevel.value,
            health = getHealthString(healthInt),
            status = getStatusString(statusInt),
            temperature = tempInt / 10.0f,
            voltage = voltInt,
            current = normalizedCurrent,
            technology = tech,
            pluggedSource = getPluggedString(plugged),
            remainingTime = calculateRemainingTime(isChargingNow, _batteryLevel.value, currentNow, capacity),
            cycleCount = cycles
        )
    }

    private var lastAlertWasFull = false
    private var lastAlertWasLow = false

    private fun sendAlert(title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2, notification)
    }

    private fun calculateRemainingTime(isCharging: Boolean, level: Int, currentNow: Int, capacity: Int): String {
        val absRawCurrent = abs(currentNow).toFloat()
        if (absRawCurrent < 10f) return "Berechne..."
        
        val currentMA = if (absRawCurrent > 5000) absRawCurrent / 1000f else absRawCurrent
        if (currentMA < 10f) return "Stabile Last"

        val hours = if (isCharging) {
            val remainingPercent = 100 - level
            if (remainingPercent <= 0) return "Voll"
            val toChargeMAh = (remainingPercent * 4000f) / 100f
            toChargeMAh / currentMA
        } else {
            val absRawCapacity = abs(capacity).toFloat()
            val remainingMAh = if (absRawCapacity > 100000) absRawCapacity / 1000f 
                              else (_batteryLevel.value * 4000f) / 100f
            remainingMAh / currentMA
        }

        val totalMinutes = (hours * 60).toInt()
        if (totalMinutes <= 0) return "Voll"
        if (totalMinutes > 48 * 60) return "Stabile Last"

        val h = totalMinutes / 60
        val m = totalMinutes % 60
        
        return if (isCharging) {
            if (h > 0) "$h h $m m bis 100%" else "$m m bis 100%"
        } else {
            if (h > 0) "$h h $m m verbleibend" else "$m m verbleibend"
        }
    }

    private fun getPluggedString(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Netzteil"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Kabellos"
        else -> "Akku"
    }

    private fun getHealthString(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Heiß"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Defekt"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Überspannung"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fehler"
        BatteryManager.BATTERY_HEALTH_COLD -> "Kalt"
        else -> "Unbekannt"
    }

    private fun getStatusString(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Lädt"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Entlädt"
        BatteryManager.BATTERY_STATUS_FULL -> "Voll"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Lädt nicht"
        else -> "Unbekannt"
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        updateScreenMetrics()

        createNotificationChannel()
        createAlertChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val stickyIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        
        // Initialen Status sofort aus dem klebrigen Intent laden
        stickyIntent?.let { updateBatteryStateFromIntent(it) }

        observeSettingsAndBattery()
    }

    private fun updateScreenMetrics() {
        density = resources.displayMetrics.density
        screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.currentWindowMetrics?.bounds?.width() ?: resources.displayMetrics.widthPixels
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(dm)
            dm.widthPixels
        }
    }

    private fun observeSettingsAndBattery() {
        combine(
            _batteryLevel,
            settingsRepository.barThickness,
            settingsRepository.barTransparency,
            settingsRepository.selectedGradientIndex,
            _isCharging,
            settingsRepository.barPosition,
            settingsRepository.useDynamicColors,
            settingsRepository.useLevelColors,
            settingsRepository.useNotchHandling,
            settingsRepository.useRingMode,
            settingsRepository.ringX,
            settingsRepository.ringY,
            settingsRepository.ringSize,
            settingsRepository.hideOnLockscreen,
            settingsRepository.alertFullBattery,
            settingsRepository.alertLowBattery
        ) { args: Array<Any> ->
            OverlayState(
                level = args[0] as Int,
                thickness = args[1] as Int,
                transparency = args[2] as Float,
                gradientIndex = args[3] as Int,
                isCharging = args[4] as Boolean,
                position = args[5] as Int,
                useDynamicColors = args[6] as Boolean,
                useLevelColors = args[7] as Boolean,
                useNotchHandling = args[8] as Boolean,
                useRingMode = args[9] as Boolean,
                ringX = args[10] as Int,
                ringY = args[11] as Int,
                ringSize = args[12] as Int,
                hideOnLockscreen = args[13] as Boolean,
                alertFullBattery = args[14] as Boolean,
                alertLowBattery = args[15] as Boolean
            )
        }.distinctUntilChanged()
        .onEach { state ->
            val needsRecreate = currentPosition != state.position || useRingMode != state.useRingMode
            
            currentThickness = state.thickness
            currentTransparency = state.transparency
            selectedGradientIndex = state.gradientIndex
            currentPosition = state.position
            useDynamicColors = state.useDynamicColors
            useLevelColors = state.useLevelColors
            useNotchHandling = state.useNotchHandling
            useRingMode = state.useRingMode
            currentRingX = state.ringX
            currentRingY = state.ringY
            currentRingSize = state.ringSize
            hideOnLockscreen = state.hideOnLockscreen
            alertFullBattery = state.alertFullBattery
            alertLowBattery = state.alertLowBattery
            
            updateOverlayVisibility()
            
            if (needsRecreate && overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    android.util.Log.e("BatteryService", "Error removing view", e)
                }
                overlayView = null
            }

            updateOverlay(state.level, state.isCharging)
        }.launchIn(serviceScope)
    }

    private data class OverlayState(
        val level: Int,
        val thickness: Int,
        val transparency: Float,
        val gradientIndex: Int,
        val isCharging: Boolean,
        val position: Int,
        val useDynamicColors: Boolean,
        val useLevelColors: Boolean,
        val useNotchHandling: Boolean,
        val useRingMode: Boolean,
        val ringX: Int,
        val ringY: Int,
        val ringSize: Int,
        val hideOnLockscreen: Boolean,
        val alertFullBattery: Boolean,
        val alertLowBattery: Boolean
    )

    private fun updateOverlay(level: Int, isCharging: Boolean) {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView == null) setupOverlay(level)

        overlayView?.let { view ->
            // Update context before redraw
            updateOverlayContent(level, isCharging)

            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@let
            
            // Only update layout params if they actually changed or it's a fresh setup
            var paramsChanged = false

            if (useRingMode) {
                val sizePx = (currentRingSize * density).toInt()
                val targetX = (currentRingX * screenWidth / 100.0).toInt() - (sizePx / 2)
                val targetY = (currentRingY * density).toInt()
                
                if (params.width != sizePx || params.height != sizePx || params.x != targetX || params.y != targetY || params.gravity != (Gravity.TOP or Gravity.START)) {
                    params.width = sizePx
                    params.height = sizePx
                    params.x = targetX
                    params.y = targetY
                    params.gravity = Gravity.TOP or Gravity.START
                    paramsChanged = true
                }
            } else {
                val thicknessPx = (currentThickness * density).toInt()
                // IMPORTANT: Fixed full width for bar when charging or needing animation
                // This ensures the custom draw code in BarDrawable can handle the level vs animation
                val targetWidth = screenWidth
                val targetGravity = when(currentPosition) {
                    1 -> Gravity.BOTTOM or Gravity.START
                    else -> Gravity.TOP or Gravity.START
                }
                
                if (params.width != targetWidth || params.height != thicknessPx || params.gravity != targetGravity || params.x != 0 || params.y != 0) {
                    params.width = targetWidth
                    params.height = thicknessPx
                    params.x = 0
                    params.y = 0
                    params.gravity = targetGravity
                    paramsChanged = true
                }
            }

            if (paramsChanged) {
                try {
                    windowManager?.updateViewLayout(view, params)
                } catch (e: Exception) {}
            }
        }
    }

    private fun updateOverlayContent(level: Int, isCharging: Boolean) {
        val view = overlayView ?: return
        val colors = when {
            useLevelColors -> getDynamicColors(level)
            useDynamicColors -> getGradientColors(selectedGradientIndex)
            else -> getSolidColors(selectedGradientIndex)
        }

        val background = view.background
        if (useRingMode) {
            val drawable = if (background is RingDrawable) background else RingDrawable().also { view.background = it }
            drawable.update(level, colors, isCharging, currentThickness, currentTransparency, density)
        } else {
            val drawable = if (background is BarDrawable) background else BarDrawable().also { view.background = it }
            drawable.update(colors, isCharging, currentTransparency, level, useDynamicColors)
        }
        view.postInvalidate()
    }

    private inner class RingDrawable : Drawable() {
        private var colors = intArrayOf()
        private var isCharging = false
        private var thickness = 0
        private var transparency = 1f
        private var density = 1f
        private var level = 0
        
        private var cachedSweepShader: SweepGradient? = null

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        fun update(level: Int, colors: IntArray, isCharging: Boolean, thickness: Int, transparency: Float, density: Float) {
            if (!this.colors.contentEquals(colors)) {
                cachedSweepShader = null
            }
            this.level = level
            this.colors = colors
            this.isCharging = isCharging
            this.thickness = thickness
            this.transparency = transparency
            this.density = density
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val stroke = thickness * density
            paint.strokeWidth = stroke
            ringRect.set(stroke/2, stroke/2, bounds.width() - stroke/2, bounds.height() - stroke/2)
            
            // Background ring - set to 5% alpha as requested
            paint.shader = null
            paint.color = Color.WHITE
            paint.alpha = (transparency * 0.05f * 255).toInt()
            canvas.drawOval(ringRect, paint)

            // Battery Level Arc
            paint.alpha = (transparency * 255).toInt()
            val sweep = (level / 100.0f) * 360f
            
            if (cachedSweepShader == null) {
                cachedSweepShader = SweepGradient(ringRect.centerX(), ringRect.centerY(), colors, null)
            }
            ringMatrix.setRotate(-90f, ringRect.centerX(), ringRect.centerY())
            cachedSweepShader?.setLocalMatrix(ringMatrix)
            paint.shader = cachedSweepShader

            canvas.drawArc(ringRect, -90f, sweep, false, paint)

            if (isCharging && isScreenOn) {
                val time = SystemClock.uptimeMillis()
                val animValue = (time % 1500) / 1500f
                
                paint.shader = null
                paint.color = Color.WHITE
                // Ein kleiner Lichtpunkt, der im Ring kreist
                val animAngle = 360f * animValue
                paint.alpha = (transparency * 0.8f * 255).toInt()
                canvas.drawArc(ringRect, -90f + animAngle, 20f, false, paint)
                
                invalidateSelf()
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private inner class BarDrawable : Drawable() {
        private var colors = intArrayOf()
        private var isCharging = false
        private var transparency = 1f
        private var level = 0
        private var useGradients = false
        
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val animPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shaderMatrix = Matrix()
        private var cachedBarShader: LinearGradient? = null
        private var cachedScannerShader: LinearGradient? = null
        private var lastBarWidth = -1f

        fun update(colors: IntArray, isCharging: Boolean, transparency: Float, level: Int, useGradients: Boolean) {
            if (!this.colors.contentEquals(colors) || this.useGradients != useGradients) {
                cachedBarShader = null
                cachedScannerShader = null
            }
            this.colors = colors
            this.isCharging = isCharging
            this.transparency = transparency
            this.level = level
            this.useGradients = useGradients
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val totalWidth = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            if (totalWidth <= 0 || height <= 0) return

            val barWidth = totalWidth * (level / 100.0f)

            // Background Track - set to 5% alpha white
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = (transparency * 0.05f * 255).toInt()
            canvas.drawRect(0f, 0f, totalWidth, height, paint)

            // Draw Filled Bar
            if (useGradients && colors.size >= 2) {
                if (cachedBarShader == null || barWidth != lastBarWidth) {
                    cachedBarShader = LinearGradient(0f, 0f, barWidth, 0f, colors, null, Shader.TileMode.CLAMP)
                    lastBarWidth = barWidth
                }
                paint.shader = cachedBarShader
            } else {
                paint.shader = null
                paint.color = colors[0]
            }
            paint.alpha = (transparency * 255).toInt()
            canvas.drawRect(0f, 0f, barWidth, height, paint)

            // Charging Animation (Scanner / Sliding Light)
            if (isCharging && level < 100 && barWidth > 0 && isScreenOn) {
                val time = SystemClock.uptimeMillis()
                val animValue = (time % 2000) / 2000f
                
                val scannerWidth = totalWidth * 0.2f 
                
                if (cachedScannerShader == null) {
                    cachedScannerShader = LinearGradient(
                        0f, 0f, scannerWidth, 0f,
                        intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.TRANSPARENT),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                
                val lightPos = (totalWidth + scannerWidth) * animValue - scannerWidth
                shaderMatrix.setTranslate(lightPos, 0f)
                cachedScannerShader?.setLocalMatrix(shaderMatrix)
                
                animPaint.style = Paint.Style.FILL
                animPaint.shader = cachedScannerShader
                animPaint.alpha = (transparency * 0.9f * 255).toInt()
                
                canvas.save()
                canvas.clipRect(0f, 0f, barWidth, height)
                canvas.drawRect(lightPos, 0f, lightPos + scannerWidth, height, animPaint)
                canvas.restore()
                
                invalidateSelf()
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun getDynamicColors(level: Int): IntArray {
        return when {
            level <= 15 -> intArrayOf(Color.RED, "#8B0000".toColorInt())
            level <= 30 -> intArrayOf(Color.YELLOW, "#DAA520".toColorInt())
            else -> intArrayOf(Color.GREEN, "#006400".toColorInt())
        }
    }

    private fun getGradientColors(index: Int): IntArray = when (index) {
        0 -> intArrayOf("#FF512F".toColorInt(), "#DD2476".toColorInt())
        1 -> intArrayOf("#00B4DB".toColorInt(), "#0083B0".toColorInt())
        2 -> intArrayOf("#11998e".toColorInt(), "#38ef7d".toColorInt())
        3 -> intArrayOf("#f8ff00".toColorInt(), "#3ad59f".toColorInt())
        4 -> intArrayOf(Color.RED, Color.GREEN)
        5 -> intArrayOf("#8E2DE2".toColorInt(), "#4A00E0".toColorInt())
        6 -> intArrayOf("#f953c6".toColorInt(), "#b91d73".toColorInt())
        7 -> intArrayOf(Color.BLACK, Color.WHITE)
        else -> intArrayOf("#11998e".toColorInt(), "#38ef7d".toColorInt())
    }

    private fun getSolidColors(index: Int): IntArray = when (index) {
        0 -> intArrayOf(Color.GREEN, Color.GREEN)
        1 -> intArrayOf("#8BC34A".toColorInt(), "#8BC34A".toColorInt())
        2 -> intArrayOf("#CDDC39".toColorInt(), "#CDDC39".toColorInt())
        3 -> intArrayOf(Color.YELLOW, Color.YELLOW)
        4 -> intArrayOf("#FFC107".toColorInt(), "#FFC107".toColorInt())
        5 -> intArrayOf("#FF9800".toColorInt(), "#FF9800".toColorInt())
        6 -> intArrayOf("#FF5722".toColorInt(), "#FF5722".toColorInt())
        7 -> intArrayOf(Color.RED, Color.RED)
        8 -> intArrayOf("#E91E63".toColorInt(), "#E91E63".toColorInt())
        9 -> intArrayOf("#9C27B0".toColorInt(), "#9C27B0".toColorInt())
        10 -> intArrayOf(Color.BLUE, Color.BLUE)
        11 -> intArrayOf(Color.CYAN, Color.CYAN)
        else -> intArrayOf(Color.GREEN, Color.GREEN)
    }

    private fun setupOverlay(level: Int) {
        if (!Settings.canDrawOverlays(this)) return
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY

        val w: Int
        val h: Int
        val gx: Int
        val gy: Int
        val grav: Int

        if (useRingMode) {
            val sizePx = (currentRingSize * density).toInt()
            w = sizePx
            h = sizePx
            gx = (currentRingX * screenWidth / 100.0).toInt() - (sizePx / 2)
            gy = (currentRingY * density).toInt()
            grav = Gravity.TOP or Gravity.START
        } else {
            w = screenWidth
            h = (currentThickness * density).toInt()
            gx = 0
            gy = 0
            grav = when(currentPosition) {
                1 -> Gravity.BOTTOM or Gravity.START
                else -> Gravity.TOP or Gravity.START
            }
        }

        val params = WindowManager.LayoutParams(
            w, h, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = grav
            x = gx; y = gy
            
            if (useNotchHandling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        overlayView = View(this)
        try { windowManager?.addView(overlayView, params) } catch (e: Exception) { overlayView = null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Batterieleiste Hintergrunddienst", NotificationManager.IMPORTANCE_MIN)
            channel.description = "Notwendig für die Anzeige der Akkuleiste"
            channel.setShowBadge(false)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID_ALERTS, "Akku Alarme", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Benachrichtigungen bei vollem oder schwachem Akku"
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            Intent(this, MainActivity::class.java), 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Batterieleiste ist aktiv")
            .setContentText("Tippen für Einstellungen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): BatteryForegroundService = this@BatteryForegroundService }
    fun getBatteryStats(): StateFlow<BatteryStats> = _batteryStats.asStateFlow()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        updateOverlay(_batteryLevel.value, _isCharging.value)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {}
        serviceScope.cancel()
        
        overlayView?.let { 
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("BatteryService", "Error removing view in onDestroy", e)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "BatteryServiceChannel_v2"
        private const val CHANNEL_ID_ALERTS = "BatteryAlertChannel"
        private const val NOTIFICATION_ID = 1
    }
}
