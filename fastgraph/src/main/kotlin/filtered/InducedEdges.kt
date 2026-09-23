package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.references.EdgeReferenceManager

internal open class InducedEdges(
    private val parent: Graph,
    vertices: InducedVertices,
    inducers: EdgeSet
) : FilteredEdges(), VertexChangeListener, EdgeChangeListener {

    companion object {
        fun from(parent: Graph, vertices: InducedVertices, inducers: EdgeSet): InducedEdges =
            if (parent.edges is CanonicalEdgeSet) CanonicalInducedEdges(parent, vertices, inducers)
            else InducedEdges(parent, vertices, inducers)
    }

    private val edges = LongHashSet(inducers.size)
    private val listeners = EdgeChangeListenerManager()

    init {
        edges.ensureCapacity(inducers.size)
        for (edge in inducers) {
            require(parent.edges.contains(edge))
            if (vertices.contains(parent.edgeSource(edge)) && vertices.contains(parent.edgeTarget(edge))) {
                edges.add(edge.id)
            }
        }
        parent.registerEdgeChangeListener(this)
        vertices.registerVertexChangeListener(this)
    }

    private lateinit var references: EdgeReferenceManager

    override val size: Int get() = edges.size
    override fun contains(element: Edge): Boolean = edges.contains(element.id)
    override fun iterator(): EdgeIterator = edges.iterator().asEdgeIterator()

    override fun registerEdgeChangeListener(listener: EdgeChangeListener) = listeners.register(listener)
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) = listeners.unregister(listener)

    override fun <T> createEdgeProperty(
        graph: Graph,
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> =
        io.github.sooniln.fastgraph.properties.createEdgeProperty(graph, type, defaultValueFunction)

    override fun <T> createEdgeKeyProperty(
        graph: Graph,
        type: PropertyType<T>
    ): MutableEdgeKeyProperty<T> = io.github.sooniln.fastgraph.properties.createEdgeKeyProperty(graph, type)

    override fun createEdgeReference(graph: Graph, edge: Edge): EdgeReference {
        if (!::references.isInitialized) {
            references = EdgeReferenceManager(graph)
        }
        return references.getReference(edge)
    }

    override fun trimToSize() {
        edges.trimToSize()
        listeners.notifyTrimToSize()
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        val it = edges.iterator()
        while (it.hasNext()) {
            val edge = Edge(it.nextLong())
            if (parent.edgeSource(edge) == vertex || parent.edgeTarget(edge) == vertex) {
                it.remove()

                // TODO: reentrancy is potentially dangerous here
                listeners.notifyEdgeRemoved(edge)
            }
        }
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {}

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        if (edges.remove(edge.id)) {
            listeners.notifyEdgeRemoved(edge)
        }
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val removed = edges.remove(newEdge.id)
        if (edges.remove(oldEdge.id)) {
            edges.add(newEdge.id)
            listeners.notifyEdgeReassigned(oldEdge, newEdge)
        } else if (removed) {
            listeners.notifyEdgeRemoved(newEdge)
        }
    }
}

private class CanonicalInducedEdges(parent: Graph, vertices: InducedVertices, inducers: EdgeSet) :
    InducedEdges(parent, vertices, inducers), CanonicalEdgeSet {

    override fun filter(parentEdges: EdgeSet): CanonicalEdgeSet = FilteredCanonicalEdgeSet(parentEdges)

    private inner class FilteredCanonicalEdgeSet(parentEdges: EdgeSet) : FilteredEdgeSet(parentEdges), CanonicalEdgeSet
}
