/**
 * Methods dealing with graph homomorphisms.
 */
@file:JvmName("Homomorphisms")
@file:JvmMultifileClass

package io.github.sooniln.fastgraph.homomorphisms

import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.VertexKeyProperty
import io.github.sooniln.fastgraph.properties.VertexProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf

/**
 * A graph homomorphism maps every vertex of one [source] graph, to a vertex of another [target] graph, such that
 * edges are respected: if two vertices are joined by an edge in [source], their mapped vertices in [target] must also
 * be joined by an edge. It is important to note that the mapping is only one way, from [source] to [target].
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
 * Some literature differentiates between monomorphism and strict/regular monomorphism. This interface represents
 * (loose) monomorphism, and not strict/regular monomorphism.
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
 * does not have a mapping from [source]. Less formally, an isomorphism is a mapping such that every vertex and edge in
 * [source] is mapped to a distinct vertex and edge in [target] AND vice versa - the source and target graph are
 * structurally completely identical.
 */
public sealed interface VertexIsomorphism<out GS : Graph, out GT : Graph> : VertexMonomorphism<GS, GT>

/** Returns a [VertexIsomorphism] relating all vertices of the graph to themselves. */
public fun <G : Graph> selfVertexIsomorphism(graph: G) : VertexIsomorphism<G, G> {
    return SelfVertexIsomorphism(graph)
}

/**
 * Returns an [VertexIsomorphism] relating an empty vertex set to another empty vertex set. This isomorphism becomes
 * invalid if the vertex set of either graph ever becomes non-empty.
 */
public fun <GS : Graph, GT : Graph> emptyVertexIsomorphism(source: GS, target: GT) : VertexIsomorphism<GS, GT> {
    return EmptyVertexIsomorphism(source, target)
}

internal fun <GS : Graph, GT : Graph> identityVertexIsomorphism(source: GS, target: GT) : VertexIsomorphism<GS, GT> {
    return SimpleVertexIsomorphism(source, target, IdentityVertexKeyProperty(source))
}

internal fun <GS : Graph, GT : Graph> keyVertexIsomorphism(
    source: GS,
    target: GT,
    keyProperty: VertexKeyProperty<Vertex>,
) : VertexIsomorphism<GS, GT> {
    return SimpleVertexIsomorphism(source, target, keyProperty)
}

private class SelfVertexIsomorphism<out G: Graph>(private val graph: G) : VertexIsomorphism<G, G> {
    override val source: G get() = graph
    override val target: G get() = graph

    override val vertexMap: VertexKeyProperty<Vertex> = object : VertexKeyProperty<Vertex> {
        override val graph: Graph get() = this@SelfVertexIsomorphism.graph
        override val type: PropertyType<Vertex> get() = propertyTypeOf()
        override fun get(vertex: Vertex): Vertex = vertex
        override fun hasVertex(key: Vertex): Boolean = true
        override fun getVertex(key: Vertex): Vertex = key
    }
}

private class EmptyVertexIsomorphism<GS : Graph, GT : Graph>(
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

private class SimpleVertexIsomorphism<out GS : Graph, out GT : Graph>(
    override val source: GS,
    override val target: GT,
    override val vertexMap: VertexKeyProperty<Vertex>,
) : VertexIsomorphism<GS, GT> {
    init {
        require(vertexMap.graph === source)
        require(source.vertices.size == target.vertices.size)
        // TODO: when do we want a fuller check?
    }
}

private class IdentityVertexKeyProperty(override val graph: Graph) : VertexKeyProperty<Vertex> {
    override val type: PropertyType<Vertex> get() = propertyTypeOf()
    override fun get(vertex: Vertex): Vertex = vertex
    override fun hasVertex(key: Vertex): Boolean = graph.vertices.contains(key)
    override fun getVertex(key: Vertex): Vertex {
        require(hasVertex(key))
        return key
    }
}
