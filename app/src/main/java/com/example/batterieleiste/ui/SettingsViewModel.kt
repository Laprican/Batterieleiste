package com.example.batterieleiste.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.batterieleiste.BatteryForegroundService
import com.example.batterieleiste.data.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _batteryStats = MutableStateFlow(BatteryForegroundService.BatteryStats())
    val batteryStats: StateFlow<BatteryForegroundService.BatteryStats> = _batteryStats.asStateFlow()

    val isServiceEnabled: StateFlow<Boolean> = repository.isServiceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val barThickness: StateFlow<Int> = repository.barThickness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val barTransparency: StateFlow<Float> = repository.barTransparency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val selectedGradientIndex: StateFlow<Int> = repository.selectedGradientIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val chargingAnimationIndex: StateFlow<Int> = repository.chargingAnimationIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val barPosition: StateFlow<Int> = repository.barPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val useDynamicColors: StateFlow<Boolean> = repository.useDynamicColors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val useNotchHandling: StateFlow<Boolean> = repository.useNotchHandling
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appThemeIndex: StateFlow<Int> = repository.appThemeIndex
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    // Ring Mode
    val useRingMode: StateFlow<Boolean> = repository.useRingMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        
    val ringX: StateFlow<Int> = repository.ringX
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)
        
    val ringY: StateFlow<Int> = repository.ringY
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)
        
    val ringSize: StateFlow<Int> = repository.ringSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setServiceEnabled(enabled) }
    }

    fun setBarThickness(thickness: Int) {
        viewModelScope.launch { repository.setBarThickness(thickness) }
    }

    fun setBarTransparency(transparency: Float) {
        viewModelScope.launch { repository.setBarTransparency(transparency) }
    }

    fun setSelectedGradientIndex(index: Int) {
        viewModelScope.launch { repository.setSelectedGradientIndex(index) }
    }

    fun setChargingAnimationIndex(index: Int) {
        viewModelScope.launch { repository.setChargingAnimationIndex(index) }
    }

    fun setBarPosition(position: Int) {
        viewModelScope.launch { repository.setBarPosition(position) }
    }

    fun setUseDynamicColors(use: Boolean) {
        viewModelScope.launch { repository.setUseDynamicColors(use) }
    }

    fun setUseNotchHandling(use: Boolean) {
        viewModelScope.launch { repository.setUseNotchHandling(use) }
    }

    fun setAppThemeIndex(index: Int) {
        viewModelScope.launch { repository.setAppThemeIndex(index) }
    }
    
    fun setUseRingMode(use: Boolean) {
        viewModelScope.launch { repository.setUseRingMode(use) }
    }
    
    fun setRingX(x: Int) {
        viewModelScope.launch { repository.setRingX(x) }
    }
    
    fun setRingY(y: Int) {
        viewModelScope.launch { repository.setRingY(y) }
    }
    
    fun setRingSize(size: Int) {
        viewModelScope.launch { repository.setRingSize(size) }
    }

    fun applyTheme(themeIndex: Int) {
        viewModelScope.launch {
            when (themeIndex) {
                0 -> { // Modern
                    repository.setBarThickness(4)
                    repository.setBarTransparency(1.0f)
                    repository.setSelectedGradientIndex(2) // Green
                    repository.setChargingAnimationIndex(1)
                    repository.setUseDynamicColors(false)
                    repository.setUseRingMode(false)
                }
                1 -> { // Neon
                    repository.setBarThickness(5)
                    repository.setBarTransparency(1.0f)
                    repository.setSelectedGradientIndex(1) // Blue
                    repository.setChargingAnimationIndex(2)
                    repository.setUseDynamicColors(false)
                    repository.setUseRingMode(false)
                }
                2 -> { // Candy
                    repository.setBarThickness(6)
                    repository.setBarTransparency(1.0f)
                    repository.setSelectedGradientIndex(6) // Pink
                    repository.setChargingAnimationIndex(1)
                    repository.setUseDynamicColors(false)
                    repository.setUseRingMode(false)
                }
                3 -> { // Cyber
                    repository.setBarThickness(6)
                    repository.setBarTransparency(0.9f)
                    repository.setSelectedGradientIndex(5) // Purple
                    repository.setChargingAnimationIndex(1)
                    repository.setUseDynamicColors(false)
                    repository.setUseRingMode(false)
                }
                4 -> { // Standart speichern
                    saveCurrentAsStandardInternal()
                }
                5 -> { // Standart einstellen
                    repository.loadStandard()
                }
            }
        }
    }

    fun saveCurrentAsStandard() {
        viewModelScope.launch { saveCurrentAsStandardInternal() }
    }

    private suspend fun saveCurrentAsStandardInternal() {
        repository.saveCurrentAsStandard(
            barThickness.value,
            barTransparency.value,
            selectedGradientIndex.value,
            chargingAnimationIndex.value,
            useDynamicColors.value,
            useRingMode.value
        )
    }

    fun updateBatteryStats(stats: BatteryForegroundService.BatteryStats) {
        _batteryStats.value = stats
    }
}
