package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.reparent
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.references.VertexReference

internal abstract class AbstractTransposedGraph(val graph: Graph) : Graph by graph {

    init {
        require(graph.directed)
    }

    override fun outDegree(vertex: Vertex): Int = graph.inDegree(vertex)
    override fun inDegree(vertex: Vertex): Int = graph.outDegree(vertex)
    override fun successors(vertex: Vertex): VertexSet = graph.predecessors(vertex)
    override fun successor(vertex: Vertex): Vertex = graph.predecessor(vertex)
    override fun predecessors(vertex: Vertex): VertexSet = graph.successors(vertex)
    override fun predecessor(vertex: Vertex): Vertex = graph.successor(vertex)
    override fun outgoingEdges(vertex: Vertex): EdgeSet = graph.incomingEdges(vertex)
    override fun outgoingEdge(vertex: Vertex): Edge = graph.incomingEdge(vertex)
    override fun incomingEdges(vertex: Vertex): EdgeSet = graph.outgoingEdges(vertex)
    override fun incomingEdge(vertex: Vertex): Edge = graph.outgoingEdge(vertex)

    // a CanonicalEdge flips its source and target when transposed, which would mean the edge id is different from the
    // original edge id. to avoid this, make sure the edge container is not marked as CanonicalEdgeSet (so consumers
    // down the line treat the edges as opaque).
    override val edges: EdgeSet = if (graph.edges is CanonicalEdgeSet) {
        object : AbstractEdgeSet(), EdgeSet by graph.edges {}
    } else {
        graph.edges
    }

    override fun edgeSource(edge: Edge): Vertex = graph.edgeTarget(edge)
    override fun edgeTarget(edge: Edge): Vertex = graph.edgeSource(edge)

    override fun hasEdge(source: Vertex, target: Vertex): Boolean = graph.hasEdge(target, source)
    override fun edge(source: Vertex, target: Vertex): Edge = graph.edge(target, source)
    override fun edges(source: Vertex, target: Vertex): EdgeSet = graph.edges(target, source)

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> = graph.createVertexProperty(type, defaultValueFunction).reparent(this)

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> = graph.createEdgeProperty(type, defaultValueFunction).reparent(this)

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> =
        graph.createVertexKeyProperty(type).reparent(this)

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> =
        graph.createEdgeKeyProperty(type).reparent(this)
}

internal class TransposedGraph(graph: Graph) : AbstractTransposedGraph(graph)

internal class ImmutableTransposedGraph(
    graph: ImmutableGraph
) : AbstractTransposedGraph(graph), InternalImmutableGraph {

    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun createVertexReference(vertex: Vertex): VertexReference = graph.createVertexReference(vertex)
    override fun createEdgeReference(edge: Edge): EdgeReference = graph.createEdgeReference(edge)
}
