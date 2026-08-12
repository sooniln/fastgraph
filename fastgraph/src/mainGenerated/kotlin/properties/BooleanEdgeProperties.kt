package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ByteArrayList
import io.github.sooniln.fastcollect.Long2ByteHashMap
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

internal class BooleanArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Boolean>,
) : MutableEdgeProperty<Boolean>, EdgeChangeListener {

    private val property = ByteArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { onEdgeAdded(it) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(edge: Edge): Boolean {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Boolean) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Boolean): Boolean {
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

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}

internal class ImmutableBooleanArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Boolean>,
) : MutableEdgeProperty<Boolean> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ByteArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(edge: Edge): Boolean {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Boolean) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Boolean): Boolean {
        try {
            val oldValue = read(property[edge.lowBits])
            property[edge.lowBits] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}

internal class BooleanMapEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Boolean>
) : MutableEdgeProperty<Boolean>, EdgeChangeListener {

    private val property = Long2ByteHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(edge: Edge): Boolean {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Boolean) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Boolean): Boolean {
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

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}

internal class ImmutableBooleanMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Boolean>
) : MutableEdgeProperty<Boolean> {

    private val property = Long2ByteHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(edge: Edge): Boolean {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Boolean) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Boolean): Boolean {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}
