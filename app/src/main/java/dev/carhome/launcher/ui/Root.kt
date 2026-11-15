package dev.carhome.launcher.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dev.carhome.launcher.data.AppSlot
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.media.MediaRepository
import dev.carhome.launcher.nav.NavRepository
import dev.carhome.launcher.ui.apps.AppsScreen
import dev.carhome.launcher.ui.glass.Backdrop
import dev.carhome.launcher.ui.glass.LocalBackdrop
import dev.carhome.launcher.ui.home.HomeScreen
import dev.carhome.launcher.ui.home.HomeTab
import dev.carhome.launcher.ui.settings.SettingsScreen
import dev.carhome.launcher.ui.theme.DesignCanvas
import dev.carhome.launcher.ui.theme.Fallback
import dev.carhome.launcher.util.Launch
import dev.carhome.launcher.wallpaper.Wallpaper

sealed interface Screen {
    data object Home : Screen
    data object Apps : Screen
    data object Settings : Screen
    data class PickApp(val slot: AppSlot, val returnTo: Screen) : Screen
}

@Composable
fun CarHomeRoot(prefs: Prefs, screenState: MutableState<Screen>) {
    val context = LocalContext.current
    var screen by screenState

    // Wallpaper: the sharp photo, plus a frosted copy for the glass.
    val wallpaperVersion by prefs.wallpaperVersion.collectAsState()
    val intensity by prefs.glass.collectAsState()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wallpaperVersion) { source = Wallpaper.load(context) }
    val sharp = remember(source) { source?.asImageBitmap() }
    var blurred by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source, intensity) { blurred = source?.let { Wallpaper.blurred(it, intensity) } }
    val tint = 0.10f + 0.20f * intensity // 0.5 → black at 20%, as in Figma

    // Auto-switch tabs when music or navigation starts. Lives here so it keeps
    // working while Apps or Settings is open, and a manual tap holds until the next start.
    val homeTab = remember { mutableStateOf(HomeTab.NowPlaying) }
    val media by MediaRepository.state.collectAsState()
    val nav by NavRepository.state.collectAsState()
    val autoSwitch by prefs.autoSwitch.collectAsState()
    LaunchedEffect(media.playing) { if (autoSwitch && media.playing) homeTab.value = HomeTab.NowPlaying }
    LaunchedEffect(nav.active) { if (autoSwitch && nav.active) homeTab.value = HomeTab.Navigation }

    BackHandler(enabled = screen != Screen.Home) {
        screen = when (val s = screen) {
            is Screen.PickApp -> s.returnTo
            else -> Screen.Home
        }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    DesignCanvas {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.positionInWindow()
                    size = it.size
                }
        ) {
            if (sharp != null) {
                Image(sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Fallback))
            }

            CompositionLocalProvider(LocalBackdrop provides Backdrop(sharp, blurred, tint, origin, size)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(
                            prefs = prefs,
                            tab = homeTab.value,
                            onTab = { homeTab.value = it },
                            onOpenApps = { screen = Screen.Apps },
                            onOpenSettings = { screen = Screen.Settings },
                            onPick = { slot -> screen = Screen.PickApp(slot, Screen.Home) },
                        )

                        Screen.Apps -> AppsScreen(
                            title = "Apps",
                            onBack = { screen = Screen.Home },
                            onPick = { pkg -> Launch.app(context, pkg) },
                        )

                        Screen.Settings -> SettingsScreen(
                            prefs = prefs,
                            onBack = { screen = Screen.Home },
                            onPick = { slot -> screen = Screen.PickApp(slot, Screen.Settings) },
                        )

                        is Screen.PickApp -> AppsScreen(
                            title = "Choose app for ${s.slot.label}",
                            onBack = { screen = s.returnTo },
                            onPick = { pkg ->
                                prefs.setSlot(s.slot, pkg)
                                screen = s.returnTo
                                // Picked from a home button: open it straight away.
                                if (s.returnTo == Screen.Home) Launch.app(context, pkg)
                            },
                        )
                    }
                }
            }
        }
    }
}
