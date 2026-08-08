package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeConsumer
import io.github.sooniln.fastgraph.EdgeInitializer
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgePredicate
import io.github.sooniln.fastgraph.EdgeReference
import java.lang.ref.WeakReference

internal class FilteredEdges(
    private val parent: Graph,
    private val vertices: SubgraphVertices,
    private val filter: EdgePredicate,
) : SubgraphEdges, AbstractEdgeSet() {

    private val properties = ArrayList<WeakReference<FilteredEdgeProperty<*>>>()

    // TODO: figure out a way to avoid lateinit?
    private lateinit var graph: Graph

    override fun bind(graph: Graph) {
        this.graph = graph
    }

    private fun test(edge: Edge): Boolean {
        context(parent) {
            return filter.test(edge) && vertices.contains(edge.source) && vertices.contains(edge.target)
        }
    }

    override val size: Int get() {
        var size = 0
        parent.edges.foreach { edge ->
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

    override fun foreach(action: EdgeConsumer) {
        parent.edges.foreach { edge ->
            if (test(edge)) {
                action.accept(edge)
            }
        }
    }

    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {
        throw UnsupportedOperationException("A graph with filtered edges cannot support edge listeners")
    }

    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {
        throw UnsupportedOperationException("A graph with filtered edges cannot support edge listeners")
    }

    override fun <T> createEdgeProperty(
        type: Class<T>,
        initializer: EdgeInitializer<T>
    ): MutableEdgeProperty<T> {
        val property = FilteredEdgeProperty(graph, type, initializer, filter)
        properties.add(WeakReference(property))
        return property
    }

    override fun createEdgeReference(edge: Edge): EdgeReference {
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
