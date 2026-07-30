@file:JvmMultifileClass

package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.IntArrayList
import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractImmutableGraph
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexConsumer
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexIterator
import io.github.sooniln.fastgraph.compareTo
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.inc
import io.github.sooniln.fastgraph.properties.createImmutableGraphEdgeProperty
import io.github.sooniln.fastgraph.properties.createImmutableGraphVertexProperty
import io.github.sooniln.fastgraph.references.ImmutableEdgeReference
import io.github.sooniln.fastgraph.references.ImmutableVertexReference
import kotlin.math.max
import kotlin.math.min

internal class ImmutableAdjacencyListGraph(
    directed: Boolean,
    private val successors: Array<IntArray>,
    _predecessors: Array<IntArray>?,
    private val numEdges: Int
) : AbstractImmutableGraph(directed), IndexedVertexGraph {

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
                pds[successor.intValue].add(vertexIntValue)
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
        if (vertex.intValue !in successors.indices) throwIllegalVertex(vertex)
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

    override val vertices: IndexedVertexSet = object : AbstractVertexIndexedVertexSet() {
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

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
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

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = Vertex(edge.highBits)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = Vertex(edge.lowBits)

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source].binarySearch(target) >= 0

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        if (!containsEdge(source, target)) throw NoSuchElementException()
        return canonicalEdge(directed, source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return if (!containsEdge(source, target)) emptyEdgeSet() else edgeSetOf(canonicalEdge(directed, source, target))
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

    private class VertexNeighbors(private val sortedNeighbors: IntArray) : AbstractVertexSet() {
        override val size: Int get() = sortedNeighbors.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Vertex): Boolean = sortedNeighbors.binarySearch(element.intValue) >= 0

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
    ) : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = sortedNeighbors.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeSource(element)
            val target = edgeTarget(element)

            return if (!directed && target == vertex) {
                sortedNeighbors.binarySearch(source.intValue) >= 0
            } else {
                vertex == source && sortedNeighbors.binarySearch(target.intValue) >= 0
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = sortedNeighbors.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(directed, vertex, Vertex(it.nextInt()))
        }

        override fun foreach(action: EdgeConsumer) {
            for (neighbor in sortedNeighbors) {
                action.accept(canonicalEdge(directed, vertex, Vertex(neighbor)))
            }
        }
    }

    private inner class IncomingIncidentEdgeSet(
        private val vertex: Vertex,
        private val sortedNeighbors: IntArray,
    ) : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = sortedNeighbors.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeTarget(element)
            val target = edgeSource(element)

            return if (!directed && target == vertex) {
                sortedNeighbors.binarySearch(source.intValue) >= 0
            } else {
                vertex == source && sortedNeighbors.binarySearch(target.intValue) >= 0
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = sortedNeighbors.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(directed, Vertex(it.nextInt()), vertex)
        }

        override fun foreach(action: EdgeConsumer) {
            for (neighbor in sortedNeighbors) {
                action.accept(canonicalEdge(directed, Vertex(neighbor), vertex))
            }
        }
    }

    private operator fun Array<IntArray>.get(vertex: Vertex) = get(vertex.intValue)
    private fun IntArray.binarySearch(vertex: Vertex) = binarySearch(vertex.intValue)
    private inline fun IntArray.foreachVertex(crossinline action: (Vertex) -> Unit) {
        for (value in this) {
            action(Vertex(value))
        }
    }

    private fun canonicalEdge(directed: Boolean, source: Vertex, target: Vertex): Edge {
        return if (!directed) {
            Edge(highBits = min(source.intValue, target.intValue), lowBits = max(source.intValue, target.intValue))
        } else {
            Edge(highBits = source.intValue, lowBits = target.intValue)
        }
    }

    // only use if you know source <= target
    private fun canonicalSortedEdge(source: Vertex, target: Vertex): Edge {
        assert(source <= target)
        return Edge(highBits = source.intValue, lowBits = target.intValue)
    }

    private companion object {
        private val INVALID_VERTEX = Vertex(-1)
    }
}

/*
// TODO: use graph internally
internal class ImmutableAdjacencyListGraphBuilder<V, E>(
    private val directed: Boolean,
) : ImmutableGraphBuilder<V, E>(), GraphMutator<V, E> {

    private val successors = ArrayList<GraphIntHashSet>()
    private var numEdges = 0

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
        successors.add(GraphIntHashSet())
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
*/
