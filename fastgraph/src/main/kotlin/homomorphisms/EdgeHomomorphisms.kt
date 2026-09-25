/**
 * Methods dealing with graph homomorphisms.
 */
@file:JvmName("Homomorphisms")
@file:JvmMultifileClass

package io.github.sooniln.fastgraph.homomorphisms

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.EdgeProperty
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf

/**
 * A graph homomorphism maps every edge of one [source] graph, to a edge of another [target] graph, such that
 * edges are respected: if two edges are joined by an edge in [source], their mapped edges in [target] must also
 * be joined by an edge. It is important to note that the mapping is only one way, from [source] to [target].
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
 * Some literature differentiates between monomorphism and strict/regular monomorphism. This interface represents
 * (loose) monomorphism, and not strict/regular monomorphism.
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
 * does not have a mapping from [source]. Less formally, an isomorphism is a mapping such that every edge and edge in
 * [source] is mapped to a distinct edge and edge in [target] AND vice versa - the source and target graph are
 * structurally completely identical.
 */
public sealed interface EdgeIsomorphism<out GS : Graph, out GT : Graph> : EdgeMonomorphism<GS, GT>

/** Returns an [EdgeIsomorphism] relating all edges of the graph to themselves. */
public fun <G : Graph> selfEdgeIsomorphism(graph: G) : EdgeIsomorphism<G, G> {
    return SelfEdgeIsomorphism(graph)
}

/**
 * Returns an [EdgeIsomorphism] relating an empty edge set to another empty edge set. This isomorphism becomes invalid
 * if the edge set of either graph ever becomes non-empty.
 */
public fun <GS : Graph, GT : Graph> emptyEdgeIsomorphism(source: GS, target: GT) : EdgeIsomorphism<GS, GT> {
    return EmptyEdgeIsomorphism(source, target)
}

internal fun <GS : Graph, GT : Graph> identityEdgeIsomorphism(source: GS, target: GT) : EdgeIsomorphism<GS, GT> {
    return SimpleEdgeIsomorphism(source, target, IdentityEdgeKeyProperty(source))
}

internal fun <GS : Graph, GT : Graph> keyEdgeIsomorphism(
    source: GS,
    target: GT,
    keyProperty: EdgeKeyProperty<Edge>,
) : EdgeIsomorphism<GS, GT> {
    return SimpleEdgeIsomorphism(source, target, keyProperty)
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

private class EmptyEdgeIsomorphism<GS : Graph, GT : Graph>(
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

private class SimpleEdgeIsomorphism<out GS : Graph, out GT : Graph>(
    override val source: GS,
    override val target: GT,
    override val edgeMap: EdgeKeyProperty<Edge>,
) : EdgeIsomorphism<GS, GT> {
    init {
        require(edgeMap.graph === source)
        require(source.vertices.size == target.vertices.size)
        // TODO: when do we want a fuller check?
    }
}

private class IdentityEdgeKeyProperty(override val graph: Graph) : EdgeKeyProperty<Edge> {
    override val type: PropertyType<Edge> get() = propertyTypeOf()
    override fun get(edge: Edge): Edge = edge
    override fun hasEdge(key: Edge): Boolean = graph.edges.contains(key)
    override fun getEdge(key: Edge): Edge {
        require(hasEdge(key))
        return key
    }
}
