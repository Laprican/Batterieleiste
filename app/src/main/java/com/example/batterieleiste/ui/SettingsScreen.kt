package com.example.batterieleiste.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.batterieleiste.BatteryForegroundService
import com.example.batterieleiste.R
import androidx.core.graphics.toColorInt
import kotlin.math.abs
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: SettingsViewModel, navController: NavController) {
    val context = LocalContext.current
    val batteryStats by viewModel.batteryStats.collectAsStateWithLifecycle()
    val appThemeIndex by viewModel.appThemeIndex.collectAsStateWithLifecycle()
    
    val themeColors = getThemeColors(appThemeIndex)
    var showMenu by remember { mutableStateOf(false) }
    
    // Bind to service for live updates
    DisposableEffect(Unit) {
        var collectionJob: kotlinx.coroutines.Job? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as BatteryForegroundService.LocalBinder
                val batteryService = binder.getService()
                collectionJob = batteryService.getBatteryStats().onEach { stats ->
                    viewModel.updateBatteryStats(stats)
                }.launchIn(CoroutineScope(Dispatchers.Main))
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                collectionJob?.cancel()
                collectionJob = null
            }
        }
        val intent = Intent(context, BatteryForegroundService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { 
            collectionJob?.cancel()
            context.unbindService(connection) 
        }
    }

    Scaffold(
        containerColor = themeColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = themeColors.background,
                    titleContentColor = themeColors.content
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_gear), tint = themeColors.content)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(themeColors.card)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_activation), color = themeColors.content) },
                                leadingIcon = { Icon(Icons.Default.Security, null, tint = themeColors.content) },
                                onClick = { showMenu = false; navController.navigate("activation") }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_bar_style), color = themeColors.content) },
                                leadingIcon = { Icon(Icons.Default.Palette, null, tint = themeColors.content) },
                                onClick = { showMenu = false; navController.navigate("bar_style") }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nav_app_theme), color = themeColors.content) },
                                leadingIcon = { Icon(Icons.Default.Brush, null, tint = themeColors.content) },
                                onClick = { showMenu = false; navController.navigate("app_theme") }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Akkustand Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(160.dp, 80.dp)
                                .clip(RoundedCornerShape(40.dp))
                                .background(themeColors.card),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (batteryStats.status == "Lädt" || batteryStats.status == "Charging") Icons.Default.FlashOn else Icons.Default.BatteryFull,
                                    contentDescription = null,
                                    tint = themeColors.content,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${batteryStats.level}%",
                                    color = themeColors.content,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (batteryStats.remainingTime == "Berechne...") stringResource(R.string.remaining_calculating) else batteryStats.remainingTime,
                            color = themeColors.secondaryText,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }

                BentoGrid(batteryStats, themeColors.card, themeColors.content, themeColors.secondaryText)
                
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Versionsnummer unten rechts
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appVersionName = packageInfo.versionName
            Text(
                text = "v$appVersionName",
                color = themeColors.secondaryText.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()
    val appThemeIndex by viewModel.appThemeIndex.collectAsStateWithLifecycle()
    val hideOnLockscreen by viewModel.hideOnLockscreen.collectAsStateWithLifecycle()
    val alertFullBattery by viewModel.alertFullBattery.collectAsStateWithLifecycle()
    val alertLowBattery by viewModel.alertLowBattery.collectAsStateWithLifecycle()
    
    val themeColors = getThemeColors(appThemeIndex)
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.setServiceEnabled(true)
    }

    val isBatteryOptimized = remember {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Scaffold(
        containerColor = themeColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.background,
                    titleContentColor = themeColors.content,
                    navigationIconContentColor = themeColors.content
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            SettingCard(themeColors.card) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.service_activate), color = themeColors.content, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (isServiceEnabled) stringResource(R.string.service_running) else stringResource(R.string.service_stopped), color = themeColors.secondaryText, fontSize = 12.sp)
                    }
                    Switch(checked = isServiceEnabled, onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!Settings.canDrawOverlays(context)) {
                                showPermissionDialog = true
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                                     context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                showNotificationPermissionDialog = true
                            } else {
                                viewModel.setServiceEnabled(true)
                            }
                        } else {
                            viewModel.setServiceEnabled(false)
                        }
                    })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.alerts_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SettingCard(themeColors.card) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.alert_full_label), color = themeColors.content)
                        Switch(checked = alertFullBattery, onCheckedChange = { viewModel.setAlertFullBattery(it) })
                    }
                    HorizontalDivider(color = themeColors.secondaryText.copy(alpha = 0.2f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.alert_low_label), color = themeColors.content)
                        Switch(checked = alertLowBattery, onCheckedChange = { viewModel.setAlertLowBattery(it) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.auto_hide_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SettingCard(themeColors.card) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hide_lockscreen_label), color = themeColors.content)
                    Switch(checked = hideOnLockscreen, onCheckedChange = { viewModel.setHideOnLockscreen(it) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.permissions_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            StabilityCard(
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                isOk = Settings.canDrawOverlays(context),
                themeColors = themeColors
            ) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StabilityCard(
                title = stringResource(R.string.perm_notif_title),
                description = stringResource(R.string.perm_notif_desc),
                isOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true,
                themeColors = themeColors
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StabilityCard(
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                isOk = isBatteryOptimized,
                themeColors = themeColors
            ) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Berechtigung erforderlich") },
            text = { Text("Um die Akkuleiste anzuzeigen, muss die App über anderen Apps erscheinen dürfen.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                }) { Text("Einstellungen öffnen") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = { Text("Benachrichtigung erforderlich") },
            text = { Text("Android benötigt eine Benachrichtigung, damit der Dienst im Hintergrund stabil laufen kann.") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Erlauben") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppThemeScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val appThemeIndex by viewModel.appThemeIndex.collectAsStateWithLifecycle()
    val themeColors = getThemeColors(appThemeIndex)

    Scaffold(
        containerColor = themeColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_theme_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.background,
                    titleContentColor = themeColors.content,
                    navigationIconContentColor = themeColors.content
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.app_theme_desc), color = themeColors.secondaryText, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            AppThemeSelector(appThemeIndex) { viewModel.setAppThemeIndex(it) }
            
            Spacer(modifier = Modifier.height(32.dp))
            // Preview card for current theme
            SettingCard(themeColors.card) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.preview_label), color = themeColors.content, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Dies ist das aktuelle Farbschema.", color = themeColors.secondaryText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarStyleScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val appThemeIndex by viewModel.appThemeIndex.collectAsStateWithLifecycle()
    val themeColors = getThemeColors(appThemeIndex)
    
    val selectedGradientIndex by viewModel.selectedGradientIndex.collectAsStateWithLifecycle()
    val barThickness by viewModel.barThickness.collectAsStateWithLifecycle()
    val barTransparency by viewModel.barTransparency.collectAsStateWithLifecycle()
    val barPosition by viewModel.barPosition.collectAsStateWithLifecycle()
    val useDynamicColors by viewModel.useDynamicColors.collectAsStateWithLifecycle()
    val useNotchHandling by viewModel.useNotchHandling.collectAsStateWithLifecycle()
    val useRingMode by viewModel.useRingMode.collectAsStateWithLifecycle()
    val ringX by viewModel.ringX.collectAsStateWithLifecycle()
    val ringY by viewModel.ringY.collectAsStateWithLifecycle()
    val ringSize by viewModel.ringSize.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = themeColors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bar_style_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeColors.background,
                    titleContentColor = themeColors.content,
                    navigationIconContentColor = themeColors.content
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Live Preview
            Text(stringResource(R.string.preview_label), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(themeColors.card),
                contentAlignment = Alignment.Center
            ) {
                LiveBarPreview(
                    level = 75,
                    thickness = barThickness,
                    transparency = barTransparency,
                    gradientIndex = selectedGradientIndex,
                    isRing = useRingMode,
                    ringSize = ringSize,
                    useDynamic = useDynamicColors
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.presets_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            BarPresetSelector(themeColors.card, themeColors.content) { viewModel.applyTheme(it) }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.mode_colors_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(themeColors.card) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ring_mode_label), color = themeColors.content)
                        Switch(checked = useRingMode, onCheckedChange = { viewModel.setUseRingMode(it) })
                    }
                    HorizontalDivider(color = themeColors.secondaryText.copy(alpha = 0.2f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dynamic_colors_label), color = themeColors.content)
                        Switch(checked = useDynamicColors, onCheckedChange = { viewModel.setUseDynamicColors(it) })
                    }
                    if (!useRingMode) {
                        HorizontalDivider(color = themeColors.secondaryText.copy(alpha = 0.2f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.notch_handling_label), color = themeColors.content)
                            Switch(checked = useNotchHandling, onCheckedChange = { viewModel.setUseNotchHandling(it) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ColorOptionSelector(selectedGradientIndex, useDynamicColors) { viewModel.setSelectedGradientIndex(it) }
            
            if (!useRingMode) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.position_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                PositionSelector(barPosition, themeColors.card, themeColors.content) { viewModel.setBarPosition(it) }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.metrics_header), color = themeColors.content, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingCard(themeColors.card) {
                Column {
                    if (useRingMode) {
                        SliderSetting("Größe", ringSize, 20..150, "dp") { viewModel.setRingSize(it) }
                        SliderSetting("Position X", ringX, 0..100, "%") { viewModel.setRingX(it) }
                        SliderSetting("Position Y", ringY, 0..100, "dp") { viewModel.setRingY(it) }
                        SliderSetting("Stärke", barThickness, 1..10, "dp") { viewModel.setBarThickness(it) }
                    } else {
                        SliderSetting(stringResource(R.string.thickness_label), barThickness, 1..20, "dp") { viewModel.setBarThickness(it) }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${stringResource(R.string.transparency_label)}: ${(barTransparency * 100).toInt()}%", color = themeColors.content, fontSize = 14.sp)
                    Slider(
                        value = barTransparency,
                        onValueChange = { viewModel.setBarTransparency(it) },
                        valueRange = 0.1f..1.0f
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun LiveBarPreview(level: Int, thickness: Int, transparency: Float, gradientIndex: Int, isRing: Boolean, ringSize: Int, useDynamic: Boolean) {
    val colors = if (useDynamic) getGradientColors(gradientIndex) else getSolidColors(gradientIndex)
    
    if (isRing) {
        val sizePx = ringSize.dp
        Box(
            modifier = Modifier
                .size(sizePx)
                .padding(thickness.dp / 2)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = thickness.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.05f * transparency),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                
                val brush = if (colors.size > 1) {
                    Brush.sweepGradient(colors.map { Color(it) })
                } else {
                    SolidColor(Color(colors[0]))
                }
                
                drawArc(
                    brush = brush,
                    startAngle = -90f,
                    sweepAngle = (level / 100f) * 360f,
                    useCenter = false,
                    alpha = transparency,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(thickness.dp)
                .clip(RoundedCornerShape(thickness.dp / 2))
                .background(Color.White.copy(alpha = 0.05f * transparency))
        ) {
            val brush = if (colors.size > 1) {
                Brush.horizontalGradient(colors.map { Color(it) })
            } else {
                SolidColor(Color(colors[0]))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(level / 100f)
                    .fillMaxHeight()
                    .background(brush, alpha = transparency)
            )
        }
    }
}

// --- Helper Components & Data ---

data class ThemeColors(
    val background: Color,
    val card: Color,
    val content: Color,
    val secondaryText: Color
)

fun getThemeColors(index: Int): ThemeColors = when (index) {
    1 -> ThemeColors(Color(0xFFF5F5F5), Color.White, Color.Black, Color.DarkGray)
    2 -> ThemeColors(Color(0xFF001F3F), Color(0xFF003366), Color.White, Color.LightGray)
    3 -> ThemeColors(Color(0xFF0B2410), Color(0xFF1B3D21), Color.White, Color.Gray)
    else -> ThemeColors(Color(0xFF121212), Color(0xFF212121), Color.White, Color.Gray)
}

@Composable
fun MenuButton(title: String, icon: ImageVector, theme: ThemeColors, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.card)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = theme.content, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = theme.content, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.secondaryText)
        }
    }
}

@Composable
fun SliderSetting(label: String, value: Int, range: IntRange, unit: String, onValueChange: (Int) -> Unit) {
    Column {
        Text("$label: $value$unit", color = Color.Unspecified, fontSize = 14.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
    }
}

@Composable
fun StabilityCard(title: String, description: String, isOk: Boolean, themeColors: ThemeColors, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = themeColors.card)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = themeColors.content, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(description, color = themeColors.secondaryText, fontSize = 11.sp)
            }
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AppThemeSelector(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val themes = listOf(stringResource(R.string.theme_dark), stringResource(R.string.theme_light), stringResource(R.string.theme_ocean), stringResource(R.string.theme_forest))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        themes.forEachIndexed { index, name ->
            Button(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedIndex == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(name, fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarPresetSelector(cardColor: Color, contentColor: Color, onSelect: (Int) -> Unit) {
    val presets = listOf(
        "Modern" to Color(0xFF00C853),
        "Neon" to Color(0xFF00B0FF),
        "Candy" to Color(0xFFFF4081),
        "Cyber" to Color(0xFF8E2DE2),
        "Standart speichern" to Color(0xFF455A64),
        "Standart einstellen" to Color(0xFF78909C)
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3
    ) {
        presets.forEachIndexed { index, (name, color) ->
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(65.dp)
                    .clickable { onSelect(index) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, contentColor.copy(alpha = 0.1f))
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(24.dp, 4.dp).background(color, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = name,
                            color = contentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCard(color: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun PositionSelector(selectedIndex: Int, cardColor: Color, contentColor: Color, onSelect: (Int) -> Unit) {
    val positions = listOf(stringResource(R.string.pos_top), stringResource(R.string.pos_bottom))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        positions.forEachIndexed { index, name ->
            Button(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedIndex == index) contentColor else cardColor,
                    contentColor = if (selectedIndex == index) (if (contentColor == Color.White) Color.Black else Color.White) else contentColor
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (selectedIndex != index) BorderStroke(1.dp, contentColor.copy(alpha = 0.2f)) else null
            ) {
                Text(name, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorOptionSelector(selectedIndex: Int, isGradientMode: Boolean, onSelect: (Int) -> Unit) {
    val gradients = listOf(
        listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
        listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
        listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        listOf(Color(0xFFF8FF00), Color(0xFF3AD59F)),
        listOf(Color.Red, Color.Green),
        listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
        listOf(Color(0xFFF953C6), Color(0xFFB91D73)),
        listOf(Color.Black, Color.White)
    )

    val solidColors = listOf(
        listOf(Color(0xFF4CAF50)), // Green
        listOf(Color(0xFF8BC34A)), // Light Green
        listOf(Color(0xFFCDDC39)), // Lime
        listOf(Color(0xFFFFEB3B)), // Yellow
        listOf(Color(0xFFFFC107)), // Amber
        listOf(Color(0xFFFF9800)), // Orange
        listOf(Color(0xFFFF5722)), // Deep Orange
        listOf(Color(0xFFF44336)), // Red
        listOf(Color(0xFFE91E63)), // Pink
        listOf(Color(0xFF9C27B0)), // Purple
        listOf(Color(0xFF2196F3)), // Blue
        listOf(Color(0xFF00BCD4))  // Cyan
    )

    val options = if (isGradientMode) gradients else solidColors

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEachIndexed { index, colors ->
            val brush = if (colors.size > 1) {
                Brush.horizontalGradient(colors)
            } else {
                Brush.linearGradient(listOf(colors[0], colors[0]))
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .clickable { onSelect(index) }
                    .then(if (selectedIndex == index) Modifier.border(2.dp, Color.Cyan, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) { if (selectedIndex == index) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
fun BentoGrid(stats: BatteryForegroundService.BatteryStats, cardColor: Color, contentColor: Color, secondaryTextColor: Color) {
    val healthIcon = if (stats.health == "Gut" || stats.health == "Good") Icons.Default.Favorite else Icons.Default.FavoriteBorder
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoCard(Modifier.weight(1f), "Gesundheit", stats.health, healthIcon, Color(0xFF4CAF50), cardColor, contentColor, secondaryTextColor)
            BentoCard(Modifier.weight(1f), "Status", stats.status, Icons.Default.BatteryFull, Color(0xFF2196F3), cardColor, contentColor, secondaryTextColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoCard(Modifier.weight(1f), "Temperatur", "${stats.temperature}°C", Icons.Default.Thermostat, Color(0xFFFFA000), cardColor, contentColor, secondaryTextColor)
            BentoCard(Modifier.weight(1f), "Stromquelle", stats.pluggedSource, Icons.Default.Power, Color(0xFFFFEB3B), cardColor, contentColor, secondaryTextColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoCard(Modifier.weight(1f), "Stromstärke", "${stats.current}mA", Icons.Default.Bolt, Color(0xFF00BCD4), cardColor, contentColor, secondaryTextColor)
            BentoCard(Modifier.weight(1f), "Leistung", "${String.format(Locale.US, "%.1f", (abs(stats.current) * (stats.voltage / 1000f)) / 1000f)}W", Icons.Default.ElectricBolt, Color(0xFFFFA000), cardColor, contentColor, secondaryTextColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoCard(Modifier.weight(1f), "Spannung", "${stats.voltage / 1000.0}V", Icons.Default.Power, Color(0xFFFFEB3B), cardColor, contentColor, secondaryTextColor)
            BentoCard(Modifier.weight(1f), "Zyklen", if (stats.cycleCount >= 0) "${stats.cycleCount}" else "---", Icons.Default.Sync, Color(0xFF4CAF50), cardColor, contentColor, secondaryTextColor)
        }
    }
}

@Composable
fun BentoCard(modifier: Modifier, title: String, value: String, icon: ImageVector, iconColor: Color, cardColor: Color, contentColor: Color, secondaryTextColor: Color) {
    Card(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, color = secondaryTextColor, fontSize = 10.sp)
            }
            Text(value, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// Function accessors for color logic (moved here for preview)
fun getGradientColors(index: Int): IntArray = when (index) {
    0 -> intArrayOf("#FF512F".toColorInt(), "#DD2476".toColorInt())
    1 -> intArrayOf("#00B4DB".toColorInt(), "#0083B0".toColorInt())
    2 -> intArrayOf("#11998e".toColorInt(), "#38ef7d".toColorInt())
    3 -> intArrayOf("#f8ff00".toColorInt(), "#3ad59f".toColorInt())
    4 -> intArrayOf(android.graphics.Color.RED, android.graphics.Color.GREEN)
    5 -> intArrayOf("#8E2DE2".toColorInt(), "#4A00E0".toColorInt())
    6 -> intArrayOf("#f953c6".toColorInt(), "#b91d73".toColorInt())
    7 -> intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)
    else -> intArrayOf("#11998e".toColorInt(), "#38ef7d".toColorInt())
}

fun getSolidColors(index: Int): IntArray = when (index) {
    0 -> intArrayOf(android.graphics.Color.GREEN, android.graphics.Color.GREEN)
    1 -> intArrayOf("#8BC34A".toColorInt(), "#8BC34A".toColorInt())
    2 -> intArrayOf("#CDDC39".toColorInt(), "#CDDC39".toColorInt())
    3 -> intArrayOf(android.graphics.Color.YELLOW, android.graphics.Color.YELLOW)
    4 -> intArrayOf("#FFC107".toColorInt(), "#FFC107".toColorInt())
    5 -> intArrayOf("#FF9800".toColorInt(), "#FF9800".toColorInt())
    6 -> intArrayOf("#FF5722".toColorInt(), "#FF5722".toColorInt())
    7 -> intArrayOf(android.graphics.Color.RED, android.graphics.Color.RED)
    8 -> intArrayOf("#E91E63".toColorInt(), "#E91E63".toColorInt())
    9 -> intArrayOf("#9C27B0".toColorInt(), "#9C27B0".toColorInt())
    10 -> intArrayOf(android.graphics.Color.BLUE, android.graphics.Color.BLUE)
    11 -> intArrayOf(android.graphics.Color.CYAN, android.graphics.Color.CYAN)
    else -> intArrayOf(android.graphics.Color.GREEN, android.graphics.Color.GREEN)
}
