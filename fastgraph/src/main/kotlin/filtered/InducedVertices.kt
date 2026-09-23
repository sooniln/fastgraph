package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.references.VertexReference
import io.github.sooniln.fastgraph.listeners.VertexChangeListenerManager
import io.github.sooniln.fastgraph.references.VertexReferenceManager
import io.github.sooniln.fastgraph.properties.reparent

internal abstract class InducedVertices : FilteredVertices() {
    companion object {
        fun from(parent: Graph, inducers: VertexSet): InducedVertices = InducedSomeVertices(parent, inducers)

        fun from(parent: Graph): InducedVertices = when (parent.vertices) {
            is IdentityIndexedVertexSet -> IdentityIndexedInducedAllVertices(parent)
            is IndexedVertexSet -> IndexedInducedAllVertices(parent)
            else -> InducedAllVertices(parent)
        }
    }
}

private class InducedSomeVertices(parent: Graph, inducers: VertexSet) : InducedVertices(), VertexChangeListener {

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

    private lateinit var references: VertexReferenceManager

    override val size: Int get() = vertices.size
    override fun contains(element: Vertex): Boolean = vertices.contains(element.id)
    override fun iterator(): VertexIterator = vertices.iterator().asVertexIterator()

    override fun registerVertexChangeListener(listener: VertexChangeListener) = listeners.register(listener)
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) = listeners.unregister(listener)

    override fun <T> createVertexProperty(
        graph: Graph,
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> =
        io.github.sooniln.fastgraph.properties.createVertexProperty(graph, type, defaultValueFunction)

    override fun <T> createVertexKeyProperty(
        graph: Graph,
        type: PropertyType<T>
    ): MutableVertexKeyProperty<T> = io.github.sooniln.fastgraph.properties.createVertexKeyProperty(graph, type)

    override fun createVertexReference(graph: Graph, vertex: Vertex): VertexReference {
        if (!::references.isInitialized) {
            references = VertexReferenceManager(graph)
        }
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
        val removed = vertices.remove(newVertex.id)
        if (vertices.remove(oldVertex.id)) {
            vertices.add(newVertex.id)
            listeners.notifyVertexReassigned(oldVertex, newVertex)
        } else if (removed) {
            listeners.notifyVertexRemoved(newVertex)
        }
    }
}

private open class InducedAllVertices(protected val parent: Graph) : InducedVertices(), VertexSet by parent.vertices {

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
        graph: Graph,
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = parent.createVertexProperty(type, defaultValueFunction).reparent(graph)

    override fun <T> createVertexKeyProperty(
        graph: Graph,
        type: PropertyType<T>
    ): MutableVertexKeyProperty<T> = parent.createVertexKeyProperty(type).reparent(graph)

    override fun createVertexReference(graph: Graph, vertex: Vertex): VertexReference = parent.createVertexReference(vertex)

    override fun trimToSize() {}
}

private class IndexedInducedAllVertices(parent: Graph) : InducedAllVertices(parent), IndexedVertexSet {
    private val vertices: IndexedVertexSet inline get() = parent.vertices as IndexedVertexSet

    override fun get(index: Int): Vertex = vertices[index]
    override fun indexOf(element: Vertex): Int = vertices.indexOf(element)

    override fun isEmpty(): Boolean = super<InducedAllVertices>.isEmpty()
    override fun contains(element: Vertex): Boolean = super<InducedAllVertices>.contains(element)
    override fun containsAll(elements: VertexCollection): Boolean = super<InducedAllVertices>.containsAll(elements)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<InducedAllVertices>.containsAll(elements)
    override fun iterator(): VertexIterator = super<InducedAllVertices>.iterator()
    override fun toIntArray(): IntArray = super<InducedAllVertices>.toIntArray()
}

private class IdentityIndexedInducedAllVertices(parent: Graph) : InducedAllVertices(parent), IdentityIndexedVertexSet {
    override fun isEmpty(): Boolean = super<InducedAllVertices>.isEmpty()
    override fun contains(element: Vertex): Boolean = super<InducedAllVertices>.contains(element)
    override fun containsAll(elements: VertexCollection): Boolean = super<InducedAllVertices>.containsAll(elements)
    override fun containsAll(elements: Collection<Vertex>): Boolean = super<InducedAllVertices>.containsAll(elements)
    override fun iterator(): VertexIterator = super<InducedAllVertices>.iterator()
    override fun toIntArray(): IntArray = super<InducedAllVertices>.toIntArray()
}
