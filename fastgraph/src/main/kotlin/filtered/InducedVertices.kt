package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexCollection
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asVertexIterator
import io.github.sooniln.fastgraph.createVertexKeyProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager

internal interface InducedVertices : FilteredVertices {
    companion object {
        fun from(graph: Graph, inducers: VertexSet): InducedVertices = InducedSomeVertices(graph, inducers)
        fun from(graph: Graph): InducedVertices = InducedAllVertices(graph)
    }
}

private class InducedSomeVertices(parent: Graph, inducers: VertexSet) : InducedVertices, AbstractVertexSet(), VertexChangeListener {

    private val vertices = IntHashSet(inducers.size)
    private val listeners = VertexChangeListenerManager()

    init {
        vertices.ensureCapacity(inducers.size)
        for (vertex in inducers) {
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

    override fun registerVertexChangeListener(listener: VertexChangeListener) {
        listeners.register(listener)
    }

    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {
        listeners.unregister(listener)
    }

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = createVertexProperty(graph, type, defaultValueFunction)

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> =
        createVertexKeyProperty(graph, type)

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

private class InducedAllVertices(private val parent: Graph) : InducedVertices, AbstractVertexSet(), VertexSet by parent.vertices {

    private lateinit var graph: Graph

    // TODO: figure out a way to avoid lateinit?
    override fun bind(graph: Graph) {
        this.graph = graph
    }

    override fun isEmpty(): Boolean = parent.vertices.isEmpty()
    override fun contains(element: Vertex): Boolean = parent.vertices.contains(element)
    override fun containsAll(elements: VertexCollection): Boolean = parent.vertices.containsAll(elements)
    override fun containsAll(elements: Collection<Vertex>): Boolean = parent.vertices.containsAll(elements)

    override fun registerVertexChangeListener(listener: VertexChangeListener) {
        parent.registerVertexChangeListener(listener)
    }

    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {
        parent.unregisterVertexChangeListener(listener)
    }

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return ReparentedMutableVertexProperty(graph, parent.createVertexProperty(type, defaultValueFunction))
    }

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return ReparentedMutableVertexKeyProperty(graph, parent.createVertexKeyProperty(type))
    }

    override fun createVertexReference(vertex: Vertex): VertexReference {
        return parent.createVertexReference(vertex)
    }

    override fun trimToSize() {}

    private class ReparentedMutableVertexProperty<T>(
        override val graph: Graph,
        private val property: MutableVertexProperty<T>
    ) : MutableVertexProperty<T> by property

    private class ReparentedMutableVertexKeyProperty<T>(
        override val graph: Graph,
        private val property: MutableVertexKeyProperty<T>
    ) : MutableVertexKeyProperty<T> by property
}
