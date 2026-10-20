package com.seteditplus.engine

import android.os.IBinder
import android.util.Log
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.ShellResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class EngineState(
    val running: Boolean = false,
    val uid: Int = -1,
    val pid: Int = -1,
    val version: Int = 0,
    val context: String = ""
) {
    val isRoot get() = uid == 0
    val identity get() = when (uid) { 0 -> "root"; 2000 -> "shell (ADB)"; -1 -> "-"; else -> "uid $uid" }
}

/** نگهدارندهٔ Binder سرور داخلی در سمت اپ. */
object EngineClient {
    private const val TAG = "EngineClient"

    @Volatile
    private var binder: IBinder? = null
    @Volatile
    private var service: IEngineService? = null

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private val death = IBinder.DeathRecipient {
        Log.i(TAG, "engine died")
        binder = null
        service = null
        _state.value = EngineState()
        ActivityLog.add(LogCategory.ENGINE, "موتور داخلی متوقف شد", success = false)
    }

    @Synchronized
    fun onBinderReceived(b: IBinder) {
        if (binder === b && b.pingBinder()) return
        try { binder?.unlinkToDeath(death, 0) } catch (_: Throwable) {}
        try { b.linkToDeath(death, 0) } catch (e: Throwable) { Log.w(TAG, "linkToDeath", e) }
        binder = b
        val s = IEngineService.Stub.asInterface(b)
        service = s
        val st = try {
            EngineState(true, s.uid, s.pid, s.version, s.seLinuxContext)
        } catch (e: Throwable) {
            EngineState(true)
        }
        val wasRunning = _state.value.running
        _state.value = st
        if (!wasRunning) ActivityLog.add(LogCategory.ENGINE, "موتور داخلی متصل شد", "UID ${st.identity} • PID ${st.pid}")
    }

    fun isAlive(): Boolean = binder?.pingBinder() == true

    fun exec(command: String, timeoutMs: Long = 30_000): ShellResult {
        val s = service ?: return ShellResult.fail("موتور داخلی در حال اجرا نیست")
        return try {
            val b = s.exec(command, timeoutMs)
            ShellResult(
                b.getInt(EngineServiceImpl.KEY_EXIT, -1),
                b.getString(EngineServiceImpl.KEY_OUT) ?: "",
                b.getString(EngineServiceImpl.KEY_ERR) ?: ""
            )
        } catch (e: Throwable) {
            ShellResult.fail("خطای موتور: ${e.message}")
        }
    }

    fun stop() {
        try { service?.destroy() } catch (_: Throwable) { /* DeadObject انتظار می‌رود */ }
        binder = null
        service = null
        _state.value = EngineState()
        ActivityLog.add(LogCategory.ENGINE, "موتور داخلی توسط کاربر متوقف شد")
    }

    suspend fun awaitAlive(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (isAlive()) return true
            delay(250)
        }
        return isAlive()
    }
}
