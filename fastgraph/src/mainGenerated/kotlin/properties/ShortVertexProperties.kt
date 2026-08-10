package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.Int2ShortHashMap
import io.github.sooniln.fastcollect.ShortArrayList
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.TypeReference
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class ShortArrayVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Short>,
) : MutableVertexProperty<Short>, VertexChangeListener {

    private val property = ShortArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { onVertexAdded(it) }
        graph.registerVertexChangeListener(this)
    }

    override val type: TypeReference<Short> = TypeReference.of()

    override fun get(vertex: Vertex): Short {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Short) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Short): Short {
        try {
            return property.replace(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == property.size)
        property.add(initializer.apply(vertex))
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
}

internal class ImmutableShortArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Short>,
) : MutableVertexProperty<Short> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = ShortArrayList()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            assert(vertex.id == property.size)
            property.add(defaultValueFunction.apply(vertex))
        }
    }

    override val type: TypeReference<Short> = TypeReference.of()

    override fun get(vertex: Vertex): Short {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Short) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Short): Short {
        try {
            return property.replace(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }
}

internal class ShortMapVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Short>
) : MutableVertexProperty<Short>, VertexChangeListener {

    private val property = Int2ShortHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: TypeReference<Short> = TypeReference.of()

    override fun get(vertex: Vertex): Short {
        return property.getOrPut(vertex.id) { initializer.apply(vertex) }
    }

    override fun set(vertex: Vertex, value: Short) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Short): Short {
        return property.replaceOrSet(vertex.id, value) { initializer.apply(vertex) }
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldValue = property.removeOrElse(oldVertex.id) { return }
        property[newVertex.id] = oldValue
    }

    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableShortMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Short>
) : MutableVertexProperty<Short> {

    private val property = Int2ShortHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = defaultValueFunction.apply(vertex)
        }
    }

    override val type: TypeReference<Short> = TypeReference.of()

    override fun get(vertex: Vertex): Short {
        try {
            return property.getValue(vertex.id)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Short) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Short): Short {
        try {
            return property.replace(vertex.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }
}
