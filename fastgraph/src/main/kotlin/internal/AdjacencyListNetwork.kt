package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeGraph
import io.github.sooniln.fastgraph.IdentityIndexedVertexGraph
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.EdgeReferenceManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager
import io.github.sooniln.fastgraph.util.cheapLazy
import io.github.sooniln.fastgraph.vertexSetOf

internal class AdjacencyListNetwork(
    override val directed: Boolean,
    override val multiEdge: Boolean,
) : AbstractGraph(), IdentityIndexedVertexGraph, IdentityIndexedEdgeGraph, MutableGraph {

    private val _predecessors = cheapLazy { check(directed); successors.transpose() }

    private val successors: ArrayList<AdjacencySet> = ArrayList()
    private val predecessors: ArrayList<AdjacencySet> inline get() = _predecessors.value
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
        val e = IdentityIndexedEdge(Math.toIntExact(edge.id))
        if (e.id !in 0..<edgeValues.size) throwIllegalEdge(edge)
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

    override fun addVertex(): Vertex = addVertex(0, 0)

    override fun addVertex(outDegreeCapacity: Int, inDegreeCapacity: Int): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(AdjacencySet(outDegreeCapacity))
        if (_predecessors.isInitialized()) {
            predecessors.add(AdjacencySet(inDegreeCapacity))
        }

        vertexListeners.notifyVertexAdded(vertex)
        return vertex
    }

    override fun removeVertex(vertex: Vertex) {
        validateVertex(vertex)

        // remove outbound edges
        val outboundAdjacencies = successors[vertex]
        while (!outboundAdjacencies.isEmpty()) {
            removeEdgeInternal(outboundAdjacencies.edgeIterator().next())
        }

        // remove inbound edges
        if (directed) {
            val inboundAdjacencies = predecessors[vertex]
            while (!inboundAdjacencies.isEmpty()) {
                removeEdgeInternal(inboundAdjacencies.edgeIterator().next())
            }
        }

        // notify listeners before removing vertex so that the graph is still consistent
        val lastVertex = Vertex(successors.lastIndex)
        if (vertex != lastVertex) {
            vertexListeners.notifyVertexReassigned(lastVertex, vertex)
        } else {
            vertexListeners.notifyVertexRemoved(vertex)
        }

        cleanupVertex(vertex, lastVertex)
    }

    private fun cleanupVertex(vertex: Vertex, lastVertex: Vertex) {
        // we're going to swap the last vertex into the spot current occupied by the vertex to be removed. this means we
        // need to update all references to last vertex to point to its new location, and then do the swap.
        if (vertex != lastVertex) {
            // update edge adjacencies
            if (directed) {
                predecessors[lastVertex].foreachAdjacency { adjacencyVertex, edgeId ->
                    // predecessors has not been updated yet, so translate vertices if necessary
                    val source = if (adjacencyVertex == lastVertex) vertex else adjacencyVertex
                    edgeValues[edgeId] = EdgeValue(true, source, vertex)
                }

                for (source in predecessors[lastVertex].vertices) {
                    successors[source].updateVertex(lastVertex, vertex)
                }

                successors[lastVertex].foreachAdjacency { adjacencyVertex, edgeId ->
                    // successors has already been updated, so no translation necessary
                    edgeValues[edgeId] = EdgeValue(true, vertex, adjacencyVertex)
                }

                for (newTarget in successors[lastVertex].vertices) {
                    // successors has already been updated, so treat index as lastIndex when necessary
                    val target = if (newTarget == vertex) lastVertex else newTarget
                    predecessors[target].updateVertex(lastVertex, vertex)
                }
            } else {
                successors[lastVertex].foreachAdjacency { adjacencyVertex, edgeId ->
                    // successors has already been updated, so no translation necessary
                    val vertexOther = if (adjacencyVertex == lastVertex) vertex else adjacencyVertex
                    edgeValues[edgeId] = EdgeValue(false, vertex, vertexOther)
                }

                var updateSelfLoop = false
                for (newTarget in successors[lastVertex].vertices) {
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

    override fun removeEdge(edge: Edge) = removeEdgeInternal(validateEdge(edge))

    private fun removeEdgeInternal(edge: Edge) {
        val edgeId = IdentityIndexedEdge.from(edge).id
        val edgeValue = edgeValues[edgeId]
        val source = edgeValue.source
        val target = edgeValue.target

        // listeners are notified first so that the graph is still consistent
        val lastEdgeId = edgeValues.lastIndex
        if (edgeId != lastEdgeId) {
            edgeListeners.notifyEdgeReassigned(canonicalEdge(lastEdgeId), edge)
        } else {
            edgeListeners.notifyEdgeRemoved(edge)
        }

        successors[source].remove(target, edgeId)

        if (!directed) {
            if (source != target) {
                successors[target].remove(source, edgeId)
            }
        } else if (_predecessors.isInitialized()) {
            predecessors[target].remove(source, edgeId)
        }

        cleanupEdge(edgeId, lastEdgeId)
    }

    private fun cleanupEdge(edgeId: Int, lastEdgeId: Int) {
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
        }

        // shift last edge into the place of removed edge now that all references have been updated
        edgeValues[edgeId] = lastEdgeValue
        edgeValues.removeAt(lastEdgeId)
    }

    override val vertices: AbstractMutableIdentityIndexedVertexSet =
        object : AbstractMutableIdentityIndexedVertexSet(this@AdjacencyListNetwork) {
            override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].vertices
    override fun getSuccessor(vertex: Vertex): Vertex = successors[vertex].vertex
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex].vertices
    override fun getPredecessor(vertex: Vertex): Vertex = predecessors[vertex].vertex
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(true, vertex, successors[vertex])
    override fun getOutgoingEdge(vertex: Vertex): Edge = successors[vertex].edge
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(false, vertex, predecessors[vertex])
    override fun getIncomingEdge(vertex: Vertex): Edge = predecessors[vertex].edge

    override val edges: AbstractMutableIdentityIndexedEdgeSet = object : AbstractMutableIdentityIndexedEdgeSet(this@AdjacencyListNetwork) {
        override val size: Int get() = edgeValues.size
    }

    override fun edgeSource(edge: IdentityIndexedEdge): Vertex = edgeValues[edge.id].source
    override fun edgeTarget(edge: IdentityIndexedEdge): Vertex = edgeValues[edge.id].target

    override fun registerVertexChangeListener(listener: VertexChangeListener) = vertexListeners.register(listener)
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) = vertexListeners.unregister(listener)
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) = edgeListeners.register(listener)
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) = edgeListeners.unregister(listener)

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source.id].contains(target)

    override fun getEdge(source: Vertex, target: Vertex): Edge = successors[source].edgeTo(target)

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return IncidentEdgeSet(true, source, successors[source].edgesTo(target))
    }

    override fun createVertexReference(vertex: Vertex): VertexReference =
        vertexRefs.getReference(validateVertex(vertex))

    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.getReference(validateEdge(edge))
    override fun createEdgeReference(edge: IdentityIndexedEdge): EdgeReference = createEdgeReference(edge.toEdge())

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
            if (element.id !in 0..<edgeValues.size) return false
            val edge = IdentityIndexedEdge.from(element)

            val target: Vertex
            val source: Vertex
            if (outgoing) {
                source = edgeSource(edge)
                target = edgeTarget(edge)
            } else {
                source = edgeTarget(edge)
                target = edgeSource(edge)
            }

            return if (!directed && target == vertex) {
                adjacencies.contains(EdgeAdjacency(source, edge.id))
            } else {
                vertex == source && adjacencies.contains(EdgeAdjacency(target, edge.id))
            }
        }

        override fun iterator(): EdgeIterator = adjacencies.edgeIterator()
    }

    private class AdjacencySet(degreeHint: Int = 0) : EdgeAdjacencySet {

        // map of vertices to edges
        // if a vertex is associated with only a single edge, the value in this map is the edge id
        // if a vertex is associated with multiple edges, the negated value in this map is the key into edgeListMap
        private val map = Int2IntHashMap(degreeHint, Int.MIN_VALUE)

        // TODO: performance implications of changing this to a set?
        private val edgeListMap = Int2AnyHashMap<IntArrayList>()

        // tracks the next available key for edgeListMap - always decrements
        private var edgeIdNextIndex = -1

        override var size = 0
            private set

        override val vertices: VertexSet get() = map.keys.asVertexSet()

        val vertex: Vertex get() {
            check(map.size == 1)
            map.forEach { (vertex, _) ->
                return Vertex(vertex)
            }
            throw IllegalStateException()
        }

        val edge: Edge get() {
            check(map.size == 1)
            map.forEach { (_, v) ->
                if (v >= 0) return canonicalEdge(v)
                throw IllegalStateException()
            }
            throw IllegalStateException()
        }

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

        override fun edgeIterator(): EdgeIterator = object : EdgeIterator {
            private val mapIt = map.iterator()
            private var edgeIdIt: IntIterator = emptyIntIterator()

            private var edgeId = -1

            init {
                increment()
            }

            override fun hasNext(): Boolean = edgeId != -1

            override fun next(): Edge {
                if (edgeId == -1) throw NoSuchElementException()
                val ea = canonicalEdge(edgeId)
                increment()
                return ea
            }

            private fun increment() {
                while (!edgeIdIt.hasNext() && mapIt.hasNext()) {
                    val entry = mapIt.next()
                    val edgeId = entry.value
                    edgeIdIt = if (edgeId < 0) edgeListMap.getValue(edgeId).iterator() else intIteratorOf(edgeId)
                }

                edgeId = if (edgeIdIt.hasNext()) edgeIdIt.nextInt() else -1
            }
        }

        inline fun foreachAdjacency(crossinline action: (Vertex, Int) -> Unit) {
            map.forEach { (vertexId, edgeId) ->
                val vertex = Vertex(vertexId)
                if (edgeId < 0) {
                    edgeListMap.getValue(edgeId).forEach { edgeId -> action(vertex, edgeId) }
                } else {
                    action(vertex, edgeId)
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
            override val size: Int get() {
                val v = map[target.id]
                return if (v == Int.MIN_VALUE) {
                    0
                } else if (v < 0) {
                    edgeListMap.getValue(v).size
                } else {
                    1
                }
            }

            override fun contains(element: EdgeAdjacency): Boolean {
                if (element.vertex != target) return false
                val v = map[target.id]
                return v != Int.MIN_VALUE && if (v < 0) {
                    edgeListMap.getValue(v).contains(element.edgeId)
                } else {
                    v == element.edgeId
                }
            }

            override fun contains(vertex: Vertex): Boolean {
                return vertex == target
            }

            override val vertices: VertexSet get() = vertexSetOf(target)

            override fun edgeIterator(): EdgeIterator = object : EdgeIterator {
                private val iterator: IntIterator
                init {
                    val v = map[target.id]
                    iterator = if (v == Int.MIN_VALUE) {
                        emptyIntIterator()
                    } else if (v < 0) {
                        edgeListMap.getValue(v).iterator()
                    } else {
                        intIteratorOf(v)
                    }
                }
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun next(): Edge = canonicalEdge(iterator.nextInt())
            }
        }

        fun edgeTo(target: Vertex): Edge {
            val v = map[target.id]
            if (v < 0) throw IllegalStateException()
            return canonicalEdge(v)
        }

        fun trimToSize() {
            map.trimToSize()
            edgeListMap.trimToSize()
            edgeListMap.forEach { (_, edgeList) -> edgeList.trimToSize() }
        }

        private operator fun Int2IntHashMap.get(vertex: Vertex) = get(vertex.id)
        private operator fun Int2IntHashMap.set(vertex: Vertex, value: Int) = set(vertex.id, value)
        private fun Int2IntHashMap.remove(vertex: Vertex) = remove(vertex.id)
    }

    private companion object {
        private operator fun ArrayList<AdjacencySet>.get(vertex: Vertex) = get(vertex.id)
        private operator fun ArrayList<AdjacencySet>.set(vertex: Vertex, value: AdjacencySet) = set(vertex.id, value)
        private fun ArrayList<AdjacencySet>.remove(vertex: Vertex) = removeAt(vertex.id)
        private fun ArrayList<AdjacencySet>.transpose(): ArrayList<AdjacencySet> {
            val transposed = ArrayList<AdjacencySet>(size)
            repeat(size) { transposed.add(AdjacencySet()) }
            for (index in 0..<size) {
                val vertex = Vertex(index)
                get(vertex).foreachAdjacency { adjacentVertex, edgeId ->
                    transposed[adjacentVertex].add(vertex, edgeId)
                }
            }
            return transposed
        }

        private fun canonicalEdge(edgeId: Int): Edge = Edge(edgeId.toLong())
    }
}
