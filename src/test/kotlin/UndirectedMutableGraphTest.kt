package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UndirectedMutableGraphTest {

    private val graph = mutableGraph(false)
    private val vertexProperty = graph.createVertexProperty<String>()
    private val edgeProperty = graph.createEdgeProperty<String>()

    @Test
    fun mutateVertex() {
        context(graph) {
            assertThat(graph.vertices).isEmpty()

            val v1 = graph.addVertex().createReference()
            vertexProperty[v1] = "v1"
            assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable)

            val v2 = graph.addVertex().createReference()
            vertexProperty[v2] = "v2"
            assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable, v2.unstable)

            graph.removeVertex(v1)
            assertThat(graph.vertices).containsExactlyInAnyOrder(v2.unstable)
            assertThat(vertexProperty[v2]).isEqualTo("v2")

            assertThrows<IllegalArgumentException> { graph.removeVertex(v1) }
            assertThrows<IllegalArgumentException> { v1.unstable }
            assertThrows<IllegalArgumentException> { vertexProperty[v1] }

            graph.removeVertex(v2)
            assertThat(graph.vertices).isEmpty()

            assertThrows<IllegalArgumentException> { v2.unstable }
            assertThrows<IllegalArgumentException> { vertexProperty[v2] }
        }
    }

    @Test
    fun removeVerticesWithIterator() {
        context(graph) {
            val v1 = graph.addVertex().createReference()
            vertexProperty[v1] = "v1"
            val v2 = graph.addVertex().createReference()
            vertexProperty[v2] = "v2"
            val v3 = graph.addVertex().createReference()
            vertexProperty[v3] = "v3"

            val e1 = graph.addEdge(v1, v2).createReference()
            edgeProperty[e1] = "e1"
            val e2 = graph.addEdge(v2, v3).createReference()
            edgeProperty[e2] = "e2"
            val e3 = graph.addEdge(v2, v2).createReference()
            edgeProperty[e3] = "e3"
            val e4 = graph.addEdge(v1, v3).createReference()
            edgeProperty[e4] = "e4"

            val it = graph.vertices.iterator()

            assertThat(it.next()).isEqualTo(v1.unstable)
            it.remove()
            assertThat(graph.vertices).containsExactlyInAnyOrder(v2.unstable, v3.unstable)
            assertThat(graph.edges).containsExactlyInAnyOrder(e2.unstable, e3.unstable)

            assertThat(it.next()).isEqualTo(v3.unstable)
            it.remove()
            assertThat(graph.vertices).containsExactlyInAnyOrder(v2.unstable)
            assertThat(graph.edges).containsExactlyInAnyOrder(e3.unstable)

            assertThat(it.next()).isEqualTo(v2.unstable)
            it.remove()
            assertThat(graph.vertices).isEmpty()
            assertThat(graph.edges).isEmpty()
        }
    }

    @Test
    fun mutateVertexWithEdges() {
        context(graph) {
            val v1 = graph.addVertex().createReference()
            vertexProperty[v1] = "v1"
            val v2 = graph.addVertex().createReference()
            vertexProperty[v2] = "v2"
            val v3 = graph.addVertex().createReference()
            vertexProperty[v3] = "v3"

            val e1 = graph.addEdge(v1, v2).createReference()
            edgeProperty[e1] = "e1"
            val e2 = graph.addEdge(v2, v3).createReference()
            edgeProperty[e2] = "e2"
            val e3 = graph.addEdge(v2, v2).createReference()
            edgeProperty[e3] = "e3"
            val e4 = graph.addEdge(v1, v3).createReference()
            edgeProperty[e4] = "e4"

            graph.removeVertex(v2)
            assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable, v3.unstable)
            assertThat(graph.edges).containsExactlyInAnyOrder(e4.unstable)
            assertThat(vertexProperty[v1]).isEqualTo("v1")
            assertThat(vertexProperty[v3]).isEqualTo("v3")
            assertThat(edgeProperty[e4]).isEqualTo("e4")

            assertThrows<IllegalArgumentException> { graph.removeVertex(v2) }
            assertThrows<IllegalArgumentException> { v2.unstable }
            assertThrows<IllegalArgumentException> { vertexProperty[v2] }
            assertThrows<IllegalArgumentException> { graph.removeEdge(e1) }
            assertThrows<IllegalArgumentException> { graph.removeEdge(e2) }
            assertThrows<IllegalArgumentException> { graph.removeEdge(e3) }
            assertThrows<IllegalArgumentException> { e1.unstable }
            assertThrows<IllegalArgumentException> { e2.unstable }
            assertThrows<IllegalArgumentException> { e3.unstable }
            assertThrows<IllegalArgumentException> { edgeProperty[e1] }
            assertThrows<IllegalArgumentException> { edgeProperty[e2] }
            assertThrows<IllegalArgumentException> { edgeProperty[e3] }

            graph.removeVertex(v3)
            assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable)
            assertThat(graph.edges).isEmpty()
            assertThat(vertexProperty[v1]).isEqualTo("v1")

            assertThrows<IllegalArgumentException> { graph.removeVertex(v3) }
            assertThrows<IllegalArgumentException> { v3.unstable }
            assertThrows<IllegalArgumentException> { vertexProperty[v3] }
            assertThrows<IllegalArgumentException> { graph.removeEdge(e4) }
            assertThrows<IllegalArgumentException> { e4.unstable }
            assertThrows<IllegalArgumentException> { edgeProperty[e4] }
        }
    }

    @Test
    fun removeEdgesWithIterator() {
        context(graph) {
            val v1 = graph.addVertex().createReference()
            vertexProperty[v1] = "v1"
            val v2 = graph.addVertex().createReference()
            vertexProperty[v2] = "v2"
            val v3 = graph.addVertex().createReference()
            vertexProperty[v3] = "v3"

            val e1 = graph.addEdge(v1, v2).createReference()
            edgeProperty[e1] = "e1"
            val e2 = graph.addEdge(v2, v3).createReference()
            edgeProperty[e2] = "e2"
            val e3 = graph.addEdge(v2, v2).createReference()
            edgeProperty[e3] = "e3"
            val e4 = graph.addEdge(v1, v3).createReference()
            edgeProperty[e4] = "e4"

            val it = graph.edges.iterator()

            assertThat(it.next()).isEqualTo(e4.unstable)
            it.remove()
            assertThat(graph.edges).containsExactlyInAnyOrder(e1.unstable, e2.unstable, e3.unstable)

            assertThat(it.next()).isEqualTo(e1.unstable)
            it.remove()
            assertThat(graph.edges).containsExactlyInAnyOrder(e2.unstable, e3.unstable)

            assertThat(it.next()).isEqualTo(e2.unstable)
            it.remove()
            assertThat(graph.edges).containsExactlyInAnyOrder(e3.unstable)

            assertThat(it.next()).isEqualTo(e3.unstable)
            it.remove()
            assertThat(graph.edges).isEmpty()
        }
    }

    @Test
    fun mutateEdge() {
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        assertThat(graph.edges).isEmpty()

        val e1 = graph.addEdge(v1, v2)
        assertThat(graph.edges).containsExactlyInAnyOrder(e1)
        assertThat(graph.edgeSource(e1)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e1)).isEqualTo(v2)

        val e2 = graph.addEdge(v1, v1)
        assertThat(graph.edges).containsExactlyInAnyOrder(e1, e2)
        assertThat(graph.edgeSource(e2)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e2)).isEqualTo(v1)

        graph.removeEdge(e1)
        assertThat(graph.edges).containsExactlyInAnyOrder(e2)

        graph.removeEdge(e2)
        assertThat(graph.edges).isEmpty()
    }
}
