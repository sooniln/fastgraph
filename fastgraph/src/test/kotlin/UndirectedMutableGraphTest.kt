package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UndirectedMutableGraphTest {

    private val graph = mutableGraph(false)
    private val vertexProperty = graph.createVertexProperty<String>()
    private val edgeProperty = graph.createEdgeProperty<String>()

    @Test
    fun mutateVertex() {
        assertThat(graph.vertices).isEmpty()

        val v1 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v1] = "v1"
        assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable)

        val v2 = graph.createVertexReference(graph.addVertex())
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

    @Test
    fun removeVerticesWithIterator() {
        val v1 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v1] = "v1"
        val v2 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v2] = "v2"
        val v3 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v3] = "v3"

        val e1 = graph.createEdgeReference(graph.addEdge(v1, v2))
        edgeProperty[e1] = "e1"
        val e2 = graph.createEdgeReference(graph.addEdge(v2, v3))
        edgeProperty[e2] = "e2"
        val e3 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e3] = "e3"
        val e4 = graph.createEdgeReference(graph.addEdge(v1, v3))
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

    @Test
    fun mutateVertexWithEdges() {
        val v1 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v1] = "v1"
        val v2 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v2] = "v2"
        val v3 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v3] = "v3"

        val e1 = graph.createEdgeReference(graph.addEdge(v1, v2))
        edgeProperty[e1] = "e1"
        val e2 = graph.createEdgeReference(graph.addEdge(v2, v3))
        edgeProperty[e2] = "e2"
        val e3 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e3] = "e3"
        val e4 = graph.createEdgeReference(graph.addEdge(v1, v3))
        edgeProperty[e4] = "e4"
        val e5 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e5] = "e5"

        graph.removeVertex(v2)
        assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable, v3.unstable)
        assertThat(graph.edges).containsExactlyInAnyOrder(e4.unstable, e5.unstable)
        assertThat(vertexProperty[v1]).isEqualTo("v1")
        assertThat(vertexProperty[v3]).isEqualTo("v3")
        assertThat(edgeProperty[e4]).isEqualTo("e4")
        assertThat(edgeProperty[e5]).isEqualTo("e5")
        assertThat(graph.hasEdge(v1.unstable, v3.unstable)).isTrue
        assertThat(graph.hasEdge(v3.unstable, v3.unstable)).isTrue

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
        assertThrows<IllegalArgumentException> { graph.removeEdge(e5) }
        assertThrows<IllegalArgumentException> { e4.unstable }
        assertThrows<IllegalArgumentException> { e5.unstable }
        assertThrows<IllegalArgumentException> { edgeProperty[e4] }
        assertThrows<IllegalArgumentException> { edgeProperty[e5] }
    }

    @Test
    fun removeEdgesWithIterator() {
        val v1 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v1] = "v1"
        val v2 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v2] = "v2"
        val v3 = graph.createVertexReference(graph.addVertex())
        vertexProperty[v3] = "v3"

        val e1 = graph.createEdgeReference(graph.addEdge(v1, v2))
        edgeProperty[e1] = "e1"
        val e2 = graph.createEdgeReference(graph.addEdge(v2, v3))
        edgeProperty[e2] = "e2"
        val e3 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e3] = "e3"
        val e4 = graph.createEdgeReference(graph.addEdge(v1, v3))
        edgeProperty[e4] = "e4"

        val remaining = mutableSetOf(e1.unstable, e2.unstable, e3.unstable, e4.unstable)
        val it = graph.edges.iterator()

        while (it.hasNext()) {
            val edge = it.next()
            assertThat(remaining.remove(edge)).isTrue
            it.remove()
            assertThat(graph.edges).containsExactlyInAnyOrderElementsOf(remaining)
        }

        assertThat(remaining).isEmpty()
        assertThat(graph.edges).isEmpty()
    }

    @Test
    fun mutateEdge() {
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        assertThat(graph.edges).isEmpty()

        val e1 = graph.addEdge(v1, v2)
        assertThat(graph.edges).containsExactlyInAnyOrder(e1)
        // an undirected edge does not promise which endpoint is the source
        assertThat(setOf(graph.edgeSource(e1), graph.edgeTarget(e1))).containsExactlyInAnyOrder(v1, v2)

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
