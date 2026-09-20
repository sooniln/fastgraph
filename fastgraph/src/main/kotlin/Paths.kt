/**
 * Methods dealing with walks, trails and paths.
 */
@file:JvmName("Paths")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex
import io.github.sooniln.fastgraph.paths.ParentPathTreeBuilder


/**
 * Represents a set of paths from the same start vertex to different end vertices. These also form a directed graph,
 * where every edge in the graph points to the previous vertex in the path. Every vertex thus has a single outgoing
 * edge, except for [startVertex] which has no outgoing edges. The PathTree also holds information about the lengths of
 * each path, which can be queried without needing to walk the path.
 *
 * To create a new [Path] for a specific end vertex, use [materializePath], which walks the full path in reverse (and
 * thus takes linear time with respect to the path length) and assembles a [Path] object.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface PathTree : ImmutableGraph, IndexedVertexGraph, IndexedEdgeGraph {
    override val directed: Boolean get() = true

    @get:JvmName("getStartVertex")
    public val startVertex: Vertex

    /**
     * Vertices are indexed in the order of discovery when this PathTree was created (i.e. `vertices[0]` will always be
     * [startVertex]).
     */
    override val vertices: IndexedVertexSet

    /**
     * Edges are indexed in the order of discovery when this PathTree was created.
     */
    override val edges: IndexedEdgeSet

    /** A property for the number of edges in a path from [startVertex] to the given vertex. */
    public val pathLengthProperty: VertexProperty<Int>

    /** Creates a new [Path] representing the path from [startVertex] to [endVertex]. */
    @JvmName("materializePath")
    public fun materializePath(endVertex: Vertex): Path
}

/**
 * Builds up a [PathTree] one vertex at a time, so that any algorithm which discovers paths by walking edges can
 * produce one. The tree is rooted at the source given to [buildPathTree] and grows outwards from it; a vertex becomes
 * part of the tree as soon as it is given a parent.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface PathTreeBuilder {

    /**
     * Records that [child] is reached from [parent] through [edge], adding [child] to the tree if it is not already
     * part of it. [parent] must already be part of the tree.
     *
     * Calling this again for a [child] which is already in the tree replaces the parent it was previously given, which
     * lets algorithms that only settle on a path once the search has progressed (such as Dijkstra's algorithm or A*)
     * report path improvements.
     */
    @JvmName("setParent")
    public fun setParent(parent: Vertex, edge: Edge, child: Vertex)
}

/**
 * Builds a [PathTree] rooted at [source], whose paths are described by the calls the given [builder] makes to
 * [PathTreeBuilder.setParent]. Throws [IllegalStateException] if a cycle is formed by the paths.
 */
@JvmName("buildPathTree")
public fun Graph.buildPathTree(source: Vertex, builder: PathTreeBuilder.() -> Unit): PathTree {
    val pathTreeBuilder = ParentPathTreeBuilder(this, source)
    pathTreeBuilder.builder()
    return pathTreeBuilder.build()
}

/**
 * An ordered sequence of vertices and edges, where vertices and edges may be repeated.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface Walk {
    public val vertices: VertexSequencedCollection
    public val edges: EdgeSequencedCollection

    public operator fun iterator(): Iterator<Step> = object : Iterator<Step> {
        private var index = 0
        override fun hasNext(): Boolean = index < edges.size
        override fun next(): Step = SimpleStep(edges[index], vertices[++index])
    }
}

public val Walk.startVertex: Vertex get() = vertices.first()
public val Walk.endVertex: Vertex get() = vertices.last()

public fun Walk.isClosed(): Boolean = startVertex == endVertex
public fun Walk.isOpen(): Boolean = startVertex != endVertex

/**
 * An ordered sequence of vertices and edges, where vertices may be repeated but edges may not be repeated.
 */
public interface Trail : Walk {
    override val edges: EdgeSequencedSet
}

/**
 * An ordered sequence of vertices and edges, where neither vertices nor edges may be repeated.
 */
public interface Path : Trail {
    override val vertices: VertexSequencedSet
}

/** A single step along a walk. */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface Step {
    /** The edge taken on this step. */
    @get:JvmName("getEdge")
    public val edge: Edge
    /** The vertex destination of this step. */
    @get:JvmName("getVertex")
    public val vertex: Vertex
}

internal data class SimpleStep(override val edge: Edge, override val vertex: Vertex) : Step

internal class SimplePath(private val vertexIds: IntArray, private val edgeIds: LongArray) : Path {
    init {
        check(vertexIds.isNotEmpty())
        check(edgeIds.size == vertexIds.size - 1)
    }

    override val vertices: VertexSequencedSet
        get() = vertexIds.asSequencedVertexSet()
    override val edges: EdgeSequencedSet
        get() = edgeIds.asSequencedEdgeSet()
}
