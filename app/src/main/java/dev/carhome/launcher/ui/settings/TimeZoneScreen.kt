package dev.carhome.launcher.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.carhome.launcher.data.Prefs
import dev.carhome.launcher.ui.common.ContentTop
import dev.carhome.launcher.ui.apps.ScreenHeader
import dev.carhome.launcher.ui.glass.GlassSurface
import dev.carhome.launcher.ui.glass.rowPress
import dev.carhome.launcher.ui.theme.Fg
import dev.carhome.launcher.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private data class ZoneRow(val id: String?, val title: String, val detail: String)

/** Pick a time zone, or "Automatic" to follow the unit. Egypt is Africa/Cairo. */
@Composable
fun TimeZoneScreen(prefs: Prefs, onBack: () -> Unit) {
    val selected by prefs.timeZone.collectAsState()

    val rows = remember {
        val now = Instant.now()
        val automatic = ZoneRow(null, "Automatic", "Follow the unit (${ZoneId.systemDefault().id})")
        val zones = ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .sorted()
            .map { id ->
                val zone = ZoneId.of(id)
                val offset = zone.rules.getOffset(now).id.let { if (it == "Z") "+00:00" else it }
                ZoneRow(id, id.replace('_', ' '), "GMT$offset · ${zone.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}")
            }
        listOf(automatic) + zones
    }

    // Open scrolled to the current choice.
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = rows.indexOfFirst { it.id == selected }
        if (index > 0) listState.scrollToItem(index - 1)
    }

    // Starts below the clock bar, like the home screen.
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = ContentTop, bottom = 24.dp)) {
        ScreenHeader("Time zone", onBack)
        Spacer(Modifier.height(24.dp))
        GlassSurface(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.id ?: "auto" }) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .rowPress {
                                prefs.setTimeZone(row.id)
                                onBack()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = Type.rowTitle)
                            Text(row.detail, style = Type.body, maxLines = 1)
                        }
                        if (row.id == selected) {
                            Icon(Icons.Rounded.Check, "Selected", tint = Fg, modifier = Modifier.size(24.dp))
                        }
                    }
                    Box(
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}
