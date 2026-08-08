package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.internal.AdjacencyListGraph
import io.github.sooniln.fastgraph.internal.AdjacencyListNetwork
import io.github.sooniln.fastgraph.internal.TransposedGraph
import io.github.sooniln.fastgraph.subgraph.Subgraphs

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
 * To create graphs, see the [Graphs] factory methods. To create immutable graphs, see the [ImmutableGraphs] factory
 * methods.
 *
 * See [MutableGraph] for the mutable version of this interface which allows for modifying the topology.
 */
public interface Graph {

    /**
     * Returns true if this graph has directed edges and false this graph has undirected edges.
     */
    public val directed: Boolean

    /**
     * Returns true if this graph supports multi-edges (multiple edges connecting the same vertices in the same
     * direction). If false, this graph does not contain any multi-edges, and will throw [IllegalArgumentException] if
     * an attempt is made to add a multi-edge to this graph. If true, the graph may contain multi-edges.
     */
    public val multiEdge: Boolean

    /**
     * Returns the set of vertices in this graph. The returned value is a live view that reflects changes to the
     * underlying topology.
     */
    public val vertices: VertexSet

    /**
     * Returns true if this graph is empty (no vertices and thus no edges).
     */
    public fun isEmpty(): Boolean = vertices.size == 0

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
    public fun outDegree(vertex: Vertex): Int

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
    public fun inDegree(vertex: Vertex): Int

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
    public fun successors(vertex: Vertex): VertexSet

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
    public fun predecessors(vertex: Vertex): VertexSet

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
    public fun outgoingEdges(vertex: Vertex): EdgeSet

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
    public fun incomingEdges(vertex: Vertex): EdgeSet

    /**
     * Returns the set of all edges in this graph. The returned value is a live view that reflects changes to the
     * underlying topology.
     */
    public val edges: EdgeSet

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
    public fun edgeSource(edge: Edge): Vertex

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
    public fun edgeTarget(edge: Edge): Vertex

    /**
     * Returns true if the graph contains an edge with the given source and target. Note that for undirected edges
     * either can serve as the source or target - for example it is possible that `hasEdge(a, b) == true` and also
     * `edgeSource(edge) == b && edgeTarget(edge) == a` for an undirected edge. Throws [IllegalArgumentException] if
     * passed a source or target vertex that is not in this graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("hasEdge")
    public fun hasEdge(source: Vertex, target: Vertex): Boolean

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
    public fun edge(source: Vertex, target: Vertex): Edge {
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
    public fun edges(source: Vertex, target: Vertex): EdgeSet

    /**
     * Adds a [VertexChangeListener] which is invoked when changes are made to the vertex topology of this graph (when
     * vertices are added/reassigned/updated). Graphs MUST weakly reference listeners, allowing them to be garbage
     * collected and removed when no longer strongly referenced anywhere else. [VertexChangeListener] must only be
     * allowed to observe the graph in a consistent state. Throws [IllegalArgumentException] if the listener is already
     * registered.
     */
    public fun registerVertexChangeListener(listener: VertexChangeListener)

    /** Unregisters the listener if it is currently registered. */
    public fun unregisterVertexChangeListener(listener: VertexChangeListener)

    /**
     * Adds an [EdgeChangeListener] which is invoked when changes are made to the edge topology of this graph (when
     * edges are added/reassigned/updated). Graphs MUST weakly reference listeners, allowing them to be garbage
     * collected when no longer strongly referenced anywhere else. [EdgeChangeListener]s must only be allowed to observe
     * the graph in a consistent state. Throws [IllegalArgumentException] if the listener is already registered.
     */
    public fun registerEdgeChangeListener(listener: EdgeChangeListener)

    /** Unregisters the listener if it is currently registered. */
    public fun unregisterEdgeChangeListener(listener: EdgeChangeListener)

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with vertices in this graph, this method returns a new [VertexProperty] instance which can
     * associate some type of data with vertices in this graph. The returned property is guaranteed to remain in sync
     * with the graph, such that vertices added to the graph will appear in the property and vertices removed from the
     * graph will be removed from the property.  If the returned property is associated with a mutable graph, the given
     * initializer may be retained indefinitely (to initialize the property for added edges) - be careful not to leak
     * memory through the initializer.
     *
     * The extension method of the same name allows for not passing in the [Class] parameter explicitly - this should be
     * simpler to use where possible.
     */
    public fun <T> createVertexProperty(type: Class<T>, initializer: VertexInitializer<T>): MutableVertexProperty<T>

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with edges in this graph, this method returns a new [EdgeProperty] instance which can associate
     * some type of data with edges in this graph. The returned property is guaranteed to remain in sync with the graph,
     * such that edges added to the graph will appear in the property and edges removed from the graph will be removed
     * from the property. If the returned property is associated with a mutable graph, the given initializer may be
     * retained indefinitely (to initialize the property for added edges) - be careful not to leak memory through the
     * initializer.
     *
     * The extension method of the same name allows for not passing in the [Class] parameter explicitly - this should be
     * simpler to use where possible.
     */
    public fun <T> createEdgeProperty(type: Class<T>, initializer: EdgeInitializer<T>): MutableEdgeProperty<T>

    /**
     * Returns a stable reference to the given vertex. For more information about vertices and stable references to
     * vertices, see [VertexReference].
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createVertexReference")
    public fun createVertexReference(vertex: Vertex): VertexReference

    /**
     * Returns a stable reference to the given edge. For more information about edges and stable references to edges,
     * see [EdgeReference].
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("createEdgeReference")
    public fun createEdgeReference(edge: Edge): EdgeReference

    /**
     * Optionally implemented to release unnecessary or unused memory from this graph.
     */
    public fun trimToSize() {}
}

/**
 * See [Graph.outDegree].
 */
public fun Graph.outDegree(vertexReference: VertexReference): Int = outDegree(vertexReference.unstable)

/**
 * See [Graph.inDegree].
 */
public fun Graph.inDegree(vertexReference: VertexReference): Int = inDegree(vertexReference.unstable)

/**
 * See [Graph.successors].
 */
public fun Graph.successors(vertexReference: VertexReference): VertexSet = successors(vertexReference.unstable)

/**
 * See [Graph.predecessors].
 */
public fun Graph.predecessors(vertexReference: VertexReference): VertexSet = predecessors(vertexReference.unstable)

/**
 * See [Graph.outgoingEdges].
 */
public fun Graph.outgoingEdges(vertexReference: VertexReference): EdgeSet = outgoingEdges(vertexReference.unstable)

/**
 * See [Graph.incomingEdges].
 */
public fun Graph.incomingEdges(vertexReference: VertexReference): EdgeSet = incomingEdges(vertexReference.unstable)

/**
 * See [Graph.edgeSource].
 */
public fun Graph.edgeSource(edgeReference: EdgeReference): Vertex = edgeSource(edgeReference.unstable)

/**
 * See [Graph.edgeTarget].
 */
public fun Graph.edgeTarget(edgeReference: EdgeReference): Vertex = edgeTarget(edgeReference.unstable)

/**
 * Returns the vertex of the given edge that is opposite the given vertex. I.e., the source vertex is returned if the
 * target vertex is provided, and vice versa. Throws [IllegalArgumentException] if the given vertex is neither the
 * source nor target of the given edge. This method is often useful when working with undirected edges where the
 * source/target distinction does not exist.
 */
@JvmName("edgeOpposite")
public fun Graph.edgeOpposite(edge: Edge, other: Vertex): Vertex {
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
public fun Graph.edgeOpposite(edge: Edge, other: VertexReference): Vertex = edgeOpposite(edge, other.unstable)

public interface VertexChangeListener {
    /**
     * Invoked after the vertex is added to the graph. The graph is in a consistent state with the vertex present.
     */
    public fun onVertexAdded(vertex: Vertex)

    /**
     * Invoked after the vertex is removed from the graph. The graph is in a consistent state with the vertex not
     * present.
     */
    // TODO: should this be invoked before the vertex is removed?
    public fun onVertexRemoved(vertex: Vertex)

    /**
     * Invoked after a vertex ID is re-assigned. The caller guarantees that [oldVertex] != [newVertex]. This indicates
     * that [oldVertex] is having its ID re-assigned to that of [newVertex]. The effect of this method should be the
     * same as if: (1) all references to `newVertex.id` are removed (if any exist) - the same as if
     * `onVertexRemoved(newVertex.id)` was invoked (2) all references to `oldVertex.id` are updated to `newVertex.id`
     * (3) no references to `oldVertex.id` should be present anywhere after completion of this method.
     *
     * When invoked, the graph is in a consistent state with [oldVertex] not present and [newVertex] present.
     */
    public fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex)

    /**
     * Invoked when the graph is ensuring that capacity should exist for at least [vertexCapacity] vertices.
     */
    public fun ensureVertexCapacity(vertexCapacity: Int) {}

    /**
     * Invoked when the graph is trimming all memory requirements to the minimum possible.
     */
    public fun trimToSize() {}
}

public interface EdgeChangeListener {
    /**
     * Invoked after the edge is added to the graph. The graph is in a consistent state with the edge present.
     */
    public fun onEdgeAdded(edge: Edge)

    /**
     * Invoked after the edge is removed from the graph. The graph is in a consistent state with the edge not present.
     */
    public fun onEdgeRemoved(edge: Edge)

    /**
     * Invoked before an edge ID is re-assigned. The caller guarantees that [oldEdge] != [newEdge]. This indicates that
     * [oldEdge] is having its ID re-assigned to that of [newEdge]. The effect of this method should be the same as if:
     * (1) all references to `newEdge.id` are removed (if any exist) - the same as if `onEdgeRemoved(newEdge.id)` was
     * invoked (2) all references to `oldEdge.id` are updated to `newEdge.id` (3) no references to `oldEdge.id` should
     * be present anywhere after completion of this method.
     *
     * When invoked, the graph is in a consistent state with [oldEdge] present ([newEdge] may or may not be present).
     */
    public fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge)

    /**
     * Invoked when the graph is ensuring that capacity should exist for at least [edgeCapacity] edges.
     */
    public fun ensureEdgeCapacity(edgeCapacity: Int) {}

    /**
     * Invoked when the graph is trimming all memory requirements to the minimum possible.
     */
    public fun trimToSize() {}
}

/**
 * The concrete [VertexProperty] initializer type.
 */
public fun interface VertexInitializer<T> {
    public fun initialize(vertex: Vertex): T
}

/**
 * The concrete [EdgeProperty] initializer type.
 */
public fun interface EdgeInitializer<T> {
    public fun initialize(edge: Edge): T
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that creates a [MutableVertexProperty] with every value
 * initialized to null.
 */
public inline fun <reified T> Graph.createVertexProperty(): MutableVertexProperty<T?> {
    // thanks to kotlin's decision to not have reasonable generic type information, this idiocy results. this also means
    // we're forced to stick with java's class type (instead of KType) and thus can't support multi-platform
    @Suppress("UNCHECKED_CAST")
    return createVertexProperty(T::class.java as Class<T?>) { null }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that creates a [MutableEdgeProperty] with every value
 * initialized to null.
 */
public inline fun <reified T> Graph.createEdgeProperty(): MutableEdgeProperty<T?> {
    // thanks to kotlin's decision to not have reasonable generic type information, this idiocy results. this also means
    // we're forced to stick with java's class type (instead of KType) and thus can't support multi-platform
    @Suppress("UNCHECKED_CAST")
    return createEdgeProperty(T::class.java as Class<T?>) { null }
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [Class].
 */
public inline fun <reified T> Graph.createVertexProperty(initializer: VertexInitializer<T>): MutableVertexProperty<T> {
    return createVertexProperty(T::class.java, initializer)
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the [Class].
 */
public inline fun <reified T> Graph.createEdgeProperty(initializer: EdgeInitializer<T>): MutableEdgeProperty<T> {
    return createEdgeProperty(T::class.java, initializer)
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [Class].
 */
public inline fun <reified T> Graph.createVertexProperty(defaultValue: T): MutableVertexProperty<T> {
    return createVertexProperty(T::class.java) { defaultValue }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the [Class].
 */
public inline fun <reified T> Graph.createEdgeProperty(defaultValue: T): MutableEdgeProperty<T> {
    return createEdgeProperty(T::class.java) { defaultValue }
}

/**
 * A graph which guarantees that all vertices in the graph can be associated with an index from `0` to
 * `vertices.size - 1`, and that [Vertex.id] is the same as the index. This makes vertices accessible by index,
 * and an index can be retrieved for each vertex. In addition, [vertices] MUST iterate vertices in index order. If
 * applied to a [MutableGraph] this implies that when a vertex is added or removed the graph must re-order vertices in
 * order to ensure the invariants are met.
 */
public interface IndexedVertexGraph : Graph {
    override val vertices: IndexedVertexSet
}

/**
 * A graph which guarantees that all edges in the graph can be associated with an index from `0` to `edges.size - 1`,
 * and [Edge.id].toInt() is the same as the index. This makes edges accessible by index, and an index can be
 * retrieved for each edge. In addition, [edges] must iterate edges in index order. If applied to a [MutableGraph] this
 * implies that when an edge is added or removed the graph must re-order edges in order to ensure the invariants are
 * met.
 *
 * Note that you CANNOT reconstruct an Edge directly from its index - i.e. `Edge(index.toLong())` will not yield the
 * correct edge - you must use `edges[index]`.
 */
public interface IndexedEdgeGraph : Graph {
    override val edges: IndexedEdgeSet
}

/**
 * An interface for building graphs. While there are some similarities to [MutableGraph], this builder only allows for
 * additive operations.
 */
public interface GraphBuilder {

    /**
     * Adds a new vertex to the graph and returns it.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("addVertex")
    public fun addVertex(): Vertex

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
    public fun addEdge(source: Vertex, target: Vertex): Edge

    /**
     * Optionally implemented to pre-allocate enough memory for the given number of total vertices.
     */
    public fun ensureVertexCapacity(vertexCapacity: Int) {}

    /**
     * Optionally implemented to pre-allocate enough memory for the given number of total edges.
     */
    public fun ensureEdgeCapacity(edgeCapacity: Int) {}
}

/**
 * A specialization of [Graph] which allows mutation of the graph topology via the addition or subtraction of vertices
 * and edges. When mutation is not required, clients should prefer [Graph].
 */
public interface MutableGraph : Graph, GraphBuilder {

    override val vertices: MutableVertexSet

    override val edges: MutableEdgeSet

    /**
     * Removes the given vertex from the graph. Any edges with the given vertex as the source or target are also
     * removed.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeVertex")
    public fun removeVertex(vertex: Vertex)

    /**
     * Removes the given edge from the graph.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("removeEdge")
    public fun removeEdge(edge: Edge)
}

/**
 * See [MutableGraph.removeVertex].
 */
public fun MutableGraph.removeVertex(vertexReference: VertexReference): Unit = removeVertex(vertexReference.unstable)

/**
 * See [MutableGraph.addEdge].
 */
public fun MutableGraph.addEdge(source: VertexReference, target: VertexReference): Edge =
    addEdge(source.unstable, target.unstable)

/**
 * See [MutableGraph.removeEdge].
 */
public fun MutableGraph.removeEdge(edgeReference: EdgeReference): Unit = removeEdge(edgeReference.unstable)

/**
 * A convenience interface for bundling a graph topology and an associated vertex and edge property. The [ValueGraph]
 * itself implements [Graph] as a convenience - invoking graph methods on the [ValueGraph] is the same as invoking them
 * on [graph].
 *
 * When creating and using [ValueGraph], note that if you only require a vertex property or an edge property, but not
 * both, you can set the type of the unused property to [Unit], which ensures it will take up no additional resources.
 */
public interface ValueGraph<V, E> : Graph {
    /** The topology. */
    public val graph: Graph
    /** The single vertex property associated with the topology. */
    public val vertexProperty: MutableVertexProperty<V>
    /** The single edge property associated with the topology. */
    public val edgeProperty: MutableEdgeProperty<E>
}

/**
 * A specialization of [ValueGraph] which contains (and implements) a [MutableGraph].
 */
public interface MutableValueGraph<V, E> : MutableGraph, ValueGraph<V, E> {
    override val graph: MutableGraph
}

/**
 * An interface for building ValueGraphs. This interface allows for associating vertex/edge values with vertices/edges
 * at construction time. In addition, it allows referring to vertices/edges by their unique values during construction
 * as a convenience.
 */
public interface ValueGraphBuilder<V, E> : GraphBuilder {
    /** Adds a new vertex with the given [value] set in [ValueGraph.vertexProperty] and returns it. */
    public fun addVertex(value: V): Vertex

    /**
     * Adds a new edge connecting the given source and target vertex with the given [value] set in
     * [ValueGraph.edgeProperty] and returns it.
     */
    public fun addEdge(source: Vertex, target: Vertex, value: E): Edge

    /**
     * Adds a new edge between the two vertices with the given unique values and returns it. Throws
     * [NoSuchElementException] if a vertex is not found with the given value. If the values given are not unique (there
     * are multiple vertices with that value) then behavior is undefined - it is your responsibility to avoid this.
     */
    public fun addEdge(sourceValue: V, targetValue:V): Edge = addEdge(getVertex(sourceValue), getVertex(targetValue))

    /**
     * Adds a new edge between the two vertices with the given unique values with the given [value] set in
     * [ValueGraph.edgeProperty] and returns it. Throws [NoSuchElementException] if a vertex is not found with the given
     * value. If the values given are not unique (there are multiple vertices with that value) then behavior is
     * undefined - it is your responsibility to avoid this.
     */
    public fun addEdge(sourceValue: V, targetValue:V, value: E): Edge = addEdge(getVertex(sourceValue), getVertex(targetValue), value)

    /**
     * Returns true if there is a vertex with the given value associated with it in [ValueGraph.vertexProperty].
     */
    public fun hasVertex(value: V): Boolean

    /**
     * Returns the vertex associated with the given value, or throws [NoSuchElementException] if there is none.
     */
    public fun getVertex(value: V): Vertex
}

@PublishedApi
internal fun <V, E> ValueGraphBuilder(graph: MutableValueGraph<V, E>): ValueGraphBuilder<V, E> {
    return object : ValueGraphBuilder<V, E> {
        private val vertexValueMap = HashMap<V, Vertex>()

        override fun addVertex(): Vertex = graph.addVertex()
        override fun addVertex(value: V): Vertex {
            val vertex = graph.addVertex()
            graph.vertexProperty[vertex] = value
            vertexValueMap[value] = vertex
            return vertex
        }

        override fun addEdge(source: Vertex, target: Vertex): Edge = graph.addEdge(source, target)
        override fun addEdge(source: Vertex, target: Vertex, value: E): Edge {
            val edge = graph.addEdge(source, target)
            graph.edgeProperty[edge] = value
            return edge
        }

        override fun hasVertex(value: V): Boolean = vertexValueMap.containsKey(value)
        override fun getVertex(value: V): Vertex =
            requireNotNull(vertexValueMap[value]) { "no vertex with value \"$value\" found" }
    }
}

/**
 * Methods dealing with graphs.
 */
public object Graphs {
    /**
     * Returns a read-only empty [Graph] with the given directedness.
     */
    public fun emptyGraph(directed: Boolean): Graph = ImmutableGraphs.emptyImmutableGraph(directed)

    /**
     * Returns a read-only empty [ValueGraph] with the given directedness.
     */
    public inline fun <reified V, reified E> emptyValueGraph(directed: Boolean): ValueGraph<V, E> =
        ImmutableGraphs.emptyImmutableValueGraph(directed)

    /**
     * Builds a [MutableGraph] with the given options. See [mutableGraph] for more information on options.
     */
    public inline fun buildGraph(
        directed: Boolean,
        multiEdge: Boolean = false,
        indexEdges: Boolean = false,
        builder: GraphBuilder.() -> Unit
    ): MutableGraph {
        return mutableGraph(directed, multiEdge, indexEdges).apply { builder() }
    }

    /**
     * Builds a [MutableValueGraph] with the given options. See [mutableGraph] for more information on options.
     */
    public inline fun <reified V, reified E> buildValueGraph(
        directed: Boolean,
        vertexInitializer: VertexInitializer<V>,
        edgeInitializer: EdgeInitializer<E>,
        multiEdge: Boolean = false,
        indexEdges: Boolean = false,
        builder: ValueGraphBuilder<V, E>.() -> Unit
    ): MutableValueGraph<V, E> {
        val graph = mutableGraph(directed, multiEdge, indexEdges)
        val valueGraph = mutableValueGraph(
            graph,
            graph.createVertexProperty(V::class.java, vertexInitializer),
            graph.createEdgeProperty(E::class.java, edgeInitializer)
        )
        ValueGraphBuilder(valueGraph).builder()
        return valueGraph
    }

    /**
     * Builds a [MutableValueGraph] with the given options. See [mutableGraph] for more information on options.
     */
    public inline fun <reified V, reified E> buildValueGraph(
        directed: Boolean,
        vertexDefaultValue: V,
        edgeDefaultValue: E,
        multiEdge: Boolean = false,
        indexEdges: Boolean = false,
        builder: ValueGraphBuilder<V, E>.() -> Unit
    ): MutableValueGraph<V, E> {
        return buildValueGraph(directed, { vertexDefaultValue }, { edgeDefaultValue }, multiEdge, indexEdges, builder)
    }

    /**
     * Constructs and returns a new empty [MutableGraph] with the given directedness. The returned mutable graph is
     * guaranteed to implement [IndexedVertexGraph].
     *
     * There are several parameters that help control the specific graph implementation chosen:
     *   * `supportMultiEdge`: If set to true, ensures that the returned mutable graph supports adding multi-edges
     *   (multiple edges that connect the same pair of vertices in the same direction). If a client attempts to add a
     *   multi-edge to a [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be
     *   thrown.
     *   * `indexEdges`: If set to true, uses additional memory to assign an index to every edge in order to speed up
     *   edge and edge property access and iteration. While this increases the amount of memory required to store edge
     *   topology, it can reduce the amount of memory needed to store edge properties, and thus in some circumstances
     *   may actually result in less overall memory usage. If set to true, the returned mutable graph is guaranteed to
     *   also implement [IndexedEdgeGraph].
     *
     * The implementation returned by this method guarantees that [Vertex] and [Edge] references are stable in the case
     * of additive mutations to the topology (i.e. adding a vertex or edge will not invalidate any existing
     * [Vertex]/[Edge] references), but may be unstable in the case of subtractive mutations to the topology.
     * Specifically, removing a vertex may invalidate all [Vertex]/[Edge] references, and removing an edge may
     * invalidate all [Edge] references (but is guaranteed not to invalidate any [Vertex] references). If a client
     * requires a reference that remains stable even through subtractive mutations to the topology, use
     * [Graph.createVertexReference] and [Graph.createEdgeReference] to obtain a stable reference.
     */
    public fun mutableGraph(directed: Boolean, multiEdge: Boolean = false, indexEdges: Boolean = false): MutableGraph {
        return if (multiEdge || indexEdges) {
            AdjacencyListNetwork(directed, multiEdge)
        } else {
            AdjacencyListGraph(directed)
        }
    }

    /**
     * Creates a [ValueGraph] from the given graph, vertex property, and edge property.
     */
    public fun <V, E> valueGraph(graph: Graph, vertexProperty: MutableVertexProperty<V>, edgeProperty: MutableEdgeProperty<E>): ValueGraph<V, E> {
        require(vertexProperty.graph === graph)
        require(edgeProperty.graph === graph)
        return object : ValueGraph<V, E>, Graph by graph {
            override val graph: Graph get() = graph
            override val vertexProperty: MutableVertexProperty<V> get() = vertexProperty
            override val edgeProperty: MutableEdgeProperty<E> get() = edgeProperty
        }
    }

    /**
     * Creates a [MutableValueGraph] from the given mutable graph, vertex property, and edge property.
     */
    public fun <V, E> mutableValueGraph(graph: MutableGraph, vertexProperty: MutableVertexProperty<V>, edgeProperty: MutableEdgeProperty<E>): MutableValueGraph<V, E> {
        require(vertexProperty.graph === graph)
        require(edgeProperty.graph === graph)
        return object : MutableValueGraph<V, E>, MutableGraph by graph {
            override val graph: MutableGraph get() = graph
            override val vertexProperty: MutableVertexProperty<V> get() = vertexProperty
            override val edgeProperty: MutableEdgeProperty<E> get() = edgeProperty
        }
    }

    /**
     * Returns a view of the given graph with every edge direction reversed (transposed). The returned graph is
     * guaranteed to use the same edge ids for transposed edges vs the original edges.
     */
    public fun transpose(graph: Graph): Graph {
        return if (!graph.directed || (graph is ImmutableGraph && graph.isEmpty())) {
            graph
        } else {
            graph as? TransposedGraph ?: TransposedGraph(graph)
        }
    }

    /**
     * Returns a view of the graph with filtered vertices and edges. There are two methods of filtering, with
     * differing trade-offs:
     *
     * If you provide inducing vertices/edges (an explicit set of vertices/edges that defines the subgraph), this
     * results in:
     *   1. Better subgraph performance (operations generally run in linear time w.r.t the inducing vertices/edges, and
     *   some values can be cached internally so that constant recalculation is not necessary).
     *   2. Less flexibility in defining the sub-graph (the subgraph cannot adapt as vertices/edges change in some
     *   fashion).
     *   3. Vertex/edge listeners function normally on the subgraph.
     *   4. Vertex/edge references function normally on the subgraph.
     *   5. If an inducing vertex/edge is removed from the parent graph it is also removed from the subgraph.
     *   6. If the parent graph is immutable the returned subgraph is also immutable.
     *
     * If you filter vertices/edges (by providing an arbitrary filtering predicate which will be invoked any time a
     * graph operation is invoked), this results in:
     *   1. Worse subgraph performance (calculating sizes and iteration generally run in linear time w.r.t all the
     *   parent graph's vertices/edges, and values cannot be cached internally - recalculation is always necessary).
     *   2. More flexibility in defining the sub-graph (the filtering predicates can take into account any arbitrary
     *   information needed).
     *   3. Since the filter may depend on arbitrary information that can change at any time it is impossible to
     *   guarantee a consistent view of which vertices/edges are in the subgraph. This further implies:
     *     a. Vertex/edge listeners are unsupported on the subgraph and will throw [UnsupportedOperationException].
     *     b. Vertex/edge references are unsupported on the subgraph and will throw [UnsupportedOperationException].
     *     c. The returned subgraph is not immutable regardless of whether the parent is immutable.
     */
    public fun subgraph(graph: Graph, inducingVertices: VertexSet, inducingEdges: EdgeSet): Graph {
        return Subgraphs.subgraph(graph, inducingVertices, inducingEdges)
    }

    /**
     * Returns a view of the graph with filtered vertices and edges. There are two methods of filtering, with
     * differing trade-offs:
     *
     * If you provide inducing vertices/edges (an explicit set of vertices/edges that defines the subgraph), this
     * results in:
     *   1. Better subgraph performance (operations generally run in linear time w.r.t the inducing vertices/edges, and
     *   some values can be cached internally so that constant recalculation is not necessary).
     *   2. Less flexibility in defining the sub-graph (the subgraph cannot adapt as vertices/edges change in some
     *   fashion).
     *   3. Vertex/edge listeners function normally on the subgraph.
     *   4. Vertex/edge references function normally on the subgraph.
     *   5. If an inducing vertex/edge is removed from the parent graph it is also removed from the subgraph.
     *   6. If the parent graph is immutable the returned subgraph is also immutable.
     *
     * If you filter vertices/edges (by providing an arbitrary filtering predicate which will be invoked any time a
     * graph operation is invoked), this results in:
     *   1. Worse subgraph performance (calculating sizes and iteration generally run in linear time w.r.t all the
     *   parent graph's vertices/edges, and values cannot be cached internally - recalculation is always necessary).
     *   2. More flexibility in defining the sub-graph (the filtering predicates can take into account any arbitrary
     *   information needed).
     *   3. Since the filter may depend on arbitrary information that can change at any time it is impossible to
     *   guarantee a consistent view of which vertices/edges are in the subgraph. This further implies:
     *     a. Vertex/edge listeners are unsupported on the subgraph and will throw [UnsupportedOperationException].
     *     b. Vertex/edge references are unsupported on the subgraph and will throw [UnsupportedOperationException].
     *     c. The returned subgraph is not immutable regardless of whether the parent is immutable.
     */
    public fun subgraph(graph: Graph, inducingVertices: VertexSet, edgeFilter: EdgePredicate = { true }): Graph {
        return Subgraphs.subgraph(graph, inducingVertices, edgeFilter)
    }

    /**
     * Returns a view of the graph with filtered vertices and edges. There are two methods of filtering, with
     * differing trade-offs:
     *
     * If you provide inducing vertices/edges (an explicit set of vertices/edges that defines the subgraph), this
     * results in:
     *   1. Better subgraph performance (operations generally run in linear time w.r.t the inducing vertices/edges, and
     *   some values can be cached internally so that constant recalculation is not necessary).
     *   2. Less flexibility in defining the sub-graph (the subgraph cannot adapt as vertices/edges change in some
     *   fashion).
     *   3. Vertex/edge listeners function normally on the subgraph.
     *   4. Vertex/edge references function normally on the subgraph.
     *   5. If an inducing vertex/edge is removed from the parent graph it is also removed from the subgraph.
     *   6. If the parent graph is immutable the returned subgraph is also immutable.
     *
     * If you filter vertices/edges (by providing an arbitrary filtering predicate which will be invoked any time a
     * graph operation is invoked), this results in:
     *   1. Worse subgraph performance (calculating sizes and iteration generally run in linear time w.r.t all the
     *   parent graph's vertices/edges, and values cannot be cached internally - recalculation is always necessary).
     *   2. More flexibility in defining the sub-graph (the filtering predicates can take into account any arbitrary
     *   information needed).
     *   3. Since the filter may depend on arbitrary information that can change at any time it is impossible to
     *   guarantee a consistent view of which vertices/edges are in the subgraph. This further implies:
     *     a. Vertex/edge listeners are unsupported on the subgraph and will throw [UnsupportedOperationException].
     *     b. Vertex/edge references are unsupported on the subgraph and will throw [UnsupportedOperationException].
     *     c. The returned subgraph is not immutable regardless of whether the parent is immutable.
     */
    public fun subgraph(graph: Graph, vertexFilter: VertexPredicate, edgeFilter: EdgePredicate = { true }): Graph {
        return Subgraphs.subgraph(graph, vertexFilter, edgeFilter)
    }
}

/** See [Graphs.transpose]. */
@JvmSynthetic
public fun Graph.transpose(): Graph = Graphs.transpose(this)

/** See [Graphs.subgraph]. */
@JvmSynthetic
public fun Graph.subgraph(vertices: VertexSet, edges: EdgeSet): Graph = Graphs.subgraph(this, vertices, edges)

/** See [Graphs.subgraph]. */
@JvmSynthetic
public fun Graph.subgraph(vertices: VertexSet, edgeFilter: EdgePredicate): Graph = Graphs.subgraph(this, vertices, edgeFilter)

/** See [Graphs.subgraph]. */
@JvmSynthetic
public fun Graph.subgraph(vertexFilter: VertexPredicate, edgeFilter: EdgePredicate): Graph = Graphs.subgraph(this, vertexFilter, edgeFilter)

/** A base class that provides some basic functionality to implement [Graph]. */
public abstract class AbstractGraph : Graph {

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
    @JvmName("edge")
    override fun edge(source: Vertex, target: Vertex): Edge {
        return getEdge(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    protected open fun getEdge(source: Vertex, target: Vertex): Edge {
        return getEdges(source, target).iterator().next()
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("edges")
    override fun edges(source: Vertex, target: Vertex): EdgeSet {
        return getEdges(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    protected abstract fun getEdges(source: Vertex, target: Vertex): EdgeSet
}
