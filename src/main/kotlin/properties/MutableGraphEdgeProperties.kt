package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.longs.Long2AnyHashMap
import io.github.sooniln.fastcollect.longs.getOrPut
import io.github.sooniln.fastcollect.longs.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal fun <V> createMutableGraphEdgeProperty(
    graph: MutableGraph,
    initializer: EdgeInitializer<V>
): MutableGraphEdgeProperty<V> {
    return if (graph is IndexedEdgeGraph) {
        ArrayEdgeProperty(graph, initializer)
    } else {
        MapEdgeProperty(graph, initializer)
    }
}

/** The interface through which a [Graph] communicates updates to a linked [io.github.sooniln.fastgraph.EdgeProperty]. */
internal interface MutableGraphEdgeProperty<V> : MutableEdgeProperty<V> {

    override val graph: MutableGraph

    /** Invoked after the edge is added to the graph. */
    fun onEdgeAdded(edge: Edge)

    /** Invoked before the edge is removed from the graph. */
    fun onEdgeRemoved(edge: Edge)

    /**
     * Invoked before an edge ID is re-assigned. The caller guarantees that [oldEdge] != [newEdge]. The effect of this
     * method should be the same as if: (1) any property data previously associated with the new edge is removed (2) any
     * property data previous associated with the old edge should instead be associated with the new edge (3) no
     * property data should be associated with the old edge after completion.
     */
    fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge)
}

private open class ArrayEdgeProperty<G, V>(
    override val graph: G,
    private val initializer: EdgeInitializer<V>,
) : MutableEdgeProperty<V>, MutableGraphEdgeProperty<V> where G : MutableGraph, G : IndexedEdgeGraph {

    private val property = ArrayList<V>()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge -> onEdgeAdded(edge) }
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
            return property.set(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            context(graph) { throwIllegalEdge(edge, e) }
        }
    }

    final override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == property.size)
        property.add(initializer.initialize(edge))
    }

    final override fun onEdgeRemoved(edge: Edge) {
        check(edge.lowBits == property.lastIndex)
        property.removeAt(edge.lowBits)
    }

    final override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(oldEdge.lowBits == property.lastIndex)
        property[newEdge.lowBits] = property.removeAt(oldEdge.lowBits)
    }

    final override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)
    final override fun trimToSize() = property.trimToSize()
}

private class MapEdgeProperty<V>(
    override val graph: MutableGraph,
    private val initializer: EdgeInitializer<V>
) : MutableEdgeProperty<V>, MutableGraphEdgeProperty<V> {

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

    override fun onEdgeAdded(edge: Edge) {
        property[edge.longValue] = initializer.initialize(edge)
    }

    override fun onEdgeRemoved(edge: Edge) {
        property.remove(edge.longValue)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        property[newEdge.longValue] = property.removeOrElse(oldEdge.longValue) { return }
    }

    override fun ensureCapacity(capacity: Int) = property.ensureCapacity(capacity)

    override fun trimToSize() = property.trimToSize()
}
