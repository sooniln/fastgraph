package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastcollect.Int2LongHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.lastIndex

internal class LongArrayVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long>, VertexChangeListener {

    private val property = LongArrayList()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: Class<Long> = Long::class.java

    private fun ensureVertexExists(vertex: Vertex): LongArrayList {
        val index = vertex.id
        if (property.size <= index) {
            if (index >= graph.vertices.size) throwIllegalVertex(vertex)
            property.ensureCapacity(index + 1)
            var i = property.size
            do {
                property.add(initializer.apply(graph.vertices[i++]))
            } while (i <= index)
        }
        return property
    }

    override fun get(vertex: Vertex): Long {
        return ensureVertexExists(vertex)[vertex.id]
    }

    override fun set(vertex: Vertex, value: Long) {
        ensureVertexExists(vertex)[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Long): Long {
        return ensureVertexExists(vertex).replace(vertex.id, value)
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.id == graph.vertices.lastIndex)
    }

    override fun onVertexRemoved(vertex: Vertex) {
        if (vertex.id == property.lastIndex) {
            property.removeAt(vertex.id)
        } else {
            check(vertex.id == graph.vertices.lastIndex)
        }
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        if (oldVertex.id == property.lastIndex) {
            property[newVertex.id] = property.removeAt(oldVertex.id)
        } else {
            check(oldVertex.id == graph.vertices.lastIndex)
        }
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()
}

internal class ImmutableLongArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Long>,
) : MutableVertexProperty<Long> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = LongArrayList()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            assert(vertex.id == property.size)
            property.add(defaultValueFunction.apply(vertex))
        }
    }

    override val type: Class<Long> = Long::class.java

    override fun get(vertex: Vertex): Long {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Long): Long {
        try {
            return property.replace(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }
}

internal class LongMapVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Long>
) : MutableVertexProperty<Long>, VertexChangeListener {

    private val property = Int2LongHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: Class<Long> = Long::class.java

    override fun get(vertex: Vertex): Long {
        return property.getOrPut(vertex.id) { initializer.apply(vertex) }
    }

    override fun set(vertex: Vertex, value: Long) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Long): Long {
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

internal class ImmutableLongMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Long>
) : MutableVertexProperty<Long> {

    private val property = Int2LongHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = defaultValueFunction.apply(vertex)
        }
    }

    override val type: Class<Long> = Long::class.java

    override fun get(vertex: Vertex): Long {
        try {
            return property.getValue(vertex.id)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Long) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Long): Long {
        try {
            return property.replace(vertex.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }
}
