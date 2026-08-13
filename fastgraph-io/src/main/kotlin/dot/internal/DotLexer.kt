package io.github.sooniln.fastgraph.io.dot.internal

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

internal enum class TokenType {
    STRICT, GRAPH, DIGRAPH, NODE, EDGE, SUBGRAPH,
    ID,
    LBRACE, RBRACE, LBRACKET, RBRACKET,
    EDGEOP_DIRECTED, EDGEOP_UNDIRECTED,
    EQUALS, COLON, SEMICOLON, COMMA, PLUS,
    EOF
}

internal class Token(val type: TokenType, val text: String, val line: Int, val column: Int)

private val KEYWORDS = mapOf(
    "strict" to TokenType.STRICT,
    "graph" to TokenType.GRAPH,
    "digraph" to TokenType.DIGRAPH,
    "node" to TokenType.NODE,
    "edge" to TokenType.EDGE,
    "subgraph" to TokenType.SUBGRAPH,
)

/**
 * Hand-rolled DOT-language tokenizer. Emits one [Token] at a time via [nextToken]. Has no knowledge of
 * brace/bracket nesting - that's entirely [DotParser]'s responsibility. Always reads as UTF-8.
 */
internal class DotLexer(input: InputStream) {

    private val reader: Reader = InputStreamReader(input, Charsets.UTF_8)
    private val buffer = CharArray(8192)
    private var bufferLen = 0
    private var bufferPos = 0
    private var eof = false
    private val lookahead = ArrayDeque<Int>()

    /** The 1-based line/column the lexer is currently positioned at, for use in error messages. */
    var line = 1
        private set
    var column = 1
        private set
    private var lineHasContent = false

    private fun fill() {
        if (!eof) {
            bufferLen = reader.read(buffer)
            bufferPos = 0
            if (bufferLen <= 0) {
                eof = true
                bufferLen = 0
            }
        }
    }

    private fun rawRead(): Int {
        if (bufferPos >= bufferLen) fill()
        return if (bufferPos >= bufferLen) -1 else buffer[bufferPos++].code
    }

    private fun peek(offset: Int = 0): Int {
        while (lookahead.size <= offset) lookahead.addLast(rawRead())
        return lookahead[offset]
    }

    private fun next(): Int {
        val c = if (lookahead.isEmpty()) rawRead() else lookahead.removeFirst()
        if (c != -1) {
            column++
            if (c == '\n'.code || (c == '\r'.code && peek() != '\n'.code)) {
                line++
                column = 1
                lineHasContent = false
            } else if (c != ' '.code && c != '\t'.code) {
                lineHasContent = true
            }
        }
        return c
    }

    private fun lexError(message: String): Nothing = throw IllegalArgumentException("$message (line $line:$column)")

    /**
     * Skips whitespace and comments (`//...`, `/* ... */`, and a line whose first non-whitespace character is
     * `#`). No backslash-newline continuation is honored for `#` comments - that's not part of the DOT spec.
     */
    private fun skipWhitespaceAndComments() {
        while (true) {
            when (val c = peek()) {
                -1 -> return
                ' '.code, '\t'.code, '\r'.code, '\n'.code -> next()
                '#'.code -> if (!lineHasContent) skipToEndOfLine() else return
                '/'.code -> when (peek(1)) {
                    '/'.code -> { next(); next(); skipToEndOfLine() }
                    '*'.code -> { next(); next(); skipBlockComment() }
                    else -> return
                }
                else -> return
            }
        }
    }

    private fun skipToEndOfLine() {
        while (true) {
            val c = peek()
            if (c == -1 || c == '\n'.code || c == '\r'.code) return
            next()
        }
    }

    private fun skipBlockComment() {
        while (true) {
            val c = next()
            if (c == -1) lexError("unterminated block comment")
            if (c == '*'.code && peek() == '/'.code) {
                next()
                return
            }
        }
    }

    private fun isIdStart(c: Int): Boolean =
        c == '_'.code || c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c >= 0x80

    private fun isIdContinue(c: Int): Boolean = isIdStart(c) || c in '0'.code..'9'.code

    /** Barewords are matched case-insensitively against DOT's reserved words; anything else stays an [TokenType.ID]. */
    private fun scanBarewordOrKeyword(line: Int, column: Int): Token {
        val sb = StringBuilder()
        while (isIdContinue(peek())) sb.append(next().toChar())
        val text = sb.toString()
        return Token(KEYWORDS[text.lowercase()] ?: TokenType.ID, text, line, column)
    }

    private fun scanNumeral(line: Int, column: Int, prefix: String): Token {
        val sb = StringBuilder(prefix)
        while (true) {
            val c = peek()
            if (c == -1 || (c.toChar() != '.' && !c.toChar().isDigit())) break
            sb.append(next().toChar())
        }
        return Token(TokenType.ID, sb.toString(), line, column)
    }

    /**
     * `\"` is an escaped quote, `\\` is an escaped backslash, `\` followed by a newline is an elided line
     * continuation, and any other `\x` is kept literally. `\\` must round-trip to a single backslash (rather than
     * being kept literally like other `\x` sequences) so that [io.github.sooniln.fastgraph.io.dot.writeDot] can
     * escape a value's literal backslashes unambiguously - otherwise a value ending in a backslash, written just
     * before the closing quote, would be indistinguishable from an escaped closing quote. A raw, unescaped newline
     * is appended as literal content rather than terminating the string, so that [io.github.sooniln.fastgraph.io.dot.writeDot]
     * can round-trip multi-line values.
     */
    private fun scanQuotedString(line: Int, column: Int): Token {
        next() // opening quote
        val sb = StringBuilder()
        while (true) {
            when (val c = next()) {
                -1 -> lexError("unterminated quoted string")
                '"'.code -> return Token(TokenType.ID, sb.toString(), line, column)
                '\\'.code -> when (val n = peek()) {
                    '"'.code -> { sb.append('"'); next() }
                    '\\'.code -> { sb.append('\\'); next() }
                    '\n'.code -> next()
                    '\r'.code -> { next(); if (peek() == '\n'.code) next() }
                    -1 -> lexError("unterminated quoted string")
                    else -> { sb.append('\\').append(n.toChar()); next() }
                }
                else -> sb.append(c.toChar())
            }
        }
    }

    /** Depth-counts nested `<`/`>` to find the true outer boundary; content is never validated as XML/HTML. */
    private fun scanHtmlString(line: Int, column: Int): Token {
        next() // opening '<'
        val sb = StringBuilder()
        var depth = 1
        while (true) {
            when (val c = next()) {
                -1 -> lexError("unterminated HTML string")
                '<'.code -> { depth++; sb.append('<') }
                '>'.code -> {
                    depth--
                    if (depth == 0) return Token(TokenType.ID, sb.toString(), line, column)
                    sb.append('>')
                }
                else -> sb.append(c.toChar())
            }
        }
    }

    private fun scanDashOrNumeral(line: Int, column: Int): Token {
        next() // consume '-'
        return when (val c = peek()) {
            '>'.code -> { next(); Token(TokenType.EDGEOP_DIRECTED, "->", line, column) }
            '-'.code -> { next(); Token(TokenType.EDGEOP_UNDIRECTED, "--", line, column) }
            else -> if (c != -1 && (c.toChar() == '.' || c.toChar().isDigit())) {
                scanNumeral(line, column, "-")
            } else {
                lexError("unexpected '-'")
            }
        }
    }

    fun nextToken(): Token {
        skipWhitespaceAndComments()
        val startLine = line
        val startColumn = column
        return when (val c = peek()) {
            -1 -> Token(TokenType.EOF, "", startLine, startColumn)
            '"'.code -> scanQuotedString(startLine, startColumn)
            '<'.code -> scanHtmlString(startLine, startColumn)
            '{'.code -> { next(); Token(TokenType.LBRACE, "{", startLine, startColumn) }
            '}'.code -> { next(); Token(TokenType.RBRACE, "}", startLine, startColumn) }
            '['.code -> { next(); Token(TokenType.LBRACKET, "[", startLine, startColumn) }
            ']'.code -> { next(); Token(TokenType.RBRACKET, "]", startLine, startColumn) }
            ':'.code -> { next(); Token(TokenType.COLON, ":", startLine, startColumn) }
            ';'.code -> { next(); Token(TokenType.SEMICOLON, ";", startLine, startColumn) }
            ','.code -> { next(); Token(TokenType.COMMA, ",", startLine, startColumn) }
            '='.code -> { next(); Token(TokenType.EQUALS, "=", startLine, startColumn) }
            '+'.code -> { next(); Token(TokenType.PLUS, "+", startLine, startColumn) }
            '-'.code -> scanDashOrNumeral(startLine, startColumn)
            else -> when {
                isIdStart(c) -> scanBarewordOrKeyword(startLine, startColumn)
                c.toChar() == '.' || c.toChar().isDigit() -> scanNumeral(startLine, startColumn, "")
                else -> lexError("unexpected character '${c.toChar()}'")
            }
        }
    }
}
