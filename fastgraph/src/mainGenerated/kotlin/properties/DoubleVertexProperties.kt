package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.DoubleArrayList
import io.github.sooniln.fastcollect.Int2DoubleHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.propertyTypeOf


internal class DoubleIdentityIndexedVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Double>,
) : MutableVertexProperty<Double>, VertexChangeListener {

    private val property = DoubleArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Double {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Double) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Double): Double {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == property.size)
        property.add(write(initializer.apply(vertex)))
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(vertex.id == property.lastIndex)
        property.removeAt(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(oldVertex.id == property.lastIndex)
        property[newVertex.id] = property.removeAt(oldVertex.id)
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class DoubleIndexedVertexProperty(
    override val graph: Graph,
    private val vertices: IndexedVertexSet,
    defaultValueFunction: VertexFunction<Double>,
) : MutableVertexProperty<Double>, VertexChangeListener {

    private val property = DoubleArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(vertices.size)
        for (vertex in vertices) { onVertexAdded(vertex) }
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Double {
        try {
            return read(property[vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Double) {
        try {
            property[vertices.indexOf(vertex)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Double): Double {
        try {
            return read(property.replace(vertices.indexOf(vertex), write(value)))
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertices.indexOf(vertex) == property.size)
        property.add(write(initializer.apply(vertex)))
    }

    override fun onVertexRemoved(vertex: Vertex) {
        check(vertices.indexOf(vertex) == property.lastIndex)
        property.removeAt(property.lastIndex)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        check(vertices.indexOf(oldVertex) == property.lastIndex)
        property[vertices.indexOf(newVertex)] = property.removeAt(property.lastIndex)
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class ImmutableDoubleIdentityIndexedVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Double>,
) : MutableVertexProperty<Double> {

    private val property = DoubleArray(graph.vertices.size) { vertexId ->
        write(defaultValueFunction.apply(Vertex(vertexId)))
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Double {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Double) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Double): Double {
        try {
            val oldValue = read(property[vertex.id])
            property[vertex.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class ImmutableDoubleIndexedVertexProperty(
    override val graph: ImmutableGraph,
    private val vertices: IndexedVertexSet,
    defaultValueFunction: VertexFunction<Double>,
) : MutableVertexProperty<Double> {

    private val property = DoubleArray(vertices.size) { index ->
        write(defaultValueFunction.apply(vertices[index]))
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Double {
        try {
            return read(property[vertices.indexOf(vertex)])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Double) {
        try {
            property[vertices.indexOf(vertex)] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Double): Double {
        try {
            val index = vertices.indexOf(vertex)
            val oldValue = read(property[index])
            property[index] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class DoubleVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Double>
) : MutableVertexProperty<Double>, VertexChangeListener {

    private val property = Int2DoubleHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Double {
        return read(property.getOrPut(vertex.id) { write(initializer.apply(vertex)) })
    }

    override fun set(vertex: Vertex, value: Double) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Double): Double {
        return read(property.replaceOrSet(vertex.id, write(value)) { write(initializer.apply(vertex)) })
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldValue = property.removeOrElse(oldVertex.id) { property.remove(newVertex.id); return }
        property[newVertex.id] = oldValue
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}

internal class ImmutableDoubleVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Double>
) : MutableVertexProperty<Double> {

    private val property = Int2DoubleHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        for (vertex in graph.vertices) {
            property[vertex.id] = write(defaultValueFunction.apply(vertex))
        }
    }

    override val type: PropertyType<Double> get() = propertyTypeOf()

    override fun get(vertex: Vertex): Double {
        try {
            return read(property.getValue(vertex.id))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Double) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Double): Double {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Double): Double { return it }
    private fun write(it: Double): Double { return it }
}
