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
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal class ArrayEdgeProperty<T>(
    override val graph: IndexedEdgeGraph,
    override val type: StaticType<T>,
    defaultValueFunction: EdgeFunction<T>,
) : MutableEdgeProperty<T>, EdgeChangeListener {

    private val property = ArrayList<T>()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { onEdgeAdded(it) }
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
    override val type: StaticType<T>,
    defaultValueFunction: EdgeFunction<T>,
) : MutableEdgeProperty<T> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
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
    override val type: StaticType<T>,
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
    override val type: StaticType<T>,
    defaultValueFunction: EdgeFunction<T>
) : MutableEdgeProperty<T> {

    private val property = Long2AnyHashMap<T>()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
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

internal class WrapperEdgeKeyProperty<T>(
    private val property: MutableEdgeProperty<T>
) : MutableEdgeKeyProperty<T>, MutableEdgeProperty<T> by property {
    private val keyMap = HashMap<T, Long>()

    init {
        graph.edges.foreach { edge ->
            val key = get(edge)
            if (keyMap.containsKey(key)) throw IllegalArgumentException("\"$key\" is not unique")
            keyMap[key] = edge.id
        }
    }

    override fun hasEdge(key: T): Boolean = keyMap.containsKey(key)
    override fun getEdge(key: T): Edge = Edge(keyMap.getValue(key))

    override fun set(edge: Edge, value: T) {
        put(edge, value)
    }

    override fun put(edge: Edge, value: T): T {
        val oldEdgeId = keyMap[value]
        if (oldEdgeId != null) {
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

    override fun copy(defaultValueFunction: EdgeFunction<T>): MutableEdgeKeyProperty<T> {
        return super<MutableEdgeKeyProperty>.copy(defaultValueFunction)
    }
}
