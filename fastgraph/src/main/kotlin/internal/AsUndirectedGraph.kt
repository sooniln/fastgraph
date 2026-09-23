package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeChangeListener
import io.github.sooniln.fastgraph.EdgeFunction
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexChangeListener
import io.github.sooniln.fastgraph.VertexFunction
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.reparent
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.references.VertexReference

internal abstract class AbstractUndirectedGraph(val graph: Graph) : Graph by graph {

    init {
        require(graph.directed)
    }

    override val directed: Boolean get() = false
    override val multiEdge: Boolean get() = true

    // a self-loop is both an outgoing and incoming edge in the original graph, but should only be counted once
    private fun degree(vertex: Vertex): Int =
        graph.outDegree(vertex) + graph.inDegree(vertex) - graph.edges(vertex, vertex).size

    override fun outDegree(vertex: Vertex): Int = degree(vertex)
    override fun inDegree(vertex: Vertex): Int = degree(vertex)
    override fun successors(vertex: Vertex): VertexSet = NeighborVertexSet(vertex)
    override fun successor(vertex: Vertex): Vertex = super.successor(vertex)
    override fun predecessors(vertex: Vertex): VertexSet = NeighborVertexSet(vertex)
    override fun predecessor(vertex: Vertex): Vertex = super.predecessor(vertex)
    override fun outgoingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(vertex)
    override fun outgoingEdge(vertex: Vertex): Edge = super.outgoingEdge(vertex)
    override fun incomingEdges(vertex: Vertex): EdgeSet = IncidentEdgeSet(vertex)
    override fun incomingEdge(vertex: Vertex): Edge = super.incomingEdge(vertex)

    // a directed CanonicalEdge is not necessarily a valid undirected CanonicalEdge (which requires source <= target),
    // so make sure the edge container is not marked as CanonicalEdgeSet (so consumers down the line treat the edges as
    // opaque).
    override val edges: EdgeSet = if (graph.edges is CanonicalEdgeSet) {
        object : AbstractEdgeSet(), EdgeSet by graph.edges {}
    } else {
        graph.edges
    }

    override fun hasEdge(source: Vertex, target: Vertex): Boolean =
        graph.hasEdge(source, target) || graph.hasEdge(target, source)
    override fun edge(source: Vertex, target: Vertex): Edge = super.edge(source, target)
    override fun edges(source: Vertex, target: Vertex): EdgeSet {
        if (source == target) return graph.edges(source, source)

        return object : AbstractEdgeSet() {
            private val forwardEdges = graph.edges(source, target)
            private val backwardEdges = graph.edges(target, source)
            override val size: Int get() = forwardEdges.size + backwardEdges.size
            override fun contains(element: Edge): Boolean =
                forwardEdges.contains(element) || backwardEdges.contains(element)
            override fun iterator(): EdgeIterator = object : EdgeIterator {
                private val forwardIt = forwardEdges.iterator()
                private val backwardIt = backwardEdges.iterator()
                override fun hasNext(): Boolean = forwardIt.hasNext() || backwardIt.hasNext()
                override fun next(): Edge = if (forwardIt.hasNext()) forwardIt.next() else backwardIt.next()
            }
        }
    }

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

    // all edges connected to the given vertex, in either direction
    private inner class IncidentEdgeSet(private val vertex: Vertex) : AbstractEdgeSet() {
        private val outgoingEdges = graph.outgoingEdges(vertex)
        private val incomingEdges = graph.incomingEdges(vertex)

        override val size: Int get() = degree(vertex)
        override fun contains(element: Edge): Boolean =
            outgoingEdges.contains(element) || incomingEdges.contains(element)
        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private val outgoingIt = outgoingEdges.iterator()
            private val incomingIt = incomingEdges.iterator()
            private var nextEdge = Edge(0)
            private var hasNextEdge = advance()

            private fun advance(): Boolean {
                if (outgoingIt.hasNext()) {
                    nextEdge = outgoingIt.next()
                    return true
                }
                while (incomingIt.hasNext()) {
                    val edge = incomingIt.next()
                    // a self-loop was already returned as an outgoing edge
                    if (graph.edgeSource(edge) != vertex) {
                        nextEdge = edge
                        return true
                    }
                }
                return false
            }

            override fun hasNext(): Boolean = hasNextEdge
            override fun next(): Edge {
                if (!hasNextEdge) throw NoSuchElementException()
                val edge = nextEdge
                hasNextEdge = advance()
                return edge
            }
        }
    }

    // all vertices adjacent to the given vertex, in either direction
    private inner class NeighborVertexSet(vertex: Vertex) : AbstractVertexSet() {
        private val successors = graph.successors(vertex)
        private val predecessors = graph.predecessors(vertex)

        override val size: Int get() = successors.size + predecessors.count { !successors.contains(it) }
        override fun contains(element: Vertex): Boolean =
            successors.contains(element) || predecessors.contains(element)
        override fun iterator(): VertexIterator = object : VertexIterator {
            private val successorsIt = successors.iterator()
            private val predecessorsIt = predecessors.iterator()
            private var nextVertex = Vertex(0)
            private var hasNextVertex = advance()

            private fun advance(): Boolean {
                if (successorsIt.hasNext()) {
                    nextVertex = successorsIt.next()
                    return true
                }
                while (predecessorsIt.hasNext()) {
                    val vertex = predecessorsIt.next()
                    // a vertex that is both a successor and predecessor was already returned as a successor
                    if (!successors.contains(vertex)) {
                        nextVertex = vertex
                        return true
                    }
                }
                return false
            }

            override fun hasNext(): Boolean = hasNextVertex
            override fun next(): Vertex {
                if (!hasNextVertex) throw NoSuchElementException()
                val vertex = nextVertex
                hasNextVertex = advance()
                return vertex
            }
        }
    }
}

internal class UndirectedGraph(graph: Graph) : AbstractUndirectedGraph(graph)

internal class ImmutableUndirectedGraph(
    graph: ImmutableGraph
) : AbstractUndirectedGraph(graph), InternalImmutableGraph {

    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun createVertexReference(vertex: Vertex): VertexReference = graph.createVertexReference(vertex)
    override fun createEdgeReference(edge: Edge): EdgeReference = graph.createEdgeReference(edge)
}
