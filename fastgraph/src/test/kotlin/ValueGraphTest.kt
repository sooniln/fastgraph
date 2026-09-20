package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ValueGraphTest {

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun builderAddsVerticesByValueOnDemand(directed: Boolean) {
        var a = Vertex(-1)
        var b = Vertex(-1)
        var c = Vertex(-1)
        var ab = Edge(-1)
        var bc = Edge(-1)
        var ca = Edge(-1)
        val graph = buildValueGraph<String, Int>(directed, { "" }, { 0 }) {
            a = addVertex("a")
            assertThat(hasVertex("a")).isTrue
            assertThat(hasVertex("b")).isFalse
            assertThat(getVertex("a")).isEqualTo(a)
            assertThrows<NoSuchElementException> { getVertex("b") }

            // an edge by value creates the missing endpoint
            ab = addEdge("a", "b", 1)
            assertThat(hasVertex("b")).isTrue
            b = getVertex("b")
            bc = addEdge("b", "c")
            c = getVertex("c")
            ca = addEdge(c, a, 3)
            assertThat(hasVertex("c")).isTrue

            // a vertex added without a value is unknown to the builder
            val anonymous = addVertex()
            assertThat(hasVertex("")).isFalse
            assertThat(addVertex(2, 2)).isNotEqualTo(anonymous)
        }

        assertThat(graph.vertices).hasSize(5)
        assertThat(graph.edges).containsExactlyInAnyOrder(ab, bc, ca)
        assertThat(graph.vertexProperty[a]).isEqualTo("a")
        assertThat(graph.vertexProperty[b]).isEqualTo("b")
        assertThat(graph.vertexProperty[c]).isEqualTo("c")
        assertThat(graph.edgeProperty[ab]).isEqualTo(1)
        assertThat(graph.edgeProperty[bc]).isEqualTo(0)
        assertThat(graph.edgeProperty[ca]).isEqualTo(3)
        if (directed) {
            assertThat(graph.edgeSource(ab)).isEqualTo(a)
            assertThat(graph.edgeTarget(ab)).isEqualTo(b)
        } else {
            assertThat(setOf(graph.edgeSource(ab), graph.edgeTarget(ab))).containsExactlyInAnyOrder(a, b)
        }
        // the anonymous vertices got the default value
        assertThat(graph.vertices.count { graph.vertexProperty[it] == "" }).isEqualTo(2)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun builderAcceptsNullValues(directed: Boolean) {
        var v = Vertex(-1)
        var e = Edge(-1)
        val graph = buildValueGraph<String?, Int?>(directed, { "x" }, { 7 }) {
            v = addVertex(null)
            assertThat(hasVertex(null)).isTrue
            assertThat(getVertex(null)).isEqualTo(v)
            e = addEdge(null, null, null)
        }

        assertThat(graph.vertexProperty[v]).isNull()
        assertThat(graph.edgeProperty[e]).isNull()
        assertThat(graph.edgeSource(e)).isEqualTo(v)
        assertThat(graph.edgeTarget(e)).isEqualTo(v)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun valueGraphExposesTheTopologyAndBothProperties(directed: Boolean) {
        val graph = buildValueGraph<String, Float>(directed, "?", 0f, multiEdge = true, indexEdges = true) {
            addEdge("a", "b", 1.5f)
            addEdge("a", "b", 2.5f)
            addEdge("b", "b")
        }

        assertThat(graph.directed).isEqualTo(directed)
        assertThat(graph.multiEdge).isTrue
        assertThat(graph.graph).isInstanceOf(IndexedEdgeGraph::class.java)
        assertThat(graph.vertexProperty.graph).isSameAs(graph.graph)
        assertThat(graph.edgeProperty.graph).isSameAs(graph.graph)
        assertThat(graph.vertexProperty.type).isEqualTo(propertyTypeOf<String>())
        assertThat(graph.edgeProperty.type).isEqualTo(propertyTypeOf<Float>())
        assertThat(graph.vertices).containsExactlyInAnyOrderElementsOf(graph.graph.vertices)
        assertThat(graph.edges).containsExactlyInAnyOrderElementsOf(graph.graph.edges)

        context(graph) {
            val a = graph.vertices.first { it.value == "a" }
            val b = graph.vertices.first { it.value == "b" }
            assertThat(a.edgesTo(b).map { it.value }).containsExactlyInAnyOrder(1.5f, 2.5f)
            assertThat(b.edgeTo(b).value).isEqualTo(0f)
            assertThat(graph.vertices.map { it.value }).containsExactlyInAnyOrder("a", "b")
        }

        // the value graph is a mutable graph: mutations are visible through the wrapped graph and its properties
        val c = graph.addVertex()
        assertThat(graph.graph.vertices).contains(c)
        assertThat(graph.vertexProperty[c]).isEqualTo("?")
        graph.vertexProperty[c] = "c"
        context(graph) { assertThat(c.value).isEqualTo("c") }
        val cc = graph.addEdge(c, c)
        assertThat(graph.edgeProperty[cc]).isEqualTo(0f)
        graph.removeVertex(c)
        assertThat(graph.graph.vertices).doesNotContain(c)
        assertThat(graph.edges).doesNotContain(cc)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun valueGraphFactoriesRequirePropertiesOfTheSameGraph(directed: Boolean) {
        val graph = mutableGraph(directed)
        val other = mutableGraph(directed)
        val vertexProperty = graph.createVertexProperty<String>()
        val edgeProperty = graph.createEdgeProperty<String>()

        val valueGraph = valueGraph(graph, vertexProperty, edgeProperty)
        assertThat(valueGraph.graph).isSameAs(graph)
        assertThat(valueGraph.vertexProperty).isSameAs(vertexProperty)
        assertThat(valueGraph.edgeProperty).isSameAs(edgeProperty)
        val mutableValueGraph = mutableValueGraph(graph, vertexProperty, edgeProperty)
        assertThat(mutableValueGraph.graph).isSameAs(graph)
        val v = mutableValueGraph.addVertex()
        assertThat(valueGraph.vertices).containsExactly(v)

        assertThrows<IllegalArgumentException> { valueGraph(graph, other.createVertexProperty<String>(), edgeProperty) }
        assertThrows<IllegalArgumentException> { valueGraph(graph, vertexProperty, other.createEdgeProperty<String>()) }
        assertThrows<IllegalArgumentException> { mutableValueGraph(graph, other.createVertexProperty<String>(), edgeProperty) }
        assertThrows<IllegalArgumentException> { mutableValueGraph(graph, vertexProperty, other.createEdgeProperty<String>()) }
        assertThrows<IllegalArgumentException> { valueGraph(other, vertexProperty, edgeProperty) }

        // unit properties are the way to say "no data on this side"
        val unit = valueGraph(graph, unitVertexProperty(graph), edgeProperty)
        assertThat(unit.vertexProperty[v]).isEqualTo(Unit)
        val immutable = graph.toImmutableGraph()
        assertThrows<IllegalArgumentException> { ImmutableValueGraph(immutable, vertexProperty, immutable.createEdgeProperty<String>()) }
        assertThrows<IllegalArgumentException> { ImmutableValueGraph(immutable, immutable.createVertexProperty<String>(), edgeProperty) }
        val immutableValueGraph = ImmutableValueGraph(immutable, immutable.createVertexProperty<String>(), immutable.createEdgeProperty<String>())
        assertThat(immutableValueGraph.graph).isSameAs(immutable)
        assertThat(immutableValueGraph.vertices).containsExactly(v)
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun nullableDefaultsForMutableAndImmutableBuilders(directed: Boolean) {
        val mutable = buildValueGraph<String?, Int?>(directed, { null }, { null }) {
            addEdge("a", "b")
        }
        val immutable = buildImmutableValueGraph<String, Int>(directed) {
            addEdge("a", "b", 5)
        }

        for (graph in listOf<ValueGraph<String?, Int?>>(mutable, immutable)) {
            assertThat(graph.vertexProperty.type).isEqualTo(propertyTypeOf<String?>())
            assertThat(graph.edgeProperty.type).isEqualTo(propertyTypeOf<Int?>())
            assertThat(graph.vertices.map { graph.vertexProperty[it] }).containsExactlyInAnyOrder("a", "b")
        }
        assertThat(mutable.edgeProperty[mutable.edges.first()]).isNull()
        assertThat(immutable.edgeProperty[immutable.edges.first()]).isEqualTo(5)

        // an added vertex gets the null default
        val added = mutable.addVertex()
        assertThat(mutable.vertexProperty[added]).isNull()
    }

    @ParameterizedTest(name = "directed={0}")
    @ValueSource(booleans = [true, false])
    fun toImmutableValueGraphPreservesIdsAndValues(directed: Boolean) {
        val mutable = buildValueGraph<String, Int>(directed, "?", -1, multiEdge = true) {
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
            assertThat(immutable.vertexProperty[vertex]).isEqualTo(mutable.vertexProperty[vertex])
        }
        for (edge in mutable.edges) {
            assertThat(immutable.edgeProperty[edge]).isEqualTo(mutable.edgeProperty[edge])
            assertThat(setOf(immutable.edgeSource(edge), immutable.edgeTarget(edge)))
                .isEqualTo(setOf(mutable.edgeSource(edge), mutable.edgeTarget(edge)))
        }
        assertThat(immutable.toImmutableValueGraph()).isSameAs(immutable)

        // an empty value graph becomes the empty immutable value graph, keeping its types
        val emptyGraph = mutableGraph(directed)
        val emptyValueGraph = mutableValueGraph(emptyGraph, emptyGraph.createVertexProperty<String>(), emptyGraph.createEdgeProperty<Int>(0))
        val emptyImmutable = emptyValueGraph.toImmutableValueGraph()
        assertThat(emptyImmutable.graph).isSameAs(emptyImmutableGraph(directed))
        assertThat(emptyImmutable.vertexProperty.type).isEqualTo(propertyTypeOf<String?>())
        assertThat(emptyImmutable.edgeProperty.type).isEqualTo(propertyTypeOf<Int>())
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
