package com.example.methodmesh.modules.astronomy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Astronomy-owned cache for light-pollution / nighttime-radiance data.
 *
 * v0.6 adds georeferenced NASA GIBS raster regions while retaining the older
 * point-grid JSON importer for backwards compatibility.
 */
class LightPollutionRepository(context: Context) {
    data class Region(
        val id: String,
        val name: String,
        val datasetId: String,
        val datasetDate: String,
        val source: String,
        val unit: String,
        val resolutionM: Double,
        val centerLat: Double,
        val centerLon: Double,
        val radiusKm: Double,
        val pointCount: Int,
        val file: File
    )

    data class RasterRegion(
        val id: String,
        val name: String,
        val datasetId: String,
        val datasetDate: String,
        val source: String,
        val unit: String,
        val resolutionM: Double,
        val centerLat: Double,
        val centerLon: Double,
        val radiusKm: Double,
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double,
        val widthPx: Int,
        val heightPx: Int,
        val cachedAtIso: String,
        val metadataFile: File,
        val imageFile: File
    ) {
        fun covers(latitude: Double, longitude: Double): Boolean =
            latitude in minLat..maxLat && longitude in minLon..maxLon
    }

    data class Lookup(
        val value: Double,
        val unit: String,
        val datasetId: String,
        val datasetDate: String,
        val source: String,
        val regionName: String,
        val resolutionM: Double,
        val sampleDistanceKm: Double,
        val cacheKind: String,
        val regionId: String
    )

    private val rootDir = File(context.applicationContext.filesDir, "astronomy/light_pollution").apply { mkdirs() }
    private val rasterDir = File(rootDir, "raster").apply { mkdirs() }

    /** Legacy point-grid import. */
    fun importRegion(raw: String): Region {
        val root = JSONObject(raw)
        val points = root.optJSONArray("points") ?: error("Region JSON must contain a points array.")
        require(points.length() > 0) { "Region contains no points." }
        val center = root.optJSONObject("center")
        val centerLat = center?.optDouble("latitude", Double.NaN)?.takeIf { it.isFinite() }
            ?: root.optDouble("center_latitude", Double.NaN).takeIf { it.isFinite() }
            ?: error("Region centre latitude is required.")
        val centerLon = center?.optDouble("longitude", Double.NaN)?.takeIf { it.isFinite() }
            ?: root.optDouble("center_longitude", Double.NaN).takeIf { it.isFinite() }
            ?: error("Region centre longitude is required.")
        val radiusKm = root.optDouble("radius_km", Double.NaN).takeIf { it.isFinite() && it > 0 }
            ?: error("Positive radius_km is required.")
        val id = root.optString("region_id").ifBlank { UUID.randomUUID().toString() }
        root.put("region_id", id)
        root.put("cached_at_iso", Instant.now().toString())
        root.put("point_count", points.length())
        val file = File(rootDir, safeName(id) + ".json")
        file.writeText(root.toString())
        return parseRegion(file) ?: error("Imported region could not be re-read.")
    }

    fun listRegions(): List<Region> = rootDir.listFiles { file -> file.extension.equals("json", true) }
        ?.mapNotNull(::parseRegion)
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()

    fun listRasterRegions(): List<RasterRegion> = rasterDir.listFiles { file -> file.name.endsWith(".meta.json") }
        ?.mapNotNull(::parseRasterRegion)
        ?.sortedWith(compareByDescending<RasterRegion> { it.datasetDate }.thenBy { it.radiusKm })
        .orEmpty()

    fun cacheCount(): Int = listRegions().size + listRasterRegions().size

    fun saveRasterRegion(
        id: String = UUID.randomUUID().toString(),
        name: String,
        datasetId: String,
        datasetDate: String,
        source: String,
        unit: String,
        resolutionM: Double,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        bitmap: Bitmap
    ): RasterRegion {
        val safe = safeName(id)
        val image = File(rasterDir, "$safe.png")
        image.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not write cached radiance image." }
        }
        val cachedAt = Instant.now().toString()
        val metadata = File(rasterDir, "$safe.meta.json")
        metadata.writeText(JSONObject().apply {
            put("region_id", id)
            put("name", name)
            put("dataset_id", datasetId)
            put("dataset_date", datasetDate)
            put("source", source)
            put("unit", unit)
            put("resolution_m", resolutionM)
            put("center_latitude", centerLat)
            put("center_longitude", centerLon)
            put("radius_km", radiusKm)
            put("min_latitude", minLat)
            put("min_longitude", minLon)
            put("max_latitude", maxLat)
            put("max_longitude", maxLon)
            put("width_px", bitmap.width)
            put("height_px", bitmap.height)
            put("cached_at_iso", cachedAt)
            put("image_file", image.name)
            put("meaning", "NASA GIBS VIIRS Black Marble nighttime at-sensor radiance; astronomy light-pollution proxy, not observed zenith sky brightness")
        }.toString())
        return parseRasterRegion(metadata) ?: error("Cached raster could not be re-read.")
    }

    fun query(latitude: Double, longitude: Double): Lookup? =
        queryRaster(latitude, longitude) ?: queryLegacyPoints(latitude, longitude)

    fun rasterCovering(latitude: Double, longitude: Double): RasterRegion? =
        listRasterRegions()
            .filter { it.covers(latitude, longitude) }
            .sortedWith(compareByDescending<RasterRegion> { it.datasetDate }.thenBy { it.radiusKm })
            .firstOrNull()

    fun rasterById(id: String): RasterRegion? = listRasterRegions().firstOrNull { it.id == id }

    fun originalBitmap(region: RasterRegion): Bitmap? = BitmapFactory.decodeFile(region.imageFile.absolutePath)

    /** Re-colour GIBS greyscale radiance into a transparent heat map for display. */
    fun heatmapBitmap(region: RasterRegion): Bitmap? {
        val source = originalBitmap(region) ?: return null
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            if (Color.alpha(p) == 0 || isFillPixel(p)) {
                pixels[i] = Color.TRANSPARENT
                continue
            }
            val grey = ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3).coerceIn(0, 255)
            val radiance = radianceFromGrey(grey)
            pixels[i] = heatColour(radiance)
        }
        output.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return output
    }

    fun deleteRegion(id: String): Boolean = File(rootDir, safeName(id) + ".json").delete()

    fun deleteRasterRegion(id: String): Boolean {
        val safe = safeName(id)
        val meta = File(rasterDir, "$safe.meta.json")
        val image = File(rasterDir, "$safe.png")
        val a = !meta.exists() || meta.delete()
        val b = !image.exists() || image.delete()
        return a && b
    }

    fun clearAll() {
        rootDir.listFiles()?.forEach { file ->
            if (file.isDirectory) file.listFiles()?.forEach { it.delete() } else file.delete()
        }
    }

    private fun queryRaster(latitude: Double, longitude: Double): Lookup? {
        val candidates = listRasterRegions()
            .filter { it.covers(latitude, longitude) }
            .sortedWith(compareByDescending<RasterRegion> { it.datasetDate }.thenBy { it.radiusKm })
        for (region in candidates) {
            val bitmap = originalBitmap(region) ?: continue
            val xFraction = ((longitude - region.minLon) / (region.maxLon - region.minLon)).coerceIn(0.0, 1.0)
            val yFraction = ((region.maxLat - latitude) / (region.maxLat - region.minLat)).coerceIn(0.0, 1.0)
            val x = (xFraction * (bitmap.width - 1)).toInt().coerceIn(0, bitmap.width - 1)
            val y = (yFraction * (bitmap.height - 1)).toInt().coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) == 0 || isFillPixel(pixel)) continue
            val grey = ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3).coerceIn(0, 255)
            return Lookup(
                value = radianceFromGrey(grey),
                unit = region.unit,
                datasetId = region.datasetId,
                datasetDate = region.datasetDate,
                source = region.source,
                regionName = region.name,
                resolutionM = region.resolutionM,
                sampleDistanceKm = 0.0,
                cacheKind = "nasa_gibs_raster",
                regionId = region.id
            )
        }
        return null
    }

    private fun queryLegacyPoints(latitude: Double, longitude: Double): Lookup? {
        val candidates = listRegions().filter { region ->
            distanceKm(latitude, longitude, region.centerLat, region.centerLon) <= region.radiusKm + 5.0
        }
        var bestRegion: Region? = null
        var bestValue: Double? = null
        var bestDistance = Double.POSITIVE_INFINITY
        for (region in candidates) {
            val root = runCatching { JSONObject(region.file.readText()) }.getOrNull() ?: continue
            val points = root.optJSONArray("points") ?: continue
            for (i in 0 until points.length()) {
                val p = points.optJSONObject(i) ?: continue
                val lat = p.optDouble("latitude", Double.NaN)
                val lon = p.optDouble("longitude", Double.NaN)
                val value = p.optDouble("value", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite() || !value.isFinite()) continue
                val d = distanceKm(latitude, longitude, lat, lon)
                if (d < bestDistance) {
                    bestRegion = region
                    bestValue = value
                    bestDistance = d
                }
            }
        }
        val region = bestRegion ?: return null
        val value = bestValue ?: return null
        val toleranceKm = maxOf(2.0, region.resolutionM / 1000.0 * 2.5)
        if (bestDistance > toleranceKm) return null
        return Lookup(
            value = value,
            unit = region.unit,
            datasetId = region.datasetId,
            datasetDate = region.datasetDate,
            source = region.source,
            regionName = region.name,
            resolutionM = region.resolutionM,
            sampleDistanceKm = bestDistance,
            cacheKind = "legacy_point_grid",
            regionId = region.id
        )
    }

    private fun parseRegion(file: File): Region? = runCatching {
        val root = JSONObject(file.readText())
        if (!root.has("points")) return@runCatching null
        val center = root.optJSONObject("center")
        val points = root.optJSONArray("points") ?: JSONArray()
        Region(
            id = root.optString("region_id", file.nameWithoutExtension),
            name = root.optString("name", "Cached light-pollution region"),
            datasetId = root.optString("dataset_id", "unknown"),
            datasetDate = root.optString("dataset_date", ""),
            source = root.optString("source", ""),
            unit = root.optString("unit", "mcd/m²"),
            resolutionM = root.optDouble("resolution_m", 1000.0),
            centerLat = center?.optDouble("latitude", Double.NaN)?.takeIf { it.isFinite() }
                ?: root.getDouble("center_latitude"),
            centerLon = center?.optDouble("longitude", Double.NaN)?.takeIf { it.isFinite() }
                ?: root.getDouble("center_longitude"),
            radiusKm = root.getDouble("radius_km"),
            pointCount = root.optInt("point_count", points.length()),
            file = file
        )
    }.getOrNull()

    private fun parseRasterRegion(file: File): RasterRegion? = runCatching {
        val root = JSONObject(file.readText())
        val image = File(rasterDir, root.getString("image_file"))
        if (!image.exists()) return@runCatching null
        RasterRegion(
            id = root.getString("region_id"),
            name = root.optString("name", "NASA night-radiance region"),
            datasetId = root.optString("dataset_id", LightPollutionNetwork.LAYER_ID),
            datasetDate = root.optString("dataset_date"),
            source = root.optString("source", LightPollutionNetwork.SOURCE_URL),
            unit = root.optString("unit", LightPollutionNetwork.UNIT),
            resolutionM = root.optDouble("resolution_m", 500.0),
            centerLat = root.getDouble("center_latitude"),
            centerLon = root.getDouble("center_longitude"),
            radiusKm = root.getDouble("radius_km"),
            minLat = root.getDouble("min_latitude"),
            minLon = root.getDouble("min_longitude"),
            maxLat = root.getDouble("max_latitude"),
            maxLon = root.getDouble("max_longitude"),
            widthPx = root.getInt("width_px"),
            heightPx = root.getInt("height_px"),
            cachedAtIso = root.optString("cached_at_iso"),
            metadataFile = file,
            imageFile = image
        )
    }.getOrNull()

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)

    private fun isFillPixel(pixel: Int): Boolean =
        Color.red(pixel) == 0 && Color.green(pixel) == 0 && Color.blue(pixel) == 160

    /**
     * Approximate inverse of the official GIBS VIIRS DNB radiance colour map.
     * GIBS publishes a quantised greyscale legend from 0 to >=38.2 nW/(cm² sr).
     * Interpolation between anchor entries produces an estimate suitable for the
     * map cursor/readout; it is deliberately reported as a proxy, not raw science data.
     */
    private fun radianceFromGrey(grey: Int): Double {
        val anchors = arrayOf(
            7 to 0.05, 48 to 0.95, 79 to 2.05, 98 to 3.05, 126 to 5.05,
            144 to 6.90, 167 to 10.05, 183 to 12.90, 193 to 15.10, 200 to 16.80,
            211 to 19.85, 220 to 22.80, 230 to 26.50, 240 to 30.75, 248 to 34.65,
            254 to 37.90, 255 to 38.20
        )
        if (grey <= anchors.first().first) return anchors.first().second
        for (i in 1 until anchors.size) {
            val (g1, v1) = anchors[i - 1]
            val (g2, v2) = anchors[i]
            if (grey <= g2) {
                val f = (grey - g1).toDouble() / (g2 - g1).toDouble()
                return v1 + f * (v2 - v1)
            }
        }
        return 38.2
    }

    private fun heatColour(radiance: Double): Int {
        val (r, g, b) = when {
            radiance < 0.5 -> Triple(18, 50, 180)
            radiance < 1.0 -> Triple(0, 145, 220)
            radiance < 2.0 -> Triple(0, 185, 125)
            radiance < 5.0 -> Triple(210, 205, 30)
            radiance < 10.0 -> Triple(245, 145, 25)
            radiance < 20.0 -> Triple(225, 55, 35)
            else -> Triple(180, 35, 120)
        }
        return Color.argb(235, r, g, b)
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
