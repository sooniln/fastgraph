package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DirectedMutableNetworkTest {

    private val graph = mutableGraph(true, multiEdge = true)
    private val vertexProperty = graph.createVertexProperty<String>()
    private val edgeProperty = graph.createEdgeProperty<String>()

    @Test
    fun multiEdge() {
        assertThat(graph.multiEdge).isTrue
    }

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
        val e2 = graph.createEdgeReference(graph.addEdge(v2, v1))
        edgeProperty[e2] = "e2"
        val e3 = graph.createEdgeReference(graph.addEdge(v2, v3))
        edgeProperty[e3] = "e3"
        val e4 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e4] = "e4"
        val e5 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e5] = "e5"
        val e6 = graph.createEdgeReference(graph.addEdge(v1, v3))
        edgeProperty[e6] = "e6"
        val e7 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e7] = "e7"
        val e8 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e8] = "e8"

        val it = graph.vertices.iterator()

        assertThat(it.next()).isEqualTo(v1.unstable)
        it.remove()
        assertThat(graph.vertices).containsExactlyInAnyOrder(v2.unstable, v3.unstable)
        assertThat(graph.edges).containsExactlyInAnyOrder(
            e3.unstable,
            e4.unstable,
            e5.unstable,
            e7.unstable,
            e8.unstable
        )

        assertThat(it.next()).isEqualTo(v3.unstable)
        it.remove()
        assertThat(graph.vertices).containsExactlyInAnyOrder(v2.unstable)
        assertThat(graph.edges).containsExactlyInAnyOrder(e4.unstable, e5.unstable)

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
        val e2 = graph.createEdgeReference(graph.addEdge(v2, v1))
        edgeProperty[e2] = "e2"
        val e3 = graph.createEdgeReference(graph.addEdge(v2, v3))
        edgeProperty[e3] = "e3"
        val e4 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e4] = "e4"
        val e5 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e5] = "e5"
        val e6 = graph.createEdgeReference(graph.addEdge(v1, v3))
        edgeProperty[e6] = "e6"
        val e7 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e7] = "e7"
        val e8 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e8] = "e8"

        graph.removeVertex(v2)
        assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable, v3.unstable)
        assertThat(graph.edges).containsExactlyInAnyOrder(e6.unstable, e7.unstable, e8.unstable)
        assertThat(vertexProperty[v1]).isEqualTo("v1")
        assertThat(vertexProperty[v3]).isEqualTo("v3")
        assertThat(edgeProperty[e6]).isEqualTo("e6")
        assertThat(edgeProperty[e7]).isEqualTo("e7")
        assertThat(edgeProperty[e8]).isEqualTo("e8")

        assertThrows<IllegalArgumentException> { graph.removeVertex(v2) }
        assertThrows<IllegalArgumentException> { v2.unstable }
        assertThrows<IllegalArgumentException> { vertexProperty[v2] }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e1) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e2) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e3) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e4) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e5) }
        assertThrows<IllegalArgumentException> { e1.unstable }
        assertThrows<IllegalArgumentException> { e2.unstable }
        assertThrows<IllegalArgumentException> { e3.unstable }
        assertThrows<IllegalArgumentException> { e4.unstable }
        assertThrows<IllegalArgumentException> { e5.unstable }
        assertThrows<IllegalArgumentException> { edgeProperty[e1] }
        assertThrows<IllegalArgumentException> { edgeProperty[e2] }
        assertThrows<IllegalArgumentException> { edgeProperty[e3] }
        assertThrows<IllegalArgumentException> { edgeProperty[e4] }
        assertThrows<IllegalArgumentException> { edgeProperty[e5] }

        graph.removeVertex(v3)
        assertThat(graph.vertices).containsExactlyInAnyOrder(v1.unstable)
        assertThat(graph.edges).isEmpty()
        assertThat(vertexProperty[v1]).isEqualTo("v1")

        assertThrows<IllegalArgumentException> { graph.removeVertex(v3) }
        assertThrows<IllegalArgumentException> { v3.unstable }
        assertThrows<IllegalArgumentException> { vertexProperty[v3] }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e5) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e6) }
        assertThrows<IllegalArgumentException> { graph.removeEdge(e7) }
        assertThrows<IllegalArgumentException> { e5.unstable }
        assertThrows<IllegalArgumentException> { e6.unstable }
        assertThrows<IllegalArgumentException> { e7.unstable }
        assertThrows<IllegalArgumentException> { edgeProperty[e5] }
        assertThrows<IllegalArgumentException> { edgeProperty[e6] }
        assertThrows<IllegalArgumentException> { edgeProperty[e7] }
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
        val e2 = graph.createEdgeReference(graph.addEdge(v2, v1))
        edgeProperty[e2] = "e2"
        val e3 = graph.createEdgeReference(graph.addEdge(v2, v3))
        edgeProperty[e3] = "e3"
        val e4 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e4] = "e4"
        val e5 = graph.createEdgeReference(graph.addEdge(v2, v2))
        edgeProperty[e5] = "e5"
        val e6 = graph.createEdgeReference(graph.addEdge(v1, v3))
        edgeProperty[e6] = "e6"
        val e7 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e7] = "e7"
        val e8 = graph.createEdgeReference(graph.addEdge(v3, v3))
        edgeProperty[e8] = "e8"

        // which edge the iterator yields after a removal is an implementation detail (indices are kept
        // contiguous by moving some edge), so only check that every edge is removed exactly once
        val remaining = mutableSetOf(e1, e2, e3, e4, e5, e6, e7, e8)
        val iterator = graph.edges.iterator()

        while (iterator.hasNext()) {
            val edge = iterator.next()
            val reference = remaining.single { it.unstable == edge }
            iterator.remove()
            assertThat(remaining.remove(reference)).isTrue
            assertThat(graph.edges).containsExactlyInAnyOrderElementsOf(remaining.map { it.unstable })
        }

        assertThat(remaining).isEmpty()
        assertThat(graph.edges).isEmpty()
    }

    @Test
    fun mutateEdge() {
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        assertThat(graph.edges).isEmpty()

        val e1 = graph.createEdgeReference(graph.addEdge(v1, v2))
        assertThat(graph.edges).containsExactlyInAnyOrder(e1.unstable)
        assertThat(graph.edgeSource(e1)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e1)).isEqualTo(v2)

        val e2 = graph.createEdgeReference(graph.addEdge(v1, v2))
        assertThat(graph.edges).containsExactlyInAnyOrder(e1.unstable, e2.unstable)
        assertThat(graph.edgeSource(e2)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e2)).isEqualTo(v2)

        graph.removeEdge(e1)
        assertThat(graph.edges).containsExactlyInAnyOrder(e2.unstable)

        graph.removeEdge(e2)
        assertThat(graph.edges).isEmpty()
    }
}
