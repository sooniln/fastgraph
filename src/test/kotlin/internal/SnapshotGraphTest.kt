package io.github.sooniln.fastgraph.internal

import io.github.sooniln.fastgraph.Edge
import io.github.sooniln.fastgraph.Graph
import io.github.sooniln.fastgraph.MutableGraph
import io.github.sooniln.fastgraph.Vertex
import io.github.sooniln.fastgraph.buildGraph
import io.github.sooniln.fastgraph.createEdgeProperty
import io.github.sooniln.fastgraph.createVertexProperty
import io.github.sooniln.fastgraph.immutableGraphBuilder
import io.github.sooniln.fastgraph.mutableGraph
import io.github.sooniln.fastgraph.withVertexProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SnapshotGraphTest {

    private lateinit var sourceGraph: MutableGraph
    private lateinit var snapshot: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)
    private var e2: Edge = Edge(-1)
    private var e3: Edge = Edge(-1)

    private fun constructGraph(directed: Boolean) {
        sourceGraph = mutableGraph(directed)
        val vertexName = sourceGraph.createVertexProperty<String> { "" }
        buildGraph<String, Nothing>(sourceGraph, vertexName) {
            v0 = addVertex("v0")
            v1 = addVertex("v1")
            v2 = addVertex("v2")
            v3 = addVertex("v3")
            e0 = addEdge("v0", "v1")
            e1 = addEdge("v1", "v2")
            e2 = addEdge("v2", "v0")
            e3 = addEdge("v0", "v0")
        }
        snapshot = SnapshotGraph(sourceGraph)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun directedPropertyPreserved(directed: Boolean) {
        constructGraph(directed)

        assertThat(snapshot.directed).isEqualTo(directed)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun multiEdgePropertyPreserved(directed: Boolean) {
        constructGraph(directed)

        assertThat(snapshot.multiEdge).isEqualTo(sourceGraph.multiEdge)
        assertThat(snapshot.multiEdge).isFalse
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun vertexValuesPreserved(directed: Boolean) {
        constructGraph(directed)

        for (sourceVertex in sourceGraph.vertices) {
            var found = false
            for (snapshotVertex in snapshot.vertices) {
                if (snapshotVertex.intValue == sourceVertex.intValue) {
                    found = true
                    break
                }
            }
            assertThat(found).isTrue
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edgeValuesPreserved(directed: Boolean) {
        constructGraph(directed)

        for (sourceEdge in sourceGraph.edges) {
            var found = false
            for (snapshotEdge in snapshot.edges) {
                if (snapshotEdge.longValue == sourceEdge.longValue) {
                    found = true
                    break
                }
            }
            assertThat(found).isTrue
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun snapshotIsolatedFromSourceAdditions(directed: Boolean) {
        constructGraph(directed)

        val originalVertexCount = snapshot.vertices.size
        val originalEdgeCount = snapshot.edges.size

        // Modify source graph
        val newVertex = sourceGraph.addVertex()
        sourceGraph.addEdge(v0, newVertex)

        // Snapshot should be unchanged
        assertThat(snapshot.vertices.size).isEqualTo(originalVertexCount)
        assertThat(snapshot.edges.size).isEqualTo(originalEdgeCount)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun snapshotIsolatedFromSourceRemovals(directed: Boolean) {
        constructGraph(directed)

        val originalVertexCount = snapshot.vertices.size
        val originalEdgeCount = snapshot.edges.size

        // Modify source graph
        sourceGraph.removeEdge(e0)
        sourceGraph.removeVertex(v3)

        // Snapshot should be unchanged
        assertThat(snapshot.vertices.size).isEqualTo(originalVertexCount)
        assertThat(snapshot.edges.size).isEqualTo(originalEdgeCount)
        assertThat(snapshot.vertices.contains(v3)).isTrue
        assertThat(snapshot.edges.contains(e0)).isTrue
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun vertices(directed: Boolean) {
        constructGraph(directed)

        assertThat(snapshot.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(snapshot.vertices.size).isEqualTo(snapshot.vertices.iterator().asSequence().count())
        assertThat(snapshot.vertices.contains(v0)).isTrue
        assertThat(snapshot.vertices.contains(v1)).isTrue
        assertThat(snapshot.vertices.contains(v2)).isTrue
        assertThat(snapshot.vertices.contains(v3)).isTrue

        assertThrows<IllegalArgumentException> { snapshot.vertices.contains(Vertex(99)) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edges(directed: Boolean) {
        constructGraph(directed)

        assertThat(snapshot.edges).containsExactlyInAnyOrder(e0, e1, e2, e3)
        assertThat(snapshot.edges.size).isEqualTo(snapshot.edges.iterator().asSequence().count())
        assertThat(snapshot.edges.contains(e0)).isTrue
        assertThat(snapshot.edges.contains(e1)).isTrue
        assertThat(snapshot.edges.contains(e2)).isTrue
        assertThat(snapshot.edges.contains(e3)).isTrue

        assertThrows<IllegalArgumentException> { snapshot.edges.contains(Edge(99L)) }
    }

    @Test
    fun outDegreeDirected() {
        constructGraph(true)

        context(snapshot) {
            assertThat(v0.outDegree).isEqualTo(2)
            assertThat(v1.outDegree).isEqualTo(1)
            assertThat(v2.outDegree).isEqualTo(1)
            assertThat(v3.outDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).outDegree }
        }
    }

    @Test
    fun outDegreeUndirected() {
        constructGraph(false)

        context(snapshot) {
            assertThat(v0.outDegree).isEqualTo(3)
            assertThat(v1.outDegree).isEqualTo(2)
            assertThat(v2.outDegree).isEqualTo(2)
            assertThat(v3.outDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).outDegree }
        }
    }

    @Test
    fun inDegreeDirected() {
        constructGraph(true)

        context(snapshot) {
            assertThat(v0.inDegree).isEqualTo(2)
            assertThat(v1.inDegree).isEqualTo(1)
            assertThat(v2.inDegree).isEqualTo(1)
            assertThat(v3.inDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).inDegree }
        }
    }

    @Test
    fun inDegreeUndirected() {
        constructGraph(false)

        context(snapshot) {
            // For undirected, inDegree equals outDegree
            assertThat(v0.inDegree).isEqualTo(3)
            assertThat(v1.inDegree).isEqualTo(2)
            assertThat(v2.inDegree).isEqualTo(2)
            assertThat(v3.inDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).inDegree }
        }
    }

    @Test
    fun successorsDirected() {
        constructGraph(true)

        context(snapshot) {
            assertThat(v0.successors()).containsExactlyInAnyOrder(v0, v1)
            assertThat(v0.successors().size).isEqualTo(v0.successors().iterator().asSequence().count())
            assertThat(v0.successors().contains(v0)).isTrue
            assertThat(v0.successors().contains(v1)).isTrue
            assertThat(v0.successors().contains(v2)).isFalse
            assertThat(v0.successors().contains(v3)).isFalse

            assertThat(v1.successors()).containsExactlyInAnyOrder(v2)
            assertThat(v2.successors()).containsExactlyInAnyOrder(v0)
            assertThat(v3.successors()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).successors() }
        }
    }

    @Test
    fun successorsUndirected() {
        constructGraph(false)

        context(snapshot) {
            assertThat(v0.successors()).containsExactlyInAnyOrder(v0, v1, v2)
            assertThat(v1.successors()).containsExactlyInAnyOrder(v0, v2)
            assertThat(v2.successors()).containsExactlyInAnyOrder(v0, v1)
            assertThat(v3.successors()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).successors() }
        }
    }

    @Test
    fun predecessorsDirected() {
        constructGraph(true)

        context(snapshot) {
            assertThat(v0.predecessors()).containsExactlyInAnyOrder(v0, v2)
            assertThat(v0.predecessors().size).isEqualTo(v0.predecessors().iterator().asSequence().count())
            assertThat(v0.predecessors().contains(v0)).isTrue
            assertThat(v0.predecessors().contains(v1)).isFalse
            assertThat(v0.predecessors().contains(v2)).isTrue
            assertThat(v0.predecessors().contains(v3)).isFalse

            assertThat(v1.predecessors()).containsExactlyInAnyOrder(v0)
            assertThat(v2.predecessors()).containsExactlyInAnyOrder(v1)
            assertThat(v3.predecessors()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).predecessors() }
        }
    }

    @Test
    fun predecessorsUndirected() {
        constructGraph(false)

        context(snapshot) {
            // For undirected, predecessors equals successors
            assertThat(v0.predecessors()).containsExactlyInAnyOrder(v0, v1, v2)
            assertThat(v1.predecessors()).containsExactlyInAnyOrder(v0, v2)
            assertThat(v2.predecessors()).containsExactlyInAnyOrder(v0, v1)
            assertThat(v3.predecessors()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).predecessors() }
        }
    }

    @Test
    fun outgoingEdgesDirected() {
        constructGraph(true)

        context(snapshot) {
            assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0, e3)
            assertThat(v0.outgoingEdges().size).isEqualTo(v0.outgoingEdges().iterator().asSequence().count())
            assertThat(v0.outgoingEdges().contains(e0)).isTrue
            assertThat(v0.outgoingEdges().contains(e1)).isFalse
            assertThat(v0.outgoingEdges().contains(e2)).isFalse
            assertThat(v0.outgoingEdges().contains(e3)).isTrue

            assertThat(v1.outgoingEdges()).containsExactlyInAnyOrder(e1)
            assertThat(v2.outgoingEdges()).containsExactlyInAnyOrder(e2)
            assertThat(v3.outgoingEdges()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).outgoingEdges() }
        }
    }

    @Test
    fun outgoingEdgesUndirected() {
        constructGraph(false)

        context(snapshot) {
            assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0, e2, e3)
            assertThat(v1.outgoingEdges()).containsExactlyInAnyOrder(e0, e1)
            assertThat(v2.outgoingEdges()).containsExactlyInAnyOrder(e1, e2)
            assertThat(v3.outgoingEdges()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).outgoingEdges() }
        }
    }

    @Test
    fun incomingEdgesDirected() {
        constructGraph(true)

        context(snapshot) {
            assertThat(v0.incomingEdges()).containsExactlyInAnyOrder(e2, e3)
            assertThat(v0.incomingEdges().size).isEqualTo(v0.incomingEdges().iterator().asSequence().count())
            assertThat(v0.incomingEdges().contains(e0)).isFalse
            assertThat(v0.incomingEdges().contains(e1)).isFalse
            assertThat(v0.incomingEdges().contains(e2)).isTrue
            assertThat(v0.incomingEdges().contains(e3)).isTrue

            assertThat(v1.incomingEdges()).containsExactlyInAnyOrder(e0)
            assertThat(v2.incomingEdges()).containsExactlyInAnyOrder(e1)
            assertThat(v3.incomingEdges()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).incomingEdges() }
        }
    }

    @Test
    fun incomingEdgesUndirected() {
        constructGraph(false)

        context(snapshot) {
            // For undirected, incomingEdges equals outgoingEdges
            assertThat(v0.incomingEdges()).containsExactlyInAnyOrder(e0, e2, e3)
            assertThat(v1.incomingEdges()).containsExactlyInAnyOrder(e0, e1)
            assertThat(v2.incomingEdges()).containsExactlyInAnyOrder(e1, e2)
            assertThat(v3.incomingEdges()).isEmpty()

            assertThrows<IllegalArgumentException> { Vertex(99).incomingEdges() }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edgeSourceAndTarget(directed: Boolean) {
        constructGraph(directed)

        context(snapshot) {
            assertThat(e0.source).isEqualTo(v0)
            assertThat(e0.target).isEqualTo(v1)
            assertThat(e1.source).isEqualTo(v1)
            assertThat(e1.target).isEqualTo(v2)
            assertThat(e2.source).isEqualTo(v2)
            assertThat(e2.target).isEqualTo(v0)
            assertThat(e3.source).isEqualTo(v0)
            assertThat(e3.target).isEqualTo(v0)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edgeSourceAndTargetValidation(directed: Boolean) {
        constructGraph(directed)

        assertThrows<IllegalArgumentException> { snapshot.edgeSource(Edge(99L)) }
        assertThrows<IllegalArgumentException> { snapshot.edgeTarget(Edge(99L)) }
    }

    @Test
    fun containsEdgeDirected() {
        constructGraph(true)

        assertThat(snapshot.containsEdge(v0, v1)).isTrue
        assertThat(snapshot.containsEdge(v1, v0)).isFalse
        assertThat(snapshot.containsEdge(v1, v2)).isTrue
        assertThat(snapshot.containsEdge(v2, v1)).isFalse
        assertThat(snapshot.containsEdge(v2, v0)).isTrue
        assertThat(snapshot.containsEdge(v0, v2)).isFalse
        assertThat(snapshot.containsEdge(v0, v0)).isTrue
        assertThat(snapshot.containsEdge(v0, v3)).isFalse
        assertThat(snapshot.containsEdge(v1, v3)).isFalse
        assertThat(snapshot.containsEdge(v2, v3)).isFalse
        assertThat(snapshot.containsEdge(v3, v0)).isFalse
        assertThat(snapshot.containsEdge(v3, v1)).isFalse
        assertThat(snapshot.containsEdge(v3, v2)).isFalse
        assertThat(snapshot.containsEdge(v3, v3)).isFalse

        assertThrows<IllegalArgumentException> { snapshot.containsEdge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { snapshot.containsEdge(Vertex(99), v0) }
    }

    @Test
    fun containsEdgeUndirected() {
        constructGraph(false)

        // For undirected, edges are symmetric
        assertThat(snapshot.containsEdge(v0, v1)).isTrue
        assertThat(snapshot.containsEdge(v1, v0)).isTrue
        assertThat(snapshot.containsEdge(v1, v2)).isTrue
        assertThat(snapshot.containsEdge(v2, v1)).isTrue
        assertThat(snapshot.containsEdge(v2, v0)).isTrue
        assertThat(snapshot.containsEdge(v0, v2)).isTrue
        assertThat(snapshot.containsEdge(v0, v0)).isTrue
        assertThat(snapshot.containsEdge(v0, v3)).isFalse
        assertThat(snapshot.containsEdge(v3, v0)).isFalse
        assertThat(snapshot.containsEdge(v3, v3)).isFalse

        assertThrows<IllegalArgumentException> { snapshot.containsEdge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { snapshot.containsEdge(Vertex(99), v0) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun createVertexReference(directed: Boolean) {
        constructGraph(directed)

        val ref0 = snapshot.createVertexReference(v0)
        val ref1 = snapshot.createVertexReference(v1)

        assertThat(ref0.unstable).isEqualTo(v0)
        assertThat(ref1.unstable).isEqualTo(v1)

        assertThrows<IllegalArgumentException> { snapshot.createVertexReference(Vertex(99)) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun createEdgeReference(directed: Boolean) {
        constructGraph(directed)

        val ref0 = snapshot.createEdgeReference(e0)
        val ref1 = snapshot.createEdgeReference(e1)

        assertThat(ref0.unstable).isEqualTo(e0)
        assertThat(ref1.unstable).isEqualTo(e1)

        assertThrows<IllegalArgumentException> { snapshot.createEdgeReference(Edge(99L)) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun emptyGraph(directed: Boolean) {
        val emptySource = mutableGraph(directed)
        val emptySnapshot = SnapshotGraph(emptySource)

        assertThat(emptySnapshot.vertices.size).isEqualTo(0)
        assertThat(emptySnapshot.edges.size).isEqualTo(0)
        assertThat(emptySnapshot.vertices.iterator().hasNext()).isFalse
        assertThat(emptySnapshot.edges.iterator().hasNext()).isFalse
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun singleVertexNoEdges(directed: Boolean) {
        val source = mutableGraph(directed)
        val v = source.addVertex()
        val snap = SnapshotGraph(source)

        assertThat(snap.vertices.size).isEqualTo(1)
        assertThat(snap.edges.size).isEqualTo(0)
        assertThat(snap.vertices.contains(v)).isTrue
        assertThat(snap.outDegree(v)).isEqualTo(0)
        assertThat(snap.inDegree(v)).isEqualTo(0)
        assertThat(snap.successors(v)).isEmpty()
        assertThat(snap.predecessors(v)).isEmpty()
    }

    @Test
    fun createEdgeProperty() {
        constructGraph(true)

        val edgeProp = snapshot.createEdgeProperty<Int> { 0 }

        // Set and get properties
        edgeProp[e0] = 10
        edgeProp[e1] = 20

        assertThat(edgeProp[e0]).isEqualTo(10)
        assertThat(edgeProp[e1]).isEqualTo(20)
        assertThat(edgeProp[e2]).isEqualTo(0) // default value
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun snapshotOfImmutableGraph(directed: Boolean) {
        val immutableGraph = immutableGraphBuilder<String, Nothing>(directed)
            .withVertexProperty()
            .buildPropertyGraph {
                addVertex("a")
                addVertex("b")
                addVertex("c")
                addEdge("a", "b")
                addEdge("b", "c")
            }.graph

        val snap = SnapshotGraph(immutableGraph)

        assertThat(snap.vertices.size).isEqualTo(3)
        assertThat(snap.edges.size).isEqualTo(2)
        assertThat(snap.directed).isEqualTo(directed)
    }

    @Test
    fun multipleSnapshotsOfSameGraph() {
        constructGraph(true)

        val snap1 = SnapshotGraph(sourceGraph)

        // Modify source
        sourceGraph.addVertex()

        val snap2 = SnapshotGraph(sourceGraph)

        // snap1 should have original size, snap2 should have new size
        assertThat(snap1.vertices.size).isEqualTo(4)
        assertThat(snap2.vertices.size).isEqualTo(5)
    }
}
