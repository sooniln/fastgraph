package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.Int2LongHashMap
import io.github.sooniln.fastcollect.Long2IntHashMap
import io.github.sooniln.fastgraph.AbstractEdgeSequencedSet
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.CanonicalEdgeGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.GraphCopy
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeSet
import io.github.sooniln.fastgraph.IdentityIndexedVertexSet
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.homomorphisms.Isomorphism
import io.github.sooniln.fastgraph.homomorphisms.identityEdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.identityVertexIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.isomorphism
import io.github.sooniln.fastgraph.homomorphisms.keyEdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.keyVertexIsomorphism
import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListGraph.Companion.CopyVertexProperty
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.EdgeProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.VertexKeyProperty
import io.github.sooniln.fastgraph.properties.VertexProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf
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
            fun createSuccessors(
                graph: Graph,
                vertexMap: IntArray?,
                reverseVertexMap: Int2IntHashMap?,
                edgeMap: LongArray?,
                reverseEdgeMap: Long2IntHashMap?,
            ): Adjacencies {
                require((vertexMap == null) == (reverseVertexMap == null))
                require(vertexMap != null || graph.vertices is IdentityIndexedVertexSet)
                if (vertexMap != null) require(vertexMap.size == graph.vertices.size)
                reverseVertexMap?.ensureCapacity(graph.vertices.size)

                require((edgeMap == null) == (reverseEdgeMap == null))
                require(edgeMap != null || graph.edges is IdentityIndexedEdgeSet)
                if (edgeMap != null) require(edgeMap.size == graph.edges.size)
                reverseEdgeMap?.ensureCapacity(graph.edges.size)

                val n = graph.vertices.size
                val targetOffsets = IntArray(n + 1)
                var numEdgeIds = 0
                var vertexId = 0
                for (vertex in graph.vertices) {
                    vertexMap?.set(vertexId, vertex.id)
                    reverseVertexMap?.set(vertex.id, vertexId)

                    targetOffsets[vertexId + 1] = targetOffsets[vertexId] + graph.successorsCount(vertex)
                    numEdgeIds += graph.outgoingEdgeCount(vertex)
                    vertexId++
                }
                val targets = IntArray(targetOffsets[n])
                vertexId = 0
                for (vertex in graph.vertices) {
                    var i = targetOffsets[vertexId]
                    for (successor in graph.successors(vertex)) {
                        targets[i++] = if (reverseVertexMap != null) reverseVertexMap[successor.id] else successor.id
                    }
                    targets.sort(targetOffsets[vertexId], i)
                    vertexId++
                }
                val edgeOffsets = IntArray(targets.size + 1)
                val edgeIds = IntArray(numEdgeIds)
                vertexId = 0
                var edgeId = 0
                for (vertex in graph.vertices) {
                    for (slot in targetOffsets[vertexId]..<targetOffsets[vertexId + 1]) {
                        var i = edgeOffsets[slot]
                        val successor = if (reverseVertexMap != null) {
                            Vertex(reverseVertexMap[targets[slot]])
                        } else {
                            Vertex(targets[slot])
                        }
                        for (edge in graph.edges(vertex, successor)) {
                            edgeMap?.set(edgeId, edge.id)
                            if (reverseEdgeMap != null) {
                                reverseEdgeMap[edge.id] = edgeId
                                edgeIds[i++] = edgeId++
                            } else {
                                edgeIds[i++] = IdentityIndexedEdge.from(edge).id
                            }
                        }
                        if (reverseEdgeMap != null) edgeIds.sort(edgeOffsets[slot], i)
                        edgeOffsets[slot + 1] = i
                    }
                    vertexId++
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
        fun copy(graph: Graph): GraphCopy<ImmutableGraph> {
            val vertexMap: IntArray?
            val reverseVertexMap: Int2IntHashMap?
            val edgeMap: LongArray?
            val reverseEdgeMap: Long2IntHashMap?

            if (graph.vertices is IdentityIndexedVertexSet) {
                vertexMap = null
                reverseVertexMap = null
            } else {
                vertexMap = IntArray(graph.vertices.size)
                reverseVertexMap = Int2IntHashMap(graph.vertices.size)
            }
            if (graph.edges is IdentityIndexedEdgeSet) {
                edgeMap = null
                reverseEdgeMap = null
            } else {
                edgeMap = LongArray(graph.edges.size)
                reverseEdgeMap = Long2IntHashMap(graph.edges.size)
            }

            val adjacencies = Adjacencies.createSuccessors(graph, vertexMap, reverseVertexMap, edgeMap, reverseEdgeMap)
            val edgeValues = EdgeValueArray(LongArray(graph.edges.size))
            for (edge in graph.edges) {
                val copyEdgeId = if (reverseEdgeMap != null) reverseEdgeMap[edge.id] else IdentityIndexedEdge.from(edge).id
                val copySource: Vertex
                val copyTarget: Vertex
                if (reverseVertexMap != null) {
                    copySource = Vertex(reverseVertexMap.getValue(graph.edgeSource(edge).id))
                    copyTarget = Vertex(reverseVertexMap.getValue(graph.edgeTarget(edge).id))
                } else {
                    copySource = graph.edgeSource(edge)
                    copyTarget = graph.edgeTarget(edge)
                }

                edgeValues[copyEdgeId] = EdgeValue(graph.directed, copySource, copyTarget)
            }

            val copy = ImmutableAdjacencyListNetwork(
                graph.directed,
                graph.multiEdge,
                adjacencies,
                edgeValues
            )
            val vertexIsomorphism = if (vertexMap == null) {
                identityVertexIsomorphism(copy, graph)
            } else {
                keyVertexIsomorphism(copy, graph, CopyVertexProperty(copy, vertexMap, reverseVertexMap!!))
            }
            val edgeIsomorphism = if (edgeMap == null) {
                identityEdgeIsomorphism(copy, graph)
            } else {
                keyEdgeIsomorphism(copy, graph, CopyEdgeProperty(copy, edgeMap, reverseEdgeMap!!))
            }
            return isomorphism(vertexIsomorphism, edgeIsomorphism)
        }

        private class CopyVertexProperty(
            private val copy: ImmutableAdjacencyListNetwork,
            private val vertexMap: IntArray,
            private val reverseVertexMap: Int2IntHashMap,
        ) : VertexKeyProperty<Vertex> {
            init {
                require(copy.vertices.size == vertexMap.size)
                require(vertexMap.size == reverseVertexMap.size)
            }

            override val graph: Graph get() = copy
            override val type: PropertyType<Vertex> get() = propertyTypeOf()
            override fun get(vertex: Vertex): Vertex = Vertex(vertexMap[vertex.id])
            override fun hasVertex(key: Vertex): Boolean = reverseVertexMap.containsKey(key.id)
            override fun getVertex(key: Vertex): Vertex = Vertex(reverseVertexMap.getValue(key.id))
        }

        private class CopyEdgeProperty(
            private val copy: ImmutableAdjacencyListNetwork,
            private val edgeMap: LongArray,
            private val reverseEdgeMap: Long2IntHashMap,
        ) : EdgeKeyProperty<Edge> {
            init {
                require(copy.edges.size == edgeMap.size)
                require(edgeMap.size == reverseEdgeMap.size)
            }

            override val graph: Graph get() = copy
            override val type: PropertyType<Edge> get() = propertyTypeOf()
            override fun get(edge: Edge): Edge = Edge(edgeMap[IdentityIndexedEdge.from(edge).id])
            override fun hasEdge(key: Edge): Boolean = reverseEdgeMap.containsKey(key.id)
            override fun getEdge(key: Edge): Edge = IdentityIndexedEdge(reverseEdgeMap.getValue(key.id)).toEdge()
        }
    }
}
