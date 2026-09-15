package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Long2LongHashMap
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

import io.github.sooniln.fastgraph.MutableEdgeKeyProperty

internal class LongArrayEdgeKeyProperty(
    override val graph: IndexedEdgeGraph,
) : MutableEdgeKeyProperty<Long>, EdgeChangeListener {

    private val keys = LongArrayList()
    private val index = Long2LongHashMap()

    init {
        keys.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "edges have no key: ${graph.edges.filter { index.getOrDefault(keys[it.lowBits], -1L) != it.id }}"
        }
    }

    override fun get(edge: Edge): Long {
        checkComplete()
        try {
            return keys[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
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

    override fun put(edge: Edge, value: Long): Long {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Long): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getEdge(key: Long): Edge {
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

internal class LongMapEdgeKeyProperty(
    override val graph: Graph,
) : MutableEdgeKeyProperty<Long>, EdgeChangeListener {

    private val keys = Long2LongHashMap()
    private val index = Long2LongHashMap()

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == graph.edges.size) {
            "edges have no key: ${graph.edges.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(edge: Edge): Long {
        checkComplete()
        return keys.getValue(edge.id)
    }

    override fun set(edge: Edge, value: Long) {
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == edge.id) return
            val existingEdge = Edge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        index.remove(keys.put(edge.id, value), edge.id)
        index[value] = edge.id
    }

    override fun put(edge: Edge, value: Long): Long {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Long): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getEdge(key: Long): Edge {
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
