package io.github.sooniln.fastgraph

import io.github.sooniln.fastgraph.filtered.filter
import io.github.sooniln.fastgraph.paths.buildPathForest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Verifies that the count methods on [Graph] agree with the sizes of the equivalent collections for the graph views and
 * derived graphs (the core graph implementations are covered by the directed/undirected graph/network tests).
 */
class GraphCountsTest {

    companion object {
        // a -> b, b -> a, a -> a, b -> c (and a second a -> b and a -> a if multiEdge)
        private fun directedGraph(multiEdge: Boolean): MutableGraph {
            val graph = mutableGraph(directed = true, multiEdge = multiEdge)
            val a = graph.addVertex()
            val b = graph.addVertex()
            val c = graph.addVertex()
            graph.addEdge(a, b)
            graph.addEdge(b, a)
            graph.addEdge(a, a)
            graph.addEdge(b, c)
            if (multiEdge) {
                graph.addEdge(a, b)
                graph.addEdge(a, a)
            }
            return graph
        }

        // a - b, a - a, b - c (and a second a - b and a - a if multiEdge)
        private fun undirectedGraph(multiEdge: Boolean): MutableGraph {
            val graph = mutableGraph(directed = false, multiEdge = multiEdge)
            val a = graph.addVertex()
            val b = graph.addVertex()
            val c = graph.addVertex()
            graph.addEdge(a, b)
            graph.addEdge(a, a)
            graph.addEdge(b, c)
            if (multiEdge) {
                graph.addEdge(a, b)
                graph.addEdge(a, a)
            }
            return graph
        }

        private fun case(name: String, factory: () -> Graph): Arguments = Arguments.of(name, factory)

        @JvmStatic
        fun graphs(): List<Arguments> = listOf(true, false).flatMap { multiEdge ->
            listOf(
                case("filtered inducing directed multiEdge=$multiEdge") {
                    val graph = directedGraph(multiEdge)
                    val c = graph.vertices.last()
                    graph.filter(
                        graph.vertices.filter { it != c }.let { vertexSetOf(*it.toTypedArray()) },
                        graph.edges.filter { graph.edgeTarget(it) != c }.let { edgeSetOf(*it.toTypedArray()) },
                    )
                },
                case("filtered predicate undirected multiEdge=$multiEdge") {
                    val graph = undirectedGraph(multiEdge)
                    val c = graph.vertices.last()
                    graph.filter({ vertex -> vertex != c })
                },
                case("transposed multiEdge=$multiEdge") { directedGraph(multiEdge).asTransposed() },
                case("asUndirected multiEdge=$multiEdge") { directedGraph(multiEdge).asUndirected() },
                // multiEdge=false produces a canonical edge graph, multiEdge=true produces an opaque edge graph
                case("asDirected multiEdge=$multiEdge") {
                    val graph = undirectedGraph(multiEdge)
                    graph.asDirected(graph.edgeIdProperty).source
                },
            )
        } + case("path forest") {
            val graph = directedGraph(multiEdge = false)
            val (a, b, c) = graph.vertices.toList()
            graph.buildPathForest {
                addRoot(a)
                setParentEdge(b, graph.edge(a, b))
                setParentEdge(c, graph.edge(b, c))
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("graphs")
    fun countsMatchCollectionSizes(@Suppress("unused") name: String, factory: () -> Graph) {
        val graph = factory()

        for (vertex in graph.vertices) {
            assertThat(graph.successorsCount(vertex)).isEqualTo(graph.successors(vertex).size)
            assertThat(graph.predecessorsCount(vertex)).isEqualTo(graph.predecessors(vertex).size)
            assertThat(graph.outgoingEdgeCount(vertex)).isEqualTo(graph.outgoingEdges(vertex).size)
            assertThat(graph.incomingEdgeCount(vertex)).isEqualTo(graph.incomingEdges(vertex).size)

            for (target in graph.vertices) {
                assertThat(graph.edgesCount(vertex, target)).isEqualTo(graph.edges(vertex, target).size)
                assertThat(graph.hasEdge(vertex, target)).isEqualTo(graph.edgesCount(vertex, target) > 0)
            }
        }
    }
}
