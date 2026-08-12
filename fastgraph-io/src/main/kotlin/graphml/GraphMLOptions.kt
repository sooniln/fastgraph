package io.github.sooniln.fastgraph.io.graphml

import java.nio.charset.Charset

public class GraphMLOptions {
    /** The charset used for the writer's output encoding and XML prolog. Defaults to UTF-8. */
    public var charset: Charset = Charsets.UTF_8

    /** If true, the writer's output is pretty-printed with indentation. Defaults to true. */
    public var indent: Boolean = true

    /** If true, the writer emits GraphML-Parseinfo `parse.*` attributes. Defaults to true. */
    public var writeParseInfo: Boolean = true
}
