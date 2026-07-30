/**
 * Builders and utilities for [Graph].
 */
@file:JvmMultifileClass @file:JvmName("Graphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.AdjacencyListGraph
import io.github.sooniln.fastgraph.internal.AdjacencyListNetwork
import io.github.sooniln.fastgraph.internal.GraphCopy
import io.github.sooniln.fastgraph.internal.PropertyGraphCopy
import io.github.sooniln.fastgraph.internal.TransposedGraph
import io.github.sooniln.fastgraph.internal.VertexInducedImmutableSubgraph
import io.github.sooniln.fastgraph.internal.VertexInducedSubgraph
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * An interface for read-only graph topology. A graph topology is composed of a set of vertices and a set of edges
 * connecting pairs of vertices. This interface represents only the topology of the graph, not any data associated with
 * particular vertices or edges (see [createVertexProperty]/[createEdgeProperty] for associating data with vertices and
 * edges).
 *
 * Implementations of this interface may support:
 *   * directed edges
 *   * undirected edges
 *   * edges that connect a vertex to itself (self-loops)
 *   * multiple edges that connect the same pair of vertices in the same direction (multi-edges)
 *
 * In this library, graphs that support multi-edges are referred to as networks.
 *
 * Not every implementation of this interface is required to support all of these properties - the expectation is that
 * the client can specify which of these properties it requires via a factory method / builder interface and an
 * appropriate implementation is chosen accordingly.
 *
 * [Vertex] and [Edge] objects in graphs do not store what graph they are associated with - clients are required to
 * track what vertices belong to which graph, and to not mix up vertices or edges belonging to different graphs. Graph
 * implementations are expected (but not required) to make a best effort to throw [IllegalArgumentException] if they are
 * supplied a vertex/edge that belongs to a different graph.
 *
 * [Vertex] and [Edge] references are unstable - that is they may be invalidated as the graph changes. For more details
 * on unstable vs stable references, see [VertexReference] and [EdgeReference].
 *
 * To create graphs, see the [mutableGraph] factory method. To create immutable graphs, see the [immutableGraph] factory
 * method.
 *
 * See [MutableGraph] for the mutable version of this interface which allows for modifying the topology.
 */
interface Graph {

    /**
     * Returns true if this graph has directed edges and false this graph has undirected edges.
     */
    val directed: Boolean

    /**
     * Returns true if this graph supports multi-edges (multiple edges connecting the same vertices in the same
     * direction). If false, this graph does not contain any multi-edges, and will throw [IllegalArgumentException] if
     * an attempt is made to add a multi-edge to this graph. If true, the graph may contain multi-edges.
     */
    val multiEdge: Boolean

    /**
     * Returns the set of vertices in this graph. The returned value is a live view that reflects changes to the
     * underlying topology.
     */
    val vertices: VertexSet

    /**
     * Returns true if this graph is empty (no vertices and thus no edges).
     */
    fun isEmpty(): Boolean = vertices.size == 0

    /**
     * Returns the number of outgoing edges from the given vertex. Equivalent to `outgoingEdges(vertex).size()`, but is
     * likely to be cheaper (as [outgoingEdges] may return a new collection on every invocation). In an undirected graph
     * all edges connected to this vertex are considered outgoing. Throws [IllegalArgumentException] if passed a vertex
     * that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    fun outDegree(vertex: Vertex): Int

    /**
     * Returns the number of edges incoming to the given vertex. Equivalent to `incomingEdges(vertex).size()`, but is
     * likely to be cheaper (as [incomingEdges] may return a new collection on every invocation). In an undirected graph
     * all edges connected to this vertex are considered incoming. Throws [IllegalArgumentException] if passed a vertex
     * that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    fun inDegree(vertex: Vertex): Int

    /**
     * Returns the set of vertices that can be reached from the given node by traversing outgoing edges. In an
     * undirected graph all edges connected to this vertex are considered outgoing. The returned value is a live view
     * that reflects changes to the underlying topology. If the vertex the collection is based on is removed from the
     * graph the behavior of the collection is undefined (and may throw exceptions). Throws [IllegalArgumentException]
     * if passed a vertex that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    fun successors(vertex: Vertex): VertexSet

    /**
     * Returns the set of vertices that can be reached from the given node by traversing incoming edges. In an
     * undirected graph all edges connected to this vertex are considered incoming. The returned value is a live view
     * that reflects changes to the underlying topology. If the vertex the collection is based on is removed from the
     * graph the behavior of the collection is undefined (and may throw exceptions). Throws [IllegalArgumentException]
     * if passed a vertex that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    fun predecessors(vertex: Vertex): VertexSet

    /**
     * Returns the set of edges that are outgoing from this vertex. In an undirected graph all edges connected to this
     * vertex are considered outgoing. The returned value is a live view that reflects changes to the underlying
     * topology. If the vertex the collection is based on is removed from the graph the behavior of the collection is
     * undefined (and may throw exceptions). Throws [IllegalArgumentException] if passed a vertex that is not in this
     * graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    fun outgoingEdges(vertex: Vertex): EdgeSet

    /**
     * Returns the set of edges that are incoming to this vertex. In an undirected graph all edges connected to this
     * vertex are considered incoming. The returned value is a live view that reflects changes to the underlying
     * topology. If the vertex the collection is based on is removed from the graph the behavior of the collection is
     * undefined (and may throw exceptions). Throws [IllegalArgumentException] if passed a vertex that is not in this
     * graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    fun incomingEdges(vertex: Vertex): EdgeSet

    /**
     * Returns the set of all edges in this graph. The returned value is a live view that reflects changes to the
     * underlying topology.
     */
    val edges: EdgeSet

    /**
     * Returns source vertex of the given edge. Note that for undirected edges there is no guarantee that the returned
     * vertex is the same as the provided source vertex when the edge was constructed (it may be reversed). It is
     * guaranteed that the vertex returned as the source will be consistent and unchanging over time. With undirected
     * edges it may be more convenient to use [edgeOpposite] sometimes. **There is NO guarantee an exception will be
     * thrown if the given edge does not belong to this graph.**
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    fun edgeSource(edge: Edge): Vertex

    /**
     * Returns target vertex of the given edge. Note that for undirected edges there is no guarantee that the returned
     * vertex is the same as the provided target vertex when the edge was constructed (it may be reversed). It is
     * guaranteed that the vertex returned as the target will be consistent and unchanging over time. With undirected
     * edges it may be more convenient to use [edgeOpposite] sometimes. **There is NO guarantee an exception will be
     * thrown if the given edge does not belong to this graph.**
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    fun edgeTarget(edge: Edge): Vertex

    /**
     * Returns true if the graph contains an edge with the given source and target. Note that for undirected edges
     * either can serve as the source or target - for example it is possible that `containsEdge(a, b) == true` and also
     * `edgeSource(edge) == b && edgeTarget(edge) == a` for an undirected edge. Throws [IllegalArgumentException] if
     * passed a source or target vertex that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    fun hasEdge(source: Vertex, target: Vertex): Boolean

    /**
     * Returns an edge with the given source and target (see undirected edge caveats discussed in [hasEdge]), or
     * throws [NoSuchElementException] if there is no such edge. If there are multiple edges with the given source and
     * target, there are no guarantees on which will be returned. Throws [IllegalArgumentException] if passed a source
     * or target vertex that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    fun edge(source: Vertex, target: Vertex): Edge {
        val edges = edges(source, target)
        if (edges.isEmpty()) throw NoSuchElementException()
        return edges.iterator().next()
    }

    /**
     * Returns the set of edges from the given source to the given target. Will return an empty set if there are no such
     * edges. The returned value is a live view that reflects changes to the underlying topology. If a vertex the
     * collection is based on is removed from the graph the behavior of the collection is undefined (and may throw
     * exceptions).
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    fun edges(source: Vertex, target: Vertex): EdgeSet

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with vertices in this graph, this method returns a new [VertexProperty] instance which can
     * associate some type of data with vertices in this graph. The returned property is guaranteed to remain in sync
     * with the graph, such that vertices added to the graph will appear in the property and vertices removed from the
     * graph will be removed from the property.  If the returned property is associated with a mutable graph, the given
     * initializer may be retained indefinitely (to initialize the property for added edges) - be careful not to leak
     * memory through the initializer.
     *
     * This method accepts two generic parameters (T and S), and it should almost always be the case that T == S. The
     * only reason for the unfortunate complication of two parameters instead of one is to support use cases where the
     * nullability of T and S differ. It should rarely ever be necessary to supply T and S explicitly, the compiler
     * should be able to predict the correct types.
     *
     * The extension method of the same name allows for not passing in the [Class] parameter explicitly - this should be
     * simpler to use where possible.
     */
    fun <T, C : T> createVertexProperty(clazz: Class<C>, initializer: VertexInitializer<T>): MutableVertexProperty<T>

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with edges in this graph, this method returns a new [EdgeProperty] instance which can associate
     * some type of data with edges in this graph. The returned property is guaranteed to remain in sync with the graph,
     * such that edges added to the graph will appear in the property and edges removed from the graph will be removed
     * from the property. If the returned property is associated with a mutable graph, the given initializer may be
     * retained indefinitely (to initialize the property for added edges) - be careful not to leak memory through the
     * initializer.
     *
     * This method accepts two generic parameters (T and S), and it should almost always be the case that T == S. The
     * only reason for the unfortunate complication of two parameters instead of one is to support use cases where the
     * nullability of T and S differ. It should rarely ever be necessary to supply T and S explicitly, the compiler
     * should be able to predict the correct types.
     *
     * The extension method of the same name allows for not passing in the [Class] parameter explicitly - this should be
     * simpler to use where possible.
     */
    fun <T, C : T> createEdgeProperty(clazz: Class<C>, initializer: EdgeInitializer<T>): MutableEdgeProperty<T>

    /**
     * Returns a stable reference to the given vertex. For more information about vertices and stable references to
     * vertices, see [VertexReference].
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    fun createVertexReference(vertex: Vertex): VertexReference

    /**
     * Returns a stable reference to the given edge. For more information about edges and stable references to edges,
     * see [EdgeReference].
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    fun createEdgeReference(edge: Edge): EdgeReference
}

/**
 * See [Graph.outDegree].
 */
fun Graph.outDegree(vertexReference: VertexReference): Int = outDegree(vertexReference.unstable)

/**
 * See [Graph.inDegree].
 */
fun Graph.inDegree(vertexReference: VertexReference): Int = inDegree(vertexReference.unstable)

/**
 * See [Graph.successors].
 */
fun Graph.successors(vertexReference: VertexReference): VertexSet = successors(vertexReference.unstable)

/**
 * See [Graph.predecessors].
 */
fun Graph.predecessors(vertexReference: VertexReference): VertexSet = predecessors(vertexReference.unstable)

/**
 * See [Graph.outgoingEdges].
 */
fun Graph.outgoingEdges(vertexReference: VertexReference): EdgeSet = outgoingEdges(vertexReference.unstable)

/**
 * See [Graph.incomingEdges].
 */
fun Graph.incomingEdges(vertexReference: VertexReference): EdgeSet = incomingEdges(vertexReference.unstable)

/**
 * See [Graph.edgeSource].
 */
fun Graph.edgeSource(edgeReference: EdgeReference): Vertex = edgeSource(edgeReference.unstable)

/**
 * See [Graph.edgeTarget].
 */
fun Graph.edgeTarget(edgeReference: EdgeReference): Vertex = edgeTarget(edgeReference.unstable)

/**
 * Returns the vertex of the given edge that is opposite the given vertex. I.e., the source vertex is returned if the
 * target vertex is provided, and vice versa. Throws [IllegalArgumentException] if the given vertex is neither the
 * source nor target of the given edge. This method is often useful when working with undirected edges where the
 * source/target distinction does not exist.
 */
@JvmName("edgeOpposite")
fun Graph.edgeOpposite(edge: Edge, other: Vertex): Vertex {
    val edgeTarget = edgeTarget(edge)
    if (other == edgeTarget) {
        return edgeSource(edge)
    } else {
        if (other != edgeSource(edge)) {
            throw IllegalArgumentException("vertex $other is not in edge ${edgeSource(edge)} -> $edgeTarget")
        }

        return edgeTarget
    }
}

/**
 * Returns the vertex of the given edge that is opposite the given vertex reference. I.e., the source vertex is returned
 * if the target vertex is provided, and vice versa. Throws [IllegalArgumentException] if the given vertex reference is
 * neither the source nor target of the given edge. This method is often useful when working with undirected edges where
 * the source/target distinction does not exist.
 */
@JvmName("edgeOpposite")
fun Graph.edgeOpposite(edge: Edge, other: VertexReference): Vertex = edgeOpposite(edge, other.unstable)

/**
 * The concrete [VertexProperty] initializer type.
 */
fun interface VertexInitializer<T> {
    fun initialize(vertex: Vertex): T
}

/**
 * The concrete [EdgeProperty] initializer type.
 */
fun interface EdgeInitializer<T> {
    fun initialize(edge: Edge): T
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that creates a [MutableVertexProperty] with every value
 * initialized to null.
 */
inline fun <reified T> Graph.createVertexProperty(): MutableVertexProperty<T?> {
    return createVertexProperty(T::class.java) { null }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that creates a [MutableEdgeProperty] with every value
 * initialized to null.
 */
inline fun <reified T> Graph.createEdgeProperty(): MutableEdgeProperty<T?> {
    return createEdgeProperty(T::class.java) { null }
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [Class].
 */
inline fun <reified T> Graph.createVertexProperty(initializer: VertexInitializer<T>): MutableVertexProperty<T> {
    return createVertexProperty(T::class.java, initializer)
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the [Class].
 */
inline fun <reified T> Graph.createEdgeProperty(initializer: EdgeInitializer<T>): MutableEdgeProperty<T> {
    return createEdgeProperty(T::class.java, initializer)
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [Class].
 */
inline fun <reified T> Graph.createVertexProperty(defaultValue: T): MutableVertexProperty<T> {
    return createVertexProperty(T::class.java) { defaultValue }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the [Class].
 */
inline fun <reified T> Graph.createEdgeProperty(defaultValue: T): MutableEdgeProperty<T> {
    return createEdgeProperty(T::class.java) { defaultValue }
}

/**
 * A graph which guarantees that all vertices in the graph can be associated with an index from `0` to
 * `vertices.size - 1`, and that [Vertex.intValue] is the same as the index. This makes vertices accessible by index,
 * and an index can be retrieved for each vertex. In addition, [vertices] MUST iterate vertices in index order. If
 * applied to a [MutableGraph] this implies that when a vertex is added or removed the graph must re-order vertices in
 * order to ensure the invariants are met.
 */
interface IndexedVertexGraph : Graph {
    override val vertices: IndexedVertexSet
}

/**
 * A graph which guarantees that all edges in the graph can be associated with an index from `0` to `edges.size - 1`,
 * and [Edge.longValue].toInt() is the same as the index. This makes edges accessible by index, and an index can be
 * retrieved for each edge. In addition, [edges] must iterate edges in index order. If applied to a [MutableGraph] this
 * implies that when an edge is added or removed the graph must re-order edges in order to ensure the invariants are
 * met.
 *
 * Note that you CANNOT reconstruct an Edge directly from its index - i.e. `Edge(index.toLong())` will not yield the
 * correct edge - you must use `edges[index]`.
 */
interface IndexedEdgeGraph : Graph {
    override val edges: IndexedEdgeSet
}

/**
 * A specialization of [Graph] which allows mutation of the graph topology via the addition or subtraction of vertices
 * and edges. When mutation is not required, clients should prefer [Graph].
 */
interface MutableGraph : Graph {

    override val vertices: MutableVertexSet

    override val edges: MutableEdgeSet

    /**
     * Adds a new vertex to the graph and returns it.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    fun addVertex(): Vertex

    /**
     * Removes the given vertex from the graph. Any edges with the given vertex as the source or target are also
     * removed.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeVertex")
    fun removeVertex(vertex: Vertex)

    /**
     * Adds a new edge connecting the given source and target vertex. See [Graph.hasEdge] for caveats on how
     * source/target are treated in undirected graphs. In a [MutableGraph] implementation that does not support
     * multi-edges, this method will throw [IllegalArgumentException] if there already exists an edge connecting those
     * vertices in the same direction.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    fun addEdge(source: Vertex, target: Vertex): Edge

    /**
     * Removes the given edge from the graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeEdge")
    fun removeEdge(edge: Edge)

    /**
     * Optionally implemented by graphs to pre-allocate enough memory for the given number of total vertices.
     */
    fun ensureVertexCapacity(vertexCapacity: Int) {}

    /**
     * Optionally implemented by graphs to pre-allocate enough memory for the given number of total edges.
     */
    fun ensureEdgeCapacity(edgeCapacity: Int) {}
}

/**
 * See [MutableGraph.removeVertex].
 */
fun MutableGraph.removeVertex(vertexReference: VertexReference) = removeVertex(vertexReference.unstable)

/**
 * See [MutableGraph.addEdge].
 */
fun MutableGraph.addEdge(source: VertexReference, target: VertexReference): Edge =
    addEdge(source.unstable, target.unstable)

/**
 * See [MutableGraph.removeEdge].
 */
fun MutableGraph.removeEdge(edgeReference: EdgeReference) = removeEdge(edgeReference.unstable)

/**
 * A uni-directional mapping from one set of vertices and edges to another. This mapping represents a relation, but the
 * relation is not required to be bijective, injective, or surjective. Given a vertex/edge from an origin graph, this
 * interface can map it to a corresponding vertex/edge in a destination graph.
 */
interface GraphMapping {
    /**
     * Given an origin [Vertex], returns the corresponding destination [Vertex]. Should make a best effort to throw
     * [IllegalArgumentException] if given an invalid vertex.
     */
    fun getCorrespondingVertex(vertex: Vertex): Vertex

    /**
     * Given an origin [Edge], returns the corresponding destination [Edge]. Makes a best effort to throw
     * [IllegalArgumentException] if given an invalid edge.
     */
    fun getCorrespondingEdge(edge: Edge): Edge
}

/**
 * A convenience interface for bundling a graph topology and an associated vertex and edge property.
 */
interface PropertyGraph<G : Graph, V, E> {
    val graph: G
    val vertexProperty: MutableVertexProperty<V>
    val edgeProperty: MutableEdgeProperty<E>
}

@JvmSynthetic
@JvmName("#PropertyGraph_component1")
operator fun <G : Graph, V, E> PropertyGraph<G, V, E>.component1(): G = graph

@JvmSynthetic
@JvmName("#PropertyGraph_component2")
operator fun <G : Graph, V, E> PropertyGraph<G, V, E>.component2(): MutableVertexProperty<V> = vertexProperty

@JvmSynthetic
@JvmName("#PropertyGraph_component3")
operator fun <G : Graph, V, E> PropertyGraph<G, V, E>.component3(): MutableEdgeProperty<E> = edgeProperty

/**
 * Constructs a [PropertyGraph] with the given arguments.
 */
fun <G : Graph, V, E> PropertyGraph(
    graph: G,
    vertexProperty: MutableVertexProperty<V>,
    edgeProperty: MutableEdgeProperty<E>
): PropertyGraph<G, V, E> {
    require(vertexProperty.graph === graph)
    require(edgeProperty.graph === graph)

    return object : PropertyGraph<G, V, E> {
        override val graph: G = graph
        override val vertexProperty: MutableVertexProperty<V> = vertexProperty
        override val edgeProperty: MutableEdgeProperty<E> = edgeProperty
    }
}

/**
 * A copy of a [Graph], which includes a graph isomorphism via [GraphMapping] so that each vertex/edge of the original
 * graph is mapped to the equivalent vertex/edge of the new graph.
 */
interface GraphCopy<G : Graph> : GraphMapping {
    val originalGraph: Graph
    val graph: G
}

/**
 * A copy of a [PropertyGraph], which includes a graph isomorphism via [GraphMapping] so that each vertex/edge of the
 * copied graph is mapped to the equivalent vertex/edge of the new graph.
 */
interface PropertyGraphCopy<G : Graph, V, E> : PropertyGraph<G, V, E>, GraphCopy<G> {
    val originalPropertyGraph: PropertyGraph<*, V, E>
}

/**
 * Returns a read-only empty graph with the given directedness.
 */
fun emptyGraph(directed: Boolean): Graph = emptyImmutableGraph(directed)

/**
 * Constructs and returns a new empty [MutableGraph] with the given directedness. The returned mutable graph is
 * guaranteed to implement [IndexedVertexGraph].
 *
 * There are several parameters that help control the specific graph implementation chosen:
 *   * `supportMultiEdge`: If set to true, ensures that the returned mutable graph supports adding multi-edges (multiple
 *   edges that connect the same pair of vertices in the same direction). If a client attempts to add a multi-edge to a
 *   [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be thrown.
 *   * `indexEdges`: If set to true, uses additional memory to assign an index to every edge in order to speed up edge
 *   and edge property access and iteration. While this increases the amount of memory required to store edge topology,
 *   it can reduce the amount of memory needed to store edge properties, and thus in some circumstances may result in
 *   less overall memory usage for the graph topology + data. If set to true, the returned mutable graph is guaranteed
 *   to also implement [IndexedEdgeGraph].
 *
 * The implementation returned by this method guarantees that [Vertex] and [Edge] references are stable in the case of
 * additive mutations to the topology (i.e. adding a vertex or edge will not invalidate any [Vertex]/[Edge] references),
 * but may be unstable in the case of subtractive mutations to the topology. Specifically, removing a vertex may
 * invalidate all [Vertex]/[Edge] references, and removing an edge may invalidate all [Edge] references (but is
 * guaranteed not to invalidate any [Vertex] references). If a client requires a reference that remains stable even
 * through subtractive mutations to the topology, use [Graph.createVertexReference] and [Graph.createEdgeReference] to
 * obtain a stable reference.
 */
fun mutableGraph(directed: Boolean, multiEdge: Boolean = false, indexEdges: Boolean = false): MutableGraph {
    return if (multiEdge || indexEdges) {
        AdjacencyListNetwork(directed, multiEdge)
    } else {
        AdjacencyListGraph(directed)
    }
}

/**
 * Convenience method for creating and building a new mutable [PropertyGraph]. See [mutableGraph] overload for more
 * details.
 */
inline fun <V, E> mutableGraph(
    directed: Boolean,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builderAction: GraphMutator<V, E>.() -> Unit = {}
): MutableGraph {
    return buildGraph(mutableGraph(directed, supportMultiEdge, indexEdges), null, null, builderAction)
}

/**
 * Convenience method for creating a new mutable [PropertyGraph]. See [mutableGraph] overloads for more details.
 */
inline fun <reified V, reified E> mutablePropertyGraph(
    directed: Boolean,
    noinline vertexInitializer: (Vertex) -> V,
    noinline edgeInitializer: (Edge) -> E,
    supportMultiEdge: Boolean = false,
    indexEdges: Boolean = false,
): PropertyGraph<MutableGraph, V, E> {
    val graph = mutableGraph(directed, supportMultiEdge, indexEdges)
    return PropertyGraph(
        graph,
        graph.createVertexProperty(vertexInitializer),
        graph.createEdgeProperty(edgeInitializer)
    )
}

/**
 * Creates a new mutable graph that is a copy of the given graph. See other [mutableGraph] overload for additional
 * documentation on parameters and returned results.
 *
 * If the input graph implements [IndexedVertexGraph], the returned graph guarantees to use the same indices for the
 * same vertex in the topology, and if the input graph implements [IndexedEdgeGraph] and `indexEdges` is true, the
 * returned graph guarantees to use the same indices for the same edges in the topology.
 */
fun mutableGraph(
    graph: Graph,
    multiEdge: Boolean = graph.multiEdge,
    indexEdges: Boolean = graph is IndexedEdgeGraph,
): GraphCopy<MutableGraph> {
    if (graph.multiEdge) {
        require(multiEdge) { "copying a graph with multi-edges requires multi-edge support" }
    }

    val newGraph = mutableGraph(graph.directed, multiEdge, indexEdges)

    newGraph.ensureVertexCapacity(graph.vertices.size)
    newGraph.ensureEdgeCapacity(graph.edges.size)

    // newGraph is guaranteed to always be IndexedVertexGraph
    val vertexMap = if (graph is IndexedVertexGraph) {
        null
    } else {
        GraphInt2IntHashMap(graph.vertices.size)
    }

    val edgeMap = if (graph is IndexedEdgeGraph && newGraph is IndexedEdgeGraph) {
        null
    } else {
        GraphLong2LongHashMap(graph.edges.size)
    }

    for (vertex in graph.vertices) {
        val newVertex = newGraph.addVertex()
        vertexMap?.put(vertex.intValue, newVertex.intValue)
    }

    for (edge in graph.edges) {
        var newSource = graph.edgeSource(edge)
        var newTarget = graph.edgeTarget(edge)
        if (vertexMap != null) {
            newSource = Vertex(vertexMap.get(newSource.intValue))
            newTarget = Vertex(vertexMap.get(newTarget.intValue))
        }
        val newEdge = newGraph.addEdge(newSource, newTarget)
        edgeMap?.put(edge.longValue, newEdge.longValue)
    }

    return GraphCopy(graph, newGraph, vertexMap, edgeMap)
}

/**
 * Convenience method for creating a new mutable [PropertyGraph] that is a copy of the given [PropertyGraph]. See
 * [mutableGraph] for more details. The returned [PropertyGraphCopy] contains new vertex and edge properties with the
 * same values as the originals.
 */
fun <V : VS?, VS, E : ES?, ES> mutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    vertexClass: Class<VS>,
    vertexInitializer: (Vertex) -> V,
    edgeClass: Class<ES>,
    edgeInitializer: (Edge) -> E,
    supportMultiEdge: Boolean = propertyGraph.graph.multiEdge,
    indexEdges: Boolean = propertyGraph.graph is IndexedEdgeGraph,
): PropertyGraphCopy<MutableGraph, V, E> {
    val graphCopy = mutableGraph(propertyGraph.graph, supportMultiEdge, indexEdges)
    return PropertyGraphCopy(
        propertyGraph,
        graphCopy,
        graphCopy.copyVertexProperty(propertyGraph.vertexProperty, vertexClass, vertexInitializer),
        graphCopy.copyEdgeProperty(propertyGraph.edgeProperty, edgeClass, edgeInitializer)
    )
}

/**
 * Convenience method for creating a new mutable [PropertyGraph] that is a copy of the given [PropertyGraph]. See
 * [mutableGraph] for more details. The returned [PropertyGraphCopy] contains new vertex and edge properties with the
 * same values as the originals.
 */
inline fun <reified V, reified E> mutablePropertyGraph(
    propertyGraph: PropertyGraph<*, V, E>,
    noinline vertexInitializer: (Vertex) -> V,
    noinline edgeInitializer: (Edge) -> E,
    supportMultiEdge: Boolean = propertyGraph.graph.multiEdge,
    indexEdges: Boolean = propertyGraph.graph is IndexedEdgeGraph,
): PropertyGraphCopy<MutableGraph, V, E> {
    return mutablePropertyGraph(
        propertyGraph,
        V::class.java,
        vertexInitializer,
        E::class.java,
        edgeInitializer,
        supportMultiEdge,
        indexEdges,
    )
}

/**
 * An interface to aid in mutating graphs. This interface may be associated with a [VertexProperty] and/or an
 * [EdgeProperty], and is responsible for initializing the properties as vertices and edges are added. This is generally
 * a convenience tool to make it easier to build graphs and vertex/edge properties at the same time. This interface
 * assumes that vertex property values are unique, and can thus be used as keys in a map. If this assumption is not true
 * then this interface cannot be used.
 */
interface GraphMutator<V, E> {
    /**
     * Adds and returns a new vertex.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    fun addVertex(): Vertex

    /**
     * Adds and returns a new vertex and sets the property value for the new vertex to the given value.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    fun addVertex(value: V): Vertex

    /**
     * Adds and returns a new edge from the given source to the given target.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    fun addEdge(source: Vertex, target: Vertex): Edge

    /**
     * Adds and returns a new edge from the given source to the given target and sets the property value for the new
     * edge to the given value.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    fun addEdge(source: Vertex, target: Vertex, value: E): Edge

    /**
     * Adds and returns a new edge from the vertex with the given source property value to the vertex with the given
     * target property value. If either vertex is not found, it will be created via [addVertex] (and it's value set
     * appropriately) before adding the edge. The given source/target value must have been set through this same
     * [GraphMutator] instance in order to be recognized properly. If vertex property values are not unique behavior is
     * undefined.
     *
     * Note that you can use this method even if there is no vertex property associated with this [GraphMutator].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    fun addEdge(sourceValue: V, targetValue: V): Edge

    /**
     * Adds and returns a new edge from the vertex with the given source property value to the vertex with the given
     * target property value, and sets the property value for the new edge to the given value. If either vertex is not
     * found, it will be created via [addVertex] (and it's value set appropriately) before adding the edge. The given
     * source/target value must have been set through this same [GraphMutator] instance in order to be recognized
     * properly. If vertex property values are not unique behavior is undefined.
     *
     * Note that you can use this method even if there is no vertex property associated with this [GraphMutator].
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    fun addEdge(sourceValue: V, targetValue: V, value: E): Edge

    /**
     * Returns true if there is a vertex with the given property value (that was set through this instance).
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("hasVertex")
    fun hasVertex(value: V): Boolean

    /**
     * Returns the vertex with the given property value (that was set through this instance). Throws
     * [IllegalArgumentException] if no such vertex exists.
     */
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getVertex")
    fun getVertex(value: V): Vertex

    /**
     * Optionally implemented to pre-allocate enough memory for the given number of total vertices.
     */
    fun ensureVertexCapacity(vertexCapacity: Int) {}

    /**
     * Optionally implemented to pre-allocate enough memory for the given number of total edges.
     */
    fun ensureEdgeCapacity(edgeCapacity: Int) {}
}

private fun <V, E> GraphMutator(
    graph: MutableGraph,
    vertexProperty: VertexProperty<V>?,
    edgeProperty: EdgeProperty<E>?
) = object : GraphMutator<V, E> {

    private val vertexMap = Object2IntOpenHashMap<V>()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(): Vertex = graph.addVertex()

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    override fun addVertex(value: V): Vertex {
        val vertex = graph.addVertex()
        vertexProperty?.set(vertex, value)
        vertexMap[value] = vertex.intValue
        return vertex
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex): Edge = graph.addEdge(source, target)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(source: Vertex, target: Vertex, value: E): Edge {
        val edge = graph.addEdge(source, target)
        edgeProperty?.set(edge, value)
        return edge
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(sourceValue: V, targetValue: V): Edge {
        val source = getOrCreateVertex(sourceValue)
        val target = getOrCreateVertex(targetValue)
        return addEdge(source, target)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addEdge")
    override fun addEdge(sourceValue: V, targetValue: V, value: E): Edge {
        val source = getOrCreateVertex(sourceValue)
        val target = getOrCreateVertex(targetValue)
        return addEdge(source, target, value)
    }

    private fun getOrCreateVertex(key: V): Vertex {
        return if (!vertexMap.containsKey(key)) {
            addVertex(key)
        } else {
            Vertex(vertexMap.getInt(key))
        }
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("hasVertex")
    override fun hasVertex(value: V): Boolean {
        return vertexMap.containsKey(value)
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getVertex")
    override fun getVertex(value: V): Vertex {
        if (!vertexMap.containsKey(value)) {
            throw IllegalArgumentException("no vertex with value $value")
        }

        return Vertex(vertexMap.getInt(value))
    }

    override fun ensureVertexCapacity(vertexCapacity: Int) = graph.ensureVertexCapacity(vertexCapacity)

    override fun ensureEdgeCapacity(edgeCapacity: Int) = graph.ensureEdgeCapacity(edgeCapacity)
}

/**
 * Returns a new [GraphMutator] instance that works with the given [Graph], [VertexProperty], and [EdgeProperty].
 * In most cases it is unlikely clients will need to call this directly - it is more convenient to use [buildGraph]
 * which directly accepts a lambda to build the graph.
 */
@JvmOverloads
fun <V, E> mutateGraph(
    graph: MutableGraph,
    vertexProperty: VertexProperty<V>? = null,
    edgeProperty: EdgeProperty<E>? = null
): GraphMutator<V, E> = GraphMutator(graph, vertexProperty, edgeProperty)

/**
 * A helper for building a graph. See documentation on the overload for more information.
 */
@OptIn(ExperimentalContracts::class)
inline fun buildGraph(graph: MutableGraph, builderAction: GraphMutator<Nothing, Nothing>.() -> Unit): MutableGraph {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    mutateGraph<Nothing, Nothing>(graph).builderAction()
    return graph
}

/**
 * A helper for building a graph along with vertex and edge properties.
 *
 * Example usage:
 * ```
 * val graph = mutableGraph(directed = false)
 * val vertexName = graph.createVertexProperty() { "" }
 * val edgeWeight = graph.createEdgeProperty() { 0f }
 * buildGraph(graph, vertexName, edgeWeight) {
 *     addEdge("vertex1", "vertex2", 1.5f)
 *     ...
 * }
 * ```
 *
 * After running this `graph` now has two vertices connected by an undirected edge. The [VertexProperty] `vertexName`
 * has values "vertex1" and "vertex2" for the respective vertices, and the [EdgeProperty] `edgeWeight` has the value
 * 1.5 for the edge.
 *
 * If either of the vertex/edge properties is unnecessary, they can be elided from the method arguments. For example:
 *
 * ```
 * val graph = mutableGraph(directed = false)
 * val edgeWeight = graph.createEdgeProperty() { 0f }
 * buildGraph<String, Nothing>(graph, edgeProperty = edgeWeight) {
 *     val v1 = addVertex()
 *     val v2 = addVertex()
 *     addEdge(v1, v2, 1.5f)
 *     ...
 * }
 * ```
 *
 * Note that you can supply a vertex value type even without supplying a vertex property, for example:
 *
 * ```
 * val graph =  buildGraph<String, Nothing>(mutableGraph(directed = false)) {
 *     addEdge("vertex1", "vertex2")
 *     addEdge("vertex2", "vertex3")
 *     ...
 * }
 * ```
 *
 * This will create a graph with three vertices (and two edges), with no vertex properties. The *vertexN* strings were
 * only used to distinguish vertices during construction, and then abandoned afterward since no [VertexProperty] was
 * supplied.
 */
@OptIn(ExperimentalContracts::class)
inline fun <V, E> buildGraph(
    graph: MutableGraph,
    vertexProperty: VertexProperty<V>? = null,
    edgeProperty: EdgeProperty<E>? = null,
    builderAction: GraphMutator<V, E>.() -> Unit
): MutableGraph {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    mutateGraph(graph, vertexProperty, edgeProperty).builderAction()
    return graph
}

/**
 * A helper for building a [PropertyGraph]. See overload for details.
 */
@OptIn(ExperimentalContracts::class)
inline fun <G : MutableGraph, V, E> buildGraph(
    propertyGraph: PropertyGraph<G, V, E>,
    builderAction: GraphMutator<V, E>.() -> Unit
): PropertyGraph<G, V, E> {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    mutateGraph(propertyGraph.graph, propertyGraph.vertexProperty, propertyGraph.edgeProperty).builderAction()
    return propertyGraph
}

/**
 * Returns a live-view subgraph based on the given vertices. Any edge incident to one of the given vertices is always
 * included in the subgraph. Since this is a live view based on the parent graph, if the parent graph is mutable then
 * some subgraph methods may execute in linear time with respect to the same methods on the parent. If the parent graph
 * is immutable, then all subgraph methods are guaranteed to execute in amortized constant time with respect to the same
 * methods on the parent.
 *
 * Note that creating a property for a subgraph will actually create a property that is backed by a property on the
 * original graph.
 */
fun Graph.subgraph(vertices: VertexSet): Graph {
    return if (this is ImmutableGraph) {
        VertexInducedImmutableSubgraph(this, vertices)
    } else {
        VertexInducedSubgraph(this, vertices)
    }
}

/**
 * Returns an immutable subgraph based a snapshot of the graph with the given filters. The snapshot will only include
 * vertices from the original graph for which the filter returned true, and will only include edges from the original
 * graph where the vertex filter returned true for both the edge source and target AND the edge filter returned true.
 * The returned snapshot will use the same [Vertex] and [Edge] values as the original graph, but creates a copy rather
 * than referencing the original graph at all.
 */
/*@Suppress("LEAKED_IN_PLACE_LAMBDA")
@OptIn(ExperimentalContracts::class)
fun Graph.subgraphSnapshot(
    vertexFilter: (Vertex) -> Boolean,
    edgeFilter: (Edge) -> Boolean = { true }
): ImmutableGraph {
    contract {
        callsInPlace(vertexFilter, InvocationKind.UNKNOWN)
        callsInPlace(edgeFilter, InvocationKind.UNKNOWN)
    }

    return FilteredGraph(this, vertexFilter, edgeFilter)
}*/

/**
 * Returns a live-view of a graph with every edge direction reversed (transposed).
 */
fun Graph.transpose(): Graph {
    return if (!directed || (this is ImmutableGraph && isEmpty())) {
        this
    } else if (this is TransposedGraph) {
        graph
    } else {
        TransposedGraph(this)
    }
}

abstract class AbstractGraph(override val directed: Boolean) : Graph {

    /** Should be implemented to throw [IllegalArgumentException] if `vertex` does not belong to this graph. */
    protected abstract fun validateVertex(vertex: Vertex): Vertex

    /** Should be implemented to throw [IllegalArgumentException] if `edge` does not belong to this graph. */
    protected abstract fun validateEdge(edge: Edge): Edge

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int = getOutDegree(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    protected abstract fun getOutDegree(vertex: Vertex): Int

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int {
        return if (!directed) outDegree(vertex) else getInDegree(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    protected abstract fun getInDegree(vertex: Vertex): Int

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSet = getSuccessors(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    protected abstract fun getSuccessors(vertex: Vertex): VertexSet

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet {
        return if (!directed) successors(vertex) else getPredecessors(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    protected abstract fun getPredecessors(vertex: Vertex): VertexSet

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet = getOutgoingEdges(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    protected abstract fun getOutgoingEdges(vertex: Vertex): EdgeSet

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet {
        return if (!directed) outgoingEdges(vertex) else getIncomingEdges(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    protected abstract fun getIncomingEdges(vertex: Vertex): EdgeSet

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("hasEdge")
    override fun hasEdge(source: Vertex, target: Vertex): Boolean {
        return containsEdge(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    protected abstract fun containsEdge(source: Vertex, target: Vertex): Boolean

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun edge(source: Vertex, target: Vertex): Edge {
        return getEdge(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    protected open fun getEdge(source: Vertex, target: Vertex): Edge {
        return getEdges(source, target).iterator().next()
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun edges(source: Vertex, target: Vertex): EdgeSet {
        return getEdges(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    protected abstract fun getEdges(source: Vertex, target: Vertex): EdgeSet
}
