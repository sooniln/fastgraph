/**
 * Methods dealing with walks, trails and paths.
 */
@file:JvmName("Paths")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.Int2IntHashMap
import io.github.sooniln.fastgraph.internal.throwIllegalEdge
import io.github.sooniln.fastgraph.internal.throwIllegalVertex


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
public interface PathTree : ImmutableGraph {
    override val directed: Boolean get() = true

    @get:JvmName("getStartVertex")
    public val startVertex: Vertex

    /**
     * Vertices are ordered in the order of discovery when this PathTree was created (i.e. `vertices[0]` will always be
     * [startVertex]).
     */
    override val vertices: VertexSequencedSet

    /**
     * Edges are ordered in the order of discovery when this PathTree was created.
     */
    override val edges: EdgeSequencedSet

    /** Returns the number of edges in the path from [source] to [endVertex]. */
    @JvmName("getPathLength")
    public fun getPathLength(endVertex: Vertex): Int

    /** Creates a new [Path] representing the path from [startVertex] to [endVertex]. */
    @JvmName("materializePath")
    public fun materializePath(endVertex: Vertex): Path
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
 * An ordered sequence of vertices and edges, where vertices may be repeated but edges may not be repeated. Note that
 * for efficiency reasons this constraint is generally not runtime-enforced and the client is expected to enforce this
 * themselves.
 */
public interface Trail : Walk {
    override val edges: EdgeSequencedSet
}

/**
 * An ordered sequence of vertices and edges, where neither vertices nor edges may be repeated. Note that for
 * efficiency reasons this constraint is generally not runtime-enforced and the client is expected to enforce this
 * themselves.
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
