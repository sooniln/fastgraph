package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractImmutableGraph
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexSet

internal class VertexInducedImmutableSubgraph(
    private val subgraph: VertexInducedSubgraph
) : AbstractImmutableGraph(), Graph by subgraph {

    constructor(graph: Graph, vertices: VertexSet) : this(VertexInducedSubgraph(graph, vertices))

    override val multiEdge: Boolean by lazy { subgraph.multiEdge }

    override fun successors(vertex: Vertex): VertexSet {
        return ImmutableFilteringVertexSet(subgraph.successors(vertex))
    }

    override fun predecessors(vertex: Vertex): VertexSet {
        return ImmutableFilteringVertexSet(subgraph.predecessors(vertex))
    }

    override fun outgoingEdges(vertex: Vertex): EdgeSet {
        return ImmutableFilteringEdgeSet(subgraph.outgoingEdges(vertex))
    }

    override fun incomingEdges(vertex: Vertex): EdgeSet {
        return ImmutableFilteringEdgeSet(subgraph.incomingEdges(vertex))
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        return ImmutableFilteringEdgeSet(subgraph.getEdges(source, target))
    }

    private class ImmutableFilteringVertexSet(private val vertexSet: VertexSet) : VertexSet by vertexSet {
        override val size: Int by lazy { vertexSet.size }
        override fun isEmpty(): Boolean = size == 0
    }

    private class ImmutableFilteringEdgeSet(private val edgeSet: EdgeSet) : EdgeSet by edgeSet {
        override val size: Int by lazy { edgeSet.size }
        override fun isEmpty(): Boolean = size == 0
    }
}
