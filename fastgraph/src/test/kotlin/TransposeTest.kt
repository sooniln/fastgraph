package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TransposeTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    private fun constructGraph(directed: Boolean, immutable: Boolean) {
        graph = if (immutable) {
            buildImmutableGraph(directed) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            buildGraph(directed) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun undirectedTransposeIsSameGraph(immutable: Boolean) {
        constructGraph(false, immutable)

        assertThat(graph.transpose()).isSameAs(graph)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun directedTransposeReversesEdges(immutable: Boolean) {
        constructGraph(true, immutable)

        val transposed = graph.transpose()

        assertThat(transposed.directed).isTrue
        assertThat(transposed.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(transposed.edges).containsExactlyInAnyOrder(e0, e1)

        assertThat(transposed.edgeSource(e0)).isEqualTo(v1)
        assertThat(transposed.edgeTarget(e0)).isEqualTo(v0)
        assertThat(transposed.edgeSource(e1)).isEqualTo(v2)
        assertThat(transposed.edgeTarget(e1)).isEqualTo(v1)

        assertThat(transposed.hasEdge(v1, v0)).isTrue
        assertThat(transposed.hasEdge(v0, v1)).isFalse
        assertThat(transposed.hasEdge(v2, v1)).isTrue
        assertThat(transposed.hasEdge(v1, v2)).isFalse

        context(transposed) {
            assertThat(v0.outDegree).isEqualTo(0)
            assertThat(v0.inDegree).isEqualTo(1)
            assertThat(v1.outDegree).isEqualTo(1)
            assertThat(v1.inDegree).isEqualTo(1)
            assertThat(v1.successors()).containsExactlyInAnyOrder(v0)
            assertThat(v1.predecessors()).containsExactlyInAnyOrder(v2)
        }

        // the singular accessors must be reversed along with their plural counterparts
        assertThat(transposed.successor(v1)).isEqualTo(v0)
        assertThat(transposed.predecessor(v1)).isEqualTo(v2)
        assertThat(transposed.successor(v2)).isEqualTo(v1)
        assertThat(transposed.predecessor(v0)).isEqualTo(v1)
        assertThrows<IllegalStateException> { transposed.successor(v0) }
        assertThrows<IllegalStateException> { transposed.predecessor(v2) }

        assertThat(transposed.outgoingEdge(v1)).isEqualTo(e0)
        assertThat(transposed.incomingEdge(v1)).isEqualTo(e1)
        assertThat(transposed.outgoingEdge(v2)).isEqualTo(e1)
        assertThat(transposed.incomingEdge(v0)).isEqualTo(e0)
        assertThrows<IllegalStateException> { transposed.outgoingEdge(v0) }
        assertThrows<IllegalStateException> { transposed.incomingEdge(v2) }

        assertThat(transposed.edge(v1, v0)).isEqualTo(e0)
        assertThat(transposed.edge(v2, v1)).isEqualTo(e1)
        assertThrows<IllegalStateException> { transposed.edge(v0, v1) }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun transposeOfEmptyImmutableGraphIsSameGraph(directed: Boolean) {
        val empty = emptyImmutableGraph(directed)

        assertThat(empty.transpose()).isSameAs(empty)
    }
}
