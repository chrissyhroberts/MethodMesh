package com.example.methodmesh.modules.weather

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

private const val WEATHER_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val WEATHER_RADAR_SOURCE = "methodmesh-weather-radar-source"
private const val WEATHER_RADAR_LAYER = "methodmesh-weather-radar-layer"

@Composable
internal fun WeatherRadarMap(
    latitude: Double,
    longitude: Double,
    tileTemplate: String,
    zoom: Double,
    gesturesEnabled: Boolean = false,
    overlayText: String = "",
    modifier: Modifier = Modifier
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val initialZoom = zoom.coerceIn(1.0, 7.0)
                val options = MapLibreMapOptions.createFromAttributes(context).apply {
                    camera(
                        CameraPosition.Builder()
                            .target(LatLng(latitude, longitude))
                            .zoom(initialZoom)
                            .build()
                    )
                    minZoomPreference(1.0)
                    maxZoomPreference(7.0)
                    scrollGesturesEnabled(gesturesEnabled)
                    zoomGesturesEnabled(gesturesEnabled)
                    rotateGesturesEnabled(gesturesEnabled)
                    tiltGesturesEnabled(gesturesEnabled)
                    compassEnabled(false)
                    logoEnabled(false)
                    attributionEnabled(true)
                }
                MapView(context, options).apply {
                    onCreate(null)
                    configureRadarTouch(this, gesturesEnabled)
                    tag = RadarMapViewState(latitude, longitude, initialZoom, tileTemplate)
                    getMapAsync { map ->
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.setAllGesturesEnabled(gesturesEnabled)
                        map.setStyle(Style.Builder().fromUri(WEATHER_MAP_STYLE)) { style ->
                            addRadar(style, tileTemplate)
                        }
                    }
                    onResume()
                }
            },
            update = { view ->
                configureRadarTouch(view, gesturesEnabled)
                val requestedZoom = zoom.coerceIn(1.0, 7.0)
                val previous = view.tag as? RadarMapViewState
                val desired = RadarMapViewState(latitude, longitude, requestedZoom, tileTemplate)
                view.tag = desired
                view.getMapAsync { map ->
                    map.uiSettings.setAllGesturesEnabled(gesturesEnabled)

                    if (previous == null ||
                        previous.latitude != latitude ||
                        previous.longitude != longitude ||
                        previous.zoom != requestedZoom
                    ) {
                        map.cameraPosition = CameraPosition.Builder(map.cameraPosition)
                            .target(LatLng(latitude, longitude))
                            .zoom(requestedZoom)
                            .build()
                    }

                    if (previous?.tileTemplate != tileTemplate) {
                        map.getStyle { style ->
                            val latest = view.tag as? RadarMapViewState
                            if (latest?.tileTemplate == tileTemplate) {
                                replaceRadar(style, tileTemplate)
                            }
                        }
                    }
                }
            }
        )
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White, 10f, c)
            drawCircle(Color(0xFF176B52), 6f, c)
            drawLine(Color.White, Offset(c.x - 18f, c.y), Offset(c.x + 18f, c.y), 2f)
            drawLine(Color.White, Offset(c.x, c.y - 18f), Offset(c.x, c.y + 18f), 2f)
        }
        if (overlayText.isNotBlank()) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 2.dp
            ) {
                Text(
                    overlayText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private fun configureRadarTouch(view: MapView, gesturesEnabled: Boolean) {
    view.isEnabled = gesturesEnabled
    view.isClickable = gesturesEnabled
    view.isFocusable = gesturesEnabled
    view.setOnTouchListener { touched, event ->
        when {
            !gesturesEnabled -> touched.parent?.requestDisallowInterceptTouchEvent(false)
            event.actionMasked == MotionEvent.ACTION_DOWN -> touched.parent?.requestDisallowInterceptTouchEvent(true)
            event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL ->
                touched.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
}

private data class RadarMapViewState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val tileTemplate: String
)

private fun replaceRadar(style: Style, tileTemplate: String) {
    style.removeLayer(WEATHER_RADAR_LAYER)
    style.removeSource(WEATHER_RADAR_SOURCE)
    addRadar(style, tileTemplate)
}

private fun addRadar(style: Style, tileTemplate: String) {
    if (tileTemplate.isBlank()) return
    val tileSet = TileSet("2.2.0", tileTemplate).apply {
        maxZoom = 7f
        minZoom = 0f
    }
    style.addSource(RasterSource(WEATHER_RADAR_SOURCE, tileSet, 256))
    style.addLayer(
        RasterLayer(WEATHER_RADAR_LAYER, WEATHER_RADAR_SOURCE)
            .withProperties(rasterOpacity(0.72f))
    )
}
