package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractIndexedEdgeSet
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
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeSet
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
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.indices
import io.github.sooniln.fastgraph.vertexSetOf

internal class ImmutableAdjacencyListNetwork private constructor(
    override val directed: Boolean,
    override val multiEdge: Boolean,
    private val successors: Array<IntArray>,
    _predecessors: Array<IntArray>?,
    private val edgeValues: EdgeValueArray,
) : AbstractGraph(), IndexedVertexGraph, IndexedEdgeGraph, InternalImmutableGraph {

    private val predecessors: Array<IntArray> by lazy {
        check(directed)

        if (_predecessors != null) {
            return@lazy _predecessors
        }

        return@lazy AdjacencySet.createPredecessors(successors)
    }

    init {
        if (_predecessors != null) {
            // touch lazy property to force initialization
            predecessors
        }
    }

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in successors.indices) throwIllegalVertex(vertex)
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        if (edge.edgeId !in edgeValues.indices) throwIllegalEdge(edge)
        return edge
    }

    override val vertices: IndexedVertexSet = object : AbstractIndexedVertexSet() {
        override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].vertices
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex].vertices
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(true, vertex, successors[vertex])
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(false, vertex, predecessors[vertex])

    override val edges: IndexedEdgeSet = object : AbstractIndexedEdgeSet() {
        override val size: Int get() = edgeValues.size
        override fun get(index: Int): Edge {
            if (index !in indices) throw IndexOutOfBoundsException()
            return canonicalEdge(index)
        }
        override fun indexOf(element: Edge): Int {
            return if (element.id in indices) element.edgeId else -1
        }

        override fun iterator(): EdgeIterator {
            return super.iterator()
        }
    }

    override fun edgeSource(edge: Edge): Vertex = edgeValues[validateEdge(edge).edgeId].source

    override fun edgeTarget(edge: Edge): Vertex = edgeValues[validateEdge(edge).edgeId].target

    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}

    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return successors[source].contains(target)
    }

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        val edgeIt = successors[source].edgesTo(target).edgeIterator()
        if (!edgeIt.hasNext()) throw NoSuchElementException()
        return edgeIt.next()
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, source, successors[source].edgesTo(target))
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

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val adjacencies: EdgeAdjacencySet,
    ) : AbstractEdgeSet() {
        override val size: Int
            get() = adjacencies.size

        override fun contains(element: Edge): Boolean {
            validateEdge(element)

            val target: Vertex
            val source: Vertex
            if (outgoing) {
                source = edgeSource(element)
                target = edgeTarget(element)
            } else {
                source = edgeTarget(element)
                target = edgeSource(element)
            }

            return if (!directed && target == vertex) {
                adjacencies.contains(EdgeAdjacency(source, element.edgeId))
            } else {
                vertex == source && adjacencies.contains(EdgeAdjacency(target, element.edgeId))
            }
        }

        override fun iterator(): EdgeIterator = adjacencies.edgeIterator()
        override fun foreach(action: EdgeConsumer) = adjacencies.foreachEdge(action)
    }

    @JvmInline
    private value class AdjacencySet(private val arr: IntArray) : EdgeAdjacencySet {

        // holds an array with the following format:
        // [0] = total number of edges represented
        // [1] = (#V) total number of distinct target vertices represented by edges
        // [2 -> (#V + 1)] = a list of distinct target vertices in sorted order
        // [#V + 2 -> #V + #V + 1] = for the given target vertex (i - #V), an absolute index into the tail region
        // below where that target vertex's edge data begins
        // [#V + #V + 2 -> end] = tail region, one span per target vertex in the same sorted order:
        //   - if there is only one edge for this target vertex, just that edge id
        //   - if there is more than one edge, a negated count (-#E) followed by the #E edge ids in sorted order
        // Because tail spans are written in the same order as the sorted vertex list, a full scan of the tail
        // region alone (without ever consulting the index array) visits every edge id in vertex order.

        private val numEdges: Int inline get() = arr[0]
        private val numVertices: Int inline get() = arr[1]

        override val size: Int get() = numEdges

        override val vertices: VertexSet
            get() = object : AbstractVertexSet() {
                override val size: Int get() = numVertices

                override fun contains(element: Vertex): Boolean = findVertex(element) >= 0

                override fun iterator(): VertexIterator = object : VertexIterator {
                    private var i = 2
                    private val end = 2 + numVertices
                    override fun hasNext(): Boolean = i < end
                    override fun next(): Vertex {
                        if (i >= end) throw NoSuchElementException()
                        return Vertex(arr[i++])
                    }
                }

                override fun foreach(action: VertexConsumer) {
                    for (i in 2..<2 + numVertices) {
                        action.accept(Vertex(arr[i]))
                    }
                }
            }

        override fun contains(element: EdgeAdjacency): Boolean {
            val vertexIndex = findVertex(element.vertex)
            if (vertexIndex < 0) {
                return false
            }

            val offset = arr[numVertices + vertexIndex]
            val head = arr[offset]
            return if (head >= 0) {
                element.edgeId == head
            } else {
                arr.binarySearch(element.edgeId, offset + 1, multiEdgeSpanEnd(offset, head)) >= 0
            }
        }

        override fun contains(vertex: Vertex): Boolean = findVertex(vertex) >= 0

        override fun edgeIterator(): EdgeIterator = object : EdgeIterator {
            private var index = tailStart

            override fun hasNext(): Boolean = index < arr.size
            override fun next(): Edge {
                if (index >= arr.size) throw NoSuchElementException()
                if (arr[index] < 0) index++
                return canonicalEdge(arr[index++])
            }
        }

        override fun foreachEdge(action: EdgeConsumer) {
            var index = tailStart
            while (index < arr.size) {
                val edgeId = arr[index]
                if (edgeId >= 0) {
                    action.accept(canonicalEdge(edgeId))
                }
                ++index
            }
        }

        inline fun foreachAdjacency(action: (Vertex, Int) -> Unit) {
            var edgeIndex = tailStart
            for (vertexIndex in 2..<numVertices + 2) {
                val vertex = Vertex(arr[vertexIndex])
                val edgeId = arr[edgeIndex]
                if (edgeId >= 0) {
                    action(vertex, edgeId)
                    edgeIndex++
                } else {
                    edgeIndex++
                    val endIndex = edgeIndex - edgeId
                    do {
                        action(vertex, arr[edgeIndex++])
                    } while (edgeIndex < endIndex)
                }
            }
        }

        private fun findVertex(vertex: Vertex) = arr.binarySearch(vertex.id, 2, 2 + arr[1])

        private val tailStart: Int inline get() = 2 + 2 * numVertices

        private fun vertexDataIdx(vertexIdx: Int) = vertexIdx + numVertices

        // offset must point at a negative (multi-edge) tail head; returns the exclusive end of its edge id span
        private fun multiEdgeSpanEnd(offset: Int, head: Int): Int {
            assert(head < 0)
            return offset + 1 - head
        }

        fun edgesTo(target: Vertex): EdgeAdjacencySet = object : EdgeAdjacencySet {
            private val edgeIdsStart: Int
            private val edgeIdsEnd: Int

            init {
                val vertexIdx = findVertex(target)
                if (vertexIdx < 0) {
                    edgeIdsStart = -1
                    edgeIdsEnd = -1
                } else {
                    val offset = arr[vertexDataIdx(vertexIdx)]
                    val head = arr[offset]
                    if (head >= 0) {
                        edgeIdsStart = offset
                        edgeIdsEnd = offset + 1
                    } else {
                        edgeIdsStart = offset + 1
                        edgeIdsEnd = multiEdgeSpanEnd(offset, head)
                    }
                }
            }

            override val size: Int
                get() = edgeIdsEnd - edgeIdsStart

            override val vertices: VertexSet get() = vertexSetOf(target)

            override fun contains(element: EdgeAdjacency): Boolean {
                return element.vertex == target && arr.binarySearch(element.edgeId, edgeIdsStart, edgeIdsEnd) >= 0
            }

            override fun contains(vertex: Vertex): Boolean = vertex == target

            override fun edgeIterator(): EdgeIterator = object : EdgeIterator {
                private var index = edgeIdsStart
                override fun hasNext(): Boolean = index < edgeIdsEnd
                override fun next(): Edge {
                    if (index >= edgeIdsEnd) throw NoSuchElementException()
                    return canonicalEdge(arr[index++])
                }
            }
        }

        companion object {
            fun <G> createSuccessors(graph: G): Array<IntArray> where G : IndexedVertexGraph, G : IndexedEdgeGraph {
                return Array(graph.vertices.size) { index ->
                    context(graph) {
                        val vertex = Vertex(index)

                        val successors = vertex.successors()
                        val numVertices = successors.size
                        val numEdges = vertex.outgoingEdges().size
                        var multiEdgeTotal = 0
                        successors.foreach { successor ->
                            val numEdges = graph.edges(vertex, successor).size
                            if (numEdges > 1) {
                                multiEdgeTotal += numEdges
                            }
                        }

                        val arr = IntArray(2 + 3 * numVertices + multiEdgeTotal)
                        arr[0] = numEdges
                        arr[1] = numVertices

                        var i = 2
                        successors.foreach { successor -> arr[i++] = successor.id }
                        arr.sort(2, i)

                        var tailIdx = i + numVertices
                        for (vIdx in 2..<i) {
                            val successor = Vertex(arr[vIdx])
                            val edges = vertex.edgesTo(successor)
                            arr[vIdx + numVertices] = tailIdx
                            if (edges.size == 1) {
                                arr[tailIdx++] = edges.iterator().next().id.toInt()
                            } else {
                                arr[tailIdx++] = -edges.size
                                val edgesStart = tailIdx
                                edges.foreach { edge ->
                                    arr[tailIdx++] = edge.id.toInt()
                                }
                                arr.sort(edgesStart, tailIdx)
                            }
                        }

                        return@context arr
                    }
                }
            }

            fun <G> createPredecessors(graph: G): Array<AdjacencySet> where G : IndexedVertexGraph, G : IndexedEdgeGraph {
                return Array(graph.vertices.size) { index ->
                    context(graph) {
                        val vertex = Vertex(index)

                        val predecessors = vertex.predecessors()
                        val numVertices = predecessors.size
                        val numEdges = vertex.incomingEdges().size
                        var multiEdgeTotal = 0
                        predecessors.foreach { predecessor ->
                            val numEdges = graph.edges(predecessor, vertex).size
                            if (numEdges > 1) {
                                multiEdgeTotal += numEdges
                            }
                        }

                        val arr = IntArray(2 + 3 * numVertices + multiEdgeTotal)
                        arr[0] = numEdges
                        arr[1] = numVertices

                        var i = 2
                        predecessors.foreach { predecessor -> arr[i++] = predecessor.id }
                        arr.sort(2, i)

                        var tailIdx = i + numVertices
                        for (vIdx in 2..<i) {
                            val predecessor = Vertex(arr[vIdx])
                            val edges = predecessor.edgesTo(vertex)
                            arr[vIdx + numVertices] = tailIdx
                            if (edges.size == 1) {
                                arr[tailIdx++] = edges.iterator().next().lowBits
                            } else {
                                arr[tailIdx++] = -edges.size
                                val edgesStart = tailIdx
                                edges.foreach { edge ->
                                    arr[tailIdx++] = graph.edges.indexOf(edge)
                                }
                                arr.sort(edgesStart, tailIdx)
                            }
                        }

                        return@context AdjacencySet(arr)
                    }
                }
            }

            fun createPredecessors(successors: Array<IntArray>): Array<IntArray> {
                if (successors.isEmpty()) return emptyArray()

                val pds = Array(successors.size) { Int2AnyHashMap<IntArrayList>() }
                for (vertexIndex in successors.indices) {
                    AdjacencySet(successors[vertexIndex]).foreachAdjacency { adjacencyVertex, edgeId ->
                        pds[adjacencyVertex.id].getOrPut(vertexIndex) { IntArrayList() }.add(edgeId)
                    }
                }

                return Array(successors.size) { vertexIndex ->
                    val predecessors = pds[vertexIndex]
                    val numVertices = predecessors.keys.size
                    val numEdges = predecessors.values.sumOf { it.size }
                    var multiEdgeTotal = 0
                    predecessors.foreach { _, edgeIds ->
                        if (edgeIds.size > 1) {
                            multiEdgeTotal += edgeIds.size
                        }
                    }

                    val arr = IntArray(2 + 3 * numVertices + multiEdgeTotal)
                    arr[0] = numEdges
                    arr[1] = numVertices

                    var i = 2
                    predecessors.foreachKey { predecessor -> arr[i++] = predecessor }
                    arr.sort(2, i)

                    var tailIdx = i + numVertices
                    for (vIdx in 2..<i) {
                        val predecessor = arr[vIdx]
                        val edges = predecessors.getValue(predecessor)
                        arr[vIdx + numVertices] = tailIdx
                        if (edges.size == 1) {
                            arr[tailIdx++] = edges[0]
                        } else {
                            arr[tailIdx++] = -edges.size
                            val edgesStart = tailIdx
                            edges.foreach { edge ->
                                arr[tailIdx++] = edge
                            }
                            arr.sort(edgesStart, tailIdx)
                        }
                    }

                    return@Array arr
                }
            }
        }
    }

    private operator fun Array<IntArray>.get(vertex: Vertex): AdjacencySet = AdjacencySet(get(vertex.id))

    companion object {
        private val Edge.edgeId: Int inline get() = lowBits

        private fun canonicalEdge(edgeId: Int): Edge = Edge(edgeId.toLong())

        fun <G> copy(graph: G): ImmutableGraph where G : IndexedVertexGraph, G : IndexedEdgeGraph {
            val edgeValues = EdgeValueArray(graph.edges.size) { index ->
                val edge = graph.edges[index]
                EdgeValue(graph.directed, graph.edgeSource(edge), graph.edgeTarget(edge))
            }
            return ImmutableAdjacencyListNetwork(
                graph.directed,
                graph.multiEdge,
                AdjacencySet.createSuccessors(graph),
                null,
                edgeValues)
        }
    }
}
