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
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class ArrayVertexProperty<T>(
    override val graph: IndexedVertexGraph,
    override val type: StaticType<T>,
    defaultValueFunction: VertexFunction<T>,
) : MutableVertexProperty<T>, VertexChangeListener {

    private val property = ArrayList<T>()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { onVertexAdded(it) }
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
    override val type: StaticType<T>,
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
    override val type: StaticType<T>,
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
    override val type: StaticType<T>,
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

internal class WrapperVertexKeyProperty<T>(
    private val property: MutableVertexProperty<T>
) : MutableVertexKeyProperty<T>, MutableVertexProperty<T> by property {
    private val keyMap = HashMap<T, Int>()

    init {
        graph.vertices.foreach { vertex ->
            val key = get(vertex)
            if (keyMap.containsKey(key)) throw IllegalArgumentException("\"$key\" is not unique")
            keyMap[key] = vertex.id
        }
    }

    override fun hasVertex(key: T): Boolean = keyMap.containsKey(key)
    override fun getVertex(key: T): Vertex = Vertex(keyMap.getValue(key))

    override fun set(vertex: Vertex, value: T) {
        put(vertex, value)
    }

    override fun put(vertex: Vertex, value: T): T {
        val oldVertexId = keyMap[value]
        if (oldVertexId != null) {
            val oldVertex = Vertex(oldVertexId)
            if (oldVertex == vertex) return value
            throw IllegalArgumentException("\"$value\" is already associated with $oldVertex")
        }

        val oldValue = property[vertex]
        check(keyMap.remove(oldValue, vertex.id))
        property[vertex] = value
        keyMap[value] = vertex.id
        return oldValue
    }

    override fun copy(defaultValueFunction: VertexFunction<T>): MutableVertexKeyProperty<T> {
        return super<MutableVertexKeyProperty>.copy(defaultValueFunction)
    }
}
