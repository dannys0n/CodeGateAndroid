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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dev.codegate.mobile.ui.theme.CodeGateCodeBackground
import dev.codegate.mobile.ui.theme.CodeGateDifficultyBeginner
import dev.codegate.mobile.ui.theme.CodeGateDifficultyEasy
import dev.codegate.mobile.ui.theme.CodeGateDifficultyHard
import dev.codegate.mobile.ui.theme.CodeGateDifficultyMedium
import dev.codegate.mobile.ui.theme.CodeGateText

private enum class ExerciseMode { BLOCKS, SYNTAX }
private const val CARET_MARKER = '\u258C'
private data class CodeHighlight(val start: Int, val end: Int, val correct: Boolean)

private fun blockComment(language: String, number: Int, source: String = ""): String {
    val indent = source.lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.takeWhile { it == ' ' || it == '\t' }
        .orEmpty()
    val marker = if (language == "python") "#" else "//"
    return "$indent$marker Block $number\n"
}
private val DEFAULT_DIFFICULTIES = setOf(
    ProblemDifficulty.EASY,
    ProblemDifficulty.MEDIUM,
    ProblemDifficulty.HARD
)

@Composable
fun CodeGateScreen(
    repository: LessonRepository,
    onSubmit: () -> Unit,
    onWakeLaunchChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("codegate_learning", 0) }
    var language by remember { mutableStateOf(preferences.getString("language", "cpp") ?: "cpp") }
    var mode by remember {
        mutableStateOf(
            runCatching {
                ExerciseMode.valueOf(preferences.getString("mode", ExerciseMode.BLOCKS.name).orEmpty())
            }
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
            preferences.getStringSet(
                "problem_difficulties",
                DEFAULT_DIFFICULTIES.mapTo(mutableSetOf()) { it.storageKey }
            )
                ?.mapNotNullTo(mutableSetOf(), ProblemDifficulty::fromStorageKey)
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_DIFFICULTIES
        )
    }
    var problemKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedProblemId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var wakeLaunchEnabled by remember { mutableStateOf(WakeLaunchSettings.isEnabled(context)) }
    var startsAfterBoot by remember { mutableStateOf(WakeLaunchSettings.startsAfterBoot(context)) }
    var allowSolvedProblems by remember {
        mutableStateOf(preferences.getBoolean("allow_solved_problems", false))
    }
    var solvedProblemKeys by remember {
        mutableStateOf(preferences.getStringSet("solved_problems", emptySet())?.toSet().orEmpty())
    }
    val excludedProblemIds = if (allowSolvedProblems) {
        emptySet()
    } else {
        solvedProblemKeys.mapNotNullTo(mutableSetOf()) { key ->
            key.removePrefix("$language:").takeIf { key.startsWith("$language:") }
        }
    }
    val lessonResult by produceState<Result<Lesson>?>(
        null,
        language,
        problemKey,
        allowedDifficulties,
        excludedProblemIds
    ) {
        value = null
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                repository.lesson(
                    language = language,
                    problemId = selectedProblemId,
                    allowedDifficulties = allowedDifficulties,
                    excludedProblemIds = excludedProblemIds
                )
            }
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
                                preferences.edit { putString("language", language) }
                            }
                            ChoiceChip("Python", language == "python") {
                                language = "python"
                                preferences.edit { putString("language", language) }
                            }
                        }

                        SettingRow("Input mode") {
                            ChoiceChip("Code blocks", mode == ExerciseMode.BLOCKS) {
                                mode = ExerciseMode.BLOCKS
                                preferences.edit { putString("mode", mode.name) }
                            }
                            ChoiceChip("Syntax choices", mode == ExerciseMode.SYNTAX) {
                                mode = ExerciseMode.SYNTAX
                                preferences.edit { putString("mode", mode.name) }
                            }
                        }

                        SettingRow("LeetCode difficulty filter") {
                            ProblemDifficulty.entries.forEach { difficulty ->
                                ChoiceChip(
                                    label = difficulty.displayName,
                                    selected = difficulty in allowedDifficulties,
                                    selectedColor = difficultyColor(difficulty),
                                    selectedContentColor = difficultyContentColor(difficulty)
                                ) {
                                    val updated = if (difficulty in allowedDifficulties) {
                                        allowedDifficulties - difficulty
                                    } else {
                                        allowedDifficulties + difficulty
                                    }
                                    if (updated.isNotEmpty()) {
                                        allowedDifficulties = updated
                                        preferences.edit {
                                            putStringSet(
                                                "problem_difficulties",
                                                updated.mapTo(mutableSetOf()) { it.storageKey }
                                            )
                                        }
                                        selectedProblemId = null
                                        problemKey++
                                    }
                                }
                            }
                        }

                        if (mode == ExerciseMode.SYNTAX) {
                            SettingRow(
                                label = "Next syntax size",
                                description = "Controls how much syntax each correct choice inserts."
                            ) {
                                SyntaxSize.entries.forEach { size ->
                                    ChoiceChip(size.name.lowercase().replaceFirstChar { it.uppercase() }, syntaxSize == size) {
                                        syntaxSize = size
                                        preferences.edit { putString("syntax_size", size.name) }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Allow solved problems", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Include completed problems when choosing a new lesson.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = allowSolvedProblems,
                                onCheckedChange = { enabled ->
                                    allowSolvedProblems = enabled
                                    preferences.edit { putBoolean("allow_solved_problems", enabled) }
                                }
                            )
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
                val solvedKey = "${currentLesson.language}:${currentLesson.problemId}"
                val completeLesson = {
                    val updated = solvedProblemKeys + solvedKey
                    solvedProblemKeys = updated
                    preferences.edit { putStringSet("solved_problems", updated) }
                    onSubmit()
                }
                ProblemCard(currentLesson, solvedKey in solvedProblemKeys)
                if (mode == ExerciseMode.BLOCKS) {
                    BlockExercise(currentLesson, completeLesson)
                } else {
                    SyntaxExercise(currentLesson, syntaxSize, completeLesson)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
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
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    selectedColor: Color? = null,
    selectedContentColor: Color = Color.Black,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = if (selectedColor == null) {
            FilterChipDefaults.filterChipColors()
        } else {
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = selectedColor,
                selectedLabelColor = selectedContentColor
            )
        }
    )
}

private fun difficultyColor(difficulty: ProblemDifficulty): Color = when (difficulty) {
    ProblemDifficulty.BEGINNER -> CodeGateDifficultyBeginner
    ProblemDifficulty.EASY -> CodeGateDifficultyEasy
    ProblemDifficulty.MEDIUM -> CodeGateDifficultyMedium
    ProblemDifficulty.HARD -> CodeGateDifficultyHard
}

private fun difficultyContentColor(difficulty: ProblemDifficulty): Color = when (difficulty) {
    ProblemDifficulty.BEGINNER, ProblemDifficulty.HARD -> Color.White
    ProblemDifficulty.EASY, ProblemDifficulty.MEDIUM -> Color.Black
}

@Composable
private fun DifficultyBadge(difficulty: ProblemDifficulty) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = difficultyColor(difficulty),
        contentColor = difficultyContentColor(difficulty)
    ) {
        Text(
            text = difficulty.displayName,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProblemCard(lesson: Lesson, solved: Boolean) {
    var hintVisible by remember(lesson.id) { mutableStateOf(false) }
    val problemNumber = lesson.problemId.toIntOrNull()?.takeIf { it > 0 }
    val title = problemNumber?.let { "$it. ${lesson.title}" } ?: lesson.title
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                if (solved) SolvedBadge()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyBadge(lesson.difficulty)
                Text(
                    if (lesson.language == "cpp") "C++" else "Python",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
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

@Composable
private fun SolvedBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = CodeGateDifficultyEasy,
        contentColor = Color.Black
    ) {
        Text(
            text = "✓ Solved",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
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
private fun CodePanel(source: String, highlights: List<CodeHighlight> = emptyList()) {
    var caretVisible by remember(source) { mutableStateOf(true) }
    val caretIndex = source.indexOf(CARET_MARKER)
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(source) {
        if (caretIndex < 0) return@LaunchedEffect
        withFrameNanos { }
        verticalScroll.animateScrollTo(verticalScroll.maxValue)
        bringIntoViewRequester.bringIntoView()
    }
    LaunchedEffect(source) {
        if (caretIndex < 0) return@LaunchedEffect
        while (true) {
            delay(500)
            caretVisible = !caretVisible
        }
    }
    val renderedSource = buildAnnotatedString {
        append(source)
        highlights.forEach { highlight ->
            addStyle(
                SpanStyle(
                    background = if (highlight.correct) {
                        CodeGateDifficultyEasy.copy(alpha = 0.32f)
                    } else {
                        CodeGateDifficultyHard.copy(alpha = 0.38f)
                    }
                ),
                start = highlight.start,
                end = highlight.end
            )
        }
        if (caretIndex >= 0 && !caretVisible) {
            addStyle(
                SpanStyle(color = Color.Transparent),
                start = caretIndex,
                end = caretIndex + 1
            )
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        colors = CardDefaults.cardColors(containerColor = CodeGateCodeBackground)
    ) {
        Text(
            text = renderedSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 420.dp)
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
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
    var feedbackVisible by remember(lesson.id) { mutableStateOf(false) }
    var lockedPositions by remember(lesson.id) { mutableStateOf(emptySet<Int>()) }
    val messageRequester = remember { BringIntoViewRequester() }
    val byId = remember(lesson.id) { lesson.blocks.associateBy { it.id } }
    val presentedBlocks = remember(lesson.id) { lesson.blocks.shuffled() }
    val presentationNumberById = remember(lesson.id) {
        presentedBlocks.mapIndexed { index, block -> block.id to index + 1 }.toMap()
    }
    val available = presentedBlocks.filterNot { it.id in selected }
    val selectedBlocks = selected.mapNotNull(byId::get)
    val displayedSelectedBlocks = selectedBlocks.map { block ->
        blockComment(
            language = lesson.language,
            number = presentationNumberById.getValue(block.id),
            source = block.sourceCode
        ) + block.sourceCode
    }
    val assembled = lesson.fixedPrefix + displayedSelectedBlocks
        .joinToString(separator = "") + "\n        $CARET_MARKER" + lesson.fixedSuffix
    val highlights = if (feedbackVisible) {
        buildList {
            var offset = lesson.fixedPrefix.length
            displayedSelectedBlocks.forEachIndexed { index, displayedBlock ->
                add(
                    CodeHighlight(
                        start = offset,
                        end = offset + displayedBlock.length,
                        correct = selected.getOrNull(index) == lesson.correctOrder.getOrNull(index)
                    )
                )
                offset += displayedBlock.length
            }
        }
    } else {
        emptyList()
    }
    val progress = if (lesson.correctOrder.isEmpty()) 1f else selected.size.toFloat() / lesson.correctOrder.size

    LaunchedEffect(checking, selected) {
        if (!checking) return@LaunchedEffect
        delay(350)
        readyToSubmit = selected == lesson.correctOrder
        checking = false
    }

    LaunchedEffect(message) {
        if (message.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        messageRequester.bringIntoView()
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Assembled solution", style = MaterialTheme.typography.titleMedium)
        Text("${selected.size}/${lesson.correctOrder.size}")
    }
    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    CodePanel(assembled, highlights)
    Text("Available blocks", style = MaterialTheme.typography.titleMedium)
    available.forEach { block ->
        CodeChoice(
            code = blockComment(
                language = lesson.language,
                number = presentationNumberById.getValue(block.id)
            ) + block.displayCode,
            onClick = {
                selected = selected + block.id
                message = ""
                readyToSubmit = false
                feedbackVisible = false
            }
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val position = lesson.correctOrder.indices.firstOrNull { index ->
                    selected.getOrNull(index) != lesson.correctOrder[index]
                } ?: return@Button
                val correctBlockId = lesson.correctOrder[position]
                val updated = selected.toMutableList()
                val existingPosition = updated.indexOf(correctBlockId)
                if (existingPosition >= 0) updated.removeAt(existingPosition)
                if (position < updated.size) updated[position] = correctBlockId
                else updated.add(correctBlockId)
                selected = updated
                lockedPositions = lockedPositions + position
                message = ""
                checking = false
                feedbackVisible = false
                readyToSubmit = updated == lesson.correctOrder
            },
            enabled = !readyToSubmit && !checking,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CodeGateDifficultyMedium,
                contentColor = Color.Black
            )
        ) { Text("Help?") }
        OutlinedButton(
            onClick = {
                selected = selected.dropLast(1)
                message = ""
                readyToSubmit = false
                feedbackVisible = false
            },
            enabled = selected.isNotEmpty() && selected.lastIndex !in lockedPositions && !checking,
            shape = RoundedCornerShape(10.dp)
        ) { Text("Undo") }
        TextButton(
            onClick = {
                selected = emptyList()
                message = ""
                checking = false
                readyToSubmit = false
                feedbackVisible = false
                lockedPositions = emptySet()
            }
        ) { Text("Reset") }
        Button(
            onClick = {
                if (readyToSubmit) {
                    onSubmit()
                } else if (selected == lesson.correctOrder) {
                    message = ""
                    feedbackVisible = true
                    checking = true
                } else {
                    feedbackVisible = true
                    message = "Green blocks are correctly placed. Red blocks are in the wrong position."
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
    if (message.isNotEmpty()) {
        Text(
            text = message,
            modifier = Modifier.bringIntoViewRequester(messageRequester),
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SyntaxExercise(lesson: Lesson, size: SyntaxSize, onSubmit: () -> Unit) {
    val engine = remember(lesson.id) { SyntaxChoiceEngine(lesson) }
    var cursor by remember(lesson.id, size) { mutableIntStateOf(0) }
    var history by remember(lesson.id, size) { mutableStateOf(emptyList<Int>()) }
    var message by remember(lesson.id, size) { mutableStateOf("") }
    val messageRequester = remember { BringIntoViewRequester() }
    val choices = remember(lesson.id, size, cursor) { engine.choices(cursor, size) }

    LaunchedEffect(message) {
        if (message.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        messageRequester.bringIntoView()
    }

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
    if (message.isNotEmpty()) {
        Text(
            text = message,
            modifier = Modifier.bringIntoViewRequester(messageRequester),
            color = MaterialTheme.colorScheme.error
        )
    }
}
