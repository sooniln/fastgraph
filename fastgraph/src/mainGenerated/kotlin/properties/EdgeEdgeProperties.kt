package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Long2LongHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.propertyTypeOf


internal class EdgeIdentityIndexedEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Edge>,
) : MutableEdgeProperty<Edge>, EdgeChangeListener {

    private val property = LongArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    override fun get(edge: Edge): Edge {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            property[edge.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Edge): Edge {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(IdentityIndexedEdge.from(edge).id == property.size)
        property.add(write(initializer.apply(edge)))
    }

    override fun onEdgeRemoved(edge: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        check(edge.id == property.lastIndex)
        property.removeAt(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldEdge = IdentityIndexedEdge.from(oldEdge)
        val newEdge = IdentityIndexedEdge.from(newEdge)
        check(oldEdge.id == property.lastIndex)
        property[newEdge.id] = property.removeAt(oldEdge.id)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class EdgeIndexedEdgeProperty(
    override val graph: Graph,
    private val edges: IndexedEdgeSet,
    defaultValueFunction: EdgeFunction<Edge>,
) : MutableEdgeProperty<Edge>, EdgeChangeListener {

    private val property = LongArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(edges.size)
        for (edge in edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    override fun get(edge: Edge): Edge {
        try {
            return read(property[edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        try {
            property[edges.indexOf(edge)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Edge): Edge {
        try {
            return read(property.replace(edges.indexOf(edge), write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edges.indexOf(edge) == property.size)
        property.add(write(initializer.apply(edge)))
    }

    override fun onEdgeRemoved(edge: Edge) {
        check(edges.indexOf(edge) == property.lastIndex)
        property.removeAt(property.lastIndex)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        check(edges.indexOf(oldEdge) == property.lastIndex)
        property[edges.indexOf(newEdge)] = property.removeAt(property.lastIndex)
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class ImmutableEdgeIdentityIndexedEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Edge>,
) : MutableEdgeProperty<Edge> {

    private val property = LongArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(Edge(edgeId.toLong())))
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    override fun get(edge: Edge): Edge {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            property[edge.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Edge): Edge {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            val oldValue = read(property[edge.id])
            property[edge.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class ImmutableEdgeIndexedEdgeProperty(
    override val graph: ImmutableGraph,
    private val edges: IndexedEdgeSet,
    defaultValueFunction: EdgeFunction<Edge>,
) : MutableEdgeProperty<Edge> {

    private val property = LongArray(edges.size) { index ->
        write(defaultValueFunction.apply(edges[index]))
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    override fun get(edge: Edge): Edge {
        try {
            return read(property[edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        try {
            property[edges.indexOf(edge)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Edge): Edge {
        try {
            val index = edges.indexOf(edge)
            val oldValue = read(property[index])
            property[index] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class EdgeEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Edge>
) : MutableEdgeProperty<Edge>, EdgeChangeListener {

    private val property = Long2LongHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    override fun get(edge: Edge): Edge {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Edge) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Edge): Edge {
        return read(property.replaceOrSet(edge.id, write(value)) { write(initializer.apply(edge)) })
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        property.remove(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldValue = property.removeOrElse(oldEdge.id) { property.remove(newEdge.id); return }
        property[newEdge.id] = oldValue
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}

internal class ImmutableEdgeEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Edge>
) : MutableEdgeProperty<Edge> {

    private val property = Long2LongHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: PropertyType<Edge> get() = propertyTypeOf()

    override fun get(edge: Edge): Edge {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Edge) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Edge): Edge {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Long): Edge { return Edge(it) }
    private fun write(it: Edge): Long { return it.id }
}
