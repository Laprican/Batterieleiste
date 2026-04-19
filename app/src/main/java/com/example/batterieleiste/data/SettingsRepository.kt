package com.example.batterieleiste.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val IS_SERVICE_ENABLED = booleanPreferencesKey("is_service_enabled")
        val BAR_THICKNESS = intPreferencesKey("bar_thickness")
        val BAR_TRANSPARENCY = floatPreferencesKey("bar_transparency")
        val LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val MEDIUM_BATTERY_THRESHOLD = intPreferencesKey("medium_battery_threshold")
        val SELECTED_GRADIENT_INDEX = intPreferencesKey("selected_gradient_index")
        val CHARGING_ANIMATION_INDEX = intPreferencesKey("charging_animation_index")
        val BAR_POSITION = intPreferencesKey("bar_position") // 0: Top, 1: Bottom
        val AUTO_HIDE_FULLSCREEN = booleanPreferencesKey("auto_hide_fullscreen")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val USE_NOTCH_HANDLING = booleanPreferencesKey("use_notch_handling")
        val APP_THEME_INDEX = intPreferencesKey("app_theme_index")
        
        // Ring Mode Settings
        val USE_RING_MODE = booleanPreferencesKey("use_ring_mode")
        val RING_X = intPreferencesKey("ring_x")
        val RING_Y = intPreferencesKey("ring_y")
        val RING_SIZE = intPreferencesKey("ring_size")

        // Saved Standard Preset
        val SAVED_THICKNESS = intPreferencesKey("saved_thickness")
        val SAVED_TRANSPARENCY = floatPreferencesKey("saved_transparency")
        val SAVED_GRADIENT = intPreferencesKey("saved_gradient")
        val SAVED_ANIMATION = intPreferencesKey("saved_animation")
        val SAVED_DYNAMIC_COLORS = booleanPreferencesKey("saved_dynamic_colors")
        val SAVED_RING_MODE = booleanPreferencesKey("saved_ring_mode")
    }

    val isServiceEnabled: Flow<Boolean> = context.dataStore.data.map { it[IS_SERVICE_ENABLED] ?: false }
    val barThickness: Flow<Int> = context.dataStore.data.map { it[BAR_THICKNESS] ?: 4 }
    val barTransparency: Flow<Float> = context.dataStore.data.map { it[BAR_TRANSPARENCY] ?: 1.0f }
    val lowBatteryThreshold: Flow<Int> = context.dataStore.data.map { it[LOW_BATTERY_THRESHOLD] ?: 15 }
    val mediumBatteryThreshold: Flow<Int> = context.dataStore.data.map { it[MEDIUM_BATTERY_THRESHOLD] ?: 30 }
    val selectedGradientIndex: Flow<Int> = context.dataStore.data.map { it[SELECTED_GRADIENT_INDEX] ?: 0 }
    val chargingAnimationIndex: Flow<Int> = context.dataStore.data.map { it[CHARGING_ANIMATION_INDEX] ?: 0 }
    val barPosition: Flow<Int> = context.dataStore.data.map { it[BAR_POSITION] ?: 0 }
    val autoHideFullscreen: Flow<Boolean> = context.dataStore.data.map { it[AUTO_HIDE_FULLSCREEN] ?: false }
    val useDynamicColors: Flow<Boolean> = context.dataStore.data.map { it[USE_DYNAMIC_COLORS] ?: false }
    val useNotchHandling: Flow<Boolean> = context.dataStore.data.map { it[USE_NOTCH_HANDLING] ?: false }
    val appThemeIndex: Flow<Int> = context.dataStore.data.map { it[APP_THEME_INDEX] ?: 0 }

    // Ring Mode Accessors
    val useRingMode: Flow<Boolean> = context.dataStore.data.map { it[USE_RING_MODE] ?: false }
    val ringX: Flow<Int> = context.dataStore.data.map { it[RING_X] ?: 50 } // Center of screen roughly
    val ringY: Flow<Int> = context.dataStore.data.map { it[RING_Y] ?: 20 }
    val ringSize: Flow<Int> = context.dataStore.data.map { it[RING_SIZE] ?: 50 }

    suspend fun setServiceEnabled(enabled: Boolean) = context.dataStore.edit { it[IS_SERVICE_ENABLED] = enabled }
    suspend fun setBarThickness(thickness: Int) = context.dataStore.edit { it[BAR_THICKNESS] = thickness }
    suspend fun setBarTransparency(transparency: Float) = context.dataStore.edit { it[BAR_TRANSPARENCY] = transparency }
    suspend fun setLowBatteryThreshold(threshold: Int) = context.dataStore.edit { it[LOW_BATTERY_THRESHOLD] = threshold }
    suspend fun setMediumBatteryThreshold(threshold: Int) = context.dataStore.edit { it[MEDIUM_BATTERY_THRESHOLD] = threshold }
    suspend fun setSelectedGradientIndex(index: Int) = context.dataStore.edit { it[SELECTED_GRADIENT_INDEX] = index }
    suspend fun setChargingAnimationIndex(index: Int) = context.dataStore.edit { it[CHARGING_ANIMATION_INDEX] = index }
    suspend fun setBarPosition(position: Int) = context.dataStore.edit { it[BAR_POSITION] = position }
    suspend fun setAutoHideFullscreen(autoHide: Boolean) = context.dataStore.edit { it[AUTO_HIDE_FULLSCREEN] = autoHide }
    suspend fun setUseDynamicColors(use: Boolean) = context.dataStore.edit { it[USE_DYNAMIC_COLORS] = use }
    suspend fun setUseNotchHandling(use: Boolean) = context.dataStore.edit { it[USE_NOTCH_HANDLING] = use }
    suspend fun setAppThemeIndex(index: Int) = context.dataStore.edit { it[APP_THEME_INDEX] = index }
    
    suspend fun setUseRingMode(use: Boolean) = context.dataStore.edit { it[USE_RING_MODE] = use }
    suspend fun setRingX(x: Int) = context.dataStore.edit { it[RING_X] = x }
    suspend fun setRingY(y: Int) = context.dataStore.edit { it[RING_Y] = y }
    suspend fun setRingSize(size: Int) = context.dataStore.edit { it[RING_SIZE] = size }

    suspend fun saveCurrentAsStandard(
        thickness: Int,
        transparency: Float,
        gradient: Int,
        animation: Int,
        dynamicColors: Boolean,
        ringMode: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[SAVED_THICKNESS] = thickness
            prefs[SAVED_TRANSPARENCY] = transparency
            prefs[SAVED_GRADIENT] = gradient
            prefs[SAVED_ANIMATION] = animation
            prefs[SAVED_DYNAMIC_COLORS] = dynamicColors
            prefs[SAVED_RING_MODE] = ringMode
        }
    }

    suspend fun loadStandard() {
        context.dataStore.edit { prefs ->
            prefs[SAVED_THICKNESS]?.let { prefs[BAR_THICKNESS] = it }
            prefs[SAVED_TRANSPARENCY]?.let { prefs[BAR_TRANSPARENCY] = it }
            prefs[SAVED_GRADIENT]?.let { prefs[SELECTED_GRADIENT_INDEX] = it }
            prefs[SAVED_ANIMATION]?.let { prefs[CHARGING_ANIMATION_INDEX] = it }
            prefs[SAVED_DYNAMIC_COLORS]?.let { prefs[USE_DYNAMIC_COLORS] = it }
            prefs[SAVED_RING_MODE]?.let { prefs[USE_RING_MODE] = it }
        }
    }
}
