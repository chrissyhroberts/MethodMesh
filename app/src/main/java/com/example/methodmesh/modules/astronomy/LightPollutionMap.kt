package com.example.methodmesh.modules.astronomy

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource

private const val LP_OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val LP_IMAGE_SOURCE = "methodmesh-light-radiance-source"
private const val LP_IMAGE_LAYER = "methodmesh-light-radiance-layer"
private const val LP_SATELLITE_STYLE = """
{
  "version": 8,
  "sources": {
    "esri-world-imagery": {
      "type": "raster",
      "tiles": ["https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "attribution": "Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
    }
  },
  "layers": [{"id":"esri-world-imagery","type":"raster","source":"esri-world-imagery","minzoom":0,"maxzoom":22}]
}
"""
private const val LP_BLANK_STYLE = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#181818"}}]}"""

/**
 * Fixed local map surface modelled on plus_code.capture's MapLibre + Canvas overlay.
 * The heat raster is georeferenced with ImageSource so it remains correctly aligned
 * if the map projection or viewport changes.
 */
@Composable
fun LightPollutionMap(
    latitude: Double,
    longitude: Double,
    radiusKm: Double,
    region: LightPollutionRepository.RasterRegion?,
    heatmap: Bitmap?,
    basemapMode: String,
    opacity: Float,
    height: Dp = 280.dp,
    modifier: Modifier = Modifier
) {
    val styleKey = "$basemapMode:${region?.id.orEmpty()}"
    Box(modifier = modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                MapView(context).apply {
                    onCreate(null)
                    getMapAsync { map ->
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.setAllGesturesEnabled(true)
                        map.setStyle(lightPollutionBaseStyle(basemapMode)) { style ->
                            addHeatOverlay(style, region, heatmap, opacity)
                        }
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(latitude, longitude))
                            .zoom(radiusZoom(radiusKm))
                            .build()
                    }
                    tag = styleKey
                    onResume()
                }
            },
            update = { mapView ->
                mapView.getMapAsync { map ->
                    val nextKey = styleKey
                    if (mapView.tag != nextKey) {
                        mapView.tag = nextKey
                        map.setStyle(lightPollutionBaseStyle(basemapMode)) { style ->
                            addHeatOverlay(style, region, heatmap, opacity)
                        }
                    } else {
                        map.style?.let { style -> updateHeatOverlay(style, region, heatmap, opacity) }
                    }
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(latitude, longitude))
                        .zoom(radiusZoom(radiusKm))
                        .build()
                }
            }
        )
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color.White, radius = 10.dp.toPx(), center = c)
            drawCircle(Color(0xFF004DCC), radius = 6.dp.toPx(), center = c)
            drawLine(Color.White, Offset(c.x - 18.dp.toPx(), c.y), Offset(c.x + 18.dp.toPx(), c.y), 2.dp.toPx())
            drawLine(Color.White, Offset(c.x, c.y - 18.dp.toPx()), Offset(c.x, c.y + 18.dp.toPx()), 2.dp.toPx())
        }
    }
}

private fun lightPollutionBaseStyle(mode: String): Style.Builder = when (mode) {
    "satellite" -> Style.Builder().fromJson(LP_SATELLITE_STYLE)
    "blank" -> Style.Builder().fromJson(LP_BLANK_STYLE)
    else -> Style.Builder().fromUri(LP_OPENFREEMAP_STYLE_URL)
}

private fun addHeatOverlay(
    style: Style,
    region: LightPollutionRepository.RasterRegion?,
    bitmap: Bitmap?,
    opacity: Float
) {
    if (region == null || bitmap == null) return
    val source = ImageSource(LP_IMAGE_SOURCE, region.quad(), bitmap)
    style.addSource(source)
    style.addLayer(RasterLayer(LP_IMAGE_LAYER, LP_IMAGE_SOURCE).withProperties(rasterOpacity(opacity.coerceIn(0f, 1f))))
}

private fun updateHeatOverlay(
    style: Style,
    region: LightPollutionRepository.RasterRegion?,
    bitmap: Bitmap?,
    opacity: Float
) {
    val source = style.getSourceAs<ImageSource>(LP_IMAGE_SOURCE)
    val layer = style.getLayerAs<RasterLayer>(LP_IMAGE_LAYER)
    if (region == null || bitmap == null) {
        layer?.setProperties(rasterOpacity(0f))
        return
    }
    if (source == null) {
        addHeatOverlay(style, region, bitmap, opacity)
    } else {
        source.setCoordinates(region.quad())
        source.setImage(bitmap)
        layer?.setProperties(rasterOpacity(opacity.coerceIn(0f, 1f)))
    }
}

private fun LightPollutionRepository.RasterRegion.quad(): LatLngQuad = LatLngQuad(
    LatLng(maxLat, minLon),
    LatLng(maxLat, maxLon),
    LatLng(minLat, maxLon),
    LatLng(minLat, minLon)
)

private fun radiusZoom(radiusKm: Double): Double = when {
    radiusKm <= 5 -> 11.5
    radiusKm <= 10 -> 10.5
    radiusKm <= 25 -> 9.2
    radiusKm <= 50 -> 8.2
    radiusKm <= 100 -> 7.2
    radiusKm <= 200 -> 6.2
    else -> 5.4
}
