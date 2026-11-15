package dev.carhome.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The home-screen buttons whose app you choose yourself. */
enum class AppSlot(val key: String, val label: String, val default: String?) {
    Connect("app_connect", "Connect Smartphone", null),
    Bluetooth("app_bluetooth", "Bluetooth", null),
    Radio("app_radio", "Radio", null),
    Navigation("app_navigation", "Navigation", "com.google.android.apps.maps"),
}

/** All settings, saved on the unit and exposed as flows the UI can watch. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("car_home", Context.MODE_PRIVATE)

    private val _glass = MutableStateFlow(sp.getFloat(KEY_GLASS, 0.5f))
    private val _use24h = MutableStateFlow(sp.getBoolean(KEY_24H, false))
    private val _autoSwitch = MutableStateFlow(sp.getBoolean(KEY_AUTO, true))
    private val _wallpaperVersion = MutableStateFlow(sp.getLong(KEY_WALLPAPER, 0L))
    private val slots = AppSlot.entries.associateWith { MutableStateFlow(sp.getString(it.key, it.default)) }

    /** 0..1; 0.5 matches the Figma design (black at 20%). */
    val glass: StateFlow<Float> = _glass.asStateFlow()
    val use24h: StateFlow<Boolean> = _use24h.asStateFlow()
    val autoSwitch: StateFlow<Boolean> = _autoSwitch.asStateFlow()
    /** Changes whenever a new wallpaper is imported, so it gets reloaded. */
    val wallpaperVersion: StateFlow<Long> = _wallpaperVersion.asStateFlow()

    fun slot(slot: AppSlot): StateFlow<String?> = slots.getValue(slot).asStateFlow()

    fun setGlass(value: Float) {
        _glass.value = value
        sp.edit().putFloat(KEY_GLASS, value).apply()
    }

    fun setUse24h(value: Boolean) {
        _use24h.value = value
        sp.edit().putBoolean(KEY_24H, value).apply()
    }

    fun setAutoSwitch(value: Boolean) {
        _autoSwitch.value = value
        sp.edit().putBoolean(KEY_AUTO, value).apply()
    }

    fun setSlot(slot: AppSlot, pkg: String?) {
        slots.getValue(slot).value = pkg
        sp.edit().putString(slot.key, pkg).apply()
    }

    fun bumpWallpaper() {
        val v = System.currentTimeMillis()
        _wallpaperVersion.value = v
        sp.edit().putLong(KEY_WALLPAPER, v).apply()
    }

    private companion object {
        const val KEY_GLASS = "glass"
        const val KEY_24H = "use24h"
        const val KEY_AUTO = "autoSwitch"
        const val KEY_WALLPAPER = "wallpaperVersion"
    }
}
