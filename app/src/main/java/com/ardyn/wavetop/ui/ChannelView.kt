package com.ardyn.wavetop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ardyn.wavetop.model.ChannelBar
import com.ardyn.wavetop.model.ChannelUsage
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.model.WifiBand
import com.ardyn.wavetop.ui.theme.Wt
import kotlin.math.max

/** Narrowest a channel column may get before the chart scrolls instead (a busy 5 GHz band). */
private val MinSlot = 24.dp

/**
 * Access points per channel for one band, coloured by the strongest signal heard there.
 * Crowded channels are the ones to avoid.
 */
@Composable
fun ChannelsScreen(devices: List<TrackedDevice>, band: WifiBand, onBand: (WifiBand) -> Unit) {
    val bars = remember(devices, band) { ChannelUsage.bars(devices, band) }
    Column(Modifier.fillMaxSize()) {
        Toolbar { Segmented(WifiBand.entries, band, { it.label }, onBand) }
        // No early return here: returning out of an inline composable lambda unbalances Compose's
        // group stack and crashes on recomposition.
        if (bars.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BarChart,
                title = "Nothing on ${band.label} yet",
                body = "No access points have been heard on this band.",
                modifier = Modifier.weight(1f),
            )
        } else {
            ChannelChart(bars)
        }
    }
}

@Composable
private fun ColumnScope.ChannelChart(bars: List<ChannelBar>) {
    val c = Wt.colors
    val measurer = rememberTextMeasurer()
    val countStyle = TextStyle(fontSize = 11.sp, color = c.text)
    val axisStyle = TextStyle(fontSize = 10.sp, color = c.muted)
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(12.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.line, shape)
            .padding(12.dp),
    ) {
        // Spread the bars across the card; only scroll when they can't fit.
        val slot = maxOf(MinSlot, maxWidth / bars.size)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            BarsCanvas(bars, slot.value, measurer, countStyle, axisStyle, Modifier.width(slot * bars.size).fillMaxHeight())
        }
    }
    Hint(
        "Access points per channel. Bar colour is the strongest signal on that channel.",
        Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
    )
}

@Composable
private fun BarsCanvas(
    bars: List<ChannelBar>,
    slotDp: Float,
    measurer: TextMeasurer,
    countStyle: TextStyle,
    axisStyle: TextStyle,
    modifier: Modifier,
) {
    val c = Wt.colors
    val good = c.good
    val fair = c.fair
    val weak = c.weak
    Canvas(modifier) {
        val maxCount = max(1, bars.maxOf { it.count })
        val axisH = 18.dp.toPx()
        val topPad = 18.dp.toPx()
        val plotH = size.height - axisH - topPad
        val slotPx = slotDp * density
        val barW = slotPx * 0.62f
        drawLine(c.line, Offset(0f, topPad + plotH), Offset(size.width, topPad + plotH), strokeWidth = 1.dp.toPx())
        bars.forEachIndexed { i, bar ->
            val x = i * slotPx + (slotPx - barW) / 2f
            if (bar.count > 0) {
                val h = plotH * bar.count / maxCount
                val color = bar.strongestDbm?.let {
                    when {
                        it >= -55 -> good
                        it >= -70 -> fair
                        else -> weak
                    }
                } ?: c.faint
                drawRoundRect(
                    color,
                    Offset(x, topPad + plotH - h),
                    Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
                val count = measurer.measure("${bar.count}", countStyle)
                drawText(count, topLeft = Offset(x + (barW - count.size.width) / 2f, topPad + plotH - h - count.size.height - 2f))
            }
            val label = measurer.measure("${bar.channel}", axisStyle)
            drawText(label, topLeft = Offset(x + (barW - label.size.width) / 2f, topPad + plotH + 4.dp.toPx()))
        }
    }
}

