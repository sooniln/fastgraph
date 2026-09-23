package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ReferenceTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    // built with indexEdges = true so that both vertices and edges are indexed sets, letting the same fixture
    // exercise indexOf for vertex and edge references too
    private fun constructGraph(immutable: Boolean, directed: Boolean = true) {
        graph = if (immutable) {
            buildImmutableGraph(directed, indexEdges = true) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            buildGraph(directed, indexEdges = true) {
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
        // a reference to the same vertex resolves to the same vertex
        assertThat(graph.createVertexReference(v1).unstable).isEqualTo(ref.unstable)
        assertThrows<IllegalArgumentException> { graph.createVertexReference(Vertex(3)) }
        assertThrows<IllegalArgumentException> { graph.createVertexReference(Vertex(-1)) }
        assertThrows<IllegalArgumentException> { graph.createVertexReference(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertexReferenceExtensionMembers(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createVertexReference(v1)

        assertThat(graph.outDegree(ref)).isEqualTo(1)
        assertThat(graph.inDegree(ref)).isEqualTo(1)
        assertThat(graph.successors(ref)).containsExactlyInAnyOrder(v2)
        assertThat(graph.predecessors(ref)).containsExactlyInAnyOrder(v0)
        assertThat(graph.outgoingEdges(ref)).containsExactlyInAnyOrder(e1)
        assertThat(graph.incomingEdges(ref)).containsExactlyInAnyOrder(e0)
        assertThat(graph.edgeOpposite(e0, ref)).isEqualTo(v0)
        assertThat(graph.edgeSource(e0, ref)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e1, ref)).isEqualTo(v2)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun undirectedVertexReferenceExtensionMembers(immutable: Boolean) {
        constructGraph(immutable, directed = false)

        val ref = graph.createVertexReference(v1)

        assertThat(graph.outDegree(ref)).isEqualTo(2)
        assertThat(graph.inDegree(ref)).isEqualTo(2)
        assertThat(graph.successors(ref)).containsExactlyInAnyOrder(v0, v2)
        assertThat(graph.predecessors(ref)).containsExactlyInAnyOrder(v0, v2)
        assertThat(graph.outgoingEdges(ref)).containsExactlyInAnyOrder(e0, e1)
        assertThat(graph.incomingEdges(ref)).containsExactlyInAnyOrder(e0, e1)
        assertThat(graph.edgeOpposite(e0, ref)).isEqualTo(v0)
        assertThat(graph.edgeSource(e0, ref)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e0, ref)).isEqualTo(v0)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertexReferenceIndex(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createVertexReference(v1)

        assertThat(graph.vertices.indexOf(ref.unstable)).isEqualTo(1)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceUnstableRoundtrips(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createEdgeReference(e0)

        assertThat(ref.unstable).isEqualTo(e0)
        // a reference to the same edge resolves to the same edge
        assertThat(graph.createEdgeReference(e0).unstable).isEqualTo(ref.unstable)
        assertThrows<IllegalArgumentException> { graph.createEdgeReference(Edge(2)) }
        assertThrows<IllegalArgumentException> { graph.createEdgeReference(Edge(-1)) }
        assertThrows<IllegalArgumentException> { graph.createEdgeReference(Edge(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceExtensionMembers(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createEdgeReference(e0)

        assertThat(graph.edgeSource(ref)).isEqualTo(v0)
        assertThat(graph.edgeTarget(ref)).isEqualTo(v1)
        assertThat(graph.edgeOpposite(ref.unstable, v0)).isEqualTo(v1)
        assertThat(graph.edgeOpposite(ref.unstable, v1)).isEqualTo(v0)
        assertThrows<IllegalArgumentException> { graph.edgeOpposite(ref.unstable, v2) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun undirectedEdgeReferenceExtensionMembers(immutable: Boolean) {
        constructGraph(immutable, directed = false)

        val ref = graph.createEdgeReference(e0)

        assertThat(setOf(graph.edgeSource(ref), graph.edgeTarget(ref))).containsExactlyInAnyOrder(v0, v1)
        assertThat(graph.edgeSource(ref)).isEqualTo(graph.edgeSource(e0))
        assertThat(graph.edgeTarget(ref)).isEqualTo(graph.edgeTarget(e0))
        assertThat(graph.edgeOpposite(ref.unstable, v0)).isEqualTo(v1)
        assertThat(graph.edgeOpposite(ref.unstable, v1)).isEqualTo(v0)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceIndex(immutable: Boolean) {
        constructGraph(immutable)

        val ref = graph.createEdgeReference(e0)

        assertThat(graph.edges.indexOf(ref.unstable)).isEqualTo(0)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun vertexReferenceFollowsReassignment(directed: Boolean) {
        constructGraph(immutable = false, directed = directed)
        val mutable = graph as MutableGraph
        val ref = graph.createVertexReference(v2)
        val staleRef = graph.createVertexReference(v0)

        // removing v0 moves the last vertex (v2) into its index
        mutable.removeVertex(v0)

        val moved = ref.unstable
        assertThat(moved).isNotEqualTo(v2)
        assertThat(graph.vertices).containsExactlyInAnyOrder(v1, moved)
        assertThat(graph.vertices.contains(v2)).isFalse
        assertThat(graph.hasEdge(v1, moved)).isTrue
        assertThat(graph.vertices.indexOf(ref.unstable)).isEqualTo(graph.vertices.indexOf(moved))
        assertThat(graph.inDegree(ref)).isEqualTo(1)
        assertThat(graph.predecessors(ref)).containsExactlyInAnyOrder(v1)

        // a reference to the removed vertex is invalid for every purpose
        assertThrows<IllegalArgumentException> { staleRef.unstable }
        assertThrows<IllegalArgumentException> { graph.outDegree(staleRef) }
        assertThrows<IllegalArgumentException> { graph.inDegree(staleRef) }
        assertThrows<IllegalArgumentException> { graph.successors(staleRef) }
        assertThrows<IllegalArgumentException> { graph.predecessors(staleRef) }
        assertThrows<IllegalArgumentException> { graph.outgoingEdges(staleRef) }
        assertThrows<IllegalArgumentException> { graph.incomingEdges(staleRef) }
        assertThrows<IllegalArgumentException> { mutable.removeVertex(staleRef) }
        assertThrows<IllegalArgumentException> { mutable.addEdge(staleRef, ref) }

        // adding vertices never disturbs a reference
        mutable.addVertex()
        assertThat(ref.unstable).isEqualTo(moved)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun edgeReferenceFollowsReassignment(directed: Boolean) {
        constructGraph(immutable = false, directed = directed)
        val mutable = graph as MutableGraph
        val ref = graph.createEdgeReference(e1)
        val staleRef = graph.createEdgeReference(e0)

        // removing e0 moves the last edge (e1) into its index
        mutable.removeEdge(e0)

        val moved = ref.unstable
        assertThat(moved).isNotEqualTo(e1)
        assertThat(graph.edges).containsExactlyInAnyOrder(moved)
        assertThat(graph.edges.contains(e1)).isFalse
        assertThat(setOf(graph.edgeSource(moved), graph.edgeTarget(moved))).containsExactlyInAnyOrder(v1, v2)
        assertThat(graph.edges.indexOf(ref.unstable)).isEqualTo(graph.edges.indexOf(moved))
        assertThat(graph.edgeOpposite(ref.unstable, v1)).isEqualTo(v2)

        // a reference to the removed edge is invalid for every purpose
        assertThrows<IllegalArgumentException> { staleRef.unstable }
        assertThrows<IllegalArgumentException> { graph.edgeSource(staleRef) }
        assertThrows<IllegalArgumentException> { graph.edgeTarget(staleRef) }
        assertThrows<IllegalArgumentException> { graph.edgeOpposite(staleRef.unstable, v0) }
        assertThrows<IllegalArgumentException> { mutable.removeEdge(staleRef) }

        // adding edges never disturbs a reference
        mutable.addEdge(v0, v2)
        assertThat(ref.unstable).isEqualTo(moved)

        // removing a vertex removes the edge, which invalidates the reference
        mutable.removeVertex(v1)
        assertThrows<IllegalArgumentException> { ref.unstable }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun canonicalEdgeReferenceFollowsVertexReassignment(directed: Boolean) {
        val mutable = mutableGraph(directed)
        val v0 = mutable.addVertex()
        val v1 = mutable.addVertex()
        val v2 = mutable.addVertex()
        mutable.addEdge(v0, v1)
        val e12 = mutable.addEdge(v1, v2)
        val e22 = mutable.addEdge(v2, v2)
        val ref12 = mutable.createEdgeReference(e12)
        val ref22 = mutable.createEdgeReference(e22)
        val ref2 = mutable.createVertexReference(v2)

        // removing v0 moves v2 into its index, which renames both of the edges of v2
        mutable.removeVertex(v0)

        val movedV2 = ref2.unstable
        assertThat(mutable.edges).containsExactlyInAnyOrder(ref12.unstable, ref22.unstable)
        assertThat(ref12.unstable).isNotEqualTo(e12)
        assertThat(ref22.unstable).isNotEqualTo(e22)
        assertThat(setOf(mutable.edgeSource(ref12.unstable), mutable.edgeTarget(ref12.unstable)))
            .containsExactlyInAnyOrder(v1, movedV2)
        assertThat(mutable.edgeSource(ref22.unstable)).isEqualTo(movedV2)
        assertThat(mutable.edgeTarget(ref22.unstable)).isEqualTo(movedV2)
        assertThat(mutable.edge(v1, movedV2)).isEqualTo(ref12.unstable)
    }
}
