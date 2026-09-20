package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.CanonicalEdgeGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IdentityIndexedVertexGraph
import io.github.sooniln.fastgraph.IdentityIndexedVertexSet
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.util.cheapLazy

internal class ImmutableAdjacencyListGraph private constructor(
    override val directed: Boolean,
    private val successors: Adjacencies,
    private val numEdges: Int,
) : AbstractGraph(), IdentityIndexedVertexGraph, CanonicalEdgeGraph, InternalImmutableGraph {

    // neighbors of vertex i are targets[offsets[i]..<offsets[i + 1]], sorted ascending
    private class Adjacencies private constructor(private val offsets: IntArray, private val targets: IntArray) {
        init {
            assert(offsets[0] == 0)
            assert(offsets[offsets.lastIndex] == targets.size)
        }

        val size: Int inline get() = offsets.lastIndex

        private fun start(vertex: Vertex): Int = offsets[vertex.id]
        private fun end(vertex: Vertex): Int = offsets[vertex.id + 1]

        fun degree(vertex: Vertex): Int {
            return end(vertex) - start(vertex)
        }

        fun isAdjacent(source: Vertex, target: Vertex): Boolean {
            return targets.binarySearch(target.id, start(source), end(source)) >= 0
        }

        fun adjacency(vertex: Vertex): Vertex {
            val start = start(vertex)
            check(end(vertex) - start == 1)
            return Vertex(targets[start])
        }

        fun adjacencies(vertex: Vertex): VertexSet = object : AbstractVertexSet() {
            override val size: Int get() = degree(vertex)
            override fun contains(element: Vertex): Boolean = isAdjacent(vertex, element)
            override fun iterator(): VertexIterator = adjacenciesIterator(vertex)
            override fun toIntArray(): IntArray = targets.copyOfRange(start(vertex), end(vertex))
        }

        fun adjacenciesIterator(vertex: Vertex): VertexIterator = object : VertexIterator {
            private var i = start(vertex)
            private val end = end(vertex)
            override fun hasNext(): Boolean = i < end
            override fun next(): Vertex {
                if (!hasNext()) throw NoSuchElementException()
                return Vertex(targets[i++])
            }
        }

        fun edgeIterator(directed: Boolean): EdgeIterator = object : EdgeIterator {
            private var source = 0
            private var target = -1

            init {
                advance()
            }

            override fun hasNext(): Boolean = target < targets.size
            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                val edge = CanonicalEdge.fromSorted(directed, Vertex(source), Vertex(targets[target])).toEdge()
                advance()
                return edge
            }

            private fun advance() {
                do {
                    if (++target >= targets.size) return
                    while (target >= offsets[source + 1]) source++

                    // don't report the same edge twice in undirected graphs - we only report an edge when we see a
                    // source less than or equal to the target. this works because we know we'll encounter every
                    // undirected edge twice since we're iterating over all vertices.
                } while (!directed && source > targets[target])
            }
        }

        fun transpose(): Adjacencies {
            val transposedOffsets = IntArray(offsets.size)
            for (target in targets) {
                transposedOffsets[target]++
            }
            for (i in 1..<transposedOffsets.size) {
                transposedOffsets[i] += transposedOffsets[i - 1]
            }
            val transposedTargets = IntArray(targets.size)
            for (source in (offsets.size - 2) downTo 0) {
                for (i in offsets[source]..<offsets[source + 1]) {
                    transposedTargets[--transposedOffsets[targets[i]]] = source
                }
            }
            return Adjacencies(transposedOffsets, transposedTargets)
        }

        companion object {
            fun <G> createSuccessors(graph: G): Adjacencies where G : IdentityIndexedVertexGraph, G : CanonicalEdgeGraph {
                val numVertices = graph.vertices.size
                val offsets = IntArray(numVertices + 1)
                for (vertexId in 0..<numVertices) {
                    offsets[vertexId + 1] = offsets[vertexId] + graph.outDegree(Vertex(vertexId))
                }
                val targets = IntArray(offsets[numVertices])
                for (vertexId in 0..<numVertices) {
                    var i = offsets[vertexId]
                    for (successor in graph.successors(Vertex(vertexId))) {
                        targets[i++] = successor.id
                    }
                    targets.sort(offsets[vertexId], i)
                }
                return Adjacencies(offsets, targets)
            }
        }
    }

    private val predecessors: Adjacencies by cheapLazy { check(directed); successors.transpose() }

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in 0..<successors.size) throwIllegalVertex(vertex)
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        val canonicalEdge = CanonicalEdge.from(edge)
        val size = successors.size
        if (canonicalEdge.source.id !in 0..<size || canonicalEdge.target.id !in 0..<size) throwIllegalEdge(canonicalEdge)
        return edge
    }

    override val vertices: IdentityIndexedVertexSet = object : IdentityIndexedVertexSet, AbstractVertexSequencedSet() {
        override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors.degree(vertex)
    override fun getInDegree(vertex: Vertex): Int = predecessors.degree(vertex)
    override fun getSuccessors(vertex: Vertex): VertexSet = successors.adjacencies(vertex)
    override fun getSuccessor(vertex: Vertex): Vertex = successors.adjacency(vertex)
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors.adjacencies(vertex)
    override fun getPredecessor(vertex: Vertex): Vertex = predecessors.adjacency(vertex)
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = OutgoingIncidentEdgeSet(vertex)
    override fun getOutgoingEdge(vertex: Vertex): Edge = canonicalEdge(vertex, successors.adjacency(vertex))
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncomingIncidentEdgeSet(vertex)
    override fun getIncomingEdge(vertex: Vertex): Edge = canonicalEdge(predecessors.adjacency(vertex), vertex)

    override val edges: EdgeSet = object : AbstractEdgeSet() {
        override val size: Int get() = numEdges
        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            val source = edge.source
            return source.id in 0..<successors.size && successors.isAdjacent(source, edge.target)
        }
        override fun iterator(): EdgeIterator = successors.edgeIterator(directed)
    }

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors.isAdjacent(source, target)

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        check(containsEdge(source, target))
        return canonicalEdge(source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return if (!containsEdge(source, target)) emptyEdgeSet() else edgeSetOf(canonicalEdge(source, target))
    }

    private inner class OutgoingIncidentEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        override val size: Int get() = successors.degree(vertex)
        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            val source = edge.source
            val target = edge.target

            return if (!directed && target == vertex) {
                successors.isAdjacent(vertex, source)
            } else {
                vertex == source && successors.isAdjacent(vertex, target)
            }
        }
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = successors.adjacenciesIterator(vertex)
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(vertex, it.next())
        }
    }

    private inner class IncomingIncidentEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        init { check(directed) }

        override val size: Int get() = predecessors.degree(vertex)
        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            return vertex == edge.target && predecessors.isAdjacent(vertex, edge.source)
        }
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = predecessors.adjacenciesIterator(vertex)
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(it.next(), vertex)
        }
    }

    private fun canonicalEdge(source: Vertex, target: Vertex): Edge {
        return CanonicalEdge.from(directed, source, target).toEdge()
    }

    companion object {
        fun <G> copy(graph: G): ImmutableGraph where G : IdentityIndexedVertexGraph, G : CanonicalEdgeGraph {
            return ImmutableAdjacencyListGraph(graph.directed, Adjacencies.createSuccessors(graph), graph.edges.size)
        }
    }
}
