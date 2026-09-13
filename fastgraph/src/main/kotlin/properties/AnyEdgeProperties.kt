package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Long2AnyHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal class ArrayEdgeProperty<T>(
    override val graph: IndexedEdgeGraph,
    override val type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>,
) : MutableEdgeProperty<T>, EdgeChangeListener {

    private val property = ArrayList<T>()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override fun get(edge: Edge): T {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(edge.lowBits, value)
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

internal class ImmutableArrayEdgeProperty<G, T>(
    override val graph: G,
    override val type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>,
) : MutableEdgeProperty<T> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            assert(edge.lowBits == property.size)
            property.add(defaultValueFunction.apply(edge))
        }
    }

    override fun get(edge: Edge): T {
        try {
            return property[edge.lowBits]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[edge.lowBits] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(edge.lowBits, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}

internal class MapEdgeProperty<T>(
    override val graph: Graph,
    override val type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>
) : MutableEdgeProperty<T>, EdgeChangeListener {

    private val property = Long2AnyHashMap<T>()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override fun get(edge: Edge): T {
        return property.getOrPut(edge.id) { initializer.apply(edge)}
    }

    override fun set(edge: Edge, value: T) {
        property[edge.id] = value
    }

    override fun put(edge: Edge, value: T): T {
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

internal class ImmutableMapEdgeProperty<T>(
    override val graph: ImmutableGraph,
    override val type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>
) : MutableEdgeProperty<T> {

    private val property = Long2AnyHashMap<T>()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            property[edge.id] = defaultValueFunction.apply(edge)
        }
    }

    override fun get(edge: Edge): T {
        try {
            return property.getValue(edge.id)
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        property[edge.id] = value
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.replace(edge.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal class ArrayEdgeKeyProperty<T>(
    override val graph: IndexedEdgeGraph,
    override val type: PropertyType<T>,
) : MutableEdgeKeyProperty<T>, EdgeChangeListener {

    private val keys = ArrayList<T?>()
    private val index = HashMap<T, Long>()

    init {
        keys.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    private fun checkComplete() {
        check(index.size == keys.size) {
            "edges have no key: ${graph.edges.filter { index[keys[it.lowBits] as T] != it.id }}"
        }
    }

    override fun get(edge: Edge): T {
        checkComplete()
        try {
            return keys[edge.lowBits] as T
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        val existingId = index[value]
        if (existingId != null) {
            if (existingId == edge.id) return
            val existingEdge = Edge(existingId)
            throw IllegalArgumentException("\"$value\" is already associated with $existingEdge (${graph.edgeSource(existingEdge)} -> ${graph.edgeTarget(existingEdge)})")
        }

        val oldValue = try {
            keys.set(edge.lowBits, value)
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
        return Edge(index.getValue(key))
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == keys.size)
        keys.add(null)
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(edge.lowBits == keys.lastIndex)
        index.remove(keys.removeAt(edge.lowBits) as T, edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(oldEdge.lowBits == keys.lastIndex)
        index.remove(keys[newEdge.lowBits] as T, newEdge.id)
        val moved = keys.removeAt(oldEdge.lowBits) as T
        keys[newEdge.lowBits] = moved
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
