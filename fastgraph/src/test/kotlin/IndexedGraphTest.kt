package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IndexedGraphTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    private fun constructGraph(immutable: Boolean, indexEdges: Boolean) {
        graph = if (immutable) {
            buildImmutableGraph(true, indexEdges = indexEdges) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            buildGraph(true, indexEdges = indexEdges) {
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
    fun graphIsIndexedVertexGraph(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        assertThat(graph).isInstanceOf(IndexedVertexGraph::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertexSetGetAndIndexOf(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        val vertices = graph.vertices as IndexedVertexSet

        assertThat(vertices[0]).isEqualTo(v0)
        assertThat(vertices[1]).isEqualTo(v1)
        assertThat(vertices[2]).isEqualTo(v2)
        assertThat(vertices.indexOf(v0)).isEqualTo(0)
        assertThat(vertices.indexOf(v1)).isEqualTo(1)
        assertThat(vertices.indexOf(v2)).isEqualTo(2)

        assertThrows<IndexOutOfBoundsException> { vertices[3] }
        assertThrows<IndexOutOfBoundsException> { vertices[-1] }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphWithoutIndexEdgesIsNotIndexedEdgeGraph(immutable: Boolean) {
        constructGraph(immutable, indexEdges = false)

        assertThat(graph).isNotInstanceOf(IndexedEdgeGraph::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun graphWithIndexEdgesIsIndexedEdgeGraph(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        assertThat(graph).isInstanceOf(IndexedEdgeGraph::class.java)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeSetGetAndIndexOf(immutable: Boolean) {
        constructGraph(immutable, indexEdges = true)

        val edges = graph.edges as IndexedEdgeSet

        assertThat(edges[0]).isEqualTo(e0)
        assertThat(edges[1]).isEqualTo(e1)
        assertThat(edges.indexOf(e0)).isEqualTo(0)
        assertThat(edges.indexOf(e1)).isEqualTo(1)

        assertThrows<IndexOutOfBoundsException> { edges[2] }
        assertThrows<IndexOutOfBoundsException> { edges[-1] }
    }
}
