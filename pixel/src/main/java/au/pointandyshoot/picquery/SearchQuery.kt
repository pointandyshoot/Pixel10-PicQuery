package au.pointandyshoot.picquery

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

data class Place(val name: String, val latitude: Double, val longitude: Double)
data class GeoFilter(val latitude: Double, val longitude: Double, val radiusKm: Double)
data class SearchQuery(
    val visual: String, val ocr: String?, val after: Long?, val before: Long?, val near: GeoFilter?
) {
    companion object {
        fun parse(raw: String, places: List<Place> = emptyList(), clock: Clock = Clock.systemDefaultZone()): SearchQuery {
            require(raw.length <= 4096) { "Please use a shorter search (up to 4,096 characters)." }
            var remaining = raw.trim()
            var after: Long? = null
            var before: Long? = null
            var ocr: String? = null
            var near: GeoFilter? = null
            remaining = Regex("\\btext:\"([^\"]+)\"", RegexOption.IGNORE_CASE).replace(remaining) {
                ocr = it.groupValues[1]; " "
            }
            fun start(date: LocalDate) = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()
            val datePattern = Regex("\\b(after|before|on):(\\d{4}-\\d{2}-\\d{2})\\b", RegexOption.IGNORE_CASE)
            remaining = datePattern.replace(remaining) {
                val date = LocalDate.parse(it.groupValues[2])
                when (it.groupValues[1].lowercase()) {
                    "after" -> after = start(date)
                    "before" -> before = start(date)
                    "on" -> { after = start(date); before = start(date.plusDays(1)) }
                }
                " "
            }
            remaining = Regex("\\b(yesterday|today)\\b", RegexOption.IGNORE_CASE).replace(remaining) {
                val date = LocalDate.now(clock).minusDays(if (it.value.equals("yesterday", true)) 1 else 0)
                after = start(date); before = start(date.plusDays(1)); " "
            }
            remaining = Regex("\\bnear:([-+\\d.]+),([-+\\d.]+)(?:,([\\d.]+))?", RegexOption.IGNORE_CASE).replace(remaining) {
                near = GeoFilter(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(),
                    it.groupValues[3].toDoubleOrNull() ?: 50.0)
                " "
            }
            for (place in places.sortedByDescending { it.name.length }) {
                val pattern = Regex("\\bnear\\s+${Regex.escape(place.name)}\\b", RegexOption.IGNORE_CASE)
                if (pattern.containsMatchIn(remaining)) {
                    near = GeoFilter(place.latitude, place.longitude, 50.0)
                    remaining = pattern.replace(remaining, " ")
                    break
                }
            }
            near?.let {
                require(it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                    it.longitude.isFinite() && it.longitude in -180.0..180.0 &&
                    it.radiusKm.isFinite() && it.radiusKm > 0 && it.radiusKm <= 20040) { "Invalid coordinates or radius in kilometres." }
            }
            require(after == null || before == null || after!! < before!!) { "The start date must be before the end date." }
            require(!Regex("\\b(after|before|on|text|near):", RegexOption.IGNORE_CASE).containsMatchIn(remaining)) {
                "Check filter syntax: on:2026-09-07, text:\"Bramwell\", near:-17.96,122.24,50"
            }
            return SearchQuery(remaining.replace(Regex("\\s+"), " ").trim(), ocr, after, before, near)
        }
    }
}

fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 6371.0088 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

fun normalise(vector: FloatArray): FloatArray {
    require(vector.size == 512 && vector.all { it.isFinite() }) { "Invalid model output." }
    val length = sqrt(vector.sumOf { it.toDouble() * it }.toFloat())
    require(length > 0.000001f) { "Empty model output." }
    return FloatArray(vector.size) { vector[it] / length }
}

fun cosine(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size)
    var dot = 0.0
    for (i in a.indices) dot += a[i] * b[i]
    return dot.coerceIn(-1.0, 1.0)
}
