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

    override val type: Class<Byte> = Byte::class.java

    override fun get(edge: Edge): Byte {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            return property.replace(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == property.size)
        property.add(initializer.apply(edge))
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

internal class ImmutableByteArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ByteArrayList()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            assert(edge.lowBits == property.size)
            property.add(defaultValueFunction.apply(edge))
        }
    }

    override val type: Class<Byte> = Byte::class.java

    override fun get(edge: Edge): Byte {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            return property.replace(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
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

    override val type: Class<Byte> = Byte::class.java

    override fun get(edge: Edge): Byte {
        return property.getOrPut(edge.id) { initializer.apply(edge) }
    }

    override fun set(edge: Edge, value: Byte) {
        property[edge.id] = value
    }

    override fun put(edge: Edge, value: Byte): Byte {
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

internal class ImmutableByteMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Byte>
) : MutableEdgeProperty<Byte> {

    private val property = Long2ByteHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = defaultValueFunction.apply(edge)
        }
    }

    override val type: Class<Byte> = Byte::class.java

    override fun get(edge: Edge): Byte {
        try {
            return property.getValue(edge.id)
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        property[edge.id] = value
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            return property.replace(edge.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}
