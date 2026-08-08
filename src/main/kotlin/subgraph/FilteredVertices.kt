package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexConsumer
import io.github.sooniln.fastgraph.VertexInitializer
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexPredicate
import io.github.sooniln.fastgraph.VertexReference
import java.lang.ref.WeakReference

internal class FilteredVertices(
    private val parent: Graph,
    private val filter: VertexPredicate,
) : SubgraphVertices, AbstractVertexSet() {

    private val properties = ArrayList<WeakReference<FilteredVertexProperty<*>>>()

    // TODO: figure out a way to avoid lateinit?
    private lateinit var graph: Graph

    override fun bind(graph: Graph) {
        this.graph = graph
    }

    override val size: Int get() {
        var size = 0
        parent.vertices.foreach { vertex ->
            if (filter.test(vertex)) {
                ++size
            }
        }
        return size
    }

    override fun contains(element: Vertex): Boolean = parent.vertices.contains(element) && filter.test(element)

    override fun iterator(): VertexIterator = object : VertexIterator {
        private val it = parent.vertices.iterator()
        private var next: Vertex? = null

        init {
            increment()
        }

        override fun hasNext(): Boolean = next != null
        override fun next(): Vertex {
            val next = checkNotNull(next)
            increment()
            return next
        }

        private fun increment() {
            while (it.hasNext()) {
                next = it.next()
                if (filter.test(next!!)) return
            }
            next = null
        }
    }

    override fun foreach(action: VertexConsumer) {
        parent.vertices.foreach { vertex ->
            if (filter.test(vertex)) {
                action.accept(vertex)
            }
        }
    }

    override fun registerVertexChangeListener(listener: VertexChangeListener) {
        throw UnsupportedOperationException("A graph with filtered vertices cannot support vertex listeners")
    }

    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {
        throw UnsupportedOperationException("A graph with filtered vertices cannot support vertex listeners")
    }

    override fun <T> createVertexProperty(
        type: Class<T>,
        initializer: VertexInitializer<T>
    ): MutableVertexProperty<T> {
        val property = FilteredVertexProperty(graph, type, initializer, filter)
        properties.add(WeakReference(property))
        return property
    }

    override fun createVertexReference(vertex: Vertex): VertexReference {
        throw UnsupportedOperationException("A graph with filtered vertices cannot support vertex references")
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
