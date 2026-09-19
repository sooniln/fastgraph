package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Long2AnyHashMap
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdge
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

@Suppress("UNCHECKED_CAST")
internal class ArrayEdgeKeyProperty<T>(
    override val graph: IndexedEdgeGraph,
    override val type: PropertyType<T>,
) : MutableEdgeKeyProperty<T>, EdgeChangeListener {

    private val keys = ArrayList<T?>()
    private val index = HashMap<T, Int>()

    init {
        keys.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    private fun checkComplete() {
        check(index.size == keys.size) {
            "edges have no key: ${graph.edges.filter { edge ->
                val edge = IndexedEdge.from(edge)
                index[keys[edge.id] as T] != edge.id
            }}"
        }
    }

    override fun get(edge: Edge): T {
        checkComplete()
        val edge = IndexedEdge.from(edge)
        try {
            return keys[edge.id] as T
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        val edge = IndexedEdge.from(edge)

        val existingId = index[value]
        if (existingId != null) {
            if (existingId == edge.id) return
            val existingEdge = IndexedEdge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.set(edge.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
        index.remove(oldValue as T, edge.id)
        index[value] = edge.id
    }

    override fun put(edge: Edge, value: T): T {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: T): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getEdge(key: T): Edge {
        checkComplete()
        return IndexedEdge(index.getValue(key)).toEdge()
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IndexedEdge.from(edge).id == keys.size)
        keys.add(null)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edge = IndexedEdge.from(edge)
        check(edge.id == keys.lastIndex)
        index.remove(keys.removeAt(edge.id) as T, edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldEdge = IndexedEdge.from(oldEdge)
        val newEdge = IndexedEdge.from(newEdge)
        check(oldEdge.id == keys.lastIndex)
        index.remove(keys[newEdge.id] as T, newEdge.id)
        val moved = keys.removeAt(oldEdge.id) as T
        keys[newEdge.id] = moved
        if (index.remove(moved, oldEdge.id)) index[moved] = newEdge.id
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = keys.ensureCapacity(edgeCapacity)
    override fun trimToSize() = keys.trimToSize()
}

@Suppress("UNCHECKED_CAST")
internal class MapEdgeKeyProperty<T>(
    override val graph: Graph,
    override val type: PropertyType<T>,
) : MutableEdgeKeyProperty<T>, EdgeChangeListener {

    private val keys = Long2AnyHashMap<T>()
    private val index = HashMap<T, Long>()

    init {
        graph.registerEdgeChangeListener(this)
    }

    private fun checkComplete() {
        check(index.size == graph.edges.size) {
            "edges have no key: ${graph.edges.filter { !keys.containsKey(it.id) }}"
        }
    }

    override fun get(edge: Edge): T {
        checkComplete()
        return keys.getValue(edge.id)
    }

    override fun set(edge: Edge, value: T) {
        val existingId = index[value]
        if (existingId != null) {
            if (existingId == edge.id) return
            val existingEdge = Edge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        index.remove(keys.put(edge.id, value) as T, edge.id)
        index[value] = edge.id
    }

    override fun put(edge: Edge, value: T): T {
        val oldValue = get(edge)
        set(edge, value)
        return oldValue
    }

    override fun hasEdge(key: T): Boolean {
        checkComplete()
        return index.containsKey(key)
    }

    override fun getEdge(key: T): Edge {
        checkComplete()
        return Edge(index.getValue(key))
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        index.remove(keys.remove(edge.id) as T, edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        index.remove(keys.remove(newEdge.id) as T, newEdge.id)
        val moved = keys.removeOrElse(oldEdge.id) { return }
        keys[newEdge.id] = moved
        if (index.remove(moved, oldEdge.id)) index[moved] = newEdge.id
    }

    override fun trimToSize() = keys.trimToSize()
}
