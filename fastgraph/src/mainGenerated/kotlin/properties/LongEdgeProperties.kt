package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Long2LongHashMap
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


import io.github.sooniln.fastgraph.MutableEdgeKeyProperty


internal class LongArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Long>,
) : MutableEdgeProperty<Long>, EdgeChangeListener {

    private val property = LongArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { onEdgeAdded(it) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Long> get() = staticTypeOf()

    override fun get(edge: Edge): Long {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Long): Long {
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

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class ImmutableLongArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Long>,
) : MutableEdgeProperty<Long> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = LongArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: StaticType<Long> get() = staticTypeOf()

    override fun get(edge: Edge): Long {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Long): Long {
        try {
            val oldValue = read(property[edge.lowBits])
            property[edge.lowBits] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class LongMapEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Long>
) : MutableEdgeProperty<Long>, EdgeChangeListener {

    private val property = Long2LongHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Long> get() = staticTypeOf()

    override fun get(edge: Edge): Long {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Long) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Long): Long {
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

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}

internal class ImmutableLongMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Long>
) : MutableEdgeProperty<Long> {

    private val property = Long2LongHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: StaticType<Long> get() = staticTypeOf()

    override fun get(edge: Edge): Long {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Long): Long {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Long): Long { return it }
    private fun write(it: Long): Long { return it }
}



internal class WrapperLongEdgeKeyProperty(
    private val property: MutableEdgeProperty<Long>
) : MutableEdgeKeyProperty<Long>, MutableEdgeProperty<Long> by property {
    private val keyMap = Long2LongHashMap()

    init {
        graph.edges.foreach { edge ->
            val key = get(edge)
            if (keyMap.containsKey(key)) throw IllegalArgumentException("\"$key\" is not unique")
            keyMap[key] = edge.id
        }
    }

    override fun hasEdge(key: Long): Boolean = keyMap.containsKey(key)
    override fun getEdge(key: Long): Edge = Edge(keyMap.getValue(key))

    override fun set(edge: Edge, value: Long) {
        put(edge, value)
    }

    override fun put(edge: Edge, value: Long): Long {
        val oldEdgeId = keyMap[value]
        if (!keyMap.isDefaultValue(oldEdgeId) || keyMap.containsKey(value)) {
            val oldEdge = Edge(oldEdgeId)
            if (oldEdge == edge) return value
            throw IllegalArgumentException("\"$value\" is already associated with $oldEdge")
        }

        val oldValue = property[edge]
        check(keyMap.remove(oldValue, edge.id))
        property[edge] = value
        keyMap[value] = edge.id
        return oldValue
    }

    override fun copy(defaultValueFunction: EdgeFunction<Long>): MutableEdgeKeyProperty<Long> {
        return super<MutableEdgeKeyProperty>.copy(defaultValueFunction)
    }
}


