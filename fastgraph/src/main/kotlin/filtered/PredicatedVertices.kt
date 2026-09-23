package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.references.VertexReference
import java.lang.ref.WeakReference

internal class PredicatedVertices(
    private val parent: Graph,
    private val predicate: VertexPredicate,
) : FilteredVertices() {

    private val properties = ArrayList<WeakReference<PredicatedVertexProperty<*>>>()

    override val size: Int get() {
        var size = 0
        for (vertex in parent.vertices) {
            if (predicate.test(vertex)) {
                ++size
            }
        }
        return size
    }

    override fun contains(element: Vertex): Boolean = parent.vertices.contains(element) && predicate.test(element)

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
                if (predicate.test(next!!)) return
            }
            next = null
        }
    }

    override fun registerVertexChangeListener(listener: VertexChangeListener) {
        throw UnsupportedOperationException("A graph with filtered vertices cannot support vertex listeners")
    }

    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {
        throw UnsupportedOperationException("A graph with filtered vertices cannot support vertex listeners")
    }

    override fun <T> createVertexProperty(
        graph: Graph,
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        val property = PredicatedVertexProperty(graph, type, defaultValueFunction, predicate)
        properties.add(WeakReference(property))
        return property
    }

    override fun <T> createVertexKeyProperty(graph: Graph, type: PropertyType<T>): MutableVertexKeyProperty<T> {
        throw UnsupportedOperationException("A graph with filtered vertices cannot support vertex key properties")
    }

    override fun createVertexReference(graph: Graph, vertex: Vertex): VertexReference {
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
