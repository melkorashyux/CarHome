package dev.carhome.launcher.ui.settings

import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.carhome.launcher.data.AppSlot
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.data.ImportState
import dev.carhome.launcher.data.Imports
import dev.carhome.launcher.map.MapStore
import dev.carhome.launcher.ui.files.FileKind
import dev.carhome.launcher.util.ClickSound
import dev.carhome.launcher.util.CrashLog
import dev.carhome.launcher.media.MediaRepository
import dev.carhome.launcher.location.GpsTracker
import dev.carhome.launcher.time.TimeSync
import dev.carhome.launcher.ui.apps.ScreenHeader
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.LocalBackdrop
import dev.carhome.launcher.ui.glass.Pill
import dev.carhome.launcher.ui.glass.Rounded8
import dev.carhome.launcher.ui.glass.rowPress
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.Muted
import dev.carhome.launcher.ui.theme.Type
import dev.carhome.launcher.update.Updater
import dev.carhome.launcher.util.Launch
import dev.carhome.launcher.wallpaper.Wallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val SwitchGreen = Color(0xFF34C759) // iOS "on" green

/** Grouped settings list in the style of iOS Settings, on the same glass. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    onBack: () -> Unit,
    onPick: (AppSlot) -> Unit,
    onOpenTimeZone: () -> Unit,
    onPickFile: (FileKind) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glass by prefs.glass.collectAsState()
    val use24h by prefs.use24h.collectAsState()
    val media by MediaRepository.state.collectAsState()
    val wallpaper = LocalBackdrop.current.wallpaper
    val wallpaperVersion by prefs.wallpaperVersion.collectAsState()
    val hasCustomWallpaper = remember(wallpaperVersion) { Wallpaper.hasCustom(context) }
    val hasDefaultWallpaper = remember { Wallpaper.defaultResId(context) != null }

    val wallpaperImport by Imports.wallpaper.collectAsState()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Settings", onBack)
        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Display") {
                val wallpaperValue = when (val w = wallpaperImport) {
                    is ImportState.Copying -> "Loading…"
                    is ImportState.Error -> "${w.message} · Try again"
                    ImportState.Idle -> "Choose photo"
                }
                LinkRow("Wallpaper", wallpaperValue, onClick = { onPickFile(FileKind.Wallpaper) }) {
                    if (wallpaper != null) {
                        Image(wallpaper, null, Modifier.size(96.dp, 56.dp).clip(Rounded8), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(96.dp, 56.dp).clip(Rounded8).background(Color.White.copy(alpha = 0.08f)))
                    }
                }
                if (hasCustomWallpaper && hasDefaultWallpaper) {
                    Divider()
                    LinkRow("Use default wallpaper", null, onClick = {
                        scope.launch {
                            Wallpaper.resetToDefault(context)
                            prefs.bumpWallpaper()
                        }
                    })
                }
                Divider()
                val lightGlass by prefs.lightGlass.collectAsState()
                ChoiceRow(
                    title = "Glass",
                    options = listOf("Dark", "Light"),
                    selected = if (lightGlass) 1 else 0,
                    onSelect = { prefs.setLightGlass(it == 1) },
                )
                Divider()
                val buttonSounds by prefs.buttonSounds.collectAsState()
                SwitchRow("Button sounds", buttonSounds, prefs::setButtonSounds)
                Divider()
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Glass intensity", style = Type.rowTitle, modifier = Modifier.width(232.dp))
                    Slider(
                        value = glass,
                        onValueChange = prefs::setGlass,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Fg,
                            activeTrackColor = Fg,
                            inactiveTrackColor = Fg.copy(alpha = 0.2f),
                        ),
                    )
                }
            }

            Section(
                "Clock",
                footer = "The unit's clock can reset when the car is off, so Car Home sets its clock once per drive from GPS. " +
                    "Internet time is a fallback for when GPS has no fix; with it off, nothing leaves the car.",
            ) {
                val internetTime by prefs.internetTime.collectAsState()
                TimeSyncRow(internetTime)
                Divider()
                SwitchRow("Internet time", internetTime) { on ->
                    prefs.setInternetTime(on)
                    TimeSync.start(context, on)
                }
                Divider()
                val zone by prefs.timeZone.collectAsState()
                LinkRow("Time zone", zone ?: "Automatic (${java.time.ZoneId.systemDefault().id})", onClick = onOpenTimeZone)
                Divider()
                SwitchRow("24-hour clock", use24h, prefs::setUse24h)
            }

            Section(
                "Offline map",
                footer = "Make the map file on your computer (see README), copy it to a USB stick, then import it here.",
            ) {
                OfflineMapRow(prefs, onPickFile)
            }


            Section("Buttons", footer = "Tip: long-press a button on the home screen to change its app.") {
                AppSlot.entries.forEachIndexed { i, slot ->
                    if (i > 0) Divider()
                    val pkg by prefs.slot(slot).collectAsState()
                    LinkRow(slot.label, Launch.label(context, pkg) ?: "Not set", onClick = { onPick(slot) })
                }
            }

            Section("System") {
                LinkRow(
                    "Notification access",
                    if (media.hasAccess) "Allowed" else "Not allowed",
                    onClick = { Launch.settings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                )
                Divider()
                LinkRow("Default home app", null, onClick = { Launch.settings(context, Settings.ACTION_HOME_SETTINGS) })
                Divider()
                LinkRow("Android settings", null, onClick = { Launch.settings(context, Settings.ACTION_SETTINGS) })
                Divider()
                UpdateRow()
            }

            DiagnosticsSection()
        }
    }
}

/** Shows how the clock was last corrected; tap to take a fresh reading. */
@Composable
private fun TimeSyncRow(internetTime: Boolean) {
    val context = LocalContext.current
    val sync by TimeSync.state.collectAsState()
    // Re-read the "x min ago" text once a minute.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            tick++
        }
    }
    val value = remember(sync, tick, internetTime) {
        val at = sync.syncedAt
        if (sync.source == null || at == null) {
            if (internetTime) "Waiting for GPS or internet" else "Waiting for GPS"
        } else {
            val minutes = (SystemClock.elapsedRealtime() - at) / 60_000L
            val ago = if (minutes < 1) "just now" else "$minutes min ago"
            "${sync.source} · $ago"
        }
    }
    LinkRow("Time sync", value, onClick = { TimeSync.syncNow(context) })
}

/** Import, replace or remove the offline map file, with progress. */
@Composable
private fun OfflineMapRow(prefs: Prefs, onPickFile: (FileKind) -> Unit) {
    val context = LocalContext.current
    val mapVersion by prefs.mapVersion.collectAsState()
    val state by Imports.map.collectAsState()
    var removed by remember { mutableStateOf(0) }
    val installed = remember(mapVersion, removed) { MapStore.isInstalled(context) }
    val size = remember(mapVersion, removed) { MapStore.sizeMb(context) }

    val value = when (val s = state) {
        is ImportState.Copying -> s.progress?.let { "Copying ${(it * 100).toInt()}%" } ?: "Copying…"
        is ImportState.Error -> "${s.message} · Try again"
        ImportState.Idle -> if (installed) "Installed · $size MB · Replace" else "Not installed · Import"
    }
    val busy = state is ImportState.Copying
    LinkRow("Map file", value, onClick = if (busy) null else {
        { onPickFile(FileKind.Map) }
    })
    if (installed && !busy) {
        Divider()
        LinkRow("Remove map", null, onClick = {
            MapStore.remove(context)
            removed++
            prefs.bumpMap()
        })
    }
}

/** What the GPS receiver is doing: useful when the map or clock takes long to start. */
@Composable
private fun GpsDiagnostics() {
    val gps by GpsTracker.state.collectAsState()
    val value = when {
        gps.hasFix -> "Fix · using ${gps.satellitesUsed} of ${gps.satellitesSeen} satellites"
        gps.satellitesSeen > 0 -> "Searching · hears ${gps.satellitesSeen} satellites"
        else -> "Searching · no satellites heard yet"
    }
    LinkRow("GPS", value, onClick = null)
}

/** The last error Car Home hit, kept on the unit so it can be photographed and fixed. */
@Composable
private fun DiagnosticsSection() {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val log = remember(version) { CrashLog.read(context) }
    var expanded by remember { mutableStateOf(false) }

    Section("Diagnostics", footer = "Stays on the unit. A photo of this helps fix problems.") {
        GpsDiagnostics()
        Divider()
        if (log == null) {
            LinkRow("Last error", "None", onClick = null)
        } else {
            LinkRow("Last error", if (expanded) "Hide" else "Show", onClick = { expanded = !expanded })
            if (expanded) {
                Divider()
                Text(
                    log,
                    style = Type.body.copy(fontSize = 12.sp, lineHeight = 16.sp, color = Fg),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                Divider()
                LinkRow("Clear", null, onClick = {
                    CrashLog.clear(context)
                    expanded = false
                    version++
                })
            }
        }
    }
}

/** What the "Software update" row is doing right now. */
private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val version: String, val url: String) : UpdateUi
    data class Downloading(val version: String, val progress: Float) : UpdateUi
    data class Ready(val version: String, val file: File) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

/**
 * One row that walks through the whole update:
 * Check for updates → Version X available → Downloading → Install.
 */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentVersion(context) }
    var state by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    val check: () -> Unit = {
        state = UpdateUi.Checking
        scope.launch {
            state = when (val result = Updater.check(context)) {
                is Updater.Check.Available -> UpdateUi.Available(result.version, result.url)
                is Updater.Check.UpToDate -> UpdateUi.UpToDate
                is Updater.Check.Failed -> UpdateUi.Failed(result.message)
            }
        }
    }

    val s = state
    val value: String
    val onClick: (() -> Unit)?
    when (s) {
        UpdateUi.Idle -> {
            value = "Version $current"
            onClick = check
        }
        UpdateUi.Checking -> {
            value = "Checking…"
            onClick = null
        }
        UpdateUi.UpToDate -> {
            value = "Up to date ($current)"
            onClick = check
        }
        is UpdateUi.Available -> {
            value = "Version ${s.version} available · Download"
            onClick = {
                state = UpdateUi.Downloading(s.version, 0f)
                scope.launch {
                    val file = Updater.download(context, s.url) { p -> state = UpdateUi.Downloading(s.version, p) }
                    state = if (file != null) {
                        Updater.install(context, file)
                        UpdateUi.Ready(s.version, file)
                    } else {
                        UpdateUi.Failed("Download failed")
                    }
                }
            }
        }
        is UpdateUi.Downloading -> {
            value = "Downloading ${(s.progress * 100).toInt()}%"
            onClick = null
        }
        is UpdateUi.Ready -> {
            value = "Install version ${s.version}"
            onClick = { Updater.install(context, s.file) }
        }
        is UpdateUi.Failed -> {
            value = "${s.message} · Try again"
            onClick = check
        }
    }

    LinkRow("Software update", value, onClick = onClick)
}

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = Type.sectionHeader, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        GlassSurface(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        if (footer != null) {
            Text(footer, style = Type.body, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (leading != null) 72.dp else 64.dp)
            .then(if (onClick != null) Modifier.rowPress(onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Text(title, style = Type.rowTitle, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = Type.body, maxLines = 1)
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted, modifier = Modifier.size(24.dp))
        }
    }
}

/** A row with a small two-or-more-way choice on the right, like an iOS segmented control. */
@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.rowTitle, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .clip(Pill)
                .background(Fg.copy(alpha = 0.08f))
                .padding(4.dp),
        ) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                Box(
                    Modifier
                        .size(96.dp, 40.dp)
                        .clip(Pill)
                        .background(if (active) Fg.copy(alpha = 0.16f) else Color.Transparent)
                        .rowPress { if (!active) onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = if (active) Type.tabActive else Type.tabInactive)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.rowTitle, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            // Change first, then tick: turning sounds on ticks, turning them off is silent.
            onCheckedChange = { onChange(it); ClickSound.play() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, // iOS: white knob on green, in both modes
                checkedTrackColor = SwitchGreen,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Fg.copy(alpha = 0.18f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Fg.copy(alpha = 0.10f))
    )
}
