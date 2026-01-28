@file:JvmMultifileClass

package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractEdgeSetList
import io.github.sooniln.fastgraph.AbstractImmutableGraph
import io.github.sooniln.fastgraph.AbstractVertexCollection
import io.github.sooniln.fastgraph.AbstractVertexSetList
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIndexedEdgeGraph
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.EdgeSetList
import io.github.sooniln.fastgraph.GraphMutator
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.ImmutableGraphBuilder
import io.github.sooniln.fastgraph.PropertyGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.VertexSetList
import io.github.sooniln.fastgraph.nothingEdgeProperty
import io.github.sooniln.fastgraph.nothingVertexProperty
import io.github.sooniln.fastgraph.vertexSetOf
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntIterator
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlin.math.max

internal class ImmutableAdjacencyListNetwork(
    override val directed: Boolean,
    private val successors: Successors,
    _predecessors: Successors?,
    override val multiEdge: Boolean,
    private val edgeValues: EdgeValueArray,
) : AbstractImmutableGraph(), VertexIndexedVertexGraph, EdgeIndexedEdgeGraph {

    private val predecessors: Successors by lazy {
        check(directed)

        if (_predecessors != null) {
            return@lazy _predecessors
        }

        if (successors.isEmpty()) {
            return@lazy Successors(emptyArray())
        }

        val pds = Array(successors.size) { Int2ObjectOpenHashMap<IntArrayList>() }
        for (vertexIntValue in 0..<successors.size) {
            val vertex = Vertex(vertexIntValue)
            for (edgeAdjacency in successors[vertex.intValue]) {
                pds[edgeAdjacency.vertex.intValue].computeIfAbsent(vertex.intValue) { IntArrayList() }
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

    private fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.intValue !in 0..<successors.size) {
            throw IllegalArgumentException("$vertex not found in graph")
        }
        return vertex
    }

    private fun validateEdge(edge: Edge): Edge {
        if (edge.lowBits !in 0..<edgeValues.size) {
            throw IllegalArgumentException("$edge not found in graph")
        }
        return edge
    }

    override val vertices: VertexSetList = object : AbstractVertexSetList() {
        override val size: Int get() = successors.size
        override fun get(index: Int): Vertex = Vertex(index)

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Vertex): Boolean {
            validateVertex(element)
            return true
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("indexOf")
        override fun indexOf(element: Vertex): Int = validateVertex(element).intValue

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("lastIndexOf")
        override fun lastIndexOf(element: Vertex): Int = validateVertex(element).intValue
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int = successors[validateVertex(vertex).intValue].size

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int {
        return if (!directed) {
            outDegree(vertex)
        } else {
            predecessors[validateVertex(vertex).intValue].size
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSet = successors[validateVertex(vertex).intValue].vertices()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet {
        return if (!directed) {
            successors(vertex)
        } else {
            predecessors[validateVertex(vertex).intValue].vertices()
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet {
        return IncidentEdgeSet(true, validateVertex(vertex), successors[vertex.intValue])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet {
        return if (!directed) {
            outgoingEdges(vertex)
        } else {
            IncidentEdgeSet(false, validateVertex(vertex), predecessors[vertex.intValue])
        }
    }

    override val edges: EdgeSetList = object : EdgeSetList, AbstractEdgeSetList() {
        override val size: Int get() = edgeValues.size
        override fun get(index: Int): Edge = canonicalEdge(directed, edgeValues[index], index)

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            return containsEdge(edgeSource(element), edgeTarget(element))
        }

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("indexOf")
        override fun indexOf(element: Edge): Int = validateEdge(element).lowBits

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("lastIndexOf")
        override fun lastIndexOf(element: Edge): Int = validateEdge(element).lowBits
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
        return successors[validateVertex(source).intValue].contains(validateVertex(target))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    override fun getEdge(source: Vertex, target: Vertex): Edge {
        val adjacency = successors[validateVertex(source).intValue].firstAdjacency(validateVertex(target))
        return canonicalEdge(directed, source, adjacency.vertex, adjacency.edgeId)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, validateVertex(source), successors[source.intValue].subset(validateVertex(target)))
    }

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        initializer: VertexInitializer<T>
    ): VertexProperty<T> {
        return immutableArrayVertexProperty(this, clazz, initializer)
    }

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: EdgeInitializer<T>): EdgeProperty<T> {
        return immutableArrayEdgeProperty(this, clazz, initializer)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference = VertexReferenceImpl(validateVertex(vertex))

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference = EdgeReferenceImpl(validateEdge(edge))

    // TODO: consolidate IncidentEdgeSet classes
    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val adjacencies: EdgeAdjacencySet,
    ) : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = adjacencies.size

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

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                val edgeAdjacency = it.next()
                return if (outgoing) {
                    canonicalEdge(directed, vertex, edgeAdjacency.vertex, edgeAdjacency.edgeId)
                } else {
                    canonicalEdge(directed, edgeAdjacency.vertex, vertex, edgeAdjacency.edgeId)
                }
            }
        }
    }
}

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

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class Successors private constructor(private val arr: Array<IntArray>) : List<SuccessorArray> {

    constructor(successors: Array<out Int2ObjectMap<out IntList>>) : this(Array(successors.size) {
        toSuccessorArray(
            successors[it]
        )
    })

    constructor(successors: List<Int2ObjectMap<out IntList>>) : this(Array(successors.size) {
        toSuccessorArray(
            successors[it]
        )
    })

    override val size: Int inline get() = arr.size
    override inline fun isEmpty(): Boolean = arr.isEmpty()
    override inline fun contains(element: SuccessorArray): Boolean = throw UnsupportedOperationException()
    override inline fun iterator(): Iterator<SuccessorArray> = throw UnsupportedOperationException()
    override inline fun containsAll(elements: Collection<SuccessorArray>): Boolean =
        throw UnsupportedOperationException()

    override inline fun get(index: Int): SuccessorArray = SuccessorArray(arr[index])
    override fun indexOf(element: SuccessorArray): Int = throw UnsupportedOperationException()
    override fun lastIndexOf(element: SuccessorArray): Int = throw UnsupportedOperationException()
    override fun listIterator(): ListIterator<SuccessorArray> = throw UnsupportedOperationException()
    override fun listIterator(index: Int): ListIterator<SuccessorArray> = throw UnsupportedOperationException()
    override fun subList(fromIndex: Int, toIndex: Int): List<SuccessorArray> = throw UnsupportedOperationException()
}

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
@JvmInline
internal value class SuccessorArray(private val arr: IntArray) : EdgeAdjacencySet {
    override val size: Int
        inline get() = if (arr.isEmpty()) 0 else arr[0]

    override fun isEmpty(): Boolean = size == 0

    private val numVertices: Int
        inline get() = if (arr.isEmpty()) 0 else arr[1]

    private inline fun findVertex(vertex: Vertex): Int {
        return if (arr.isEmpty()) -1 else arr.binarySearch(vertex.intValue, 2, 2 + numVertices)
    }

    private inline fun vertexDataIdx(vertexIdx: Int) = vertexIdx + numVertices
    private inline fun edgeIdsStart(vertexData: Int): Int {
        assert(vertexData < 0)
        return -vertexData + 1
    }

    private inline fun edgeIdsSize(vertexData: Int): Int {
        assert(vertexData < 0)
        return arr[-vertexData]
    }

    override fun contains(element: EdgeAdjacency): Boolean {
        val vertexIdx = findVertex(element.vertex)
        if (vertexIdx < 0) {
            return false
        }

        val vertexData = arr[vertexDataIdx(vertexIdx)]
        if (vertexData >= 0) {
            return element.edgeId == vertexData
        } else {
            val start = edgeIdsStart(vertexData)
            return arr.binarySearch(element.edgeId, start, start + edgeIdsSize(vertexData)) >= 0
        }
    }

    override fun containsAll(elements: Collection<EdgeAdjacency>): Boolean = throw UnsupportedOperationException()

    override inline fun contains(vertex: Vertex): Boolean = findVertex(vertex) >= 0

    override fun vertices(): VertexSet = object : VertexSet, AbstractVertexCollection() {
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

    override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
        private var vertexIdx = 1
        private val vertexEndIdx = 2 + numVertices
        private var vertexAdjacency = EdgeAdjacency(0L)
        private var edgeIdIdx = -1
        private var edgeIdsEnd = -1

        init {
            increment()
        }

        override fun hasNext(): Boolean = vertexIdx < vertexEndIdx
        override fun next(): EdgeAdjacency {
            if (vertexIdx >= vertexEndIdx) throw NoSuchElementException()
            val ea = EdgeAdjacency(vertexAdjacency, arr[edgeIdIdx])
            increment()
            return ea
        }

        private fun increment() {
            if (++edgeIdIdx >= edgeIdsEnd && ++vertexIdx < vertexEndIdx) {
                vertexAdjacency = EdgeAdjacency(Vertex(arr[vertexIdx]), 0)
                val vertexDataIdx = vertexDataIdx(vertexIdx)
                val vertexData = arr[vertexDataIdx]
                if (vertexData >= 0) {
                    edgeIdIdx = vertexDataIdx
                    edgeIdsEnd = edgeIdIdx + 1
                } else {
                    edgeIdIdx = edgeIdsStart(vertexData)
                    edgeIdsEnd = edgeIdIdx + edgeIdsSize(vertexData)
                }
            }
        }
    }

    override fun edgeIdIterator(): IntIterator = throw UnsupportedOperationException()

    fun firstAdjacency(vertex: Vertex): EdgeAdjacency {
        val vertexIdx = findVertex(vertex)
        if (vertexIdx < 0) {
            throw NoSuchElementException()
        } else {
            val vertexDataIdx = vertexDataIdx(vertexIdx)
            val vertexData = arr[vertexDataIdx]
            if (vertexData >= 0) {
                return EdgeAdjacency(vertex, vertexData)
            } else {
                return EdgeAdjacency(vertex, arr[edgeIdsStart(vertexData)])
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

        override fun containsAll(elements: Collection<EdgeAdjacency>): Boolean = throw UnsupportedOperationException()

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

        override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
    }

    override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
}

// creates an array with the following format:
// [0] = total number of edges represented
// [1] = (#V) total number of distinct target vertices represented by edges
// [2 -> (#V + 2)] = a list of target vertices in sorted order. for the given target vertex, if there is only one edge
// for this target vertex, the edge id for that edge. if there is more than one edge for this target vertex, this
// contains a negative index (-#VE) to deeper in the array where a list of edge ids for this target vertex can be found.
// [#VE] = the number of edge ids (#E) listed in the next indices
// [#VE + 1 -> #VE + 1 + #E] = a list of edge ids in sorted order
private fun toSuccessorArray(map: Int2ObjectMap<out IntList>): IntArray {
    if (map.isEmpty()) {
        return IntArray(0)
    }

    val numVertices = map.size
    val numEdges = map.values.sumOf { it.size }
    val numVerticesMoreThanOneEdge = map.values.sumOf { if (it.size == 1) 0 else 1 }
    val numEdgesMoreThanOne = map.values.sumOf { if (it.size == 1) 0 else it.size }
    val arr = IntArray(2 + 2 * numVertices + numVerticesMoreThanOneEdge + numEdgesMoreThanOne)
    arr[0] = numEdges
    arr[1] = numVertices

    var i = 2
    val keyIt = map.keys.iterator()
    while (keyIt.hasNext()) {
        arr[i++] = keyIt.nextInt()
    }
    arr.sort(2, i)

    var edgeIdsStart = i + numVertices
    for (vIdx in 2..<i) {
        val vertex = arr[vIdx]
        val list = map[vertex]
        if (list.size == 1) {
            arr[i++] = list.getInt(0)
        } else {
            arr[i++] = -edgeIdsStart
            val edges = list.toArray(IntArray(0)).apply { sort() }
            arr[edgeIdsStart++] = edges.size
            System.arraycopy(edges, 0, arr, edgeIdsStart, edges.size)
            edgeIdsStart += edges.size
        }
    }

    return arr
}

// TODO: use graph internally
internal class ImmutableAdjacencyListNetworkBuilder<V, E> internal constructor(
    private val directed: Boolean,
    private val supportMultiEdge: Boolean,
) : ImmutableGraphBuilder<V, E>(), GraphMutator<V, E> {

    private var multiEdge = false
    private val successors = ArrayList<Int2ObjectOpenHashMap<IntArrayList>>()
    private var edgeValues = LongArrayList()

    private val vertexMap = Object2IntOpenHashMap<V>()

    private var vertexPropertyClass: Class<V>? = null
    private var vertexPropertyInitializer: VertexInitializer<V>? = null
    private var vertexProperty: ArrayList<V>? = null

    private var edgePropertyClass: Class<E>? = null
    private var edgePropertyInitializer: EdgeInitializer<E>? = null
    private var edgeProperty: Long2ObjectOpenHashMap<E>? = null

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
        edgeProperty = Long2ObjectOpenHashMap()
        return this
    }

    private fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.intValue !in 0..<successors.size) throw IllegalArgumentException("$vertex not found in graph builder")
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
        successors.add(Int2ObjectOpenHashMap<IntArrayList>())
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
        adjacencySet.computeIfAbsent(target.intValue) { IntArrayList() }.add(edgeId)
        if (containsTarget) {
            multiEdge = true
        }

        if (!directed && source != target) {
            successors[target.intValue].computeIfAbsent(source.intValue) { IntArrayList() }.add(edgeId)
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
