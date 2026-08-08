package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ReferenceTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    // built with multiEdge = true so that the graph is guaranteed to implement both IndexedVertexGraph and
    // IndexedEdgeGraph, letting the same fixture exercise VertexReference.index/EdgeReference.index too
    private fun constructGraph(immutable: Boolean) {
        graph = if (immutable) {
            ImmutableGraphs.buildImmutableGraph(true, multiEdge = true) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            Graphs.buildGraph(true, multiEdge = true) {
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
    fun vertexReferenceUnstableRoundtrips(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createVertexReference(v1)

        assertThat(ref.unstable).isEqualTo(v1)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertexReferenceExtensionMembers(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createVertexReference(v1)

        context(graph) {
            assertThat(ref.outDegree).isEqualTo(1)
            assertThat(ref.inDegree).isEqualTo(1)
            assertThat(ref.successors()).containsExactlyInAnyOrder(v2)
            assertThat(ref.predecessors()).containsExactlyInAnyOrder(v0)
            assertThat(ref.outgoingEdges()).containsExactlyInAnyOrder(e1)
            assertThat(ref.incomingEdges()).containsExactlyInAnyOrder(e0)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertexReferenceIndex(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createVertexReference(v1)

        context(graph as IndexedEdgeGraph) {
            assertThat(ref.index).isEqualTo(1)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceUnstableRoundtrips(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createEdgeReference(e0)

        assertThat(ref.unstable).isEqualTo(e0)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceExtensionMembers(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createEdgeReference(e0)

        context(graph) {
            assertThat(ref.source).isEqualTo(v0)
            assertThat(ref.target).isEqualTo(v1)
            assertThat(ref.opposite(v0)).isEqualTo(v1)
            assertThat(ref.opposite(v1)).isEqualTo(v0)

            val (source, target) = ref
            assertThat(source).isEqualTo(v0)
            assertThat(target).isEqualTo(v1)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceIndex(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createEdgeReference(e0)

        context(graph as IndexedEdgeGraph) {
            assertThat(ref.index).isEqualTo(0)
        }
    }
}
