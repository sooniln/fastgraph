package io.github.sooniln.fastgraph

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PropertyTest {

    enum class GraphType {
        MUTABLE_GRAPH {
            override fun loadGraph(): Graph = buildGraph(true, builder = build())
        },
        MUTABLE_NETWORK {
            override fun loadGraph(): Graph = buildGraph(true, multiEdge = true, builder = build())
        },
        IMMUTABLE_GRAPH {
            override fun loadGraph(): Graph = buildImmutableGraph(true, builder = build())
        },
        IMMUTABLE_NETWORK {
            override fun loadGraph(): Graph = buildImmutableGraph(true, multiEdge = true, builder = build())
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

    class PropertyCase<T>(
        val type: TypeReference<T>,
        val defaultValue: T,
        val valueAt: (Int) -> T,
    ) {
        override fun toString(): String = type.toString()
    }

    companion object {
        @JvmStatic
        fun propertyCases(): List<PropertyCase<*>> = listOf(
            PropertyCase(TypeReference.of(), Unit) { },
            PropertyCase(TypeReference.of(), true) { index -> index % 2 == 0 },
            PropertyCase(TypeReference.of(), 1.toByte()) { index -> (2 shl index).toByte() },
            PropertyCase(TypeReference.of(), 1.toShort()) { index -> (2 shl index).toShort() },
            PropertyCase(TypeReference.of(), 1) { index -> 2 shl index },
            PropertyCase(TypeReference.of(), 1L) { index -> 2L shl index },
            PropertyCase(TypeReference.of(), 1f) { index -> (2 shl index).toFloat() },
            PropertyCase(TypeReference.of(), 1.0) { index -> (2 shl index).toDouble() },
            PropertyCase(TypeReference.of(), "hello") { index -> "test$index" },
        )

        @JvmStatic
        fun graphTypeAndPropertyCases(): Stream<Arguments> =
            GraphType.entries.stream().flatMap { graphType ->
                propertyCases().stream().map { case -> Arguments.of(graphType, case) }
            }
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("graphTypeAndPropertyCases")
    fun vertexProperty(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadGraph()
        val property = graph.createVertexProperty(typedCase.type) { typedCase.defaultValue }

        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(typedCase.defaultValue)
        }

        var index = 0
        for (vertex in graph.vertices) {
            property[vertex] = typedCase.valueAt(index)
            index++
        }

        index = 0
        for (vertex in graph.vertices) {
            assertThat(property[vertex]).isEqualTo(typedCase.valueAt(index))
            index++
        }
    }

    @Suppress("UNCHECKED_CAST")
    @ParameterizedTest(name = "graphType={0}, type={1}")
    @MethodSource("graphTypeAndPropertyCases")
    fun edgeProperty(graphType: GraphType, case: PropertyCase<*>) {
        val typedCase = case as PropertyCase<Any?>
        val graph = graphType.loadGraph()
        val property = graph.createEdgeProperty(typedCase.type) { typedCase.defaultValue }

        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(typedCase.defaultValue)
        }

        var index = 0
        for (edge in graph.edges) {
            property[edge] = typedCase.valueAt(index)
            index++
        }

        index = 0
        for (edge in graph.edges) {
            assertThat(property[edge]).isEqualTo(typedCase.valueAt(index))
            index++
        }
    }
}
