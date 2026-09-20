package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class ImmutableGraphsTest {

    companion object {
        @JvmStatic
        fun kinds(): List<Arguments> = MutableGraphContractTest.kinds()
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun emptyImmutableGraph(directed: Boolean) {
        val graph = io.github.sooniln.fastgraph.emptyImmutableGraph(directed)

        assertThat(graph.directed).isEqualTo(directed)
        assertThat(graph.isEmpty()).isTrue
        assertThat(graph.vertices).isEmpty()
        assertThat(graph.edges).isEmpty()

        assertThrows<IllegalArgumentException> { graph.outDegree(Vertex(0)) }
        assertThrows<IllegalArgumentException> { graph.createVertexReference(Vertex(0)) }
        assertThrows<IllegalArgumentException> { graph.createEdgeReference(Edge(0)) }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun emptyImmutableValueGraph(directed: Boolean) {
        val graph = emptyImmutableValueGraph<String, Int>(directed)

        assertThat(graph.directed).isEqualTo(directed)
        assertThat(graph.isEmpty()).isTrue
        assertThat(graph.vertexProperty.type).isEqualTo(propertyTypeOf<String>())
        assertThat(graph.edgeProperty.type).isEqualTo(propertyTypeOf<Int>())

        assertThrows<IllegalArgumentException> { graph.vertexProperty[Vertex(0)] }
        assertThrows<IllegalArgumentException> { graph.edgeProperty[Edge(0)] }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun immutableGraphCopiesTopologyAndIsIndependentOfSource(directed: Boolean) {
        val mutable = mutableGraph(directed)
        val v0 = mutable.addVertex()
        val v1 = mutable.addVertex()
        val e0 = mutable.addEdge(v0, v1)

        val immutable = mutable.toImmutableGraph()

        assertThat(immutable).isInstanceOf(ImmutableGraph::class.java)
        assertThat(immutable.vertices).containsExactlyInAnyOrder(v0, v1)
        assertThat(immutable.edges).containsExactlyInAnyOrder(e0)
        if (directed) {
            assertThat(immutable.edgeSource(e0)).isEqualTo(v0)
            assertThat(immutable.edgeTarget(e0)).isEqualTo(v1)
        } else {
            assertThat(setOf(immutable.edgeSource(e0), immutable.edgeTarget(e0))).containsExactlyInAnyOrder(v0, v1)
        }

        // mutating the source graph after the copy must not affect the immutable copy
        mutable.addVertex()
        assertThat(immutable.vertices).containsExactlyInAnyOrder(v0, v1)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun immutableGraphOfAlreadyImmutableGraphReturnsSameInstance(directed: Boolean) {
        val immutable = buildImmutableGraph(directed) {
            addVertex()
        }

        assertThat(immutable.toImmutableGraph()).isSameAs(immutable)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun immutableValueGraphCopiesPropertyValues(directed: Boolean) {
        val valueGraph = buildValueGraph<String, Int>(directed, { "" }, { 0 }) {
            val v0 = addVertex("a")
            val v1 = addVertex("b")
            addEdge(v0, v1, 42)
        }

        val immutable = valueGraph.toImmutableValueGraph()

        for (vertex in immutable.graph.vertices) {
            assertThat(immutable.vertexProperty[vertex]).isEqualTo(valueGraph.vertexProperty[vertex])
        }
        for (edge in immutable.graph.edges) {
            assertThat(immutable.edgeProperty[edge]).isEqualTo(valueGraph.edgeProperty[edge])
        }

        // mutating the source value graph's properties after the copy must not affect the immutable copy
        for (vertex in valueGraph.graph.vertices) {
            valueGraph.vertexProperty[vertex] = "changed"
        }
        for (edge in valueGraph.graph.edges) {
            valueGraph.edgeProperty[edge] = -1
        }
        assertThat(immutable.vertexProperty[immutable.graph.vertices.first()]).isEqualTo("a")
        assertThat(immutable.edgeProperty[immutable.graph.edges.first()]).isEqualTo(42)

        // and vice versa
        immutable.vertexProperty[immutable.graph.vertices.first()] = "copy"
        immutable.edgeProperty[immutable.graph.edges.first()] = 7
        assertThat(valueGraph.vertexProperty[valueGraph.graph.vertices.first()]).isEqualTo("changed")
        assertThat(valueGraph.edgeProperty[valueGraph.graph.edges.first()]).isEqualTo(-1)

        assertThat(immutable.vertexProperty.graph).isSameAs(immutable.graph)
        assertThat(immutable.edgeProperty.graph).isSameAs(immutable.graph)
        assertThat(immutable.toImmutableValueGraph()).isSameAs(immutable)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun buildImmutableValueGraphWithNullableDefaults(directed: Boolean) {
        val graph = buildImmutableValueGraph<String, Int>(directed) {
            val v0 = addVertex()
            val v1 = addVertex()
            addEdge(v0, v1)
        }

        for (vertex in graph.graph.vertices) {
            assertThat(graph.vertexProperty[vertex]).isNull()
        }
        for (edge in graph.graph.edges) {
            assertThat(graph.edgeProperty[edge]).isNull()
        }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun toImmutableGraphPreservesIdsAndTopologyForEveryOption(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val mutable = kind.create(directed)
        val a = mutable.addVertex()
        val b = mutable.addVertex()
        val c = mutable.addVertex()
        mutable.addVertex() // isolated
        val edges = mutableListOf(mutable.addEdge(a, b), mutable.addEdge(b, c), mutable.addEdge(c, a), mutable.addEdge(a, a))
        if (kind.multiEdge) {
            edges += mutable.addEdge(a, b)
            edges += mutable.addEdge(a, a)
        }

        val immutable = mutable.toImmutableGraph()

        assertThat(immutable).isInstanceOf(ImmutableGraph::class.java)
        assertThat(immutable.directed).isEqualTo(directed)
        assertThat(immutable.multiEdge).isEqualTo(kind.multiEdge)
        assertThat(immutable).isInstanceOf(IndexedVertexGraph::class.java)
        if (kind.indexEdges) assertThat(immutable).isInstanceOf(IndexedEdgeGraph::class.java)

        // identical ids: every vertex/edge of the source is a vertex/edge of the copy with the same endpoints
        assertThat(immutable.vertices).containsExactlyInAnyOrderElementsOf(mutable.vertices)
        assertThat(immutable.edges).containsExactlyInAnyOrderElementsOf(mutable.edges)
        assertThat(immutable.edges).containsExactlyInAnyOrderElementsOf(edges)
        for (edge in mutable.edges) {
            if (directed) {
                assertThat(immutable.edgeSource(edge)).isEqualTo(mutable.edgeSource(edge))
                assertThat(immutable.edgeTarget(edge)).isEqualTo(mutable.edgeTarget(edge))
            } else {
                assertThat(setOf(immutable.edgeSource(edge), immutable.edgeTarget(edge)))
                    .isEqualTo(setOf(mutable.edgeSource(edge), mutable.edgeTarget(edge)))
            }
        }
        for (vertex in mutable.vertices) {
            assertThat(immutable.outDegree(vertex)).isEqualTo(mutable.outDegree(vertex))
            assertThat(immutable.inDegree(vertex)).isEqualTo(mutable.inDegree(vertex))
            assertThat(immutable.successors(vertex)).containsExactlyInAnyOrderElementsOf(mutable.successors(vertex))
            assertThat(immutable.predecessors(vertex)).containsExactlyInAnyOrderElementsOf(mutable.predecessors(vertex))
            assertThat(immutable.outgoingEdges(vertex)).containsExactlyInAnyOrderElementsOf(mutable.outgoingEdges(vertex))
            assertThat(immutable.incomingEdges(vertex)).containsExactlyInAnyOrderElementsOf(mutable.incomingEdges(vertex))
            for (other in mutable.vertices) {
                assertThat(immutable.hasEdge(vertex, other)).isEqualTo(mutable.hasEdge(vertex, other))
                assertThat(immutable.edges(vertex, other)).containsExactlyInAnyOrderElementsOf(mutable.edges(vertex, other))
            }
        }

        // the copy is a snapshot
        mutable.removeVertex(a)
        assertThat(immutable.vertices).hasSize(4)
        assertThat(immutable.edges).containsExactlyInAnyOrderElementsOf(edges)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun toImmutableGraphIsUnsupportedForViewsWithOpaqueIds(directed: Boolean) {
        val mutable = mutableGraph(directed)
        val v0 = mutable.addVertex()
        val v1 = mutable.addVertex()
        mutable.addEdge(v0, v1)

        // views do not guarantee identity-indexed vertices, so there is nothing to copy them into yet
        assertThrows<UnsupportedOperationException> { mutable.filter(vertexSetOf(v0, v1), mutable.edges).toImmutableGraph() }
        assertThrows<UnsupportedOperationException> { mutable.filter({ true }, { true }).toImmutableGraph() }
        if (directed) {
            assertThrows<UnsupportedOperationException> { mutable.transpose().toImmutableGraph() }
        }

        // ... except for empty views, which become the empty immutable graph
        assertThat(mutable.filter({ false }, { true }).toImmutableGraph()).isSameAs(io.github.sooniln.fastgraph.emptyImmutableGraph(directed))

        // a path tree is already immutable
        val tree = mutable.breadthFirstPathTree(v0)
        assertThat(tree.toImmutableGraph()).isSameAs(tree)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun emptyImmutableGraphIsASingletonPerDirectedness(directed: Boolean) {
        val empty = io.github.sooniln.fastgraph.emptyImmutableGraph(directed)

        assertThat(io.github.sooniln.fastgraph.emptyImmutableGraph(directed)).isSameAs(empty)
        assertThat(io.github.sooniln.fastgraph.emptyImmutableGraph(!directed)).isNotSameAs(empty)
        assertThat(mutableGraph(directed).toImmutableGraph()).isSameAs(empty)
        assertThat(mutableGraph(directed, multiEdge = true).toImmutableGraph()).isSameAs(empty)
        assertThat(emptyGraph(directed)).isSameAs(empty)
        assertThat(empty.multiEdge).isFalse
        assertThat(empty).isInstanceOf(IdentityIndexedVertexGraph::class.java)
        assertThat(empty).isInstanceOf(IdentityIndexedEdgeGraph::class.java)

        // every accessor rejects every vertex and edge
        for (vertex in listOf(Vertex(-1), Vertex(0), Vertex(1))) {
            assertThrows<IllegalArgumentException> { empty.outDegree(vertex) }
            assertThrows<IllegalArgumentException> { empty.inDegree(vertex) }
            assertThrows<IllegalArgumentException> { empty.successors(vertex) }
            assertThrows<IllegalArgumentException> { empty.predecessors(vertex) }
            assertThrows<IllegalArgumentException> { empty.outgoingEdges(vertex) }
            assertThrows<IllegalArgumentException> { empty.incomingEdges(vertex) }
            assertThrows<IllegalArgumentException> { empty.hasEdge(vertex, vertex) }
            assertThrows<IllegalArgumentException> { empty.edges(vertex, vertex) }
            assertThrows<IllegalArgumentException> { empty.edge(vertex, vertex) }
            assertThrows<IllegalArgumentException> { empty.createVertexReference(vertex) }
            assertThat(empty.vertices.contains(vertex)).isFalse
        }
        for (edge in listOf(Edge(-1), Edge(0), Edge(1))) {
            assertThrows<IllegalArgumentException> { empty.edgeSource(edge) }
            assertThrows<IllegalArgumentException> { empty.edgeTarget(edge) }
            assertThrows<IllegalArgumentException> { empty.createEdgeReference(edge) }
            assertThat(empty.edges.contains(edge)).isFalse
        }

        // properties exist but hold nothing
        val vertexProperty = empty.createVertexProperty<String>()
        val edgeProperty = empty.createEdgeProperty<String>()
        val vertexKeys = empty.createVertexKeyProperty<String>()
        val edgeKeys = empty.createEdgeKeyProperty<String>()
        assertThat(vertexProperty.graph).isSameAs(empty)
        assertThat(edgeProperty.graph).isSameAs(empty)
        assertThat(vertexKeys.graph).isSameAs(empty)
        assertThat(edgeKeys.graph).isSameAs(empty)
        assertThrows<IllegalArgumentException> { vertexProperty[Vertex(0)] }
        assertThrows<IllegalArgumentException> { vertexProperty[Vertex(0)] = "x" }
        assertThrows<IllegalArgumentException> { edgeProperty[Edge(0)] }
        assertThrows<IllegalArgumentException> { edgeProperty[Edge(0)] = "x" }
        assertThat(vertexKeys.hasVertex("x")).isFalse
        assertThat(edgeKeys.hasEdge("x")).isFalse
        assertThrows<NoSuchElementException> { vertexKeys.getVertex("x") }
        assertThrows<NoSuchElementException> { edgeKeys.getEdge("x") }
        assertThrows<IllegalArgumentException> { vertexKeys[Vertex(0)] = "x" }
        assertThrows<IllegalArgumentException> { edgeKeys[Edge(0)] = "x" }

        // listeners are accepted and ignored
        empty.registerVertexChangeListener(object : VertexChangeListener {
            override fun onVertexAdded(vertex: Vertex) = throw AssertionError()
            override fun onVertexRemoved(vertex: Vertex) = throw AssertionError()
            override fun onVertexReassigned(oldVertex: Vertex, newVertex: Vertex) = throw AssertionError()
        })
        empty.registerEdgeChangeListener(object : EdgeChangeListener {
            override fun onEdgeAdded(edge: Edge) = throw AssertionError()
            override fun onEdgeRemoved(edge: Edge) = throw AssertionError()
            override fun onEdgeReassigned(oldEdge: Edge, newEdge: Edge) = throw AssertionError()
        })
        empty.trimToSize()
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun emptyValueGraphMatchesEmptyImmutableValueGraph(directed: Boolean) {
        val graph = emptyValueGraph<String, Int>(directed)

        assertThat(graph.graph).isSameAs(io.github.sooniln.fastgraph.emptyImmutableGraph(directed))
        assertThat(graph.directed).isEqualTo(directed)
        assertThat(graph.isEmpty()).isTrue
        assertThat(graph.vertexProperty.type).isEqualTo(propertyTypeOf<String>())
        assertThat(graph.edgeProperty.type).isEqualTo(propertyTypeOf<Int>())
        assertThat(graph.vertexProperty.graph).isSameAs(graph.graph)
        assertThat(graph.edgeProperty.graph).isSameAs(graph.graph)
        assertThrows<IllegalArgumentException> { graph.vertexProperty[Vertex(0)] = "x" }
        assertThrows<IllegalArgumentException> { graph.edgeProperty[Edge(0)] = 1 }
    }

    @ParameterizedTest(name = "{0}, directed={1}")
    @MethodSource("kinds")
    fun immutableGraphRejectsForeignReferences(kind: MutableGraphContractTest.GraphKind, directed: Boolean) {
        val mutable = kind.create(directed)
        val v0 = mutable.addVertex()
        val v1 = mutable.addVertex()
        val e0 = mutable.addEdge(v0, v1)
        val immutable = mutable.toImmutableGraph()

        assertThat(immutable.createVertexReference(v1).unstable).isEqualTo(v1)
        assertThat(immutable.createEdgeReference(e0).unstable).isEqualTo(e0)
        assertThrows<IllegalArgumentException> { immutable.createVertexReference(Vertex(2)) }
        assertThrows<IllegalArgumentException> { immutable.createVertexReference(Vertex(-1)) }
        assertThrows<IllegalArgumentException> { immutable.createEdgeReference(Edge(-1)) }
        assertThrows<IllegalArgumentException> { immutable.createEdgeReference(Edge(99)) }
    }
}
