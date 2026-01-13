/**
 * Builders and utilities for [ImmutableGraph].
 */
@file:JvmMultifileClass @file:JvmName("ImmutableGraphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.GraphCopy
import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListGraphBuilder
import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListNetworkBuilder
import io.github.sooniln.fastgraph.internal.PropertyGraphCopy
import io.github.sooniln.fastgraph.internal.emptyEdgeProperty
import io.github.sooniln.fastgraph.internal.emptyVertexProperty
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
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
sealed interface ImmutableGraph : Graph, IndexedVertexGraph

// exists purely to allow implementations in different packages
internal abstract class AbstractImmutableGraph : ImmutableGraph

/**
 * Returns an immutable empty graph with the given directedness.
 */
fun emptyImmutableGraph(directed: Boolean): ImmutableGraph =
    if (directed) EmptyGraph.DIRECTED else EmptyGraph.UNDIRECTED

private class EmptyGraph(override val directed: Boolean) : ImmutableGraph, IndexedVertexGraph, IndexedEdgeGraph {
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
 * Creates an [ImmutableGraphBuilder] with the given directedness. The returned graph is guaranteed to implement
 * [IndexedVertexGraph].
 *
 * There are several parameters that help control the specific graph implementation chosen:
 *   * `allowMultiEdge`: Controls whether the returned mutable graph supports adding multi-edges (multiple
 *   edges that connect the same pair of vertices in the same direction). If a client attempts to add a multi-edge to a
 *   [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be thrown.
 *   * `indexEdges`: If set to true, uses additional memory to assign an index to every edge in order to speed up edge
 *   and edge property access and iteration. While this increases the amount of memory required to store edge topology,
 *   it can reduce the amount of memory needed to store edge properties, and thus in some circumstances may result in
 *   less overall memory usage for the graph topology + data. If set to true, the returned immutable graph is guaranteed
 *   to also be an [IndexedEdgeGraph].
 *
 * Note that the returned builder, while it has vertex and edge types, will not produce vertex or edge properties unless
 * [ImmutableGraphBuilder.withVertexProperty] and [ImmutableGraphBuilder.withEdgeProperty] respectively are invoked.
 */
@JvmName("immutableGraphTyped")
fun <V, E> immutableGraph(
    directed: Boolean,
    allowMultiEdge: Boolean = false,
    indexEdges: Boolean = false
): ImmutableGraphBuilder<V, E> {
    return if (allowMultiEdge || indexEdges) {
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
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false
): ImmutableGraphBuilder<Nothing, Nothing> {
    return immutableGraph<Nothing, Nothing>(directed, supportMultiEdge, indexEdges)
}

fun <V, E> immutableGraph(
    graph: Graph,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    mapVertices: VertexSet = emptyVertexSet(),
    mapEdges: EdgeSet = emptyEdgeSet(),
    builderAction: GraphMutator<V, E>.() -> Unit = {}
): GraphCopy<ImmutableGraph> {
    val builder = if (graph.multiEdge || supportMultiEdge || indexEdges) {
        ImmutableAdjacencyListNetworkBuilder<V, E>(graph.directed)
    } else {
        ImmutableAdjacencyListGraphBuilder(graph.directed)
    }

    builder.ensureVertexCapacity(graph.vertices.size)
    builder.ensureEdgeCapacity(graph.edges.size)

    val allVertexMap = if (graph is IndexedVertexGraph) null else Int2IntOpenHashMap(graph.vertices.size)
    val vertexMap = if (graph is IndexedVertexGraph) null else Int2IntOpenHashMap(mapVertices.size)
    val edgeMap = if (graph is IndexedEdgeGraph) null else Long2LongOpenHashMap()

    // if graph is IndexedVertexGraph, we know that adding vertices in the same order will ensure that our
    // vertex ids will be the exact same as the graphs, and we can use that assumption to add edges directly
    for (vertex in graph.vertices) {
        val newVertex = builder.addVertex()
        allVertexMap?.put(vertex.intValue, newVertex.intValue)
        if (vertexMap != null && mapVertices.contains(vertex)) {
            vertexMap.put(vertex.intValue, newVertex.intValue)
        }
    }

    for (edge in graph.edges) {
        var newSource = graph.edgeSource(edge)
        var newTarget = graph.edgeTarget(edge)
        if (allVertexMap != null) {
            newSource = Vertex(allVertexMap.get(newSource.intValue))
            newTarget = Vertex(allVertexMap.get(newTarget.intValue))
        }
        val newEdge = builder.addEdge(newSource, newTarget)
        if (edgeMap != null && mapEdges.contains(edge)) {
            edgeMap.put(edge.longValue, newEdge.longValue)
        }
    }

    builder.mutate().builderAction()
    return GraphCopy(builder.build().graph, vertexMap, edgeMap)
}

@JvmSynthetic
@JvmName("#immutableGraph")
fun immutableGraph(
    graph: Graph,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    mapVertices: VertexSet = emptyVertexSet(),
    mapEdges: EdgeSet = emptyEdgeSet(),
    builderAction: GraphMutator<Nothing, Nothing>.() -> Unit = {}
): GraphCopy<ImmutableGraph> {
    return immutableGraph<Nothing, Nothing>(graph, supportMultiEdge, indexEdges, mapVertices, mapEdges, builderAction)
}

fun <V : VS?, VS, E : ES?, ES> immutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    vertexClass: Class<VS>?,
    vertexInitializer: ((Vertex) -> V)?,
    edgeClass: Class<ES>?,
    edgeInitializer: ((Edge) -> E)?,
    mapVertices: VertexSet = emptyVertexSet(),
    mapEdges: EdgeSet = emptyEdgeSet(),
    builderAction: GraphMutator<V, E>.() -> Unit = {}
): PropertyGraphCopy<ImmutableGraph, V, E> {
    val graph = propertyGraph.graph

    val builder = if (graph.multiEdge || supportMultiEdge || indexEdges) {
        ImmutableAdjacencyListNetworkBuilder<V, E>(graph.directed)
    } else {
        ImmutableAdjacencyListGraphBuilder(graph.directed)
    }

    val vertexProperty = if (!propertyGraph.vertexProperty.isNothingProperty()) {
        builder.withVertexProperty(vertexClass!!, vertexInitializer!!)
        propertyGraph.vertexProperty
    } else {
        null
    }

    val edgeProperty = if (!propertyGraph.edgeProperty.isNothingProperty()) {
        builder.withEdgeProperty(edgeClass!!, edgeInitializer!!)
        propertyGraph.edgeProperty
    } else {
        null
    }

    builder.ensureVertexCapacity(graph.vertices.size)
    builder.ensureEdgeCapacity(graph.edges.size)

    val allVertexMap = if (graph is IndexedVertexGraph) null else Int2IntOpenHashMap(graph.vertices.size)
    val vertexMap = if (graph is IndexedVertexGraph) null else Int2IntOpenHashMap(mapVertices.size)
    val edgeMap = if (graph is IndexedEdgeGraph) null else Long2LongOpenHashMap()

    // if graph is IndexedVertexGraph, we know that adding vertices in the same order will ensure that our
    // vertex ids will be the exact same as the graphs, and we can use that assumption to add edges directly
    for (vertex in graph.vertices) {
        val newVertex = if (vertexProperty == null) {
            builder.addVertex()
        } else {
            builder.addVertex(vertexProperty[vertex])
        }
        allVertexMap?.put(vertex.intValue, newVertex.intValue)
        if (vertexMap != null && mapVertices.contains(vertex)) {
            vertexMap.put(vertex.intValue, newVertex.intValue)
        }
    }

    for (edge in graph.edges) {
        var newSource = graph.edgeSource(edge)
        var newTarget = graph.edgeTarget(edge)
        if (allVertexMap != null) {
            newSource = Vertex(allVertexMap.get(newSource.intValue))
            newTarget = Vertex(allVertexMap.get(newTarget.intValue))
        }
        val newEdge = if (edgeProperty == null) {
            builder.addEdge(newSource, newTarget)
        } else {
            builder.addEdge(newSource, newTarget, edgeProperty[edge])
        }
        if (edgeMap != null && mapEdges.contains(edge)) {
            edgeMap.put(edge.longValue, newEdge.longValue)
        }
    }

    builder.mutate().builderAction()
    return PropertyGraphCopy(builder.build(), vertexMap, edgeMap)
}

inline fun <reified V, reified E> immutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    noinline vertexInitializer: (Vertex) -> V,
    noinline edgeInitializer: (Edge) -> E,
    mapVertices: VertexSet = emptyVertexSet(),
    mapEdges: EdgeSet = emptyEdgeSet(),
    noinline builderAction: GraphMutator<V, E>.() -> Unit = {}
): PropertyGraphCopy<ImmutableGraph, V, E> {
    return immutablePropertyGraph(
        propertyGraph,
        supportMultiEdge,
        indexEdges,
        V::class.java,
        vertexInitializer,
        E::class.java,
        edgeInitializer,
        mapVertices,
        mapEdges,
        builderAction
    )
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
     * Builds the [ImmutableGraph] with the given action and returns a [PropertyGraph].
     */
    @OptIn(ExperimentalContracts::class)
    inline fun build(builderAction: GraphMutator<V, E>.() -> Unit): PropertyGraph<ImmutableGraph, V, E> {
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
     * Builds a new [ImmutableGraph] instance and associated vertex/edge properties, and returns them via a
     * [PropertyGraph] instance.
     */
    abstract fun build(): PropertyGraph<ImmutableGraph, V, E>
}

/**
 * See [ImmutableGraphBuilder.withVertexProperty].
 */
inline fun <reified V, E> ImmutableGraphBuilder<V, E>.withVertexProperty(
    noinline initializer: (Vertex) -> V = { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withVertexProperty(V::class.java, initializer)

/**
 * See [ImmutableGraphBuilder.withVertexProperty].
 */
inline fun <V : S?, S, E> ImmutableGraphBuilder<V, E>.withVertexProperty(
    clazz: Class<S>,
    noinline initializer: (Vertex) -> V = { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withVertexProperty(clazz as Class<V>, initializer)

/**
 * See [ImmutableGraphBuilder.withEdgeProperty].
 */
inline fun <V, reified E> ImmutableGraphBuilder<V, E>.withEdgeProperty(
    noinline initializer: (Edge) -> E = { throw IllegalStateException("No edge initializer supplied") }
): ImmutableGraphBuilder<V, E> = withEdgeProperty(E::class.java, initializer)


/**
 * See [ImmutableGraphBuilder.withEdgeProperty].
 */
inline fun <V, E : S?, S> ImmutableGraphBuilder<V, E>.withEdgeProperty(
    clazz: Class<S>,
    noinline initializer: (Edge) -> E = { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withEdgeProperty(clazz as Class<E>, initializer)
