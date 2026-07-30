package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.longs.Long2AnyHashMap
import io.github.sooniln.fastcollect.longs.getOrPut
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal fun <V> createImmutableGraphEdgeProperty(
    graph: ImmutableGraph,
    initializer: EdgeInitializer<V>
): MutableEdgeProperty<V> {
    return if (graph is IndexedEdgeGraph) {
        ImmutableArrayEdgeProperty(graph, initializer)
    } else {
        ImmutableMapEdgeProperty(graph, initializer)
    }
}

private open class ImmutableArrayEdgeProperty<G, V>(
    override val graph: G,
    initializer: EdgeInitializer<V>,
) : MutableEdgeProperty<V> where G : ImmutableGraph, G : IndexedEdgeGraph {

    @Suppress("UNCHECKED_CAST")
    private val property = arrayOfNulls<Any>(graph.edges.size) as Array<V>

    init {
        graph.edges.foreach { edge ->
            property[edge.lowBits] = initializer.initialize(edge)
        }
    }

    final override fun get(edge: Edge): V {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            context(graph) { throwIllegalEdge(edge, e) }
        }
    }

    final override fun set(edge: Edge, value: V) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            context(graph) { throwIllegalEdge(edge, e) }
        }
    }

    final override fun put(edge: Edge, value: V): V {
        try {
            val oldValue = property[edge.lowBits]
            property[edge.lowBits] = value
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            context(graph) { throwIllegalEdge(edge, e) }
        }
    }
}

private class ImmutableMapEdgeProperty<V>(
    override val graph: ImmutableGraph,
    initializer: EdgeInitializer<V>
) : MutableEdgeProperty<V> {

    private val property = Long2AnyHashMap<V>()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge -> property[edge.longValue] = initializer.initialize(edge) }
    }

    override fun get(edge: Edge): V {
        return property.getOrPut(edge.longValue) { context(graph) { throwIllegalEdge(edge) } }
    }

    override fun set(edge: Edge, value: V) {
        property.replace(edge.longValue, value)
    }

    override fun put(edge: Edge, value: V): V {
        return property.replace(edge.longValue, value)
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)

    override fun trimToSize() = property.trimToSize()
}
