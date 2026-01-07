package io.github.sooniln.fastgraph

import com.google.common.graph.Traverser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TraversalTest {

    @Test
    fun breadthFirst() {
        val a: Vertex
        val b: Vertex
        val c: Vertex
        val d: Vertex
        val e: Vertex
        val f: Vertex
        val graph = immutableGraph(false).build {
            a = addVertex()
            b = addVertex()
            c = addVertex()
            d = addVertex()
            e = addVertex()
            f = addVertex()
            addEdge(a, b)
            addEdge(b, e)
            addEdge(e, c)
            addEdge(a, c)
            addEdge(a, d)
            addEdge(c, f)
        }.graph

        assertThat(Traversal.breadthFirst(graph, a))
            .containsExactlyElementsOf(Traverser<Vertex>.forGraph(graph::successors).breadthFirst(a))
        assertThat(Traversal.breadthFirst(graph, b))
            .containsExactlyElementsOf(Traverser<Vertex>.forGraph(graph::successors).breadthFirst(b))
        assertThat(Traversal.breadthFirst(graph, c))
            .containsExactlyElementsOf(Traverser<Vertex>.forGraph(graph::successors).breadthFirst(c))
        assertThat(Traversal.breadthFirst(graph, d))
            .containsExactlyElementsOf(Traverser<Vertex>.forGraph(graph::successors).breadthFirst(d))
        assertThat(Traversal.breadthFirst(graph, e))
            .containsExactlyElementsOf(Traverser<Vertex>.forGraph(graph::successors).breadthFirst(e))
        assertThat(Traversal.breadthFirst(graph, f))
            .containsExactlyElementsOf(Traverser<Vertex>.forGraph(graph::successors).breadthFirst(f))
    }
}
