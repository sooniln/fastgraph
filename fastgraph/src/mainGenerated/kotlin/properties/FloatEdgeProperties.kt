package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.FloatArrayList
import io.github.sooniln.fastcollect.Long2FloatHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedEdgeGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.staticTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalEdge


internal class FloatArrayEdgeProperty(
    override val graph: IndexedEdgeGraph,
    defaultValueFunction: EdgeFunction<Float>,
) : MutableEdgeProperty<Float>, EdgeChangeListener {

    private val property = FloatArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { onEdgeAdded(it) }
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Float> get() = staticTypeOf()

    override fun get(edge: Edge): Float {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Float) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Float): Float {
        try {
            return read(property.replace(edge.lowBits, write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun onEdgeAdded(edge: Edge) {
        check(edge.lowBits == property.size)
        property.add(write(initializer.apply(edge)))
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

    private fun read(it: Float): Float { return it }
    private fun write(it: Float): Float { return it }
}

internal class ImmutableFloatArrayEdgeProperty<G>(
    override val graph: G,
    defaultValueFunction: EdgeFunction<Float>,
) : MutableEdgeProperty<Float> where G : ImmutableGraph, G : IndexedEdgeGraph {

    private val property = FloatArray(graph.edges.size) { edgeId ->
        write(defaultValueFunction.apply(graph.edges[edgeId]))
    }

    override val type: StaticType<Float> get() = staticTypeOf()

    override fun get(edge: Edge): Float {
        try {
            return read(property[edge.lowBits])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Float) {
        try {
            property[edge.lowBits] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun put(edge: Edge, value: Float): Float {
        try {
            val oldValue = read(property[edge.lowBits])
            property[edge.lowBits] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Float): Float { return it }
    private fun write(it: Float): Float { return it }
}

internal class FloatMapEdgeProperty(
    override val graph: Graph,
    defaultValueFunction: EdgeFunction<Float>
) : MutableEdgeProperty<Float>, EdgeChangeListener {

    private val property = Long2FloatHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerEdgeChangeListener(this)
    }

    override val type: StaticType<Float> get() = staticTypeOf()

    override fun get(edge: Edge): Float {
        return read(property.getOrPut(edge.id) { write(initializer.apply(edge)) })
    }

    override fun set(edge: Edge, value: Float) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Float): Float {
        return read(property.replaceOrSet(edge.id, write(value)) { write(initializer.apply(edge)) })
    }

    override fun onEdgeAdded(edge: Edge) {}

    override fun onEdgeRemoved(edge: Edge) {
        property.remove(edge.id)
    }

    override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {
        val oldValue = property.removeOrElse(oldEdge.id) { return }
        property[newEdge.id] = oldValue
    }

    override fun ensureEdgeCapacity(edgeCapacity: Int) = property.ensureCapacity(edgeCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Float): Float { return it }
    private fun write(it: Float): Float { return it }
}

internal class ImmutableFloatMapEdgeProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: EdgeFunction<Float>
) : MutableEdgeProperty<Float> {

    private val property = Long2FloatHashMap()

    init {
        property.ensureCapacity(graph.edges.size)
        graph.edges.foreach { edge ->
            property[edge.id] = write(defaultValueFunction.apply(edge))
        }
    }

    override val type: StaticType<Float> get() = staticTypeOf()

    override fun get(edge: Edge): Float {
        try {
            return read(property.getValue(edge.id))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    override fun set(edge: Edge, value: Float) {
        property[edge.id] = write(value)
    }

    override fun put(edge: Edge, value: Float): Float {
        try {
            return read(property.replace(edge.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalEdge(graph, edge, e)
        }
    }

    private fun read(it: Float): Float { return it }
    private fun write(it: Float): Float { return it }
}


