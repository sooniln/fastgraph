package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SubgraphTest {

    private class RecordingVertexListener : VertexChangeListener {
        val removed = mutableListOf<Vertex>()
        override fun onVertexAdded(vertex: Vertex) {}
        override fun onVertexRemoved(vertex: Vertex) {
            removed.add(vertex)
        }

        override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {}
    }

    private class NoOpEdgeListener : EdgeChangeListener {
        override fun onEdgeAdded(edge: Edge) {}
        override fun onEdgeRemoved(edge: Edge) {}
        override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {}
    }

    @Test
    fun inducingSubgraphContainsOnlyInducedVerticesAndEdges() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val v3 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)
        graph.addEdge(v0, v3)

        val subgraph = graph.subgraph(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1, e2))

        assertThat(subgraph.directed).isEqualTo(graph.directed)
        assertThat(subgraph.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(subgraph.edges).containsExactlyInAnyOrder(e0, e1, e2)
        // v3 is not part of the induced subgraph, so it is treated like any other foreign vertex
        assertThrows<IllegalArgumentException> { subgraph.hasEdge(v0, v3) }

        context(subgraph) {
            assertThat(v0.outDegree).isEqualTo(1)
            assertThat(v0.successors()).containsExactlyInAnyOrder(v1)
            assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0)
        }
    }

    @Test
    fun inducingSubgraphExcludesEdgeNotInInducingSetEvenWhenBothEndpointsIncluded() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)

        val subgraph = graph.subgraph(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1))

        assertThat(subgraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(subgraph.hasEdge(v2, v0)).isFalse
    }

    @Test
    fun inducingSubgraphStaysInSyncWithParentVertexRemoval() {
        val graph = Graphs.mutableGraph(true)
        graph.addVertex() // v3-equivalent placeholder, added first so it is not the highest-indexed vertex
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val subgraph = graph.subgraph(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1, e2))

        // v2 is the highest-indexed vertex, so removing it is a plain removal (no id swap to disturb v0/v1)
        graph.removeVertex(v2)

        assertThat(subgraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(subgraph.edges).containsExactlyInAnyOrder(e0)
    }

    @Test
    fun inducingSubgraphSupportsListenersAndReferences() {
        val graph = Graphs.mutableGraph(true)
        graph.addVertex()
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)

        val subgraph = graph.subgraph(vertexSetOf(v0, v1, v2), edgeSetOf<Edge>())
        val listener = RecordingVertexListener()
        subgraph.registerVertexChangeListener(listener)

        graph.removeVertex(v2)

        assertThat(listener.removed).containsExactly(v2)

        val ref = subgraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)
    }

    @Test
    fun inducingVertexWithEdgeFilterSubgraphIsEvaluatedDynamicallyAndOnlySupportsVertexListenersReferences() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val allowedEdges = mutableSetOf(e0, e1)
        val subgraph = graph.subgraph(vertexSetOf(v0, v1, v2)) { edge -> edge in allowedEdges }

        assertThat(subgraph.edges).containsExactlyInAnyOrder(e0, e1)

        allowedEdges.remove(e0)
        assertThat(subgraph.edges).containsExactlyInAnyOrder(e1)

        allowedEdges.add(e2)
        assertThat(subgraph.edges).containsExactlyInAnyOrder(e1, e2)

        // vertices are still inducing-based, so listener/reference support must work
        subgraph.registerVertexChangeListener(RecordingVertexListener())
        val ref = subgraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)

        // edges are filter-based, so listener/reference support must be rejected
        assertThrows<UnsupportedOperationException> { subgraph.registerEdgeChangeListener(NoOpEdgeListener()) }
        assertThrows<UnsupportedOperationException> { subgraph.createEdgeReference(e1) }
    }

    @Test
    fun vertexAndEdgeFilterSubgraphDoesNotSupportListenersOrReferences() {
        val graph = Graphs.mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        graph.addEdge(v1, v2)

        val subgraph = graph.subgraph({ vertex -> vertex != v2 }, { true })

        assertThat(subgraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(subgraph.edges).containsExactlyInAnyOrder(e0)

        assertThrows<UnsupportedOperationException> { subgraph.registerVertexChangeListener(RecordingVertexListener()) }
        assertThrows<UnsupportedOperationException> { subgraph.createVertexReference(v0) }
        assertThrows<UnsupportedOperationException> { subgraph.registerEdgeChangeListener(NoOpEdgeListener()) }
        assertThrows<UnsupportedOperationException> { subgraph.createEdgeReference(e0) }
    }

    @Test
    fun subgraphRequiresInducingVerticesToBelongToParent() {
        val graph = Graphs.mutableGraph(true)
        graph.addVertex()

        assertThrows<IllegalArgumentException> { graph.subgraph(vertexSetOf(Vertex(999)), edgeSetOf<Edge>()) }
    }

    @Test
    fun immutableParentProducesImmutableSubgraphWithNoOpListeners() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val immutable = ImmutableGraphs.buildImmutableGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }

        val subgraph = immutable.subgraph(vertexSetOf(v0, v1), immutable.edges)

        assertThat(subgraph).isInstanceOf(ImmutableGraph::class.java)
        assertThat(subgraph.vertices).containsExactlyInAnyOrder(v0, v1)

        // registration silently succeeds and does nothing on an immutable subgraph, rather than throwing
        subgraph.registerVertexChangeListener(RecordingVertexListener())

        val ref = subgraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)
    }
}
