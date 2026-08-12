/**
 * Methods dealing with graphs.
 */
@file:JvmName("CsvEdgeList")
package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableValueGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.ValueGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.io.csv.internal.CsvRecordReader
import io.github.sooniln.fastgraph.io.csv.internal.CsvRecordWriter
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.mutableValueGraph
import io.github.sooniln.fastgraph.staticTypeOf
import io.github.sooniln.fastgraph.vertexIdProperty
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/** Information loaded from a CSV edge list. */
public class CsvEdgeListGraph<V>(
    public val graph: MutableGraph,
    public val vertexProperty: MutableVertexProperty<V>,
    public val edgeProperties: List<MutableEdgeProperty<*>>,
)

@Throws(CsvFormatException::class)
public fun readCsvEdgeList(
    path: Path,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    graphInitializer: MutableGraph.() -> Unit = {},
    options: CsvOptions.() -> Unit = {}
): MutableGraph = Files.newInputStream(path).use { inputStream ->
    readCsvEdgeList(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        graphInitializer,
        options)
}

@Throws(CsvFormatException::class)
public fun readCsvEdgeList(
    inputStream: InputStream,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    graphInitializer: MutableGraph.() -> Unit = {},
    options: CsvOptions.() -> Unit = {}
): MutableGraph {
    return readCsvEdgeList(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        CsvVertexField.unit,
        emptyList(),
        graphInitializer,
        options
    ).graph
}

@Throws(CsvFormatException::class)
public inline fun <reified V, reified E> readCsvEdgeList(
    path: Path,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    noinline graphInitializer: MutableGraph.() -> Unit = {},
    noinline options: CsvOptions.() -> Unit = {}
): MutableValueGraph<V, E> = Files.newInputStream(path).use { inputStream ->
    readCsvEdgeList<V, E>(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        graphInitializer,
        options)
}

@Throws(CsvFormatException::class)
public inline fun <reified V, reified E> readCsvEdgeList(
    inputStream: InputStream,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    noinline graphInitializer: MutableGraph.() -> Unit = {},
    noinline options: CsvOptions.() -> Unit = {}
): MutableValueGraph<V, E> {
    return readCsvEdgeList(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        defaultCsvVertexField<V>(),
        defaultCsvEdgeField<E>(),
        graphInitializer,
        options
    )
}

@Throws(CsvFormatException::class)
public fun <V, E> readCsvEdgeList(
    path: Path,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    vertexColumn: CsvVertexField<V>,
    edgeColumn: CsvEdgeField<E>,
    graphInitializer: MutableGraph.() -> Unit = {},
    options: CsvOptions.() -> Unit = {}
): MutableValueGraph<V, E> = Files.newInputStream(path).use { inputStream ->
    readCsvEdgeList(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        vertexColumn,
        edgeColumn,
        graphInitializer,
        options
    )
}

@Throws(CsvFormatException::class)
public fun <V, E> readCsvEdgeList(
    inputStream: InputStream,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    vertexColumn: CsvVertexField<V>,
    edgeColumn: CsvEdgeField<E>,
    graphInitializer: MutableGraph.() -> Unit = {},
    options: CsvOptions.() -> Unit = {}
): MutableValueGraph<V, E> {
    val edgeColumns = if (edgeColumn.type == staticTypeOf<Unit>()) emptyList() else listOf(edgeColumn)
    val graph = readCsvEdgeList(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        vertexColumn,
        edgeColumns,
        graphInitializer,
        options
    )
    @Suppress("UNCHECKED_CAST")
    return mutableValueGraph(graph.graph, graph.vertexProperty, graph.edgeProperties.first() as MutableEdgeProperty<E>)
}

@Throws(CsvFormatException::class)
public fun <V> readCsvEdgeList(
    path: Path,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    vertexColumn: CsvVertexField<V>,
    edgeColumns: List<CsvEdgeField<*>> = emptyList(),
    graphInitializer: MutableGraph.() -> Unit = {},
    options: CsvOptions.() -> Unit = {}
): CsvEdgeListGraph<V> = Files.newInputStream(path).use { inputStream ->
    readCsvEdgeList(
        inputStream,
        directed,
        multiEdge,
        indexEdges,
        vertexColumn,
        edgeColumns,
        graphInitializer,
        options
    )
}

/**
 * Loads a [CsvEdgeListGraph] from the given [inputStream]. The input is expected to be a CSV edge list, where the
 * first column represents the value of the edge source vertex, the second column represents the value of the edge
 * target vertex, and any following columns represent edge property values. Each distinct parsed vertex value results
 * in exactly one vertex being added to the returned graph. [inputStream] is not closed by this function - that
 * remains the caller's responsibility.
 */
@Throws(CsvFormatException::class)
public fun <V> readCsvEdgeList(
    inputStream: InputStream,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    vertexColumn: CsvVertexField<V>,
    edgeColumns: List<CsvEdgeField<*>> = emptyList(),
    graphInitializer: MutableGraph.() -> Unit = {},
    options: CsvOptions.() -> Unit = {}
) : CsvEdgeListGraph<V> {
    class EdgeColumnBinding<E>(graph: Graph, column: CsvEdgeField<E>) {
        private val parser = column.parser
        val property = graph.createEdgeProperty(column.type, column.defaultValueFunction)
        fun parse(edge: Edge, field: String) {
            property[edge] = parser(field)
        }
    }

    val csvOptions = CsvOptions().apply(options)

    val graph = mutableGraph(directed, multiEdge, indexEdges).also { graphInitializer(it) }
    val vertexProperty = graph.createVertexProperty(vertexColumn.type, vertexColumn.defaultValueFunction)
    val edgeBindings = edgeColumns.map { EdgeColumnBinding(graph, it) }

    val vertexIds = HashMap<V, Vertex>()

    val expectedColumns = 2 + edgeBindings.size
    val reader = CsvRecordReader(inputStream, csvOptions)

    if (csvOptions.hasHeader) reader.nextRecord()
    var record = reader.nextRecord()
    while (record != null) {
        try {
            if (record.size < expectedColumns) {
                reader.throwFormatException("required at least $expectedColumns columns, but found only ${record.size} columns")
            }

            val sourceValue = try { vertexColumn.parser(record[0]) } catch (e: RuntimeException) {
                reader.throwFormatException("illegal source vertex value \"${record[0]}\"", e)
            }
            val targetValue = try { vertexColumn.parser(record[1]) } catch (e: RuntimeException) {
                reader.throwFormatException("illegal target vertex value \"${record[1]}\"", e)
            }

            val source = vertexIds.getOrPut(sourceValue) { graph.addVertex().also { vertexProperty[it] = sourceValue } }
            val target = vertexIds.getOrPut(targetValue) { graph.addVertex().also { vertexProperty[it] = targetValue } }
            val edge = graph.addEdge(source, target)
            for (i in edgeBindings.indices) {
                edgeBindings[i].parse(edge, record[2 + i])
            }
        } catch (e: RuntimeException) {
            reader.throwFormatException(e)
        }

        record = reader.nextRecord()
    }

    graph.trimToSize()
    return CsvEdgeListGraph(graph, vertexProperty, edgeBindings.map { it.property })
}

public fun writeCsvEdgeList(path: Path, graph: Graph, options: CsvOptions.() -> Unit = {}): Unit =
    Files.newOutputStream(path).use { outputStream -> writeCsvEdgeList(outputStream, graph, options) }

public fun writeCsvEdgeList(outputStream: OutputStream, graph: Graph, options: CsvOptions.() -> Unit = {}) {
    writeCsvEdgeList(outputStream, graph, graph.vertexIdProperty, emptyList(), options)
}

public fun writeCsvEdgeList(path: Path, valueGraph: ValueGraph<*, *>, options: CsvOptions.() -> Unit = {}): Unit =
    Files.newOutputStream(path).use { outputStream -> writeCsvEdgeList(outputStream, valueGraph, options) }

public fun writeCsvEdgeList(outputStream: OutputStream, graph: ValueGraph<*, *>, options: CsvOptions.() -> Unit = {}) {
    val edgeProperties = if (graph.edgeProperty.type == staticTypeOf<Unit>()) {
        emptyList()
    } else {
        listOf(graph.edgeProperty)
    }
    val vertexProperty = if (graph.vertexProperty.type == staticTypeOf<Unit>()) {
        graph.graph.vertexIdProperty
    } else {
        graph.vertexProperty
    }
    writeCsvEdgeList(outputStream, graph.graph, vertexProperty, edgeProperties, options)
}

public fun writeCsvEdgeList(
    path: Path,
    graph: Graph,
    edgeProperties: List<EdgeProperty<*>> = emptyList(),
    options: CsvOptions.() -> Unit = {}
): Unit = Files.newOutputStream(path).use { outputStream ->
    writeCsvEdgeList(outputStream, graph, edgeProperties, options)
}

public fun writeCsvEdgeList(
    outputStream: OutputStream,
    graph: Graph,
    edgeProperties: List<EdgeProperty<*>> = emptyList(),
    options: CsvOptions.() -> Unit = {}
): Unit = writeCsvEdgeList(outputStream, graph, graph.vertexIdProperty, edgeProperties, options)

public fun writeCsvEdgeList(
    path: Path,
    graph: Graph,
    vertexProperty: VertexProperty<*>,
    edgeProperties: List<EdgeProperty<*>> = emptyList(),
    options: CsvOptions.() -> Unit = {}
): Unit = Files.newOutputStream(path).use { outputStream ->
    writeCsvEdgeList(outputStream, graph, vertexProperty, edgeProperties, options)
}

/**
 * Writes [graph]'s edges as a CSV edge list to [outputStream]. Each output row consists of the string representation of
 * the edge's source vertex value, the target vertex value, followed by the string representations of each of the
 * [edgeProperties] values for that edge (`null` values are written as an empty string). If a property requires more
 * complex serialization than just `toString()`, consider using [io.github.sooniln.fastgraph.map] to convert the
 * property into a String property with the correct serialization first. The [outputStream] is not closed by this
 * function - that remains the caller's responsibility.
 */
public fun writeCsvEdgeList(
    outputStream: OutputStream,
    graph: Graph,
    vertexProperty: VertexProperty<*>,
    edgeProperties: List<EdgeProperty<*>> = emptyList(),
    options: CsvOptions.() -> Unit = {}
) {
    fun Any?.toFieldString() = this?.toString() ?: ""

    val writer = CsvRecordWriter(outputStream, CsvOptions().apply(options))
    val fields = ArrayList<String>(2 + edgeProperties.size)
    for (edge in graph.edges) {
        fields.clear()
        fields.add(vertexProperty[graph.edgeSource(edge)].toFieldString())
        fields.add(vertexProperty[graph.edgeTarget(edge)].toFieldString())
        for (edgeProperty in edgeProperties) {
            fields.add(edgeProperty[edge].toFieldString())
        }
        writer.writeRecord(fields)
    }
    writer.flush()
}
