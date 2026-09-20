package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Long2LongHashMap
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastcollect.Long2IntHashMap

import io.github.sooniln.fastgraph.MutableEdgeKeyProperty

internal class LongIdentityIndexedEdgeKeyProperty(
    override val graph: IdentityIndexedEdgeGraph,
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
            "edges have no key: ${graph.edges.filter { edge ->                val edge = IdentityIndexedEdge.from(edge);                index.getOrDefault(keys[edge.id], -1) != edge.id            }}"
        }
    }

    override fun get(edge: Edge): Long {
        checkComplete()
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return keys[edge.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
        val edge = IdentityIndexedEdge.from(edge)
        val existingId = index[value]
        if (!index.isDefaultValue(existingId) || index.containsKey(value)) {
            if (existingId == edge.id) return
            val existingEdge = IdentityIndexedEdge(existingId)
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
        return IdentityIndexedEdge(index.getValue(key)).toEdge()
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IdentityIndexedEdge.from(edge).id == keys.size)
        keys.add(0)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        check(edge.id == keys.lastIndex)
        index.remove(keys.removeAt(edge.id), edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldEdge = IdentityIndexedEdge.from(oldEdge)
        val newEdge = IdentityIndexedEdge.from(newEdge)
        check(oldEdge.id == keys.lastIndex)
        index.remove(keys[newEdge.id], newEdge.id)
        val moved = keys.removeAt(oldEdge.id)
        keys[newEdge.id] = moved
        if (index.remove(moved, oldEdge.id)) index[moved] = newEdge.id
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = keys.ensureCapacity(edgeCapacity)
    override fun trimToSize() = keys.trimToSize()
}

internal class LongIndexedEdgeKeyProperty(
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
            "edges have no key: ${graph.edges.filter { val i = graph.edges.indexOf(it); index.getOrDefault(keys[i], -1) != i }}"
        }
    }

    override fun get(edge: Edge): Long {
        checkComplete()
        try {
            return keys[graph.edges.indexOf(edge)]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Long) {
        val edgeIndex = graph.edges.indexOf(edge)
        val existingIndex = index[value]
        if (!index.isDefaultValue(existingIndex) || index.containsKey(value)) {
            if (existingIndex == edgeIndex) return
            val existingEdge = graph.edges[existingIndex]
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.replace(edgeIndex, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
        index.remove(oldValue, edgeIndex)
        index[value] = edgeIndex
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
        return graph.edges[index.getValue(key)]
    }

    override fun onEdgeAdded(edge: Edge) {
        check(graph.edges.indexOf(edge) == keys.size)
        keys.add(0)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edgeIndex = graph.edges.indexOf(edge)
        check(edgeIndex == keys.lastIndex)
        index.remove(keys.removeAt(edgeIndex), edgeIndex)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldIndex = graph.edges.indexOf(oldEdge)
        val newIndex = graph.edges.indexOf(newEdge)
        check(oldIndex == keys.lastIndex)
        index.remove(keys[newIndex], newIndex)
        val moved = keys.removeAt(oldIndex)
        keys[newIndex] = moved
        if (index.remove(moved, oldIndex)) index[moved] = newIndex
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = keys.ensureCapacity(edgeCapacity)
    override fun trimToSize() = keys.trimToSize()
}

internal class LongEdgeKeyProperty(
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
