/**
 * Methods dealing with graphs.
 */
@file:JvmName("GraphML")
package io.github.sooniln.fastgraph.io.graphml

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.io.graphml.internal.IndentingXMLStreamWriter
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.vertexIdProperty
import java.io.InputStream
import java.io.OutputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import javax.xml.stream.XMLStreamWriter
import kotlin.reflect.typeOf

/** Information loaded from a GraphML document. */
public class GraphMLGraph(
    public val graph: MutableGraph,
    public val nodeIdProperty: MutableVertexProperty<String>,
    public val vertexProperties: Map<String, MutableVertexProperty<*>>,
    public val edgeProperties: Map<String, MutableEdgeProperty<*>>,
)

/**
 * Loads a [GraphMLGraph] from the given [inputStream]. Only the first `<graph>` element in the document is read -
 * ports, hyperedges, and any nested or additional `<graph>` elements are silently skipped, per the fall-back
 * behavior documented by the GraphML spec for applications that don't support those constructs. Vertex/edge
 * attributes are created dynamically from the document's own `<key>` declarations. [inputStream] is not closed by
 * this function - that remains the caller's responsibility.
 */
@Throws(GraphMLFormatException::class)
public fun readGraphML(
    inputStream: InputStream,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    options: GraphMLOptions.() -> Unit = {}
): GraphMLGraph {
    GraphMLOptions().apply(options)

    val factory = XMLInputFactory.newFactory()
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)

    val reader = factory.createXMLStreamReader(inputStream)
    try {
        return parseGraphML(reader, multiEdge, indexEdges)
    } catch (e: XMLStreamException) {
        throw GraphMLFormatException("${e.message} (line ${reader.location.lineNumber}:${reader.location.columnNumber})", e)
    } catch (e: RuntimeException) {
        throw GraphMLFormatException("${e.message} (line ${reader.location.lineNumber}:${reader.location.columnNumber})", e)
    } finally {
        reader.close()
    }
}

private class VertexKeyBinding<T>(val name: String, val property: MutableVertexProperty<T>, val parser: (String) -> T) {
    fun applyValue(vertex: Vertex, text: String) {
        property[vertex] = parser(text)
    }
}

private class EdgeKeyBinding<T>(val name: String, val property: MutableEdgeProperty<T>, val parser: (String) -> T) {
    fun applyValue(edge: Edge, text: String) {
        property[edge] = parser(text)
    }
}

private class KeyDef(val id: String, val forValue: String, val attrName: String, val attrType: String, val defaultText: String?)

private fun XMLStreamReader.requiredAttribute(name: String): String =
    getAttributeValue(null, name) ?: throw IllegalArgumentException("<$localName> missing \"$name\" attribute")

private fun skipMisc(reader: XMLStreamReader) {
    while (reader.eventType == XMLStreamConstants.CHARACTERS ||
        reader.eventType == XMLStreamConstants.SPACE ||
        reader.eventType == XMLStreamConstants.COMMENT ||
        reader.eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
    ) {
        reader.next()
    }
}

/** Skips the element the reader is currently positioned at (a START_ELEMENT), including any nested content. */
private fun skipElement(reader: XMLStreamReader) {
    var depth = 1
    while (depth > 0) {
        reader.next()
        when (reader.eventType) {
            START_ELEMENT -> depth++
            END_ELEMENT -> depth--
        }
    }
    reader.next()
    skipMisc(reader)
}

private fun createVertexKeyBinding(graph: MutableGraph, attrName: String, attrType: String, defaultText: String?): VertexKeyBinding<*> {
    return when (attrType) {
        "boolean" -> {
            val default = defaultText?.toBooleanStrict() ?: false
            VertexKeyBinding(attrName, graph.createVertexProperty<Boolean> { default }, String::toBoolean)
        }

        "int" -> {
            val default = defaultText?.toInt() ?: 0
            VertexKeyBinding(attrName, graph.createVertexProperty<Int> { default }, String::toInt)
        }

        "long" -> {
            val default = defaultText?.toLong() ?: 0L
            VertexKeyBinding(attrName, graph.createVertexProperty<Long> { default }, String::toLong)
        }

        "float" -> {
            val default = defaultText?.toFloat() ?: 0F
            VertexKeyBinding(attrName, graph.createVertexProperty<Float> { default }, String::toFloat)
        }

        "double" -> {
            val default = defaultText?.toDouble() ?: 0.0
            VertexKeyBinding(attrName, graph.createVertexProperty<Double> { default }, String::toDouble)
        }

        "string" -> {
            if (defaultText != null) {
                VertexKeyBinding(attrName, graph.createVertexProperty<String> { defaultText }) { it }
            } else {
                VertexKeyBinding(attrName, graph.createVertexProperty<String>()) { it }
            }
        }

        else -> throw IllegalArgumentException("unsupported attr.type \"$attrType\"")
    }
}

private fun createEdgeKeyBinding(graph: MutableGraph, attrName: String, attrType: String, defaultText: String?): EdgeKeyBinding<*> {
    return when (attrType) {
        "boolean" -> {
            val default = defaultText?.toBooleanStrict() ?: false
            EdgeKeyBinding(attrName, graph.createEdgeProperty<Boolean> { default }, String::toBooleanStrict)
        }

        "int" -> {
            val default = defaultText?.toInt() ?: 0
            EdgeKeyBinding(attrName, graph.createEdgeProperty<Int> { default }, String::toInt)
        }

        "long" -> {
            val default = defaultText?.toLong() ?: 0L
            EdgeKeyBinding(attrName, graph.createEdgeProperty<Long> { default }, String::toLong)
        }

        "float" -> {
            val default = defaultText?.toFloat() ?: 0F
            EdgeKeyBinding(attrName, graph.createEdgeProperty<Float> { default }, String::toFloat)
        }

        "double" -> {
            val default = defaultText?.toDouble() ?: 0.0
            EdgeKeyBinding(attrName, graph.createEdgeProperty<Double> { default }, String::toDouble)
        }

        "string" -> {
            if (defaultText != null) {
                EdgeKeyBinding(attrName, graph.createEdgeProperty<String> { defaultText }) { it }
            } else {
                EdgeKeyBinding(attrName, graph.createEdgeProperty<String>()) { it }
            }
        }

        else -> throw IllegalArgumentException("unsupported attr.type \"$attrType\"")
    }
}

private fun parseGraphML(reader: XMLStreamReader, multiEdge: Boolean, indexEdges: Boolean): GraphMLGraph {
    while (reader.eventType != START_ELEMENT) {
        reader.next()
    }
    require(reader.localName == "graphml") { "expected <graphml> root element, found <${reader.localName}>" }
    reader.next()
    skipMisc(reader)

    // <key> elements always precede <graph> - collect them all before creating any properties.
    val keys = ArrayList<KeyDef>()
    while (reader.eventType == START_ELEMENT && reader.localName == "key") {
        val id = reader.requiredAttribute("id")
        val forValue = reader.requiredAttribute("for")
        val attrName = reader.requiredAttribute("attr.name")
        val attrType = reader.requiredAttribute("attr.type")
        require(forValue == "node" || forValue == "edge" || forValue == "all") { "unsupported key for=\"$forValue\"" }

        reader.next()
        skipMisc(reader)
        var defaultText: String? = null
        if (reader.eventType == START_ELEMENT && reader.localName == "default") {
            defaultText = reader.elementText
            reader.next()
            skipMisc(reader)
        }
        check(reader.eventType == END_ELEMENT && reader.localName == "key") { "expected </key>" }
        reader.next()
        skipMisc(reader)

        keys.add(KeyDef(id, forValue, attrName, attrType, defaultText))
    }

    require(reader.eventType == START_ELEMENT && reader.localName == "graph") { "expected <graph> element" }
    val directed = when (val edgeDefault = reader.requiredAttribute("edgedefault")) {
        "directed" -> true
        "undirected" -> false
        else -> throw IllegalArgumentException("invalid edgedefault \"$edgeDefault\"")
    }
    // GraphML-ParseInfo: only parse.nodes is used, purely to pre-size the node-id lookup map.
    val numVertices = reader.getAttributeValue(null, "parse.nodes")?.toIntOrNull()
    val numEdges = reader.getAttributeValue(null, "parse.nodes")?.toIntOrNull()

    val graph = mutableGraph(directed, multiEdge, indexEdges)

    val vertexKeyBindings = HashMap<String, VertexKeyBinding<*>>()
    val edgeKeyBindings = HashMap<String, EdgeKeyBinding<*>>()
    for (key in keys) {
        if (key.forValue == "node" || key.forValue == "all") {
            vertexKeyBindings[key.id] = createVertexKeyBinding(graph, key.attrName, key.attrType, key.defaultText)
        }
        if (key.forValue == "edge" || key.forValue == "all") {
            edgeKeyBindings[key.id] = createEdgeKeyBinding(graph, key.attrName, key.attrType, key.defaultText)
        }
    }

    val nodeIdProperty = graph.createVertexProperty<String> { "" }
    val nodeIds = HashMap<String, Vertex>(numVertices ?: 0)
    val confirmedNodeIds = HashSet<String>()

    if (numVertices != null) graph.ensureVertexCapacity(numVertices)
    if (numEdges != null) graph.ensureEdgeCapacity(numEdges)

    fun resolveVertex(id: String): Vertex = nodeIds.getOrPut(id) { graph.addVertex() }

    reader.next()
    skipMisc(reader)
    while (!(reader.eventType == END_ELEMENT && reader.localName == "graph")) {
        check(reader.eventType == START_ELEMENT) { "unexpected content inside <graph>" }
        when (reader.localName) {
            "node" -> {
                val id = reader.requiredAttribute("id")
                val vertex = resolveVertex(id)
                require(confirmedNodeIds.add(id)) { "duplicate node id=\"$id\"" }

                nodeIdProperty[vertex] = id

                reader.next()
                skipMisc(reader)
                while (!(reader.eventType == END_ELEMENT && reader.localName == "node")) {
                    check(reader.eventType == START_ELEMENT) { "unexpected content inside <node>" }
                    when (reader.localName) {
                        "data" -> {
                            val keyId = reader.requiredAttribute("key")
                            val text = reader.elementText
                            check(vertexKeyBindings.containsKey(keyId)) { "key id=\"$keyId\" does not exist" }
                            vertexKeyBindings.getValue(keyId).applyValue(vertex, text)
                            reader.next()
                            skipMisc(reader)
                        }
                        else -> skipElement(reader)
                    }
                }
                reader.next()
                skipMisc(reader)
            }
            "edge" -> {
                val sourceId = reader.requiredAttribute("source")
                val targetId = reader.requiredAttribute("target")
                val edgeDirected = when (val attrDirected = reader.getAttributeValue(null, "directed")) {
                    null -> directed
                    "true" -> {
                        check(directed) { "undirected graph cannot contain a directed edge" }
                        true
                    }
                    "false" -> false
                    else -> throw IllegalArgumentException("invalid directed attribute \"$attrDirected\"")
                }
                val source = resolveVertex(sourceId)
                val target = resolveVertex(targetId)
                // An undirected edge in a directed graph is represented losslessly as two opposite directed edges.
                // A directed edge in an undirected graph can't be represented and is unsupported.
                val createdEdges = if (edgeDirected) {
                    listOf(graph.addEdge(source, target))
                } else {
                    listOf(graph.addEdge(source, target), graph.addEdge(target, source))
                }

                reader.next()
                skipMisc(reader)
                while (!(reader.eventType == END_ELEMENT && reader.localName == "edge")) {
                    check(reader.eventType == START_ELEMENT) { "unexpected content inside <edge>" }
                    when (reader.localName) {
                        "data" -> {
                            val keyId = reader.requiredAttribute("key")
                            val text = reader.elementText
                            check(edgeKeyBindings.containsKey(keyId)) { "key id=\"$keyId\" does not exist" }
                            for (edge in createdEdges) edgeKeyBindings.getValue(keyId).applyValue(edge, text)
                            reader.next()
                            skipMisc(reader)
                        }
                        else -> skipElement(reader)
                    }
                }
                reader.next()
                skipMisc(reader)
            }
            else -> skipElement(reader)
        }
    }

    require(nodeIds.size == confirmedNodeIds.size) { "edge references unknown node id(s): ${nodeIds.keys - confirmedNodeIds}" }

    graph.trimToSize()
    return GraphMLGraph(
        graph,
        nodeIdProperty,
        vertexKeyBindings.values.associate { it.name to it.property },
        edgeKeyBindings.values.associate { it.name to it.property })
}

/**
 * Writes [graph] as a GraphML document to [outputStream]. [vertexProperties]/[edgeProperties] are emitted as
 * `<key>`/`<data>` attributes, keyed by their map name; a property's `attr.type` is inferred from its
 * [StaticType] where possible ([Boolean]/[Int]/[Long]/[Float]/[Double]), and `string` (via `toString()`)
 * otherwise. The [outputStream] is not closed by this function - that remains the caller's responsibility.
 */
public fun writeGraphML(
    outputStream: OutputStream,
    graph: Graph,
    vertexProperties: Map<String, VertexProperty<*>> = emptyMap(),
    edgeProperties: Map<String, EdgeProperty<*>> = emptyMap(),
    options: GraphMLOptions.() -> Unit = {}
) {
    val graphMLOptions = GraphMLOptions().apply(options)
    val factory = XMLOutputFactory.newFactory()
    val rawWriter = factory.createXMLStreamWriter(outputStream, graphMLOptions.charset.name())
    val writer: XMLStreamWriter = if (graphMLOptions.indent) IndentingXMLStreamWriter(rawWriter) else rawWriter

    var keyCounter = 0
    val vertexKeyIds = LinkedHashMap<String, String>()
    for (name in vertexProperties.keys) vertexKeyIds[name] = "d${keyCounter++}"
    val edgeKeyIds = LinkedHashMap<String, String>()
    for (name in edgeProperties.keys) edgeKeyIds[name] = "d${keyCounter++}"

    writer.writeStartDocument(graphMLOptions.charset.name(), "1.0")
    writer.writeStartElement("graphml")
    writer.writeDefaultNamespace("http://graphml.graphdrawing.org/xmlns")

    for ((name, property) in vertexProperties) {
        writer.writeEmptyElement("key")
        writer.writeAttribute("id", vertexKeyIds.getValue(name))
        writer.writeAttribute("for", "node")
        writer.writeAttribute("attr.name", name)
        writer.writeAttribute("attr.type", attrTypeOf(property.type))
    }
    for ((name, property) in edgeProperties) {
        writer.writeEmptyElement("key")
        writer.writeAttribute("id", edgeKeyIds.getValue(name))
        writer.writeAttribute("for", "edge")
        writer.writeAttribute("attr.name", name)
        writer.writeAttribute("attr.type", attrTypeOf(property.type))
    }

    writer.writeStartElement("graph")
    writer.writeAttribute("id", "G")
    writer.writeAttribute("edgedefault", if (graph.directed) "directed" else "undirected")
    if (graphMLOptions.writeParseInfo) {
        var maxIndegree = 0
        var maxOutdegree = 0
        for (vertex in graph.vertices) {
            maxIndegree = maxOf(maxIndegree, graph.inDegree(vertex))
            maxOutdegree = maxOf(maxOutdegree, graph.outDegree(vertex))
        }
        writer.writeAttribute("parse.nodes", graph.vertices.size.toString())
        writer.writeAttribute("parse.edges", graph.edges.size.toString())
        writer.writeAttribute("parse.maxindegree", maxIndegree.toString())
        writer.writeAttribute("parse.maxoutdegree", maxOutdegree.toString())
        writer.writeAttribute("parse.order", "nodesfirst")
        if (graph is IndexedVertexGraph) writer.writeAttribute("parse.nodeids", "canonical")
        if (graph is IndexedEdgeGraph) writer.writeAttribute("parse.edgeids", "canonical")
    }

    val vertexIdProperty = graph.vertexIdProperty
    fun nodeId(vertex: Vertex) = "n${vertexIdProperty[vertex]}"

    for (vertex in graph.vertices) {
        writer.writeStartElement("node")
        writer.writeAttribute("id", nodeId(vertex))
        if (graphMLOptions.writeParseInfo) {
            writer.writeAttribute("parse.indegree", graph.inDegree(vertex).toString())
            writer.writeAttribute("parse.outdegree", graph.outDegree(vertex).toString())
        }
        for ((name, property) in vertexProperties) {
            writer.writeStartElement("data")
            writer.writeAttribute("key", vertexKeyIds.getValue(name))
            writer.writeCharacters(property[vertex].toFieldText())
            writer.writeEndElement()
        }
        writer.writeEndElement()
    }

    for (edge in graph.edges) {
        writer.writeStartElement("edge")
        writer.writeAttribute("id", "e${edge.id}")
        writer.writeAttribute("source", nodeId(graph.edgeSource(edge)))
        writer.writeAttribute("target", nodeId(graph.edgeTarget(edge)))
        for ((name, property) in edgeProperties) {
            writer.writeStartElement("data")
            writer.writeAttribute("key", edgeKeyIds.getValue(name))
            writer.writeCharacters(property[edge].toFieldText())
            writer.writeEndElement()
        }
        writer.writeEndElement()
    }

    writer.writeEndElement() // </graph>
    writer.writeEndElement() // </graphml>
    writer.writeEndDocument()
    writer.flush()
}

private fun Any?.toFieldText(): String = this?.toString() ?: ""

private fun attrTypeOf(type: StaticType<*>): String = when (type.kType) {
    typeOf<Boolean>() -> "boolean"
    typeOf<Int>() -> "int"
    typeOf<Long>() -> "long"
    typeOf<Float>() -> "float"
    typeOf<Double>() -> "double"
    else -> "string"
}
