package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class PropertyTest {

    enum class GraphType {
        MUTABLE_GRAPH {
            override fun loadGraph(): Graph = Graphs.buildGraph(true, builder = build())
        },
        MUTABLE_NETWORK {
            override fun loadGraph(): Graph = Graphs.buildGraph(true, multiEdge = true, builder = build())
        },
        IMMUTABLE_GRAPH {
            override fun loadGraph(): Graph = ImmutableGraphs.buildImmutableGraph(true, builder = build())
        },
        IMMUTABLE_NETWORK {
            override fun loadGraph(): Graph = ImmutableGraphs.buildImmutableGraph(true, multiEdge = true, builder = build())
        };

        abstract fun loadGraph(): Graph

        protected fun build(): GraphBuilder.() -> Unit = {
            val v0 = addVertex()
            val v1 = addVertex()
            val v2 = addVertex()
            addEdge(v0, v1)
            addEdge(v1, v2)
            addEdge(v2, v0)
            addEdge(v0, v0)
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun booleanVertexProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty { true }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isTrue()
        }

        for (vertex in graph.vertices) {
            property[vertex] = false
            assertThat(property[vertex]).isFalse()
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun intVertexProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty { 1 }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(1)
        }

        var c = 2
        for (vertex in graph.vertices) {
            property[vertex] = c
            c *= 2
        }

        c = 2
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun floatVertexProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty { 1f }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(1f)
        }

        var c = 2f
        for (vertex in graph.vertices) {
            property[vertex] = c
            c *= 2
        }

        c = 2f
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun longVertexProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty { 1L }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(1L)
        }

        var c = 2L
        for (vertex in graph.vertices) {
            property[vertex] = c
            c *= 2
        }

        c = 2L
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun doubleVertexProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty { 1.0 }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(1.0)
        }

        var c = 2.0
        for (vertex in graph.vertices) {
            property[vertex] = c
            c *= 2
        }

        c = 2.0
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun objectVertexProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty { "hello" }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo("hello")
        }

        for (vertex in graph.vertices) {
            property[vertex] = "test$vertex"
        }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo("test$vertex")
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun booleanEdgeProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty { true }

        for (edge in graph.edges) {
            assertThat(property[edge]).isTrue()
        }

        for (edge in graph.edges) {
            property[edge] = false
            assertThat(property[edge]).isFalse()
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun intEdgeProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty { 1 }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(1)
        }

        var c = 2
        for (edge in graph.edges) {
            property[edge] = c
            c *= 2
        }

        c = 2
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun floatEdgeProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty { 1f }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(1f)
        }

        var c = 2f
        for (edge in graph.edges) {
            property[edge] = c
            c *= 2
        }

        c = 2f
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun longEdgeProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty { 1L }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(1L)
        }

        var c = 2L
        for (edge in graph.edges) {
            property[edge] = c
            c *= 2
        }

        c = 2L
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun doubleEdgeProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty { 1.0 }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(1.0)
        }

        var c = 2.0
        for (edge in graph.edges) {
            property[edge] = c
            c *= 2
        }

        c = 2.0
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(c)
            c *= 2
        }
    }

    @ParameterizedTest
    @EnumSource(GraphType::class)
    fun objectEdgeProperty(graphType: GraphType) {
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty { "hello" }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo("hello")
        }

        for (edge in graph.edges) {
            property[edge] = "test$edge"
        }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo("test$edge")
        }
    }
}
