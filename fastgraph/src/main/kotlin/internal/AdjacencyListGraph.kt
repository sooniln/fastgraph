package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.IntHashSet
import io.github.sooniln.fastcollect.emptyMutableIntIterator
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractMutableIndexedVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableEdgeSet
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableIndexedVertexSet
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.TypeReference
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.compareTo
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.inc
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.EdgeReferenceManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager
import kotlin.math.max
import kotlin.math.min

internal class AdjacencyListGraph(override val directed: Boolean) : AbstractGraph(), IndexedVertexGraph, MutableGraph {

    private val _predecessors = lazy {
        check(directed)

        val predecessors = ArrayList<IntHashSet>(successors.size)
        repeat(successors.size) {
            predecessors.add(IntHashSet())
        }
        for (index in successors.indices) {
            val vertex = Vertex(index)
            successors[vertex].foreach { successor -> predecessors[successor].add(vertex) }
        }
        return@lazy predecessors
    }

    private val successors: ArrayList<IntHashSet> = ArrayList()
    private val predecessors: ArrayList<IntHashSet> by _predecessors
    private var edgeCount = 0

    private val vertexListeners = VertexChangeListenerManager()
    private val edgeListeners = EdgeChangeListenerManager()

    private val vertexRefs = VertexReferenceManager(this)
    private val edgeRefs = EdgeReferenceManager(this)

    override val multiEdge: Boolean
        get() = false

    override fun validateVertex(vertex: Vertex): Vertex {
        if (vertex.id !in successors.indices) throwIllegalVertex(vertex)
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

    override fun addVertex(): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(IntHashSet())
        if (_predecessors.isInitialized()) {
            predecessors.add(IntHashSet())
        }

        vertexListeners.notifyVertexAdded(vertex)
        return vertex
    }

    override fun removeVertex(vertex: Vertex) {
        validateVertex(vertex)

        // remove outbound edges
        val successorsIt = successors[vertex].iterator()
        while (successorsIt.hasNext()) {
            val target = Vertex(successorsIt.next())
            successorsIt.remove()
            if (!directed) {
                if (vertex != target) {
                    check(successors[target].remove(vertex))
                }
            } else if (_predecessors.isInitialized()) {
                check(predecessors[target].remove(vertex))
            }

            cleanupEdge(canonicalEdge(vertex, target))
        }

        // remove inbound edges
        if (directed) {
            val predecessorsIt = predecessors[vertex].iterator()
            while (predecessorsIt.hasNext()) {
                val source = Vertex(predecessorsIt.next())
                predecessorsIt.remove()
                check(successors[source].remove(vertex))
                cleanupEdge(canonicalEdge(source, vertex))
            }
        }

        // handle vertex removal and reference updates
        cleanupVertex(vertex)
    }

    private fun cleanupVertex(vertex: Vertex) {
        // we're going to swap the last vertex into the spot current occupied by the vertex to be removed. this means we
        // need to update all references to last vertex to point to its new location, and then do the swap.
        val lastVertex = Vertex(successors.lastIndex)

        if (vertex != lastVertex) {
            // update edge references
            if (directed) {
                predecessors[lastVertex].foreachVertex { source ->
                    // predecessors hasn't been corrected yet, so treat lastIndex as index when necessary
                    val newSource = if (source == lastVertex) vertex else source

                    val oldEdge = canonicalEdge(source, lastVertex)
                    val newEdge = canonicalEdge(newSource, vertex)
                    edgeListeners.notifyEdgeReassigned(oldEdge, newEdge)

                    check(successors[source].remove(lastVertex))
                    check(successors[source].add(vertex))
                }

                successors[lastVertex].foreachVertex { newTarget ->
                    // successors has already been corrected, so treat index as lastIndex when necessary
                    val target = if (newTarget == vertex) lastVertex else newTarget

                    // if this is a self-loop, then it was already swapped and removed when we went through the
                    // predecessors above, and swapping and removing again would lose info, so only swap and remove for
                    // non-self-loops
                    if (vertex != newTarget) {
                        val oldEdge = canonicalEdge(lastVertex, target)
                        val newEdge = canonicalEdge(vertex, newTarget)
                        edgeListeners.notifyEdgeReassigned(oldEdge, newEdge)
                    }

                    check(predecessors[target].remove(lastVertex))
                    check(predecessors[target].add(vertex))
                }
            } else {
                successors[lastVertex].foreachVertex { target ->
                    // successors hasn't been corrected yet, so treat lastIndex as index when necessary
                    val newTarget = if (target == lastVertex) vertex else target

                    val oldEdge = canonicalEdge(lastVertex, target)
                    val newEdge = canonicalEdge(vertex, newTarget)
                    edgeListeners.notifyEdgeReassigned(oldEdge, newEdge)

                    check(successors[target].remove(lastVertex))
                    check(successors[target].add(vertex))
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

        if (vertex != lastVertex) {
            vertexListeners.notifyVertexReassigned(lastVertex, vertex)
        } else {
            vertexListeners.notifyVertexRemoved(vertex)
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
        validateEdge(edge)

        val source = edgeSource(edge)
        val target = edgeTarget(edge)

        check(successors[source].remove(target))
        if (!directed) {
            if (source != target) {
                check(successors[target].remove(source))
            }
        } else if (_predecessors.isInitialized()) {
            check(predecessors[target].remove(source))
        }

        cleanupEdge(edge)
    }

    private fun cleanupEdge(edge: Edge) {
        edgeListeners.notifyEdgeRemoved(edge)
        --edgeCount
    }

    override val vertices: MutableIndexedVertexSet =
        object : AbstractMutableIndexedVertexSet(this@AdjacencyListGraph) {
            override val size: Int get() = successors.size
    }

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex].asVertexSet()
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex].asVertexSet()
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = OutgoingEdgeSet(vertex)
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncomingEdgeSet(vertex)

    override val edges: MutableEdgeSet = object : MutableEdgeSet, AbstractEdgeSet() {
        override val size: Int get() = edgeCount

        override fun contains(element: Edge): Boolean {
            return hasEdge(edgeSource(element), edgeTarget(element))
        }

        override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
            private var source = INVALID_VERTEX
            private var successor = emptyMutableIntIterator()
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
                return canonicalSortedEdge(source, target)
            }

            override fun remove() {
                // remove is not supported after hasNext() is invoked - technically we're breaking iterator specs, but
                // there's simply no good way around this.
                check(removeSupported)

                successor.remove()
                if (!directed) {
                    if (source != target) {
                        check(successors[target].remove(source))
                    }
                } else if (_predecessors.isInitialized()) {
                    check(predecessors[target].remove(source))
                }

                cleanupEdge(canonicalEdge(source, target))
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

    override fun edgeSource(edge: Edge): Vertex = Vertex(edge.highBits)

    override fun edgeTarget(edge: Edge): Vertex = Vertex(edge.lowBits)

    override fun registerVertexChangeListener(listener: VertexChangeListener) { vertexListeners.register(listener) }
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) { vertexListeners.unregister(listener) }
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) { edgeListeners.register(listener) }
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) { edgeListeners.unregister(listener) }

    override fun containsEdge(source: Vertex, target: Vertex): Boolean = successors[source].contains(target)

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        if (!containsEdge(source, target)) throw NoSuchElementException()
        return canonicalEdge(source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return if (!containsEdge(source, target)) emptyEdgeSet() else edgeSetOf(canonicalEdge(source, target))
    }

    override fun <T> createVertexProperty(
        type: TypeReference<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = createVertexProperty(this, type, defaultValueFunction)

    override fun <T> createEdgeProperty(
        type: TypeReference<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> = createEdgeProperty(this, type, defaultValueFunction)

    override fun createVertexReference(vertex: Vertex): VertexReference =
        vertexRefs.getReference(validateVertex(vertex))

    override fun createEdgeReference(edge: Edge): EdgeReference = edgeRefs.getReference(validateEdge(edge))

    private inner class OutgoingEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        private val adjacencies = successors[vertex.id]

        override val size: Int get() = adjacencies.size

        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeSource(element)
            val target = edgeTarget(element)

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

        override fun foreach(action: EdgeConsumer) {
            adjacencies.foreachVertex { adjacent -> action.accept(canonicalEdge(vertex, adjacent)) }
        }
    }

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

    private inner class IncomingEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        private val adjacencies = predecessors[vertex.id]

        override val size: Int get() = adjacencies.size

        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeTarget(element)
            val target = edgeSource(element)

            return if (!directed && target == vertex) {
                adjacencies.contains(source.id)
            } else {
                vertex == source && adjacencies.contains(target.id)
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(Vertex(it.nextInt()), vertex)
        }

        override fun foreach(action: EdgeConsumer) {
            adjacencies.foreachVertex { adjacent -> action.accept(canonicalEdge(adjacent, vertex)) }
        }
    }

    private operator fun ArrayList<IntHashSet>.get(vertex: Vertex) = get(vertex.id)
    private operator fun ArrayList<IntHashSet>.set(vertex: Vertex, value: IntHashSet) = set(vertex.id, value)
    private fun ArrayList<IntHashSet>.remove(vertex: Vertex) = removeAt(vertex.id)
    private fun IntHashSet.contains(vertex: Vertex) = contains(vertex.id)
    private fun IntHashSet.add(vertex: Vertex) = add(vertex.id)
    private fun IntHashSet.remove(vertex: Vertex) = remove(vertex.id)
    private inline fun IntHashSet.foreachVertex(crossinline action: (Vertex) -> Unit) = foreach { action(Vertex(it)) }

    private fun canonicalEdge(source: Vertex, target: Vertex): Edge {
        return if (!directed) {
            Edge(highBits = min(source.id, target.id), lowBits = max(source.id, target.id))
        } else {
            Edge(highBits = source.id, lowBits = target.id)
        }
    }

    // only use if you know directed || source <= target
    private fun canonicalSortedEdge(source: Vertex, target: Vertex): Edge {
        assert(directed || source <= target)
        return Edge(highBits = source.id, lowBits = target.id)
    }

    private companion object {
        private val INVALID_VERTEX = Vertex(-1)
    }
}
