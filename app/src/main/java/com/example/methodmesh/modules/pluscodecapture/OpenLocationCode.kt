package com.example.methodmesh.modules.pluscodecapture

import kotlin.math.floor

object OpenLocationCode {
    private const val SEPARATOR = '+'
    private const val SEPARATOR_POSITION = 8
    private const val ALPHABET = "23456789CFGHJMPQRVWX"
    private const val ENCODING_BASE = 20

    fun encode(latitude: Double, longitude: Double, codeLength: Int = 10): String {
        val length = codeLength.coerceIn(2, 10).let { if (it % 2 == 0) it else it - 1 }
        var lat = latitude.coerceIn(-90.0, 90.0)
        var lon = normalizeLongitude(longitude)

        if (lat == 90.0) lat -= 0.000000001

        lat += 90.0
        lon += 180.0

        var placeValue = 20.0
        val code = StringBuilder()
        var emitted = 0

        while (emitted < length) {
            val latDigit = floor(lat / placeValue).toInt().coerceIn(0, ENCODING_BASE - 1)
            val lonDigit = floor(lon / placeValue).toInt().coerceIn(0, ENCODING_BASE - 1)
            code.append(ALPHABET[latDigit])
            code.append(ALPHABET[lonDigit])
            lat -= latDigit * placeValue
            lon -= lonDigit * placeValue
            placeValue /= ENCODING_BASE
            emitted += 2
        }

        while (code.length < SEPARATOR_POSITION) code.append('0')
        code.insert(SEPARATOR_POSITION, SEPARATOR)
        return code.toString()
    }

    fun decode(code: String): PlusCodeArea {
        val clean = code.uppercase()
            .filter { it != SEPARATOR && it != '0' && !it.isWhitespace() }
        require(clean.length >= 2) { "Plus Code must contain at least two code digits." }
        require(clean.length % 2 == 0) { "Only even-length full Plus Codes are supported in this version." }

        var south = -90.0
        var west = -180.0
        var placeValue = 20.0

        clean.chunked(2).forEach { pair ->
            val latDigit = ALPHABET.indexOf(pair[0])
            val lonDigit = ALPHABET.indexOf(pair[1])
            require(latDigit >= 0 && lonDigit >= 0) { "Invalid Plus Code character." }
            south += latDigit * placeValue
            west += lonDigit * placeValue
            placeValue /= ENCODING_BASE
        }

        val cellSize = placeValue * ENCODING_BASE

        return PlusCodeArea(
            code = encode(south + cellSize / 2.0, west + cellSize / 2.0, clean.length),
            codeLength = clean.length,
            south = south,
            west = normalizeLongitude(west),
            north = (south + cellSize).coerceAtMost(90.0),
            east = normalizeLongitude(west + cellSize),
            latitudeHeight = cellSize,
            longitudeWidth = cellSize
        )
    }

    fun cellFor(latitude: Double, longitude: Double, codeLength: Int = 10): PlusCodeArea =
        decode(encode(latitude, longitude, codeLength))

    private fun normalizeLongitude(longitude: Double): Double {
        var lon = longitude
        while (lon < -180.0) lon += 360.0
        while (lon >= 180.0) lon -= 360.0
        return lon
    }
}

data class PlusCodeArea(
    val code: String,
    val codeLength: Int,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val latitudeHeight: Double,
    val longitudeWidth: Double
) {
    val centerLatitude: Double get() = south + latitudeHeight / 2.0
    val centerLongitude: Double get() = west + longitudeWidth / 2.0
}
