package dev.carhome.launcher.ui.files

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.carhome.launcher.ui.common.ContentTop
import dev.carhome.launcher.ui.apps.ScreenHeader
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.OnWallpaper
import dev.carhome.launcher.ui.glass.rowPress
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.Muted
import dev.carhome.launcher.ui.theme.Type
import java.io.File

/** What a file browser is looking for. */
enum class FileKind(val title: String, val extensions: Set<String>) {
    Wallpaper("Choose wallpaper", setOf("jpg", "jpeg", "png", "webp")),
    Map("Choose map file", setOf("pmtiles")),
}

private data class Entry(val file: File, val label: String, val detail: String, val icon: ImageVector, val isDir: Boolean)

/**
 * Car Home's own file browser. Head units often lack Android's standard file picker
 * (asking for it crashes the app), so this reads the unit's storage and USB sticks directly.
 */
@Composable
fun FilePickerScreen(kind: FileKind, onBack: () -> Unit, onPicked: (File) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }

    // null = the list of drives; otherwise the folder being shown.
    var folder by remember { mutableStateOf<File?>(null) }
    val entries = remember(folder, granted) { if (folder == null) roots() else list(folder!!, kind) }

    // Back goes up one folder, then to the drive list, then out.
    val goUp: () -> Unit = {
        val current = folder
        if (current == null) {
            onBack()
        } else {
            folder = if (roots().any { it.file == current }) null else current.parentFile
        }
    }

    // Starts below the clock bar, like the home screen.
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = ContentTop, bottom = 24.dp)) {
        ScreenHeader(kind.title, goUp)
        Spacer(Modifier.height(8.dp))
        OnWallpaper(Modifier.padding(start = 72.dp)) {
            Text(
                folder?.absolutePath ?: "Plug in your USB stick, then choose it below",
                style = Type.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))

        GlassSurface(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            when {
                !granted -> Message("Allow access to files so Car Home can read your USB stick.")
                entries.isEmpty() && folder == null -> Message("No storage found. Plug in a USB stick and come back.")
                entries.isEmpty() -> Message("Nothing here that Car Home can use.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.file.absolutePath }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .rowPress { if (e.isDir) folder = e.file else onPicked(e.file) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(e.icon, null, tint = if (e.isDir) Muted else Fg, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(e.label, style = Type.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text(e.detail, style = Type.body)
                        }
                        Box(
                            Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.body)
    }
}

/** The unit's storage plus any USB sticks or SD cards, wherever this unit mounts them. */
private fun roots(): List<Entry> {
    val found = linkedMapOf<String, Entry>()
    File("/storage").listFiles()?.forEach { f ->
        when {
            f.name == "self" -> Unit
            f.name == "emulated" -> {
                val internal = File("/storage/emulated/0")
                if (internal.canRead()) found[internal.absolutePath] =
                    Entry(internal, "Unit storage", "", Icons.Rounded.PhoneAndroid, true)
            }
            f.isDirectory && f.canRead() -> found[f.absolutePath] = Entry(f, "USB / SD card", f.name, Icons.Rounded.Usb, true)
        }
    }
    // Some head units mount USB sticks outside /storage.
    listOf("/mnt/usb_storage", "/mnt/usbhost", "/mnt/udisk", "/storage/usbdisk", "/mnt/media_rw").forEach { path ->
        val f = File(path)
        if (f.isDirectory && f.canRead()) {
            val inside = f.listFiles()?.filter { it.isDirectory && it.canRead() }.orEmpty()
            if (inside.isEmpty()) {
                found.putIfAbsent(f.absolutePath, Entry(f, "USB", f.name, Icons.Rounded.Usb, true))
            } else {
                inside.forEach { found.putIfAbsent(it.absolutePath, Entry(it, "USB", it.name, Icons.Rounded.Usb, true)) }
            }
        }
    }
    return found.values.toList()
}

private fun list(folder: File, kind: FileKind): List<Entry> {
    val children = folder.listFiles()?.filter { !it.name.startsWith(".") }.orEmpty()
    val dirs = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        .map { Entry(it, it.name, "", Icons.Rounded.Folder, true) }
    val files = children.filter { it.isFile && it.extension.lowercase() in kind.extensions }
        .sortedBy { it.name.lowercase() }
        .map { Entry(it, it.name, sizeText(it.length()), Icons.Rounded.Description, false) }
    return files + dirs // the files you're looking for first
}

private fun sizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.ENGLISH, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / 1024} KB"
}
