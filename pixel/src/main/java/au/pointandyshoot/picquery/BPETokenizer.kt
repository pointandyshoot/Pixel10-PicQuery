// Adapted from greyovo/PicQuery (MIT); see THIRD_PARTY_NOTICES.md.
package au.pointandyshoot.picquery

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream



private fun createCharDict(): Map<Int, Char> {
    val bytesList = mutableListOf<Int>()
    bytesList.addAll(33..126)
    bytesList.addAll(161..172)
    bytesList.addAll(174..255)
    val charList = bytesList.toMutableList()
    var n = 0
    for (b in 0..255) {
        if (b !in bytesList) {
            bytesList.add(b)
            charList.add(256 + n)
            n++
        }
    }
    return bytesList.zip(charList.map { it.toChar() }).toMap()
}

private fun readGzipFile(context: Context, assetName: String): List<String> {
    return GZIPInputStream(context.assets.open(assetName)).bufferedReader(Charsets.UTF_8).use { it.readLines() }
}

private fun getPairs(word: List<String>): Set<Pair<String, String>> {
    return word.zipWithNext().map { it.first to it.second }.toSet()
}

private fun whitespaceClean(text: String): String {
    var cleanedText = text.replace(Regex("\\s+"), " ")
    cleanedText = cleanedText.trim()
    return cleanedText
}

internal fun utf8ByteValues(token: String): IntArray {
    return token.toByteArray(Charsets.UTF_8)
        .map { it.toInt() and 0xFF }
        .toIntArray()
}

class BPETokenizer(context: Context, bpePath: String = "bpe_vocab_gz") {
    companion object {
        private const val START_TOKEN = "<|startoftext|>"
        private const val END_TOKEN = "<|endoftext|>"
        private const val WORD_END = "</w>"

        private val PATTERN = Pattern.compile(
            "${Pattern.quote(START_TOKEN)}|${Pattern.quote(END_TOKEN)}|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+"
        )
    }

    private val byteEncoder = createCharDict()

    private val merges: List<Pair<String, String>>
    val encoder: Map<String, Int>
    private val decoder: Map<Int, String>
    private val bpeRanks: Map<Pair<String, String>, Int>
    private val cache: MutableMap<String, String>

    init {
        val vocab: MutableList<String> = byteEncoder.values.map { it.toString() }.toMutableList()
        vocab.addAll(vocab.map { "$it$WORD_END" }.toList())

        val mergesFile: List<String> = readGzipFile(context, bpePath)
        merges =
            mergesFile.subList(1, 49152 - 256 - 2 + 1).map {
                val sp = it.split(" ")
                Pair(sp[0], sp[1])
            }

        vocab.addAll(merges.map { it.first + it.second })
        vocab.addAll(listOf(START_TOKEN, END_TOKEN))

        encoder = vocab.withIndex()
            .associateBy({ it.value }, { it.index })
        decoder = encoder
            .map { it.value to it.key }.toMap()
        bpeRanks = merges.mapIndexed { index, pair -> pair to index }.toMap()
        cache = mutableMapOf(
            START_TOKEN to START_TOKEN,
            END_TOKEN to END_TOKEN
        )
    }

    private fun bpe(token: String): String {
        cache[token]?.let { return it }

        var word = token.dropLast(1).map { it.toString() }.toMutableList().apply {
            add(token.last().toString() + WORD_END)
        }
        var pairs = getPairs(word)

        if (pairs.isEmpty()) return "$token$WORD_END"

        while (true) {
            val bigram = pairs.minByOrNull { bpeRanks[it] ?: Int.MAX_VALUE } ?: break
            if (bigram !in bpeRanks) break

            val (first, second) = bigram
            val newWord = mutableListOf<String>()
            var i = 0

            while (i < word.size) {
                val j = word.subList(i, word.size).indexOf(first).takeIf { it != -1 }?.plus(i) ?: word.size
                newWord.addAll(word.subList(i, j))
                i = j

                if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else if (i < word.size) {
                    newWord.add(word[i])
                    i++
                }
            }
            word = newWord
            if (word.size == 1) break
            pairs = getPairs(word)
        }

        return word.joinToString(" ").also { if (cache.size < 4096) cache[token] = it }
    }

    private fun encode(text: String): List<Int> {
        val cleanedText = whitespaceClean(text).lowercase()
        val matcher = PATTERN.matcher(cleanedText)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            val match = matcher.group()
            matches.add(match)
        }
        val bpeTokens = mutableListOf<Int>()
//        for token in re.findall(self.pat, text):
//        token = ''.join(self.byte_encoder[b] for b in token.encode('utf-8'))
//        bpe_tokens.extend(self.encoder[bpe_token] for bpe_token in self.bpe(token).split(' '))

//        return bpe_tokens
        for (token in matches) {
            val encodedToken = utf8ByteValues(token).map { byteEncoder.getValue(it) }.joinToString("")
            for (bpeToken in bpe(encodedToken).split(" ")) {
                bpeTokens.add(encoder.getValue(bpeToken))
            }
        }
        return bpeTokens
    }

    @Synchronized
    fun tokenize(text: String): Pair<IntArray, LongArray> {
        val sotToken: Int = encoder.getValue(START_TOKEN)
        val eotToken: Int = encoder.getValue(END_TOKEN)
        val tokens: MutableList<Int> = ArrayList()
        tokens.add(sotToken)
        tokens.addAll(encode(text.take(4096)))
        tokens.add(eotToken)

        if (tokens.size > 77) {
            if (true) {
                val truncatedTokens = tokens.subList(0, 77)
                truncatedTokens[77 - 1] = eotToken
            } else {
                throw java.lang.RuntimeException(
                    "Input $text is too long for context length $77"
                )
            }
        }
        val result = IntArray(77) {
            if (it < tokens.size) {
                tokens[it]
            } else {
                0
            }
        }
        val shape = longArrayOf(1, 77.toLong())
        return Pair(result, shape)
    }
}
