package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastcollect.Long2LongHashMap
import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastgraph.AbstractEdgeSequencedSet
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.CanonicalEdgeGraph
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeSet
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.edgeIteratorOf
import io.github.sooniln.fastgraph.emptyEdgeIterator
import io.github.sooniln.fastgraph.emptyImmutableGraph
import io.github.sooniln.fastgraph.homomorphisms.EdgeHomomorphism
import io.github.sooniln.fastgraph.homomorphisms.InternalEdgeHomomorphism
import io.github.sooniln.fastgraph.homomorphisms.emptyEdgeIsomorphism
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.properties.EdgeProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.createEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.createEdgeProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import io.github.sooniln.fastgraph.properties.reparent
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.references.EdgeReferenceManager
import io.github.sooniln.fastgraph.references.VertexReference
import io.github.sooniln.fastgraph.util.cheapSynchronizedLazy
import java.util.BitSet
import kotlin.math.max
import kotlin.math.min

internal fun createAssociatedDiGraph(graph: Graph): EdgeHomomorphism<Graph, Graph> {
    return when (graph) {
        is ImmutableGraph -> createAssociatedDiGraph(graph)
        is CanonicalEdgeGraph -> CanonicalEdgeAssociatedDiGraph(graph).createEdgeHomomorphism()
        else -> OpaqueEdgeDirectedGraph(graph).createEdgeHomomorphism()
    }
}

internal fun createAssociatedDiGraph(graph: ImmutableGraph): EdgeHomomorphism<ImmutableGraph, ImmutableGraph> {
    return if (graph.isEmpty()) {
        emptyEdgeIsomorphism(emptyImmutableGraph(true), graph)
    } else if (graph is CanonicalEdgeGraph) {
        ImmutableCanonicalEdgeAssociatedDiGraph(graph).createEdgeHomomorphism()
    } else {
        ImmutableOpaqueEdgeDirectedGraph(graph).createEdgeHomomorphism()
    }
}

private abstract class AbstractCanonicalEdgeAssociatedDiGraph<G : CanonicalEdgeGraph>(
    protected val graph: G
) : CanonicalEdgeGraph by graph {

    // a self-loop maps to a single directed edge rather than two
    protected var selfLoopCount = 0

    init {
        require(!graph.multiEdge)
        require(!graph.directed)

        for (vertex in graph.vertices) {
            if (graph.hasEdge(vertex, vertex)) ++selfLoopCount
        }
    }

    override val directed: Boolean get() = true
    override val multiEdge: Boolean get() = false

    protected fun validateEdge(edge: Edge): Edge {
        val e = CanonicalEdge.from(edge)
        if (!graph.hasEdge(e.source, e.target)) throwIllegalEdge(e, null)
        return edge
    }

    protected fun reverseEdge(edge: Edge): Edge = reverseEdge(CanonicalEdge.from(edge))
    protected fun reverseEdge(edge: CanonicalEdge): Edge = CanonicalEdge.from(true, edge.target, edge.source).toEdge()

    override fun outgoingEdges(vertex: Vertex): CanonicalEdgeSet = OutgoingEdgeSet(vertex, graph.outgoingEdges(vertex))
    override fun outgoingEdge(vertex: Vertex): Edge {
        val target = CanonicalEdge.from(graph.outgoingEdge(vertex)).opposite(vertex)
        return CanonicalEdge.from(true, vertex, target).toEdge()
    }
    override fun incomingEdges(vertex: Vertex): CanonicalEdgeSet = IncomingEdgeSet(vertex, graph.outgoingEdges(vertex))
    override fun incomingEdge(vertex: Vertex): Edge {
        val source = CanonicalEdge.from(graph.outgoingEdge(vertex)).opposite(vertex)
        return CanonicalEdge.from(true, source, vertex).toEdge()
    }

    override val edges: CanonicalEdgeSet = object : CanonicalEdgeSet {
        override val size: Int get() = 2 * graph.edges.size - selfLoopCount
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = graph.edges.iterator()
            private var edge = CanonicalEdge(0)
            private var edgeComplete = true

            override fun hasNext(): Boolean = !edgeComplete || it.hasNext()
            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                if (!edgeComplete) {
                    edgeComplete = true
                    return reverseEdge(edge)
                } else {
                    edge = CanonicalEdge.from(it.next())
                    edgeComplete = edge.source == edge.target
                    return edge.toEdge()
                }
            }
        }
    }

    override fun edgeSource(edge: Edge): Vertex = CanonicalEdge.from(edge).source
    override fun edgeTarget(edge: Edge): Vertex = CanonicalEdge.from(edge).target

    override fun edge(source: Vertex, target: Vertex): Edge {
        check(graph.hasEdge(source, target))
        return CanonicalEdge.from(true, source, target).toEdge()
    }

    override fun edges(source: Vertex, target: Vertex): CanonicalEdgeSet = object : CanonicalEdgeSet {
        override val size: Int get() = if (graph.hasEdge(source, target)) 1 else 0
        override fun iterator(): EdgeIterator {
            return if (graph.hasEdge(source, target)) {
                edgeIteratorOf(CanonicalEdge.from(true, source, target).toEdge())
            } else {
                emptyEdgeIterator()
            }
        }
    }

    abstract override fun registerEdgeChangeListener(listener: EdgeChangeListener)
    abstract override fun unregisterEdgeChangeListener(listener: EdgeChangeListener)

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = graph.createVertexProperty(type, defaultValueFunction).reparent(this)

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return graph.createVertexKeyProperty(type).reparent(this)
    }

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> = createEdgeProperty(this, type, defaultValueFunction)

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        return createEdgeKeyProperty(this, type)
    }

    abstract override fun createEdgeReference(edge: Edge): EdgeReference

    override fun trimToSize() {}

    abstract fun createEdgeHomomorphism(): EdgeHomomorphism<Graph, Graph>

    private class OutgoingEdgeSet(
        private val vertex: Vertex,
        private val edgeSet: CanonicalEdgeSet
    ) : AbstractEdgeSet(), CanonicalEdgeSet {
        override val size: Int get() = edgeSet.size
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = edgeSet.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                val originalEdge = CanonicalEdge.from(it.next())
                val target = originalEdge.opposite(vertex)
                return CanonicalEdge.from(true, vertex, target).toEdge()
            }
        }
    }

    private class IncomingEdgeSet(
        private val vertex: Vertex,
        private val edgeSet: CanonicalEdgeSet
    ) : AbstractEdgeSet(), CanonicalEdgeSet {
        override val size: Int get() = edgeSet.size
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = edgeSet.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                val originalEdge = CanonicalEdge.from(it.next())
                val source = originalEdge.opposite(vertex)
                return CanonicalEdge.from(true, source, vertex).toEdge()
            }
        }
    }
}

private class CanonicalEdgeAssociatedDiGraph(graph: CanonicalEdgeGraph) : AbstractCanonicalEdgeAssociatedDiGraph<CanonicalEdgeGraph>(graph) {

    private val edgeChangeListenerManager = EdgeChangeListenerManager()
    private val edgeReferenceManager by cheapSynchronizedLazy { EdgeReferenceManager(this) }

    private val edgeListener = object : EdgeChangeListener {
        override fun onEdgeAdded(edge: Edge) {
            edgeChangeListenerManager.notifyEdgeAdded(edge)
            if (isSelfLoop(edge)) {
                ++selfLoopCount
            } else {
                edgeChangeListenerManager.notifyEdgeAdded(reverseEdge(edge))
            }
        }

        override fun onEdgeRemoved(edge: Edge) {
            edgeChangeListenerManager.notifyEdgeRemoved(edge)
            if (isSelfLoop(edge)) {
                --selfLoopCount
            } else {
                edgeChangeListenerManager.notifyEdgeRemoved(reverseEdge(edge))
            }
        }

        override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
            // canonical edges are only reassigned through vertex reassignment, which cannot change whether an edge is
            // a self-loop
            check(isSelfLoop(oldEdge) == isSelfLoop(newEdge))
            edgeChangeListenerManager.notifyEdgeReassigned(oldEdge, newEdge)
            if (!isSelfLoop(oldEdge)) {
                edgeChangeListenerManager.notifyEdgeReassigned(reverseEdge(oldEdge), reverseEdge(newEdge))
            }
        }

        private fun isSelfLoop(edge: Edge): Boolean {
            val e = CanonicalEdge.from(edge)
            return e.source == e.target
        }
    }

    init {
        graph.registerEdgeChangeListener(edgeListener)
    }

    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {
        edgeChangeListenerManager.register(listener)
    }

    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {
        edgeChangeListenerManager.unregister(listener)
    }

    override fun createEdgeReference(edge: Edge): EdgeReference = edgeReferenceManager.getReference(validateEdge(edge))

    override fun trimToSize() {
        edgeChangeListenerManager.notifyTrimToSize()
        edgeReferenceManager.trimToSize()
    }

    override fun createEdgeHomomorphism(): EdgeHomomorphism<Graph, Graph> {
        return object : InternalEdgeHomomorphism<Graph, Graph> {
            override val source: Graph get() = this@CanonicalEdgeAssociatedDiGraph
            override val target: Graph get() = graph
            override val edgeMap: EdgeProperty<Edge> = object : EdgeProperty<Edge> {
                override val graph: Graph get() = source
                override val type: PropertyType<Edge> get() = propertyTypeOf()
                override fun get(edge: Edge): Edge {
                    val edge = CanonicalEdge.from(edge)
                    return CanonicalEdge.from(false, edge.source, edge.target).toEdge()
                }
            }
        }
    }
}

private class ImmutableCanonicalEdgeAssociatedDiGraph<G>(
    graph: G
) : AbstractCanonicalEdgeAssociatedDiGraph<G>(graph), InternalImmutableGraph where G : CanonicalEdgeGraph, G : ImmutableGraph {

    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun createVertexReference(vertex: Vertex): VertexReference = graph.createVertexReference(vertex)
    override fun createEdgeReference(edge: Edge): EdgeReference = ImmutableEdgeReference(validateEdge(edge))

    override fun createEdgeHomomorphism(): EdgeHomomorphism<ImmutableGraph, ImmutableGraph> {
        return object : InternalEdgeHomomorphism<ImmutableGraph, ImmutableGraph> {
            override val source: ImmutableGraph get() = this@ImmutableCanonicalEdgeAssociatedDiGraph
            override val target: ImmutableGraph get() = graph
            override val edgeMap: EdgeProperty<Edge> = object : EdgeProperty<Edge> {
                override val graph: Graph get() = source
                override val type: PropertyType<Edge> get() = propertyTypeOf()
                override fun get(edge: Edge): Edge {
                    val edge = CanonicalEdge.from(edge)
                    return CanonicalEdge.from(false, edge.source, edge.target).toEdge()
                }
            }
        }
    }
}

internal abstract class AbstractOpaqueEdgeDirectedGraph(protected val graph: Graph) : Graph by graph {

    @JvmInline
    protected value class BiDirectionalEdge(val value: Long) {
        constructor(edge1Id: Int, edge2Id: Int) : this(
            edge2Id.toLong().shl(32).or(edge1Id.toLong().and(0xFFFFFFFF))
        )

        // we adopt the convention that edge1 is the edge outgoing from the original edge's source vertex, and edge2
        // is the edge incoming to the original edge's source vertex. a self-loop maps to only a single directed edge,
        // edge1, and edge2Id is -1.
        val edge1Id: Int inline get() = value.toInt()
        val edge2Id: Int inline get() = value.ushr(32).toInt()
        val edge1: Edge inline get() = IdentityIndexedEdge(edge1Id).toEdge()
        val edge2: Edge inline get() = IdentityIndexedEdge(edge2Id).toEdge()
        val isSelfLoop: Boolean inline get() = edge2Id < 0
    }

    protected var nextEdgeId = 0
    protected val edgeMap = Long2LongHashMap()
    // indexed by edge id, stores the id of the original edge
    protected val reverseEdgeMap = LongArrayList()
    // indexed by edge id, set if the edge is an edge2 (goes from the original edge's target to its source)
    protected val reversedEdges = BitSet()

    init {
        require(!graph.directed)

        edgeMap.ensureCapacity(graph.edges.size)
        reverseEdgeMap.ensureCapacity(2 * graph.edges.size)

        for (edge in graph.edges) {
            addOriginal(edge)
        }
    }

    protected fun addOriginal(original: Edge): BiDirectionalEdge {
        val bidirectionalEdge = if (graph.edgeSource(original) == graph.edgeTarget(original)) {
            reverseEdgeMap.add(original.id)
            BiDirectionalEdge(nextEdgeId++, -1)
        } else {
            reverseEdgeMap.add(original.id)
            reverseEdgeMap.add(original.id)
            reversedEdges.set(nextEdgeId + 1)
            BiDirectionalEdge(nextEdgeId++, nextEdgeId++)
        }
        edgeMap[original.id] = bidirectionalEdge.value
        check(reverseEdgeMap.size == nextEdgeId)
        return bidirectionalEdge
    }

    protected fun validateEdge(edge: Edge): Edge {
        if (edge.id !in 0..<nextEdgeId) throwIllegalEdge(this, edge)
        return edge
    }
    protected fun original(edge: Edge): Edge {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return Edge(reverseEdgeMap[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(this, edge, e)
        }
    }
    protected fun bidirectionalEdge(original: Edge): BiDirectionalEdge {
        return BiDirectionalEdge(edgeMap.getValue(original.id))
    }
    protected fun BiDirectionalEdge.outgoing(source: Vertex, original: Edge): Edge {
        return if (graph.edgeSource(original) == source) edge1 else edge2
    }
    protected fun BiDirectionalEdge.incoming(source: Vertex, original: Edge): Edge {
        return if (isSelfLoop || graph.edgeSource(original) != source) edge1 else edge2
    }
    protected fun IdentityIndexedEdge.isOutgoing() = !reversedEdges[id]

    override val directed: Boolean get() = true

    override fun outgoingEdges(vertex: Vertex): EdgeSet = object : EdgeSet {
        private val outgoingEdges = graph.outgoingEdges(vertex)
        override val size: Int get() = outgoingEdges.size
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = outgoingEdges.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                val original = it.next()
                return bidirectionalEdge(original).outgoing(vertex, original)
            }
        }
    }

    override fun outgoingEdge(vertex: Vertex): Edge {
        val original = graph.outgoingEdge(vertex)
        return bidirectionalEdge(original).outgoing(vertex, original)
    }

    override fun incomingEdges(vertex: Vertex): EdgeSet = object : EdgeSet {
        private val incomingEdges = graph.incomingEdges(vertex)
        override val size: Int get() = incomingEdges.size
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = incomingEdges.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                val original = it.next()
                return bidirectionalEdge(original).incoming(vertex, original)
            }
        }
    }

    override fun incomingEdge(vertex: Vertex): Edge {
        val original = graph.incomingEdge(vertex)
        return bidirectionalEdge(original).incoming(vertex, original)
    }

    override val edges: EdgeSet = object : AbstractEdgeSequencedSet(), IdentityIndexedEdgeSet {
        override val size: Int get() = nextEdgeId
    }

    override fun edgeSource(edge: Edge): Vertex {
        val original = original(edge)
        val edge = IdentityIndexedEdge.from(edge)
        return if (edge.isOutgoing()) {
            graph.edgeSource(original)
        } else {
            graph.edgeTarget(original)
        }
    }

    override fun edgeTarget(edge: Edge): Vertex {
        val original = original(edge)
        val edge = IdentityIndexedEdge.from(edge)
        return if (edge.isOutgoing()) {
            graph.edgeTarget(original)
        } else {
            graph.edgeSource(original)
        }
    }

    override fun edge(source: Vertex, target: Vertex): Edge {
        val original = graph.edge(source, target)
        return bidirectionalEdge(original).outgoing(source, original)
    }

    override fun edges(source: Vertex, target: Vertex): EdgeSet = object : EdgeSet {
        private val originalEdges = graph.edges(source, target)
        override val size: Int get() = originalEdges.size
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = originalEdges.iterator()
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): Edge {
                val original = it.next()
                return bidirectionalEdge(original).outgoing(source, original)
            }
        }
    }

    abstract override fun registerEdgeChangeListener(listener: EdgeChangeListener)
    abstract override fun unregisterEdgeChangeListener(listener: EdgeChangeListener)

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = graph.createVertexProperty(type, defaultValueFunction).reparent(this)

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return graph.createVertexKeyProperty(type).reparent(this)
    }

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return createEdgeProperty(this, type, defaultValueFunction)
    }

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        return createEdgeKeyProperty(this, type)
    }

    abstract override fun createEdgeReference(edge: Edge): EdgeReference

    override fun trimToSize() {}

    abstract fun createEdgeHomomorphism(): EdgeHomomorphism<Graph, Graph>
}

private class OpaqueEdgeDirectedGraph(graph: Graph) : AbstractOpaqueEdgeDirectedGraph(graph) {

    private val edgeChangeListenerManager = EdgeChangeListenerManager()
    private val edgeReferenceManager by cheapSynchronizedLazy { EdgeReferenceManager(this) }

    private val edgeListener = object : EdgeChangeListener {
        override fun onEdgeAdded(edge: Edge) {
            val bidirectionalEdge = addOriginal(edge)

            edgeChangeListenerManager.notifyEdgeAdded(bidirectionalEdge.edge1)
            if (!bidirectionalEdge.isSelfLoop) {
                edgeChangeListenerManager.notifyEdgeAdded(bidirectionalEdge.edge2)
            }
        }

        override fun onEdgeRemoved(edge: Edge) {
            val bidirectionalEdge = BiDirectionalEdge(edgeMap.getValue(edge.id))

            if (bidirectionalEdge.isSelfLoop) {
                removeEdgeId(bidirectionalEdge.edge1Id)
            } else {
                // vacated ids must be handled from highest to lowest, which guarantees the last edge is never the
                // other vacated id
                val edge1Id = bidirectionalEdge.edge1Id
                val edge2Id = bidirectionalEdge.edge2Id
                removeEdgeId(max(edge1Id, edge2Id))
                removeEdgeId(min(edge1Id, edge2Id))
            }
            edgeMap.remove(edge.id)
        }

        override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
            if (edgeMap.containsKey(newEdge.id)) {
                onEdgeRemoved(newEdge)
            }

            val bidirectionalEdge = BiDirectionalEdge(edgeMap.getValue(oldEdge.id))
            edgeMap.remove(oldEdge.id)
            edgeMap[newEdge.id] = bidirectionalEdge.value
            reverseEdgeMap[bidirectionalEdge.edge1Id] = newEdge.id
            if (!bidirectionalEdge.isSelfLoop) {
                reverseEdgeMap[bidirectionalEdge.edge2Id] = newEdge.id
            }
        }

        // removes the given edge id by moving the last edge id into its place (if necessary)
        private fun removeEdgeId(edgeId: Int) {
            val lastEdgeId = nextEdgeId - 1
            if (edgeId == lastEdgeId) {
                edgeChangeListenerManager.notifyEdgeRemoved(IdentityIndexedEdge(edgeId).toEdge())
            } else {
                edgeChangeListenerManager.notifyEdgeReassigned(
                    IdentityIndexedEdge(lastEdgeId).toEdge(),
                    IdentityIndexedEdge(edgeId).toEdge()
                )

                val originalEdgeId = reverseEdgeMap[lastEdgeId]
                val reversed = reversedEdges[lastEdgeId]
                reverseEdgeMap[edgeId] = originalEdgeId
                reversedEdges[edgeId] = reversed

                val movedEdge = BiDirectionalEdge(edgeMap.getValue(originalEdgeId))
                edgeMap[originalEdgeId] = if (reversed) {
                    BiDirectionalEdge(movedEdge.edge1Id, edgeId).value
                } else {
                    BiDirectionalEdge(edgeId, movedEdge.edge2Id).value
                }
            }
            reverseEdgeMap.removeLast()
            reversedEdges.clear(lastEdgeId)
            nextEdgeId--
        }
    }

    init {
        graph.registerEdgeChangeListener(edgeListener)
    }

    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {
        edgeChangeListenerManager.register(listener)
    }

    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {
        edgeChangeListenerManager.unregister(listener)
    }

    override fun createEdgeReference(edge: Edge): EdgeReference = edgeReferenceManager.getReference(validateEdge(edge))

    override fun trimToSize() {
        edgeMap.trimToSize()
        reverseEdgeMap.trimToSize()
        edgeChangeListenerManager.notifyTrimToSize()
        edgeReferenceManager.trimToSize()
    }

    override fun createEdgeHomomorphism(): EdgeHomomorphism<Graph, Graph> {
        return object : InternalEdgeHomomorphism<Graph, Graph> {
            override val source: Graph get() = this@OpaqueEdgeDirectedGraph
            override val target: Graph get() = graph
            override val edgeMap: EdgeProperty<Edge> = object : EdgeProperty<Edge> {
                override val graph: Graph get() = source
                override val type: PropertyType<Edge> get() = propertyTypeOf()
                override fun get(edge: Edge): Edge = original(edge)
            }
        }
    }
}

internal class ImmutableOpaqueEdgeDirectedGraph(graph: Graph) : AbstractOpaqueEdgeDirectedGraph(graph), InternalImmutableGraph {
    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun createVertexReference(vertex: Vertex): VertexReference = graph.createVertexReference(vertex)
    override fun createEdgeReference(edge: Edge): EdgeReference = ImmutableEdgeReference(validateEdge(edge))

    override fun createEdgeHomomorphism(): EdgeHomomorphism<ImmutableGraph, ImmutableGraph> {
        return object : InternalEdgeHomomorphism<ImmutableGraph, ImmutableGraph> {
            override val source: ImmutableGraph get() = this@ImmutableOpaqueEdgeDirectedGraph
            override val target: ImmutableGraph get() = graph as ImmutableGraph
            override val edgeMap: EdgeProperty<Edge> = object : EdgeProperty<Edge> {
                override val graph: Graph get() = source
                override val type: PropertyType<Edge> get() = propertyTypeOf()
                override fun get(edge: Edge): Edge = original(edge)
            }
        }
    }
}
