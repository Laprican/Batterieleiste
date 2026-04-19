package com.example.batterieleiste

import android.animation.ValueAnimator
import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat
import com.example.batterieleiste.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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
    private var chargingAnimationIndex = 0
    private var currentPosition = 0 // 0: Top, 1: Bottom
    private var useDynamicColors = false
    private var useNotchHandling = false
    
    // Ring Mode
    private var useRingMode = false
    private var currentRingX = 50
    private var currentRingY = 20
    private var currentRingSize = 50

    private var chargingAnimator: ValueAnimator? = null
    private var animationValue = 0f

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
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
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

                val isChargingNow = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                        statusInt == BatteryManager.BATTERY_STATUS_FULL
                _isCharging.value = isChargingNow

                val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // in uAh

                if (level != -1 && scale != -1) {
                    _batteryLevel.value = (level * 100 / scale.toFloat()).toInt()
                }

                val normalizedCurrent = if (Math.abs(currentNow) > 5000) currentNow / 1000 else currentNow

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
        }
    }

    private fun calculateRemainingTime(isCharging: Boolean, level: Int, currentNow: Int, capacity: Int): String {
        val absRawCurrent = Math.abs(currentNow).toFloat()
        if (absRawCurrent < 10f) return "Berechne..."
        
        val currentMA = if (absRawCurrent > 5000) absRawCurrent / 1000f else absRawCurrent
        if (currentMA < 10f) return "Stabile Last"

        val hours = if (isCharging) {
            val remainingPercent = 100 - level
            if (remainingPercent <= 0) return "Voll"
            val toChargeMAh = (remainingPercent * 4000f) / 100f
            toChargeMAh / currentMA
        } else {
            val absRawCapacity = Math.abs(capacity).toFloat()
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

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        observeSettingsAndBattery()
    }

    private fun observeSettingsAndBattery() {
        kotlinx.coroutines.flow.combine(
            _batteryLevel,
            settingsRepository.barThickness,
            settingsRepository.barTransparency,
            settingsRepository.selectedGradientIndex,
            settingsRepository.chargingAnimationIndex,
            _isCharging,
            settingsRepository.barPosition,
            settingsRepository.useDynamicColors,
            settingsRepository.useNotchHandling,
            settingsRepository.useRingMode,
            settingsRepository.ringX,
            settingsRepository.ringY,
            settingsRepository.ringSize
        ) { args ->
            OverlayState(
                level = args[0] as Int,
                thickness = args[1] as Int,
                transparency = args[2] as Float,
                gradientIndex = args[3] as Int,
                animIndex = args[4] as Int,
                isCharging = args[5] as Boolean,
                position = args[6] as Int,
                useDynamicColors = args[7] as Boolean,
                useNotchHandling = args[8] as Boolean,
                useRingMode = args[9] as Boolean,
                ringX = args[10] as Int,
                ringY = args[11] as Int,
                ringSize = args[12] as Int
            )
        }.onEach { state ->
            val needsRecreate = currentPosition != state.position || useRingMode != state.useRingMode
            
            currentThickness = state.thickness
            currentTransparency = state.transparency
            selectedGradientIndex = state.gradientIndex
            chargingAnimationIndex = state.animIndex
            currentPosition = state.position
            useDynamicColors = state.useDynamicColors
            useNotchHandling = state.useNotchHandling
            useRingMode = state.useRingMode
            currentRingX = state.ringX
            currentRingY = state.ringY
            currentRingSize = state.ringSize
            
            if (needsRecreate && overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }

            if (state.isCharging) startChargingAnimation() else stopChargingAnimation()
            updateOverlay(state.level)
        }.launchIn(serviceScope)
    }

    private data class OverlayState(
        val level: Int,
        val thickness: Int,
        val transparency: Float,
        val gradientIndex: Int,
        val animIndex: Int,
        val isCharging: Boolean,
        val position: Int,
        val useDynamicColors: Boolean,
        val useNotchHandling: Boolean,
        val useRingMode: Boolean,
        val ringX: Int,
        val ringY: Int,
        val ringSize: Int
    )

    private fun startChargingAnimation() {
        if (chargingAnimator != null) return
        chargingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (chargingAnimationIndex == 0) 1500 else 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = if (chargingAnimationIndex == 0) ValueAnimator.REVERSE else ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                animationValue = animator.animatedValue as Float
                updateOverlay(_batteryLevel.value)
            }
            start()
        }
    }

    private fun stopChargingAnimation() {
        chargingAnimator?.cancel()
        chargingAnimator = null
        animationValue = 0f
    }

    private fun updateOverlay(level: Int) {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView == null) setupOverlay()

        overlayView?.let { view ->
            val screenWidth = getScreenWidth()
            val density = resources.displayMetrics.density
            val params = view.layoutParams as WindowManager.LayoutParams

            val gradientColors = if (useDynamicColors) {
                getDynamicColors(level)
            } else {
                getGradientColors(selectedGradientIndex)
            }

            if (useRingMode) {
                val sizePx = (currentRingSize * density).toInt()
                params.width = sizePx
                params.height = sizePx
                
                // Position X is percentage of screen width, center of the ring
                params.x = (currentRingX * screenWidth / 100.0) .toInt() - (sizePx / 2)
                params.y = (currentRingY * density).toInt()
                params.gravity = Gravity.TOP or Gravity.START

                view.background = object : Drawable() {
                    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = currentThickness * density
                        strokeCap = Paint.Cap.ROUND
                    }

                    override fun draw(canvas: Canvas) {
                        val stroke = paint.strokeWidth
                        val rect = RectF(stroke/2, stroke/2, bounds.width() - stroke/2, bounds.height() - stroke/2)
                        
                        // Background ring (optional, maybe very faint?)
                        paint.color = Color.DKGRAY
                        paint.alpha = (currentTransparency * 50).toInt()
                        canvas.drawOval(rect, paint)

                        // Battery Level Arc
                        paint.alpha = (currentTransparency * 255).toInt()
                        val sweep = (level / 100.0f) * 360f
                        
                        val shader = SweepGradient(rect.centerX(), rect.centerY(), gradientColors, null)
                        val matrix = Matrix()
                        matrix.postRotate(-90f, rect.centerX(), rect.centerY())
                        shader.setLocalMatrix(matrix)
                        paint.shader = shader

                        canvas.drawArc(rect, -90f, sweep, false, paint)
                        
                        // Charging Animation on Ring
                        if (_isCharging.value) {
                             paint.shader = null
                             paint.color = Color.WHITE
                             paint.alpha = (animationValue * 255).toInt()
                             val animSweep = 30f
                             val startAngle = -90f + (animationValue * 360f)
                             canvas.drawArc(rect, startAngle, animSweep, false, paint)
                        }
                    }
                    override fun setAlpha(alpha: Int) {}
                    override fun setColorFilter(colorFilter: ColorFilter?) {}
                    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                }

            } else {
                val thicknessPx = (currentThickness * density).toInt()
                params.width = (screenWidth * (level / 100.0f)).toInt()
                params.height = thicknessPx
                params.gravity = when(currentPosition) {
                    0 -> Gravity.TOP or Gravity.START
                    1 -> Gravity.BOTTOM or Gravity.START
                    else -> Gravity.TOP or Gravity.START
                }
                
                val shapeDrawable = ShapeDrawable(RectShape())
                shapeDrawable.shaderFactory = object : ShapeDrawable.ShaderFactory() {
                    override fun resize(width: Int, height: Int): Shader {
                        val shader = LinearGradient(0f, 0f, width.toFloat(), 0f, gradientColors, null, Shader.TileMode.CLAMP)
                        if (!_isCharging.value) return shader

                        return when (chargingAnimationIndex) {
                            1 -> { // Flow
                                val highlightPos = animationValue * width
                                LinearGradient(highlightPos - 100, 0f, highlightPos + 100, 0f,
                                    intArrayOf(gradientColors[0], Color.WHITE, gradientColors[1]),
                                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                            }
                            2 -> { // Scan
                                val scanPos = if (animationValue < 0.5f) animationValue * 2 else (1f - animationValue) * 2
                                val pos = scanPos * width
                                LinearGradient(pos - 40, 0f, pos + 40, 0f,
                                    intArrayOf(gradientColors[0], Color.WHITE, gradientColors[1]),
                                    floatArrayOf(0.4f, 0.5f, 0.6f), Shader.TileMode.CLAMP)
                            }
                            else -> shader
                        }
                    }
                }
                view.background = shapeDrawable
                view.alpha = if (_isCharging.value && chargingAnimationIndex == 0) 
                    currentTransparency * (0.4f + 0.6f * animationValue) 
                    else currentTransparency
            }

            try { windowManager?.updateViewLayout(view, params) } catch (e: Exception) {}
        }
    }

    private fun getDynamicColors(level: Int): IntArray {
        return when {
            level <= 15 -> intArrayOf(Color.RED, Color.parseColor("#8B0000"))
            level <= 30 -> intArrayOf(Color.YELLOW, Color.parseColor("#DAA520"))
            else -> intArrayOf(Color.GREEN, Color.parseColor("#006400"))
        }
    }

    private fun getGradientColors(index: Int): IntArray = when (index) {
        0 -> intArrayOf(Color.parseColor("#FF512F"), Color.parseColor("#DD2476"))
        1 -> intArrayOf(Color.parseColor("#00B4DB"), Color.parseColor("#0083B0"))
        2 -> intArrayOf(Color.parseColor("#11998e"), Color.parseColor("#38ef7d"))
        3 -> intArrayOf(Color.parseColor("#f8ff00"), Color.parseColor("#3ad59f"))
        4 -> intArrayOf(Color.RED, Color.GREEN)
        5 -> intArrayOf(Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"))
        6 -> intArrayOf(Color.parseColor("#f953c6"), Color.parseColor("#b91d73"))
        7 -> intArrayOf(Color.BLACK, Color.WHITE)
        else -> intArrayOf(Color.GREEN, Color.GREEN)
    }

    private fun getScreenWidth(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager?.currentWindowMetrics?.bounds?.width() ?: 0
    } else {
        val dm = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(dm)
        dm.widthPixels
    }

    private fun setupOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY

        val params = WindowManager.LayoutParams(
            0, 0, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (useRingMode) Gravity.TOP or Gravity.START else when(currentPosition) {
                1 -> Gravity.BOTTOM or Gravity.START
                else -> Gravity.TOP or Gravity.START
            }
            x = 0; y = 0
            
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Batterieleiste ist aktiv")
            .setContentText("Tippen für Einstellungen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): BatteryForegroundService = this@BatteryForegroundService }
    fun getBatteryStats(): StateFlow<BatteryStats> = _batteryStats.asStateFlow()

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        serviceScope.cancel()
        stopChargingAnimation()
        overlayView?.let { windowManager?.removeView(it) }
    }

    companion object {
        private const val CHANNEL_ID = "BatteryServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
