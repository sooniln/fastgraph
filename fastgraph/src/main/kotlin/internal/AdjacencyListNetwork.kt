package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.IntList
import io.github.sooniln.fastcollect.emptyIntIterator
import io.github.sooniln.fastcollect.emptyIntList
import io.github.sooniln.fastcollect.intIteratorOf
import io.github.sooniln.fastcollect.intListOf
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractMutableIndexedEdgeSet
import io.github.sooniln.fastgraph.AbstractMutableIndexedVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeFunction
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
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.indices
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.EdgeReferenceManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager
import io.github.sooniln.fastgraph.vertexSetOf

internal class AdjacencyListNetwork(
    override val directed: Boolean,
    override val multiEdge: Boolean,
) : AbstractGraph(), IndexedVertexGraph, IndexedEdgeGraph, MutableGraph {

    private val _predecessors = lazy {
        check(directed)

        val predecessors = ArrayList<AdjacencySet>(successors.size)
        repeat(successors.size) {
            predecessors.add(AdjacencySet())
        }
        for (index in successors.indices) {
            val vertex = Vertex(index)
            successors[vertex].foreach { edgeAdjacency ->
                predecessors[edgeAdjacency.vertex].add(vertex, edgeAdjacency.edgeId)
            }
        }
        return@lazy predecessors
    }

    private val successors: ArrayList<AdjacencySet> = ArrayList()
    private val predecessors: ArrayList<AdjacencySet> by _predecessors
    private val edgeValues = EdgeValueArrayList()

    private val vertexListeners = VertexChangeListenerManager()
    private val edgeListeners = EdgeChangeListenerManager()

    private val vertexRefs = VertexReferenceManager(this)
    private val edgeRefs = EdgeReferenceManager(this)

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in successors.indices) throwIllegalVertex(vertex)
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        if (edge.edgeId !in edgeValues.indices) throwIllegalEdge(edge)
        return edge
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) {
        successors.ensureCapacity(vertexCapacity)
        if (_predecessors.isInitialized()) {
            predecessors.ensureCapacity(vertexCapacity)
        }
        vertexListeners.notifyEnsureCapacity(vertexCapacity)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) {
        edgeValues.ensureCapacity(edgeCapacity)
        edgeListeners.notifyEnsureCapacity(edgeCapacity)
    }

    override fun addVertex(): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(AdjacencySet())
        if (_predecessors.isInitialized()) {
            predecessors.add(AdjacencySet())
        }

        vertexListeners.notifyVertexAdded(vertex)
        return vertex
    }

    override fun removeVertex(vertex: Vertex) {
        validateVertex(vertex)

        // remove outbound edges
        val outboundAdjacencies = successors[vertex]
        while (!outboundAdjacencies.isEmpty()) {
            removeEdgeInternal(outboundAdjacencies.iterator().next().edgeId)
        }

        // remove inbound edges
        if (directed) {
            val inboundAdjacencies = predecessors[vertex]
            while (!inboundAdjacencies.isEmpty()) {
                removeEdgeInternal(inboundAdjacencies.iterator().next().edgeId)
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
                    edgeValues[adjacency.edgeId] = EdgeValue(true, source, vertex)
                }

                predecessors[lastVertex].vertices.foreach { source ->
                    successors[source].updateVertex(lastVertex, vertex)
                }

                successors[lastVertex].foreach { adjacency ->
                    // successors has already been updated, so no translation necessary
                    edgeValues[adjacency.edgeId] = EdgeValue(true, vertex, adjacency.vertex)
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
                    edgeValues[adjacency.edgeId] = EdgeValue(false, vertex, vertexOther)
                }

                var updateSelfLoop = false
                successors[lastVertex].vertices.foreach { newTarget ->
                    if (newTarget == lastVertex) {
                        // a self-loop on lastVertex is its own entry in successors[lastVertex], so updating it in place
                        // would mutate successors while we're looping through it - defer that update until after
                        updateSelfLoop = true
                    } else {
                        successors[newTarget].updateVertex(lastVertex, vertex)
                    }
                }
                if (updateSelfLoop) {
                    successors[lastVertex].updateVertex(lastVertex, vertex)
                }
            }

            successors[vertex] = successors[lastVertex]
            if (directed) {
                predecessors[vertex] = predecessors[lastVertex]
            }
        }

        // remove vertex
        successors.remove(lastVertex)
        if (directed) {
            predecessors.remove(lastVertex)
        }

        if (vertex != lastVertex) {
            vertexListeners.notifyVertexReassigned(lastVertex, vertex)
        } else {
            vertexListeners.notifyVertexRemoved(vertex)
        }
    }

    override fun addEdge(source: Vertex, target: Vertex): Edge {
        validateVertex(source)
        validateVertex(target)

        val adjacencySet = successors[source]
        require(multiEdge || !adjacencySet.contains(target)) { "$source -> $target already exists in graph" }

        val edgeValue = EdgeValue(directed, source, target)
        val edgeId = edgeValues.add(edgeValue)
        adjacencySet.add(target, edgeId)

        if (!directed) {
            if (source != target) {
                successors[target].add(source, edgeId)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target].add(source, edgeId)
        }

        val edge = canonicalEdge(edgeId)

        edgeListeners.notifyEdgeAdded(edge)
        return edge
    }

    override fun removeEdge(edge: Edge) = removeEdgeInternal(validateEdge(edge).edgeId)

    private fun removeEdgeInternal(edgeId: Int) {
        val edgeValue = edgeValues[edgeId]
        val source = edgeValue.source
        val target = edgeValue.target

        successors[source].remove(target, edgeId)

        if (!directed) {
            if (source != target) {
                successors[target].remove(source, edgeId)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target].remove(source, edgeId)
        }

        cleanupEdge(edgeId)
    }

    private fun cleanupEdge(edgeId: Int) {
        val edge = canonicalEdge(edgeId)
        val lastEdgeId = edgeValues.lastIndex
        val lastEdge = canonicalEdge(lastEdgeId)
        val lastEdgeValue = edgeValues[lastEdgeId]

        if (edgeId != lastEdgeId) {
            val lastSource = lastEdgeValue.source
            val lastTarget = lastEdgeValue.target

            // update successor and predecessor values
            successors[lastSource].reassign(lastTarget, lastEdgeId, edgeId)
            if (!directed) {
                if (lastSource != lastTarget) {
                    successors[lastTarget].reassign(lastSource, lastEdgeId, edgeId)
                }
            } else if (_predecessors.isInitialized()) {
                predecessors[lastTarget].reassign(lastSource, lastEdgeId, edgeId)
            }

            edgeListeners.notifyEdgeReassigned(lastEdge, edge)
        } else {
            edgeListeners.notifyEdgeRemoved(edge)
        }

        // shift last edge into the place of removed edge now that all references have been updated
        edgeValues[edgeId] = lastEdgeValue
        edgeValues.removeAt(lastEdgeId)
    }

    override val vertices: MutableIndexedVertexSet =
        object : AbstractMutableIndexedVertexSet(this@AdjacencyListNetwork) {
            override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].vertices
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex].vertices
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(true, vertex, successors[vertex])
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(false, vertex, predecessors[vertex])

    override val edges: MutableIndexedEdgeSet = object : AbstractMutableIndexedEdgeSet(this@AdjacencyListNetwork) {
        override val size: Int get() = edgeValues.size
        override fun get(index: Int): Edge {
            if (index !in indices) throw IndexOutOfBoundsException()
            return canonicalEdge(index)
        }
        override fun indexOf(element: Edge): Int {
            return if (element.id in indices) element.edgeId else -1
        }
    }

    override fun edgeSource(edge: Edge): Vertex = edgeValues[edge.edgeId].source

    override fun edgeTarget(edge: Edge): Vertex = edgeValues[edge.edgeId].target

    override fun registerVertexChangeListener(listener: VertexChangeListener) { vertexListeners.register(listener) }
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) { vertexListeners.unregister(listener) }
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) { edgeListeners.register(listener) }
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) { edgeListeners.unregister(listener) }

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source.id].contains(target)

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        val adjacenciesIt = successors[source].edgesTo(target).iterator()
        if (!adjacenciesIt.hasNext()) throw NoSuchElementException()
        return canonicalEdge(adjacenciesIt.next().edgeId)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, source, successors[source].edgesTo(target))
    }

    override fun <T> createVertexProperty(
        type: StaticType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = createVertexProperty(this, type, defaultValueFunction)

    override fun <T> createEdgeProperty(
        type: StaticType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> = createEdgeProperty(this, type, defaultValueFunction)

    override fun createVertexReference(vertex: Vertex): VertexReference =
        vertexRefs.getReference(validateVertex(vertex))

    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.getReference(validateEdge(edge))

    override fun trimToSize() {
        successors.trimToSize()
        for (successor in successors) {
            successor.trimToSize()
        }
        if (_predecessors.isInitialized()) {
            predecessors.trimToSize()
            for (predecessor in predecessors) {
                predecessor.trimToSize()
            }
        }
        vertexListeners.notifyTrimToSize()
        edgeListeners.notifyTrimToSize()
        vertexRefs.trimToSize()
        edgeRefs.trimToSize()
    }

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean,
        private val vertex: Vertex,
        private val adjacencies: EdgeAdjacencySet
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
            val v = map.getOrDefault(element.vertex.id, Int.MIN_VALUE)
            return v != Int.MIN_VALUE && if (v < 0) {
                edgeListMap.getValue(v).contains(element.edgeId)
            } else {
                element.edgeId == v
            }
        }

        override fun contains(vertex: Vertex): Boolean {
            return map.containsKey(vertex.id)
        }

        override fun iterator(): EdgeAdjacencyIterator = object : EdgeAdjacencyIterator {
            private val mapIt = map.iterator()
            private var edgeIdIt: IntIterator = emptyIntIterator()

            private var vertex = INVALID_VERTEX
            private var edgeId = -1

            init {
                increment()
            }

            override fun hasNext(): Boolean = edgeId != -1

            override fun next(): EdgeAdjacency {
                if (edgeId == -1) throw NoSuchElementException()
                val ea = EdgeAdjacency(vertex, edgeId)
                increment()
                return ea
            }

            private fun increment() {
                while (!edgeIdIt.hasNext() && mapIt.hasNext()) {
                    val entry = mapIt.next()
                    vertex = Vertex(entry.key)
                    val edgeId = entry.value
                    edgeIdIt = if (edgeId < 0) edgeListMap.getValue(edgeId).iterator() else intIteratorOf(edgeId)
                }

                edgeId = if (edgeIdIt.hasNext()) edgeIdIt.nextInt() else -1
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
            check(!map.containsKey(newVertex.id))
            val oldValue = map.remove(oldVertex.id)
            check(oldValue != Int.MIN_VALUE)
            map[newVertex.id] = oldValue
        }

        fun reassign(vertex: Vertex, oldEdgeId: Int, newEdgeId: Int) {
            val v = map[vertex]
            if (v == Int.MIN_VALUE) {
                throw IllegalStateException()
            } else if (v < 0) {
                val edgeIds = edgeListMap.getValue(v)
                val i = edgeIds.indexOf(oldEdgeId)
                check(i != -1)
                edgeIds[i] = newEdgeId
            } else {
                check(v == oldEdgeId)
                map[vertex] = newEdgeId
            }
        }

        fun add(vertex: Vertex, edgeId: Int) {
            val v = map[vertex]
            if (v == Int.MIN_VALUE) {
                map[vertex] = edgeId
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

                edgeIds.add(edgeId)
            }

            ++size
        }

        fun remove(vertex: Vertex, edgeId: Int) {
            val v = map[vertex]
            check(v != Int.MIN_VALUE)
            if (v < 0) {
                val edgeIds = edgeListMap.getValue(v)
                check(edgeIds.remove(edgeId))
                if (edgeIds.size == 1) {
                    edgeListMap.remove(v)
                    map[vertex] = edgeIds[0]
                }
            } else {
                check(v == edgeId)
                map.remove(vertex)
            }

            --size
        }

        fun edgesTo(target: Vertex): EdgeAdjacencySet = object : EdgeAdjacencySet {
            private val edgeIds: IntList

            init {
                val v = map[target.id]
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
                edgeIds.foreach { edgeId -> action.accept(EdgeAdjacency(target, edgeId)) }
            }

            override fun toString(): String = iterator().asSequence().joinToString(", ", "[", "]")
        }

        fun trimToSize() {
            map.trimToSize()
            edgeListMap.trimToSize()
            edgeListMap.foreach { _, v -> v.trimToSize() }
        }

        override fun toString(): String = iterator().asSequence().joinToString(", ", "[", "]")

        private operator fun Int2IntHashMap.get(vertex: Vertex) = get(vertex.id)
        private operator fun Int2IntHashMap.set(vertex: Vertex, value: Int) = set(vertex.id, value)
        private fun Int2IntHashMap.remove(vertex: Vertex) = remove(vertex.id)
    }

    private operator fun ArrayList<AdjacencySet>.get(vertex: Vertex) = get(vertex.id)
    private operator fun ArrayList<AdjacencySet>.set(vertex: Vertex, value: AdjacencySet) = set(vertex.id, value)
    private fun ArrayList<AdjacencySet>.remove(vertex: Vertex) = removeAt(vertex.id)

    private fun canonicalEdge(edgeId: Int): Edge = Edge(edgeId.toLong())
    private val Edge.edgeId: Int inline get() = lowBits

    private companion object {
        private val INVALID_VERTEX = Vertex(-1)
    }
}
