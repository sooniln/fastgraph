package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Long2LongHashMap
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdge
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastcollect.Long2IntHashMap

import io.github.sooniln.fastgraph.MutableEdgeKeyProperty

internal class LongArrayEdgeKeyProperty(
    override val graph: IndexedEdgeGraph,
) : MutableEdgeKeyProperty<Long>, EdgeChangeListener {

    private val keys = LongArrayList()
    private val index = Long2IntHashMap()

    init {
        keys.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Long> get() = propertyTypeOf()

    private fun checkComplete() {

        check(index.size == keys.size) {
            "edges have no key: ${graph.edges.filter { edge ->                val edge = IndexedEdge.from(edge);                index.getOrDefault(keys[edge.id], -1) != edge.id            }}"
        }
    }

    override fun get(edge: Edge): Long {
        checkComplete()
        val edge = IndexedEdge.from(edge)
        try {
            return keys[edge.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
        val edge = IndexedEdge.from(edge)
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == edge.id) return
            val existingEdge = IndexedEdge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.replace(edge.id, value)
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
        return IndexedEdge(index.getValue(key)).toEdge()
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IndexedEdge.from(edge).id == keys.size)
        keys.add(0)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edge = IndexedEdge.from(edge)
        check(edge.id == keys.lastIndex)
        index.remove(keys.removeAt(edge.id), edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldEdge = IndexedEdge.from(oldEdge)
        val newEdge = IndexedEdge.from(newEdge)
        check(oldEdge.id == keys.lastIndex)
        index.remove(keys[newEdge.id], newEdge.id)
        val moved = keys.removeAt(oldEdge.id)
        keys[newEdge.id] = moved
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
