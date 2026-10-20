package com.seteditplus.data

import android.content.Context
import com.seteditplus.adb.AdbEngine
import com.seteditplus.engine.EngineClient
import com.seteditplus.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku

/**
 * Checks availability of Root, Shizuku, Provider, Engine and Direct permission modes.
 */
object PermissionChecker {

    fun isRootAvailable(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).waitFor() == 0
    } catch (e: Exception) {
        false
    }

    fun isShizukuAvailable(): Boolean = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun isShizukuPermissionGranted(): Boolean = ShizukuBridge.hasPermission()

    /** اپ پل نصب است و WRITE_SECURE_SETTINGS به آن داده شده؟ */
    fun isProviderAvailable(context: Context): Boolean = ProviderBridge.isAvailable(context)

    fun isEngineAvailable(): Boolean = EngineClient.isAlive() || AdbEngine.isConnected()

    fun isDirectAvailable(context: Context): Boolean = DirectSettings.isGranted(context)
}
