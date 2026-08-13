package io.github.sooniln.fastgraph.io.csv

import java.nio.charset.Charset

public class CsvOptions(
    /** The field delimiter. Defaults to `,`. */
    public var delimiter: Char = ',',

    /** The character used to quote fields (i.e. with a delimiter, quote, or whitespace). Defaults to `"`. */
    public var quote: Char = '"',

    /** If true, the first non-blank, non-comment line is treated as a header and is skipped. Defaults to false. */
    public var hasHeader: Boolean = false,

    /** If non-null, lines with this first character are skipped entirely. Defaults to null (disabled). */
    public var comment: Char? = null,

    /** The charset used to decode the input. Defaults to UTF-8. */
    public var charset: Charset = Charsets.UTF_8,

    /** If true, leading/trailing whitespace is trimmed from fields (before parsing any quoting). Defaults to false. */
    public var trimFields: Boolean = false,
)
