package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IdentityIndexedVertexGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class AnyIdentityIndexedVertexProperty<T>(
    override val graph: IdentityIndexedVertexGraph,
    override val type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T>, VertexChangeListener {

    private val property = ArrayList<T>()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
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
        property.add(initializer.apply(vertex))
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

internal class AnyIndexedVertexProperty<T>(
    override val graph: IndexedVertexGraph,
    override val type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T>, VertexChangeListener {

    private val property = ArrayList<T>()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override fun get(vertex: Vertex): T {
        try {
            return property[graph.vertices.indexOf(vertex)]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: T) {
        try {
            property[graph.vertices.indexOf(vertex)] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: T): T {
        try {
            return property.set(graph.vertices.indexOf(vertex), value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(graph.vertices.indexOf(vertex) == property.size)
        property.add(initializer.apply(vertex))
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(graph.vertices.indexOf(vertex) == property.lastIndex)
        property.removeAt(property.lastIndex)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(graph.vertices.indexOf(oldVertex) == property.lastIndex)
        property[graph.vertices.indexOf(newVertex)] = property.removeAt(property.lastIndex)
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableAnyIdentityIndexedVertexProperty<G, T>(
    override val graph: G,
    override val type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T> where G : ImmutableGraph, G : IdentityIndexedVertexGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
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

internal class ImmutableAnyIndexedVertexProperty<G, T>(
    override val graph: G,
    override val type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
            assert(graph.vertices.indexOf(vertex) == property.size)
            property.add(defaultValueFunction.apply(vertex))
        }
    }

    override fun get(vertex: Vertex): T {
        try {
            return property[graph.vertices.indexOf(vertex)]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: T) {
        try {
            property[graph.vertices.indexOf(vertex)] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: T): T {
        try {
            return property.set(graph.vertices.indexOf(vertex), value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }
}

internal class AnyVertexProperty<T>(
    override val graph: Graph,
    override val type: PropertyType<T>,
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

internal class ImmutableAnyVertexProperty<T>(
    override val graph: ImmutableGraph,
    override val type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>
) : MutableVertexProperty<T> {

    private val property = Int2AnyHashMap<T>()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
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
