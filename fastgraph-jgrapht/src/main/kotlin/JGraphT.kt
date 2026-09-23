/**
 * Methods for viewing FastGraph graphs as JGraphT graphs.
 */
@file:JvmName("JGraphT")
package io.github.sooniln.fastgraph.jgrapht

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.EdgeProperty
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.properties.VertexKeyProperty
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.edgeIdProperty
import io.github.sooniln.fastgraph.vertexIdProperty
import org.jgrapht.GraphType
import org.jgrapht.graph.AbstractGraph
import org.jgrapht.graph.DefaultGraphType
import java.util.function.Supplier

/**
 * Returns an unmodifiable JGraphT view of this graph, for use with JGraphT APIs. The view is live - changes to
 * this graph are reflected in the returned JGraphT graph.
 *
 * The properties given by [vertexKeys] and [edgeKeys] are used to map between this graph's vertices/edges and JGraphT's
 * Vertex/Edge types - the key of each vertex/edge is its JGraphT vertex/edge. If you have no meaningful keys,
 * [vertexIdProperty] and [edgeIdProperty] may be used.
 *
 * If [weights] is provided, the returned graph is weighted and reads edge weights from it. Otherwise, the returned
 * graph is unweighted and every edge has weight [org.jgrapht.Graph.DEFAULT_EDGE_WEIGHT].
 *
 * All JGraphT methods that modify the returned graph throw [UnsupportedOperationException] - see [asMutableJGraphT] for
 * a modifiable view. Sets returned by the JGraphT graph are unmodifiable live views, except for
 * [org.jgrapht.Graph.getAllEdges] and [org.jgrapht.Graph.edgesOf] on directed graphs, which return snapshots.
 */
@JvmOverloads
public fun <V, E> Graph.asJGraphT(
    vertexKeys: VertexKeyProperty<V>,
    edgeKeys: EdgeKeyProperty<E>,
    weights: EdgeProperty<Double>? = null,
): org.jgrapht.Graph<V, E> {
    require(vertexKeys.graph === this)
    require(edgeKeys.graph === this)
    require(weights == null || weights.graph === this)
    return JGraphTGraphView(this, vertexKeys, edgeKeys, weights, modifiable = false)
}

/**
 * Returns an unmodifiable JGraphT view of this graph, using vertex IDs and edge IDs as the JGraphT Vertex/Edge types.
 * See [asJGraphT] for more documentation.
 */
@JvmOverloads
public fun Graph.asJGraphT(weights: EdgeProperty<Double>? = null): org.jgrapht.Graph<Int, Long> {
    return asJGraphT(vertexIdProperty, edgeIdProperty, weights)
}

/**
 * Returns a modifiable JGraphT view of this graph, for use with JGraphT APIs. See [asJGraphT] for the general
 * semantics of the view - in addition, modifications made through the returned JGraphT graph are applied to this
 * graph and to [vertexKeys]/[edgeKeys].
 *
 * [org.jgrapht.Graph.addVertex] and [org.jgrapht.Graph.addEdge] without an explicit vertex/edge require
 * [vertexSupplier]/[edgeSupplier] respectively to generate new keys, and throw [UnsupportedOperationException] if
 * the relevant supplier is not provided. [org.jgrapht.Graph.setEdgeWeight] throws [UnsupportedOperationException] if
 * [weights] is not provided.
 */
@JvmOverloads
public fun <V, E> MutableGraph.asMutableJGraphT(
    vertexKeys: MutableVertexKeyProperty<V>,
    edgeKeys: MutableEdgeKeyProperty<E>,
    weights: MutableEdgeProperty<Double>? = null,
    vertexSupplier: Supplier<V>? = null,
    edgeSupplier: Supplier<E>? = null,
): org.jgrapht.Graph<V, E> {
    require(vertexKeys.graph === this)
    require(edgeKeys.graph === this)
    require(weights == null || weights.graph === this)
    return MutableJGraphTGraphView(this, vertexKeys, edgeKeys, weights, vertexSupplier, edgeSupplier)
}

/** A read-only JGraphT view of a [Graph], using key properties to map vertices/edges to JGraphT vertex/edge types. */
private open class JGraphTGraphView<V, E>(
    private val graph: Graph,
    private val vertexKeys: VertexKeyProperty<V>,
    private val edgeKeys: EdgeKeyProperty<E>,
    private val weights: EdgeProperty<Double>?,
    modifiable: Boolean,
) : AbstractGraph<V, E>() {

    private val type: GraphType = DefaultGraphType.Builder(graph.directed, !graph.directed)
        .allowSelfLoops(true)
        .allowMultipleEdges(graph.multiEdge)
        .allowCycles(true)
        .weighted(weights != null)
        .modifiable(modifiable)
        .build()

    private val vertexSet: Set<V> = KeyedVertexSet(graph.vertices)
    private val edgeSet: Set<E> = KeyedEdgeSet(graph.edges)

    protected fun vertex(key: V): Vertex {
        require(vertexKeys.hasVertex(key)) { "no such vertex in graph: $key" }
        return vertexKeys.getVertex(key)
    }

    protected fun edge(key: E): Edge {
        require(edgeKeys.hasEdge(key)) { "no such edge in graph: $key" }
        return edgeKeys.getEdge(key)
    }

    override fun getType(): GraphType = type

    override fun getVertexSupplier(): Supplier<V>? = null
    override fun getEdgeSupplier(): Supplier<E>? = null

    override fun vertexSet(): Set<V> = vertexSet
    override fun edgeSet(): Set<E> = edgeSet

    override fun containsVertex(v: V): Boolean = vertexKeys.hasVertex(v)
    override fun containsEdge(e: E): Boolean = edgeKeys.hasEdge(e)

    override fun getEdgeSource(e: E): V = vertexKeys[graph.edgeSource(edge(e))]
    override fun getEdgeTarget(e: E): V = vertexKeys[graph.edgeTarget(edge(e))]

    override fun getEdge(sourceVertex: V, targetVertex: V): E? {
        if (!vertexKeys.hasVertex(sourceVertex) || !vertexKeys.hasVertex(targetVertex)) return null
        val edges = graph.edges(vertexKeys.getVertex(sourceVertex), vertexKeys.getVertex(targetVertex))
        return if (edges.isEmpty()) null else edgeKeys[edges.iterator().next()]
    }

    override fun getAllEdges(sourceVertex: V, targetVertex: V): Set<E>? {
        if (!vertexKeys.hasVertex(sourceVertex) || !vertexKeys.hasVertex(targetVertex)) return null
        // snapshot rather than live view - AbstractGraph.removeAllEdges(V, V) removes edges while iterating this
        return graph.edges(vertexKeys.getVertex(sourceVertex), vertexKeys.getVertex(targetVertex))
            .mapTo(LinkedHashSet()) { edgeKeys[it] }
    }

    override fun outgoingEdgesOf(vertex: V): Set<E> = KeyedEdgeSet(graph.outgoingEdges(vertex(vertex)))
    override fun incomingEdgesOf(vertex: V): Set<E> = KeyedEdgeSet(graph.incomingEdges(vertex(vertex)))

    override fun edgesOf(vertex: V): Set<E> {
        val v = vertex(vertex)
        if (!graph.directed) return KeyedEdgeSet(graph.outgoingEdges(v))
        val edges = LinkedHashSet<E>()
        graph.outgoingEdges(v).mapTo(edges) { edgeKeys[it] }
        graph.incomingEdges(v).mapTo(edges) { edgeKeys[it] }
        return edges
    }

    override fun outDegreeOf(vertex: V): Int = graph.outDegree(vertex(vertex))
    override fun inDegreeOf(vertex: V): Int = graph.inDegree(vertex(vertex))

    override fun degreeOf(vertex: V): Int {
        val v = vertex(vertex)
        return if (graph.directed) {
            graph.outDegree(v) + graph.inDegree(v)
        } else {
            // JGraphT counts an undirected self-loop twice, fastgraph counts it once
            graph.outDegree(v) + graph.edges(v, v).size
        }
    }

    override fun getEdgeWeight(e: E): Double {
        val edge = edge(e)
        return weights?.get(edge) ?: DEFAULT_EDGE_WEIGHT
    }

    override fun setEdgeWeight(e: E, weight: Double): Unit = throw UnsupportedOperationException()

    override fun addVertex(): V = throw UnsupportedOperationException()
    override fun addVertex(v: V): Boolean = throw UnsupportedOperationException()
    override fun addEdge(sourceVertex: V, targetVertex: V): E? = throw UnsupportedOperationException()
    override fun addEdge(sourceVertex: V, targetVertex: V, e: E): Boolean = throw UnsupportedOperationException()
    override fun removeVertex(v: V): Boolean = throw UnsupportedOperationException()
    override fun removeEdge(e: E): Boolean = throw UnsupportedOperationException()
    override fun removeEdge(sourceVertex: V, targetVertex: V): E? = throw UnsupportedOperationException()

    private inner class KeyedVertexSet(private val vertices: VertexSet) : AbstractSet<V>() {
        override val size: Int get() = vertices.size
        override fun contains(element: V): Boolean =
            vertexKeys.hasVertex(element) && vertices.contains(vertexKeys.getVertex(element))
        override fun iterator(): Iterator<V> = object : Iterator<V> {
            private val iterator = vertices.iterator()
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): V = vertexKeys[iterator.next()]
        }
    }

    private inner class KeyedEdgeSet(private val edges: EdgeSet) : AbstractSet<E>() {
        override val size: Int get() = edges.size
        override fun contains(element: E): Boolean =
            edgeKeys.hasEdge(element) && edges.contains(edgeKeys.getEdge(element))
        override fun iterator(): Iterator<E> = object : Iterator<E> {
            private val iterator = edges.iterator()
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): E = edgeKeys[iterator.next()]
        }
    }
}

/** A modifiable JGraphT view of a [MutableGraph]. */
private class MutableJGraphTGraphView<V, E>(
    private val graph: MutableGraph,
    private val vertexKeys: MutableVertexKeyProperty<V>,
    private val edgeKeys: MutableEdgeKeyProperty<E>,
    private val weights: MutableEdgeProperty<Double>?,
    private val vertexSupplier: Supplier<V>?,
    private val edgeSupplier: Supplier<E>?,
) : JGraphTGraphView<V, E>(graph, vertexKeys, edgeKeys, weights, modifiable = true) {

    override fun getVertexSupplier(): Supplier<V>? = vertexSupplier
    override fun getEdgeSupplier(): Supplier<E>? = edgeSupplier

    override fun setEdgeWeight(e: E, weight: Double) {
        if (weights == null) throw UnsupportedOperationException("graph is not weighted")
        weights[edge(e)] = weight
    }

    override fun addVertex(): V {
        val supplier = vertexSupplier ?: throw UnsupportedOperationException("graph has no vertex supplier")
        val v = supplier.get()
        require(!vertexKeys.hasVertex(v)) { "vertex supplier returned a vertex already in the graph: $v" }
        vertexKeys[graph.addVertex()] = v
        return v
    }

    override fun addVertex(v: V): Boolean {
        if (vertexKeys.hasVertex(v)) return false
        vertexKeys[graph.addVertex()] = v
        return true
    }

    override fun addEdge(sourceVertex: V, targetVertex: V): E? {
        val supplier = edgeSupplier ?: throw UnsupportedOperationException("graph has no edge supplier")
        val source = vertex(sourceVertex)
        val target = vertex(targetVertex)
        if (!graph.multiEdge && graph.hasEdge(source, target)) return null
        val e = supplier.get()
        require(!edgeKeys.hasEdge(e)) { "edge supplier returned an edge already in the graph: $e" }
        edgeKeys[graph.addEdge(source, target)] = e
        return e
    }

    override fun addEdge(sourceVertex: V, targetVertex: V, e: E): Boolean {
        val source = vertex(sourceVertex)
        val target = vertex(targetVertex)
        if (edgeKeys.hasEdge(e)) return false
        if (!graph.multiEdge && graph.hasEdge(source, target)) return false
        edgeKeys[graph.addEdge(source, target)] = e
        return true
    }

    override fun removeVertex(v: V): Boolean {
        if (!vertexKeys.hasVertex(v)) return false
        graph.removeVertex(vertexKeys.getVertex(v))
        return true
    }

    override fun removeEdge(e: E): Boolean {
        if (!edgeKeys.hasEdge(e)) return false
        graph.removeEdge(edgeKeys.getEdge(e))
        return true
    }

    override fun removeEdge(sourceVertex: V, targetVertex: V): E? {
        val e = getEdge(sourceVertex, targetVertex) ?: return null
        graph.removeEdge(edgeKeys.getEdge(e))
        return e
    }
}
