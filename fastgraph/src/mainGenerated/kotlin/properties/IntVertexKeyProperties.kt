package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.propertyTypeOf


internal class IntIdentityIndexedVertexKeyProperty(
    override val graph: Graph,
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
            return read(keys[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        val storeValue = write(value)
        val existingId = index[storeValue]
        if (!index.isDefaultValue(existingId) || index.containsKey(storeValue)) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        val oldValue = try {
            keys.replace(vertex.id, storeValue)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
        index.remove(oldValue, vertex.id)
        index[storeValue] = vertex.id
    }

    override fun put(vertex: Vertex, value: Int): Int {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Int): Boolean {
        checkComplete()
        return index.containsKey(write(key))
    }

    override fun getVertex(key: Int): Vertex {
        checkComplete()
        return Vertex(index.getValue(write(key)))
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class IntIndexedVertexKeyProperty(
    override val graph: Graph,
    private val vertices: IndexedVertexSet,
) : MutableVertexKeyProperty<Int>, VertexChangeListener {

    private val keys = IntArrayList()
    private val index = Int2IntHashMap()

    init {
        keys.ensureCapacity(vertices.size)
        for (vertex in vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "vertices have no key: ${vertices.filter { val i = vertices.indexOf(it); index.getOrDefault(keys[i], -1) != i }}"
        }
    }

    override fun get(vertex: Vertex): Int {
        checkComplete()
        try {
            return read(keys[vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        val vertexIndex = vertices.indexOf(vertex)
        val storeValue = write(value)
        val existingIndex = index[storeValue]
        if (!index.isDefaultValue(existingIndex) || index.containsKey(storeValue)) {
            if (existingIndex == vertexIndex) return
            val existingVertex = vertices[existingIndex]
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        val oldValue = try {
            keys.replace(vertexIndex, storeValue)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
        index.remove(oldValue, vertexIndex)
        index[storeValue] = vertexIndex
    }

    override fun put(vertex: Vertex, value: Int): Int {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Int): Boolean {
        checkComplete()
        return index.containsKey(write(key))
    }

    override fun getVertex(key: Int): Vertex {
        checkComplete()
        return vertices[index.getValue(write(key))]
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertices.indexOf(vertex) == keys.size)
        keys.add(0)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        val vertexIndex = vertices.indexOf(vertex)
        check(vertexIndex == keys.lastIndex)
        index.remove(keys.removeAt(vertexIndex), vertexIndex)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldIndex = vertices.indexOf(oldVertex)
        val newIndex = vertices.indexOf(newVertex)
        check(oldIndex == keys.lastIndex)
        index.remove(keys[newIndex], newIndex)
        val moved = keys.removeAt(oldIndex)
        keys[newIndex] = moved
        if (index.remove(moved, oldIndex)) index[moved] = newIndex
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = keys.ensureCapacity(vertexCapacity)
    override fun trimToSize() = keys.trimToSize()

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class IntVertexKeyProperty(
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
        return read(keys.getValue(vertex.id))
    }

    override fun set(vertex: Vertex, value: Int) {
        val storeValue = write(value)
        val existingId = index[storeValue]
        if (!index.isDefaultValue(existingId) || index.containsKey(storeValue)) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        index.remove(keys.put(vertex.id, storeValue), vertex.id)
        index[storeValue] = vertex.id
    }

    override fun put(vertex: Vertex, value: Int): Int {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: Int): Boolean {
        checkComplete()
        return index.containsKey(write(key))
    }

    override fun getVertex(key: Int): Vertex {
        checkComplete()
        return Vertex(index.getValue(write(key)))
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}
