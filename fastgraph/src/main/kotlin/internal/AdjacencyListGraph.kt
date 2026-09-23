package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.CanonicalEdgeGraph
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedVertexSet
import io.github.sooniln.fastgraph.MutableCanonicalEdgeSet
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.references.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.compareTo
import io.github.sooniln.fastgraph.edgeIteratorOf
import io.github.sooniln.fastgraph.emptyEdgeIterator
import io.github.sooniln.fastgraph.inc
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.EdgeReferenceManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager
import io.github.sooniln.fastgraph.util.cheapLazy
import io.github.sooniln.fastgraph.util.cheapSynchronizedLazy

internal class AdjacencyListGraph(override val directed: Boolean) : AbstractGraph<CanonicalEdgeSet>(), CanonicalEdgeGraph, MutableGraph {

    private val _predecessors = cheapLazy { check(directed); successors.transpose() }

    private val successors: ArrayList<IntHashSet> = ArrayList()
    private val predecessors: ArrayList<IntHashSet> inline get() = _predecessors.value
    private var edgeCount = 0

    private val vertexListeners = VertexChangeListenerManager()
    private val edgeListeners = EdgeChangeListenerManager()

    private val vertexRefs by cheapSynchronizedLazy { VertexReferenceManager(this) }
    private val edgeRefs by cheapSynchronizedLazy { EdgeReferenceManager(this) }

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in 0..<successors.size) throwIllegalVertex(vertex)
        return vertex
    }

    private fun validateEdge(edge: Edge): CanonicalEdge {
        val edge = CanonicalEdge.from(edge)
        try {
            validateVertex(edge.source)
            validateVertex(edge.target)
        } catch (e: IllegalArgumentException) {
            throwIllegalEdge(edge, e)
        }
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
        edgeListeners.notifyEnsureCapacity(edgeCapacity)
    }

    override fun addVertex(): Vertex = addVertex(0, 0)

    override fun addVertex(outDegreeCapacity: Int, inDegreeCapacity: Int): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(IntHashSet(outDegreeCapacity))
        if (_predecessors.isInitialized()) {
            predecessors.add(IntHashSet(inDegreeCapacity))
        }

        vertexListeners.notifyVertexAdded(vertex)
        return vertex
    }

    override fun removeVertex(vertex: Vertex) {
        validateVertex(vertex)

        // remove outbound edges
        val successorsIt = successors[vertex].iterator()
        while (successorsIt.hasNext()) {
            val target = Vertex(successorsIt.nextInt())
            edgeListeners.notifyEdgeRemoved(canonicalEdge(vertex, target))
            successorsIt.remove()
            if (!directed) {
                if (vertex != target) {
                    check(successors[target].remove(vertex))
                }
            } else if (_predecessors.isInitialized()) {
                check(predecessors[target].remove(vertex))
            }

            --edgeCount
        }

        // remove inbound edges
        if (directed) {
            val predecessorsIt = predecessors[vertex].iterator()
            while (predecessorsIt.hasNext()) {
                val source = Vertex(predecessorsIt.nextInt())
                edgeListeners.notifyEdgeRemoved(canonicalEdge(source, vertex))
                predecessorsIt.remove()
                check(successors[source].remove(vertex))
                --edgeCount
            }
        }

        // handle vertex removal and reference updates
        cleanupVertex(vertex)
    }

    private fun cleanupVertex(vertex: Vertex) {
        // we're going to swap the last vertex into the spot current occupied by the vertex to be removed. this means we
        // need to update all references to last vertex to point to its new location, and then do the swap.
        val lastVertex = Vertex(successors.lastIndex)

        // listeners must be notified while the graph is in a consistent state. moving the last vertex renames all of
        // its edges, so every edge is reported re-assigned before the vertex itself is.
        if (vertex != lastVertex) {
            if (directed) {
                predecessors[lastVertex].foreachVertex { source ->
                    val newSource = if (source == lastVertex) vertex else source
                    edgeListeners.notifyEdgeReassigned(canonicalEdge(source, lastVertex), canonicalEdge(newSource, vertex))
                }
                successors[lastVertex].foreachVertex { target ->
                    // a self-loop was already reported when we went through the predecessors above
                    if (target != lastVertex) {
                        edgeListeners.notifyEdgeReassigned(canonicalEdge(lastVertex, target), canonicalEdge(vertex, target))
                    }
                }
            } else {
                successors[lastVertex].foreachVertex { target ->
                    val newTarget = if (target == lastVertex) vertex else target
                    edgeListeners.notifyEdgeReassigned(canonicalEdge(lastVertex, target), canonicalEdge(vertex, newTarget))
                }
            }

            vertexListeners.notifyVertexReassigned(lastVertex, vertex)
        } else {
            vertexListeners.notifyVertexRemoved(vertex)
        }

        if (vertex != lastVertex) {
            // update edge references
            if (directed) {
                predecessors[lastVertex].foreachVertex { source ->
                    check(successors[source].remove(lastVertex))
                    check(successors[source].add(vertex))
                }

                successors[lastVertex].foreachVertex { newTarget ->
                    // successors has already been corrected, so treat index as lastIndex when necessary
                    val target = if (newTarget == vertex) lastVertex else newTarget
                    check(predecessors[target].remove(lastVertex))
                    check(predecessors[target].add(vertex))
                }
            } else {
                val hasSelfLoop = successors[lastVertex].remove(lastVertex)

                successors[lastVertex].foreachVertex { target ->
                    check(successors[target].remove(lastVertex))
                    check(successors[target].add(vertex))
                }

                if (hasSelfLoop) {
                    successors[lastVertex].add(vertex)
                }
            }

            // shift last vertex into the place of removed vertex now that all references have been updated
            successors[vertex] = successors[lastVertex]
            if (directed) {
                predecessors[vertex.id] = predecessors[lastVertex]
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

        val edge = canonicalEdge(source, target)
        val vertexSuccessors = successors[source]
        if (vertexSuccessors.add(target)) {
            if (!directed) {
                if (source != target) {
                    successors[target].add(source)
                }
            } else if (_predecessors.isInitialized()) {
                predecessors[target].add(source)
            }

            ++edgeCount
        } else {
            throw IllegalArgumentException("${canonicalEdge(source, target)} already exists in graph")
        }

        edgeListeners.notifyEdgeAdded(edge)
        return edge
    }

    override fun removeEdge(edge: Edge) {
        val edge = validateEdge(edge)
        val source = edge.source
        val target = edge.target
        if (!successors[source].contains(target)) throwIllegalEdge(edge)

        edgeListeners.notifyEdgeRemoved(edge.toEdge())

        check(successors[source].remove(target))
        if (!directed) {
            if (source != target) {
                check(successors[target].remove(source))
            }
        } else if (_predecessors.isInitialized()) {
            check(predecessors[target].remove(source))
        }

        --edgeCount
    }

    override val multiEdge: Boolean get() = false

    override val vertices: AbstractMutableIdentityIndexedVertexSet =
        object : AbstractMutableIdentityIndexedVertexSet(this@AdjacencyListGraph) {
            override val size: Int get() = successors.size
        }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].asVertexSet()
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex].asVertexSet()
    override fun getOutgoingEdges(vertex: Vertex): CanonicalEdgeSet = OutgoingEdgeSet(vertex)
    override fun getIncomingEdges(vertex: Vertex): CanonicalEdgeSet = IncomingEdgeSet(vertex)

    override val edges: MutableCanonicalEdgeSet = object : AbstractEdgeSet(), MutableCanonicalEdgeSet {
        override val size: Int get() = edgeCount

        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            val source = edge.source
            return source.id in 0..<successors.size && successors[source].contains(edge.target)
        }

        override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
            private var source = INVALID_VERTEX
            private var successor = emptyIntIterator()
            private var target = INVALID_VERTEX

            private var ready = false
            private var removeSupported = false

            override fun hasNext(): Boolean {
                if (ready) return true

                do {
                    while (!successor.hasNext() && ++source < successors.size) {
                        successor = successors[source].iterator()
                    }
                    if (!successor.hasNext()) return false

                    target = Vertex(successor.nextInt())

                    // don't report the same edge twice in undirected graphs - we only report an edge when we see a
                    // source less than or equal to the target. this works because we know we'll encounter every
                    // undirected edge twice since we're iterating over all vertices.
                } while (!directed && source > target)

                removeSupported = false
                ready = true
                return true
            }

            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                removeSupported = true
                ready = false
                return CanonicalEdge.fromSorted(directed, source, target).toEdge()
            }

            override fun remove() {
                // remove is not supported after hasNext() is invoked - technically we're breaking iterator specs, but
                // there's simply no good way around this.
                check(removeSupported)

                edgeListeners.notifyEdgeRemoved(canonicalEdge(source, target))

                successor.remove()
                if (!directed) {
                    if (source != target) {
                        check(successors[target].remove(source))
                    }
                } else if (_predecessors.isInitialized()) {
                    check(predecessors[target].remove(source))
                }

                --edgeCount
            }
        }
    }

    override fun edgeSource(edge: Edge): Vertex = CanonicalEdge.from(edge).source
    override fun edgeTarget(edge: Edge): Vertex = CanonicalEdge.from(edge).target

    override fun registerVertexChangeListener(listener: VertexChangeListener) = vertexListeners.register(listener)
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) = vertexListeners.unregister(listener)
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) = edgeListeners.register(listener)
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) = edgeListeners.unregister(listener)

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source].contains(target)

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        check(containsEdge(source, target))
        return canonicalEdge(source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): CanonicalEdgeSet = EdgesBetween(source, target)

    override fun createVertexReference(vertex: Vertex): VertexReference =
        vertexRefs.getReference(validateVertex(vertex))

    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.getReference(validateEdge(edge).toEdge())

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

    private inner class OutgoingEdgeSet(private val vertex: Vertex) : AbstractEdgeSet(), CanonicalEdgeSet {
        private val adjacencies = successors[vertex.id]

        override val size: Int get() = adjacencies.size
        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            val source = edge.source
            val target = edge.target

            return if (!directed && target == vertex) {
                adjacencies.contains(source.id)
            } else {
                vertex == source && adjacencies.contains(target.id)
            }
        }
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(vertex, Vertex(it.nextInt()))
        }
    }

    private inner class IncomingEdgeSet(private val vertex: Vertex) : AbstractEdgeSet(), CanonicalEdgeSet {
        init { check(directed) }

        private val adjacencies = predecessors[vertex.id]

        override val size: Int get() = adjacencies.size
        override fun contains(element: Edge): Boolean {
            val edge = CanonicalEdge.from(element)
            return vertex == edge.target && adjacencies.contains(edge.source.id)
        }
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(Vertex(it.nextInt()), vertex)
        }
    }

    private inner class EdgesBetween(
        private val source: Vertex,
        private val target: Vertex
    ) : AbstractEdgeSet(), CanonicalEdgeSet {
        override val size: Int get() = if (containsEdge(source, target)) 1 else 0
        override fun contains(element: Edge): Boolean {
            return element == canonicalEdge(source, target) && containsEdge(source, target)
        }
        override fun iterator(): EdgeIterator {
            return if (containsEdge(source, target)) {
                edgeIteratorOf(canonicalEdge(source, target))
            } else {
                emptyEdgeIterator()
            }
        }
    }

    private fun canonicalEdge(source: Vertex, target: Vertex): Edge {
        return CanonicalEdge.from(directed, source, target).toEdge()
    }

    companion object {
        private val INVALID_VERTEX = Vertex(-1)

        private operator fun ArrayList<IntHashSet>.get(vertex: Vertex) = get(vertex.id)
        private operator fun ArrayList<IntHashSet>.set(vertex: Vertex, value: IntHashSet) = set(vertex.id, value)
        private fun ArrayList<IntHashSet>.remove(vertex: Vertex) = removeAt(vertex.id)
        private fun ArrayList<IntHashSet>.transpose(): ArrayList<IntHashSet> {
            val transposed = ArrayList<IntHashSet>(size)
            repeat(size) { transposed.add(IntHashSet()) }
            for (index in 0..<size) {
                val vertex = Vertex(index)
                get(vertex).forEach { successor -> transposed[successor].add(vertex) }
            }
            return transposed
        }

        private fun IntHashSet.contains(vertex: Vertex) = contains(vertex.id)
        private fun IntHashSet.add(vertex: Vertex) = add(vertex.id)
        private fun IntHashSet.remove(vertex: Vertex) = remove(vertex.id)
        private inline fun IntHashSet.foreachVertex(crossinline action: (Vertex) -> Unit) = forEach { action(Vertex(it)) }

        fun copy(graph: Graph): AdjacencyListGraph {
            require(graph.vertices is IdentityIndexedVertexSet)
            require(graph.edges is CanonicalEdgeSet)

            val copy = AdjacencyListGraph(graph.directed)
            copy.ensureVertexCapacity(graph.vertices.size)
            copy.ensureEdgeCapacity(graph.edges.size)
            for (vertex in graph.vertices) {
                val vertexCopy = copy.addVertex(graph.outDegree(vertex), 0)
                assert(vertexCopy.id == vertex.id)
            }
            for (edge in graph.edges) {
                val edge = CanonicalEdge.from(edge)
                val edgeCopy = copy.addEdge(edge.source, edge.target)
                assert(edgeCopy.id == edge.id)
            }
            return copy
        }
    }
}
