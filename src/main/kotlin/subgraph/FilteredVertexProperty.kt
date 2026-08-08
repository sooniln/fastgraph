package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastcollect.ints.Int2AnyHashMap
import io.github.sooniln.fastcollect.ints.getOrPut
import io.github.sooniln.fastcollect.ints.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexPredicate
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class FilteredVertexProperty<T>(
    override val graph: Graph,
    override val type: Class<T>,
    private val initializer: VertexInitializer<T>,
    private val filter: VertexPredicate,
) : MutableVertexProperty<T> {

    private val property = Int2AnyHashMap<T>()

    override fun get(vertex: Vertex): T {
        if (!filter.test(vertex)) {
            property.remove(vertex.id)
            throwIllegalVertex(vertex)
        }

        return property.getOrPut(vertex.id) { initializer.initialize(vertex) }
    }

    override fun set(vertex: Vertex, value: T) {
        if (!filter.test(vertex)) {
            property.remove(vertex.id)
            throwIllegalVertex(vertex)
        }

        property[vertex.id] = value
    }

    override fun put(vertex: Vertex, value: T): T {
        if (!filter.test(vertex)) {
            property.remove(vertex.id)
            throwIllegalVertex(vertex)
        }

        return property.replaceOrSet(vertex.id, value) { initializer.initialize(vertex) }
    }

    fun trimToSize() = property.trimToSize()
}
