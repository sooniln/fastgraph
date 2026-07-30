package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastcollect.ints.removeOrElse
import io.github.sooniln.fastcollect.ints.replaceOrElse
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal fun <V> createMutableGraphVertexProperty(
    graph: MutableGraph,
    initializer: VertexInitializer<V>
): MutableGraphVertexProperty<V> {
    return if (graph is IndexedVertexGraph) {
        ArrayVertexProperty(graph, initializer)
    } else {
        MapVertexProperty(graph, initializer)
    }
}

/** The interface through which a [MutableGraph] communicates updates to a linked [io.github.sooniln.fastgraph.VertexProperty]. */
internal interface MutableGraphVertexProperty<V> : MutableVertexProperty<V> {

    override val graph: MutableGraph

    /** Invoked after the vertex is added to the graph. */
    fun onVertexAdded(vertex: Vertex)

    /** Invoked before the vertex is removed from the graph. */
    fun onVertexRemoved(vertex: Vertex)

    /**
     * Invoked before a vertex ID is re-assigned. The caller guarantees that [oldVertex] != [newVertex]. The effect of
     * this method should be the same as if: (1) any property data previously associated with the new vertex is removed
     * (2) any property data previous associated with the old vertex should instead be associated with the new vertex
     * (3) no property data should be associated with the old vertex after completion.
     */
    fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex)
}

private open class ArrayVertexProperty<G, V>(
    override val graph: G,
    private val initializer: VertexInitializer<V>,
) : MutableVertexProperty<V>, MutableGraphVertexProperty<V> where G : MutableGraph, G : IndexedVertexGraph {

    private val property = ArrayList<V>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex -> onVertexAdded(vertex) }
    }

    final override fun get(vertex: Vertex): V {
        try {
            return property[vertex.intValue]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    final override fun set(vertex: Vertex, value: V) {
        try {
            property[vertex.intValue] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    final override fun put(vertex: Vertex, value: V): V {
        try {
            return property.set(vertex.intValue, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    final override fun onVertexAdded(vertex: Vertex) {
        check(vertex.intValue == property.size)
        property.add(initializer.initialize(vertex))
    }

    final override fun onVertexRemoved(vertex: Vertex) {
        check(vertex.intValue == property.lastIndex)
        property.removeAt(vertex.intValue)
    }

    final override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(oldVertex.intValue == property.lastIndex)
        property[newVertex.intValue] = property.removeAt(oldVertex.intValue)
    }

    final override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
    final override fun trimToSize() = property.trimToSize()
}

private class MapVertexProperty<V>(
    override val graph: MutableGraph,
    private val initializer: VertexInitializer<V>
) : MutableVertexProperty<V>, MutableGraphVertexProperty<V> {

    private val property = Int2AnyHashMap<V>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex -> property[vertex.intValue] = initializer.initialize(vertex) }
    }

    override fun get(vertex: Vertex): V {
        return property.getOrPut(vertex.intValue) { throwIllegalVertex(vertex) }
    }

    override fun set(vertex: Vertex, value: V) {
        check(graph.vertices.contains(vertex))
        property[vertex.intValue] = value
    }

    override fun put(vertex: Vertex, value: V): V {
        check(graph.vertices.contains(vertex))
        return property.replaceOrElse(vertex.intValue, value) { initializer.initialize(vertex) }
    }

    override fun onVertexAdded(vertex: Vertex) {
        property[vertex.intValue] = initializer.initialize(vertex)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.intValue)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        property[newVertex.intValue] = property.removeOrElse(oldVertex.intValue) { return }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)

    override fun trimToSize() = property.trimToSize()
}
