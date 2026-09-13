package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.Int2IntHashMap
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


import io.github.sooniln.fastgraph.MutableVertexKeyProperty


internal class IntArrayVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Int>,
) : MutableVertexProperty<Int>, VertexChangeListener {

    private val property = IntArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Int {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Int): Int {
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class ImmutableIntArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Int>,
) : MutableVertexProperty<Int> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = IntArray(graph.vertices.size) { vertexId ->
        write(defaultValueFunction.apply(Vertex(vertexId)))
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Int {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Int): Int {
        try {
            val oldValue = read(property[vertex.id])
            property[vertex.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class IntMapVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Int>
) : MutableVertexProperty<Int>, VertexChangeListener {

    private val property = Int2IntHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Int {
        return read(property.getOrPut(vertex.id) { write(initializer.apply(vertex)) })
    }

    override fun set(vertex: Vertex, value: Int) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Int): Int {
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class ImmutableIntMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Int>
) : MutableVertexProperty<Int> {

    private val property = Int2IntHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
            property[vertex.id] = write(defaultValueFunction.apply(vertex))
        }
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Int {
        try {
            return read(property.getValue(vertex.id))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Int): Int {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}



internal class IntArrayVertexKeyProperty(
    override val graph: IndexedVertexGraph,
) : MutableVertexKeyProperty<Int>, VertexChangeListener {

    private val keys = IntArrayList()
    private val index = Int2IntHashMap()

    init {
        keys.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "vertices have no key: ${graph.vertices.filter { index.getOrDefault(keys[it.id], -1) != it.id }}"
        }
    }

    override fun get(vertex: Vertex): Int {
        checkComplete()
        try {
            return keys[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
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

    override fun put(vertex: Vertex, value: Int): Int {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Int): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: Int): Vertex {
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

internal class IntMapVertexKeyProperty(
    override val graph: Graph,
) : MutableVertexKeyProperty<Int>, VertexChangeListener {

    private val keys = Int2IntHashMap()
    private val index = Int2IntHashMap()

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == graph.vertices.size) {
            "vertices have no key: ${graph.vertices.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(vertex: Vertex): Int {
        checkComplete()
        return keys.getValue(vertex.id)
    }

    override fun set(vertex: Vertex, value: Int) {
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        index.remove(keys.put(vertex.id, value), vertex.id)
        index[value] = vertex.id
    }

    override fun put(vertex: Vertex, value: Int): Int {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Int): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: Int): Vertex {
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


