package com.example.methodmesh.modules.astronomy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.cos

/** Public NASA GIBS downloader for module-owned nighttime-radiance regional caches. */
object LightPollutionNetwork {
    const val LAYER_ID = "VIIRS_SNPP_DayNightBand_At_Sensor_Radiance"
    const val UNIT = "nW/(cm² sr)"
    const val SOURCE_URL = "https://gibs.earthdata.nasa.gov/"
    private const val WMS_URL = "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi"
    private const val DEFAULT_PX = 768

    data class Bounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double)
    data class Downloaded(val bitmap: Bitmap, val date: LocalDate, val bounds: Bounds)

    fun downloadAndCache(
        repository: LightPollutionRepository,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        requestedDate: LocalDate? = null,
        maxLookbackDays: Int = 14
    ): LightPollutionRepository.RasterRegion {
        require(radiusKm in 2.0..500.0) { "Radius must be between 2 and 500 km." }
        val bounds = bounds(latitude, longitude, radiusKm)
        val firstDate = requestedDate ?: LocalDate.now(ZoneOffset.UTC).minusDays(2)
        var lastError: Throwable? = null
        for (offset in 0..maxLookbackDays) {
            val date = firstDate.minusDays(offset.toLong())
            runCatching { fetch(bounds, date) }
                .onSuccess { downloaded ->
                    return repository.saveRasterRegion(
                        name = "NASA VIIRS ${radiusKm.toInt()} km · $date",
                        datasetId = LAYER_ID,
                        datasetDate = date.toString(),
                        source = "$SOURCE_URL · GIBS WMS",
                        unit = UNIT,
                        resolutionM = 500.0,
                        centerLat = latitude,
                        centerLon = longitude,
                        radiusKm = radiusKm,
                        minLat = bounds.minLat,
                        minLon = bounds.minLon,
                        maxLat = bounds.maxLat,
                        maxLon = bounds.maxLon,
                        bitmap = downloaded.bitmap
                    )
                }
                .onFailure { lastError = it }
        }
        throw IllegalStateException("No usable NASA nighttime-radiance image was found in the last ${maxLookbackDays + 1} days.", lastError)
    }

    fun requestUrl(latitude: Double, longitude: Double, radiusKm: Double, date: LocalDate): String =
        buildUrl(bounds(latitude, longitude, radiusKm), date)

    fun bounds(latitude: Double, longitude: Double, radiusKm: Double): Bounds {
        val latDelta = radiusKm / 111.32
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.08)
        val lonDelta = radiusKm / (111.32 * cosLat)
        return Bounds(
            minLat = (latitude - latDelta).coerceAtLeast(-85.0),
            minLon = (longitude - lonDelta).coerceAtLeast(-179.999),
            maxLat = (latitude + latDelta).coerceAtMost(85.0),
            maxLon = (longitude + lonDelta).coerceAtMost(179.999)
        )
    }

    private fun fetch(bounds: Bounds, date: LocalDate): Downloaded {
        val connection = (URL(buildUrl(bounds, date)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MethodMesh-Astronomy/0.6")
        }
        try {
            val status = connection.responseCode
            check(status in 200..299) { "NASA GIBS returned HTTP $status." }
            val bitmap = connection.inputStream.use { input -> BitmapFactory.decodeStream(input) }
                ?: error("NASA GIBS response was not a readable image.")
            check(hasUsableData(bitmap) && hasUsableCentre(bitmap)) {
                "NASA image did not contain usable radiance at the selected site for $date."
            }
            return Downloaded(bitmap, date, bounds)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildUrl(bounds: Bounds, date: LocalDate): String {
        val params = linkedMapOf(
            "SERVICE" to "WMS",
            "VERSION" to "1.1.1",
            "REQUEST" to "GetMap",
            "LAYERS" to LAYER_ID,
            "STYLES" to "default",
            "FORMAT" to "image/png",
            "TRANSPARENT" to "TRUE",
            "WIDTH" to DEFAULT_PX.toString(),
            "HEIGHT" to DEFAULT_PX.toString(),
            "SRS" to "EPSG:4326",
            "BBOX" to "${bounds.minLon},${bounds.minLat},${bounds.maxLon},${bounds.maxLat}",
            "TIME" to date.toString()
        )
        return WMS_URL + "?" + params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
    }

    private fun hasUsableCentre(bitmap: Bitmap): Boolean {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val half = 4
        for (y in (cy - half).coerceAtLeast(0)..(cy + half).coerceAtMost(bitmap.height - 1)) {
            for (x in (cx - half).coerceAtLeast(0)..(cx + half).coerceAtMost(bitmap.width - 1)) {
                val p = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(p)
                val fill = android.graphics.Color.red(p) == 0 &&
                    android.graphics.Color.green(p) == 0 &&
                    android.graphics.Color.blue(p) == 160
                if (alpha > 0 && !fill) return true
            }
        }
        return false
    }

    private fun hasUsableData(bitmap: Bitmap): Boolean {
        val stepX = (bitmap.width / 64).coerceAtLeast(1)
        val stepY = (bitmap.height / 64).coerceAtLeast(1)
        var usable = 0
        var sampled = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                sampled++
                val alpha = android.graphics.Color.alpha(p)
                val fill = android.graphics.Color.red(p) == 0 && android.graphics.Color.green(p) == 0 && android.graphics.Color.blue(p) == 160
                if (alpha > 0 && !fill) usable++
                x += stepX
            }
            y += stepY
        }
        return sampled > 0 && usable.toDouble() / sampled >= 0.002
    }
}
