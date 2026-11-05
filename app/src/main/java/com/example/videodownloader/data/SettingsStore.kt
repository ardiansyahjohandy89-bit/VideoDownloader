package com.example.videodownloader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SaveLocation { DOWNLOADS, MEDIA }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val saveLocation: SaveLocation = SaveLocation.DOWNLOADS,
    val defaultPreset: QualityPreset = QualityPreset.BEST,
    val maxConcurrent: Int = 2,
    val fragments: Int = 4,
    val wifiOnly: Boolean = false,
    val embedSubs: Boolean = false,
    val quickShare: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val autoUpdate: Boolean = true,
    val nightlyChannel: Boolean = false,
)

object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = read()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        write(next)
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) {
            prefs.edit().putLong("last_update_check", value).apply()
        }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            saveLocation = enumOf(prefs.getString("save_location", null), d.saveLocation),
            defaultPreset = enumOf(prefs.getString("default_preset", null), d.defaultPreset),
            maxConcurrent = prefs.getInt("max_concurrent", d.maxConcurrent).coerceIn(1, 3),
            fragments = prefs.getInt("fragments", d.fragments).coerceIn(1, 16),
            wifiOnly = prefs.getBoolean("wifi_only", d.wifiOnly),
            embedSubs = prefs.getBoolean("embed_subs", d.embedSubs),
            quickShare = prefs.getBoolean("quick_share", d.quickShare),
            theme = enumOf(prefs.getString("theme", null), d.theme),
            autoUpdate = prefs.getBoolean("auto_update", d.autoUpdate),
            nightlyChannel = prefs.getBoolean("nightly", d.nightlyChannel),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString("save_location", s.saveLocation.name)
            .putString("default_preset", s.defaultPreset.name)
            .putInt("max_concurrent", s.maxConcurrent)
            .putInt("fragments", s.fragments)
            .putBoolean("wifi_only", s.wifiOnly)
            .putBoolean("embed_subs", s.embedSubs)
            .putBoolean("quick_share", s.quickShare)
            .putString("theme", s.theme.name)
            .putBoolean("auto_update", s.autoUpdate)
            .putBoolean("nightly", s.nightlyChannel)
            .apply()
    }
}
