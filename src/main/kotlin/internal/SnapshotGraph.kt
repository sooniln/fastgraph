package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.longs.LongArrayList
import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractImmutableGraph
import io.github.sooniln.fastgraph.AbstractVertexCollection
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asEdgeIterator
import io.github.sooniln.fastgraph.asVertexIterator
import io.github.sooniln.fastgraph.edgeOpposite
import io.github.sooniln.fastgraph.primitives.collections.GraphInt2AnyHashMap
import io.github.sooniln.fastgraph.primitives.collections.GraphLong2LongHashMap

/**
 * An immutable snapshot of another [Graph]'s structure. This graph stores a copy of the topology
 * at the time of construction and is not affected by subsequent changes to the source graph.
 *
 * The snapshot preserves the original [Vertex] and [Edge] values - that is, vertices and edges
 * in this graph have the same [Vertex.intValue] and [Edge.longValue] as they did in the source graph.
 */
internal class SnapshotGraph(graph: Graph) : AbstractImmutableGraph() {

    override val directed: Boolean = graph.directed
    override val multiEdge: Boolean = graph.multiEdge

    private val _incomingEdges = lazy {
        check(directed)
        val ice = GraphInt2AnyHashMap<LongArrayList>()
        outgoingEdges.fastForEachKey { vertexIntValue -> ice.put(vertexIntValue, LongArrayList()) }
        outgoingEdges.fastForEach { _, edges ->
            for (edgeLongValue in edges) {
                ice.getValue(edgeTarget(Edge(edgeLongValue)).intValue).add(edgeLongValue)
            }
        }
        GraphInt2AnyHashMap<LongArray>().also { result ->
            ice.fastForEach { key, value -> result.put(key, value.toLongArray().apply { sort() }) }
        }
    }

    private val outgoingEdges = GraphInt2AnyHashMap<LongArray>()
    private val incomingEdges: GraphInt2AnyHashMap<LongArray> by _incomingEdges

    private val edgeValues = GraphLong2LongHashMap()

    init {
        for (v in graph.vertices) {
            outgoingEdges[v.intValue] = graph.outgoingEdges(v).toLongArray().apply { sort() }
        }
        for (e in graph.edges) {
            edgeValues[e.longValue] = EdgeValue(directed, graph.edgeSource(e), graph.edgeTarget(e)).longValue
        }
    }

    private fun validateVertex(vertex: Vertex): Vertex {
        if (!outgoingEdges.containsKey(vertex.intValue)) {
            throw IllegalArgumentException("$vertex not found in graph")
        }
        return vertex
    }

    private fun validateEdge(edge: Edge): Edge {
        if (!edgeValues.containsKey(edge.longValue)) {
            throw IllegalArgumentException("$edge not found in graph")
        }
        return edge
    }

    override val vertices: VertexSet = object : AbstractVertexSet() {
        override val size: Int get() = outgoingEdges.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Vertex): Boolean {
            validateVertex(element)
            return true
        }

        override fun iterator(): VertexIterator = outgoingEdges.keys.iterator().asVertexIterator()
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int {
        return outgoingEdges.getValue(validateVertex(vertex).intValue).size
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int {
        if (!directed) {
            return outDegree(vertex)
        }
        return incomingEdges.getValue(validateVertex(vertex).intValue).size
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSet {
        return NeighborSet(validateVertex(vertex), true, outgoingEdges.getValue(vertex.intValue))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet {
        if (!directed) {
            return successors(vertex)
        }
        return NeighborSet(validateVertex(vertex), false, incomingEdges.getValue(vertex.intValue))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet {
        return IncidentEdgeSet(outgoingEdges.getValue(validateVertex(vertex).intValue))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet {
        if (!directed) {
            return outgoingEdges(vertex)
        }
        return IncidentEdgeSet(incomingEdges.getValue(validateVertex(vertex).intValue))
    }

    override val edges: EdgeSet = object : AbstractEdgeSet() {
        override val size: Int get() = edgeValues.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean = edgeValues.containsKey(validateEdge(element).longValue)

        override fun iterator(): EdgeIterator = edgeValues.keys.iterator().asEdgeIterator()
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex {
        return EdgeValue(edgeValues[validateEdge(edge).longValue]).source
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex {
        return EdgeValue(edgeValues[validateEdge(edge).longValue]).target
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    override fun hasEdge(source: Vertex, target: Vertex): Boolean {
        validateVertex(target)
        for (e in outgoingEdges.getValue(validateVertex(source).intValue)) {
            val edge = Edge(e)
            if (edgeTarget(edge) == target) return true
            if (!directed && edgeSource(edge) == target) return true
        }
        return false
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    override fun edge(source: Vertex, target: Vertex): Edge {
        TODO()
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    override fun edges(source: Vertex, target: Vertex): EdgeSet {
        TODO()
    }

    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        initializer: VertexInitializer<T>
    ): VertexProperty<T> {
        TODO()
    }

    override fun <T : S?, S> createEdgeProperty(
        clazz: Class<S>,
        initializer: EdgeInitializer<T>
    ): EdgeProperty<T> {
        return mutableMapEdgeProperty(this, clazz, initializer)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference {
        return VertexReferenceImpl(validateVertex(vertex))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference {
        return EdgeReferenceImpl(validateEdge(edge))
    }

    private inner class NeighborSet(
        private val vertex: Vertex,
        private val successors: Boolean,
        private val sortedEdges: LongArray,
    ) : VertexSet, AbstractVertexCollection() {
        override val size: Int get() = sortedEdges.size

        private fun getNeighbor(edgeLongValue: Long): Vertex {
            val edge = Edge(edgeLongValue)
            return if (directed) {
                if (successors) {
                    edgeTarget(edge)
                } else {
                    edgeSource(edge)
                }
            } else {
                edgeOpposite(edge, vertex)
            }
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Vertex): Boolean {
            for (edge in sortedEdges) {
                if (getNeighbor(edge) == element) return true
            }
            return false
        }

        override fun iterator(): VertexIterator = object : VertexIterator {
            private var index = 0
            override fun hasNext(): Boolean = index < sortedEdges.size
            override fun next(): Vertex {
                if (index >= sortedEdges.size) throw NoSuchElementException()
                return getNeighbor(sortedEdges[index++])
            }
        }
    }

    private inner class IncidentEdgeSet(
        private val sortedEdges: LongArray,
    ) : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = sortedEdges.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            return sortedEdges.binarySearch(validateEdge(element).longValue) != -1
        }

        override fun iterator(): EdgeIterator = sortedEdges.iterator().asEdgeIterator()
    }
}
