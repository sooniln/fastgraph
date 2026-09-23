/**
 * Methods dealing with graph traversals.
 */
@file:JvmName("Traversals")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.paths.PathForest
import io.github.sooniln.fastgraph.paths.SimpleStep
import io.github.sooniln.fastgraph.paths.Step
import io.github.sooniln.fastgraph.paths.buildPathForest
import io.github.sooniln.fastgraph.properties.MutableVertexProperty
import io.github.sooniln.fastgraph.util.VertexArrayDeque
import kotlin.collections.isNotEmpty

/**
 * The colors which the traversal visitors ([visitBreadthFirst], [visitDepthFirst] and [visitDepthFirstPreOrder])
 * record in their color property: every vertex starts out [WHITE], becomes [GRAY] when it is discovered and [BLACK]
 * once it is finished.
 */
public object VertexColors {
    /** The vertex has not been discovered. */
    public const val WHITE: Byte = 0

    /** The vertex has been discovered but not yet finished - it is in the queue/stack of the traversal. */
    public const val GRAY: Byte = 1

    /** The vertex has been finished. */
    public const val BLACK: Byte = 2
}

/**
 * Visits the graph in breadth-first order beginning from [initialVertices], invoking the given callbacks as the search
 * progresses.
 *
 * The [colors] property is used to guide the search by recording the current vertex coloring (see [VertexColors]) as
 * the search progresses. The search respects changes made to the [colors] property before/while the search is ongoing.
 * The property is left as is (and is not reset) when the search is complete, so it may be reused. Any [colors] value
 * which is not a valid value from [VertexColors] is treated as [VertexColors.BLACK].
 *
 * @param onVertexDiscovered invoked when a vertex is colored [VertexColors.GRAY] and added to the queue.
 * @param onLayerStarted invoked immediately before [onVertexExamined] for the first vertex in a search layer, with the
 * search depth (starting at 0 and counting up) and the layer size (number of vertices in this layer).
 * @param onVertexExamined invoked when a vertex is removed from the queue, before its outgoing edges are examined.
 * @param shouldExpand invoked immediately after [onVertexExamined] - returning `false` prunes the search tree at that
 * vertex.
 * @param onEdgeExamined invoked for each outgoing edge of an expanded vertex.
 * @param onTreeEdge invoked immediately after [onEdgeExamined] for an edge whose target is [VertexColors.WHITE].
 * @param onVertexFinished invoked once all outgoing edges of a vertex have been examined (or it has been pruned) and
 * the vertex has been colored [VertexColors.BLACK].
 * @param onLayerFinished invoked immediately after [onVertexFinished] for the last vertex in the search layer.
 */
public inline fun Graph.visitBreadthFirst(
    initialVertices: VertexSet,
    onVertexDiscovered: (Vertex) -> Unit = {},
    onLayerStarted: (depth: Int, size: Int) -> Unit = {_,_ -> },
    onVertexExamined: (Vertex) -> Unit = {},
    shouldExpand: (Vertex) -> Boolean = { true },
    onEdgeExamined: (Edge) -> Unit = {},
    onTreeEdge: (Edge) -> Unit = {},
    onVertexFinished: (Vertex) -> Unit = {},
    onLayerFinished: (depth: Int) -> Unit = {},
    colors: MutableVertexProperty<Byte> = createVertexProperty { VertexColors.WHITE },
) {
    require(initialVertices.isNotEmpty())

    val queue = VertexArrayDeque(initialVertices.size)

    for (vertex in initialVertices) {
        require(vertices.contains(vertex))
        if (colors[vertex] != VertexColors.WHITE) continue
        queue.addLast(vertex)
        colors[vertex] = VertexColors.GRAY
        onVertexDiscovered(vertex)
    }

    var layer = -1
    do {
        val layerSize = queue.size
        onLayerStarted(++layer, layerSize)
        for (i in 0..<layerSize) {
            val vertex = queue.removeFirst()
            onVertexExamined(vertex)
            if (shouldExpand(vertex)) {
                for (outgoingEdge in outgoingEdges(vertex)) {
                    onEdgeExamined(outgoingEdge)
                    val target = edgeTarget(outgoingEdge, vertex)
                    if (colors[target] == VertexColors.WHITE) {
                        onTreeEdge(outgoingEdge)
                        queue.addLast(target)
                        colors[target] = VertexColors.GRAY
                        onVertexDiscovered(target)
                    }
                }
            }
            colors[vertex] = VertexColors.BLACK
            onVertexFinished(vertex)
        }
        onLayerFinished(layer)
    } while (!queue.isEmpty())
}

/**
 * Visits the graph in depth-first order beginning from [initialVertex], invoking the given callbacks as the search
 * progresses. Note that for undirected graphs the edge back to a vertex's parent is reported as a back edge.
 *
 * The [colors] property is used to guide the search by recording the current vertex coloring (see [VertexColors]) as
 * the search progresses. The search respects mutations made to the [colors] property while the search is ongoing. The
 * property is left as is (and is not reset) when the search is complete, so it may be reused. Any [colors] value which
 * is not a valid value from [VertexColors] is treated as [VertexColors.BLACK].
 *
 * @param onVertexStarted invoked once for [initialVertex] before it is discovered.
 * @param onVertexDiscovered invoked when a vertex is colored [VertexColors.GRAY], before its outgoing edges are
 * examined (pre-order).
 * @param shouldExpand invoked immediately after [onVertexDiscovered] - returning `false` prunes the search at that
 * vertex.
 * @param onEdgeExamined invoked for each outgoing edge of an expanded vertex.
 * @param onTreeEdge invoked immediately after [onEdgeExamined] for an edge whose target is [VertexColors.WHITE].
 * @param onBackEdge invoked immediately after [onEdgeExamined] for an edge whose target is [VertexColors.GRAY].
 * @param onVertexFinished invoked once all outgoing edges of a vertex have been examined and all of its descendants
 * have been finished (post-order), and the vertex has been colored [VertexColors.BLACK].
 */
// TODO: make this more efficient?
@JvmName("visitDepthFirst")
public inline fun Graph.visitDepthFirst(
    initialVertex: Vertex,
    onVertexStarted: (Vertex) -> Unit = {},
    onVertexDiscovered: (Vertex) -> Unit = {},
    shouldExpand: (Vertex) -> Boolean = { true },
    onEdgeExamined: (Edge) -> Unit = {},
    onTreeEdge: (Edge) -> Unit = {},
    onBackEdge: (Edge) -> Unit = {},
    onVertexFinished: (Vertex) -> Unit = {},
    colors: MutableVertexProperty<Byte> = createVertexProperty { VertexColors.WHITE },
) {
    require(vertices.contains(initialVertex))
    if (colors[initialVertex] != VertexColors.WHITE) return

    val vertexStack = VertexArrayDeque()
    val edgeStack = ArrayList<EdgeIterator>()

    onVertexStarted(initialVertex)
    colors[initialVertex] = VertexColors.GRAY
    onVertexDiscovered(initialVertex)
    if (!shouldExpand(initialVertex)) {
        colors[initialVertex] = VertexColors.BLACK
        onVertexFinished(initialVertex)
        return
    }
    vertexStack.addLast(initialVertex)
    edgeStack.add(outgoingEdges(initialVertex).iterator())

    while (!vertexStack.isEmpty()) {
        val vertex = vertexStack[vertexStack.size - 1]
        val edges = edgeStack[edgeStack.size - 1]
        if (edges.hasNext()) {
            val edge = edges.next()
            onEdgeExamined(edge)
            val target = edgeTarget(edge, vertex)
            when (colors[target]) {
                VertexColors.WHITE -> {
                    onTreeEdge(edge)
                    colors[target] = VertexColors.GRAY
                    onVertexDiscovered(target)
                    if (shouldExpand(target)) {
                        vertexStack.addLast(target)
                        edgeStack.add(outgoingEdges(target).iterator())
                    } else {
                        colors[target] = VertexColors.BLACK
                        onVertexFinished(target)
                    }
                }

                VertexColors.GRAY -> {
                    onBackEdge(edge)
                }
            }
        } else {
            vertexStack.removeLast()
            edgeStack.removeLast()
            colors[vertex] = VertexColors.BLACK
            onVertexFinished(vertex)
        }
    }
}

/**
 * Visits the graph in depth-first order, but only allows for pre-order information to be extracted from the callbacks.
 * This method is generally cheaper and faster than [visitDepthFirst], but loses some information and callbacks in
 * return.
 *
 * The [colors] property is used to guide the search by recording the current vertex coloring (see [VertexColors]) as
 * the search progresses. The search respects mutations made to the [colors] property while the search is ongoing. The
 * property is left as is (and is not reset) when the search is complete, so it may be reused. Any [colors] value which
 * is not a valid value from [VertexColors] is treated as [VertexColors.BLACK].
 *
 * @param onVertexDiscovered invoked when a vertex is colored [VertexColors.GRAY], before its outgoing edges are
 * examined (pre-order).
 * @param shouldExpand invoked immediately after [onVertexDiscovered] - returning `false` prunes the search at that
 * vertex.
 * @param onEdgeExamined invoked for each outgoing edge of an expanded vertex.
 * @param onVertexFinished invoked once all outgoing edges of a vertex have been examined (or it has been pruned) and
 * the vertex has been colored [VertexColors.BLACK]. Unlike [visitDepthFirst] this is not post-order: it happens before
 * any of the vertex's descendants are discovered.
 */
@JvmName("visitDepthFirstPreOrder")
public inline fun Graph.visitDepthFirstPreOrder(
    initialVertex: Vertex,
    onVertexDiscovered: (Vertex) -> Unit = {},
    shouldExpand: (Vertex) -> Boolean = { true },
    onEdgeExamined: (Edge) -> Unit = {},
    onVertexFinished: (Vertex) -> Unit = {},
    colors: MutableVertexProperty<Byte> = createVertexProperty { VertexColors.WHITE },
) {
    require(vertices.contains(initialVertex))
    if (colors[initialVertex] != VertexColors.WHITE) return

    val stack = VertexArrayDeque()
    stack.addLast(initialVertex)

    while (!stack.isEmpty()) {
        val vertex = stack.removeLast()
        colors[vertex] = VertexColors.GRAY
        onVertexDiscovered(vertex)
        if (shouldExpand(vertex)) {
            for (outgoingEdge in outgoingEdges(vertex)) {
                onEdgeExamined(outgoingEdge)
                val target = edgeTarget(outgoingEdge, vertex)
                if (colors[target] == VertexColors.WHITE) {
                    stack.addLast(target)
                }
            }
        }
        colors[vertex] = VertexColors.BLACK
        onVertexFinished(vertex)

        // a vertex can be pushed multiple times before it is discovered - drain later entries
        while (!stack.isEmpty() && colors[stack.last()] != VertexColors.WHITE) {
            stack.removeLast()
        }
    }
}

/**
 * Returns an iterator over the vertices reachable from [vertex] in breadth-first order.
 */
@JvmName("breadthFirstVertexIterator")
public fun Graph.breadthFirstVertexIterator(vertex: Vertex): VertexIterator {
    return breadthFirstVertexIterator(vertexSetOf(vertex))
}

/**
 * Returns an iterator over the vertices reachable from [initialVertices] in breadth-first order.
 */
public fun Graph.breadthFirstVertexIterator(initialVertices: VertexSet): VertexIterator {
    return BFIterator(this, initialVertices)
}

/**
 * Returns an iterator over the vertices reachable from [vertex] in depth-first pre-order.
 */
@JvmName("depthFirstPreOrderVertexIterator")
public fun Graph.depthFirstPreOrderVertexIterator(vertex: Vertex): VertexIterator {
    return DFPreOrderIterator(this, vertex)
}

/**
 * Returns an iterator over the vertices reachable from [vertex] in depth-first post-order.
 */
@JvmName("depthFirstPostOrderVertexIterator")
public fun Graph.depthFirstPostOrderVertexIterator(vertex: Vertex): VertexIterator {
    return DFPostOrderIterator(this, vertex)
}

/**
 * Returns an iterator over the tree edges of a breadth-first search beginning from the given vertices. Each step holds
 * a tree edge and the vertex discovered through it; the initial vertices have no tree edge and are not included.
 */
@JvmName("breadthFirstTreeEdgeIterator")
public fun Graph.breadthFirstTreeEdgeIterator(vertex: Vertex): Iterator<Step> {
    return breadthFirstTreeEdgeIterator(vertexSetOf(vertex))
}

/**
 * Returns an iterator over the tree edges of a breadth-first search beginning from the given vertices. Each step holds
 * a tree edge and the vertex discovered through it; the initial vertices have no tree edge and are not included.
 */
public fun Graph.breadthFirstTreeEdgeIterator(initialVertices: VertexSet): Iterator<Step> {
    return BFTreeEdgeIterator(this, initialVertices)
}

/**
 * Returns an iterator over the tree edges of a depth-first search beginning from [vertex], in pre-order. Each step
 * holds a tree edge and the vertex discovered through it; [vertex] has no tree edge and is not included.
 */
@JvmName("depthFirstTreeEdgeIterator")
public fun Graph.depthFirstTreeEdgeIterator(vertex: Vertex): Iterator<Step> {
    return DFTreeEdgeIterator(this, vertex)
}

/**
 * Runs a breadth-first search from [vertex] and returns the resulting path tree (a forest with only a single root). The
 * tree includes every vertex reachable from [vertex], including the initial vertices themselves.
 */
@JvmName("breadthFirstPathForest")
public fun Graph.breadthFirstPathForest(vertex: Vertex): PathForest {
    return breadthFirstPathForest(vertexSetOf(vertex))
}

/**
 * Runs a breadth-first search from [initialVertices] and returns the resulting forest of shortest paths (by edge
 * count), in which every reachable vertex is placed under the nearest initial vertex. The forest includes every vertex
 * reachable from [initialVertices], including the initial vertices themselves.
 */
public fun Graph.breadthFirstPathForest(initialVertices: VertexSet): PathForest = buildPathForest {
    for (root in initialVertices) {
        addRoot(root)
    }
    var treeEdge = Edge(0)
    var viaTreeEdge = false
    visitBreadthFirst(
        initialVertices,
        onVertexDiscovered = { vertex ->
            if (viaTreeEdge) {
                setParentEdge(vertex, treeEdge)
                viaTreeEdge = false
            }
        },
        onTreeEdge = { treeEdge = it; viaTreeEdge = true },
    )
}

/**
 * Runs a depth-first search from [vertex] and returns the resulting path tree (a forest with only a single root). The
 * forest includes every vertex reachable from [vertex], including the initial vertex itself.
 */
@JvmName("depthFirstPathForest")
public fun Graph.depthFirstPathForest(vertex: Vertex): PathForest = buildPathForest {
    addRoot(vertex)
    var treeEdge = Edge(0)
    visitDepthFirst(
        vertex,
        onVertexDiscovered = { discovered ->
            if (discovered != vertex) setParentEdge(discovered, treeEdge)
        },
        onTreeEdge = { treeEdge = it },
    )
}

private class BFIterator(private val graph: Graph, startVertices: VertexSet) : VertexIterator {

    private val visited = graph.createVertexProperty { false }
    private val queue = IntArrayDeque(startVertices.size)

    init {
        require(startVertices.isNotEmpty())
        for (vertex in startVertices) {
            require(graph.vertices.contains(vertex))
            queue.addLast(vertex.id)
            visited[vertex] = true
        }
    }

    override fun hasNext(): Boolean = !queue.isEmpty()

    override fun next(): Vertex {
        val next = Vertex(queue.removeFirst())
        for (vertex in graph.successors(next)) {
            if (!visited[vertex]) {
                queue.addLast(vertex.id)
                visited[vertex] = true
            }
        }
        return next
    }
}

private class DFPreOrderIterator(private val graph: Graph, startVertex: Vertex) : VertexIterator {

    private val visited = graph.createVertexProperty { false }
    private val queue = IntArrayDeque()

    init {
        require(graph.vertices.contains(startVertex))
        queue.addLast(startVertex.id)
    }

    override fun hasNext(): Boolean = !queue.isEmpty()

    override fun next(): Vertex {
        val next = Vertex(queue.removeLast())
        visited[next] = true
        for (vertex in graph.successors(next)) {
            if (!visited[vertex]) {
                queue.addLast(vertex.id)
            }
        }

        drain()
        return next
    }

    private fun drain() {
        while (!queue.isEmpty() && visited[Vertex(queue.last())]) {
            queue.removeLast()
        }
    }
}

private class DFPostOrderIterator(private val graph: Graph, startVertex: Vertex) : VertexIterator {

    // 0 = undiscovered, 1 = discovered (on the current path), 2 = finished
    private val state = graph.createVertexProperty<Byte> { 0 }
    private val queue = IntArrayDeque()

    init {
        require(graph.vertices.contains(startVertex))
        queue.addLast(startVertex.id)
    }

    override fun hasNext(): Boolean = !queue.isEmpty()

    override fun next(): Vertex {
        if (queue.isEmpty()) throw NoSuchElementException()
        while (true) {
            val top = Vertex(queue.last())
            if (state[top].toInt() == 1) {
                queue.removeLast()
                state[top] = 2
                drain()
                return top
            }
            state[top] = 1
            for (vertex in graph.successors(top)) {
                if (state[vertex].toInt() == 0) {
                    queue.addLast(vertex.id)
                }
            }
        }
    }

    // A vertex may be pushed more than once before it is discovered; later entries are stale. Removing them here
    // keeps the top of the stack unfinished (or the stack empty) between calls.
    private fun drain() {
        while (!queue.isEmpty() && state[Vertex(queue.last())].toInt() == 2) {
            queue.removeLast()
        }
    }
}

private class BFTreeEdgeIterator(private val graph: Graph, startVertices: VertexSet) : Iterator<Step> {

    private val visited = graph.createVertexProperty { false }
    private val queue = IntArrayDeque(startVertices.size)
    private var source = Vertex(-1)
    private var edges: EdgeIterator = emptyEdgeSet().iterator()
    // The edge which discovered the vertex at the tail of the queue. Only valid when the queue is non-empty.
    private var pendingEdge = Edge(0)

    init {
        require(startVertices.isNotEmpty())
        for (vertex in startVertices) {
            require(graph.vertices.contains(vertex))
            queue.addLast(vertex.id)
            visited[vertex] = true
        }
        advance()
    }

    override fun hasNext(): Boolean = !queue.isEmpty()

    override fun next(): Step {
        if (queue.isEmpty()) throw NoSuchElementException()
        val step = SimpleStep(pendingEdge, Vertex(queue.last()))
        advance()
        return step
    }

    private fun advance() {
        while (true) {
            while (edges.hasNext()) {
                val edge = edges.next()
                val target = graph.edgeTarget(edge, source)
                if (!visited[target]) {
                    visited[target] = true
                    queue.addLast(target.id)
                    pendingEdge = edge
                    return
                }
            }
            if (queue.isEmpty()) return
            source = Vertex(queue.removeFirst())
            edges = graph.outgoingEdges(source).iterator()
        }
    }
}

private class DFTreeEdgeIterator(private val graph: Graph, startVertex: Vertex) : Iterator<Step> {

    private val visited = graph.createVertexProperty { false }
    // Parallel stacks: an edge to a vertex that was unvisited at push time, and that vertex. After advance(), the top
    // entry (if any) is a tree edge to an unvisited vertex.
    private val edges = LongArrayDeque()
    private val targets = IntArrayDeque()

    init {
        require(graph.vertices.contains(startVertex))
        visited[startVertex] = true
        expand(startVertex)
        advance()
    }

    override fun hasNext(): Boolean = !edges.isEmpty()

    override fun next(): Step {
        if (!hasNext()) throw NoSuchElementException()

        val edge = Edge(edges.removeLast())
        val target = Vertex(targets.removeLast())
        visited[target] = true
        expand(target)
        advance()
        return SimpleStep(edge, target)
    }

    // a vertex may be pushed more than once before it is visited - later entries are stale.
    private fun advance() {
        while (!edges.isEmpty() && visited[Vertex(targets.last())]) {
            edges.removeLast()
            targets.removeLast()
        }
    }

    private fun expand(vertex: Vertex) {
        for (edge in graph.outgoingEdges(vertex)) {
            val target = graph.edgeTarget(edge, vertex)
            if (!visited[target]) {
                edges.addLast(edge.id)
                targets.addLast(target.id)
            }
        }
    }
}
