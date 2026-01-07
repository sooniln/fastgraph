package io.github.sooniln.fastgraph

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue
import it.unimi.dsi.fastutil.ints.IntArrayList

object Traversal {
    @JvmStatic
    fun breadthFirst(graph: Graph, initialVertex: Vertex): VertexIterable {
        return breadthFirst(graph, vertexSetOf(initialVertex))
    }

    @JvmStatic
    fun breadthFirst(graph: Graph, initialVertices: VertexSet): VertexIterable {
        return object : VertexIterable {
            override fun iterator(): VertexIterator = BFIterator(graph, initialVertices)
        }
    }

    @JvmStatic
    fun depthFirstPreOrder(graph: Graph, initialVertex: Vertex): VertexIterable {
        return depthFirstPreOrder(graph, vertexSetOf(initialVertex))
    }

    @JvmStatic
    fun depthFirstPreOrder(graph: Graph, initialVertices: VertexSet): VertexIterable {
        return object : VertexIterable {
            override fun iterator(): VertexIterator = DFPreOrderIterator(graph, initialVertices)
        }
    }

    // TODO: depthFirstPostOrder

    private class BFIterator(private val graph: Graph, startVertices: VertexSet) : VertexIterator {

        private val visited = graph.createVertexProperty { false }
        private val queue = IntArrayFIFOQueue(startVertices.size)

        init {
            require(startVertices.isNotEmpty())
            for (vertex in startVertices) {
                require(graph.vertices.contains(vertex))
                queue.enqueue(vertex.intValue)
                visited[vertex] = true
            }
        }

        override fun hasNext(): Boolean = !queue.isEmpty

        override fun next(): Vertex {
            val next = Vertex(queue.dequeueInt())
            for (vertex in graph.successors(next)) {
                if (!visited[vertex]) {
                    queue.enqueue(vertex.intValue)
                    visited[vertex] = true
                }
            }
            return next
        }
    }

    private class DFPreOrderIterator(private val graph: Graph, startVertices: VertexSet) : VertexIterator {

        private val visited = graph.createVertexProperty { false }
        private val queue = IntArrayList(startVertices.size)

        init {
            require(startVertices.isNotEmpty())
            for (vertex in startVertices) {
                require(graph.vertices.contains(vertex))
                queue.add(vertex.intValue)
            }
        }

        override fun next(): Vertex {
            val next = Vertex(queue.removeLast())
            visited[next] = true
            for (vertex in graph.successors(next)) {
                if (!visited[vertex]) {
                    queue.add(vertex.intValue)
                }
            }

            drain()
            return next
        }

        override fun hasNext(): Boolean = !queue.isEmpty

        private fun drain() {
            var size = queue.size
            var trash = Vertex(if (size <= 0) 0 else queue.getInt(size - 1))
            while (size > 0 && visited[trash]) {
                queue.removeInt(--size)
                trash = Vertex(if (size <= 0) 0 else queue.getInt(size - 1))
            }
        }
    }
}
