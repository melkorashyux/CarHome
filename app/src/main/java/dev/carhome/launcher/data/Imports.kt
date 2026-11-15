package dev.carhome.launcher.data

import android.content.Context
import android.net.Uri
import dev.carhome.launcher.map.MapStore
import dev.carhome.launcher.wallpaper.Wallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ImportState {
    data object Idle : ImportState
    data class Copying(val progress: Float?) : ImportState
    data class Error(val message: String) : ImportState
}

/**
 * Runs map and wallpaper imports outside any one screen, so they keep going (and
 * report back) while you move between screens.
 */
object Imports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _map = MutableStateFlow<ImportState>(ImportState.Idle)
    val map: StateFlow<ImportState> = _map.asStateFlow()

    private val _wallpaper = MutableStateFlow<ImportState>(ImportState.Idle)
    val wallpaper: StateFlow<ImportState> = _wallpaper.asStateFlow()

    fun importMap(context: Context, file: File, prefs: Prefs) {
        if (_map.value is ImportState.Copying) return
        val app = context.applicationContext
        _map.value = ImportState.Copying(0f)
        scope.launch {
            val result = MapStore.import(app, Uri.fromFile(file), file.length()) { p ->
                _map.value = ImportState.Copying(p)
            }
            _map.value = when (result) {
                MapStore.ImportResult.Ok -> ImportState.Idle.also { prefs.bumpMap() }
                MapStore.ImportResult.NotAMap -> ImportState.Error("That isn't a .pmtiles map file")
                is MapStore.ImportResult.NoSpace -> ImportState.Error("Not enough space (needs ${result.neededMb} MB)")
                is MapStore.ImportResult.Failed -> ImportState.Error("Import failed: ${result.reason}")
            }
        }
    }

    fun importWallpaper(context: Context, file: File, prefs: Prefs) {
        if (_wallpaper.value is ImportState.Copying) return
        val app = context.applicationContext
        _wallpaper.value = ImportState.Copying(null)
        scope.launch {
            val ok = Wallpaper.importFrom(app, Uri.fromFile(file))
            if (ok) prefs.bumpWallpaper()
            _wallpaper.value = if (ok) ImportState.Idle else ImportState.Error("Couldn't read that image")
        }
    }
}
