package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeCollection
import io.github.sooniln.fastgraph.AbstractVertexSetList
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperty
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.MutableEdgeIterator
import io.github.sooniln.fastgraph.MutableEdgeSet
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableIndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexIterator
import io.github.sooniln.fastgraph.MutableVertexListIterator
import io.github.sooniln.fastgraph.MutableVertexSetList
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexProperty
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.primitives.IntHashSet
import io.github.sooniln.fastgraph.primitives.emptyIntIterator
import kotlin.math.max
import kotlin.math.min

internal class AdjacencyListGraph(override val directed: Boolean) : MutableGraph, MutableIndexedVertexGraph,
    VertexIndexedVertexGraph {

    private val _predecessors = lazy {
        check(directed)

        val predecessors = ArrayList<IntHashSet>(successors.size)
        repeat(successors.size) {
            predecessors.add(IntHashSet())
        }
        for (vertexIntValue in successors.indices) {
            val it = successors[vertexIntValue].iterator()
            while (it.hasNext()) {
                predecessors[it.nextInt()].add(vertexIntValue)
            }
        }
        return@lazy predecessors
    }

    private val successors: ArrayList<IntHashSet> = ArrayList()
    private val predecessors: ArrayList<IntHashSet> by _predecessors

    private val vertexRefs = VertexReferenceHolder()
    private val edgeRefs = LongEdgeReferenceHolder()

    private val vertexProperties = VertexPropertiesHolder()
    private val edgeProperties = EdgePropertiesHolder()

    private var numEdges = 0

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
            val targetIntValue = outIt.nextInt()

            outIt.remove()
            if (!directed) {
                if (vertex.intValue != targetIntValue) {
                    check(successors[targetIntValue].remove(vertex.intValue))
                }
            } else if (_predecessors.isInitialized()) {
                check(predecessors[targetIntValue].remove(vertex.intValue))
            }

            cleanupEdgeInternal(vertex.intValue, targetIntValue)
        }

        // remove inbound edges
        if (directed) {
            val inIt = predecessors[vertex.intValue].iterator()
            while (inIt.hasNext()) {
                val sourceIntValue = inIt.nextInt()

                inIt.remove()
                check(successors[sourceIntValue].remove(vertex.intValue))

                cleanupEdgeInternal(sourceIntValue, vertex.intValue)
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
                val inboundIt = predecessors[lastIndex].iterator()
                while (inboundIt.hasNext()) {
                    val source = inboundIt.nextInt()
                    // predecessors hasn't been corrected yet, so treat lastIndex as index when necessary
                    val newSource = if (source == lastIndex) index else source

                    val oldEdge = canonicalEdge(true, source, lastIndex)
                    val newEdge = canonicalEdge(true, newSource, index)
                    edgeProperties.swapAndRemove(oldEdge, newEdge)
                    edgeRefs.swapAndRemove(oldEdge, newEdge)

                    check(successors[source].remove(lastIndex))
                    check(successors[source].add(index))
                }

                val outboundIt = successors[lastIndex].iterator()
                while (outboundIt.hasNext()) {
                    val newTarget = outboundIt.nextInt()
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
                val outboundIt = successors[lastIndex].iterator()
                while (outboundIt.hasNext()) {
                    val target = outboundIt.nextInt()
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

            ++numEdges
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

    private fun removeEdgeInternal(sourceIntValue: Int, targetIntValue: Int) {
        check(successors[sourceIntValue].remove(targetIntValue))
        if (!directed) {
            if (sourceIntValue != targetIntValue) {
                check(successors[targetIntValue].remove(sourceIntValue))
            }
        } else if (_predecessors.isInitialized()) {
            check(predecessors[targetIntValue].remove(sourceIntValue))
        }

        cleanupEdgeInternal(sourceIntValue, targetIntValue)
    }

    private fun cleanupEdgeInternal(sourceIntValue: Int, targetIntValue: Int) {
        val edge = canonicalEdge(directed, sourceIntValue, targetIntValue)
        edgeProperties.swapAndRemove(edge, edge)
        edgeRefs.swapAndRemove(edge, edge)
        --numEdges
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
    override fun successors(vertex: Vertex): VertexSet = successors[validateVertex(vertex).intValue].asVertexSet()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet {
        if (!directed) {
            return successors(vertex)
        }

        return predecessors[validateVertex(vertex).intValue].asVertexSet()
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

    override val edges: MutableEdgeSet = object : MutableEdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = numEdges

        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("contains")
        override fun contains(element: Edge): Boolean {
            return containsEdge(edgeSource(element), edgeTarget(element))
        }

        override fun iterator(): MutableEdgeIterator = object : MutableEdgeIterator {
            private var ready = false
            private var source = 0
            private var targetIt = if (successors.isEmpty()) emptyIntIterator() else successors[0].iterator()
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
        return successors[validateVertex(source).intValue].contains(validateVertex(target).intValue)
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

    private inner class IncidentEdgeSet(
        private val outgoing: Boolean, private val vertex: Vertex, private val neighbors: IntHashSet
    ) : EdgeSet, AbstractEdgeCollection() {
        override val size: Int get() = neighbors.size

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
                neighbors.contains(source.intValue)
            } else {
                vertex == source && neighbors.contains(target.intValue)
            }
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = neighbors.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                return if (outgoing) {
                    canonicalEdge(directed, vertex.intValue, it.nextInt())
                } else {
                    canonicalEdge(directed, it.nextInt(), vertex.intValue)
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

// only use if you know source <= target
@Suppress("NOTHING_TO_INLINE")
private inline fun canonicalSortedEdge(sourceIntValue: Int, targetIntValue: Int) =
    Edge(highBits = sourceIntValue, lowBits = targetIntValue)
