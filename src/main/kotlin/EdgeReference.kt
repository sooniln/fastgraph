package io.github.sooniln.fastgraph

/**
 * A *stable* reference to an edge. This reference is guaranteed to never be invalidated when mutations are made to the
 * graph topology. A stable reference can be obtained through [Graph.createEdgeReference]. [EdgeReference] is generally
 * a less efficient representation than [Edge], in terms of both memory and CPU. Prefer [Edge] unless reference
 * stability across mutations is a requirement.
 */
public interface EdgeReference {

    /**
     * An unstable [Edge] reference corresponding to this stable reference.
     */
    // KT-31420: until this is resolved this must be suppressed, and @JvmName must be explicitly specified on all
    //   overrides of this method
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("unstable")
    public val unstable: Edge
}

/**
 * See [Graph.edgeSource].
 */
context(graph: Graph)
public val EdgeReference.source: Vertex
    @JvmSynthetic @JvmName("#EdgeReference_source") inline get() = graph.edgeSource(
        unstable
    )

/**
 * See [Graph.edgeTarget].
 */
context(graph: Graph)
public val EdgeReference.target: Vertex
    @JvmSynthetic @JvmName("#EdgeReference_target") inline get() = graph.edgeTarget(
        unstable
    )

/**
 * See [edgeOpposite].
 */
@JvmSynthetic
@JvmName("#EdgeReference_opposite")
context(graph: Graph)
public fun EdgeReference.opposite(other: Vertex): Vertex = graph.edgeOpposite(unstable, other)

/**
 * Returns the index of this edge in [IndexedEdgeGraph.edges].
 */
context(graph: IndexedEdgeGraph)
public val EdgeReference.index: Int
    @JvmSynthetic @JvmName("#EdgeReference_index")
    inline get() = graph.edges.indexOf(unstable)

@JvmSynthetic
@JvmName("#EdgeReference_component1")
context(graph: Graph)
public operator fun EdgeReference.component1(): Vertex = source

@JvmSynthetic
@JvmName("#EdgeReference_component2")
context(graph: Graph)
public operator fun EdgeReference.component2(): Vertex = target
