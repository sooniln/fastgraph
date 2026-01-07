@file:JvmMultifileClass @file:JvmName("Graphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.AdjacencyListGraph
import io.github.sooniln.fastgraph.internal.AdjacencyListNetwork
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
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
     * Returns the set of vertices in this graph. The returned value is a live view that reflects changes to the
     * underlying topology.
     */
    val vertices: VertexSet

    /**
     * Returns true if this graph is empty (no vertices and thus no edges).
     */
    fun isEmpty(): Boolean = vertices.isEmpty()

    /**
     * Returns the number of outgoing edges from the given vertex. Equivalent to `successors(vertex).size()`, but is
     * likely to be cheaper (as [successors] may return a new collection on every invocation). In an undirected graph
     * all edges connected to this vertex are considered outgoing.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    fun outDegree(vertex: Vertex): Int

    /**
     * Returns the number of edges incoming to the given vertex. Equivalent to `predecessors(vertex).size()`, but is
     * likely to be cheaper (as [predecessors] may return a new collection on every invocation). In an undirected graph
     * all edges connected to this vertex are considered incoming.
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
     * graph the behavior of the collection is undefined (and may throw exceptions).
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
     * graph the behavior of the collection is undefined (and may throw exceptions).
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
     * undefined (and may throw exceptions).
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
     * undefined (and may throw exceptions).
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
     * edges it may be more convenient to use [edgeOpposite] sometimes.
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
     * edges it may be more convenient to use [edgeOpposite] sometimes.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    fun edgeTarget(edge: Edge): Vertex

    /**
     * Returns true if the graph contains an edge with the given source and target. Note that for undirected edges
     * either can serve as the source or target - for example it is possible that `containsEdge(a, b) == true` and also
     * `edgeSource(edge) == b && edgeTarget(edge) == a` for an undirected edge.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    fun containsEdge(source: Vertex, target: Vertex): Boolean

    /**
     * Returns an edge with the given source and target (see undirected edge caveats discussed in [containsEdge]), or
     * throws [NoSuchElementException] if there is no such edge. If there are multiple edges with the given source and
     * target, there are no guarantees on which will be returned.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    fun getEdge(source: Vertex, target: Vertex): Edge {
        val edges = getEdges(source, target)
        if (edges.isEmpty()) throw NoSuchElementException()
        return edges.iterator().next()
    }

    /**
     * Returns the set of edges from the given source to the given target. Will return an empty set if there are no such
     * edges.The returned value is a live view that reflects changes to the underlying topology. If a vertex the
     * collection is based on is removed from the graph the behavior of the collection is undefined (and may throw
     * exceptions).
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    fun getEdges(source: Vertex, target: Vertex): EdgeSet

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with vertices in this graph, this method returns a new [VertexProperty] instance which can
     * associate some type of data with vertices in this graph. The returned property is guaranteed to remain in sync
     * with the graph, such that vertices added to the graph will appear in the property and vertices removed from the
     * graph will be removed from the property. Properties also assume that a value is associated with every vertex in
     * the graph and allocate memory accordingly - if sparse storage of data for only a subset of vertices in the graph
     * is required, a [VertexProperty] may not be a good fit.
     *
     * The given initializer may be stored by the vertex property to initialize values for future vertices. Since the
     * initializer may be retained for the lifetime of the vertex property, be careful not to leak memory through it.
     *
     * This method accepts two generic parameters (T and S), and it should almost always be the case that T == S. The
     * only reason for the unfortunate complication of two parameters instead of one is to support use cases where the
     * nullability of T and S differ. It should rarely ever be necessary to supply T and S explicitly, the compiler
     * should be able to predict the correct types.
     *
     * The extension method of the same name allows for not passing in the [Class] parameter explicitly - this should be
     * simpler to use where possible.
     */
    fun <T : S?, S> createVertexProperty(clazz: Class<S>, initializer: (Vertex) -> T): VertexProperty<T>

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with edges in this graph, this method returns a new [EdgeProperty] instance which can associate
     * some type of data with edges in this graph. The returned property is guaranteed to remain in sync with the graph,
     * such that edges added to the graph will appear in the property and edges removed from the graph will be removed
     * from the property. Properties also assume that a value is associated with every edge in the graph and allocate
     * memory accordingly - if sparse storage of data for only a subset of edges in the graph is required, an
     * [EdgeProperty] may not be a good fit.
     *
     * The given initializer may be stored by the edge property to initialize values for future edges. Since the
     * initializer may be retained for the lifetime of the edge property, be careful not to leak memory through it.
     *
     * This method accepts two generic parameters (T and S), and it should almost always be the case that T == S. The
     * only reason for the unfortunate complication of two parameters instead of one is to support use cases where the
     * nullability of T and S differ. It should rarely ever be necessary to supply T and S explicitly, the compiler
     * should be able to predict the correct types.
     *
     * The extension method of the same name allows for not passing in the [Class] parameter explicitly - this should be
     * simpler to use where possible.
     */
    fun <T : S?, S> createEdgeProperty(clazz: Class<S>, initializer: (Edge) -> T): EdgeProperty<T>

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
 * Returns the vertex of the given edge that is opposite the given vertex. Ie, the source vertex is returned if the
 * target vertex is provided, and vice versa. Throws [IllegalArgumentException] if the given vertex is neither the
 * source nor target of the given edge. This method is often useful when working with undirected edges where the
 * source/target distinction does not exist.
 */
@JvmName("edgeOpposite")
fun Graph.edgeOpposite(edge: Edge, other: Vertex): Vertex {
    val edgeSource = edgeSource(edge)
    val edgeTarget = edgeTarget(edge)

    return when (other) {
        edgeTarget -> edgeSource
        edgeSource -> edgeTarget
        else -> throw IllegalArgumentException("vertex $other is not in edge $edgeSource -> $edgeTarget")
    }
}

@JvmName("edgeOpposite")
fun Graph.edgeOpposite(edge: Edge, other: VertexReference): Vertex = edgeOpposite(edge, other.unstable)

/**
 * A convenient extension method for [Graph.createVertexProperty] that creates a [VertexProperty] with every value
 * initialized to null.
 */
inline fun <reified T> Graph.createVertexProperty(): VertexProperty<T?> {
    return createVertexProperty(T::class.java) { null }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that creates a [EdgeProperty] with every value
 * initialized to null.
 */
inline fun <reified T> Graph.createEdgeProperty(): EdgeProperty<T?> {
    return createEdgeProperty(T::class.java) { null }
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [Class].
 */
inline fun <reified T> Graph.createVertexProperty(crossinline initializer: (Vertex) -> T): VertexProperty<T> {
    return createVertexProperty(T::class.java) { initializer(it) }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the [Class].
 */
inline fun <reified T> Graph.createEdgeProperty(crossinline initializer: (Edge) -> T): EdgeProperty<T> {
    return createEdgeProperty(T::class.java) { initializer(it) }
}

/**
 * A graph which guarantees that all vertices in the graph can be associated with an index from `0` to
 * `vertices.size() - 1`. This makes vertices accessible by index, and an index can be retrieved for each vertice (via
 * `vertices.indexOf(vertex)`). The `vertices.indexOf()` call is guaranteed to take constant time.
 */
interface IndexedVertexGraph : Graph {
    override val vertices: VertexSetList
}


/**
 * A graph which guarantees that all edges in the graph can be associated with an index from `0` to `edges.size() - 1`.
 * This makes edges accessible by index, and an index can be retrieved for each edge (via `edges.indexOf(edge)`).
 * The `edges.indexOf()` call is guaranteed to take constant time.
 */
interface IndexedEdgeGraph : Graph {
    override val edges: EdgeSetList
}

/**
 * A specialization of [Graph] which allows mutation of the graph topology via the addition or subtraction of vertices
 * and edges. When mutation is not required, clients should prefer [Graph].
 */
interface MutableGraph : Graph {

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
     * Adds a new edge connecting the given source and target vertex. See [Graph.containsEdge] for caveats on how
     * source/target are treated in undirected graphs. In the [Graph] implementation does not support multi-edges, it
     * may throw [IllegalArgumentException] if there already exists an edge connecting those vertices in the same order.
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
 * A store of property values for vertices. Conceptually this functions a map - mapping vertices to values. Every vertex
 * property is associated with a particular graph, and should make a best effort (but is not required) to throw
 * [IllegalArgumentException] when supplied with a vertex belonging to a different graph. A vertex property stores a
 * value for every vertex in the graph. Vertex properties are required to remain in sync with their respective graphs.
 */
interface VertexProperty<V> {
    /**
     * Retrieves the value associated with the given vertex.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    operator fun get(vertex: Vertex): V

    /**
     * Sets the value associated with the given vertex.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    operator fun set(vertex: Vertex, value: V)
}

/**
 * See [VertexProperty.get].
 */
operator fun <V> VertexProperty<V>.get(vertexReference: VertexReference): V = get(vertexReference.unstable)

/**
 * See [VertexProperty.set].
 */
operator fun <V> VertexProperty<V>.set(vertexReference: VertexReference, value: V) =
    set(vertexReference.unstable, value)

/**
 * A store of property values for edges. Conceptually this functions a map - mapping edges to values. Every edge
 * property is associated with a particular graph, and should make a best effort (but is not required) to throw
 * [IllegalArgumentException] when supplied with an edge belonging to a different graph. An edge property stores a
 * value for every edge in the graph. Edge properties are required to remain in sync with their respective graphs.
 */
interface EdgeProperty<E> {
    /**
     * Retrieves the value associated with the given edge.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("get")
    operator fun get(edge: Edge): E

    /**
     * Sets the value associated with the given edge.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("set")
    operator fun set(edge: Edge, value: E)
}

/**
 * See [EdgeProperty.get].
 */
operator fun <E> EdgeProperty<E>.get(edgeReference: EdgeReference): E = get(edgeReference.unstable)

/**
 * See [EdgeProperty.set].
 */
operator fun <E> EdgeProperty<E>.set(edgeReference: EdgeReference, value: E) = set(edgeReference.unstable, value)


/**
 * Returns a read-only empty graph with the given directedness.
 */
fun emptyGraph(directed: Boolean): Graph = emptyImmutableGraph(directed)

/**
 * Constructs and returns a new empty mutable graph with the given directedness.
 *
 * There are several parameters that help control the specific graph implementation chosen:
 *   * `multiEdge`: Controls whether the returned mutable graph supports adding multi-edges (multiple
 *   edges that connect the same pair of vertices in the same direction). If a client attempts to add a multi-edge to a
 *   [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be thrown.
 *   * `optimizeEdges`: If set to true, uses additional memory to speed up edge and edge property access and iteration.
 *   While this increases the amount of memory required to store edge topology, it can reduce the amount of memory
 *   needed to store edge properties, and thus in some circumstances may result in less overall memory usage for the
 *   graph topology + data.
 *
 * The implementation returned by this method guarantees that [Vertex] and [Edge] references are stable in the case of
 * additive mutations to the topology (i.e. adding a vertex or edge will not invalidate any [Vertex]/[Edge] references),
 * but may be unstable in the case of subtractive mutations to the topology. Specifically, removing a vertex may
 * invalidate all [Vertex]/[Edge] references, and removing an edge may invalidate all [Edge] references (but is
 * guaranteed not to invalidate any [Vertex] references). If a client requires a reference that remains stable even
 * through subtractive mutations to the topology, [Graph.createVertexReference] and [Graph.createEdgeReference] may be
 * used to obtain a stable reference.
 */
fun mutableGraph(directed: Boolean, multiEdge: Boolean = false, optimizeEdges: Boolean = false): MutableGraph {
    return if (multiEdge || optimizeEdges) {
        AdjacencyListNetwork(directed)
    } else {
        AdjacencyListGraph(directed)
    }
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

private class GraphMutatorImpl<V, E>(
    private val graph: MutableGraph,
    private val vertexProperty: VertexProperty<V>? = null,
    private val edgeProperty: EdgeProperty<E>? = null
) : GraphMutator<V, E> {

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
fun <V, E> mutateGraph(
    graph: MutableGraph,
    vertexProperty: VertexProperty<V>? = null,
    edgeProperty: EdgeProperty<E>? = null
): GraphMutator<V, E> {
    return GraphMutatorImpl(graph, vertexProperty, edgeProperty)
}

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
 * A helper for build a new along with vertex and edge properties.
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
 * Returns a read-only view of a graph with every edge direction reversed (transposed).
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

private class TransposedGraph(val graph: Graph) : Graph by graph {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int = graph.inDegree(vertex)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int = graph.outDegree(vertex)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSet = graph.predecessors(vertex)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet = graph.successors(vertex)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): EdgeSet = graph.incomingEdges(vertex)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): EdgeSet = graph.outgoingEdges(vertex)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeSource")
    override fun edgeSource(edge: Edge): Vertex = graph.edgeTarget(edge)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edgeTarget")
    override fun edgeTarget(edge: Edge): Vertex = graph.edgeSource(edge)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("containsEdge")
    override fun containsEdge(source: Vertex, target: Vertex): Boolean = graph.containsEdge(target, source)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdge")
    override fun getEdge(source: Vertex, target: Vertex): Edge = graph.getEdge(target, source)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("getEdges")
    override fun getEdges(source: Vertex, target: Vertex): EdgeSet = graph.getEdges(target, source)
}
