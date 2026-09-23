package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.properties.VertexProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DirectedGraphTest {

    private lateinit var graph: Graph
    private lateinit var valueGraph: ValueGraph<String, Float>
    private lateinit var vertexName: VertexProperty<String>
    private lateinit var edgeWeight: io.github.sooniln.fastgraph.properties.EdgeProperty<Float>
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var v3: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)
    private var e2: Edge = Edge(-1)
    private var e3: Edge = Edge(-1)

    private fun constructGraph(immutable: Boolean) {
        valueGraph = if (immutable) {
            buildImmutableValueGraph<String, Float>(true, { 0f }) {
                v0 = addVertex("v0")
                v1 = addVertex("v1")
                v2 = addVertex("v2")
                v3 = addVertex("v3")
                e0 = addEdge("v0", "v1", 1.5f)
                e1 = addEdge("v1", "v2", 2.0f)
                e2 = addEdge("v2", "v0", 2.1f)
                e3 = addEdge("v0", "v0", 1.0f)
            }
        } else {
            buildValueGraph<String, Float>(true, { 0f }) {
                v0 = addVertex("v0")
                v1 = addVertex("v1")
                v2 = addVertex("v2")
                v3 = addVertex("v3")
                e0 = addEdge("v0", "v1", 1.5f)
                e1 = addEdge("v1", "v2", 2.0f)
                e2 = addEdge("v2", "v0", 2.1f)
                e3 = addEdge("v0", "v0", 1.0f)
            }
        }
        graph = valueGraph.graph
        vertexName = valueGraph.vertexKeys
        edgeWeight = valueGraph.edgeValues
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun multiEdge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.multiEdge).isFalse
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun vertices(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(graph.vertices.size).isEqualTo(graph.vertices.iterator().asSequence().count())
        assertThat(graph.vertices.contains(v0)).isTrue
        assertThat(graph.vertices.contains(v1)).isTrue
        assertThat(graph.vertices.contains(v2)).isTrue
        assertThat(graph.vertices.contains(v3)).isTrue

        assertThat(graph.vertices.contains(Vertex(99))).isFalse
        assertThat(graph.vertices.contains(Vertex(-1))).isFalse
        assertThat(graph.isEmpty()).isFalse

        assertThat(valueGraph.vertexKeys[v0]).isEqualTo("v0")
        assertThat(valueGraph.vertexKeys[v1]).isEqualTo("v1")
        assertThat(valueGraph.vertexKeys[v2]).isEqualTo("v2")
        assertThat(valueGraph.vertexKeys[v3]).isEqualTo("v3")
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun outDegree(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.outDegree(v0)).isEqualTo(2)
        assertThat(graph.outDegree(v1)).isEqualTo(1)
        assertThat(graph.outDegree(v2)).isEqualTo(1)
        assertThat(graph.outDegree(v3)).isEqualTo(0)

        assertThrows<IllegalArgumentException> { graph.outDegree(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.outDegree(Vertex(-1)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun inDegree(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.inDegree(v0)).isEqualTo(2)
        assertThat(graph.inDegree(v1)).isEqualTo(1)
        assertThat(graph.inDegree(v2)).isEqualTo(1)
        assertThat(graph.inDegree(v3)).isEqualTo(0)

        assertThrows<IllegalArgumentException> { graph.inDegree(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.inDegree(Vertex(-1)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun successors(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.successors(v0)).containsExactlyInAnyOrder(v0, v1)
        assertThat(graph.successors(v0).size).isEqualTo(graph.successors(v0).iterator().asSequence().count())
        assertThat(graph.successors(v0).contains(v0)).isTrue
        assertThat(graph.successors(v0).contains(v1)).isTrue
        assertThat(graph.successors(v0).contains(v2)).isFalse
        assertThat(graph.successors(v0).contains(v3)).isFalse

        assertThat(graph.successors(v1)).containsExactlyInAnyOrder(v2)
        assertThat(graph.successors(v1).size).isEqualTo(graph.successors(v1).iterator().asSequence().count())
        assertThat(graph.successors(v1).contains(v0)).isFalse
        assertThat(graph.successors(v1).contains(v1)).isFalse
        assertThat(graph.successors(v1).contains(v2)).isTrue
        assertThat(graph.successors(v1).contains(v3)).isFalse

        assertThat(graph.successors(v2)).containsExactlyInAnyOrder(v0)
        assertThat(graph.successors(v2).size).isEqualTo(graph.successors(v2).iterator().asSequence().count())
        assertThat(graph.successors(v2).contains(v0)).isTrue
        assertThat(graph.successors(v2).contains(v1)).isFalse
        assertThat(graph.successors(v2).contains(v2)).isFalse
        assertThat(graph.successors(v2).contains(v3)).isFalse

        assertThat(graph.successors(v3)).isEmpty()
        assertThat(graph.successors(v3).size).isEqualTo(graph.successors(v3).iterator().asSequence().count())
        assertThat(graph.successors(v3).contains(v0)).isFalse
        assertThat(graph.successors(v3).contains(v1)).isFalse
        assertThat(graph.successors(v3).contains(v2)).isFalse
        assertThat(graph.successors(v3).contains(v3)).isFalse

        assertThrows<IllegalArgumentException> { graph.successors(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun successor(immutable: Boolean) {
        constructGraph(immutable)

        // v0 has two successors (itself via e3 and v1 via e0)
        assertThrows<IllegalStateException> { graph.successor(v0) }
        assertThat(graph.successor(v1)).isEqualTo(v2)
        assertThat(graph.successor(v2)).isEqualTo(v0)
        assertThrows<IllegalStateException> { graph.successor(v3) }

        assertThrows<IllegalArgumentException> { graph.successor(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun predecessors(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.predecessors(v0)).containsExactlyInAnyOrder(v0, v2)
        assertThat(graph.predecessors(v0).size).isEqualTo(graph.predecessors(v0).iterator().asSequence().count())
        assertThat(graph.predecessors(v0).contains(v0)).isTrue
        assertThat(graph.predecessors(v0).contains(v1)).isFalse
        assertThat(graph.predecessors(v0).contains(v2)).isTrue
        assertThat(graph.predecessors(v0).contains(v3)).isFalse

        assertThat(graph.predecessors(v1)).containsExactlyInAnyOrder(v0)
        assertThat(graph.predecessors(v1).size).isEqualTo(graph.predecessors(v1).iterator().asSequence().count())
        assertThat(graph.predecessors(v1).contains(v0)).isTrue
        assertThat(graph.predecessors(v1).contains(v1)).isFalse
        assertThat(graph.predecessors(v1).contains(v2)).isFalse
        assertThat(graph.predecessors(v1).contains(v3)).isFalse

        assertThat(graph.predecessors(v2)).containsExactlyInAnyOrder(v1)
        assertThat(graph.predecessors(v2).size).isEqualTo(graph.predecessors(v2).iterator().asSequence().count())
        assertThat(graph.predecessors(v2).contains(v0)).isFalse
        assertThat(graph.predecessors(v2).contains(v1)).isTrue
        assertThat(graph.predecessors(v2).contains(v2)).isFalse
        assertThat(graph.predecessors(v2).contains(v3)).isFalse

        assertThat(graph.predecessors(v3)).isEmpty()
        assertThat(graph.predecessors(v3).size).isEqualTo(graph.predecessors(v3).iterator().asSequence().count())
        assertThat(graph.predecessors(v3).contains(v0)).isFalse
        assertThat(graph.predecessors(v3).contains(v1)).isFalse
        assertThat(graph.predecessors(v3).contains(v2)).isFalse
        assertThat(graph.predecessors(v3).contains(v3)).isFalse

        assertThrows<IllegalArgumentException> { graph.predecessors(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun predecessor(immutable: Boolean) {
        constructGraph(immutable)

        // v0 has two predecessors (itself via e3 and v2 via e2)
        assertThrows<IllegalStateException> { graph.predecessor(v0) }
        assertThat(graph.predecessor(v1)).isEqualTo(v0)
        assertThat(graph.predecessor(v2)).isEqualTo(v1)
        assertThrows<IllegalStateException> { graph.predecessor(v3) }

        assertThrows<IllegalArgumentException> { graph.predecessor(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun outgoingEdges(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.outgoingEdges(v0)).containsExactlyInAnyOrder(e0, e3)
        assertThat(graph.outgoingEdges(v0).size).isEqualTo(graph.outgoingEdges(v0).iterator().asSequence().count())
        assertThat(graph.outgoingEdges(v0).contains(e0)).isTrue
        assertThat(graph.outgoingEdges(v0).contains(e1)).isFalse
        assertThat(graph.outgoingEdges(v0).contains(e2)).isFalse
        assertThat(graph.outgoingEdges(v0).contains(e3)).isTrue

        assertThat(graph.outgoingEdges(v1)).containsExactlyInAnyOrder(e1)
        assertThat(graph.outgoingEdges(v1).size).isEqualTo(graph.outgoingEdges(v1).iterator().asSequence().count())
        assertThat(graph.outgoingEdges(v1).contains(e0)).isFalse
        assertThat(graph.outgoingEdges(v1).contains(e1)).isTrue
        assertThat(graph.outgoingEdges(v1).contains(e2)).isFalse
        assertThat(graph.outgoingEdges(v1).contains(e3)).isFalse

        assertThat(graph.outgoingEdges(v2)).containsExactlyInAnyOrder(e2)
        assertThat(graph.outgoingEdges(v2).size).isEqualTo(graph.outgoingEdges(v2).iterator().asSequence().count())
        assertThat(graph.outgoingEdges(v2).contains(e0)).isFalse
        assertThat(graph.outgoingEdges(v2).contains(e1)).isFalse
        assertThat(graph.outgoingEdges(v2).contains(e2)).isTrue
        assertThat(graph.outgoingEdges(v2).contains(e3)).isFalse

        assertThat(graph.outgoingEdges(v3)).isEmpty()
        assertThat(graph.outgoingEdges(v3).size).isEqualTo(graph.outgoingEdges(v3).iterator().asSequence().count())
        assertThat(graph.outgoingEdges(v3).contains(e0)).isFalse
        assertThat(graph.outgoingEdges(v3).contains(e1)).isFalse
        assertThat(graph.outgoingEdges(v3).contains(e2)).isFalse
        assertThat(graph.outgoingEdges(v3).contains(e3)).isFalse

        assertThrows<IllegalArgumentException> { graph.outgoingEdges(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun outgoingEdge(immutable: Boolean) {
        constructGraph(immutable)

        // v0 has two outgoing edges (e0 and e3)
        assertThrows<IllegalStateException> { graph.outgoingEdge(v0) }
        assertThat(graph.outgoingEdge(v1)).isEqualTo(e1)
        assertThat(graph.outgoingEdge(v2)).isEqualTo(e2)
        assertThrows<IllegalStateException> { graph.outgoingEdge(v3) }

        assertThrows<IllegalArgumentException> { graph.outgoingEdge(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun incomingEdges(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.incomingEdges(v0)).containsExactlyInAnyOrder(e2, e3)
        assertThat(graph.incomingEdges(v0).size).isEqualTo(graph.incomingEdges(v0).iterator().asSequence().count())
        assertThat(graph.incomingEdges(v0).contains(e0)).isFalse
        assertThat(graph.incomingEdges(v0).contains(e1)).isFalse
        assertThat(graph.incomingEdges(v0).contains(e2)).isTrue
        assertThat(graph.incomingEdges(v0).contains(e3)).isTrue

        assertThat(graph.incomingEdges(v1)).containsExactlyInAnyOrder(e0)
        assertThat(graph.incomingEdges(v1).size).isEqualTo(graph.incomingEdges(v1).iterator().asSequence().count())
        assertThat(graph.incomingEdges(v1).contains(e0)).isTrue
        assertThat(graph.incomingEdges(v1).contains(e1)).isFalse
        assertThat(graph.incomingEdges(v1).contains(e2)).isFalse
        assertThat(graph.incomingEdges(v1).contains(e3)).isFalse

        assertThat(graph.incomingEdges(v2)).containsExactlyInAnyOrder(e1)
        assertThat(graph.incomingEdges(v2).size).isEqualTo(graph.incomingEdges(v2).iterator().asSequence().count())
        assertThat(graph.incomingEdges(v2).contains(e0)).isFalse
        assertThat(graph.incomingEdges(v2).contains(e1)).isTrue
        assertThat(graph.incomingEdges(v2).contains(e2)).isFalse
        assertThat(graph.incomingEdges(v2).contains(e3)).isFalse

        assertThat(graph.incomingEdges(v3)).isEmpty()
        assertThat(graph.incomingEdges(v3).size).isEqualTo(graph.incomingEdges(v3).iterator().asSequence().count())
        assertThat(graph.incomingEdges(v3).contains(e0)).isFalse
        assertThat(graph.incomingEdges(v3).contains(e1)).isFalse
        assertThat(graph.incomingEdges(v3).contains(e2)).isFalse
        assertThat(graph.incomingEdges(v3).contains(e3)).isFalse

        assertThrows<IllegalArgumentException> { graph.incomingEdges(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun incomingEdge(immutable: Boolean) {
        constructGraph(immutable)

        // v0 has two incoming edges (e2 and e3)
        assertThrows<IllegalStateException> { graph.incomingEdge(v0) }
        assertThat(graph.incomingEdge(v1)).isEqualTo(e0)
        assertThat(graph.incomingEdge(v2)).isEqualTo(e1)
        assertThrows<IllegalStateException> { graph.incomingEdge(v3) }

        assertThrows<IllegalArgumentException> { graph.incomingEdge(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edges(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edges).containsExactlyInAnyOrder(e0, e1, e2, e3)
        assertThat(graph.edges.size).isEqualTo(graph.edges.iterator().asSequence().count())
        assertThat(graph.edges.contains(e0)).isTrue
        assertThat(graph.edges.contains(e1)).isTrue
        assertThat(graph.edges.contains(e2)).isTrue
        assertThat(graph.edges.contains(e3)).isTrue
        assertThat(graph.edges.contains(Edge(99L))).isFalse
        assertThat(graph.edges.contains(Edge(-1L))).isFalse

        assertThat(valueGraph.edgeValues[e0]).isEqualTo(1.5f)
        assertThat(valueGraph.edgeValues[e1]).isEqualTo(2.0f)
        assertThat(valueGraph.edgeValues[e2]).isEqualTo(2.1f)
        assertThat(valueGraph.edgeValues[e3]).isEqualTo(1.0f)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeSourceAndTarget(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edgeSource(e0)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e0)).isEqualTo(v1)
        assertThat(graph.edgeSource(e1)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e1)).isEqualTo(v2)
        assertThat(graph.edgeSource(e2)).isEqualTo(v2)
        assertThat(graph.edgeTarget(e2)).isEqualTo(v0)
        assertThat(graph.edgeSource(e3)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e3)).isEqualTo(v0)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun hasEdge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.hasEdge(v0, v1)).isTrue()
        assertThat(graph.hasEdge(v1, v0)).isFalse()
        assertThat(graph.hasEdge(v1, v2)).isTrue()
        assertThat(graph.hasEdge(v2, v1)).isFalse()
        assertThat(graph.hasEdge(v2, v0)).isTrue()
        assertThat(graph.hasEdge(v0, v2)).isFalse()
        assertThat(graph.hasEdge(v0, v0)).isTrue()
        assertThat(graph.hasEdge(v0, v3)).isFalse()
        assertThat(graph.hasEdge(v1, v3)).isFalse()
        assertThat(graph.hasEdge(v2, v3)).isFalse()
        assertThat(graph.hasEdge(v3, v0)).isFalse()
        assertThat(graph.hasEdge(v3, v1)).isFalse()
        assertThat(graph.hasEdge(v3, v2)).isFalse()
        assertThat(graph.hasEdge(v3, v3)).isFalse()

        assertThrows<IllegalArgumentException> { graph.hasEdge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.hasEdge(Vertex(99), v0) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edge(v0, v1)).isEqualTo(e0)
        assertThrows<IllegalStateException> { graph.edge(v1, v0) }
        assertThat(graph.edge(v1, v2)).isEqualTo(e1)
        assertThrows<IllegalStateException> { graph.edge(v2, v1) }
        assertThat(graph.edge(v2, v0)).isEqualTo(e2)
        assertThrows<IllegalStateException> { graph.edge(v0, v2) }
        assertThat(graph.edge(v0, v0)).isEqualTo(e3)
        assertThrows<IllegalStateException> { graph.edge(v0, v3) }
        assertThrows<IllegalStateException> { graph.edge(v1, v3) }
        assertThrows<IllegalStateException> { graph.edge(v2, v3) }
        assertThrows<IllegalStateException> { graph.edge(v3, v3) }

        assertThrows<IllegalArgumentException> { graph.edge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.edge(Vertex(99), v0) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun getEdges(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edges(v0, v1)).containsExactlyInAnyOrder(e0)
        assertThat(graph.edges(v0, v1).contains(e0)).isTrue
        assertThat(graph.edges(v0, v1).contains(e1)).isFalse
        assertThat(graph.edges(v0, v1).contains(e2)).isFalse
        assertThat(graph.edges(v0, v1).contains(e3)).isFalse

        assertThat(graph.edges(v1, v0)).isEmpty()
        assertThat(graph.edges(v1, v0).contains(e0)).isFalse
        assertThat(graph.edges(v1, v0).contains(e1)).isFalse
        assertThat(graph.edges(v1, v0).contains(e2)).isFalse
        assertThat(graph.edges(v1, v0).contains(e3)).isFalse

        assertThat(graph.edges(v1, v2)).containsExactlyInAnyOrder(e1)
        assertThat(graph.edges(v1, v2).contains(e0)).isFalse
        assertThat(graph.edges(v1, v2).contains(e1)).isTrue
        assertThat(graph.edges(v1, v2).contains(e2)).isFalse
        assertThat(graph.edges(v1, v2).contains(e3)).isFalse

        assertThat(graph.edges(v2, v1)).isEmpty()
        assertThat(graph.edges(v2, v1).contains(e0)).isFalse
        assertThat(graph.edges(v2, v1).contains(e1)).isFalse
        assertThat(graph.edges(v2, v1).contains(e2)).isFalse
        assertThat(graph.edges(v2, v1).contains(e3)).isFalse

        assertThat(graph.edges(v2, v0)).containsExactlyInAnyOrder(e2)
        assertThat(graph.edges(v2, v0).contains(e0)).isFalse
        assertThat(graph.edges(v2, v0).contains(e1)).isFalse
        assertThat(graph.edges(v2, v0).contains(e2)).isTrue
        assertThat(graph.edges(v2, v0).contains(e3)).isFalse

        assertThat(graph.edges(v0, v2)).isEmpty()
        assertThat(graph.edges(v0, v2).contains(e0)).isFalse
        assertThat(graph.edges(v0, v2).contains(e1)).isFalse
        assertThat(graph.edges(v0, v2).contains(e2)).isFalse
        assertThat(graph.edges(v0, v2).contains(e3)).isFalse

        assertThat(graph.edges(v0, v0)).containsExactlyInAnyOrder(e3)
        assertThat(graph.edges(v0, v0).contains(e0)).isFalse
        assertThat(graph.edges(v0, v0).contains(e1)).isFalse
        assertThat(graph.edges(v0, v0).contains(e2)).isFalse
        assertThat(graph.edges(v0, v0).contains(e3)).isTrue

        assertThat(graph.edges(v0, v3)).isEmpty()
        assertThat(graph.edges(v0, v3).contains(e0)).isFalse
        assertThat(graph.edges(v0, v3).contains(e1)).isFalse
        assertThat(graph.edges(v0, v3).contains(e2)).isFalse
        assertThat(graph.edges(v0, v3).contains(e3)).isFalse

        assertThat(graph.edges(v1, v3)).isEmpty()
        assertThat(graph.edges(v1, v3).contains(e0)).isFalse
        assertThat(graph.edges(v1, v3).contains(e1)).isFalse
        assertThat(graph.edges(v1, v3).contains(e2)).isFalse
        assertThat(graph.edges(v1, v3).contains(e3)).isFalse

        assertThat(graph.edges(v2, v3)).isEmpty()
        assertThat(graph.edges(v2, v3).contains(e0)).isFalse
        assertThat(graph.edges(v2, v3).contains(e1)).isFalse
        assertThat(graph.edges(v2, v3).contains(e2)).isFalse
        assertThat(graph.edges(v2, v3).contains(e3)).isFalse

        assertThat(graph.edges(v3, v3)).isEmpty()
        assertThat(graph.edges(v3, v3).contains(e0)).isFalse
        assertThat(graph.edges(v3, v3).contains(e1)).isFalse
        assertThat(graph.edges(v3, v3).contains(e2)).isFalse
        assertThat(graph.edges(v3, v3).contains(e3)).isFalse

        assertThrows<IllegalArgumentException> { graph.edges(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.edges(Vertex(99), v0) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeOpposite(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edgeOpposite(e0, v0)).isEqualTo(v1)
        assertThat(graph.edgeOpposite(e0, v1)).isEqualTo(v0)
        assertThat(graph.edgeOpposite(e1, v1)).isEqualTo(v2)
        assertThat(graph.edgeOpposite(e1, v2)).isEqualTo(v1)
        assertThat(graph.edgeOpposite(e2, v2)).isEqualTo(v0)
        assertThat(graph.edgeOpposite(e2, v0)).isEqualTo(v2)
        assertThat(graph.edgeOpposite(e3, v0)).isEqualTo(v0)

        assertThrows<IllegalArgumentException> { graph.edgeOpposite(e0, v2) }
        assertThrows<IllegalArgumentException> { graph.edgeOpposite(e0, v3) }
        assertThrows<IllegalArgumentException> { graph.edgeOpposite(e3, v1) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeEndpointRelativeToOtherEndpoint(immutable: Boolean) {
        constructGraph(immutable)

        // in a directed graph the two-argument forms are just edgeSource/edgeTarget with a consistency check
        assertThat(graph.edgeSource(e0, v1)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e0, v0)).isEqualTo(v1)
        assertThat(graph.edgeSource(e1, v2)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e1, v1)).isEqualTo(v2)
        assertThat(graph.edgeSource(e2, v0)).isEqualTo(v2)
        assertThat(graph.edgeTarget(e2, v2)).isEqualTo(v0)
        assertThat(graph.edgeSource(e3, v0)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e3, v0)).isEqualTo(v0)

        assertThat(graph.edgeSource(e0, graph.createVertexReference(v1))).isEqualTo(v0)
        assertThat(graph.edgeTarget(e0, graph.createVertexReference(v0))).isEqualTo(v1)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun singularVertexAccessors(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.successor(v1)).isEqualTo(v2)
        assertThat(graph.predecessor(v1)).isEqualTo(v0)
        assertThat(graph.outgoingEdge(v1)).isEqualTo(e1)
        assertThat(graph.incomingEdge(v1)).isEqualTo(e0)
        assertThat(graph.successor(v2)).isEqualTo(v0)
        assertThat(graph.predecessor(v2)).isEqualTo(v1)
        assertThat(graph.outgoingEdge(v2)).isEqualTo(e2)
        assertThat(graph.incomingEdge(v2)).isEqualTo(e1)

        // v0 has several neighbours and edges in both directions, v3 has none
        assertThrows<IllegalStateException> { graph.successor(v0) }
        assertThrows<IllegalStateException> { graph.predecessor(v0) }
        assertThrows<IllegalStateException> { graph.outgoingEdge(v0) }
        assertThrows<IllegalStateException> { graph.incomingEdge(v0) }
        assertThrows<IllegalStateException> { graph.successor(v3) }
        assertThrows<IllegalStateException> { graph.predecessor(v3) }
        assertThrows<IllegalStateException> { graph.outgoingEdge(v3) }
        assertThrows<IllegalStateException> { graph.incomingEdge(v3) }

        assertThrows<IllegalArgumentException> { graph.successor(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.predecessor(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.outgoingEdge(Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.incomingEdge(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeToAndEdgesTo(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edge(v0, v1)).isEqualTo(e0)
        assertThat(graph.edge(v1, v2)).isEqualTo(e1)
        assertThat(graph.edge(v2, v0)).isEqualTo(e2)
        assertThrows<IllegalStateException> { graph.edge(v1, v0) }
        assertThrows<IllegalStateException> { graph.edge(v0, v3) }

        assertThat(graph.edges(v0, v1)).containsExactlyInAnyOrder(e0)
        assertThat(graph.edges(v1, v0)).isEmpty()
        assertThat(graph.edges(v0, v3)).isEmpty()

        assertThrows<IllegalArgumentException> { graph.edge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.edges(Vertex(99), v0) }
    }
}
