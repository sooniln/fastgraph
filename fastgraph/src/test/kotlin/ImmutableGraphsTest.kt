package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ImmutableGraphsTest {

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
        assertThat(graph.vertexProperty.type).isEqualTo(staticTypeOf<String>())
        assertThat(graph.edgeProperty.type).isEqualTo(staticTypeOf<Int>())

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
        assertThat(immutable.edgeSource(e0)).isEqualTo(v0)
        assertThat(immutable.edgeTarget(e0)).isEqualTo(v1)

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
        assertThat(immutable.toImmutableGraph()).isSameAs(immutable)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun toImmutableGraphExtensionMatchesFactoryMethod(directed: Boolean) {
        val mutable = mutableGraph(directed)
        mutable.addVertex()

        val immutable = mutable.toImmutableGraph()

        assertThat(immutable).isInstanceOf(ImmutableGraph::class.java)
        assertThat(immutable.vertices).containsExactlyInAnyOrderElementsOf(mutable.vertices)
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
        assertThat(immutable.vertexProperty[immutable.graph.vertices.first()]).isNotEqualTo("changed")
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
}
