package dev.codegate.mobile

import android.content.Context
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import kotlin.random.Random

data class LessonBlock(
    val id: String,
    val displayCode: String,
    val sourceCode: String
)

data class Lesson(
    val id: String,
    val problemId: String,
    val title: String,
    val difficulty: String,
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
    private val cache = mutableMapOf<String, List<Lesson>>()

    fun lesson(language: String, problemId: String? = null): Lesson {
        val lessons = cache.getOrPut(language) { loadShard(language) }
        if (problemId != null) {
            lessons.firstOrNull { it.problemId == problemId }?.let { return it }
        }
        return lessons[Random.nextInt(lessons.size)]
    }

    private fun loadShard(language: String): List<Lesson> {
        val baseName = if (language == "python") "python-easy.json" else "cpp-easy.json"
        val packagedFiles = context.assets.list("codegate").orEmpty().toSet()
        val json = if ("$baseName.gz" in packagedFiles) {
            context.assets.open("codegate/$baseName.gz").use { input ->
                GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } else {
            context.assets.open("codegate/$baseName").bufferedReader(Charsets.UTF_8).use { it.readText() }
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
                        difficulty = item.getString("difficulty"),
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
}

private fun org.json.JSONArray.toStringList(): List<String> =
    List(length()) { index -> getString(index) }
