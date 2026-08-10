package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastcollect.IntHashSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexConsumer
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexIterator
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager

internal class InducingVertices(parent: Graph, inducers: VertexSet) : SubgraphVertices, AbstractVertexSet(), VertexChangeListener {

    private val vertices = IntHashSet(inducers.size)
    private val listeners = VertexChangeListenerManager()

    init {
        vertices.ensureCapacity(inducers.size)
        inducers.foreach { vertex ->
            require(parent.vertices.contains(vertex))
            vertices.add(vertex.id)
        }
        parent.registerVertexChangeListener(this)
    }

    // TODO: figure out a way to avoid lateinit?
    private lateinit var graph: Graph
    private lateinit var references: VertexReferenceManager

    override fun bind(graph: Graph) {
        this.graph = graph
        references = VertexReferenceManager(graph)
    }

    override val size: Int get() = vertices.size

    override fun contains(element: Vertex): Boolean = vertices.contains(element.id)

    override fun iterator(): VertexIterator = vertices.iterator().asVertexIterator()

    override fun foreach(action: VertexConsumer) {
        vertices.foreach { action.accept(Vertex(it)) }
    }

    override fun registerVertexChangeListener(listener: VertexChangeListener) {
        listeners.register(listener)
    }

    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {
        listeners.unregister(listener)
    }

    override fun <T> createVertexProperty(
        type: Class<T>,
        initializer: VertexFunction<T>
    ): MutableVertexProperty<T> = createVertexProperty(graph, type, initializer)

    override fun createVertexReference(vertex: Vertex): VertexReference {
        return references.getReference(vertex)
    }

    override fun trimToSize() {
        vertices.trimToSize()
        listeners.notifyTrimToSize()
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        if (vertices.remove(vertex.id)) {
            listeners.notifyVertexRemoved(vertex)
        }
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        if (vertices.remove(oldVertex.id)) {
            vertices.add(newVertex.id)
            listeners.notifyVertexReassigned(oldVertex, newVertex)
        }
    }
}
