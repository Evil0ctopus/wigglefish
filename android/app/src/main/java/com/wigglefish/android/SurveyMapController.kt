package com.wigglefish.android

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max
import kotlin.math.min

/**
 * osmdroid helpers for Survey map: GPS track, AP/BLE markers, RSSI/hit density circles.
 * Uses OSM tiles (no Google Maps API key).
 */
object SurveyMapController {
    fun init(context: Context) {
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = context.packageName
        cfg.osmdroidBasePath = File(context.cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        cfg.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    fun bindMapView(map: MapView) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 3.0
        map.maxZoomLevel = 20.0
        map.controller.setZoom(15.0)
        // Allow gestures inside a ScrollView parent.
        map.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    fun render(
        map: MapView,
        gpsFixes: List<GpsFix>,
        observations: List<ObservationRecord>,
        followLatest: Boolean = true,
    ) {
        map.overlays.removeAll { it is Marker || it is Polyline || it is DensityOverlay }
        val points = mutableListOf<GeoPoint>()

        if (gpsFixes.size >= 2) {
            val track = Polyline(map).apply {
                outlinePaint.color = Color.parseColor("#8FF7FF")
                outlinePaint.strokeWidth = 6f
                setPoints(gpsFixes.map { GeoPoint(it.latitude, it.longitude) })
            }
            map.overlays.add(0, track)
            points += track.actualPoints
        } else if (gpsFixes.size == 1) {
            points += GeoPoint(gpsFixes[0].latitude, gpsFixes[0].longitude)
        }

        val densityPts = mutableListOf<DensityOverlay.DensityPoint>()
        observations.forEach { rec ->
            val gps = rec.lastGps ?: return@forEach
            if (!WardriveExporter.isValidGps(gps.latitude, gps.longitude)) return@forEach
            val gp = GeoPoint(gps.latitude, gps.longitude)
            points += gp
            densityPts += DensityOverlay.DensityPoint(
                gp,
                radiusMeters = densityRadiusMeters(rec),
                color = densityColor(rec),
            )
            val marker = Marker(map).apply {
                position = gp
                title = if (rec.type == "wifi") rec.ssidOrName else "BLE ${rec.ssidOrName}"
                snippet = "${rec.mac}  ${rec.lastRssi} dBm  n=${rec.hitCount}" +
                    if (rec.vendor.isNotEmpty()) "  ${rec.vendor}" else ""
                relatedObject = rec
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            map.overlays.add(marker)
        }

        if (densityPts.isNotEmpty()) {
            map.overlays.add(0, DensityOverlay(densityPts))
        }

        when {
            points.size >= 2 -> {
                val box = BoundingBox.fromGeoPoints(points)
                try {
                    map.zoomToBoundingBox(box, false, 64)
                } catch (_: Exception) {
                    map.controller.setCenter(points.last())
                    map.controller.setZoom(15.0)
                }
            }
            points.size == 1 -> {
                map.controller.setZoom(16.0)
                map.controller.setCenter(points[0])
            }
            followLatest -> {
                // keep last camera
            }
        }
        map.invalidate()
    }

    private fun densityRadiusMeters(rec: ObservationRecord): Double {
        val rssiBoost = ((rec.peakRssi + 100).coerceIn(0, 70)) * 0.6
        val hitBoost = min(40.0, rec.hitCount * 2.0)
        return 18.0 + rssiBoost + hitBoost
    }

    private fun densityColor(rec: ObservationRecord): Int {
        val alpha = min(160, 40 + rec.hitCount * 8 + max(0, rec.peakRssi + 90))
        return if (rec.type == "wifi") {
            Color.argb(alpha, 0x8F, 0xF7, 0xFF)
        } else {
            Color.argb(alpha, 0xFF, 0x4F, 0xD8)
        }
    }

    /** Simple filled-circle density overlay (heatmap-ish). */
    class DensityOverlay(private val points: List<DensityPoint>) : Overlay() {
        data class DensityPoint(val geo: GeoPoint, val radiusMeters: Double, val color: Int)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val projection = mapView.projection
            val screen = android.graphics.Point()
            points.forEach { p ->
                projection.toPixels(p.geo, screen)
                val px = projection.metersToEquatorPixels(p.radiusMeters.toFloat())
                paint.color = p.color
                canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), px, paint)
            }
        }
    }
}
