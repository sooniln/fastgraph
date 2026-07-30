package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.Int2IntHashMap
import io.github.sooniln.fastcollect.ints.IntArrayList
import io.github.sooniln.fastcollect.ints.IntList
import io.github.sooniln.fastcollect.ints.emptyIntIterator
import io.github.sooniln.fastcollect.ints.emptyIntList
import io.github.sooniln.fastcollect.ints.intIteratorOf
import io.github.sooniln.fastcollect.ints.intListOf
import io.github.sooniln.fastcollect.longs.LongArrayList
import io.github.sooniln.fastcollect.longs.lastIndex
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableIndexedEdgeSet
import io.github.sooniln.fastgraph.MutableIndexedVertexSet
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.properties.MutableGraphEdgePropertiesManager
import io.github.sooniln.fastgraph.properties.MutableGraphVertexPropertiesManager
import io.github.sooniln.fastgraph.properties.createMutableGraphEdgeProperty
import io.github.sooniln.fastgraph.properties.createMutableGraphVertexProperty
import io.github.sooniln.fastgraph.references.EdgeReferenceManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager
import io.github.sooniln.fastgraph.vertexSetOf

internal class AdjacencyListNetwork(
    directed: Boolean,
    override val multiEdge: Boolean,
) : IndexedVertexGraph, IndexedEdgeGraph, MutableGraph, AbstractGraph(directed) {

    private val _predecessors = lazy {
        check(directed)

        val predecessors = ArrayList<AdjacencySet>(successors.size)
        repeat(successors.size) {
            predecessors.add(AdjacencySet())
        }
        for (index in successors.indices) {
            val vertex = Vertex(index)
            successors[vertex].foreach { edgeAdjacency ->
                predecessors[edgeAdjacency.vertex].add(vertex, edgeAdjacency.edge)
            }
        }
        return@lazy predecessors
    }

    private val successors: ArrayList<AdjacencySet> = ArrayList()
    private val predecessors: ArrayList<AdjacencySet> by _predecessors
    private val edgeValues = LongArrayList()

    private val vertexProperties = MutableGraphVertexPropertiesManager()
    private val edgeProperties = MutableGraphEdgePropertiesManager()

    private val vertexRefs = VertexReferenceManager()
    private val edgeRefs = EdgeReferenceManager()

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.intValue !in successors.indices) throwIllegalVertex(vertex)
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        if (edge.lowBits !in edgeValues.indices) throwIllegalEdge(edge)
        return edge
    }

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

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(AdjacencySet())
        if (_predecessors.isInitialized()) {
            predecessors.add(AdjacencySet())
        }

        // update vertex properties
        vertexProperties.onVertexAdded(vertex)

        return vertex
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeVertex")
    override fun removeVertex(vertex: Vertex) {
        validateVertex(vertex)

        // remove outbound edges
        val outboundAdjacencies = successors[vertex]
        while (!outboundAdjacencies.isEmpty()) {
            removeEdgeInternal(outboundAdjacencies.iterator().next().edge)
        }

        // remove inbound edges
        if (directed) {
            val inboundAdjacencies = predecessors[vertex]
            while (!inboundAdjacencies.isEmpty()) {
                removeEdgeInternal(inboundAdjacencies.iterator().next().edge)
            }
        }

        cleanupVertex(vertex)
    }

    private fun cleanupVertex(vertex: Vertex) {
        // we're going to swap the last vertex into the spot current occupied by the vertex to be removed. this means we
        // need to update all references to last vertex to point to its new location, and then do the swap.
        val lastVertex = Vertex(successors.lastIndex)

        if (vertex != lastVertex) {
            // update edge adjacencies
            if (directed) {
                predecessors[lastVertex].foreach { adjacency ->
                    // predecessors has not been updated yet, so translate vertices if necessary
                    val source = if (adjacency.vertex == lastVertex) vertex else adjacency.vertex
                    edgeValues[adjacency.edge] = EdgeValue(true, source, vertex)
                }

                predecessors[lastVertex].vertices.foreach { source ->
                    successors[source].updateVertex(lastVertex, vertex)
                }

                successors[lastVertex].foreach { adjacency ->
                    // successors has already been updated, so no translation necessary
                    edgeValues[adjacency.edge] = EdgeValue(true, vertex, adjacency.vertex)
                }

                successors[lastVertex].vertices.foreach { newTarget ->
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget == vertex) lastVertex else newTarget
                    predecessors[target].updateVertex(lastVertex, vertex)
                }
            } else {
                successors[lastVertex].foreach { adjacency ->
                    // successors has already been updated, so no translation necessary
                    val vertexOther = if (adjacency.vertex == lastVertex) vertex else adjacency.vertex
                    edgeValues[adjacency.edge] = EdgeValue(false, vertex, vertexOther)
                }

                successors[lastVertex].vertices.foreach { newTarget ->
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget == vertex) lastVertex else newTarget
                    successors[target].updateVertex(lastVertex, vertex)
                }
            }

            // update vertex references
            vertexProperties.onVertexReassigned(lastVertex, vertex)
            vertexRefs.onVertexReassigned(lastVertex, vertex)

            successors[vertex] = successors[lastVertex]
            if (directed) {
                predecessors[vertex] = predecessors[lastVertex]
            }
        } else {
            // update vertex references
            vertexProperties.onVertexRemoved(vertex)
            vertexRefs.onVertexRemoved(vertex)
        }

        // remove vertex
        successors.remove(lastVertex)
        if (directed) {
            predecessors.remove(lastVertex)
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex): Edge {
        validateVertex(source)
        validateVertex(target)

        val edge = Edge(edgeValues.size.toLong())
        val edgeValue = EdgeValue(directed, source, target)

        val adjacencySet = successors[source]
        require(multiEdge || !adjacencySet.contains(target)) { "$source -> $target already exists in graph" }

        edgeValues.add(edgeValue)
        adjacencySet.add(target, edge)

        if (!directed) {
            if (source != target) {
                successors[target].add(source, edge)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target].add(source, edge)
        }

        return edge
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeEdge")
    override fun removeEdge(edge: Edge) = removeEdgeInternal(validateEdge(edge))

    private fun removeEdgeInternal(edge: Edge) {
        val edgeValue = edgeValues[edge]
        val source = edgeValue.source
        val target = edgeValue.target

        successors[source].remove(target, edge)

        if (!directed) {
            if (source != target) {
                successors[target].remove(source, edge)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target].remove(source, edge)
        }

        cleanupEdge(edge)
    }

    private fun cleanupEdge(edge: Edge) {
        val lastEdge = Edge(edgeValues.lastIndex.toLong())
        val lastEdgeValue = edgeValues[lastEdge]
        val lastSource = lastEdgeValue.source
        val lastTarget = lastEdgeValue.target

        // update edge references
        edgeProperties.onEdgeReassigned(lastEdge, edge)
        edgeRefs.onEdgeReassigned(lastEdge, edge)

        if (edge != lastEdge) {
            // update successor and predecessor values
            successors[lastSource].reassign(lastTarget, lastEdge, edge)
            if (!directed) {
                if (lastSource != lastTarget) {
                    successors[lastTarget].reassign(lastSource, lastEdge, edge)
                }
            } else if (_predecessors.isInitialized()) {
                predecessors[lastTarget].reassign(lastSource, lastEdge, edge)
            }
        }

        // shift last edge into the place of removed edge now that all references have been updated
        edgeValues[edge] = lastEdgeValue
        edgeValues.remove(lastEdge)
    }

    override val vertices: MutableIndexedVertexSet =
        object : AbstractMutableVertexIndexedVertexSet(this@AdjacencyListNetwork) {
            override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].vertices
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex].vertices
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(true, vertex, successors[vertex])
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(false, vertex, predecessors[vertex])

    override val edges: MutableIndexedEdgeSet = object : AbstractMutableEdgeIndexedEdgeSet(this@AdjacencyListNetwork) {
        override val size: Int get() = edgeValues.size
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = edgeValues[validateEdge(edge)].source

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = edgeValues[validateEdge(edge)].target

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source.intValue].contains(target)

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
        return vertexProperties.registerProperty(createMutableGraphVertexProperty(this, initializer))
    }

    override fun <T, C : T> createEdgeProperty(
        clazz: Class<C>,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> {
        return edgeProperties.registerProperty(createMutableGraphEdgeProperty(this, initializer))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference =
        vertexRefs.getReference(validateVertex(vertex))

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.getReference(validateEdge(edge))

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val adjacencies: EdgeAdjacencySet
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
                adjacencies.contains(EdgeAdjacency(source, element))
            } else {
                vertex == source && adjacencies.contains(EdgeAdjacency(target, element))
            }
        }

        override fun iterator(): EdgeIterator = adjacencies.iterator().toEdgeIterator()

        override fun foreach(action: EdgeConsumer) =
            adjacencies.foreach { edgeAdjacency -> action.accept(edgeAdjacency.edge) }
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
            private var edgeIt: IntIterator = emptyIntIterator()

            private var vertex = INVALID_VERTEX
            private var edge = INVALID_EDGE

            init {
                increment()
            }

            override fun hasNext(): Boolean = edge != INVALID_EDGE

            override fun next(): EdgeAdjacency {
                if (edge == INVALID_EDGE) throw NoSuchElementException()
                val ea = EdgeAdjacency(vertex, edge)
                increment()
                return ea
            }

            private fun increment() {
                while (!edgeIt.hasNext() && mapIt.hasNext()) {
                    val entry = mapIt.next()
                    vertex = Vertex(entry.key)
                    val edgeId = entry.value
                    edgeIt = if (edgeId < 0) edgeListMap.getValue(edgeId).iterator() else intIteratorOf(edgeId)
                }

                edge = if (edgeIt.hasNext()) Edge(edgeIt.nextInt().toLong()) else INVALID_EDGE
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

        fun reassign(vertex: Vertex, oldEdge: Edge, newEdge: Edge) {
            val v = map[vertex]
            if (v == Int.MIN_VALUE) {
                throw IllegalStateException()
            } else if (v < 0) {
                val edgeIds = edgeListMap.getValue(v)
                val i = edgeIds.indexOf(oldEdge)
                check(i != -1)
                edgeIds[i] = newEdge
            } else {
                check(v == oldEdge.lowBits)
                map[vertex] = newEdge
            }
        }

        fun add(vertex: Vertex, edge: Edge) {
            val v = map[vertex]
            if (v == Int.MIN_VALUE) {
                map[vertex] = edge
            } else {
                val edgeIds: IntArrayList
                if (v < 0) {
                    edgeIds = edgeListMap.getValue(v)
                } else {
                    edgeIds = IntArrayList(2)
                    edgeIds.add(v)
                    edgeListMap[edgeIdNextIndex] = edgeIds
                    map[vertex] = edgeIdNextIndex
                    --edgeIdNextIndex
                }

                edgeIds.add(edge)
            }

            ++size
        }

        fun remove(vertex: Vertex, edge: Edge) {
            val v = map[vertex]
            check(v != Int.MIN_VALUE)
            if (v < 0) {
                val edgeIds = edgeListMap.getValue(v)
                check(edgeIds.remove(edge))
                if (edgeIds.size == 1) {
                    edgeListMap.remove(v)
                    map[vertex] = edgeIds[0]
                }
            } else {
                check(v == edge.lowBits)
                map.remove(vertex)
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

        private operator fun Int2IntHashMap.get(vertex: Vertex) = get(vertex.intValue)
        private operator fun Int2IntHashMap.set(vertex: Vertex, value: Int) = set(vertex.intValue, value)
        private operator fun Int2IntHashMap.set(vertex: Vertex, edge: Edge) = set(vertex.intValue, edge.lowBits)
        private fun Int2IntHashMap.remove(vertex: Vertex) = remove(vertex.intValue)
        private operator fun IntArrayList.set(index: Int, edge: Edge) = set(index, edge.lowBits)
        private fun IntArrayList.add(edge: Edge) = add(edge.lowBits)
        private fun IntArrayList.remove(edge: Edge) = remove(edge.lowBits)
        private fun IntArrayList.indexOf(edge: Edge) = indexOf(edge.lowBits)
    }

    private operator fun ArrayList<AdjacencySet>.get(vertex: Vertex) = get(vertex.intValue)
    private operator fun ArrayList<AdjacencySet>.set(vertex: Vertex, value: AdjacencySet) = set(vertex.intValue, value)
    private fun ArrayList<AdjacencySet>.remove(vertex: Vertex) = removeAt(vertex.intValue)
    private operator fun LongArrayList.get(edge: Edge) = EdgeValue(get(edge.lowBits))
    private operator fun LongArrayList.set(edge: Edge, value: EdgeValue) = set(edge.lowBits, value.longValue)
    private fun LongArrayList.add(edgeValue: EdgeValue) = add(edgeValue.longValue)
    private fun LongArrayList.remove(edge: Edge) = removeAt(edge.lowBits)

    private companion object {
        private val INVALID_VERTEX = Vertex(-1)
        private val INVALID_EDGE = Edge(-1)
    }
}
