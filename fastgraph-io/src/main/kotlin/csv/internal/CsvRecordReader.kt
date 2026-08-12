package io.github.sooniln.fastgraph.io.csv.internal

import io.github.sooniln.fastgraph.io.csv.CsvFormatException
import io.github.sooniln.fastgraph.io.csv.CsvOptions
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

/**
 * Hand-rolled CSV record tokenizer. Reads records (rows of fields) one at a time from [input], honoring the
 * delimiter/quote/comment/trimFields/charset settings in [options].
 */
internal class CsvRecordReader(input: InputStream, private val options: CsvOptions) {

    private val reader: Reader = InputStreamReader(input, options.charset)
    private val buffer = CharArray(8192)
    private var bufferLen = 0
    private var bufferPos = 0
    private var eof = false
    private val field = StringBuilder()
    private val fields = ArrayList<String>(4)

    /** The 1-based line the reader is currently positioned on, for use in [throwFormatException]. */
    private var lineNumber = 1

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

    private fun peek(): Int {
        if (bufferPos >= bufferLen) fill()
        return if (bufferPos >= bufferLen) -1 else buffer[bufferPos].code
    }

    private fun next(): Int {
        val c = peek()
        if (c != -1) {
            bufferPos++
            // Count "\n" and a lone "\r" (one not part of a "\r\n" pair, which is counted via the "\n" it precedes)
            // as a single line each, wherever they're consumed from - a line terminator ending a record, or a
            // literal embedded in a quoted field.
            if (c == '\n'.code || (c == '\r'.code && peek() != '\n'.code)) {
                lineNumber++
            }
        }
        return c
    }

    private fun isLineTerminator(c: Int): Boolean = c == '\n'.code || c == '\r'.code

    private fun consumeLineTerminator() {
        val c = next()
        if (c == '\r'.code && peek() == '\n'.code) next()
    }

    /** Throws a [CsvFormatException] for [message], annotated with the current line number. */
    fun throwFormatException(message: String, cause: Throwable? = null): Nothing {
        throw CsvFormatException("$message (line $lineNumber)", cause)
    }

    /** Throws a [CsvFormatException] wrapping [cause], annotated with the current line number. */
    fun throwFormatException(cause: RuntimeException): Nothing =
        throwFormatException(cause.message ?: cause.toString(), cause)

    private fun skipLine() {
        while (true) {
            val c = peek()
            if (c == -1) return
            if (isLineTerminator(c)) {
                consumeLineTerminator()
                return
            }
            next()
        }
    }

    private fun readQuotedField(): String {
        val quote = options.quote.code
        while (true) {
            val c = next()
            if (c == -1) throwFormatException("Unterminated quoted field")
            if (c == quote) {
                if (peek() == quote) {
                    next()
                    field.append(options.quote)
                } else {
                    break
                }
            } else {
                field.append(c.toChar())
            }
        }
        val c = peek()
        if (c != -1 && c != options.delimiter.code && !isLineTerminator(c)) {
            throwFormatException("Unexpected character after closing quote")
        }
        return field.toString()
    }

    private fun readUnquotedField(): String {
        while (true) {
            val c = peek()
            if (c == -1 || c == options.delimiter.code || isLineTerminator(c)) break
            field.append(c.toChar())
            next()
        }
        val result = field.toString()
        return if (options.trimFields) result.trim() else result
    }

    private fun readField(): String {
        field.setLength(0)
        return if (peek() == options.quote.code) {
            next()
            readQuotedField()
        } else {
            readUnquotedField()
        }
    }

    /**
     * Returns the next record's fields, or null if there are no more records (end of input). The returned list is
     * reused across calls - it is only valid until the next call to [nextRecord].
     */
    fun nextRecord(): List<String>? {
        while (true) {
            val c = peek()
            if (c == -1) return null
            if (isLineTerminator(c)) {
                consumeLineTerminator()
                continue
            }
            val comment = options.comment
            if (comment != null && c == comment.code) {
                skipLine()
                continue
            }
            break
        }

        // Note: the line terminator ending this record (if any) is deliberately left unconsumed here - consuming it
        // now would advance lineNumber past the record just parsed, before the caller has had a chance to inspect
        // or report an error against it. It's consumed lazily by the next call's leading skip-loop instead.
        fields.clear()
        while (true) {
            fields.add(readField())
            if (peek() == options.delimiter.code) {
                next()
                continue
            }
            break
        }
        return fields
    }
}
