package com.smartnotes.core

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Small TF-IDF index. Runs fully on-device, no network, no embeddings model.
 * Used for auto-linking, smart resurfacing and search.
 */
class TextIndex(docs: List<NoteDoc>) {

    private val docs = docs
    private val idf: Map<String, Double>
    private val vectors: Map<Long, Map<String, Double>>

    init {
        val tokenized = docs.associate { it.id to tokenize(it.fullText) }
        val df = HashMap<String, Int>()
        tokenized.values.forEach { tokens -> tokens.toSet().forEach { df.merge(it, 1, Int::plus) } }
        val n = docs.size.coerceAtLeast(1)
        idf = df.mapValues { (_, count) -> ln((n + 1.0) / (count + 1.0)) + 1.0 }
        vectors = tokenized.mapValues { (_, tokens) -> vectorize(tokens) }
    }

    fun vectorize(text: String): Map<String, Double> = vectorize(tokenize(text))

    private fun vectorize(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val tf = tokens.groupingBy { it }.eachCount()
        val raw = tf.mapValues { (term, count) -> (count.toDouble() / tokens.size) * (idf[term] ?: 1.0) }
        val norm = sqrt(raw.values.sumOf { it * it })
        return if (norm == 0.0) raw else raw.mapValues { it.value / norm }
    }

    /** Returns notes ranked by cosine similarity to [text], best first. */
    fun search(text: String, limit: Int = 5, excludeId: Long? = null, minScore: Double = 0.0): List<ScoredNote> {
        val query = vectorize(text)
        if (query.isEmpty()) return emptyList()
        return docs.asSequence()
            .filter { it.id != excludeId }
            .map { ScoredNote(it, cosine(query, vectors[it.id].orEmpty())) }
            .filter { it.score > minScore }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    companion object {
        private val STOPWORDS = setOf(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "has", "have",
            "i", "in", "is", "it", "its", "me", "my", "of", "on", "or", "so", "that", "the", "this",
            "to", "was", "we", "were", "what", "when", "where", "which", "who", "will", "with", "you",
            "your", "do", "did", "about", "not", "can", "if", "then", "there", "they", "our", "us",
        )

        fun tokenize(text: String): List<String> =
            text.lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 1 && it !in STOPWORDS }
                .map(::stem)

        // Light suffix stripping so "decided" and "decide" match.
        private fun stem(word: String): String = when {
            word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
            word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
            word.length > 4 && word.endsWith("es") -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }

        fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
            val (small, large) = if (a.size <= b.size) a to b else b to a
            return small.entries.sumOf { (k, v) -> v * (large[k] ?: 0.0) }
        }
    }
}

data class ScoredNote(val note: NoteDoc, val score: Double)
