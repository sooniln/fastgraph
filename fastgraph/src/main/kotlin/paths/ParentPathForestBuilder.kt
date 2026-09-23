package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastgraph.CanonicalEdgeSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex

internal class ParentPathForestBuilder(private val graph: Graph) : PathForestBuilder {

    // id of vertex at the given index
    private val vertexIds = IntArrayList()
    // index of parent of vertex at the given index
    private val parentIndices = IntArrayList()
    // id of edge from vertex at index
    private val parentEdgeIds = LongArrayList()
    // path depth of vertex at given index
    private val depths = IntArrayList()
    // id of root at given root index
    private val rootVertexIds = IntArrayList()
    // map of vertex id to index (for non-root -> the index in vertexIds, for roots -> -2 - <rootVertexIds index>)
    private val indices = Int2IntHashMap(defaultValue = -1)

    // once a setParentEdge call reparents a vertex we need to scan for cycles at the end
    private var reparented = false

    override fun addRoot(root: Vertex) {
        if (!graph.vertices.contains(root)) throwIllegalVertex(root)
        require(indices[root.id] == -1) { "$root is already part of this path forest" }

        indices[root.id] = rootRef(rootVertexIds.size)
        rootVertexIds.add(root.id)
    }

    override fun setParentEdge(child: Vertex, edge: Edge) {
        if (!graph.edges.contains(edge)) throwIllegalEdge(graph, edge)
        val source = graph.edgeSource(edge)
        val target = graph.edgeTarget(edge)
        val parent = if (target == child) {
            source
        } else {
            require(!graph.directed && source == child) { "$edge does not lead to $child" }
            target
        }
        require(parent != child) { "$child cannot be its own parent" }

        val parentRef = indices[parent.id]
        if (parentRef == -1) throwIllegalVertex(parent)

        val index = indices[child.id]
        if (index == -1) {
            indices[child.id] = vertexIds.size
            vertexIds.add(child.id)
            parentIndices.add(parentRef)
            parentEdgeIds.add(edge.id)
            depths.add(if (parentRef < 0) 1 else depths[parentRef] + 1)
        } else {
            require(index >= 0) { "$child is a root of this path forest and cannot be given a parent" }
            // we're reparenting now, so depths becomes garbage, but will be recomputed at the end
            parentIndices[index] = parentRef
            parentEdgeIds[index] = edge.id
            reparented = true
        }
    }

    fun build(): PathForest {
        val n = vertexIds.size
        val rootCount = rootVertexIds.size
        val outVertexIds = vertexIds.copyInto(IntArray(n + rootCount))
        val outParentIndices = parentIndices.copyInto(IntArray(n + rootCount))
        val outParentEdgeIds = parentEdgeIds.copyInto(LongArray(n + rootCount))
        val outDepths = IntArray(n + rootCount)

        // append roots after the non-roots (depths and parent edge ids remain default for roots)
        for (root in 0..<rootCount) {
            val rootId = rootVertexIds[root]
            outVertexIds[n + root] = rootId
            outParentIndices[n + root] = -1
            indices[rootId] = n + root
        }
        // translate parent roots indices
        for (i in 0..<n) {
            val index = outParentIndices[i]
            if (index < 0) {
                outParentIndices[i] = n + rootRef(index)
            }
        }

        // everything except depths is laid out now
        if (reparented) {
            // if we reparented, then we need to both recalculate depths and check for cycles...
            resolveDepthsAndCycles(outVertexIds, outParentIndices, outDepths, n)
        } else {
            depths.copyInto(outDepths)
        }

        return if (graph.edges is CanonicalEdgeSet) {
            if (graph.directed) {
                ParentDirectedCanonicalEdgePathForest(outVertexIds, outParentIndices, outParentEdgeIds, outDepths, rootCount, indices)
            } else {
                ParentUndirectedCanonicalEdgePathForest(outVertexIds, outParentIndices, outParentEdgeIds, outDepths, rootCount, indices)
            }
        } else {
            ParentPathForest(outVertexIds, outParentIndices, outParentEdgeIds, outDepths, rootCount, indices)
        }
    }

    /**
     * Recomputes every depth from the parent pointers, which insertion order alone can no longer supply once a vertex
     * has been rewired. This runs over the laid out forest arrays, where the roots (from [n] onwards)
     * already have a depth of 0. Each non-root is walked up to the nearest ancestor with a known depth and the walked
     * indices are then filled back in on the way down, so the whole pass is linear. Encountering an index which is
     * already on the current walk means the parent pointers form a cycle.
     */
    private fun resolveDepthsAndCycles(outVertexIds: IntArray, outParentIndices: IntArray, outDepths: IntArray, n: Int) {
        outDepths.fill(UNRESOLVED, 0, n)

        val pending = IntArrayList()
        for (start in 0..<n) {
            if (outDepths[start] >= 0) continue

            var index = start
            while (outDepths[index] == UNRESOLVED) {
                outDepths[index] = IN_PROGRESS
                pending.add(index)
                index = outParentIndices[index]
            }
            check(outDepths[index] != IN_PROGRESS) { "the parents of ${Vertex(outVertexIds[index])} form a cycle" }

            var depth = outDepths[index]
            while (!pending.isEmpty()) outDepths[pending.removeLast()] = ++depth
        }
    }

    private companion object {
        private const val UNRESOLVED = -1
        private const val IN_PROGRESS = -2

        // maps a root's rank to its ref and back again (it is its own inverse)
        private fun rootRef(x: Int): Int = -2 - x
    }
}
