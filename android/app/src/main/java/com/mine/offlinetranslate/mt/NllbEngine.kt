package com.mine.offlinetranslate.mt

import android.util.JsonReader
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * Offline NLLB-200 inference backed by a small C API JNI bridge.
 *
 * The bridge deliberately links directly to the same libonnxruntime.so already
 * used by Sherpa. Mixing that core with the official ONNX Runtime Java JNI was
 * incompatible because their exported symbol versions differ.
 */
internal class NllbEngine(modelDir: String) : AutoCloseable {
    private val tokenizer = NllbTokenizer(File(modelDir, "tokenizer.json"))
    private val handle = NllbNative.create(modelDir)

    fun translate(text: String, source: String, target: String): String {
        if (source == target || text.isBlank()) return text
        val sourceLanguage = nllbLanguage(source)
            ?: error("NLLB 暂不支持源语言：$source")
        val targetLanguage = nllbLanguage(target)
            ?: error("NLLB 暂不支持目标语言：$target")
        val sourceLanguageId = tokenizer.languageId(sourceLanguage)
        val targetLanguageId = tokenizer.languageId(targetLanguage)
        val chunks = TranslationTextChunker.split(
            text,
            when (target) {
                "ru" -> 35
                "zh" -> 120
                else -> 100
            },
        )
        val translated = chunks.mapIndexed { index, chunk ->
            val result = translateChunk(
                chunk,
                sourceLanguageId,
                targetLanguageId,
                MAX_NEW_TOKENS,
            )
            Log.i(
                TAG,
                "chunk ${index + 1}/${chunks.size} srcChars=${chunk.length} " +
                    "generated=${result.generatedTokens} outChars=${result.text.length}",
            )
            result.text
        }
        return translated.joinToString("\n").trim().also {
            Log.i(TAG, "complete chunks=${chunks.size} inputChars=${text.length} outputChars=${it.length}")
        }
    }

    private fun translateChunk(
        text: String,
        sourceLanguageId: Long,
        targetLanguageId: Long,
        maxNewTokens: Int,
    ): ChunkResult {
        val encoded = tokenizer.encode(text)
        val ids = LongArray(encoded.size + 2)
        ids[0] = sourceLanguageId
        encoded.copyInto(ids, 1)
        ids[ids.lastIndex] = EOS_TOKEN_ID
        val generated = NllbNative.translate(
            handle,
            ids,
            targetLanguageId,
            maxNewTokens,
        )
        return ChunkResult(tokenizer.decode(generated.toList()), generated.size)
    }

    override fun close() {
        NllbNative.close(handle)
    }

    private fun nllbLanguage(code: String): String? = when (code) {
        "zh" -> "zho_Hans"
        "ru" -> "rus_Cyrl"
        "en" -> "eng_Latn"
        else -> null
    }

    private companion object {
        const val TAG = "NllbEngine"
        const val EOS_TOKEN_ID = 2L
        const val MAX_NEW_TOKENS = 256
    }

    private data class ChunkResult(val text: String, val generatedTokens: Int)
}

internal object NllbNative {
    init {
        System.loadLibrary("nllb-ort")
    }

    external fun create(modelDir: String): Long

    external fun translate(
        handle: Long,
        inputIds: LongArray,
        forcedFirstToken: Long,
        maxTokens: Int,
    ): LongArray

    external fun close(handle: Long)
}

/**
 * Streaming parser for the 17 MB Hugging Face tokenizer.json. Building the
 * whole JSON tree would unnecessarily spike memory before loading ONNX.
 *
 * The BPE layout follows the MIT-licensed Android reference at
 * github.com/nwdxlgzs/M2M100_418M_ONNX_Java_AndroidCoreCode.
 */
private class NllbTokenizer(file: File) {
    private val tokenToId = HashMap<String, Long>(300_000)
    private val idToToken = HashMap<Long, String>(300_000)
    private val bpeRanks = HashMap<String, Int>(550_000)
    private val languageIds = HashMap<String, Long>()
    private val specialIds = HashSet<Long>()
    private var unknownTokenId = 0L

    init {
        JsonReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "model" -> readModel(reader)
                    "added_tokens" -> readAddedTokens(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
    }

    fun languageId(language: String): Long =
        languageIds[language] ?: error("tokenizer 缺少语言 token：$language")

    fun encode(text: String): LongArray {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return LongArray(0)
        val result = ArrayList<Long>(normalized.length + 8)
        normalized.split(' ').forEach { word ->
            if (word.isNotEmpty()) {
                bpe("\u2581$word").forEach(result::add)
            }
        }
        return result.toLongArray()
    }

    fun decode(ids: List<Long>): String {
        val text = buildString {
            ids.forEach { id ->
                if (id !in specialIds) append(idToToken[id].orEmpty())
            }
        }
        return text
            .replace('\u2581', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun bpe(word: String): LongArray {
        var symbols = word.map(Char::toString).toMutableList()
        while (symbols.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestLeft = ""
            var bestRight = ""
            for (index in 0 until symbols.lastIndex) {
                val left = symbols[index]
                val right = symbols[index + 1]
                val rank = bpeRanks["$left $right"] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestLeft = left
                    bestRight = right
                }
            }
            if (bestRank == Int.MAX_VALUE) break
            val merged = ArrayList<String>(symbols.size)
            var index = 0
            while (index < symbols.size) {
                if (
                    index < symbols.lastIndex &&
                    symbols[index] == bestLeft &&
                    symbols[index + 1] == bestRight
                ) {
                    merged.add(bestLeft + bestRight)
                    index += 2
                } else {
                    merged.add(symbols[index])
                    index++
                }
            }
            symbols = merged
        }
        return LongArray(symbols.size) { tokenToId[symbols[it]] ?: unknownTokenId }
    }

    private fun readModel(reader: JsonReader) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "vocab" -> readVocabulary(reader)
                "merges" -> readMerges(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun readVocabulary(reader: JsonReader) {
        reader.beginObject()
        while (reader.hasNext()) {
            val token = reader.nextName()
            val id = reader.nextLong()
            tokenToId[token] = id
            idToToken[id] = token
        }
        reader.endObject()
    }

    private fun readMerges(reader: JsonReader) {
        reader.beginArray()
        var rank = 0
        while (reader.hasNext()) {
            bpeRanks[reader.nextString()] = rank++
        }
        reader.endArray()
    }

    private fun readAddedTokens(reader: JsonReader) {
        reader.beginArray()
        while (reader.hasNext()) {
            var content = ""
            var id = -1L
            var special = false
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "content" -> content = reader.nextString()
                    "id" -> id = reader.nextLong()
                    "special" -> special = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (id < 0) continue
            tokenToId[content] = id
            idToToken[id] = content
            if (content in NLLB_LANGUAGE_TOKENS) languageIds[content] = id
            if (content == "<unk>") unknownTokenId = id
            if (special) specialIds.add(id)
        }
        reader.endArray()
    }

    private companion object {
        val NLLB_LANGUAGE_TOKENS = setOf(
            "zho_Hans",
            "rus_Cyrl",
            "eng_Latn",
        )
    }
}
