package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TransposeTest {

    private lateinit var graph: Graph
    private var v0: Vertex = Vertex(-1)
    private var v1: Vertex = Vertex(-1)
    private var v2: Vertex = Vertex(-1)
    private var e0: Edge = Edge(-1)
    private var e1: Edge = Edge(-1)

    private fun constructGraph(directed: Boolean, immutable: Boolean) {
        graph = if (immutable) {
            buildImmutableGraph(directed) {
                v0 = addVertex()
                v1 = addVertex()
                v2 = addVertex()
                e0 = addEdge(v0, v1)
                e1 = addEdge(v1, v2)
            }
        } else {
            buildGraph(directed) {
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
    fun undirectedTransposeIsSameGraph(immutable: Boolean) {
        constructGraph(false, immutable)

        assertThat(graph.transpose()).isSameAs(graph)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun directedTransposeReversesEdges(immutable: Boolean) {
        constructGraph(true, immutable)

        val transposed = graph.transpose()

        assertThat(transposed.directed).isTrue
        assertThat(transposed.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(transposed.edges).containsExactlyInAnyOrder(e0, e1)

        assertThat(transposed.edgeSource(e0)).isEqualTo(v1)
        assertThat(transposed.edgeTarget(e0)).isEqualTo(v0)
        assertThat(transposed.edgeSource(e1)).isEqualTo(v2)
        assertThat(transposed.edgeTarget(e1)).isEqualTo(v1)

        assertThat(transposed.hasEdge(v1, v0)).isTrue
        assertThat(transposed.hasEdge(v0, v1)).isFalse
        assertThat(transposed.hasEdge(v2, v1)).isTrue
        assertThat(transposed.hasEdge(v1, v2)).isFalse

        context(transposed) {
            assertThat(v0.outDegree).isEqualTo(0)
            assertThat(v0.inDegree).isEqualTo(1)
            assertThat(v1.outDegree).isEqualTo(1)
            assertThat(v1.inDegree).isEqualTo(1)
            assertThat(v1.successors()).containsExactlyInAnyOrder(v0)
            assertThat(v1.predecessors()).containsExactlyInAnyOrder(v2)
        }

        // the singular accessors must be reversed along with their plural counterparts
        assertThat(transposed.successor(v1)).isEqualTo(v0)
        assertThat(transposed.predecessor(v1)).isEqualTo(v2)
        assertThat(transposed.successor(v2)).isEqualTo(v1)
        assertThat(transposed.predecessor(v0)).isEqualTo(v1)
        assertThrows<IllegalStateException> { transposed.successor(v0) }
        assertThrows<IllegalStateException> { transposed.predecessor(v2) }

        assertThat(transposed.outgoingEdge(v1)).isEqualTo(e0)
        assertThat(transposed.incomingEdge(v1)).isEqualTo(e1)
        assertThat(transposed.outgoingEdge(v2)).isEqualTo(e1)
        assertThat(transposed.incomingEdge(v0)).isEqualTo(e0)
        assertThrows<IllegalStateException> { transposed.outgoingEdge(v0) }
        assertThrows<IllegalStateException> { transposed.incomingEdge(v2) }

        assertThat(transposed.edge(v1, v0)).isEqualTo(e0)
        assertThat(transposed.edge(v2, v1)).isEqualTo(e1)
        assertThrows<IllegalStateException> { transposed.edge(v0, v1) }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun transposeOfEmptyImmutableGraphIsSameGraph(directed: Boolean) {
        val empty = emptyImmutableGraph(directed)

        assertThat(empty.transpose()).isSameAs(empty)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun transposingTwiceReturnsTheOriginalGraph(immutable: Boolean) {
        constructGraph(true, immutable)

        val transposed = graph.transpose()

        assertThat(transposed).isNotSameAs(graph)
        assertThat(transposed.transpose()).isSameAs(graph)
        assertThat(transposed.multiEdge).isEqualTo(graph.multiEdge)
        assertThat(transposed.isEmpty()).isFalse
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun transposeReversesEdgeEndpointHelpers(immutable: Boolean) {
        constructGraph(true, immutable)

        val transposed = graph.transpose()

        assertThat(transposed.edgeOpposite(e0, v0)).isEqualTo(v1)
        assertThat(transposed.edgeOpposite(e0, v1)).isEqualTo(v0)
        assertThrows<IllegalArgumentException> { transposed.edgeOpposite(e0, v2) }
        // e0 runs v1 -> v0 in the transposed view
        assertThat(transposed.edgeSource(e0, v0)).isEqualTo(v1)
        assertThat(transposed.edgeTarget(e0, v1)).isEqualTo(v0)

        context(transposed) {
            assertThat(e0.source).isEqualTo(v1)
            assertThat(e0.target).isEqualTo(v0)
            val (source, target) = e1
            assertThat(source).isEqualTo(v2)
            assertThat(target).isEqualTo(v1)
            assertThat(v1.successor()).isEqualTo(v0)
            assertThat(v1.predecessor()).isEqualTo(v2)
            assertThat(v1.outgoingEdge()).isEqualTo(e0)
            assertThat(v1.incomingEdge()).isEqualTo(e1)
            assertThat(v1.edgeTo(v0)).isEqualTo(e0)
            assertThat(v0.edgesTo(v1)).isEmpty()
            assertThat(v1.edgesTo(v0)).containsExactlyInAnyOrder(e0)
            assertThat(v2.outgoingEdges()).containsExactlyInAnyOrder(e1)
            assertThat(v2.incomingEdges()).isEmpty()
        }

        assertThrows<IllegalArgumentException> { transposed.outDegree(Vertex(99)) }
        assertThrows<IllegalArgumentException> { transposed.edges(Vertex(99), v0) }
    }

    @Test
    fun transposeIsALiveView() {
        constructGraph(true, immutable = false)
        val mutable = graph as MutableGraph
        val transposed = graph.transpose()

        val v3 = mutable.addVertex()
        val e2 = mutable.addEdge(v3, v0)

        assertThat(transposed.vertices).containsExactlyInAnyOrder(v0, v1, v2, v3)
        assertThat(transposed.edges).containsExactlyInAnyOrder(e0, e1, e2)
        assertThat(transposed.edgeSource(e2)).isEqualTo(v0)
        assertThat(transposed.edgeTarget(e2)).isEqualTo(v3)
        assertThat(transposed.successors(v0)).containsExactlyInAnyOrder(v3)
        assertThat(transposed.predecessors(v0)).containsExactlyInAnyOrder(v1)

        mutable.removeEdge(e0)
        assertThat(transposed.edges).containsExactlyInAnyOrder(e1, e2)
        assertThat(transposed.hasEdge(v1, v0)).isFalse
    }

    @Test
    fun transposeForwardsListenersAndReferences() {
        constructGraph(true, immutable = false)
        val mutable = graph as MutableGraph
        val transposed = graph.transpose()
        val added = mutableListOf<Vertex>()
        val vertexListener = object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) { added.add(vertex) }
            override fun onVertexRemoved(vertex: Vertex) {}
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) {}
        }
        val addedEdges = mutableListOf<Edge>()
        val edgeListener = object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) { addedEdges.add(edge) }
            override fun onEdgeRemoved(edge: Edge) {}
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) {}
        }

        transposed.registerVertexChangeListener(vertexListener)
        transposed.registerEdgeChangeListener(edgeListener)
        assertThrows<IllegalArgumentException> { graph.registerVertexChangeListener(vertexListener) }
        assertThrows<IllegalArgumentException> { graph.registerEdgeChangeListener(edgeListener) }

        val v3 = mutable.addVertex()
        val e2 = mutable.addEdge(v3, v0)
        assertThat(added).containsExactly(v3)
        assertThat(addedEdges).containsExactly(e2)

        transposed.unregisterVertexChangeListener(vertexListener)
        transposed.unregisterEdgeChangeListener(edgeListener)
        mutable.addEdge(mutable.addVertex(), v0)
        assertThat(added).containsExactly(v3)
        assertThat(addedEdges).containsExactly(e2)

        val v2Ref = transposed.createVertexReference(v2)
        val e1Ref = transposed.createEdgeReference(e1)
        assertThat(v2Ref.unstable).isEqualTo(v2)
        assertThat(e1Ref.unstable).isEqualTo(e1)
        context(transposed) {
            assertThat(e1Ref.source).isEqualTo(v2)
            assertThat(e1Ref.target).isEqualTo(v1)
            assertThat(v2Ref.outDegree).isEqualTo(1)
            assertThat(v2Ref.inDegree).isEqualTo(0)
        }
        assertThrows<IllegalArgumentException> { transposed.createVertexReference(Vertex(99)) }
        assertThrows<IllegalArgumentException> { transposed.createEdgeReference(Edge(-1)) }

        // removing v0 moves the last vertex into its place, and the references follow through the view
        mutable.removeVertex(v0)
        assertThat(transposed.vertices.contains(v2Ref.unstable)).isTrue
        assertThat(transposed.edges.contains(e1Ref.unstable)).isTrue
        assertThat(transposed.edgeSource(e1Ref.unstable)).isEqualTo(v2Ref.unstable)
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun transposedPropertiesBelongToTheTransposedView(immutable: Boolean) {
        constructGraph(true, immutable)
        val transposed = graph.transpose()

        val vertexProperty = transposed.createVertexProperty<String>()
        val edgeProperty = transposed.createEdgeProperty<Int>(0)
        val vertexKeys = transposed.createVertexKeyProperty<String>()
        val edgeKeys = transposed.createEdgeKeyProperty<String>()

        assertThat(vertexProperty.graph).isSameAs(transposed)
        assertThat(edgeProperty.graph).isSameAs(transposed)
        assertThat(vertexKeys.graph).isSameAs(transposed)
        assertThat(edgeKeys.graph).isSameAs(transposed)
        assertThat(vertexProperty.type).isEqualTo(propertyTypeOf<String?>())
        assertThat(edgeProperty.type).isEqualTo(propertyTypeOf<Int>())

        vertexProperty[v1] = "v1"
        assertThat(vertexProperty[v1]).isEqualTo("v1")
        assertThat(vertexProperty[v0]).isNull()
        assertThat(edgeProperty.put(e0, 5)).isEqualTo(0)
        assertThat(edgeProperty[e0]).isEqualTo(5)
        vertexKeys[v0] = "a"
        vertexKeys[v1] = "b"
        vertexKeys[v2] = "c"
        assertThat(vertexKeys.getVertex("b")).isEqualTo(v1)
        edgeKeys[e0] = "x"
        edgeKeys[e1] = "y"
        assertThat(edgeKeys.getEdge("y")).isEqualTo(e1)
        assertThat(vertexKeys.copy().getVertex("c")).isEqualTo(v2)
        assertThat(edgeKeys.copy().getEdge("x")).isEqualTo(e0)

        // ... which means a value graph can be built over the view
        val valueGraph = valueGraph(transposed, vertexKeys, edgeProperty)
        assertThat(valueGraph.graph).isSameAs(transposed)
        assertThat(valueGraph.edgeSource(e0)).isEqualTo(v1)
        context(valueGraph) {
            assertThat(v1.key).isEqualTo("b")
            assertThat(e0.value).isEqualTo(5)
        }
        assertThrows<IllegalArgumentException> { valueGraph(transposed, graph.createVertexKeyProperty<String>(), edgeProperty) }
        assertThrows<IllegalArgumentException> { valueGraph(graph, vertexKeys, graph.createEdgeProperty<Int>(0)) }

        if (!immutable) {
            val mutable = graph as MutableGraph
            val v3 = mutable.addVertex()
            assertThat(vertexProperty[v3]).isNull()
            val e2 = mutable.addEdge(v3, v0)
            assertThat(edgeProperty[e2]).isEqualTo(0)
            assertThrows<IllegalStateException> { vertexKeys.getVertex("a") }
            vertexKeys[v3] = "d"
            assertThat(vertexKeys.getVertex("d")).isEqualTo(v3)
        }
    }

    @ParameterizedTest(name = "immutable={0}")
    @ValueSource(booleans = [true, false])
    fun algorithmsRunOverTheTransposedView(immutable: Boolean) {
        constructGraph(true, immutable)
        val transposed = graph.transpose()

        // v2 -> v1 -> v0 in the transposed view
        val tree = transposed.breadthFirstPathTree(v2)
        assertThat(tree.vertices).containsExactlyInAnyOrder(v0, v1, v2)
        assertThat(tree.materializePath(v0).vertices).containsExactly(v2, v1, v0)
        assertThat(tree.materializePath(v0).edges).containsExactly(e1, e0)
        assertThat(tree.pathLengthProperty[v0]).isEqualTo(2)
        assertThat(transposed.breadthFirstPathTree(v0).vertices).containsExactly(v0)

        assertThat(transposed.breadthFirstVertexIterator(v2).asSequence().toList()).containsExactly(v2, v1, v0)
        assertThat(transposed.depthFirstPostOrderVertexIterator(v2).asSequence().toList()).containsExactly(v0, v1, v2)

        val filtered = transposed.filter(vertexSetOf(v0, v1), edgeSetOf(e0))
        assertThat(filtered.parent).isSameAs(transposed)
        assertThat(filtered.edgeSource(e0)).isEqualTo(v1)
        assertThat(filtered.successors(v1)).containsExactlyInAnyOrder(v0)
    }
}
