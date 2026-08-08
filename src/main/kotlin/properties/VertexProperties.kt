package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

// TODO: lazy/no initialization?
internal class ArrayVertexProperty<T>(
    override val graph: IndexedVertexGraph,
    override val type: Class<T>,
    private val initializer: VertexInitializer<T>,
) : MutableVertexProperty<T>, VertexChangeListener {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex -> onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override fun get(vertex: Vertex): T {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: T) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: T): T {
        try {
            return property.set(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == property.size)
        property.add(initializer.initialize(vertex))
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(vertex.id == property.lastIndex)
        property.removeAt(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(oldVertex.id == property.lastIndex)
        property[newVertex.id] = property.removeAt(oldVertex.id)
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()
}

// TODO: lazy/no initialization?
internal class MapVertexProperty<T>(
    override val graph: Graph,
    override val type: Class<T>,
    private val initializer: VertexInitializer<T>
) : MutableVertexProperty<T>, VertexChangeListener {

    // we take advantage of the default value to compress the map where possible
    private val property = Int2AnyHashMap<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex -> property[vertex.id] = initializer.initialize(vertex) }
    }

    init {
        graph.vertices.foreach { onVertexAdded(it) }
    }

    override fun get(vertex: Vertex): T {
        @Suppress("UNCHECKED_CAST")
        return property[vertex.id] as T
    }

    override fun set(vertex: Vertex, value: T) {
        if (property.isDefaultValue(value)) {
            property.remove(vertex.id)
        } else {
            property[vertex.id] = value
        }
    }

    override fun put(vertex: Vertex, value: T): T {
        return if (property.isDefaultValue(value)) {
            @Suppress("UNCHECKED_CAST")
            property.remove(vertex.id) as T
        } else {
            @Suppress("UNCHECKED_CAST")
            property.put(vertex.id, value) as T
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        val value = initializer.initialize(vertex)
        if (!property.isDefaultValue(value)) {
            property[vertex.id] = value
        }
    }

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldValue = property.remove(oldVertex.id)
        if (!property.isDefaultValue(oldValue)) {
            @Suppress("UNCHECKED_CAST")
            property[newVertex.id] = oldValue as T
        }
    }

    override fun trimToSize() = property.trimToSize()
}
