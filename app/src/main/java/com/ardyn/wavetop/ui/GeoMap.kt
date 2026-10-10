package com.ardyn.wavetop.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ardyn.wavetop.model.GeoFix
import com.ardyn.wavetop.model.Phy
import com.ardyn.wavetop.model.RadarMath
import com.ardyn.wavetop.model.TrackedDevice
import com.ardyn.wavetop.ui.theme.MonoStyle
import com.ardyn.wavetop.ui.theme.Wt
import com.ardyn.wavetop.ui.theme.WtColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** NetSeer's operator marker colour (`.geo-operator`). */
private val OperatorColor = Color(0xFFFF5A3C)

/**
 * The street map, after NetSeer's geo map: devices pinned where they were heard loudest, your
 * position (live) or the route driven (saved drive), a legend card, zoom controls at the bottom
 * right, and a pop-up card for whatever you tap. Tiles are tinted to the theme.
 */
@Composable
fun GeoMap(
    devices: List<TrackedDevice>,
    fix: GeoFix?,
    highlightKey: String?,
    focusNonce: Int,
    nowMs: Long,
    basemap: Basemap,
    showLabels: Boolean,
    onDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Live survey: shows you. Saved drive: shows [track] (up to [timeMs]) instead. */
    live: Boolean = true,
    track: List<GeoFix> = emptyList(),
    timeMs: Long? = null,
) {
    val c = Wt.colors
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var popup by remember { mutableStateOf<List<TrackedDevice>>(emptyList()) }
    val overlay = remember { PinsOverlay(context) { hits -> popup = hits } }
    var centered by remember { mutableStateOf(false) }
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            overlays.add(overlay)
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // A saved drive's map can be scrubbed through time: only what had been heard by then.
    val shownTrack = if (timeMs == null) track else track.filter { it.timeMs <= timeMs }
    val pinned = devices.filter { it.bestFix != null && (timeMs == null || it.firstSeenMs <= timeMs) }
    val highlighted = pinned.firstOrNull { it.key == highlightKey }
    val operator = if (live) fix else shownTrack.lastOrNull().takeIf { timeMs != null }

    val initialCenter = highlighted?.bestFix ?: fix ?: track.lastOrNull() ?: pinned.firstOrNull()?.bestFix
    LaunchedEffect(initialCenter != null) {
        if (initialCenter != null && !centered) {
            mapView.controller.setCenter(GeoPoint(initialCenter.lat, initialCenter.lon))
            centered = true
        }
    }
    LaunchedEffect(focusNonce) {
        val target = highlighted?.bestFix ?: return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(target.lat, target.lon), maxOf(mapView.zoomLevelDouble, 18.0), 600L)
        centered = true
    }
    // Fit a saved drive's route the first time its map opens.
    LaunchedEffect(live, track.size) {
        if (!live && track.size >= 2 && highlightKey == null) {
            mapView.post { mapView.zoomToBoundingBox(boundsOf(track.map { GeoPoint(it.lat, it.lon) }), false, dpPx(context, 48)) }
        }
    }

    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                styleTiles(it, c, basemap)
                overlay.colors = c
                overlay.dots = pinned
                overlay.me = operator
                overlay.liveYou = live
                overlay.track = shownTrack
                overlay.highlightKey = highlightKey
                overlay.nowMs = nowMs
                overlay.labels = showLabels
                it.invalidate()
            },
        )

        GeoLegend(live = live, hasRoute = shownTrack.size >= 2, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))

        when {
            live && fix == null -> MapNote(
                "Waiting for GPS. Pins need a fresh, accurate fix; step outside if this takes long.",
                Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
            pinned.isEmpty() -> MapNote(
                if (live) "No pins yet. Keep scanning and move around: each device is pinned where it's loudest."
                else "No sighting in this drive had an accurate enough GPS fix to pin.",
                Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
            highlightKey != null && highlighted == null -> MapNote(
                "That device was never heard with an accurate GPS fix, so it has no position.",
                Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapButton(Icons.Outlined.Add, "Zoom in") { mapView.controller.zoomIn() }
            MapButton(Icons.Outlined.Remove, "Zoom out") { mapView.controller.zoomOut() }
            if (live && fix != null) {
                MapButton(Icons.Outlined.MyLocation, "Centre on me") { mapView.controller.animateTo(GeoPoint(fix.lat, fix.lon)) }
            }
            val extent = track.map { GeoPoint(it.lat, it.lon) } + pinned.mapNotNull { d -> d.bestFix?.let { GeoPoint(it.lat, it.lon) } }
            if (extent.size >= 2) {
                MapButton(Icons.Outlined.FitScreen, "Fit everything") {
                    mapView.zoomToBoundingBox(boundsOf(extent), true, dpPx(context, 48))
                }
            }
        }

        Text(
            "© OpenStreetMap contributors",
            fontSize = 9.5.sp,
            color = c.muted,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.raised.copy(alpha = 0.85f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )

        if (popup.size == 1) {
            DevicePopup(
                device = popup.single(),
                onDetails = {
                    val key = popup.single().key
                    popup = emptyList()
                    onDetails(key)
                },
                onClose = { popup = emptyList() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 64.dp, bottom = 34.dp),
            )
        }
    }

    if (popup.size > 1) {
        val center = popup.mapNotNull { it.bestFix }.let { fixes ->
            GeoPoint(fixes.map { it.lat }.average(), fixes.map { it.lon }.average())
        }
        ClusterPicker(
            devices = popup,
            onPick = {
                popup = emptyList()
                onDetails(it.key)
            },
            onDismiss = { popup = emptyList() },
            onZoom = {
                popup = emptyList()
                mapView.controller.animateTo(center, (mapView.zoomLevelDouble + 3.0).coerceAtMost(mapView.maxZoomLevel), 600L)
            },
        )
    }
}

/** NetSeer's grouped legend card (`.geo-legend`). */
@Composable
private fun GeoLegend(live: Boolean, hasRoute: Boolean, modifier: Modifier) {
    val c = Wt.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            // Only as wide as its longest row (the divider below would otherwise stretch it).
            .width(IntrinsicSize.Max)
            .clip(shape)
            .background(c.raised.copy(alpha = 0.94f))
            .border(1.dp, c.line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LegendRow(dot = c.nodeAp, label = "Access point")
        LegendRow(dot = c.nodeBluetooth, label = "Bluetooth")
        LegendRow(ring = c.danger, label = "Open network")
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(c.line))
        if (live) LegendRow(operator = true, label = "You")
        if (hasRoute) LegendRow(line = c.accent, label = "Route")
        LegendRow(ring = c.select, label = "Selected")
    }
}

@Composable
private fun LegendRow(label: String, dot: Color? = null, ring: Color? = null, line: Color? = null, operator: Boolean = false) {
    val c = Wt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            when {
                dot != null -> Box(Modifier.size(12.dp).clip(CircleShape).background(dot).border(2.dp, Color.White, CircleShape))
                ring != null -> Box(Modifier.size(12.dp).border(2.dp, ring, CircleShape))
                line != null -> Box(Modifier.width(14.dp).height(3.dp).background(line))
                operator -> Box(Modifier.size(10.dp).rotate(45f).clip(RoundedCornerShape(2.dp)).background(OperatorColor).border(1.5.dp, Color.White, RoundedCornerShape(2.dp)))
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.sp, color = c.muted)
    }
}

@Composable
private fun MapNote(text: String, modifier: Modifier) {
    val c = Wt.colors
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        fontSize = 11.5.sp,
        color = c.muted,
        modifier = modifier
            .width(180.dp)
            .clip(shape)
            .background(c.raised.copy(alpha = 0.94f))
            .border(1.dp, c.line, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun MapButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Wt.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .background(c.raised)
            .border(1.dp, c.lineStrong, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = c.text, modifier = Modifier.size(20.dp)) }
}

/** The card for one tapped pin, like NetSeer's `.geo-pop`. */
@Composable
private fun DevicePopup(device: TrackedDevice, onDetails: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val c = Wt.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.lineStrong, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(device, size = 32)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(device.mac, style = MonoStyle.copy(fontSize = 11.sp), color = c.muted)
            }
            Icon(Icons.Outlined.Close, "Close", tint = c.muted, modifier = Modifier.size(20.dp).clickable(onClick = onClose))
        }
        Spacer(Modifier.height(6.dp))
        if (device.phy == Phy.Wifi) {
            PopRow("Channel", "${device.channel ?: "?"}${device.frequencyMhz?.let { " · $it MHz" } ?: ""}")
            PopRow("Security", device.crypto, securityColor(device))
        } else {
            PopRow("Type", device.type)
        }
        device.rssi?.let { PopRow("Signal", "$it dBm · strongest ${device.maxRssi ?: it} dBm", signalColor(it)) }
        if (device.manufacturer.isNotBlank()) PopRow("Vendor", device.manufacturer)
        Spacer(Modifier.height(8.dp))
        WtButton("Details", onDetails, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PopRow(label: String, value: String, color: Color = Wt.colors.text) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, fontSize = 12.sp, color = Wt.colors.muted, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- osmdroid plumbing --------------------------------------------------------------------------

private var osmdroidConfigured = false

/** osmdroid must know who's asking (OSM tile policy) and where to cache, before the first MapView. */
private fun configureOsmdroid(context: Context) {
    if (osmdroidConfigured) return
    Configuration.getInstance().apply {
        userAgentValue = "WaveTop/${com.ardyn.wavetop.BuildConfig.VERSION_NAME} (${context.packageName})"
        val base = File(context.cacheDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
    }
    osmdroidConfigured = true
}

private fun dpPx(context: Context, dp: Int) = (dp * context.resources.displayMetrics.density).toInt()

private fun boundsOf(points: List<GeoPoint>): BoundingBox {
    val b = BoundingBox.fromGeoPoints(points)
    // A single spot or a straight east-west line has no height; give it some so the zoom is sane.
    val pad = 0.0005
    return if (b.latitudeSpan < pad || b.longitudeSpan < pad) {
        BoundingBox(b.latNorth + pad, b.lonEast + pad, b.latSouth - pad, b.lonWest - pad)
    } else {
        b
    }
}

/** Recolours street tiles to sit under each theme (NetSeer tints its tiles the same way). */
private fun styleTiles(map: MapView, c: WtColors, basemap: Basemap) {
    val tiles = map.overlayManager.tilesOverlay
    map.setBackgroundColor(c.canvas.toArgb())
    val filter = tileFilter(c)
    // The filter also recolours the "still loading" squares; on dark themes it inverts them, so draw
    // them white to land on the theme's background instead of showing as light grey patches.
    tiles.loadingBackgroundColor = if (filter != null) android.graphics.Color.WHITE else c.canvas.toArgb()
    tiles.loadingLineColor = if (filter != null) android.graphics.Color.WHITE else c.line.toArgb()
    tiles.isEnabled = basemap == Basemap.Streets
    tiles.setColorFilter(filter)
}

private val filterCache = HashMap<WtColors, ColorMatrixColorFilter?>()

private fun tileFilter(c: WtColors): ColorMatrixColorFilter? = filterCache.getOrPut(c) {
    if (c.isLight) return@getOrPut null
    // Dark themes: invert the light OSM style (land goes dark, roads light), then map each
    // pixel's brightness onto a ramp from the theme's background to its muted text colour.
    // Streets come out in the theme's own palette, dim enough that the pins stay brightest.
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    fun row(from: Float, to: Float): FloatArray {
        val span = (to - from) * 0.85f
        return floatArrayOf(span * 0.299f, span * 0.587f, span * 0.114f, 0f, from * 255f)
    }
    val ramp = ColorMatrix(
        row(c.bg.red, c.muted.red) +
            row(c.bg.green, c.muted.green) +
            row(c.bg.blue, c.muted.blue) +
            floatArrayOf(0f, 0f, 0f, 1f, 0f),
    )
    invert.postConcat(ramp)
    ColorMatrixColorFilter(invert)
}

/** Draws the route, pins and you straight onto the map canvas, and hit-tests taps. */
private class PinsOverlay(context: Context, private val onTap: (List<TrackedDevice>) -> Unit) : Overlay() {
    var colors: WtColors? = null
    var dots: List<TrackedDevice> = emptyList()
    var me: GeoFix? = null
    var liveYou: Boolean = true
    var track: List<GeoFix> = emptyList()
    var highlightKey: String? = null
    var nowMs: Long = 0L
    var labels: Boolean = true

    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val route = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11.5f * density
        setShadowLayer(3f * density, 0f, 0f, android.graphics.Color.BLACK)
    }
    private val point = android.graphics.Point()
    private val path = Path()
    private var drawn: List<Pair<TrackedDevice, PointF>> = emptyList()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val c = colors ?: return
        val projection = mapView.projection

        if (track.size >= 2) {
            path.reset()
            track.forEachIndexed { i, f ->
                projection.toPixels(GeoPoint(f.lat, f.lon), point)
                if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            route.color = c.accent.copy(alpha = 0.75f).toArgb()
            route.strokeWidth = 3.5f * density
            canvas.drawPath(path, route)
        }

        val r = 6.5f * density
        val placed = ArrayList<Pair<TrackedDevice, PointF>>(dots.size)
        for (d in dots) {
            val f = d.bestFix ?: continue
            projection.toPixels(GeoPoint(f.lat, f.lon), point)
            // Devices pinned at one spot would stack exactly; fan them out a little.
            val a = Math.toRadians(RadarMath.angleDegrees(d.key).toDouble())
            placed += d to PointF(point.x + (cos(a) * 7 * density).toFloat(), point.y + (sin(a) * 7 * density).toFloat())
        }
        // Faded (stale) first so fresh pins draw on top.
        for ((d, p) in placed.sortedByDescending { nowMs - it.first.lastSeenMs }) {
            val faded = liveYou && nowMs - d.lastSeenMs > STALE_MS
            val kind = if (d.phy == Phy.Bluetooth) c.nodeBluetooth else c.nodeAp
            if (d.phy == Phy.Wifi && d.crypto.startsWith("Open")) {
                stroke.color = c.danger.toArgb()
                stroke.strokeWidth = 2.5f * density
                canvas.drawCircle(p.x, p.y, r + 3.5f * density, stroke)
            }
            fill.color = kind.copy(alpha = if (faded) 0.45f else 1f).toArgb()
            canvas.drawCircle(p.x, p.y, r, fill)
            stroke.color = android.graphics.Color.WHITE
            stroke.strokeWidth = 2f * density
            canvas.drawCircle(p.x, p.y, r, stroke)
        }
        drawn = placed

        // Names for the strongest few, so the map reads without tapping.
        label.color = c.text.toArgb()
        if (labels) {
            placed.filter { it.first.key != highlightKey }
                .sortedByDescending { it.first.maxRssi ?: Int.MIN_VALUE }
                .take(6)
                .forEach { (d, p) -> canvas.drawText(d.name.take(20), p.x + r * 1.7f, p.y + r * 0.45f, label) }
        }
        placed.firstOrNull { it.first.key == highlightKey }?.let { (d, p) ->
            stroke.color = c.select.toArgb()
            stroke.strokeWidth = 2.5f * density
            canvas.drawCircle(p.x, p.y, r * 2.1f, stroke)
            stroke.color = c.select.copy(alpha = 0.45f).toArgb()
            canvas.drawCircle(p.x, p.y, r * 3.0f, stroke)
            canvas.drawText(d.name.take(24), p.x + r * 3.3f, p.y + r * 0.45f, label)
        }

        me?.let { fix ->
            projection.toPixels(GeoPoint(fix.lat, fix.lon), point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (liveYou) {
                fill.color = OperatorColor.copy(alpha = 0.12f).toArgb()
                canvas.drawCircle(x, y, projection.metersToPixels(fix.accuracyM), fill)
            }
            // NetSeer's operator: an orange-red diamond with a white edge.
            val s = 7f * density
            path.reset()
            path.moveTo(x, y - s)
            path.lineTo(x + s, y)
            path.lineTo(x, y + s)
            path.lineTo(x - s, y)
            path.close()
            fill.color = OperatorColor.toArgb()
            canvas.drawPath(path, fill)
            stroke.color = android.graphics.Color.WHITE
            stroke.strokeWidth = 2f * density
            canvas.drawPath(path, stroke)
        }
    }

    /** Every pin within a finger's reach, so a tap on a cluster can list them all. */
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val reach = 22f * density
        val hits = drawn.filter { (_, p) -> hypot(p.x - e.x, p.y - e.y) <= reach }.map { it.first }
        if (hits.isEmpty()) return false
        onTap(hits)
        return true
    }
}
