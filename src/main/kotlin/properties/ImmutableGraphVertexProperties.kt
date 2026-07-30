package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal fun <V> createImmutableGraphVertexProperty(
    graph: ImmutableGraph,
    initializer: VertexInitializer<V>
): MutableVertexProperty<V> {
    return if (graph is IndexedVertexGraph) {
        ImmutableArrayVertexProperty(graph, initializer)
    } else {
        ImmutableMapVertexProperty(graph, initializer)
    }
}

private open class ImmutableArrayVertexProperty<G, V>(
    override val graph: G,
    initializer: VertexInitializer<V>,
) : MutableVertexProperty<V> where G : ImmutableGraph, G : IndexedVertexGraph {

    @Suppress("UNCHECKED_CAST")
    private val property = arrayOfNulls<Any>(graph.vertices.size) as Array<V>

    init {
        graph.vertices.forEach { vertex ->
            property[vertex.intValue] = initializer.initialize(vertex)
        }
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
            val oldValue = property[vertex.intValue]
            property[vertex.intValue] = value
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }
}

private class ImmutableMapVertexProperty<V>(
    override val graph: ImmutableGraph,
    initializer: VertexInitializer<V>
) : MutableVertexProperty<V> {

    private val property = Int2AnyHashMap<V>()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex -> property[vertex.intValue] = initializer.initialize(vertex) }
    }

    override fun get(vertex: Vertex): V {
        return property.getOrPut(vertex.intValue) { throwIllegalVertex(vertex) }
    }

    override fun set(vertex: Vertex, value: V) {
        property.replace(vertex.intValue, value)
    }

    override fun put(vertex: Vertex, value: V): V {
        return property.replace(vertex.intValue, value)
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)

    override fun trimToSize() = property.trimToSize()
}
