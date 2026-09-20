package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.DoubleArrayList
import io.github.sooniln.fastcollect.Long2DoubleHashMap
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
import io.github.sooniln.fastgraph.IdentityIndexedEdgeGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.propertyTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge


internal class DoubleIdentityIndexedEdgeProperty(
    override val graph: IdentityIndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Double>,
) : MutableEdgeProperty<Double>, EdgeChangeListener {

    private val property = DoubleArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(edge: Edge): Double {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Double) {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            property[edge.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Double): Double {
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

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class DoubleIndexedEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Double>,
) : MutableEdgeProperty<Double>, EdgeChangeListener {

    private val property = DoubleArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) { onEdgeAdded(edge) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(edge: Edge): Double {
        try {
            return read(property[graph.edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Double) {
        try {
            property[graph.edges.indexOf(edge)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Double): Double {
        try {
            return read(property.replace(graph.edges.indexOf(edge), write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(graph.edges.indexOf(edge) == property.size)
        property.add(write(initializer.apply(edge)))
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

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class ImmutableDoubleIdentityIndexedEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Double>,
) : MutableEdgeProperty<Double> where G : ImmutableGraph, G : IdentityIndexedEdgeGraph {

    private val property = DoubleArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(edge: Edge): Double {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            return read(property[edge.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Double) {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            property[edge.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Double): Double {
        val edge = IdentityIndexedEdge.from(edge)
        try {
            val oldValue = read(property[edge.id])
            property[edge.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class ImmutableDoubleIndexedEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Double>,
) : MutableEdgeProperty<Double> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = DoubleArray(graph.edges.size) { index ->
        write(defaultValueFunction.apply(graph.edges[index]))
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(edge: Edge): Double {
        try {
            return read(property[graph.edges.indexOf(edge)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Double) {
        try {
            property[graph.edges.indexOf(edge)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Double): Double {
        try {
            val index = graph.edges.indexOf(edge)
            val oldValue = read(property[index])
            property[index] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class DoubleEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Double>
) : MutableEdgeProperty<Double>, EdgeChangeListener {

    private val property = Long2DoubleHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(edge: Edge): Double {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Double) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Double): Double {
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

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class ImmutableDoubleEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Double>
) : MutableEdgeProperty<Double> {

    private val property = Long2DoubleHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        for (edge in graph.edges) {
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(edge: Edge): Double {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Double) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Double): Double {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}
