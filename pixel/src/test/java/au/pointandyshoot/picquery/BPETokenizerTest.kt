package au.pointandyshoot.picquery

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BPETokenizerTest {
    @Test fun matchesOpenClipReferenceIncludingUtf8AndTruncation() {
        val tokenizer = BPETokenizer(ApplicationProvider.getApplicationContext())
        val fixtures = JSONArray(javaClass.getResourceAsStream("/tokenizer-fixtures.json")!!.bufferedReader().use { it.readText() })
        for (i in 0 until fixtures.length()) {
            val sample = fixtures.getJSONObject(i)
            val expected = sample.getJSONArray("tokens").let { array -> IntArray(array.length()) { array.getInt(it) } }
            assertArrayEquals(sample.getString("text"), expected, tokenizer.tokenize(sample.getString("text")).first)
        }
    }
}
