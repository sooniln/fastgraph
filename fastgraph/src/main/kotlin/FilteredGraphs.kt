/**
 * Methods dealing with filtering graphs.
 */
@file:JvmName("FilteredGraphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.IntHashSet
import io.github.sooniln.fastcollect.LongHashSet
import io.github.sooniln.fastgraph.filtered.FilteredEdges
import io.github.sooniln.fastgraph.filtered.FilteredVertices
import io.github.sooniln.fastgraph.filtered.PredicatedEdges
import io.github.sooniln.fastgraph.filtered.PredicatedVertices
import io.github.sooniln.fastgraph.filtered.InducedEdges
import io.github.sooniln.fastgraph.filtered.InducedVertices

/**
 * A special type of [Graph] which is a filtered live-view of another [Graph]. In order to create a [FilteredGraph] see
 * [Graph.filter]/[ImmutableGraph.filter].
 *
 * A FilteredGraph a filtered live-view of the graph. Both vertices and edges may be filtered, but edge filtering is
 * always applied after vertex filtering (i.e. an edge is only present if both of its endpoints pass the vertex filter
 * AND it passes the edge filter). There are two methods of filtering, with differing trade-offs:
 *
 * If you provide inducing vertices/edges (an explicit set of vertices/edges that defines the filter), this results
 * in:
 *   1. Better FilteredGraph performance (operations generally run in linear time w.r.t the inducing vertices/edges,
 *   and some values can be cached internally so that constant recalculation is not necessary).
 *   2. Less flexibility in defining the filter (the FilteredGraph cannot adapt as vertices/edges change in some
 *   fashion).
 *   3. Vertex/edge listeners function normally on the FilteredGraph.
 *   4. Vertex/edge references function normally on the FilteredGraph.
 *   5. If an inducing vertex/edge is removed from the parent graph it is also removed from the subgraph.
 *   6. If the parent graph is immutable the returned FilteredGraph is also immutable.
 *
 * If you provide an arbitrary predicate as the filter, this results in:
 *   1. Worse FilteredGraph performance (calculating sizes and iteration generally run in linear time w.r.t all the
 *   parent graph's vertices/edges, and values cannot be cached internally - recalculation is always necessary).
 *   2. More flexibility in defining the FilteredGraph (the filtering predicates can take into account any arbitrary
 *   information needed).
 *   3. Since the predicate may depend on arbitrary information that can change at any time it is impossible to
 *   guarantee a consistent view of which vertices/edges are in the FilteredGraph. This further implies:
 *     a. Vertex/edge listeners are unsupported on the FilteredGraph and will throw [UnsupportedOperationException].
 *     b. Vertex/edge references are unsupported on the FilteredGraph and will throw [UnsupportedOperationException].
 *     c. The returned FilteredGraph is not immutable regardless of whether the parent is immutable.
 */
public interface FilteredGraph : Graph {
    /** The graph that is being filtered to produce this FilteredGraph. */
    public val parent: Graph
}

/**
 * A [FilteredGraph] which is immutable due to having an [ImmutableGraph] [parent] and inducing vertices/edges.
 */
public sealed interface ImmutableFilteredGraph : FilteredGraph, ImmutableGraph {
    override val parent: ImmutableGraph
}

/**
 * Returns a live-view of the graph with filtered vertices and edges. See [FilteredGraph] for more information on the
 * various types of filtering.
 *
 * Vertices/edges are filtered by inducing from the given [inducingVertices] and [inducingEdges]. A copy is made of
 * both, so future changes to either collection will have no effect on the returned [FilteredGraph]. Passing null for
 * [inducingVertices] implies that the set of inducing vertices is always the same as all parent graph vertices (i.e.
 * all vertices always pass the filter).
 */
public fun Graph.filter(inducingVertices: VertexSet? = null, inducingEdges: EdgeSet): FilteredGraph {
    if (this is ImmutableGraph) {
        return filter(inducingVertices, inducingEdges)
    } else {
        val vertices = if (inducingVertices == null) {
            InducedVertices.from(this)
        } else {
            InducedVertices.from(this, inducingVertices)
        }
        val edges = InducedEdges(this, vertices, inducingEdges)
        return FilteringGraph(this, vertices, edges)
    }
}

/**
 * Returns a live-view of the graph with filtered vertices and edges. See [FilteredGraph] for more information on the
 * various types of filtering.
 *
 * Vertices/edges are filtered by inducing from the given [inducingVertices] and the given [edgeFilter]. A copy is
 * made of [inducingVertices], so future changes to the collection will have no effect on the returned [FilteredGraph].
 * Passing null for [inducingVertices] implies that the set of inducing vertices is always the same as all parent graph
 * vertices (i.e. all vertices always pass the filter).
 */
public fun Graph.filter(inducingVertices: VertexSet? = null, edgeFilter: EdgePredicate): FilteredGraph {
    val vertices = if (inducingVertices == null) {
        InducedVertices.from(this)
    } else {
        InducedVertices.from(this, inducingVertices)
    }
    val edges = PredicatedEdges(this, vertices, edgeFilter)
    return FilteringGraph(this, vertices, edges)
}

/**
 * Returns a live-view of the graph with filtered vertices and edges. See [FilteredGraph] for more information on the
 * various types of filtering.
 */
public fun Graph.filter(vertexFilter: VertexPredicate, edgeFilter: EdgePredicate = { true }): FilteredGraph {
    val vertices = PredicatedVertices(this, vertexFilter)
    val edges = PredicatedEdges(this, vertices, edgeFilter)
    return FilteringGraph(this, vertices, edges)
}

/**
 * Returns an immutable view of the immutable graph with filtered vertices and edges. See [FilteredGraph] for more
 * information on the various types of filtering.
 *
 * Vertices/edges are filtered by inducing from the given [inducingVertices] and [inducingEdges]. A copy is made of
 * both, so future changes to either collection will have no effect on the returned [FilteredGraph]. Passing null for
 * [inducingVertices] implies that the set of inducing vertices is always the same as all parent graph vertices (i.e.
 * all vertices are pass the filter).
 */
public fun ImmutableGraph.filter(inducingVertices: VertexSet? = null, inducingEdges: EdgeSet): ImmutableFilteredGraph {
    val vertices = if (inducingVertices == null) {
        InducedVertices.from(this)
    } else {
        InducedVertices.from(this, inducingVertices)
    }
    val edges = InducedEdges(this, vertices, inducingEdges)
    return ImmutableFilteringGraph(this, vertices, edges)
}

private class FilteringGraph(
    parent: Graph,
    vertices: FilteredVertices,
    edges: FilteredEdges
) : AbstractFilteredGraph<Graph>(parent, vertices, edges) {
    override fun registerVertexChangeListener(listener: VertexChangeListener) = vertices.registerVertexChangeListener(listener)
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) = vertices.unregisterVertexChangeListener(listener)
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) = edges.registerEdgeChangeListener(listener)
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) = edges.unregisterEdgeChangeListener(listener)

    override fun createVertexReference(vertex: Vertex): VertexReference = vertices.createVertexReference(vertex)
    override fun createEdgeReference(edge: Edge): EdgeReference = edges.createEdgeReference(edge)
}

private class ImmutableFilteringGraph(
    parent: ImmutableGraph,
    vertices: InducedVertices,
    edges: InducedEdges
) : AbstractFilteredGraph<ImmutableGraph>(parent, vertices, edges), ImmutableFilteredGraph

private abstract class AbstractFilteredGraph<G : Graph>(
    override val parent: G,
    override val vertices: FilteredVertices,
    override val edges: FilteredEdges
) : AbstractGraph(), FilteredGraph {
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
        for (edge in parentEdges) {
            if (edges.contains(edge)) {
                ++count
            }
        }
        return count
    }

    override fun getInDegree(vertex: Vertex): Int {
        val parentEdges = parent.incomingEdges(vertex)
        var count = 0
        for (edge in parentEdges) {
            if (edges.contains(edge)) {
                ++count
            }
        }
        return count
    }

    override fun getSuccessors(vertex: Vertex): VertexSet {
        val parentVertices = parent.successors(vertex)
        var successors: IntHashSet? = null
        for (vertex in parentVertices) {
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
        for (vertex in parentVertices) {
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
        for (edge in parentEdges) {
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
        for (edge in parentEdges) {
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
        var foundEdge = false
        var singleEdge = Edge(0)
        for (edge in parent.edges(source, target)) {
            if (edges.contains(edge)) {
                check(!foundEdge)
                foundEdge = true
                singleEdge = edge
            }
        }

        check(foundEdge)
        return singleEdge
    }

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet {
        val parentEdges = parent.edges(source, target)
        var all: LongHashSet? = null
        for (edge in parentEdges) {
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
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return vertices.createVertexProperty(type, defaultValueFunction)
    }

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return edges.createEdgeProperty(type, defaultValueFunction)
    }

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return vertices.createVertexKeyProperty(type)
    }

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        return edges.createEdgeKeyProperty(type)
    }

    override fun trimToSize() {
        vertices.trimToSize()
        edges.trimToSize()
    }
}
