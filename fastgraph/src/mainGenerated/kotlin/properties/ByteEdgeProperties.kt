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
import io.github.sooniln.fastgraph.staticTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge


internal class ByteArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte>, EdgeChangeListener {

    private val property = ByteArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { onEdgeAdded(it) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Byte> get() = staticTypeOf()

    override fun get(edge: Edge): Byte {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
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

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ImmutableByteArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ByteArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: StaticType<Byte> get() = staticTypeOf()

    override fun get(edge: Edge): Byte {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            val oldValue = read(property[edge.lowBits])
            property[edge.lowBits] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ByteMapEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Byte>
) : MutableEdgeProperty<Byte>, EdgeChangeListener {

    private val property = Long2ByteHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Byte> get() = staticTypeOf()

    override fun get(edge: Edge): Byte {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Byte) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Byte): Byte {
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

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ImmutableByteMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Byte>
) : MutableEdgeProperty<Byte> {

    private val property = Long2ByteHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: StaticType<Byte> get() = staticTypeOf()

    override fun get(edge: Edge): Byte {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}


