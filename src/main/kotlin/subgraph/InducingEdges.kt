package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastcollect.longs.LongHashSet
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeProperties
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.asEdgeIterator
import io.github.sooniln.fastgraph.listeners.EdgeChangeListenerManager
import io.github.sooniln.fastgraph.references.EdgeReferenceManager

// TODO: document removal behavior
internal class InducingEdges(
    private val parent: Graph,
    private val vertices: InducingVertices,
    inducers: EdgeSet
) : SubgraphEdges, AbstractEdgeSet(), VertexChangeListener, EdgeChangeListener {

    private val edges = LongHashSet(inducers.size)
    private val listeners = EdgeChangeListenerManager()

    init {
        edges.ensureCapacity(inducers.size)
        inducers.foreach { edge ->
            require(parent.edges.contains(edge))
            context(parent) {
                if (vertices.contains(edge.source) && vertices.contains(edge.target)) {
                    edges.add(edge.id)
                }
            }
        }
        parent.registerEdgeChangeListener(this)
        vertices.registerVertexChangeListener(this)
    }

    // TODO: figure out a way to avoid lateinit?
    private lateinit var graph: Graph
    private lateinit var references: EdgeReferenceManager

    override fun bind(graph: Graph) {
        this.graph = graph
        references = EdgeReferenceManager(graph)
    }

    override val size: Int get() = edges.size

    override fun contains(element: Edge): Boolean = edges.contains(element.id)

    override fun iterator(): EdgeIterator = edges.iterator().asEdgeIterator()

    override fun foreach(action: EdgeConsumer) {
        edges.foreach { action.accept(Edge(it)) }
    }

    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {
        listeners.register(listener)
    }

    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {
        listeners.unregister(listener)
    }

    override fun <T> createEdgeProperty(
        type: Class<T>,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> = EdgeProperties.createEdgeProperty(graph, type, initializer)

    override fun createEdgeReference(edge: Edge): EdgeReference {
        return references.getReference(edge)
    }

    override fun trimToSize() {
        edges.trimToSize()
        listeners.notifyTrimToSize()
    }

    override fun onVertexAdded(vertex: Vertex) {
        throw IllegalStateException()
    }

    override fun onVertexRemoved(vertex: Vertex) {
        val it = edges.iterator()
        while (it.hasNext()) {
            context(parent) {
                val edge = Edge(it.next())
                if (edge.source == vertex || edge.target == vertex) {
                    it.remove()

                    // TODO: reentrancy is potentially dangerous here
                    listeners.notifyEdgeRemoved(edge)
                }
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
        if (edges.remove(oldEdge.id)) {
            edges.add(newEdge.id)
            listeners.notifyEdgeReassigned(oldEdge, newEdge)
        }
    }
}
