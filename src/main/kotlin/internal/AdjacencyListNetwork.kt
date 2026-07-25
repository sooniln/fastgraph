package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.Int2IntHashMap
import io.github.sooniln.fastcollect.ints.IntArrayList
import io.github.sooniln.fastcollect.ints.IntList
import io.github.sooniln.fastcollect.ints.emptyIntList
import io.github.sooniln.fastcollect.ints.intListOf
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeIndexedEdgeGraph
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.MutableIndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableIndexedEdgeSet
import io.github.sooniln.fastgraph.MutableIndexedVertexGraph
import io.github.sooniln.fastgraph.MutableIndexedVertexSet
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.vertexSetOf

internal class AdjacencyListNetwork(
    directed: Boolean,
    override val multiEdge: Boolean,
) : MutableIndexedVertexGraph, MutableIndexedEdgeGraph, VertexIndexedVertexGraph, EdgeIndexedEdgeGraph, AbstractGraph(directed) {

    private val _predecessors = lazy {
        check(directed)

        val pds = ArrayList<AdjacencySet>(successors.size)
        repeat(successors.size) {
            pds.add(AdjacencySet())
        }
        for (vertexId in successors.indices) {
            successors[vertexId].foreach { edgeAdjacency ->
                pds[edgeAdjacency.vertex.intValue].add(Vertex(vertexId), edgeAdjacency.edgeId)
            }
        }
        return@lazy pds
    }

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
                predecessors[lastIndex].foreach { adjacency ->
                    val edgeId = adjacency.edgeId

                    // predecessors has not been updated yet, so translate vertices if necessary
                    val source = if (adjacency.vertex == lastVertex) vertex else adjacency.vertex
                    edgeValues[edgeId] = EdgeValue(true, source, vertex)
                }

                predecessors[lastIndex].vertices.foreach { source ->
                    successors[source.intValue].updateVertex(lastVertex, vertex)
                }

                successors[lastIndex].foreach { adjacency ->
                    val edgeId = adjacency.edgeId
                    // successors has already been updated, so no translation necessary
                    edgeValues[edgeId] = EdgeValue(true, vertex, adjacency.vertex)
                }

                successors[lastIndex].vertices.foreach { newTarget ->
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget.intValue == index) lastIndex else newTarget.intValue
                    predecessors[target].updateVertex(lastVertex, vertex)
                }
            } else {
                successors[lastIndex].foreach { adjacency ->
                    val edgeId = adjacency.edgeId
                    // successors has already been updated, so no translation necessary
                    val vertexOther = if (adjacency.vertex == lastVertex) vertex else adjacency.vertex
                    edgeValues[edgeId] = EdgeValue(false, vertex, vertexOther)
                }

                successors[lastIndex].vertices.foreach { newTarget ->
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget.intValue == index) lastIndex else newTarget.intValue
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
        if (!multiEdge) {
            require(!adjacencySet.contains(target)) { "$source -> $target already exists in graph" }
        }

        edgeValues.add(edgeValue)
        adjacencySet.add(target, edgeId)

        if (!directed) {
            if (source != target) {
                successors[target.intValue].add(source, edgeId)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target.intValue].add(source, edgeId)
        }

        return Edge(edgeId.toLong())
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
        val oldEdge = Edge(lastIndex.toLong())
        val newEdge = Edge(index.toLong())
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

    override fun validateVertex(vertex: Vertex): Vertex {
        require(vertex.intValue in successors.indices) { "$vertex not found in graph" }
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        require(edge.lowBits in edgeValues.indices) { "$edge not found in graph" }
        return edge
    }

    override val vertexCount: Int get() = successors.size

    override val vertices: MutableIndexedVertexSet = MutableVertexIndexedVertexSet<AdjacencyListNetwork>(this@AdjacencyListNetwork)

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex.intValue].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex.intValue].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex.intValue].vertices
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex.intValue].vertices
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(true, validateVertex(vertex), successors[vertex.intValue])
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(false, validateVertex(vertex), predecessors[vertex.intValue])

    override val edgeCount: Int get() = edgeValues.size

    override val edges: MutableIndexedEdgeSet = MutableEdgeIndexedEdgeSet<AdjacencyListNetwork>(this@AdjacencyListNetwork)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = edgeValues[validateEdge(edge).lowBits].source

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = edgeValues[validateEdge(edge).lowBits].target

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source.intValue].contains(target)
    override fun getEdge(source: Vertex, target: Vertex): Edge {
        val adjacenciesIt = successors[source.intValue].edgesTo(target).iterator()
        if (!adjacenciesIt.hasNext()) throw NoSuchElementException()
        return adjacenciesIt.next().edge
    }
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet = IncidentEdgeSet(true, source, successors[source.intValue].edgesTo(target))

    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        sparse: Boolean,
        initializer: VertexInitializer<T>
    ): MutableVertexProperty<T> {
        val property = mutableArrayListVertexProperty(this, clazz, initializer)
        property.ensureCapacity(vertices.size)
        vertexProperties.addProperty(property)
        return property
    }

    override fun <T : S?, S> createEdgeProperty(
        clazz: Class<S>,
        sparse: Boolean,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> {
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
    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.ref(validateEdge(edge))

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
    ) : AbstractEdgeSet() {
        override val size: Int get() = adjacencies.size
        override fun iterator(): EdgeIterator = adjacencies.iterator().toEdgeIterator()
        override fun foreach(action: EdgeConsumer) = adjacencies.foreach { edgeAdjacency -> action.accept(edgeAdjacency.edge) }

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
                adjacencies.contains(EdgeAdjacency(source, element))
            } else {
                vertex == source && adjacencies.contains(EdgeAdjacency(target, element))
            }
        }
    }
}

private class AdjacencySet : EdgeAdjacencySet {

    // map of vertices to edges
    // if a vertex is associated with only a single edge, the value in this map is the edge id
    // if a vertex is associated with multiple edges, the negated value in this map is the key into edgeListMap
    private val map = Int2IntHashMap(defaultValue = Int.MIN_VALUE)
    // TODO: performance implications of changing this to a set?
    private val edgeListMap = Int2AnyHashMap<IntArrayList>()
    // tracks the next available key for edgeListMap - always decrements
    private var edgeIdNextIndex = -1

    override var size = 0
        private set

    override val vertices: VertexSet get() = map.keys.asVertexSet()

    override fun contains(element: EdgeAdjacency): Boolean {
        val v = map.getOrDefault(element.vertex.intValue, Int.MIN_VALUE)
        return v != Int.MIN_VALUE && if (v < 0) {
            edgeListMap.getValue(v).contains(element.edgeId)
        } else {
            element.edgeId == v
        }
    }

    override fun contains(vertex: Vertex): Boolean {
        return map.containsKey(vertex.intValue)
    }

    override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
        private val mapIt = map.iterator()
        private var vertex = Vertex(-1)
        private var edgeIt: IntIterator? = null
        private var edgeId = -1

        init {
            increment()
        }

        override fun hasNext(): Boolean = edgeId >= 0

        override fun next(): EdgeAdjacency {
            if (edgeId < 0) throw NoSuchElementException()
            val ea = EdgeAdjacency(vertex, edgeId)
            increment()
            return ea
        }

        private fun increment() {
            var edgeIterator = edgeIt
            if (edgeIterator == null || !edgeIterator.hasNext()) {
                if (!mapIt.hasNext()) {
                    edgeId = -1
                    return
                }

                val entry = mapIt.next()
                vertex = Vertex(entry.key)
                val v = entry.value
                if (v < 0) {
                    edgeIterator = edgeListMap.getValue(v).iterator()
                } else {
                    edgeIt = null
                    edgeId = v
                    return
                }
            }

            edgeIt = edgeIterator
            edgeId = edgeIterator.nextInt()
        }
    }

    override fun foreach(action: EdgeAdjacencyConsumer) {
        map.foreach { vertex, edgeId ->
            val vertex = Vertex(vertex)
            if (edgeId < 0) {
                edgeListMap.getValue(edgeId).foreach { edgeId ->
                    action.accept(EdgeAdjacency(vertex, edgeId))
                }
            } else {
                action.accept(EdgeAdjacency(vertex, edgeId))
            }
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
            val edgeIds = edgeListMap.getValue(v)
            val i = edgeIds.indexOf(oldEdgeId)
            check(i != -1)
            edgeIds[i] = newEdgeId
        } else {
            check(v == oldEdgeId)
            map[vertex.intValue] = newEdgeId
        }
    }

    fun add(vertex: Vertex, edgeId: Int) {
        val v = map[vertex.intValue]
        if (v == Int.MIN_VALUE) {
            map[vertex.intValue] = edgeId
        } else {
            val edgeIds: IntArrayList
            if (v < 0) {
                edgeIds = edgeListMap.getValue(v)
            } else {
                edgeIds = IntArrayList(2)
                edgeIds.add(v)
                edgeListMap.put(edgeIdNextIndex, edgeIds)
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
            val edgeIds = edgeListMap.getValue(v)
            check(edgeIds.remove(edgeId))
            if (edgeIds.size == 1) {
                edgeListMap.remove(v)
                map.put(vertex.intValue, edgeIds[0])
            }
        } else {
            check(v == edgeId)
            map.remove(vertex.intValue)
        }

        --size
    }

    fun edgesTo(target: Vertex): EdgeAdjacencySet = object : EdgeAdjacencySet {
        private val edgeIds: IntList

        init {
            val v = map[target.intValue]
            edgeIds = if (v == Int.MIN_VALUE) {
                emptyIntList()
            } else if (v < 0) {
                edgeListMap.getValue(v)
            } else {
                intListOf(v)
            }
        }

        override val size: Int get() = edgeIds.size

        override fun contains(element: EdgeAdjacency): Boolean {
            return element.vertex == target && edgeIds.contains(element.edgeId)
        }

        override fun contains(vertex: Vertex): Boolean {
            return vertex == target
        }

        override val vertices: VertexSet get() = vertexSetOf(target)

        override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
            private val it = edgeIds.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): EdgeAdjacency = EdgeAdjacency(target, it.nextInt())
        }

        override fun foreach(action: EdgeAdjacencyConsumer) {
            edgeIds.forEach { edgeId -> action.accept(EdgeAdjacency(target, edgeId)) }
        }

        override fun toString(): String = iterator().asSequence().joinToString(", ", "[", "]")
    }

    override fun toString(): String = iterator().asSequence().joinToString(", ", "[", "]")
}
