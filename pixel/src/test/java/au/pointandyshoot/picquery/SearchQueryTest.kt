package au.pointandyshoot.picquery

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SearchQueryTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-07T21:00:00Z"), ZoneId.of("Australia/Brisbane"))
    @Test fun datesUsePhoneTimezone() {
        val q = SearchQuery.parse("sunset yesterday", clock = clock)
        assertEquals("sunset", q.visual)
        assertEquals(Instant.parse("2026-09-06T14:00:00Z").toEpochMilli(), q.after)
        assertEquals(Instant.parse("2026-09-07T14:00:00Z").toEpochMilli(), q.before)
    }
    @Test fun hybridFiltersRemainIndependent() {
        val q = SearchQuery.parse("camper text:\"Bramwell station\" after:2026-08-01 before:2026-09-01 near:-17.96,122.24,50", clock = clock)
        assertEquals("camper", q.visual); assertEquals("Bramwell station", q.ocr)
        assertEquals(50.0, q.near!!.radiusKm, 0.0)
        assertNotNull(q.after); assertNotNull(q.before)
    }
    @Test fun savedPlaceMatchingIsLiteralAndCaseInsensitive() {
        val q = SearchQuery.parse("sunset near Broome", listOf(Place("Broome", -17.96, 122.24)), clock)
        assertEquals("sunset", q.visual); assertEquals(-17.96, q.near!!.latitude, 0.0)
    }
    @Test fun onDateIsHalfOpenAndDstAware() {
        val q = SearchQuery.parse("on:2026-10-04", clock = Clock.system(ZoneId.of("Australia/Sydney")))
        assertEquals(23 * 60 * 60 * 1000L, q.before!! - q.after!!)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMalformedFilters() { SearchQuery.parse("near:wrong") }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidCoordinates() { SearchQuery.parse("near:95,12,10") }
    @Test(expected = IllegalArgumentException::class) fun rejectsReverseDateRange() { SearchQuery.parse("after:2026-09-10 before:2026-09-01") }
    @Test fun distanceHandlesDateline() { assertTrue(distanceKm(0.0, 179.9, 0.0, -179.9) < 23) }
    @Test fun quotedOcrDoesNotBecomeADateFilter() {
        val q = SearchQuery.parse("text:\"yesterday on:2026-09-07\"", clock = clock)
        assertEquals("yesterday on:2026-09-07", q.ocr); assertNull(q.after); assertNull(q.before)
    }
    @Test fun cosineAndNormalisation() {
        val a = normalise(FloatArray(512) { if (it == 0) 3f else 0f })
        assertEquals(1.0, cosine(a, a), 0.00001)
        assertEquals(0.0, cosine(a, FloatArray(512) { if (it == 1) 1f else 0f }), 0.00001)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonfiniteEmbedding() { normalise(FloatArray(512) { Float.NaN }) }
}
