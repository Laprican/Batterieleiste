package com.example.batterieleiste

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.batterieleiste.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BatteryBarTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            val isEnabled = repository.isServiceEnabled.first()
            updateTile(isEnabled)
        }
    }

    private fun updateTile(isEnabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "Aktiv" else "Inaktiv"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val currentState = repository.isServiceEnabled.first()
            val newState = !currentState
            repository.setServiceEnabled(newState)
            
            val intent = Intent(this@BatteryBarTileService, BatteryForegroundService::class.java)
            if (newState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopService(intent)
            }
            updateTile(newState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
