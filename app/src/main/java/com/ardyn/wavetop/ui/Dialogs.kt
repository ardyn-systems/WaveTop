package com.ardyn.wavetop.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.drive.ExportFormat
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.prefs.NetSeerLink
import com.ardyn.wavetop.ui.theme.MonoStyle
import com.ardyn.wavetop.ui.theme.Wt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One look for every dialog: panel colour, 16dp corners, title in the text colour. */
@Composable
fun WtDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = Wt.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = confirm,
        dismissButton = dismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = content,
        containerColor = c.panel,
        titleContentColor = c.text,
        textContentColor = c.muted,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
fun WtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val c = Wt.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.faint) } },
        singleLine = true,
        textStyle = if (mono) MonoStyle.copy(fontSize = 15.sp, color = c.text) else MaterialTheme.typography.bodyLarge.copy(color = c.text),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.lineStrong,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.muted,
            cursorColor = c.accent,
            focusedContainerColor = c.raised,
            unfocusedContainerColor = c.raised,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Asks for the wardrive's name up front; it's saved under that name when stopped. */
@Composable
fun WardriveStartDialog(onStart: (String) -> Unit, onDismiss: () -> Unit) {
    val default = remember { "Drive " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()) }
    var name by remember { mutableStateOf(default) }
    WtDialog(
        title = "Start a wardrive",
        onDismiss = onDismiss,
        confirm = { WtButton("Start", { onStart(name) }, kind = BtnKind.Primary) },
        dismiss = { WtButton("Cancel", onDismiss, kind = BtnKind.Ghost) },
    ) {
        Column {
            WtTextField(name, { name = it.take(80) }, "Save as", onDone = { onStart(name) })
            Spacer(Modifier.height(10.dp))
            Hint(
                "Records every device with the time and GPS position it was heard. It keeps going with " +
                    "the screen off and saves under this name when you stop. Some phones pause Bluetooth LE " +
                    "scanning while the screen is off.",
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    WtDialog(
        title = title,
        onDismiss = onDismiss,
        confirm = { WtButton(confirm, onConfirm, kind = if (danger) BtnKind.Danger else BtnKind.Primary) },
        dismiss = { WtButton("Cancel", onDismiss, kind = BtnKind.Ghost) },
    ) { Text(body, style = MaterialTheme.typography.bodyMedium) }
}

/** One tap to send the open drive to the paired NetSeer, or a pointer to pair first. */
@Composable
fun SendToNetSeerDialog(
    paired: NetSeerLink?,
    task: TaskStatus,
    onSend: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Wt.colors
    val busy = task is TaskStatus.Working
    WtDialog(
        title = "Send to NetSeer",
        onDismiss = onDismiss,
        confirm = {
            if (paired == null) WtButton("Pair with NetSeer", onOpenSettings, kind = BtnKind.Primary)
            else WtButton(if (task is TaskStatus.Done) "Send again" else "Send", onSend, kind = BtnKind.Primary, enabled = !busy)
        },
        dismiss = { WtButton("Close", onDismiss, kind = BtnKind.Ghost) },
    ) {
        Column {
            if (paired == null) {
                Text(
                    "WaveTop isn't paired with a NetSeer yet. Pairing takes a minute: open NetSeer's " +
                        "Settings → Integrations → Pair a device, then type the code it shows into WaveTop.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Sends this drive's Wi-Fi and Bluetooth sightings, with their GPS positions, to NetSeer at " +
                        "${paired.baseUrl}. NetSeer maps it and estimates where each device is.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusLine(task)
            if (task is TaskStatus.Failed && paired != null) {
                Text(
                    "NetSeer settings",
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp).clickable(onClick = onOpenSettings),
                )
            }
        }
    }
}

@Composable
fun ShareDialog(onPick: (ExportFormat) -> Unit, onDismiss: () -> Unit) {
    val c = Wt.colors
    WtDialog(
        title = "Share this drive as…",
        onDismiss = onDismiss,
        confirm = { WtButton("Cancel", onDismiss, kind = BtnKind.Ghost) },
    ) {
        Column {
            ExportFormat.entries.forEach { format ->
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(shape)
                        .border(1.dp, c.line, shape)
                        .clickable { onPick(format) }
                        .padding(12.dp),
                ) {
                    Text(format.label, fontWeight = FontWeight.SemiBold, color = c.text)
                    Hint(
                        when (format) {
                            ExportFormat.WigleCsv -> "Every geotagged Wi-Fi and Bluetooth sighting. Opens in NetSeer; uploads to wigle.net."
                            ExportFormat.KismetNetxml -> "Wi-Fi networks with GPS, for Kismet tools."
                            ExportFormat.WaveTopCsv -> "The full recording, every sighting, as saved on the phone."
                        },
                    )
                }
            }
        }
    }
}

/**
 * Several devices under one tap (they share a spot, or overlap on the radar): list them so
 * it's clear which one you're opening. [onZoom] is offered where zooming would pull them apart.
 */
@Composable
fun ClusterPicker(
    devices: List<TrackedDevice>,
    onPick: (TrackedDevice) -> Unit,
    onDismiss: () -> Unit,
    onZoom: (() -> Unit)? = null,
) {
    val c = Wt.colors
    val sorted = remember(devices) { devices.sortedByDescending { it.rssi ?: Int.MIN_VALUE } }
    WtDialog(
        title = "${devices.size} devices here",
        onDismiss = onDismiss,
        confirm = { if (onZoom != null) WtButton("Zoom in", onZoom) },
        dismiss = { WtButton("Close", onDismiss, kind = BtnKind.Ghost) },
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
            items(sorted, key = { it.key }) { d ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(d) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KindBadge(d, size = 30)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${d.mac} · ${shortCrypto(d)}", style = MonoStyle.copy(fontSize = 11.sp), color = c.muted, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(d.rssi?.toString() ?: "—", fontWeight = FontWeight.Bold, color = d.rssi?.let { signalColor(it) } ?: c.faint)
                }
            }
        }
    }
}

