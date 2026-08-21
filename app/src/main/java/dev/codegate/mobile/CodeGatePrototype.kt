package dev.codegate.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ExerciseMode { BLOCKS, SYNTAX }

@Composable
fun CodeGatePrototype(repository: LessonRepository) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("codegate_learning", 0) }
    var language by remember { mutableStateOf(preferences.getString("language", "cpp") ?: "cpp") }
    var mode by remember {
        mutableStateOf(
            runCatching { ExerciseMode.valueOf(preferences.getString("mode", "BLOCKS")!!) }
                .getOrDefault(ExerciseMode.BLOCKS)
        )
    }
    var syntaxSize by remember {
        mutableStateOf(
            runCatching { SyntaxSize.valueOf(preferences.getString("syntax_size", "MEDIUM")!!) }
                .getOrDefault(SyntaxSize.MEDIUM)
        )
    }
    var problemKey by remember { mutableIntStateOf(0) }
    val lessonResult by produceState<Result<Lesson>?>(null, language, problemKey) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { repository.randomLesson(language) } }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("CodeGate learning prototype", style = MaterialTheme.typography.headlineSmall)

            SettingRow("Language") {
                ChoiceChip("C++", language == "cpp") {
                    language = "cpp"
                    preferences.edit().putString("language", language).apply()
                }
                ChoiceChip("Python", language == "python") {
                    language = "python"
                    preferences.edit().putString("language", language).apply()
                }
            }

            SettingRow("Input mode") {
                ChoiceChip("Code blocks", mode == ExerciseMode.BLOCKS) {
                    mode = ExerciseMode.BLOCKS
                    preferences.edit().putString("mode", mode.name).apply()
                }
                ChoiceChip("Syntax choices", mode == ExerciseMode.SYNTAX) {
                    mode = ExerciseMode.SYNTAX
                    preferences.edit().putString("mode", mode.name).apply()
                }
            }

            if (mode == ExerciseMode.SYNTAX) {
                SettingRow("Syntax size") {
                    SyntaxSize.entries.forEach { size ->
                        ChoiceChip(size.name.lowercase().replaceFirstChar { it.uppercase() }, syntaxSize == size) {
                            syntaxSize = size
                            preferences.edit().putString("syntax_size", size.name).apply()
                        }
                    }
                }
            }

            OutlinedButton(onClick = { problemKey++ }) {
                Text("Different problem")
            }

            val currentResult = lessonResult
            val currentLesson = currentResult?.getOrNull()
            if (currentResult == null) {
                Text("Loading lesson…")
            } else if (currentLesson == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Content pack unavailable", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currentResult.exceptionOrNull()?.message ?: "The lesson could not be loaded.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text("Run a full Android Studio build and reinstall so the assets are included.")
                    }
                }
            } else {
                ProblemCard(currentLesson)
                if (mode == ExerciseMode.BLOCKS) {
                    BlockExercise(currentLesson)
                } else {
                    SyntaxExercise(currentLesson, syntaxSize)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ProblemCard(lesson: Lesson) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(lesson.title, style = MaterialTheme.typography.titleLarge)
            Text("${lesson.difficulty} · ${if (lesson.language == "cpp") "C++" else "Python"}")
            Text(lesson.statement)
            lesson.hints.firstOrNull()?.let { Text("Hint: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CodePanel(source: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = source,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 420.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BlockExercise(lesson: Lesson) {
    var selected by remember(lesson.id) { mutableStateOf(emptyList<String>()) }
    var message by remember(lesson.id) { mutableStateOf("") }
    val byId = remember(lesson.id) { lesson.blocks.associateBy { it.id } }
    val available = remember(lesson.id) { lesson.blocks.shuffled() }
        .filterNot { it.id in selected }
    val assembled = lesson.fixedPrefix + selected.mapNotNull(byId::get)
        .joinToString(separator = "") { it.sourceCode } + "\n        ▌" + lesson.fixedSuffix

    Text("Assembled solution", style = MaterialTheme.typography.titleMedium)
    CodePanel(assembled)
    Text("Available blocks", style = MaterialTheme.typography.titleMedium)
    available.forEach { block ->
        OutlinedButton(
            onClick = {
                selected = selected + block.id
                message = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(block.displayCode, fontFamily = FontFamily.Monospace)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { selected = emptyList(); message = "" }) { Text("Reset") }
        Button(onClick = {
            message = if (selected == lesson.correctOrder) "Correct" else "Not yet—the block order is incorrect."
        }) { Text("Check") }
    }
    if (message.isNotEmpty()) Text(message)
}

@Composable
private fun SyntaxExercise(lesson: Lesson, size: SyntaxSize) {
    val engine = remember(lesson.id) { SyntaxChoiceEngine(lesson) }
    var cursor by remember(lesson.id, size) { mutableIntStateOf(0) }
    var message by remember(lesson.id, size) { mutableStateOf("") }
    val choices = remember(lesson.id, size, cursor) { engine.choices(cursor, size) }

    Text("Reference-guided solution", style = MaterialTheme.typography.titleMedium)
    CodePanel(engine.renderedSource(cursor))
    if (cursor >= engine.tokenCount) {
        Text("Solution complete", style = MaterialTheme.typography.titleMedium)
    } else {
        Text("Choose the next syntax", style = MaterialTheme.typography.titleMedium)
        choices.forEach { choice ->
            OutlinedButton(
                onClick = {
                    if (choice.correct) {
                        cursor = engine.nextEnd(cursor, size)
                        message = ""
                    } else {
                        message = "That syntax does not match the reference solution."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(choice.code, fontFamily = FontFamily.Monospace)
            }
        }
        OutlinedButton(onClick = { cursor = 0; message = "" }) { Text("Reset") }
    }
    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
}
