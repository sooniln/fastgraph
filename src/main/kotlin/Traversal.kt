package io.github.sooniln.fastgraph

import io.github.sooniln.fastcollect.ints.IntArrayDeque

public object Traversal {
    /**
     * Returns an iterable that will return vertices from the given graph in breadth-first iteration order, beginning
     * from the given vertex.
     */
    @JvmStatic
    @JvmName("breadthFirst")
    public fun breadthFirst(graph: Graph, initialVertex: Vertex): VertexIterable {
        return breadthFirst(graph, vertexSetOf(initialVertex))
    }

    /**
     * Returns an iterable that will return vertices from the given graph in breadth-first iteration order, beginning
     * from the given vertices.
     */
    @JvmStatic
    public fun breadthFirst(graph: Graph, initialVertices: VertexSet): VertexIterable {
        return object : VertexIterable {
            override fun iterator(): VertexIterator = BFIterator(graph, initialVertices)
        }
    }

    /**
     * Returns an iterable that will return vertices from the given graph in depth-first pre-order iteration order,
     * beginning from the given vertex.
     */
    @JvmStatic
    @JvmName("depthFirstPreOrder")
    public fun depthFirstPreOrder(graph: Graph, initialVertex: Vertex): VertexIterable {
        return depthFirstPreOrder(graph, vertexSetOf(initialVertex))
    }


    /**
     * Returns an iterable that will return vertices from the given graph in depth-first pre-order iteration order,
     * beginning from the given vertices.
     */
    @JvmStatic
    public fun depthFirstPreOrder(graph: Graph, initialVertices: VertexSet): VertexIterable {
        return object : VertexIterable {
            override fun iterator(): VertexIterator = DFPreOrderIterator(graph, initialVertices)
        }
    }

    // TODO: depthFirstPostOrder

    private class BFIterator(private val graph: Graph, startVertices: VertexSet) : VertexIterator {

        private val visited = graph.createVertexProperty { false }
        private val queue = IntArrayDeque(startVertices.size)

        init {
            require(startVertices.isNotEmpty())
            startVertices.foreach { vertex ->
                require(graph.vertices.contains(vertex))
                queue.addLast(vertex.id)
                visited[vertex] = true
            }
        }

        override fun hasNext(): Boolean = !queue.isEmpty()

        override fun next(): Vertex {
            val next = Vertex(queue.removeFirst())
            graph.successors(next).foreach { vertex ->
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
            startVertices.foreach { vertex ->
                require(graph.vertices.contains(vertex))
                queue.addLast(vertex.id)
            }
        }

        override fun next(): Vertex {
            val next = Vertex(queue.removeLast())
            visited[next] = true
            graph.successors(next).foreach { vertex ->
                if (!visited[vertex]) {
                    queue.addLast(vertex.id)
                }
            }

            drain()
            return next
        }

        override fun hasNext(): Boolean = !queue.isEmpty()

        private fun drain() {
            while (!queue.isEmpty() && visited[Vertex(queue.last())]) {
                queue.removeLast()
            }
        }
    }
}
