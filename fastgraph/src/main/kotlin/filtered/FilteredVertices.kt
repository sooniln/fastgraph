package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.references.VertexReference

internal abstract class FilteredVertices : AbstractVertexSet() {
    abstract fun registerVertexChangeListener(listener: VertexChangeListener)
    abstract fun unregisterVertexChangeListener(listener: VertexChangeListener)

    abstract fun <T> createVertexProperty(
        graph: Graph,
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T>
    abstract fun <T> createVertexKeyProperty(graph: Graph, type: PropertyType<T>): MutableVertexKeyProperty<T>
    abstract fun createVertexReference(graph: Graph, vertex: Vertex): VertexReference

    abstract fun trimToSize()
}
