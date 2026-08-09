package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ByteArrayList
import io.github.sooniln.fastcollect.Int2ByteHashMap
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

internal class ByteArrayVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Byte>,
) : MutableVertexProperty<Byte>, VertexChangeListener {

    private val property = ByteArrayList()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: Class<Byte> = Byte::class.java

    private fun ensureVertexExists(vertex: Vertex): ByteArrayList {
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

    override fun get(vertex: Vertex): Byte {
        return ensureVertexExists(vertex)[vertex.id]
    }

    override fun set(vertex: Vertex, value: Byte) {
        ensureVertexExists(vertex)[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Byte): Byte {
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

internal class ImmutableByteArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Byte>,
) : MutableVertexProperty<Byte> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = ByteArrayList()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            assert(vertex.id == property.size)
            property.add(defaultValueFunction.apply(vertex))
        }
    }

    override val type: Class<Byte> = Byte::class.java

    override fun get(vertex: Vertex): Byte {
        try {
            return property[vertex.id]
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Byte) {
        try {
            property[vertex.id] = value
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Byte): Byte {
        try {
            return property.replace(vertex.id, value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }
}

internal class ByteMapVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Byte>
) : MutableVertexProperty<Byte>, VertexChangeListener {

    private val property = Int2ByteHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: Class<Byte> = Byte::class.java

    override fun get(vertex: Vertex): Byte {
        return property.getOrPut(vertex.id) { initializer.apply(vertex) }
    }

    override fun set(vertex: Vertex, value: Byte) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Byte): Byte {
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

internal class ImmutableByteMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Byte>
) : MutableVertexProperty<Byte> {

    private val property = Int2ByteHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = defaultValueFunction.apply(vertex)
        }
    }

    override val type: Class<Byte> = Byte::class.java

    override fun get(vertex: Vertex): Byte {
        try {
            return property.getValue(vertex.id)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Byte) {
        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: Byte): Byte {
        try {
            return property.replace(vertex.id, value)
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }
}
