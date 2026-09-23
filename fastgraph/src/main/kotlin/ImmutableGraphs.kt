/**
 * Methods dealing with immutable graphs.
 */
@file:JvmName("ImmutableGraphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.homomorphisms.EdgeHomomorphism
import io.github.sooniln.fastgraph.homomorphisms.GraphHomomorphism
import io.github.sooniln.fastgraph.homomorphisms.VertexIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.homomorphism
import io.github.sooniln.fastgraph.homomorphisms.isomorphism
import io.github.sooniln.fastgraph.homomorphisms.vertexIdentityIsomorphism
import io.github.sooniln.fastgraph.internal.ImmutableEdgeReference
import io.github.sooniln.fastgraph.internal.ImmutableTransposedGraph
import io.github.sooniln.fastgraph.internal.ImmutableUndirectedGraph
import io.github.sooniln.fastgraph.internal.ImmutableVertexReference
import io.github.sooniln.fastgraph.internal.createAssociatedDiGraph
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.copyInto
import io.github.sooniln.fastgraph.properties.emptyEdgeProperty
import io.github.sooniln.fastgraph.properties.emptyVertexProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.references.VertexReference

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
 * To create immutable graphs, see the [buildImmutableGraph]/[toImmutableGraph]/etc methods.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public sealed interface ImmutableGraph : Graph {
    override fun registerVertexChangeListener(listener: VertexChangeListener) {}
    override fun unregisterVertexChangeListener(listener: VertexChangeListener) {}
    override fun registerEdgeChangeListener(listener: EdgeChangeListener) {}
    override fun unregisterEdgeChangeListener(listener: EdgeChangeListener) {}

    @JvmName("createVertexReference")
    override fun createVertexReference(vertex: Vertex): VertexReference {
        if (!vertices.contains(vertex)) throwIllegalVertex(vertex)
        return ImmutableVertexReference(vertex)
    }
    @JvmName("createEdgeReference")
    override fun createEdgeReference(edge: Edge): EdgeReference {
        if (!edges.contains(edge)) throwIllegalEdge(this, edge)
        return ImmutableEdgeReference(edge)
    }
}

// what is the point of this Kotlin stupidity - who thought limiting a sealed interface to the same package was a good
// idea when Kotlin doesn't even have package-private?? instead we have to use this moronic workaround.
internal interface InternalImmutableGraph : ImmutableGraph

/**
 * An immutable version of [ValueGraph]. Note that it is only the graph (topology) that is immutable, not the
 * properties.
 */
public class ImmutableValueGraph<V, E>(
    override val graph: ImmutableGraph,
    override val vertexKeys: MutableVertexKeyProperty<V>,
    override val edgeValues: MutableEdgeProperty<E>
) : ValueGraph<V, E> {
    init {
        require(vertexKeys.graph === graph)
        require(edgeValues.graph === graph)
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
    vertexType: PropertyType<V>,
    edgeType: PropertyType<E>
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
    return emptyImmutableValueGraph(directed, propertyTypeOf(), propertyTypeOf())
}

public fun <V, E> ValueGraph<V, E>.toImmutableValueGraph(): ImmutableValueGraph<V, E> {
    if (this is ImmutableValueGraph) {
        return this
    } else if (graph.isEmpty()) {
        return emptyImmutableValueGraph(
            graph.directed,
            vertexKeys.type,
            edgeValues.type)
    }

    val graph = graph.toImmutableGraph()
    // the property initializer and key copy are safe because (1) ImmutableGraph is a sealed type (2) we know all
    // implementations will never retain a reference to the initializer post-construction (3) we know all copy
    // implementations return identity isomorphisms (all vertex/edge ids are the same)
    return ImmutableValueGraph(
        graph,
        graph.createVertexKeyProperty(vertexKeys.type).also { vertexKeys.copyInto(it) },
        graph.createEdgeProperty(edgeValues.type) { edge -> edgeValues[edge] })
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
    return buildGraph(directed, multiEdge, indexEdges, builder).toImmutableGraph()
}

/**
 * Builds an [ImmutableValueGraph] with the given options. See [mutableGraph] for more information on
 * options.
 */
public inline fun <reified V, reified E> buildImmutableValueGraph(
    directed: Boolean,
    edgeInitializer: EdgeFunction<E>,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V, E>.() -> Unit
): ImmutableValueGraph<V, E> {
    return buildValueGraph(directed, edgeInitializer, multiEdge, indexEdges, builder).toImmutableValueGraph()
}

/**
 * Builds an [ImmutableValueGraph] with the given options. See [mutableGraph] for more information on
 * options.
 */
public inline fun <reified V, reified E> buildImmutableValueGraph(
    directed: Boolean,
    edgeDefaultValue: E,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V, E>.() -> Unit
): ImmutableValueGraph<V, E> {
    return buildImmutableValueGraph(directed, { edgeDefaultValue }, multiEdge, indexEdges, builder)
}

/**
 * Builds an [ImmutableValueGraph] with the given options. See [mutableGraph] for more information on
 * options.
 */
public inline fun <reified V, reified E> buildImmutableValueGraph(
    directed: Boolean,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V, E?>.() -> Unit
): ImmutableValueGraph<V, E?> {
    return buildImmutableValueGraph(directed, { null }, multiEdge, indexEdges, builder)
}

/**
 * Returns a live view of the given immutable graph with every edge direction reversed (transposed). The returned
 * immutable graph is guaranteed to use the same edge ids for transposed edges vs the original edges.
 */
public fun ImmutableGraph.asTransposed(): ImmutableGraph = if (isEmpty()) this else ImmutableTransposedGraph(this)

/**
 * Returns a live view of the given immutable graph with every edge treated as undirected. The returned immutable graph
 * is guaranteed to use the same vertex and edge ids as the original graph. Since edges in opposite directions between
 * the same vertices become parallel undirected edges, the returned graph always supports multi-edges.
 */
public fun ImmutableGraph.asUndirected(): ImmutableGraph {
    return if (!directed) {
        this
    } else if (isEmpty()) {
        emptyImmutableGraph(false)
    } else {
        ImmutableUndirectedGraph(this)
    }
}


/**
 * Returns a live view of the given immutable graph as a directed graph ([GraphHomomorphism.source] is the live view,
 * [GraphHomomorphism.target] is the original).
 */
public fun ImmutableGraph.asDirected(): GraphHomomorphism<ImmutableGraph, ImmutableGraph, VertexIsomorphism<ImmutableGraph, ImmutableGraph>, EdgeHomomorphism<ImmutableGraph, ImmutableGraph>> {
    if (directed) return isomorphism(this)

    val edgeHomomorphism = createAssociatedDiGraph(this)
    return homomorphism(vertexIdentityIsomorphism(edgeHomomorphism.source, this), edgeHomomorphism)
}

private class EmptyGraph(override val directed: Boolean) : ImmutableGraph {
    companion object {
        val DIRECTED = EmptyGraph(true)
        val UNDIRECTED = EmptyGraph(false)
    }

    override val multiEdge: Boolean
        get() = false

    override val vertices: IdentityIndexedVertexSet
        get() = emptyVertexSet()

    override fun outDegree(vertex: Vertex): Int = throw IllegalArgumentException()

    override fun inDegree(vertex: Vertex): Int = throw IllegalArgumentException()

    override fun successors(vertex: Vertex): VertexSet = throw IllegalArgumentException()

    override fun predecessors(vertex: Vertex): VertexSet = throw IllegalArgumentException()

    override fun outgoingEdges(vertex: Vertex): EdgeSet = throw IllegalArgumentException()

    override fun incomingEdges(vertex: Vertex): EdgeSet = throw IllegalArgumentException()

    override val edges: IdentityIndexedEdgeSet
        get() = emptyEdgeSet()

    override fun edgeSource(edge: Edge): Vertex = throw IllegalArgumentException()
    override fun edgeTarget(edge: Edge): Vertex = throw IllegalArgumentException()

    override fun hasEdge(source: Vertex, target: Vertex): Boolean = throw IllegalArgumentException()

    override fun edge(source: Vertex, target: Vertex): Edge = throw IllegalArgumentException()

    override fun edges(source: Vertex, target: Vertex): EdgeSet = throw IllegalArgumentException()

    override fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> {
        return emptyVertexProperty(this, type)
    }

    override fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> {
        return emptyEdgeProperty(this, type)
    }

    override fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return emptyVertexProperty(this, type)
    }

    override fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        return emptyEdgeProperty(this, type)
    }

    override fun createVertexReference(vertex: Vertex): VertexReference = throw IllegalArgumentException()
    override fun createEdgeReference(edge: Edge): EdgeReference = throw IllegalArgumentException()
}
