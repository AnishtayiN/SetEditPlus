package com.seteditplus.ui.hub

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.Privileged
import com.seteditplus.priv.ShellResult

data class AppItem(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val enabled: Boolean,
    val versionName: String?,
    val uid: Int
)

data class PermItem(val name: String, val granted: Boolean, val dangerous: Boolean)

/** منطق مدیریت برنامه‌ها — همهٔ اکشن‌ها با pm/am/appops از طریق Privileged */
object AppOps {

    fun loadApps(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val list: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
        else pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
        return list.mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            AppItem(
                pkg = pi.packageName,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName),
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                enabled = ai.enabled,
                versionName = pi.versionName,
                uid = ai.uid
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun icon(ctx: Context, pkg: String): ImageBitmap? = try {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }

    fun permissions(ctx: Context, pkg: String): List<PermItem> {
        val pm = ctx.packageManager
        val pi = try {
            if (Build.VERSION.SDK_INT >= 33)
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            else pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (_: Throwable) {
            return emptyList()
        }
        val names = pi.requestedPermissions ?: return emptyList()
        val flags = pi.requestedPermissionsFlags ?: IntArray(names.size)
        return names.indices.map { i ->
            val name = names[i]
            val dangerous = try {
                val info = pm.getPermissionInfo(name, 0)
                val base = if (Build.VERSION.SDK_INT >= 28) info.protection else (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
                base == PermissionInfo.PROTECTION_DANGEROUS ||
                    (if (Build.VERSION.SDK_INT >= 28) info.protectionFlags else info.protectionLevel) and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0
            } catch (_: Throwable) {
                false
            }
            PermItem(name, (flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0, dangerous)
        }.sortedWith(compareByDescending<PermItem> { it.dangerous }.thenBy { it.name })
    }

    private fun run(cmd: String): ShellResult = Privileged.exec(cmd, 60_000, log = LogCategory.APPS)

    fun grant(pkg: String, perm: String) = run("pm grant ${Privileged.q(pkg)} ${Privileged.q(perm)}")
    fun revoke(pkg: String, perm: String) = run("pm revoke ${Privileged.q(pkg)} ${Privileged.q(perm)}")
    fun forceStop(pkg: String) = run("am force-stop ${Privileged.q(pkg)}")
    fun disable(pkg: String) = run("pm disable-user --user 0 ${Privileged.q(pkg)}")
    fun enable(pkg: String) = run("pm enable ${Privileged.q(pkg)}")
    fun clearData(pkg: String) = run("pm clear ${Privileged.q(pkg)}")
    fun uninstallForUser(pkg: String) = run("pm uninstall -k --user 0 ${Privileged.q(pkg)}")
    fun reinstallExisting(pkg: String) = run("cmd package install-existing ${Privileged.q(pkg)}")
    fun compile(pkg: String) = run("cmd package compile -m speed-profile -f ${Privileged.q(pkg)}")
    fun standby(pkg: String, bucket: String) = run("am set-standby-bucket ${Privileged.q(pkg)} $bucket")
    fun backgroundRun(pkg: String, allow: Boolean) =
        run("cmd appops set ${Privileged.q(pkg)} RUN_ANY_IN_BACKGROUND ${if (allow) "allow" else "ignore"}")
    fun batteryWhitelist(pkg: String, add: Boolean) =
        run("cmd deviceidle whitelist ${if (add) "+" else "-"}${pkg}")

    val STANDBY_BUCKETS = listOf("active", "working_set", "frequent", "rare", "restricted")
}
