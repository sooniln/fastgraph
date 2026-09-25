/**
 * Methods dealing with graphs.
 */
@file:JvmName("Graphs")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.homomorphisms.EdgeHomomorphism
import io.github.sooniln.fastgraph.homomorphisms.EdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.GraphHomomorphism
import io.github.sooniln.fastgraph.homomorphisms.GraphIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.VertexIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.homomorphism
import io.github.sooniln.fastgraph.homomorphisms.selfIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.transferInto
import io.github.sooniln.fastgraph.homomorphisms.selfVertexIsomorphism
import io.github.sooniln.fastgraph.internal.AbstractTransposedGraph
import io.github.sooniln.fastgraph.internal.AdjacencyListGraph
import io.github.sooniln.fastgraph.internal.AdjacencyListNetwork
import io.github.sooniln.fastgraph.internal.TransposedGraph
import io.github.sooniln.fastgraph.internal.UndirectedGraph
import io.github.sooniln.fastgraph.internal.copyToMutableGraph
import io.github.sooniln.fastgraph.internal.createAssociatedDiGraph
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.VertexKeyProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import io.github.sooniln.fastgraph.references.EdgeReference
import io.github.sooniln.fastgraph.references.VertexReference

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
 * [Vertex] and [Edge] objects in graphs do not store what graph they are associated with - clients are required to
 * track what vertices belong to which graph, and to not mix up vertices or edges belonging to different graphs. Graph
 * implementations are expected (but not required) to make a best effort to throw [IllegalArgumentException] if they are
 * supplied a vertex/edge that belongs to a different graph. Clients must expect some implementations to forgo these
 * checks for performance.
 *
 * [Vertex] and [Edge] references are unstable - that is they may be invalidated as the graph changes. For more details
 * on unstable vs stable references, see [io.github.sooniln.fastgraph.references.VertexReference] and [io.github.sooniln.fastgraph.references.EdgeReference].
 *
 * To create graphs, see the [mutableGraph]/[buildGraph]/etc factory methods. See also [ImmutableGraph] for more
 * information on immutable graphs.
 *
 * See [MutableGraph] for the mutable version of this interface which allows for modifying the topology.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
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
     * Returns the out-degree of the given vertex. In a directed graph this is equivalent to
     * `outgoingEdges(vertex).size()`. In an undirected graph all edges connected to this vertex are considered
     * outgoing, and a self-loop counts twice towards the degree (so that the sum of all degrees is always twice the
     * number of edges). Throws [IllegalArgumentException] if passed a vertex that is not in this graph.
     */
    @JvmName("outDegree")
    public fun outDegree(vertex: Vertex): Int

    /**
     * Returns the in-degree of the given vertex. In a directed graph this is equivalent to
     * `incomingEdges(vertex).size()`. In an undirected graph all edges connected to this vertex are considered
     * incoming, and this is always equal to [outDegree] (see [outDegree] for how self-loops are counted). Throws
     * [IllegalArgumentException] if passed a vertex that is not in this graph.
     */
    @JvmName("inDegree")
    public fun inDegree(vertex: Vertex): Int

    /**
     * Returns the number of distinct successor vertices for the given vertex. This will always be equivalent to
     * `successors(vertex).size`, but may not allocate a new collection. Distinct from [outDegree] in that it counts
     * the number of vertices rather than the number of edges.
     */
    @JvmName("successorsCount")
    public fun successorsCount(vertex: Vertex): Int = successors(vertex).size

    /**
     * Returns the set of vertices that can be reached from the given vertex by traversing outgoing edges. In an
     * undirected graph all edges connected to this vertex are considered outgoing. The returned value is a live view
     * that reflects changes to the underlying topology. If the vertex the collection is based on is removed from the
     * graph the behavior of the collection is undefined (and may throw exceptions). Throws [IllegalArgumentException]
     * if passed a vertex that is not in this graph.
     */
    @JvmName("successors")
    public fun successors(vertex: Vertex): VertexSet

    /**
     * Returns the single vertex that can be reached from the given vertex by traversing outgoing edges. If there is
     * not exactly one such vertex (see [successors]), then [IllegalStateException] is thrown. Throws
     * [IllegalArgumentException] if passed a vertex that is not in this graph.
     */
    @JvmName("successor")
    public fun successor(vertex: Vertex): Vertex {
        val successors = successors(vertex)
        check (successors.size == 1)
        return successors.iterator().next()
    }

    /**
     * Returns the number of distinct predecessor vertices for the given vertex. This will always be equivalent to
     * `predecessors(vertex).size`, but may not allocate new collection. Distinct from [inDegree] in that it counts the
     * number of vertices rather than the number of edges.
     */
    @JvmName("predecessorsCount")
    public fun predecessorsCount(vertex: Vertex): Int = predecessors(vertex).size

    /**
     * Returns the set of vertices that can be reached from the given vertex by traversing incoming edges. In an
     * undirected graph all edges connected to this vertex are considered incoming. The returned value is a live view
     * that reflects changes to the underlying topology. If the vertex the collection is based on is removed from the
     * graph the behavior of the collection is undefined (and may throw exceptions). Throws [IllegalArgumentException]
     * if passed a vertex that is not in this graph.
     */
    @JvmName("predecessors")
    public fun predecessors(vertex: Vertex): VertexSet

    /**
     * Returns the single vertex that can be reached from the given vertex by traversing incoming edges. If there is
     * not exactly one such vertex (see [predecessors]), then [IllegalStateException] is thrown. Throws
     * [IllegalArgumentException] if passed a vertex that is not in this graph.
     */
    @JvmName("predecessor")
    public fun predecessor(vertex: Vertex): Vertex {
        val predecessors = predecessors(vertex)
        check (predecessors.size == 1)
        return predecessors.iterator().next()
    }

    /**
     * Returns the number of distinct outgoing edges for the given vertex. This will always be equivalent to
     * `outgoingEdges(vertex).size`, but may not allocate a new collection. Always equivalent to [outDegree] in a
     * directed graph, but may not be equal to [outDegree] in an undirected graph (see notes on self-loops).
     */
    @JvmName("outgoingEdgeCount")
    public fun outgoingEdgeCount(vertex: Vertex): Int = outgoingEdges(vertex).size

    /**
     * Returns the set of edges that are outgoing from this vertex. In an undirected graph all edges connected to this
     * vertex are considered outgoing. The returned value is a live view that reflects changes to the underlying
     * topology. If the vertex the collection is based on is removed from the graph the behavior of the collection is
     * undefined (and may throw exceptions). Throws [IllegalArgumentException] if passed a vertex that is not in this
     * graph.
     */
    @JvmName("outgoingEdges")
    public fun outgoingEdges(vertex: Vertex): EdgeSet

    /**
     * Returns the single outgoing edge from this vertex. If there are no outgoing edges or more than one outgoing edge,
     * then [IllegalStateException] is thrown. Throws [IllegalArgumentException] if passed a vertex that is not in this
     * graph.
     */
    @JvmName("outgoingEdge")
    public fun outgoingEdge(vertex: Vertex): Edge {
        val outgoingEdges = outgoingEdges(vertex)
        check (outgoingEdges.size == 1)
        return outgoingEdges.iterator().next()
    }

    /**
     * Returns the number of distinct incoming edges for the given vertex. This will always be equivalent to
     * `incomingEdges(vertex).size`, but may not allocate a new collection. Always equivalent to [inDegree] in a
     * directed graph, but may not be equal to [inDegree] in an undirected graph (see notes on self-loops).
     */
    @JvmName("incomingEdgeCount")
    public fun incomingEdgeCount(vertex: Vertex): Int = incomingEdges(vertex).size

    /**
     * Returns the set of edges that are incoming to this vertex. In an undirected graph all edges connected to this
     * vertex are considered incoming. The returned value is a live view that reflects changes to the underlying
     * topology. If the vertex the collection is based on is removed from the graph the behavior of the collection is
     * undefined (and may throw exceptions). Throws [IllegalArgumentException] if passed a vertex that is not in this
     * graph.
     */
    @JvmName("incomingEdges")
    public fun incomingEdges(vertex: Vertex): EdgeSet

    /**
     * Returns the single incoming edge from this vertex. If there are no incoming edges or more than one incoming edge,
     * then [IllegalStateException] is thrown. Throws [IllegalArgumentException] if passed a vertex that is not in this
     * graph.
     */
    @JvmName("incomingEdge")
    public fun incomingEdge(vertex: Vertex): Edge {
        val incomingEdges = incomingEdges(vertex)
        check (incomingEdges.size == 1)
        return incomingEdges.iterator().next()
    }

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
    @JvmName("edgeSource")
    public fun edgeSource(edge: Edge): Vertex

    /**
     * Returns target vertex of the given edge. Note that for undirected edges there is no guarantee that the returned
     * vertex is the same as the provided target vertex when the edge was constructed (it may be reversed). It is
     * guaranteed that the vertex returned as the target will be consistent and unchanging over time. With undirected
     * edges it may be more convenient to use [edgeOpposite] sometimes. **There is NO guarantee an exception will be
     * thrown if the given edge does not belong to this graph.**
     */
    @JvmName("edgeTarget")
    public fun edgeTarget(edge: Edge): Vertex

    /**
     * Returns true if the graph contains an edge with the given source and target. Note that for undirected edges
     * either can serve as the source or target - for example it is possible that `hasEdge(a, b) == true` and also
     * `edgeSource(edge) == b && edgeTarget(edge) == a` for an undirected edge. Throws [IllegalArgumentException] if
     * passed a source or target vertex that is not in this graph.
     */
    @JvmName("hasEdge")
    public fun hasEdge(source: Vertex, target: Vertex): Boolean = edgesCount(source, target) > 0

    /**
     * Returns the number of distinct edges from the source vertex to the target vertex. This will always be equivalent
     * to `edges(source, target).size`, but may not allocate a new collection. Note that for undirected edges either can
     * serve as the source or target - for example it is possible that `edgeSource(edge(source, target)) == target`
     * and/or `edgeTarget(edge(source, target)) == source` for an undirected edge. Throws [IllegalArgumentException] if
     * passed a source or target vertex that is not in this graph.
     */
    @JvmName("edgesCount")
    public fun edgesCount(source: Vertex, target: Vertex): Int = edges(source, target).size

    /**
     * Returns the set of edges from the given source to the given target. Will return an empty set if there are no such
     * edges. The returned value is a live view that reflects changes to the underlying topology. If a vertex the
     * collection is based on is removed from the graph the behavior of the collection is undefined (and may throw
     * exceptions). Throws [IllegalArgumentException] if passed a vertex that is not in this graph.
     */
    @JvmName("edges")
    public fun edges(source: Vertex, target: Vertex): EdgeSet

    /**
     * Returns the single edge with the given source and target (see undirected edge caveats discussed in [hasEdge]). If
     * there are no edges or multiple edges with the given source and target, then [IllegalStateException] is thrown.
     * Throws [IllegalArgumentException] if passed a vertex that is not in this graph.
     */
    @JvmName("edge")
    public fun edge(source: Vertex, target: Vertex): Edge {
        val edges = edges(source, target)
        check (edges.size == 1)
        return edges.iterator().next()
    }

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
     * to associate data with vertices in this graph, this method returns a new [io.github.sooniln.fastgraph.properties.VertexProperty] instance which can
     * associate some type of data with vertices in this graph. The returned property is guaranteed to remain in sync
     * with the graph, such that vertices added to the graph will appear in the property and vertices removed from the
     * graph will be removed from the property.
     *
     * If this method is invoked on an [ImmutableGraph], the returned property will never reference
     * [defaultValueFunction] after initialization. For other graphs, the returned property may continue to reference
     * [defaultValueFunction] indefinitely (in case vertices are later added), so be cautious of leaking memory through
     * the reference.
     *
     * The extension method of the same name allows for not passing in the [io.github.sooniln.fastgraph.properties.PropertyType] parameter explicitly - this
     * should be simpler to use where possible.
     */
    @JvmName("createVertexProperty")
    public fun <T> createVertexProperty(
        type: PropertyType<T>,
        defaultValueFunction: VertexFunction<T>
    ): MutableVertexProperty<T> =
        io.github.sooniln.fastgraph.properties.createVertexProperty(this, type, defaultValueFunction)

    /**
     * [Graph] represents only a topology, not any data associated with the vertices and edges of the topology. In order
     * to associate data with edges in this graph, this method returns a new [io.github.sooniln.fastgraph.properties.EdgeProperty] instance which can associate
     * some type of data with edges in this graph. The returned property is guaranteed to remain in sync with the graph,
     * such that edges added to the graph will appear in the property and edges removed from the graph will be removed
     * from the property.
     *
     * If this method is invoked on an [ImmutableGraph], the returned property will never reference
     * [defaultValueFunction] after this method completes. For graphs that are not [ImmutableGraph], the returned
     * property will reference [defaultValueFunction] indefinitely, but is guaranteed to only invoke
     * [defaultValueFunction] if an attempt is made to access a property value that has not yet been explicitly set for
     * the given edge.
     *
     * The extension method of the same name allows for not passing in the [PropertyType] parameter explicitly - this
     * should be simpler to use where possible.
     */
    @JvmName("createEdgeProperty")
    public fun <T> createEdgeProperty(
        type: PropertyType<T>,
        defaultValueFunction: EdgeFunction<T>
    ): MutableEdgeProperty<T> =
        io.github.sooniln.fastgraph.properties.createEdgeProperty(this, type, defaultValueFunction)

    /**
     * Returns a new [io.github.sooniln.fastgraph.properties.MutableVertexKeyProperty] associated with this graph. Key properties have no default value -
     * see [io.github.sooniln.fastgraph.properties.VertexKeyProperty] for their semantics. The returned property is guaranteed to remain in sync with the
     * graph.
     *
     * The extension method of the same name allows for not passing in the [PropertyType] parameter explicitly - this
     * should be simpler to use where possible.
     */
    @JvmName("createVertexKeyProperty")
    public fun <T> createVertexKeyProperty(type: PropertyType<T>): MutableVertexKeyProperty<T> {
        return io.github.sooniln.fastgraph.properties.createVertexKeyProperty(this, type)
    }

    /**
     * Returns a new [io.github.sooniln.fastgraph.properties.MutableEdgeKeyProperty] associated with this graph. Key properties have no default value - see
     * [io.github.sooniln.fastgraph.properties.EdgeKeyProperty] for their semantics. The returned property is guaranteed to remain in sync with the graph.
     *
     * The extension method of the same name allows for not passing in the [PropertyType] parameter explicitly - this
     * should be simpler to use where possible.
     */
    @JvmName("createEdgeKeyProperty")
    public fun <T> createEdgeKeyProperty(type: PropertyType<T>): MutableEdgeKeyProperty<T> {
        return io.github.sooniln.fastgraph.properties.createEdgeKeyProperty(this, type)
    }

    /**
     * Returns a stable reference to the given vertex. For more information about vertices and stable references to
     * vertices, see [io.github.sooniln.fastgraph.references.VertexReference].
     */
    @JvmName("createVertexReference")
    public fun createVertexReference(vertex: Vertex): VertexReference

    /**
     * Returns a stable reference to the given edge. For more information about edges and stable references to edges,
     * see [io.github.sooniln.fastgraph.references.EdgeReference].
     */
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
@JvmSynthetic
public fun Graph.outDegree(vertexReference: VertexReference): Int = outDegree(vertexReference.unstable)

/**
 * See [Graph.inDegree].
 */
@JvmSynthetic
public fun Graph.inDegree(vertexReference: VertexReference): Int = inDegree(vertexReference.unstable)

/**
 * See [Graph.successors].
 */
@JvmSynthetic
public fun Graph.successors(vertexReference: VertexReference): VertexSet = successors(vertexReference.unstable)

/**
 * See [Graph.predecessors].
 */
@JvmSynthetic
public fun Graph.predecessors(vertexReference: VertexReference): VertexSet = predecessors(vertexReference.unstable)

/**
 * See [Graph.outgoingEdges].
 */
@JvmSynthetic
public fun Graph.outgoingEdges(vertexReference: VertexReference): EdgeSet = outgoingEdges(vertexReference.unstable)

/**
 * See [Graph.incomingEdges].
 */
@JvmSynthetic
public fun Graph.incomingEdges(vertexReference: VertexReference): EdgeSet = incomingEdges(vertexReference.unstable)

/**
 * See [Graph.edgeSource].
 */
@JvmSynthetic
public fun Graph.edgeSource(edgeReference: EdgeReference): Vertex = edgeSource(edgeReference.unstable)

/**
 * See [Graph.edgeTarget].
 */
@JvmSynthetic
public fun Graph.edgeTarget(edgeReference: EdgeReference): Vertex = edgeTarget(edgeReference.unstable)

/**
 * For a directed graph, this is equivalent to [Graph.edgeSource]. For an undirected graph this returns the vertex
 * opposite the given [target] vertex. Behavior is undefined if [target] does not belong to [edge]. This method is
 * faster than [edgeOpposite] for directed graphs.
 */
@JvmName("edgeSource")
public fun Graph.edgeSource(edge: Edge, target: Vertex): Vertex {
    if (directed) {
        val source = edgeSource(edge)
        assert(edgeTarget(edge) == target)
        return source
    } else {
        return edgeOpposite(edge, target)
    }
}

/**
 * For a directed graph, this is equivalent to [Graph.edgeSource]. For an undirected graph this returns the vertex
 * opposite the given [target] vertex. Behavior is undefined if [target] does not belong to [edge]. This method is
 * faster than [edgeOpposite].
 */
@JvmSynthetic
public fun Graph.edgeSource(edge: Edge, target: VertexReference): Vertex = edgeSource(edge, target.unstable)

/**
 * For a directed graph, this is equivalent to [Graph.edgeTarget]. For an undirected graph this returns the vertex
 * opposite the given [source] vertex. Behavior is undefined if [source] does not belong to [edge]. This method is
 * faster than [edgeOpposite] for directed graphs.
 */
@JvmName("edgeTarget")
public fun Graph.edgeTarget(edge: Edge, source: Vertex): Vertex {
    if (directed) {
        val target = edgeTarget(edge)
        assert(edgeSource(edge) == source)
        return target
    } else {
        return edgeOpposite(edge, source)
    }
}

/**
 * For a directed graph, this is equivalent to [Graph.edgeTarget]. For an undirected graph this returns the vertex
 * opposite the given [source] vertex. Behavior is undefined if [source] does not belong to [edge]. This method is
 * faster than [edgeOpposite].
 */
@JvmSynthetic
public fun Graph.edgeTarget(edge: Edge, source: VertexReference): Vertex = edgeTarget(edge, source.unstable)

/**
 * Returns the vertex of the given edge that is opposite the given vertex. I.e., the source vertex is returned if the
 * target vertex is provided, and vice versa. Throws [IllegalArgumentException] if the given vertex is neither the
 * source nor target of the given edge. This method is often useful when working with undirected edges where the
 * source/target distinction does not exist. If viable, the [edgeSource] and [edgeTarget] extension methods are faster
 * than this method.
 */
@JvmName("edgeOpposite")
public fun Graph.edgeOpposite(edge: Edge, other: Vertex): Vertex {
    val target = edgeTarget(edge)
    val source = edgeSource(edge)
    if (other == target) {
        return source
    } else {
        if (other != source) {
            throw IllegalArgumentException("vertex $other is not in edge $source -> $target")
        }

        return target
    }
}

/**
 * Returns the vertex of the given edge that is opposite the given vertex reference. I.e., the source vertex is returned
 * if the target vertex is provided, and vice versa. Throws [IllegalArgumentException] if the given vertex reference is
 * neither the source nor target of the given edge. This method is often useful when working with undirected edges where
 * the source/target distinction does not exist.
 */
@JvmSynthetic
public fun Graph.edgeOpposite(edge: Edge, other: VertexReference): Vertex = edgeOpposite(edge, other.unstable)

/**
 * Returns the density of the graph, defined as M/(N*(N-1)) for directed graphs and 2M/(N*(N-1)) for undirected graphs,
 * where M is the number of edges and N is the number of vertices.
 */
public fun Graph.density(): Double {
    val numerator = if (directed) edges.size.toDouble() else 2.0 * edges.size
    val numVertices = vertices.size.toDouble()
    return numerator / (numVertices * (numVertices - 1))
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that creates a [MutableVertexProperty] with every
 * value initialized to null.
 */
public inline fun <reified T> Graph.createVertexProperty(): MutableVertexProperty<T?> {
    return createVertexProperty(propertyTypeOf<T?>()) { null }
}

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [PropertyType].
 */
public inline fun <reified T> Graph.createVertexProperty(
    defaultValueFunction: VertexFunction<T>
): MutableVertexProperty<T> = createVertexProperty(propertyTypeOf<T>(), defaultValueFunction)

/**
 * A convenient extension method for [Graph.createVertexProperty] that does not require explicitly providing the
 * [PropertyType].
 */
public inline fun <reified T> Graph.createVertexProperty(defaultValue: T): MutableVertexProperty<T> {
    return createVertexProperty(propertyTypeOf<T>()) { defaultValue }
}

/**
 * A convenient extension method for [Graph.createVertexKeyProperty] that does not require explicitly providing the
 * [PropertyType].
 */
public inline fun <reified T> Graph.createVertexKeyProperty(): MutableVertexKeyProperty<T> =
    createVertexKeyProperty(propertyTypeOf<T>())

/**
 * A convenient extension method for [Graph.createEdgeProperty] that creates a [MutableEdgeProperty] with every value
 * initialized to null.
 */
public inline fun <reified T> Graph.createEdgeProperty(): MutableEdgeProperty<T?> {
    return createEdgeProperty(propertyTypeOf<T?>()) { null }
}

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the
 * [PropertyType].
 */
public inline fun <reified T> Graph.createEdgeProperty(
    defaultValueFunction: EdgeFunction<T>
): MutableEdgeProperty<T> = createEdgeProperty(propertyTypeOf<T>(), defaultValueFunction)

/**
 * A convenient extension method for [Graph.createEdgeProperty] that does not require explicitly providing the
 * [PropertyType].
 */
public inline fun <reified T> Graph.createEdgeProperty(defaultValue: T): MutableEdgeProperty<T> {
    return createEdgeProperty(propertyTypeOf<T>()) { defaultValue }
}

/**
 * A convenient extension method for [Graph.createEdgeKeyProperty] that does not require explicitly providing the
 * [PropertyType].
 */
public inline fun <reified T> Graph.createEdgeKeyProperty(): MutableEdgeKeyProperty<T> =
    createEdgeKeyProperty(propertyTypeOf<T>())

/**
 * A [Graph] that encodes all edges as [CanonicalEdge].
 */
public interface CanonicalEdgeGraph : Graph {
    override fun outgoingEdges(vertex: Vertex): CanonicalEdgeSet
    override fun incomingEdges(vertex: Vertex): CanonicalEdgeSet

    override val edges: CanonicalEdgeSet
    override fun edges(source: Vertex, target: Vertex): CanonicalEdgeSet
}

@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexChangeListener {
    /**
     * Invoked after the vertex is added to the graph. The graph is in a consistent state with the vertex present.
     */
    @JvmName("onVertexAdded")
    public fun onVertexAdded(vertex: Vertex)

    /**
     * Invoked before the vertex is removed from the graph. The graph is in a consistent state with the vertex present.
     */
    @JvmName("onVertexRemoved")
    public fun onVertexRemoved(vertex: Vertex)

    /**
     * Invoked before a vertex ID is re-assigned. The caller guarantees that [oldVertex] != [newVertex]. This indicates
     * that [newVertex] is being removed from the graph and [oldVertex] is having its ID re-assigned to that of
     * [newVertex]. The effect of this method should be the same as if: (1) all references to `newVertex.id` are
     * removed (if any exist) - the same as if `onVertexRemoved(newVertex.id)` was invoked (2) all references to
     * `oldVertex.id` are updated to `newVertex.id` (3) no references to `oldVertex.id` should be present anywhere after
     * completion of this method.
     */
    @JvmName("onVertexReassigned")
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

@Suppress("INAPPLICABLE_JVM_NAME")
public interface EdgeChangeListener {
    /**
     * Invoked after the edge is added to the graph. The graph is in a consistent state with the edge present.
     */
    @JvmName("onEdgeAdded")
    public fun onEdgeAdded(edge: Edge)

    /**
     * Invoked before the edge is removed from the graph. The graph is in a consistent state with the edge present.
     */
    @JvmName("onEdgeRemoved")
    public fun onEdgeRemoved(edge: Edge)

    /**
     * Invoked before an edge ID is re-assigned. The caller guarantees that [oldEdge] != [newEdge]. This indicates that
     * [oldEdge] is having its ID re-assigned to that of [newEdge]. The effect of this method should be the same as if:
     * (1) all references to `newEdge.id` are removed (if any exist) - the same as if `onEdgeRemoved(newEdge.id)` was
     * invoked (2) all references to `oldEdge.id` are updated to `newEdge.id` (3) no references to `oldEdge.id` should
     * be present anywhere after completion of this method.
     */
    @JvmName("onEdgeReassigned")
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
 * An interface for building graphs. While there are some similarities to [MutableGraph], this builder only allows for
 * additive operations.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface GraphBuilder {

    /**
     * Adds a new vertex to the graph and returns it.
     */
    @JvmName("addVertex")
    public fun addVertex(): Vertex

    /**
     * Adds a new vertex to the graph and returns it. Optionally may pre-allocate enough memory for the given
     * [outDegreeCapacity]/[inDegreeCapacity].
     */
    @JvmName("addVertex")
    public fun addVertex(outDegreeCapacity: Int, inDegreeCapacity: Int): Vertex = addVertex()

    /**
     * Adds a new edge connecting the given source and target vertex. See [Graph.edgesCount] for caveats on how
     * source/target are treated in undirected graphs. In a [MutableGraph] implementation that does not support
     * multi-edges, this method will throw [IllegalArgumentException] if there already exists an edge connecting those
     * vertices in the same direction.
     */
    @JvmName("addEdge")
    public fun addEdge(source: Vertex, target: Vertex): Edge

    /**
     * Optionally implemented to pre-allocate enough memory for the given [vertexCapacity].
     */
    public fun ensureVertexCapacity(vertexCapacity: Int) {}

    /**
     * Optionally implemented to pre-allocate enough memory for the given [edgeCapacity].
     */
    public fun ensureEdgeCapacity(edgeCapacity: Int) {}
}

/**
 * A specialization of [Graph] which allows mutation of the graph topology via the addition or subtraction of vertices
 * and edges. When mutation is not required, clients should prefer [Graph].
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface MutableGraph : Graph, GraphBuilder {

    override val vertices: MutableVertexSet

    override val edges: MutableEdgeSet

    /**
     * Removes the given vertex from the graph. Any edges with the given vertex as the source or target are also
     * removed.
     */
    @JvmName("removeVertex")
    public fun removeVertex(vertex: Vertex)

    /**
     * Removes the given edge from the graph.
     */
    @JvmName("removeEdge")
    public fun removeEdge(edge: Edge)
}

/**
 * See [MutableGraph.removeVertex].
 */
@JvmSynthetic
public fun MutableGraph.removeVertex(vertexReference: VertexReference): Unit = removeVertex(vertexReference.unstable)

/**
 * See [MutableGraph.addEdge].
 */
@JvmSynthetic
public fun MutableGraph.addEdge(source: VertexReference, target: VertexReference): Edge =
    addEdge(source.unstable, target.unstable)

/**
 * See [MutableGraph.removeEdge].
 */
@JvmSynthetic
public fun MutableGraph.removeEdge(edgeReference: EdgeReference): Unit = removeEdge(edgeReference.unstable)

/**
 * A convenience interface for bundling a graph topology with a vertex key property and an edge value property. Every
 * vertex is identified by a unique key (see [io.github.sooniln.fastgraph.properties.VertexKeyProperty]) and every edge carries a value.
 *
 * When creating and using [ValueGraph], note that if you do not require an edge value, you can set the edge type to
 * [Unit], which ensures it will take up no additional resources. If you do not require a vertex key of your own,
 * [Graph.vertexIdProperty] can be used as a key property which takes up no additional resources.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface ValueGraph<V, E> {
    /** The topology. */
    public val graph: Graph
    /** The vertex key property associated with the topology. */
    public val vertexKeys: VertexKeyProperty<V>
    /** The edge value property associated with the topology. */
    public val edgeValues: MutableEdgeProperty<E>

    /** A convenience property that returns the property of the same name on [graph]. */
    public val directed: Boolean get() = graph.directed
    /** A convenience property that returns the property of the same name on [graph]. */
    public val multiEdge: Boolean get() = graph.multiEdge
    /** A convenience property that returns the property of the same name on [graph]. */
    public val vertices: VertexSet get() = graph.vertices
    /** A convenience property that returns the property of the same name on [graph]. */
    public val edges: EdgeSet get() = graph.edges

    /** A convenience method that calls the method of the same name on [graph]. */
    public fun isEmpty(): Boolean = graph.isEmpty()
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("outDegree")
    public fun outDegree(vertex: Vertex): Int = graph.outDegree(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("inDegree")
    public fun inDegree(vertex: Vertex): Int = graph.inDegree(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("successorsCount")
    public fun successorsCount(vertex: Vertex): Int = graph.successorsCount(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("successors")
    public fun successors(vertex: Vertex): VertexSet = graph.successors(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("successor")
    public fun successor(vertex: Vertex): Vertex = graph.successor(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("predecessorsCount")
    public fun predecessorsCount(vertex: Vertex): Int = graph.predecessorsCount(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("predecessors")
    public fun predecessors(vertex: Vertex): VertexSet = graph.predecessors(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("predecessor")
    public fun predecessor(vertex: Vertex): Vertex = graph.predecessor(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("outgoingEdgeCount")
    public fun outgoingEdgeCount(vertex: Vertex): Int = graph.outgoingEdgeCount(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("outgoingEdges")
    public fun outgoingEdges(vertex: Vertex): EdgeSet = graph.outgoingEdges(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("outgoingEdge")
    public fun outgoingEdge(vertex: Vertex): Edge = graph.outgoingEdge(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("incomingEdgeCount")
    public fun incomingEdgeCount(vertex: Vertex): Int = graph.incomingEdgeCount(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("incomingEdges")
    public fun incomingEdges(vertex: Vertex): EdgeSet = graph.incomingEdges(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("incomingEdge")
    public fun incomingEdge(vertex: Vertex): Edge = graph.incomingEdge(vertex)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("edgeSource")
    public fun edgeSource(edge: Edge): Vertex = graph.edgeSource(edge)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("edgeTarget")
    public fun edgeTarget(edge: Edge): Vertex = graph.edgeTarget(edge)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("hasEdge")
    public fun hasEdge(source: Vertex, target: Vertex): Boolean = graph.hasEdge(source, target)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("edgesCount")
    public fun edgesCount(source: Vertex, target: Vertex): Int = graph.edgesCount(source, target)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("edges")
    public fun edges(source: Vertex, target: Vertex): EdgeSet = graph.edges(source, target)
    /** A convenience method that calls the method of the same name on [graph]. */
    @JvmName("edge")
    public fun edge(source: Vertex, target: Vertex): Edge = graph.edge(source, target)
}

/**
 * A specialization of [ValueGraph] which contains a [MutableGraph] and allows mutation of the graph topology and the
 * vertex keys.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface MutableValueGraph<V, E> : ValueGraph<V, E>, ValueGraphBuilder<V, E> {
    override val graph: MutableGraph
    override val vertexKeys: MutableVertexKeyProperty<V>

    /**
     * See [MutableGraph.removeVertex].
     */
    @JvmName("removeVertex")
    public fun removeVertex(vertex: Vertex): Unit = graph.removeVertex(vertex)

    /**
     * See [MutableGraph.removeEdge].
     */
    @JvmName("removeEdge")
    public fun removeEdge(edge: Edge): Unit = graph.removeEdge(edge)

    @JvmName("hasVertex")
    override fun hasVertex(key: V): Boolean = vertexKeys.hasVertex(key)

    @JvmName("getVertex")
    override fun getVertex(key: V): Vertex = vertexKeys.getVertex(key)
}

/**
 * An interface for building ValueGraphs. This interface allows for associating vertex keys/edge values with
 * vertices/edges at construction time. In addition, it allows referring to vertices by their keys during construction
 * as a convenience.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface ValueGraphBuilder<V, E> {
    /**
     * Adds a new vertex with the given [key] set in [ValueGraph.vertexKeys] and returns it. Throws
     * [IllegalArgumentException] if the key is already the key of a different vertex.
     */
    @JvmName("addVertex")
    public fun addVertex(key: V): Vertex

    /**
     * Adds a new vertex with the given [key] set in [ValueGraph.vertexKeys] and returns it. Optionally may
     * pre-allocate enough memory for the given [outDegreeCapacity]/[inDegreeCapacity].
     */
    @JvmName("addVertex")
    public fun addVertex(key: V, outDegreeCapacity: Int, inDegreeCapacity: Int): Vertex = addVertex(key)

    /**
     * Adds a new edge connecting the given source and target vertex with the default value of
     * [ValueGraph.edgeValues] and returns it. See [GraphBuilder.addEdge] for caveats.
     */
    @JvmName("addEdge")
    public fun addEdge(source: Vertex, target: Vertex): Edge

    /**
     * Adds a new edge connecting the given source and target vertex with the given [value] set in
     * [ValueGraph.edgeValues] and returns it. See [GraphBuilder.addEdge] for caveats.
     */
    @JvmName("addEdge")
    public fun addEdge(source: Vertex, target: Vertex, value: E): Edge

    /**
     * Adds a new edge between the two vertices with the given keys and returns it. If a vertex is not found with the
     * desired key, one will be created with that key.
     */
    @JvmName("addEdge")
    public fun addEdge(sourceKey: V, targetKey: V): Edge {
        val source = if (hasVertex(sourceKey)) getVertex(sourceKey) else addVertex(sourceKey)
        val target = if (hasVertex(targetKey)) getVertex(targetKey) else addVertex(targetKey)
        return addEdge(source, target)
    }

    /**
     * Adds a new edge between the two vertices with the given keys with the given [value] set in
     * [ValueGraph.edgeValues] and returns it. If a vertex is not found with the desired key, one will be created with
     * that key.
     */
    @JvmName("addEdge")
    public fun addEdge(sourceKey: V, targetKey: V, value: E): Edge {
        val source = if (hasVertex(sourceKey)) getVertex(sourceKey) else addVertex(sourceKey)
        val target = if (hasVertex(targetKey)) getVertex(targetKey) else addVertex(targetKey)
        return addEdge(source, target, value)
    }

    /**
     * See [VertexKeyProperty.hasVertex].
     */
    @JvmName("hasVertex")
    public fun hasVertex(key: V): Boolean

    /**
     * See [VertexKeyProperty.getVertex].
     */
    @JvmName("getVertex")
    public fun getVertex(key: V): Vertex

    /**
     * Optionally implemented to pre-allocate enough memory for the given [vertexCapacity].
     */
    public fun ensureVertexCapacity(vertexCapacity: Int) {}

    /**
     * Optionally implemented to pre-allocate enough memory for the given [edgeCapacity].
     */
    public fun ensureEdgeCapacity(edgeCapacity: Int) {}
}

/**
 * Returns a read-only empty [Graph] with the given directedness.
 */
public fun emptyGraph(directed: Boolean): Graph = emptyImmutableGraph(directed)

/**
 * Returns a read-only empty [ValueGraph] with the given directedness.
 */
public inline fun <reified V, reified E> emptyValueGraph(directed: Boolean): ValueGraph<V, E> =
    emptyImmutableValueGraph(directed)

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
    defaultEdgeValueFunction: EdgeFunction<E>,
    multiEdge: Boolean = false,
    indexEdges: Boolean = false,
    builder: ValueGraphBuilder<V, E>.() -> Unit
): MutableValueGraph<V, E> {
    val graph = mutableGraph(directed, multiEdge, indexEdges)
    return mutableValueGraph(
        graph,
        graph.createVertexKeyProperty(propertyTypeOf<V>()),
        graph.createEdgeProperty(propertyTypeOf<E>(), defaultEdgeValueFunction)
    ).apply { builder() }
}

/**
 * Constructs and returns a new empty [MutableGraph] with the given directedness. The returned mutable graph's
 * [Graph.vertices] is guaranteed to be an [IndexedVertexSet].
 *
 * There are several parameters that help control the specific graph implementation chosen:
 *   * [multiEdge]: If set to true, ensures that the returned mutable graph supports adding multi-edges
 *   (multiple edges that connect the same pair of vertices in the same direction). If a client attempts to add a
 *   multi-edge to a [Graph] implementation that does not support multi-edges, [IllegalArgumentException] will be
 *   thrown.
 *   * [indexEdges]: If set to true, uses additional memory to assign an index to every edge in order to speed up
 *   edge and edge property access and iteration. While this increases the amount of memory required to store edge
 *   topology, it substantially speeds up access, and will reduce the amount of memory needed to store edge properties.
 *   While you should always measure to be sure, with even a single edge property present it usually uses less memory
 *   AND is faster to set [indexEdges]. If set to true, the returned mutable graph's [Graph.edges] is guaranteed to
 *   be an [IndexedEdgeSet].
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
 * Creates a [ValueGraph] from the given graph, vertex key property, and edge value property.
 */
public fun <V, E> valueGraph(graph: Graph, vertexKeys: VertexKeyProperty<V>, edgeValues: MutableEdgeProperty<E>): ValueGraph<V, E> {
    require(vertexKeys.graph === graph)
    require(edgeValues.graph === graph)
    return object : ValueGraph<V, E> {
        override val graph: Graph get() = graph
        override val vertexKeys: VertexKeyProperty<V> get() = vertexKeys
        override val edgeValues: MutableEdgeProperty<E> get() = edgeValues
    }
}

/**
 * Creates a [MutableValueGraph] from the given mutable graph, vertex key property, and edge value property.
 */
public fun <V, E> mutableValueGraph(graph: MutableGraph, vertexKeys: MutableVertexKeyProperty<V>, edgeValues: MutableEdgeProperty<E>): MutableValueGraph<V, E> {
    require(vertexKeys.graph === graph)
    require(edgeValues.graph === graph)
    return object : MutableValueGraph<V, E> {
        override val graph: MutableGraph get() = graph
        override val vertexKeys: MutableVertexKeyProperty<V> get() = vertexKeys
        override val edgeValues: MutableEdgeProperty<E> get() = edgeValues

        private fun checkKey(key: V) {
            require(!vertexKeys.hasVertex(key)) { "\"$key\" is already associated with ${vertexKeys.getVertex(key)}" }
        }

        override fun addVertex(key: V): Vertex {
            checkKey(key)
            return graph.addVertex().also { vertexKeys[it] = key }
        }
        override fun addVertex(key: V, outDegreeCapacity: Int, inDegreeCapacity: Int): Vertex {
            checkKey(key)
            return graph.addVertex(outDegreeCapacity, inDegreeCapacity).also { vertexKeys[it] = key }
        }

        override fun addEdge(source: Vertex, target: Vertex): Edge {
            return graph.addEdge(source, target)
        }
        override fun addEdge(source: Vertex, target: Vertex, value: E): Edge {
            return graph.addEdge(source, target).also { edgeValues[it] = value }
        }

        override fun ensureVertexCapacity(vertexCapacity: Int) = graph.ensureVertexCapacity(vertexCapacity)
        override fun ensureEdgeCapacity(edgeCapacity: Int) = graph.ensureEdgeCapacity(edgeCapacity)
    }
}

/**
 * An isomorphism from a graph copy ([GraphIsomorphism.source]) to the original graph ([GraphIsomorphism.target]).
 */
public typealias GraphCopy<GS> = GraphIsomorphism<GS, Graph, VertexIsomorphism<GS, Graph>, EdgeIsomorphism<GS, Graph>>

/**
 * Returns a copy of this graph as a [MutableGraph]. The copy is the [GraphIsomorphism.target] of the returned
 * isomorphism, whose source is this graph. Vertex and edge ids in the copy may differ from those in this graph, so
 * [GraphIsomorphism.vertexMap]/[GraphIsomorphism.edgeMap] (or
 * [io.github.sooniln.fastgraph.homomorphisms.transferInto]) must be used to relate vertices, edges, and properties
 * between the two. As with any homomorphism, the returned isomorphism is only valid until either graph is modified.
 *
 * The copy supports multi-edges if this graph does or if [forceMultiEdge] is true, and indexes edges if this graph
 * does (its [Graph.edges] is an [IndexedEdgeSet]) or if [indexEdges] is true. See [mutableGraph] for more information
 * on these options.
 */
@JvmOverloads
public fun Graph.toMutableGraph(forceMultiEdge: Boolean = false, indexEdges: Boolean = false): GraphCopy<MutableGraph> {
    return copyToMutableGraph(this, forceMultiEdge, indexEdges)
}

/**
 * Returns a copy of this value graph as a [MutableValueGraph], with the same vertex keys and edge values. Edges added
 * to the copy afterward are initialized with [defaultEdgeValueFunction]. See [toMutableGraph] for the other options.
 */
@JvmOverloads
public fun <V, E> ValueGraph<V, E>.toMutableValueGraph(
    defaultEdgeValueFunction: EdgeFunction<E>,
    forceMultiEdge: Boolean = false,
    indexEdges: Boolean = false
): MutableValueGraph<V, E> {
    val copy = graph.toMutableGraph(forceMultiEdge, indexEdges)
    val keys = copy.target.createVertexKeyProperty(vertexKeys.type)
    copy.transferInto(vertexKeys, keys)
    val values = copy.target.createEdgeProperty(edgeValues.type, defaultEdgeValueFunction)
    copy.transferInto(edgeValues, values)
    return mutableValueGraph(copy.target, keys, values)
}

/**
 * Returns a live view of the given graph with every edge direction reversed (transposed). The returned graph is
 * guaranteed to use the same edge ids for transposed edges vs the original edges.
 */
public fun Graph.asTransposed(): Graph {
    return if (!directed) {
        this
    } else if (this is AbstractTransposedGraph) {
        graph
    } else {
        if (this is ImmutableGraph) {
            asTransposed()
        } else {
            TransposedGraph(this)
        }
    }
}

/**
 * Returns a live view of the given graph with every edge treated as undirected. The returned graph is guaranteed to
 * use the same vertex and edge ids as the original graph. Since edges in opposite directions between the same vertices
 * become parallel undirected edges, the returned graph always supports multi-edges.
 */
public fun Graph.asUndirected(): Graph {
    return if (!directed) {
        this
    } else if (this is ImmutableGraph) {
        asUndirected()
    } else {
        UndirectedGraph(this)
    }
}

/**
 * Returns a live view of the given graph as a directed graph ([GraphHomomorphism.source] is the live view,
 * [GraphHomomorphism.target] is the original).
 */
public fun <E> Graph.asDirected(edgeKeyProperty: EdgeKeyProperty<E>): GraphHomomorphism<Graph, Graph, VertexIsomorphism<Graph, Graph>, EdgeHomomorphism<Graph, Graph>> {
    if (this is ImmutableGraph) return asDirected(edgeKeyProperty)
    if (directed) return selfIsomorphism(this)

    val edgeHomomorphism = createAssociatedDiGraph(this)
    return homomorphism(selfVertexIsomorphism(edgeHomomorphism.source, this), edgeHomomorphism)
}

/** An integer property that simply returns the [Vertex.id] for every vertex. */
public val Graph.vertexIdProperty: VertexKeyProperty<Int>
    get() = object : VertexKeyProperty<Int> {
    override val graph: Graph get() = this@vertexIdProperty
    override val type: PropertyType<Int> get() = propertyTypeOf()
    override fun get(vertex: Vertex): Int = vertex.id
    override fun hasVertex(key: Int): Boolean = graph.vertices.contains(Vertex(key))
    override fun getVertex(key: Int): Vertex = Vertex(key)
}

/** A long property that simply returns the [Edge.id] for every edge. */
public val Graph.edgeIdProperty: EdgeKeyProperty<Long>
    get() = object : EdgeKeyProperty<Long> {
    override val graph: Graph get() = this@edgeIdProperty
    override val type: PropertyType<Long> get() = propertyTypeOf()
    override fun get(edge: Edge): Long = edge.id
    override fun hasEdge(key: Long): Boolean = graph.edges.contains(Edge(key))
    override fun getEdge(key: Long): Edge = Edge(key)
}

/** A base class that provides some basic functionality to implement [Graph]. */
@Suppress("INAPPLICABLE_JVM_NAME")
public abstract class AbstractGraph<TEdgeSet : EdgeSet> : Graph {

    /** Should be implemented to throw [IllegalArgumentException] if `vertex` does not belong to this graph. */
    @JvmName("validateVertex")
    protected abstract fun validateVertex(vertex: Vertex): Vertex

    @JvmName("outDegree")
    override fun outDegree(vertex: Vertex): Int = getOutDegree(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getOutDegree")
    protected abstract fun getOutDegree(vertex: Vertex): Int

    @JvmName("inDegree")
    override fun inDegree(vertex: Vertex): Int {
        return if (!directed) outDegree(vertex) else getInDegree(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    @JvmName("getInDegree")
    protected abstract fun getInDegree(vertex: Vertex): Int

    @JvmName("successorsCount")
    override fun successorsCount(vertex: Vertex): Int = getSuccessorsCount(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getSuccessorsCount")
    protected open fun getSuccessorsCount(vertex: Vertex): Int = getSuccessors(vertex).size

    @JvmName("successors")
    override fun successors(vertex: Vertex): VertexSet = getSuccessors(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getSuccessors")
    protected abstract fun getSuccessors(vertex: Vertex): VertexSet

    @JvmName("successor")
    override fun successor(vertex: Vertex): Vertex = getSuccessor(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getSuccessor")
    protected open fun getSuccessor(vertex: Vertex): Vertex {
        val successors = getSuccessors(vertex)
        check (successors.size == 1)
        return successors.iterator().next()
    }

    @JvmName("predecessorsCount")
    override fun predecessorsCount(vertex: Vertex): Int {
        return if (!directed) successorsCount(vertex) else getPredecessorsCount(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    @JvmName("getPredecessorsCount")
    protected open fun getPredecessorsCount(vertex: Vertex): Int = getPredecessors(vertex).size

    @JvmName("predecessors")
    override fun predecessors(vertex: Vertex): VertexSet {
        return if (!directed) successors(vertex) else getPredecessors(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    @JvmName("getPredecessors")
    protected abstract fun getPredecessors(vertex: Vertex): VertexSet

    @JvmName("predecessor")
    override fun predecessor(vertex: Vertex): Vertex {
        return if (!directed) successor(vertex) else getPredecessor(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getPredecessor")
    protected open fun getPredecessor(vertex: Vertex): Vertex {
        val predecessors = getPredecessors(vertex)
        check (predecessors.size == 1)
        return predecessors.iterator().next()
    }

    @JvmName("outgoingEdgeCount")
    override fun outgoingEdgeCount(vertex: Vertex): Int = getOutgoingEdgeCount(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getOutgoingEdgeCount")
    protected open fun getOutgoingEdgeCount(vertex: Vertex): Int = getOutgoingEdges(vertex).size

    @JvmName("outgoingEdges")
    override fun outgoingEdges(vertex: Vertex): TEdgeSet = getOutgoingEdges(validateVertex(vertex))

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getOutgoingEdges")
    protected abstract fun getOutgoingEdges(vertex: Vertex): TEdgeSet

    @JvmName("outgoingEdge")
    override fun outgoingEdge(vertex: Vertex): Edge {
        return getOutgoingEdge(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getOutgoingEdge")
    protected open fun getOutgoingEdge(vertex: Vertex): Edge {
        val outgoingEdges = getOutgoingEdges(vertex)
        check (outgoingEdges.size == 1)
        return outgoingEdges.iterator().next()
    }

    @JvmName("incomingEdgeCount")
    override fun incomingEdgeCount(vertex: Vertex): Int {
        return if (!directed) outgoingEdgeCount(vertex) else getIncomingEdgeCount(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    @JvmName("getIncomingEdgeCount")
    protected open fun getIncomingEdgeCount(vertex: Vertex): Int = getIncomingEdges(vertex).size

    @JvmName("incomingEdges")
    override fun incomingEdges(vertex: Vertex): TEdgeSet {
        return if (!directed) outgoingEdges(vertex) else getIncomingEdges(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid and `directed` is true. */
    @JvmName("getIncomingEdges")
    protected abstract fun getIncomingEdges(vertex: Vertex): TEdgeSet

    @JvmName("incomingEdge")
    override fun incomingEdge(vertex: Vertex): Edge {
        return if (!directed) outgoingEdge(vertex) else getIncomingEdge(validateVertex(vertex))
    }

    /** Will only ever be invoked if `vertex` is valid. */
    @JvmName("getIncomingEdge")
    protected open fun getIncomingEdge(vertex: Vertex): Edge {
        val incomingEdges = getIncomingEdges(vertex)
        check (incomingEdges.size == 1)
        return incomingEdges.iterator().next()
    }

    @JvmName("hasEdge")
    override fun hasEdge(source: Vertex, target: Vertex): Boolean {
        return containsEdge(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    @JvmName("containsEdge")
    protected abstract fun containsEdge(source: Vertex, target: Vertex): Boolean

    @JvmName("edgesCount")
    override fun edgesCount(source: Vertex, target: Vertex): Int {
        return getEdgesCount(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    @JvmName("getEdgesCount")
    protected open fun getEdgesCount(source: Vertex, target: Vertex): Int = getEdges(source, target).size

    @JvmName("edges")
    override fun edges(source: Vertex, target: Vertex): TEdgeSet {
        return getEdges(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    @JvmName("getEdges")
    protected abstract fun getEdges(source: Vertex, target: Vertex): TEdgeSet

    @JvmName("edge")
    override fun edge(source: Vertex, target: Vertex): Edge {
        return getEdge(validateVertex(source), validateVertex(target))
    }

    /** Will only ever be invoked if `source` and `target` are valid. */
    @JvmName("getEdge")
    protected open fun getEdge(source: Vertex, target: Vertex): Edge {
        val edges = getEdges(source, target)
        check (edges.size == 1)
        return edges.iterator().next()
    }
}
