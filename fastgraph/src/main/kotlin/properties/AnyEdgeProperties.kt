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
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IdentityIndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal class AnyIdentityIndexedEdgeProperty<T>(
    override val graph: IdentityIndexedEdgeGraph,
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
            return property[IdentityIndexedEdge.from(edge).id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[IdentityIndexedEdge.from(edge).id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(IdentityIndexedEdge.from(edge).id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IdentityIndexedEdge.from(edge).id == property.size)
        property.add(initializer.apply(edge))
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(IdentityIndexedEdge.from(edge).id == property.lastIndex)
        property.removeAt(IdentityIndexedEdge.from(edge).id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(IdentityIndexedEdge.from(oldEdge).id == property.lastIndex)
        property[IdentityIndexedEdge.from(newEdge).id] = property.removeAt(IdentityIndexedEdge.from(oldEdge).id)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()
}

internal class AnyIndexedEdgeProperty<T>(
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
            return property[graph.edges.indexOf(edge)]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[graph.edges.indexOf(edge)] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(graph.edges.indexOf(edge), value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(graph.edges.indexOf(edge) == property.size)
        property.add(initializer.apply(edge))
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(graph.edges.indexOf(edge) == property.lastIndex)
        property.removeAt(property.lastIndex)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(graph.edges.indexOf(oldEdge) == property.lastIndex)
        property[graph.edges.indexOf(newEdge)] = property.removeAt(property.lastIndex)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableAnyIdentityIndexedEdgeProperty<G, T>(
    override val graph: G,
    override val type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>,
) : MutableEdgeProperty<T> where G : ImmutableGraph, G : IdentityIndexedEdgeGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            assert(IdentityIndexedEdge.from(edge).id == property.size)
            property.add(defaultValueFunction.apply(edge))
        }
    }

    override fun get(edge: Edge): T {
        try {
            return property[IdentityIndexedEdge.from(edge).id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[IdentityIndexedEdge.from(edge).id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(IdentityIndexedEdge.from(edge).id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}

internal class ImmutableAnyIndexedEdgeProperty<G, T>(
    override val graph: G,
    override val type: PropertyType<T>,
    defaultValueFunction: EdgeFunction<T>,
) : MutableEdgeProperty<T> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = ArrayList<T>()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            assert(graph.edges.indexOf(edge) == property.size)
            property.add(defaultValueFunction.apply(edge))
        }
    }

    override fun get(edge: Edge): T {
        try {
            return property[graph.edges.indexOf(edge)]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: T) {
        try {
            property[graph.edges.indexOf(edge)] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: T): T {
        try {
            return property.set(graph.edges.indexOf(edge), value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }
}

internal class AnyEdgeProperty<T>(
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
        val oldValue = property.removeOrElse(oldEdge.id) { property.remove(newEdge.id); return }
        property[newEdge.id] = oldValue
    }

    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableAnyEdgeProperty<T>(
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
