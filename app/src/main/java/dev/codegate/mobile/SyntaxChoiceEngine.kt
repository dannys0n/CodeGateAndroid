package dev.codegate.mobile

import kotlin.random.Random

enum class SyntaxSize { SMALL, MEDIUM, LARGE }

data class SyntaxChoice(val code: String, val correct: Boolean)

private enum class TokenKind { IDENTIFIER, KEYWORD, NUMBER, STRING, OPERATOR, PUNCTUATION }

private data class CodeToken(
    val leading: String,
    val text: String,
    val kind: TokenKind
)

class SyntaxChoiceEngine(private val lesson: Lesson) {
    private val keywords = if (lesson.language == "python") PYTHON_KEYWORDS else CPP_KEYWORDS
    private val tokens = lex(lesson.referenceBody)
    private val prefixIdentifiers = lex(lesson.fixedPrefix)
        .filter { it.kind == TokenKind.IDENTIFIER }
        .map { it.text }
        .toSet()

    val tokenCount: Int get() = tokens.size

    fun nextEnd(cursor: Int, size: SyntaxSize): Int {
        if (cursor >= tokens.size) return tokens.size
        val maximum = when (size) {
            SyntaxSize.SMALL -> 1
            SyntaxSize.MEDIUM -> 4
            SyntaxSize.LARGE -> 16
        }
        var end = cursor
        while (end < tokens.size && end - cursor < maximum) {
            if (end > cursor && size != SyntaxSize.SMALL && tokens[end].leading.contains('\n')) break
            end++
            val text = tokens[end - 1].text
            if (size != SyntaxSize.SMALL && text in setOf(";", "{", "}", ":")) break
        }
        return end.coerceAtLeast(cursor + 1)
    }

    fun choices(cursor: Int, size: SyntaxSize): List<SyntaxChoice> {
        if (cursor >= tokens.size) return emptyList()
        val end = nextEnd(cursor, size)
        val correctText = displayChunk(cursor, end)
        val replacementIndex = (cursor until end).firstOrNull {
            tokens[it].kind != TokenKind.PUNCTUATION
        } ?: cursor
        val alternatives = alternativesFor(replacementIndex, cursor)
            .filter { it != tokens[replacementIndex].text }
            .distinct()
            .take(3)
            .map { replacement ->
                SyntaxChoice(displayChunk(cursor, end, replacementIndex, replacement), false)
            }
        val fallback = fallbackAlternatives(tokens[replacementIndex])
            .filter { it != tokens[replacementIndex].text }
            .map { replacement ->
                SyntaxChoice(displayChunk(cursor, end, replacementIndex, replacement), false)
            }
        return (listOf(SyntaxChoice(correctText, true)) + alternatives + fallback)
            .distinctBy { it.code }
            .take(4)
            .shuffled(Random(lesson.id.hashCode() * 31 + cursor * 7 + size.ordinal))
    }

    fun renderedSource(cursor: Int): String {
        val accepted = tokens.take(cursor).joinToString(separator = "") { it.leading + it.text }
        val marker = if (cursor < tokens.size) "\n        ▌" else ""
        return lesson.fixedPrefix + accepted + marker + lesson.fixedSuffix
    }

    private fun displayChunk(
        start: Int,
        end: Int,
        replacementIndex: Int = -1,
        replacement: String = ""
    ): String = buildString {
        for (index in start until end) {
            append(if (index == start) tokens[index].leading.trimStart() else tokens[index].leading)
            append(if (index == replacementIndex) replacement else tokens[index].text)
        }
    }.trim()

    private fun alternativesFor(index: Int, cursor: Int): List<String> {
        val token = tokens[index]
        val previous = tokens.getOrNull(index - 1)?.text
        return when (token.kind) {
            TokenKind.IDENTIFIER -> {
                if (previous == "." || previous == "->") {
                    if (lesson.language == "python") PYTHON_MEMBERS else CPP_MEMBERS
                } else {
                    val inScope = (prefixIdentifiers + tokens.take(cursor)
                        .filter { it.kind == TokenKind.IDENTIFIER }
                        .map { it.text })
                    inScope.toList() + if (lesson.language == "python") PYTHON_NAMES else CPP_NAMES
                }
            }
            TokenKind.KEYWORD -> if (lesson.language == "python") PYTHON_KEYWORDS.toList() else CPP_KEYWORDS.toList()
            TokenKind.OPERATOR -> OPERATOR_FAMILIES.firstOrNull { token.text in it }?.toList().orEmpty()
            TokenKind.NUMBER -> listOf("0", "1", "-1", "2")
            TokenKind.STRING -> listOf("\"\"", "\"a\"", "'a'", "' '")
            TokenKind.PUNCTUATION -> PUNCTUATION
        }
    }

    private fun fallbackAlternatives(token: CodeToken): List<String> = when (token.kind) {
        TokenKind.IDENTIFIER -> listOf("result", "value", "index")
        TokenKind.KEYWORD -> listOf("if", "for", "return")
        TokenKind.NUMBER -> listOf("0", "1", "2")
        TokenKind.STRING -> listOf("\"\"", "'a'", "\"value\"")
        TokenKind.OPERATOR -> listOf("+", "-", "==", "<")
        TokenKind.PUNCTUATION -> PUNCTUATION
    }

    private fun lex(source: String): List<CodeToken> {
        val result = mutableListOf<CodeToken>()
        var index = 0
        var leading = ""
        while (index < source.length) {
            val char = source[index]
            if (char.isWhitespace()) {
                leading += char
                index++
                continue
            }
            if ((lesson.language != "python" && source.startsWith("//", index)) || (lesson.language == "python" && char == '#')) {
                val end = source.indexOf('\n', index).let { if (it < 0) source.length else it }
                leading += source.substring(index, end)
                index = end
                continue
            }
            if (source.startsWith("/*", index)) {
                val end = source.indexOf("*/", index + 2).let { if (it < 0) source.length else it + 2 }
                leading += source.substring(index, end)
                index = end
                continue
            }
            val start = index
            val kind = when {
                char.isLetter() || char == '_' -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                    if (source.substring(start, index) in keywords) TokenKind.KEYWORD else TokenKind.IDENTIFIER
                }
                char.isDigit() -> {
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] in ".xX_")) index++
                    TokenKind.NUMBER
                }
                char == '\'' || char == '"' -> {
                    val quote = char
                    index++
                    while (index < source.length) {
                        if (source[index] == '\\') index += 2
                        else if (source[index++] == quote) break
                    }
                    index = index.coerceAtMost(source.length)
                    TokenKind.STRING
                }
                else -> {
                    val operator = OPERATORS.firstOrNull { source.startsWith(it, index) }
                    if (operator != null) {
                        index += operator.length
                        TokenKind.OPERATOR
                    } else {
                        index++
                        if (char in "+-*/%=<>!&|^~?:") TokenKind.OPERATOR else TokenKind.PUNCTUATION
                    }
                }
            }
            result += CodeToken(leading, source.substring(start, index), kind)
            leading = ""
        }
        return result
    }

    companion object {
        private val CPP_KEYWORDS = setOf("auto", "bool", "break", "case", "char", "const", "continue", "double", "else", "false", "float", "for", "if", "int", "long", "return", "string", "true", "vector", "void", "while")
        private val PYTHON_KEYWORDS = setOf("and", "break", "continue", "def", "elif", "else", "False", "for", "if", "in", "is", "lambda", "None", "not", "or", "pass", "return", "True", "while")
        private val CPP_MEMBERS = listOf("begin", "end", "find", "contains", "insert", "erase", "size", "empty", "push_back", "pop_back", "front", "back")
        private val PYTHON_MEMBERS = listOf("append", "add", "get", "items", "keys", "values", "pop", "sort", "reverse", "count", "index")
        private val CPP_NAMES = listOf("result", "value", "index", "left", "right", "current", "count")
        private val PYTHON_NAMES = listOf("result", "value", "index", "left", "right", "current", "count")
        private val OPERATOR_FAMILIES = listOf(setOf("+", "-", "*", "/", "%"), setOf("==", "!=", "<", ">", "<=", ">="), setOf("&&", "||", "and", "or"), setOf("++", "--", "+=", "-=", "*=", "/="))
        private val PUNCTUATION = listOf("(", ")", "[", "]", "{", "}", ",", ";", ":")
        private val OPERATORS = listOf("<=>", ">>=", "<<=", "->*", "::", "->", "++", "--", "==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=", "<<", ">>", "**", "//", ":=")
    }
}
