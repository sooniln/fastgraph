package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexSet

internal class TransposedGraph(val graph: Graph) : Graph by graph {
    override fun outDegree(vertex: Vertex): Int = graph.inDegree(vertex)

    override fun inDegree(vertex: Vertex): Int = graph.outDegree(vertex)

    override fun successors(vertex: Vertex): VertexSet = graph.predecessors(vertex)

    override fun predecessors(vertex: Vertex): VertexSet = graph.successors(vertex)

    override fun outgoingEdges(vertex: Vertex): EdgeSet = graph.incomingEdges(vertex)

    override fun incomingEdges(vertex: Vertex): EdgeSet = graph.outgoingEdges(vertex)

    override fun edgeSource(edge: Edge): Vertex = graph.edgeTarget(edge)

    override fun edgeTarget(edge: Edge): Vertex = graph.edgeSource(edge)

    override fun hasEdge(source: Vertex, target: Vertex): Boolean = graph.hasEdge(target, source)

    override fun edge(source: Vertex, target: Vertex): Edge = graph.edge(target, source)

    override fun edges(source: Vertex, target: Vertex): EdgeSet = graph.edges(target, source)
}
