package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DirectedNetworkTest {

    private lateinit var graph: Graph
    private lateinit var vertexName: VertexProperty<String>
    private lateinit var edgeWeight: EdgeProperty<Float>
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)
    private var e2: Edge = Edge(-1)
    private var e3: Edge = Edge(-1)
    private var e4: Edge = Edge(-1)

    private fun constructGraph(immutable: Boolean) {
        if (immutable) {
            val immutable =
                immutableGraph<String, Float>(true, allowMultiEdge = true).withVertexProperty().withEdgeProperty()
                    .build {
                    v0 = addVertex("v0")
                    v1 = addVertex("v1")
                    v2 = addVertex("v2")
                    v3 = addVertex("v3")
                    e0 = addEdge("v0", "v1", 1.5f)
                    e1 = addEdge("v1", "v2", 2.0f)
                    e2 = addEdge("v2", "v0", 2.1f)
                    e3 = addEdge("v0", "v0", 1.0f)
                    e4 = addEdge("v0", "v0", 3.0f)
                }
            graph = immutable.graph
            vertexName = immutable.vertexProperty
            edgeWeight = immutable.edgeProperty
        } else {
            val g = mutableGraph(true, allowMultiEdge = true)
            graph = g
            vertexName = graph.createVertexProperty { "" }
            edgeWeight = graph.createEdgeProperty { 0f }
            buildGraph(g, vertexName, edgeWeight) {
                v0 = addVertex("v0")
                v1 = addVertex("v1")
                v2 = addVertex("v2")
                v3 = addVertex("v3")
                e0 = addEdge("v0", "v1", 1.5f)
                e1 = addEdge("v1", "v2", 2.0f)
                e2 = addEdge("v2", "v0", 2.1f)
                e3 = addEdge("v0", "v0", 1.0f)
                e4 = addEdge("v0", "v0", 3.0f)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun vertices(immutable: Boolean) {
        constructGraph(immutable)

        context(graph, vertexName) {
            assertThat(graph.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
            assertThat(graph.vertices.size).isEqualTo(graph.vertices.iterator().asSequence().count())
            assertThat(graph.vertices.contains(v0)).isTrue
            assertThat(graph.vertices.contains(v1)).isTrue
            assertThat(graph.vertices.contains(v2)).isTrue
            assertThat(graph.vertices.contains(v3)).isTrue

            assertThrows<IllegalArgumentException> { graph.vertices.contains(Vertex(99)) }

            assertThat(v0.property).isEqualTo("v0")
            assertThat(v1.property).isEqualTo("v1")
            assertThat(v2.property).isEqualTo("v2")
            assertThat(v3.property).isEqualTo("v3")
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun outDegree(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.outDegree).isEqualTo(3)
            assertThat(v1.outDegree).isEqualTo(1)
            assertThat(v2.outDegree).isEqualTo(1)
            assertThat(v3.outDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).outDegree }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun inDegree(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.inDegree).isEqualTo(3)
            assertThat(v1.inDegree).isEqualTo(1)
            assertThat(v2.inDegree).isEqualTo(1)
            assertThat(v3.inDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).inDegree }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun successors(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.successors()).containsExactlyInAnyOrder(v0, v1)
            assertThat(v0.successors().size).isEqualTo(v0.successors().iterator().asSequence().count())
            assertThat(v0.successors().contains(v0)).isTrue
            assertThat(v0.successors().contains(v1)).isTrue
            assertThat(v0.successors().contains(v2)).isFalse
            assertThat(v0.successors().contains(v3)).isFalse

            assertThat(v1.successors()).containsExactlyInAnyOrder(v2)
            assertThat(v1.successors().size).isEqualTo(v1.successors().iterator().asSequence().count())
            assertThat(v1.successors().contains(v0)).isFalse
            assertThat(v1.successors().contains(v1)).isFalse
            assertThat(v1.successors().contains(v2)).isTrue
            assertThat(v1.successors().contains(v3)).isFalse

            assertThat(v2.successors()).containsExactlyInAnyOrder(v0)
            assertThat(v2.successors().size).isEqualTo(v2.successors().iterator().asSequence().count())
            assertThat(v2.successors().contains(v0)).isTrue
            assertThat(v2.successors().contains(v1)).isFalse
            assertThat(v2.successors().contains(v2)).isFalse
            assertThat(v2.successors().contains(v3)).isFalse

            assertThat(v3.successors()).isEmpty()
            assertThat(v3.successors().size).isEqualTo(v3.successors().iterator().asSequence().count())
            assertThat(v3.successors().contains(v0)).isFalse
            assertThat(v3.successors().contains(v1)).isFalse
            assertThat(v3.successors().contains(v2)).isFalse
            assertThat(v3.successors().contains(v3)).isFalse

            assertThrows<IllegalArgumentException> { Vertex(99).successors() }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun predecessors(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.predecessors()).containsExactlyInAnyOrder(v0, v2)
            assertThat(v0.predecessors().size).isEqualTo(v0.predecessors().iterator().asSequence().count())
            assertThat(v0.predecessors().contains(v0)).isTrue
            assertThat(v0.predecessors().contains(v1)).isFalse
            assertThat(v0.predecessors().contains(v2)).isTrue
            assertThat(v0.predecessors().contains(v3)).isFalse

            assertThat(v1.predecessors()).containsExactlyInAnyOrder(v0)
            assertThat(v1.predecessors().size).isEqualTo(v1.predecessors().iterator().asSequence().count())
            assertThat(v1.predecessors().contains(v0)).isTrue
            assertThat(v1.predecessors().contains(v1)).isFalse
            assertThat(v1.predecessors().contains(v2)).isFalse
            assertThat(v1.predecessors().contains(v3)).isFalse

            assertThat(v2.predecessors()).containsExactlyInAnyOrder(v1)
            assertThat(v2.predecessors().size).isEqualTo(v2.predecessors().iterator().asSequence().count())
            assertThat(v2.predecessors().contains(v0)).isFalse
            assertThat(v2.predecessors().contains(v1)).isTrue
            assertThat(v2.predecessors().contains(v2)).isFalse
            assertThat(v2.predecessors().contains(v3)).isFalse

            assertThat(v3.predecessors()).isEmpty()
            assertThat(v3.predecessors().size).isEqualTo(v3.predecessors().iterator().asSequence().count())
            assertThat(v3.predecessors().contains(v0)).isFalse
            assertThat(v3.predecessors().contains(v1)).isFalse
            assertThat(v3.predecessors().contains(v2)).isFalse
            assertThat(v3.predecessors().contains(v3)).isFalse

            assertThrows<IllegalArgumentException> { Vertex(99).predecessors() }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun outgoingEdges(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0, e3, e4)
            assertThat(v0.outgoingEdges().size).isEqualTo(v0.outgoingEdges().iterator().asSequence().count())
            assertThat(v0.outgoingEdges().contains(e0)).isTrue
            assertThat(v0.outgoingEdges().contains(e1)).isFalse
            assertThat(v0.outgoingEdges().contains(e2)).isFalse
            assertThat(v0.outgoingEdges().contains(e3)).isTrue
            assertThat(v0.outgoingEdges().contains(e4)).isTrue

            assertThat(v1.outgoingEdges()).containsExactlyInAnyOrder(e1)
            assertThat(v1.outgoingEdges().size).isEqualTo(v1.outgoingEdges().iterator().asSequence().count())
            assertThat(v1.outgoingEdges().contains(e0)).isFalse
            assertThat(v1.outgoingEdges().contains(e1)).isTrue
            assertThat(v1.outgoingEdges().contains(e2)).isFalse
            assertThat(v1.outgoingEdges().contains(e3)).isFalse
            assertThat(v1.outgoingEdges().contains(e4)).isFalse

            assertThat(v2.outgoingEdges()).containsExactlyInAnyOrder(e2)
            assertThat(v2.outgoingEdges().size).isEqualTo(v2.outgoingEdges().iterator().asSequence().count())
            assertThat(v2.outgoingEdges().contains(e0)).isFalse
            assertThat(v2.outgoingEdges().contains(e1)).isFalse
            assertThat(v2.outgoingEdges().contains(e2)).isTrue
            assertThat(v2.outgoingEdges().contains(e3)).isFalse
            assertThat(v2.outgoingEdges().contains(e4)).isFalse

            assertThat(v3.outgoingEdges()).isEmpty()
            assertThat(v3.outgoingEdges().size).isEqualTo(v3.outgoingEdges().iterator().asSequence().count())
            assertThat(v3.outgoingEdges().contains(e0)).isFalse
            assertThat(v3.outgoingEdges().contains(e1)).isFalse
            assertThat(v3.outgoingEdges().contains(e2)).isFalse
            assertThat(v3.outgoingEdges().contains(e3)).isFalse
            assertThat(v3.outgoingEdges().contains(e4)).isFalse

            assertThrows<IllegalArgumentException> { Vertex(99).outgoingEdges() }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun incomingEdges(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.incomingEdges()).containsExactlyInAnyOrder(e2, e3, e4)
            assertThat(v0.incomingEdges().size).isEqualTo(v0.incomingEdges().iterator().asSequence().count())
            assertThat(v0.incomingEdges().contains(e0)).isFalse
            assertThat(v0.incomingEdges().contains(e1)).isFalse
            assertThat(v0.incomingEdges().contains(e2)).isTrue
            assertThat(v0.incomingEdges().contains(e3)).isTrue
            assertThat(v0.incomingEdges().contains(e4)).isTrue

            assertThat(v1.incomingEdges()).containsExactlyInAnyOrder(e0)
            assertThat(v1.incomingEdges().size).isEqualTo(v1.incomingEdges().iterator().asSequence().count())
            assertThat(v1.incomingEdges().contains(e0)).isTrue
            assertThat(v1.incomingEdges().contains(e1)).isFalse
            assertThat(v1.incomingEdges().contains(e2)).isFalse
            assertThat(v1.incomingEdges().contains(e3)).isFalse
            assertThat(v1.incomingEdges().contains(e4)).isFalse

            assertThat(v2.incomingEdges()).containsExactlyInAnyOrder(e1)
            assertThat(v2.incomingEdges().size).isEqualTo(v2.incomingEdges().iterator().asSequence().count())
            assertThat(v2.incomingEdges().contains(e0)).isFalse
            assertThat(v2.incomingEdges().contains(e1)).isTrue
            assertThat(v2.incomingEdges().contains(e2)).isFalse
            assertThat(v2.incomingEdges().contains(e3)).isFalse
            assertThat(v2.incomingEdges().contains(e4)).isFalse

            assertThat(v3.incomingEdges()).isEmpty()
            assertThat(v3.incomingEdges().size).isEqualTo(v3.incomingEdges().iterator().asSequence().count())
            assertThat(v3.incomingEdges().contains(e0)).isFalse
            assertThat(v3.incomingEdges().contains(e1)).isFalse
            assertThat(v3.incomingEdges().contains(e2)).isFalse
            assertThat(v3.incomingEdges().contains(e3)).isFalse
            assertThat(v3.incomingEdges().contains(e4)).isFalse

            assertThrows<IllegalArgumentException> { Vertex(99).incomingEdges() }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edges(immutable: Boolean) {
        constructGraph(immutable)

        context(graph, edgeWeight) {
            assertThat(graph.edges).containsExactlyInAnyOrder(e0, e1, e2, e3, e4)
            assertThat(graph.edges.size).isEqualTo(graph.edges.iterator().asSequence().count())
            assertThat(graph.edges.contains(e0)).isTrue
            assertThat(graph.edges.contains(e1)).isTrue
            assertThat(graph.edges.contains(e2)).isTrue
            assertThat(graph.edges.contains(e3)).isTrue
            assertThat(graph.edges.contains(e4)).isTrue

            assertThrows<IllegalArgumentException> { graph.edges.contains(Edge(99L)) }

            assertThat(e0.property).isEqualTo(1.5f)
            assertThat(e1.property).isEqualTo(2.0f)
            assertThat(e2.property).isEqualTo(2.1f)
            assertThat(e3.property).isEqualTo(1.0f)
            assertThat(e4.property).isEqualTo(3.0f)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun edgeSourceAndTarget(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(e0.source).isEqualTo(v0)
            assertThat(e0.target).isEqualTo(v1)
            assertThat(e1.source).isEqualTo(v1)
            assertThat(e1.target).isEqualTo(v2)
            assertThat(e2.source).isEqualTo(v2)
            assertThat(e2.target).isEqualTo(v0)
            assertThat(e3.source).isEqualTo(v0)
            assertThat(e3.target).isEqualTo(v0)
            assertThat(e4.source).isEqualTo(v0)
            assertThat(e4.target).isEqualTo(v0)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun containsEdge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.containsEdge(v0, v1)).isTrue()
        assertThat(graph.containsEdge(v1, v0)).isFalse()
        assertThat(graph.containsEdge(v1, v2)).isTrue()
        assertThat(graph.containsEdge(v2, v1)).isFalse()
        assertThat(graph.containsEdge(v2, v0)).isTrue()
        assertThat(graph.containsEdge(v0, v2)).isFalse()
        assertThat(graph.containsEdge(v0, v0)).isTrue()
        assertThat(graph.containsEdge(v0, v3)).isFalse()
        assertThat(graph.containsEdge(v1, v3)).isFalse()
        assertThat(graph.containsEdge(v2, v3)).isFalse()
        assertThat(graph.containsEdge(v3, v0)).isFalse()
        assertThat(graph.containsEdge(v3, v1)).isFalse()
        assertThat(graph.containsEdge(v3, v2)).isFalse()
        assertThat(graph.containsEdge(v3, v3)).isFalse()

        assertThrows<IllegalArgumentException> { graph.containsEdge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.containsEdge(Vertex(99), v0) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun getEdge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.getEdge(v0, v1)).isEqualTo(e0)
        assertThrows<NoSuchElementException> { graph.getEdge(v1, v0) }
        assertThat(graph.getEdge(v1, v2)).isEqualTo(e1)
        assertThrows<NoSuchElementException> { graph.getEdge(v2, v1) }
        assertThat(graph.getEdge(v2, v0)).isEqualTo(e2)
        assertThrows<NoSuchElementException> { graph.getEdge(v0, v2) }
        assertThat(graph.getEdge(v0, v0)).satisfiesAnyOf(
            { assertThat(it).isEqualTo(e3) },
            { assertThat(it).isEqualTo(e4) },
        )
        assertThrows<NoSuchElementException> { graph.getEdge(v0, v3) }
        assertThrows<NoSuchElementException> { graph.getEdge(v1, v3) }
        assertThrows<NoSuchElementException> { graph.getEdge(v2, v3) }
        assertThrows<NoSuchElementException> { graph.getEdge(v3, v3) }

        assertThrows<IllegalArgumentException> { graph.getEdge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.getEdge(Vertex(99), v0) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun getEdges(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.getEdges(v0, v1)).containsExactlyInAnyOrder(e0)
        assertThat(graph.getEdges(v0, v1).contains(e0)).isTrue
        assertThat(graph.getEdges(v0, v1).contains(e1)).isFalse
        assertThat(graph.getEdges(v0, v1).contains(e2)).isFalse
        assertThat(graph.getEdges(v0, v1).contains(e3)).isFalse
        assertThat(graph.getEdges(v0, v1).contains(e4)).isFalse

        assertThat(graph.getEdges(v1, v0)).isEmpty()
        assertThat(graph.getEdges(v1, v0).contains(e0)).isFalse
        assertThat(graph.getEdges(v1, v0).contains(e1)).isFalse
        assertThat(graph.getEdges(v1, v0).contains(e2)).isFalse
        assertThat(graph.getEdges(v1, v0).contains(e3)).isFalse
        assertThat(graph.getEdges(v1, v0).contains(e4)).isFalse

        assertThat(graph.getEdges(v1, v2)).containsExactlyInAnyOrder(e1)
        assertThat(graph.getEdges(v1, v2).contains(e0)).isFalse
        assertThat(graph.getEdges(v1, v2).contains(e1)).isTrue
        assertThat(graph.getEdges(v1, v2).contains(e2)).isFalse
        assertThat(graph.getEdges(v1, v2).contains(e3)).isFalse
        assertThat(graph.getEdges(v1, v2).contains(e4)).isFalse

        assertThat(graph.getEdges(v2, v1)).isEmpty()
        assertThat(graph.getEdges(v2, v1).contains(e0)).isFalse
        assertThat(graph.getEdges(v2, v1).contains(e1)).isFalse
        assertThat(graph.getEdges(v2, v1).contains(e2)).isFalse
        assertThat(graph.getEdges(v2, v1).contains(e3)).isFalse
        assertThat(graph.getEdges(v2, v1).contains(e4)).isFalse

        assertThat(graph.getEdges(v2, v0)).containsExactlyInAnyOrder(e2)
        assertThat(graph.getEdges(v2, v0).contains(e0)).isFalse
        assertThat(graph.getEdges(v2, v0).contains(e1)).isFalse
        assertThat(graph.getEdges(v2, v0).contains(e2)).isTrue
        assertThat(graph.getEdges(v2, v0).contains(e3)).isFalse
        assertThat(graph.getEdges(v2, v0).contains(e4)).isFalse

        assertThat(graph.getEdges(v0, v2)).isEmpty()
        assertThat(graph.getEdges(v0, v2).contains(e0)).isFalse
        assertThat(graph.getEdges(v0, v2).contains(e1)).isFalse
        assertThat(graph.getEdges(v0, v2).contains(e2)).isFalse
        assertThat(graph.getEdges(v0, v2).contains(e3)).isFalse
        assertThat(graph.getEdges(v0, v2).contains(e4)).isFalse

        assertThat(graph.getEdges(v0, v0)).containsExactlyInAnyOrder(e3, e4)
        assertThat(graph.getEdges(v0, v0).contains(e0)).isFalse
        assertThat(graph.getEdges(v0, v0).contains(e1)).isFalse
        assertThat(graph.getEdges(v0, v0).contains(e2)).isFalse
        assertThat(graph.getEdges(v0, v0).contains(e3)).isTrue
        assertThat(graph.getEdges(v0, v0).contains(e4)).isTrue

        assertThat(graph.getEdges(v0, v3)).isEmpty()
        assertThat(graph.getEdges(v0, v3).contains(e0)).isFalse
        assertThat(graph.getEdges(v0, v3).contains(e1)).isFalse
        assertThat(graph.getEdges(v0, v3).contains(e2)).isFalse
        assertThat(graph.getEdges(v0, v3).contains(e3)).isFalse
        assertThat(graph.getEdges(v0, v3).contains(e4)).isFalse

        assertThat(graph.getEdges(v1, v3)).isEmpty()
        assertThat(graph.getEdges(v1, v3).contains(e0)).isFalse
        assertThat(graph.getEdges(v1, v3).contains(e1)).isFalse
        assertThat(graph.getEdges(v1, v3).contains(e2)).isFalse
        assertThat(graph.getEdges(v1, v3).contains(e3)).isFalse
        assertThat(graph.getEdges(v1, v3).contains(e4)).isFalse

        assertThat(graph.getEdges(v2, v3)).isEmpty()
        assertThat(graph.getEdges(v2, v3).contains(e0)).isFalse
        assertThat(graph.getEdges(v2, v3).contains(e1)).isFalse
        assertThat(graph.getEdges(v2, v3).contains(e2)).isFalse
        assertThat(graph.getEdges(v2, v3).contains(e3)).isFalse
        assertThat(graph.getEdges(v2, v3).contains(e4)).isFalse

        assertThat(graph.getEdges(v3, v3)).isEmpty()
        assertThat(graph.getEdges(v3, v3).contains(e0)).isFalse
        assertThat(graph.getEdges(v3, v3).contains(e1)).isFalse
        assertThat(graph.getEdges(v3, v3).contains(e2)).isFalse
        assertThat(graph.getEdges(v3, v3).contains(e3)).isFalse
        assertThat(graph.getEdges(v3, v3).contains(e4)).isFalse

        assertThrows<IllegalArgumentException> { graph.getEdges(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.getEdges(Vertex(99), v0) }
    }
}
