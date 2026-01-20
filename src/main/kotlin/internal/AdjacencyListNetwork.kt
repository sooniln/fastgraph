package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractEdgeSetList
import io.github.sooniln.fastgraph.AbstractVertexCollection
import io.github.sooniln.fastgraph.AbstractVertexSetList
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIndexedEdgeGraph
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableEdgeListIterator
import io.github.sooniln.fastgraph.MutableEdgeSetList
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableIndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableIndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexIterator
import io.github.sooniln.fastgraph.MutableVertexListIterator
import io.github.sooniln.fastgraph.MutableVertexSetList
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexIteratorWrapper
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.primitives.Int2IntHashMap
import io.github.sooniln.fastgraph.vertexSetOf
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntIterator
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntLists

internal class AdjacencyListNetwork(
    override val directed: Boolean,
    private val supportMultiEdge: Boolean
) : MutableGraph, MutableIndexedVertexGraph, MutableIndexedEdgeGraph, VertexIndexedVertexGraph, EdgeIndexedEdgeGraph {

    private val _predecessors = lazy {
        check(directed)

        val pds = ArrayList<AdjacencySet>(successors.size)
        repeat(successors.size) {
            pds.add(AdjacencySet())
        }
        for (vertexIntValue in successors.indices) {
            val it = successors[vertexIntValue].iterator()
            while (it.hasNext()) {
                val adjacency = it.next()
                pds[adjacency.vertex.intValue].add(Vertex(vertexIntValue), adjacency.edgeId)
            }
        }
        return@lazy pds
    }

    private var multiEdgeCount = 0
    private val successors: ArrayList<AdjacencySet> = ArrayList()
    private val predecessors: ArrayList<AdjacencySet> by _predecessors
    private val edgeValues = EdgeValueArrayList()

    private val vertexRefs = VertexReferenceHolder()
    private val edgeRefs = IntEdgeReferenceHolder()

    private val vertexProperties = VertexPropertiesHolder()
    private val edgeProperties = EdgePropertiesHolder()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(AdjacencySet())
        if (_predecessors.isInitialized()) {
            predecessors.add(AdjacencySet())
        }

        return vertex
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeVertex")
    override fun removeVertex(vertex: Vertex) {
        // remove outbound edges
        val outboundAdjacencies = successors[validateVertex(vertex).intValue]
        while (!outboundAdjacencies.isEmpty()) {
            removeEdgeInternal(outboundAdjacencies.iterator().next().edgeId)
        }

        // remove inbound edges
        if (directed) {
            val inboundAdjacencies = predecessors[vertex.intValue]
            while (!inboundAdjacencies.isEmpty()) {
                removeEdgeInternal(inboundAdjacencies.iterator().next().edgeId)
            }
        }

        cleanupVertex(vertex.intValue)
    }

    private fun cleanupVertex(index: Int) {
        val vertex = Vertex(index)

        // we're going to swap the last vertex into the spot current occupied by the vertex to be removed. this means we
        // need to update all references to last vertex to point to its new location, and then do the swap.
        val lastIndex = successors.lastIndex
        val lastVertex = Vertex(lastIndex)

        if (index != lastIndex) {
            // update edge adjacencies
            if (directed) {
                val inboundAdjacencyIt = predecessors[lastIndex].iterator()
                while (inboundAdjacencyIt.hasNext()) {
                    val adjacency = inboundAdjacencyIt.next()
                    val edgeId = adjacency.edgeId

                    // predecessors has not been updated yet, so translate vertices if necessary
                    val source = if (adjacency.vertex == lastVertex) vertex else adjacency.vertex
                    edgeValues[edgeId] = EdgeValue(true, source, vertex)
                }

                val sourceIt = predecessors[lastIndex].vertexIterator()
                while (sourceIt.hasNext()) {
                    successors[sourceIt.next().intValue].updateVertex(lastVertex, vertex)
                }

                val outboundAdjacencyIt = successors[lastIndex].iterator()
                while (outboundAdjacencyIt.hasNext()) {
                    val adjacency = outboundAdjacencyIt.next()
                    val edgeId = adjacency.edgeId
                    // successors has already been updated, so no translation necessary
                    edgeValues[edgeId] = EdgeValue(true, vertex, adjacency.vertex)
                }

                val targetIt = successors[lastIndex].vertexIterator()
                while (targetIt.hasNext()) {
                    val newTarget = targetIt.next().intValue
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget == index) lastIndex else newTarget
                    predecessors[target].updateVertex(lastVertex, vertex)
                }
            } else {
                val outboundAdjacencyIt = successors[lastIndex].iterator()
                while (outboundAdjacencyIt.hasNext()) {
                    val adjacency = outboundAdjacencyIt.next()
                    val edgeId = adjacency.edgeId
                    // successors has already been updated, so no translation necessary
                    val vertexOther = if (adjacency.vertex == lastVertex) vertex else adjacency.vertex
                    edgeValues[edgeId] = EdgeValue(true, vertex, vertexOther)
                }

                val targetIt = successors[lastIndex].vertexIterator()
                while (targetIt.hasNext()) {
                    val newTarget = targetIt.next().intValue
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget == index) lastIndex else newTarget
                    successors[target].updateVertex(lastVertex, vertex)
                }
            }
        }

        val oldVertex = Vertex(lastIndex)
        val newVertex = Vertex(index)

        // update vertex references
        vertexProperties.swapAndRemove(oldVertex, newVertex)
        vertexRefs.swapAndRemove(oldVertex, newVertex)

        // shift last vertex into the place of removed vertex now that all references have been updated
        successors[index] = successors[lastIndex]
        successors.removeAt(lastIndex)
        if (directed) {
            predecessors[index] = predecessors[lastIndex]
            predecessors.removeAt(lastIndex)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex): Edge {
        val edgeId = edgeValues.size
        val edgeValue = EdgeValue(directed, validateVertex(source), validateVertex(target))

        val adjacencySet = successors[source.intValue]
        val containsTarget = adjacencySet.contains(target)
        if (!supportMultiEdge) {
            require(!containsTarget) { "$source -> $target already exists in graph" }
        }

        edgeValues.add(edgeValue)
        adjacencySet.add(target, edgeId)
        if (containsTarget) {
            ++multiEdgeCount
        }

        if (!directed) {
            if (source != target) {
                successors[target.intValue].add(source, edgeId)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target.intValue].add(source, edgeId)
        }

        return canonicalEdge(edgeId)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeEdge")
    override fun removeEdge(edge: Edge) = removeEdgeInternal(validateEdge(edge).lowBits)

    private fun removeEdgeInternal(edgeId: Int) {
        val edgeValue = edgeValues[edgeId]
        val source = edgeValue.source
        val target = edgeValue.target

        val adjacencySet = successors[source.intValue]
        adjacencySet.remove(target, edgeId)
        if (adjacencySet.contains(target)) {
            check(--multiEdgeCount >= 0)
        }

        if (!directed) {
            if (source != target) {
                successors[target.intValue].remove(source, edgeId)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target.intValue].remove(source, edgeId)
        }

        cleanupEdge(edgeId)
    }

    private fun cleanupEdge(index: Int) {
        val lastIndex = edgeValues.lastIndex

        val lastEdgeValue = edgeValues[lastIndex]
        val lastSource = lastEdgeValue.source
        val lastTarget = lastEdgeValue.target

        // update edge references
        val oldEdge = canonicalEdge(lastIndex)
        val newEdge = canonicalEdge(index)
        edgeProperties.swapAndRemove(oldEdge, newEdge)
        edgeRefs.swapAndRemove(oldEdge, newEdge)

        if (index != lastIndex) {
            // update successor and predecessor values
            successors[lastSource.intValue].updateAdjacency(lastTarget, lastIndex, index)
            if (!directed) {
                if (lastSource != lastTarget) {
                    successors[lastTarget.intValue].updateAdjacency(lastSource, lastIndex, index)
                }
            } else if (_predecessors.isInitialized()) {
                predecessors[lastTarget.intValue].updateAdjacency(lastSource, lastIndex, index)
            }
        }

        // shift last edge into the place of removed edge now that all references have been updated
        edgeValues[index] = lastEdgeValue
        edgeValues.removeAt(lastIndex)
    }

    override val multiEdge: Boolean
        get() = multiEdgeCount > 0

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

    override val vertices: MutableVertexSetList = object : MutableVertexSetList, AbstractVertexSetList() {
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

        override fun iterator(): MutableVertexIterator = Iterator(0)
        override fun listIterator(): MutableVertexListIterator = Iterator(0)
        override fun listIterator(index: Int): MutableVertexListIterator = Iterator(index)

        private inner class Iterator(index: Int) : AbstractVertexListIterator(index) {
            override fun remove(index: Int) = removeVertex(Vertex(index))
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
    override fun successors(vertex: Vertex): VertexSet = successors[validateVertex(vertex).intValue].vertices()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet {
        if (!directed) {
            return successors(vertex)
        }

        return predecessors[validateVertex(vertex).intValue].vertices()
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet =
        IncidentEdgeSet(true, validateVertex(vertex), successors[vertex.intValue])

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet {
        if (!directed) {
            return outgoingEdges(vertex)
        }

        return IncidentEdgeSet(false, validateVertex(vertex), predecessors[vertex.intValue])
    }

    override val edges: MutableEdgeSetList = object : MutableEdgeSetList, AbstractEdgeSetList() {
        override val size: Int get() = edgeValues.size
        override fun get(index: Int): Edge = canonicalEdge(index)

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("indexOf")
        override fun indexOf(element: Edge): Int = validateEdge(element).lowBits

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("lastIndexOf")
        override fun lastIndexOf(element: Edge): Int = validateEdge(element).lowBits

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            return true
        }

        override fun iterator(): MutableEdgeIterator = Iterator(0)
        override fun listIterator(): MutableEdgeListIterator = Iterator(0)
        override fun listIterator(index: Int): MutableEdgeListIterator = Iterator(index)

        private inner class Iterator(index: Int) : AbstractEdgeListIterator(index) {
            override fun remove(index: Int) = removeEdgeInternal(index)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = edgeValues[validateEdge(edge).lowBits].source

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = edgeValues[validateEdge(edge).lowBits].target

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return successors[validateVertex(source).intValue].contains(validateVertex(target))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    override fun getEdge(source: Vertex, target: Vertex): Edge {
        val adjacency = successors[validateVertex(source).intValue].firstAdjacency(validateVertex(target))
        return canonicalEdge(adjacency.edgeId)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, validateVertex(source), successors[source.intValue].subset(validateVertex(target)))
    }

    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        initializer: VertexInitializer<T>
    ): VertexProperty<T> {
        val property = mutableArrayListVertexProperty(this, clazz, initializer)
        property.ensureCapacity(vertices.size)
        vertexProperties.addProperty(property)
        return property
    }

    override fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: EdgeInitializer<T>): EdgeProperty<T> {
        val property = mutableArrayListEdgeProperty(this, clazz, initializer)
        property.ensureCapacity(edges.size)
        edgeProperties.addProperty(property)
        return property
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference = vertexRefs.ref(validateVertex(vertex))

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.ref(edge)

    override fun ensureVertexCapacity(vertexCapacity: Int) {
        successors.ensureCapacity(vertexCapacity)
        if (_predecessors.isInitialized()) {
            predecessors.ensureCapacity(vertexCapacity)
        }
        vertexProperties.ensureCapacity(vertexCapacity)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) {
        edgeValues.ensureCapacity(edgeCapacity)
        edgeProperties.ensureCapacity(edgeCapacity)
    }

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val adjacencies: EdgeAdjacencySet
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
            private val it = adjacencies.edgeIdIterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(it.nextInt())
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun canonicalEdge(edgeId: Int): Edge {
    return Edge(edgeId.toLong())
}

private class AdjacencySet : EdgeAdjacencySet {

    private val map = Int2IntHashMap(poisonValue = Int.MIN_VALUE)
    private val edgeIdMap = Int2ObjectOpenHashMap<IntArrayList>()
    private var edgeIdNextIndex = -1

    override var size = 0
        private set

    override fun isEmpty(): Boolean = size == 0

    override fun contains(element: EdgeAdjacency): Boolean {
        val v = map[element.vertex.intValue]
        return if (v == Int.MIN_VALUE) {
            false
        } else if (v < 0) {
            edgeIdMap.get(v).contains(element.edgeId)
        } else {
            element.edgeId == v
        }
    }

    override fun contains(vertex: Vertex): Boolean = map.containsKey(vertex.intValue)

    override fun containsAll(elements: Collection<EdgeAdjacency>): Boolean = throw UnsupportedOperationException()

    override fun vertices(): VertexSet = object : VertexSet, AbstractVertexCollection() {
        override val size: Int get() = map.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Vertex): Boolean = map.containsKey(element.intValue)
        override fun iterator(): VertexIterator = VertexIteratorWrapper(map.keys.iterator())
    }

    override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
        private val mapIt = map.primitiveEntries.iterator()
        private var vertexAdjacency = EdgeAdjacency(0L)
        private var edgeIt: IntIterator? = null
        private var edgeId: Int = -1

        init {
            increment()
        }

        override fun hasNext(): Boolean = edgeId >= 0

        override fun next(): EdgeAdjacency {
            if (edgeId < 0) throw NoSuchElementException()
            val ea = EdgeAdjacency(vertexAdjacency, edgeId)
            increment()
            return ea
        }

        private fun increment() {
            var edgeIt = edgeIt
            if (edgeIt == null || !edgeIt.hasNext()) {
                if (!mapIt.hasNext()) {
                    edgeId = -1
                    return
                }

                val entry = mapIt.next()
                val key = entry.key
                val value = entry.value
                vertexAdjacency = EdgeAdjacency(Vertex(key), 0)
                if (value < 0) {
                    edgeIt = edgeIdMap[value].iterator()
                    this.edgeIt = edgeIt
                } else {
                    edgeId = value
                    return
                }
            }

            edgeId = edgeIt.nextInt()
        }
    }

    fun firstAdjacency(vertex: Vertex): EdgeAdjacency {
        val v = map[vertex.intValue]
        return if (v == Int.MIN_VALUE) {
            throw NoSuchElementException()
        } else if (v < 0) {
            EdgeAdjacency(vertex, edgeIdMap.get(v).getInt(0))
        } else {
            EdgeAdjacency(vertex, v)
        }
    }

    fun updateVertex(oldVertex: Vertex, newVertex: Vertex) {
        check(!map.containsKey(newVertex.intValue))
        val oldValue = map.remove(oldVertex.intValue)
        check(oldValue != Int.MIN_VALUE)
        map[newVertex.intValue] = oldValue
    }

    fun updateAdjacency(vertex: Vertex, oldEdgeId: Int, newEdgeId: Int) {
        val v = map[vertex.intValue]
        if (v == Int.MIN_VALUE) {
            throw IllegalStateException()
        } else if (v < 0) {
            val edgeIds = edgeIdMap.get(v)
            val i = edgeIds.indexOf(oldEdgeId)
            check(i != -1)
            edgeIds[i] = newEdgeId
        } else {
            check(v == oldEdgeId)
            map[vertex.intValue] = newEdgeId
        }
    }

    fun subset(subsetVertex: Vertex): EdgeAdjacencySet = object : EdgeAdjacencySet {
        private val edgeIds: IntList

        init {
            val v = map[subsetVertex.intValue]
            edgeIds = if (v == Int.MIN_VALUE) {
                IntLists.emptyList()
            } else if (v < 0) {
                edgeIdMap.get(v)
            } else {
                IntLists.singleton(v)
            }
        }

        override val size: Int
            get() = edgeIds.size

        override fun isEmpty(): Boolean = size == 0

        override fun contains(element: EdgeAdjacency): Boolean {
            return element.vertex == subsetVertex && edgeIds.contains(element.edgeId)
        }

        override fun containsAll(elements: Collection<EdgeAdjacency>): Boolean = throw UnsupportedOperationException()

        override fun contains(vertex: Vertex): Boolean = vertex == subsetVertex

        override fun vertices(): VertexSet = vertexSetOf(subsetVertex)

        override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
            private val it = edgeIds.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): EdgeAdjacency = EdgeAdjacency(subsetVertex, it.nextInt())
        }

        override fun edgeIdIterator(): IntIterator = edgeIds.iterator()

        override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
    }

    fun add(vertex: Vertex, edgeId: Int) {
        val v = map[vertex.intValue]
        if (v == Int.MIN_VALUE) {
            map[vertex.intValue] = edgeId
        } else {
            val edgeIds: IntArrayList
            if (v < 0) {
                edgeIds = edgeIdMap[v]
            } else {
                edgeIds = IntArrayList(2)
                edgeIds.add(v)
                edgeIdMap.put(edgeIdNextIndex, edgeIds)
                map.put(vertex.intValue, edgeIdNextIndex)
                --edgeIdNextIndex
            }

            edgeIds.add(edgeId)
        }

        ++size
    }

    fun remove(vertex: Vertex, edgeId: Int) {
        val v = map[vertex.intValue]
        check(v != Int.MIN_VALUE)
        if (v < 0) {
            val edgeIds = edgeIdMap[v]
            check(edgeIds.rem(edgeId))
            if (edgeIds.size == 1) {
                edgeIdMap.remove(v)
                map.put(vertex.intValue, edgeIds.getInt(0))
            }
        } else {
            check(v == edgeId)
            map.remove(vertex.intValue)
        }

        --size
    }

    fun vertexIterator(): VertexIterator = VertexIteratorWrapper(map.keys.iterator())

    override fun edgeIdIterator(): IntIterator = object : IntIterator {
        private val mapIt = map.values.iterator()
        private var edgeIt: IntIterator? = null
        private var edgeId: Int = -1

        init {
            increment()
        }

        override fun hasNext(): Boolean = edgeId >= 0

        override fun nextInt(): Int {
            if (edgeId < 0) throw NoSuchElementException()
            val ea = edgeId
            increment()
            return ea
        }

        private fun increment() {
            var edgeIt = edgeIt
            if (edgeIt != null) {
                if (edgeIt.hasNext()) {
                    edgeId = edgeIt.nextInt()
                    return
                } else {
                    this.edgeIt = null
                }
            }

            if (!mapIt.hasNext()) {
                edgeId = -1
                return
            }

            val value = mapIt.nextInt()
            edgeId = if (value < 0) {
                edgeIt = edgeIdMap[value].iterator()
                this.edgeIt = edgeIt
                edgeIt.nextInt()
            } else {
                value
            }
        }

        override fun remove() = throw UnsupportedOperationException()
    }

    override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
}
