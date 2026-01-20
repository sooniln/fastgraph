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

    override fun <T : S?, S> createVertexProperty(
        clazz: Class<S>,
        initializer: VertexInitializer<T>
    ): VertexProperty<T> =
        emptyVertexProperty(this)

    override fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: EdgeInitializer<T>): EdgeProperty<T> =
        emptyEdgeProperty(this)

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
 *   * `supportMultiEdge`: Controls whether the returned mutable graph supports adding multi-edges (multiple
 *   edges that connect the same pair of vertices in the same direction). If a client attempts to add a multi-edge to a
 *   [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be thrown.
 *   * `indexEdges`: If set to true, uses additional memory to assign an index to every edge in order to speed up edge
 *   and edge property access and iteration. While this increases the amount of memory required to store edge topology,
 *   it can reduce the amount of memory needed to store edge properties, and thus in some circumstances may result in
 *   less overall memory usage for the graph topology + data. If set to true, the returned immutable graph is guaranteed
 *   to also implement [IndexedEdgeGraph].
 *
 * Note that the returned builder, while it has vertex and edge types, will not produce vertex or edge properties unless
 * [ImmutableGraphBuilder.withVertexProperty] and [ImmutableGraphBuilder.withEdgeProperty] respectively are invoked.
 */
fun <V, E> immutableGraphBuilder(
    directed: Boolean,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false
): ImmutableGraphBuilder<V, E> {
    return if (supportMultiEdge || indexEdges) {
        ImmutableAdjacencyListNetworkBuilder(directed, supportMultiEdge)
    } else {
        ImmutableAdjacencyListGraphBuilder(directed)
    }
}

/**
 * Convenience method for creating a new mutable [ImmutableGraph]. See [immutableGraph] overloads for more details.
 */
@JvmName("immutableGraphTyped")
@OptIn(ExperimentalContracts::class)
inline fun <V, E> immutableGraph(
    directed: Boolean,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builderAction: GraphMutator<V, E>.() -> Unit = {}
): ImmutableGraph {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    val builder = immutableGraphBuilder<V, E>(directed, supportMultiEdge, indexEdges)
    builder.mutate().builderAction()
    return builder.build()
}

/**
 * Convenience method for creating a new mutable [ImmutableGraph]. See [immutableGraph] overloads for more details.
 */
@OptIn(ExperimentalContracts::class)
inline fun immutableGraph(
    directed: Boolean,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builderAction: GraphMutator<Nothing, Nothing>.() -> Unit = {}
): ImmutableGraph {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    val builder = immutableGraphBuilder<Nothing, Nothing>(directed, supportMultiEdge, indexEdges)
    builder.mutate().builderAction()
    return builder.build()
}

/**
 * Creates a new immutable graph that is a copy of the given graph. See other [immutableGraph] overload for additional
 * documentation on parameters and returned results.
 *
 * If the input graph implements [IndexedVertexGraph], the returned graph guarantees to use the same indices for the
 * same vertex in the topology, and if the input graph implements [IndexedEdgeGraph] and `indexEdges` is true, the
 * returned graph guarantees to use the same indices for the same edges in the topology.
 *
 * If the above holds true AND the input graph was also created via [mutableGraph] or [immutableGraph] then it is also
 * guaranteed that a [Vertex] reference from the input graph also works as a reference to the topologically equivalent
 * vertex in the returned graph (as long as vertex references remain stable in the input graph), and it is guaranteed
 * that an [Edge] reference from the input graph also works as a reference to the topologically equivalent edge in the
 * returned graph (as long as edge references remain stable in the input graph).
 */
@OptIn(ExperimentalContracts::class)
fun immutableGraph(
    graph: Graph,
    supportMultiEdge: Boolean = graph.multiEdge,
    indexEdges: Boolean = graph is IndexedEdgeGraph,
    builderAction: GraphMutator<Nothing, Nothing>.() -> Unit = {},
): GraphCopy<ImmutableGraph> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    if (graph.multiEdge) {
        require(supportMultiEdge) { "copying a graph with multi-edges requires multi-edge support" }
    }

    val vertexMap: Int2IntOpenHashMap?
    val edgeMap: Long2LongOpenHashMap?

    val newGraph = immutableGraph(graph.directed, supportMultiEdge, indexEdges) {
        ensureVertexCapacity(graph.vertices.size)
        ensureEdgeCapacity(graph.edges.size)

        // immutableGraph is guaranteed to always be IndexedVertexGraph
        vertexMap = if (graph is IndexedVertexGraph) {
            null
        } else {
            Int2IntOpenHashMap(graph.vertices.size)
        }
        edgeMap = if (graph is IndexedEdgeGraph && (supportMultiEdge || indexEdges)) {
            null
        } else {
            Long2LongOpenHashMap(graph.edges.size)
        }

        // if graph is IndexedVertexGraph, we know that adding vertices in the same order will ensure that our
        // vertex ids will be the exact same as the graphs, and we can use that assumption to add edges directly
        for (vertex in graph.vertices) {
            val newVertex = addVertex()
            vertexMap?.put(vertex.intValue, newVertex.intValue)
        }

        for (edge in graph.edges) {
            var newSource = graph.edgeSource(edge)
            var newTarget = graph.edgeTarget(edge)
            if (vertexMap != null) {
                newSource = Vertex(vertexMap.get(newSource.intValue))
                newTarget = Vertex(vertexMap.get(newTarget.intValue))
            }
            val newEdge = addEdge(newSource, newTarget)
            edgeMap?.put(edge.longValue, newEdge.longValue)
        }

        builderAction()
    }

    return GraphCopy(graph, newGraph, vertexMap, edgeMap)
}

/**
 * Convenience method for creating a new immutable [PropertyGraph] that is a copy of the given [PropertyGraph]. See
 * [immutableGraph] for more details. The returned [PropertyGraphCopy] contains new vertex and edge properties with the
 * same values as the originals.
 */
@OptIn(ExperimentalContracts::class)
fun <V : VS?, VS, E : ES?, ES> immutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    vertexClass: Class<VS>,
    vertexInitializer: (Vertex) -> V,
    edgeClass: Class<ES>,
    edgeInitializer: (Edge) -> E,
    supportMultiEdge: Boolean = propertyGraph.graph.multiEdge,
    indexEdges: Boolean = propertyGraph.graph is IndexedEdgeGraph,
    builderAction: GraphMutator<V, E>.() -> Unit = {}
): PropertyGraphCopy<ImmutableGraph, V, E> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    val graph = propertyGraph.graph
    if (graph.multiEdge) {
        require(supportMultiEdge) { "copying a graph with multi-edges requires multi-edge support" }
    }

    val builder = immutableGraphBuilder<V, E>(graph.directed, supportMultiEdge, indexEdges)

    val useVertexProperty = !propertyGraph.vertexProperty.isNothingProperty()
    if (useVertexProperty) builder.withVertexProperty(vertexClass, vertexInitializer)

    val useEdgeProperty = !propertyGraph.edgeProperty.isNothingProperty()
    if (useEdgeProperty) builder.withEdgeProperty(edgeClass, edgeInitializer)

    val vertexMap: Int2IntOpenHashMap?
    val edgeMap: Long2LongOpenHashMap?

    val newPropertyGraph = builder.buildPropertyGraph {
        ensureVertexCapacity(graph.vertices.size)
        ensureEdgeCapacity(graph.edges.size)

        // immutableGraph is guaranteed to always be IndexedVertexGraph
        vertexMap = if (graph is IndexedVertexGraph) {
            null
        } else {
            Int2IntOpenHashMap(graph.vertices.size)
        }
        edgeMap = if (graph is IndexedEdgeGraph && (supportMultiEdge || indexEdges)) {
            null
        } else {
            Long2LongOpenHashMap(graph.edges.size)
        }

        for (vertex in graph.vertices) {
            val newVertex = if (useVertexProperty) {
                addVertex(propertyGraph.vertexProperty[vertex])
            } else {
                addVertex()
            }
            vertexMap?.put(vertex.intValue, newVertex.intValue)
        }

        for (edge in graph.edges) {
            var newSource = graph.edgeSource(edge)
            var newTarget = graph.edgeTarget(edge)
            if (vertexMap != null) {
                newSource = Vertex(vertexMap.get(newSource.intValue))
                newTarget = Vertex(vertexMap.get(newTarget.intValue))
            }
            val newEdge = if (useEdgeProperty) {
                addEdge(newSource, newTarget, propertyGraph.edgeProperty[edge])
            } else {
                addEdge(newSource, newTarget)
            }
            edgeMap?.put(edge.longValue, newEdge.longValue)
        }

        builderAction()
    }

    return PropertyGraphCopy(propertyGraph, newPropertyGraph, vertexMap, edgeMap)
}

/**
 * Convenience method for creating a new immutable [PropertyGraph] that is a copy of the given [PropertyGraph]. See
 * [immutableGraph] for more details. The returned [PropertyGraphCopy] contains new vertex and edge properties with the
 * same values as the originals.
 */
@OptIn(ExperimentalContracts::class)
inline fun <reified V, reified E> immutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    noinline vertexInitializer: (Vertex) -> V,
    noinline edgeInitializer: (Edge) -> E,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    noinline builderAction: GraphMutator<V, E>.() -> Unit = {}
): PropertyGraphCopy<ImmutableGraph, V, E> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    return immutablePropertyGraph(
        propertyGraph,
        V::class.java,
        vertexInitializer,
        E::class.java,
        edgeInitializer,
        supportMultiEdge,
        indexEdges,
        builderAction
    )
}

/**
 * Convenience method for creating a new immutable [PropertyGraph] that is a copy of the given [PropertyGraph]. See
 * [immutableGraph] for more details. The returned [PropertyGraphCopy] contains new vertex and edge properties with the
 * same values as the originals.
 */
inline fun <reified V, reified E> immutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
): PropertyGraphCopy<ImmutableGraph, V, E> {
    return immutablePropertyGraph(
        propertyGraph,
        { throw IllegalStateException() },
        { throw IllegalStateException() },
        supportMultiEdge,
        indexEdges
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
        initializer: VertexInitializer<V> = VertexInitializer { throw IllegalStateException("No vertex initializer supplied") }
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
        initializer: EdgeInitializer<E> = EdgeInitializer { throw IllegalStateException("No edge initializer supplied") }
    ): ImmutableGraphBuilder<V, E>

    /**
     * Builds the [ImmutableGraph] with the given action and returns a [PropertyGraph].
     */
    @OptIn(ExperimentalContracts::class)
    inline fun build(builderAction: GraphMutator<V, E>.() -> Unit): ImmutableGraph {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }

        mutate().builderAction()
        return build()
    }

    /**
     * Builds the [ImmutableGraph] with the given action and returns a [PropertyGraph].
     */
    @OptIn(ExperimentalContracts::class)
    inline fun buildPropertyGraph(builderAction: GraphMutator<V, E>.() -> Unit): PropertyGraph<ImmutableGraph, V, E> {
        contract {
            callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
        }

        mutate().builderAction()
        return buildPropertyGraph()
    }

    /**
     * Returns a [GraphMutator] which can be used to build the topology of the [ImmutableGraph]. Clients should
     * generally prefer to use [build] instead.
     */
    abstract fun mutate(): GraphMutator<V, E>

    /**
     * Builds a new [ImmutableGraph] instance.
     */
    abstract fun build(): ImmutableGraph

    /**
     * Builds a new [ImmutableGraph] instance and associated vertex/edge properties, and returns them via a
     * [PropertyGraph] instance.
     */
    abstract fun buildPropertyGraph(): PropertyGraph<ImmutableGraph, V, E>
}

/**
 * See [ImmutableGraphBuilder.withVertexProperty].
 */
inline fun <reified V, E> ImmutableGraphBuilder<V, E>.withVertexProperty(
    initializer: VertexInitializer<V> = VertexInitializer { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withVertexProperty(V::class.java, initializer)

/**
 * See [ImmutableGraphBuilder.withVertexProperty].
 */
@Suppress("UNCHECKED_CAST")
fun <V : S?, S, E> ImmutableGraphBuilder<V, E>.withVertexProperty(
    clazz: Class<S>,
    initializer: VertexInitializer<V> = VertexInitializer { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withVertexProperty(clazz as Class<V>, initializer)

/**
 * See [ImmutableGraphBuilder.withEdgeProperty].
 */
inline fun <V, reified E> ImmutableGraphBuilder<V, E>.withEdgeProperty(
    initializer: EdgeInitializer<E> = EdgeInitializer { throw IllegalStateException("No edge initializer supplied") }
): ImmutableGraphBuilder<V, E> = withEdgeProperty(E::class.java, initializer)


/**
 * See [ImmutableGraphBuilder.withEdgeProperty].
 */
@Suppress("UNCHECKED_CAST")
fun <V, E : S?, S> ImmutableGraphBuilder<V, E>.withEdgeProperty(
    clazz: Class<S>,
    initializer: EdgeInitializer<E> = EdgeInitializer { throw IllegalStateException("No vertex initializer supplied") }
): ImmutableGraphBuilder<V, E> = withEdgeProperty(clazz as Class<E>, initializer)
