package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.ints.IntHashSet
import io.github.sooniln.fastcollect.ints.emptyMutableIntIterator
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableEdgeSet
import io.github.sooniln.fastgraph.MutableIndexedVertexGraph
import io.github.sooniln.fastgraph.MutableIndexedVertexSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import kotlin.math.max
import kotlin.math.min

internal class AdjacencyListGraph(directed: Boolean) : MutableIndexedVertexGraph,
    VertexIndexedVertexGraph, AbstractGraph(directed) {

    private val _predecessors = lazy {
        check(directed)

        val predecessors = ArrayList<IntHashSet>(successors.size)
        repeat(successors.size) {
            predecessors.add(IntHashSet())
        }
        for (vertex in successors.indices) {
            successors[vertex].foreach { successor -> predecessors[successor].add(vertex) }
        }
        return@lazy predecessors
    }

    private val successors: ArrayList<IntHashSet> = ArrayList()
    private val predecessors: ArrayList<IntHashSet> by _predecessors
    private var edgeCount = 0

    private val vertexRefs = VertexReferenceHolder()
    private val edgeRefs = LongEdgeReferenceHolder()

    private val vertexProperties = VertexPropertiesHolder()
    private val edgeProperties = EdgePropertiesHolder()

    override fun ensureVertexCapacity(vertexCapacity: Int) {
        successors.ensureCapacity(vertexCapacity)
        if (_predecessors.isInitialized()) {
            predecessors.ensureCapacity(vertexCapacity)
        }
        vertexProperties.ensureCapacity(vertexCapacity)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) {
        edgeProperties.ensureCapacity(edgeCapacity)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(): Vertex {
        val vertex = Vertex(successors.size)
        successors.add(IntHashSet())
        if (_predecessors.isInitialized()) {
            predecessors.add(IntHashSet())
        }

        return vertex
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeVertex")
    override fun removeVertex(vertex: Vertex) {
        // remove outbound edges
        val outIt = successors[validateVertex(vertex).intValue].iterator()
        while (outIt.hasNext()) {
            val targetVertexId = outIt.nextInt()

            outIt.remove()
            if (!directed) {
                if (vertex.intValue != targetVertexId) {
                    check(successors[targetVertexId].remove(vertex.intValue))
                }
            } else if (_predecessors.isInitialized()) {
                check(predecessors[targetVertexId].remove(vertex.intValue))
            }

            cleanupEdgeInternal(vertex.intValue, targetVertexId)
        }

        // remove inbound edges
        if (directed) {
            val inIt = predecessors[vertex.intValue].iterator()
            while (inIt.hasNext()) {
                val sourceVertexId = inIt.nextInt()

                inIt.remove()
                check(successors[sourceVertexId].remove(vertex.intValue))

                cleanupEdgeInternal(sourceVertexId, vertex.intValue)
            }
        }

        // handle vertex removal and reference updates
        cleanupVertex(vertex.intValue)
    }

    private fun cleanupVertex(index: Int) {
        // we're going to swap the last vertex into the spot current occupied by the vertex to be removed. this means we
        // need to update all references to last vertex to point to its new location, and then do the swap.
        val lastIndex = successors.lastIndex

        if (index != lastIndex) {
            // update edge references
            if (directed) {
                predecessors[lastIndex].foreach { source ->
                    // predecessors hasn't been corrected yet, so treat lastIndex as index when necessary
                    val newSource = if (source == lastIndex) index else source

                    val oldEdge = canonicalEdge(true, source, lastIndex)
                    val newEdge = canonicalEdge(true, newSource, index)
                    edgeProperties.swapAndRemove(oldEdge, newEdge)
                    edgeRefs.swapAndRemove(oldEdge, newEdge)

                    check(successors[source].remove(lastIndex))
                    check(successors[source].add(index))
                }

                successors[lastIndex].foreach { newTarget ->
                    // successors has already been corrected, so treat index as lastIndex when necessary
                    val target = if (newTarget == index) lastIndex else newTarget

                    // if this is a self-loop, then it was already swapped and removed when we went through the
                    // predecessors above, and swapping and removing again would lose info, so only swap and remove for
                    // non-self-loops
                    if (index != newTarget) {
                        val oldEdge = canonicalEdge(true, lastIndex, target)
                        val newEdge = canonicalEdge(true, index, newTarget)
                        edgeProperties.swapAndRemove(oldEdge, newEdge)
                        edgeRefs.swapAndRemove(oldEdge, newEdge)
                    }

                    check(predecessors[target].remove(lastIndex))
                    check(predecessors[target].add(index))
                }
            } else {
                successors[lastIndex].foreach { target ->
                    // successors hasn't been corrected yet, so treat lastIndex as index when necessary
                    val newTarget = if (target == lastIndex) index else target

                    val oldEdge = canonicalEdge(false, lastIndex, target)
                    val newEdge = canonicalEdge(false, index, newTarget)
                    edgeProperties.swapAndRemove(oldEdge, newEdge)
                    edgeRefs.swapAndRemove(oldEdge, newEdge)

                    check(successors[target].remove(lastIndex))
                    check(successors[target].add(index))
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
        val vertexSuccessors = successors[validateVertex(source).intValue]
        val edge = canonicalEdge(directed, source, validateVertex(target))
        if (vertexSuccessors.add(target.intValue)) {
            if (!directed) {
                if (source != target) {
                    successors[target.intValue].add(source.intValue)
                }
            } else if (_predecessors.isInitialized()) {
                predecessors[target.intValue].add(source.intValue)
            }

            ++edgeCount
        } else {
            throw IllegalArgumentException("$source -> $target already exists in graph")
        }

        return edge
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeEdge")
    override fun removeEdge(edge: Edge) {
        removeEdgeInternal(
            validateVertex(edgeSource(validateEdge(edge))).intValue,
            validateVertex(edgeTarget(edge)).intValue
        )
    }

    private fun removeEdgeInternal(sourceVertexId: Int, targetVertexId: Int) {
        check(successors[sourceVertexId].remove(targetVertexId))
        if (!directed) {
            if (sourceVertexId != targetVertexId) {
                check(successors[targetVertexId].remove(sourceVertexId))
            }
        } else if (_predecessors.isInitialized()) {
            check(predecessors[targetVertexId].remove(sourceVertexId))
        }

        cleanupEdgeInternal(sourceVertexId, targetVertexId)
    }

    private fun cleanupEdgeInternal(sourceVertexId: Int, targetVertexId: Int) {
        val edge = canonicalEdge(directed, sourceVertexId, targetVertexId)
        edgeProperties.swapAndRemove(edge)
        edgeRefs.swapAndRemove(edge)
        --edgeCount
    }

    override val multiEdge: Boolean
        get() = false

    override fun validateVertex(vertex: Vertex): Vertex {
        require(vertex.intValue in successors.indices) { "vertex $vertex not found in graph" }
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        try {
            validateVertex(edgeSource(edge))
            validateVertex(edgeTarget(edge))
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$edge (${edgeSource(edge)} -> ${edgeTarget(edge)}) not found in graph", e)
        }
        return edge
    }

    override val vertices: MutableIndexedVertexSet = MutableVertexIndexedVertexSet(this@AdjacencyListGraph)

    override fun getOutDegree(vertex: Vertex): Int = successors[vertex.intValue].size
    override fun getInDegree(vertex: Vertex): Int = predecessors[vertex.intValue].size
    override fun getSuccessors(vertex: Vertex): VertexSet = successors[vertex.intValue].asVertexSet()
    override fun getPredecessors(vertex: Vertex): VertexSet = predecessors[vertex.intValue].asVertexSet()
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = OutgoingEdgeSet(vertex)
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = IncomingEdgeSet(vertex)

    override val edges: MutableEdgeSet = object : MutableEdgeSet, AbstractEdgeSet() {
        override val size: Int get() = edgeCount

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            return hasEdge(edgeSource(element), edgeTarget(element))
        }

        override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
            private var ready = false
            private var source = 0
            private var targetIt = if (successors.isEmpty()) emptyMutableIntIterator() else successors[0].iterator()
            private var edge: Edge = Edge(0L)

            override fun hasNext(): Boolean {
                if (!ready) increment()
                return source < successors.size
            }

            override fun next(): Edge {
                if (source >= successors.size) throw NoSuchElementException()
                if (!ready) increment()
                ready = false
                return edge
            }

            private fun increment() {
                ready = true

                var target: Int
                do {
                    while (!targetIt.hasNext()) {
                        if (++source >= successors.size) {
                            return
                        }
                        targetIt = successors[source].iterator()
                    }


                    target = targetIt.nextInt()

                    // don't report the same edge twice in undirected graphs - we only reported an edge when we see it
                    // in the configuration where the source value is less than or equal to the target value. this works
                    // because we know we'll encounter every undirected edge twice since we're iterating over all
                    // vertices.
                } while (!directed && source > target)

                edge = canonicalSortedEdge(source, target)
            }

            override fun remove() {
                targetIt.remove()

                val target = edgeTarget(edge).intValue
                if (!directed) {
                    if (source != target) {
                        check(successors[target].remove(source))
                    }
                } else if (_predecessors.isInitialized()) {
                    check(predecessors[target].remove(source))
                }

                cleanupEdgeInternal(source, target)
            }
        }

        override fun foreach(action: EdgeConsumer) {
            for (source in successors.indices) {
                successors[source].foreach { target ->
                    // don't report the same edge twice in undirected graphs - we only reported an edge when we see it
                    // in the configuration where the source value is less than or equal to the target value. this works
                    // because we know we'll encounter every undirected edge twice since we're iterating over all
                    // vertices.
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

    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return successors[source.intValue].contains(target.intValue)
    }

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        if (!containsEdge(source, target)) throw NoSuchElementException()
        return canonicalEdge(directed, source, target)
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return if (!containsEdge(source, target)) emptyEdgeSet() else edgeSetOf(canonicalEdge(directed, source, target))
    }

    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        initializer: VertexInitializer<T>
    ): MutableVertexProperty<T> {
        val property = mutableArrayListVertexProperty(this, clazz, initializer)
        property.ensureCapacity(vertices.size)
        vertexProperties.addProperty(property)
        return property
    }

    override fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: EdgeInitializer<T>): MutableEdgeProperty<T> {
        val property = mutableMapEdgeProperty(this, clazz, initializer)
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

    private inner class OutgoingEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        private val adjacencies = successors[vertex.intValue]

        override val size: Int get() = adjacencies.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeSource(element)
            val target = edgeTarget(element)

            return if (!directed && target == vertex) {
                adjacencies.contains(source.intValue)
            } else {
                vertex == source && adjacencies.contains(target.intValue)
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(directed, vertex.intValue, it.nextInt())
        }

        override fun foreach(action: EdgeConsumer) {
            adjacencies.foreach { nextVertex ->
                action.accept(canonicalEdge(directed, vertex.intValue, nextVertex))
            }
        }
    }

    private inner class IncomingEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        private val adjacencies = predecessors[vertex.intValue]

        override val size: Int get() = adjacencies.size

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            validateEdge(element)
            val source = edgeTarget(element)
            val target = edgeSource(element)

            return if (!directed && target == vertex) {
                adjacencies.contains(source.intValue)
            } else {
                vertex == source && adjacencies.contains(target.intValue)
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = adjacencies.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge = canonicalEdge(directed, it.nextInt(), vertex.intValue)
        }

        override fun foreach(action: EdgeConsumer) {
            adjacencies.foreach { nextVertex ->
                action.accept(canonicalEdge(directed, nextVertex, vertex.intValue))
            }
        }
    }
}

private fun canonicalEdge(directed: Boolean, source: Vertex, target: Vertex) =
    canonicalEdge(directed, source.intValue, target.intValue)

private fun canonicalEdge(directed: Boolean, sourceVertexId: Int, targetVertexId: Int): Edge {
    return if (!directed) {
        Edge(highBits = min(sourceVertexId, targetVertexId), lowBits = max(sourceVertexId, targetVertexId))
    } else {
        Edge(highBits = sourceVertexId, lowBits = targetVertexId)
    }
}

// only use if you know source <= target
private fun canonicalSortedEdge(sourceVertexId: Int, targetVertexId: Int) =
    Edge(highBits = sourceVertexId, lowBits = targetVertexId)
