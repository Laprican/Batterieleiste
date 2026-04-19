package com.example.batterieleiste.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.batterieleiste.BatteryForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()
    val batteryStats by viewModel.batteryStats.collectAsStateWithLifecycle()
    val selectedGradientIndex by viewModel.selectedGradientIndex.collectAsStateWithLifecycle()
    val barThickness by viewModel.barThickness.collectAsStateWithLifecycle()
    val barTransparency by viewModel.barTransparency.collectAsStateWithLifecycle()
    val chargingAnimationIndex by viewModel.chargingAnimationIndex.collectAsStateWithLifecycle()
    val barPosition by viewModel.barPosition.collectAsStateWithLifecycle()
    val useDynamicColors by viewModel.useDynamicColors.collectAsStateWithLifecycle()
    val useNotchHandling by viewModel.useNotchHandling.collectAsStateWithLifecycle()
    val appThemeIndex by viewModel.appThemeIndex.collectAsStateWithLifecycle()
    
    val useRingMode by viewModel.useRingMode.collectAsStateWithLifecycle()
    val ringX by viewModel.ringX.collectAsStateWithLifecycle()
    val ringY by viewModel.ringY.collectAsStateWithLifecycle()
    val ringSize by viewModel.ringSize.collectAsStateWithLifecycle()

    var showPermissionDialog by remember { mutableStateOf(false) }

    // App Theme Colors
    val backgroundColor = when (appThemeIndex) {
        1 -> Color(0xFFF5F5F5) // Hell
        2 -> Color(0xFF001F3F) // Ocean
        3 -> Color(0xFF0B2410) // Forest
        else -> Color(0xFF121212) // Dunkel (Standard)
    }

    val cardColor = when (appThemeIndex) {
        1 -> Color.White
        2 -> Color(0xFF003366)
        3 -> Color(0xFF1B3D21)
        else -> Color(0xFF212121)
    }

    val contentColor = if (appThemeIndex == 1) Color.Black else Color.White
    val secondaryTextColor = if (appThemeIndex == 1) Color.DarkGray else Color.Gray

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as BatteryForegroundService.LocalBinder
                val batteryService = binder.getService()
                batteryService.getBatteryStats().onEach { stats ->
                    viewModel.updateBatteryStats(stats)
                }.launchIn(CoroutineScope(Dispatchers.Main))
            }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val intent = Intent(context, BatteryForegroundService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(connection) }
    }

    Scaffold(
        containerColor = backgroundColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Akkustand Box
            Box(
                modifier = Modifier
                    .size(140.dp, 70.dp)
                    .clip(RoundedCornerShape(35.dp))
                    .background(cardColor),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (batteryStats.status == "Lädt") Icons.Default.FlashOn else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${batteryStats.level}%",
                        color = contentColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(text = batteryStats.remainingTime, color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

            BentoGrid(batteryStats, cardColor, contentColor, secondaryTextColor)

            Spacer(modifier = Modifier.height(24.dp))

            Text("App-Design", color = contentColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            AppThemeSelector(appThemeIndex) { viewModel.setAppThemeIndex(it) }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Balken Presets", color = contentColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            BarPresetSelector(cardColor, contentColor) { viewModel.applyTheme(it) }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Anpassung", color = contentColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Toggles
            SettingCard(cardColor) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Ring-Modus (um Kamera)", color = contentColor, fontSize = 14.sp)
                        Switch(checked = useRingMode, onCheckedChange = { viewModel.setUseRingMode(it) })
                    }
                    HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Dynamische Farben (Balken)", color = contentColor, fontSize = 14.sp)
                        Switch(checked = useDynamicColors, onCheckedChange = { viewModel.setUseDynamicColors(it) })
                    }
                    if (!useRingMode) {
                        HorizontalDivider(color = secondaryTextColor.copy(alpha = 0.2f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notch-Handling", color = contentColor, fontSize = 14.sp)
                                Text("Zeichnet den Balken hinter die Kamera-Aussparung", color = secondaryTextColor, fontSize = 11.sp)
                            }
                            Switch(checked = useNotchHandling, onCheckedChange = { viewModel.setUseNotchHandling(it) })
                        }
                    }
                }
            }

            if (!useDynamicColors) {
                Spacer(modifier = Modifier.height(8.dp))
                GradientSelector(selectedGradientIndex) { viewModel.setSelectedGradientIndex(it) }
            }
            
            if (!useRingMode) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Animation", color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                AnimationSelector(chargingAnimationIndex, cardColor, contentColor) { viewModel.setChargingAnimationIndex(it) }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Position", color = secondaryTextColor, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                PositionSelector(barPosition, cardColor, contentColor) { viewModel.setBarPosition(it) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sliders / Ring Configuration
            SettingCard(cardColor) {
                Column {
                    if (useRingMode) {
                        Text("Ring-Größe: ${ringSize}dp", color = contentColor, fontSize = 13.sp)
                        Slider(value = ringSize.toFloat(), onValueChange = { viewModel.setRingSize(it.toInt()) }, valueRange = 20f..150f)
                        
                        Text("Ring-Position X (Mitte): ${ringX}%", color = contentColor, fontSize = 13.sp)
                        Slider(value = ringX.toFloat(), onValueChange = { viewModel.setRingX(it.toInt()) }, valueRange = 0f..100f)
                        
                        Text("Ring-Position Y (Höhe): ${ringY}dp", color = contentColor, fontSize = 13.sp)
                        Slider(value = ringY.toFloat(), onValueChange = { viewModel.setRingY(it.toInt()) }, valueRange = 0f..100f)
                        
                        Text("Stärke: ${barThickness}dp", color = contentColor, fontSize = 13.sp)
                        Slider(value = barThickness.toFloat(), onValueChange = { viewModel.setBarThickness(it.toInt()) }, valueRange = 1f..10f)
                    } else {
                        Text("Balkendicke: ${barThickness}dp", color = contentColor, fontSize = 13.sp)
                        Slider(
                            value = barThickness.toFloat(),
                            onValueChange = { viewModel.setBarThickness(it.toInt()) },
                            valueRange = 1f..20f,
                            steps = 18
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Transparenz: ${(barTransparency * 100).toInt()}%", color = contentColor, fontSize = 13.sp)
                    Slider(
                        value = barTransparency,
                        onValueChange = { viewModel.setBarTransparency(it) },
                        valueRange = 0.1f..1.0f
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingCard(cardColor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Service aktiv", color = contentColor, fontSize = 14.sp)
                        Text(if (isServiceEnabled) "Läuft im Hintergrund" else "Service ist gestoppt", color = secondaryTextColor, fontSize = 11.sp)
                    }
                    Switch(checked = isServiceEnabled, onCheckedChange = { enabled ->
                        if (enabled) {
                            if (!Settings.canDrawOverlays(context)) {
                                showPermissionDialog = true
                            } else {
                                viewModel.setServiceEnabled(true)
                                val intent = Intent(context, BatteryForegroundService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        } else {
                            viewModel.setServiceEnabled(false)
                            val intent = Intent(context, BatteryForegroundService::class.java)
                            context.stopService(intent)
                        }
                    })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Akku-Optimierung Sektion
            SettingCard(cardColor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hintergrund-Einschränkung", color = contentColor, fontSize = 14.sp)
                        Text("Deaktiviere die Akku-Optimierung, damit die Leiste stabil bleibt.", color = secondaryTextColor, fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = contentColor)
                    }
                }
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
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text("Einstellungen öffnen") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun AppThemeSelector(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val themes = listOf("Dunkel", "Hell", "Ocean", "Forest")
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
    val positions = listOf("Oben", "Unten")
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

@Composable
fun AnimationSelector(selectedIndex: Int, cardColor: Color, contentColor: Color, onSelect: (Int) -> Unit) {
    val animations = listOf("Puls", "Fluss", "Scan")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        animations.forEachIndexed { index, name ->
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
fun GradientSelector(selectedIndex: Int, onSelect: (Int) -> Unit) {
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
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        gradients.forEachIndexed { index, colors ->
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(colors))
                    .clickable { onSelect(index) }
                    .then(if (selectedIndex == index) Modifier.border(2.dp, Color.Cyan, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) { if (selectedIndex == index) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
fun BentoGrid(stats: BatteryForegroundService.BatteryStats, cardColor: Color, contentColor: Color, secondaryTextColor: Color) {
    val healthIcon = if (stats.health == "Gut") Icons.Default.Favorite else Icons.Default.FavoriteBorder
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
            BentoCard(Modifier.weight(1f), "Spannung", "${stats.voltage / 1000.0}V", Icons.Default.ElectricBolt, Color(0xFFFFEB3B), cardColor, contentColor, secondaryTextColor)
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
