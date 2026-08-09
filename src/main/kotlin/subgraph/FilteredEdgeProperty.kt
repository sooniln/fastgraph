package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastcollect.Long2AnyHashMap
import io.github.sooniln.fastcollect.getOrPut
import io.github.sooniln.fastcollect.replaceOrSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgePredicate
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal class FilteredEdgeProperty<T>(
    override val graph: Graph,
    override val type: Class<T>,
    private val initializer: EdgeFunction<T>,
    private val filter: EdgePredicate,
) : MutableEdgeProperty<T> {

    private val property = Long2AnyHashMap<T>()

    override fun get(edge: Edge): T {
        if (!filter.test(edge)) {
            property.remove(edge.id)
            throwIllegalEdge(graph, edge)
        }

        return property.getOrPut(edge.id) { initializer.apply(edge) }
    }

    override fun set(edge: Edge, value: T) {
        if (!filter.test(edge)) {
            property.remove(edge.id)
            throwIllegalEdge(graph, edge)
        }

        property[edge.id] = value
    }

    override fun put(edge: Edge, value: T): T {
        if (!filter.test(edge)) {
            property.remove(edge.id)
            throwIllegalEdge(graph, edge)
        }

        return property.replaceOrSet(edge.id, value) { initializer.apply(edge) }
    }

    fun trimToSize() = property.trimToSize()
}
