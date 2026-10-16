package com.seteditplus.data

import android.content.Context
import rikka.shizuku.Shizuku

/**
 * Checks availability of Root, Shizuku and Provider permission modes.
 */
object PermissionChecker {

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isShizukuPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * اپ پل نصب است و WRITE_SECURE_SETTINGS به آن داده شده؟
     */
    fun isProviderAvailable(context: Context): Boolean = ProviderBridge.isAvailable(context)
}
