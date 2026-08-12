package io.github.sooniln.fastgraph.io.csv.internal

import io.github.sooniln.fastgraph.io.csv.CsvOptions
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

/**
 * Hand-rolled CSV record writer - the write-side counterpart to [CsvRecordReader]. Writes records (rows of fields)
 * one at a time to [output], quoting/escaping fields as needed per the delimiter/quote/charset settings in
 * [options].
 */
internal class CsvRecordWriter(output: OutputStream, private val options: CsvOptions) {

    private val writer: Writer = OutputStreamWriter(output, options.charset)

    /** Writes one CSV record (row) for [fields], terminated by a newline. */
    fun writeRecord(fields: List<String>) {
        for (i in fields.indices) {
            if (i > 0) writer.write(options.delimiter.code)
            writeField(fields[i])
        }
        writer.write('\n'.code)
    }

    private fun writeField(field: String) {
        if (!needsQuoting(field)) {
            writer.write(field)
            return
        }

        writer.write(options.quote.code)
        for (c in field) {
            if (c == options.quote) writer.write(options.quote.code)
            writer.write(c.code)
        }
        writer.write(options.quote.code)
    }

    private fun needsQuoting(field: String): Boolean =
        field.any { it == options.delimiter || it == options.quote || it == '\n' || it == '\r' }

    /** Flushes any buffered output to the underlying stream. Does not close it. */
    fun flush() {
        writer.flush()
    }
}
