/**
 * Methods dealing with graph traversals.
 */
@file:JvmName("Traversals")

package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.*
import io.github.sooniln.fastgraph.util.VertexArrayDeque
import kotlin.collections.isNotEmpty

/**
 * Visits the graph in breadth-first order beginning from [initialVertices], invoking the given callbacks as the search
 * progresses.
 */
public inline fun Graph.visitBreadthFirst(
    initialVertices: VertexSet,
    onVertexDiscovered: (Vertex) -> Unit = {},
    onVertexExamined: (Vertex) -> Unit = {},
    onEdgeExamined: (Edge) -> Unit = {},
    onTreeEdge: (Edge) -> Unit = {},
    onVertexFinished: (Vertex) -> Unit = {},
) {
    require(initialVertices.isNotEmpty())

    val visited = createVertexProperty { false }
    val queue = VertexArrayDeque(initialVertices.size)

    for (vertex in initialVertices) {
        require(vertices.contains(vertex))
        queue.addLast(vertex)
        visited[vertex] = true
        onVertexDiscovered(vertex)
    }

    while (!queue.isEmpty()) {
        val vertex = queue.removeFirst()
        onVertexExamined(vertex)
        for (outgoingEdge in outgoingEdges(vertex)) {
            val target = edgeTarget(outgoingEdge, vertex)
            onEdgeExamined(outgoingEdge)
            if (!visited[target]) {
                onTreeEdge(outgoingEdge)
                queue.addLast(target)
                visited[target] = true
                onVertexDiscovered(target)
            }
        }
        onVertexFinished(vertex)
    }
}

/**
 * Visits the graph in depth-first order beginning from [initialVertices], invoking the given callbacks as the search
 * progresses. Note that for undirected graphs the edge back to a vertex's parent is reported as a back edge.
 */
public inline fun Graph.visitDepthFirst(
    initialVertices: VertexSet,
    onVertexStarted: (Vertex) -> Unit = {},
    onVertexDiscovered: (Vertex) -> Unit = {},
    onEdgeExamined: (Edge) -> Unit = {},
    onTreeEdge: (Edge) -> Unit = {},
    onBackEdge: (Edge) -> Unit = {},
    onVertexFinished: (Vertex) -> Unit = {},
) {
    require(initialVertices.isNotEmpty())

    // 0 = undiscovered, 1 = discovered (on the stack), 2 = finished
    val state = createVertexProperty<Byte> { 0 }
    val vertexStack = VertexArrayDeque(initialVertices.size)
    val edgeStack = ArrayList<EdgeIterator>()

    for (initialVertex in initialVertices) {
        require(vertices.contains(initialVertex))
        if (state[initialVertex].toInt() != 0) continue

        onVertexStarted(initialVertex)
        state[initialVertex] = 1
        onVertexDiscovered(initialVertex)
        vertexStack.addLast(initialVertex)
        edgeStack.add(outgoingEdges(initialVertex).iterator())

        while (!vertexStack.isEmpty()) {
            val vertex = vertexStack[vertexStack.size - 1]
            val edges = edgeStack[edgeStack.size - 1]
            if (edges.hasNext()) {
                val edge = edges.next()
                val target = edgeTarget(edge, vertex)
                onEdgeExamined(edge)
                val targetState = state[target].toInt()
                if (targetState == 0) {
                    onTreeEdge(edge)
                    state[target] = 1
                    onVertexDiscovered(target)
                    vertexStack.addLast(target)
                    edgeStack.add(outgoingEdges(target).iterator())
                } else if (targetState == 1) {
                    onBackEdge(edge)
                }
            } else {
                vertexStack.removeLast()
                edgeStack.removeAt(edgeStack.size - 1)
                state[vertex] = 2
                onVertexFinished(vertex)
            }
        }
    }
}

/**
 * Visits the graph in depth-first order, but only allows for pre-order information to be extracted from the callbacks.
 * This method is generally cheaper and faster than [visitDepthFirst], but loses some information and callbacks in
 * return.
 */
public inline fun Graph.visitDepthFirstPreOrder(
    initialVertices: VertexSet,
    onVertexDiscovered: (Vertex) -> Unit = {},
    onEdgeExamined: (Edge) -> Unit = {},
    onVertexFinished: (Vertex) -> Unit = {},
) {
    require(initialVertices.isNotEmpty())

    val visited = createVertexProperty { false }
    val stack = VertexArrayDeque(initialVertices.size)

    for (vertex in initialVertices) {
        require(vertices.contains(vertex))
        stack.addLast(vertex)
    }

    while (!stack.isEmpty()) {
        val vertex = stack.removeLast()
        visited[vertex] = true
        onVertexDiscovered(vertex)
        for (outgoingEdge in outgoingEdges(vertex)) {
            val target = edgeTarget(outgoingEdge, vertex)
            onEdgeExamined(outgoingEdge)
            if (!visited[target]) {
                stack.addLast(target)
            }
        }
        onVertexFinished(vertex)

        while (!stack.isEmpty() && visited[stack[stack.size - 1]]) {
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
    return depthFirstPreOrderVertexIterator(vertexSetOf(vertex))
}

/**
 * Returns an iterator over the vertices reachable from [initialVertices] in depth-first pre-order.
 */
public fun Graph.depthFirstPreOrderVertexIterator(initialVertices: VertexSet): VertexIterator {
    return DFPreOrderIterator(this, initialVertices)
}

/**
 * Returns an iterator over the vertices reachable from [vertex] in depth-first post-order.
 */
@JvmName("depthFirstPostOrderVertexIterator")
public fun Graph.depthFirstPostOrderVertexIterator(vertex: Vertex): VertexIterator {
    return depthFirstPostOrderVertexIterator(vertexSetOf(vertex))
}

/**
 * Returns an iterator over the vertices reachable from [initialVertices] in depth-first post-order.
 */
public fun Graph.depthFirstPostOrderVertexIterator(initialVertices: VertexSet): VertexIterator {
    return DFPostOrderIterator(this, initialVertices)
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
 * Returns an iterator over the tree edges of a depth-first search beginning from the given vertices, in pre-order.
 * Each step holds a tree edge and the vertex discovered through it; the initial vertices have no tree edge and are not
 * included.
 */
@JvmName("depthFirstTreeEdgeIterator")
public fun Graph.depthFirstTreeEdgeIterator(vertex: Vertex): Iterator<Step> {
    return depthFirstTreeEdgeIterator(vertexSetOf(vertex))
}

/**
 * Returns an iterator over the tree edges of a depth-first search beginning from the given vertices, in pre-order.
 * Each step holds a tree edge and the vertex discovered through it; the initial vertices have no tree edge and are not
 * included.
 */
public fun Graph.depthFirstTreeEdgeIterator(initialVertices: VertexSet): Iterator<Step> {
    return DFTreeEdgeIterator(this, initialVertices)
}

/**
 * Runs a breadth-first search from [source] and returns the resulting tree of shortest paths (by edge count). The
 * targets of the returned tree are every vertex reachable from [source], including [source] itself (whose path is the
 * trivial single-vertex path).
 */
@JvmName("breadthFirstPathTree")
public fun Graph.breadthFirstPathTree(source: Vertex): PathTree = buildPathTree(source) {
    // BFS examines a vertex before any of the vertices it discovers, so the vertex examined most recently is the
    // parent of the vertex being discovered.
    var parent = source
    var treeEdge = Edge(0)
    visitBreadthFirst(
        vertexSetOf(source),
        onVertexExamined = { parent = it },
        onTreeEdge = { treeEdge = it },
        onVertexDiscovered = { vertex ->
            if (vertex != source) setParent(parent, treeEdge, vertex)
        },
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

private class DFPreOrderIterator(private val graph: Graph, startVertices: VertexSet) : VertexIterator {

    private val visited = graph.createVertexProperty { false }
    private val queue = IntArrayDeque(startVertices.size)

    init {
        require(startVertices.isNotEmpty())
        for (vertex in startVertices) {
            require(graph.vertices.contains(vertex))
            queue.addLast(vertex.id)
        }
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

private class DFPostOrderIterator(private val graph: Graph, startVertices: VertexSet) : VertexIterator {

    // 0 = undiscovered, 1 = discovered (on the current path), 2 = finished
    private val state = graph.createVertexProperty<Byte> { 0 }
    private val queue = IntArrayDeque(startVertices.size)

    init {
        require(startVertices.isNotEmpty())
        for (vertex in startVertices) {
            require(graph.vertices.contains(vertex))
            queue.addLast(vertex.id)
        }
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

private class DFTreeEdgeIterator(private val graph: Graph, startVertices: VertexSet) : Iterator<Step> {

    private val visited = graph.createVertexProperty { false }
    private val roots = IntArrayDeque(startVertices.size)
    // Parallel stacks: an edge to a vertex that was unvisited at push time, and that vertex. After advance(), the top
    // entry (if any) is a tree edge to an unvisited vertex.
    private val edges = LongArrayDeque()
    private val targets = IntArrayDeque()

    init {
        require(startVertices.isNotEmpty())
        // like the breadth-first iterator, every initial vertex is a root: none is ever discovered through an edge
        for (vertex in startVertices) {
            require(graph.vertices.contains(vertex))
            roots.addLast(vertex.id)
            visited[vertex] = true
        }
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

    private fun advance() {
        while (true) {
            // a vertex may be pushed more than once before it is visited - later entries are stale.
            while (!edges.isEmpty() && visited[Vertex(targets.last())]) {
                edges.removeLast()
                targets.removeLast()
            }
            if (!edges.isEmpty() || roots.isEmpty()) return
            expand(Vertex(roots.removeFirst()))
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
