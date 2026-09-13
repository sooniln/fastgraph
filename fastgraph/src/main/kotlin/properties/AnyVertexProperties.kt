package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class ArrayVertexProperty<T>(
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

internal class ImmutableArrayVertexProperty<G, T>(
    override val graph: G,
    override val type: PropertyType<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T> where G : ImmutableGraph, G : IndexedVertexGraph {

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

internal class MapVertexProperty<T>(
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

internal class ImmutableMapVertexProperty<T>(
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

/**
 * Slots in [keys] for unkeyed vertices hold an arbitrary placeholder (null may itself be a valid key), so all removals
 * from [index] are conditional on the entry pointing at the vertex in question.
 */
@Suppress("UNCHECKED_CAST")
internal class ArrayVertexKeyProperty<T>(
    override val graph: IndexedVertexGraph,
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
internal class MapVertexKeyProperty<T>(
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
