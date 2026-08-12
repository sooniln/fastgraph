package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.Long2IntHashMap
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
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.staticTypeOf

internal class IntArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Int>,
) : MutableEdgeProperty<Int>, EdgeChangeListener {

    private val property = IntArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { onEdgeAdded(it) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(edge: Edge): Int {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Int) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Int): Int {
        try {
            return read(property.replace(edge.lowBits, write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == property.size)
        property.add(write(initializer.apply(edge)))
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class ImmutableIntArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Int>,
) : MutableEdgeProperty<Int> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = IntArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(edge: Edge): Int {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Int) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Int): Int {
        try {
            val oldValue = read(property[edge.lowBits])
            property[edge.lowBits] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class IntMapEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Int>
) : MutableEdgeProperty<Int>, EdgeChangeListener {

    private val property = Long2IntHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(edge: Edge): Int {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Int) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Int): Int {
        return read(property.replaceOrSet(edge.id, write(value)) { write(initializer.apply(edge)) })
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        property.remove(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldValue = property.removeOrElse(oldEdge.id) { return }
        property[newEdge.id] = oldValue
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class ImmutableIntMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Int>
) : MutableEdgeProperty<Int> {

    private val property = Long2IntHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(edge: Edge): Int {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Int) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Int): Int {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}
