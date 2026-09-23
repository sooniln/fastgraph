package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal abstract class ParentCanonicalEdgePathForest(
    vertexIds: IntArray,
    parentIndices: IntArray,
    parentEdgeIds: LongArray,
    depths: IntArray,
    rootCount: Int,
    vertexIndices: Int2IntHashMap,
) : AbstractParentPathForest(vertexIds, parentIndices, parentEdgeIds, depths, rootCount, vertexIndices) {
    override val edges: IndexedEdgeSet = object : TreeEdgeSet(), CanonicalEdgeSet {}
}

internal class ParentUndirectedCanonicalEdgePathForest(
    vertexIds: IntArray,
    parentIndices: IntArray,
    parentEdgeIds: LongArray,
    depths: IntArray,
    rootCount: Int,
    vertexIndices: Int2IntHashMap,
) : ParentCanonicalEdgePathForest(vertexIds, parentIndices, parentEdgeIds, depths, rootCount, vertexIndices) {

    override fun childIndex(edge: Edge): Int {
        val edge = CanonicalEdge.from(edge)
        var index = vertexIndices[edge.target.id]
        if (index >= 0 && parentIndices[index] >= 0 && parentEdgeIds[index] == edge.id) {
            return index
        } else {
            index = vertexIndices[edge.source.id]
            return if (index >= 0 && parentIndices[index] >= 0 && parentEdgeIds[index] == edge.id) index else -1
        }
    }
}

internal class ParentDirectedCanonicalEdgePathForest(
    vertexIds: IntArray,
    parentIndices: IntArray,
    parentEdgeIds: LongArray,
    depths: IntArray,
    rootCount: Int,
    vertexIndices: Int2IntHashMap,
) : ParentCanonicalEdgePathForest(vertexIds, parentIndices, parentEdgeIds, depths, rootCount, vertexIndices) {

    override fun edgeSource(edge: Edge): Vertex = CanonicalEdge.from(edge).target

    override fun edgeTarget(edge: Edge): Vertex = CanonicalEdge.from(edge).source

    override fun childIndex(edge: Edge): Int {
        val edge = CanonicalEdge.from(edge)
        val index = vertexIndices[edge.target.id]
        return if (index >= 0 && parentIndices[index] >= 0 && parentEdgeIds[index] == edge.id) index else -1
    }

    override fun materializePath(endVertex: Vertex): Path {
        val index = vertexIndices[endVertex.id]
        if (index < 0) throwIllegalVertex(endVertex)

        val length = depths[index]
        val pathEdgeIds = LongArray(length)
        var i = index
        for (k in length downTo 1) {
            pathEdgeIds[k - 1] = parentEdgeIds[i]
            i = parentIndices[i]
        }
        return CanonicalEdgePath(Vertex(vertexIds[i]), pathEdgeIds)
    }
}
