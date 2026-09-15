package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FilteredGraphTest {

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
    fun inducingFilteredGraphContainsOnlyInducedVerticesAndEdges() {
        val graph = mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val v3 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)
        graph.addEdge(v0, v3)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1, e2))

        assertThat(filteredGraph.directed).isEqualTo(graph.directed)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1, e2)
        // v3 is not part of the filtered graph, so it is treated like any other foreign vertex
        assertThrows<IllegalArgumentException> { filteredGraph.hasEdge(v0, v3) }

        context(filteredGraph) {
            assertThat(v0.outDegree).isEqualTo(1)
            assertThat(v0.successors()).containsExactlyInAnyOrder(v1)
            assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0)
        }
    }

    @Test
    fun inducingFilteredGraphWithoutInducingVerticesKeepsAllParentVertices() {
        val graph = mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        graph.addEdge(v1, v2)

        val filteredGraph = graph.filter(null, edgeSetOf(e0))

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)

        // the vertex set tracks every parent vertex, so it compares equal to the parent's
        assertThat(filteredGraph.vertices).isEqualTo(graph.vertices)

        // properties created through the filtered graph belong to it, not to the parent
        assertThat(filteredGraph.createVertexProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph.createVertexKeyProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph.createEdgeProperty<String>().graph).isSameAs(filteredGraph)

        // adding a vertex to the parent is an ordinary event for a filtered graph that induces all vertices
        val v3 = graph.addVertex()
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
    }

    @Test
    fun inducingFilteredGraphExcludesEdgeNotInInducingSetEvenWhenBothEndpointsIncluded() {
        val graph = mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1))

        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(filteredGraph.hasEdge(v2, v0)).isFalse
    }

    @Test
    fun inducingFilteredGraphResolvesSingleEdgeAgainstTheFilteredEdges() {
        val graph = mutableGraph(true, multiEdge = true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        graph.addEdge(v0, v1)
        val e2 = graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e2))

        // only e0 of the parallel pair survives the filter, so it is the single edge from v0 to v1
        assertThat(filteredGraph.edge(v0, v1)).isEqualTo(e0)
        assertThat(filteredGraph.edge(v1, v2)).isEqualTo(e2)

        // the v2 -> v0 edge was filtered out, so there is no edge to resolve
        assertThrows<IllegalStateException> { filteredGraph.edge(v2, v0) }
    }

    @Test
    fun inducingFilteredGraphHasNoSingleEdgeWhenBothParallelEdgesSurvive() {
        val graph = mutableGraph(true, multiEdge = true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v0, v1)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1), edgeSetOf(e0, e1))

        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThrows<IllegalStateException> { filteredGraph.edge(v0, v1) }
    }

    @Test
    fun inducingFilteredGraphStaysInSyncWithParentVertexRemoval() {
        val graph = mutableGraph(true)
        graph.addVertex() // v3-equivalent placeholder, added first so it is not the highest-indexed vertex
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1, e2))

        // v2 is the highest-indexed vertex, so removing it is a plain removal (no id swap to disturb v0/v1)
        graph.removeVertex(v2)

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
    }

    @Test
    fun inducingFilteredGraphSupportsListenersAndReferences() {
        val graph = mutableGraph(true)
        graph.addVertex()
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        graph.addEdge(v0, v1)
        graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf<Edge>())
        val listener = RecordingVertexListener()
        filteredGraph.registerVertexChangeListener(listener)

        graph.removeVertex(v2)

        assertThat(listener.removed).containsExactly(v2)

        val ref = filteredGraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)
    }

    @Test
    fun inducingVertexWithEdgeFilterIsEvaluatedDynamicallyAndOnlySupportsVertexListenersReferences() {
        val graph = mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val allowedEdges = mutableSetOf(e0, e1)
        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2)) { edge -> edge in allowedEdges }

        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)

        allowedEdges.remove(e0)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e1)

        allowedEdges.add(e2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e1, e2)

        // vertices are still inducing-based, so listener/reference support must work
        filteredGraph.registerVertexChangeListener(RecordingVertexListener())
        val ref = filteredGraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)

        // edges are filter-based, so listener/reference support must be rejected
        assertThrows<UnsupportedOperationException> { filteredGraph.registerEdgeChangeListener(NoOpEdgeListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createEdgeReference(e1) }
    }

    @Test
    fun vertexAndEdgeFilterDoesNotSupportListenersOrReferences() {
        val graph = mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        graph.addEdge(v1, v2)

        val filteredGraph = graph.filter({ vertex -> vertex != v2 }, { true })

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)

        assertThrows<UnsupportedOperationException> { filteredGraph.registerVertexChangeListener(RecordingVertexListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createVertexReference(v0) }
        assertThrows<UnsupportedOperationException> { filteredGraph.registerEdgeChangeListener(NoOpEdgeListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createEdgeReference(e0) }
    }

    @Test
    fun filteredGraphRequiresInducingVerticesToBelongToParent() {
        val graph = mutableGraph(true)
        graph.addVertex()

        assertThrows<IllegalArgumentException> { graph.filter(vertexSetOf(Vertex(999)), edgeSetOf<Edge>()) }
    }

    @Test
    fun immutableParentProducesImmutableFilteredGraphWithNoOpListeners() {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        val immutable = buildImmutableGraph(true) {
            v0 = addVertex()
            v1 = addVertex()
            addEdge(v0, v1)
        }

        val filteredGraph = immutable.filter(vertexSetOf(v0, v1), immutable.edges)

        assertThat(filteredGraph).isInstanceOf(ImmutableGraph::class.java)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)

        // registration silently succeeds and does nothing on an immutable filtered graph, rather than throwing
        filteredGraph.registerVertexChangeListener(RecordingVertexListener())

        val ref = filteredGraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)
    }
}
