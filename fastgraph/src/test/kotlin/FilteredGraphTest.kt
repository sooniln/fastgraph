package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FilteredGraphTest {

    private class RecordingVertexListener : VertexChangeListener {
        val added = mutableListOf<Vertex>()
        val removed = mutableListOf<Vertex>()
        val reassigned = mutableListOf<Pair<Vertex, Vertex>>()
        override fun onVertexAdded(vertex: Vertex) { added.add(vertex) }
        override fun onVertexRemoved(vertex: Vertex) { removed.add(vertex) }
        override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) { reassigned.add(oldVertex to newVertex) }
    }

    private class RecordingEdgeListener : EdgeChangeListener {
        val added = mutableListOf<Edge>()
        val removed = mutableListOf<Edge>()
        val reassigned = mutableListOf<Pair<Edge, Edge>>()
        override fun onEdgeAdded(edge: Edge) { added.add(edge) }
        override fun onEdgeRemoved(edge: Edge) { removed.add(edge) }
        override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) { reassigned.add(oldEdge to newEdge) }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphContainsOnlyInducedVerticesAndEdges(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val v3 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)
        graph.addEdge(v0, v3)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1, e2))

        assertThat(filteredGraph.directed).isEqualTo(directed)
        assertThat(filteredGraph.multiEdge).isEqualTo(graph.multiEdge)
        assertThat(filteredGraph.parent).isSameAs(graph)
        assertThat(filteredGraph.isEmpty()).isFalse
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1, e2)
        assertThat(filteredGraph.vertices.contains(v3)).isFalse
        // v3 is not part of the filtered graph, so it is treated like any other foreign vertex
        assertThrows<IllegalArgumentException> { filteredGraph.hasEdge(v0, v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.outDegree(v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.inDegree(v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.successors(v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.predecessors(v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.outgoingEdges(v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.incomingEdges(v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.edges(v0, v3) }
        assertThrows<IllegalArgumentException> { filteredGraph.createVertexReference(v3) }

        context(filteredGraph) {
            // the edge to v3 is filtered out along with v3, so the out-side of v0 only sees e0 (and e2 if undirected)
            if (directed) {
                assertThat(v0.outDegree).isEqualTo(1)
                assertThat(v0.inDegree).isEqualTo(1)
                assertThat(v0.successors()).containsExactlyInAnyOrder(v1)
                assertThat(v0.predecessors()).containsExactlyInAnyOrder(v2)
                assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0)
                assertThat(v0.incomingEdges()).containsExactlyInAnyOrder(e2)
                assertThat(v0.successor()).isEqualTo(v1)
                assertThat(v0.predecessor()).isEqualTo(v2)
                assertThat(v0.outgoingEdge()).isEqualTo(e0)
                assertThat(v0.incomingEdge()).isEqualTo(e2)
                assertThat(filteredGraph.hasEdge(v1, v0)).isFalse
                assertThat(v1.edgesTo(v0)).isEmpty()
            } else {
                assertThat(v0.outDegree).isEqualTo(2)
                assertThat(v0.inDegree).isEqualTo(2)
                assertThat(v0.successors()).containsExactlyInAnyOrder(v1, v2)
                assertThat(v0.predecessors()).containsExactlyInAnyOrder(v1, v2)
                assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0, e2)
                assertThat(v0.incomingEdges()).containsExactlyInAnyOrder(e0, e2)
                assertThrows<IllegalStateException> { v0.successor() }
                assertThat(filteredGraph.hasEdge(v1, v0)).isTrue
                assertThat(v1.edgesTo(v0)).containsExactlyInAnyOrder(e0)
            }
            assertThat(filteredGraph.hasEdge(v0, v1)).isTrue
            assertThat(v0.edgeTo(v1)).isEqualTo(e0)
            assertThat(v0.edgesTo(v1)).containsExactlyInAnyOrder(e0)
            assertThat(e0.source).isEqualTo(graph.edgeSource(e0))
            assertThat(e0.target).isEqualTo(graph.edgeTarget(e0))
            assertThat(e0.opposite(v0)).isEqualTo(v1)
        }

        // an edge outside the filtered graph is a foreign edge
        assertThrows<IllegalArgumentException> { filteredGraph.createEdgeReference(graph.edge(v0, v3)) }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphWithoutInducingVerticesKeepsAllParentVertices(directed: Boolean) {
        val graph = mutableGraph(directed)
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
        assertThat(graph.vertices).isEqualTo(filteredGraph.vertices)

        // properties created through the filtered graph belong to it, not to the parent
        assertThat(filteredGraph.createVertexProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph.createVertexKeyProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph.createEdgeProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph.createEdgeKeyProperty<String>().graph).isSameAs(filteredGraph)

        // adding a vertex to the parent is an ordinary event for a filtered graph that induces all vertices
        val listener = RecordingVertexListener()
        filteredGraph.registerVertexChangeListener(listener)
        val v3 = graph.addVertex()
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
        assertThat(listener.added).containsExactly(v3)
        filteredGraph.unregisterVertexChangeListener(listener)
        graph.addVertex()
        assertThat(listener.added).containsExactly(v3)

        // ... but an edge added to the parent is not induced
        val edgeListener = RecordingEdgeListener()
        filteredGraph.registerEdgeChangeListener(edgeListener)
        graph.addEdge(v2, v3)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
        assertThat(edgeListener.added).isEmpty()
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphExcludesEdgeNotInInducingSetEvenWhenBothEndpointsIncluded(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1))

        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(filteredGraph.edges.contains(e2)).isFalse
        assertThat(filteredGraph.hasEdge(v2, v0)).isFalse
        assertThat(filteredGraph.hasEdge(v0, v2)).isFalse
        assertThat(filteredGraph.edges(v2, v0)).isEmpty()
        assertThrows<IllegalStateException> { filteredGraph.edge(v2, v0) }
        assertThat(filteredGraph.outgoingEdges(v2).contains(e2)).isFalse
        assertThat(filteredGraph.incomingEdges(v0).contains(e2)).isFalse
        assertThat(filteredGraph.successors(v2).contains(v0)).isFalse
        assertThat(filteredGraph.predecessors(v0).contains(v2)).isFalse
        assertThat(filteredGraph.outDegree(v2)).isEqualTo(if (directed) 0 else 1)
        assertThat(filteredGraph.inDegree(v0)).isEqualTo(if (directed) 0 else 1)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphSilentlyDropsInducingEdgesWithoutInducingEndpoints(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)

        // edge filtering is applied after vertex filtering: e1 loses its endpoint v2, so it cannot be induced
        val filteredGraph = graph.filter(vertexSetOf(v0, v1), edgeSetOf(e0, e1))

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
        assertThat(filteredGraph.edges.contains(e1)).isFalse
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphResolvesSingleEdgeAgainstTheFilteredEdges(directed: Boolean) {
        val graph = mutableGraph(directed, multiEdge = true)
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
        assertThat(filteredGraph.outgoingEdge(v0)).isEqualTo(e0)
        assertThat(filteredGraph.incomingEdge(v2)).isEqualTo(e2)
        assertThat(filteredGraph.successor(v0)).isEqualTo(v1)
        assertThat(filteredGraph.predecessor(v2)).isEqualTo(v1)

        // the v2 -> v0 edge was filtered out, so there is no edge to resolve
        assertThrows<IllegalStateException> { filteredGraph.edge(v2, v0) }
        if (directed) {
            assertThrows<IllegalStateException> { filteredGraph.outgoingEdge(v2) }
            assertThrows<IllegalStateException> { filteredGraph.incomingEdge(v0) }
        } else {
            assertThat(filteredGraph.outgoingEdge(v2)).isEqualTo(e2)
            assertThat(filteredGraph.incomingEdge(v0)).isEqualTo(e0)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphHasNoSingleEdgeWhenBothParallelEdgesSurvive(directed: Boolean) {
        val graph = mutableGraph(directed, multiEdge = true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v0, v1)

        val filteredGraph = graph.filter(vertexSetOf(v0, v1), edgeSetOf(e0, e1))

        assertThat(filteredGraph.multiEdge).isTrue
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(filteredGraph.edges(v0, v1)).containsExactlyInAnyOrder(e0, e1)
        assertThrows<IllegalStateException> { filteredGraph.edge(v0, v1) }
        assertThrows<IllegalStateException> { filteredGraph.outgoingEdge(v0) }
        assertThat(filteredGraph.successor(v0)).isEqualTo(v1)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphStaysInSyncWithParentVertexRemoval(directed: Boolean) {
        val graph = mutableGraph(directed)
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

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphFollowsParentReassignments(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val v3 = graph.addVertex()
        val e01 = graph.addEdge(v0, v1)
        val e13 = graph.addEdge(v1, v3)
        val e33 = graph.addEdge(v3, v3)
        graph.addEdge(v2, v3)

        val filteredGraph = graph.filter(vertexSetOf(v1, v3), edgeSetOf(e13, e33))
        val vertexListener = RecordingVertexListener()
        val edgeListener = RecordingEdgeListener()
        filteredGraph.registerVertexChangeListener(vertexListener)
        filteredGraph.registerEdgeChangeListener(edgeListener)
        val v3Ref = filteredGraph.createVertexReference(v3)
        val e13Ref = filteredGraph.createEdgeReference(e13)
        val e33Ref = filteredGraph.createEdgeReference(e33)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e13, e33)

        // removing v0 (not in the filtered graph) moves v3 (in the filtered graph) into its index and renames the
        // edges of v3; the filtered graph must follow along, reporting the changes to its own listeners
        graph.removeVertex(v0)

        val movedV3 = v3Ref.unstable
        assertThat(movedV3).isNotEqualTo(v3)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v1, movedV3)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e13Ref.unstable, e33Ref.unstable)
        assertThat(filteredGraph.hasEdge(v1, movedV3)).isTrue
        assertThat(filteredGraph.hasEdge(movedV3, movedV3)).isTrue
        assertThat(filteredGraph.edge(v1, movedV3)).isEqualTo(e13Ref.unstable)
        assertThat(vertexListener.reassigned).containsExactly(v3 to movedV3)
        assertThat(vertexListener.removed).isEmpty()
        assertThat(edgeListener.reassigned).containsExactlyInAnyOrder(e13 to e13Ref.unstable, e33 to e33Ref.unstable)
        assertThat(edgeListener.removed).isEmpty()

        // removing an inducing vertex removes it and its edges from the filtered graph
        val movedE13 = e13Ref.unstable
        val movedE33 = e33Ref.unstable
        graph.removeVertex(movedV3)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v1)
        assertThat(filteredGraph.edges).isEmpty()
        assertThat(vertexListener.removed).containsExactly(movedV3)
        assertThat(edgeListener.removed).containsExactlyInAnyOrder(movedE13, movedE33)
        assertThrows<IllegalArgumentException> { v3Ref.unstable }
        assertThrows<IllegalArgumentException> { e13Ref.unstable }
        assertThrows<IllegalArgumentException> { e33Ref.unstable }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphSupportsEdgeListenersAndReferences(directed: Boolean) {
        val graph = mutableGraph(directed, indexEdges = true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val filteredGraph = graph.filter(null, edgeSetOf(e1, e2))
        val listener = RecordingEdgeListener()
        filteredGraph.registerEdgeChangeListener(listener)
        assertThrows<IllegalArgumentException> { filteredGraph.registerEdgeChangeListener(listener) }
        val e2Ref = filteredGraph.createEdgeReference(e2)
        assertThrows<IllegalArgumentException> { filteredGraph.createEdgeReference(e0) }

        // removing e0 (not in the filtered graph) re-assigns the last edge e2, which is
        graph.removeEdge(e0)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e1, e2Ref.unstable)
        assertThat(listener.reassigned).containsExactly(e2 to e2Ref.unstable)
        assertThat(listener.removed).isEmpty()

        // removing an inducing edge removes it from the filtered graph
        graph.removeEdge(e1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e2Ref.unstable)
        assertThat(listener.removed).containsExactly(e1)

        filteredGraph.unregisterEdgeChangeListener(listener)
        graph.removeEdge(e2Ref.unstable)
        assertThat(filteredGraph.edges).isEmpty()
        assertThat(listener.removed).containsExactly(e1)
        assertThrows<IllegalArgumentException> { e2Ref.unstable }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingFilteredGraphSupportsVertexListenersAndReferences(directed: Boolean) {
        val graph = mutableGraph(directed)
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
        assertThrows<IllegalArgumentException> { filteredGraph.registerVertexChangeListener(listener) }

        graph.removeVertex(v2)

        assertThat(listener.removed).containsExactly(v2)

        val ref = filteredGraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)

        filteredGraph.unregisterVertexChangeListener(listener)
        graph.removeVertex(v1)
        assertThat(listener.removed).containsExactly(v2)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingSetsAreCopied(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)

        val inducingVertices = graph.filter({ it != v2 }, { true }).vertices
        val filteredGraph = graph.filter(vertexSetOf(v0, v1), edgeSetOf(e0))
        val fromViews = graph.filter(inducingVertices, graph.edges)

        // mutating the parent afterwards changes what the live views contain, but not what was induced
        graph.removeEdge(e1)
        val v3 = graph.addVertex()
        graph.addEdge(v1, v3)

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
        assertThat(fromViews.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(fromViews.edges).containsExactlyInAnyOrder(e0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun inducingVertexWithEdgeFilterIsEvaluatedDynamicallyAndOnlySupportsVertexListenersReferences(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        val e2 = graph.addEdge(v2, v0)

        val allowedEdges = mutableSetOf(e0, e1)
        val filteredGraph = graph.filter(vertexSetOf(v0, v1, v2)) { edge -> edge in allowedEdges }

        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(filteredGraph.edges.size).isEqualTo(2)

        allowedEdges.remove(e0)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e1)
        assertThat(filteredGraph.edges.size).isEqualTo(1)
        assertThat(filteredGraph.edges.contains(e0)).isFalse
        assertThat(filteredGraph.hasEdge(v0, v1)).isFalse
        assertThat(filteredGraph.outgoingEdges(v0).contains(e0)).isFalse

        allowedEdges.add(e2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e1, e2)
        assertThat(filteredGraph.hasEdge(v2, v0)).isTrue

        // vertices are still inducing-based, so listener/reference support must work
        filteredGraph.registerVertexChangeListener(RecordingVertexListener())
        val ref = filteredGraph.createVertexReference(v0)
        assertThat(ref.unstable).isEqualTo(v0)

        // edges are filter-based, so listener/reference support must be rejected
        assertThrows<UnsupportedOperationException> { filteredGraph.registerEdgeChangeListener(RecordingEdgeListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.unregisterEdgeChangeListener(RecordingEdgeListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createEdgeReference(e1) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createEdgeKeyProperty<String>() }
        assertThat(filteredGraph.createEdgeProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph).isNotInstanceOf(ImmutableGraph::class.java)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun vertexAndEdgeFilterDoesNotSupportListenersOrReferences(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)

        val allowed = mutableSetOf(v0, v1)
        val filteredGraph = graph.filter({ vertex -> vertex in allowed }, { true })

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.vertices.size).isEqualTo(2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
        assertThat(filteredGraph.edges.size).isEqualTo(1)
        // e1 has an endpoint the vertex filter rejects
        assertThat(filteredGraph.edges.contains(e1)).isFalse
        assertThrows<IllegalArgumentException> { filteredGraph.outgoingEdges(v2) }
        assertThrows<IllegalArgumentException> { filteredGraph.edges(v1, v2) }

        // the predicate is consulted on every access
        allowed.add(v2)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1)
        assertThat(filteredGraph.outgoingEdges(v1)).containsExactlyInAnyOrderElementsOf(graph.outgoingEdges(v1))
        allowed.remove(v0)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v1, v2)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e1)
        assertThrows<IllegalArgumentException> { filteredGraph.outDegree(v0) }

        assertThrows<UnsupportedOperationException> { filteredGraph.registerVertexChangeListener(RecordingVertexListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.unregisterVertexChangeListener(RecordingVertexListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createVertexReference(v1) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createVertexKeyProperty<String>() }
        assertThrows<UnsupportedOperationException> { filteredGraph.registerEdgeChangeListener(RecordingEdgeListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.unregisterEdgeChangeListener(RecordingEdgeListener()) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createEdgeReference(e1) }
        assertThrows<UnsupportedOperationException> { filteredGraph.createEdgeKeyProperty<String>() }
        assertThat(filteredGraph.createVertexProperty<String>().graph).isSameAs(filteredGraph)
        assertThat(filteredGraph.createEdgeProperty<String>().graph).isSameAs(filteredGraph)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun vertexFilterAloneKeepsEveryEdgeBetweenAcceptedVertices(directed: Boolean) {
        val graph = mutableGraph(directed, multiEdge = true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v0, v1)
        val e2 = graph.addEdge(v1, v1)
        graph.addEdge(v1, v2)

        val filteredGraph = graph.filter({ vertex -> vertex != v2 })

        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0, e1, e2)
        assertThat(filteredGraph.edges(v0, v1)).containsExactlyInAnyOrder(e0, e1)
        assertThat(filteredGraph.edges(v1, v1)).containsExactlyInAnyOrder(e2)
        assertThat(filteredGraph.outDegree(v1)).isEqualTo(if (directed) 1 else 3)
        assertThat(filteredGraph.inDegree(v1)).isEqualTo(3)
    }

    @Test
    fun filteredGraphRequiresInducingVerticesAndEdgesToBelongToParent() {
        val graph = mutableGraph(true)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        graph.addEdge(v0, v1)

        assertThrows<IllegalArgumentException> { graph.filter(vertexSetOf(Vertex(999)), edgeSetOf<Edge>()) }
        assertThrows<IllegalArgumentException> { graph.filter(vertexSetOf(Vertex(999))) { true } }
        assertThrows<IllegalArgumentException> { graph.filter(null, edgeSetOf(Edge(999))) }
        assertThrows<IllegalArgumentException> { graph.filter(vertexSetOf(v0, v1), edgeSetOf(Edge(999))) }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun immutableParentProducesImmutableFilteredGraphWithNoOpListeners(directed: Boolean) {
        var v0 = Vertex(-1)
        var v1 = Vertex(-1)
        var v2 = Vertex(-1)
        var e0 = Edge(-1)
        var e1 = Edge(-1)
        val immutable = buildImmutableGraph(directed) {
            v0 = addVertex()
            v1 = addVertex()
            v2 = addVertex()
            e0 = addEdge(v0, v1)
            e1 = addEdge(v1, v2)
        }

        val filteredGraph = immutable.filter(vertexSetOf(v0, v1), immutable.edges)

        assertThat(filteredGraph).isInstanceOf(ImmutableGraph::class.java)
        assertThat(filteredGraph).isInstanceOf(ImmutableFilteredGraph::class.java)
        assertThat(filteredGraph.parent).isSameAs(immutable)
        assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
        assertThat(filteredGraph.edges.contains(e1)).isFalse
        assertThat(filteredGraph.toImmutableGraph()).isSameAs(filteredGraph)

        // registration silently succeeds and does nothing on an immutable filtered graph, rather than throwing
        val vertexListener = RecordingVertexListener()
        val edgeListener = RecordingEdgeListener()
        filteredGraph.registerVertexChangeListener(vertexListener)
        filteredGraph.registerVertexChangeListener(vertexListener)
        filteredGraph.unregisterVertexChangeListener(vertexListener)
        filteredGraph.registerEdgeChangeListener(edgeListener)
        filteredGraph.registerEdgeChangeListener(edgeListener)
        filteredGraph.unregisterEdgeChangeListener(edgeListener)

        val vertexRef = filteredGraph.createVertexReference(v0)
        assertThat(vertexRef.unstable).isEqualTo(v0)
        val edgeRef = filteredGraph.createEdgeReference(e0)
        assertThat(edgeRef.unstable).isEqualTo(e0)
        assertThrows<IllegalArgumentException> { filteredGraph.createVertexReference(v2) }
        assertThrows<IllegalArgumentException> { filteredGraph.createEdgeReference(e1) }

        // the immutable overload also accepts null inducing vertices
        val allVertices = immutable.filter(null, edgeSetOf(e1))
        assertThat(allVertices).isInstanceOf(ImmutableGraph::class.java)
        assertThat(allVertices.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(allVertices.edges).containsExactlyInAnyOrder(e1)

        // properties on an immutable filtered graph are eagerly initialised like on any immutable graph
        val property = filteredGraph.createVertexProperty { vertex -> vertex.id * 2 }
        assertThat(property.graph).isSameAs(filteredGraph)
        assertThat(property[v1]).isEqualTo(2)
        val edgeProperty = filteredGraph.createEdgeProperty { edge -> edge.id.toInt() }
        assertThat(edgeProperty.graph).isSameAs(filteredGraph)
        assertThat(edgeProperty[e0]).isEqualTo(e0.id.toInt())
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun trimToSizeDoesNotChangeTheFilteredGraph(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)

        for (filteredGraph in listOf(
            graph.filter(vertexSetOf(v0, v1), edgeSetOf(e0)),
            graph.filter(null, edgeSetOf(e0)),
            graph.filter(vertexSetOf(v0, v1)) { true },
            graph.filter({ true }, { true }),
        )) {
            val property = filteredGraph.createVertexProperty<String>()
            property[v0] = "v0"
            filteredGraph.trimToSize()
            assertThat(filteredGraph.vertices).containsExactlyInAnyOrder(v0, v1)
            assertThat(filteredGraph.edges).containsExactlyInAnyOrder(e0)
            assertThat(property[v0]).isEqualTo("v0")
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun filteredGraphsCanBeFilteredAgain(directed: Boolean) {
        val graph = mutableGraph(directed)
        val v0 = graph.addVertex()
        val v1 = graph.addVertex()
        val v2 = graph.addVertex()
        val e0 = graph.addEdge(v0, v1)
        val e1 = graph.addEdge(v1, v2)
        graph.addEdge(v2, v0)

        val first = graph.filter(vertexSetOf(v0, v1, v2), edgeSetOf(e0, e1))
        val second = first.filter(vertexSetOf(v0, v1), first.edges)

        assertThat(second.parent).isSameAs(first)
        assertThat(second.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(second.edges).containsExactlyInAnyOrder(e0)

        // changes propagate through every level
        graph.removeVertex(v1)
        assertThat(first.vertices).hasSize(2)
        assertThat(first.edges).isEmpty()
        assertThat(second.vertices).containsExactlyInAnyOrder(v0)
        assertThat(second.edges).isEmpty()
    }
}
