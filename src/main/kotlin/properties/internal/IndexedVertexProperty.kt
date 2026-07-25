package io.github.sooniln.fastgraph.properties.internal

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.OwnedIndexedVertexProperty

private fun throwIllegalVertex(vertex: Vertex, cause: Throwable? = null): Nothing = throw IllegalArgumentException("vertex $vertex not found in graph", cause)

class ArrayVertexProperty<V>(override val graph: VertexIndexedVertexGraph, private val initializer: VertexInitializer<V>) : MutableVertexProperty<V>, OwnedIndexedVertexProperty<V>() {

    private val property = ArrayList<V>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            check(vertex.intValue == property.size)
            property.add(initializer.initialize(vertex))
        }
    }

    override fun get(vertex: Vertex): V {
        try {
            return property[vertex.intValue]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: V) {
        try {
            property[vertex.intValue] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: V): V {
        try {
            return property.set(vertex.intValue, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexReindexed(oldVertexIndex: Int, newVertexIndex: Int) {
        check(oldVertexIndex == property.size)
        if (newVertexIndex != oldVertexIndex) {
            property[newVertexIndex] = property[oldVertexIndex]
        }
        property.removeAt(oldVertexIndex)
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.intValue == property.size)
        property.add(initializer.initialize(vertex))
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
    override fun trimToSize() = property.trimToSize()
}

class MapVertexProperty<V>(override val graph: Graph, private val sparse: Boolean, private val initializer: VertexInitializer<V>) : MutableVertexProperty<V>, OwnedIndexedVertexProperty<V>() {

    private val property = Int2AnyHashMap<V>()

    init {
        if (!sparse) {
            property.ensureCapacity(graph.vertices.size)
            graph.vertices.foreach { vertex -> property[vertex.intValue] = initializer.initialize(vertex) }
        }
    }

    override fun get(vertex: Vertex): V {
        return if (sparse) {
            check(graph.vertices.contains(vertex))
            property.getOrPut(vertex.intValue) { initializer.initialize(vertex) }
        } else {
            property.getOrPut(vertex.intValue) { throwIllegalVertex(vertex) }
        }
    }

    override fun set(vertex: Vertex, value: V) {
        if (sparse) {
            check(graph.vertices.contains(vertex))
            property[vertex.intValue] = value
        } else {
            property.replace(vertex.intValue, value)
        }
    }

    override fun put(vertex: Vertex, value: V): V {
        if (sparse) {
            check(graph.vertices.contains(vertex))
            return property.replaceOrElse(vertex.intValue, value) { initializer.initialize(vertex) }
        } else {
            return property.replace(vertex.intValue, value)
        }
    }

    override fun onVertexReindexed(oldVertexIndex: Int, newVertexIndex: Int) {
        if (newVertexIndex == oldVertexIndex) {
            property.remove(oldVertexIndex)
        } else {
            if (property.containsKey(oldVertexIndex)) {
                property[newVertexIndex] = property.removeKey(oldVertexIndex)
            }
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        if (!sparse) {
            property[vertex.intValue] = initializer.initialize(vertex)
        }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
    override fun trimToSize() = property.trimToSize()
}
