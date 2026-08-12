package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastcollect.Int2AnyHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexPredicate
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class FilteredVertexProperty<T>(
    override val graph: Graph,
    override val type: StaticType<T>,
    private val defaultValueFunction: VertexFunction<T>,
    private val filter: VertexPredicate,
) : MutableVertexProperty<T> {

    private val property = Int2AnyHashMap<T>()

    override fun get(vertex: Vertex): T {
        if (!filter.test(vertex)) {
            property.remove(vertex.id)
            throwIllegalVertex(vertex)
        }

        return property.getOrPut(vertex.id) { defaultValueFunction.apply(vertex) }
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

        return property.replaceOrSet(vertex.id, value) { defaultValueFunction.apply(vertex) }
    }

    fun trimToSize() = property.trimToSize()
}
