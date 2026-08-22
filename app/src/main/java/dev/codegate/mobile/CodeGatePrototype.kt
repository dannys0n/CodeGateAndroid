package dev.codegate.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dev.codegate.mobile.ui.theme.CodeGateCodeBackground
import dev.codegate.mobile.ui.theme.CodeGateText

private enum class ExerciseMode { BLOCKS, SYNTAX }
private val ALL_DIFFICULTIES = setOf("easy", "medium", "hard")

@Composable
fun CodeGatePrototype(
    repository: LessonRepository,
    onSubmit: () -> Unit,
    onWakeLaunchChanged: (Boolean) -> Unit
) {
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
    var allowedDifficulties by remember {
        mutableStateOf(
            preferences.getStringSet("problem_difficulties", ALL_DIFFICULTIES)
                ?.intersect(ALL_DIFFICULTIES)
                ?.takeIf { it.isNotEmpty() }
                ?: ALL_DIFFICULTIES
        )
    }
    var problemKey by remember { mutableIntStateOf(0) }
    var selectedProblemId by remember { mutableStateOf<String?>(null) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var wakeLaunchEnabled by remember { mutableStateOf(WakeLaunchSettings.isEnabled(context)) }
    var startsAfterBoot by remember { mutableStateOf(WakeLaunchSettings.startsAfterBoot(context)) }
    val lessonResult by produceState<Result<Lesson>?>(null, language, problemKey, allowedDifficulties) {
        value = null
        val loaded = withContext(Dispatchers.IO) {
            runCatching { repository.lesson(language, selectedProblemId, allowedDifficulties) }
        }
        value = loaded
        loaded.getOrNull()?.let { selectedProblemId = it.problemId }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CodeGate", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${if (language == "cpp") "C++" else "Python"} · ${if (mode == ExerciseMode.BLOCKS) "Blocks" else "Syntax · ${syntaxSize.name.lowercase()}"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                    Text(if (settingsExpanded) "Close" else "Settings")
                }
                Button(
                    onClick = {
                        selectedProblemId = null
                        problemKey++
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("New")
                }
            }

            if (settingsExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
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

                        SettingRow("Problem difficulty") {
                            ALL_DIFFICULTIES.forEach { difficulty ->
                                val label = difficulty.replaceFirstChar { it.uppercase() }
                                ChoiceChip(label, difficulty in allowedDifficulties) {
                                    val updated = if (difficulty in allowedDifficulties) {
                                        allowedDifficulties - difficulty
                                    } else {
                                        allowedDifficulties + difficulty
                                    }
                                    if (updated.isNotEmpty()) {
                                        allowedDifficulties = updated
                                        preferences.edit()
                                            .putStringSet("problem_difficulties", updated)
                                            .apply()
                                        selectedProblemId = null
                                        problemKey++
                                    }
                                }
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Open on device wake", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Run the foreground service at boot and open CodeGate when the screen becomes active.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = wakeLaunchEnabled,
                                onCheckedChange = { enabled ->
                                    wakeLaunchEnabled = enabled
                                    onWakeLaunchChanged(enabled)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Start service after reboot", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Automatically start the wake service after the device completes a cold boot.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = startsAfterBoot,
                                onCheckedChange = { enabled ->
                                    startsAfterBoot = enabled
                                    WakeLaunchSettings.setStartsAfterBoot(context, enabled)
                                },
                                enabled = wakeLaunchEnabled
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

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
                    BlockExercise(currentLesson, onSubmit)
                } else {
                    SyntaxExercise(currentLesson, syntaxSize, onSubmit)
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun ProblemCard(lesson: Lesson) {
    var hintVisible by remember(lesson.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(lesson.title, style = MaterialTheme.typography.titleLarge)
            Text("${lesson.difficulty} · ${if (lesson.language == "cpp") "C++" else "Python"}")
            Text(cleanStatement(lesson.statement))
            lesson.examples.forEachIndexed { index, example ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Example ${index + 1}", fontWeight = FontWeight.SemiBold)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            example,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            lesson.hints.firstOrNull()?.let { hint ->
                TextButton(onClick = { hintVisible = !hintVisible }) {
                    Text(if (hintVisible) "Hide hint" else "Show hint")
                }
                if (hintVisible) {
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun cleanStatement(statement: String): String = statement
    .lineSequence()
    .filterNot { line ->
        line.trim().matches(Regex("Example \\d+:")) || line.trim() == "Constraints:"
    }
    .joinToString("\n")
    .trim()

@Composable
private fun CodeChoice(code: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = code,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CodePanel(source: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CodeGateCodeBackground)
    ) {
        Text(
            text = source,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 420.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(14.dp),
            fontFamily = FontFamily.Monospace,
            color = CodeGateText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BlockExercise(lesson: Lesson, onSubmit: () -> Unit) {
    var selected by remember(lesson.id) { mutableStateOf(emptyList<String>()) }
    var message by remember(lesson.id) { mutableStateOf("") }
    var checking by remember(lesson.id) { mutableStateOf(false) }
    var readyToSubmit by remember(lesson.id) { mutableStateOf(false) }
    val byId = remember(lesson.id) { lesson.blocks.associateBy { it.id } }
    val available = remember(lesson.id) { lesson.blocks.shuffled() }
        .filterNot { it.id in selected }
    val assembled = lesson.fixedPrefix + selected.mapNotNull(byId::get)
        .joinToString(separator = "") { it.sourceCode } + "\n        ▌" + lesson.fixedSuffix
    val progress = if (lesson.correctOrder.isEmpty()) 1f else selected.size.toFloat() / lesson.correctOrder.size

    LaunchedEffect(checking, selected) {
        if (!checking) return@LaunchedEffect
        delay(350)
        readyToSubmit = selected == lesson.correctOrder
        checking = false
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Assembled solution", style = MaterialTheme.typography.titleMedium)
        Text("${selected.size}/${lesson.correctOrder.size}")
    }
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    CodePanel(assembled)
    Text("Available blocks", style = MaterialTheme.typography.titleMedium)
    available.forEach { block ->
        CodeChoice(
            code = block.displayCode,
            onClick = {
                selected = selected + block.id
                message = ""
                readyToSubmit = false
            }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                selected = selected.dropLast(1)
                message = ""
                readyToSubmit = false
            },
            enabled = selected.isNotEmpty() && !checking,
            shape = RoundedCornerShape(10.dp)
        ) { Text("Undo") }
        TextButton(
            onClick = {
                selected = emptyList()
                message = ""
                checking = false
                readyToSubmit = false
            }
        ) { Text("Reset") }
        Button(
            onClick = {
                if (readyToSubmit) {
                    onSubmit()
                } else if (selected == lesson.correctOrder) {
                    message = ""
                    checking = true
                } else {
                    message = "Not yet—the block order is incorrect."
                }
            },
            enabled = !checking,
            shape = RoundedCornerShape(10.dp)
        ) {
            AnimatedContent(
                targetState = when {
                    checking -> "Checking…"
                    readyToSubmit -> "Submit"
                    else -> "Check"
                },
                transitionSpec = {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                },
                label = "check-submit"
            ) { label ->
                Text(label)
            }
        }
    }
    if (message.isNotEmpty()) Text(message)
}

@Composable
private fun SyntaxExercise(lesson: Lesson, size: SyntaxSize, onSubmit: () -> Unit) {
    val engine = remember(lesson.id) { SyntaxChoiceEngine(lesson) }
    var cursor by remember(lesson.id, size) { mutableIntStateOf(0) }
    var history by remember(lesson.id, size) { mutableStateOf(emptyList<Int>()) }
    var message by remember(lesson.id, size) { mutableStateOf("") }
    val choices = remember(lesson.id, size, cursor) { engine.choices(cursor, size) }

    val progress = if (engine.tokenCount == 0) 1f else cursor.toFloat() / engine.tokenCount
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Guided solution", style = MaterialTheme.typography.titleMedium)
        Text("${(progress * 100).toInt()}%")
    }
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    CodePanel(engine.renderedSource(cursor))
    if (cursor >= engine.tokenCount) {
        Text("Solution complete", style = MaterialTheme.typography.titleMedium)
    } else {
        Text("Choose the next syntax", style = MaterialTheme.typography.titleMedium)
        choices.forEach { choice ->
            CodeChoice(
                code = choice.code,
                onClick = {
                    if (choice.correct) {
                        history = history + cursor
                        cursor = engine.nextEnd(cursor, size)
                        message = ""
                    } else {
                        message = "That syntax does not match the reference solution."
                    }
                }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
            onClick = {
                cursor = history.last()
                history = history.dropLast(1)
                message = ""
            },
                enabled = history.isNotEmpty(),
                shape = RoundedCornerShape(10.dp)
        ) { Text("Undo") }
        TextButton(onClick = { cursor = 0; history = emptyList(); message = "" }) { Text("Reset") }
        Button(
            onClick = onSubmit,
            enabled = cursor >= engine.tokenCount,
            shape = RoundedCornerShape(10.dp)
        ) { Text("Submit") }
    }
    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
}
