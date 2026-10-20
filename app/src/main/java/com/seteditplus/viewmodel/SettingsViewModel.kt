package com.seteditplus.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seteditplus.data.ModifiedSetting
import com.seteditplus.data.PermissionChecker
import com.seteditplus.data.GamePreset
import com.seteditplus.data.SettingsDump
import com.seteditplus.data.GameMode
import com.seteditplus.data.PermissionMode
import com.seteditplus.data.PreferencesRepository
import com.seteditplus.data.SettingsEntry
import com.seteditplus.data.SettingsRepository
import com.seteditplus.data.SettingsTable
import com.seteditplus.data.ThemeMode
import com.seteditplus.ui.screens.ExtendedTab
import com.seteditplus.data.DirectSettings
import com.seteditplus.engine.EngineClient
import com.seteditplus.adb.AdbEngine
import com.seteditplus.log.ActivityLog
import com.seteditplus.log.LogCategory
import com.seteditplus.priv.Privileged
import com.seteditplus.shizuku.ShizukuBridge
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = PreferencesRepository(application)

    val permissionMode = mutableStateOf<PermissionMode?>(preferences.getPermissionMode())
    val isPermissionGranted = mutableStateOf(false)
    val permissionError = mutableStateOf<String?>(null)

    val themeMode = mutableStateOf(preferences.getThemeMode())
    val isAutostartEnabled = mutableStateOf(preferences.isAutostartEnabled())

    val currentTab = mutableStateOf(ExtendedTab.SYSTEM)
    var currentTable: SettingsTable = SettingsTable.SYSTEM

    private val _allSettings = mutableStateListOf<SettingsEntry>()
    val filteredSettings = mutableStateListOf<SettingsEntry>()

    /**
     * Cached modified settings map to avoid re-reading SharedPreferences per list item.
     */
    val modifiedSettingsMap = mutableStateMapOf<String, ModifiedSetting>()

    val searchQuery = mutableStateOf("")
    val isSearchActive = mutableStateOf(false)

    val isLoading = mutableStateOf(false)

    val operationMessage = mutableStateOf<String?>(null)

    private var repository: SettingsRepository? = null

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 101) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ShizukuBridge.bind()
                viewModelScope.launch {
                    finalizePermissionSuccess(PermissionMode.SHIZUKU)
                }
            } else {
                permissionError.value = "Shizuku access denied."
                isLoading.value = false
            }
        }
    }

    init {
        Privileged.rootGranted = preferences.isRootConfirmed()
        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
            Log.w("SettingsViewModel", "Shizuku listener registration failed", e)
        }

        permissionMode.value?.let { mode ->
            selectPermissionMode(mode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
            Log.w("SettingsViewModel", "Shizuku listener removal failed", e)
        }
    }

    /**
     * Attempt to set the permission mode (Root or Shizuku).
     */
    fun selectPermissionMode(mode: PermissionMode) {
        viewModelScope.launch {
            isLoading.value = true
            permissionError.value = null

            if (mode == PermissionMode.ROOT) {
                val available = withContext(Dispatchers.IO) { PermissionChecker.isRootAvailable() }
                if (available) {
                    Privileged.rootGranted = true
                    preferences.setRootConfirmed(true)
                    finalizePermissionSuccess(mode)
                } else {
                    permissionError.value = "Root access not detected. Try using Shizuku."
                    isPermissionGranted.value = false
                    isLoading.value = false
                }
            } else if (mode == PermissionMode.SHIZUKU) {
                val available = withContext(Dispatchers.IO) { PermissionChecker.isShizukuAvailable() }
                if (!available) {
                    permissionError.value = "Shizuku is not running."
                    isPermissionGranted.value = false
                    isLoading.value = false
                    return@launch
                }
                
                val granted = withContext(Dispatchers.IO) { PermissionChecker.isShizukuPermissionGranted() }
                if (granted) {
                    ShizukuBridge.bind()
                    finalizePermissionSuccess(mode)
                } else {
                    try {
                        Shizuku.requestPermission(101)
                    } catch (e: Exception) {
                        permissionError.value = "Shizuku request error: ${e.message}"
                        isLoading.value = false
                    }
                }
            } else if (mode == PermissionMode.ENGINE) {
                val ok = withContext(Dispatchers.IO) {
                    EngineClient.isAlive() || EngineClient.awaitAlive(2500) || AdbEngine.isConnected()
                }
                if (ok) {
                    finalizePermissionSuccess(mode)
                } else {
                    permissionError.value = "موتور داخلی فعال نیست. از «راه‌اندازی موتور داخلی» یک بار جفت‌سازی و استارت کن."
                    engineSetupRequested.value = true
                    isPermissionGranted.value = false
                    isLoading.value = false
                }
            } else if (mode == PermissionMode.DIRECT) {
                if (DirectSettings.isGranted(getApplication())) {
                    finalizePermissionSuccess(mode)
                } else {
                    permissionError.value = "WRITE_SECURE_SETTINGS هنوز به SetEdit+ داده نشده. با موتور داخلی/Shizuku/روت «اعطای دائمی» را بزن یا: adb shell pm grant com.seteditplus android.permission.WRITE_SECURE_SETTINGS"
                    isPermissionGranted.value = false
                    isLoading.value = false
                }
            } else if (mode == PermissionMode.PROVIDER) {
                val ok = withContext(Dispatchers.IO) {
                    PermissionChecker.isProviderAvailable(getApplication())
                }
                if (ok) {
                    finalizePermissionSuccess(mode)
                } else {
                    permissionError.value = "اپ پل نصب نیست یا دستوری به آن داده نشده. اول Settings Database Provider را نصب و grant کن، بعد دوباره امتحان کن."
                    isPermissionGranted.value = false
                    isLoading.value = false
                }
            }
        }
    }

    /** وقتی کاربر حالت موتور را زد ولی موتور فعال نبود، صفحهٔ راه‌اندازی باز شود */
    val engineSetupRequested = mutableStateOf(false)

    fun repositoryOrNull(): SettingsRepository? = repository

    /** خواندن یک کلید از هر جدول (برای ابزارها) */
    suspend fun readSetting(table: SettingsTable, key: String): String? =
        withContext(Dispatchers.IO) { repository?.get(table, key) }

    /** نوشتن کلید از ابزارها + ثبت در «تغییرات» و گزارش فعالیت */
    suspend fun writeSetting(table: SettingsTable, key: String, value: String?, category: LogCategory = LogCategory.TWEAK): Boolean {
        val repo = repository ?: return false
        val stock = withContext(Dispatchers.IO) { repo.get(table, key) }
        val ok = withContext(Dispatchers.IO) {
            if (value == null) repo.delete(table, key) else repo.put(table, key, value)
        }
        if (ok) {
            val existing = preferences.getModifiedSettings().find { it.key == key && it.table == table.name }
            val mod = ModifiedSetting(table.name, key, value ?: "", existing?.stockValue ?: stock, value == null)
            preferences.addModifiedSetting(mod)
            modifiedSettingsMap[key] = mod
        }
        ActivityLog.add(category, "${table.name.lowercase()} • $key", if (value == null) "حذف" else "= $value", ok)
        return ok
    }

    private fun finalizePermissionSuccess(mode: PermissionMode) {
        ActivityLog.add(LogCategory.SYSTEM, "حالت دسترسی: ${mode.name}")
        permissionMode.value = mode
        preferences.setPermissionMode(mode)
        isPermissionGranted.value = true
        repository = SettingsRepository(getApplication(), mode)
        loadSettings()
        isLoading.value = false
    }

    fun setTheme(mode: ThemeMode) {
        themeMode.value = mode
        preferences.setThemeMode(mode)
    }

    fun setAutostart(enabled: Boolean) {
        isAutostartEnabled.value = enabled
        preferences.setAutostartEnabled(enabled)
    }

    fun resetPreferences() {
        val repo = repository ?: return
        val mods = preferences.getModifiedSettings()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mods.forEach { mod ->
                    try {
                        val table = try { SettingsTable.valueOf(mod.table) } catch (e: Exception) { SettingsTable.SYSTEM }
                        if (mod.stockValue == null) {
                            repo.delete(table, mod.key)
                        } else {
                            repo.put(table, mod.key, mod.stockValue)
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsViewModel", "Failed to restore ${mod.key}", e)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                preferences.resetAllPreferences()
                themeMode.value = ThemeMode.SYSTEM
                isAutostartEnabled.value = false
                permissionMode.value = null
                isPermissionGranted.value = false
                modifiedSettingsMap.clear()
                operationMessage.value = "Preferences reset to stock"
            }
        }
    }

    val isRefreshing = mutableStateOf(false)

    fun refreshSettings() {
        if (repository == null) return
        viewModelScope.launch {
            isRefreshing.value = true
            loadSettingsInternal()
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.delay(400)
                isRefreshing.value = false
            }
        }
    }

    /**
     * Switch to a different settings table or Modified tab.
     */
    fun switchTable(table: SettingsTable) {
        currentTab.value = when(table) {
            SettingsTable.SYSTEM -> ExtendedTab.SYSTEM
            SettingsTable.SECURE -> ExtendedTab.SECURE
            SettingsTable.GLOBAL -> ExtendedTab.GLOBAL
        }
        currentTable = table
        loadSettings()
    }

    fun switchTab(tab: ExtendedTab) {
        currentTab.value = tab
        if (tab != ExtendedTab.MODIFIED) {
            currentTable = when(tab) {
                ExtendedTab.SYSTEM -> SettingsTable.SYSTEM
                ExtendedTab.SECURE -> SettingsTable.SECURE
                ExtendedTab.GLOBAL -> SettingsTable.GLOBAL
                ExtendedTab.MODIFIED -> currentTable
            }
        }
        loadSettings()
    }

    /**
     * Load settings for the current table or Modified tab.
     */
    fun loadSettings() {
        if (repository == null) return
        viewModelScope.launch {
            isLoading.value = true
            loadSettingsInternal()
            isLoading.value = false
        }
    }

    private suspend fun loadSettingsInternal() {
        val repo = repository ?: return
        try {
            val modified = withContext(Dispatchers.IO) { preferences.getModifiedSettings() }
            withContext(Dispatchers.Main) {
                modifiedSettingsMap.clear()
                modified.forEach { modifiedSettingsMap[it.key] = it }
            }

            if (currentTab.value == ExtendedTab.MODIFIED) {
                val liveEntries = withContext(Dispatchers.IO) {
                    modified.map { mod ->
                        val table = try { SettingsTable.valueOf(mod.table) } catch (e: Exception) { SettingsTable.SYSTEM }
                        if (mod.isDeleted) {
                            SettingsEntry(mod.key, "")
                        } else {
                            val liveValue = repo.get(table, mod.key) ?: mod.value
                            SettingsEntry(mod.key, liveValue)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    _allSettings.clear()
                    _allSettings.addAll(liveEntries)
                }
            } else {
                val entries = withContext(Dispatchers.IO) {
                    repo.getAll(currentTable)
                }
                withContext(Dispatchers.Main) {
                    _allSettings.clear()
                    _allSettings.addAll(entries)
                }
            }
            
            applyFilter()
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "Error loading settings", e)
            withContext(Dispatchers.Main) {
                operationMessage.value = "Load error: ${e.message}"
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            applyFilter()
        }
    }

    private suspend fun applyFilter() {
        val query = searchQuery.value.lowercase()
        val result = withContext(Dispatchers.Default) {
            if (query.isEmpty()) {
                _allSettings.toList()
            } else {
                _allSettings.filter {
                    it.key.lowercase().contains(query) || it.value.lowercase().contains(query)
                }
            }
        }
        
        withContext(Dispatchers.Main) {
            filteredSettings.clear()
            filteredSettings.addAll(result)
        }
    }

    /**
     * Put/update a setting value.
     */
    fun putSetting(key: String, value: String) {
        val repo = repository ?: return
        var targetTable = currentTable
        var stockValue: String? = null
        
        if (currentTab.value == ExtendedTab.MODIFIED) {
            val mod = preferences.getModifiedSettings().find { it.key == key }
            if (mod != null) {
                targetTable = try { SettingsTable.valueOf(mod.table) } catch (e: Exception) { currentTable }
                stockValue = mod.stockValue
            }
        } else {
            stockValue = _allSettings.find { it.key == key }?.value
            
            val existingMod = preferences.getModifiedSettings().find { it.key == key }
            if (existingMod != null) {
                stockValue = existingMod.stockValue
            }
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                repo.put(targetTable, key, value)
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    val mod = ModifiedSetting(targetTable.name, key, value, stockValue, false)
                    preferences.addModifiedSetting(mod)
                    modifiedSettingsMap[key] = mod
                    operationMessage.value = "✓ Setting saved"
                    ActivityLog.add(LogCategory.SETTING, "${targetTable.name.lowercase()} • $key", "= $value")
                    loadSettings()
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "Error: System rejected the change"
                    ActivityLog.add(LogCategory.SETTING, "${targetTable.name.lowercase()} • $key", "رد شد: $value", false)
                }
            }
        }
    }

    /**
     * Delete a setting. Instead of removing from tracking, we mark it as deleted to show in the Modified tab.
     */
    fun deleteSetting(key: String) {
        val repo = repository ?: return
        var targetTable = currentTable
        var stockValue: String? = null

        if (currentTab.value == ExtendedTab.MODIFIED) {
            val mod = preferences.getModifiedSettings().find { it.key == key }
            if (mod != null) {
                targetTable = try { SettingsTable.valueOf(mod.table) } catch (e: Exception) { currentTable }
                stockValue = mod.stockValue
            }
        } else {
            val existingMod = preferences.getModifiedSettings().find { it.key == key }
            stockValue = existingMod?.stockValue ?: _allSettings.find { it.key == key }?.value
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                repo.delete(targetTable, key)
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    val mod = ModifiedSetting(targetTable.name, key, "", stockValue, true)
                    preferences.addModifiedSetting(mod)
                    modifiedSettingsMap[key] = mod
                    operationMessage.value = "✓ Setting deleted"
                    ActivityLog.add(LogCategory.SETTING, "${targetTable.name.lowercase()} • $key", "حذف شد")
                    loadSettings()
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "Error: System rejected deletion"
                }
            }
        }
    }

    /**
     * Restore a setting back to its stock value, or delete it if it was newly added.
     */
    fun restoreSetting(key: String) {
        val repo = repository ?: return
        val mod = preferences.getModifiedSettings().find { it.key == key } ?: return
        val targetTable = try { SettingsTable.valueOf(mod.table) } catch (e: Exception) { SettingsTable.SYSTEM }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (mod.stockValue == null) {
                    repo.delete(targetTable, key)
                } else {
                    repo.put(targetTable, key, mod.stockValue)
                }
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    preferences.removeModifiedSetting(mod.table, key)
                    modifiedSettingsMap.remove(key)
                    operationMessage.value = "✓ Setting restored to stock"
                    ActivityLog.add(LogCategory.SETTING, "${targetTable.name.lowercase()} • $key", "بازگردانی به پیش‌فرض")
                    loadSettings()
                }
            } else {
                withContext(Dispatchers.Main) {
                    operationMessage.value = "Error: Failed to restore setting"
                }
            }
        }
    }

    /**
     * Add a new setting.
     */
    fun addSetting(key: String, value: String) {
        putSetting(key, value)
    }

    fun clearMessage() {
        operationMessage.value = null
    }

    // ---------- حالت بازی (Game Mode) ----------
    val gameActivePreset = mutableStateOf<GamePreset?>(null)
    val gameBusy = mutableStateOf(false)
    private var gameSnapshot: MutableMap<String, String?>? = null

    /**
     * اعمال پروفایل بازی + لانچ بازی. snapshot قبلی نگه داشته می‌شود تا «بازگردانی» دقیق باشد.
     */
    fun startGameMode(preset: GamePreset, onLaunched: (Boolean) -> Unit) {
        val repo = repository ?: run { onLaunched(false); return }
        viewModelScope.launch {
            gameBusy.value = true
            val result = withContext(Dispatchers.IO) {
                val snap = GameMode.snapshot(getApplication(), repo, preset).toMutableMap()
                val applied = GameMode.applyAll(repo, preset)
                Pair(snap, applied)
            }
            gameSnapshot = result.first
            gameActivePreset.value = preset
            gameBusy.value = false
            val ok = result.second.all { it.second }
            operationMessage.value = if (ok) "پروفایل بازی اعمال شد" else "بعضی کلیدها اعمال نشد"
            ActivityLog.add(LogCategory.GAME, "حالت بازی: ${preset.label}", "", ok)
            val launched = GameMode.launchApp(getApplication(), preset.packageName)
            onLaunched(launched)
        }
    }

    /**
     * بازگردانی تنظیمات به snapshotِ قبل از شروع.
     */
    fun stopGameMode() {
        val repo = repository ?: return
        val preset = gameActivePreset.value ?: return
        val snap = gameSnapshot ?: return
        viewModelScope.launch {
            gameBusy.value = true
            withContext(Dispatchers.IO) { GameMode.restoreAll(repo, snap, preset) }
            gameSnapshot = null
            gameActivePreset.value = null
            gameBusy.value = false
            operationMessage.value = "تنظیمات بازگردانی شد"
            loadSettingsInternal()
        }
    }

    /**
     * خروجی کامل دیتابیس تنظیمات به Download — همان کار اسکریپت، از داخل اپ.
     */
    fun exportAllSettings(onDone: (String?) -> Unit) {
        val repo = repository ?: run { onDone(null); return }
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                SettingsDump.export(getApplication(), repo)
            }
            operationMessage.value = if (path != null) "ذخیره شد: $path" else "ذخیره ناموفق بود"
            onDone(path)
        }
    }
}
