package io.github.sooniln.fastgraph

/**
 * A *stable* reference to a vertex. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [Graph.createVertexReference]. [VertexReference] is
 * generally a less efficient representation than [Vertex], in terms of both memory and CPU. Prefer [Vertex] unless
 * reference stability across mutations is a requirement.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface VertexReference {

    /**
     * An unstable [Vertex] reference corresponding to this stable reference.
     */
    @get:JvmName("unstable")
    public val unstable: Vertex
}

/**
 * See [Graph.outDegree].
 */
context(graph: Graph)
public val VertexReference.outDegree: Int
    @JvmSynthetic inline get() = graph.outDegree(unstable)

/**
 * See [Graph.inDegree].
 */
context(graph: Graph)
public val VertexReference.inDegree: Int
    @JvmSynthetic inline get() = graph.inDegree(unstable)

/**
 * See [Graph.successors].
 */
@JvmSynthetic
context(graph: Graph)
public fun VertexReference.successors(): VertexSet = graph.successors(unstable)

/**
 * See [Graph.predecessors].
 */
@JvmSynthetic
context(graph: Graph)
public fun VertexReference.predecessors(): VertexSet = graph.predecessors(unstable)

/**
 * See [Graph.outgoingEdges].
 */
@JvmSynthetic
context(graph: Graph)
public fun VertexReference.outgoingEdges(): EdgeSet = graph.outgoingEdges(unstable)

/**
 * See [Graph.incomingEdges].
 */
@JvmSynthetic
context(graph: Graph)
public fun VertexReference.incomingEdges(): EdgeSet = graph.incomingEdges(unstable)

/**
 * Returns the index of this vertex in [IndexedVertexGraph.vertices].
 */
context(graph: IndexedEdgeGraph)
public val VertexReference.index: Int
    @JvmSynthetic inline get() = graph.vertices.indexOf(unstable)
