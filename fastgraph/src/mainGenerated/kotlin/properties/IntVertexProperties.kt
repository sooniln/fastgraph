package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.Int2IntHashMap
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

internal class IntArrayVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Int>,
) : MutableVertexProperty<Int>, VertexChangeListener {

    private val property = IntArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { onVertexAdded(it) }
        graph.registerVertexChangeListener(this)
    }

    override val type: Class<Int> = Int::class.java

    override fun get(vertex: Vertex): Int {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Int): Int {
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

internal class ImmutableIntArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Int>,
) : MutableVertexProperty<Int> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = IntArrayList()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            assert(vertex.id == property.size)
            property.add(defaultValueFunction.apply(vertex))
        }
    }

    override val type: Class<Int> = Int::class.java

    override fun get(vertex: Vertex): Int {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Int): Int {
        try {
            return property.replace(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }
}

internal class IntMapVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Int>
) : MutableVertexProperty<Int>, VertexChangeListener {

    private val property = Int2IntHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: Class<Int> = Int::class.java

    override fun get(vertex: Vertex): Int {
        return property.getOrPut(vertex.id) { initializer.apply(vertex) }
    }

    override fun set(vertex: Vertex, value: Int) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Int): Int {
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

internal class ImmutableIntMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Int>
) : MutableVertexProperty<Int> {

    private val property = Int2IntHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = defaultValueFunction.apply(vertex)
        }
    }

    override val type: Class<Int> = Int::class.java

    override fun get(vertex: Vertex): Int {
        try {
            return property.getValue(vertex.id)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Int): Int {
        try {
            return property.replace(vertex.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }
}
