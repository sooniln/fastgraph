/**
 * Builders and utilities for [ImmutableGraph].
 */
@file:JvmMultifileClass @file:JvmName("ImmutableGraphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListGraphBuilder
import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListNetworkBuilder
import io.github.sooniln.fastgraph.internal.emptyEdgeProperty
import io.github.sooniln.fastgraph.internal.emptyVertexProperty
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A [Graph] whose topology will never change. This class offers similar guarantees to most immutable collections:
 *
 *   * **Shallow immutability:** Vertices and edge can never be added or removed in this graph.
 *   * **Deterministic iteration:** The iteration order of vertices and edges will never change.
 *   * **Thread safety**: It is safe to access this graph concurrently from multiple threads.
 *   * **Integrity**: This class cannot be subclassed outside this package (which would allow these guarantees to be
 *   violated).
 *
 * Generally speaking [ImmutableGraph] implementations are expected to be more efficient than mutable [Graph]
 * implementations in terms of memory required to store the topology and vertex/edge properties, and the fastest in
 * terms of accessing or iterating over the topology and vertex/edge properties. If memory or CPU efficiency are a
 * concern, using ImmutableGraph is generally the best way to meet those concerns.
 *
 * To create immutable graphs, see the [immutableGraph] factory method.
 */
sealed interface ImmutableGraph : Graph {
    override val vertices: VertexSetList
}

// exists purely to allow implementations in different packages
internal abstract class AbstractImmutableGraph : ImmutableGraph

/**
 * Returns an immutable empty graph with the given directedness.
 */
fun emptyImmutableGraph(directed: Boolean): ImmutableGraph =
    if (directed) EmptyGraph.DIRECTED else EmptyGraph.UNDIRECTED

private class EmptyGraph(override val directed: Boolean) : ImmutableGraph {
    companion object {
        val DIRECTED = EmptyGraph(true)
        val UNDIRECTED = EmptyGraph(false)
    }

    override val multiEdge: Boolean
        get() = false

    override val vertices: VertexSetList
        get() = emptyVertexSet()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSet = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet = throw IllegalArgumentException()

    override val edges: EdgeSetList
        get() = emptyEdgeSet()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    override fun containsEdge(source: Vertex, target: Vertex): Boolean = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    override fun getEdge(source: Vertex, target: Vertex): Edge = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet = throw IllegalArgumentException()

    override fun <T : S?, S> createVertexProperty(clazz: Class<S>, initializer: (Vertex) -> T): VertexProperty<T> =
        emptyVertexProperty()

    override fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: (Edge) -> T): EdgeProperty<T> =
        emptyEdgeProperty()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference = throw IllegalArgumentException()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference = throw IllegalArgumentException()
}

/**
 * Creates an [ImmutableGraphBuilder] with the given directedness.
 *
 * There are several parameters that help control the specific graph implementation chosen:
 *   * `allowMultiEdge`: Controls whether the returned mutable graph supports adding multi-edges (multiple
 *   edges that connect the same pair of vertices in the same direction). If a client attempts to add a multi-edge to a
 *   [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be thrown.
 *   * `optimizeEdges`: If set to true, uses additional memory to speed up edge and edge property access and iteration.
 *   While this increases the amount of memory required to store edge topology, it can reduce the amount of memory
 *   needed to store edge properties, and thus in some circumstances may result in less overall memory usage for the
 *   graph topology + data.
 *
 * Note that the returned builder, while it has vertex and edge types, will not produce vertex or edge properties unless
 * [ImmutableGraphBuilder.withVertexProperty] and [ImmutableGraphBuilder.withEdgeProperty] respectively are invoked.
 */
@JvmName("immutableGraphTyped")
fun <V, E> immutableGraph(
    directed: Boolean,
    allowMultiEdge: Boolean = false,
    optimizeEdges: Boolean = false
): ImmutableGraphBuilder<V, E> {
    return if (allowMultiEdge || optimizeEdges) {
        ImmutableAdjacencyListNetworkBuilder(directed)
    } else {
        ImmutableAdjacencyListGraphBuilder(directed)
    }
}

/**
 * Creates an [ImmutableGraphBuilder] with the given directedness.
 *
 * See overload for further documentation.
 */
fun immutableGraph(
    directed: Boolean,
    allowMultiEdge: Boolean = false,
    optimizeEdges: Boolean = false
): ImmutableGraphBuilder<Nothing, Nothing> {
    return immutableGraph<Nothing, Nothing>(directed, allowMultiEdge, optimizeEdges)
}

/**
 * This interface is used to construct [ImmutableGraph] instances and related properties.
 */
abstract class ImmutableGraphBuilder<V, E> {

    /**
     * Changes the vertex type as given, and will create a vertex property of the given type when the [ImmutableGraph]
     * is constructed. The default initializer value simply throws [IllegalStateException], which implies that a
     * property value must be supplied for every vertex when the graph is constructed. A custom initializer can be
     * supplied if a client does not wish to supply a value for every vertex.
     *
     * It may be more convenient to use the reified extension method which allows the client to elide the class.
     */
    abstract fun withVertexProperty(
        clazz: Class<V>,
        initializer: (Vertex) -> V = { throw IllegalStateException("No vertex initializer supplied") }
    ): ImmutableGraphBuilder<V, E>

    /**
     * Changes the edge type as given, and will create an edge property of the given type when the [ImmutableGraph]
     * is constructed. The default initializer value simply throws [IllegalStateException], which implies that a
     * property value must be supplied for every edge when the graph is constructed. A custom initializer can be
     * supplied if a client does not wish to supply a value for every edge.
     *
     * It may be more convenient to use the reified extension method which allows the client to elide the class.
     */
    abstract fun withEdgeProperty(
        clazz: Class<E>,
        initializer: (Edge) -> E = { throw IllegalStateException("No edge initializer supplied") }
    ): ImmutableGraphBuilder<V, E>

    /**
     * Builds the [ImmutableGraph] with the given action and returns an [ImmutableGraphAndProperties].
     */
    @OptIn(ExperimentalContracts::class)
    inline fun build(builderAction: GraphMutator<V, E>.() -> Unit): ImmutableGraphAndProperties<V, E> {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }

        mutate().builderAction()
        return build()
    }

    /**
     * Returns a [GraphMutator] which can be used to build the topology of the [ImmutableGraph]. Clients should
     * generally prefer to use [build] instead.
     */
    abstract fun mutate(): GraphMutator<V, E>

    /**
     * Builds a new [ImmutableGraph] instance and associated vertex/edge properties, and returns them via an
     * [ImmutableGraphAndProperties] instance. Clients should generally prefer to use [build] instead.
     */
    abstract fun build(): ImmutableGraphAndProperties<V, E>
}

/**
 * A container for holding a newly constructed immutable graph and related vertex and edge properties.
 */
@ConsistentCopyVisibility
data class ImmutableGraphAndProperties<V, E> internal constructor(
    /** The constructed [ImmutableGraph]. */
    val graph: ImmutableGraph,
    /** The constructed [VertexProperty]. */
    val vertexProperty: VertexProperty<V>,
    /** The constructed [EdgeProperty]. */
    val edgeProperty: EdgeProperty<E>,
)

/**
 * See [ImmutableGraphBuilder.withVertexProperty].
 */
inline fun <reified V, E> ImmutableGraphBuilder<V, E>.withVertexProperty(
    noinline initializer: (Vertex) -> V = { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withVertexProperty(V::class.java, initializer)

/**
 * See [ImmutableGraphBuilder.withEdgeProperty].
 */
inline fun <V, reified E> ImmutableGraphBuilder<V, E>.withEdgeProperty(
    noinline initializer: (Edge) -> E = { throw IllegalStateException("No edge initializer supplied") }
): ImmutableGraphBuilder<V, E> = withEdgeProperty(E::class.java, initializer)

/**
 * An instance of this class is returned by [ImmutableGraph.copyOf]. This class holds a reference to the original graph
 * that was copied, the new [ImmutableGraph], and provides methods to translate vertices, edges, and properties between
 * the old and new graph.
 */
/*class CopiedImmutableGraph internal constructor(
    val originalGraph: Graph,
    val graph: ImmutableGraph,
    private val vertexMap: Int2IntMap?,
    private val inverseVertexMap: Int2IntMap?,
    private val edgeMap: Long2LongMap?,
    private val inverseEdgeMap: Long2LongMap?
) {
    /**
     * Converts the given input vertex belonging to the original copied graph into the same vertex in the new
     * [ImmutableGraph]. If the input vertex has been removed from the original graph behavior is undefined.
     */
    fun mapVertex(originalVertex: Vertex): Vertex {
        require(originalGraph.vertices.contains(originalVertex))
        val vertex = if (vertexMap == null) {
            originalVertex
        } else {
            Vertex(vertexMap.get(originalVertex.intValue))
        }
        require(graph.vertices.contains(vertex))
        return vertex
    }

    /**
     * Converts the given input vertex belonging to the new [ImmutableGraph] into the same vertex in the original copied
     * graph. If the equivalent vertex has been removed from the original graph behavior is undefined.
     */
    fun mapVertexInverse(vertex: Vertex): Vertex {
        require(graph.vertices.contains(vertex))
        val originalVertex = if (inverseVertexMap == null) {
            vertex
        } else {
            Vertex(inverseVertexMap.get(vertex.intValue))
        }
        require(originalGraph.vertices.contains(originalVertex))
        return originalVertex
    }

    /**
     * Converts the given input edge belonging to the original copied graph into the same edge in the new
     * [ImmutableGraph]. If the input edge has been removed from the original graph behavior is undefined.
     */
    fun mapEdge(originalEdge: Edge): Edge {
        require(originalGraph.edges.contains(originalEdge))
        val edge = if (edgeMap == null) {
            originalEdge
        } else {
            Edge(edgeMap.get(originalEdge.longValue))
        }
        require(graph.edges.contains(edge))
        return edge
    }

    /**
     * Converts the given input edge belonging to the new [ImmutableGraph] into the same edge in the original copied
     * graph. If the equivalent edge has been removed from the original graph behavior is undefined.
     */
    fun mapEdgeInverse(edge: Edge): Edge {
        require(graph.edges.contains(edge))
        val originalEdge = if (inverseEdgeMap == null) {
            edge
        } else {
            Edge(inverseEdgeMap.get(edge.longValue))
        }
        require(originalGraph.edges.contains(originalEdge))
        return originalEdge
    }
}*/
