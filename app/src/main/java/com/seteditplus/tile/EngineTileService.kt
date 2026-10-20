package com.seteditplus.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.seteditplus.MainActivity
import com.seteditplus.adb.EngineForegroundService
import com.seteditplus.data.DirectSettings
import com.seteditplus.engine.EngineClient
import com.seteditplus.shizuku.ShizukuBridge

/** کاشی تنظیمات سریع: وضعیت و روشن/خاموش کردن موتور داخلی */
class EngineTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val alive = EngineClient.isAlive()
        tile.state = if (alive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "موتور SetEdit+"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                alive -> EngineClient.state.value.identity
                ShizukuBridge.isReady() -> "Shizuku فعال"
                else -> "خاموش"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (EngineClient.isAlive()) {
            EngineClient.stop()
            refresh()
            return
        }
        if (DirectSettings.isGranted(this)) {
            // می‌توانیم خودمان اشکال‌زدایی بی‌سیم را روشن و موتور را استارت کنیم
            EngineForegroundService.start(this, EngineForegroundService.ACTION_BOOT_START)
            qsTile?.apply { state = Tile.STATE_UNAVAILABLE; updateTile() }
        } else {
            openApp()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, "ENGINE")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
