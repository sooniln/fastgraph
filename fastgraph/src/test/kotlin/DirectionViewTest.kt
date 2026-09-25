package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DirectionViewTest {

    // multiEdge=false produces a canonical edge graph, multiEdge=true produces an opaque edge graph
    @ParameterizedTest(name = "multiEdge={0}")
    @ValueSource(booleans = [true, false])
    fun asDirectedSelfLoopCountsOnceEachWay(multiEdge: Boolean) {
        val graph = mutableGraph(directed = false, multiEdge = multiEdge)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)
        graph.addEdge(a, a)

        // the undirected self-loop counts twice towards degree
        assertThat(graph.outDegree(a)).isEqualTo(3)

        // the undirected self-loop maps to a single directed self-loop
        val directed = graph.asDirected(graph.edgeIdProperty).source
        assertThat(directed.edges).hasSize(3)
        assertThat(directed.outDegree(a)).isEqualTo(2)
        assertThat(directed.inDegree(a)).isEqualTo(2)
        assertThat(directed.outgoingEdges(a)).hasSize(2)
        assertThat(directed.incomingEdges(a)).hasSize(2)
        assertThat(directed.outDegree(b)).isEqualTo(1)
        assertThat(directed.inDegree(b)).isEqualTo(1)
    }

    @Test
    fun asUndirectedSelfLoopCountsTwice() {
        val graph = mutableGraph(directed = true)
        val a = graph.addVertex()
        val b = graph.addVertex()
        graph.addEdge(a, b)
        graph.addEdge(b, a)
        graph.addEdge(a, a)

        val undirected = graph.asUndirected()
        assertThat(undirected.outDegree(a)).isEqualTo(4)
        assertThat(undirected.inDegree(a)).isEqualTo(4)
        assertThat(undirected.outgoingEdges(a)).hasSize(3)
        assertThat(undirected.outDegree(b)).isEqualTo(2)
        assertThat(undirected.vertices.sumOf { undirected.outDegree(it) }).isEqualTo(2 * undirected.edges.size)
    }
}
