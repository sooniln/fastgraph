package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import java.lang.ref.WeakReference

internal open class PredicatedEdges(
    private val parent: Graph,
    private val vertices: FilteredVertices,
    private val predicate: EdgePredicate,
) : FilteredEdges() {

    companion object {
        // filtering never rewrites an edge id, so canonical endpoints survive any filter
        fun from(parent: Graph, vertices: FilteredVertices, predicate: EdgePredicate): PredicatedEdges =
            if (parent.edges is CanonicalEdgeSet) CanonicalPredicatedEdges(parent, vertices, predicate)
            else PredicatedEdges(parent, vertices, predicate)
    }

    private val properties = ArrayList<WeakReference<PredicatedEdgeProperty<*>>>()

    private fun test(edge: Edge): Boolean {
        return predicate.test(edge) &&
                vertices.contains(parent.edgeSource(edge)) &&
                vertices.contains(parent.edgeTarget(edge))
    }

    override val size: Int get() {
        var size = 0
        for (edge in parent.edges) {
            if (test(edge)) {
                ++size
            }
        }
        return size
    }

    override fun contains(element: Edge): Boolean = parent.edges.contains(element) && test(element)

    override fun iterator(): EdgeIterator = object : EdgeIterator {
        private val it = parent.edges.iterator()
        private var next: Edge? = null

        init {
            increment()
        }

        override fun hasNext(): Boolean = next != null
        override fun next(): Edge {
            val next = checkNotNull(next)
            increment()
            return next
        }

        private fun increment() {
            while (it.hasNext()) {
                next = it.next()
                if (test(next!!)) return
            }
            next = null
        }
    }

    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {
        throw UnsupportedOperationException("A graph with filtered edges cannot support edge listeners")
    }

    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {
        throw UnsupportedOperationException("A graph with filtered edges cannot support edge listeners")
    }

    override fun <T> createEdgeProperty(
        graph: Graph,
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        val property = PredicatedEdgeProperty(graph, type, defaultValueFunction, predicate)
        properties.add(WeakReference(property))
        return property
    }

    override fun <T> createEdgeKeyProperty(graph: Graph, type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        throw UnsupportedOperationException("A graph with filtered edges cannot support edge key properties")
    }

    override fun createEdgeReference(graph: Graph,edge: Edge): EdgeReference {
        throw UnsupportedOperationException("A graph with filtered edges cannot support edge references")
    }

    override fun trimToSize() {
        var index = 0
        while (index < properties.size) {
            val property = properties[index].get()
            if (property == null) {
                val other = properties.removeAt(properties.lastIndex)
                if (index != properties.size) {
                    properties[index] = other
                }
            } else {
                property.trimToSize()
                ++index
            }
        }

        properties.trimToSize()
    }
}

private class CanonicalPredicatedEdges(parent: Graph, vertices: FilteredVertices, predicate: EdgePredicate) :
    PredicatedEdges(parent, vertices, predicate), CanonicalEdgeSet
