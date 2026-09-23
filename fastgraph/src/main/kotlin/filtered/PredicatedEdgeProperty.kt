package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalEdge

internal class PredicatedEdgeProperty<T>(
    override val graph: Graph,
    override val type: PropertyType<T>,
    private val initializer: EdgeFunction<T>,
    private val predicate: EdgePredicate,
) : MutableEdgeProperty<T> {

    private val property = Long2AnyHashMap<T>()

    override fun get(edge: Edge): T {
        if (!predicate.test(edge)) {
            property.remove(edge.id)
            throwIllegalEdge(graph, edge)
        }

        return property.getOrPut(edge.id) { initializer.apply(edge) }
    }

    override fun set(edge: Edge, value: T) {
        if (!predicate.test(edge)) {
            property.remove(edge.id)
            throwIllegalEdge(graph, edge)
        }

        property[edge.id] = value
    }

    override fun put(edge: Edge, value: T): T {
        if (!predicate.test(edge)) {
            property.remove(edge.id)
            throwIllegalEdge(graph, edge)
        }

        return property.replaceOrSet(edge.id, value) { initializer.apply(edge) }
    }

    fun trimToSize() {
        val it = property.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!predicate.test(Edge(entry.key))) {
                it.remove()
            }
        }
        property.trimToSize()
    }
}
