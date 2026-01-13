@file:JvmMultifileClass

package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractImmutableGraph
import io.github.sooniln.fastgraph.AbstractVertexSetList
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.GraphMutator
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.ImmutableGraphBuilder
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.PropertyGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSetList
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.nothingEdgeProperty
import io.github.sooniln.fastgraph.nothingVertexProperty
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlin.math.max
import kotlin.math.min

internal class ImmutableAdjacencyListGraph(
    override val directed: Boolean,
    private val successors: Array<IntArray>,
    _predecessors: Array<IntArray>?,
    private val numEdges: Int
) : AbstractImmutableGraph(), IndexedVertexGraph {

    private val predecessors: Array<IntArray> by lazy {
        check(directed)

        if (_predecessors != null) {
            return@lazy _predecessors
        }

        if (successors.isEmpty()) {
            return@lazy emptyArray()
        }

        val pds = Array(successors.size) { IntArrayList() }
        for (vertexIntValue in 0..<successors.size) {
            val vertex = Vertex(vertexIntValue)
            for (successor in successors(vertex)) {
                pds[successor.intValue].add(vertexIntValue)
            }
        }
        return@lazy Array(pds.size) { pds[it].toIntArray() }
    }

    init {
        if (_predecessors != null) {
            // touch lazy property to force initialization
            predecessors
        }
    }

    override val multiEdge: Boolean
        get() = false

    private fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.intValue !in 0..<successors.size) {
            throw IllegalArgumentException("$vertex not found in graph")
        }
        return vertex
    }

    private fun validateEdge(edge: Edge): Edge {
        try {
            validateVertex(edgeSource(edge))
            validateVertex(edgeTarget(edge))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$edge (${edgeSource(edge)} -> ${edgeTarget(edge)}) not found in graph", e)
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
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int = successors[validateVertex(vertex).intValue].size

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int {
        if (!directed) {
            return outDegree(vertex)
        }

        return predecessors[validateVertex(vertex).intValue].size
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSetList =
        VertexNeighbors(successors[validateVertex(vertex).intValue])

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSetList {
        if (!directed) {
            return successors(vertex)
        }

        return VertexNeighbors(predecessors[validateVertex(vertex).intValue])
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet =
        IncidentEdgeSet(true, vertex, successors[validateVertex(vertex).intValue])

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet {
        if (!directed) {
            return outgoingEdges(vertex)
        }

        return IncidentEdgeSet(false, vertex, predecessors[validateVertex(vertex).intValue])
    }

    override val edges: EdgeSet = object : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = numEdges

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            return true
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private var sourceVertex = 0
            private var targetSuccessorIndex = -1
            private var targetVertex = -1

            init {
                increment()
            }

            override fun hasNext(): Boolean = sourceVertex < successors.size
            override fun next(): Edge {
                if (sourceVertex >= successors.size) throw NoSuchElementException()
                val edge = canonicalEdge(directed, sourceVertex, targetVertex)
                increment()
                return edge
            }

            private fun increment() {
                ++targetSuccessorIndex
                while (true) {
                    if (sourceVertex >= successors.size) {
                        break
                    } else {
                        val successors = successors[sourceVertex]
                        if (targetSuccessorIndex == successors.size) {
                            ++sourceVertex
                            targetSuccessorIndex = 0
                        } else {
                            targetVertex = successors[targetSuccessorIndex]
                            if (!directed && sourceVertex > targetVertex) {
                                // don't report reverse edges (since we know we're iterating over all edges here it's
                                // safe to not follow the reverse entry as we can assume we'll encounter the canonical
                                // entry at some point).
                                ++targetSuccessorIndex
                            } else {
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = Vertex(edge.highBits)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = Vertex(edge.lowBits)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return successors[validateVertex(source).intValue].binarySearch(validateVertex(target).intValue) >= 0
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    override fun getEdge(source: Vertex, target: Vertex): Edge {
        if (!containsEdge(source, target)) throw NoSuchElementException()
        return canonicalEdge(directed, source, target)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return if (!containsEdge(source, target)) emptyEdgeSet() else edgeSetOf(canonicalEdge(directed, source, target))
    }

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun <T : S?, S> createVertexProperty(clazz: Class<S>, initializer: (Vertex) -> T): VertexProperty<T> {
        return immutableArrayVertexProperty(this, clazz, initializer)
    }

    override fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: (Edge) -> T): EdgeProperty<T> {
        // 1000 is somewhat arbitrarily chosen, but should create a difference < ~5s in accessing every
        // edge over a million edge property
        return if (edges.size < 1000) {
            immutableArrayMapEdgeProperty(this, clazz, initializer)
        } else {
            immutableMapEdgeProperty(this, clazz, initializer)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference = VertexReferenceImpl(validateVertex(vertex))

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference = EdgeReferenceImpl(validateEdge(edge))

    private class VertexNeighbors(private val sortedNeighbors: IntArray) : AbstractVertexSetList() {
        override val size: Int
            get() = sortedNeighbors.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Vertex): Boolean = sortedNeighbors.binarySearch(element.intValue) >= 0
        override fun get(index: Int): Vertex = Vertex(sortedNeighbors[index])

        // override superclass implementation for substantial speed gains, likely by changing the JVM profile point and
        // thus enabling additional optimizations?
        override fun iterator(): VertexIterator = object : VertexIterator {
            private var i = 0

            override fun hasNext(): Boolean = i < sortedNeighbors.size
            override fun next(): Vertex {
                if (i >= sortedNeighbors.size) throw NoSuchElementException()
                return Vertex(sortedNeighbors[i++])
            }
        }
    }

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val sortedNeighbors: IntArray,
    ) : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = sortedNeighbors.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)

            val source: Vertex
            val target: Vertex
            if (outgoing) {
                source = edgeSource(element)
                target = edgeTarget(element)
            } else {
                source = edgeTarget(element)
                target = edgeSource(element)
            }

            return if (!directed && target == vertex) {
                sortedNeighbors.binarySearch(source.intValue) >= 0
            } else {
                vertex == source && sortedNeighbors.binarySearch(target.intValue) >= 0
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private var i = 0
            override fun hasNext(): Boolean = i < sortedNeighbors.size
            override fun next(): Edge {
                if (i >= sortedNeighbors.size) throw NoSuchElementException()
                val neighbor = sortedNeighbors[i++]
                return if (outgoing) {
                    canonicalEdge(directed, vertex.intValue, neighbor)
                } else {
                    canonicalEdge(directed, neighbor, vertex.intValue)
                }
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun canonicalEdge(directed: Boolean, source: Vertex, target: Vertex) =
    canonicalEdge(directed, source.intValue, target.intValue)

@Suppress("NOTHING_TO_INLINE")
private inline fun canonicalEdge(directed: Boolean, sourceIntValue: Int, targetIntValue: Int): Edge {
    return if (!directed) {
        Edge(highBits = min(sourceIntValue, targetIntValue), lowBits = max(sourceIntValue, targetIntValue))
    } else {
        Edge(highBits = sourceIntValue, lowBits = targetIntValue)
    }
}

internal class ImmutableAdjacencyListGraphBuilder<V, E>(
    private val directed: Boolean,
) : ImmutableGraphBuilder<V, E>(), GraphMutator<V, E> {

    private val successors = ArrayList<IntSet>()
    private var numEdges = 0

    private val vertexMap = Object2IntOpenHashMap<V>()

    private var vertexPropertyClass: Class<V>? = null
    private var vertexPropertyInitializer: ((Vertex) -> V)? = null
    private var vertexProperty: ArrayList<V>? = null

    private var edgePropertyClass: Class<E>? = null
    private var edgePropertyInitializer: ((Edge) -> E)? = null
    private var edgeProperty: Long2ObjectOpenHashMap<E>? = null

    override fun withVertexProperty(clazz: Class<V>, initializer: (Vertex) -> V): ImmutableGraphBuilder<V, E> {
        check(successors.isEmpty())
        vertexPropertyClass = clazz
        vertexPropertyInitializer = initializer
        vertexProperty = ArrayList()
        return this
    }

    override fun withEdgeProperty(clazz: Class<E>, initializer: (Edge) -> E): ImmutableGraphBuilder<V, E> {
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
    override fun addVertex(): Vertex = addVertexInternal(false) { vertexPropertyInitializer!!(it) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(value: V): Vertex = addVertexInternal(true) { value }

    private inline fun addVertexInternal(mapValue: Boolean, valueRetriever: (Vertex) -> V): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(IntOpenHashSet())
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
        addEdgeInternal(source, target) { edgePropertyInitializer!!(it) }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex, value: E): Edge =
        addEdgeInternal(source, target) { value }

    private inline fun addEdgeInternal(source: Vertex, target: Vertex, valueRetriever: (Edge) -> E): Edge {
        if (successors[validateVertex(source).intValue].add(validateVertex(target).intValue)) {
            ++numEdges
            if (!directed && source != target) {
                successors[target.intValue].add(source.intValue)
            }
            val edge = canonicalEdge(directed, source, target)
            edgeProperty?.set(edge.longValue, valueRetriever(edge))
            return edge
        } else {
            throw IllegalArgumentException("This graph builder does not support multi-edges and $source -> $target already exists in graph builder")
        }
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
    }

    override fun mutate(): GraphMutator<V, E> = this

    override fun build(): ImmutableGraph {
        return ImmutableAdjacencyListGraph(
            directed,
            Array(successors.size) { successors[it].toIntArray().apply { sort() } },
            null,
            numEdges
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
