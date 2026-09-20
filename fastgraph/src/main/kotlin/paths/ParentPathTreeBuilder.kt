package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastcollect.IntArrayList
import io.github.sooniln.fastcollect.LongArrayList
import io.github.sooniln.fastgraph.CanonicalEdgeGraph
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.PathTree
import io.github.sooniln.fastgraph.PathTreeBuilder
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.internal.throwIllegalVertex


/**
 * Accumulates the parent pointer arrays that [AbstractParentPathTree] is built from. Each vertex is assigned an index
 * in the order it is first reached, with the source at index 0.
 */
internal class ParentPathTreeBuilder(private val graph: Graph, private val source: Vertex) : PathTreeBuilder {

    private val vertexIds = IntArrayList()
    private val parentIndices = IntArrayList()
    private val parentEdgeIds = LongArrayList()
    private val depths = IntArrayList()
    private val indices = Int2IntHashMap(defaultValue = -1)

    // Set once a setParent call rewires a vertex which was already present. While this is false, parentIndices[i] < i
    // holds for every i, which means no cycle can exist and the depths filled in on insert are already correct.
    private var reparented = false

    init {
        if (!graph.vertices.contains(source)) throwIllegalVertex(source)
        indices[source.id] = 0
        vertexIds.add(source.id)
        parentIndices.add(-1)
        parentEdgeIds.add(0)
        depths.add(0)
    }

    override fun setParent(parent: Vertex, edge: Edge, child: Vertex) {
        val parentIndex = indices[parent.id]
        if (parentIndex < 0) throwIllegalVertex(parent)
        require(parent != child) { "$child cannot be its own parent" }

        val childIndex = indices[child.id]
        if (childIndex < 0) {
            indices[child.id] = vertexIds.size
            vertexIds.add(child.id)
            parentIndices.add(parentIndex)
            parentEdgeIds.add(edge.id)
            // Stale if an earlier call rewired an ancestor, but then resolveDepths() recomputes every depth anyway.
            depths.add(depths[parentIndex] + 1)
        } else {
            require(childIndex != 0) { "$child is the source of this path tree and cannot be given a parent" }
            parentIndices[childIndex] = parentIndex
            parentEdgeIds[childIndex] = edge.id
            reparented = true
        }
    }

    fun build(): PathTree {
        if (reparented) resolveDepths()
        return if (graph is CanonicalEdgeGraph) {
            ParentCanonicalEdgePathTree(
                graph.directed,
                source,
                vertexIds.toArray(),
                parentIndices.toArray(),
                parentEdgeIds.toArray(),
                depths.toArray(),
                indices,
            )
        } else {
            ParentPathTree(
                source,
                vertexIds.toArray(),
                parentIndices.toArray(),
                parentEdgeIds.toArray(),
                depths.toArray(),
                indices,
            )
        }
    }

    /**
     * Recomputes every depth from the parent pointers, which index order alone can no longer supply once a vertex has
     * been rewired. Each index is walked up to the nearest ancestor with a known depth and the walked indices are then
     * filled back in on the way down, so the whole pass is linear. Encountering an index which is already on the
     * current walk means the parent pointers form a cycle.
     */
    private fun resolveDepths() {
        val size = vertexIds.size
        for (i in 1 until size) depths[i] = UNRESOLVED

        val pending = IntArrayList()
        for (start in 1 until size) {
            if (depths[start] >= 0) continue

            var index = start
            while (depths[index] == UNRESOLVED) {
                depths[index] = IN_PROGRESS
                pending.add(index)
                index = parentIndices[index]
            }
            check(depths[index] != IN_PROGRESS) {
                "the parents of ${Vertex(vertexIds[index])} form a cycle"
            }

            var depth = depths[index]
            while (!pending.isEmpty()) depths[pending.removeLast()] = ++depth
        }
    }

    private companion object {
        private const val UNRESOLVED = -1
        private const val IN_PROGRESS = -2
    }
}
