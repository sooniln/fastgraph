package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.CanonicalEdgeGraph
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IdentityIndexedVertexSet
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.MutableCanonicalEdgeSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.edgeIteratorOf
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.util.cheapLazy
import java.util.BitSet

internal class ImmutableAdjacencyListGraph private constructor(
    override val directed: Boolean,
    private val successors: Adjacencies,
    private val numEdges: Int,
) : AbstractGraph<CanonicalEdgeSet>(), CanonicalEdgeGraph, InternalImmutableGraph {

    override val multiEdge: Boolean get() = false

    override fun edgeSource(edge: Edge): Vertex = CanonicalEdge.from(edge).source
    override fun edgeTarget(edge: Edge): Vertex = CanonicalEdge.from(edge).target

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
            fun createSuccessors(graph: Graph): Adjacencies {
                require(graph.vertices is IdentityIndexedVertexSet)
                require(graph.edges is CanonicalEdgeSet)

                val numVertices = graph.vertices.size
                val offsets = IntArray(numVertices + 1)
                for (vertexId in 0..<numVertices) {
                    offsets[vertexId + 1] = offsets[vertexId] + graph.successorsCount(Vertex(vertexId))
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

    private val selfLoops: BitSet by cheapLazy {
        check(!directed)

        val selfLoops = BitSet()
        for (vertexId in 0..<successors.size) {
            if (successors.isAdjacent(Vertex(vertexId), Vertex(vertexId))) {
                selfLoops.set(vertexId)
            }
        }
        return@cheapLazy selfLoops
    }

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in 0..<successors.size) throwIllegalVertex(vertex)
        return vertex
    }

    override val vertices: IdentityIndexedVertexSet = object : IdentityIndexedVertexSet, AbstractVertexSequencedSet() {
        override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int {
        val degree = successors.degree(vertex)
        return if (!directed && selfLoops.get(vertex.id)) degree + 1 else degree
    }
    override fun getInDegree(vertex: Vertex): Int = predecessors.degree(vertex)
    override fun getSuccessorsCount(vertex: Vertex): Int = successors.degree(vertex)
    override fun getSuccessors(vertex: Vertex): VertexSet = successors.adjacencies(vertex)
    override fun getSuccessor(vertex: Vertex): Vertex = successors.adjacency(vertex)
    override fun getPredecessorsCount(vertex: Vertex): Int = predecessors.degree(vertex)
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors.adjacencies(vertex)
    override fun getPredecessor(vertex: Vertex): Vertex = predecessors.adjacency(vertex)
    override fun getOutgoingEdgeCount(vertex: Vertex): Int = successors.degree(vertex)
    override fun getOutgoingEdges(vertex: Vertex): CanonicalEdgeSet = OutgoingIncidentEdgeSet(vertex)
    override fun getOutgoingEdge(vertex: Vertex): Edge = canonicalEdge(vertex, successors.adjacency(vertex))
    override fun getIncomingEdgeCount(vertex: Vertex): Int = predecessors.degree(vertex)
    override fun getIncomingEdges(vertex: Vertex): CanonicalEdgeSet = IncomingIncidentEdgeSet(vertex)
    override fun getIncomingEdge(vertex: Vertex): Edge = canonicalEdge(predecessors.adjacency(vertex), vertex)

    override val edges: CanonicalEdgeSet = object : CanonicalEdgeSet, AbstractEdgeSet() {
        override val size: Int get() = numEdges
        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            val source = edge.source
            return source.id in 0..<successors.size && successors.isAdjacent(source, edge.target)
        }
        override fun iterator(): EdgeIterator = successors.edgeIterator(directed)
    }

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors.isAdjacent(source, target)

    override fun getEdgesCount(source: Vertex, target: Vertex): Int = if (containsEdge(source, target)) 1 else 0

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        check(containsEdge(source, target))
        return canonicalEdge(source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): CanonicalEdgeSet {
        return if (!containsEdge(source, target)) {
            emptyEdgeSet()
        } else {
            object : AbstractEdgeSet(), CanonicalEdgeSet {
                override val size: Int get() = 1
                override fun iterator(): EdgeIterator = edgeIteratorOf(canonicalEdge(source, target))
            }
        }
    }

    private inner class OutgoingIncidentEdgeSet(private val vertex: Vertex) : AbstractEdgeSet(), CanonicalEdgeSet {
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

    private inner class IncomingIncidentEdgeSet(private val vertex: Vertex) : AbstractEdgeSet(), CanonicalEdgeSet {
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
        fun copy(graph: Graph): ImmutableGraph {
            return ImmutableAdjacencyListGraph(graph.directed, Adjacencies.createSuccessors(graph), graph.edges.size)
        }
    }
}
