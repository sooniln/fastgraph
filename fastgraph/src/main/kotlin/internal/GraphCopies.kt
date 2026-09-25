package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IdentityIndexedEdge
import io.github.sooniln.fastgraph.GraphCopy
import io.github.sooniln.fastgraph.IdentityIndexedEdgeSet
import io.github.sooniln.fastgraph.IdentityIndexedVertexSet
import io.github.sooniln.fastgraph.ImmutableGraph
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.emptyImmutableGraph
import io.github.sooniln.fastgraph.homomorphisms.EdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.VertexIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.copyEdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.identityEdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.selfEdgeIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.emptyIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.selfIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.isomorphism
import io.github.sooniln.fastgraph.homomorphisms.selfVertexIsomorphism
import io.github.sooniln.fastgraph.homomorphisms.vertexIsomorphism
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.properties.MutableEdgeProperty
import io.github.sooniln.fastgraph.properties.MutableVertexProperty

// forward map values for vertices/edges that have no mapping (i.e. were added to the source after the copy)
private val UNMAPPED_VERTEX = Vertex(-1)
private val UNMAPPED_EDGE = Edge(-1)

// a vertex or edge map from source to copy (forward), and from copy ids to source ids (reverse)
private class VertexMaps(val forward: MutableVertexProperty<Vertex>, val reverse: IntArray)
private class EdgeMaps(val forward: MutableEdgeProperty<Edge>, val reverse: LongArray)

internal fun copyToMutableGraph(source: Graph, forceMultiEdge: Boolean, indexEdges: Boolean): GraphCopy<MutableGraph> {
    val target = mutableGraph(
        source.directed,
        source.multiEdge || forceMultiEdge,
        indexEdges || source.edges is IndexedEdgeSet)

    // vertex ids are only preserved if source vertices are identity indexed. canonical edge ids are derived from vertex
    // ids, whereas identity indexed edge ids are assigned consecutively as edges are added.
    val vertexMaps = if (source.vertices is IdentityIndexedVertexSet) null else {
        VertexMaps(source.createVertexProperty(UNMAPPED_VERTEX), IntArray(source.vertices.size))
    }
    val identityEdges = if (target.edges is CanonicalEdgeSet) {
        vertexMaps == null && source.edges is CanonicalEdgeSet
    } else {
        source.edges is IdentityIndexedEdgeSet
    }
    val edgeMaps = if (identityEdges || !source.multiEdge) null else {
        EdgeMaps(source.createEdgeProperty(UNMAPPED_EDGE), LongArray(source.edges.size))
    }

    target.ensureVertexCapacity(source.vertices.size)
    target.ensureEdgeCapacity(source.edges.size)
    for (vertex in source.vertices) {
        val vertexCopy = target.addVertex(source.successorsCount(vertex), 0)
        if (vertexMaps == null) {
            assert(vertexCopy == vertex)
        } else {
            vertexMaps.forward[vertex] = vertexCopy
            vertexMaps.reverse[vertexCopy.id] = vertex.id
        }
    }
    for (edge in source.edges) {
        val edgeSource = source.edgeSource(edge)
        val edgeTarget = source.edgeTarget(edge)
        val edgeCopy = target.addEdge(
            if (vertexMaps == null) edgeSource else vertexMaps.forward[edgeSource],
            if (vertexMaps == null) edgeTarget else vertexMaps.forward[edgeTarget])
        if (identityEdges) {
            assert(edgeCopy == edge)
        } else if (edgeMaps != null) {
            edgeMaps.forward[edge] = edgeCopy
            edgeMaps.reverse[IdentityIndexedEdge.from(edgeCopy).id] = edge.id
        }
    }

    return copyIsomorphism(source, target, vertexMaps, identityEdges, edgeMaps)
}

// edges are mapped by identity if identityEdges is true, else by edgeMaps if not null, and else derived from vertices
private fun <GT : Graph> copyIsomorphism(
    source: Graph,
    target: GT,
    vertexMaps: VertexMaps?,
    identityEdges: Boolean,
    edgeMaps: EdgeMaps?,
): GraphCopy<GT> {
    val vertexIsomorphism: VertexIsomorphism<Graph, GT> = if (vertexMaps == null) {
        selfVertexIsomorphism(source, target)
    } else {
        vertexIsomorphism(source, target, vertexMaps.forward, vertexMaps.reverse)
    }
    val edgeIsomorphism: EdgeIsomorphism<Graph, GT> = if (identityEdges) {
        identityEdgeIsomorphism(source, target)
    } else if (edgeMaps != null) {
        selfEdgeIsomorphism(source, target, edgeMaps.forward, edgeMaps.reverse)
    } else {
        copyEdgeIsomorphism(vertexIsomorphism)
    }
    return isomorphism(vertexIsomorphism, edgeIsomorphism)
}
