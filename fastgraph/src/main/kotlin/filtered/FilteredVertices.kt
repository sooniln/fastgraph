package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet

internal interface FilteredVertices : VertexSet {
    fun bind(graph: Graph)

    fun registerVertexChangeListener(listener: VertexChangeListener)
    fun unregisterVertexChangeListener(listener: VertexChangeListener)

    fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T>
    fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T>
    fun createVertexReference(vertex: Vertex): VertexReference

    fun trimToSize()
}
