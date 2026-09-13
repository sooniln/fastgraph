package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Int2LongHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalVertex


import io.github.sooniln.fastcollect.Long2IntHashMap

import io.github.sooniln.fastgraph.MutableVertexKeyProperty


internal class LongArrayVertexProperty(
    override val graph: IndexedVertexGraph,
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

internal class ImmutableLongArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long> where G : ImmutableGraph, G : IndexedVertexGraph {

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

internal class LongMapVertexProperty(
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
        val oldValue = property.removeOrElse(oldVertex.id) { return }
        property[newVertex.id] = oldValue
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class ImmutableLongMapVertexProperty(
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



internal class LongArrayVertexKeyProperty(
    override val graph: IndexedVertexGraph,
) : MutableVertexKeyProperty<Long>, VertexChangeListener {

    private val keys = LongArrayList()
    private val index = Long2IntHashMap()

    init {
        keys.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "vertices have no key: ${graph.vertices.filter { index.getOrDefault(keys[it.id], -1) != it.id }}"
        }
    }

    override fun get(vertex: Vertex): Long {
        checkComplete()
        try {
            return keys[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        val oldValue = try {
            keys.replace(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
        index.remove(oldValue, vertex.id)
        index[value] = vertex.id
    }

    override fun put(vertex: Vertex, value: Long): Long {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Long): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: Long): Vertex {
        checkComplete()
        return Vertex(index.getValue(key))
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == keys.size)
        keys.add(0)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(vertex.id == keys.lastIndex)
        index.remove(keys.removeAt(vertex.id), vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(oldVertex.id == keys.lastIndex)
        index.remove(keys[newVertex.id], newVertex.id)
        val moved = keys.removeAt(oldVertex.id)
        keys[newVertex.id] = moved
        if (index.remove(moved, oldVertex.id)) index[moved] = newVertex.id
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = keys.ensureCapacity(vertexCapacity)
    override fun trimToSize() = keys.trimToSize()
}

internal class LongMapVertexKeyProperty(
    override val graph: Graph,
) : MutableVertexKeyProperty<Long>, VertexChangeListener {

    private val keys = Int2LongHashMap()
    private val index = Long2IntHashMap()

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == graph.vertices.size) {
            "vertices have no key: ${graph.vertices.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(vertex: Vertex): Long {
        checkComplete()
        return keys.getValue(vertex.id)
    }

    override fun set(vertex: Vertex, value: Long) {
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        index.remove(keys.put(vertex.id, value), vertex.id)
        index[value] = vertex.id
    }

    override fun put(vertex: Vertex, value: Long): Long {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Long): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: Long): Vertex {
        checkComplete()
        return Vertex(index.getValue(key))
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        index.remove(keys.remove(vertex.id), vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        index.remove(keys.remove(newVertex.id), newVertex.id)
        val moved = keys.removeOrElse(oldVertex.id) { return }
        keys[newVertex.id] = moved
        if (index.remove(moved, oldVertex.id)) index[moved] = newVertex.id
    }

    override fun trimToSize() = keys.trimToSize()
}


