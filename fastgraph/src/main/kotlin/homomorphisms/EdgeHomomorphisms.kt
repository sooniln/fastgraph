/**
 * Methods dealing with graph homomorphisms.
 */
@file:JvmName("Homomorphisms")
@file:JvmMultifileClass

package io.github.sooniln.fastgraph.homomorphisms

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.EdgeProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf

/**
 * A graph homomorphism maps every edge of one [source] graph, to a edge of another [target] graph, such that
 * edges are respected: if two edges are joined by an edge in [source], their mapped edges in [target] must also
 * be joined by an edge. It is important to note that the mapping is only one way, from [source] to [target].
 *
 * A Homomorphism is only valid so long as no changes have been made to [source] or [target] topology which would
 * invalidate the homomorphism. As there is no method of detecting whether any given change invalidates the
 * homomorphism, the behavior of this class is undefined if such a change is made.
 */
public sealed interface EdgeHomomorphism<out GS : Graph, out GT : Graph> {
    public val source: GS
    public val target: GT

    /**
     * A edge property for [source] edges to [target] edges. [EdgeProperty.graph] is always [source] for this
     * property.
     */
    public val edgeMap: EdgeProperty<Edge>
}

// exists only for internal implementation
internal interface InternalEdgeHomomorphism<out GS : Graph, out GT : Graph> : EdgeHomomorphism<GS, GT>

/**
 * A monomorphism is an injective [EdgeHomomorphism], where every edge in [source] is mapped to a distinct edge
 * in [target] (no two edges in the source can map to the same target edge). Note that this does not imply that
 * either edges or edges are surjective ([target] may have edges/edges that are not mapped to by [source]).
 *
 * Some literature differentiates between monomorphism and strict/regular monomorphism. This interface represents a
 * (loose) monomorphism, and not a strict/regular monomorphism.
 *
 * A [EdgeHomomorphism] is only valid so long as no changes have been made to [source] or [target] topology which would
 * invalidate the homomorphism. As there is no method of detecting whether any given change invalidates the
 * homomorphism, the behavior of this class is undefined if such a change is made.
 */
public sealed interface EdgeMonomorphism<out GS : Graph, out GT : Graph> : EdgeHomomorphism<GS, GT> {

    /**
     * A edge property for [source] edges to [target] edges which can also be used to reverse lookup the
     * corresponding [source] edge for a given [target] edge.
     */
    public override val edgeMap: EdgeKeyProperty<Edge>
}

/**
 * An isomorphism is a surjective [EdgeMonomorphism] (a full bijection), where there is no edge or edge in [target] that
 * does not have a mapping from [source]. Less formally, and isomorphism is a mapping such that every edge and edge in
 * [source] is mapped to a distinct edge and edge in [target] AND vice versa - the source and target graph are
 * structurally completely identical.
 *
 * A [EdgeHomomorphism] is only valid so long as no changes have been made to [source] or [target] topology which would
 * invalidate the homomorphism. As there is no method of detecting whether any given change invalidates the
 * homomorphism, the behavior of this class is undefined if such a change is made.
 */
public sealed interface EdgeIsomorphism<out GS : Graph, out GT : Graph> : EdgeMonomorphism<GS, GT>

public fun <G : Graph> edgeIsomorphism(graph: G) : EdgeIsomorphism<G, G> {
    return SelfEdgeIsomorphism(graph)
}

public fun <GS : ImmutableGraph, GT : ImmutableGraph> emptyEdgeIsomorphism(source: GS, target: GT) : EdgeIsomorphism<GS, GT> {
    return EmptyEdgeIsomorphism(source, target)
}

private class SelfEdgeIsomorphism<out G: Graph>(private val graph: G) : EdgeIsomorphism<G, G> {
    override val source: G get() = graph
    override val target: G get() = graph

    override val edgeMap: EdgeKeyProperty<Edge> = object : EdgeKeyProperty<Edge> {
        override val graph: Graph get() = this@SelfEdgeIsomorphism.graph
        override val type: PropertyType<Edge> get() = propertyTypeOf()
        override fun get(edge: Edge): Edge = edge
        override fun hasEdge(key: Edge): Boolean = true
        override fun getEdge(key: Edge): Edge = key
    }
}

private class EmptyEdgeIsomorphism<GS : ImmutableGraph, GT : ImmutableGraph>(
    override val source: GS,
    override val target: GT,
) : EdgeIsomorphism<GS, GT> {
    init {
        require(source.isEmpty())
        require(target.isEmpty())
    }

    override val edgeMap: EdgeKeyProperty<Edge> = object : EdgeKeyProperty<Edge> {
        override val graph: Graph get() = source
        override val type: PropertyType<Edge> get() = propertyTypeOf()
        override fun get(edge: Edge): Edge = throwIllegalEdge(source, edge)
        override fun hasEdge(key: Edge): Boolean = false
        override fun getEdge(key: Edge): Edge = throwIllegalEdge(target, key)
    }
}
