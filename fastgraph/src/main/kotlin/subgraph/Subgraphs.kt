package io.github.sooniln.fastgraph.subgraph

import io.github.sooniln.fastcollect.IntHashSet
import io.github.sooniln.fastcollect.LongHashSet
import io.github.sooniln.fastgraph.AbstractGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgePredicate
import io.github.sooniln.fastgraph.EdgeReference
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableVertexProperty
import io.github.sooniln.fastgraph.StaticType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexPredicate
import io.github.sooniln.fastgraph.VertexReference
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asEdgeSet
import io.github.sooniln.fastgraph.asVertexSet
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.emptyVertexSet
import io.github.sooniln.fastgraph.internal.ImmutableEdgeReference
import io.github.sooniln.fastgraph.internal.ImmutableVertexReference
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal object Subgraphs {
    fun subgraph(graph: ImmutableGraph, inducingVertices: VertexSet, inducingEdges: EdgeSet): ImmutableGraph {
        val vertices = InducingVertices(graph, inducingVertices)
        val edges = InducingEdges(graph, vertices, inducingEdges)
        return ImmutableSubgraph(graph, vertices, edges)
    }

    fun subgraph(graph: Graph, inducingVertices: VertexSet, inducingEdges: EdgeSet): Graph {
        if (graph is ImmutableGraph) {
            return subgraph(graph, inducingVertices, inducingEdges)
        } else {
            val vertices = InducingVertices(graph, inducingVertices)
            val edges = InducingEdges(graph, vertices, inducingEdges)
            return Subgraph(graph, vertices, edges)
        }
    }

    fun subgraph(graph: Graph, inducingVertices: VertexSet, edgeFilter: EdgePredicate): Graph {
        val vertices = InducingVertices(graph, inducingVertices)
        val edges = FilteredEdges(graph, vertices, edgeFilter)
        return Subgraph(graph, vertices, edges)
    }

    fun subgraph(graph: Graph, vertexFilter: VertexPredicate, edgeFilter: EdgePredicate): Graph {
        val vertices = FilteredVertices(graph, vertexFilter)
        val edges = FilteredEdges(graph, vertices, edgeFilter)
        return Subgraph(graph, vertices, edges)
    }
}

internal interface SubgraphVertices : VertexSet {
    fun bind(graph: Graph)

    fun registerVertexChangeListener(listener: VertexChangeListener)
    fun unregisterVertexChangeListener(listener: VertexChangeListener)

    fun <T> createVertexProperty(
        type: StaticType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T>
    fun createVertexReference(vertex: Vertex): VertexReference

    fun trimToSize()
}

internal interface SubgraphEdges : EdgeSet {
    fun bind(graph: Graph)

    fun registerEdgeChangeListener(listener: EdgeChangeListener)
    fun unregisterEdgeChangeListener(listener: EdgeChangeListener)

    fun <T> createEdgeProperty(type: StaticType<T>, defaultValueFunction: EdgeFunction<T>): MutableEdgeProperty<T>
    fun createEdgeReference(edge: Edge): EdgeReference

    fun trimToSize()
}

private abstract class AbstractSubgraph(
    protected val parent: Graph,
    override val vertices: SubgraphVertices,
    override val edges: SubgraphEdges
) : AbstractGraph() {
    init {
        vertices.bind(this)
        edges.bind(this)
    }

    override val directed: Boolean get() = parent.directed
    override val multiEdge: Boolean get() = parent.multiEdge

    override fun validateVertex(vertex: Vertex): Vertex {
        require(vertices.contains(vertex))
        return vertex
    }

    override fun validateEdge(edge: Edge): Edge {
        require(edges.contains(edge))
        return edge
    }

    override fun getOutDegree(vertex: Vertex): Int {
        val parentEdges = parent.outgoingEdges(vertex)
        var count = 0
        parentEdges.foreach { edge ->
            if (edges.contains(edge)) {
                ++count
            }
        }
        return count
    }

    override fun getInDegree(vertex: Vertex): Int {
        val parentEdges = parent.incomingEdges(vertex)
        var count = 0
        parentEdges.foreach { edge ->
            if (edges.contains(edge)) {
                ++count
            }
        }
        return count
    }

    override fun getSuccessors(vertex: Vertex): VertexSet {
        val parentVertices = parent.successors(vertex)
        var successors: IntHashSet? = null
        parentVertices.foreach { vertex ->
            if (vertices.contains(vertex)) {
                if (successors == null) {
                    successors = IntHashSet(parentVertices.size)
                }
                successors.add(vertex.id)
            }
        }
        return successors?.asVertexSet() ?: emptyVertexSet()
    }

    override fun getPredecessors(vertex: Vertex): VertexSet {
        val parentVertices = parent.predecessors(vertex)
        var predecessors: IntHashSet? = null
        parentVertices.foreach { vertex ->
            if (vertices.contains(vertex)) {
                if (predecessors == null) {
                    predecessors = IntHashSet(parentVertices.size)
                }
                predecessors.add(vertex.id)
            }
        }
        return predecessors?.asVertexSet() ?: emptyVertexSet()
    }

    override fun getOutgoingEdges(vertex: Vertex): EdgeSet {
        val parentEdges = parent.outgoingEdges(vertex)
        var outgoing: LongHashSet? = null
        parentEdges.foreach { edge ->
            if (edges.contains(edge)) {
                if (outgoing == null) {
                    outgoing = LongHashSet(parentEdges.size)
                }
                outgoing.add(edge.id)
            }
        }
        return outgoing?.asEdgeSet() ?: emptyEdgeSet()
    }

    override fun getIncomingEdges(vertex: Vertex): EdgeSet {
        val parentEdges = parent.incomingEdges(vertex)
        var incoming: LongHashSet? = null
        parentEdges.foreach { edge ->
            if (edges.contains(edge)) {
                if (incoming == null) {
                    incoming = LongHashSet(parentEdges.size)
                }
                incoming.add(edge.id)
            }
        }
        return incoming?.asEdgeSet() ?: emptyEdgeSet()
    }

    override fun edgeSource(edge: Edge): Vertex = parent.edgeSource(edge)
    override fun edgeTarget(edge: Edge): Vertex = parent.edgeTarget(edge)

    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        if (!parent.hasEdge(source, target)) {
            return false
        }

        for (edge in parent.edges(source, target)) {
            if (edges.contains(edge)) {
                return true
            }
        }

        return false
    }

    override fun getEdge(source: Vertex, target: Vertex): Edge {
        for (edge in parent.edges(source, target)) {
            if (edges.contains(edge)) {
                return edge
            }
        }

        throw NoSuchElementException()
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        val parentEdges = parent.edges(source, target)
        var all: LongHashSet? = null
        parentEdges.foreach { edge ->
            if (edges.contains(edge)) {
                if (all == null) {
                    all = LongHashSet(parentEdges.size)
                }
                all.add(edge.id)
            }
        }
        return all?.asEdgeSet() ?: emptyEdgeSet()
    }

    override fun <T> createVertexProperty(
        type: StaticType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return vertices.createVertexProperty(type, defaultValueFunction)
    }

    override fun <T> createEdgeProperty(
        type: StaticType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return edges.createEdgeProperty(type, defaultValueFunction)
    }

    override fun trimToSize() {
        vertices.trimToSize()
        edges.trimToSize()
    }
}

private class Subgraph(
    parent: Graph,
    vertices: SubgraphVertices,
    edges: SubgraphEdges
) : AbstractSubgraph(parent, vertices, edges) {
    override fun registerVertexChangeListener(listener: VertexChangeListener) = vertices.registerVertexChangeListener(listener)
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) = vertices.unregisterVertexChangeListener(listener)
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) = edges.registerEdgeChangeListener(listener)
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) = edges.unregisterEdgeChangeListener(listener)

    override fun createVertexReference(vertex: Vertex): VertexReference = vertices.createVertexReference(vertex)
    override fun createEdgeReference(edge: Edge): EdgeReference = edges.createEdgeReference(edge)
}

private class ImmutableSubgraph(
    parent: ImmutableGraph,
    vertices: InducingVertices,
    edges: InducingEdges
) : AbstractSubgraph(parent, vertices, edges), InternalImmutableGraph {
    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}

    override fun createVertexReference(vertex: Vertex): VertexReference {
        if (!vertices.contains(vertex)) throwIllegalVertex(vertex)
        return ImmutableVertexReference(vertex)
    }

    override fun createEdgeReference(edge: Edge): EdgeReference {
        if (!edges.contains(edge)) throwIllegalEdge(this, edge)
        return ImmutableEdgeReference(edge)
    }
}
