package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Int2LongHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.propertyTypeOf


import io.github.sooniln.fastcollect.Long2IntHashMap

import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty


internal class LongIdentityIndexedVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long>, VertexChangeListener {

    private val property = LongArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Long {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Long): Long {
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

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class LongIndexedVertexProperty(
    override val graph: Graph,
    private val vertices: IndexedVertexSet,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long>, VertexChangeListener {

    private val property = LongArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(vertices.size)
        for (vertex in vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Long {
        try {
            return read(property[vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        try {
            property[vertices.indexOf(vertex)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Long): Long {
        try {
            return read(property.replace(vertices.indexOf(vertex), write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertices.indexOf(vertex) == property.size)
        property.add(write(initializer.apply(vertex)))
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(vertices.indexOf(vertex) == property.lastIndex)
        property.removeAt(property.lastIndex)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(vertices.indexOf(oldVertex) == property.lastIndex)
        property[vertices.indexOf(newVertex)] = property.removeAt(property.lastIndex)
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class ImmutableLongIdentityIndexedVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long> {

    private val property = LongArray(graph.vertices.size) { vertexId ->
        write(defaultValueFunction.apply(Vertex(vertexId)))
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Long {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Long): Long {
        try {
            val oldValue = read(property[vertex.id])
            property[vertex.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class ImmutableLongIndexedVertexProperty(
    override val graph: ImmutableGraph,
    private val vertices: IndexedVertexSet,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long> {

    private val property = LongArray(vertices.size) { index ->
        write(defaultValueFunction.apply(vertices[index]))
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Long {
        try {
            return read(property[vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        try {
            property[vertices.indexOf(vertex)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Long): Long {
        try {
            val index = vertices.indexOf(vertex)
            val oldValue = read(property[index])
            property[index] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class LongVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Long>
) : MutableVertexProperty<Long>, VertexChangeListener {

    private val property = Int2LongHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Long {
        return read(property.getOrPut(vertex.id) { write(initializer.apply(vertex)) })
    }

    override fun set(vertex: Vertex, value: Long) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Long): Long {
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

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class ImmutableLongVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Long>
) : MutableVertexProperty<Long> {

    private val property = Int2LongHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
            property[vertex.id] = write(defaultValueFunction.apply(vertex))
        }
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Long {
        try {
            return read(property.getValue(vertex.id))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Long): Long {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}
