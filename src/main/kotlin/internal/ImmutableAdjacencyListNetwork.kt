package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.IntArrayList
import io.github.sooniln.fastcollect.ints.emptyIntIterator
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastcollect.ints.intIteratorOf
import io.github.sooniln.fastgraph.*

internal class ImmutableAdjacencyListNetwork private constructor(
    override val directed: Boolean,
    override val multiEdge: Boolean,
    private val successors: Array<AdjacencySet>,
    _predecessors: Array<AdjacencySet>?,
    private val edgeValues: EdgeValueArray,
) : AbstractGraph(), IndexedVertexGraph, IndexedEdgeGraph, InternalImmutableGraph {

    private val predecessors: Array<AdjacencySet> by lazy {
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
        override fun toEdgeId(edge: Edge): Int = edge.edgeId
        override fun toEdge(edgeId: Int): Edge = canonicalEdge(edgeId)
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
        val adjacenciesIt = successors[source].edgesTo(target).iterator()
        if (!adjacenciesIt.hasNext()) throw NoSuchElementException()
        return canonicalEdge(adjacenciesIt.next().edgeId)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, source, successors[source].edgesTo(target))
    }

    override fun <T> createVertexProperty(
        type: Class<T>,
        initializer: VertexInitializer<T>
    ): MutableVertexProperty<T> {
        return VertexProperties.createVertexProperty(this, type, initializer)
    }

    override fun <T> createEdgeProperty(
        type: Class<T>,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> {
        return EdgeProperties.createEdgeProperty(this, type, initializer)
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

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(it.next().edgeId)
        }

        override fun foreach(action: EdgeConsumer) =
            adjacencies.foreach { edgeAdjacency -> action.accept(canonicalEdge(edgeAdjacency.edgeId)) }
    }

    private class AdjacencySet private constructor(private val arr: IntArray) : EdgeAdjacencySet {

        // holds an array with the following format:
        // [0] = total number of edges represented
        // [1] = (#V) total number of distinct target vertices represented by edges
        // [2 -> (#V + 1)] = a list of target vertices in sorted order.
        // [#V + 2 -> #V + #V + 1] = for the given target vertex (i - #V), if there is only one edge for this target
        // vertex, the edge id for that edge. if there is more than one edge for this target vertex, this contains a
        // negative index (-#VE) to deeper in the array where a list of edge ids for this target vertex can be found.
        // [#VE] = the number of edge ids (#E) listed in the next indices
        // [#VE + 1 -> #VE + 1 + #E] = a list of edge ids in sorted order

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

            val vertexData = arr[numVertices + vertexIndex]
            if (vertexData >= 0) {
                return element.edgeId == vertexData
            } else {
                val start = edgeIdsStart(vertexData)
                return arr.binarySearch(element.edgeId, start, start + edgeIdsSize(vertexData)) >= 0
            }
        }

        override fun contains(vertex: Vertex): Boolean = findVertex(vertex) >= 0

        override operator fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
            private val vertexIndexIt: IntIterator = (2..<2 + numVertices).iterator()
            private var edgeIdIndexIt: IntIterator = emptyIntIterator()

            private var vertex: Vertex = INVALID_VERTEX
            private var edgeId: Int = -1

            init {
                increment()
            }

            override fun hasNext(): Boolean = edgeId != -1
            override fun next(): EdgeAdjacency {
                if (edgeId == -1) throw NoSuchElementException()
                val edgeAdjacency = EdgeAdjacency(vertex, edgeId)
                increment()
                return edgeAdjacency
            }

            private fun increment() {
                while (!edgeIdIndexIt.hasNext() && vertexIndexIt.hasNext()) {
                    val vertexIndex = vertexIndexIt.next()
                    vertex = Vertex(arr[vertexIndex])

                    val vertexDataIdx = vertexDataIdx(vertexIndex)
                    val vertexData = arr[vertexDataIdx]
                    if (vertexData >= 0) {
                        edgeIdIndexIt = intIteratorOf(vertexDataIdx)
                    } else {
                        val startIndex = edgeIdsStart(vertexData)
                        edgeIdIndexIt = (startIndex..<startIndex + edgeIdsSize(vertexData)).iterator()
                    }
                }

                edgeId = if (!edgeIdIndexIt.hasNext()) -1 else arr[edgeIdIndexIt.nextInt()]
            }
        }

        override fun foreach(action: EdgeAdjacencyConsumer) {
            for (vertexIndex in 2..<numVertices + 2) {
                val vertex = Vertex(arr[vertexIndex])
                val vertexData = arr[vertexDataIdx(vertexIndex)]
                if (vertexData >= 0) {
                    action.accept(EdgeAdjacency(vertex, vertexData))
                } else {
                    val start = edgeIdsStart(vertexData)
                    for (edgeIdIndex in start..<start + edgeIdsSize(vertexData)) {
                        action.accept(EdgeAdjacency(vertex, arr[edgeIdIndex]))
                    }
                }
            }
        }

        private fun findVertex(vertex: Vertex) = arr.binarySearch(vertex.id, 2, 2 + arr[1])

        private fun vertexDataIdx(vertexIdx: Int) = vertexIdx + numVertices
        private fun edgeIdsStart(vertexData: Int): Int {
            assert(vertexData < 0)
            return -vertexData + 1
        }

        private fun edgeIdsSize(vertexData: Int): Int {
            assert(vertexData < 0)
            return arr[-vertexData]
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
                    val vertexDataIdx = vertexDataIdx(vertexIdx)
                    val vertexData = arr[vertexDataIdx]
                    if (vertexData >= 0) {
                        edgeIdsStart = vertexDataIdx
                        edgeIdsEnd = edgeIdsStart + 1
                    } else {
                        edgeIdsStart = edgeIdsStart(vertexData)
                        edgeIdsEnd = edgeIdsStart + edgeIdsSize(vertexData)
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

            override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
                private var i = edgeIdsStart

                override fun hasNext(): Boolean = i < edgeIdsEnd
                override fun next(): EdgeAdjacency {
                    if (i >= edgeIdsEnd) throw NoSuchElementException()
                    return EdgeAdjacency(target, arr[i++])
                }
            }

            override fun foreach(action: EdgeAdjacencyConsumer) {
                for (i in edgeIdsStart..<edgeIdsEnd) {
                    action.accept(EdgeAdjacency(target, arr[i]))
                }
            }

            override fun toString(): String = Iterable { iterator() }.joinToString(", ", "[", "]")
        }

        override fun toString(): String = Iterable { iterator() }.joinToString(", ", "[", "]")

        companion object {
            fun <G> createSuccessors(graph: G): Array<AdjacencySet> where G : IndexedVertexGraph, G : IndexedEdgeGraph {
                return Array(graph.vertices.size) { index ->
                    context(graph) {
                        val vertex = Vertex(index)

                        val successors = vertex.successors()
                        val numVertices = successors.size
                        val numEdges = vertex.outgoingEdges().size
                        var multiEdgeVertices = 0
                        var multiEdgeTotal = 0
                        successors.foreach { successor ->
                            val numEdges = graph.edges(vertex, successor).size
                            if (numEdges > 1) {
                                ++multiEdgeVertices
                                multiEdgeTotal += numEdges
                            }
                        }

                        val arr = IntArray(2 + 2 * numVertices + multiEdgeVertices + multiEdgeTotal)
                        arr[0] = numEdges
                        arr[1] = numVertices

                        var i = 2
                        successors.foreach { successor -> arr[i++] = successor.id }
                        arr.sort(2, i)

                        var edgeIdsStart = i + numVertices
                        for (vIdx in 2..<i) {
                            val successor = Vertex(arr[vIdx])
                            val edges = vertex.edgesTo(successor)
                            if (edges.size == 1) {
                                arr[i++] = edges.iterator().next().id.toInt()
                            } else {
                                arr[i++] = -edgeIdsStart
                                arr[edgeIdsStart++] = edges.size
                                val edgesStart = edgeIdsStart
                                edges.foreach { edge ->
                                    arr[edgeIdsStart++] = edge.id.toInt()
                                }
                                arr.sort(edgesStart, edgeIdsStart)
                            }
                        }

                        return@context AdjacencySet(arr)
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
                        var multiEdgeVertices = 0
                        var multiEdgeTotal = 0
                        predecessors.foreach { predecessor ->
                            val numEdges = graph.edges(predecessor, vertex).size
                            if (numEdges > 1) {
                                ++multiEdgeVertices
                                multiEdgeTotal += numEdges
                            }
                        }

                        val arr = IntArray(2 + 2 * numVertices + multiEdgeVertices + multiEdgeTotal)
                        arr[0] = numEdges
                        arr[1] = numVertices

                        var i = 2
                        predecessors.foreach { predecessor -> arr[i++] = predecessor.id }
                        arr.sort(2, i)

                        var edgeIdsStart = i + numVertices
                        for (vIdx in 2..<i) {
                            val predecessor = Vertex(arr[vIdx])
                            val edges = predecessor.edgesTo(vertex)
                            if (edges.size == 1) {
                                arr[i++] = edges.iterator().next().lowBits
                            } else {
                                arr[i++] = -edgeIdsStart
                                arr[edgeIdsStart++] = edges.size
                                val edgesStart = edgeIdsStart
                                edges.foreach { edge ->
                                    arr[edgeIdsStart++] = graph.edges.indexOf(edge)
                                }
                                arr.sort(edgesStart, edgeIdsStart)
                            }
                        }

                        return@context AdjacencySet(arr)
                    }
                }
            }

            fun createPredecessors(successors: Array<AdjacencySet>): Array<AdjacencySet> {
                if (successors.isEmpty()) return emptyArray()

                val pds = Array(successors.size) { Int2AnyHashMap<IntArrayList>() }
                for (vertexIndex in successors.indices) {
                    for (edgeAdjacency in successors[vertexIndex]) {
                        pds[edgeAdjacency.vertex.id].getOrPut(vertexIndex) { IntArrayList() }
                            .add(edgeAdjacency.edgeId)
                    }
                }

                return Array(successors.size) { vertexIndex ->
                    val predecessors = pds[vertexIndex]
                    val numVertices = predecessors.keys.size
                    val numEdges = predecessors.values.sumOf { it.size }
                    var multiEdgeVertices = 0
                    var multiEdgeTotal = 0
                    predecessors.foreach { _, edgeIds ->
                        val numEdges = edgeIds.size
                        if (numEdges > 1) {
                            ++multiEdgeVertices
                            multiEdgeTotal += numEdges
                        }
                    }

                    val arr = IntArray(2 + 2 * numVertices + multiEdgeVertices + multiEdgeTotal)
                    arr[0] = numEdges
                    arr[1] = numVertices

                    var i = 2
                    predecessors.foreachKey { predecessor -> arr[i++] = predecessor }
                    arr.sort(2, i)

                    var edgeIdsStart = i + numVertices
                    for (vIdx in 2..<i) {
                        val predecessor = arr[vIdx]
                        val edges = predecessors.getValue(predecessor)
                        if (edges.size == 1) {
                            arr[i++] = edges[0]
                        } else {
                            arr[i++] = -edgeIdsStart
                            arr[edgeIdsStart++] = edges.size
                            val edgesStart = edgeIdsStart
                            edges.foreach { edge ->
                                arr[edgeIdsStart++] = edge
                            }
                            arr.sort(edgesStart, edgeIdsStart)
                        }
                    }

                    return@Array AdjacencySet(arr)
                }
            }
        }
    }

    private operator fun Array<AdjacencySet>.get(vertex: Vertex) = get(vertex.id)

    private fun canonicalEdge(edgeId: Int): Edge = Edge(edgeId.toLong())
    private val Edge.edgeId: Int inline get() = lowBits

    companion object {
        private val INVALID_VERTEX = Vertex(-1)

        fun <G> copy(graph: G): ImmutableGraph where G : IndexedVertexGraph, G : IndexedEdgeGraph {
            val edgeValues = context(graph) { EdgeValueArray(graph.edges.size) { index -> EdgeValue(graph.directed, graph.edges[index].source, graph.edges[index].target) } }
            return ImmutableAdjacencyListNetwork(graph.directed, graph.multiEdge, AdjacencySet.createSuccessors(graph), null, edgeValues)
        }
    }
}
