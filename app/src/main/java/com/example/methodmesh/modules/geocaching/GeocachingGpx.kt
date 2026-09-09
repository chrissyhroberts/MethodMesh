package com.example.methodmesh.modules.geocaching

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** Conservative GPX/Geocaching GPX reader. Unknown extension elements are ignored, never fatal. */
object GeocachingGpx {
    fun parse(input: InputStream): List<CacheRecord> {
        val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
        val result = mutableListOf<CacheRecord>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.local() == "wpt") {
                parseWaypoint(parser)?.let(result::add)
            }
            event = parser.next()
        }
        return result
    }

    private fun parseWaypoint(p: XmlPullParser): CacheRecord? {
        val latitude = p.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: return null
        val longitude = p.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: return null
        val waypointDepth = p.depth
        var code = ""
        var cacheName = ""
        var topLevelDescription = ""
        var description = ""
        var cacheType = "Traditional"
        var size = "Unknown"
        var difficulty: Double? = null
        var terrain: Double? = null
        var owner = ""
        var hint = ""
        var symbol = ""
        var insideGroundspeakCache = false
        var insideLog = false
        val attributes = mutableListOf<String>()
        val logs = mutableListOf<String>()
        var logDate = ""
        var logType = ""
        var logFinder = ""
        var logText = ""

        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    val tag = p.local()
                    when (tag) {
                        "cache" -> insideGroundspeakCache = true
                        "log" -> { insideLog=true; logDate=""; logType=""; logFinder=""; logText="" }
                        "name" -> {
                            val text = p.simpleText()
                            if (insideGroundspeakCache) cacheName = text else if (code.isBlank()) code = text
                        }
                        "desc" -> if (!insideGroundspeakCache) topLevelDescription = p.simpleText()
                        "sym" -> symbol = p.simpleText()
                        "type" -> {
                            val text = p.simpleText()
                            if (insideLog) logType = text
                            else if (insideGroundspeakCache) cacheType = text.substringAfterLast('|').ifBlank { cacheType }
                            else if (cacheType == "Traditional") cacheType = text.substringAfterLast('|').ifBlank { cacheType }
                        }
                        "container" -> size = p.simpleText().ifBlank { size }
                        "difficulty" -> difficulty = p.simpleText().toDoubleOrNull()
                        "terrain" -> terrain = p.simpleText().toDoubleOrNull()
                        "owner" -> owner = p.simpleText()
                        "encoded_hints" -> hint = p.simpleText()
                        "short_description", "long_description" -> {
                            val text = p.simpleText()
                            if (text.isNotBlank() && description.length < 20_000) {
                                description = listOf(description, stripHtml(text)).filter { it.isNotBlank() }.joinToString("\n\n")
                            }
                        }
                        "attribute" -> {
                            val included = p.getAttributeValue(null,"inc")
                            val text = p.simpleText().trim()
                            if (text.isNotBlank()) attributes += if (included == "0") "Not $text" else text
                        }
                        "date" -> logDate = p.simpleText()
                        "finder" -> logFinder = p.simpleText()
                        "text" -> logText = stripHtml(p.simpleText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (p.local()) {
                        "cache" -> insideGroundspeakCache = false
                        "log" -> {
                            insideLog=false
                            val line = listOf(logType, logDate, logFinder, logText)
                                .filter { it.isNotBlank() }.joinToString(" · ")
                            if (line.isNotBlank() && logs.size < 20) logs += line
                        }
                        "wpt" -> if (p.depth == waypointDepth) break
                    }
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        val name = cacheName.ifBlank { topLevelDescription.ifBlank { code.ifBlank { "Waypoint" } } }
        val resolvedType = if (cacheName.isBlank() && symbol.isNotBlank() && cacheType == "Traditional") "Waypoint: $symbol" else cacheType
        return CacheRecord(
            code = code.ifBlank { "MM-${latitude.gcFmt(5)}-${longitude.gcFmt(5)}" },
            name = name,
            source = CacheSource.LOCAL_GPX,
            latitude = latitude,
            longitude = longitude,
            type = resolvedType,
            size = size,
            difficulty = difficulty,
            terrain = terrain,
            owner = owner,
            description = description.ifBlank { topLevelDescription },
            hint = hint,
            attributes = attributes.distinct(),
            recentLogs = logs
        )
    }

    private fun XmlPullParser.local(): String = name?.substringAfter(':').orEmpty()
    private fun XmlPullParser.simpleText(): String = runCatching { nextText() }.getOrDefault("")
    private fun stripHtml(value:String):String = value
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"")
        .replace(Regex("[ \\t]+"), " ")
        .trim()
}
