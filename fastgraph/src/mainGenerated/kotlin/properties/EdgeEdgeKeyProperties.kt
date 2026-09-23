package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Long2LongHashMap
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastcollect.Long2IntHashMap


internal class EdgeIdentityIndexedEdgeKeyProperty(
    override val graph: Graph,
) : MutableEdgeKeyProperty<Edge>, EdgeChangeListener {

    private val keys = LongArrayList()
    private val index = Long2IntHashMap()

    init {
        keys.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "edges have no key: ${graph.edges.filter { edge ->                val edge = IdentityIndexedEdge.from(edge);                index.getOrDefault(keys[edge.id], -1) != edge.id            }}"
        }
    }

    override fun get(edge: Edge): Edge {
        checkComplete()
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(keys[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        val storeValue = write(value)
        val existingId = index[storeValue]
        if (!index.isDefaultValue(existingId) || index.containsKey(storeValue)) {
            if (existingId == edge.id) return
            val existingEdge = IdentityIndexedEdge(existingId).toEdge()
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.replace(edge.id, storeValue)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
        index.remove(oldValue, edge.id)
        index[storeValue] = edge.id
    }

    override fun put(edge: Edge, value: Edge): Edge {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Edge): Boolean {
        checkComplete()
        return index.containsKey(write(key))
    }

    override fun getEdge(key: Edge): Edge {
        checkComplete()
        return IdentityIndexedEdge(index.getValue(write(key))).toEdge()
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

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class EdgeIndexedEdgeKeyProperty(
    override val graph: Graph,
    private val edges: IndexedEdgeSet,
) : MutableEdgeKeyProperty<Edge>, EdgeChangeListener {

    private val keys = LongArrayList()
    private val index = Long2IntHashMap()

    init {
        keys.ensureCapacity(edges.size)
        for (edge in edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == keys.size) {
            "edges have no key: ${edges.filter { val i = edges.indexOf(it); index.getOrDefault(keys[i], -1) != i }}"
        }
    }

    override fun get(edge: Edge): Edge {
        checkComplete()
        try {
            return read(keys[edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        val edgeIndex = edges.indexOf(edge)
        val storeValue = write(value)
        val existingIndex = index[storeValue]
        if (!index.isDefaultValue(existingIndex) || index.containsKey(storeValue)) {
            if (existingIndex == edgeIndex) return
            val existingEdge = edges[existingIndex]
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.replace(edgeIndex, storeValue)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
        index.remove(oldValue, edgeIndex)
        index[storeValue] = edgeIndex
    }

    override fun put(edge: Edge, value: Edge): Edge {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Edge): Boolean {
        checkComplete()
        return index.containsKey(write(key))
    }

    override fun getEdge(key: Edge): Edge {
        checkComplete()
        return edges[index.getValue(write(key))]
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edges.indexOf(edge) == keys.size)
        keys.add(0)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edgeIndex = edges.indexOf(edge)
        check(edgeIndex == keys.lastIndex)
        index.remove(keys.removeAt(edgeIndex), edgeIndex)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldIndex = edges.indexOf(oldEdge)
        val newIndex = edges.indexOf(newEdge)
        check(oldIndex == keys.lastIndex)
        index.remove(keys[newIndex], newIndex)
        val moved = keys.removeAt(oldIndex)
        keys[newIndex] = moved
        if (index.remove(moved, oldIndex)) index[moved] = newIndex
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = keys.ensureCapacity(edgeCapacity)
    override fun trimToSize() = keys.trimToSize()

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class EdgeEdgeKeyProperty(
    override val graph: Graph,
) : MutableEdgeKeyProperty<Edge>, EdgeChangeListener {

    private val keys = Long2LongHashMap()
    private val index = Long2LongHashMap()

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    private fun checkComplete() {
        check(index.size == graph.edges.size) {
            "edges have no key: ${graph.edges.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(edge: Edge): Edge {
        checkComplete()
        return read(keys.getValue(edge.id))
    }

    override fun set(edge: Edge, value: Edge) {
        val storeValue = write(value)
        val existingId = index[storeValue]
        if (!index.isDefaultValue(existingId) || index.containsKey(storeValue)) {
            if (existingId == edge.id) return
            val existingEdge = Edge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        index.remove(keys.put(edge.id, storeValue), edge.id)
        index[storeValue] = edge.id
    }

    override fun put(edge: Edge, value: Edge): Edge {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: Edge): Boolean {
        checkComplete()
        return index.containsKey(write(key))
    }

    override fun getEdge(key: Edge): Edge {
        checkComplete()
        return Edge(index.getValue(write(key)))
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

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}
