package io.github.sooniln.fastgraph.properties

import io.github.sooniln.fastcollect.ByteArrayList
import io.github.sooniln.fastcollect.Int2ByteHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.staticTypeOf
import io.github.sooniln.fastgraph.internal.throwIllegalVertex


internal class BooleanArrayVertexProperty(
    override val graph: IndexedVertexGraph,
    defaultValueFunction: VertexFunction<Boolean>,
) : MutableVertexProperty<Boolean>, VertexChangeListener {

    private val property = ByteArrayList()
    private val initializer = defaultValueFunction

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { onVertexAdded(it) }
        graph.registerVertexChangeListener(this)
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(vertex: Vertex): Boolean {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Boolean) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Boolean): Boolean {
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

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}

internal class ImmutableBooleanArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Boolean>,
) : MutableVertexProperty<Boolean> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = ByteArray(graph.vertices.size) { vertexId ->
        write(defaultValueFunction.apply(Vertex(vertexId)))
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(vertex: Vertex): Boolean {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Boolean) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Boolean): Boolean {
        try {
            val oldValue = read(property[vertex.id])
            property[vertex.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}

internal class BooleanMapVertexProperty(
    override val graph: Graph,
    defaultValueFunction: VertexFunction<Boolean>
) : MutableVertexProperty<Boolean>, VertexChangeListener {

    private val property = Int2ByteHashMap()
    private val initializer = defaultValueFunction

    init {
        graph.registerVertexChangeListener(this)
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(vertex: Vertex): Boolean {
        return read(property.getOrPut(vertex.id) { write(initializer.apply(vertex)) })
    }

    override fun set(vertex: Vertex, value: Boolean) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Boolean): Boolean {
        return read(property.replaceOrSet(vertex.id, write(value)) { write(initializer.apply(vertex)) })
    }

    override fun onVertexAdded(vertex: Vertex) {}

    override fun onVertexRemoved(vertex: Vertex) {
        property.remove(vertex.id)
    }

    override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {
        val oldValue = property.removeOrElse(oldVertex.id) { return }
        property[newVertex.id] = oldValue
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = property.ensureCapacity(vertexCapacity)
    override fun trimToSize() = property.trimToSize()

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}

internal class ImmutableBooleanMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Boolean>
) : MutableVertexProperty<Boolean> {

    private val property = Int2ByteHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = write(defaultValueFunction.apply(vertex))
        }
    }

    override val type: StaticType<Boolean> get() = staticTypeOf()

    override fun get(vertex: Vertex): Boolean {
        try {
            return read(property.getValue(vertex.id))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Boolean) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Boolean): Boolean {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Byte): Boolean { return it != 0.toByte() }
    private fun write(it: Boolean): Byte { return if (it) 1 else 0 }
}


