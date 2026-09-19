package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PathTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)
    private var e2: Edge = Edge(-1)

    // v0 -> v1 -> v2, v0 -> v3
    private fun constructGraph() {
        graph = buildGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            v3 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
            e2 = addEdge(v0, v3)
        }
    }

    @Test
    fun pathVerticesAndEdgesAreOrdered() {
        constructGraph()

        val tree = graph.breadthFirstPathTree(v0)
        val path = tree.materializePath(v2)

        assertThat(path.vertices).containsExactly(v0, v1, v2)
        assertThat(path.edges).containsExactly(e0, e1)
        assertThat(path.vertices[1]).isEqualTo(v1)
        assertThat(path.edges[1]).isEqualTo(e1)
        assertThat(path.vertices.indexOf(v2)).isEqualTo(2)
        assertThat(path.edges.indexOf(e1)).isEqualTo(1)
        assertThat(path.startVertex).isEqualTo(v0)
        assertThat(path.endVertex).isEqualTo(v2)
        assertThat(path.vertices.reversed()).containsExactly(v2, v1, v0)
        assertThat(tree.getPathLength(v2)).isEqualTo(2)
    }

    @Test
    fun pathVerticesHaveSetEquality() {
        constructGraph()

        val path = graph.breadthFirstPathTree(v0).materializePath(v2)

        val vertices = vertexSetOf(v2, v0, v1)
        assertThat(path.vertices).isEqualTo(vertices)
        assertThat(vertices).isEqualTo(path.vertices)
        assertThat(path.vertices.hashCode()).isEqualTo(vertices.hashCode())

        val edges = edgeSetOf(e1, e0)
        assertThat(path.edges).isEqualTo(edges)
        assertThat(edges).isEqualTo(path.edges)
        assertThat(path.edges.hashCode()).isEqualTo(edges.hashCode())
    }

    @Test
    fun trivialPath() {
        constructGraph()

        val tree = graph.breadthFirstPathTree(v0)
        val path = tree.materializePath(v0)

        assertThat(path.vertices).containsExactly(v0)
        assertThat(path.edges).isEmpty()
        assertThat(path.startVertex).isEqualTo(v0)
        assertThat(path.endVertex).isEqualTo(v0)
        assertThat(path.isClosed()).isTrue()
        assertThrows<NoSuchElementException> { path.edges.first() }
        assertThat(tree.getPathLength(v0)).isEqualTo(0)
    }

    @Test
    fun unreachableTarget() {
        constructGraph()

        val tree = graph.breadthFirstPathTree(v3)
        assertThat(tree.vertices).containsExactly(v3)
        assertThrows<IllegalArgumentException> { tree.materializePath(v0) }
        assertThrows<IllegalArgumentException> { tree.getPathLength(v0) }
    }
}
