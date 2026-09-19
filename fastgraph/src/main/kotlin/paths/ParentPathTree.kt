package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.Long2IntHashMap
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Vertex


/**
 * An [AbstractParentPathTree] for graphs whose edge ids do not encode their endpoints; edges are resolved through an
 * edge id -> index map that is built on first use.
 */
internal class ParentPathTree(
    startVertex: Vertex,
    vertexIds: IntArray,
    parentIndices: IntArray,
    parentEdgeIds: LongArray,
    depths: IntArray,
    vertexIndices: Int2IntHashMap,
) : AbstractParentPathTree(startVertex, vertexIds, parentIndices, parentEdgeIds, depths, vertexIndices) {

    private val edgeIndices: Long2IntHashMap by lazy {
        val map = Long2IntHashMap(parentEdgeIds.size, defaultValue = -1)
        for (i in 1 until parentEdgeIds.size) map[parentEdgeIds[i]] = i
        return@lazy map
    }

    override fun childIndex(edge: Edge): Int = edgeIndices[edge.id]
}
