package com.lumecard.shared.data

import com.lumecard.shared.util.loadTextResource
import kotlin.random.Random

/**
 * Generates fun, recognizable names for sync backups.
 *
 * Word pools are stored as one file per category under /backup/. Each file
 * holds one word per line. On first use the whole pool is loaded and cached;
 * words are drawn in random groups of [GROUP_SIZE] lines at a time to avoid
 * repeated combinations.
 *
 * Example generated name:
 *   一头从容游荡的沙丁鱼-下行-C-20260903-225313
 */
class BackupNameGenerator {

    private data class Subject(val word: String, val quantifiers: List<String>)

    /** Per-category queue of words waiting to be used (drawn in groups of GROUP_SIZE). */
    private val queues = mutableMapOf<String, MutableList<String>>()
    private var subjects: List<Subject>? = null

    /**
     * Generate a backup name of the form:
     *   <fun-name>-<direction>-<type>-<yyyymmdd-HHMMSS>
     *
     * @param direction "上行" or "下行"
     * @param type "D" (data) or "C" (config)
     */
    fun generateName(direction: String, type: String): String {
        val funName = buildFunName()
        val timestamp = timestampNow()
        return "$funName-$direction-$type-$timestamp"
    }

    private fun buildFunName(): String {
        val subject = nextSubject()
        val quantifier = subject.quantifiers[Random.nextInt(subject.quantifiers.size)]
        val template = Random.nextInt(4)
        return when (template) {
            0 -> "$quantifier${nextWord("adjectives")}的${subject.word}"
            1 -> "$quantifier${nextWord("adverbs")}${nextWord("verbs")}的${subject.word}"
            2 -> "$quantifier${nextWord("adverbs")}${nextWord("verbs")}在${nextWord("places")}的${subject.word}"
            else -> "$quantifier${nextWord("adjectives")}的${subject.word}在${nextWord("places")}"
        }
    }

    private fun timestampNow(): String {
        // kotlin.time.Instant.toString() -> "2026-09-03T09:00:54.123Z"
        val raw = kotlin.time.Clock.System.now().toString()
        val datePart = raw.substringBefore("T")          // 2026-09-03
        val timePart = raw.substringAfter("T").substringBefore(".") // 09:00:54
        return "${datePart.replace("-", "")}-${timePart.replace(":", "")}"
    }

    private fun nextSubject(): Subject {
        val pool = subjects ?: loadSubjectPool().also { subjects = it }
        return if (pool.isEmpty()) Subject("旅行者", listOf("一位"))
        else pool[Random.nextInt(pool.size)]
    }

    /**
     * Draw one word from a category pool. Words are drawn from a queue filled
     * with random groups of [GROUP_SIZE] distinct lines; when the queue runs
     * empty a fresh random group is drawn.
     */
    private fun nextWord(category: String): String {
        val queue = queues.getOrPut(category) { mutableListOf() }
        if (queue.isEmpty()) {
            val pool = wordPool(category)
            if (pool == null || pool.isEmpty()) return defaultWord(category)
            queue.addAll(pool.shuffled().take(GROUP_SIZE))
        }
        return queue.removeAt(queue.lastIndex)
    }

    private fun wordPool(category: String): List<String>? {
        return loadLinePool("/backup/$category.txt")
    }

    private fun defaultWord(category: String): String = when (category) {
        "verbs" -> "漫步"
        "places" -> "天际"
        "adjectives" -> "快乐的"
        else -> "从容地"
    }

    private fun loadLinePool(path: String): List<String>? {
        val text = loadTextResource(path) ?: return null
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun loadSubjectPool(): List<Subject> {
        val text = loadTextResource("/backup/subjects.txt") ?: return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 2) return@mapNotNull null
                val word = parts[0].trim()
                val quantifiers = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (word.isEmpty() || quantifiers.isEmpty()) null
                else Subject(word, quantifiers)
            }
            .toList()
    }

    companion object {
        private const val GROUP_SIZE = 9
    }
}