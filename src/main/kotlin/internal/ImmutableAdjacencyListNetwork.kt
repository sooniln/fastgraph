@file:JvmMultifileClass

package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.Int2AnyMap
import io.github.sooniln.fastcollect.ints.IntArrayList
import io.github.sooniln.fastcollect.ints.IntList
import io.github.sooniln.fastcollect.ints.MutableIntList
import io.github.sooniln.fastcollect.ints.emptyIntIterator
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastcollect.longs.LongArrayList
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractImmutableGraph
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.GraphMutator
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.ImmutableGraphBuilder
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.internal.AdjacencyListNetwork.Companion.INVALID_EDGE
import io.github.sooniln.fastgraph.nothingEdgeProperty
import io.github.sooniln.fastgraph.nothingVertexProperty
import io.github.sooniln.fastgraph.properties.createImmutableGraphEdgeProperty
import io.github.sooniln.fastgraph.properties.createImmutableGraphVertexProperty
import io.github.sooniln.fastgraph.references.ImmutableEdgeReference
import io.github.sooniln.fastgraph.references.ImmutableVertexReference
import io.github.sooniln.fastgraph.vertexSetOf
import kotlin.math.max

internal class ImmutableAdjacencyListNetwork private constructor(
    directed: Boolean,
    override val multiEdge: Boolean,
    private val successors: Array<AdjacencySet>,
    _predecessors: Array<AdjacencySet>?,
    private val edgeValues: EdgeValueArray,
) : AbstractImmutableGraph(directed), IndexedVertexGraph, IndexedEdgeGraph {

    private val predecessors: Array<AdjacencySet> by lazy {
        check(directed)

        if (_predecessors != null) {
            return@lazy _predecessors
        }

        if (successors.isEmpty()) {
            return@lazy emptyArray()
        }

        val pds = Array(successors.size) { Int2AnyHashMap<IntArrayList>() }
        for (vertexIntValue in successors.indices) {
            val vertex = Vertex(vertexIntValue)
            for (edgeAdjacency in successors[vertex.intValue]) {
                pds[edgeAdjacency.vertex.intValue].getOrPut(vertex.intValue) { IntArrayList() }
                    .add(edgeAdjacency.edgeId)
            }
        }
        return@lazy Successors(pds)
    }

    init {
        if (_predecessors != null) {
            // touch lazy property to force initialization
            predecessors
        }
    }

    override fun validateVertex(vertex: Vertex): Vertex {
        require(vertex.intValue in successors.indices) { "$vertex not found in graph" }
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        require(edge.lowBits in edgeValues.indices) { "$edge not found in graph" }
        return edge
    }

    override val vertices: IndexedVertexSet = object : AbstractVertexIndexedVertexSet() {
        override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].vertices
    override fun predecessors(vertex: Vertex): VertexSet = predecessors[vertex].vertices
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(true, vertex, successors[vertex])
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(false, vertex, predecessors[vertex])

    override val edges: IndexedEdgeSet = object : AbstractEdgeIndexedEdgeSet() {
        override val size: Int get() = edgeValues.size
        override fun get(index: Int): Edge = canonicalEdge(directed, edgeValues[index], index)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = edgeValues[validateEdge(edge).lowBits].source

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = Vertex(validateEdge(edge).highBits)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return successors[source].contains(target)
    }

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        val adjacenciesIt = successors[source].edgesTo(target).iterator()
        if (!adjacenciesIt.hasNext()) throw NoSuchElementException()
        return adjacenciesIt.next().edge
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, source, successors[source].edgesTo(target))
    }

    override fun <T, C : T> createVertexProperty(
        clazz: Class<C>,
        initializer: VertexInitializer<T>
    ): MutableVertexProperty<T> {
        return createImmutableGraphVertexProperty(this, initializer)
    }

    override fun <T, C : T> createEdgeProperty(
        clazz: Class<C>,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> {
        return createImmutableGraphEdgeProperty(this, initializer)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference =
        ImmutableVertexReference(validateVertex(vertex))

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference = ImmutableEdgeReference(validateEdge(edge))

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val adjacencies: EdgeAdjacencySet,
    ) : AbstractEdgeSet() {
        override val size: Int
            get() = adjacencies.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
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
                adjacencies.contains(EdgeAdjacency(source, element.lowBits))
            } else {
                vertex == source && adjacencies.contains(EdgeAdjacency(target, element.lowBits))
            }
        }

        override fun iterator(): EdgeIterator = adjacencies.iterator().toEdgeIterator()

        override fun foreach(action: EdgeConsumer) =
            adjacencies.foreach { edgeAdjacency -> action.accept(edgeAdjacency.edge) }
    }

    private class AdjacencySet(map: Int2AnyMap<IntList>) : EdgeAdjacencySet {

        constructor(graph: IndexedEdgeGraph, vertex: Vertex) : this(convertToMap(graph, vertex))

        // creates an array with the following format:
        // [0] = total number of edges represented
        // [1] = (#V) total number of distinct target vertices represented by edges
        // [2 -> (#V + 2)] = a list of target vertices in sorted order. for the given target vertex, if there is only one edge
        // for this target vertex, the edge id for that edge. if there is more than one edge for this target vertex, this
        // contains a negative index (-#VE) to deeper in the array where a list of edge ids for this target vertex can be found.
        // [#VE] = the number of edge ids (#E) listed in the next indices
        // [#VE + 1 -> #VE + 1 + #E] = a list of edge ids in sorted order
        private val arr: IntArray

        init {
            if (map.isEmpty()) {
                arr = intArrayOf(0, 0)
            } else {
                val numVertices = map.size
                val numEdges = map.values.sumOf { it.size }
                val numVerticesMoreThanOneEdge = map.values.sumOf { if (it.size == 1) 0 else 1 }
                val numEdgesMoreThanOne = map.values.sumOf { if (it.size == 1) 0 else it.size }

                arr = IntArray(2 + 2 * numVertices + numVerticesMoreThanOneEdge + numEdgesMoreThanOne)
                arr[0] = numEdges
                arr[1] = numVertices

                var i = 2
                map.foreachKey { key -> arr[i++] = key }
                arr.sort(2, i)

                var edgeIdsStart = i + numVertices
                for (vIdx in 2..<i) {
                    val vertex = arr[vIdx]
                    val list = map.getValue(vertex)
                    if (list.size == 1) {
                        arr[i++] = list[0]
                    } else {
                        arr[i++] = -edgeIdsStart
                        val edges = list.toIntArray().apply { sort() }
                        arr[edgeIdsStart++] = edges.size
                        edges.copyInto(arr, edgeIdsStart)
                        edgeIdsStart += edges.size
                    }
                }
            }
        }

        private val numEdges: Int inline get() = arr[0]
        private val numVertices: Int inline get() = arr[1]

        override val size: Int get() = numEdges

        override val vertices: VertexSet
            get() = object : AbstractVertexSet() {
                override val size: Int get() = numVertices

                @Suppress("INAPPLICABLE_JVM_NAME")
                @JvmName("contains")
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
            }

        override fun contains(element: EdgeAdjacency): Boolean {
            val vertexIdx = findVertex(element.vertex)
            if (vertexIdx < 0) {
                return false
            }

            val vertexData = arr[numVertices + vertexIdx]
            if (vertexData >= 0) {
                return element.edgeId == vertexData
            } else {
                val start = edgeIdsStart(vertexData)
                return arr.binarySearch(element.edgeId, start, start + edgeIdsSize(vertexData)) >= 0
            }
        }

        override fun contains(vertex: Vertex): Boolean = findVertex(vertex) >= 0

        override operator fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
            private val vertexIndexIt = (1..(1 + numVertices)).iterator()
            private var edgeIt = emptyIntIterator()

            private var vertex: Vertex = INVALID_VERTEX
            private var edge: Edge = INVALID_EDGE

            private var vertexIndex = 1
            private val vertexEnd = 2 + numVertices
            private var edgeIndex = -1
            private var edgeEnd = -1

            init {
                increment()
            }

            override fun hasNext(): Boolean = vertex != INVALID_VERTEX
            override fun next(): EdgeAdjacency {
                if (vertex == INVALID_VERTEX) throw NoSuchElementException()
                val edgeAdjacency = EdgeAdjacency(vertex, edge)
                increment()
                return edgeAdjacency
            }

            private fun increment() {
                while (!edgeIt.hasNext() && vertexIndexIt.hasNext()) {
                    val vertexIndex = vertexIndexIt.next()
                    vertex = Vertex(arr[vertexIndex])

                    val vertexDataIdx = vertexDataIdx(vertexIndex)
                    val vertexData = arr[vertexDataIdx]
                    if (vertexData >= 0) {
                        edgeIndex = vertexDataIdx
                        edgeEnd = edgeIndex + 1
                    } else {
                        edgeIndex = edgeIdsStart(vertexData)
                        edgeEnd = edgeIndex + edgeIdsSize(vertexData)
                    }
                }

                edge = if (edgeIt.hasNext()) Edge(edgeIt.next().toLong()) else INVALID_EDGE
            }

            override fun hasNext(): Boolean = vertexIndex < vertexEnd
            override fun next(): EdgeAdjacency {
                if (vertexIndex >= vertexEnd) throw NoSuchElementException()
                val ea = EdgeAdjacency(vertex, arr[edgeIndex])
                increment()
                return ea
            }

            private fun increment() {
                if (++edgeIndex >= edgeEnd && ++vertexIndex < vertexEnd) {
                    vertex = Vertex(arr[vertexIndex])
                    val vertexDataIdx = vertexDataIdx(vertexIndex)
                    val vertexData = arr[vertexDataIdx]
                    if (vertexData >= 0) {
                        edgeIndex = vertexDataIdx
                        edgeEnd = edgeIndex + 1
                    } else {
                        edgeIndex = edgeIdsStart(vertexData)
                        edgeEnd = edgeIndex + edgeIdsSize(vertexData)
                    }
                }
            }
        }

        private fun findVertex(vertex: Vertex) = arr.binarySearch(vertex.intValue, 2, 2 + arr[1])

        private fun vertexDataIdx(vertexIdx: Int) = vertexIdx + numVertices
        private fun edgeIdsStart(vertexData: Int): Int {
            assert(vertexData < 0)
            return -vertexData + 1
        }

        private fun edgeIdsSize(vertexData: Int): Int {
            assert(vertexData < 0)
            return arr[-vertexData]
        }

        fun firstAdjacency(vertex: Vertex): EdgeAdjacency {
            val vertexIdx = findVertex(vertex)
            return if (vertexIdx < 0) {
                throw NoSuchElementException()
            } else {
                val vertexDataIdx = vertexDataIdx(vertexIdx)
                val vertexData = arr[vertexDataIdx]
                if (vertexData >= 0) {
                    EdgeAdjacency(vertex, vertexData)
                } else {
                    EdgeAdjacency(vertex, arr[edgeIdsStart(vertexData)])
                }
            }
        }

        fun subset(subsetVertex: Vertex): EdgeAdjacencySet = object : EdgeAdjacencySet {
            private val edgeIdsStart: Int
            private val edgeIdsEnd: Int

            init {
                val vertexIdx = findVertex(subsetVertex)
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

            override fun isEmpty(): Boolean = size == 0

            override fun contains(element: EdgeAdjacency): Boolean {
                return element.vertex == subsetVertex && arr.binarySearch(element.edgeId, edgeIdsStart, edgeIdsEnd) >= 0
            }

            override fun containsAll(elements: Collection<EdgeAdjacency>): Boolean =
                throw UnsupportedOperationException()

            override fun contains(vertex: Vertex): Boolean = vertex == subsetVertex

            override fun vertices(): VertexSet = vertexSetOf(subsetVertex)

            override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
                private var i = edgeIdsStart

                override fun hasNext(): Boolean = i < edgeIdsEnd
                override fun next(): EdgeAdjacency {
                    if (i >= edgeIdsEnd) throw NoSuchElementException()
                    return EdgeAdjacency(subsetVertex, arr[i++])
                }
            }

            override fun edgeIdIterator(): IntIterator = throw UnsupportedOperationException()

            override fun toString(): String = joinToString(", ", "[", "]")
        }

        override fun toString(): String = joinToString(", ", "[", "]")

        private companion object {
            private fun convertToMap(graph: IndexedEdgeGraph, vertex: Vertex): Int2AnyMap<IntList> {
                context(graph) {
                    val edges = vertex.outgoingEdges()
                    val map = Int2AnyHashMap<MutableIntList>(edges.size)
                    edges.foreach { edge ->
                        map.getOrPut(edge.opposite(vertex).intValue) { IntArrayList(1) }.add(edge.lowBits)
                    }
                    return map
                }
            }
        }
    }

    private operator fun Array<AdjacencySet>.get(vertex: Vertex) = get(vertex.intValue)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun canonicalEdge(directed: Boolean, edgeValue: EdgeValue, edgeId: Int): Edge {
        return canonicalEdge(directed, edgeValue.source, edgeValue.target, edgeId)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun canonicalEdge(directed: Boolean, source: Vertex, target: Vertex, edgeId: Int): Edge {
        return if (!directed) {
            Edge(highBits = max(source.intValue, target.intValue), lowBits = edgeId)
        } else {
            Edge(highBits = target.intValue, lowBits = edgeId)
        }
    }

    companion object {
        fun <G> copy(graph: G): ImmutableAdjacencyListNetwork where G : IndexedVertexGraph, G : IndexedEdgeGraph {
            return ImmutableAdjacencyListNetwork(
                graph.directed,
                graph.multiEdge,
                Array(graph.vertices.size) { v -> AdjacencySet(graph, Vertex(v)) },
                null,
                EdgeValueArray(LongArray(graph.edges.size) { e ->
                    EdgeValue(
                        graph.directed,
                        graph.edgeSource(graph.edges[e]),
                        graph.edgeTarget(graph.edges[e])
                    ).longValue
                })
            )
        }
    }
}

// TODO: use graph internally
internal class ImmutableAdjacencyListNetworkBuilder<V, E> internal constructor(
    private val directed: Boolean,
    private val supportMultiEdge: Boolean,
) : ImmutableGraphBuilder<V, E>(), GraphMutator<V, E> {

    private var multiEdge = false
    private val successors = ArrayList<GraphInt2AnyHashMap<IntArrayList>>()
    private var edgeValues = LongArrayList()

    private val vertexMap = Object2IntOpenHashMap<V>()

    private var vertexPropertyClass: Class<V>? = null
    private var vertexPropertyInitializer: VertexInitializer<V>? = null
    private var vertexProperty: ArrayList<V>? = null

    private var edgePropertyClass: Class<E>? = null
    private var edgePropertyInitializer: EdgeInitializer<E>? = null
    private var edgeProperty: GraphLong2AnyHashMap<E>? = null

    override fun withVertexProperty(clazz: Class<V>, initializer: VertexInitializer<V>): ImmutableGraphBuilder<V, E> {
        check(successors.isEmpty())
        vertexPropertyClass = clazz
        vertexPropertyInitializer = initializer
        vertexProperty = ArrayList()
        return this
    }

    override fun withEdgeProperty(clazz: Class<E>, initializer: EdgeInitializer<E>): ImmutableGraphBuilder<V, E> {
        check(successors.isEmpty())
        edgePropertyClass = clazz
        edgePropertyInitializer = initializer
        edgeProperty = GraphLong2AnyHashMap()
        return this
    }

    private fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.intValue !in successors.indices) throw IllegalArgumentException("$vertex not found in graph builder")
        return vertex
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(): Vertex = addVertexInternal(false) { vertexPropertyInitializer!!.initialize(it) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(value: V): Vertex = addVertexInternal(true) { value }

    private inline fun addVertexInternal(mapValue: Boolean, valueRetriever: (Vertex) -> V): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(GraphInt2AnyHashMap())
        if (mapValue || vertexProperty != null) {
            val value = valueRetriever(vertex)
            vertexProperty?.add(value)
            if (mapValue) {
                vertexMap[value] = vertex.intValue
            }
        }
        return vertex
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex): Edge =
        addEdgeInternal(source, target) { edgePropertyInitializer!!.initialize(it) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex, value: E): Edge =
        addEdgeInternal(source, target) { value }

    private inline fun addEdgeInternal(source: Vertex, target: Vertex, valueRetriever: (Edge) -> E): Edge {
        val edgeId = edgeValues.size
        val edgeValue = EdgeValue(directed, validateVertex(source), validateVertex(target))

        val adjacencySet = successors[source.intValue]
        val containsTarget = adjacencySet.containsKey(target.intValue)
        if (!supportMultiEdge) {
            require(!containsTarget) { "$source -> $target already exists in graph" }
        }

        edgeValues.add(edgeValue.longValue)
        adjacencySet.getOrPut(target.intValue) { IntArrayList() }.add(edgeId)
        if (containsTarget) {
            multiEdge = true
        }

        if (!directed && source != target) {
            successors[target.intValue].getOrPut(source.intValue) { IntArrayList() }.add(edgeId)
        }

        val edge = canonicalEdge(directed, edgeValue, edgeId)
        edgeProperty?.set(edge.longValue, valueRetriever(edge))
        return edge
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(sourceValue: V, targetValue: V): Edge {
        val source = getOrCreateVertex(sourceValue)
        val target = getOrCreateVertex(targetValue)
        return addEdge(source, target)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(sourceValue: V, targetValue: V, value: E): Edge {
        val source = getOrCreateVertex(sourceValue)
        val target = getOrCreateVertex(targetValue)
        return addEdge(source, target, value)
    }

    private fun getOrCreateVertex(key: V): Vertex {
        return if (!vertexMap.containsKey(key)) {
            addVertex(key)
        } else {
            Vertex(vertexMap.getInt(key))
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("hasVertex")
    override fun hasVertex(value: V): Boolean {
        return vertexMap.containsKey(value)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getVertex")
    override fun getVertex(value: V): Vertex {
        if (!vertexMap.containsKey(value)) {
            throw IllegalArgumentException("no vertex with value $value")
        }

        return Vertex(vertexMap.getInt(value))
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) {
        successors.ensureCapacity(vertexCapacity)
        vertexProperty?.ensureCapacity(vertexCapacity)
        vertexMap.ensureCapacity(vertexCapacity)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) {
        edgeProperty?.ensureCapacity(edgeCapacity)
        edgeValues.ensureCapacity(edgeCapacity)
    }

    override fun mutate(): GraphMutator<V, E> = this

    override fun build(): ImmutableGraph {
        return ImmutableAdjacencyListNetwork(
            directed,
            Successors(successors),
            null,
            multiEdge,
            EdgeValueArray(edgeValues.toLongArray())
        )
    }

    override fun buildPropertyGraph(): PropertyGraph<ImmutableGraph, V, E> {
        val graph = build()
        val vertexProperty = if (vertexProperty != null) {
            // we know the vertex property will not retain a reference to the initializer, so we can use it to
            // initialize the property
            graph.createVertexProperty(vertexPropertyClass!!) { vertexProperty!![it.intValue] }
        } else {
            nothingVertexProperty(graph)
        }
        val edgeProperty = if (edgeProperty != null) {
            // we know the edge property will not retain a reference to the initializer, so we can use it to
            // initialize the property
            graph.createEdgeProperty(edgePropertyClass!!) { edgeProperty!![it.longValue] }
        } else {
            nothingEdgeProperty(graph)
        }
        return PropertyGraph(graph, vertexProperty, edgeProperty)
    }
}
