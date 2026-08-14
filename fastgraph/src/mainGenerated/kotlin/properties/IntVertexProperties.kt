package io.github.sooniln.fastgraph.properties


import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.lastIndex
import io.github.sooniln.fastcollect.removeOrElse
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedVertexGraph
import io.github.sooniln.fastgraph.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.staticTypeOf


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

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(vertex: Vertex): Int {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Int): Int {
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class ImmutableIntArrayVertexProperty<G>(
    override val graph: G,
    defaultValueFunction: VertexFunction<Int>,
) : MutableVertexProperty<Int> where G : ImmutableGraph, G : IndexedVertexGraph {

    private val property = IntArray(graph.vertices.size) { vertexId ->
        write(defaultValueFunction.apply(Vertex(vertexId)))
    }

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(vertex: Vertex): Int {
        try {
            return read(property[vertex.id])
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        try {
            property[vertex.id] = write(value)
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun put(vertex: Vertex, value: Int): Int {
        try {
            val oldValue = read(property[vertex.id])
            property[vertex.id] = write(value)
            return oldValue
        } catch (e: IndexOutOfBoundsException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
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

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(vertex: Vertex): Int {
        return read(property.getOrPut(vertex.id) { write(initializer.apply(vertex)) })
    }

    override fun set(vertex: Vertex, value: Int) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Int): Int {
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

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}

internal class ImmutableIntMapVertexProperty(
    override val graph: ImmutableGraph,
    defaultValueFunction: VertexFunction<Int>
) : MutableVertexProperty<Int> {

    private val property = Int2IntHashMap()

    init {
        property.ensureCapacity(graph.vertices.size)
        graph.vertices.foreach { vertex ->
            property[vertex.id] = write(defaultValueFunction.apply(vertex))
        }
    }

    override val type: StaticType<Int> get() = staticTypeOf()

    override fun get(vertex: Vertex): Int {
        try {
            return read(property.getValue(vertex.id))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    override fun set(vertex: Vertex, value: Int) {
        property[vertex.id] = write(value)
    }

    override fun put(vertex: Vertex, value: Int): Int {
        try {
            return read(property.replace(vertex.id, write(value)))
        } catch (e: NoSuchElementException) {
            throwIllegalVertex(vertex, e)
        }
    }

    private fun read(it: Int): Int { return it }
    private fun write(it: Int): Int { return it }
}



internal class WrapperIntVertexKeyProperty(
    private val property: MutableVertexProperty<Int>
) : MutableVertexKeyProperty<Int>, MutableVertexProperty<Int> by property {
    private val keyMap = Int2IntHashMap()

    init {
        graph.vertices.foreach { vertex ->
            val key = get(vertex)
            if (keyMap.containsKey(key)) throw IllegalArgumentException("\"$key\" is not unique")
            keyMap[key] = vertex.id
        }
    }

    override fun hasVertex(key: Int): Boolean = keyMap.containsKey(key)
    override fun getVertex(key: Int): Vertex = Vertex(keyMap.getValue(key))

    override fun set(vertex: Vertex, value: Int) {
        put(vertex, value)
    }

    override fun put(vertex: Vertex, value: Int): Int {
        val oldVertexId = keyMap[value]
        if (!keyMap.isDefaultValue(oldVertexId) || keyMap.containsKey(value)) {
            val oldVertex = Vertex(oldVertexId)
            if (oldVertex == vertex) return value
            throw IllegalArgumentException("\"$value\" is already associated with $oldVertex")
        }

        val oldValue = property[vertex]
        check(keyMap.remove(oldValue, vertex.id))
        property[vertex] = value
        keyMap[value] = vertex.id
        return oldValue
    }

    override fun copy(defaultValueFunction: VertexFunction<Int>): MutableVertexKeyProperty<Int> {
        return super<MutableVertexKeyProperty>.copy(defaultValueFunction)
    }
}


