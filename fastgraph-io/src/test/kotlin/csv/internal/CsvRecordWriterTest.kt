package io.github.sooniln.fastgraph.io.csv.internal

import io.github.sooniln.fastgraph.io.csv.CsvOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class CsvRecordWriterTest {

    private fun write(configure: CsvOptions.() -> Unit = {}, block: CsvRecordWriter.() -> Unit): String {
        val options = CsvOptions().apply(configure)
        val output = ByteArrayOutputStream()
        val writer = CsvRecordWriter(output, options)
        writer.block()
        writer.flush()
        return output.toString(options.charset)
    }

    @Test
    fun simpleFieldsAreWrittenPlainly() {
        val text = write { writeRecord(listOf("a", "b", "c")) }
        assertThat(text).isEqualTo("a,b,c\n")
    }

    @Test
    fun multipleRecordsAreEachTerminatedByNewline() {
        val text = write {
            writeRecord(listOf("a", "b"))
            writeRecord(listOf("c", "d"))
        }
        assertThat(text).isEqualTo("a,b\nc,d\n")
    }

    @Test
    fun emptyFieldIsWrittenAsNothing() {
        val text = write { writeRecord(listOf("a", "", "c")) }
        assertThat(text).isEqualTo("a,,c\n")
    }

    @Test
    fun fieldContainingDelimiterIsQuoted() {
        val text = write { writeRecord(listOf("a,b", "c")) }
        assertThat(text).isEqualTo("\"a,b\",c\n")
    }

    @Test
    fun fieldContainingQuoteIsQuotedAndEscaped() {
        val text = write { writeRecord(listOf("a\"b", "c")) }
        assertThat(text).isEqualTo("\"a\"\"b\",c\n")
    }

    @Test
    fun fieldContainingNewlineIsQuoted() {
        val text = write { writeRecord(listOf("a\nb", "c")) }
        assertThat(text).isEqualTo("\"a\nb\",c\n")
    }

    @Test
    fun fieldWithNoSpecialCharactersIsNotQuoted() {
        val text = write { writeRecord(listOf("plain")) }
        assertThat(text).isEqualTo("plain\n")
    }

    @Test
    fun customDelimiterIsRespected() {
        val text = write({ delimiter = '\t' }) { writeRecord(listOf("a", "b")) }
        assertThat(text).isEqualTo("a\tb\n")
    }

    @Test
    fun customQuoteCharacterIsRespected() {
        val text = write({ quote = '\'' }) { writeRecord(listOf("a,b", "c")) }
        assertThat(text).isEqualTo("'a,b',c\n")
    }

    @Test
    fun fieldContainingCustomDelimiterIsQuotedUsingCustomQuote() {
        val text = write({ delimiter = '\t'; quote = '\'' }) { writeRecord(listOf("a\tb", "c")) }
        assertThat(text).isEqualTo("'a\tb'\tc\n")
    }
}
