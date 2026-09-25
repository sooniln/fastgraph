/**
 * Methods dealing with filtering graphs.
 */
@file:JvmName("FilteredGraphs")

package io.github.sooniln.fastgraph.filtered

import io.github.sooniln.fastgraph.*
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.references.VertexReference
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty

/**
 * Returns a live-view of the graph with filtered vertices and edges. Both vertices and edges may be filtered, but edge
 * filtering is always applied after vertex filtering (i.e. an edge is only present if both of its endpoints pass the
 * vertex filter AND it passes the edge filter). There are two methods of filtering, with differing trade-offs:
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
 *     c. The returned FilteredGraph is not immutable regardless of whether the parent is immutable (since a change to
 *     the filtering predicate can change the graph topology even if the parent is unchanged).
 *
 * In this method, vertices/edges are filtered by inducing from the given [inducingVertices] and [inducingEdges]. A copy
 * is made of both, so future changes to either collection will have no effect on the returned [Graph]. Passing null for
 * [inducingVertices] implies that the set of inducing vertices is always the same as all parent graph vertices (i.e.
 * all vertices always pass the filter).
 */
public fun Graph.filter(inducingVertices: VertexSet? = null, inducingEdges: EdgeSet): Graph {
    if (this is ImmutableGraph) {
        return filter(inducingVertices, inducingEdges)
    } else {
        val vertices = if (inducingVertices == null) {
            InducedVertices.from(this)
        } else {
            InducedVertices.from(this, inducingVertices)
        }
        val edges = InducedEdges.from(this, vertices, inducingEdges)
        return FilteringGraph(this, vertices, edges)
    }
}

/**
 * Returns a live-view of the graph with filtered vertices and edges. Both vertices and edges may be filtered, but edge
 * filtering is always applied after vertex filtering (i.e. an edge is only present if both of its endpoints pass the
 * vertex filter AND it passes the edge filter). There are two methods of filtering, with differing trade-offs:
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
 *     c. The returned FilteredGraph is not immutable regardless of whether the parent is immutable (since a change to
 *     the filtering predicate can change the graph topology even if the parent is unchanged).
 *
 * In this method vertices/edges are filtered by inducing from the given [inducingVertices] and the given [edgeFilter].
 * A copy is made of [inducingVertices], so future changes to the collection will have no effect on the returned
 * [Graph]. Passing null for [inducingVertices] implies that the set of inducing vertices is always the same as all
 * parent graph vertices (i.e. all vertices always pass the filter).
 */
public fun Graph.filter(inducingVertices: VertexSet? = null, edgeFilter: EdgePredicate): Graph {
    val vertices = if (inducingVertices == null) {
        InducedVertices.from(this)
    } else {
        InducedVertices.from(this, inducingVertices)
    }
    val edges = PredicatedEdges.from(this, vertices, edgeFilter)
    return FilteringGraph(this, vertices, edges)
}

/**
 * Returns a live-view of the graph with filtered vertices and edges. Both vertices and edges may be filtered, but edge
 * filtering is always applied after vertex filtering (i.e. an edge is only present if both of its endpoints pass the
 * vertex filter AND it passes the edge filter). There are two methods of filtering, with differing trade-offs:
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
 *     c. The returned FilteredGraph is not immutable regardless of whether the parent is immutable (since a change to
 *     the filtering predicate can change the graph topology even if the parent is unchanged).
 *
 * In this method vertices/edges are filtered by the given [vertexFilter] and [edgeFilter].
 */
public fun Graph.filter(vertexFilter: VertexPredicate, edgeFilter: EdgePredicate = { true }): Graph {
    val vertices = PredicatedVertices(this, vertexFilter)
    val edges = PredicatedEdges.from(this, vertices, edgeFilter)
    return FilteringGraph(this, vertices, edges)
}

/**
 * Returns an immutable view of the immutable graph with filtered vertices and edges. Both vertices and edges may be
 * filtered, but edge filtering is always applied after vertex filtering (i.e. an edge is only present if both of its
 * endpoints pass the vertex filter AND it passes the edge filter). There are two methods of filtering, with differing
 * trade-offs:
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
 *     c. The returned FilteredGraph is not immutable regardless of whether the parent is immutable (since a change to
 *     the filtering predicate can change the graph topology even if the parent is unchanged).
 *
 * In this method vertices/edges are filtered by inducing from the given [inducingVertices] and [inducingEdges]. A copy
 * is made of both, so future changes to either collection will have no effect on the returned [Graph]. Passing null for
 * [inducingVertices] implies that the set of inducing vertices is always the same as all parent graph vertices (i.e.
 * all vertices always pass the filter).
 */
public fun ImmutableGraph.filter(inducingVertices: VertexSet? = null, inducingEdges: EdgeSet): ImmutableGraph {
    val vertices = if (inducingVertices == null) {
        InducedVertices.from(this)
    } else {
        InducedVertices.from(this, inducingVertices)
    }
    val edges = InducedEdges.from(this, vertices, inducingEdges)
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

    override fun createVertexReference(vertex: Vertex): VertexReference =
        vertices.createVertexReference(this, validateVertex(vertex))
    override fun createEdgeReference(edge: Edge): EdgeReference = edges.createEdgeReference(this, validateEdge(edge))
}

private class ImmutableFilteringGraph(
    parent: ImmutableGraph,
    vertices: InducedVertices,
    edges: InducedEdges
) : AbstractFilteredGraph<ImmutableGraph>(parent, vertices, edges), InternalImmutableGraph

private abstract class AbstractFilteredGraph<G : Graph>(
    protected val parent: G,
    override val vertices: FilteredVertices,
    override val edges: FilteredEdges
) : AbstractGraph<EdgeSet>() {

    override val directed: Boolean get() = parent.directed
    override val multiEdge: Boolean get() = parent.multiEdge

    override fun validateVertex(vertex: Vertex): Vertex {
        require(vertices.contains(vertex))
        return vertex
    }

    protected fun validateEdge(edge: Edge): Edge {
        require(edges.contains(edge))
        return edge
    }

    override fun getOutDegree(vertex: Vertex): Int {
        val degree = parent.outgoingEdges(vertex).count { edges.contains(it) }
        return if (directed) degree else degree + parent.edges(vertex, vertex).count { edges.contains(it) }
    }
    override fun getInDegree(vertex: Vertex): Int = parent.incomingEdges(vertex).count { edges.contains(it) }
    override fun getSuccessors(vertex: Vertex): VertexSet = FilteredVertexSet(vertex, parent.successors(vertex), successors = true)
    override fun getPredecessors(vertex: Vertex): VertexSet = FilteredVertexSet(vertex, parent.predecessors(vertex), successors = false)
    override fun getOutgoingEdges(vertex: Vertex): EdgeSet = edges.filter(parent.outgoingEdges(vertex))
    override fun getIncomingEdges(vertex: Vertex): EdgeSet = edges.filter(parent.incomingEdges(vertex))

    override fun edgeSource(edge: Edge): Vertex = parent.edgeSource(edge)
    override fun edgeTarget(edge: Edge): Vertex = parent.edgeTarget(edge)

    override fun containsEdge(source: Vertex, target: Vertex): Boolean {
        return parent.hasEdge(source, target) && parent.edges(source, target).any { edges.contains(it) }
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

    override fun getEdges(source: Vertex, target: Vertex): EdgeSet = edges.filter(parent.edges(source, target))

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return vertices.createVertexProperty(this, type, defaultValueFunction)
    }

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return edges.createEdgeProperty(this, type, defaultValueFunction)
    }

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return vertices.createVertexKeyProperty(this, type)
    }

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        return edges.createEdgeKeyProperty(this, type)
    }

    override fun trimToSize() {
        vertices.trimToSize()
        edges.trimToSize()
    }

    private inner class FilteredVertexSet(
        private val vertex: Vertex,
        private val parentVertices: VertexSet,
        private val successors: Boolean
    ) : AbstractVertexSet() {
        override val size: Int get()  = parentVertices.count{ test(it) }
        override fun contains(element: Vertex): Boolean = parentVertices.contains(element) && test(element)
        override fun iterator(): VertexIterator = object : VertexIterator {
            private val it = parentVertices.iterator()
            private var next = Vertex(0)
            private var done = false

            init { increment() }

            override fun hasNext(): Boolean = !done
            override fun next(): Vertex {
                if (!hasNext()) throw NoSuchElementException()
                return next.also { increment() }
            }

            private fun increment() {
                while (it.hasNext()) {
                    next = it.next()
                    if (test(next)) return
                }
                done = true
            }
        }

        private fun test(neighbor: Vertex): Boolean {
            return if (successors) containsEdge(vertex, neighbor) else containsEdge(neighbor, vertex)
        }
    }
}
