package com.homeos.bridge.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.AppState
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.ui.icons.HomeOsIcons

/**
 * Dev mode: the console the app used to be.
 *
 * Deliberately unstyled — no paper, no plates, no glass, mono type on the raised shell. It should
 * read as a tool rather than as a screen of the product, so that nobody mistakes it for one and so
 * that what it shows is never shaped by the design. Everything the old single-screen app could do
 * is here, plus a raw inspector for the last Health Connect batch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevScreen(
    state: AppState,
    serverUrl: String,
    healthConnectAvailable: Boolean,
    lastSync: String,
    busy: Boolean,
    onSetServer: (String) -> Unit,
    onSwitchPerson: (String) -> Unit,
    onSyncNow: () -> Unit,
    onBackfill: () -> Unit,
    onRequestPermissions: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    // Seeded from prefs; edits stay local until Save, so a half-typed address never takes effect.
    var serverField by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }
    val report = remember(lastSync) { parseSyncSummary(lastSync) }

    // Dev mode covers the chrome, so it owns its own system-bar insets.
    val statusBars = WindowInsets.statusBars.asPaddingValues()
    val navBars = WindowInsets.navigationBars.asPaddingValues()

    Column(
        modifier
            .fillMaxSize()
            .background(colors.shellRaised)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = statusBars.calculateTopPadding() + 12.dp,
                bottom = navBars.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                HomeOsIcons.terminal,
                contentDescription = null,
                tint = colors.onShellMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("DEV MODE", style = type.micro, color = colors.onShellMuted)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(HomeOsIcons.close, contentDescription = "Close dev mode", tint = colors.onShell)
            }
        }

        DevSection("Link")
        KeyValue("status", if (state.linked) "linked" else "not linked")
        KeyValue("reachable", if (state.reachable) "yes" else "no")
        KeyValue("acting as", state.alias.ifEmpty { "(none)" })
        state.lastError?.let { KeyValue("last error", it) }

        OutlinedTextField(
            value = serverField,
            onValueChange = { serverField = it },
            label = { Text("Server", style = type.mono) },
            singleLine = true,
            textStyle = type.mono,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSetServer(serverField.trim().trimEnd('/')) }) {
                Text("Save server", style = type.mono)
            }
        }

        DevSection("Person")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.home.people.forEach { person ->
                val selected = person.alias == state.alias
                OutlinedButton(onClick = { onSwitchPerson(person.alias) }) {
                    Text(
                        text = person.alias + if (person.isDataset) " (dataset)" else "",
                        style = type.mono,
                        color = if (selected) colors.teal else colors.onShellMuted,
                    )
                }
            }
        }

        DevSection("Health Connect")
        KeyValue("available", if (healthConnectAvailable) "yes" else "no")
        KeyValue("permissions", report.permissions ?: "unknown")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = onSyncNow) { Text("Sync now", style = type.mono) }
            OutlinedButton(enabled = !busy, onClick = onBackfill) { Text("Backfill 120 d", style = type.mono) }
        }
        OutlinedButton(onClick = onRequestPermissions) {
            Text("Request permissions", style = type.mono)
        }

        DevSection("Last batch")
        if (report.counts.isEmpty()) {
            Text(lastSync, style = type.mono, color = colors.onShellMuted)
        } else {
            report.counts.forEach { (label, value) -> KeyValue(label, value) }
            report.origins?.let { KeyValue("origins", it) }
            report.window?.let { KeyValue("window", it) }
        }
        report.errors?.let {
            Text("errors", style = type.micro, color = colors.red)
            Text(it, style = type.mono, color = colors.red)
        }
        report.server?.let {
            Text("server replied", style = type.micro, color = colors.onShellMuted)
            Text(it, style = type.mono, color = colors.onShell)
        }

        DevSection("Devices")
        state.home.devices.forEach { device ->
            KeyValue(
                device.id,
                buildList {
                    device.power?.let { add("power=$it") }
                    device.setpoint?.let { add("setpoint=$it") }
                    device.level?.let { add("level=$it") }
                    device.brightness?.let { add("brightness=$it") }
                    device.temperature?.let { add("t=$it") }
                    device.humidity?.let { add("rh=$it") }
                    device.bright?.let { add("lux=$it") }
                    device.leasedBy?.let { add("leased=$it") }
                }.joinToString(" "),
            )
        }

        DevSection("Log")
        state.log.forEach { line ->
            Text(line, style = type.monoMicro, color = colors.onShellMuted)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DevSection(title: String) {
    val colors = HomeOs.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(4.dp))
        Text(title.uppercase(), style = HomeOs.type.micro, color = colors.teal)
        HorizontalDivider(color = colors.onShellMuted.copy(alpha = 0.25f))
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    val colors = HomeOs.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            key,
            style = HomeOs.type.mono,
            color = colors.onShellMuted,
            modifier = Modifier.width(118.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(value, style = HomeOs.type.mono, color = colors.onShell, modifier = Modifier.weight(1f))
    }
}

/** The structured view of the last batch, pulled out of `HealthSync.sync`'s summary line. */
internal data class SyncReport(
    val counts: List<Pair<String, String>> = emptyList(),
    val origins: String? = null,
    val window: String? = null,
    val permissions: String? = null,
    val errors: String? = null,
    val server: String? = null,
)

/**
 * `HealthSync.sync` returns a human summary rather than a structured result, and that file is left
 * exactly as the previous session wrote it (it carries the menstruation reader), so the inspector
 * parses the summary instead of changing the reader.
 *
 * Shape being parsed:
 * `Read 0 nights, 48 HR samples, ... over 2 d (from com.gabitrn) · 10 permissions`
 * then optional `Errors: ...` and `Server: ...` lines.
 */
internal fun parseSyncSummary(summary: String): SyncReport {
    if (summary.isBlank() || summary == "never") return SyncReport()
    val lines = summary.lines()
    val head = lines.firstOrNull().orEmpty()
    if (!head.startsWith("Read ")) {
        return SyncReport(
            errors = lines.firstOrNull { it.startsWith("Errors:") }?.removePrefix("Errors:")?.trim(),
            server = lines.firstOrNull { it.startsWith("Server:") }?.removePrefix("Server:")?.trim(),
        )
    }

    val permissions = head.substringAfterLast("· ", "").trim().takeIf { it.isNotEmpty() }
    val beforePermissions = head.substringBeforeLast("· ")
    val origins = Regex("""\(from ([^)]*)\)""").find(beforePermissions)?.groupValues?.get(1)
    val beforeOrigins = beforePermissions.substringBefore(" (from ")
    val window = Regex("""over (.+)$""").find(beforeOrigins.trim())?.groupValues?.get(1)?.trim()
    val countsPart = beforeOrigins.removePrefix("Read ").substringBefore(" over ")

    val counts = countsPart.split(", ").mapNotNull { item ->
        val trimmed = item.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val value = trimmed.substringBefore(' ')
        val label = trimmed.substringAfter(' ', "").ifEmpty { return@mapNotNull null }
        label to value
    }

    return SyncReport(
        counts = counts,
        origins = origins,
        window = window,
        permissions = permissions,
        errors = lines.firstOrNull { it.startsWith("Errors:") }?.removePrefix("Errors:")?.trim(),
        server = lines.firstOrNull { it.startsWith("Server:") }?.removePrefix("Server:")?.trim(),
    )
}
