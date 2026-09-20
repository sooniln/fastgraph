package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
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
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalVertex


internal class VertexIdentityIndexedVertexProperty(
    override val graph: IdentityIndexedVertexGraph,
    defaultValueFunction: VertexFunction<Vertex>,
) : MutableVertexProperty<Vertex>, VertexChangeListener {

    private val property = IntArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Vertex> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Vertex {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Vertex) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Vertex): Vertex {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == property.size)
        property.add(write(initializer.apply(vertex)))
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

    private fun read(it: Int): Vertex { return Vertex(it) }
    private fun write(it: Vertex): Int { return it.id }
}

internal class VertexIndexedVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Vertex>,
) : MutableVertexProperty<Vertex>, VertexChangeListener {

    private val property = IntArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Vertex> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Vertex {
        try {
            return read(property[graph.vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Vertex) {
        try {
            property[graph.vertices.indexOf(vertex)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Vertex): Vertex {
        try {
            return read(property.replace(graph.vertices.indexOf(vertex), write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(graph.vertices.indexOf(vertex) == property.size)
        property.add(write(initializer.apply(vertex)))
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

    private fun read(it: Int): Vertex { return Vertex(it) }
    private fun write(it: Vertex): Int { return it.id }
}

internal class ImmutableVertexIdentityIndexedVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Vertex>,
) : MutableVertexProperty<Vertex> where G : ImmutableGraph, G : IdentityIndexedVertexGraph {

    private val property = IntArray(graph.vertices.size) { vertexId ->
        write(defaultValueFunction.apply(Vertex(vertexId)))
    }

    override val type: PropertyType<Vertex> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Vertex {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Vertex) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Vertex): Vertex {
        try {
            val oldValue = read(property[vertex.id])
            property[vertex.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Vertex { return Vertex(it) }
    private fun write(it: Vertex): Int { return it.id }
}

internal class ImmutableVertexIndexedVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Vertex>,
) : MutableVertexProperty<Vertex> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = IntArray(graph.vertices.size) { index ->
        write(defaultValueFunction.apply(graph.vertices[index]))
    }

    override val type: PropertyType<Vertex> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Vertex {
        try {
            return read(property[graph.vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Vertex) {
        try {
            property[graph.vertices.indexOf(vertex)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Vertex): Vertex {
        try {
            val index = graph.vertices.indexOf(vertex)
            val oldValue = read(property[index])
            property[index] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Vertex { return Vertex(it) }
    private fun write(it: Vertex): Int { return it.id }
}

internal class VertexVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Vertex>
) : MutableVertexProperty<Vertex>, VertexChangeListener {

    private val property = Int2IntHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Vertex> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Vertex {
        return read(property.getOrPut(vertex.id) { write(initializer.apply(vertex)) })
    }

    override fun set(vertex: Vertex, value: Vertex) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Vertex): Vertex {
        return read(property.replaceOrSet(vertex.id, write(value)) { write(initializer.apply(vertex)) })
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldValue = property.removeOrElse(oldVertex.id) { property.remove(newVertex.id); return }
        property[newVertex.id] = oldValue
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Int): Vertex { return Vertex(it) }
    private fun write(it: Vertex): Int { return it.id }
}

internal class ImmutableVertexVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Vertex>
) : MutableVertexProperty<Vertex> {

    private val property = Int2IntHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
            property[vertex.id] = write(defaultValueFunction.apply(vertex))
        }
    }

    override val type: PropertyType<Vertex> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Vertex {
        try {
            return read(property.getValue(vertex.id))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Vertex) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Vertex): Vertex {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Vertex { return Vertex(it) }
    private fun write(it: Vertex): Int { return it.id }
}
