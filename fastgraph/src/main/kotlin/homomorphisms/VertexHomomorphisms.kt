/**
 * Methods dealing with graph homomorphisms.
 */
@file:JvmName("Homomorphisms")
@file:JvmMultifileClass

package io.github.sooniln.fastgraph.homomorphisms

import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.VertexKeyProperty
import io.github.sooniln.fastgraph.properties.VertexProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf

/**
 * A graph homomorphism maps every vertex of one [source] graph, to a vertex of another [target] graph, such that
 * edges are respected: if two vertices are joined by an edge in [source], their mapped vertices in [target] must also
 * be joined by an edge. It is important to note that the mapping is only one way, from [source] to [target].
 *
 * A Homomorphism is only valid so long as no changes have been made to [source] or [target] topology which would
 * invalidate the homomorphism. As there is no method of detecting whether any given change invalidates the
 * homomorphism, the behavior of this class is undefined if such a change is made.
 */
public sealed interface VertexHomomorphism<out GS : Graph, out GT : Graph> {
    public val source: GS
    public val target: GT

    /**
     * A vertex property for [source] vertices to [target] vertices. [VertexProperty.graph] is always [source] for this
     * property.
     */
    public val vertexMap: VertexProperty<Vertex>
}

/**
 * A monomorphism is an injective [VertexHomomorphism], where every vertex in [source] is mapped to a distinct vertex
 * in [target] (no two vertices in the source can map to the same target vertex). Note that this does not imply that
 * either vertices or edges are surjective ([target] may have vertices/edges that are not mapped to by [source]).
 *
 * Some literature differentiates between monomorphism and strict/regular monomorphism. This interface represents a
 * (loose) monomorphism, and not a strict/regular monomorphism.
 *
 * A [VertexHomomorphism] is only valid so long as no changes have been made to [source] or [target] topology which would
 * invalidate the homomorphism. As there is no method of detecting whether any given change invalidates the
 * homomorphism, the behavior of this class is undefined if such a change is made.
 */
public sealed interface VertexMonomorphism<out GS : Graph, out GT : Graph> : VertexHomomorphism<GS, GT> {

    /**
     * A vertex property for [source] vertices to [target] vertices which can also be used to reverse lookup the
     * corresponding [source] vertex for a given [target] vertex.
     */
    public override val vertexMap: VertexKeyProperty<Vertex>
}

/**
 * An isomorphism is a surjective [VertexMonomorphism] (a full bijection), where there is no vertex or edge in [target] that
 * does not have a mapping from [source]. Less formally, and isomorphism is a mapping such that every vertex and edge in
 * [source] is mapped to a distinct vertex and edge in [target] AND vice versa - the source and target graph are
 * structurally completely identical.
 *
 * A [VertexHomomorphism] is only valid so long as no changes have been made to [source] or [target] topology which would
 * invalidate the homomorphism. As there is no method of detecting whether any given change invalidates the
 * homomorphism, the behavior of this class is undefined if such a change is made.
 */
public sealed interface VertexIsomorphism<out GS : Graph, out GT : Graph> : VertexMonomorphism<GS, GT>

public fun <GS : Graph, GT : Graph> vertexIdentityIsomorphism(source: GS, target: GT) : VertexIsomorphism<GS, GT> {
    return VertexIdentityIsomorphism(source, target)
}

public fun <G : Graph> vertexIdentityIsomorphism(graph: G) : VertexIsomorphism<G, G> {
    return SelfVertexIdentityIsomorphism(graph)
}

public fun <GS : ImmutableGraph, GT : ImmutableGraph> emptyVertexIsomorphism(source: GS, target: GT) : VertexIsomorphism<GS, GT> {
    return EmptyVertexIsomorphism(source, target)
}

private class VertexIdentityIsomorphism<out GS : Graph, out GT : Graph>(
    override val source: GS, override val target: GT
) : VertexIsomorphism<GS, GT> {
    override val vertexMap: VertexKeyProperty<Vertex> = object : VertexKeyProperty<Vertex> {
        override val graph: Graph get() = source
        override val type: PropertyType<Vertex> get() = propertyTypeOf()
        override fun get(vertex: Vertex): Vertex = vertex
        override fun hasVertex(key: Vertex): Boolean = true
        override fun getVertex(key: Vertex): Vertex = key
    }
}

private class SelfVertexIdentityIsomorphism<out G: Graph>(private val graph: G) : VertexIsomorphism<G, G> {
    override val source: G get() = graph
    override val target: G get() = graph

    override val vertexMap: VertexKeyProperty<Vertex> = object : VertexKeyProperty<Vertex> {
        override val graph: Graph get() = this@SelfVertexIdentityIsomorphism.graph
        override val type: PropertyType<Vertex> get() = propertyTypeOf()
        override fun get(vertex: Vertex): Vertex = vertex
        override fun hasVertex(key: Vertex): Boolean = true
        override fun getVertex(key: Vertex): Vertex = key
    }
}

private class EmptyVertexIsomorphism<GS : ImmutableGraph, GT : ImmutableGraph>(
    override val source: GS,
    override val target: GT,
) : VertexIsomorphism<GS, GT> {
    init {
        require(source.isEmpty())
        require(target.isEmpty())
    }

    override val vertexMap: VertexKeyProperty<Vertex> = object : VertexKeyProperty<Vertex> {
        override val graph: Graph get() = source
        override val type: PropertyType<Vertex> get() = propertyTypeOf()
        override fun get(vertex: Vertex): Vertex = throwIllegalVertex(vertex)
        override fun hasVertex(key: Vertex): Boolean = false
        override fun getVertex(key: Vertex): Vertex = throwIllegalVertex(key)
    }
}
