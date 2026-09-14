package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.PropertyType

internal interface FilteredEdges : EdgeSet {
    fun bind(graph: Graph)

    fun registerEdgeChangeListener(listener: EdgeChangeListener)
    fun unregisterEdgeChangeListener(listener: EdgeChangeListener)

    fun <T> createEdgeProperty(type: PropertyType<T>, defaultValueFunction: EdgeFunction<T>): MutableEdgeProperty<T>
    fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T>
    fun createEdgeReference(edge: Edge): EdgeReference

    fun trimToSize()
}
