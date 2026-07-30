package io.github.sooniln.fastgraph

/**
 * A *stable* reference to a vertex. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [Graph.createVertexReference]. [VertexReference] is
 * generally a less efficient representation than [Vertex], in terms of both memory and CPU. Prefer [Vertex] unless
 * reference stability across mutations is a requirement.
 */
interface VertexReference {

    /**
     * An unstable [Vertex] reference corresponding to this stable reference.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("unstable")
    val unstable: Vertex
}

/**
 * See [Graph.outDegree].
 */
context(graph: Graph)
val VertexReference.outDegree: Int
    @JvmSynthetic @JvmName("#VertexReference_outDegree") inline get() = graph.outDegree(unstable)

/**
 * See [Graph.inDegree].
 */
context(graph: Graph)
val VertexReference.inDegree
    @JvmSynthetic @JvmName("#VertexReference_inDegree") inline get() = graph.inDegree(unstable)

/**
 * See [Graph.successors].
 */
@JvmSynthetic
@JvmName("#VertexReference_successors")
context(graph: Graph)
fun VertexReference.successors() = graph.successors(unstable)

/**
 * See [Graph.predecessors].
 */
@JvmSynthetic
@JvmName("#VertexReference_predecessors")
context(graph: Graph)
fun VertexReference.predecessors() = graph.predecessors(unstable)

/**
 * See [Graph.outgoingEdges].
 */
@JvmSynthetic
@JvmName("#VertexReference_outgoingEdges")
context(graph: Graph)
fun VertexReference.outgoingEdges() = graph.outgoingEdges(unstable)

/**
 * See [Graph.incomingEdges].
 */
@JvmSynthetic
@JvmName("#VertexReference_incomingEdges")
context(graph: Graph)
fun VertexReference.incomingEdges() = graph.incomingEdges(unstable)

/**
 * Returns the index of this vertex in [IndexedVertexGraph.vertices].
 */
context(graph: IndexedEdgeGraph)
val VertexReference.index: Int
    @JvmSynthetic @JvmName("#VertexReference_index") inline get() = graph.vertices.indexOf(unstable)
