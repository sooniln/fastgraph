package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.longs.Long2AnyHashMap
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

// TODO: lazy/no initialization?
internal class ArrayEdgeProperty<T>(
    override val graph: IndexedEdgeGraph,
    override val type: Class<T>,
    initializer: EdgeInitializer<T>,
) : MutableEdgeProperty<T>, EdgeChangeListener {

    private val property = ArrayList<T>()
    private val initializer = if (graph is ImmutableGraph) null else initializer

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property.add(initializer.initialize(edge))
        }

        if (this.initializer != null) {
            graph.registerEdgeChangeListener(this)
        }
    }

    override fun get(edge: Edge): T {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == property.size)
        property.add(initializer!!.initialize(edge))
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(edge.lowBits == property.lastIndex)
        property.removeAt(edge.lowBits)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(oldEdge.lowBits == property.lastIndex)
        property[newEdge.lowBits] = property.removeAt(oldEdge.lowBits)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()
}

// TODO: lazy/no initialization?
internal class MapEdgeProperty<T>(
    override val graph: Graph,
    override val type: Class<T>,
    initializer: EdgeInitializer<T>
) : MutableEdgeProperty<T>, EdgeChangeListener {

    // we take advantage of the default value to compress the map where possible
    private val property = Long2AnyHashMap<T>()
    private val initializer = if (graph is ImmutableGraph) null else initializer

    init {
        graph.edges.foreach { edge ->
            val value = initializer.initialize(edge)
            if (!property.isDefaultValue(value)) {
                property[edge.id] = value
            }
        }

        if (this.initializer != null) {
            graph.registerEdgeChangeListener(this)
        }
    }

    override fun get(edge: Edge): T {
        @Suppress("UNCHECKED_CAST")
        return property[edge.id] as T
    }

    override fun set(edge: Edge, value: T) {
        if (property.isDefaultValue(value)) {
            property.remove(edge.id)
        } else {
            property[edge.id] = value
        }
    }

    override fun put(edge: Edge, value: T): T {
        return if (property.isDefaultValue(value)) {
            @Suppress("UNCHECKED_CAST")
            property.remove(edge.id) as T
        } else {
            @Suppress("UNCHECKED_CAST")
            property.put(edge.id, value) as T
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        val value = initializer!!.initialize(edge)
        if (!property.isDefaultValue(value)) {
            property[edge.id] = value
        }
    }

    override fun onEdgeRemoved(edge: Edge) {
        property.remove(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldValue = property.remove(oldEdge.id)
        if (!property.isDefaultValue(oldValue)) {
            @Suppress("UNCHECKED_CAST")
            property[newEdge.id] = oldValue as T
        }
    }

    override fun trimToSize() = property.trimToSize()
}
