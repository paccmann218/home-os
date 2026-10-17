package com.homeos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeos.bridge.data.Decision
import com.homeos.bridge.data.Device
import com.homeos.bridge.theme.HomeOs
import com.homeos.bridge.theme.HomeOsDims
import com.homeos.bridge.theme.HomeOsShapes
import com.homeos.bridge.theme.Plate
import com.homeos.bridge.theme.ink
import com.homeos.bridge.ui.icons.HomeOsIcons

/** An appliance kind HomeOS knows how to drive, whether or not one is connected yet. */
@Immutable
data class ApplianceKind(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val how: String,
)

/**
 * Everything the home can drive.
 *
 * Kept in the app rather than fetched, because it is a catalogue of what HomeOS *supports*, not of
 * what happens to be plugged in — which is exactly what someone wants to see when they are
 * deciding whether to add something.
 */
val APPLIANCE_CATALOGUE = listOf(
    ApplianceKind("light", "Light", HomeOsIcons.lightbulb, "WiZ over Wi-Fi, or an ESP32 LED"),
    ApplianceKind("fan", "Fan", HomeOsIcons.modeFan, "IR blaster or a smart plug"),
    ApplianceKind("ac", "Air conditioner", HomeOsIcons.acUnit, "IR blaster"),
    ApplianceKind("curtain", "Curtains", HomeOsIcons.curtains, "Motorised rail over the ESP32"),
    ApplianceKind("heater", "Heater", HomeOsIcons.modeHeat, "Smart plug"),
    ApplianceKind("purifier", "Air purifier", HomeOsIcons.airPurifierGen, "Smart plug"),
    ApplianceKind("humidifier", "Humidifier", HomeOsIcons.humidityHigh, "Smart plug"),
    ApplianceKind("sensor", "Room sensor", HomeOsIcons.sensors, "ESP32 with DHT11 and a light sensor"),
)

fun applianceIcon(kind: String): ImageVector = when (kind) {
    "ac" -> HomeOsIcons.acUnit
    "fan" -> HomeOsIcons.modeFan
    "light" -> HomeOsIcons.lightbulb
    "curtain" -> HomeOsIcons.curtains
    "heater" -> HomeOsIcons.modeHeat
    "purifier" -> HomeOsIcons.airPurifierGen
    "humidifier" -> HomeOsIcons.humidityHigh
    "sensor" -> HomeOsIcons.sensors
    else -> HomeOsIcons.devicesOther
}

/** The one-line state a tile shows. */
private fun Device.readout(): String = when {
    isSensor -> temperature?.let { "%.1f °C".format(it) } ?: "reporting"
    !isOn -> "off"
    kind == "ac" && setpoint != null -> "$setpoint °C"
    kind == "fan" && level != null -> "level $level"
    kind == "light" && brightness != null -> "$brightness%"
    else -> "on"
}

/**
 * The appliances strip: everything in the room, scrollable sideways.
 *
 * These tiles report rather than switch, and that is the product working as designed: in HomeOS a
 * person says how they feel and the agents decide what the room does, so a manual toggle here would
 * quietly contradict the thing the whole system is for. Tapping a tile opens what it is doing and
 * why. (The backend has no device-write route either — it is driven entirely through `/api/chip`.)
 */
@Composable
fun AppliancePanel(
    devices: List<Device>,
    onOpenDevice: (Device) -> Unit,
    onAddAppliance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = HomeOsDims.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(devices, key = { it.id }) { device ->
            ApplianceTile(device = device, onClick = { onOpenDevice(device) })
        }
        item(key = "add") {
            AddApplianceTile(onClick = onAddAppliance)
        }
    }
}

@Composable
private fun ApplianceTile(device: Device, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val live = device.isOn || device.isSensor
    val plate = when {
        device.leasedBy != null -> Plate.Attention
        live -> Plate.Home
        else -> Plate.None
    }

    PaperBlock(
        modifier = modifier.width(132.dp),
        plate = plate,
        onClick = onClick,
        padding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        if (live) plate.ink(colors).copy(alpha = 0.14f) else colors.paperSunk,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    applianceIcon(device.kind),
                    contentDescription = null,
                    tint = if (live) plate.ink(colors) else colors.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (device.leasedBy != null) {
                Icon(
                    HomeOsIcons.lock,
                    contentDescription = "held by ${device.leasedBy}",
                    tint = colors.amberInk,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            device.name,
            style = type.label,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            device.readout(),
            style = type.caption,
            color = if (live) plate.ink(colors) else colors.inkMuted,
        )
    }
}

@Composable
private fun AddApplianceTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val dash = colors.onShellMuted.copy(alpha = 0.55f)

    Column(
        modifier
            .width(132.dp)
            .height(140.dp)
            .clip(HomeOsShapes.block)
            .clickable(onClick = onClick)
            // Dashed rather than solid: this is a slot, not an object, and a broken outline is the
            // oldest and clearest way to say so. It stays on the shell with no paper fill, so it
            // reads as an absence rather than as another block.
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    style = Stroke(
                        width = HomeOsDims.border.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(9.dp.toPx(), 7.dp.toPx()),
                        ),
                    ),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(colors.onShell.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                HomeOsIcons.add,
                contentDescription = null,
                tint = colors.onShell,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text("Add appliance", style = type.label, color = colors.onShell)
        Text("${APPLIANCE_CATALOGUE.size} kinds supported", style = type.caption, color = colors.onShellMuted)
    }
}

/**
 * What one appliance is doing, and why.
 *
 * The "why" is the point. A smart-home app that shows a thermostat at 25 °C and no reason is just a
 * remote control; the reason is the thing HomeOS has that a remote does not.
 */
@Composable
fun DeviceDetail(
    device: Device,
    decision: Decision?,
    onWhy: (Decision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val live = device.isOn || device.isSensor
    val plate = if (live) Plate.Home else Plate.None

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter)) {
        PaperBlock(plate = plate) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(
                            if (live) plate.ink(colors).copy(alpha = 0.14f) else colors.paperSunk,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        applianceIcon(device.kind),
                        contentDescription = null,
                        tint = if (live) plate.ink(colors) else colors.inkMuted,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = type.blockTitle, color = colors.ink)
                    Text(
                        "${device.kind} · ${device.room.ifEmpty { "bedroom" }}",
                        style = type.caption,
                        color = colors.inkMuted,
                    )
                }
                Text(device.readout(), style = type.readout, color = if (live) plate.ink(colors) else colors.inkMuted)
            }
        }

        if (device.leasedBy != null) {
            PaperBlock(plate = Plate.Attention) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        HomeOsIcons.lock,
                        contentDescription = null,
                        tint = colors.amberInk,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text("Held by ${device.leasedBy}", style = type.label, color = colors.ink)
                        Text(
                            "An agent has a lease on this, so nothing else can change it until it is released.",
                            style = type.caption,
                            color = colors.inkMuted,
                        )
                    }
                }
            }
        }

        if (decision != null) {
            PaperBlock(plate = Plate.Home, onClick = { onWhy(decision) }) {
                Text("WHY IT IS LIKE THIS", style = type.micro, color = colors.inkMuted)
                Text(decision.summary, style = type.body, color = colors.ink)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("See the reasoning", style = type.label, color = colors.ink)
                    Icon(
                        HomeOsIcons.arrowOutward,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Text(
            "HomeOS changes appliances through its agents. Tell it how you feel on Check in and it " +
                "works out what the room should do, with a reason you can read.",
            style = type.caption,
            color = colors.onShellMuted,
        )
    }
}

/** What the Add appliance sheet shows: the catalogue, with what is already connected marked. */
@Composable
fun ApplianceCatalogue(
    connected: List<Device>,
    modifier: Modifier = Modifier,
) {
    val colors = HomeOs.colors
    val type = HomeOs.type
    val connectedKinds = connected.map { it.kind }.toSet()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HomeOsDims.gutter)) {
        Text(
            "HomeOS drives appliances through its agents: you say how you feel, and the home works " +
                "out what the room should do. Adding one here tells the home it exists.",
            style = type.body,
            color = colors.onShellMuted,
        )
        APPLIANCE_CATALOGUE.forEach { kind ->
            val already = kind.key in connectedKinds
            PaperBlock(
                plate = if (already) Plate.Good else Plate.None,
                padding = PaddingValues(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(
                                if (already) colors.greenInk.copy(alpha = 0.14f) else colors.paperSunk,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            kind.icon,
                            contentDescription = null,
                            tint = if (already) colors.greenInk else colors.inkMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(kind.label, style = type.blockTitle, color = colors.ink)
                        Text(kind.how, style = type.caption, color = colors.inkMuted)
                    }
                    Text(
                        if (already) "connected" else "not set up",
                        style = type.micro,
                        color = if (already) colors.greenInk else colors.inkMuted,
                    )
                }
            }
        }
    }
}
