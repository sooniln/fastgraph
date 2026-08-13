/**
 * Methods dealing with CSV edge list input/output.
 */
@file:JvmName("CsvEdgeList")
package io.github.sooniln.fastgraph.io.csv

import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.ValueGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.io.ParsingEdgeProperty
import io.github.sooniln.fastgraph.io.ParsingVertexProperty
import io.github.sooniln.fastgraph.io.TypeBinding
import io.github.sooniln.fastgraph.io.csv.internal.CsvRecordReader
import io.github.sooniln.fastgraph.io.csv.internal.CsvRecordWriter
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.vertexIdProperty
import java.io.InputStream
import java.io.OutputStream

/** Information loaded from a CSV edge list. */
public interface CsvEdgeListGraph {
    public val graph: Graph
    public val vertexProperty: VertexProperty<Any>
    public val edgeProperties: List<EdgeProperty<*>>
}

/** A convenient way to construct a [CsvEdgeListGraph] for writing. */
@JvmOverloads
public fun CsvEdgeListGraph(
    graph: Graph,
    vertexProperty: VertexProperty<Any> = graph.vertexIdProperty,
    edgeProperties: List<EdgeProperty<*>> = emptyList(),
) : CsvEdgeListGraph = object : CsvEdgeListGraph {
    override val graph: Graph get() = graph
    override val vertexProperty: VertexProperty<Any> get() = vertexProperty
    override val edgeProperties: List<EdgeProperty<*>> get() = edgeProperties
}

/** A convenient way to construct a [CsvEdgeListGraph] for writing. */
public fun CsvEdgeListGraph(graph: ValueGraph<out Any, *>) : CsvEdgeListGraph = object : CsvEdgeListGraph {
    override val graph: Graph get() = graph.graph
    override val vertexProperty: VertexProperty<Any> get() = graph.vertexProperty
    override val edgeProperties: List<EdgeProperty<*>> get() {
        return if (graph.edgeProperty.type.isUnitType()) emptyList() else listOf(graph.edgeProperty)
    }
}

/** A mutable version of [CsvEdgeListGraph] used for output from CSV edge list reading methods. */
public class MutableCsvEdgeListGraph(
    override val graph: MutableGraph,
    override val vertexProperty: MutableVertexProperty<Any>,
    override val edgeProperties: List<MutableEdgeProperty<*>>,
) : CsvEdgeListGraph

/**
 * Loads a [CsvEdgeListGraph] from the given [inputStream]. The input is expected to be a CSV edge list, where the
 * first column represents the value of the edge source vertex, the second column represents the value of the edge
 * target vertex, and any following columns represent edge property values. Each distinct parsed vertex value results
 * in exactly one vertex being added to the returned graph. [inputStream] is not closed by this function - that
 * remains the caller's responsibility.
 */
@JvmOverloads
@Throws(CsvFormatException::class)
public fun readCsvEdgeList(
    inputStream: InputStream,
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    vertexPropertyType: TypeBinding<Any> = TypeBinding.nonNullString,
    edgePropertyTypes: List<TypeBinding<*>> = emptyList(),
    csvOptions: CsvOptions = CsvOptions(),
) : MutableCsvEdgeListGraph {
    val graph = mutableGraph(directed, multiEdge, indexEdges)
    val vertexProperty = ParsingVertexProperty(graph, vertexPropertyType)
    val edgeProperties = edgePropertyTypes.map { ParsingEdgeProperty(graph, it) }

    val vertexIds = HashMap<Any?, Vertex>()

    val expectedColumns = 2 + edgeProperties.size
    val reader = CsvRecordReader(inputStream, csvOptions)

    fun resolveVertex(valueString: String): Vertex {
        val value = try {
            vertexProperty.parse(valueString)
        } catch (e: RuntimeException) {
            reader.throwFormatException("illegal vertex value \"$valueString\"", e)
        }
        return vertexIds.getOrPut(value) { graph.addVertex().also { vertexProperty[it] = value} }
    }

    if (csvOptions.hasHeader) reader.nextRecord()
    var record = reader.nextRecord()
    while (record != null) {
        try {
            if (record.size != expectedColumns) {
                reader.throwFormatException("required $expectedColumns columns, but found only ${record.size} columns")
            }

            val edge = graph.addEdge(resolveVertex(record[0]), resolveVertex(record[1]))
            for (i in edgeProperties.indices) {
                val valueString = record[2 + i]
                try {
                    edgeProperties[i].parseAndSet(edge, valueString)
                } catch (e: RuntimeException) {
                    reader.throwFormatException("illegal edge property value \"$valueString\"", e)
                }
            }
        } catch (e: RuntimeException) {
            reader.throwFormatException(e)
        }

        record = reader.nextRecord()
    }

    return MutableCsvEdgeListGraph(graph, vertexProperty.property, edgeProperties.map { it.property })
}

/**
 * Writes [graph]'s edges as a CSV edge list to [outputStream]. Each output row consists of the string representation of
 * the edge's source vertex value, the target vertex value, followed by the string representations of each of the
 * [CsvEdgeListGraph.edgeProperties] values for that edge. If a property requires more complex serialization than just
 * `toString()`, consider using [io.github.sooniln.fastgraph.map] to convert the property into a String property with
 * the correct serialization first. The [outputStream] is not closed by this function - that remains the caller's
 * responsibility.
 */
@JvmOverloads
public fun writeCsvEdgeList(
    outputStream: OutputStream,
    graph: CsvEdgeListGraph,
    csvOptions: CsvOptions = CsvOptions(),
) {
    val writer = CsvRecordWriter(outputStream, csvOptions)
    val fields = ArrayList<String>(2 + graph.edgeProperties.size)
    for (edge in graph.graph.edges) {
        fields.clear()
        fields.add(graph.vertexProperty[graph.graph.edgeSource(edge)].toString())
        fields.add(graph.vertexProperty[graph.graph.edgeTarget(edge)].toString())
        for (edgeProperty in graph.edgeProperties) {
            fields.add(edgeProperty[edge].toString())
        }
        writer.writeRecord(fields)
    }
    writer.flush()
}
