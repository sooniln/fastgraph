/**
 * Methods dealing with graph homomorphisms.
 */
@file:JvmName("Homomorphisms")
@file:JvmMultifileClass

package io.github.sooniln.fastgraph.homomorphisms

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.properties.EdgeKeyProperty
import io.github.sooniln.fastgraph.properties.EdgeProperty
import io.github.sooniln.fastgraph.properties.VertexKeyProperty
import io.github.sooniln.fastgraph.properties.VertexProperty
import io.github.sooniln.fastgraph.properties.propertyTypeOf

public interface GraphHomomorphism<out GS : Graph, out GT : Graph, out VH : VertexHomomorphism<GS, GT>, out EH : EdgeHomomorphism<GS, GT>>  {
    public val source: GS
    public val target: GT

    public val vertexHomomorphism: VH
    public val edgeHomomorphism: EH

    public val vertexMap: VertexProperty<Vertex> get() = vertexHomomorphism.vertexMap
    public val edgeMap: EdgeProperty<Edge> get() = edgeHomomorphism.edgeMap
}

public interface GraphMonomorphism<out GS : Graph, out GT : Graph, out VH : VertexMonomorphism<GS, GT>, out EH : EdgeMonomorphism<GS, GT>> : GraphHomomorphism<GS, GT, VH, EH>  {
    override val vertexHomomorphism: VH
    override val edgeHomomorphism: EH

    override val vertexMap: VertexKeyProperty<Vertex> get() = vertexHomomorphism.vertexMap
    override val edgeMap: EdgeKeyProperty<Edge> get() = edgeHomomorphism.edgeMap
}

public interface GraphIsomorphism<out GS : Graph, out GT : Graph, out VH : VertexIsomorphism<GS, GT>, out EH : EdgeIsomorphism<GS, GT>> : GraphMonomorphism<GS, GT, VH, EH>  {
    override val vertexHomomorphism: VH
    override val edgeHomomorphism: EH
}

public fun <GS : Graph, GT : Graph, VH: VertexHomomorphism<GS, GT>, EH: EdgeHomomorphism<GS, GT>> homomorphism(
    vertexHomomorphism: VH,
    edgeMonomorphism: EH,
): GraphHomomorphism<GS, GT, VH, EH> {
    return SimpleGraphHomomorphism(vertexHomomorphism, edgeMonomorphism)
}

public fun <GS : Graph, GT : Graph> homomorphism(vertexHomomorphism: VertexHomomorphism<GS, GT>): GraphHomomorphism<GS, GT, VertexHomomorphism<GS, GT>, EdgeHomomorphism<GS, GT>> {
    return if (vertexHomomorphism is VertexMonomorphism) {
        monomorphism(vertexHomomorphism)
    } else {
        SimpleGraphHomomorphism(vertexHomomorphism, DerivedEdgeHomomorphism(vertexHomomorphism))
    }
}

public fun <GS : Graph, GT : Graph, VH: VertexMonomorphism<GS, GT>, EH: EdgeMonomorphism<GS, GT>> monomorphism(
    vertexMonomorphism: VH,
    edgeMonomorphism: EH,
): GraphMonomorphism<GS, GT, VH, EH> {
    return SimpleGraphMonomorphism(vertexMonomorphism, edgeMonomorphism)
}

public fun <GS : Graph, GT : Graph> monomorphism(vertexMonomorphism: VertexMonomorphism<GS, GT>): GraphMonomorphism<GS, GT, VertexMonomorphism<GS, GT>, EdgeMonomorphism<GS, GT>> {
    return if (vertexMonomorphism is VertexIsomorphism) {
        isomorphism(vertexMonomorphism)
    } else {
        SimpleGraphMonomorphism(vertexMonomorphism, DerivedEdgeMonomorphism(vertexMonomorphism))
    }
}

public fun <GS : Graph, GT : Graph, VH: VertexIsomorphism<GS, GT>, EH: EdgeIsomorphism<GS, GT>> isomorphism(
    vertexIsomorphism: VH,
    edgeIsomorphism: EH,
): GraphIsomorphism<GS, GT, VH, EH> {
    return SimpleGraphIsomorphism(vertexIsomorphism, edgeIsomorphism)
}

public fun <GS : Graph, GT : Graph> isomorphism(vertexIsomorphism:  VertexIsomorphism<GS, GT>): GraphIsomorphism<GS, GT, VertexIsomorphism<GS, GT>, EdgeIsomorphism<GS, GT>> {
    return SimpleGraphIsomorphism(vertexIsomorphism, DerivedEdgeIsomorphism(vertexIsomorphism))
}

public fun <G : Graph> isomorphism(graph:  G): GraphIsomorphism<G, G, VertexIsomorphism<G, G>, EdgeIsomorphism<G, G>> {
    return SimpleGraphIsomorphism(vertexIdentityIsomorphism(graph), edgeIsomorphism(graph))
}

public fun <GS : ImmutableGraph, GT : ImmutableGraph> emptyIsomorphism(source: GS, target: GT): GraphIsomorphism<GS, GT, VertexIsomorphism<GS, GT>, EdgeIsomorphism<GS, GT>> {
    return SimpleGraphIsomorphism(emptyVertexIsomorphism(source, target), emptyEdgeIsomorphism(source, target))
}

private class SimpleGraphHomomorphism<out GS : Graph, out GT : Graph, out VH : VertexHomomorphism<GS, GT>, out EH: EdgeHomomorphism<GS, GT>>(
    override val vertexHomomorphism: VH,
    override val edgeHomomorphism: EH,
) : GraphHomomorphism<GS, GT, VH, EH> {

    init {
        require(vertexHomomorphism.source == edgeHomomorphism.source)
        require(vertexHomomorphism.target == edgeHomomorphism.target)
    }

    override val source: GS get() = vertexHomomorphism.source
    override val target: GT get() = vertexHomomorphism.target
}

private class SimpleGraphMonomorphism<out GS : Graph, out GT : Graph, out VH : VertexMonomorphism<GS, GT>, out EH: EdgeMonomorphism<GS, GT>>(
    override val vertexHomomorphism: VH,
    override val edgeHomomorphism: EH,
) : GraphMonomorphism<GS, GT, VH, EH> {

    init {
        require(vertexHomomorphism.source == edgeHomomorphism.source)
        require(vertexHomomorphism.target == edgeHomomorphism.target)
    }

    override val source: GS get() = vertexHomomorphism.source
    override val target: GT get() = vertexHomomorphism.target
}

private class SimpleGraphIsomorphism<out GS : Graph, out GT : Graph, out VH : VertexIsomorphism<GS, GT>, out EH: EdgeIsomorphism<GS, GT>>(
    override val vertexHomomorphism: VH,
    override val edgeHomomorphism: EH,
) : GraphIsomorphism<GS, GT, VH, EH> {

    init {
        require(vertexHomomorphism.source == edgeHomomorphism.source)
        require(vertexHomomorphism.target == edgeHomomorphism.target)
    }

    override val source: GS get() = vertexHomomorphism.source
    override val target: GT get() = vertexHomomorphism.target
}

private class DerivedEdgeHomomorphism<out GS : Graph, out GT : Graph>(
    private val vertexHomomorphism: VertexHomomorphism<GS, GT>,
) : EdgeHomomorphism<GS, GT> {

    init {
        require(!source.multiEdge && !target.multiEdge) { "An EdgeHomomorphism cannot be derived from a VertexHomomorphism on a multi-edge graph." }
    }

    override val source: GS get() = vertexHomomorphism.source
    override val target: GT get() = vertexHomomorphism.target

    override val edgeMap: EdgeProperty<Edge> = object : EdgeProperty<Edge> {
        override val graph: Graph get() = source
        override val type: PropertyType<Edge> get() = propertyTypeOf()

        override fun get(edge: Edge): Edge {
            val edgeSource = source.edgeSource(edge)
            val edgeTarget = source.edgeTarget(edge)
            val targetEdgeSource = vertexHomomorphism.vertexMap[edgeSource]
            val targetEdgeTarget = vertexHomomorphism.vertexMap[edgeTarget]
            return target.edge(targetEdgeSource, targetEdgeTarget)
        }
    }
}

private class DerivedEdgeMonomorphism<out GS : Graph, out GT : Graph>(
    private val vertexMonomorphism: VertexMonomorphism<GS, GT>,
) : EdgeMonomorphism<GS, GT> {

    init {
        require(!source.multiEdge && !target.multiEdge) { "An EdgeMonomorphism cannot be derived from a VertexMonomorphism on a multi-edge graph." }
    }

    override val source: GS get() = vertexMonomorphism.source
    override val target: GT get() = vertexMonomorphism.target

    override val edgeMap: EdgeKeyProperty<Edge> = object : EdgeKeyProperty<Edge> {
        override val graph: Graph get() = source
        override val type: PropertyType<Edge> get() = propertyTypeOf()

        override fun get(edge: Edge): Edge {
            val edgeSource = source.edgeSource(edge)
            val edgeTarget = source.edgeTarget(edge)
            val targetEdgeSource = vertexMonomorphism.vertexMap[edgeSource]
            val targetEdgeTarget = vertexMonomorphism.vertexMap[edgeTarget]
            return target.edge(targetEdgeSource, targetEdgeTarget)
        }

        override fun hasEdge(key: Edge): Boolean {
            val edgeSource = target.edgeSource(key)
            val edgeTarget = source.edgeTarget(key)
            val sourceEdgeSource = vertexMonomorphism.vertexMap.getVertex(edgeSource)
            val sourceEdgeTarget = vertexMonomorphism.vertexMap.getVertex(edgeTarget)
            return source.hasEdge(sourceEdgeSource, sourceEdgeTarget)
        }

        override fun getEdge(key: Edge): Edge {
            val edgeSource = target.edgeSource(key)
            val edgeTarget = source.edgeTarget(key)
            val sourceEdgeSource = vertexMonomorphism.vertexMap.getVertex(edgeSource)
            val sourceEdgeTarget = vertexMonomorphism.vertexMap.getVertex(edgeTarget)
            return source.edge(sourceEdgeSource, sourceEdgeTarget)
        }
    }
}

private class DerivedEdgeIsomorphism<out GS : Graph, out GT : Graph>(
    private val vertexIsomorphism: VertexIsomorphism<GS, GT>,
) : EdgeIsomorphism<GS, GT> {

    init {
        require(!source.multiEdge && !target.multiEdge) { "An EdgeIsomorphism cannot be derived from a VertexIsomorphism on a multi-edge graph." }
    }

    override val source: GS get() = vertexIsomorphism.source
    override val target: GT get() = vertexIsomorphism.target

    override val edgeMap: EdgeKeyProperty<Edge> = object : EdgeKeyProperty<Edge> {
        override val graph: Graph get() = source
        override val type: PropertyType<Edge> get() = propertyTypeOf()

        override fun get(edge: Edge): Edge {
            val edgeSource = source.edgeSource(edge)
            val edgeTarget = source.edgeTarget(edge)
            val targetEdgeSource = vertexIsomorphism.vertexMap[edgeSource]
            val targetEdgeTarget = vertexIsomorphism.vertexMap[edgeTarget]
            return target.edge(targetEdgeSource, targetEdgeTarget)
        }

        override fun hasEdge(key: Edge): Boolean {
            val edgeSource = target.edgeSource(key)
            val edgeTarget = source.edgeTarget(key)
            val sourceEdgeSource = vertexIsomorphism.vertexMap.getVertex(edgeSource)
            val sourceEdgeTarget = vertexIsomorphism.vertexMap.getVertex(edgeTarget)
            return source.hasEdge(sourceEdgeSource, sourceEdgeTarget)
        }

        override fun getEdge(key: Edge): Edge {
            val edgeSource = target.edgeSource(key)
            val edgeTarget = source.edgeTarget(key)
            val sourceEdgeSource = vertexIsomorphism.vertexMap.getVertex(edgeSource)
            val sourceEdgeTarget = vertexIsomorphism.vertexMap.getVertex(edgeTarget)
            return source.edge(sourceEdgeSource, sourceEdgeTarget)
        }
    }
}
