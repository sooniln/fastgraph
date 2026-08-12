package io.github.sooniln.fastgraph.io.csv.internal

import io.github.sooniln.fastgraph.io.csv.CsvFormatException
import io.github.sooniln.fastgraph.io.csv.CsvOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CsvRecordReaderTest {

    private fun records(text: String, configure: CsvOptions.() -> Unit = {}): List<List<String>> {
        val options = CsvOptions().apply(configure)
        val reader = CsvRecordReader(text.byteInputStream(options.charset), options)
        val result = mutableListOf<List<String>>()
        var record = reader.nextRecord()
        while (record != null) {
            // nextRecord() reuses its backing list across calls, so snapshot it before advancing.
            result.add(record.toList())
            record = reader.nextRecord()
        }
        return result
    }

    @Test
    fun emptyInputYieldsNoRecords() {
        assertThat(records("")).isEmpty()
    }

    @Test
    fun simpleUnquotedFields() {
        assertThat(records("a,b,c\n")).containsExactly(listOf("a", "b", "c"))
    }

    @Test
    fun multipleRecords() {
        assertThat(records("a,b\nc,d\n")).containsExactly(listOf("a", "b"), listOf("c", "d"))
    }

    @Test
    fun noTrailingNewlineStillYieldsLastRecord() {
        assertThat(records("a,b")).containsExactly(listOf("a", "b"))
    }

    @Test
    fun crlfLineEndings() {
        assertThat(records("a,b\r\nc,d\r\n")).containsExactly(listOf("a", "b"), listOf("c", "d"))
    }

    @Test
    fun bareCrLineEndings() {
        assertThat(records("a,b\rc,d\r")).containsExactly(listOf("a", "b"), listOf("c", "d"))
    }

    @Test
    fun blankLinesAreSkipped() {
        assertThat(records("a,b\n\n\nc,d\n")).containsExactly(listOf("a", "b"), listOf("c", "d"))
    }

    @Test
    fun quotedFieldWithEmbeddedDelimiterAndNewline() {
        assertThat(records("\"a,b\nc\",d\n")).containsExactly(listOf("a,b\nc", "d"))
    }

    @Test
    fun doubledQuoteIsAnEscapedLiteralQuote() {
        assertThat(records("\"a\"\"b\",c\n")).containsExactly(listOf("a\"b", "c"))
    }

    @Test
    fun unterminatedQuoteThrows() {
        assertThrows<CsvFormatException> { records("\"a,b\n") }
    }

    @Test
    fun garbageAfterClosingQuoteThrows() {
        assertThrows<CsvFormatException> { records("\"a\"b,c\n") }
    }

    @Test
    fun customDelimiter() {
        assertThat(records("a\tb\n") { delimiter = '\t' }).containsExactly(listOf("a", "b"))
    }

    @Test
    fun customQuoteCharacter() {
        assertThat(records("'a,b',c\n") { quote = '\'' }).containsExactly(listOf("a,b", "c"))
    }

    @Test
    fun commentLinesAreSkippedWhenConfigured() {
        assertThat(records("# hello\na,b\n") { comment = '#' }).containsExactly(listOf("a", "b"))
    }

    @Test
    fun commentCharacterIsLiteralWhenNotConfigured() {
        assertThat(records("#hello,b\n")).containsExactly(listOf("#hello", "b"))
    }

    @Test
    fun trimFieldsTrimsUnquotedFieldWhitespace() {
        assertThat(records("  a  , b \n") { trimFields = true }).containsExactly(listOf("a", "b"))
    }

    @Test
    fun trimFieldsDoesNotTrimInsideQuotes() {
        assertThat(records("\" a \",b\n") { trimFields = true }).containsExactly(listOf(" a ", "b"))
    }

    @Test
    fun leadingWhitespaceBeforeQuoteDisablesQuotingEvenWithTrimFields() {
        assertThat(records(" \"a\",b\n") { trimFields = true }).containsExactly(listOf("\"a\"", "b"))
    }

    @Test
    fun nextRecordReusesItsBackingList() {
        val options = CsvOptions()
        val reader = CsvRecordReader("a,b\nc,d\n".byteInputStream(options.charset), options)

        val first = reader.nextRecord()
        val second = reader.nextRecord()

        assertThat(first).isSameAs(second)
        assertThat(second).containsExactly("c", "d")
    }

    @Test
    fun unterminatedQuoteReportsLineNumber() {
        // The embedded newline inside the unterminated quote is itself a real physical line, so EOF is hit on the
        // line after the one the field started on.
        val exception = assertThrows<CsvFormatException> { records("a,b\nc,\"d\n") }
        assertThat(exception.message).contains("line 3")
    }

    @Test
    fun garbageAfterClosingQuoteReportsLineNumber() {
        val exception = assertThrows<CsvFormatException> { records("a,b\n\"c\"d,e\n") }
        assertThat(exception.message).contains("line 2")
    }

    @Test
    fun throwFormatExceptionIncludesCurrentLineNumber() {
        val options = CsvOptions()
        val reader = CsvRecordReader("a,b\nc,d\n".byteInputStream(options.charset), options)
        reader.nextRecord()
        reader.nextRecord()

        val exception = assertThrows<CsvFormatException> { reader.throwFormatException("bad value") }

        assertThat(exception.message).isEqualTo("bad value (line 2)")
        assertThat(exception.cause).isNull()
    }

    @Test
    fun throwFormatExceptionWithCauseWrapsIt() {
        val options = CsvOptions()
        val reader = CsvRecordReader("a,b\n".byteInputStream(options.charset), options)
        val cause = NumberFormatException("not a number")

        val exception = assertThrows<CsvFormatException> { reader.throwFormatException(cause) }

        assertThat(exception.message).isEqualTo("not a number (line 1)")
        assertThat(exception.cause).isSameAs(cause)
    }
}
