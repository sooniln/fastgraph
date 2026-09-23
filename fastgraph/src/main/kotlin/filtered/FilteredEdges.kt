package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.PropertyType

internal abstract class FilteredEdges : AbstractEdgeSet() {
    abstract fun registerEdgeChangeListener(listener: EdgeChangeListener)
    abstract fun unregisterEdgeChangeListener(listener: EdgeChangeListener)

    abstract fun <T> createEdgeProperty(graph: Graph, type: PropertyType<T>, defaultValueFunction: EdgeFunction<T>): MutableEdgeProperty<T>
    abstract fun <T> createEdgeKeyProperty(graph: Graph, type: PropertyType<T>): MutableEdgeKeyProperty<T>
    abstract fun createEdgeReference(graph: Graph, edge: Edge): EdgeReference

    open fun filter(parentEdges: EdgeSet): EdgeSet = FilteredEdgeSet(parentEdges)

    abstract fun trimToSize()

    protected open inner class FilteredEdgeSet(private val parentEdges: EdgeSet) : AbstractEdgeSet() {
        override val size: Int get() = parentEdges.count { contains(it) }
        override fun contains(element: Edge): Boolean = parentEdges.contains(element) && contains(element)
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val it = parentEdges.iterator()
            private var next = Edge(0)
            private var done = false

            init { increment() }

            override fun hasNext(): Boolean = !done
            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                return next.also { increment() }
            }

            private fun increment() {
                while (it.hasNext()) {
                    next = it.next()
                    if (contains(next)) return
                }
                done = true
            }
        }
    }
}
