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
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge


internal class ByteIdentityIndexedEdgeProperty(
    override val graph: IdentityIndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte>, EdgeChangeListener {

    private val property = ByteArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Byte> get() = propertyTypeOf()

    override fun get(edge: Edge): Byte {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            property[edge.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IdentityIndexedEdge.from(edge).id == property.size)
        property.add(write(initializer.apply(edge)))
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        check(edge.id == property.lastIndex)
        property.removeAt(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldEdge = IdentityIndexedEdge.from(oldEdge)
        val newEdge = IdentityIndexedEdge.from(newEdge)
        check(oldEdge.id == property.lastIndex)
        property[newEdge.id] = property.removeAt(oldEdge.id)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ByteIndexedEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte>, EdgeChangeListener {

    private val property = ByteArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Byte> get() = propertyTypeOf()

    override fun get(edge: Edge): Byte {
        try {
            return read(property[graph.edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        try {
            property[graph.edges.indexOf(edge)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            return read(property.replace(graph.edges.indexOf(edge), write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(graph.edges.indexOf(edge) == property.size)
        property.add(write(initializer.apply(edge)))
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(graph.edges.indexOf(edge) == property.lastIndex)
        property.removeAt(property.lastIndex)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(graph.edges.indexOf(oldEdge) == property.lastIndex)
        property[graph.edges.indexOf(newEdge)] = property.removeAt(property.lastIndex)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ImmutableByteIdentityIndexedEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte> where G : ImmutableGraph, G : IdentityIndexedEdgeGraph {

    private val property = ByteArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: PropertyType<Byte> get() = propertyTypeOf()

    override fun get(edge: Edge): Byte {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            property[edge.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            val oldValue = read(property[edge.id])
            property[edge.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ImmutableByteIndexedEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Byte>,
) : MutableEdgeProperty<Byte> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ByteArray(graph.edges.size) { index ->
        write(defaultValueFunction.apply(graph.edges[index]))
    }

    override val type: PropertyType<Byte> get() = propertyTypeOf()

    override fun get(edge: Edge): Byte {
        try {
            return read(property[graph.edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Byte) {
        try {
            property[graph.edges.indexOf(edge)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Byte): Byte {
        try {
            val index = graph.edges.indexOf(edge)
            val oldValue = read(property[index])
            property[index] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Byte): Byte { return it }
    private fun write(it: Byte): Byte { return it }
}

internal class ByteEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Byte>
) : MutableEdgeProperty<Byte>, EdgeChangeListener {

    private val property = Long2ByteHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Byte> get() = propertyTypeOf()

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

internal class ImmutableByteEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Byte>
) : MutableEdgeProperty<Byte> {

    private val property = Long2ByteHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: PropertyType<Byte> get() = propertyTypeOf()

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
