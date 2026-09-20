package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ValueGraphTest {

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun builderAddsVerticesByKeyOnDemand(directed: Boolean) {
        var a = Vertex(-1)
        var b = Vertex(-1)
        var c = Vertex(-1)
        var d = Vertex(-1)
        var ab = Edge(-1)
        var bc = Edge(-1)
        var ca = Edge(-1)
        val graph = buildValueGraph<String, Int>(directed, { 0 }) {
            a = addVertex("a")
            assertThat(hasVertex("a")).isTrue
            assertThat(hasVertex("b")).isFalse
            assertThat(getVertex("a")).isEqualTo(a)
            assertThrows<NoSuchElementException> { getVertex("b") }
            assertThrows<IllegalArgumentException> { addVertex("a") }

            // an edge by key creates the missing endpoint
            ab = addEdge("a", "b", 1)
            assertThat(hasVertex("b")).isTrue
            b = getVertex("b")
            bc = addEdge("b", "c")
            c = getVertex("c")
            ca = addEdge(c, a, 3)
            assertThat(hasVertex("c")).isTrue

            d = addVertex("d", 2, 2)
            assertThat(getVertex("d")).isEqualTo(d)
        }

        assertThat(graph.vertices).containsExactlyInAnyOrder(a, b, c, d)
        assertThat(graph.edges).containsExactlyInAnyOrder(ab, bc, ca)
        assertThat(graph.vertexKeys[a]).isEqualTo("a")
        assertThat(graph.vertexKeys[b]).isEqualTo("b")
        assertThat(graph.vertexKeys[c]).isEqualTo("c")
        assertThat(graph.vertexKeys[d]).isEqualTo("d")
        assertThat(graph.vertexKeys.getVertex("c")).isEqualTo(c)
        assertThat(graph.edgeValues[ab]).isEqualTo(1)
        assertThat(graph.edgeValues[bc]).isEqualTo(0)
        assertThat(graph.edgeValues[ca]).isEqualTo(3)
        if (directed) {
            assertThat(graph.edgeSource(ab)).isEqualTo(a)
            assertThat(graph.edgeTarget(ab)).isEqualTo(b)
        } else {
            assertThat(setOf(graph.edgeSource(ab), graph.edgeTarget(ab))).containsExactlyInAnyOrder(a, b)
        }
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun builderAcceptsNullValues(directed: Boolean) {
        var v = Vertex(-1)
        var e = Edge(-1)
        val graph = buildValueGraph<String?, Int?>(directed, { 7 }) {
            v = addVertex(null)
            assertThat(hasVertex(null)).isTrue
            assertThat(getVertex(null)).isEqualTo(v)
            e = addEdge(null, null, null)
        }

        assertThat(graph.vertexKeys[v]).isNull()
        assertThat(graph.edgeValues[e]).isNull()
        assertThat(graph.edgeSource(e)).isEqualTo(v)
        assertThat(graph.edgeTarget(e)).isEqualTo(v)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun valueGraphExposesTheTopologyAndBothProperties(directed: Boolean) {
        val graph = buildValueGraph<String, Float>(directed, { 0f }, multiEdge = true, indexEdges = true) {
            addEdge("a", "b", 1.5f)
            addEdge("a", "b", 2.5f)
            addEdge("b", "b")
        }

        assertThat(graph.directed).isEqualTo(directed)
        assertThat(graph.multiEdge).isTrue
        assertThat(graph.graph).isInstanceOf(IndexedEdgeGraph::class.java)
        assertThat(graph.vertexKeys.graph).isSameAs(graph.graph)
        assertThat(graph.edgeValues.graph).isSameAs(graph.graph)
        assertThat(graph.vertexKeys.type).isEqualTo(propertyTypeOf<String>())
        assertThat(graph.edgeValues.type).isEqualTo(propertyTypeOf<Float>())
        assertThat(graph.vertices).containsExactlyInAnyOrderElementsOf(graph.graph.vertices)
        assertThat(graph.edges).containsExactlyInAnyOrderElementsOf(graph.graph.edges)

        context(graph) {
            val a = graph.getVertex("a")
            val b = graph.getVertex("b")
            assertThat(a.key).isEqualTo("a")
            assertThat(a.edgesTo(b).map { it.value }).containsExactlyInAnyOrder(1.5f, 2.5f)
            assertThat(b.edgeTo(b).value).isEqualTo(0f)
            assertThat(graph.vertices.map { it.key }).containsExactlyInAnyOrder("a", "b")
        }

        // keyed mutations are visible through the wrapped graph and its properties
        val c = graph.addVertex("c")
        assertThat(graph.graph.vertices).contains(c)
        assertThat(graph.vertexKeys[c]).isEqualTo("c")
        context(graph) { assertThat(c.key).isEqualTo("c") }
        val cc = graph.addEdge(c, c)
        assertThat(graph.edgeValues[cc]).isEqualTo(0f)
        graph.removeEdge(cc)
        assertThat(graph.edges).doesNotContain(cc)
        graph.removeVertex(c)
        assertThat(graph.graph.vertices).doesNotContain(c)
        assertThat(graph.hasVertex("c")).isFalse
        assertThat(graph.vertexKeys.hasVertex("c")).isFalse
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun valueGraphFactoriesRequirePropertiesOfTheSameGraph(directed: Boolean) {
        val graph = mutableGraph(directed)
        val other = mutableGraph(directed)
        val vertexKeys = graph.createVertexKeyProperty<String>()
        val edgeValues = graph.createEdgeProperty<String>()

        val valueGraph = valueGraph(graph, vertexKeys, edgeValues)
        assertThat(valueGraph.graph).isSameAs(graph)
        assertThat(valueGraph.vertexKeys).isSameAs(vertexKeys)
        assertThat(valueGraph.edgeValues).isSameAs(edgeValues)
        val mutableValueGraph = mutableValueGraph(graph, vertexKeys, edgeValues)
        assertThat(mutableValueGraph.graph).isSameAs(graph)
        val v = mutableValueGraph.addVertex("v")
        assertThat(valueGraph.vertices).containsExactly(v)
        assertThat(vertexKeys[v]).isEqualTo("v")

        assertThrows<IllegalArgumentException> { valueGraph(graph, other.createVertexKeyProperty<String>(), edgeValues) }
        assertThrows<IllegalArgumentException> { valueGraph(graph, vertexKeys, other.createEdgeProperty<String>()) }
        assertThrows<IllegalArgumentException> { mutableValueGraph(graph, other.createVertexKeyProperty<String>(), edgeValues) }
        assertThrows<IllegalArgumentException> { mutableValueGraph(graph, vertexKeys, other.createEdgeProperty<String>()) }
        assertThrows<IllegalArgumentException> { valueGraph(other, vertexKeys, edgeValues) }

        // the vertex id property is the way to say "no vertex data"
        val ids = valueGraph(graph, graph.vertexIdProperty, edgeValues)
        assertThat(ids.vertexKeys[v]).isEqualTo(v.id)
        val immutable = graph.toImmutableGraph()
        assertThrows<IllegalArgumentException> { ImmutableValueGraph(immutable, vertexKeys, immutable.createEdgeProperty<String>()) }
        assertThrows<IllegalArgumentException> { ImmutableValueGraph(immutable, immutable.createVertexKeyProperty<String>(), edgeValues) }
        val immutableValueGraph = ImmutableValueGraph(immutable, immutable.createVertexKeyProperty<String>(), immutable.createEdgeProperty<String>())
        assertThat(immutableValueGraph.graph).isSameAs(immutable)
        assertThat(immutableValueGraph.vertices).containsExactly(v)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun nullableDefaultsForMutableAndImmutableBuilders(directed: Boolean) {
        val mutable = buildValueGraph<String, Int?>(directed, { null }) {
            addEdge("a", "b")
        }
        val immutable = buildImmutableValueGraph<String, Int>(directed) {
            addEdge("a", "b", 5)
        }

        for (graph in listOf<ValueGraph<String, Int?>>(mutable, immutable)) {
            assertThat(graph.vertexKeys.type).isEqualTo(propertyTypeOf<String>())
            assertThat(graph.edgeValues.type).isEqualTo(propertyTypeOf<Int?>())
            assertThat(graph.vertices.map { graph.vertexKeys[it] }).containsExactlyInAnyOrder("a", "b")
        }
        assertThat(mutable.edgeValues[mutable.edges.first()]).isNull()
        assertThat(immutable.edgeValues[immutable.edges.first()]).isEqualTo(5)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun toImmutableValueGraphPreservesIdsAndValues(directed: Boolean) {
        val mutable = buildValueGraph<String, Int>(directed, { -1 }, multiEdge = true) {
            addEdge("a", "b", 1)
            addEdge("a", "b", 2)
            addEdge("b", "b", 3)
            addVertex("lonely")
        }

        val immutable = mutable.toImmutableValueGraph()

        assertThat(immutable.graph).isInstanceOf(ImmutableGraph::class.java)
        assertThat(immutable.graph.multiEdge).isTrue
        assertThat(immutable.vertices).containsExactlyInAnyOrderElementsOf(mutable.vertices)
        assertThat(immutable.edges).containsExactlyInAnyOrderElementsOf(mutable.edges)
        for (vertex in mutable.vertices) {
            assertThat(immutable.vertexKeys[vertex]).isEqualTo(mutable.vertexKeys[vertex])
            assertThat(immutable.vertexKeys.getVertex(mutable.vertexKeys[vertex])).isEqualTo(vertex)
        }
        for (edge in mutable.edges) {
            assertThat(immutable.edgeValues[edge]).isEqualTo(mutable.edgeValues[edge])
            assertThat(setOf(immutable.edgeSource(edge), immutable.edgeTarget(edge)))
                .isEqualTo(setOf(mutable.edgeSource(edge), mutable.edgeTarget(edge)))
        }
        assertThat(immutable.toImmutableValueGraph()).isSameAs(immutable)

        // an empty value graph becomes the empty immutable value graph, keeping its types
        val emptyGraph = mutableGraph(directed)
        val emptyValueGraph = mutableValueGraph(emptyGraph, emptyGraph.createVertexKeyProperty<String>(), emptyGraph.createEdgeProperty<Int>(0))
        val emptyImmutable = emptyValueGraph.toImmutableValueGraph()
        assertThat(emptyImmutable.graph).isSameAs(emptyImmutableGraph(directed))
        assertThat(emptyImmutable.vertexKeys.type).isEqualTo(propertyTypeOf<String>())
        assertThat(emptyImmutable.edgeValues.type).isEqualTo(propertyTypeOf<Int>())
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun density(directed: Boolean) {
        val graph = buildGraph(directed) {
            val v0 = addVertex()
            val v1 = addVertex()
            val v2 = addVertex()
            val v3 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
            addEdge(v2, v3)
        }

        // M / (N (N - 1)) for directed graphs, 2M / (N (N - 1)) for undirected graphs
        assertThat(graph.density()).isEqualTo(if (directed) 3.0 / 12 else 6.0 / 12)

        val complete = buildGraph(directed) {
            val v0 = addVertex()
            val v1 = addVertex()
            val v2 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
            addEdge(v2, v0)
            if (directed) {
                addEdge(v1, v0)
                addEdge(v2, v1)
                addEdge(v0, v2)
            }
        }
        assertThat(complete.density()).isEqualTo(1.0)
        assertThat(complete.toImmutableGraph().density()).isEqualTo(1.0)
    }
}
