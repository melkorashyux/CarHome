package dev.carhome.launcher.ui.settings

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.carhome.launcher.data.AppSlot
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.media.MediaRepository
import dev.carhome.launcher.ui.apps.ScreenHeader
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.LocalBackdrop
import dev.carhome.launcher.ui.glass.Rounded8
import dev.carhome.launcher.ui.glass.rowPress
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.Muted
import dev.carhome.launcher.ui.theme.Type
import dev.carhome.launcher.util.Launch
import dev.carhome.launcher.wallpaper.Wallpaper
import kotlinx.coroutines.launch

private val SwitchGreen = Color(0xFF34C759) // iOS "on" green

/** Grouped settings list in the style of iOS Settings, on the same glass. */
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onPick: (AppSlot) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glass by prefs.glass.collectAsState()
    val use24h by prefs.use24h.collectAsState()
    val autoSwitch by prefs.autoSwitch.collectAsState()
    val media by MediaRepository.state.collectAsState()
    val wallpaper = LocalBackdrop.current.wallpaper

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            if (Wallpaper.importFrom(context, uri)) prefs.bumpWallpaper()
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Settings", onBack)
        Spacer(Modifier.height(24.dp))

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Display") {
                LinkRow("Wallpaper", "Choose photo", onClick = { pickPhoto.launch("image/*") }) {
                    if (wallpaper != null) {
                        Image(wallpaper, null, Modifier.size(96.dp, 56.dp).clip(Rounded8), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(96.dp, 56.dp).clip(Rounded8).background(Color.White.copy(alpha = 0.08f)))
                    }
                }
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                }
                Divider()
                SwitchRow("24-hour clock", use24h, prefs::setUse24h)
            }

            Section("Home") {
                SwitchRow("Switch tabs when music or directions start", autoSwitch, prefs::setAutoSwitch)
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
                val version = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    null
                }
                LinkRow("Version", version, onClick = null)
            }
        }
    }
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

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Type.rowTitle, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Fg,
                checkedTrackColor = SwitchGreen,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Fg,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
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
            .background(Color.White.copy(alpha = 0.08f))
    )
}
