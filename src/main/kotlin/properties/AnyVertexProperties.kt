package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.lastIndex

internal class ArrayVertexProperty<T>(
    override val graph: IndexedVertexGraph,
    override val type: Class<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T>, VertexChangeListener {

    private val property = ArrayList<T>()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    private fun ensureVertexExists(vertex: Vertex): ArrayList<T> {
        val index = vertex.id
        if (property.size <= index) {
            if (index >= graph.vertices.size) throwIllegalVertex(vertex)
            property.ensureCapacity(index + 1)
            var i = property.size
            do {
                property.add(initializer.apply(graph.vertices[i++]))
            } while (i <= index)
        }
        return property
    }

    override fun get(vertex: Vertex): T {
        return ensureVertexExists(vertex)[vertex.id]
    }

    override fun set(vertex: Vertex, value: T) {
        ensureVertexExists(vertex)[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: T): T {
        return ensureVertexExists(vertex).set(vertex.id, value)
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == graph.vertices.lastIndex)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        if (vertex.id == property.lastIndex) {
            property.removeAt(vertex.id)
        } else {
            check(vertex.id == graph.vertices.lastIndex)
        }
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        if (oldVertex.id == property.lastIndex) {
            property[newVertex.id] = property.removeAt(oldVertex.id)
        } else {
            check(oldVertex.id == graph.vertices.lastIndex)
        }
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableArrayVertexProperty<G, T>(
    override val graph: G,
    override val type: Class<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            assert(vertex.id == property.size)
            property.add(defaultValueFunction.apply(vertex))
        }
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
}

internal class MapVertexProperty<T>(
    override val graph: Graph,
    override val type: Class<T>,
    defaultValueFunction: VertexFunction<T>
) : MutableVertexProperty<T>, VertexChangeListener {

    private val property = Int2AnyHashMap<T>()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override fun get(vertex: Vertex): T {
        return property.getOrPut(vertex.id) { initializer.apply(vertex)}
    }

    override fun set(vertex: Vertex, value: T) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: T): T {
        return property.replaceOrSet(vertex.id, value) { initializer.apply(vertex) }
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldValue = property.removeOrElse(oldVertex.id) { return }
        property[newVertex.id] = oldValue
    }

    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableMapVertexProperty<T>(
    override val graph: ImmutableGraph,
    override val type: Class<T>,
    defaultValueFunction: VertexFunction<T>
) : MutableVertexProperty<T> {

    private val property = Int2AnyHashMap<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = defaultValueFunction.apply(vertex)
        }
    }

    override fun get(vertex: Vertex): T {
        try {
            return property.getValue(vertex.id)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: T) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: T): T {
        try {
            return property.replace(vertex.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }
}
