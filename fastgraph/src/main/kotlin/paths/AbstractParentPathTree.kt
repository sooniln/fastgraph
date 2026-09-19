package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastgraph.AbstractEdgeSequencedSet
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSequencedSet
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Path
import io.github.sooniln.fastgraph.PathTree
import io.github.sooniln.fastgraph.SimplePath
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.VertexSequencedSet
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.emptyVertexSet
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.vertexSetOf


/**
 * A path tree snapshot which holds no reference to the graph it was built from. Each reached vertex is assigned an
 * index (the source is index 0) and the tree is stored as a parent pointer per index; paths are reconstructed on
 * demand by walking back to the source.
 *
 * Subclasses only need to resolve an [Edge] back to the index of the vertex it was discovered through, via
 * [childIndex].
 */
internal abstract class AbstractParentPathTree(
    override val startVertex: Vertex,
    protected val vertexIds: IntArray,
    // The parent's index and the tree edge from the parent, per index. Neither is meaningful for index 0.
    protected val parentIndices: IntArray,
    protected val parentEdgeIds: LongArray,
    protected val depths: IntArray,
    // vertex id -> index, or < 0 if the vertex was not reached
    protected val vertexIndices: Int2IntHashMap,
) : PathTree {

    init {
        require(vertexIds.size == parentEdgeIds.size)
        require(parentEdgeIds.size == parentIndices.size)
        require(parentIndices.size == depths.size)
        require(depths.size == vertexIndices.size)
    }

    override val multiEdge: Boolean get() = false

    /** Index of the vertex whose tree edge (to its parent) is [edge], or -1 if [edge] is not in this tree. */
    protected abstract fun childIndex(edge: Edge): Int

    private class Children(val offsets: IntArray, val indices: IntArray)

    private val children: Children by lazy {
        val n = vertexIds.size
        val offsets = IntArray(n + 1)
        for (i in 1 until n) offsets[parentIndices[i] + 1]++
        for (i in 1..n) offsets[i] += offsets[i - 1]
        val next = offsets.copyOf(n)
        val indices = IntArray(n - 1)
        for (i in 1 until n) indices[next[parentIndices[i]]++] = i
        return@lazy Children(offsets, indices)
    }

    private fun childCount(index: Int): Int = children.offsets[index + 1] - children.offsets[index]

    override val vertices: VertexSequencedSet = object : VertexSequencedSet {
        override val size: Int get() = vertexIds.size
        override fun get(index: Int): Vertex = Vertex(vertexIds[index])
        override fun contains(element: Vertex): Boolean = vertexIndices.containsKey(element.id)
    }

    override fun outDegree(vertex: Vertex): Int {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (index == 0) 0 else 1
    }

    override fun inDegree(vertex: Vertex): Int {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return childCount(index)
    }

    override fun successors(vertex: Vertex): VertexSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (index == 0) emptyVertexSet() else vertexSetOf(Vertex(vertexIds[parentIndices[index]]))
    }

    override fun successor(vertex: Vertex): Vertex {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (index == 0) throw IllegalStateException() else Vertex(vertexIds[parentIndices[index]])
    }

    override fun predecessors(vertex: Vertex): VertexSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return ChildVertexSet(index)
    }

    override fun outgoingEdges(vertex: Vertex): EdgeSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (index == 0) emptyEdgeSet() else edgeSetOf(Edge(parentEdgeIds[index]))
    }

    override fun outgoingEdge(vertex: Vertex): Edge {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (index == 0) throw IllegalStateException() else Edge(parentEdgeIds[index])
    }

    override fun incomingEdges(vertex: Vertex): EdgeSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return ChildEdgeSet(index)
    }

    override val edges: EdgeSequencedSet = object : EdgeSequencedSet, AbstractEdgeSequencedSet() {
        override val size: Int get() = parentEdgeIds.size - 1
        override fun get(index: Int): Edge = Edge(parentEdgeIds[index + 1])
        override fun contains(element: Edge): Boolean = childIndex(element) >= 0
    }

    override fun edgeSource(edge: Edge): Vertex {
        val index = childIndex(edge)
        if (index < 0) throwIllegalEdge(edge)
        return Vertex(vertexIds[index])
    }

    override fun edgeTarget(edge: Edge): Vertex {
        val index = childIndex(edge)
        if (index < 0) throwIllegalEdge(edge)
        return Vertex(vertexIds[parentIndices[index]])
    }

    override fun hasEdge(
        source: Vertex,
        target: Vertex
    ): Boolean {
        val sourceIndex = vertexIndices[source.id]
        if (sourceIndex < 0) throwIllegalVertex(source)
        val targetIndex = vertexIndices[target.id]
        if (targetIndex < 0) throwIllegalVertex(target)
        return parentIndices[sourceIndex] == targetIndex
    }

    override fun edges(
        source: Vertex,
        target: Vertex
    ): EdgeSet {
        val sourceIndex = vertexIndices[source.id]
        if (sourceIndex < 0) throwIllegalVertex(source)
        val targetIndex = vertexIndices[target.id]
        if (targetIndex < 0) throwIllegalVertex(target)
        return if (parentIndices[sourceIndex] == targetIndex) {
            edgeSetOf(Edge(parentEdgeIds[sourceIndex]))
        } else {
            emptyEdgeSet()
        }
    }

    override fun getPathLength(endVertex: Vertex): Int {
        val index = vertexIndices[endVertex.id]
        if (index < 0) throwIllegalVertex(endVertex)
        return depths[index]
    }

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
        pathVertexIds[0] = startVertex.id
        return SimplePath(pathVertexIds, pathEdgeIds)
    }

    private inner class ChildVertexSet(private val parentIndex: Int) : AbstractVertexSet() {
        override val size: Int get() = childCount(parentIndex)

        // answered from the parent array so this doesn't force the lazy child index
        override fun contains(element: Vertex): Boolean {
            val index = vertexIndices[element.id]
            return index > 0 && parentIndices[index] == parentIndex
        }

        override fun iterator(): VertexIterator = object : VertexIterator {
            private var i = children.offsets[parentIndex]
            private val end = children.offsets[parentIndex + 1]
            override fun hasNext(): Boolean = i < end
            override fun next(): Vertex {
                if (!hasNext()) throw NoSuchElementException()
                return Vertex(vertexIds[children.indices[i++]])
            }
        }
    }

    private inner class ChildEdgeSet(private val parentIndex: Int) : AbstractEdgeSet() {
        override val size: Int get() = childCount(parentIndex)

        override fun contains(element: Edge): Boolean {
            val index = childIndex(element)
            return index >= 0 && parentIndices[index] == parentIndex
        }

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private var i = children.offsets[parentIndex]
            private val end = children.offsets[parentIndex + 1]
            override fun hasNext(): Boolean = i < end
            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                return Edge(parentEdgeIds[children.indices[i++]])
            }
        }
    }
}
