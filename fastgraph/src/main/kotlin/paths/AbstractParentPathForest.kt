package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastgraph.AbstractEdgeSequencedSet
import io.github.sooniln.fastgraph.AbstractEdgeSet
import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.AbstractVertexSet
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeIterator
import io.github.sooniln.fastgraph.EdgeSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.IndexedEdgeSet
import io.github.sooniln.fastgraph.IndexedVertexSet
import io.github.sooniln.fastgraph.InternalImmutableGraph
import io.github.sooniln.fastgraph.properties.PropertyType
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexIterator
import io.github.sooniln.fastgraph.properties.VertexProperty
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.edgeSetOf
import io.github.sooniln.fastgraph.emptyEdgeSet
import io.github.sooniln.fastgraph.emptyVertexSet
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.properties.propertyTypeOf
import io.github.sooniln.fastgraph.util.cheapLazy
import io.github.sooniln.fastgraph.vertexSetOf


/**
 * A path forest snapshot which holds no reference to the graph it was built from. Each reached vertex is assigned an
 * index, with the roots occupying the last [rootCount] indices, and the forest is stored as a parent pointer per
 * index, with roots marked by a parent index of -1; paths are reconstructed on demand by walking back to the root.
 *
 * Subclasses only need to resolve an [Edge] back to the index of the vertex it was discovered through, via
 * [childIndex].
 */
internal abstract class AbstractParentPathForest(
    protected val vertexIds: IntArray,
    // for each index/vertex, the parent's index
    protected val parentIndices: IntArray,
    // for each index/vertex, the tree edge from the parent
    protected val parentEdgeIds: LongArray,
    // for each index/vertex, the number of edges take to reach it from its root
    protected val depths: IntArray,
    // the number of roots, which occupy the indices from nonRootCount onwards
    private val rootCount: Int,
    // vertex id -> index
    protected val vertexIndices: Int2IntHashMap,
) : PathForest, InternalImmutableGraph {

    init {
        require(vertexIds.size == parentEdgeIds.size)
        require(parentEdgeIds.size == parentIndices.size)
        require(parentIndices.size == depths.size)
        require(depths.size == vertexIndices.size)
        require(rootCount in 0..vertexIds.size)
    }

    // the non-roots occupy indices 0 until nonRootCount
    protected val nonRootCount: Int = vertexIds.size - rootCount

    override val multiEdge: Boolean get() = false

    /** Index of the vertex whose tree edge (to its parent) is [edge], or -1 if [edge] is not in this tree. */
    protected abstract fun childIndex(edge: Edge): Int

    private val children: Children by cheapLazy {
        val n = vertexIds.size
        val offsets = IntArray(n + 1)
        for (i in 0 until nonRootCount) {
            offsets[parentIndices[i] + 1]++
        }
        for (i in 1..n) offsets[i] += offsets[i - 1]
        val next = offsets.copyOf(n)
        val indices = IntArray(nonRootCount)
        for (i in 0 until nonRootCount) {
            indices[next[parentIndices[i]]++] = i
        }
        return@cheapLazy Children(offsets, indices)
    }

    private fun childCount(index: Int): Int = children.offsets[index + 1] - children.offsets[index]

    override val roots: VertexSet = object : AbstractVertexSet() {
        override val size: Int get() = rootCount

        override fun contains(element: Vertex): Boolean = vertexIndices[element.id] >= nonRootCount

        override fun iterator(): VertexIterator = object : VertexIterator {
            private var i = nonRootCount
            override fun hasNext(): Boolean = i < vertexIds.size
            override fun next(): Vertex {
                if (!hasNext()) throw NoSuchElementException()
                return Vertex(vertexIds[i++])
            }
        }
    }

    override val vertices: IndexedVertexSet = object : IndexedVertexSet, AbstractVertexSequencedSet() {
        override val size: Int get() = vertexIds.size
        override fun get(index: Int): Vertex {
            if (index !in 0..<size) throw IndexOutOfBoundsException()
            return Vertex(vertexIds[index])
        }
        override fun contains(element: Vertex): Boolean = vertexIndices.containsKey(element.id)
        override fun indexOf(element: Vertex): Int = vertexIndices[element.id]
    }

    override fun outDegree(vertex: Vertex): Int {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (parentIndices[index] < 0) 0 else 1
    }

    override fun inDegree(vertex: Vertex): Int {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return childCount(index)
    }

    override fun successorsCount(vertex: Vertex): Int = outDegree(vertex)

    override fun successors(vertex: Vertex): VertexSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (parentIndices[index] < 0) emptyVertexSet() else vertexSetOf(Vertex(vertexIds[parentIndices[index]]))
    }

    override fun successor(vertex: Vertex): Vertex {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (parentIndices[index] < 0) throw IllegalStateException() else Vertex(vertexIds[parentIndices[index]])
    }

    override fun predecessorsCount(vertex: Vertex): Int = inDegree(vertex)

    override fun predecessors(vertex: Vertex): VertexSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return ChildVertexSet(index)
    }

    override fun outgoingEdgeCount(vertex: Vertex): Int = outDegree(vertex)

    override fun outgoingEdges(vertex: Vertex): EdgeSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (parentIndices[index] < 0) emptyEdgeSet() else edgeSetOf(Edge(parentEdgeIds[index]))
    }

    override fun outgoingEdge(vertex: Vertex): Edge {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return if (parentIndices[index] < 0) throw IllegalStateException() else Edge(parentEdgeIds[index])
    }
    override fun incomingEdgeCount(vertex: Vertex): Int = inDegree(vertex)

    override fun incomingEdges(vertex: Vertex): EdgeSet {
        val index = vertexIndices[vertex.id]
        if (index < 0) throwIllegalVertex(vertex)
        return ChildEdgeSet(index)
    }

    // the tree edges are the entries of parentEdgeIds for the non-roots, so edge index = vertex index
    // subclasses supply this so that a forest over canonical edge ids can additionally declare [CanonicalEdgeSet]
    abstract override val edges: IndexedEdgeSet

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

    override fun edgesCount(source: Vertex, target: Vertex): Int = if (hasEdge(source, target)) 1 else 0

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

    override val pathLengthProperty: VertexProperty<Int> = object : VertexProperty<Int> {
        override val graph: Graph get() = this@AbstractParentPathForest
        override val type: PropertyType<Int> get() = propertyTypeOf()
        override fun get(vertex: Vertex): Int {
            val index = vertexIndices[vertex.id]
            if (index < 0) throwIllegalVertex(vertex)
            return depths[index]
        }
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
        pathVertexIds[0] = vertexIds[i]
        return SimplePath(pathVertexIds, pathEdgeIds)
    }

    private class Children(val offsets: IntArray, val indices: IntArray)

    private inner class ChildVertexSet(private val parentIndex: Int) : AbstractVertexSet() {
        override val size: Int get() = childCount(parentIndex)

        // answered from the parent array so this doesn't force the lazy child index
        override fun contains(element: Vertex): Boolean {
            val index = vertexIndices[element.id]
            return index >= 0 && parentIndices[index] == parentIndex
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

    protected open inner class TreeEdgeSet : IndexedEdgeSet, AbstractEdgeSequencedSet() {
        override val size: Int get() = nonRootCount

        override fun get(index: Int): Edge {
            if (index !in 0..<size) throw IndexOutOfBoundsException()
            return Edge(parentEdgeIds[index])
        }

        override fun contains(element: Edge): Boolean = childIndex(element) >= 0

        override fun indexOf(element: Edge): Int = childIndex(element)

        override fun iterator(): EdgeIterator = object : EdgeIterator {
            private var i = 0
            override fun hasNext(): Boolean = i < nonRootCount
            override fun next(): Edge {
                if (!hasNext()) throw NoSuchElementException()
                return Edge(parentEdgeIds[i++])
            }
        }
    }
}
