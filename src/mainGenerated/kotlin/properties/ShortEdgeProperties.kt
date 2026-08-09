package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ShortArrayList
import io.github.sooniln.fastcollect.Long2ShortHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.lastIndex

internal class ShortArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Short>,
) : MutableEdgeProperty<Short>, EdgeChangeListener {

    private val property = ShortArrayList()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: Class<Short> = Short::class.java

    private fun ensureEdgeExists(edge: Edge): ShortArrayList {
        val index = edge.lowBits
        if (property.size <= index) {
            if (index >= graph.edges.size) throwIllegalEdge(graph, edge)
            property.ensureCapacity(index + 1)
            var i = property.size
            do {
                property.add(initializer.apply(graph.edges[i++]))
            } while (i <= index)
        }
        return property
    }

    override fun get(edge: Edge): Short {
        return ensureEdgeExists(edge)[edge.lowBits]
    }

    override fun set(edge: Edge, value: Short) {
        ensureEdgeExists(edge)[edge.lowBits] = value
    }

    override fun put(edge: Edge, value: Short): Short {
        return ensureEdgeExists(edge).replace(edge.lowBits, value)
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == graph.edges.lastIndex)
    }

    override fun onEdgeRemoved(edge: Edge) {
        if (edge.lowBits == property.lastIndex) {
            property.removeAt(edge.lowBits)
        } else {
            check(edge.lowBits == graph.edges.lastIndex)
        }
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        if (oldEdge.lowBits == property.lastIndex) {
            property[newEdge.lowBits] = property.removeAt(oldEdge.lowBits)
        } else {
            check(oldEdge.lowBits == graph.edges.lastIndex)
        }
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableShortArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Short>,
) : MutableEdgeProperty<Short> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ShortArrayList()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            assert(edge.lowBits == property.size)
            property.add(defaultValueFunction.apply(edge))
        }
    }

    override val type: Class<Short> = Short::class.java

    override fun get(edge: Edge): Short {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Short) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Short): Short {
        try {
            return property.replace(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}

internal class ShortMapEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Short>
) : MutableEdgeProperty<Short>, EdgeChangeListener {

    private val property = Long2ShortHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: Class<Short> = Short::class.java

    override fun get(edge: Edge): Short {
        return property.getOrPut(edge.id) { initializer.apply(edge) }
    }

    override fun set(edge: Edge, value: Short) {
        property[edge.id] = value
    }

    override fun put(edge: Edge, value: Short): Short {
        return property.replaceOrSet(edge.id, value) { initializer.apply(edge) }
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        property.remove(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldValue = property.removeOrElse(oldEdge.id) { return }
        property[newEdge.id] = oldValue
    }

    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableShortMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Short>
) : MutableEdgeProperty<Short> {

    private val property = Long2ShortHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = defaultValueFunction.apply(edge)
        }
    }

    override val type: Class<Short> = Short::class.java

    override fun get(edge: Edge): Short {
        try {
            return property.getValue(edge.id)
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Short) {
        property[edge.id] = value
    }

    override fun put(edge: Edge, value: Short): Short {
        try {
            return property.replace(edge.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}
