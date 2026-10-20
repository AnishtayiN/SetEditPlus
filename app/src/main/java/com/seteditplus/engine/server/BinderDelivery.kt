package com.seteditplus.engine.server

import android.content.AttributionSource
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.system.Os
import android.util.Log

/**
 * تحویل Binder سرور به ContentProvider اپ با APIهای مخفی ActivityManager — همان روشی که Shizuku استفاده می‌کند.
 * چون این کد در app_process (نه پروسهٔ اپ) اجرا می‌شود، محدودیت hidden API اعمال نمی‌شود.
 */
internal object BinderDelivery {

    private const val TAG = "SetEditEngine"
    const val EXTRA_BINDER = "com.seteditplus.engine.extra.BINDER"
    const val METHOD_SEND_BINDER = "sendBinder"

    fun send(authority: String, userId: Int, binder: IBinder): Boolean {
        val amClass = Class.forName("android.app.IActivityManager")
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
            ?: return false
        val token: IBinder? = null
        var provider: Any? = null
        try {
            val holder = if (Build.VERSION.SDK_INT >= 29) {
                amClass.getMethod(
                    "getContentProviderExternal",
                    String::class.java, Int::class.javaPrimitiveType, IBinder::class.java, String::class.java
                ).invoke(am, authority, userId, token, authority)
            } else {
                amClass.getMethod(
                    "getContentProviderExternal",
                    String::class.java, Int::class.javaPrimitiveType, IBinder::class.java
                ).invoke(am, authority, userId, token)
            } ?: return false

            provider = holder.javaClass.getField("provider").get(holder) ?: return false
            val extras = Bundle()
            extras.putBinder(EXTRA_BINDER, binder)
            val reply = callCompat(provider, authority, METHOD_SEND_BINDER, extras)
            return reply != null
        } catch (t: Throwable) {
            Log.w(TAG, "send binder failed", t)
            return false
        } finally {
            if (provider != null) {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        amClass.getMethod(
                            "removeContentProviderExternalAsUser",
                            String::class.java, IBinder::class.java, Int::class.javaPrimitiveType
                        ).invoke(am, authority, token, userId)
                    } else {
                        amClass.getMethod(
                            "removeContentProviderExternal", String::class.java, IBinder::class.java
                        ).invoke(am, authority, token)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun callCompat(provider: Any, authority: String, method: String, extras: Bundle): Bundle? {
        val cls = Class.forName("android.content.IContentProvider")
        val s = String::class.java
        val b = Bundle::class.java
        return when {
            Build.VERSION.SDK_INT >= 31 -> {
                val source = AttributionSource.Builder(Os.getuid()).setPackageName(null).build()
                cls.getMethod("call", AttributionSource::class.java, s, s, s, b)
                    .invoke(provider, source, authority, method, null, extras)
            }
            Build.VERSION.SDK_INT == 30 ->
                cls.getMethod("call", s, s, s, s, s, b)
                    .invoke(provider, null, null, authority, method, null, extras)
            Build.VERSION.SDK_INT == 29 ->
                cls.getMethod("call", s, s, s, s, b)
                    .invoke(provider, null, authority, method, null, extras)
            else ->
                cls.getMethod("call", s, s, s, b)
                    .invoke(provider, null, method, null, extras)
        } as Bundle?
    }
}
