/**
 * Methods dealing with immutable graphs.
 */
@file:JvmName("ImmutableGraphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.AdjacencyListGraph
import io.github.sooniln.fastgraph.internal.AdjacencyListNetwork
import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListGraph
import io.github.sooniln.fastgraph.internal.ImmutableAdjacencyListNetwork
import io.github.sooniln.fastgraph.subgraph.Subgraphs

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
public sealed interface ImmutableGraph : Graph

// what is the point of this Kotlin stupidity - who thought limiting a sealed interface to the same package was a good
// idea when Kotlin doesn't even have package-private?? instead we have to use this moronic workaround.
internal interface InternalImmutableGraph : ImmutableGraph

/**
 * An immutable version of [ValueGraph]. Note that it is only the graph (topology) that is immutable, not the
 * properties.
 */
public class ImmutableValueGraph<V, E>(
    override val graph: ImmutableGraph,
    override val vertexProperty: MutableVertexProperty<V>,
    override val edgeProperty: MutableEdgeProperty<E>
) : ValueGraph<V, E>, ImmutableGraph by graph {
    init {
        require(vertexProperty.graph === graph)
        require(edgeProperty.graph === graph)
    }
}

/**
 * Returns an empty [ImmutableGraph] with the given directedness.
 */
public fun emptyImmutableGraph(directed: Boolean): ImmutableGraph {
    return if (directed) EmptyGraph.DIRECTED else EmptyGraph.UNDIRECTED
}

/**
 * Returns an empty [ImmutableValueGraph] with the given directedness and vertex/edge property types.
 */
@JvmName("emptyImmutableValueGraph")
public fun <V, E> emptyImmutableValueGraph(
    directed: Boolean,
    vertexType: TypeReference<V>,
    edgeType: TypeReference<E>
): ImmutableValueGraph<V, E> {
    val graph = if (directed) EmptyGraph.DIRECTED else EmptyGraph.UNDIRECTED
    return ImmutableValueGraph(
        graph,
        emptyVertexProperty(graph, vertexType),
        emptyEdgeProperty(graph, edgeType)
    )
}

/**
 * Returns an empty [ImmutableValueGraph] with the given directedness.
 */
public inline fun <reified V, reified E> emptyImmutableValueGraph(directed: Boolean): ImmutableValueGraph<V, E> {
    return emptyImmutableValueGraph(directed, TypeReference.of(), TypeReference.of())
}

/**
 * Returns an [ImmutableGraph] which is a copy of the given [Graph]. The returned graph is guaranteed to have
 * identical vertex/edge ids as the input graph.
 */
public fun immutableGraph(graph: Graph): ImmutableGraph {
    if (graph is ImmutableGraph) {
        return graph
    } else if (graph.isEmpty()) {
        return emptyImmutableGraph(graph.directed)
    }

    return when (graph) {
        is AdjacencyListGraph -> ImmutableAdjacencyListGraph.copy(graph)
        is AdjacencyListNetwork -> ImmutableAdjacencyListNetwork.copy(graph)
        else -> {
            if (graph is IndexedVertexGraph && graph is IndexedEdgeGraph) ImmutableAdjacencyListNetwork.copy(graph)
            else throw UnsupportedOperationException("Creating immutable copies of third-party graphs that do not implement both IndexedVertexGraph and IndexedEdgeGraph is currently unsupported")
        }
    }
}

/** See [immutableGraph]. */
@JvmSynthetic
public fun Graph.toImmutableGraph(): ImmutableGraph = immutableGraph(this)

/**
 * Returns an [ImmutableValueGraph] which is a copy of the given [ValueGraph]. The returned graph is guaranteed to
 * have identical vertex/edge ids as the input graph, and the returned vertex/edge properties have the mappings as
 * the input properties.
 */
public fun <V, E> immutableValueGraph(valueGraph: ValueGraph<V, E>): ImmutableValueGraph<V, E> {
    if (valueGraph is ImmutableValueGraph) {
        return valueGraph
    } else if (valueGraph.graph.isEmpty()) {
        return emptyImmutableValueGraph(
            valueGraph.graph.directed,
            valueGraph.vertexProperty.type,
            valueGraph.edgeProperty.type)
    }

    val graph = immutableGraph(valueGraph.graph)
    // the property initializers are safe because (1) ImmutableGraph is a sealed type (2) we know all implementations
    // will never retain a reference to the initializer post-construction (3) we know all copy implementations return
    // identity isomorphisms (all vertex/edge ids are the same)
    return ImmutableValueGraph(
        graph,
        graph.createVertexProperty(valueGraph.vertexProperty.type) { vertex -> valueGraph.vertexProperty[vertex] },
        graph.createEdgeProperty(valueGraph.edgeProperty.type) { edge -> valueGraph.edgeProperty[edge] },)
}

/** See [immutableValueGraph]. */
@JvmSynthetic
public fun <V, E> ValueGraph<V, E>.toImmutableValueGraph(): ImmutableValueGraph<V, E> {
    return immutableValueGraph(this)
}

/**
 * Builds an [ImmutableGraph] with the given options. See [mutableGraph] for more information on options.
 */
public inline fun buildImmutableGraph(
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: GraphBuilder.() -> Unit
): ImmutableGraph {
    return immutableGraph(buildGraph(directed, multiEdge, indexEdges, builder))
}

/**
 * Builds an [ImmutableValueGraph] with the given options. See [mutableGraph] for more information on
 * options.
 */
public inline fun <reified V, reified E> buildImmutableValueGraph(
    directed: Boolean,
    vertexInitializer: VertexFunction<V>,
    edgeInitializer: EdgeFunction<E>,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V, E>.() -> Unit
): ImmutableValueGraph<V, E> {
    return immutableValueGraph(buildValueGraph(directed, vertexInitializer, edgeInitializer, multiEdge, indexEdges, builder))
}

/**
 * Builds an [ImmutableValueGraph] with the given options. See [mutableGraph] for more information on
 * options.
 */
public inline fun <reified V, reified E> buildImmutableValueGraph(
    directed: Boolean,
    vertexDefaultValue: V,
    edgeDefaultValue: E,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V, E>.() -> Unit
): ImmutableValueGraph<V, E> {
    return buildImmutableValueGraph(directed, { vertexDefaultValue }, { edgeDefaultValue }, multiEdge, indexEdges, builder)
}

/**
 * Builds an [ImmutableValueGraph] with the given options. See [mutableGraph] for more information on
 * options.
 */
public inline fun <reified V, reified E> buildImmutableValueGraph(
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V?, E?>.() -> Unit
): ImmutableValueGraph<V?, E?> {
    return buildImmutableValueGraph(directed, { null }, { null }, multiEdge, indexEdges, builder)
}

/**
 * Returns a view of the immutable graph with filtered vertices and edges. See [subgraph] for more
 * information on options.
 */
public fun subgraph(graph: ImmutableGraph, inducingVertices: VertexSet, inducingEdges: EdgeSet): ImmutableGraph {
    return Subgraphs.subgraph(graph, inducingVertices, inducingEdges)
}

/** See [subgraph]. */
@JvmSynthetic
@JvmName("#subgraph")
public fun ImmutableGraph.subgraph(inducingVertices: VertexSet, inducingEdges: EdgeSet): ImmutableGraph {
    return subgraph(this, inducingVertices, inducingEdges)
}

private class EmptyGraph(override val directed: Boolean) : ImmutableGraph, IndexedVertexGraph, IndexedEdgeGraph {
    companion object {
        val DIRECTED = EmptyGraph(true)
        val UNDIRECTED = EmptyGraph(false)
    }

    override val multiEdge: Boolean
        get() = false

    override val vertices: IndexedVertexSet
        get() = emptyVertexSet()

    override fun outDegree(vertex: Vertex): Int = throw IllegalArgumentException()

    override fun inDegree(vertex: Vertex): Int = throw IllegalArgumentException()

    override fun successors(vertex: Vertex): VertexSet = throw IllegalArgumentException()

    override fun predecessors(vertex: Vertex): VertexSet = throw IllegalArgumentException()

    override fun outgoingEdges(vertex: Vertex): EdgeSet = throw IllegalArgumentException()

    override fun incomingEdges(vertex: Vertex): EdgeSet = throw IllegalArgumentException()

    override val edges: IndexedEdgeSet
        get() = emptyEdgeSet()

    override fun edgeSource(edge: Edge): Vertex = throw IllegalArgumentException()

    override fun edgeTarget(edge: Edge): Vertex = throw IllegalArgumentException()

    override fun hasEdge(source: Vertex, target: Vertex): Boolean = throw IllegalArgumentException()

    override fun edge(source: Vertex, target: Vertex): Edge = throw IllegalArgumentException()

    override fun edges(source: Vertex, target: Vertex): EdgeSet = throw IllegalArgumentException()

    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}

    override fun <T> createVertexProperty(
        type: TypeReference<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return emptyVertexProperty(this, type)
    }

    override fun <T> createEdgeProperty(
        type: TypeReference<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return emptyEdgeProperty(this, type)
    }

    override fun createVertexReference(vertex: Vertex): VertexReference = throw IllegalArgumentException()

    override fun createEdgeReference(edge: Edge): EdgeReference = throw IllegalArgumentException()
}
