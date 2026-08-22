package dev.codegate.mobile

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.random.Random

data class LessonBlock(
    val id: String,
    val displayCode: String,
    val sourceCode: String
)

enum class ProblemDifficulty(val storageKey: String, val displayName: String) {
    BEGINNER("beginner", "Beginner"),
    EASY("easy", "Easy"),
    MEDIUM("medium", "Medium"),
    HARD("hard", "Hard");

    companion object {
        fun fromStorageKey(value: String): ProblemDifficulty? =
            entries.firstOrNull { it.storageKey == value.lowercase() }
    }
}

data class Lesson(
    val id: String,
    val problemId: String,
    val title: String,
    val difficulty: ProblemDifficulty,
    val language: String,
    val statement: String,
    val examples: List<String>,
    val hints: List<String>,
    val fixedPrefix: String,
    val fixedSuffix: String,
    val blocks: List<LessonBlock>,
    val correctOrder: List<String>
) {
    val orderedBlocks: List<LessonBlock>
        get() {
            val byId = blocks.associateBy { it.id }
            return correctOrder.mapNotNull(byId::get)
        }

    val referenceBody: String
        get() = orderedBlocks.joinToString(separator = "") { it.sourceCode }
}

class LessonRepository(private val context: Context) {
    private val cache = ConcurrentHashMap<String, List<Lesson>>()
    private val packagedFiles by lazy { context.assets.list(ASSET_DIRECTORY).orEmpty().toSet() }

    fun lesson(
        language: String,
        problemId: String? = null,
        allowedDifficulties: Set<ProblemDifficulty> = ProblemDifficulty.entries.toSet()
    ): Lesson {
        val lessons = cache.computeIfAbsent(language, ::loadLanguage)
            .filter { it.difficulty in allowedDifficulties }
        require(lessons.isNotEmpty()) { "No lessons match the selected difficulties." }
        if (problemId != null) {
            lessons.firstOrNull { it.problemId == problemId }?.let { return it }
        }
        return lessons[Random.nextInt(lessons.size)]
    }

    private fun loadLanguage(language: String): List<Lesson> =
        ProblemDifficulty.entries.flatMap { difficulty ->
            loadShard(language, difficulty)
        }

    private fun loadShard(language: String, difficulty: ProblemDifficulty): List<Lesson> {
        val baseName = "$language-${difficulty.storageKey}.json"
        val json = if ("$baseName.gz" in packagedFiles) {
            context.assets.open("$ASSET_DIRECTORY/$baseName.gz").use { input ->
                GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } else {
            context.assets.open("$ASSET_DIRECTORY/$baseName")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }
        val lessons = JSONObject(json).getJSONArray("lessons")
        return buildList(lessons.length()) {
            for (index in 0 until lessons.length()) {
                val item = lessons.getJSONObject(index)
                val blocksJson = item.getJSONArray("blocks")
                val blocks = buildList(blocksJson.length()) {
                    for (blockIndex in 0 until blocksJson.length()) {
                        val block = blocksJson.getJSONObject(blockIndex)
                        add(
                            LessonBlock(
                                id = block.getString("id"),
                                displayCode = block.getString("displayCode"),
                                sourceCode = block.getString("sourceCode")
                            )
                        )
                    }
                }
                add(
                    Lesson(
                        id = item.getString("id"),
                        problemId = item.getString("problemId"),
                        title = item.getString("title"),
                        difficulty = ProblemDifficulty.fromStorageKey(item.getString("difficulty"))
                            ?: error("Unknown problem difficulty in $baseName"),
                        language = item.getString("language"),
                        statement = item.getString("statement"),
                        examples = item.getJSONArray("examples").toStringList(),
                        hints = item.getJSONArray("hints").toStringList(),
                        fixedPrefix = item.getString("fixedPrefix"),
                        fixedSuffix = item.getString("fixedSuffix"),
                        blocks = blocks,
                        correctOrder = item.getJSONArray("correctOrder").toStringList()
                    )
                )
            }
        }
    }

    private companion object {
        const val ASSET_DIRECTORY = "codegate"
    }
}

private fun org.json.JSONArray.toStringList(): List<String> =
    List(length()) { index -> getString(index) }
