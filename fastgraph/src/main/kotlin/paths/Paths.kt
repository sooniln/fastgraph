/**
 * Methods dealing with walks, trails and paths.
 */
@file:JvmName("Paths")

package io.github.sooniln.fastgraph.paths

import io.github.sooniln.fastgraph.AbstractVertexSequencedSet
import io.github.sooniln.fastgraph.CanonicalEdge
import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.EdgeSequencedCollection
import io.github.sooniln.fastgraph.EdgeSequencedSet
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.VertexSequencedCollection
import io.github.sooniln.fastgraph.VertexSequencedSet
import io.github.sooniln.fastgraph.VertexSet
import io.github.sooniln.fastgraph.asSequencedEdgeSet
import io.github.sooniln.fastgraph.asSequencedVertexSet
import io.github.sooniln.fastgraph.properties.VertexProperty

/**
 * Represents a set of paths from one or more root vertices, where every vertex and edge belongs to exactly one path.
 * This forms a directed graph, where every edge in the graph points to the previous vertex in the path. Every vertex
 * thus has a single outgoing edge, except for the [roots] which have no outgoing edges. The PathForest also holds
 * information about the lengths of each path (in terms of # of edges), which can be queried without needing to walk the
 * path. A PathForest may be empty. A path forest with a single root is known as a path tree.
 *
 * To create a new [Path] for a specific end vertex, use [materializePath], which walks the full path in reverse (and
 * thus takes linear time with respect to the path length) and assembles a [Path] object.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface PathForest : Graph {
    override val directed: Boolean get() = true

    /** The roots of the paths represented in this forest. */
    public val roots: VertexSet

    /** A property for the number of edges in the path from the root of the given vertex to it. */
    public val pathLengthProperty: VertexProperty<Int>

    /**
     * Creates a new [Path] representing the path from the root of [endVertex] to [endVertex]. Note that this implies
     * that the path travels in the OPPOSITE direction of the edges in this graph (edges point from [endVertex] towards
     * its root, the path travels from the root to the [endVertex]).
     *
     * If this forest re-uses vertex/edge ids from the original graph (as [buildPathForest] does for example) and the
     * original graph is directed, then the path will travel in the edge direction within the original graph (the
     * opposite of the edge direction in this graph).
     */
    @JvmName("materializePath")
    public fun materializePath(endVertex: Vertex): Path
}

/**
 * Builds up a [PathForest] one vertex at a time, so that any algorithm which discovers paths by walking edges can
 * produce one. The forest grows outwards from its roots; a vertex becomes part of the forest as soon as it is added
 * as a root or given a parent. Roots may be added at any point during the build.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
public interface PathForestBuilder {

    /**
     * Adds [root] to the forest as the root of a new tree. It must not already be part of the forest. If it is possible
     * to add all roots first (before any child is added via [setParentEdge]), this may result in slightly better
     * performance than adding a new root after some children have already been added to the builder (this is not
     * expected to be possible for every algorithm that builds a path forest).
     */
    @JvmName("addRoot")
    public fun addRoot(root: Vertex)

    /**
     * Records that [child] is reached through [edge] from the vertex at its other end (the parent), adding [child] to
     * the forest if it is not already part of it. [edge] must lead to [child] (i.e. [child] must be its target if the
     * graph is directed), the parent must already be part of the forest, and [child] must not be a root.
     *
     * Calling this again for a [child] which is already in the forest replaces the parent it was previously given,
     * which lets algorithms that only settle on a path once the search has progressed (such as Dijkstra's algorithm or
     * A*) report path improvements. If this is invoked to re-parent a child, this necessitates an extra pass at the end
     * of building to detect/prevent cycles.
     */
    @JvmName("setParentEdge")
    public fun setParentEdge(child: Vertex, edge: Edge)
}

/**
 * Builds a [PathForest] whose roots and paths are described by the calls the given [builder]. Throws
 * [IllegalStateException] if a cycle is formed by the paths.
 *
 * This returns a [Graph] which uses vertices/edges with the same ids as in the parent graph (so that it is trivial to
 * link a vertex/edge in the [PathForest] to the equivalent vertex/edge in the parent graph). Caution is needed when
 * handling edges in this manner however, this does NOT mean that the edge target/source in the forest is the same as
 * the edge target/source in the parent graph. If the parent graph is directed, then edge direction is always reversed
 * in the resulting forest (since the tree edge points to the parent vertex, not the child vertex). If the parent graph
 * is undirected, a forest edge is still always directed (pointing at the parent). Be careful not to mistake the
 * source/target of an edge in the forest for the source/target of the edge with the same id in the parent graph (see
 * [PathForest.materializePath] for example).
 */
public fun Graph.buildPathForest(builder: PathForestBuilder.() -> Unit): PathForest {
    val pathForestBuilder = ParentPathForestBuilder(this)
    pathForestBuilder.builder()
    return pathForestBuilder.build()
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
    /** The destination of this step. */
    @get:JvmName("getTarget")
    public val target: Vertex
}

internal data class SimpleStep(override val edge: Edge, override val target: Vertex) : Step

internal data class CanonicalEdgeStep(private val canonicalEdge: CanonicalEdge) : Step {
    override val edge: Edge get() = canonicalEdge.toEdge()
    override val target: Vertex get() = canonicalEdge.target
}

internal class CanonicalEdgePath(private val root: Vertex, private val edgeIds: LongArray) : Path {

    override val vertices: VertexSequencedSet = object : AbstractVertexSequencedSet() {
        override val size: Int get() = edgeIds.size + 1
        override fun get(index: Int): Vertex = if (index == 0) root else CanonicalEdge(edgeIds[index - 1]).target
    }

    override val edges: EdgeSequencedSet get() = edgeIds.asSequencedEdgeSet()

    override fun iterator(): Iterator<Step> {
        return object : Iterator<Step> {
            private var index = 0
            override fun hasNext(): Boolean = index < edgeIds.size
            override fun next(): Step = CanonicalEdgeStep(CanonicalEdge(edgeIds[index++]))
        }
    }
}

internal class SimplePath(private val vertexIds: IntArray, private val edgeIds: LongArray) : Path {
    init {
        check(vertexIds.isNotEmpty())
        check(edgeIds.size == vertexIds.size - 1)
    }

    override val vertices: VertexSequencedSet get() = vertexIds.asSequencedVertexSet()
    override val edges: EdgeSequencedSet get() = edgeIds.asSequencedEdgeSet()
}
