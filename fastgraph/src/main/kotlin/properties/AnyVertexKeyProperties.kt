package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

@Suppress("UNCHECKED_CAST")
internal class AnyIdentityIndexedVertexKeyProperty<T>(
    override val graph: Graph,
    override val type: PropertyType<T>,
) : MutableVertexKeyProperty<T>, VertexChangeListener {

    private val keys = ArrayList<T?>()
    private val index = HashMap<T, Int>()

    init {
        keys.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    private fun checkComplete() {
        check(index.size == keys.size) {
            "vertices have no key: ${graph.vertices.filter { index[keys[it.id] as T] != it.id }}"
        }
    }

    override fun get(vertex: Vertex): T {
        checkComplete()
        try {
            return keys[vertex.id] as T
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: T) {
        val existingId = index[value]
        if (existingId != null) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        val oldValue = try {
            keys.set(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
        index.remove(oldValue as T, vertex.id)
        index[value] = vertex.id
    }

    override fun put(vertex: Vertex, value: T): T {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: T): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: T): Vertex {
        checkComplete()
        return Vertex(index.getValue(key))
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == keys.size)
        keys.add(null)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(vertex.id == keys.lastIndex)
        index.remove(keys.removeAt(vertex.id) as T, vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(oldVertex.id == keys.lastIndex)
        index.remove(keys[newVertex.id] as T, newVertex.id)
        val moved = keys.removeAt(oldVertex.id) as T
        keys[newVertex.id] = moved
        if (index.remove(moved, oldVertex.id)) index[moved] = newVertex.id
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = keys.ensureCapacity(vertexCapacity)
    override fun trimToSize() = keys.trimToSize()
}

@Suppress("UNCHECKED_CAST")
internal class AnyIndexedVertexKeyProperty<T>(
    override val graph: Graph,
    private val vertices: IndexedVertexSet,
    override val type: PropertyType<T>,
) : MutableVertexKeyProperty<T>, VertexChangeListener {

    private val keys = ArrayList<T?>()
    private val index = HashMap<T, Int>()

    init {
        keys.ensureCapacity(vertices.size)
        for (vertex in vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    private fun checkComplete() {
        check(index.size == keys.size) {
            "vertices have no key: ${vertices.filter {
                val i = vertices.indexOf(it)
                index[keys[i] as T] != i
            }}"
        }
    }

    override fun get(vertex: Vertex): T {
        checkComplete()
        try {
            return keys[vertices.indexOf(vertex)] as T
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: T) {
        val vertexIndex = vertices.indexOf(vertex)
        val existingIndex = index[value]
        if (existingIndex != null) {
            if (existingIndex == vertexIndex) return
            val existingVertex = vertices[existingIndex]
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        val oldValue = try {
            keys.set(vertexIndex, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
        index.remove(oldValue as T, vertexIndex)
        index[value] = vertexIndex
    }

    override fun put(vertex: Vertex, value: T): T {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: T): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: T): Vertex {
        checkComplete()
        return vertices[index.getValue(key)]
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertices.indexOf(vertex) == keys.size)
        keys.add(null)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        val vertexIndex = vertices.indexOf(vertex)
        check(vertexIndex == keys.lastIndex)
        index.remove(keys.removeAt(vertexIndex) as T, vertexIndex)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldIndex = vertices.indexOf(oldVertex)
        val newIndex = vertices.indexOf(newVertex)
        check(oldIndex == keys.lastIndex)
        index.remove(keys[newIndex] as T, newIndex)
        val moved = keys.removeAt(oldIndex) as T
        keys[newIndex] = moved
        if (index.remove(moved, oldIndex)) index[moved] = newIndex
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = keys.ensureCapacity(vertexCapacity)
    override fun trimToSize() = keys.trimToSize()
}

@Suppress("UNCHECKED_CAST")
internal class AnyVertexKeyProperty<T>(
    override val graph: Graph,
    override val type: PropertyType<T>,
) : MutableVertexKeyProperty<T>, VertexChangeListener {

    private val keys = Int2AnyHashMap<T>()
    private val index = HashMap<T, Int>()

    init {
        graph.registerVertexChangeListener(this)
    }

    private fun checkComplete() {
        check(index.size == graph.vertices.size) {
            "vertices have no key: ${graph.vertices.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(vertex: Vertex): T {
        checkComplete()
        return keys.getValue(vertex.id)
    }

    override fun set(vertex: Vertex, value: T) {
        val existingId = index[value]
        if (existingId != null) {
            if (existingId == vertex.id) return
            val existingVertex = Vertex(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingVertex")
        }

        index.remove(keys.put(vertex.id, value) as T, vertex.id)
        index[value] = vertex.id
    }

    override fun put(vertex: Vertex, value: T): T {
        val oldValue = get(vertex)
        set(vertex, value)
        return oldValue
    }

    override fun hasVertex(key: T): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getVertex(key: T): Vertex {
        checkComplete()
        return Vertex(index.getValue(key))
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        index.remove(keys.remove(vertex.id) as T, vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        index.remove(keys.remove(newVertex.id) as T, newVertex.id)
        val moved = keys.removeOrElse(oldVertex.id) { return }
        keys[newVertex.id] = moved
        if (index.remove(moved, oldVertex.id)) index[moved] = newVertex.id
    }

    override fun trimToSize() = keys.trimToSize()
}
