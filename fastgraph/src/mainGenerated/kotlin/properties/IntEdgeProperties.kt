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
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge


import io.github.sooniln.fastcollect.Int2LongHashMap

import io.github.sooniln.fastgraph.MutableEdgeKeyProperty


internal class IntArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Int>,
) : MutableEdgeProperty<Int>, EdgeChangeListener {

    private val property = IntArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

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

    override val type: PropertyType<Int> get() = propertyTypeOf()

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

    override val type: PropertyType<Int> get() = propertyTypeOf()

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
        for (edge in graph.edges) {
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

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



internal class IntArrayEdgeKeyProperty(
    override val graph: IndexedEdgeGraph,
) : MutableEdgeKeyProperty<Int>, EdgeChangeListener {

    private val keys = IntArrayList()
    private val index = Int2LongHashMap()

    init {
        keys.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "edges have no key: ${graph.edges.filter { index.getOrDefault(keys[it.lowBits], -1L) != it.id }}"
        }
    }

    override fun get(edge: Edge): Int {
        checkComplete()
        try {
            return keys[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Int) {
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == edge.id) return
            val existingEdge = Edge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.replace(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
        index.remove(oldValue, edge.id)
        index[value] = edge.id
    }

    override fun put(edge: Edge, value: Int): Int {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Int): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getEdge(key: Int): Edge {
        checkComplete()
        return Edge(index.getValue(key))
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == keys.size)
        keys.add(0)
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(edge.lowBits == keys.lastIndex)
        index.remove(keys.removeAt(edge.lowBits), edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(oldEdge.lowBits == keys.lastIndex)
        index.remove(keys[newEdge.lowBits], newEdge.id)
        val moved = keys.removeAt(oldEdge.lowBits)
        keys[newEdge.lowBits] = moved
        if (index.remove(moved, oldEdge.id)) index[moved] = newEdge.id
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = keys.ensureCapacity(edgeCapacity)
    override fun trimToSize() = keys.trimToSize()
}

internal class IntMapEdgeKeyProperty(
    override val graph: Graph,
) : MutableEdgeKeyProperty<Int>, EdgeChangeListener {

    private val keys = Long2IntHashMap()
    private val index = Int2LongHashMap()

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Int> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == graph.edges.size) {
            "edges have no key: ${graph.edges.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(edge: Edge): Int {
        checkComplete()
        return keys.getValue(edge.id)
    }

    override fun set(edge: Edge, value: Int) {
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == edge.id) return
            val existingEdge = Edge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        index.remove(keys.put(edge.id, value), edge.id)
        index[value] = edge.id
    }

    override fun put(edge: Edge, value: Int): Int {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Int): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getEdge(key: Int): Edge {
        checkComplete()
        return Edge(index.getValue(key))
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        index.remove(keys.remove(edge.id), edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        index.remove(keys.remove(newEdge.id), newEdge.id)
        val moved = keys.removeOrElse(oldEdge.id) { return }
        keys[newEdge.id] = moved
        if (index.remove(moved, oldEdge.id)) index[moved] = newEdge.id
    }

    override fun trimToSize() = keys.trimToSize()
}


