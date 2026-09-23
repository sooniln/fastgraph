package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.Long2IntHashMap
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.util.cheapLazy

internal class ParentPathForest(
    vertexIds: IntArray,
    parentIndices: IntArray,
    parentEdgeIds: LongArray,
    depths: IntArray,
    rootCount: Int,
    vertexIndices: Int2IntHashMap,
) : AbstractParentPathForest(vertexIds, parentIndices, parentEdgeIds, depths, rootCount, vertexIndices) {

    override val edges: IndexedEdgeSet = object : TreeEdgeSet() {}

    private val edgeIndices: Long2IntHashMap by cheapLazy {
        Long2IntHashMap(parentEdgeIds.size, defaultValue = -1).apply {
            for (i in 0 until nonRootCount) {
                set(parentEdgeIds[i], i)
            }
        }
    }

    override fun childIndex(edge: Edge): Int = edgeIndices[edge.id]

    override fun materializePath(endVertex: Vertex): Path {
        val index = vertexIndices[endVertex.id]
        if (index < 0) throwIllegalVertex(endVertex)

        val length = depths[index]
        val pathVertexIds = IntArray(length + 1)
        val pathEdgeIds = LongArray(length)
        var i = index
        for (k in length downTo 1) {
            pathVertexIds[k] = vertexIds[i]
            pathEdgeIds[k - 1] = parentEdgeIds[i]
            i = parentIndices[i]
        }
        pathVertexIds[0] = vertexIds[i]
        return SimplePath(pathVertexIds, pathEdgeIds)
    }
}
