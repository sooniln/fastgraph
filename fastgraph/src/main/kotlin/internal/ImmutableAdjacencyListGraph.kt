package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractIndexedVertexSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexConsumer
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexIterator
import io.github.sooniln.fastgraph.compareTo
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.inc
import kotlin.math.max
import kotlin.math.min

internal class ImmutableAdjacencyListGraph private constructor(
    override val directed: Boolean,
    private val successors: Array<IntArray>,
    _predecessors: Array<IntArray>?,
    private val numEdges: Int,
) : AbstractGraph(), IndexedVertexGraph, InternalImmutableGraph {

    private val predecessors: Array<IntArray> by lazy {
        check(directed)

        if (_predecessors != null) {
            return@lazy _predecessors
        }

        if (successors.isEmpty()) {
            return@lazy emptyArray()
        }

        val pds = Array(successors.size) { IntArrayList() }
        for (vertexIntValue in successors.indices) {
            val vertex = Vertex(vertexIntValue)
            for (successor in successors(vertex)) {
                pds[successor.id].add(vertexIntValue)
            }
        }
        return@lazy Array(pds.size) { pds[it].toIntArray().apply { sort() } }
    }

    init {
        if (_predecessors != null) {
            // touch lazy property to force initialization
            predecessors
        }
    }

    override val multiEdge: Boolean
        get() = false

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in successors.indices) throwIllegalVertex(vertex)
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        try {
            validateVertex(edgeSource(edge))
            validateVertex(edgeTarget(edge))
        } catch (e: IllegalArgumentException) {
            throwIllegalEdge(edge, e)
        }
        return edge
    }

    override val vertices: IndexedVertexSet = object : AbstractIndexedVertexSet() {
        override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = VertexNeighbors(successors[vertex])
    override fun getPredecessors(vertex: Vertex): VertexSet = VertexNeighbors(predecessors[vertex])
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = OutgoingIncidentEdgeSet(vertex, successors[vertex])
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncomingIncidentEdgeSet(vertex, predecessors[vertex])

    override val edges: EdgeSet = object : AbstractEdgeSet() {
        override val size: Int get() = numEdges

        override fun contains(element: Edge): Boolean {
            return hasEdge(edgeSource(element), edgeTarget(element))
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private var source = INVALID_VERTEX
            private var successor = intArrayOf()
            private var successorIndex = 0
            private var target = INVALID_VERTEX

            init {
                increment()
            }

            override fun hasNext(): Boolean = source < successors.size
            override fun next(): Edge {
                if (source >= successors.size) throw NoSuchElementException()
                val edge = canonicalSortedEdge(source, target)
                increment()
                return edge
            }

            private fun increment() {
                do {
                    while (successorIndex == successor.size && ++source < successors.size) {
                        successor = successors[source]
                        successorIndex = 0
                    }
                    if (successorIndex == successor.size) return

                    target = Vertex(successor[successorIndex++])

                    // don't report the same edge twice in undirected graphs - we only report an edge when we see a
                    // source less than or equal to the target. this works because we know we'll encounter every
                    // undirected edge twice since we're iterating over all vertices.
                } while (!directed && source > target)
            }
        }

        override fun foreach(action: EdgeConsumer) {
            for (index in successors.indices) {
                val source = Vertex(index)
                successors[source].foreachVertex { target ->
                    // don't report the same edge twice in undirected graphs - we only report an edge when we see a
                    // source less than or equal to the target. this works because we know we'll encounter every
                    // undirected edge twice since we're iterating over all vertices.
                    if (directed || source <= target) {
                        action.accept(canonicalSortedEdge(source, target))
                    }
                }
            }
        }
    }

    override fun edgeSource(edge: Edge): Vertex = Vertex(edge.highBits)

    override fun edgeTarget(edge: Edge): Vertex = Vertex(edge.lowBits)

    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source].binarySearch(target) >= 0

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        if (!containsEdge(source, target)) throw NoSuchElementException()
        return canonicalEdge(source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return if (!containsEdge(source, target)) emptyEdgeSet() else edgeSetOf(canonicalEdge(source, target))
    }

    override fun <T> createVertexProperty(
        type: StaticType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return createVertexProperty(this, type, defaultValueFunction)
    }

    override fun <T> createEdgeProperty(
        type: StaticType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return createEdgeProperty(this, type, defaultValueFunction)
    }

    override fun createVertexReference(vertex: Vertex): VertexReference =
        ImmutableVertexReference(validateVertex(vertex))

    override fun createEdgeReference(edge: Edge): EdgeReference = ImmutableEdgeReference(validateEdge(edge))

    private class VertexNeighbors(private val sortedNeighbors: IntArray) : AbstractVertexSet() {
        override val size: Int get() = sortedNeighbors.size

        override fun contains(element: Vertex): Boolean = sortedNeighbors.binarySearch(element.id) >= 0

        override fun iterator(): VertexIterator = sortedNeighbors.iterator().asVertexIterator()
        override fun foreach(action: VertexConsumer) {
            for (neighbor in sortedNeighbors) {
                action.accept(Vertex(neighbor))
            }
        }

        override fun toIntArray(): IntArray = sortedNeighbors.copyOf()
    }

    private inner class OutgoingIncidentEdgeSet(
        private val vertex: Vertex,
        private val sortedNeighbors: IntArray,
    ) : AbstractEdgeSet() {
        override val size: Int get() = sortedNeighbors.size

        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeSource(element)
            val target = edgeTarget(element)

            return if (!directed && target == vertex) {
                sortedNeighbors.binarySearch(source.id) >= 0
            } else {
                vertex == source && sortedNeighbors.binarySearch(target.id) >= 0
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = sortedNeighbors.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(vertex, Vertex(it.nextInt()))
        }

        override fun foreach(action: EdgeConsumer) {
            for (neighbor in sortedNeighbors) {
                action.accept(canonicalEdge(vertex, Vertex(neighbor)))
            }
        }
    }

    private inner class IncomingIncidentEdgeSet(
        private val vertex: Vertex,
        private val sortedNeighbors: IntArray,
    ) : AbstractEdgeSet() {
        override val size: Int get() = sortedNeighbors.size

        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeTarget(element)
            val target = edgeSource(element)

            return if (!directed && target == vertex) {
                sortedNeighbors.binarySearch(source.id) >= 0
            } else {
                vertex == source && sortedNeighbors.binarySearch(target.id) >= 0
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = sortedNeighbors.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(Vertex(it.nextInt()), vertex)
        }

        override fun foreach(action: EdgeConsumer) {
            for (neighbor in sortedNeighbors) {
                action.accept(canonicalEdge(Vertex(neighbor), vertex))
            }
        }
    }

    private operator fun Array<IntArray>.get(vertex: Vertex) = get(vertex.id)
    private fun IntArray.binarySearch(vertex: Vertex) = binarySearch(vertex.id)
    private inline fun IntArray.foreachVertex(crossinline action: (Vertex) -> Unit) {
        for (value in this) {
            action(Vertex(value))
        }
    }

    private fun canonicalEdge(source: Vertex, target: Vertex): Edge {
        return if (!directed) {
            Edge(highBits = min(source.id, target.id), lowBits = max(source.id, target.id))
        } else {
            Edge(highBits = source.id, lowBits = target.id)
        }
    }

    // only use if you know directed || source <= target
    private fun canonicalSortedEdge(source: Vertex, target: Vertex): Edge {
        assert(directed || source <= target)
        return Edge(highBits = source.id, lowBits = target.id)
    }

    companion object {
        private val INVALID_VERTEX = Vertex(-1)

        fun copy(graph: AdjacencyListGraph): ImmutableGraph {
            val successors = context(graph) { Array(graph.vertices.size) { vertexId ->
                val vertex = Vertex(vertexId)
                IntArray(vertex.outDegree).also {
                    var i = 0
                    vertex.successors().foreach { successorId -> it[i++] = successorId.id }
                }.apply { sort() }
            } }
            return ImmutableAdjacencyListGraph(graph.directed, successors, null, graph.edges.size)
        }
    }
}
