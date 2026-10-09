package com.ardyn.wavetop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.model.RadarMath
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.ui.theme.Wt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val RingsDbm = listOf(-50, -70, -90)
private const val LABELLED = 6

private class RadarDot(val device: TrackedDevice, val offset: Offset)

/**
 * Signal radar: you're at the centre and each device sits as far out as its signal is weak.
 * A phone can't measure bearing, so the angle only keeps each dot in a fixed place.
 */
@Composable
fun RadarView(
    devices: List<TrackedDevice>,
    highlightKey: String?,
    sweeping: Boolean,
    nowMs: Long,
    showLabels: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Wt.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.5.sp, color = c.muted)
    val ringStyle = TextStyle(fontSize = 9.sp, color = c.faint)
    val plotted = remember(devices) { devices.filter { it.rssi != null } }
    val labelled = remember(plotted, nowMs, showLabels) {
        if (!showLabels) emptySet() else plotted.filter { nowMs - it.lastSeenMs <= STALE_MS }
            .sortedByDescending { it.rssi }.take(LABELLED).map { it.key }.toSet()
    }
    // Read only inside the Canvas so each animation frame redraws without recomposing.
    val sweep: State<Float>? = if (sweeping) {
        rememberInfiniteTransition(label = "radar").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
    } else {
        null
    }
    val layout = remember { arrayOf<List<RadarDot>>(emptyList()) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    var cluster by remember { mutableStateOf<List<TrackedDevice>>(emptyList()) }
    val ap = c.nodeAp
    val bt = c.nodeBluetooth
    val danger = c.danger

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(14.dp)
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        // Everything under the finger: one opens directly, several open the picker.
                        val hits = layout[0].filter { hypot(it.offset.x - tap.x, it.offset.y - tap.y) <= 22.dp.toPx() }.map { it.device }
                        when (hits.size) {
                            0 -> Unit
                            1 -> currentOnSelect(hits.single().key)
                            else -> cluster = hits
                        }
                    }
                },
        ) {
            val radius = min(size.width, size.height) / 2f - 4.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(c.panel, radius, center)
            drawLine(c.line, center.copy(x = center.x - radius), center.copy(x = center.x + radius))
            drawLine(c.line, center.copy(y = center.y - radius), center.copy(y = center.y + radius))
            RingsDbm.forEach { dbm ->
                val r = RadarMath.radiusFraction(dbm) * radius
                drawCircle(c.line, r, center, style = Stroke(1.dp.toPx()))
                drawText(measurer, "$dbm dBm", center + Offset(5.dp.toPx(), -r + 2.dp.toPx()), ringStyle)
            }
            drawCircle(c.lineStrong, radius, center, style = Stroke(1.5.dp.toPx()))

            sweep?.value?.let { deg ->
                rotate(deg, center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.9f to Color.Transparent,
                            1.0f to c.accent.copy(alpha = 0.25f),
                            center = center,
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = center - Offset(radius, radius),
                        size = Size(radius * 2, radius * 2),
                    )
                }
            }

            val dots = plotted.map { d ->
                val r = RadarMath.radiusFraction(d.rssi!!) * radius
                val a = Math.toRadians(RadarMath.angleDegrees(d.key).toDouble())
                RadarDot(d, Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
            }
            layout[0] = dots
            val s = 6.dp.toPx()
            dots.sortedByDescending { nowMs - it.device.lastSeenMs }.forEach { dot ->
                val stale = nowMs - dot.device.lastSeenMs > STALE_MS
                val color = (if (dot.device.phy == Phy.Bluetooth) bt else ap).copy(alpha = if (stale) 0.4f else 1f)
                if (dot.device.phy == Phy.Wifi && dot.device.crypto.startsWith("Open")) {
                    drawCircle(danger, s + 3.dp.toPx(), dot.offset, style = Stroke(2.dp.toPx()))
                }
                drawCircle(color, s, dot.offset)
                drawCircle(Color.White, s, dot.offset, style = Stroke(1.5.dp.toPx()))
                if (dot.device.key == highlightKey) drawCircle(c.select, s * 2.1f, dot.offset, style = Stroke(2.5.dp.toPx()))
            }
            dots.filter { it.device.key in labelled || it.device.key == highlightKey }.forEach { dot ->
                drawText(
                    measurer,
                    dot.device.name.take(18),
                    dot.offset + Offset(10.dp.toPx(), -8.dp.toPx()),
                    if (dot.device.key == highlightKey) labelStyle.copy(color = c.text) else labelStyle,
                )
            }

            // You, in NetSeer's operator colour.
            drawCircle(Color(0xFFFF5A3C), 5.dp.toPx(), center)
            drawCircle(Color.White, 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
        Text(
            "Closer to the centre means a stronger signal. A phone can't tell direction, so the angle is just a fixed spot per device.",
            fontSize = 11.5.sp,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
    }
    if (cluster.isNotEmpty()) {
        ClusterPicker(
            devices = cluster,
            onPick = {
                cluster = emptyList()
                onSelect(it.key)
            },
            onDismiss = { cluster = emptyList() },
        )
    }
}
