package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UndirectedGraphTest {

    private lateinit var graph: Graph
    private lateinit var valueGraph: ValueGraph<String, Float>
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

    private fun constructGraph(immutable: Boolean) {
        valueGraph = if (immutable) {
            buildImmutableValueGraph<String, Float>(false, { 0f }) {
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
            buildValueGraph<String, Float>(false, { 0f }) {
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

        context(valueGraph) {
            assertThat(graph.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
            assertThat(graph.vertices.size).isEqualTo(graph.vertices.iterator().asSequence().count())
            assertThat(graph.vertices.contains(v0)).isTrue
            assertThat(graph.vertices.contains(v1)).isTrue
            assertThat(graph.vertices.contains(v2)).isTrue
            assertThat(graph.vertices.contains(v3)).isTrue

            assertThat(graph.vertices.contains(Vertex(99))).isFalse
            assertThat(graph.vertices.contains(Vertex(-1))).isFalse
            assertThat(graph.isEmpty()).isFalse

            assertThat(v0.key).isEqualTo("v0")
            assertThat(v1.key).isEqualTo("v1")
            assertThat(v2.key).isEqualTo("v2")
            assertThat(v3.key).isEqualTo("v3")
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun outDegree(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.outDegree).isEqualTo(3)
            assertThat(v1.outDegree).isEqualTo(2)
            assertThat(v2.outDegree).isEqualTo(2)
            assertThat(v3.outDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).outDegree }
            assertThrows<IllegalArgumentException> { Vertex(-1).outDegree }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun inDegree(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.inDegree).isEqualTo(3)
            assertThat(v1.inDegree).isEqualTo(2)
            assertThat(v2.inDegree).isEqualTo(2)
            assertThat(v3.inDegree).isEqualTo(0)

            assertThrows<IllegalArgumentException> { Vertex(99).inDegree }
            assertThrows<IllegalArgumentException> { Vertex(-1).inDegree }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun successors(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.successors()).containsExactlyInAnyOrder(v0, v1, v2)
            assertThat(v0.successors().size).isEqualTo(v0.successors().iterator().asSequence().count())
            assertThat(v0.successors().contains(v0)).isTrue
            assertThat(v0.successors().contains(v1)).isTrue
            assertThat(v0.successors().contains(v2)).isTrue
            assertThat(v0.successors().contains(v3)).isFalse

            assertThat(v1.successors()).containsExactlyInAnyOrder(v0, v2)
            assertThat(v1.successors().size).isEqualTo(v1.successors().iterator().asSequence().count())
            assertThat(v1.successors().contains(v0)).isTrue
            assertThat(v1.successors().contains(v1)).isFalse
            assertThat(v1.successors().contains(v2)).isTrue
            assertThat(v1.successors().contains(v3)).isFalse

            assertThat(v2.successors()).containsExactlyInAnyOrder(v0, v1)
            assertThat(v2.successors().size).isEqualTo(v2.successors().iterator().asSequence().count())
            assertThat(v2.successors().contains(v0)).isTrue
            assertThat(v2.successors().contains(v1)).isTrue
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

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun successor(immutable: Boolean) {
        constructGraph(immutable)

        // every connected vertex here has more than one neighbour, and v3 has none
        assertThrows<IllegalStateException> { graph.successor(v0) }
        assertThrows<IllegalStateException> { graph.successor(v1) }
        assertThrows<IllegalStateException> { graph.successor(v2) }
        assertThrows<IllegalStateException> { graph.successor(v3) }

        assertThrows<IllegalArgumentException> { graph.successor(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun predecessors(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.predecessors()).containsExactlyInAnyOrder(v0, v1, v2)
            assertThat(v0.predecessors().size).isEqualTo(v0.predecessors().iterator().asSequence().count())
            assertThat(v0.predecessors().contains(v0)).isTrue
            assertThat(v0.predecessors().contains(v1)).isTrue
            assertThat(v0.predecessors().contains(v2)).isTrue
            assertThat(v0.predecessors().contains(v3)).isFalse

            assertThat(v1.predecessors()).containsExactlyInAnyOrder(v0, v2)
            assertThat(v1.predecessors().size).isEqualTo(v1.predecessors().iterator().asSequence().count())
            assertThat(v1.predecessors().contains(v0)).isTrue
            assertThat(v1.predecessors().contains(v1)).isFalse
            assertThat(v1.predecessors().contains(v2)).isTrue
            assertThat(v1.predecessors().contains(v3)).isFalse

            assertThat(v2.predecessors()).containsExactlyInAnyOrder(v0, v1)
            assertThat(v2.predecessors().size).isEqualTo(v2.predecessors().iterator().asSequence().count())
            assertThat(v2.predecessors().contains(v0)).isTrue
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

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun predecessor(immutable: Boolean) {
        constructGraph(immutable)

        // in an undirected graph predecessors are the same as successors
        assertThrows<IllegalStateException> { graph.predecessor(v0) }
        assertThrows<IllegalStateException> { graph.predecessor(v1) }
        assertThrows<IllegalStateException> { graph.predecessor(v2) }
        assertThrows<IllegalStateException> { graph.predecessor(v3) }

        assertThrows<IllegalArgumentException> { graph.predecessor(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun outgoingEdges(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.outgoingEdges()).containsExactlyInAnyOrder(e0, e2, e3)
            assertThat(v0.outgoingEdges().size).isEqualTo(v0.outgoingEdges().iterator().asSequence().count())
            assertThat(v0.outgoingEdges().contains(e0)).isTrue
            assertThat(v0.outgoingEdges().contains(e1)).isFalse
            assertThat(v0.outgoingEdges().contains(e2)).isTrue
            assertThat(v0.outgoingEdges().contains(e3)).isTrue

            assertThat(v1.outgoingEdges()).containsExactlyInAnyOrder(e0, e1)
            assertThat(v1.outgoingEdges().size).isEqualTo(v1.outgoingEdges().iterator().asSequence().count())
            assertThat(v1.outgoingEdges().contains(e0)).isTrue
            assertThat(v1.outgoingEdges().contains(e1)).isTrue
            assertThat(v1.outgoingEdges().contains(e2)).isFalse
            assertThat(v1.outgoingEdges().contains(e3)).isFalse

            assertThat(v2.outgoingEdges()).containsExactlyInAnyOrder(e1, e2)
            assertThat(v2.outgoingEdges().size).isEqualTo(v2.outgoingEdges().iterator().asSequence().count())
            assertThat(v2.outgoingEdges().contains(e0)).isFalse
            assertThat(v2.outgoingEdges().contains(e1)).isTrue
            assertThat(v2.outgoingEdges().contains(e2)).isTrue
            assertThat(v2.outgoingEdges().contains(e3)).isFalse

            assertThat(v3.outgoingEdges()).isEmpty()
            assertThat(v3.outgoingEdges().size).isEqualTo(v3.outgoingEdges().iterator().asSequence().count())
            assertThat(v3.outgoingEdges().contains(e0)).isFalse
            assertThat(v3.outgoingEdges().contains(e1)).isFalse
            assertThat(v3.outgoingEdges().contains(e2)).isFalse
            assertThat(v3.outgoingEdges().contains(e3)).isFalse

            assertThrows<IllegalArgumentException> { Vertex(99).outgoingEdges() }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun outgoingEdge(immutable: Boolean) {
        constructGraph(immutable)

        // every connected vertex here has more than one incident edge, and v3 has none
        assertThrows<IllegalStateException> { graph.outgoingEdge(v0) }
        assertThrows<IllegalStateException> { graph.outgoingEdge(v1) }
        assertThrows<IllegalStateException> { graph.outgoingEdge(v2) }
        assertThrows<IllegalStateException> { graph.outgoingEdge(v3) }

        assertThrows<IllegalArgumentException> { graph.outgoingEdge(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun incomingEdges(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.incomingEdges()).containsExactlyInAnyOrder(e0, e2, e3)
            assertThat(v0.incomingEdges().size).isEqualTo(v0.incomingEdges().iterator().asSequence().count())
            assertThat(v0.incomingEdges().contains(e0)).isTrue
            assertThat(v0.incomingEdges().contains(e1)).isFalse
            assertThat(v0.incomingEdges().contains(e2)).isTrue
            assertThat(v0.incomingEdges().contains(e3)).isTrue

            assertThat(v1.incomingEdges()).containsExactlyInAnyOrder(e0, e1)
            assertThat(v1.incomingEdges().size).isEqualTo(v1.incomingEdges().iterator().asSequence().count())
            assertThat(v1.incomingEdges().contains(e0)).isTrue
            assertThat(v1.incomingEdges().contains(e1)).isTrue
            assertThat(v1.incomingEdges().contains(e2)).isFalse
            assertThat(v1.incomingEdges().contains(e3)).isFalse

            assertThat(v2.incomingEdges()).containsExactlyInAnyOrder(e1, e2)
            assertThat(v2.incomingEdges().size).isEqualTo(v2.incomingEdges().iterator().asSequence().count())
            assertThat(v2.incomingEdges().contains(e0)).isFalse
            assertThat(v2.incomingEdges().contains(e1)).isTrue
            assertThat(v2.incomingEdges().contains(e2)).isTrue
            assertThat(v2.incomingEdges().contains(e3)).isFalse

            assertThat(v3.incomingEdges()).isEmpty()
            assertThat(v3.incomingEdges().size).isEqualTo(v3.incomingEdges().iterator().asSequence().count())
            assertThat(v3.incomingEdges().contains(e0)).isFalse
            assertThat(v3.incomingEdges().contains(e1)).isFalse
            assertThat(v3.incomingEdges().contains(e2)).isFalse
            assertThat(v3.incomingEdges().contains(e3)).isFalse

            assertThrows<IllegalArgumentException> { Vertex(99).incomingEdges() }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun incomingEdge(immutable: Boolean) {
        constructGraph(immutable)

        // in an undirected graph incoming edges are the same as outgoing edges
        assertThrows<IllegalStateException> { graph.incomingEdge(v0) }
        assertThrows<IllegalStateException> { graph.incomingEdge(v1) }
        assertThrows<IllegalStateException> { graph.incomingEdge(v2) }
        assertThrows<IllegalStateException> { graph.incomingEdge(v3) }

        assertThrows<IllegalArgumentException> { graph.incomingEdge(Vertex(99)) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun singleNeighbor(immutable: Boolean) {
        var a = Vertex(-1)
        var b = Vertex(-1)
        var e = Edge(-1)
        val pair = if (immutable) {
            buildImmutableGraph(false) {
                a = addVertex()
                b = addVertex()
                e = addEdge(a, b)
            }
        } else {
            buildGraph(false) {
                a = addVertex()
                b = addVertex()
                e = addEdge(a, b)
            }
        }

        // both directions resolve to the same neighbour and the same edge in an undirected graph
        assertThat(pair.successor(a)).isEqualTo(b)
        assertThat(pair.predecessor(a)).isEqualTo(b)
        assertThat(pair.successor(b)).isEqualTo(a)
        assertThat(pair.predecessor(b)).isEqualTo(a)

        assertThat(pair.outgoingEdge(a)).isEqualTo(e)
        assertThat(pair.incomingEdge(a)).isEqualTo(e)
        assertThat(pair.outgoingEdge(b)).isEqualTo(e)
        assertThat(pair.incomingEdge(b)).isEqualTo(e)

        assertThat(pair.edge(a, b)).isEqualTo(e)
        assertThat(pair.edge(b, a)).isEqualTo(e)

        context(pair) {
            assertThat(a.successor()).isEqualTo(b)
            assertThat(a.predecessor()).isEqualTo(b)
            assertThat(a.outgoingEdge()).isEqualTo(e)
            assertThat(a.incomingEdge()).isEqualTo(e)
            assertThat(b.edgeTo(a)).isEqualTo(e)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edges(immutable: Boolean) {
        constructGraph(immutable)

        context(valueGraph) {
            assertThat(graph.edges).containsExactlyInAnyOrder(e0, e1, e2, e3)
            assertThat(graph.edges.size).isEqualTo(graph.edges.iterator().asSequence().count())
            assertThat(graph.edges.contains(e0)).isTrue
            assertThat(graph.edges.contains(e1)).isTrue
            assertThat(graph.edges.contains(e2)).isTrue
            assertThat(graph.edges.contains(e3)).isTrue
            assertThat(graph.edges.contains(Edge(99L))).isFalse
            assertThat(graph.edges.contains(Edge(-1L))).isFalse

            assertThat(e0.value).isEqualTo(1.5f)
            assertThat(e1.value).isEqualTo(2.0f)
            assertThat(e2.value).isEqualTo(2.1f)
            assertThat(e3.value).isEqualTo(1.0f)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeOpposite(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(e0.opposite(v1)).isEqualTo(v0)
            assertThat(e0.opposite(v0)).isEqualTo(v1)
            assertThat(e1.opposite(v2)).isEqualTo(v1)
            assertThat(e1.opposite(v1)).isEqualTo(v2)
            assertThat(e2.opposite(v0)).isEqualTo(v2)
            assertThat(e2.opposite(v2)).isEqualTo(v0)
            assertThat(e3.opposite(v0)).isEqualTo(v0)

            assertThrows<IllegalArgumentException> { e0.opposite(v2) }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun hasEdge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.hasEdge(v0, v1)).isTrue
        assertThat(graph.hasEdge(v1, v0)).isTrue
        assertThat(graph.hasEdge(v1, v2)).isTrue
        assertThat(graph.hasEdge(v2, v1)).isTrue
        assertThat(graph.hasEdge(v2, v0)).isTrue
        assertThat(graph.hasEdge(v0, v2)).isTrue
        assertThat(graph.hasEdge(v0, v0)).isTrue
        assertThat(graph.hasEdge(v0, v3)).isFalse
        assertThat(graph.hasEdge(v1, v3)).isFalse
        assertThat(graph.hasEdge(v2, v3)).isFalse
        assertThat(graph.hasEdge(v3, v0)).isFalse
        assertThat(graph.hasEdge(v3, v1)).isFalse
        assertThat(graph.hasEdge(v3, v2)).isFalse
        assertThat(graph.hasEdge(v3, v3)).isFalse

        assertThrows<IllegalArgumentException> { graph.hasEdge(v0, Vertex(99)) }
        assertThrows<IllegalArgumentException> { graph.hasEdge(Vertex(99), v0) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edge(immutable: Boolean) {
        constructGraph(immutable)

        assertThat(graph.edge(v0, v1)).isEqualTo(e0)
        assertThat(graph.edge(v1, v0)).isEqualTo(e0)
        assertThat(graph.edge(v1, v2)).isEqualTo(e1)
        assertThat(graph.edge(v2, v1)).isEqualTo(e1)
        assertThat(graph.edge(v2, v0)).isEqualTo(e2)
        assertThat(graph.edge(v0, v2)).isEqualTo(e2)
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

        assertThat(graph.edges(v1, v0)).containsExactlyInAnyOrder(e0)
        assertThat(graph.edges(v1, v0).contains(e0)).isTrue
        assertThat(graph.edges(v1, v0).contains(e1)).isFalse
        assertThat(graph.edges(v1, v0).contains(e2)).isFalse
        assertThat(graph.edges(v1, v0).contains(e3)).isFalse

        assertThat(graph.edges(v1, v2)).containsExactlyInAnyOrder(e1)
        assertThat(graph.edges(v1, v2).contains(e0)).isFalse
        assertThat(graph.edges(v1, v2).contains(e1)).isTrue
        assertThat(graph.edges(v1, v2).contains(e2)).isFalse
        assertThat(graph.edges(v1, v2).contains(e3)).isFalse

        assertThat(graph.edges(v2, v1)).containsExactlyInAnyOrder(e1)
        assertThat(graph.edges(v2, v1).contains(e0)).isFalse
        assertThat(graph.edges(v2, v1).contains(e1)).isTrue
        assertThat(graph.edges(v2, v1).contains(e2)).isFalse
        assertThat(graph.edges(v2, v1).contains(e3)).isFalse

        assertThat(graph.edges(v2, v0)).containsExactlyInAnyOrder(e2)
        assertThat(graph.edges(v2, v0).contains(e0)).isFalse
        assertThat(graph.edges(v2, v0).contains(e1)).isFalse
        assertThat(graph.edges(v2, v0).contains(e2)).isTrue
        assertThat(graph.edges(v2, v0).contains(e3)).isFalse

        assertThat(graph.edges(v0, v2)).containsExactlyInAnyOrder(e2)
        assertThat(graph.edges(v0, v2).contains(e0)).isFalse
        assertThat(graph.edges(v0, v2).contains(e1)).isFalse
        assertThat(graph.edges(v0, v2).contains(e2)).isTrue
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
    fun edgeSourceAndTarget(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            // an undirected edge does not promise which endpoint is reported as the source, only that the pair is
            // right, that the choice is stable, and that it agrees with edgeOpposite
            assertThat(setOf(e0.source, e0.target)).containsExactlyInAnyOrder(v0, v1)
            assertThat(setOf(e1.source, e1.target)).containsExactlyInAnyOrder(v1, v2)
            assertThat(setOf(e2.source, e2.target)).containsExactlyInAnyOrder(v0, v2)
            assertThat(e3.source).isEqualTo(v0)
            assertThat(e3.target).isEqualTo(v0)

            for (edge in listOf(e0, e1, e2, e3)) {
                assertThat(edge.source).isEqualTo(edge.source)
                assertThat(edge.target).isEqualTo(edge.target)
                assertThat(edge.opposite(edge.source)).isEqualTo(edge.target)
                assertThat(edge.opposite(edge.target)).isEqualTo(edge.source)
            }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeEndpointRelativeToOtherEndpoint(immutable: Boolean) {
        constructGraph(immutable)

        // in an undirected graph the two-argument forms return the opposite endpoint whichever is supplied
        assertThat(graph.edgeSource(e0, v1)).isEqualTo(v0)
        assertThat(graph.edgeSource(e0, v0)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e0, v0)).isEqualTo(v1)
        assertThat(graph.edgeTarget(e0, v1)).isEqualTo(v0)
        assertThat(graph.edgeSource(e3, v0)).isEqualTo(v0)
        assertThat(graph.edgeTarget(e3, v0)).isEqualTo(v0)

        assertThat(graph.edgeSource(e0, graph.createVertexReference(v1))).isEqualTo(v0)
        assertThat(graph.edgeTarget(e0, graph.createVertexReference(v1))).isEqualTo(v0)

        context(graph) {
            assertThat(e0.source(v1)).isEqualTo(v0)
            assertThat(e0.source(v0)).isEqualTo(v1)
            assertThat(e0.target(v0)).isEqualTo(v1)
            assertThat(e0.target(v1)).isEqualTo(v0)
        }

        assertThrows<IllegalArgumentException> { graph.edgeSource(e0, v2) }
        assertThrows<IllegalArgumentException> { graph.edgeTarget(e0, v2) }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeDestructuring(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            val (source, target) = e0
            assertThat(source).isEqualTo(e0.source)
            assertThat(target).isEqualTo(e0.target)
            assertThat(setOf(source, target)).containsExactlyInAnyOrder(v0, v1)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun singularVertexContextMembers(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            // every connected vertex here has more than one neighbour, and v3 has none
            for (vertex in listOf(v0, v1, v2, v3)) {
                assertThrows<IllegalStateException> { vertex.successor() }
                assertThrows<IllegalStateException> { vertex.predecessor() }
                assertThrows<IllegalStateException> { vertex.outgoingEdge() }
                assertThrows<IllegalStateException> { vertex.incomingEdge() }
            }

            assertThrows<IllegalArgumentException> { Vertex(99).successor() }
            assertThrows<IllegalArgumentException> { Vertex(99).predecessor() }
            assertThrows<IllegalArgumentException> { Vertex(99).outgoingEdge() }
            assertThrows<IllegalArgumentException> { Vertex(99).incomingEdge() }
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun edgeToAndEdgesTo(immutable: Boolean) {
        constructGraph(immutable)

        context(graph) {
            assertThat(v0.edgeTo(v1)).isEqualTo(e0)
            assertThat(v1.edgeTo(v0)).isEqualTo(e0)
            assertThat(v2.edgeTo(v1)).isEqualTo(e1)
            assertThat(v0.edgeTo(v0)).isEqualTo(e3)
            assertThrows<IllegalStateException> { v0.edgeTo(v3) }
            assertThrows<IllegalStateException> { v3.edgeTo(v0) }

            assertThat(v0.edgesTo(v1)).containsExactlyInAnyOrder(e0)
            assertThat(v1.edgesTo(v0)).containsExactlyInAnyOrder(e0)
            assertThat(v0.edgesTo(v3)).isEmpty()

            assertThrows<IllegalArgumentException> { v0.edgeTo(Vertex(99)) }
            assertThrows<IllegalArgumentException> { Vertex(99).edgesTo(v0) }
        }
    }
}
