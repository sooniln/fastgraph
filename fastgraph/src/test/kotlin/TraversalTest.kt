package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TraversalTest {

    private lateinit var graph: MutableGraph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var v4: Vertex = Vertex(-1)

    // v0 -> v1 -> v3, v0 -> v2 -> v3, v4 is disconnected
    private fun constructGraph(directed: Boolean) {
        graph = mutableGraph(directed)
        v0 = graph.addVertex()
        v1 = graph.addVertex()
        v2 = graph.addVertex()
        v3 = graph.addVertex()
        v4 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v0, v2)
        graph.addEdge(v1, v3)
        graph.addEdge(v2, v3)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstSingleVertex(directed: Boolean) {
        constructGraph(directed)

        val result = Traversal.breadthFirst(graph, v0).toList()

        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo(v0)
        assertThat(result.subList(1, 3)).containsExactlyInAnyOrder(v1, v2)
        assertThat(result[3]).isEqualTo(v3)
        assertThat(result).doesNotContain(v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstMultipleVertices(directed: Boolean) {
        constructGraph(directed)

        val result = Traversal.breadthFirst(graph, vertexSetOf(v0, v4)).toList()

        assertThat(result).hasSize(5)
        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstRequiresNonEmptyStart(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { Traversal.breadthFirst(graph, emptyVertexSet()).iterator() }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun breadthFirstRequiresVertexInGraph(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { Traversal.breadthFirst(graph, Vertex(99)).iterator() }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPreOrderSingleVertex(directed: Boolean) {
        constructGraph(directed)

        val result = Traversal.depthFirstPreOrder(graph, v0).toList()

        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo(v0)
        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(result).doesNotContain(v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPreOrderMultipleVertices(directed: Boolean) {
        constructGraph(directed)

        val result = Traversal.depthFirstPreOrder(graph, vertexSetOf(v0, v4)).toList()

        assertThat(result).hasSize(5)
        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPreOrderRequiresNonEmptyStart(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { Traversal.depthFirstPreOrder(graph, emptyVertexSet()).iterator() }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPreOrderRequiresVertexInGraph(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { Traversal.depthFirstPreOrder(graph, Vertex(99)).iterator() }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderSingleVertex(directed: Boolean) {
        constructGraph(directed)

        val result = Traversal.depthFirstPostOrder(graph, v0).toList()

        assertThat(result).hasSize(4)
        assertThat(result.last()).isEqualTo(v0)
        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(result).doesNotContain(v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderMultipleVertices(directed: Boolean) {
        constructGraph(directed)

        val result = Traversal.depthFirstPostOrder(graph, vertexSetOf(v0, v4)).toList()

        assertThat(result).hasSize(5)
        assertThat(result).containsExactlyInAnyOrder(v0, v1, v2, v3, v4)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderRequiresNonEmptyStart(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { Traversal.depthFirstPostOrder(graph, emptyVertexSet()).iterator() }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun depthFirstPostOrderRequiresVertexInGraph(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { Traversal.depthFirstPostOrder(graph, Vertex(99)).iterator() }
    }
}
