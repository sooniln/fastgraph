package io.github.sooniln.fastgraph.properties.internal

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIndexedVertexGraph
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.OwnedIndexedVertexProperty

class ArrayVertexProperty<V>(override val graph: VertexIndexedVertexGraph, private val initializer: VertexInitializer<V>) : MutableVertexProperty<V>, OwnedIndexedVertexProperty<V>() {

    private val property = ArrayList<V>()

    override fun get(vertex: Vertex): V = property[vertex.intValue]

    override fun set(vertex: Vertex, value: V) {
        property[vertex.intValue] = value
    }

    override fun put(vertex: Vertex, value: V): V {
        return property.set(vertex.intValue, value)
    }

    override fun onVertexReindexed(oldVertexIndex: Int, newVertexIndex: Int) {
        check(oldVertexIndex == property.size)
        if (newVertexIndex != oldVertexIndex) {
            property[newVertexIndex] = property[oldVertexIndex]
        }
        property.removeAt(oldVertexIndex)
    }

    override fun onVertexAdded(vertex: Vertex) {
        check(vertex.intValue == property.size)
        property.add(initializer.initialize(vertex))
    }
}

class MapVertexProperty<V>(override val graph: Graph, private val sparse: Boolean, private val initializer: VertexInitializer<V>) : MutableVertexProperty<V>, OwnedIndexedVertexProperty<V>() {

    private val property = Int2AnyHashMap<V>()

    override fun get(vertex: Vertex): V {
        return property.getOrPut(vertex.intValue) { initializer.initialize(vertex) }
    }

    override fun set(vertex: Vertex, value: V) {
        property[vertex.intValue] = value
    }

    override fun put(vertex: Vertex, value: V): V {
        return property.replaceOrElse(vertex.intValue, value) { initializer.initialize(vertex) }
    }

    override fun onVertexReindexed(oldVertexIndex: Int, newVertexIndex: Int) {
        if (newVertexIndex == oldVertexIndex) {
            property.remove(oldVertexIndex)
        } else {
            if (property.containsKey(oldVertexIndex)) {
                property[newVertexIndex] = property.removeKey(oldVertexIndex)
            }
        }
    }

    override fun onVertexAdded(vertex: Vertex) {}
}
