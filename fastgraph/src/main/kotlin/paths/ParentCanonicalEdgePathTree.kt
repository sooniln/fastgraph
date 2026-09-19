package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Vertex


/**
 * An [AbstractParentPathTree] built from a [io.github.sooniln.fastgraph.CanonicalEdgeGraph], whose edge ids encode
 * their endpoints so no extra edge index is needed.
 */
internal class ParentCanonicalEdgePathTree(
    private val directedParentGraph: Boolean,
    startVertex: Vertex,
    vertexIds: IntArray,
    parentIndices: IntArray,
    parentEdgeIds: LongArray,
    depths: IntArray,
    vertexIndices: Int2IntHashMap,
) : AbstractParentPathTree(startVertex, vertexIds, parentIndices, parentEdgeIds, depths, vertexIndices) {

    override fun edgeSource(edge: Edge): Vertex {
        if (directedParentGraph) return CanonicalEdge.from(edge).target
        return super.edgeSource(edge)
    }

    override fun edgeTarget(edge: Edge): Vertex {
        if (directedParentGraph) return CanonicalEdge.from(edge).source
        return super.edgeTarget(edge)
    }

    override fun childIndex(edge: Edge): Int {
        val edge = CanonicalEdge.from(edge)
        var index = vertexIndices[edge.target.id]
        if (index > 0 && parentEdgeIds[index] == edge.id) return index
        if (directedParentGraph) return -1
        index = vertexIndices[edge.source.id]
        return if (index > 0 && parentEdgeIds[index] == edge.id) index else -1
    }
}
