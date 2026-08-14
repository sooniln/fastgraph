/**
 * Methods dealing with DOT input/output.
 */
@file:JvmName("Dot")
package io.github.sooniln.fastgraph.io.dot

import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.ValueGraph
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.io.TypeBinding
import io.github.sooniln.fastgraph.io.dot.internal.DotLexer
import io.github.sooniln.fastgraph.io.dot.internal.DotParser
import io.github.sooniln.fastgraph.vertexIdProperty
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

/** Information loaded from a DOT document. */
public interface DotGraph {
    public val graph: Graph
    public val graphId: String?
    public val vertexIdProperty: VertexProperty<out Any>
    public val vertexProperties: Map<String, VertexProperty<*>>
    public val edgeProperties: Map<String, EdgeProperty<*>>
    public val graphProperties: Map<String, Any?>
}

/** A convenient way to construct a [DotGraph] for writing. */
@JvmOverloads
public fun DotGraph(
    graph: Graph,
    graphId: String? = null,
    vertexIdProperty: VertexProperty<out Any> = graph.vertexIdProperty,
    vertexProperties: Map<String, VertexProperty<*>> = emptyMap(),
    edgeProperties: Map<String, EdgeProperty<*>> = emptyMap(),
    graphProperties: Map<String, String> = emptyMap(),
) : DotGraph = object : DotGraph {
    override val graph: Graph get() = graph
    override val graphId: String? get() = graphId
    override val vertexIdProperty: VertexProperty<out Any> get() = vertexIdProperty
    override val vertexProperties: Map<String, VertexProperty<*>> get() = vertexProperties
    override val edgeProperties: Map<String, EdgeProperty<*>> get() = edgeProperties
    override val graphProperties: Map<String, Any?> get() = graphProperties
}

/** A convenient way to construct a [DotGraph] for writing. */
@JvmOverloads
public fun DotGraph(
    graph: ValueGraph<out Any, *>,
    edgePropertyName: String,
    graphId: String? = null,
    graphProperties: Map<String, String> = emptyMap(),
) : DotGraph = object : DotGraph {
    override val graph: Graph get() = graph.graph
    override val graphId: String? get() = graphId
    override val vertexIdProperty: VertexProperty<out Any> get() = graph.vertexProperty
    override val vertexProperties: Map<String, VertexProperty<*>> get() = emptyMap()
    override val edgeProperties: Map<String, EdgeProperty<*>> get() {
        return if (graph.edgeProperty.type.isUnitType()) emptyMap() else mapOf(edgePropertyName to graph.edgeProperty)
    }
    override val graphProperties: Map<String, Any?> get() = graphProperties
}

/** A mutable version of [DotGraph] used for output from DOT reading methods. */
public class MutableDotGraph(
    override val graph: MutableGraph,
    override val graphId: String?,
    override val vertexIdProperty: MutableVertexProperty<out Any>,
    override val vertexProperties: Map<String, MutableVertexProperty<*>>,
    override val edgeProperties: Map<String, MutableEdgeProperty<*>>,
    override val graphProperties: Map<String, Any?>,
) : DotGraph

/**
 * Loads a [MutableDotGraph] from the given [inputStream]. If the DOT graph is specified as `strict` this will override
 * the [multiEdge] argument to false, otherwise the [multiEdge] argument will be respected. The vertex id property type
 * defaults to `String`, but can be overriden via [nodeIdType]. Since DOT attributes have no type information, they are
 * loaded by default as `String?` properties, unless overridden in [attributeTypes] (i.e. if the attribute named
 * "weight" should be loaded as a float, there should be an entry in [attributeTypes] mapping "weight" to
 * [TypeBinding.float]). If you do not want to parse/store a particular attribute, then supply [TypeBinding.unit] for
 * that attribute. The [inputStream] is not closed by this function - that remains the caller's responsibility.
 *
 * Clients are expected to use [io.github.sooniln.fastgraph.safeCast] to convert the output properties in
 * [MutableDotGraph] to the correct types.
 */
@JvmOverloads
@Throws(DotFormatException::class)
public fun readDot(
    inputStream: InputStream,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    nodeIdType: TypeBinding<Any> = TypeBinding.nonNullString,
    attributeTypes: Map<String, TypeBinding<*>> = emptyMap(),
): MutableDotGraph {
    val lexer = DotLexer(inputStream)
    try {
        return DotParser(lexer).parse(multiEdge, indexEdges, nodeIdType, attributeTypes)
    } catch (e: RuntimeException) {
        throw DotFormatException(e.message ?: e.toString(), e)
    }
}

/**
 * Writes [graph] as a DOT document to [outputStream]. The [outputStream] is not closed by this function - that remains
 * the caller's responsibility.
 */
public fun writeDot(outputStream: OutputStream, graph: DotGraph) {
    val writer: Writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
    val keyword = if (graph.graph.directed) "digraph" else "graph"
    val edgeOp = if (graph.graph.directed) "->" else "--"

    if (graph.graph.multiEdge) {
        writer.write("strict ")
    }
    writer.write("$keyword G {\n")

    if (graph.graphProperties.isNotEmpty()) {
        writer.write("  graph")
        writeAttrList(writer, graph.graphProperties)
        writer.write(";\n")
    }

    for (vertex in graph.graph.vertices) {
        writer.write("  ")
        writer.write(quote(graph.vertexIdProperty[vertex]))
        writeAttrList(writer, graph.vertexProperties, vertex) { property, v -> property[v] }
        writer.write(";\n")
    }
    for (edge in graph.graph.edges) {
        writer.write("  ")
        writer.write(quote(graph.vertexIdProperty[graph.graph.edgeSource(edge)]))
        writer.write(" $edgeOp ")
        writer.write(quote(graph.vertexIdProperty[graph.graph.edgeTarget(edge)]))
        writeAttrList(writer, graph.edgeProperties, edge) { property, e -> property[e] }
        writer.write(";\n")
    }

    writer.write("}\n")
    writer.flush()
}

private fun <O, P> writeAttrList(writer: Writer, properties: Map<String, P>, obj: O, value: (P, O) -> Any?) {
    val present = properties.mapNotNull { (name, property) -> value(property, obj)?.let { name to it.toString() } }.toMap()
    writeAttrList(writer, present)
}

private fun writeAttrList(writer: Writer, attrs: Map<String, Any?>) {
    if (attrs.isEmpty()) return
    writer.write(" [")
    attrs.entries.forEachIndexed { i, (name, value) ->
        if (i > 0) writer.write(", ")
        writer.write(quote(name))
        writer.write("=")
        writer.write(quote(value))
    }
    writer.write("]")
}

/**
 * Double-quotes [s], escaping only `"` and `\` - the only two characters DOT's quoted-string syntax recognizes an
 * escape for. A literal newline in [s] is written raw/unescaped: `\n` is a Graphviz rendering-layer convention, not
 * a DOT grammar escape, and `\<newline>` is elided by [DotLexer]'s line-continuation handling on read - either
 * would corrupt round-tripping through this library's own reader/writer.
 */
private fun quote(s: Any?): String = buildString {
    append('"')
    for (c in s.toString()) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        else -> append(c)
    }
    append('"')
}
