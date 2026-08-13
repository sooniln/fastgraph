/**
 * Methods dealing with GraphML input/output.
 */
@file:JvmName("GraphML")
package io.github.sooniln.fastgraph.io.graphml

import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.ValueGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.io.ParsingEdgeProperty
import io.github.sooniln.fastgraph.io.ParsingVertexProperty
import io.github.sooniln.fastgraph.io.TypeBinding
import io.github.sooniln.fastgraph.io.graphml.internal.IndentingXMLStreamWriter
import io.github.sooniln.fastgraph.mutableGraph
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader
import kotlin.reflect.typeOf

/**
 * Information loaded from a GraphML document.
 */
public interface GraphMLGraph {
    public val graph: Graph
    public val vertexProperties: Map<String, VertexProperty<*>>
    public val edgeProperties: Map<String, EdgeProperty<*>>
    public val graphAttributes: Map<String, Any>
}

/** A convenient way to construct a [GraphMLGraph] for writing. */
public fun GraphMLGraph(
    graph: Graph,
    vertexProperties: Map<String, VertexProperty<*>> = emptyMap(),
    edgeProperties: Map<String, EdgeProperty<*>> = emptyMap(),
    graphAttributes: Map<String, Any> = emptyMap(),
) : GraphMLGraph = object : GraphMLGraph {
    override val graph: Graph get() = graph
    override val vertexProperties: Map<String, VertexProperty<*>> get() = vertexProperties
    override val edgeProperties: Map<String, EdgeProperty<*>> get() = edgeProperties
    override val graphAttributes: Map<String, Any> get() = graphAttributes
}

/** A convenient way to construct a [GraphMLGraph] for writing. */
public fun GraphMLGraph(
    graph: ValueGraph<*, *>,
    vertexPropertyName: String,
    edgePropertyName: String,
    graphAttributes: Map<String, Any> = emptyMap(),
) : GraphMLGraph = object : GraphMLGraph {
    override val graph: Graph get() = graph.graph
    override val vertexProperties: Map<String, VertexProperty<*>> get() {
        return mapOf(vertexPropertyName to graph.vertexProperty)
    }
    override val edgeProperties: Map<String, EdgeProperty<*>> get() {
        return mapOf(edgePropertyName to graph.edgeProperty)
    }
    override val graphAttributes: Map<String, Any> get() = graphAttributes
}

/** A mutable version of [GraphMLGraph] used for output from GraphML reading methods. */
public class MutableGraphMLGraph(
    override val graph: MutableGraph,
    override val vertexProperties: Map<String, MutableVertexProperty<*>> = emptyMap(),
    override val edgeProperties: Map<String, MutableEdgeProperty<*>> = emptyMap(),
    override val graphAttributes: Map<String, Any> = emptyMap(),
) : GraphMLGraph

/**
 * Loads a [GraphMLGraph] from the given [inputStream]. Only the first `<graph>` element in the document is read. The
 * [inputStream] is not closed by this function - that remains the caller's responsibility.
 */
@JvmOverloads
@Throws(GraphMLFormatException::class)
public fun readGraphML(
    inputStream: InputStream,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
): MutableGraphMLGraph {
    val factory = XMLInputFactory.newFactory()
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)

    val reader = factory.createXMLStreamReader(inputStream)
    try {
        return parseGraphML(reader, multiEdge, indexEdges)
    } catch (e: Exception) {
        throw GraphMLFormatException("${e.message} (line ${reader.location.lineNumber}:${reader.location.columnNumber})", e)
    } finally {
        reader.close()
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

/**
 * Maps a `<key>` element's `attr.type` to the [TypeBinding] used to parse its values, substituting a custom
 * default (parsed from the `<default>` element's text, if present) for the type's normal default. A "string" key
 * with no default is nullable ([TypeBinding.string] itself defaults to `null`); one with an explicit `<default>` is
 * not, since it always has a concrete value to fall back on.
 */
private fun keyBinding(attrType: String, defaultText: String?): TypeBinding<*> {
    if (attrType == "string" && defaultText != null) {
        return TypeBinding(TypeBinding.nonNullString.type, defaultText, TypeBinding.nonNullString.parser)
    }
    val base = when (attrType) {
        "boolean" -> TypeBinding.boolean
        "int" -> TypeBinding.int
        "long" -> TypeBinding.long
        "float" -> TypeBinding.float
        "double" -> TypeBinding.double
        "string" -> TypeBinding.string
        else -> throw IllegalArgumentException("unsupported attr.type \"$attrType\"")
    }
    return if (defaultText == null) base else TypeBinding(base.type, base.parser(defaultText), base.parser)
}

private fun parseGraphML(reader: XMLStreamReader, multiEdge: Boolean, indexEdges: Boolean): MutableGraphMLGraph {
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
        require(forValue == "node" || forValue == "edge" || forValue == "graph" || forValue == "all") {
            "unsupported key for=\"$forValue\""
        }

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
    val numEdges = reader.getAttributeValue(null, "parse.edges")?.toIntOrNull()

    val graph = mutableGraph(directed, multiEdge, indexEdges)

    val attrNameById = keys.associate { it.id to it.attrName }
    val vertexBindings = HashMap<String, ParsingVertexProperty<*>>()
    val edgeBindings = HashMap<String, ParsingEdgeProperty<*>>()
    val graphBindings = HashMap<String, TypeBinding<*>>()
    val graphAttributes = HashMap<String, Any>()
    for (key in keys) {
        if (key.forValue == "node" || key.forValue == "all") {
            vertexBindings[key.id] = ParsingVertexProperty(graph, keyBinding(key.attrType, key.defaultText))
        }
        if (key.forValue == "edge" || key.forValue == "all") {
            edgeBindings[key.id] = ParsingEdgeProperty(graph, keyBinding(key.attrType, key.defaultText))
        }
        if (key.forValue == "graph" || key.forValue == "all") {
            val binding = keyBinding(key.attrType, key.defaultText)
            graphBindings[key.id] = binding
            // Non-null: none of the type-mapped parsers (boolean/int/long/float/double/string) ever return null
            // for non-null input text.
            if (key.defaultText != null) graphAttributes[key.attrName] = binding.parser(key.defaultText)!!
        }
    }

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
                val outDegreeHint = reader.getAttributeValue(null, "parse.outdegree")?.toInt() ?: 0
                val inDegreeHint = reader.getAttributeValue(null, "parse.indegree")?.toInt() ?: 0
                val vertex = nodeIds.getOrPut(id) { graph.addVertex(outDegreeHint, inDegreeHint) }
                require(confirmedNodeIds.add(id)) { "duplicate node id=\"$id\"" }

                reader.next()
                skipMisc(reader)
                while (!(reader.eventType == END_ELEMENT && reader.localName == "node")) {
                    check(reader.eventType == START_ELEMENT) { "unexpected content inside <node>" }
                    when (reader.localName) {
                        "data" -> {
                            val keyId = reader.requiredAttribute("key")
                            val text = reader.elementText
                            check(vertexBindings.containsKey(keyId)) { "key id=\"$keyId\" does not exist" }
                            vertexBindings.getValue(keyId).parseAndSet(vertex, text)
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
                            check(edgeBindings.containsKey(keyId)) { "key id=\"$keyId\" does not exist" }
                            for (edge in createdEdges) edgeBindings.getValue(keyId).parseAndSet(edge, text)
                            reader.next()
                            skipMisc(reader)
                        }
                        else -> skipElement(reader)
                    }
                }
                reader.next()
                skipMisc(reader)
            }
            "data" -> {
                val keyId = reader.requiredAttribute("key")
                val text = reader.elementText
                check(graphBindings.containsKey(keyId)) { "key id=\"$keyId\" does not exist" }
                // Non-null: see the identical assertion above, in the key-binding setup loop.
                graphAttributes[attrNameById.getValue(keyId)] = graphBindings.getValue(keyId).parser(text)!!
                reader.next()
                skipMisc(reader)
            }
            else -> skipElement(reader)
        }
    }

    require(nodeIds.size == confirmedNodeIds.size) { "edge references unknown node id(s): ${nodeIds.keys - confirmedNodeIds}" }

    return MutableGraphMLGraph(
        graph,
        vertexBindings.entries.associate { (id, binding) -> attrNameById.getValue(id) to binding.property },
        edgeBindings.entries.associate { (id, binding) -> attrNameById.getValue(id) to binding.property },
        graphAttributes)
}

/**
 * Writes [graph] as a GraphML document to [outputStream]. The [outputStream] is not closed by this function - that
 * remains the caller's responsibility.
 */
public fun writeGraphML(
    outputStream: OutputStream,
    graph: GraphMLGraph,
) {
    val charset = StandardCharsets.UTF_8.name()
    val writer = IndentingXMLStreamWriter(XMLOutputFactory.newFactory().createXMLStreamWriter(outputStream, charset))

    var keyCounter = 0
    val vertexKeyIds = graph.vertexProperties.keys.associateWith { "d${keyCounter++}" }
    val edgeKeyIds = graph.edgeProperties.keys.associateWith { "d${keyCounter++}" }
    val graphKeyIds = graph.graphAttributes.keys.associateWith { "d${keyCounter++}" }

    writer.writeStartDocument(charset, "1.0")
    writer.writeStartElement("graphml")
    writer.writeDefaultNamespace("http://graphml.graphdrawing.org/xmlns")

    fun StaticType<*>.toAttrType(): String = when (this.kType) {
        typeOf<Boolean>() -> "boolean"
        typeOf<Int>() -> "int"
        typeOf<Long>() -> "long"
        typeOf<Float>() -> "float"
        typeOf<Double>() -> "double"
        else -> "string"
    }

    fun Any.toAttrType(): String = when (this) {
        is Boolean -> "boolean"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Double -> "double"
        else -> "string"
    }

    for ((name, property) in graph.vertexProperties) {
        writer.writeEmptyElement("key")
        writer.writeAttribute("id", vertexKeyIds.getValue(name))
        writer.writeAttribute("for", "node")
        writer.writeAttribute("attr.name", name)
        writer.writeAttribute("attr.type", property.type.toAttrType())
    }
    for ((name, property) in graph.edgeProperties) {
        writer.writeEmptyElement("key")
        writer.writeAttribute("id", edgeKeyIds.getValue(name))
        writer.writeAttribute("for", "edge")
        writer.writeAttribute("attr.name", name)
        writer.writeAttribute("attr.type", property.type.toAttrType())
    }
    for ((name, value) in graph.graphAttributes) {
        writer.writeEmptyElement("key")
        writer.writeAttribute("id", graphKeyIds.getValue(name))
        writer.writeAttribute("for", "graph")
        writer.writeAttribute("attr.name", name)
        writer.writeAttribute("attr.type", value.toAttrType())
    }

    writer.writeStartElement("graph")
    writer.writeAttribute("id", "G")
    writer.writeAttribute("edgedefault", if (graph.graph.directed) "directed" else "undirected")
    writer.writeAttribute("parse.nodes", graph.graph.vertices.size.toString())
    writer.writeAttribute("parse.edges", graph.graph.edges.size.toString())
    writer.writeAttribute("parse.order", "nodesfirst")
    if (graph.graph is IndexedVertexGraph) writer.writeAttribute("parse.nodeids", "canonical")
    if (graph.graph is IndexedEdgeGraph) writer.writeAttribute("parse.edgeids", "canonical")

    for ((name, value) in graph.graphAttributes) {
        writer.writeStartElement("data")
        writer.writeAttribute("key", graphKeyIds.getValue(name))
        writer.writeCharacters(value.toString())
        writer.writeEndElement()
    }

    fun Vertex.toId(): String = "n$id"
    fun Any?.toFieldText(): String = this?.toString() ?: ""

    for (vertex in graph.graph.vertices) {
        writer.writeStartElement("node")
        writer.writeAttribute("id", vertex.toId())
        writer.writeAttribute("parse.indegree", graph.graph.inDegree(vertex).toString())
        writer.writeAttribute("parse.outdegree", graph.graph.outDegree(vertex).toString())
        for ((name, property) in graph.vertexProperties) {
            writer.writeStartElement("data")
            writer.writeAttribute("key", vertexKeyIds.getValue(name))
            writer.writeCharacters(property[vertex].toFieldText())
            writer.writeEndElement()
        }
        writer.writeEndElement()
    }

    for (edge in graph.graph.edges) {
        writer.writeStartElement("edge")
        writer.writeAttribute("id", "e${edge.id}")
        writer.writeAttribute("source", graph.graph.edgeSource(edge).toId())
        writer.writeAttribute("target", graph.graph.edgeTarget(edge).toId())
        for ((name, property) in graph.edgeProperties) {
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
