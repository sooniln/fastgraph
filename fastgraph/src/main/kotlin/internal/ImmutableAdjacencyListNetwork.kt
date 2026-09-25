package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.Int2IntMap
import io.github.sooniln.fastgraph.AbstractEdgeSequencedSet
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeSet
import io.github.sooniln.fastgraph.IdentityIndexedVertexSet
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.util.cheapLazy

internal class ImmutableAdjacencyListNetwork private constructor(
    override val directed: Boolean,
    override val multiEdge: Boolean,
    private val successors: Adjacencies,
    private val edgeValues: EdgeValueArray,
) : AbstractGraph<EdgeSet>(), InternalImmutableGraph {

    // neighbors of vertex i are targets[targetOffsets[i]..<targetOffsets[i + 1]], sorted ascending
    // edges of vertex i are edgeIds[edgeOffsets[targetOffsets[i]]..<edgeOffset[targetOffsets[i + 1]]]
    private class Adjacencies private constructor(
        private val targetOffsets: IntArray,
        private val targets: IntArray,
        private val edgeOffsets: IntArray,
        private val edgeIds: IntArray,
    ) {

        inner class Edges(private val edgeStart: Int, private val edgeEnd: Int) {
            val size: Int get() = edgeEnd - edgeStart
            fun iterator(): EdgeIterator = edgeIterator(edgeStart, edgeEnd)
        }

        init {
            assert(targetOffsets[0] == 0)
            assert(targetOffsets[targetOffsets.lastIndex] == targets.size)
            assert(edgeOffsets[0] == 0)
            assert(edgeOffsets[edgeOffsets.lastIndex] == edgeIds.size)
        }

        val size: Int inline get() = targetOffsets.lastIndex

        private fun start(vertex: Vertex): Int = targetOffsets[vertex.id]
        private fun end(vertex: Vertex): Int = targetOffsets[vertex.id + 1]
        private fun edgeStart(vertex: Vertex): Int = edgeOffsets[start(vertex)]
        private fun edgeEnd(vertex: Vertex): Int = edgeOffsets[end(vertex)]
        private fun slot(source: Vertex, target: Vertex): Int = targets.binarySearch(target.id, start(source), end(source))
        private fun edgeStart(slot: Int): Int = edgeOffsets[slot]
        private fun edgeEnd(slot: Int): Int = edgeOffsets[slot + 1]

        fun degree(vertex: Vertex): Int = edgeEnd(vertex) - edgeStart(vertex)

        fun isAdjacent(source: Vertex, target: Vertex): Boolean = slot(source, target) >= 0

        fun adjacency(vertex: Vertex): Vertex {
            val start = start(vertex)
            check(end(vertex) - start == 1)
            return Vertex(targets[start])
        }

        fun adjacencyCount(vertex: Vertex): Int = end(vertex) - start(vertex)

        fun adjacencies(vertex: Vertex): VertexSet = object : AbstractVertexSet() {
            override val size: Int get() = adjacencyCount(vertex)
            override fun contains(element: Vertex): Boolean = isAdjacent(vertex, element)
            override fun iterator(): VertexIterator = object : VertexIterator {
                private var i = start(vertex)
                private val end = end(vertex)
                override fun hasNext(): Boolean = i < end
                override fun next(): Vertex {
                    if (!hasNext()) throw NoSuchElementException()
                    return Vertex(targets[i++])
                }
            }
            override fun toIntArray(): IntArray {
                return targets.copyOfRange(start(vertex), end(vertex))
            }
        }

        fun edge(vertex: Vertex): Edge {
            val start = edgeStart(vertex)
            check(edgeEnd(vertex) - start == 1)
            return IdentityIndexedEdge(edgeIds[start]).toEdge()
        }

        fun edges(vertex: Vertex): Edges = Edges(edgeStart(vertex), edgeEnd(vertex))

        fun edge(source: Vertex, target: Vertex): Edge {
            val slot = slot(source, target)
            check(slot >= 0)
            val start = edgeStart(slot)
            check(edgeEnd(slot) - start == 1)
            return IdentityIndexedEdge(edgeIds[start]).toEdge()
        }

        fun edges(source: Vertex, target: Vertex): Edges {
            val slot = slot(source, target)
            return if (slot < 0) Edges(0, 0) else Edges(edgeStart(slot), edgeEnd(slot))
        }

        fun edgeIterator(edgeStart: Int, edgeEnd: Int): EdgeIterator = object : EdgeIterator {
            private var i = edgeStart
            override fun hasNext(): Boolean = i < edgeEnd
            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                return IdentityIndexedEdge(edgeIds[i++]).toEdge()
            }
        }

        fun transpose(): Adjacencies {
            val numSlots = targets.size

            val transposedTargetOffsets = IntArray(targetOffsets.size)
            for (target in targets) {
                transposedTargetOffsets[target]++
            }
            for (i in 1..size) {
                transposedTargetOffsets[i] += transposedTargetOffsets[i - 1]
            }
            // fill each slice from its end, scanning sources in descending order so slices come out ascending. this
            // walks every cursor back to the start of its slice, leaving transposedTargetOffsets as the final offsets
            // array. slotMap records the original slot that landed in each transposed slot.
            val transposedTargets = IntArray(numSlots)
            val slotMap = IntArray(numSlots)
            for (source in size - 1 downTo 0) {
                for (slot in targetOffsets[source]..<targetOffsets[source + 1]) {
                    val k = --transposedTargetOffsets[targets[slot]]
                    transposedTargets[k] = source
                    slotMap[k] = slot
                }
            }

            // a transposed slot owns the same (already sorted) edge ids as the original slot it came from
            val transposedEdgeOffsets = IntArray(numSlots + 1)
            for (k in 0..<numSlots) {
                val slot = slotMap[k]
                transposedEdgeOffsets[k + 1] = transposedEdgeOffsets[k] + edgeOffsets[slot + 1] - edgeOffsets[slot]
            }
            val transposedEdgeIds = IntArray(edgeIds.size)
            for (k in 0..<numSlots) {
                val slot = slotMap[k]
                edgeIds.copyInto(transposedEdgeIds, transposedEdgeOffsets[k], edgeOffsets[slot], edgeOffsets[slot + 1])
            }
            return Adjacencies(transposedTargetOffsets, transposedTargets, transposedEdgeOffsets, transposedEdgeIds)
        }

        companion object {
            fun createSuccessors(graph: Graph): Adjacencies {
                require(graph.vertices is IdentityIndexedVertexSet)
                require(graph.edges is IdentityIndexedEdgeSet)

                val n = graph.vertices.size
                val targetOffsets = IntArray(n + 1)
                var numEdgeIds = 0
                for (vertexId in 0..<n) {
                    val vertex = Vertex(vertexId)
                    targetOffsets[vertexId + 1] = targetOffsets[vertexId] + graph.successorsCount(vertex)
                    numEdgeIds += graph.outgoingEdgeCount(vertex)
                }
                val targets = IntArray(targetOffsets[n])
                for (vertexId in 0..<n) {
                    var i = targetOffsets[vertexId]
                    for (successor in graph.successors(Vertex(vertexId))) { targets[i++] = successor.id }
                    targets.sort(targetOffsets[vertexId], i)
                }
                val edgeOffsets = IntArray(targets.size + 1)
                val edgeIds = IntArray(numEdgeIds)
                for (vertexId in 0..<n) {
                    val vertex = Vertex(vertexId)
                    for (slot in targetOffsets[vertexId]..<targetOffsets[vertexId + 1]) {
                        var i = edgeOffsets[slot]
                        for (edge in graph.edges(vertex, Vertex(targets[slot]))) { edgeIds[i++] = IdentityIndexedEdge.from(edge).id }
                        edgeIds.sort(edgeOffsets[slot], i)
                        edgeOffsets[slot + 1] = i
                    }
                }
                return Adjacencies(targetOffsets, targets, edgeOffsets, edgeIds)
            }
        }
    }

    private val predecessors: Adjacencies by cheapLazy { check(directed); successors.transpose() }

    // number of undirected self-loops per vertex, which count twice towards degree (null if there are none)
    private val selfLoopCounts by cheapLazy {
        check(!directed)
        var selfLoopCounts = Int2IntHashMap(defaultValue = 0)
        for (vertexId in 0..<successors.size) {
            val count = successors.edges(Vertex(vertexId), Vertex(vertexId)).size
            if (count > 0) {
                selfLoopCounts[vertexId] = count
            }
        }
        return@cheapLazy selfLoopCounts
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
        return if (!directed) degree + selfLoopCounts[vertex.id] else degree
    }
    override fun getInDegree(vertex: Vertex): Int = predecessors.degree(vertex)
    override fun getSuccessorsCount(vertex: Vertex): Int = successors.adjacencyCount(vertex)
    override fun getSuccessors(vertex: Vertex): VertexSet = successors.adjacencies(vertex)
    override fun getSuccessor(vertex: Vertex): Vertex = successors.adjacency(vertex)
    override fun getPredecessorsCount(vertex: Vertex): Int = predecessors.adjacencyCount(vertex)
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors.adjacencies(vertex)
    override fun getPredecessor(vertex: Vertex): Vertex = predecessors.adjacency(vertex)
    override fun getOutgoingEdgeCount(vertex: Vertex): Int = successors.degree(vertex)
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = OutgoingEdges(vertex)
    override fun getOutgoingEdge(vertex: Vertex): Edge = successors.edge(vertex)
    override fun getIncomingEdgeCount(vertex: Vertex): Int = predecessors.degree(vertex)
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncomingEdges(vertex)
    override fun getIncomingEdge(vertex: Vertex): Edge = predecessors.edge(vertex)

    override val edges: IdentityIndexedEdgeSet = object : IdentityIndexedEdgeSet, AbstractEdgeSequencedSet() {
        override val size: Int get() = edgeValues.size
    }

    override fun edgeSource(edge: Edge): Vertex = edgeValues[edge.id.toInt()].source
    override fun edgeTarget(edge: Edge): Vertex = edgeValues[edge.id.toInt()].target

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors.isAdjacent(source, target)

    override fun getEdgesCount(source: Vertex, target: Vertex): Int = successors.edges(source, target).size

    override fun getEdge(source: Vertex, target: Vertex): Edge = successors.edge(source, target)
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet = EdgesBetween(source, target)

    private inner class OutgoingEdges(private val vertex: Vertex) : AbstractEdgeSet() {
        private val edges = successors.edges(vertex)

        override val size: Int get() = edges.size
        override fun contains(element: Edge): Boolean {
            val id = IdentityIndexedEdge.from(element).id
            if (id !in edgeValues.indices) return false
            val value = edgeValues[id]
            return value.source == vertex || (!directed && value.target == vertex)
        }
        override fun iterator(): EdgeIterator = edges.iterator()
    }

    private inner class IncomingEdges(private val vertex: Vertex) : AbstractEdgeSet() {
        private val edges = predecessors.edges(vertex)

        override val size: Int get() = edges.size
        override fun contains(element: Edge): Boolean {
            val id = IdentityIndexedEdge.from(element).id
            return id in edgeValues.indices && edgeValues[id].target == vertex
        }
        override fun iterator(): EdgeIterator = edges.iterator()
    }

    private inner class EdgesBetween(source: Vertex, target: Vertex) : AbstractEdgeSet() {
        private val edges = successors.edges(source, target)
        private val edgeValue = EdgeValue(directed, source, target)

        override val size: Int get() = edges.size
        override fun contains(element: Edge): Boolean {
            val id = IdentityIndexedEdge.from(element).id
            return id in edgeValues.indices && edgeValues[id] == edgeValue
        }
        override fun iterator(): EdgeIterator = edges.iterator()
    }

    companion object {
        fun copy(graph: Graph): ImmutableGraph {
            return ImmutableAdjacencyListNetwork(
                graph.directed,
                graph.multiEdge,
                Adjacencies.createSuccessors(graph),
                EdgeValueArray(graph.edges.size) { index ->
                    val edge = Edge(index.toLong())
                    EdgeValue(graph.directed, graph.edgeSource(edge), graph.edgeTarget(edge))
                })
        }
    }
}
