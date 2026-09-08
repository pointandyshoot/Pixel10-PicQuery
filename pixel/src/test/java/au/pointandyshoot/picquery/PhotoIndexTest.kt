package au.pointandyshoot.picquery

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PhotoIndexTest {
    private lateinit var db: PhotoIndex
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("photo-index.db"); db = PhotoIndex(context)
    }
    @After fun close() { db.close() }
    private fun photo(uri: String = "content://media/external_primary/images/media/1", text: String = "Bramwell Station") =
        IndexedPhoto(uri, "generation:1", "Photo", 1000, -17.96, 122.24, text, FloatArray(512) { if (it == 0) 1f else 0f }, "model-v1", true, true)
    @Test fun repeatScanReplacesInsteadOfDuplicating() {
        db.upsert(photo()); db.upsert(photo().copy(fingerprint = "generation:2", ocr = "New sign"))
        assertEquals(1, db.count()); assertEquals("New sign", db.get(photo().uri)!!.ocr)
    }
    @Test fun vectorRoundTripIsExact() {
        db.upsert(photo()); assertArrayEquals(photo().vector, db.get(photo().uri)!!.vector, 0f)
    }
    @Test fun pruneAndClearDeleteDerivedData() {
        db.upsert(photo()); db.upsert(photo("content://media/external_primary/images/media/2"))
        db.prune(setOf(photo().uri)); assertEquals(1, db.count())
        db.clear(); assertEquals(0, db.count())
    }
    @Test fun strictOcrPhraseCannotInjectSql() {
        db.upsert(photo()); db.upsert(photo("other", "Unrelated"))
        assertEquals(1, db.search(SearchQuery("", "bramwell", null, null, null), null, "model-v1").size)
        assertTrue(db.search(SearchQuery("", "' OR 1=1 --", null, null, null), null, "model-v1").isEmpty())
    }
    @Test fun datesLocationsAndModelRevisionAreRespected() {
        db.upsert(photo()); db.upsert(photo("far").copy(latitude = 0.0, longitude = 0.0))
        val q = SearchQuery("", null, 1000, 1001, GeoFilter(-17.96, 122.24, 1.0))
        assertEquals(listOf(photo().uri), db.search(q, null, "model-v1").map { it.uri })
        assertTrue(db.search(q.copy(after = 1001), null, "model-v1").isEmpty())
    }
    @Test fun privacyTogglesPurgeStoredFields() {
        db.upsert(photo()); db.clearGps(); db.clearOcr()
        val p = db.get(photo().uri)!!
        assertNull(p.latitude); assertNull(p.longitude); assertEquals("", p.ocr)
        assertFalse(p.ocrDone); assertFalse(p.gpsDone)
    }
    @Test fun resultLimitIsBounded() {
        repeat(150) { db.upsert(photo("content://media/$it")) }
        assertEquals(100, db.search(SearchQuery("", null, null, null, null), null, "model-v1").size)
    }
    @Test fun revokedPhotosAreExcludedBeforeRanking() {
        db.upsert(photo()); db.upsert(photo("revoked"))
        val hits = db.search(SearchQuery("", null, null, null, null), null, "model-v1", visible = setOf(photo().uri))
        assertEquals(listOf(photo().uri), hits.map { it.uri })
    }
    @Test fun imageQueryUsesVectorScoresWithoutOcrBoost() {
        db.upsert(photo()); db.upsert(photo("opposite").copy(vector = FloatArray(512) { if (it == 0) -1f else 0f }))
        val hits = db.search(SearchQuery("", null, null, null, null), photo().vector, "model-v1")
        assertEquals(photo().uri, hits.first().uri)
        assertEquals(1.0, hits.first().score, 0.00001)
        assertEquals(-1.0, hits.last().score, 0.00001)
    }
}
