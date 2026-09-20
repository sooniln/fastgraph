package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Long2AnyHashMap
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

@Suppress("UNCHECKED_CAST")
internal class AnyIdentityIndexedEdgeKeyProperty<T>(
    override val graph: IdentityIndexedEdgeGraph,
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
                val edge = IdentityIndexedEdge.from(edge)
                index[keys[edge.id] as T] != edge.id
            }}"
        }
    }

    override fun get(edge: Edge): T {
        checkComplete()
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return keys[edge.id] as T
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        val edge = IdentityIndexedEdge.from(edge)

        val existingId = index[value]
        if (existingId != null) {
            if (existingId == edge.id) return
            val existingEdge = IdentityIndexedEdge(existingId)
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
        return IdentityIndexedEdge(index.getValue(key)).toEdge()
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IdentityIndexedEdge.from(edge).id == keys.size)
        keys.add(null)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        check(edge.id == keys.lastIndex)
        index.remove(keys.removeAt(edge.id) as T, edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldEdge = IdentityIndexedEdge.from(oldEdge)
        val newEdge = IdentityIndexedEdge.from(newEdge)
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
internal class AnyIndexedEdgeKeyProperty<T>(
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
            "edges have no key: ${graph.edges.filter {
                val i = graph.edges.indexOf(it)
                index[keys[i] as T] != i
            }}"
        }
    }

    override fun get(edge: Edge): T {
        checkComplete()
        try {
            return keys[graph.edges.indexOf(edge)] as T
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        val edgeIndex = graph.edges.indexOf(edge)
        val existingIndex = index[value]
        if (existingIndex != null) {
            if (existingIndex == edgeIndex) return
            val existingEdge = graph.edges[existingIndex]
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.set(edgeIndex, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
        index.remove(oldValue as T, edgeIndex)
        index[value] = edgeIndex
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
        return graph.edges[index.getValue(key)]
    }

    override fun onEdgeAdded(edge: Edge) {
        check(graph.edges.indexOf(edge) == keys.size)
        keys.add(null)
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edgeIndex = graph.edges.indexOf(edge)
        check(edgeIndex == keys.lastIndex)
        index.remove(keys.removeAt(edgeIndex) as T, edgeIndex)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldIndex = graph.edges.indexOf(oldEdge)
        val newIndex = graph.edges.indexOf(newEdge)
        check(oldIndex == keys.lastIndex)
        index.remove(keys[newIndex] as T, newIndex)
        val moved = keys.removeAt(oldIndex) as T
        keys[newIndex] = moved
        if (index.remove(moved, oldIndex)) index[moved] = newIndex
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = keys.ensureCapacity(edgeCapacity)
    override fun trimToSize() = keys.trimToSize()
}

@Suppress("UNCHECKED_CAST")
internal class AnyEdgeKeyProperty<T>(
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
